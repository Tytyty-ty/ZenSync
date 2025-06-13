package com.example.zensyncapp

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.zensyncapp.models.MeditationRoom
import com.example.zensyncapp.network.WebSocketManager
import com.example.zensyncapp.viewmodels.MeditationViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateMeditationRoomScreen(
    navController: NavController,
    meditationGoal: String,
    viewModel: MeditationViewModel
) {
    var duration by remember { mutableStateOf(15) }
    var roomName by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
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
            Text("Создать комнату", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Ваша цель:", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = meditationGoal,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Название комнаты:", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = roomName,
            onValueChange = { roomName = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Моя медитационная комната") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Длительность (мин):", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = duration.toFloat(),
            onValueChange = { duration = it.toInt() },
            valueRange = 5f..60f,
            steps = 10,
            modifier = Modifier.fillMaxWidth()
        )
        Text(text = "$duration минут", modifier = Modifier.align(Alignment.CenterHorizontally))

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
                } else {
                    viewModel.createRoom(roomName, duration, meditationGoal, isPublic)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Начать медитацию", fontSize = 18.sp)
        }
    }
}

@Composable
fun JoinMeditationRoomScreen(
    navController: NavController,
    viewModel: MeditationViewModel
) {
    val rooms by viewModel.rooms.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val isLoading by viewModel.isLoading.collectAsState()

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
                placeholder = { Text("Поиск комнат") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                items(
                    rooms.filter { room ->
                        room.name.contains(searchQuery, ignoreCase = true) ||
                                room.creator.contains(searchQuery, ignoreCase = true) ||
                                (room.goal?.contains(searchQuery, ignoreCase = true) ?: false)
                    }
                ) { room ->
                    MeditationRoomCard(room = room) {
                        viewModel.joinRoom(room.id)
                        navController.navigate("LiveMeditationSession/${room.id}")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun MeditationRoomCard(
    room: MeditationRoom,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = room.name,
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = "Создатель",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = room.creator,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.width(12.dp))
                room.goal?.let { goal ->
                    Text(
                        text = "Цель: $goal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(
                    Icons.Default.People,
                    contentDescription = "Участники",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${room.participants} чел.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    Icons.Default.AccessTime,
                    contentDescription = "Длительность",
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

@Composable
fun LiveMeditationScreen(
    navController: NavController,
    roomId: String,
    viewModel: MeditationViewModel,
    webSocketManager: WebSocketManager
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isPlaying by webSocketManager.isPlaying.collectAsState()
    val currentTime by webSocketManager.currentTime.collectAsState()

    fun toggleMeditation() {
        coroutineScope.launch {
            if (isPlaying) {
                webSocketManager.sendCommand("pause")
            } else {
                webSocketManager.sendCommand("play")
            }
        }
    }

    val room by viewModel.currentRoom.collectAsState()
    var showEndDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.meditation_bg),
            contentDescription = "Фон медитации",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(
                    onClick = { showEndDialog = true },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Icon(Icons.Default.Close, "Закрыть", tint = Color.White)
                }

                Text(
                    text = room?.name ?: "Медитация",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = "Цель: ${room?.goal ?: "Расслабление"}",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${currentTime / 60}:${"%02d".format(currentTime % 60)}",
                    color = Color.White,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Light
                )

                Spacer(modifier = Modifier.height(32.dp))

                IconButton(
                    onClick = { toggleMeditation() },
                    modifier = Modifier.size(80.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Пауза" else "Старт",
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Участники: ${room?.participants ?: 1}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    items(listOf(room?.creator ?: "Создатель", "Вы")) { user ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = "Участник",
                                tint = if (user == "Вы") MaterialTheme.colorScheme.primary
                                else Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = user,
                                color = Color.White,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }

    if (showEndDialog) {
        AlertDialog(
            onDismissRequest = { showEndDialog = false },
            title = { Text("Завершить медитацию?") },
            text = { Text("Вы уверены, что хотите выйти из '${room?.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        showEndDialog = false
                        navController.popBackStack()
                    }
                ) {
                    Text("Выйти")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEndDialog = false }
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}