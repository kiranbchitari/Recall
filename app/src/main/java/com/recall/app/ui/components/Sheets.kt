package com.recall.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.recall.app.data.Item
import com.recall.app.data.ItemStatus
import com.recall.app.data.ItemType
import com.recall.app.domain.Snooze
import com.recall.app.ui.theme.Recall
import com.recall.app.ui.theme.Tone
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecallSheet(onDismiss: () -> Unit, title: String? = null, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Recall.colors.surface,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Recall.colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 12.dp).semantics { heading() },
                )
            }
            content()
        }
    }
}

@Composable
fun SheetRow(
    icon: ImageVector,
    label: String,
    tone: Tone,
    onClick: () -> Unit,
    hint: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tone, size = 42.dp)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = Recall.colors.ink, modifier = Modifier.weight(1f))
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = Recall.colors.inkFaint)
    }
}

/** "When should this come back?" — five presets and a calendar, nothing more. */
@Composable
fun SnoozeSheet(onPick: (Long?) -> Unit, onDismiss: () -> Unit) {
    val p = Recall.colors
    var pickDate by remember { mutableStateOf(false) }
    val options = remember { Snooze.options(System.currentTimeMillis()) }

    RecallSheet(onDismiss, title = "Bring it back…") {
        options.forEach { option ->
            val (icon, tone) = when (option.kind) {
                Snooze.Kind.LATER_TODAY -> Icons.Outlined.Coffee to p.amber
                Snooze.Kind.TOMORROW -> Icons.Outlined.WbSunny to p.rose
                Snooze.Kind.WEEKEND -> Icons.Outlined.Weekend to p.green
                Snooze.Kind.NEXT_WEEK -> Icons.Outlined.DateRange to p.blue
                Snooze.Kind.SOMEDAY -> Icons.Outlined.AllInclusive to p.pink
            }
            if (option.kind == Snooze.Kind.SOMEDAY) {
                SheetRow(Icons.Outlined.CalendarMonth, "Choose a date", p.violet, onClick = { pickDate = true })
            }
            SheetRow(icon, option.label, tone, onClick = { onPick(option.until) }, hint = option.hint)
        }
    }

    if (pickDate) {
        PickDateDialog(
            initial = null,
            futureOnly = true,
            onPick = { onPick(Snooze.onDate(it)) },
            onDismiss = { pickDate = false },
        )
    }
}

/** Quick actions for one item: the four Forgotten Radar choices, plus housekeeping when asked for. */
@Composable
fun ItemActionsSheet(
    item: Item,
    onDone: () -> Unit,
    onLater: () -> Unit,
    onNotImportant: () -> Unit,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onReopen: (() -> Unit)? = null,
    onArchive: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val p = Recall.colors
    fun then(action: () -> Unit): () -> Unit = { onDismiss(); action() }

    RecallSheet(onDismiss, title = item.title) {
        if (item.status == ItemStatus.ACTIVE) {
            SheetRow(Icons.Rounded.Check, "Done", p.green, then(onDone))
            SheetRow(Icons.Outlined.Schedule, "Later", p.violet, then(onLater))
            SheetRow(Icons.Outlined.DoNotDisturbOn, "Not important", p.rose, then(onNotImportant))
        } else if (onReopen != null) {
            SheetRow(Icons.Outlined.Replay, "Move back to inbox", p.violet, then(onReopen))
        }
        SheetRow(if (onDelete != null) Icons.Outlined.Edit else Icons.AutoMirrored.Outlined.OpenInNew, "Open", p.blue, then(onOpen))
        if (onArchive != null && item.status == ItemStatus.ACTIVE) {
            SheetRow(Icons.Outlined.Archive, "Archive", p.amber, then(onArchive))
        }
        if (onDelete != null) SheetRow(Icons.Outlined.Delete, "Delete", p.rose, then(onDelete))
    }
}

// ---- Capture ----

/** Launches the system speech recogniser and hands back what was heard. */
@Composable
fun rememberVoiceCapture(onText: (String) -> Unit): () -> Unit {
    val messages = container().messages
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { onText(it.replaceFirstChar(Char::uppercase)) }
    }
    return remember(launcher) {
        {
            try {
                launcher.launch(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, "What's on your mind?")
                )
            } catch (e: ActivityNotFoundException) {
                messages.show("Voice input isn't available on this device")
            }
        }
    }
}

