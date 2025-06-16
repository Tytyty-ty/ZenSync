package com.example.zensyncapp.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.zensyncapp.ApiClient
import com.example.zensyncapp.models.MeditationRoom
import com.example.zensyncapp.models.CreateMeditationRoomRequest
import com.example.zensyncapp.WebSocketService
import com.example.zensyncapp.network.WebSocketManager
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MeditationViewModel(application: Application) : AndroidViewModel(application) {
    private val _rooms = MutableStateFlow<List<MeditationRoom>>(emptyList())
    val rooms: StateFlow<List<MeditationRoom>> = _rooms

    private val _currentRoom = MutableStateFlow<MeditationRoom?>(null)
    val currentRoom: StateFlow<MeditationRoom?> = _currentRoom

    private val _navigateToRoom = MutableStateFlow<String?>(null)
    val navigateToRoom: StateFlow<String?> = _navigateToRoom

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _navigationEvent = MutableStateFlow<String?>(null)
    val navigationEvent: StateFlow<String?> = _navigationEvent

    private val _roomParticipants = MutableStateFlow<List<String>>(emptyList())
    val roomParticipants: StateFlow<List<String>> = _roomParticipants

    private val _refreshInterval = MutableStateFlow(5000L)
    private var refreshJob: Job? = null

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _showTimerControls = MutableStateFlow(false)
    val showTimerControls: StateFlow<Boolean> = _showTimerControls

    private val _timerText = MutableStateFlow("0:00")
    val timerText: StateFlow<String> = _timerText

    private val _newParticipantNotification = MutableStateFlow<String?>(null)
    val newParticipantNotification: StateFlow<String?> = _newParticipantNotification

    private var webSocketManager: WebSocketManager? = null

    fun setWebSocketManager(manager: WebSocketManager) {
        webSocketManager = manager
        setupWebSocketListeners(manager)
    }

    init {
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                delay(_refreshInterval.value)
                fetchRooms()
            }
        }
    }

    fun setRefreshInterval(interval: Long) {
        _refreshInterval.value = interval
        startAutoRefresh()
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
    }

    fun setupWebSocketListeners(webSocketManager: WebSocketManager) {
        viewModelScope.launch {
            webSocketManager.participantUpdates.collect { participants ->
                _roomParticipants.value = participants
                _currentRoom.value?.let { currentRoom ->
                    _currentRoom.value = currentRoom.copy(participants = participants.size)
                }
            }
        }

        viewModelScope.launch {
            webSocketManager.isPlaying.collect { isPlaying ->
                _showTimerControls.value = isPlaying
            }
        }

        viewModelScope.launch {
            webSocketManager.serverTime.collect { seconds ->
                val minutes = seconds / 60
                val remainingSeconds = seconds % 60
                _timerText.value = String.format("%d:%02d", minutes, remainingSeconds)
            }
        }

        viewModelScope.launch {
            webSocketManager.newParticipantNotification.collect { notification ->
                _newParticipantNotification.value = notification
            }
        }
    }

    fun onNavigationHandled() {
        _navigationEvent.value = null
    }

    fun onRoomNavigated() {
        _navigateToRoom.value = null
    }

    fun fetchRooms(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            if (forceRefresh) {
                _isRefreshing.value = true
            }
            try {
                val response = ApiClient.httpClient.get("/api/meditation/rooms")
                if (response.status == HttpStatusCode.OK) {
                    _rooms.value = response.body()
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to fetch rooms"
            } finally {
                _isLoading.value = false
                _isRefreshing.value = false
            }
        }
    }

    fun createRoom(name: String, duration: Int, goal: String, isPublic: Boolean = true) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = ApiClient.httpClient.post("/api/meditation/rooms") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateMeditationRoomRequest(name, duration, goal, isPublic))
                }

                when (response.status) {
                    HttpStatusCode.Created -> {
                        val room = response.body<MeditationRoom>()
                        _currentRoom.value = room
                        joinRoom(room.id)
                        _navigationEvent.value = room.id
                        fetchRooms()
                    }
                    else -> {
                        val errorText = response.bodyAsText()
                        _error.value = errorText ?: "Failed to create room"
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to create room"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun joinRoom(roomId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = ApiClient.httpClient.post("/api/meditation/rooms/$roomId/join") {
                    contentType(ContentType.Application.Json)
                }

                when (response.status) {
                    HttpStatusCode.OK -> {
                        val roomResponse = ApiClient.httpClient.get("/api/meditation/rooms/$roomId")
                        if (roomResponse.status == HttpStatusCode.OK) {
                            _currentRoom.value = roomResponse.body()
                        }
                        val token = ApiClient.getAuthToken()
                        WebSocketService.startService(
                            getApplication(),
                            roomId,
                            "meditation",
                            token
                        )
                    }
                    else -> {
                        val errorText = response.bodyAsText()
                        _error.value = errorText ?: "Failed to join room"
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to join room"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun startMeditation(roomId: String, duration: Int) {
        viewModelScope.launch {
            try {
                val response = ApiClient.httpClient.post("/api/meditation/rooms/$roomId/start") {
                    contentType(ContentType.Application.Json)
                    setBody(duration)
                }
                if (response.status == HttpStatusCode.OK) {
                    _showTimerControls.value = true
                    webSocketManager?.sendCommand("duration:${duration * 60}")
                    webSocketManager?.sendCommand("time:${duration * 60}")
                    webSocketManager?.sendCommand("play")
                    // Инициализируем таймер в ViewModel
                    _timerText.value = formatTime(duration * 60)
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to start meditation"
            }
        }
    }

    private fun formatTime(seconds: Int): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }

    fun cleanupOldRooms() {
        viewModelScope.launch {
            try {
                val response = ApiClient.httpClient.delete("/api/meditation/rooms/cleanup")
                if (response.status == HttpStatusCode.OK) {
                    fetchRooms()
                }
            } catch (e: Exception) {
                _error.value = "Failed to cleanup rooms: ${e.message}"
            }
        }
    }

    fun leaveRoom(roomId: String) {
        viewModelScope.launch {
            try {
                ApiClient.httpClient.post("/api/meditation/rooms/$roomId/leave") {
                    contentType(ContentType.Application.Json)
                }
                _currentRoom.value = null
                WebSocketService.stopService(getApplication())
                fetchRooms()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to leave room"
            }
        }
    }

    fun toggleMeditation(roomId: String) {
        viewModelScope.launch {
            if (webSocketManager?.isPlaying?.value == true) {
                webSocketManager?.sendCommand("pause")
            } else {
                webSocketManager?.sendCommand("play")
            }
        }
    }
}