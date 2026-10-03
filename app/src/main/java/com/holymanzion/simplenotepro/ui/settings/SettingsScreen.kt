package com.holymanzion.simplenotepro.ui.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Schedule
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.FragmentActivity
import com.holymanzion.simplenotepro.backup.AutoBackupWorker
import com.holymanzion.simplenotepro.data.BackupFrequency
import com.holymanzion.simplenotepro.lock.DeviceAuth
import com.holymanzion.simplenotepro.ui.components.fullDateTime
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.holymanzion.simplenotepro.AppContainer
import com.holymanzion.simplenotepro.BuildConfig
import com.holymanzion.simplenotepro.data.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit, onOpenPrivacyPolicy: () -> Unit) {
    val context = LocalContext.current
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var themeDialog by remember { mutableStateOf(false) }

    var frequencyDialog by remember { mutableStateOf(false) }

    fun toggleAppLock() {
        val activity = context.findActivity() ?: return
        if (!settings.appLock && !DeviceAuth.isAvailable(context)) {
            scope.launch { snackbar.showSnackbar("Set up a screen lock or fingerprint in your phone's settings first") }
            return
        }
        // Confirm it's really the owner, both when turning the lock on and off.
        DeviceAuth.authenticate(
            activity,
            title = if (settings.appLock) "Turn off app lock" else "Turn on app lock",
            onSuccess = { container.settings.update { it.copy(appLock = !it.appLock) } },
        )
    }

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
        container.settings.update {
            it.copy(
                backupFolder = uri.toString(),
                lastBackupError = null,
                // Choosing a folder is a clear sign automatic backups are wanted.
                backupFrequency = if (it.backupFrequency == BackupFrequency.OFF) BackupFrequency.DAILY else it.backupFrequency,
            )
        }
        AutoBackupWorker.schedule(context, container.settings.settings.value.backupFrequency)
        AutoBackupWorker.runNow(context)
    }
    val folderName = remember(settings.backupFolder) {
        settings.backupFolder?.let { runCatching { DocumentFile.fromTreeUri(context, Uri.parse(it))?.name }.getOrNull() }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val message = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { container.backup.export(it) }
                        ?: error("Could not open file")
                }
            }.fold({ "Exported $it notes" }, { "Export failed: ${it.message}" })
            snackbar.showSnackbar(message)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val message = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { container.backup.import(it) }
                        ?: error("Could not open file")
                }
            }.fold({ "Imported $it notes" }, { "That file isn't a Simple Note Pro backup" })
            // Backups from before text search have pictures without their text yet.
            container.appScope.launch { container.repository.readPendingImageText() }
            snackbar.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader("Appearance")
            SettingRow(
                icon = Icons.Outlined.DarkMode,
                title = "Theme",
                subtitle = when (settings.themeMode) {
                    ThemeMode.SYSTEM -> "System default"
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.DARK -> "Dark"
                },
                onClick = { themeDialog = true },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SettingRow(
                    icon = Icons.Outlined.ColorLens,
                    title = "Dynamic color",
                    subtitle = "Match the app's colors to your wallpaper",
                    checked = settings.dynamicColor,
                    onClick = { container.settings.update { it.copy(dynamicColor = !it.dynamicColor) } },
                )
            }

            SectionHeader("Notes")
            SettingRow(
                icon = Icons.Outlined.Checklist,
                title = "Move checked items to bottom",
                subtitle = "Keep completed checklist items out of the way",
                checked = settings.checkedToBottom,
                onClick = { container.settings.update { it.copy(checkedToBottom = !it.checkedToBottom) } },
            )

            SectionHeader("Security")
            SettingRow(
                icon = Icons.Outlined.Lock,
                title = "App lock",
                subtitle = "Ask for your fingerprint, face or screen lock when opening the app, " +
                    "and hide notes from screenshots and recent apps",
                checked = settings.appLock,
                onClick = ::toggleAppLock,
            )

            SectionHeader("Automatic backup")
            SettingRow(
                icon = Icons.Outlined.Folder,
                title = "Backup folder",
                subtitle = folderName ?: "Choose a folder, for example one synced by Google Drive",
                onClick = { folderLauncher.launch(null) },
            )
            SettingRow(
                icon = Icons.Outlined.Schedule,
                title = "Frequency",
                subtitle = settings.backupFrequency.label + if (settings.backupFrequency != BackupFrequency.OFF) " · keeps the last 7" else "",
                onClick = { frequencyDialog = true },
            )
            if (settings.backupFolder != null) {
                SettingRow(
                    icon = if (settings.lastBackupError != null) Icons.Outlined.ErrorOutline else Icons.Outlined.CloudDone,
                    title = "Back up now",
                    subtitle = when {
                        settings.lastBackupError != null -> "Last backup failed: ${settings.lastBackupError}"
                        settings.lastBackupAt != null -> "Last backup ${fullDateTime(settings.lastBackupAt!!)}"
                        else -> "No backup yet"
                    },
                    onClick = {
                        AutoBackupWorker.runNow(context)
                        scope.launch { snackbar.showSnackbar("Backing up…") }
                    },
                )
            }

            SectionHeader("Backup file")
            SettingRow(
                icon = Icons.Outlined.Backup,
                title = "Export notes",
                subtitle = "Save all notes, labels and images to a ZIP file",
                onClick = { exportLauncher.launch("simple-note-pro-${LocalDate.now()}.zip") },
            )
            SettingRow(
                icon = Icons.Outlined.Restore,
                title = "Import notes",
                subtitle = "Add notes from a backup file (existing notes are kept)",
                onClick = {
                    importLauncher.launch(arrayOf("application/zip", "application/json", "text/plain", "application/octet-stream"))
                },
            )

            SectionHeader("About")
            SettingRow(
                icon = Icons.Outlined.Info,
                title = "Simple Note Pro",
                subtitle = "Version ${BuildConfig.VERSION_NAME} · Your notes are stored only on this device",
                onClick = null,
            )
            SettingRow(
                icon = Icons.Outlined.PrivacyTip,
                title = "Privacy policy",
                subtitle = "What the app stores, and what it never collects",
                onClick = onOpenPrivacyPolicy,
            )
        }
    }

    if (frequencyDialog) {
        AlertDialog(
            onDismissRequest = { frequencyDialog = false },
            title = { Text("Back up automatically") },
            text = {
                Column {
                    BackupFrequency.entries.forEach { option ->
                        ListItemRadio(option.label, settings.backupFrequency == option) {
                            container.settings.update { it.copy(backupFrequency = option) }
                            AutoBackupWorker.schedule(context, option)
                            frequencyDialog = false
                            if (option != BackupFrequency.OFF && settings.backupFolder == null) folderLauncher.launch(null)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { frequencyDialog = false }) { Text("Cancel") } },
        )
    }
    if (themeDialog) {
        AlertDialog(
            onDismissRequest = { themeDialog = false },
            title = { Text("Theme") },
            text = {
                Column {
                    listOf(ThemeMode.SYSTEM to "System default", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark").forEach { (mode, label) ->
                        ListItemRadio(label, settings.themeMode == mode) {
                            container.settings.update { it.copy(themeMode = mode) }
                            themeDialog = false
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { themeDialog = false }) { Text("Cancel") } },
        )
    }
}

private tailrec fun Context.findActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    checked: Boolean? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = checked?.let { { Switch(checked = it, onCheckedChange = { onClick?.invoke() }) } },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

@Composable
private fun ListItemRadio(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
