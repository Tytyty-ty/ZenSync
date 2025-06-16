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

    private val _newParticipantNotification = MutableStateFlow<String?>(null)
    val newParticipantNotification: StateFlow<String?> = _newParticipantNotification

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
        username: String? = null
    ) {
        try {
            _connectionState.value = ConnectionState.CONNECTING
            session?.close()
            timerJob?.cancel()

            session = client.webSocketSession {
                // ... существующий код подключения ...
            }

            job = CoroutineScope(Dispatchers.IO).launch {
                _connectionState.value = ConnectionState.CONNECTED

                // Запрашиваем текущее состояние комнаты при подключении
                sendCommand("get_state")

                launch {
                    try {
                        session?.incoming?.consumeAsFlow()?.collect { frame ->
                            if (frame is Frame.Text) {
                                val message = frame.readText()
                                handleMessage(message)
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
    private suspend fun broadcast(message: String) {
        try {
            session?.send(Frame.Text(message))
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.ERROR("Failed to broadcast: ${e.message}")
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
                                handleMusicMessage(message)
                            }
                        }
                    } catch (e: Exception) {
                        _connectionState.value = ConnectionState.ERROR(e.message ?: "Music connection error")
                    }
                }

                launch {
                    for (message in messageChannel) {
                        handleMusicMessage(message)
                    }
                }
            }
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.ERROR(e.message ?: "Music connection failed")
        }
    }

    suspend fun sendCommand(command: String) {
        println("Sending command: $command") // Логирование исходящих команд
        try {
            session?.send(Frame.Text(command))
        } catch (e: Exception) {
            println("Failed to send command: ${e.message}")
            _connectionState.value = ConnectionState.ERROR("Send failed: ${e.message}")
        }
    }

    private fun handleMusicMessage(message: String) {
        when {
            message.startsWith("track:") -> {
                val trackInfo = message.removePrefix("track:")
                _messages.add("Now playing: $trackInfo")
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
                _participants.clear()
                _participants.addAll(participants)
            }
            message.startsWith("new_participant:") -> {
                val username = message.removePrefix("new_participant:")
                _newParticipantNotification.value = "$username присоединился"
                _participants.add(username)
                _participantUpdates.value = _participants.toList()
                // Автоматически скрываем уведомление через 3 секунды
                CoroutineScope(Dispatchers.IO).launch {
                    delay(3000)
                    _newParticipantNotification.value = null
                }
            }
            else -> _messages.add(message)
        }
    }

    private fun handleMessage(message: String) {
        println("Received message: $message")
        when {
            message.startsWith("duration:") -> {
                val duration = message.removePrefix("duration:").toIntOrNull() ?: 0
                _roomDuration.value = duration
                _remainingTime.value = duration
                _serverTime.value = duration
                CoroutineScope(Dispatchers.IO).launch {
                    broadcast("time:$duration")
                }
            }
            message == "play" -> {
                _isPlaying.value = true
                timerJob?.cancel()
                timerJob = CoroutineScope(Dispatchers.IO).launch {
                    while (_serverTime.value > 0 && _isPlaying.value) {
                        delay(1000)
                        _serverTime.value = _serverTime.value - 1
                        if (_serverTime.value <= 0) {
                            _isPlaying.value = false
                            sendCommand("completed")
                            break
                        }
                    }
                }
            }
            message == "pause" -> {
                _isPlaying.value = false
                timerJob?.cancel()
            }
            message.startsWith("state:") -> {
                // Обработка начального состояния комнаты
                val parts = message.removePrefix("state:").split(",")
                if (parts.size >= 3) {
                    _serverTime.value = parts[0].toIntOrNull() ?: 0
                    _isPlaying.value = parts[1].toBoolean()
                    _roomDuration.value = parts[2].toIntOrNull() ?: 0
                }
            }
            message == "get_participants" -> {
                CoroutineScope(Dispatchers.IO).launch {
                    sendCommand("get_participants")
                }
            }
            message.startsWith("time:") -> {
                _serverTime.value = message.removePrefix("time:").toIntOrNull() ?: 0
            }
            message.startsWith("participant:") -> {
                val participant = message.removePrefix("participant:")
                if (!_participants.contains(participant)) {
                    _participants.add(participant)
                    _participantUpdates.value = _participants.toList()
                }
            }
            message.startsWith("new_participant:") -> {
                val username = message.removePrefix("new_participant:")
                _newParticipantNotification.value = "$username присоединился"
                _participants.add(username)
                _participantUpdates.value = _participants.toList()
                // Автоматически скрываем уведомление через 3 секунды
                CoroutineScope(Dispatchers.IO).launch {
                    delay(3000)
                    _newParticipantNotification.value = null
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

    suspend fun startMeditation(durationSeconds: Int) {
        _serverTime.value = durationSeconds
        _roomDuration.value = durationSeconds
        sendCommand("duration:$durationSeconds")
        sendCommand("play")
    }

    suspend fun toggleMeditation() {
        if (_isPlaying.value) {
            pauseMeditation()
        } else {
            playMeditation()
        }
    }


    suspend fun playMeditation() {
        if (_serverTime.value <= 0) {
            _serverTime.value = _roomDuration.value
        }
        _isPlaying.value = true
        sendCommand("play")
    }

    suspend fun pauseMeditation() {
        _isPlaying.value = false
        sendCommand("pause")
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
        _newParticipantNotification.value = null
    }

    suspend fun requestParticipantsUpdate() {
        sendCommand("get_participants")
    }
}