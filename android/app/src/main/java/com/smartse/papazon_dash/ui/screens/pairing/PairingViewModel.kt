package com.smartse.papazon_dash.ui.screens.pairing

import androidx.lifecycle.ViewModel
import com.smartse.papazon_dash.data.repository.FirebaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val repository: FirebaseRepository,
) : ViewModel() {

    val isPaired: StateFlow<Boolean> = repository.isPaired

    fun generateInviteCode(): String = repository.generateInviteCode()

    fun completeInvite() = repository.completeInvite()

    fun joinByCode(code: String, onResult: (Boolean) -> Unit) = repository.joinByCode(code, onResult)
}
