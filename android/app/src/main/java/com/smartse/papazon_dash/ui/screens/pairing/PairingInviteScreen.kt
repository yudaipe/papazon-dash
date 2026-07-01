package com.smartse.papazon_dash.ui.screens.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingInviteScreen(
    onPairingComplete: () -> Unit,
    onBack: () -> Unit,
    viewModel: PairingViewModel = hiltViewModel(),
) {
    val code = remember { viewModel.generateInviteCode() }
    val isPaired by viewModel.isPaired.collectAsState()
    var waitingForPair by remember { mutableStateOf(false) }

    LaunchedEffect(isPaired) {
        if (isPaired) {
            onPairingComplete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("招待コード") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            Text(
                "下のコードをパートナーに送ってください",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )

            // 招待コード表示
            Box(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 40.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = code.chunked(3).joinToString(" "),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 8.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Text(
                "このコードはFirestoreに保存されます",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )

            if (!waitingForPair) {
                Button(
                    onClick = { waitingForPair = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text("パートナーを待つ")
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text("パートナーの参加を待っています...")
                        Button(onClick = {
                            viewModel.completeInvite()
                            onPairingComplete()
                        }) {
                            Text("参加を確認してリストへ進む")
                        }
                    }
                }
            }
        }
    }
}
