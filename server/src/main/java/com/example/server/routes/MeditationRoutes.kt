package com.example.server.routes

import com.example.zensyncapp.models.CreateMeditationRoomRequest
import com.example.zensyncapp.models.MeditationRoom
import com.example.zensyncserver.*
import com.example.zensyncserver.extensions.toMeditationRoom
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

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

            val roomId = transaction {
                MeditationRooms.insert {
                    it[name] = request.name
                    it[creatorId] = 1 // TODO: ID текущего пользователя
                    it[durationMinutes] = request.duration
                    it[goal] = request.goal
                    it[isPublic] = request.isPublic
                    it[Users.createdAt] = LocalDateTime.now()
                } get MeditationRooms.id
            }

            call.respond(HttpStatusCode.Created, mapOf("id" to roomId))
        }
}
    }

