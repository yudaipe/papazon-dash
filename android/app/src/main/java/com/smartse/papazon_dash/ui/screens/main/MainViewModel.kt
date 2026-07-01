package com.smartse.papazon_dash.ui.screens.main

import androidx.lifecycle.ViewModel
import com.smartse.papazon_dash.data.model.Item
import com.smartse.papazon_dash.data.model.User
import com.smartse.papazon_dash.data.repository.FirebaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: FirebaseRepository,
) : ViewModel() {

    val currentUser: StateFlow<User?> = repository.currentUser
    val items: StateFlow<List<Item>> = repository.items
    val historyItems: StateFlow<List<Item>> = repository.historyItems

    fun addItem(name: String) = repository.addItem(name)

    fun toggleItem(itemId: String) = repository.toggleItem(itemId)
}
