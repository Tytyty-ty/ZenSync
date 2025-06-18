package com.example.zensyncapp.network

import androidx.compose.runtime.*
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

    private val _participantUpdates = MutableStateFlow<List<String>>(emptyList())
    val participantUpdates: StateFlow<List<String>> = _participantUpdates

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentTime = MutableStateFlow(0)
    val currentTime: StateFlow<Int> = _currentTime

    private val _timerState = MutableStateFlow(TimerState())
    val timerState: StateFlow<TimerState> = _timerState

    private val _newParticipantNotification = MutableStateFlow<String?>(null)
    val newParticipantNotification: StateFlow<String?> = _newParticipantNotification

    data class TimerState(
        val currentTime: Int = 0,
        val isPlaying: Boolean = false,
        val duration: Int = 0
    )

    suspend fun connectToRoom(
        roomId: String,
        roomType: String,
        authToken: String? = null,
        userId: String? = null,
        username: String? = null
    ) {
        try {
            session?.close()
            timerJob?.cancel()

            session = client.webSocketSession {
                url {
                    protocol = URLProtocol.WS
                    host = "192.168.3.6"
                    port = 8081
                    path("ws/$roomType/$roomId")
                }
                authToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                userId?.let { header("X-User-Id", it) }
                username?.let { header("X-Username", it) }
            }

            job = CoroutineScope(Dispatchers.IO).launch {
                launch {
                    session?.incoming?.consumeAsFlow()?.collect { frame ->
                        if (frame is Frame.Text) {
                            handleMessage(frame.readText(), roomType)
                        }
                    }
                }

                // Запрашиваем текущее состояние при подключении
                sendCommand("get_state")
                sendCommand("get_participants")
            }

            startTimerJob()
        } catch (e: Exception) {
            disconnect()
            throw e
        }
    }

    private fun startTimerJob() {
        timerJob?.cancel()
        timerJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                delay(1000)
                if (_timerState.value.isPlaying && _timerState.value.currentTime > 0) {
                    _timerState.value = _timerState.value.copy(
                        currentTime = _timerState.value.currentTime - 1
                    )
                    if (_timerState.value.currentTime <= 0) {
                        _timerState.value = _timerState.value.copy(isPlaying = false)
                        sendCommand("completed")
                    }
                }
            }
        }
    }

    private fun handleMessage(message: String, roomType: String) {
        when {
            message.startsWith("state:") -> {
                val parts = message.removePrefix("state:").split(",")
                if (parts.size >= 3) {
                    _timerState.value = TimerState(
                        currentTime = parts[0].toIntOrNull() ?: 0,
                        isPlaying = parts[1].toBoolean(),
                        duration = parts[2].toIntOrNull() ?: 0
                    )
                }
            }
            message == "play" -> _timerState.value = _timerState.value.copy(isPlaying = true)
            message == "pause" -> _timerState.value = _timerState.value.copy(isPlaying = false)
            message.startsWith("time:") -> {
                val time = message.removePrefix("time:").toIntOrNull() ?: 0
                _timerState.value = _timerState.value.copy(currentTime = time)
            }
            message.startsWith("participants:") -> {
                val participants = message.removePrefix("participants:").split(",")
                _participantUpdates.value = participants
            }
            message.startsWith("new_participant:") -> {
                val username = message.removePrefix("new_participant:")
                _newParticipantNotification.value = username
            }
            roomType == "music" && message.startsWith("playback:") -> {
                _isPlaying.value = message.removePrefix("playback:") == "play"
            }
        }
    }

    suspend fun sendCommand(command: String) {
        try {
            session?.send(Frame.Text(command))
        } catch (e: Exception) {
            disconnect()
            throw e
        }
    }

    suspend fun startMeditation(durationSeconds: Int) {
        _timerState.value = TimerState(
            currentTime = durationSeconds,
            duration = durationSeconds,
            isPlaying = true
        )
        sendCommand("duration:$durationSeconds")
        sendCommand("play")
    }

    suspend fun toggleMeditation() {
        if (_timerState.value.isPlaying) {
            pauseMeditation()
        } else {
            playMeditation()
        }
    }

    suspend fun playMeditation() {
        if (_timerState.value.currentTime <= 0) {
            _timerState.value = _timerState.value.copy(
                currentTime = _timerState.value.duration
            )
        }
        _timerState.value = _timerState.value.copy(isPlaying = true)
        sendCommand("play")
    }

    suspend fun pauseMeditation() {
        _timerState.value = _timerState.value.copy(isPlaying = false)
        sendCommand("pause")
    }

    suspend fun requestParticipantsUpdate() {
        sendCommand("get_participants")
    }

    fun disconnect() {
        job?.cancel()
        timerJob?.cancel()
        runBlocking {
            session?.close()
        }
        session = null
        _timerState.value = TimerState()
        _participantUpdates.value = emptyList()
        _isPlaying.value = false
        _currentTime.value = 0
        _newParticipantNotification.value = null
    }
}