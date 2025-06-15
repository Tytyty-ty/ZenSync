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

class WebSocketManager(private val client: HttpClient) {
    private var session: DefaultClientWebSocketSession? = null
    private var job: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _participants = mutableStateListOf<String>()
    val participants: SnapshotStateList<String> = _participants

    private val _currentTime = MutableStateFlow(0)
    val currentTime: StateFlow<Int> = _currentTime

    private val _messages = mutableStateListOf<String>()
    val messages: SnapshotStateList<String> = _messages

    sealed class ConnectionState {
        object CONNECTED : ConnectionState()
        object DISCONNECTED : ConnectionState()
        object CONNECTING : ConnectionState()
        data class ERROR(val message: String) : ConnectionState()
    }

    private val _connectionState = mutableStateOf<ConnectionState>(ConnectionState.DISCONNECTED)
    val connectionState: State<ConnectionState> = _connectionState

    suspend fun connectToMeditationRoom(roomId: String, authToken: String? = null) {
        try {
            _connectionState.value = ConnectionState.CONNECTING
            session?.close()

            session = client.webSocketSession {
                url {
                    protocol = URLProtocol.WS
                    host = "10.0.2.2"
                    port = 8081
                    path("ws/meditation/$roomId")
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
                            handleMessage(message)
                        }
                    }
                } catch (e: Exception) {
                    _connectionState.value = ConnectionState.ERROR(e.message ?: "Connection error")
                }
            }
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.ERROR(e.message ?: "Connection failed")
        }
    }

    suspend fun connectToMusicRoom(roomId: String, authToken: String? = null) {
        try {
            _connectionState.value = ConnectionState.CONNECTING
            session?.close()

            session = client.webSocketSession {
                url {
                    protocol = URLProtocol.WS
                    host = "10.0.2.2"
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
            else -> _messages.add(message)
        }
    }

    private fun handleMessage(message: String) {
        when {
            message.startsWith("play") -> _isPlaying.value = true
            message.startsWith("pause") -> _isPlaying.value = false
            message.startsWith("time:") -> {
                val time = message.removePrefix("time:").toIntOrNull()
                time?.let { _currentTime.value = it }
            }
            message.startsWith("participant:") -> {
                val participant = message.removePrefix("participant:")
                if (!_participants.contains(participant)) {
                    _participants.add(participant)
                }
            }
            else -> _messages.add(message)
        }
    }

    fun disconnect() {
        job?.cancel()
        runBlocking {
            session?.close()
        }
        session = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _participants.clear()
        _messages.clear()
        _isPlaying.value = false
        _currentTime.value = 0
    }
}