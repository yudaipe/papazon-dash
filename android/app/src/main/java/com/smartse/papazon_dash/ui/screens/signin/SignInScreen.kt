package com.smartse.papazon_dash.ui.screens.signin

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartse.papazon_dash.data.model.UserRole

private const val TAG = "PapazonAuth"

@Composable
fun SignInScreen(
    onNeedsPairing: () -> Unit,
    onAlreadyPaired: (UserRole) -> Unit,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var mode by remember { mutableStateOf(AuthMode.REGISTER) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(UserRole.MASTER) }

    // Navigate when sign-in/registration succeeds
    LaunchedEffect(currentUser) {
        val user = currentUser ?: return@LaunchedEffect
        Log.d(TAG, "SignInScreen: currentUser updated uid=${user.uid} pairId=${user.pairId}")
        if (user.pairId != null) onAlreadyPaired(user.role)
        else onNeedsPairing()
    }

    val isLoading = uiState is AuthUiState.Loading
    val errorMsg = (uiState as? AuthUiState.Error)?.message

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))

        Text("🛒", fontSize = 56.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "papazon-dash",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "夫婦のお使いリアルタイム共有アプリ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(32.dp))

        // Mode tab (新規登録 / ログイン)
        val tabs = listOf("新規登録", "ログイン")
        TabRow(selectedTabIndex = mode.ordinal) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = mode.ordinal == index,
                    onClick = {
                        mode = if (index == 0) AuthMode.REGISTER else AuthMode.LOGIN
                        viewModel.clearError()
                    },
                    text = { Text(title) },
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Email field
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; viewModel.clearError() },
            label = { Text("メールアドレス") },
            placeholder = { Text("例: hanako@example.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
        )

        Spacer(Modifier.height(12.dp))

        // Password field
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; viewModel.clearError() },
            label = { Text("パスワード（6文字以上）") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
        )

        // Register-only fields
        if (mode == AuthMode.REGISTER) {
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("あなたの名前") },
                placeholder = { Text(if (selectedRole == UserRole.MASTER) "例: はなこ" else "例: ひろし") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                "役割を選択",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start),
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedRole == UserRole.MASTER,
                    onClick = { selectedRole = UserRole.MASTER },
                    label = { Text("奥様（マスター）") },
                )
                FilterChip(
                    selected = selectedRole == UserRole.MEMBER,
                    onClick = { selectedRole = UserRole.MEMBER },
                    label = { Text("旦那（メンバー）") },
                )
            }
        }

        // Error message
        if (errorMsg != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                errorMsg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.Start),
            )
        }

        Spacer(Modifier.height(24.dp))

        val isRegisterEnabled = email.isNotBlank() && password.length >= 6 && !isLoading
        val isLoginEnabled = email.isNotBlank() && password.isNotBlank() && !isLoading

        Button(
            onClick = {
                if (mode == AuthMode.REGISTER) {
                    Log.d(TAG, "register: email=$email role=$selectedRole name=${name.trim()}")
                    viewModel.createAccount(email.trim(), password, selectedRole, name.trim())
                } else {
                    Log.d(TAG, "login: email=$email")
                    viewModel.signInWithEmail(email.trim(), password)
                }
            },
            enabled = if (mode == AuthMode.REGISTER) isRegisterEnabled else isLoginEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(if (mode == AuthMode.REGISTER) "登録する" else "ログイン", fontSize = 16.sp)
            }
        }
    }
}
