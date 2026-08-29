package com.riviets.freelock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class AppBlockerService : Service() {

    companion object {
        var temporarilyUnlockedApp: String? = null
        var unlockedUntil: Long = 0L

        fun unlockApp(packageName: String, seconds: Long) {
            temporarilyUnlockedApp = packageName
            unlockedUntil = System.currentTimeMillis() + (seconds * 1000)
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var settings: SettingsManager
    private var isLockScreenShowing = false

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        settings = SettingsManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMonitoring()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundServiceNotification() {
        val channelId = "AppBlockerChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Pushlock Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Pushlock активний")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun startMonitoring() {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                val topApp = getTopApp()
                val blockedApps = settings.getBlockedApps()

                if (topApp != null && blockedApps.contains(topApp)) {
                    val currentTime = System.currentTimeMillis()
                    val isTemporarilyUnlocked = (topApp == temporarilyUnlockedApp && currentTime < unlockedUntil)

                    if (!isTemporarilyUnlocked) {
                        // Якщо час щойно вийшов
                        if (topApp == temporarilyUnlockedApp && currentTime >= unlockedUntil) {
                            temporarilyUnlockedApp = null
                            unlockedUntil = 0L
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@AppBlockerService, "Час вийшов!", Toast.LENGTH_LONG).show()
                            }
                        }

                        if (!isLockScreenShowing) {
                            isLockScreenShowing = true
                            withContext(Dispatchers.Main) {
                                val intent = Intent(this@AppBlockerService, LockActivity::class.java).apply {
                                    putExtra("BLOCKED_PACKAGE", topApp)
                                    // Обов'язкові прапорці для перехоплення з фону
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                                startActivity(intent)
                            }
                        }
                    } else {
                        isLockScreenShowing = false
                    }
                } else if (topApp != null && topApp != packageName) {
                    isLockScreenShowing = false
                }
                delay(1000)
            }
        }
    }

    private fun getTopApp(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 10, time)
        return stats?.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}