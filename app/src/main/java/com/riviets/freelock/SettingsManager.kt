package com.riviets.freelock

import android.content.Context
import android.content.pm.PackageManager

data class AppInfo(
    val name: String,
    val packageName: String,
    var isBlocked: Boolean = false
)

class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("FreeLockPrefs", Context.MODE_PRIVATE)

    fun saveBlockedApps(apps: Set<String>) {
        prefs.edit().putStringSet("blocked_apps", apps).apply()
    }

    fun getBlockedApps(): Set<String> {
        return prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()
    }

    fun saveUnlockTime(minutes: Float) {
        prefs.edit().putFloat("unlock_time_mins", minutes).apply()
    }

    fun getUnlockTime(): Float {
        return prefs.getFloat("unlock_time_mins", 1f) // За замовчуванням 1 хвилина
    }

    // Додай ці методи всередину класу SettingsManager
    fun addEarnedTime(seconds: Long) {
        val current = getEarnedTime()
        prefs.edit().putLong("earned_time_seconds", current + seconds).apply()
    }

    fun spendTime(seconds: Long): Boolean {
        val current = getEarnedTime()
        if (current >= seconds) {
            prefs.edit().putLong("earned_time_seconds", current - seconds).apply()
            return true
        }
        return false
    }

    fun getEarnedTime(): Long {
        return prefs.getLong("earned_time_seconds", 0L) // Баланс 0 за замовчуванням
    }
}

fun getInstalledApps(context: Context, settings: SettingsManager): List<AppInfo> {
    val pm = context.packageManager
    val blockedApps = settings.getBlockedApps()

    // Отримуємо всі додатки, які можна запустити (і виключаємо сам наш додаток)
    return pm.getInstalledApplications(PackageManager.GET_META_DATA)
        .filter { pm.getLaunchIntentForPackage(it.packageName) != null && it.packageName != context.packageName }
        .map {
            AppInfo(
                name = it.loadLabel(pm).toString(),
                packageName = it.packageName,
                isBlocked = blockedApps.contains(it.packageName)
            )
        }
        .sortedBy { it.name }
}