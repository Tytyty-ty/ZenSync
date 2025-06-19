package com.example.zensyncapp

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.zensyncapp.network.WebSocketManager
import com.example.zensyncapp.ui.theme.ZenSyncAppTheme
import com.example.zensyncapp.viewmodels.*


class MainActivity : ComponentActivity() {
    private val webSocketManager by lazy { WebSocketManager(ApiClient.httpClient) }

    @SuppressLint("StateFlowValueCalledInComposition")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ZenSyncAppTheme {
                val navController = rememberNavController()
                val authViewModel: AuthViewModel = viewModel()
                val currentUser by authViewModel.currentUser.collectAsState()

                NavHost(
                    navController = navController,
                    startDestination = "WelcomeScreen"
                ) {
                    composable("WelcomeScreen") { WelcomeScreen(navController) }
                    composable("LoginScreen") { LoginScreen(navController, authViewModel) }
                    composable("RegisterScreen") { RegisterScreen(navController, authViewModel) }
                    composable("MainHub") { MainHub(navController) }

                    // Медитационные комнаты
                    composable("CreateMeditationRoom/{goal}") { backStackEntry ->
                        val goal = backStackEntry.arguments?.getString("goal") ?: ""
                        val viewModel: MeditationViewModel = viewModel()
                        RoomNavigationHandler(navController, viewModel)
                        CreateMeditationRoomScreen(navController, goal, viewModel)
                    }

                    composable("JoinMeditationRoom") {
                        val viewModel: MeditationViewModel = viewModel()
                        JoinMeditationRoomScreen(navController, viewModel)
                    }

                    composable("LiveMeditationSession/{roomId}") { backStackEntry ->
                        val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
                        val viewModel: MeditationViewModel = viewModel()

                        LaunchedEffect(roomId) {
                            currentUser?.let { user ->
                                webSocketManager.connectToRoom(
                                    roomId = roomId,
                                    roomType = "meditation",
                                    authToken = user.token,
                                    userId = user.userId,
                                    username = user.username
                                )
                                viewModel.setupWebSocketManager(webSocketManager)
                                viewModel.joinRoom(roomId)
                            }
                        }

                        DisposableEffect(Unit) {
                            onDispose {
                                webSocketManager.disconnect()
                            }
                        }

                        LiveMeditationScreen(
                            navController = navController,
                            roomId = roomId,
                            viewModel = viewModel,
                            webSocketManager = webSocketManager,
                            currentUser = currentUser
                        )
                    }

                    // Музыкальные комнаты
                    composable("CreateMusicRoom") {
                        val viewModel: MusicViewModel = viewModel()
                        RoomNavigationHandler(navController, viewModel)
                        CreateMusicRoomScreen(navController, viewModel)
                    }

                    composable("MusicRoomListScreen") {
                        val viewModel: MusicViewModel = viewModel()
                        MusicRoomListScreen(navController, viewModel)
                    }

                    composable("MusicRoom/{roomId}") { backStackEntry ->
                        val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
                        val viewModel: MusicViewModel = viewModel()

                        LaunchedEffect(roomId) {
                            currentUser?.let { user ->
                                webSocketManager.connectToRoom(
                                    roomId = roomId,
                                    roomType = "music",
                                    authToken = user.token,
                                    userId = user.userId,
                                    username = user.username
                                )
                                viewModel.setupWebSocketManager(webSocketManager)
                                viewModel.joinMusicRoom(roomId)
                            }
                        }

                        DisposableEffect(Unit) {
                            onDispose {
                                webSocketManager.disconnect()
                                viewModel.leaveRoom(roomId, "music")
                            }
                        }

                        MusicRoomDetailScreen(
                            navController = navController,
                            roomId = roomId,
                            webSocketManager = webSocketManager,
                            viewModel = viewModel,
                            currentUser = currentUser
                        )
                    }

                    composable("SettingsScreen") { SettingsScreen() }
                }
            }
        }
    }

    @Composable
    private fun <T : BaseRoomViewModel> RoomNavigationHandler(
        navController: NavController,
        viewModel: T
    ) {
        LaunchedEffect(viewModel.navigateToRoom) {
            viewModel.navigateToRoom.collect { roomId ->
                roomId?.let {
                    val route = when (viewModel) {
                        is MeditationViewModel -> "LiveMeditationSession/$roomId"
                        is MusicViewModel -> "MusicRoom/$roomId"
                        else -> return@let
                    }
                    navController.navigate(route) {
                        popUpTo(navController.currentBackStackEntry?.destination?.route ?: return@navigate) {
                            inclusive = true
                        }
                    }
                    viewModel.onRoomNavigated()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocketManager.disconnect()
    }
}