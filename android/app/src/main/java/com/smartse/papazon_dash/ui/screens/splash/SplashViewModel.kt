package com.smartse.papazon_dash.ui.screens.splash

import androidx.lifecycle.ViewModel
import com.smartse.papazon_dash.data.model.User
import com.smartse.papazon_dash.data.repository.FirebaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val repository: FirebaseRepository,
) : ViewModel() {

    val currentUser: StateFlow<User?> = repository.currentUser

    private val _isCheckComplete = MutableStateFlow(false)
    val isCheckComplete: StateFlow<Boolean> = _isCheckComplete.asStateFlow()

    init {
        repository.tryAutoSignIn { _isCheckComplete.value = true }
    }
}
