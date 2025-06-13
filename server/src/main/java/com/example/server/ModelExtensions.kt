package com.example.zensyncserver.extensions

import com.example.zensyncapp.models.MeditationRoom
import com.example.zensyncapp.models.MusicRoom
import com.example.zensyncapp.models.SpotifyPlaylist
import com.example.zensyncserver.MeditationRooms
import com.example.zensyncserver.MusicRooms
import org.jetbrains.exposed.sql.ResultRow

fun ResultRow.toMeditationRoom(): MeditationRoom {
    return MeditationRoom(
        id = this[MeditationRooms.id].toString(),
        name = this[MeditationRooms.name],
        creator = "TODO", // Получить из Users
        duration = this[MeditationRooms.durationMinutes],
        participants = 0, // Получить из RoomParticipants
        goal = this[MeditationRooms.goal],
        isPublic = this[MeditationRooms.isPublic]
    )
}

fun ResultRow.toMusicRoom(): MusicRoom {
    return MusicRoom(
        id = this[MusicRooms.id].toString(),
        name = this[MusicRooms.name],
        creator = "TODO", // Получить из Users
        playlist = SpotifyPlaylist(
            id = this[MusicRooms.spotifyPlaylistId],
            name = this[MusicRooms.spotifyPlaylistName]
        ),
        participants = 0, // Получить из RoomParticipants
        duration = this[MusicRooms.durationMinutes],
        isPublic = this[MusicRooms.isPublic]
    )
}