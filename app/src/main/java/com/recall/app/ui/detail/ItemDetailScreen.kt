package com.recall.app.ui.detail

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.recall.app.AppContainer
import com.recall.app.data.Item
import com.recall.app.data.ItemStatus
import com.recall.app.data.Priority
import com.recall.app.data.Repeat
import com.recall.app.data.isSnoozed
import com.recall.app.domain.TimeText
import com.recall.app.notify.Notifier
import com.recall.app.ui.components.IconTile
import com.recall.app.ui.components.PickDateDialog
import com.recall.app.ui.components.PickTimeDialog
import com.recall.app.ui.components.PillChip
import com.recall.app.ui.components.RecallSheet
import com.recall.app.ui.components.SheetRow
import com.recall.app.ui.components.SnoozeSheet
import com.recall.app.ui.components.TypeRow
import com.recall.app.ui.components.appViewModel
import com.recall.app.ui.components.container
import com.recall.app.ui.components.itemVisual
import com.recall.app.ui.components.label
import com.recall.app.ui.components.softShadow
import com.recall.app.ui.theme.Recall
import com.recall.app.ui.theme.Tone
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DetailState(val loaded: Boolean = false, val item: Item? = null, val categories: List<String> = emptyList())

class ItemDetailViewModel(private val container: AppContainer, private val id: Long) : ViewModel() {
    val state = combine(container.repo.observe(id), container.repo.items) { item, all ->
        DetailState(loaded = true, item = item, categories = all.mapNotNull { it.category }.distinct().sorted())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState())

    init {
        // Opening an item counts as looking at it, which is what takes it off the radar.
        container.actions.touch(id)
    }

    fun edit(transform: (Item) -> Item) {
        container.actions.edit(id, transform)
    }

    fun saveText(title: String, notes: String) {
        val current = state.value.item ?: return
        val cleanTitle = title.trim().ifEmpty { current.title }
        if (cleanTitle == current.title && notes == current.notes) return
        edit { it.copy(title = cleanTitle, notes = notes) }
    }
}

private enum class Editing { DUE, REMINDER_DATE, REMINDER_TIME, PRIORITY, CATEGORY, REPEAT, SNOOZE }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemDetailScreen(id: Long, contentPadding: PaddingValues, onBack: () -> Unit) {
    val vm = appViewModel(key = "item-$id") { ItemDetailViewModel(it, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    val item = state.item
    if (item == null) {
        // Deleted (here or elsewhere): nothing left to show.
        if (state.loaded) LaunchedEffect(Unit) { onBack() }
        return
    }

    val p = Recall.colors
    val actions = container().actions
    val messages = container().messages
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val now = remember(item) { System.currentTimeMillis() }
    val open = item.status == ItemStatus.ACTIVE

    var title by rememberSaveable(id) { mutableStateOf(item.title) }
    var notes by rememberSaveable(id) { mutableStateOf(item.notes) }
    val latestTitle by rememberUpdatedState(title)
    val latestNotes by rememberUpdatedState(notes)
    LaunchedEffect(title, notes) {
        delay(400)
        vm.saveText(title, notes)
    }
    DisposableEffect(Unit) { onDispose { vm.saveText(latestTitle, latestNotes) } }

    var editing by remember { mutableStateOf<Editing?>(null) }
    var reminderDate by remember { mutableStateOf<LocalDate?>(null) }

    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) messages.show("Notifications are off, so this reminder will only show inside the app")
    }

    fun leaveAfter(action: () -> Unit) {
        vm.saveText(title, notes)
        action()
        onBack()
    }

