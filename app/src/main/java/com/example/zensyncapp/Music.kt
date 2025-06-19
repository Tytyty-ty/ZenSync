package com.example.zensyncapp

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.zensyncapp.models.AuthResponse
import com.example.zensyncapp.models.MusicRoom
import com.example.zensyncapp.models.SpotifyPlaylist
import com.example.zensyncapp.network.WebSocketManager
import com.example.zensyncapp.viewmodels.MusicViewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
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
                items(items = rooms, key = { it.id }) { room ->
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
    viewModel: MusicViewModel,
    currentUser: AuthResponse?
) {
    val room by viewModel.currentRoom.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isPlaying by webSocketManager.isPlaying.collectAsState()
    val participants by viewModel.roomParticipants.collectAsState()
    val newParticipantNotification by viewModel.newParticipantNotification.collectAsState()

    val participantsList = remember(participants, currentUser) {
        participants.toMutableList().apply {
            currentUser?.username?.let { if (!contains(it)) add(it) }
        }.map { if (it == currentUser?.username) "Вы" else it }
    }

    LaunchedEffect(Unit) {
        webSocketManager.requestParticipantsUpdate()
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
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    RoomInfoSection(room!!)
                    Spacer(modifier = Modifier.height(24.dp))
                    PlayerControls(
                        isPlaying = isPlaying,
                        onPlayPause = {
                            viewModel.viewModelScope.launch {
                                if (isPlaying) {
                                    webSocketManager.sendCommand("pause")
                                } else {
                                    webSocketManager.sendCommand("play")
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    ParticipantsSection(participants = participantsList)
                }
            }

            newParticipantNotification?.let { notification ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = notification,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
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
private fun PlayerControls(
    isPlaying: Boolean,
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
    }
}

@Composable
private fun ParticipantsSection(participants: List<String>) {
    Text(
        text = "Участники (${participants.size})",
        style = MaterialTheme.typography.titleMedium
    )
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        items(items = participants, key = { it }) { user ->
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

@Composable
fun CreateMusicRoomScreen(
    navController: NavController,
    viewModel: MusicViewModel
) {
    var roomName by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val showPlaylistSelector by viewModel.showPlaylistSelector.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
            }
            Text("Создать музыкальную комнату", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Название комнаты:", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = roomName,
            onValueChange = { roomName = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Моя музыкальная комната") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Плейлист:", style = MaterialTheme.typography.titleMedium)
        Button(
            onClick = { viewModel.showSpotifyPlaylistSelector() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(selectedPlaylist?.name ?: "Выбрать плейлист")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = isPublic,
                onCheckedChange = { isPublic = it }
            )
            Text("Публичная комната", modifier = Modifier.clickable { isPublic = !isPublic })
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                if (roomName.isBlank()) {
                    Toast.makeText(context, "Введите название комнаты", Toast.LENGTH_SHORT).show()
                } else if (selectedPlaylist == null) {
                    Toast.makeText(context, "Выберите плейлист", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.createMusicRoom(roomName, selectedPlaylist!!, 0, isPublic)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Создать комнату", fontSize = 18.sp)
        }
    }

    if (showPlaylistSelector) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPlaylistSelector() },
            title = { Text("Выберите плейлист") },
            text = {
                val playlists by viewModel.spotifyPlaylists.collectAsState()
                LazyColumn {
                    items(items = playlists, key = { it.id }) { playlist ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectPlaylist(playlist) }
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.LibraryMusic,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(playlist.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${playlist.trackCount} треков",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.dismissPlaylistSelector() }
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
fun MusicRoomListScreen(
    navController: NavController,
    viewModel: MusicViewModel = viewModel()
) {
    val rooms by viewModel.rooms.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Поиск музыкальных комнат") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            IconButton(
                onClick = {
                    viewModel.fetchRooms(forceRefresh = true)
                    Toast.makeText(context, "Список обновлен", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(Icons.Default.Refresh, "Обновить список")
            }
        }

        SwipeRefresh(
            state = rememberSwipeRefreshState(isRefreshing),
            onRefresh = { viewModel.fetchRooms(forceRefresh = true) },
            modifier = Modifier.weight(1f)
        ) {
            if (rooms.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Нет доступных комнат")
                }
            } else {
                LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                    items(
                        rooms.filter { room ->
                            room.name.contains(searchQuery, ignoreCase = true) ||
                                    room.creator.contains(searchQuery, ignoreCase = true) ||
                                    room.playlist?.name?.contains(searchQuery, ignoreCase = true) ?: false
                        },
                        key = { it.id }
                    ) { room ->
                        MusicRoomCard(
                            room = room,
                            onJoin = {
                                viewModel.joinMusicRoom(room.id)
                                navController.navigate("MusicRoom/${room.id}")
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}