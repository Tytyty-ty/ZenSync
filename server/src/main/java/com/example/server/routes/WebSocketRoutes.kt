package com.example.zensyncserver.routes

import com.example.zensyncserver.MeditationRooms
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import java.util.concurrent.ConcurrentHashMap

fun Route.webSocketRoutes() {
    val meditationRooms = ConcurrentHashMap<String, MeditationRoomData>()
    val musicRooms = ConcurrentHashMap<String, MusicRoomData>()

    route("/ws") {
        webSocket("/meditation/{roomId}") {
            val roomId = call.parameters["roomId"] ?: return@webSocket
            val session = this

            // Инициализируем данные комнаты, если их нет
            val roomData = meditationRooms.getOrPut(roomId) {
                MeditationRoomData().apply {
                    // Загружаем продолжительность комнаты из базы данных
                    transaction {
                        MeditationRooms.select { MeditationRooms.id eq roomId.toInt() }
                            .singleOrNull()
                            ?.get(MeditationRooms.durationMinutes)
                            ?.let { duration ->
                                this@apply.duration = duration * 60
                                this@apply.currentTime = duration * 60
                            }
                    }
                }
            }

            try {
                roomData.connections[session.hashCode().toString()] = session
                roomData.sendInitialState(session)

                // Получаем информацию о пользователе из заголовков
                val userId = call.request.headers["X-User-Id"]
                val username = call.request.headers["X-Username"]

                // Добавляем участника и уведомляем всех
                if (userId != null && username != null) {
                    roomData.participants[userId] = username
                    // Отправляем обновленный список всем участникам
                    roomData.broadcast("participants:${roomData.participants.values.joinToString(",")}")
                    // Отправляем уведомление о новом участнике
                    roomData.broadcast("new_participant:$username")
                }

                // Отправляем текущее состояние новому участнику
                session.send(Frame.Text("duration:${roomData.duration}"))
                session.send(Frame.Text("time:${roomData.currentTime}"))
                session.send(Frame.Text(if (roomData.isPlaying) "play" else "pause"))
                // Отправляем текущий список участников новому подключению
                session.send(Frame.Text("participants:${roomData.participants.values.joinToString(",")}"))

                incoming.consumeEach { frame ->
                    if (frame is Frame.Text) {
                        val message = frame.readText()
                        when {
                            message == "get_state" -> {
                                roomData.sendInitialState(session)
                            }
                            message == "get_participants" -> {
                                // Отправляем текущий список участников по запросу
                                session.send(Frame.Text("participants:${roomData.participants.values.joinToString(",")}"))
                            }
                            message.startsWith("duration:") -> {
                                roomData.duration = message.removePrefix("duration:").toIntOrNull() ?: 0
                                roomData.currentTime = roomData.duration
                                roomData.broadcast("time:${roomData.currentTime}")
                            }
                            message == "play" -> {
                                roomData.handlePlayCommand()
                            }
                            message == "pause" -> {
                                roomData.handlePauseCommand()
                            }
                            message.startsWith("time:") -> {
                                roomData.currentTime = message.removePrefix("time:").toIntOrNull() ?: 0
                                roomData.broadcast("time:${roomData.currentTime}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                println("WebSocket error: ${e.message}")
            } finally {
                // При отключении пользователя удаляем его из списка участников
                val userId = call.request.headers["X-User-Id"]
                if (userId != null) {
                    roomData.participants.remove(userId)
                    // Уведомляем остальных участников об обновленном списке
                    roomData.broadcast("participants:${roomData.participants.values.joinToString(",")}")
                }

                roomData.connections.remove(session.hashCode().toString())
                if (roomData.connections.isEmpty()) {
                    meditationRooms.remove(roomId)
                }
                coroutineContext.cancelChildren()
            }
        }

        webSocket("/music/{roomId}") {
            val roomId = call.parameters["roomId"] ?: return@webSocket
            val session = this

            val roomData = musicRooms.getOrPut(roomId) {
                MusicRoomData()
            }

            try {
                roomData.connections[session.hashCode().toString()] = session

                // Получаем информацию о пользователе из заголовков
                val userId = call.request.headers["X-User-Id"]
                val username = call.request.headers["X-Username"]

                // Добавляем участника и уведомляем всех
                if (userId != null && username != null) {
                    roomData.participants[userId] = username
                    // Отправляем обновленный список всем участникам
                    roomData.broadcast("participants:${roomData.participants.values.joinToString(",")}")
                    // Отправляем уведомление о новом участнике
                    roomData.broadcast("new_participant:$username")
                }

                // Отправляем текущий список участников новому подключению
                session.send(Frame.Text("participants:${roomData.participants.values.joinToString(",")}"))

                incoming.consumeEach { frame ->
                    if (frame is Frame.Text) {
                        val message = frame.readText()
                        when {
                            message == "play" -> {
                                roomData.isPlaying = true
                                roomData.broadcast("playback:play")
                            }
                            message == "pause" -> {
                                roomData.isPlaying = false
                                roomData.broadcast("playback:pause")
                            }
                            message == "get_participants" -> {
                                // Отправляем текущий список участников по запросу
                                session.send(Frame.Text("participants:${roomData.participants.values.joinToString(",")}"))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                println("Music WebSocket error: ${e.message}")
            } finally {
                // При отключении пользователя удаляем его из списка участников
                val userId = call.request.headers["X-User-Id"]
                if (userId != null) {
                    roomData.participants.remove(userId)
                    // Уведомляем остальных участников об обновленном списке
                    roomData.broadcast("participants:${roomData.participants.values.joinToString(",")}")
                }

                roomData.connections.remove(session.hashCode().toString())
                if (roomData.connections.isEmpty()) {
                    musicRooms.remove(roomId)
                }
                coroutineContext.cancelChildren()
            }
        }
    }
}

class MeditationRoomData {
    var currentTime: Int = 0
    var duration: Int = 0
    var isPlaying: Boolean = false
    var timerJob: Job? = null
    val connections = ConcurrentHashMap<String, WebSocketSession>()
    val participants = ConcurrentHashMap<String, String>() // userId to username

    suspend fun broadcast(message: String) {
        connections.values.forEach { session ->
            try {
                session.send(Frame.Text(message))
            } catch (e: Exception) {
                println("Failed to send message: ${e.message}")
                connections.remove(session.hashCode().toString())
            }
        }
    }

    suspend fun sendInitialState(session: WebSocketSession) {
        session.send(Frame.Text("state:$currentTime,$isPlaying,$duration"))
    }

    suspend fun handlePlayCommand() {
        if (!isPlaying) {
            if (currentTime <= 0) {
                currentTime = duration
            }
            isPlaying = true
            timerJob?.cancel()
            timerJob = CoroutineScope(Dispatchers.IO).launch {
                while (currentTime > 0 && isPlaying) {
                    delay(1000)
                    currentTime--
                    broadcast("time:$currentTime")
                    if (currentTime <= 0) {
                        broadcast("completed")
                        isPlaying = false
                        break
                    }
                }
            }
            broadcast("play")
        }
    }

    suspend fun handlePauseCommand() {
        if (isPlaying) {
            isPlaying = false
            timerJob?.cancel()
            broadcast("pause")
        }
    }
}

class MusicRoomData {
    var isPlaying: Boolean = false
    val connections = ConcurrentHashMap<String, WebSocketSession>()
    val participants = ConcurrentHashMap<String, String>() // userId to username

    suspend fun broadcast(message: String) {
        connections.values.forEach { session ->
            try {
                session.send(Frame.Text(message))
            } catch (e: Exception) {
                println("Failed to send music message: ${e.message}")
                connections.remove(session.hashCode().toString())
            }
        }
    }
}