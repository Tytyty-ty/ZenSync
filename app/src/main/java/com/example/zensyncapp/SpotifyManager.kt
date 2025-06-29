package com.example.zensyncapp.spotify

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SpotifyManager(private val context: Context) {
    private var isConnected = false

    fun connect(callback: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            isConnected = true
            callback(true)
            showToast("Spotify подключен (эмуляция)")
        }
    }

    fun playPlaylist(playlistId: String) {
        if (!isConnected) {
            showToast("Сначала подключите Spotify")
            return
        }
        showToast("Воспроизводим плейлист $playlistId (эмуляция)")
    }

    fun pause() {
        if (!isConnected) return
        showToast("Пауза (эмуляция)")
    }

    fun resume() {
        if (!isConnected) return
        showToast("Продолжаем воспроизведение (эмуляция)")
    }

    fun disconnect() {
        isConnected = false
        showToast("Spotify отключен (эмуляция)")
    }

    private fun showToast(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}