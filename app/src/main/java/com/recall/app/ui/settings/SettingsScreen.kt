package com.recall.app.ui.settings

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.recall.app.AppContainer
import com.recall.app.backup.DriveAuth
import com.recall.app.backup.DriveFile
import com.recall.app.data.Accent
import com.recall.app.data.AppSettings
import com.recall.app.data.ThemeMode
import com.recall.app.domain.TimeText
import com.recall.app.notify.Notifier
import com.recall.app.ui.components.IconTile
import com.recall.app.ui.components.PickTimeDialog
import com.recall.app.ui.components.PillChip
import com.recall.app.ui.components.RecallSheet
import com.recall.app.ui.components.ScreenHeader
import com.recall.app.ui.components.SheetRow
import com.recall.app.ui.components.SoftCard
import com.recall.app.ui.components.appViewModel
import com.recall.app.ui.theme.Recall
import com.recall.app.ui.theme.Tone
import com.recall.app.ui.theme.isDark
import com.recall.app.ui.theme.palette
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Something the user has asked to restore from, awaiting their confirmation. */
sealed interface RestoreSource {
    data class Drive(val file: DriveFile) : RestoreSource
    data class LocalFile(val uri: Uri) : RestoreSource
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val settings = container.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    var busy by mutableStateOf(false)
        private set
    var driveBackups by mutableStateOf<List<DriveFile>?>(null)
    var confirm by mutableStateOf<RestoreSource?>(null)

