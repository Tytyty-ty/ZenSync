package com.example.zensyncserver.routes

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.ConcurrentHashMap

fun Route.webSocketRoutes() {
    val meditationRooms = ConcurrentHashMap<String, MeditationRoomData>()

    route("/ws") {
        webSocket("/meditation/{roomId}") {
            val roomId = call.parameters["roomId"] ?: return@webSocket
            val session = this

            // Инициализируем данные комнаты, если их нет
            val roomData = meditationRooms.getOrPut(roomId) {
                MeditationRoomData()
            }

            try {
                roomData.connections[session.hashCode().toString()] = session

                // Отправляем текущее состояние новому участнику
                session.send(Frame.Text("time:${roomData.currentTime}"))
                session.send(Frame.Text(if (roomData.isPlaying) "play" else "pause"))

                incoming.consumeEach { frame ->
                    if (frame is Frame.Text) {
                        val message = frame.readText()
                        when {
                            message.startsWith("duration:") -> {
                                roomData.duration = message.removePrefix("duration:").toIntOrNull() ?: 0
                                roomData.currentTime = roomData.duration
                                roomData.broadcast("time:${roomData.currentTime}")
                            }
                            message == "play" -> {
                                roomData.isPlaying = true
                                roomData.timerJob?.cancel()
                                roomData.timerJob = launch {
                                    while (roomData.currentTime > 0 && roomData.isPlaying) {
                                        delay(1000)
                                        roomData.currentTime--
                                        roomData.broadcast("time:${roomData.currentTime}")
                                        if (roomData.currentTime <= 0) {
                                            roomData.broadcast("completed")
                                            roomData.isPlaying = false
                                            break
                                        }
                                    }
                                }
                                roomData.broadcast("play")
                            }
                            message == "pause" -> {
                                roomData.isPlaying = false
                                roomData.timerJob?.cancel()
                                roomData.broadcast("pause")
                            }
                            message == "get_time" -> {
                                session.send(Frame.Text("time:${roomData.currentTime}"))
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
                roomData.connections.remove(session.hashCode().toString())
                if (roomData.connections.isEmpty()) {
                    meditationRooms.remove(roomId)
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
}