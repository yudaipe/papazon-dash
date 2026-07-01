package com.smartse.papazon_dash.ui.screens.pairing

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun PairingScreen(
    onGenerateCode: () -> Unit,
    onEnterCode: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("💑", fontSize = 64.sp)
            Text(
                "ペアリング",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "パートナーとペアリングしてリストを共有しましょう",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("招待する側（奥様）", fontWeight = FontWeight.Bold)
                    Text(
                        "招待コードを生成してパートナーに送ります",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onGenerateCode,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("招待コードを生成")
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("招待される側（旦那）", fontWeight = FontWeight.Bold)
                    Text(
                        "パートナーから受け取ったコードを入力します",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = onEnterCode,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("コードを入力してペアリング")
                    }
                }
            }

        }
    }
}
