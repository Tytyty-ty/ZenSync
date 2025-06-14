package com.example.zensyncserver.routes

import com.example.zensyncapp.models.CreateMusicRoomRequest
import com.example.zensyncapp.models.MusicRoom
import com.example.zensyncapp.models.SpotifyPlaylist
import com.example.zensyncserver.*
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
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import java.time.LocalDateTime

fun Route.musicRoutes() {
    route("/api/music") {
        get("/rooms") {
            val rooms = transaction {
                MusicRooms
                    .join(Users, JoinType.INNER, MusicRooms.creatorId, Users.id)
                    .selectAll()
                    .map {
                        val participantsCount = RoomParticipants
                            .select {
                                RoomParticipants.roomId eq it[MusicRooms.id].value and
                                        (RoomParticipants.roomType eq "music")
                            }
                            .count()

                        MusicRoom(
                            id = it[MusicRooms.id].value.toString(),
                            name = it[MusicRooms.name],
                            creator = it[Users.username],
                            playlist = SpotifyPlaylist(
                                id = it[MusicRooms.spotifyPlaylistId],
                                name = it[MusicRooms.spotifyPlaylistName]
                            ),
                            participants = participantsCount.toInt(),
                            duration = it[MusicRooms.durationMinutes],
                            isPublic = it[MusicRooms.isPublic]
                        )
                    }
            }
            call.respond(rooms)
        }

        post("/rooms") {
            val request = call.receive<CreateMusicRoomRequest>()
            val userId = call.request.headers["X-User-Id"]?.toIntOrNull() ?: run {
                call.respond(HttpStatusCode.Unauthorized, "User ID is required")
                return@post
            }

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

            transaction {
                RoomParticipants.insert {
                    it[RoomParticipants.roomId] = roomId
                    it[RoomParticipants.roomType] = "music"
                    it[RoomParticipants.userId] = userId
                    it[RoomParticipants.joinedAt] = LocalDateTime.now()
                }
            }

            val room = transaction {
                MusicRooms
                    .join(Users, JoinType.INNER, MusicRooms.creatorId, Users.id)
                    .select { MusicRooms.id eq roomId }
                    .map {
                        val participantsCount = RoomParticipants
                            .select {
                                RoomParticipants.roomId eq roomId and
                                        (RoomParticipants.roomType eq "music")
                            }
                            .count()

                        MusicRoom(
                            id = it[MusicRooms.id].value.toString(),
                            name = it[MusicRooms.name],
                            creator = it[Users.username],
                            playlist = SpotifyPlaylist(
                                id = it[MusicRooms.spotifyPlaylistId],
                                name = it[MusicRooms.spotifyPlaylistName]
                            ),
                            participants = participantsCount.toInt(),
                            duration = it[MusicRooms.durationMinutes],
                            isPublic = it[MusicRooms.isPublic]
                        )
                    }
                    .singleOrNull()
            }

            room?.let { call.respond(HttpStatusCode.Created, it) }
                ?: call.respond(HttpStatusCode.InternalServerError, "Failed to create room")
        }

        get("/rooms/{id}") {
            val roomId = call.parameters["id"]?.toIntOrNull() ?: run {
                call.respond(HttpStatusCode.BadRequest, "Invalid room ID")
                return@get
            }

            val room = transaction {
                MusicRooms
                    .join(Users, JoinType.INNER, MusicRooms.creatorId, Users.id)
                    .select { MusicRooms.id eq roomId }
                    .map {
                        val participantsCount = RoomParticipants
                            .select {
                                RoomParticipants.roomId eq roomId and
                                        (RoomParticipants.roomType eq "music")
                            }
                            .count()

                        MusicRoom(
                            id = it[MusicRooms.id].value.toString(),
                            name = it[MusicRooms.name],
                            creator = it[Users.username],
                            playlist = SpotifyPlaylist(
                                id = it[MusicRooms.spotifyPlaylistId],
                                name = it[MusicRooms.spotifyPlaylistName]
                            ),
                            participants = participantsCount.toInt(),
                            duration = it[MusicRooms.durationMinutes],
                            isPublic = it[MusicRooms.isPublic]
                        )
                    }
                    .singleOrNull()
            }

            room?.let { call.respond(it) } ?: call.respond(HttpStatusCode.NotFound)
        }

        post("/rooms/{id}/join") {
            val roomId = call.parameters["id"]?.toIntOrNull() ?: run {
                call.respond(HttpStatusCode.BadRequest, "Invalid room ID")
                return@post
            }

            val userId = call.request.headers["X-User-Id"]?.toIntOrNull() ?: run {
                call.respond(HttpStatusCode.Unauthorized, "User ID is required")
                return@post
            }

            transaction {
                RoomParticipants.insert {
                    it[RoomParticipants.roomId] = roomId
                    it[RoomParticipants.roomType] = "music"
                    it[RoomParticipants.userId] = userId
                    it[RoomParticipants.joinedAt] = LocalDateTime.now()
                }
            }

            call.respond(HttpStatusCode.OK, mapOf("message" to "Joined successfully"))
        }
    }
}