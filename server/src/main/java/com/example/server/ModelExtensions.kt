package com.example.zensyncserver.extensions

import com.example.zensyncapp.models.MeditationRoom
import com.example.zensyncapp.models.MusicRoom
import com.example.zensyncapp.models.SpotifyPlaylist
import com.example.zensyncserver.MeditationRooms
import com.example.zensyncserver.MusicRooms
import org.jetbrains.exposed.sql.ResultRow

fun ResultRow.toMeditationRoom(): MeditationRoom {
    return MeditationRoom(
        id = this[MeditationRooms.id].value.toString(),
        name = this[MeditationRooms.name],
        creator = "User",
        duration = this[MeditationRooms.durationMinutes],
        participants = 1, // По умолчанию ток создатель
        goal = this[MeditationRooms.goal],
        isPublic = this[MeditationRooms.isPublic]
    )
}

fun ResultRow.toMusicRoom(): MusicRoom {
    return MusicRoom(
        id = this[MusicRooms.id].value.toString(),
        name = this[MusicRooms.name],
        creator = "User",
        playlist = SpotifyPlaylist(
            id = this[MusicRooms.spotifyPlaylistId],
            name = this[MusicRooms.spotifyPlaylistName]
        ),
        participants = 1, // По умолчанию создатель
        duration = this[MusicRooms.durationMinutes],
        isPublic = this[MusicRooms.isPublic]
    )
}