package com.recall.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Upcoming
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.recall.app.AppContainer
import com.recall.app.R
import com.recall.app.data.Item
import com.recall.app.data.ItemStatus
import com.recall.app.data.ItemType
import com.recall.app.domain.Days
import com.recall.app.domain.ForgottenRadar
import com.recall.app.domain.TimeText
import com.recall.app.ui.components.CaptureField
import com.recall.app.ui.components.CaptureSheet
import com.recall.app.ui.components.DaysPill
import com.recall.app.ui.components.Dot
import com.recall.app.ui.components.Hairline
import com.recall.app.ui.components.ItemSheets
import com.recall.app.ui.components.ItemSheetsHost
import com.recall.app.ui.components.MindIllustration
import com.recall.app.ui.components.SoftCard
import com.recall.app.ui.components.TypeShortcuts
import com.recall.app.ui.components.appViewModel
import com.recall.app.ui.components.container
import com.recall.app.ui.components.rememberItemSheets
import com.recall.app.ui.components.rememberVoiceCapture
import com.recall.app.ui.components.softShadow
import com.recall.app.ui.minuteTicker
import com.recall.app.ui.theme.Recall
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalTime

data class DatedItem(val item: Item, val label: String)

data class HomeState(
    val name: String = "",
    val now: Long = System.currentTimeMillis(),
    val hasItems: Boolean = false,
    val radar: List<ForgottenRadar.Hit> = emptyList(),
    val today: List<DatedItem> = emptyList(),
    val upcoming: List<DatedItem> = emptyList(),
)

class HomeViewModel(container: AppContainer) : ViewModel() {
    val state = combine(container.repo.items, container.settings.flow, minuteTicker()) { items, settings, now ->
        val open = items.filter { it.status == ItemStatus.ACTIVE }
        HomeState(
            name = settings.name,
            now = now,
            hasItems = items.isNotEmpty(),
            radar = ForgottenRadar.scan(items, now, ForgottenRadar.Config(quietDays = settings.quietDays)),
            today = open.mapNotNull { item -> todayLabel(item, now)?.let { DatedItem(item, it) } },
            upcoming = open.mapNotNull { item -> upcoming(item, now) }
                .sortedBy { it.first }.take(3).map { DatedItem(it.second, TimeText.date(it.first, now)) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    /** Why an item belongs to today, or null if it doesn't. */
    private fun todayLabel(item: Item, now: Long): String? = when {
        item.reminderAt != null && Days.isToday(item.reminderAt, now) ->
            "Reminder ${TimeText.dateTime(item.reminderAt, now).substringAfter(", ")}"
        item.dueAt != null && Days.isToday(item.dueAt, now) -> "Due today"
        item.snoozedUntil != null && item.snoozedUntil <= now && Days.isToday(item.snoozedUntil, now) -> "Back today"
        else -> null
    }

    /** The next dated moment within a week, if the item has one. */
    private fun upcoming(item: Item, now: Long): Pair<Long, Item>? {
        val next = listOfNotNull(item.dueAt, item.reminderAt, item.snoozedUntil)
            .filter { Days.between(now, it) in 1..7 }
            .minOrNull() ?: return null
        return next to item
    }
}

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onOpenItem: (Long) -> Unit,
    onOpenReview: () -> Unit,
    onOpenMe: () -> Unit,
) {
    val vm = appViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val actions = container().actions
    val sheets = rememberItemSheets()
    var showCapture by rememberSaveable { mutableStateOf(false) }

    var text by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf<ItemType?>(null) }
    val focus = remember { FocusRequester() }
    val voice = rememberVoiceCapture { actions.capture(it, type); type = null }

    Box(Modifier.fillMaxSize().padding(contentPadding)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 104.dp)
        ) {
            Header(radarCount = state.radar.size, name = state.name, onOpenReview = onOpenReview, onOpenMe = onOpenMe)
            Spacer(Modifier.height(22.dp))
            Hero(state.name)
            Spacer(Modifier.height(20.dp))

            CaptureField(
                text = text,
                onTextChange = { text = it },
                onSave = {
                    if (text.isNotBlank()) {
                        actions.capture(text, type)
                        text = ""
                        type = null
                    }
                },
                onVoice = voice,
                focusRequester = focus,
            )
            Spacer(Modifier.height(14.dp))
            TypeShortcuts(
                selected = type,
                onSelect = {
                    type = it
                    if (it != null) runCatching { focus.requestFocus() }
                },
            )
            Spacer(Modifier.height(22.dp))

            RadarCard(
                hits = state.radar,
                hasItems = state.hasItems,
                onOpenReview = onOpenReview,
                onDone = { actions.done(it) },
                sheets = sheets,
            )
            Spacer(Modifier.height(18.dp))

            // The last card stops short of the capture button so the two never overlap at rest.
            val beside = Modifier.padding(end = 80.dp)
            DatedCard(
                title = "Today",
                emptyText = "Nothing scheduled",
                icon = Icons.Outlined.CalendarToday,
                items = state.today,
                onDone = { actions.done(it) },
                onOpen = onOpenItem,
                modifier = if (state.upcoming.isEmpty()) beside else Modifier,
            )
            if (state.upcoming.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                DatedCard(
                    title = "Upcoming",
                    emptyText = "",
                    icon = Icons.Outlined.Upcoming,
                    items = state.upcoming,
                    onDone = { actions.done(it) },
                    onOpen = onOpenItem,
                    modifier = beside,
                )
            }
        }

