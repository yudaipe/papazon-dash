package com.smartse.papazon_dash.ui.state

import androidx.lifecycle.ViewModel
import com.smartse.papazon_dash.data.model.Item
import com.smartse.papazon_dash.data.model.PairInfo
import com.smartse.papazon_dash.data.model.User
import com.smartse.papazon_dash.data.repository.FirebaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    repository: FirebaseRepository,
) : ViewModel() {
    val currentUser: StateFlow<User?> = repository.currentUser
    val isPaired: StateFlow<Boolean> = repository.isPaired
    val pairInfo: StateFlow<PairInfo?> = repository.pairInfo
    val openItems: StateFlow<List<Item>> = repository.items
    val historyItems: StateFlow<List<Item>> = repository.historyItems
}
