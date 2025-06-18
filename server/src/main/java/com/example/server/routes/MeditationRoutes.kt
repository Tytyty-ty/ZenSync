package com.example.zensyncserver.routes

import com.example.zensyncapp.models.CreateMeditationRoomRequest
import com.example.zensyncapp.models.MeditationRoom
import com.example.zensyncserver.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

fun Route.meditationRoutes() {
    route("/api/meditation") {
        get("/rooms") {
            val rooms = transaction {
                MeditationRooms
                    .join(Users, JoinType.INNER, MeditationRooms.creatorId, Users.id)
                    .selectAll()
                    .map {
                        val participantsCount = RoomParticipants
                            .select {
                                RoomParticipants.roomId eq it[MeditationRooms.id].value and
                                        (RoomParticipants.roomType eq "meditation")
                            }
                            .count()

                        MeditationRoom(
                            id = it[MeditationRooms.id].value.toString(),
                            name = it[MeditationRooms.name],
                            creator = it[Users.username],
                            duration = it[MeditationRooms.durationMinutes],
                            participants = participantsCount.toInt(),
                            goal = it[MeditationRooms.goal],
                            isPublic = it[MeditationRooms.isPublic]
                        )
                    }
            }
            call.respond(rooms)
        }

        post("/rooms") {
            val request = call.receive<CreateMeditationRoomRequest>()
            val userId = call.request.headers["X-User-Id"]?.toIntOrNull() ?: run {
                call.respond(HttpStatusCode.Unauthorized, "User ID is required")
                return@post
            }

            val roomId = transaction {
                MeditationRooms.insert {
                    it[name] = request.name
                    it[creatorId] = userId
                    it[durationMinutes] = request.duration
                    it[goal] = request.goal
                    it[isPublic] = request.isPublic
                    it[createdAt] = LocalDateTime.now()
                } get MeditationRooms.id
            }.value

            transaction {
                RoomParticipants.insert {
                    it[RoomParticipants.roomId] = roomId
                    it[RoomParticipants.roomType] = "meditation"
                    it[RoomParticipants.userId] = userId
                    it[RoomParticipants.joinedAt] = LocalDateTime.now()
                }
            }

            val room = transaction {
                MeditationRooms
                    .join(Users, JoinType.INNER, MeditationRooms.creatorId, Users.id)
                    .select { MeditationRooms.id eq roomId }
                    .map {
                        val participantsCount = RoomParticipants
                            .select {
                                RoomParticipants.roomId eq roomId and
                                        (RoomParticipants.roomType eq "meditation")
                            }
                            .count()

                        MeditationRoom(
                            id = it[MeditationRooms.id].value.toString(),
                            name = it[MeditationRooms.name],
                            creator = it[Users.username],
                            duration = it[MeditationRooms.durationMinutes],
                            participants = participantsCount.toInt(),
                            goal = it[MeditationRooms.goal],
                            isPublic = it[MeditationRooms.isPublic]
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
                MeditationRooms
                    .join(Users, JoinType.INNER, MeditationRooms.creatorId, Users.id)
                    .select { MeditationRooms.id eq roomId }
                    .map {
                        val participantsCount = RoomParticipants
                            .select {
                                RoomParticipants.roomId eq roomId and
                                        (RoomParticipants.roomType eq "meditation")
                            }
                            .count()

                        MeditationRoom(
                            id = it[MeditationRooms.id].value.toString(),
                            name = it[MeditationRooms.name],
                            creator = it[Users.username],
                            duration = it[MeditationRooms.durationMinutes],
                            participants = participantsCount.toInt(),
                            goal = it[MeditationRooms.goal],
                            isPublic = it[MeditationRooms.isPublic]
                        )
                    }
                    .singleOrNull()
            }

            room?.let { call.respond(it) }
                ?: call.respond(HttpStatusCode.NotFound, "Room not found")
        }



        post("/rooms/{id}/start") {
            val roomId = call.parameters["id"]?.toIntOrNull() ?: run {
                call.respond(HttpStatusCode.BadRequest, "Invalid room ID")
                return@post
            }

            val duration = transaction {
                MeditationRooms.select { MeditationRooms.id eq roomId }
                    .singleOrNull()
                    ?.get(MeditationRooms.durationMinutes)
            } ?: run {
                call.respond(HttpStatusCode.NotFound, "Room not found")
                return@post
            }

            call.respond(HttpStatusCode.OK, mapOf(
                "duration" to duration,
                "message" to "Meditation started"
            ))
        }

        post("/rooms/{id}/join") {
            val roomId = call.parameters["id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
            val userId = call.request.headers["X-User-Id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.Unauthorized)

            transaction {
                RoomParticipants.deleteWhere {
                    (RoomParticipants.userId eq userId) and (RoomParticipants.roomType eq "meditation")
                }

                RoomParticipants.insert {
                    it[RoomParticipants.roomId] = roomId
                    it[RoomParticipants.roomType] = "meditation"
                    it[RoomParticipants.userId] = userId
                    it[RoomParticipants.joinedAt] = LocalDateTime.now()
                }
            }

            call.respond(HttpStatusCode.OK)
        }

        post("/rooms/{id}/start") {
            val roomId = call.parameters["id"]?.toIntOrNull() ?: run {
                call.respond(HttpStatusCode.BadRequest, "Invalid room ID")
                return@post
            }

            val duration = call.receive<Int>()

            transaction {
                MeditationRooms.update({ MeditationRooms.id eq roomId }) {
                    it[MeditationRooms.durationMinutes] = duration
                }
            }

            call.respond(HttpStatusCode.OK, mapOf(
                "duration" to duration,
                "message" to "Meditation started"
            ))
        }

        post("/rooms/{id}/leave") {
            val roomId = call.parameters["id"]?.toIntOrNull() ?: run {
                call.respond(HttpStatusCode.BadRequest, "Invalid room ID")
                return@post
            }

            val userId = call.request.headers["X-User-Id"]?.toIntOrNull() ?: run {
                call.respond(HttpStatusCode.Unauthorized, "User ID is required")
                return@post
            }

            transaction {
                RoomParticipants.deleteWhere {
                    (RoomParticipants.roomId eq roomId) and
                            (RoomParticipants.roomType eq "meditation") and
                            (RoomParticipants.userId eq userId)
                }

                val participantsCount = RoomParticipants
                    .select { RoomParticipants.roomId eq roomId and (RoomParticipants.roomType eq "meditation") }
                    .count()

                if (participantsCount == 0L) {
                    MeditationRooms.deleteWhere { MeditationRooms.id eq roomId }
                }
            }

            call.respond(HttpStatusCode.OK, mapOf("message" to "Left successfully"))
        }

        delete("/rooms/clear-all") {
            transaction {
                MeditationRooms.deleteAll()
                RoomParticipants.deleteWhere { RoomParticipants.roomType eq "meditation" }
            }
            call.respond(HttpStatusCode.OK, mapOf("message" to "All meditation rooms cleared"))
        }
    }
}