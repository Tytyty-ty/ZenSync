package com.example.zensyncapp

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.zensyncapp.models.MusicRoom
import com.example.zensyncapp.models.SpotifyPlaylist
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainHub(navController: NavController) {
    var selectedTab by remember { mutableStateOf(0) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (selectedTab) {
                    0 -> "Медитация"
                    1 -> "Музыка"
                    else -> "Настройки"
                },
                style = MaterialTheme.typography.titleLarge
            )
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "Профиль",
                modifier = Modifier
                    .size(36.dp)
                    .clickable { showLogoutDialog = true }
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> MeditationScreen(navController)
                1 -> MusicRoomListScreen(navController)
                2 -> SettingsScreen()
            }
        }

        NavigationBar(containerColor = MaterialTheme.colorScheme.primaryContainer) {
            NavigationBarItem(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                icon = { Icon(Icons.Default.SelfImprovement, contentDescription = null) },
                label = { Text("Медитация") }
            )
            NavigationBarItem(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                icon = { Icon(Icons.Default.Headset, contentDescription = null) },
                label = { Text("Музыка") }
            )
            NavigationBarItem(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                label = { Text("Настройки") }
            )
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Подтверждение выхода") },
            text = { Text("Вы уверены, что хотите выйти из аккаунта?") },
            confirmButton = {
                Button(
                    onClick = {
                        navController.navigate("WelcomeScreen") {
                            popUpTo(0)
                        }
                    }
                ) {
                    Text("Выйти")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false }
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
fun MeditationScreen(navController: NavController) {
    val context = LocalContext.current
    var meditationGoal by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Цели медитации", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = meditationGoal,
            onValueChange = { meditationGoal = it },
            placeholder = { Text("Например: расслабиться, сосредоточиться...") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text("Комнаты медитации", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                if (meditationGoal.isBlank()) {
                    Toast.makeText(context, "Установите цель перед медитацией", Toast.LENGTH_SHORT).show()
                } else {
                    navController.navigate("CreateMeditationRoom/${meditationGoal}")
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Создать комнату")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { navController.navigate("JoinMeditationRoom") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Вступить в комнату")
        }
    }
}

@Composable
fun SettingsScreen() {
    var showOnlineStatus by remember { mutableStateOf(true) }
    var notificationsEnabled by remember { mutableStateOf(true) }
    var isSpotifyConnected by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Настройки", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(24.dp))

        Text("Интеграции", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isSpotifyConnected) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_spotify),
                    contentDescription = "Spotify",
                    modifier = Modifier.size(32.dp),
                    tint = if (isSpotifyConnected) Color(0xFF1DB954) else MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isSpotifyConnected) "Spotify подключен" else "Spotify не подключен",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (!isSpotifyConnected) {
                        Text(
                            text = "Требуется для создания музыкальных комнат",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                    }
                }
                Button(
                    onClick = {
                        isSpotifyConnected = !isSpotifyConnected
                        Toast.makeText(
                            context,
                            if (isSpotifyConnected) "Spotify успешно подключен"
                            else "Spotify отключен",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSpotifyConnected) MaterialTheme.colorScheme.error
                        else Color(0xFF1DB954)
                    )
                ) {
                    Text(if (isSpotifyConnected) "Отключить" else "Привязать")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Приватность", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Показывать мой статус в комнатах",
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = showOnlineStatus,
                onCheckedChange = { showOnlineStatus = it }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Уведомления", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Получать уведомления о новых комнатах",
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = notificationsEnabled,
                onCheckedChange = { notificationsEnabled = it }
            )
        }
        Column(modifier = Modifier.padding(16.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        val success = ApiClient.clearAllMeditationRooms()
                        Toast.makeText(
                            context,
                            if (success) "Все медитационные комнаты очищены" else "Ошибка очистки",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Очистить ВСЕ медитационные комнаты")
            }
            Button(
                onClick = {
                    scope.launch {
                        val success = ApiClient.clearAllMusicRooms()
                        Toast.makeText(
                            context,
                            if (success) "Все музыкальные комнаты очищены" else "Ошибка очистки",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Очистить ВСЕ музыкальные комнаты")
            }
        }
    }
}
