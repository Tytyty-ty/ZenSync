package com.example.zensyncapp.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.zensyncapp.ApiClient
import com.example.zensyncapp.models.MeditationRoom
import com.example.zensyncapp.models.CreateMeditationRoomRequest
import com.example.zensyncapp.WebSocketService
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MeditationViewModel(application: Application) : AndroidViewModel(application) {
    private val _rooms = MutableStateFlow<List<MeditationRoom>>(emptyList())
    val rooms: StateFlow<List<MeditationRoom>> = _rooms

    private val _currentRoom = MutableStateFlow<MeditationRoom?>(null)
    val currentRoom: StateFlow<MeditationRoom?> = _currentRoom

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun fetchRooms() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = ApiClient.httpClient.get("/api/meditation/rooms")
                if (response.status == HttpStatusCode.OK) {
                    _rooms.value = response.body()
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun joinRoom(roomId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val token = ApiClient.getAuthToken()
                WebSocketService.startService(
                    getApplication(),
                    roomId,
                    "meditation",
                    token
                )

                val response = ApiClient.httpClient.post("/api/meditation/rooms/$roomId/join")
                if (response.status == HttpStatusCode.OK) {
                    _currentRoom.value = response.body()
                }
            } catch (e: Exception) {
                _error.value = e.message
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
                    val roomId = response.body<Map<String, String>>()["id"]
                    roomId?.let {
                        // После создания получаем полные данные комнаты
                        val roomResponse = ApiClient.httpClient.get("/api/meditation/rooms/$it")
                        if (roomResponse.status == HttpStatusCode.OK) {
                            _currentRoom.value = roomResponse.body()
                            joinRoom(it)
                        }
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
}