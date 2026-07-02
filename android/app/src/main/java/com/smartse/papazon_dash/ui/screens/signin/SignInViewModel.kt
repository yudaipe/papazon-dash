package com.smartse.papazon_dash.ui.screens.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartse.papazon_dash.data.model.User
import com.smartse.papazon_dash.data.model.UserRole
import com.smartse.papazon_dash.data.repository.FirebaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthMode { REGISTER, LOGIN }

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val repository: FirebaseRepository,
) : ViewModel() {

    val currentUser: StateFlow<User?> = repository.currentUser

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun createAccount(email: String, password: String, role: UserRole, name: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            repository.createAccount(email, password, role, name) { success, error ->
                _uiState.value = if (success) AuthUiState.Idle
                else AuthUiState.Error(error ?: "登録に失敗しました")
            }
        }
    }

    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            repository.signInWithEmail(email, password) { success, error ->
                _uiState.value = if (success) AuthUiState.Idle
                else AuthUiState.Error(error ?: "ログインに失敗しました")
            }
        }
    }

    fun clearError() {
        _uiState.value = AuthUiState.Idle
    }
}
