package com.example.zensyncserver.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.zensyncapp.models.AuthResponse
import com.example.zensyncapp.models.LoginRequest
import com.example.zensyncapp.models.RegisterRequest
import com.example.zensyncserver.Users
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.security.crypto.bcrypt.BCrypt
import java.time.LocalDateTime
import java.util.*

fun Route.authRoutes() {
    route("/api/auth") {
        post("/register") {
            val request = call.receive<RegisterRequest>()

            if (request.username.isBlank() || request.email.isBlank() || request.password.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Все поля обязательны"))
                return@post
            }

            val existingUser = transaction {
                Users.select {
                    (Users.username eq request.username) or (Users.email eq request.email)
                }.singleOrNull()
            }

            if (existingUser != null) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to "Пользователь уже существует"))
                return@post
            }

            val passwordHash = BCrypt.hashpw(request.password, BCrypt.gensalt())
            val userId = transaction {
                Users.insert {
                    it[Users.username] = request.username
                    it[Users.email] = request.email
                    it[Users.passwordHash] = passwordHash
                    it[Users.createdAt] = LocalDateTime.now()
                }[Users.id]
            }

            val secret = "vtqqBq/E3lmswdl9Av+5cr8FeCSB6CyH9WA/ITMZ5Wo="
            val token = JWT.create()
                .withSubject(userId.toString())
                .withClaim("username", request.username)
                .withExpiresAt(Date(System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000))
                .sign(Algorithm.HMAC256(secret))

            call.respond(
                HttpStatusCode.Created,
                AuthResponse(
                    userId = userId.toString(),
                    username = request.username,
                    email = request.email,
                    token = token
                )
            )
        }

        post("/login") {
            val request = call.receive<LoginRequest>()

            if (request.email.isBlank() || request.password.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Email и пароль обязательны"))
                return@post
            }

            val user = transaction {
                Users.select { Users.email eq request.email }.singleOrNull()
            }

            if (user == null || !BCrypt.checkpw(request.password, user[Users.passwordHash])) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Неверные данные"))
                return@post
            }

            val secret = "vtqqBq/E3lmswdl9Av+5cr8FeCSB6CyH9WA/ITMZ5Wo="
            val token = JWT.create()
                .withSubject(user[Users.id].toString())
                .withClaim("username", user[Users.username])
                .withExpiresAt(Date(System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000))
                .sign(Algorithm.HMAC256(secret))

            call.respond(
                HttpStatusCode.OK,
                AuthResponse(
                    userId = user[Users.id].toString(),
                    username = user[Users.username],
                    email = user[Users.email],
                    token = token
                )
            )
        }
    }
}