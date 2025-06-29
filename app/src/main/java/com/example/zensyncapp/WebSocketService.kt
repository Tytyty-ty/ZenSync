package com.example.zensyncapp

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.example.zensyncapp.network.WebSocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

class WebSocketService : Service() {
    private val webSocketManager = WebSocketManager(ApiClient.httpClient)
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val roomId = intent?.getStringExtra("roomId") ?: return START_NOT_STICKY
        val roomType = intent.getStringExtra("roomType") ?: "meditation"
        val token = intent.getStringExtra("token")
        val userId = intent.getStringExtra("userId")
        val username = intent.getStringExtra("username")

        serviceScope.launch {
            webSocketManager.connectToRoom(
                roomId = roomId,
                roomType = roomType,
                authToken = token,
                userId = userId,
                username = username
            )
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocketManager.disconnect()
        serviceScope.coroutineContext.cancelChildren()
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
            context.startService(Intent(context, WebSocketService::class.java).apply {
                putExtra("roomId", roomId)
                putExtra("roomType", roomType)
                token?.let { putExtra("token", it) }
                userId?.let { putExtra("userId", it) }
                username?.let { putExtra("username", it) }
                })
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, WebSocketService::class.java))
        }
    }
}