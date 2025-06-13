package com.example.zensyncapp

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.header
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

object ApiClient {
    private const val BASE_URL = "http://127.0.0.1:8080/"
    private var authToken: String? = null

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
        defaultRequest {
            url(BASE_URL)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            authToken?.let {
                header(HttpHeaders.Authorization, "Bearer $it")
            }
        }
    }

    fun setAuthToken(token: String?) {
        authToken = token
    }
    fun clearAuthToken() {
        authToken = null
    }
    fun getAuthToken(): String? = authToken
}