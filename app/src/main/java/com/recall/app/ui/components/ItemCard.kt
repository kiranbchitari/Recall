package com.recall.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.recall.app.data.Item
import com.recall.app.data.ItemStatus
import com.recall.app.data.Priority
import com.recall.app.data.Repeat
import com.recall.app.domain.Days
import com.recall.app.domain.TimeText
import com.recall.app.ui.theme.Recall
import com.recall.app.ui.theme.Tone
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val cardShape = RoundedCornerShape(22.dp)

/** One captured thing: a picture, the words, and only the metadata that is actually set. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemCard(
    item: Item,
    now: Long,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = Recall.colors
    val open = item.status == ItemStatus.ACTIVE
    val visual = itemVisual(item)
    val overdue = open && item.dueAt != null && Days.between(item.dueAt, now) > 0
    val attention = open && (overdue || item.priority == Priority.HIGH)

    val meta = when (item.status) {
        ItemStatus.DONE -> "Done ${TimeText.ago(item.completedAt ?: item.updatedAt, now).replaceFirstChar { it.lowercase() }}"
        ItemStatus.ARCHIVED -> "Archived"
        ItemStatus.DISMISSED -> "Let go"
        ItemStatus.ACTIVE -> listOfNotNull(
            TimeText.ago(item.createdAt, now),
            item.dueAt?.let { TimeText.due(it, now) },
            TimeText.returns(item, now),
            item.category,
        ).joinToString("  ·  ")
    }

    Box(
        modifier
            .fillMaxWidth()
            .softShadow(cardShape, 6.dp)
            .clip(cardShape)
            .background(p.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "More actions")
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.status == ItemStatus.DONE) IconTile(Icons.Rounded.Check, p.green, size = 52.dp)
            else IconTile(visual.icon, if (open) visual.tone else Tone(p.surfaceSoft, p.inkFaint), size = 52.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (open) p.ink else p.inkSoft,
                    textDecoration = if (item.status == ItemStatus.DONE) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = p.inkSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (open && item.reminderAt != null && item.reminderAt > now) {
                        MetaIcon(Icons.Outlined.NotificationsNone, "Reminder set")
                    }
                    if (open && item.repeat != Repeat.NONE) MetaIcon(Icons.Rounded.Repeat, "Repeats")
                }
            }
            if (attention) {
                Spacer(Modifier.width(10.dp))
                Dot(p.rose.fg)
            }
        }
    }
}

@Composable
private fun MetaIcon(icon: ImageVector, description: String) {
    Icon(icon, description, tint = Recall.colors.inkFaint, modifier = Modifier.padding(start = 6.dp).size(15.dp))
}

/**
 * Swipe an item to act on it: right to finish, left to push it away. The same actions are
 * exposed as accessibility actions, and through long-press, for anyone who doesn't swipe.
 */
@Composable
fun SwipeActionBox(
    rightLabel: String,
    rightIcon: ImageVector,
    rightTone: Tone,
    onSwipeRight: () -> Unit,
    leftLabel: String?,
    leftIcon: ImageVector = Icons.Outlined.Schedule,
    leftTone: Tone = Recall.colors.violet,
    onSwipeLeft: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val right by rememberUpdatedState(onSwipeRight)
    val left by rememberUpdatedState(onSwipeLeft)
    var armed by remember { mutableStateOf(false) }

    Box(
        modifier
            .semantics {
                customActions = listOfNotNull(
                    CustomAccessibilityAction(rightLabel) { right(); true },
                    left?.let { action -> leftLabel?.let { CustomAccessibilityAction(it) { action(); true } } },
                )
            }
            .pointerInput(Unit) {
                val trigger = 88.dp.toPx()
                val limit = 160.dp.toPx()
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val x = offset.value
                        armed = false
                        scope.launch { offset.animateTo(0f, spring()) }
                        if (x > trigger) right() else if (x < -trigger) left?.invoke()
                    },
                    onDragCancel = {
                        armed = false
                        scope.launch { offset.animateTo(0f, spring()) }
                    },
                    onHorizontalDrag = { change, drag ->
                        change.consume()
                        val target = (offset.value + drag).coerceIn(if (left != null) -limit else 0f, limit)
                        val nowArmed = abs(target) > trigger
                        if (nowArmed && !armed) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        armed = nowArmed
                        scope.launch { offset.snapTo(target) }
                    },
                )
            }
    ) {
        val x = offset.value
        if (x != 0f) {
            val tone = if (x > 0) rightTone else leftTone
            Box(Modifier.matchParentSize().clip(cardShape).background(tone.bg)) {
                Icon(
                    if (x > 0) rightIcon else leftIcon,
                    contentDescription = null,
                    tint = if (armed) tone.fg else tone.fg.copy(alpha = 0.5f),
                    modifier = Modifier
                        .align(if (x > 0) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(horizontal = 26.dp)
                        .size(26.dp),
                )
            }
        }
        Box(Modifier.offset { IntOffset(offset.value.roundToInt(), 0) }) { content() }
    }
}

/** Holds which item, if any, has its action or snooze sheet open. */
@Stable
class ItemSheets {
    var actions by mutableStateOf<Item?>(null)
    var snooze by mutableStateOf<Item?>(null)
}

@Composable
fun rememberItemSheets() = remember { ItemSheets() }

@Composable
fun ItemSheetsHost(
    sheets: ItemSheets,
    onOpen: (Long) -> Unit,
    housekeeping: Boolean = false,
    onActed: (Item) -> Unit = {},
) {
    val actions = container().actions
    sheets.actions?.let { item ->
        ItemActionsSheet(
            item = item,
            onDone = { actions.done(item); onActed(item) },
            onLater = { sheets.snooze = item },
            onNotImportant = { actions.notImportant(item); onActed(item) },
            onOpen = { onOpen(item.id) },
            onDismiss = { sheets.actions = null },
            onReopen = { actions.reopen(item) },
            onArchive = if (housekeeping) ({ actions.archive(item) }) else null,
            onDelete = if (housekeeping) ({ actions.delete(item) }) else null,
        )
    }
    sheets.snooze?.let { item ->
        SnoozeSheet(
            onPick = { until ->
                actions.snooze(item, until)
                sheets.snooze = null
                onActed(item)
            },
            onDismiss = { sheets.snooze = null },
        )
    }
}
