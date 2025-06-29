package com.example.zensyncapp.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.zensyncapp.ApiClient
import com.example.zensyncapp.WebSocketService
import com.example.zensyncapp.network.WebSocketManager
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

abstract class BaseRoomViewModel(application: Application) : AndroidViewModel(application) {
    protected val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    protected val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    protected val _navigateToRoom = MutableStateFlow<String?>(null)
    val navigateToRoom: StateFlow<String?> = _navigateToRoom

    protected val _roomParticipants = MutableStateFlow<List<String>>(emptyList())
    val roomParticipants: StateFlow<List<String>> = _roomParticipants

    protected val _newParticipantNotification = MutableStateFlow<String?>(null)
    val newParticipantNotification: StateFlow<String?> = _newParticipantNotification

    internal var _webSocketManager: WebSocketManager? = null

    fun setupWebSocketManager(manager: WebSocketManager) {
        _webSocketManager = manager
        setupWebSocketListeners(manager)
    }

    protected abstract fun setupWebSocketListeners(webSocketManager: WebSocketManager)

    fun leaveRoom(roomId: String, roomType: String) {
        viewModelScope.launch {
            try {
                ApiClient.httpClient.post("/api/$roomType/rooms/$roomId/leave") {
                    contentType(ContentType.Application.Json)
                }
                WebSocketService.stopService(getApplication())
                _navigateToRoom.value = null
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to leave room"
            }
        }
    }

    fun onRoomNavigated() {
        _navigateToRoom.value = null
    }

    protected fun handleNewParticipant(username: String) {
        _newParticipantNotification.value = "$username присоединился"
        viewModelScope.launch {
            delay(3000)
            _newParticipantNotification.value = null
        }
    }
}