    Column(Modifier.fillMaxSize().padding(contentPadding).consumeWindowInsets(contentPadding).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = p.ink, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.weight(1f))
            if (open) {
                IconButton(onClick = { leaveAfter { actions.archive(item) } }) {
                    Icon(Icons.Outlined.Archive, "Archive", tint = p.inkSoft)
                }
            }
            IconButton(onClick = { leaveAfter { actions.delete(item) } }) {
                Icon(Icons.Outlined.Delete, "Delete", tint = p.inkSoft)
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val visual = itemVisual(item)
                IconTile(visual.icon, visual.tone, size = 52.dp)
                Spacer(Modifier.width(14.dp))
                Text(
                    listOfNotNull(
                        "Captured ${TimeText.ago(item.createdAt, now).replaceFirstChar { it.lowercase() }}",
                        when (item.status) {
                            ItemStatus.DONE -> "Done"
                            ItemStatus.ARCHIVED -> "Archived"
                            ItemStatus.DISMISSED -> "Let go"
                            ItemStatus.ACTIVE -> if (item.snoozeCount > 1) "Postponed ${item.snoozeCount} times" else null
                        },
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = p.inkSoft,
                )
            }
            Spacer(Modifier.height(16.dp))

            PlainField(
                value = title,
                onChange = { title = it.replace("\n", " ") },
                placeholder = "What is it?",
                style = MaterialTheme.typography.headlineMedium,
                imeAction = ImeAction.Done,
                onImeDone = { focusManager.clearFocus() },
            )
            Spacer(Modifier.height(10.dp))
            PlainField(
                value = notes,
                onChange = { notes = it },
                placeholder = "Add a note…",
                style = MaterialTheme.typography.bodyLarge,
                minHeight = 56,
            )
            Spacer(Modifier.height(14.dp))
            TypeRow(item.type, onSelect = { type -> vm.edit { it.copy(type = type) } })
            Spacer(Modifier.height(20.dp))

            // Only what has been set is shown; everything else is one tap away below.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (item.isSnoozed(now)) {
                    Property(
                        Icons.Outlined.Snooze, p.violet, "Snoozed", TimeText.returns(item, now) ?: "",
                        onClick = { editing = Editing.SNOOZE },
                        onClear = { actions.wake(item) },
                        clearLabel = "Bring back now",
                    )
                }
                item.dueAt?.let {
                    Property(
                        Icons.Outlined.CalendarToday, p.blue, "Due", TimeText.date(it, now),
                        onClick = { editing = Editing.DUE },
                        onClear = { vm.edit { i -> i.copy(dueAt = null) } },
                    )
                }
                item.reminderAt?.let {
                    Property(
                        Icons.Outlined.NotificationsNone, p.amber, "Reminder", TimeText.dateTime(it, now),
                        onClick = { editing = Editing.REMINDER_DATE },
                        onClear = { vm.edit { i -> i.copy(reminderAt = null) } },
                    )
                }
                if (item.priority != Priority.NONE) {
                    Property(
                        Icons.Outlined.Flag, p.rose, "Priority", item.priority.label,
                        onClick = { editing = Editing.PRIORITY },
                        onClear = { vm.edit { i -> i.copy(priority = Priority.NONE) } },
                    )
                }
                item.category?.let {
                    Property(
                        Icons.Outlined.Sell, p.green, "Category", it,
                        onClick = { editing = Editing.CATEGORY },
                        onClear = { vm.edit { i -> i.copy(category = null) } },
                    )
                }
                if (item.repeat != Repeat.NONE) {
                    Property(
                        Icons.Rounded.Repeat, p.pink, "Repeat", item.repeat.label,
                        onClick = { editing = Editing.REPEAT },
                        onClear = { vm.edit { i -> i.copy(repeat = Repeat.NONE) } },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.dueAt == null) AddChip("Due date") { editing = Editing.DUE }
                if (item.reminderAt == null) AddChip("Reminder") { editing = Editing.REMINDER_DATE }
                if (item.priority == Priority.NONE) AddChip("Priority") { editing = Editing.PRIORITY }
                if (item.category == null) AddChip("Category") { editing = Editing.CATEGORY }
                if (item.repeat == Repeat.NONE) AddChip("Repeat") { editing = Editing.REPEAT }
            }
            Spacer(Modifier.height(24.dp))
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (open) {
                BigButton("Done", Icons.Rounded.Check, filled = true, Modifier.weight(1f)) { leaveAfter { actions.done(item) } }
                BigButton("Later", Icons.Outlined.Schedule, filled = false, Modifier.weight(1f)) { editing = Editing.SNOOZE }
            } else {
                BigButton("Move back to inbox", Icons.Rounded.Replay, filled = true, Modifier.weight(1f)) { actions.reopen(item) }
            }
        }
    }

    val zone = remember { ZoneId.systemDefault() }
    when (editing) {
        // Pickers open on today, so the common case is a single tap on "Set".
        Editing.DUE -> PickDateDialog(
            initial = item.dueAt ?: System.currentTimeMillis(),
            futureOnly = false,
            onPick = { date -> vm.edit { it.copy(dueAt = date.atStartOfDay(zone).toInstant().toEpochMilli()) } },
            onDismiss = { editing = null },
        )
        Editing.REMINDER_DATE -> PickDateDialog(
            initial = item.reminderAt ?: System.currentTimeMillis(),
            futureOnly = true,
            onPick = { reminderDate = it },
            // Picking a day leads straight on to picking the time.
            onDismiss = { editing = if (reminderDate != null) Editing.REMINDER_TIME else null },
        )
        Editing.REMINDER_TIME -> PickTimeDialog(
            initialMinutes = item.reminderAt?.let {
                Instant.ofEpochMilli(it).atZone(zone).toLocalTime().let { t -> t.hour * 60 + t.minute }
            } ?: if (reminderDate == LocalDate.now(zone)) {
                // Today: suggest the next full hour rather than a morning that has already gone.
                (java.time.LocalTime.now(zone).hour + 1).coerceAtMost(23) * 60
            } else {
                9 * 60
            },
            onPick = { minutes ->
                val date = reminderDate ?: LocalDate.now(zone)
                val at = date.atStartOfDay(zone).plusMinutes(minutes.toLong()).toInstant().toEpochMilli()
                if (at <= System.currentTimeMillis()) {
                    messages.show("That time has already passed")
                } else {
                    vm.edit { it.copy(reminderAt = at) }
                    if (Build.VERSION.SDK_INT >= 33 && !Notifier.canNotify(context)) {
                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            },
            onDismiss = { editing = null; reminderDate = null },
        )
        Editing.PRIORITY -> RecallSheet({ editing = null }, title = "Priority") {
            listOf(Priority.HIGH to p.rose, Priority.MEDIUM to p.amber, Priority.LOW to p.blue).forEach { (priority, tone) ->
                SheetRow(Icons.Outlined.Flag, priority.label, tone, onClick = {
                    vm.edit { it.copy(priority = priority) }
                    editing = null
                })
            }
        }
        Editing.REPEAT -> RecallSheet({ editing = null }, title = "Repeat") {
            listOf(Repeat.DAILY, Repeat.WEEKLY, Repeat.MONTHLY, Repeat.YEARLY).forEach { repeat ->
                SheetRow(Icons.Rounded.Repeat, repeat.label, p.pink, onClick = {
                    vm.edit { it.copy(repeat = repeat) }
                    editing = null
                })
            }
            Text(
                "When you mark it done, the next one is created for you.",
                style = MaterialTheme.typography.bodySmall,
                color = p.inkSoft,
                modifier = Modifier.padding(start = 6.dp, top = 8.dp),
            )
        }
        Editing.CATEGORY -> CategorySheet(
            current = item.category,
            existing = state.categories,
            onPick = { category -> vm.edit { it.copy(category = category) } },
            onDismiss = { editing = null },
        )
        Editing.SNOOZE -> SnoozeSheet(
            onPick = { until ->
                editing = null
                leaveAfter { actions.snooze(item, until) }
            },
            onDismiss = { editing = null },
        )
        null -> Unit
    }
}

@Composable
private fun PlainField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    style: androidx.compose.ui.text.TextStyle,
    imeAction: ImeAction = ImeAction.Default,
    onImeDone: () -> Unit = {},
    minHeight: Int = 0,
) {
    val p = Recall.colors
    Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = minHeight.dp)) {
        if (value.isEmpty()) Text(placeholder, style = style, color = p.inkFaint)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = style.copy(color = p.ink),
            cursorBrush = SolidColor(p.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = { onImeDone() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Property(
    icon: ImageVector,
    tone: Tone,
    label: String,
    value: String,
    onClick: () -> Unit,
    onClear: () -> Unit,
    clearLabel: String = "Remove ${label.lowercase()}",
) {
    val p = Recall.colors
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().softShadow(shape, 4.dp).clip(shape).background(p.surface)
            .clickable(role = Role.Button, onClick = onClick).padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tone, size = 42.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = p.inkFaint)
            Text(value, style = MaterialTheme.typography.titleSmall, color = p.ink)
        }
        IconButton(onClick = onClear) {
            Icon(Icons.Rounded.Close, clearLabel, tint = p.inkFaint, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun AddChip(label: String, onClick: () -> Unit) {
    val p = Recall.colors
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(p.surfaceSoft)
            .clickable(role = Role.Button, onClickLabel = "Add $label", onClick = onClick)
            .defaultMinSize(minHeight = 44.dp).padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, null, tint = p.inkSoft, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = p.inkSoft)
    }
}

@Composable
private fun BigButton(label: String, icon: ImageVector, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = Recall.colors
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .then(if (filled) Modifier.softShadow(shape, 8.dp) else Modifier)
            .clip(shape)
            .background(if (filled) p.primary else p.primarySoft)
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val fg = if (filled) p.onPrimary else p.primary
        Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = fg, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySheet(current: String?, existing: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val p = Recall.colors
    var text by rememberSaveable { mutableStateOf(current.orEmpty()) }
    fun save() {
        text.trim().takeIf { it.isNotEmpty() }?.let(onPick)
        onDismiss()
    }
    RecallSheet(onDismiss, title = "Category") {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surfaceSoft)
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            if (text.isEmpty()) Text("Home, Work, Money…", style = MaterialTheme.typography.bodyLarge, color = p.inkFaint)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = p.ink),
                cursorBrush = SolidColor(p.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (existing.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                existing.forEach { category ->
                    PillChip(category, category == current, { onPick(category); onDismiss() })
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = ::save) { Text("Save") }
        }
    }
}
