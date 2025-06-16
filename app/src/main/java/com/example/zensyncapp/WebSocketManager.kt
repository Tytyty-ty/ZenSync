package com.example.zensyncapp.network

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.header
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.Channel

class WebSocketManager(private val client: HttpClient) {
    private var session: DefaultClientWebSocketSession? = null
    private var job: Job? = null
    private var timerJob: Job? = null
    private val messageChannel = Channel<String>(Channel.UNLIMITED)
    private val _participantUpdates = MutableStateFlow<List<String>>(emptyList())
    val participantUpdates: StateFlow<List<String>> = _participantUpdates

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _participants = mutableStateListOf<String>()
    val participants: SnapshotStateList<String> = _participants

    private val _currentTime = MutableStateFlow(0)
    val currentTime: StateFlow<Int> = _currentTime

    private val _roomDuration = MutableStateFlow(0)
    val roomDuration: StateFlow<Int> = _roomDuration

    private val _messages = mutableStateListOf<String>()
    val messages: SnapshotStateList<String> = _messages

    private val _remainingTime = MutableStateFlow(0)
    val remainingTime: StateFlow<Int> = _remainingTime

    private val _serverTime = MutableStateFlow(0)
    val serverTime: StateFlow<Int> = _serverTime

    fun initializeRoom(durationMinutes: Int) {
        _roomDuration.value = durationMinutes * 60
        _remainingTime.value = durationMinutes * 60
        _serverTime.value = durationMinutes * 60
    }

    fun setInitialTime(minutes: Int) {
        _remainingTime.value = minutes * 60
        _serverTime.value = minutes * 60
    }

    sealed class ConnectionState {
        object CONNECTED : ConnectionState()
        object DISCONNECTED : ConnectionState()
        object CONNECTING : ConnectionState()
        data class ERROR(val message: String) : ConnectionState()
    }

    private val _connectionState = mutableStateOf<ConnectionState>(ConnectionState.DISCONNECTED)
    val connectionState: State<ConnectionState> = _connectionState

    suspend fun connectToMeditationRoom(
        roomId: String,
        authToken: String? = null,
        userId: String? = null,
        username: String? = null) {
        try {
            _connectionState.value = ConnectionState.CONNECTING
            session?.close()
            timerJob?.cancel()

            session = client.webSocketSession {
                url {
                    protocol = URLProtocol.WS
                    host = "192.168.3.6"
                    port = 8081
                    path("ws/meditation/$roomId")
                }
                authToken?.let {
                    header(HttpHeaders.Authorization, "Bearer $it")
                }
                userId?.let {
                    header("X-User-Id", it)
                }
                username?.let {
                    header("X-Username", it)
                }
            }

            job = CoroutineScope(Dispatchers.IO).launch {
                _connectionState.value = ConnectionState.CONNECTED

                launch {
                    try {
                        session?.incoming?.consumeAsFlow()?.collect { frame ->
                            if (frame is Frame.Text) {
                                val message = frame.readText()
                                messageChannel.send(message)
                            }
                        }
                    } catch (e: Exception) {
                        _connectionState.value = ConnectionState.ERROR(e.message ?: "Connection error")
                    }
                }

                launch {
                    for (message in messageChannel) {
                        handleMessage(message)
                    }
                }
            }

            timerJob = CoroutineScope(Dispatchers.IO).launch {
                while (true) {
                    delay(1000)
                    if (isPlaying.value && _serverTime.value > 0) {
                        _serverTime.value = _serverTime.value - 1
                        if (_serverTime.value <= 0) {
                            _isPlaying.value = false
                        }
                    }
                }
            }
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.ERROR(e.message ?: "Connection failed")
        }
    }

    suspend fun connectToMusicRoom(
        roomId: String,
        authToken: String? = null,
        userId: String? = null,
        username: String? = null
    ) {
        try {
            _connectionState.value = ConnectionState.CONNECTING
            session?.close()
            timerJob?.cancel()

            session = client.webSocketSession {
                url {
                    protocol = URLProtocol.WS
                    host = "192.168.3.6"
                    port = 8081
                    path("ws/music/$roomId")
                }
                authToken?.let {
                    header(HttpHeaders.Authorization, "Bearer $it")
                }
            }

            job = CoroutineScope(Dispatchers.IO).launch {
                _connectionState.value = ConnectionState.CONNECTED

                try {
                    session?.incoming?.consumeAsFlow()?.collect { frame ->
                        if (frame is Frame.Text) {
                            val message = frame.readText()
                            handleMusicMessage(message)
                        }
                    }
                } catch (e: Exception) {
                    _connectionState.value = ConnectionState.ERROR(e.message ?: "Music connection error")
                }
            }
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.ERROR(e.message ?: "Music connection failed")
        }
    }

    suspend fun sendCommand(command: String) {
        try {
            session?.send(Frame.Text(command))
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.ERROR("Send failed: ${e.message}")
        }
    }

    private fun handleMusicMessage(message: String) {
        when {
            message.startsWith("track:") -> {
                val trackInfo = message.removePrefix("track:")
                // Handle track info
            }
            message.startsWith("playback:") -> {
                _isPlaying.value = message.removePrefix("playback:") == "play"
            }
            message.startsWith("progress:") -> {
                val progress = message.removePrefix("progress:").toIntOrNull()
                progress?.let { _currentTime.value = it }
            }
            message.startsWith("participants:") -> {
                val participants = message.removePrefix("participants:").split(",")
                _participantUpdates.value = participants
            }
            else -> _messages.add(message)
        }
    }

    private fun handleMessage(message: String) {
        when {
            message.startsWith("duration:") -> {
                _roomDuration.value = message.removePrefix("duration:").toIntOrNull() ?: 0
                _remainingTime.value = _roomDuration.value
                _serverTime.value = _roomDuration.value
            }
            message == "play" -> {
                _isPlaying.value = true
            }
            message == "pause" -> {
                _isPlaying.value = false
            }
            message.startsWith("participants:") -> {
                val participants = message.removePrefix("participants:").split(",")
                _participantUpdates.value = participants
            }
            message.startsWith("duration:") -> {
                val duration = message.removePrefix("duration:").toIntOrNull() ?: 0
                _roomDuration.value = duration
                _serverTime.value = duration
                _remainingTime.value = duration
            }
            message.startsWith("time:") -> {
                val time = message.removePrefix("time:").toIntOrNull() ?: 0
                _serverTime.value = time
                _remainingTime.value = time
            }
            message.startsWith("participant:") -> {
                val participant = message.removePrefix("participant:")
                if (!_participants.contains(participant)) {
                    _participants.add(participant)
                }
            }
            message == "completed" -> {
                _isPlaying.value = false
                _remainingTime.value = 0
                _serverTime.value = 0
            }
            else -> _messages.add(message)
        }
    }

    fun disconnect() {
        job?.cancel()
        timerJob?.cancel()
        runBlocking {
            session?.close()
            messageChannel.close()
        }
        session = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _participants.clear()
        _messages.clear()
        _isPlaying.value = false
        _currentTime.value = 0
        _roomDuration.value = 0
        _remainingTime.value = 0
        _serverTime.value = 0
    }

    suspend fun requestParticipantsUpdate() {
        sendCommand("get_participants")
    }
}