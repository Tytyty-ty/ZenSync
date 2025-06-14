package com.example.zensyncserver.routes

import com.example.zensyncapp.models.CreateMusicRoomRequest
import com.example.zensyncapp.models.MusicRoom
import com.example.zensyncapp.models.SpotifyPlaylist
import com.example.zensyncserver.MusicRooms
import com.example.zensyncserver.Users
import com.example.zensyncserver.extensions.toMusicRoom
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

fun Route.musicRoutes() {
    route("/api/music") {
        get("/rooms") {
            val rooms = transaction {
                MusicRooms.selectAll().map { it.toMusicRoom() }
            }
            call.respond(rooms)
        }

        post("/rooms") {
            val request = call.receive<CreateMusicRoomRequest>()
            val userId = call.request.headers["X-User-Id"]?.toIntOrNull() ?: 1 // Временное решение

            val roomId = transaction {
                MusicRooms.insert {
                    it[name] = request.name
                    it[creatorId] = userId
                    it[spotifyPlaylistId] = request.playlistId
                    it[spotifyPlaylistName] = request.playlistName
                    it[durationMinutes] = request.duration
                    it[isPublic] = request.isPublic
                    it[Users.createdAt] = LocalDateTime.now()
                } get MusicRooms.id
            }.value

            call.respond(HttpStatusCode.Created, mapOf("id" to roomId.toString()))
        }

        get("/rooms/{id}") {
            val roomId = call.parameters["id"]?.toIntOrNull() ?: run {
                call.respond(HttpStatusCode.BadRequest, "Invalid room ID")
                return@get
            }

            val room = transaction {
                MusicRooms.select { MusicRooms.id eq roomId }.singleOrNull()?.toMusicRoom()
            }

            room?.let { call.respond(it) } ?: call.respond(HttpStatusCode.NotFound)
        }

        post("/rooms/{id}/join") {
            val roomId = call.parameters["id"]?.toIntOrNull() ?: run {
                call.respond(HttpStatusCode.BadRequest, "Invalid room ID")
                return@post
            }

            val userId = call.request.headers["X-User-Id"]?.toIntOrNull() ?: 1 // Временное решение

            // Здесь должна быть логика добавления участника
            call.respond(HttpStatusCode.OK, mapOf("message" to "Joined successfully"))
        }
    }
}