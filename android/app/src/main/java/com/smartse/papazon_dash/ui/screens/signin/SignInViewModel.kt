package com.smartse.papazon_dash.ui.screens.signin

import androidx.lifecycle.ViewModel
import com.smartse.papazon_dash.data.model.UserRole
import com.smartse.papazon_dash.data.repository.FirebaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val repository: FirebaseRepository,
) : ViewModel() {

    val currentUser = repository.currentUser

    fun signIn(role: UserRole, name: String = "") {
        repository.signIn(role, name)
    }
}
