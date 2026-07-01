package com.smartse.papazon_dash.ui.screens.signin

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.OutlinedTextField
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartse.papazon_dash.data.model.UserRole

private const val TAG = "PapazonAuth"

@Composable
fun SignInScreen(
    onSignInSuccess: () -> Unit,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    var selectedRole by remember { mutableStateOf(UserRole.MASTER) }
    var nameInput by remember { mutableStateOf("") }
    var showRoleDialog by remember { mutableStateOf(false) }
    val currentUser by viewModel.currentUser.collectAsState()

    // signInAnonymously()は非同期 — currentUserがnon-nullになってから遷移する
    LaunchedEffect(currentUser) {
        Log.d(TAG, "SignInScreen LaunchedEffect: currentUser=$currentUser")
        if (currentUser != null) {
            Log.d(TAG, "SignInScreen: currentUser non-null, calling onSignInSuccess()")
            onSignInSuccess()
        }
    }

    if (showRoleDialog) {
        AlertDialog(
            onDismissRequest = { showRoleDialog = false },
            title = { Text("役割を選択") },
            text = {
                Column {
                    Text("どちらの役割でサインインしますか？", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedRole == UserRole.MASTER,
                            onClick = {
                                Log.d(TAG, "master_selected: 奥様（マスター）選択")
                                selectedRole = UserRole.MASTER
                            },
                            label = { Text("奥様（マスター）") },
                        )
                        FilterChip(
                            selected = selectedRole == UserRole.MEMBER,
                            onClick = {
                                Log.d(TAG, "member_selected: 旦那（メンバー）選択")
                                selectedRole = UserRole.MEMBER
                            },
                            label = { Text("旦那（メンバー）") },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("あなたの名前（必須）") },
                        placeholder = { Text(if (selectedRole == UserRole.MASTER) "例: はなこ" else "例: ひろし") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = nameInput.isNotBlank(),
                    onClick = {
                        Log.d(TAG, "SignInScreen: サインインボタンtap role=$selectedRole name=${nameInput.trim()}")
                        showRoleDialog = false
                        viewModel.signIn(selectedRole, nameInput.trim())
                        nameInput = ""
                        // onSignInSuccess()はLaunchedEffect(currentUser)が担う
                    },
                ) { Text("サインイン") }
            },
            dismissButton = {
                TextButton(onClick = {
                    Log.d(TAG, "dialog_cancel_clicked: キャンセルボタンtap")
                    showRoleDialog = false
                    nameInput = ""
                }) { Text("キャンセル") }
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text("🛒", fontSize = 64.sp)
            Text(
                "papazon-dash",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "夫婦のお使いリアルタイム共有アプリ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    Log.d(TAG, "button_tapped: Firebase匿名認証ボタンがtapされた")
                    showRoleDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text("Firebase匿名認証でサインイン", fontSize = 16.sp)
            }
        }
    }
}
