package com.holymanzion.simplenotepro.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class SortOrder(val label: String) {
    MODIFIED("Date modified"),
    CREATED("Date created"),
    TITLE("Title (A–Z)"),
}

enum class BackupFrequency(val label: String, val days: Long) {
    OFF("Off", 0),
    DAILY("Daily", 1),
    WEEKLY("Weekly", 7),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val gridLayout: Boolean = true,
    val sortOrder: SortOrder = SortOrder.MODIFIED,
    val checkedToBottom: Boolean = true,
    val appLock: Boolean = false,
    /** Folder (a SAF tree URI) that automatic backups are written to. */
    val backupFolder: String? = null,
    val backupFrequency: BackupFrequency = BackupFrequency.OFF,
    val lastBackupAt: Long? = null,
    /** Why the last automatic backup failed, or null if it worked. */
    val lastBackupError: String? = null,
)

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = state.asStateFlow()

    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(state.value)
        state.value = next
        prefs.edit()
            .putString(KEY_THEME, next.themeMode.name)
            .putBoolean(KEY_DYNAMIC, next.dynamicColor)
            .putBoolean(KEY_GRID, next.gridLayout)
            .putString(KEY_SORT, next.sortOrder.name)
            .putBoolean(KEY_CHECKED_BOTTOM, next.checkedToBottom)
            .putBoolean(KEY_APP_LOCK, next.appLock)
            .putString(KEY_BACKUP_FOLDER, next.backupFolder)
            .putString(KEY_BACKUP_FREQUENCY, next.backupFrequency.name)
            .putLong(KEY_LAST_BACKUP, next.lastBackupAt ?: 0L)
            .putString(KEY_BACKUP_ERROR, next.lastBackupError)
            .apply()
    }

    private fun read() = AppSettings(
        themeMode = enumOrDefault(prefs.getString(KEY_THEME, null), ThemeMode.SYSTEM),
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC, false),
        gridLayout = prefs.getBoolean(KEY_GRID, true),
        sortOrder = enumOrDefault(prefs.getString(KEY_SORT, null), SortOrder.MODIFIED),
        checkedToBottom = prefs.getBoolean(KEY_CHECKED_BOTTOM, true),
        appLock = prefs.getBoolean(KEY_APP_LOCK, false),
        backupFolder = prefs.getString(KEY_BACKUP_FOLDER, null),
        backupFrequency = enumOrDefault(prefs.getString(KEY_BACKUP_FREQUENCY, null), BackupFrequency.OFF),
        lastBackupAt = prefs.getLong(KEY_LAST_BACKUP, 0L).takeIf { it > 0 },
        lastBackupError = prefs.getString(KEY_BACKUP_ERROR, null),
    )

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_GRID = "grid_layout"
        const val KEY_SORT = "sort_order"
        const val KEY_CHECKED_BOTTOM = "checked_to_bottom"
        const val KEY_APP_LOCK = "app_lock"
        const val KEY_BACKUP_FOLDER = "backup_folder"
        const val KEY_BACKUP_FREQUENCY = "backup_frequency"
        const val KEY_LAST_BACKUP = "last_backup_at"
        const val KEY_BACKUP_ERROR = "last_backup_error"
    }
}
