package com.riviets.freelock

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }
}

fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}

fun hasOverlayPermission(context: Context): Boolean {
    return Settings.canDrawOverlays(context)
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings = remember { SettingsManager(context) }

    var apps by remember { mutableStateOf(getInstalledApps(context, settings)) }
    var selectedTime by remember { mutableStateOf(settings.getUnlockTime()) }
    var balance by remember { mutableLongStateOf(settings.getEarnedTime()) }
    var searchQuery by remember { mutableStateOf("") }
    val timeOptions = listOf(0.1f, 0.25f, 0.5f, 1f)

    // Автоматичне оновлення балансу, коли екран стає активним (наприклад, після камери)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                balance = settings.getEarnedTime()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val filteredApps = apps.filter { it.name.contains(searchQuery, ignoreCase = true) }

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {

        // Блок балансу та тестової навігації
        // Блок балансу та навігації до камери
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Доступний баланс: $balance сек", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))

                // Змінена кнопка: тепер вона веде одразу до віджимань
                Button(onClick = {
                    val intent = Intent(context, CameraActivity::class.java)
                    context.startActivity(intent)
                }) {
                    Text("Збільшити баланс (Камера)")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Скільки часу дає 1 віджимання (хв):", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            timeOptions.forEach { time ->
                Button(
                    onClick = {
                        selectedTime = time
                        settings.saveUnlockTime(time)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTime == time) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text(time.toString())
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Пошук додатків...") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(filteredApps) { app ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = app.name, modifier = Modifier.weight(1f))
                    Switch(
                        checked = app.isBlocked,
                        onCheckedChange = { isChecked ->
                            apps = apps.map { if (it.packageName == app.packageName) it.copy(isBlocked = isChecked) else it }
                            val currentBlocked = settings.getBlockedApps().toMutableSet()
                            if (isChecked) currentBlocked.add(app.packageName) else currentBlocked.remove(app.packageName)
                            settings.saveBlockedApps(currentBlocked)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (!hasUsageStatsPermission(context)) {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                } else if (!hasOverlayPermission(context)) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    context.startActivity(intent)
                } else {
                    val intent = Intent(context, AppBlockerService::class.java)
                    context.startForegroundService(intent)
                    Toast.makeText(context, "Блокувальник запущено!", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Запустити блокувальник")
        }
    }
}