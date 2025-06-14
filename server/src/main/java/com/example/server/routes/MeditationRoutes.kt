package com.example.zensyncserver.routes

import com.example.zensyncapp.models.CreateMeditationRoomRequest
import com.example.zensyncapp.models.MeditationRoom
import com.example.zensyncserver.MeditationRooms
import com.example.zensyncserver.Users
import com.example.zensyncserver.extensions.toMeditationRoom
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
import java.util.*

fun Route.meditationRoutes() {
    route("/api/meditation") {
        get("/rooms") {
            val rooms = transaction {
                MeditationRooms.selectAll().map { it.toMeditationRoom() }
            }
            call.respond(rooms)
        }

        post("/rooms") {
            val request = call.receive<CreateMeditationRoomRequest>()
            val userId = call.request.headers["X-User-Id"]?.toIntOrNull() ?: 1

            val roomId = transaction {
                MeditationRooms.insert {
                    it[name] = request.name
                    it[creatorId] = userId
                    it[durationMinutes] = request.duration
                    it[goal] = request.goal
                    it[isPublic] = request.isPublic
                    it[Users.createdAt] = LocalDateTime.now()
                } get MeditationRooms.id
            }

            // Возвращаем полный объект комнаты вместо только ID
            val room = transaction {
                MeditationRooms.select { MeditationRooms.id eq roomId }.single().toMeditationRoom()
            }

            call.respond(HttpStatusCode.Created, room)
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