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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
                val meditationViewModel: MeditationViewModel = viewModel()


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

                        LaunchedEffect(meditationViewModel.navigateToRoom.value) {
                            meditationViewModel.navigateToRoom.value?.let { roomId ->
                                navController.navigate("LiveMeditationSession/$roomId") {
                                    popUpTo("CreateMeditationRoom/{goal}") { inclusive = true }
                                }
                                meditationViewModel.onRoomNavigated()
                            }
                        }

                        CreateMeditationRoomScreen(
                            navController = navController,
                            meditationGoal = goal,
                            viewModel = meditationViewModel
                        )
                    }

                    composable("JoinMeditationRoom") {
                        val meditationViewModel: MeditationViewModel = viewModel()
                        JoinMeditationRoomScreen(
                            navController = navController,
                            viewModel = meditationViewModel
                        )
                    }

                    composable("LiveMeditationSession/{roomId}") { backStackEntry ->
                        val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
                        val meditationViewModel: MeditationViewModel = viewModel()
                        val authViewModel: AuthViewModel = viewModel()
                        val currentUser = authViewModel.currentUser.value

                        LaunchedEffect(roomId) {
                            webSocketManager.connectToMeditationRoom(
                                roomId = roomId,
                                authToken = ApiClient.getAuthToken(),
                                userId = currentUser?.userId,
                                username = currentUser?.username
                            )
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
                            webSocketManager = webSocketManager,
                            currentUser = currentUser
                        )
                    }

                    composable("CreateMusicRoom") {
                        val musicViewModel: MusicViewModel = viewModel()
                        CreateMusicRoomScreen(
                            navController = navController,
                            viewModel = musicViewModel
                        )
                    }

                    composable("JoinMusicRoom") {
                        val musicViewModel: MusicViewModel = viewModel()
                        MusicRoomListScreen(
                            navController = navController,
                            viewModel = musicViewModel
                        )
                    }

                    composable("MusicRoom/{roomId}") { backStackEntry ->
                        val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
                        val musicViewModel: MusicViewModel = viewModel()
                        val authViewModel: AuthViewModel = viewModel()
                        val currentUser = authViewModel.currentUser.value

                        LaunchedEffect(roomId) {
                            webSocketManager.connectToMusicRoom(
                                roomId = roomId,
                                authToken = ApiClient.getAuthToken(),
                                userId = currentUser?.userId,
                                username = currentUser?.username
                            )
                        }

                        DisposableEffect(Unit) {
                            onDispose {
                                webSocketManager.disconnect()
                                musicViewModel.leaveRoom(roomId)
                            }
                        }

                        MusicRoomDetailScreen(
                            navController = navController,
                            roomId = roomId,
                            webSocketManager = webSocketManager,
                            viewModel = musicViewModel,
                            currentUser = currentUser
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