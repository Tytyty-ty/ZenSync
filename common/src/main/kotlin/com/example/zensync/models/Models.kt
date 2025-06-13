package com.example.zensyncapp.models

import kotlinx.serialization.Serializable

@Serializable
data class AuthResponse(
    val userId: String,
    val username: String,
    val email: String,
    val token: String? = null
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
)

@Serializable
data class MeditationRoom(
    val id: String,
    val name: String,
    val creator: String,
    val duration: Int,
    val participants: Int,
    val goal: String? = null,
    val isPublic: Boolean
)

@Serializable
data class CreateMeditationRoomRequest(
    val name: String,
    val duration: Int,
    val goal: String,
    val isPublic: Boolean = true
)

@Serializable
data class MusicRoom(
    val id: String,
    val name: String,
    val creator: String,
    val playlist: SpotifyPlaylist?,
    val participants: Int,
    val duration: Int,
    val isPublic: Boolean = true
)

@Serializable
data class CreateMusicRoomRequest(
    val name: String,
    val playlistId: String,
    val playlistName: String,
    val duration: Int,
    val isPublic: Boolean = true
)

@Serializable
data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
    val trackCount: Int = 0,
    val durationMs: Long = 0L
)