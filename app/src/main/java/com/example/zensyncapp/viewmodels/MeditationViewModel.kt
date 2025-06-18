package com.example.zensyncapp.viewmodels

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.example.zensyncapp.ApiClient
import com.example.zensyncapp.models.MeditationRoom
import com.example.zensyncapp.models.CreateMeditationRoomRequest
import com.example.zensyncapp.network.WebSocketManager
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MeditationViewModel(application: Application) : BaseRoomViewModel(application) {
    private val _rooms = MutableStateFlow<List<MeditationRoom>>(emptyList())
    val rooms: StateFlow<List<MeditationRoom>> = _rooms

    private val _currentRoom = MutableStateFlow<MeditationRoom?>(null)
    val currentRoom: StateFlow<MeditationRoom?> = _currentRoom

    private val _timerText = MutableStateFlow("0:00")
    val timerText: StateFlow<String> = _timerText

    private val _showTimerControls = MutableStateFlow(false)
    val showTimerControls: StateFlow<Boolean> = _showTimerControls

    private val _showCompletionDialog = MutableStateFlow(false)
    val showCompletionDialog: StateFlow<Boolean> = _showCompletionDialog

    init {
        fetchRooms()
    }

    override fun setupWebSocketListeners(webSocketManager: WebSocketManager) {
        viewModelScope.launch {
            _webSocketManager?.timerState?.collect { timerState ->
                _showTimerControls.value = true
                _timerText.value = formatTime(timerState.currentTime)

                if (timerState.currentTime <= 0 && timerState.isPlaying) {
                    webSocketManager.pauseMeditation()
                    _showCompletionDialog.value = true
                }
            }
        }

        viewModelScope.launch {
            webSocketManager.participantUpdates.collect { participants ->
                _roomParticipants.value = participants
                _currentRoom.value?.let { currentRoom ->
                    _currentRoom.value = currentRoom.copy(participants = participants.size)
                }
            }
        }

        viewModelScope.launch {
            webSocketManager.newParticipantNotification.collect { notification ->
                notification?.let { handleNewParticipant(it) }
            }
        }
    }

    fun fetchRooms() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = ApiClient.httpClient.get("/api/meditation/rooms")
                if (response.status == HttpStatusCode.OK) {
                    _rooms.value = response.body()
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to fetch rooms"
            } finally {
                _isLoading.value = false
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

                if (response.status == HttpStatusCode.Created) {
                    val room = response.body<MeditationRoom>()
                    _currentRoom.value = room
                    joinRoom(room.id)
                    fetchRooms()
                } else {
                    _error.value = response.bodyAsText() ?: "Failed to create room"
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
                val response = ApiClient.httpClient.post("/api/meditation/rooms/$roomId/join")
                if (response.status == HttpStatusCode.OK) {
                    val roomResponse = ApiClient.httpClient.get("/api/meditation/rooms/$roomId")
                    if (roomResponse.status == HttpStatusCode.OK) {
                        _currentRoom.value = roomResponse.body()
                        _navigateToRoom.value = roomId
                    }
                } else {
                    _error.value = response.bodyAsText() ?: "Failed to join room"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to join room"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun startMeditation(duration: Int) {
        viewModelScope.launch {
            _webSocketManager?.startMeditation(duration * 60)
        }
    }

    fun toggleMeditation() {
        viewModelScope.launch {
            _webSocketManager?.toggleMeditation()
        }
    }

    fun dismissCompletionDialog() {
        _showCompletionDialog.value = false
    }

    private fun formatTime(seconds: Int): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }

    fun clearAllRooms() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val success = ApiClient.clearAllMeditationRooms()
                if (success) {
                    _rooms.value = emptyList()
                }
            } catch (e: Exception) {
                _error.value = "Failed to clear rooms: ${e.message}"
            } finally {
                _isLoading.value = false
            }

        }
    }
}