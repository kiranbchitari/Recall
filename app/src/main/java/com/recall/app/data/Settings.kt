package com.recall.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class Accent { VIOLET, INDIGO, TEAL, ROSE }

data class AppSettings(
    val name: String = "",
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.VIOLET,
    /** Notifications for reminders the user set themselves. */
    val reminders: Boolean = true,
    val dailyReview: Boolean = false,
    /** Minutes after midnight for the daily review nudge. */
    val dailyReviewAt: Int = 20 * 60,
    val forgottenNudge: Boolean = false,
    /** Days an item can sit untouched before Forgotten Radar picks it up. */
    val quietDays: Int = 5,
    val driveEnabled: Boolean = false,
    val autoBackup: Boolean = false,
    val lastBackupAt: Long = 0,
    val lastBackupError: String = "",
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val name = stringPreferencesKey("name")
        val theme = stringPreferencesKey("theme")
        val accent = stringPreferencesKey("accent")
        val reminders = booleanPreferencesKey("reminders")
        val dailyReview = booleanPreferencesKey("daily_review")
        val dailyReviewAt = intPreferencesKey("daily_review_at")
        val forgottenNudge = booleanPreferencesKey("forgotten_nudge")
        val quietDays = intPreferencesKey("quiet_days")
        val driveEnabled = booleanPreferencesKey("drive_enabled")
        val autoBackup = booleanPreferencesKey("auto_backup")
        val lastBackupAt = longPreferencesKey("last_backup_at")
        val lastBackupError = stringPreferencesKey("last_backup_error")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map(::read)

    private fun read(p: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            name = p[Keys.name] ?: d.name,
            theme = enumOr(p[Keys.theme], d.theme),
            accent = enumOr(p[Keys.accent], d.accent),
            reminders = p[Keys.reminders] ?: d.reminders,
            dailyReview = p[Keys.dailyReview] ?: d.dailyReview,
            dailyReviewAt = p[Keys.dailyReviewAt] ?: d.dailyReviewAt,
            forgottenNudge = p[Keys.forgottenNudge] ?: d.forgottenNudge,
            quietDays = p[Keys.quietDays] ?: d.quietDays,
            driveEnabled = p[Keys.driveEnabled] ?: d.driveEnabled,
            autoBackup = p[Keys.autoBackup] ?: d.autoBackup,
            lastBackupAt = p[Keys.lastBackupAt] ?: d.lastBackupAt,
            lastBackupError = p[Keys.lastBackupError] ?: d.lastBackupError,
        )
    }

    suspend fun current(): AppSettings = flow.first()

    suspend fun update(transform: (AppSettings) -> AppSettings): AppSettings {
        var result = AppSettings()
        context.dataStore.edit { p ->
            val s = transform(read(p))
            result = s
            p[Keys.name] = s.name
            p[Keys.theme] = s.theme.name
            p[Keys.accent] = s.accent.name
            p[Keys.reminders] = s.reminders
            p[Keys.dailyReview] = s.dailyReview
            p[Keys.dailyReviewAt] = s.dailyReviewAt
            p[Keys.forgottenNudge] = s.forgottenNudge
            p[Keys.quietDays] = s.quietDays
            p[Keys.driveEnabled] = s.driveEnabled
            p[Keys.autoBackup] = s.autoBackup
            p[Keys.lastBackupAt] = s.lastBackupAt
            p[Keys.lastBackupError] = s.lastBackupError
        }
        return result
    }

    private inline fun <reified T : Enum<T>> enumOr(value: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: default
}
