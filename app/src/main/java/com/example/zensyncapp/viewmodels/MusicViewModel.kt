package com.example.zensyncapp.viewmodels

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.zensyncapp.ApiClient
import com.example.zensyncapp.WebSocketService
import com.example.zensyncapp.models.CreateMusicRoomRequest
import com.example.zensyncapp.models.MusicRoom
import com.example.zensyncapp.models.SpotifyPlaylist
import com.example.zensyncapp.network.WebSocketManager
import com.example.zensyncapp.spotify.SpotifyManager
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val _rooms = MutableStateFlow<List<MusicRoom>>(emptyList())
    val rooms: StateFlow<List<MusicRoom>> = _rooms

    private val spotifyManager = SpotifyManager(application.applicationContext)
    var isSpotifyConnected by mutableStateOf(false)

    private val _selectedPlaylist = MutableStateFlow<SpotifyPlaylist?>(null)
    val selectedPlaylist: StateFlow<SpotifyPlaylist?> = _selectedPlaylist

    private val _showPlaylistSelector = MutableStateFlow(false)
    val showPlaylistSelector: StateFlow<Boolean> = _showPlaylistSelector

    private val _currentRoom = MutableStateFlow<MusicRoom?>(null)
    val currentRoom: StateFlow<MusicRoom?> = _currentRoom

    private val _spotifyPlaylists = MutableStateFlow<List<SpotifyPlaylist>>(emptyList())
    val spotifyPlaylists: StateFlow<List<SpotifyPlaylist>> = _spotifyPlaylists

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _navigateToRoom = MutableStateFlow<String?>(null)
    val navigateToRoom: StateFlow<String?> = _navigateToRoom

    private val _roomParticipants = MutableStateFlow<List<String>>(emptyList())
    val roomParticipants: StateFlow<List<String>> = _roomParticipants

    private val _refreshInterval = MutableStateFlow(5000L)
    private var refreshJob: Job? = null

    private val _newParticipantNotification = MutableStateFlow<String?>(null)
    val newParticipantNotification: StateFlow<String?> = _newParticipantNotification

    private var webSocketManager: WebSocketManager? = null

    init {
        fetchRooms()
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

    fun setWebSocketManager(manager: WebSocketManager) {
        webSocketManager = manager
        setupWebSocketListeners(manager)
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
            webSocketManager.newParticipantNotification.collect { notification ->
                _newParticipantNotification.value = notification
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun connectSpotify() {
        spotifyManager.connect { success ->
            isSpotifyConnected = success
        }
    }

    fun showSpotifyPlaylistSelector() {
        _showPlaylistSelector.value = true
        fetchSpotifyPlaylists()
    }

    fun selectPlaylist(playlist: SpotifyPlaylist) {
        _selectedPlaylist.value = playlist
        _showPlaylistSelector.value = false
    }

    fun dismissPlaylistSelector() {
        _showPlaylistSelector.value = false
    }

    fun fetchRooms() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = ApiClient.httpClient.get("/api/music/rooms")
                if (response.status == HttpStatusCode.OK) {
                    _rooms.value = response.body<List<MusicRoom>>()
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load rooms"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fetchRoomDetails(roomId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = ApiClient.httpClient.get("/api/music/rooms/$roomId")
                if (response.status == HttpStatusCode.OK) {
                    _currentRoom.value = response.body()
                } else if (response.status == HttpStatusCode.NotFound) {
                    _error.value = "Room not found"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load room details"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fetchSpotifyPlaylists() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _spotifyPlaylists.value = listOf(
                    SpotifyPlaylist(
                        id = "37i9dQZF1DXc5e2bJhV6pu",
                        name = "Morning Energy",
                        trackCount = 25,
                        durationMs = 3600000
                    ),
                    SpotifyPlaylist(
                        id = "37i9dQZF1DX3rxVfibe1L0",
                        name = "Peaceful Meditation",
                        trackCount = 30,
                        durationMs = 5400000
                    )
                )
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load playlists"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createMusicRoom(name: String, playlist: SpotifyPlaylist, duration: Int, isPublic: Boolean = true) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = ApiClient.httpClient.post("/api/music/rooms") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        CreateMusicRoomRequest(
                            name = name,
                            playlistId = playlist.id,
                            playlistName = playlist.name,
                            duration = duration,
                            isPublic = isPublic
                        )
                    )
                }

                when (response.status) {
                    HttpStatusCode.Created -> {
                        val room = response.body<MusicRoom>()
                        _currentRoom.value = room
                        _navigateToRoom.value = room.id
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

    fun onRoomNavigated() {
        _navigateToRoom.value = null
    }

    fun joinMusicRoom(roomId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = ApiClient.httpClient.post("/api/music/rooms/$roomId/join")
                if (response.status == HttpStatusCode.OK) {
                    fetchRoomDetails(roomId)
                    val token = ApiClient.getAuthToken()
                    WebSocketService.startService(
                        getApplication(),
                        roomId,
                        "music",
                        token
                    )
                } else {
                    val errorText = response.bodyAsText()
                    _error.value = errorText ?: "Failed to join room"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to join room"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun leaveRoom(roomId: String) {
        viewModelScope.launch {
            try {
                ApiClient.httpClient.post("/api/music/rooms/$roomId/leave") {
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

    fun cleanupOldRooms() {
        viewModelScope.launch {
            try {
                val response = ApiClient.httpClient.delete("/api/music/rooms/cleanup")
                if (response.status == HttpStatusCode.OK) {
                    fetchRooms()
                }
            } catch (e: Exception) {
                _error.value = "Failed to cleanup rooms: ${e.message}"
            }
        }
    }

    fun clearRooms() {
        viewModelScope.launch {
            _rooms.value = emptyList()
        }
    }
}