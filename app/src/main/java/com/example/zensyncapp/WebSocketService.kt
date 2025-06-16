package com.example.zensyncapp

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.example.zensyncapp.network.WebSocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class WebSocketService : Service() {
    private val webSocketManager = WebSocketManager(ApiClient.httpClient)
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var roomId: String = ""
    private var roomType: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        roomId = intent?.getStringExtra("roomId") ?: ""
        roomType = intent?.getStringExtra("roomType") ?: "meditation"
        val token = intent?.getStringExtra("token")
        val userId = ApiClient.getUserId()
        val username: String? = null

        serviceScope.launch {
            when (roomType) {
                "meditation" -> webSocketManager.connectToMeditationRoom(roomId, token, userId, username)
                "music" -> webSocketManager.connectToMusicRoom(roomId, token, userId, username)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocketManager.disconnect()
        serviceJob.cancel()
    }

    companion object {
        fun startService(
            context: Context,
            roomId: String,
            roomType: String,
            token: String? = null,
            userId: String? = null,
            username: String? = null
        ) {
            val intent = Intent(context, WebSocketService::class.java).apply {
                putExtra("roomId", roomId)
                putExtra("roomType", roomType)
                token?.let { putExtra("token", it) }
                userId?.let { putExtra("userId", it) }
                username?.let { putExtra("username", it) }
            }
            context.startService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, WebSocketService::class.java)
            context.stopService(intent)
        }
    }
}