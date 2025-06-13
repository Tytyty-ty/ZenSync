package com.example.zensyncapp

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.zensyncapp.viewmodels.AuthViewModel

@Composable
fun WelcomeScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 75.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Image(
            painter = painterResource(id = R.drawable.welcome),
            contentDescription = "WelcomeImage",
            modifier = Modifier
                .size(300.dp)
                .padding(top = 80.dp)
        )
        Text(text = "Добро пожаловать", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(text = "в ZenSync!", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.size(70.dp))
        Text(text = "Выберите один из предложенных вариантов", fontSize = 18.sp)
        Spacer(modifier = Modifier.size(30.dp))
        Button(
            onClick = { navController.navigate("LoginScreen") },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .size(55.dp)
        ) { Text(text = "Вход", fontSize = 16.sp) }
        Spacer(modifier = Modifier.size(15.dp))
        Button(
            onClick = { navController.navigate("RegisterScreen") },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .size(55.dp)
        ) { Text(text = "Регистрация", fontSize = 16.sp) }
    }
}

@Composable
fun LoginScreen(navController: NavController, authViewModel: AuthViewModel = viewModel()) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val authState by authViewModel.authState.collectAsState()

    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthViewModel.AuthState.Success -> {
                navController.navigate("MainHub") {
                    popUpTo("LoginScreen") { inclusive = true }
                }
                Toast.makeText(context, "Добро пожаловать!", Toast.LENGTH_SHORT).show()
            }
            is AuthViewModel.AuthState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Image(
            painter = painterResource(id = R.drawable.login),
            contentDescription = "LoginImage",
            modifier = Modifier
                .size(300.dp)
                .padding(top = 80.dp)
        )
        Text(text = "Вход в аккаунт", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.size(30.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(text = "Введите почту") },
            modifier = Modifier.fillMaxWidth(0.8f)
        )
        Spacer(modifier = Modifier.size(20.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(text = "Введите пароль") },
            modifier = Modifier.fillMaxWidth(0.8f)
        )
        Spacer(modifier = Modifier.size(30.dp))
        Button(
            onClick = { authViewModel.login(email, password) },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .size(55.dp),
            enabled = authState !is AuthViewModel.AuthState.Loading
        ) {
            if (authState is AuthViewModel.AuthState.Loading) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Text(text = "Войти", fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun RegisterScreen(navController: NavController, authViewModel: AuthViewModel = viewModel()) {
    val context = LocalContext.current
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val authState by authViewModel.authState.collectAsState()

    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthViewModel.AuthState.Success -> {
                navController.navigate("MainHub") {
                    popUpTo("LoginScreen") { inclusive = true }
                }
                Toast.makeText(context, "Добро пожаловать!", Toast.LENGTH_SHORT).show()
            }
            is AuthViewModel.AuthState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Image(
            painter = painterResource(id = R.drawable.register),
            contentDescription = "RegisterImage",
            modifier = Modifier
                .size(300.dp)
                .padding(top = 80.dp)
        )
        Text(text = "Регистрация аккаунта", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.size(30.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(text = "Введите никнейм") },
            modifier = Modifier.fillMaxWidth(0.8f)
        )
        Spacer(modifier = Modifier.size(20.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(text = "Введите почту") },
            modifier = Modifier.fillMaxWidth(0.8f)
        )
        Spacer(modifier = Modifier.size(20.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(text = "Введите пароль") },
            modifier = Modifier.fillMaxWidth(0.8f)
        )
        Spacer(modifier = Modifier.size(30.dp))
        Button(
            onClick = { authViewModel.register(username, email, password) },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .size(55.dp),
            enabled = authState !is AuthViewModel.AuthState.Loading
        ) {
            if (authState is AuthViewModel.AuthState.Loading) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Text(text = "Зарегистрироваться", fontSize = 16.sp)
            }
        }
    }
}