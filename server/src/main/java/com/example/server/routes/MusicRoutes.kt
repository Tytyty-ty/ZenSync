package com.example.server.routes

import com.example.zensyncapp.models.CreateMusicRoomRequest
import com.example.zensyncapp.models.MusicRoom
import com.example.zensyncapp.models.SpotifyPlaylist
import com.example.zensyncserver.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

fun Route.musicRoutes() {
    route("/api/music") {
        get("/rooms") {
            val rooms = transaction {
                MusicRooms.selectAll().map {
                    MusicRoom(
                        id = it[MusicRooms.id].value.toString(),
                        name = it[MusicRooms.name],
                        creator = "TODO", // Здесь нужно получить имя создателя из таблицы Users
                        playlist = SpotifyPlaylist(
                            id = it[MusicRooms.spotifyPlaylistId],
                            name = it[MusicRooms.spotifyPlaylistName]
                        ),
                        participants = 0, // TODO: нужно подсчитать участников
                        duration = it[MusicRooms.durationMinutes],
                        isPublic = it[MusicRooms.isPublic]
                    )
                }
            }
            call.respond(rooms)
        }

        post("/rooms") {
            val request = call.receive<CreateMusicRoomRequest>()

            val roomId = transaction {
                MusicRooms.insert {
                    it[name] = request.name
                    it[creatorId] = 1 // TODO: заменить на ID текущего пользователя
                    it[spotifyPlaylistId] = request.playlistId
                    it[spotifyPlaylistName] = request.playlistName
                    it[durationMinutes] = request.duration
                    it[isPublic] = request.isPublic
                    it[Users.createdAt] = LocalDateTime.now() // Исправлено: убрана ссылка на Users.createdAt
                } get MusicRooms.id
            }.value

            call.respond(HttpStatusCode.Created, mapOf("id" to roomId))
        }

        get("/rooms/{id}") {
            val roomId = call.parameters["id"]?.toIntOrNull() ?: run {
                call.respond(HttpStatusCode.BadRequest, "Invalid room ID")
                return@get
            }

            val room = transaction {
                MusicRooms.select { MusicRooms.id eq roomId }.singleOrNull()?.let {
                    MusicRoom(
                        id = it[MusicRooms.id].value.toString(),
                        name = it[MusicRooms.name],
                        creator = "TODO", // Здесь нужно получить имя создателя из таблицы Users
                        playlist = SpotifyPlaylist(
                            id = it[MusicRooms.spotifyPlaylistId],
                            name = it[MusicRooms.spotifyPlaylistName]
                        ),
                        participants = 0, // TODO: нужно подсчитать участников
                        duration = it[MusicRooms.durationMinutes],
                        isPublic = it[MusicRooms.isPublic]
                    )
                }
            }

            room?.let { call.respond(it) } ?: call.respond(HttpStatusCode.NotFound)
        }

        post("/rooms/{id}/join") {
            val roomId = call.parameters["id"]?.toIntOrNull() ?: run {
                call.respond(HttpStatusCode.BadRequest, "Invalid room ID")
                return@post
            }

            // TODO: добавить логику добавления пользователя в комнату
            call.respond(HttpStatusCode.OK, mapOf("message" to "Joined successfully"))
        }
    }
}