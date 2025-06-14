package com.example.zensyncserver

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

object DatabaseFactory {
    fun init() {
        val database = Database.connect(
            url = "jdbc:postgresql://localhost:5432/postgres",
            driver = "org.postgresql.Driver",
            user = "postgres",
            password = "admin"
        )

        transaction {
            SchemaUtils.create(
                Users,
                MeditationRooms,
                MusicRooms,
                RoomParticipants,
                MeditationNotes
            )
        }
    }
}

object Users : IntIdTable() {
    val username = varchar("username", 50).uniqueIndex()
    val email = varchar("email", 100).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val avatarUrl = varchar("avatar_url", 255).nullable()
    val createdAt = datetime("created_at")
}

object MeditationRooms : IntIdTable() {
    val name = varchar("name", 100)
    val creatorId = integer("creator_id").references(Users.id)
    val durationMinutes = integer("duration_minutes")
    val goal = text("goal").nullable()
    val isPublic = bool("is_public").default(true)
    val startTime = datetime("start_time").nullable()
    val endTime = datetime("end_time").nullable()
    val createdAt = datetime("created_at")
}

object MusicRooms : IntIdTable() {
    val name = varchar("name", 100)
    val creatorId = integer("creator_id").references(Users.id)
    val spotifyPlaylistId = varchar("spotify_playlist_id", 50)
    val spotifyPlaylistName = varchar("spotify_playlist_name", 100)
    val durationMinutes = integer("duration_minutes")
    val isPublic = bool("is_public").default(true)
    val createdAt = datetime("created_at")
}

object RoomParticipants : Table() {
    val roomId = integer("room_id")
    val roomType = varchar("room_type", 10)
    val userId = integer("user_id").references(Users.id)
    val joinedAt = datetime("joined_at")
    override val primaryKey = PrimaryKey(roomId, roomType, userId)
}

object MeditationNotes : IntIdTable() {
    val userId = integer("user_id").references(Users.id)
    val roomId = integer("room_id").nullable()
    val goal = text("goal").nullable()
    val notes = text("notes")
    val createdAt = datetime("created_at")
}