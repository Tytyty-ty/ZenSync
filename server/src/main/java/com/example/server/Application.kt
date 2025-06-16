package com.example.zensyncserver

import com.example.zensyncserver.routes.authRoutes
import com.example.zensyncserver.routes.meditationRoutes
import com.example.zensyncserver.routes.musicRoutes
import com.example.zensyncserver.routes.webSocketRoutes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.request.path
import org.slf4j.event.Level
import kotlinx.serialization.json.Json
import java.time.Duration
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq


fun main() {
    embeddedServer(Netty, port = 8081, host = "0.0.0.0") {
        install(WebSockets) {
            pingPeriod = Duration.ofSeconds(15)
            timeout = Duration.ofSeconds(15)
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }
        CoroutineScope(Dispatchers.IO).launch {
            cleanupEmptyRooms()
            cleanupEmptyMusicRooms()
            while (true) {
                delay(TimeUnit.MINUTES.toMillis(2)) // И каждые 2 минут
                cleanupEmptyRooms()
                cleanupEmptyMusicRooms()
            }
        }

        install(CallLogging) {
            level = Level.INFO
            filter { call -> call.request.path().startsWith("/") }
        }

        install(CORS) {
            anyHost()
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Delete)
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
            allowHeader("X-User-Id")
        }

        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }

        DatabaseFactory.init()


        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                delay(TimeUnit.MINUTES.toMillis(5))
                cleanupEmptyRooms()
            }
        }

        routing {
            get("/") {
                call.respondText("ZenSync Server is running!")
            }

            authRoutes()
            meditationRoutes()
            musicRoutes()
            webSocketRoutes()
        }
    }.start(wait = true)
}

// Функция для очистки пустых комнат
suspend fun cleanupEmptyRooms() {
    try {
        transaction {
            // Очистка медитационных комнат
            val meditationRooms = MeditationRooms.selectAll().map { it[MeditationRooms.id].value }
            val meditationWithParticipants = RoomParticipants
                .select {
                    (RoomParticipants.roomId inList meditationRooms) and
                            (RoomParticipants.roomType eq "meditation")
                }
                .map { it[RoomParticipants.roomId] }
                .distinct()

            (meditationRooms - meditationWithParticipants).forEach { roomId ->
                MeditationRooms.deleteWhere { MeditationRooms.id eq roomId }
                println("Deleted empty meditation room: $roomId")
            }

            // Очистка музыкальных комнат
            val musicRooms = MusicRooms.selectAll().map { it[MusicRooms.id].value }
            val musicWithParticipants = RoomParticipants
                .select {
                    (RoomParticipants.roomId inList musicRooms) and
                            (RoomParticipants.roomType eq "music")
                }
                .map { it[RoomParticipants.roomId] }
                .distinct()

            (musicRooms - musicWithParticipants).forEach { roomId ->
                MusicRooms.deleteWhere { MusicRooms.id eq roomId }
                println("Deleted empty music room: $roomId")
            }
        }
    } catch (e: Exception) {
        println("Error during room cleanup: ${e.message}")
    }
}

suspend fun cleanupEmptyMusicRooms() {
    try {
        val deleted = transaction {
            // Альтернативный способ поиска пустых комнат
            val emptyRooms = MusicRooms
                .leftJoin(RoomParticipants,
                    onColumn = { MusicRooms.id },
                    otherColumn = { RoomParticipants.roomId },
                    additionalConstraint = { RoomParticipants.roomType eq "music" })
                .slice(MusicRooms.id)
                .select {
                    RoomParticipants.roomId.isNull()
                }
                .map { it[MusicRooms.id].value }

            emptyRooms.sumOf { roomId ->
                MusicRooms.deleteWhere { MusicRooms.id eq roomId }
            }
        }

        if (deleted > 0) {
            println("Background cleanup deleted $deleted empty music rooms")
        }
    } catch (e: Exception) {
        println("Error in music room cleanup: ${e.message}")
    }
}
