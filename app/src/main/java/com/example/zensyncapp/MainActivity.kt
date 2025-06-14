package com.example.zensyncapp

import android.annotation.SuppressLint
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

    @SuppressLint("StateFlowValueCalledInComposition")
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

                LaunchedEffect(authViewModel.currentUser.value) {
                    authViewModel.currentUser.value?.let { user ->
                        ApiClient.setAuthToken(user.token)
                        ApiClient.setUserId(user.userId)
                    }
                }

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



