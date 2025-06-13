package com.example.zensyncapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.zensyncapp.network.WebSocketManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.zensyncapp.ui.theme.ZenSyncAppTheme
import com.example.zensyncapp.viewmodels.AuthViewModel
import com.example.zensyncapp.viewmodels.MeditationViewModel
import com.example.zensyncapp.viewmodels.MusicViewModel

class MainActivity : ComponentActivity() {
    private val webSocketManager by lazy { WebSocketManager(ApiClient.httpClient) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.hasExtra("roomId") == true) {
            val roomId = intent.getStringExtra("roomId") ?: ""
            val roomType = intent.getStringExtra("roomType") ?: "meditation"
            val token = ApiClient.getAuthToken()
            WebSocketService.startService(this, roomId, roomType, token)
        }

        setContent {
            ZenSyncAppTheme {
                val navController = rememberNavController()
                val authViewModel: AuthViewModel = viewModel()

                NavHost(
                    navController = navController,
                    startDestination = "WelcomeScreen"
                ) {
                    composable("WelcomeScreen") {
                        WelcomeScreen(navController)
                    }

                    composable("LoginScreen") {
                        LoginScreen(navController, authViewModel)
                    }

                    composable("RegisterScreen") {
                        RegisterScreen(navController, authViewModel)
                    }

                    composable("MainHub") {
                        MainHub(navController)
                    }

                    composable("CreateMeditationRoom/{goal}") { backStackEntry ->
                        val goal = backStackEntry.arguments?.getString("goal") ?: ""
                        val meditationViewModel: MeditationViewModel = viewModel()
                        CreateMeditationRoomScreen(
                            navController = navController,
                            meditationGoal = goal,
                            viewModel = meditationViewModel
                        )
                    }

                    composable("JoinMeditationRoom") {
                        val meditationViewModel: MeditationViewModel = viewModel()
                        MeditationRoomListScreen( // Изменено с JoinMeditationRoomScreen на MeditationRoomListScreen
                            navController = navController,
                            viewModel = meditationViewModel
                        )
                    }

                    composable("LiveMeditationSession/{roomId}") { backStackEntry ->
                        val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
                        val meditationViewModel: MeditationViewModel = viewModel()

                        LaunchedEffect(roomId) {
                            webSocketManager.connectToMeditationRoom(roomId)
                        }

                        DisposableEffect(Unit) {
                            onDispose {
                                webSocketManager.disconnect()
                            }
                        }

                        LiveMeditationScreen(
                            navController = navController,
                            roomId = roomId,
                            viewModel = meditationViewModel,
                            webSocketManager = webSocketManager
                        )
                    }

                    composable("CreateMusicRoom") {
                        val musicViewModel: MusicViewModel = viewModel()
                        CreateMusicRoomScreen( // Добавлен импорт или определение этого composable
                            navController = navController,
                            viewModel = musicViewModel
                        )
                    }

                    composable("JoinMusicRoom") {
                        val musicViewModel: MusicViewModel = viewModel()
                        MusicRoomListScreen( // Изменено с JoinMusicRoomScreen на MusicRoomListScreen
                            navController = navController,
                            viewModel = musicViewModel
                        )
                    }

                    composable("MusicRoom/{roomId}") { backStackEntry ->
                        val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
                        val musicViewModel: MusicViewModel = viewModel()

                        LaunchedEffect(roomId) {
                            webSocketManager.connectToMusicRoom(roomId)
                        }

                        DisposableEffect(Unit) {
                            onDispose {
                                webSocketManager.disconnect()
                            }
                        }

                        MusicRoomDetailScreen(
                            navController = navController,
                            roomId = roomId,
                            viewModel = musicViewModel,
                            webSocketManager = webSocketManager
                        )
                    }

                    composable("SettingsScreen") {
                        SettingsScreen()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocketManager.disconnect()
    }
}

// Добавляем недостающие composable функции, если они не были определены ранее

@Composable
fun CreateMusicRoomScreen(
    navController: NavController,
    viewModel: MusicViewModel
) {
    var roomName by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf(30) }
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

        Text("Длительность (мин):", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = duration.toFloat(),
            onValueChange = { duration = it.toInt() },
            valueRange = 5f..120f,
            steps = 22,
            modifier = Modifier.fillMaxWidth()
        )
        Text(text = "$duration минут", modifier = Modifier.align(Alignment.CenterHorizontally))

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
                    viewModel.createMusicRoom(roomName, selectedPlaylist!!, duration, isPublic)
                    navController.popBackStack()
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
                    items(playlists) { playlist ->
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
    viewModel: MusicViewModel
) {
    val rooms by viewModel.rooms.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

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
        }

        LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
            items(
                rooms.filter { room ->
                    room.name.contains(searchQuery, ignoreCase = true) ||
                            room.creator.contains(searchQuery, ignoreCase = true) ||
                            room.playlist?.name?.contains(searchQuery, ignoreCase = true) ?: false
                }
            ) { room ->
                MusicRoomCard(room = room) {
                    navController.navigate("MusicRoom/${room.id}")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun MeditationRoomListScreen(
    navController: NavController,
    viewModel: MeditationViewModel
) {
    val rooms by viewModel.rooms.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

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

        LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
            items(
                rooms.filter { room ->
                    room.name.contains(searchQuery, ignoreCase = true) ||
                            room.creator.contains(searchQuery, ignoreCase = true) ||
                            (room.goal?.contains(searchQuery, ignoreCase = true) ?: false)
                }
            ) { room ->
                MeditationRoomCard(room = room) {
                    navController.navigate("LiveMeditationSession/${room.id}")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}