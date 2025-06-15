package com.example.zensyncserver.routes

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import java.util.*

fun Route.webSocketRoutes() {
    route("/ws") {
        webSocket("/meditation/{roomId}") {
            val roomId = call.parameters["roomId"] ?: return@webSocket
            var currentTime = 0

            try {
                send(Frame.Text("Welcome to meditation room $roomId"))

                incoming.consumeEach { frame ->
                    if (frame is Frame.Text) {
                        val message = frame.readText()
                        when {
                            message == "play" -> {
                                send(Frame.Text("play"))
                            }
                            message == "pause" -> {
                                send(Frame.Text("pause"))
                            }
                            message.startsWith("time:") -> {
                                currentTime = message.removePrefix("time:").toIntOrNull() ?: 0
                                // Рассылаем обновленное время всем участникам
                                send(Frame.Text("time:$currentTime"))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
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