        CaptureButton(
            onClick = { showCapture = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 16.dp),
        )
    }

    if (showCapture) {
        CaptureSheet(onDismiss = { showCapture = false }, onCapture = { t, ty -> actions.capture(t, ty) })
    }
    ItemSheetsHost(sheets, onOpen = onOpenItem)
}

@Composable
private fun Header(radarCount: Int, name: String, onOpenReview: () -> Unit, onOpenMe: () -> Unit) {
    val p = Recall.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_brand_mark), null, tint = p.primary, modifier = Modifier.size(46.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Recall", style = MaterialTheme.typography.headlineSmall, color = p.ink)
            Text(
                "Your Personal Memory Assistant",
                style = MaterialTheme.typography.bodySmall,
                color = p.inkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = onOpenReview) {
                Icon(
                    Icons.Outlined.NotificationsNone,
                    if (radarCount > 0) "Review, $radarCount waiting" else "Review",
                    tint = p.ink,
                    modifier = Modifier.size(28.dp),
                )
            }
            if (radarCount > 0) Dot(p.rose.fg, Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp))
        }
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(p.primarySoft)
                .clickable(role = Role.Button, onClickLabel = "Open settings", onClick = onOpenMe)
                .semantics { contentDescription = "Me" },
            contentAlignment = Alignment.Center,
        ) {
            val initial = name.trim().firstOrNull()?.uppercase()
            if (initial != null) Text(initial, style = MaterialTheme.typography.titleMedium, color = p.primary)
            else Icon(Icons.Outlined.Person, null, tint = p.primary, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun Hero(name: String) {
    val p = Recall.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (name.isNotBlank()) {
                val hour = LocalTime.now().hour
                val greeting = when (hour) {
                    in 5..11 -> "Good morning"
                    in 12..16 -> "Good afternoon"
                    in 17..21 -> "Good evening"
                    else -> "Hello"
                }
                Text("$greeting, ${name.trim()}", style = MaterialTheme.typography.labelLarge, color = p.inkSoft)
                Spacer(Modifier.height(6.dp))
            }
            Column(Modifier.semantics(mergeDescendants = true) { heading() }) {
                Text("A little space", style = MaterialTheme.typography.headlineLarge, color = p.ink)
                Row {
                    Text("for your ", style = MaterialTheme.typography.headlineLarge, color = p.ink)
                    Text(
                        "mind.",
                        style = MaterialTheme.typography.headlineLarge,
                        color = p.ink,
                        modifier = Modifier.drawBehind {
                            val y = size.height - 1.dp.toPx()
                            drawPath(
                                Path().apply {
                                    moveTo(-4.dp.toPx(), y + 2.dp.toPx())
                                    cubicTo(size.width * 0.3f, y - 5.dp.toPx(), size.width * 0.7f, y - 4.dp.toPx(), size.width, y + 1.dp.toPx())
                                },
                                color = p.primary.copy(alpha = 0.55f),
                                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Capture thoughts, tasks, ideas and things you don't want to forget.",
                style = MaterialTheme.typography.bodyMedium,
                color = p.inkSoft,
            )
        }
        Spacer(Modifier.width(8.dp))
        MindIllustration(Modifier.size(124.dp))
    }
}

/** The signature section: a small, calm preview of what has slipped out of sight. */
@Composable
private fun RadarCard(
    hits: List<ForgottenRadar.Hit>,
    hasItems: Boolean,
    onOpenReview: () -> Unit,
    onDone: (Item) -> Unit,
    sheets: ItemSheets,
) {
    val p = Recall.colors
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .softShadow(shape, 6.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(p.primarySoft, lerp(p.primarySoft, p.surface, 0.55f))))
            .padding(12.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable(role = Role.Button, onClickLabel = "Open review", onClick = onOpenReview)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(58.dp).clip(CircleShape).background(lerp(p.primarySoft, p.primary, 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Radar, null, tint = p.primary, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Forgotten Radar",
                    style = MaterialTheme.typography.titleLarge,
                    color = p.ink,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    "Things you captured but haven't looked at recently.",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.inkSoft,
                )
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = p.ink, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(10.dp))

        SoftCard(shape = RoundedCornerShape(20.dp), elevation = 0.dp, modifier = Modifier.fillMaxWidth()) {
            if (hits.isEmpty()) {
                Text(
                    if (hasItems) "Nothing is slipping through right now."
                    else "Things you capture and then forget will quietly show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.inkSoft,
                    modifier = Modifier.padding(18.dp),
                )
            } else {
                Column {
                    hits.take(3).forEachIndexed { index, hit ->
                        if (index > 0) Hairline(Modifier.padding(horizontal = 16.dp))
                        CheckRow(
                            title = hit.item.title,
                            onCheck = { onDone(hit.item) },
                            onClick = { sheets.actions = hit.item },
                        ) { DaysPill(hit.daysCaptured) }
                    }
                }
            }
        }
        if (hits.size > 3) {
            Text(
                "${hits.size - 3} more in Review",
                style = MaterialTheme.typography.labelMedium,
                color = p.primary,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenReview)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun CheckRow(
    title: String,
    onCheck: () -> Unit,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    val p = Recall.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 4.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCheck) {
            Box(
                Modifier.size(22.dp).border(1.6.dp, p.primary.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                    .semantics { contentDescription = "Mark done: $title" }
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = p.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 10.dp),
        )
        trailing()
    }
}

@Composable
private fun DatedCard(
    title: String,
    emptyText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    items: List<DatedItem>,
    onDone: (Item) -> Unit,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = Recall.colors
    // Collapsed to a single quiet line until asked: Home stays a place to capture, not a list to face.
    var expanded by rememberSaveable { mutableStateOf(false) }
    val turn by animateFloatAsState(if (expanded && items.isNotEmpty()) 90f else 0f, label = "chevron")

    SoftCard(modifier.fillMaxWidth(), elevation = 6.dp) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = items.isNotEmpty(), role = Role.Button) { expanded = !expanded }
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = p.ink, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = p.ink, modifier = Modifier.semantics { heading() })
                    Text(
                        when (items.size) {
                            0 -> emptyText
                            1 -> "1 item"
                            else -> "${items.size} items"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = p.inkSoft,
                    )
                }
                if (items.isNotEmpty()) {
                    Icon(Icons.Rounded.ChevronRight, null, tint = p.ink, modifier = Modifier.size(26.dp).rotate(turn))
                }
            }
            AnimatedVisibility(expanded && items.isNotEmpty()) {
                Column(Modifier.padding(bottom = 6.dp)) {
                    items.forEach { dated ->
                        Hairline(Modifier.padding(horizontal = 16.dp))
                        CheckRow(
                            title = dated.item.title,
                            onCheck = { onDone(dated.item) },
                            onClick = { onOpen(dated.item.id) },
                        ) {
                            Text(dated.label, style = MaterialTheme.typography.labelMedium, color = p.inkSoft)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = Recall.colors
    Box(
        modifier
            .softShadow(CircleShape, 12.dp)
            .size(64.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(lerp(p.primary, p.surface, 0.12f), p.primary)))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Capture something" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, null, tint = p.onPrimary, modifier = Modifier.size(34.dp))
    }
}
