package com.example.zensyncapp

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.zensyncapp.models.MusicRoom
import com.example.zensyncapp.models.SpotifyPlaylist
import com.example.zensyncapp.network.WebSocketManager
import com.example.zensyncapp.viewmodels.MusicViewModel
import kotlinx.coroutines.launch

@Composable
fun MusicRoomScreen(navController: NavController, viewModel: MusicViewModel = viewModel()) {
    val rooms by viewModel.rooms.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    val isLoading by viewModel.isLoading.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Музыкальные комнаты",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Создать комнату"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rooms) { room ->
                    MusicRoomCard(
                        room = room,
                        onJoin = { navController.navigate("MusicRoom/${room.id}") }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(text = "Создать новую комнату") },
            text = { Text(text = "Вы хотите создать новую музыкальную комнату?") },
            confirmButton = {
                Button(
                    onClick = {
                        showCreateDialog = false
                        navController.navigate("CreateMusicRoom")
                    }
                ) {
                    Text(text = "Создать")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreateDialog = false }
                ) {
                    Text(text = "Отмена")
                }
            }
        )
    }
}

@Composable
fun MusicRoomCard(
    room: MusicRoom,
    onJoin: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onJoin),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Creator",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = room.creator,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = room.name,
                style = MaterialTheme.typography.titleMedium
            )

            room.playlist?.let { playlist ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LibraryMusic,
                        contentDescription = "Playlist",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.People,
                    contentDescription = "Participants",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${room.participants} чел.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = "Duration",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${room.duration} мин",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicRoomDetailScreen(
    navController: NavController,
    roomId: String,
    webSocketManager: WebSocketManager,
    viewModel: MusicViewModel
) {
    val room by viewModel.currentRoom.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current
    val isPlaying by webSocketManager.isPlaying.collectAsState()
    val currentTime by webSocketManager.currentTime.collectAsState()

    LaunchedEffect(roomId) {
        viewModel.fetchRoomDetails(roomId)
    }

    fun togglePlayback() {
        viewModel.viewModelScope.launch {
            if (isPlaying) {
                webSocketManager.sendCommand("pause")
            } else {
                webSocketManager.sendCommand("play")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = room?.name ?: "Музыкальная комната") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when {
                isLoading -> FullScreenLoading()
                room == null -> FullScreenError("Не удалось загрузить комнату")
                else -> MusicRoomContent(
                    room = room!!,
                    isPlaying = isPlaying,
                    currentTime = currentTime,
                    onPlayPause = { togglePlayback() }
                )
            }
        }
    }
}

@Composable
private fun MusicRoomContent(
    room: MusicRoom,
    isPlaying: Boolean,
    currentTime: Int,
    onPlayPause: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        RoomInfoSection(room)
        Spacer(modifier = Modifier.height(24.dp))
        MusicPlayerSection(
            room = room,
            isPlaying = isPlaying,
            currentTime = currentTime,
            onPlayPause = onPlayPause
        )
        Spacer(modifier = Modifier.height(24.dp))
        ParticipantsSection(room)
    }
}

@Composable
private fun RoomInfoSection(room: MusicRoom) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = "Creator",
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = "Создатель: ${room.creator}",
                style = MaterialTheme.typography.bodyMedium
            )
            room.playlist?.let {
                Text(
                    text = "Плейлист: ${it.name}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun MusicPlayerSection(
    room: MusicRoom,
    isPlaying: Boolean,
    currentTime: Int,
    onPlayPause: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.LibraryMusic,
                contentDescription = "Playlist cover",
                modifier = Modifier.size(150.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Сейчас играет",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = room.playlist?.name ?: "Плейлист не выбран",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${currentTime / 60}:${"%02d".format(currentTime % 60)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            PlayerControls(isPlaying, onPlayPause)
        }
    }
}

@Composable
private fun PlayerControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        IconButton(onClick = { /* Previous track */ }) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous"
            )
        }
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play/Pause",
                modifier = Modifier.size(48.dp)
            )
        }
        IconButton(onClick = { /* Next track */ }) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next"
            )
        }
    }
}

@Composable
private fun ParticipantsSection(room: MusicRoom) {
    Text(
        text = "Участники (${room.participants})",
        style = MaterialTheme.typography.titleMedium
    )
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        items(listOf(room.creator, "Вы")) { user ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = if (user == "Вы") MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondary
                )
                Text(text = user)
            }
        }
    }
}

@Composable
private fun FullScreenLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun FullScreenError(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}