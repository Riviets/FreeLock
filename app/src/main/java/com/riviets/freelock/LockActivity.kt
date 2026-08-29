package com.riviets.freelock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

class LockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageName = intent.getStringExtra("BLOCKED_PACKAGE") ?: ""
        val settings = SettingsManager(this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Використовуємо mutableLongStateOf для коректної роботи з Long
                    var balance by remember { mutableLongStateOf(settings.getEarnedTime()) }
                    var sliderValue by remember { mutableFloatStateOf(if (balance >= 15f) 15f else balance.toFloat()) }
                    val context = LocalContext.current

                    // Обробка системної кнопки "Назад", щоб не повертало в заблокований додаток
                    BackHandler {
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(homeIntent)
                    }

                    Column(
                        // systemBarsPadding гарантує, що контент не сховається під годинник чи виріз екрана
                        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Додаток заблоковано!", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Твій баланс: $balance сек", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(32.dp))

                        // Захист від помилки, якщо баланс менший за 15
                        val maxTime = maxOf(balance.toFloat(), 15f)

                        Text("Вибрано: ${sliderValue.roundToInt()} сек")
                        Slider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                            valueRange = 0f..maxTime,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Button(
                            onClick = {
                                val timeToSpend = sliderValue.toLong()
                                if (settings.spendTime(timeToSpend)) {
                                    AppBlockerService.unlockApp(packageName, timeToSpend)
                                    finish() // Закриваємо LockActivity, пускаємо в додаток
                                }
                            },
                            enabled = balance >= sliderValue.toLong() && sliderValue > 0f,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text("Витратити ${sliderValue.roundToInt()} сек")
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Button(
                            onClick = {
                                val intent = Intent(this@LockActivity, CameraActivity::class.java)
                                startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Віджиматися (заробити час)")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Кнопка для повернення в головне меню додатка
                        TextButton(
                            onClick = {
                                val intent = Intent(this@LockActivity, MainActivity::class.java).apply {
                                    // Очищаємо стек, щоб не накопичувалися відкриті вікна
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                                startActivity(intent)
                            }
                        ) {
                            Text("Повернутися до головного екрана")
                        }
                    }
                }
            }
        }
    }
}