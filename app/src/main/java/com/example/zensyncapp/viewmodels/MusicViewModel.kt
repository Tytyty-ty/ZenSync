package com.example.zensyncapp.viewmodels

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.example.zensyncapp.ApiClient
import com.example.zensyncapp.models.*
import com.example.zensyncapp.network.WebSocketManager
import com.example.zensyncapp.spotify.SpotifyManager
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MusicViewModel(application: Application) : BaseRoomViewModel(application) {
    private val _rooms = MutableStateFlow<List<MusicRoom>>(emptyList())
    val rooms: StateFlow<List<MusicRoom>> = _rooms

    private val _currentRoom = MutableStateFlow<MusicRoom?>(null)
    val currentRoom: StateFlow<MusicRoom?> = _currentRoom

    private val _spotifyPlaylists = MutableStateFlow<List<SpotifyPlaylist>>(emptyList())
    val spotifyPlaylists: StateFlow<List<SpotifyPlaylist>> = _spotifyPlaylists

    private val _selectedPlaylist = MutableStateFlow<SpotifyPlaylist?>(null)
    val selectedPlaylist: StateFlow<SpotifyPlaylist?> = _selectedPlaylist

    private val _showPlaylistSelector = MutableStateFlow(false)
    val showPlaylistSelector: StateFlow<Boolean> = _showPlaylistSelector

    var isSpotifyConnected by mutableStateOf(false)
    private val spotifyManager = SpotifyManager(application.applicationContext)

    init {
        fetchRooms()
    }

    override fun setupWebSocketListeners(webSocketManager: WebSocketManager) {
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
                val response = ApiClient.httpClient.get("/api/music/rooms")
                if (response.status == HttpStatusCode.OK) {
                    _rooms.value = response.body()
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
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load room details"
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
                    setBody(CreateMusicRoomRequest(name, playlist.id, playlist.name, duration, isPublic))
                }

                when (response.status) {
                    HttpStatusCode.Created -> {
                        val room = response.body<MusicRoom>()
                        _currentRoom.value = room
                        _navigateToRoom.value = room.id
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

    fun joinMusicRoom(roomId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = ApiClient.httpClient.post("/api/music/rooms/$roomId/join")
                if (response.status == HttpStatusCode.OK) {
                    fetchRoomDetails(roomId)
                    _navigateToRoom.value = roomId
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

    private fun fetchSpotifyPlaylists() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Здесь должна быть реальная загрузка плейлистов из Spotify API
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

    fun clearAllRooms() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val success = ApiClient.clearAllMusicRooms()
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

    fun fetchRooms(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = ApiClient.httpClient.get("/api/music/rooms") {
                    if (forceRefresh) {
                        parameter("force", "true")
                    }
                }
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
}