    /** Emits when Google needs the user to choose an account or grant Drive access. */
    val consent = MutableSharedFlow<PendingIntent>(extraBufferCapacity = 1)
    private var afterConsent: (suspend (String) -> Unit)? = null

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { container.settings.update(transform) }
    }

    fun backupNow(context: Context) = withDrive(context) { token ->
        val count = container.backup.backupToDrive(token)
        container.messages.show(if (count == 1) "Backed up 1 item to Google Drive" else "Backed up $count items to Google Drive")
    }

    fun setAutoBackup(context: Context, enabled: Boolean) {
        if (!enabled) return update { it.copy(autoBackup = false) }
        // Turning it on needs Drive access once, so check now rather than failing later in the background.
        withDrive(context) { container.settings.update { it.copy(driveEnabled = true, autoBackup = true) } }
    }

    fun findDriveBackups(context: Context) = withDrive(context) { token ->
        val found = container.backup.listDriveBackups(token)
        if (found.isEmpty()) container.messages.show("No backups in Google Drive yet") else driveBackups = found
    }

    fun restore(context: Context, source: RestoreSource) {
        confirm = null
        when (source) {
            is RestoreSource.Drive -> withDrive(context) { token ->
                restored(container.backup.restoreFromDrive(token, source.file.id))
            }
            is RestoreSource.LocalFile -> guarded { restored(container.backup.importFrom(source.uri)) }
        }
    }

    fun exportTo(uri: Uri) = guarded {
        val count = container.backup.exportTo(uri)
        container.messages.show(if (count == 1) "Saved 1 item to the file" else "Saved $count items to the file")
    }

    fun disconnectDrive() = update { it.copy(driveEnabled = false, autoBackup = false, lastBackupError = "") }

    fun onConsent(context: Context, data: Intent?) {
        val action = afterConsent ?: return
        afterConsent = null
        when (val auth = DriveAuth.fromConsent(context.applicationContext, data)) {
            is DriveAuth.Outcome.Token -> guarded { action(auth.value) }
            is DriveAuth.Outcome.Failed -> container.messages.show(auth.message)
            is DriveAuth.Outcome.NeedsConsent -> container.messages.show("Google Drive wasn't connected.")
        }
    }

    private fun restored(count: Int) {
        container.messages.show(if (count == 1) "Restored 1 item" else "Restored $count items") {
            container.backup.undoRestore()
        }
    }

    private fun withDrive(context: Context, action: suspend (String) -> Unit) {
        val app = context.applicationContext
        viewModelScope.launch {
            busy = true
            when (val auth = DriveAuth.authorize(app)) {
                is DriveAuth.Outcome.Token -> run(action, auth.value)
                is DriveAuth.Outcome.NeedsConsent -> {
                    busy = false
                    afterConsent = action
                    consent.emit(auth.intent)
                }
                is DriveAuth.Outcome.Failed -> {
                    busy = false
                    container.messages.show(auth.message)
                }
            }
        }
    }

    private fun guarded(action: suspend () -> Unit) {
        viewModelScope.launch { run({ action() }, "") }
    }

    private suspend fun run(action: suspend (String) -> Unit, token: String) {
        busy = true
        try {
            action(token)
        } catch (e: Exception) {
            container.messages.show(e.message?.takeIf { it.isNotBlank() } ?: "That didn't work. Nothing was changed.")
        } finally {
            busy = false
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(contentPadding: PaddingValues) {
    val vm = appViewModel { SettingsViewModel(it) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val p = Recall.colors
    val now = remember(settings) { System.currentTimeMillis() }

    var editingName by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        vm.onConsent(context, it.data)
    }
    LaunchedEffect(Unit) {
        vm.consent.collect { consentLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build()) }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::exportTo)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.confirm = RestoreSource.LocalFile(it) }
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun ensureNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canNotify(context)) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader("Me", "Make it yours.", Icons.Outlined.Person)
        Spacer(Modifier.height(2.dp))

        Section("You") {
            ActionRow(
                Icons.Outlined.Person, p.violet,
                title = settings.name.ifBlank { "Add your name" },
                subtitle = "Used for the greeting on Home",
                onClick = { editingName = true },
            )
        }

        Section("Appearance") {
            Label("Theme")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(ThemeMode.SYSTEM to "Match phone", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark").forEach { (mode, label) ->
                    PillChip(label, settings.theme == mode, { vm.update { it.copy(theme = mode) } })
                }
            }
            Label("Accent")
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                val dark = isDark(settings.theme)
                Accent.entries.forEach { accent ->
                    val color = palette(accent, dark).primary
                    Box(
                        Modifier
                            .size(48.dp)
                            .then(if (settings.accent == accent) Modifier.border(2.dp, color, CircleShape) else Modifier)
                            .padding(5.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable(role = Role.RadioButton) { vm.update { it.copy(accent = accent) } }
                            .semantics { contentDescription = accent.name.lowercase().replaceFirstChar(Char::uppercase) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (settings.accent == accent) {
                            Icon(Icons.Rounded.Check, null, tint = palette(accent, dark).onPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        Section("Forgotten Radar") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.Radar, p.violet, size = 42.dp)
                Spacer(Modifier.width(14.dp))
                Text(
                    "Bring things back when I haven't looked at them for",
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.ink,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(3, 5, 7, 14).forEach { days ->
                    PillChip("$days days", settings.quietDays == days, { vm.update { it.copy(quietDays = days) } })
                }
            }
        }

        Section("Notifications") {
            SwitchRow(
                Icons.Outlined.NotificationsNone, p.amber, "Reminders",
                "Only for reminders you set yourself",
                settings.reminders,
            ) { on -> vm.update { it.copy(reminders = on) }; if (on) ensureNotifications() }
            SwitchRow(
                Icons.Outlined.AutoAwesome, p.violet, "Daily review nudge",
                "One quiet note a day, only when something is waiting",
                settings.dailyReview,
            ) { on -> vm.update { it.copy(dailyReview = on) }; if (on) ensureNotifications() }
            if (settings.dailyReview) {
                ActionRow(
                    Icons.Outlined.Schedule, p.blue,
                    title = TimeText.minutesOfDay(settings.dailyReviewAt),
                    subtitle = "When to nudge",
                    onClick = { pickingTime = true },
                )
            }
            SwitchRow(
                Icons.Rounded.Radar, p.pink, "Forgotten items nudge",
                "At most twice a week",
                settings.forgottenNudge,
            ) { on -> vm.update { it.copy(forgottenNudge = on) }; if (on) ensureNotifications() }
        }

        Section("Backup") {
            val status = when {
                settings.lastBackupError.isNotBlank() -> settings.lastBackupError
                settings.lastBackupAt > 0 -> "Last backup ${TimeText.dateTime(settings.lastBackupAt, now).replaceFirstChar { it.lowercase() }}"
                settings.driveEnabled -> "Connected. No backup yet."
                else -> "Optional. Keeps a private copy in your own Drive."
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(
                    when {
                        settings.lastBackupError.isNotBlank() -> Icons.Outlined.CloudOff
                        settings.lastBackupAt > 0 -> Icons.Outlined.CloudDone
                        else -> Icons.Outlined.CloudUpload
                    },
                    if (settings.lastBackupError.isNotBlank()) p.rose else p.blue,
                    size = 42.dp,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Google Drive", style = MaterialTheme.typography.titleSmall, color = p.ink)
                    Text(status, style = MaterialTheme.typography.bodySmall, color = p.inkSoft)
                }
                if (vm.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PillChip("Back up now", false, { if (!vm.busy) vm.backupNow(context) }, icon = Icons.Outlined.CloudUpload)
                PillChip("Restore", false, { if (!vm.busy) vm.findDriveBackups(context) }, icon = Icons.Outlined.Restore)
            }
            SwitchRow(
                Icons.Outlined.Schedule, p.green, "Back up automatically",
                "Once a day on Wi-Fi, when something changed",
                settings.autoBackup,
            ) { on -> vm.setAutoBackup(context, on) }
            if (settings.driveEnabled) {
                TextButton(onClick = vm::disconnectDrive) { Text("Stop using Google Drive") }
            }
            Label("Or keep a copy yourself")
            ActionRow(
                Icons.Outlined.FileDownload, p.violet, "Save a copy to a file", "Anywhere you choose, including other cloud apps",
                onClick = { exportLauncher.launch("recall-backup-${LocalDate.now()}.json") },
            )
            ActionRow(
                Icons.Outlined.FileUpload, p.amber, "Restore from a file", "Replaces what is on this phone",
                onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
            )
        }

        Text(
            "Everything you capture is stored on this phone and works without internet.",
            style = MaterialTheme.typography.bodySmall,
            color = p.inkFaint,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }

    if (editingName) NameSheet(settings.name, onSave = { name -> vm.update { it.copy(name = name) } }, onDismiss = { editingName = false })

    if (pickingTime) {
        PickTimeDialog(settings.dailyReviewAt, onPick = { minutes -> vm.update { it.copy(dailyReviewAt = minutes) } }, onDismiss = { pickingTime = false })
    }

    vm.driveBackups?.let { backups ->
        RecallSheet({ vm.driveBackups = null }, title = "Choose a backup") {
            backups.forEach { file ->
                SheetRow(
                    icon = Icons.Outlined.CloudDone,
                    label = TimeText.dateTime(file.createdAt, now),
                    tone = p.blue,
                    onClick = { vm.driveBackups = null; vm.confirm = RestoreSource.Drive(file) },
                    hint = "${(file.size / 1024).coerceAtLeast(1)} KB",
                )
            }
        }
    }

    vm.confirm?.let { source ->
        AlertDialog(
            onDismissRequest = { vm.confirm = null },
            title = { Text("Replace what's on this phone?") },
            text = {
                Text(
                    when (source) {
                        is RestoreSource.Drive ->
                            "Everything in the app now will be replaced by the backup from ${TimeText.dateTime(source.file.createdAt, now)}."
                        is RestoreSource.LocalFile -> "Everything in the app now will be replaced by the contents of that file."
                    } + " A copy of what you have now is kept so you can undo straight afterwards."
                )
            },
            confirmButton = { TextButton(onClick = { vm.restore(context, source) }) { Text("Replace") } },
            dismissButton = { TextButton(onClick = { vm.confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = Recall.colors.ink,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp).semantics { heading() },
        )
        SoftCard(Modifier.fillMaxWidth(), elevation = 6.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Recall.colors.inkSoft)
}

@Composable
private fun ActionRow(icon: ImageVector, tone: Tone, title: String, subtitle: String, onClick: () -> Unit) {
    val p = Recall.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tone, size = 42.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = p.ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = p.inkSoft)
        }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    tone: Tone,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val p = Recall.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Switch) { onChange(!checked) }
            .defaultMinSize(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tone, size = 42.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = p.ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = p.inkSoft)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun NameSheet(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val p = Recall.colors
    var text by rememberSaveable { mutableStateOf(current) }
    RecallSheet(onDismiss, title = "What should we call you?") {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surfaceSoft)
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            if (text.isEmpty()) Text("Your name", style = MaterialTheme.typography.bodyLarge, color = p.inkFaint)
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(30) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = p.ink),
                cursorBrush = SolidColor(p.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onSave(text.trim()); onDismiss() }) { Text("Save") }
        }
    }
}
