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

fun Route.webSocketRoutes() {
    route("/ws") {
        webSocket("/meditation/{roomId}") {
            var currentTime = 0
            var roomDuration = 0
            var timerJob: Job? = null
            val roomId = call.parameters["roomId"] ?: return@webSocket

            try {
                // Отправляем текущее время новому участнику
                send(Frame.Text("time:$currentTime"))

                incoming.consumeEach { frame ->
                    if (frame is Frame.Text) {
                        val message = frame.readText()
                        when {
                            message.startsWith("duration:") -> {
                                roomDuration = message.removePrefix("duration:").toIntOrNull() ?: 0
                                currentTime = roomDuration
                                send(Frame.Text("time:$currentTime"))
                            }
                            message == "play" -> {
                                // Отменяем предыдущий таймер, если он был
                                timerJob?.cancel()
                                // Запускаем новый таймер
                                timerJob = launch {
                                    while (currentTime > 0) {
                                        delay(1000)
                                        currentTime--
                                        send(Frame.Text("time:$currentTime"))
                                        if (currentTime <= 0) {
                                            send(Frame.Text("completed"))
                                            break
                                        }
                                    }
                                }
                                send(Frame.Text("play"))
                            }
                            message == "pause" -> {
                                // Останавливаем таймер
                                timerJob?.cancel()
                                send(Frame.Text("pause"))
                            }
                            message == "get_time" -> {
                                // Отправляем текущее время по запросу
                                send(Frame.Text("time:$currentTime"))
                            }
                            message.startsWith("time:") -> {
                                // Обновляем время (если пришло извне)
                                currentTime = message.removePrefix("time:").toIntOrNull() ?: 0
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                println("WebSocket error: ${e.message}")
            } finally {
                timerJob?.cancel()
                coroutineContext.cancelChildren()
            }
        }

        webSocket("/music/{roomId}") {
            val roomId = call.parameters["roomId"] ?: return@webSocket

            try {
                send(Frame.Text("Welcome to music room $roomId"))

                incoming.consumeEach { frame ->
                    if (frame is Frame.Text) {
                        val message = frame.readText()
                        when (message) {
                            "play" -> send(Frame.Text("play"))
                            "pause" -> send(Frame.Text("pause"))
                            else -> send(Frame.Text("Unknown command"))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}