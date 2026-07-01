package com.smartse.papazon_dash.ui.screens.pairing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingJoinScreen(
    onJoinSuccess: () -> Unit,
    onBack: () -> Unit,
    viewModel: PairingViewModel = hiltViewModel(),
) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("コードを入力") },
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(24.dp))

            Text("🔗", fontSize = 48.sp)
            Text(
                "招待コードを入力",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "パートナーから受け取った6桁のコードを入力してください",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = code,
                onValueChange = { if (it.length <= 6) code = it.filter { c -> c.isDigit() } },
                label = { Text("招待コード (6桁)") },
                placeholder = { Text("483921") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    isLoading = true
                    error = null
                    viewModel.joinByCode(code) { success ->
                        if (success) {
                            onJoinSuccess()
                        } else {
                            error = "コードが一致しませんでした"
                            isLoading = false
                        }
                    }
                },
                enabled = code.length == 6 && !isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("ペアリング開始")
                }
            }
        }
    }
}
