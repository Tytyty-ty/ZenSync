package com.example.zensyncapp

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

object ApiClient {
    private const val BASE_URL = "http://192.168.3.6:8081/"
    private var authToken: String? = null
    private var userId: String? = null

    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                explicitNulls = false
            })
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15000
        }
        install(WebSockets) {
            pingInterval = 15_000
            maxFrameSize = Long.MAX_VALUE
        }
        defaultRequest {
            url(BASE_URL)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            authToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            userId?.let { header("X-User-Id", it) }
        }
    }

    fun setAuthToken(token: String?) {
        authToken = token
    }

    suspend fun clearAllMeditationRooms(): Boolean {
        return try {
            val response = httpClient.delete("/api/meditation/rooms/clear-all")
            response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            false
        }
    }

    suspend fun clearAllMusicRooms(): Boolean {
        return try {
            val response = httpClient.delete("/api/music/rooms/clear-all")
            response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            false
        }
    }

    fun setUserId(id: String?) {
        userId = id
    }

    fun getAuthToken(): String? = authToken

    fun getUserId(): String? = userId

    fun clearAuth() {
        authToken = null
        userId = null
    }
}