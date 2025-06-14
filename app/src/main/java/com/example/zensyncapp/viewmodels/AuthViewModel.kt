package com.example.zensyncapp.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zensyncapp.ApiClient
import com.example.zensyncapp.models.AuthResponse
import com.example.zensyncapp.models.LoginRequest
import com.example.zensyncapp.models.RegisterRequest
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    private val _currentUser = MutableStateFlow<AuthResponse?>(null)
    val currentUser: StateFlow<AuthResponse?> = _currentUser

    sealed class AuthState {
        object Idle : AuthState()
        object Loading : AuthState()
        data class Success(val user: AuthResponse) : AuthState()
        data class Error(val message: String) : AuthState()
    }

    fun register(username: String, email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                if (username.isBlank() || email.isBlank() || password.isBlank()) {
                    _authState.value = AuthState.Error("Все поля обязательны")
                    return@launch
                }

                val response = ApiClient.httpClient.post("/api/auth/register") {
                    contentType(ContentType.Application.Json)
                    setBody(RegisterRequest(username, email, password))
                }

                when (response.status) {
                    HttpStatusCode.Created -> {
                        val authResponse = response.body<AuthResponse>()
                        _authState.value = AuthState.Success(authResponse)
                        _currentUser.value = authResponse
                        ApiClient.setAuthToken(authResponse.token)
                    }
                    else -> {
                        val errorText = response.bodyAsText()
                        _authState.value = AuthState.Error(errorText ?: "Ошибка регистрации")
                    }
                }
            } catch (e: ClientRequestException) {
                val errorText = e.response.bodyAsText()
                _authState.value = AuthState.Error(errorText ?: "Ошибка запроса")
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Неизвестная ошибка")
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val response = ApiClient.httpClient.post("/api/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody(LoginRequest(email, password))
                }

                when (response.status) {
                    HttpStatusCode.OK -> {
                        val authResponse = response.body<AuthResponse>()
                        _authState.value = AuthState.Success(authResponse)
                        _currentUser.value = authResponse
                        ApiClient.setAuthToken(authResponse.token)
                    }
                    else -> {
                        val errorText = response.bodyAsText()
                        _authState.value = AuthState.Error(errorText ?: "Ошибка входа")
                    }
                }
            } catch (e: ClientRequestException) {
                val errorText = e.response.bodyAsText()
                _authState.value = AuthState.Error(errorText ?: "Неверные данные")
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Ошибка соединения")
            }
        }
    }

    fun logout() {
        _currentUser.value = null
        _authState.value = AuthState.Idle
        ApiClient.clearAuthToken()
    }
}