val captureTypes = listOf(ItemType.TASK, ItemType.IDEA, ItemType.NOTE)

/**
 * The capture field. Type and go: the keyboard's action key saves, nothing else is asked.
 * The type is an optional hint, chosen before or not at all.
 */
@Composable
fun CaptureField(
    text: String,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onVoice: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    placeholder: String = "Remember something…",
) {
    val p = Recall.colors
    val shape = RoundedCornerShape(30.dp)
    Row(
        modifier
            .fillMaxWidth()
            .softShadow(shape, 8.dp)
            .clip(shape)
            .background(p.surface)
            .border(1.5.dp, p.primary.copy(alpha = 0.28f), shape)
            .defaultMinSize(minHeight = 62.dp)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Edit, null, tint = p.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f).padding(vertical = 14.dp), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = p.inkFaint, maxLines = 1)
            }
            BasicTextField(
                value = text,
                // Enter always means "save", whether it comes from the keyboard's action key or a real one.
                onValueChange = {
                    if ('\n' in it) {
                        onTextChange(it.replace("\n", " ").trim())
                        onSave()
                    } else {
                        onTextChange(it)
                    }
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = p.ink),
                cursorBrush = SolidColor(p.primary),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onSave() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }
        if (text.isBlank()) {
            IconButton(onClick = onVoice) {
                Icon(Icons.Outlined.MicNone, "Capture by voice", tint = p.ink, modifier = Modifier.size(26.dp))
            }
        } else {
            Box(
                Modifier.padding(4.dp).size(44.dp).clip(CircleShape).background(p.primary)
                    .clickable(role = Role.Button, onClickLabel = "Save", onClick = onSave),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.ArrowUpward, "Save", tint = p.onPrimary, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
fun TypeShortcuts(
    selected: ItemType?,
    onSelect: (ItemType?) -> Unit,
    modifier: Modifier = Modifier,
    types: List<ItemType> = captureTypes,
) {
    val p = Recall.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        types.forEach { type ->
            ToneChip(
                label = type.label,
                icon = type.icon,
                tone = p.tone(type),
                selected = selected == type,
                onClick = { onSelect(if (selected == type) null else type) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Capture in a bottom sheet, keyboard already up. Used by the Home "+" button. */
@Composable
fun CaptureSheet(
    onDismiss: () -> Unit,
    onCapture: (String, ItemType?) -> Unit,
    initialText: String = "",
    startWithVoice: Boolean = false,
) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    var type by rememberSaveable { mutableStateOf<ItemType?>(null) }
    val focus = remember { FocusRequester() }
    val voice = rememberVoiceCapture { onCapture(it, type); onDismiss() }

    RecallSheet(onDismiss) {
        CaptureField(
            text = text,
            onTextChange = { text = it },
            onSave = { if (text.isNotBlank()) { onCapture(text, type); onDismiss() } },
            onVoice = voice,
            focusRequester = focus,
        )
        Spacer(Modifier.height(14.dp))
        TypeShortcuts(type, { type = it })
        Spacer(Modifier.height(4.dp))
    }
    LaunchedEffect(Unit) {
        if (startWithVoice) voice() else runCatching { focus.requestFocus() }
    }
}

@Composable
fun TypeRow(selected: ItemType, onSelect: (ItemType) -> Unit, modifier: Modifier = Modifier) {
    val p = Recall.colors
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(ItemType.TASK, ItemType.IDEA, ItemType.NOTE, ItemType.SHOPPING, ItemType.THOUGHT).forEach { type ->
            ToneChip(type.label, type.icon, p.tone(type), selected == type, onClick = { onSelect(type) })
        }
    }
}

// ---- Pickers ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickDateDialog(initial: Long?, futureOnly: Boolean, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    // The picker works in UTC midnights; convert at the edges so the chosen day is the local day.
    val todayUtc = remember { LocalDate.now(zone).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.let {
            Instant.ofEpochMilli(it).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        },
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = !futureOnly || utcTimeMillis >= todayUtc
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    state.selectedDateMillis?.let {
                        onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    onDismiss()
                },
            ) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

/** [initialMinutes] and the result are minutes after midnight. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickTimeDialog(initialMinutes: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMinutes / 60, initialMinute = initialMinutes % 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(state.hour * 60 + state.minute); onDismiss() }) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}
