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

        // Запускаем фоновую задачу для очистки пустых комнат
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                delay(TimeUnit.MINUTES.toMillis(5)) // Проверка каждые 5 минут
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
            // Очищаем медитационные комнаты без участников
            val emptyMeditationRooms = MeditationRooms
                .leftJoin(RoomParticipants, { MeditationRooms.id }, { RoomParticipants.roomId })
                .slice(MeditationRooms.id)
                .select {
                    (RoomParticipants.roomId.isNull()) and
                            (RoomParticipants.roomType eq "meditation")
                }
                .map { it[MeditationRooms.id].value }

            emptyMeditationRooms.forEach { roomId ->
                MeditationRooms.deleteWhere { MeditationRooms.id eq roomId }
                println("Deleted empty meditation room: $roomId")
            }

            // Очищаем музыкальные комнаты без участников
            val emptyMusicRooms = MusicRooms
                .leftJoin(RoomParticipants, { MusicRooms.id }, { RoomParticipants.roomId })
                .slice(MusicRooms.id)
                .select {
                    (RoomParticipants.roomId.isNull()) and
                            (RoomParticipants.roomType eq "music")
                }
                .map { it[MusicRooms.id].value }

            emptyMusicRooms.forEach { roomId ->
                MusicRooms.deleteWhere { MusicRooms.id eq roomId }
                println("Deleted empty music room: $roomId")
            }
        }
    } catch (e: Exception) {
        println("Error during room cleanup: ${e.message}")
    }
}