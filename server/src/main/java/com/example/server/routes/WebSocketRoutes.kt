package com.example.server.routes

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import java.util.*

fun Route.webSocketRoutes() {
    route("/ws") {
        // Вебсокет для медитаций
        webSocket("/meditation/{roomId}") {
            val roomId = call.parameters["roomId"] ?: return@webSocket
            val sessionId = UUID.randomUUID().toString()

            try {
                // Обработка входящих сообщений
                incoming.consumeEach { frame ->
                    if (frame is Frame.Text) {
                        val message = frame.readText()
                        // Обработка команд и трансляция другим участникам
                        // (реализация зависит от вашей бизнес-логики)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // Очистка при отключении
            }
        }

        // Вебсокет для музыкальных комнат
        webSocket("/music/{roomId}") {
            val roomId = call.parameters["roomId"] ?: return@webSocket
            val sessionId = UUID.randomUUID().toString()

            try {
                incoming.consumeEach { frame ->
                    if (frame is Frame.Text) {
                        val message = frame.readText()
                        // Обработка команд для музыкальных комнат
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // Очистка при отключении
            }
        }
    }
}