package com.smartse.papazon_dash.ui.screens.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartse.papazon_dash.data.model.UserRole
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNeedsPairing: () -> Unit,
    onAlreadyPaired: (UserRole) -> Unit,
    onNotSignedIn: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    val user by viewModel.currentUser.collectAsState()
    val isCheckComplete by viewModel.isCheckComplete.collectAsState()
    var minTimerDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(1200)
        minTimerDone = true
    }

    LaunchedEffect(minTimerDone, isCheckComplete) {
        if (!minTimerDone || !isCheckComplete) return@LaunchedEffect
        val currentUser = user
        when {
            currentUser == null -> onNotSignedIn()
            currentUser.pairId != null -> onAlreadyPaired(currentUser.role)
            else -> onNeedsPairing()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "🛒", fontSize = 72.sp)
            Text(
                text = "papazon-dash",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = "お使いリアルタイム共有",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
