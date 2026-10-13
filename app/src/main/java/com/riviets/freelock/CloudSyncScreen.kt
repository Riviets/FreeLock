package com.riviets.freelock

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun CloudSyncScreen(onBack: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isOnline by remember { mutableStateOf(false) }
    var cloudBalance by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }

    val userId = 1

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Статус сервера: ${if (isOnline) "Онлайн 🟢" else "Офлайн 🔴"}",
                color = if (isOnline) Color.Green else Color.Red,
                style = MaterialTheme.typography.titleLarge)

            Spacer(modifier = Modifier.height(16.dp))

            Text("Глобальний баланс у хмарі: $cloudBalance хв", style = MaterialTheme.typography.headlineMedium)

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    coroutineScope.launch {
                        isLoading = true
                        try {
                            val status = RetrofitClient.api.getUserStatus(userId)
                            cloudBalance = status.balance
                            isOnline = true
                            snackbarHostState.showSnackbar("Синхронізація успішна!")
                        } catch (e: Exception) {
                            isOnline = false
                            snackbarHostState.showSnackbar("Помилка підключення: ${e.localizedMessage}")
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("Примусова синхронізація (Force Sync)")
                }
            }
        }
    }
}
