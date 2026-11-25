package com.recall.app.ui.review

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.recall.app.AppContainer
import com.recall.app.data.Item
import com.recall.app.data.ItemStatus
import com.recall.app.domain.ForgottenRadar
import com.recall.app.domain.TimeText
import com.recall.app.ui.components.ClipboardIllustration
import com.recall.app.ui.components.EmptyState
import com.recall.app.ui.components.IconTile
import com.recall.app.ui.components.ScreenHeader
import com.recall.app.ui.components.SnoozeSheet
import com.recall.app.ui.components.SoftCard
import com.recall.app.ui.components.appViewModel
import com.recall.app.ui.components.container
import com.recall.app.ui.components.itemVisual
import com.recall.app.ui.theme.Recall
import com.recall.app.ui.theme.Tone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A review session is a short, fixed queue taken when it starts — it never grows while you work. */
private data class Session(val queue: List<Long> = emptyList(), val index: Int = 0, val handled: Int = 0, val started: Boolean = false)

data class ReviewState(
    val loading: Boolean = true,
    val current: ForgottenRadar.Hit? = null,
    val position: Int = 0,
    val total: Int = 0,
    val handled: Int = 0,
    /** How many more are waiting beyond this session. */
    val waiting: Int = 0,
    val now: Long = System.currentTimeMillis(),
) {
    val finished: Boolean get() = !loading && current == null && total > 0
    val empty: Boolean get() = !loading && current == null && total == 0
}

class ReviewViewModel(private val container: AppContainer) : ViewModel() {
    private val session = MutableStateFlow(Session())

    val state = combine(container.repo.items, container.settings.flow, session) { items, settings, s ->
        val now = System.currentTimeMillis()
        val config = ForgottenRadar.Config(quietDays = settings.quietDays)
        if (!s.started) return@combine ReviewState(loading = true)

        val byId = items.associateBy { it.id }
        // Skip anything that was dealt with elsewhere since the session began.
        var index = s.index
        var current: Item? = null
        while (index < s.queue.size) {
            val candidate = byId[s.queue[index]]
            if (candidate != null && candidate.status == ItemStatus.ACTIVE && !isHidden(candidate, now)) {
                current = candidate
                break
            }
            index++
        }
        val hit = current?.let {
            ForgottenRadar.evaluate(it, now, config, java.time.ZoneId.systemDefault())
                ?: ForgottenRadar.Hit(it, ForgottenRadar.Reason.LINGERING, daysCaptured(it, now), 0, 0, 0)
        }
        val waiting = ForgottenRadar.scan(items, now, config).count { it.item.id !in s.queue }
        ReviewState(
            loading = false,
            current = hit,
            position = (index + 1).coerceAtMost(s.queue.size),
            total = s.queue.size,
            handled = s.handled,
            waiting = waiting,
            now = now,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewState())

    private fun isHidden(item: Item, now: Long) = (item.snoozedUntil ?: 0) > now

    private fun daysCaptured(item: Item, now: Long) = com.recall.app.domain.Days.between(item.createdAt, now)

    /** Begins a session if none is running; called whenever the tab is opened. */
    fun ensureSession() {
        val s = session.value
        if (s.started && state.value.current != null) return
        start()
    }

    fun start() = viewModelScope.launch {
        val settings = container.settings.current()
        val hits = ForgottenRadar.scan(
            container.repo.items.first(),
            System.currentTimeMillis(),
            ForgottenRadar.Config(quietDays = settings.quietDays),
        )
        session.value = Session(queue = hits.take(SESSION_SIZE).map { it.item.id }, started = true)
    }

    /** Moves past [item]; [acted] is false for a plain skip. */
    fun next(item: Item, acted: Boolean) = session.update { s ->
        val at = s.queue.indexOf(item.id).takeIf { it >= 0 } ?: s.index
        s.copy(index = at + 1, handled = s.handled + if (acted) 1 else 0)
    }

    private companion object {
        /** Enough for a two-minute tidy; more can always be started afterwards. */
        const val SESSION_SIZE = 7
    }
}

@Composable
fun ReviewScreen(contentPadding: PaddingValues, onOpenItem: (Long) -> Unit, onOpenSettings: () -> Unit) {
    val vm = appViewModel { ReviewViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val actions = container().actions
    val p = Recall.colors
    var snoozing by remember { mutableStateOf<Item?>(null) }

    LaunchedEffect(Unit) { vm.ensureSession() }

    Column(Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(12.dp))
        ScreenHeader("Review", "Revisit, clear and stay organized.", Icons.Outlined.AutoAwesome) {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, "Review settings", tint = p.ink, modifier = Modifier.size(28.dp))
            }
        }

        val hit = state.current
        when {
            state.loading -> Spacer(Modifier.fillMaxSize())
            hit == null && state.finished -> Finished(state, onAgain = { vm.start() })
            hit == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    Icons.Outlined.Spa,
                    "Nothing waiting",
                    "When something you captured goes quiet for a while, it will show up here for a quick look.",
                )
            }
            else -> {
                Spacer(Modifier.height(22.dp))
                Progress(state.position, state.total)
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(12.dp))
                    ClipboardIllustration(Modifier.fillMaxWidth(0.72f).aspectRatio(1.25f))
                    Text(
                        "Still want to\nremember this?",
                        style = MaterialTheme.typography.displaySmall,
                        color = p.ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(22.dp))
                    AnimatedContent(
                        targetState = hit,
                        contentKey = { it.item.id },
                        transitionSpec = {
                            (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
                        },
                        label = "review item",
                    ) { shown ->
                        ReviewCard(
                            hit = shown,
                            now = state.now,
                            onDone = { actions.done(shown.item); vm.next(shown.item, acted = true) },
                            onLater = { snoozing = shown.item },
                            onDiscard = { actions.notImportant(shown.item); vm.next(shown.item, acted = true) },
                            onEdit = { onOpenItem(shown.item.id) },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { vm.next(hit.item, acted = false) }) {
                        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Skip for now", style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }

    snoozing?.let { item ->
        SnoozeSheet(
            onPick = { until ->
                actions.snooze(item, until)
                vm.next(item, acted = true)
                snoozing = null
            },
            onDismiss = { snoozing = null },
        )
    }
}

@Composable
private fun Progress(position: Int, total: Int) {
    val p = Recall.colors
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "Item $position of $total" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(total) { i ->
                Box(
                    Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50))
                        .background(if (i < position) p.primary else p.primary.copy(alpha = 0.16f))
                )
            }
        }
        Spacer(Modifier.width(18.dp))
        Text("$position of $total", style = MaterialTheme.typography.bodyMedium, color = p.inkSoft)
    }
}

@Composable
private fun ReviewCard(
    hit: ForgottenRadar.Hit,
    now: Long,
    onDone: () -> Unit,
    onLater: () -> Unit,
    onDiscard: () -> Unit,
    onEdit: () -> Unit,
) {
    val p = Recall.colors
    val item = hit.item
    val visual = itemVisual(item)
    SoftCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp)) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(visual.icon, visual.tone, size = 60.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = p.ink,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CalendarToday, null, tint = p.inkSoft, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Captured ${TimeText.ago(item.createdAt, now).replaceFirstChar { it.lowercase() }}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = p.inkSoft,
                        )
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, "Edit", tint = p.inkFaint, modifier = Modifier.size(22.dp))
                }
            }

            val detail = listOfNotNull(
                when (hit.reason) {
                    ForgottenRadar.Reason.OVERDUE -> item.dueAt?.let { TimeText.due(it, now) }
                    ForgottenRadar.Reason.POSTPONED -> "Postponed ${item.snoozeCount} times"
                    ForgottenRadar.Reason.UNTOUCHED ->
                        if (hit.daysQuiet != hit.daysCaptured) "Not looked at for ${TimeText.days(hit.daysQuiet)}" else null
                    ForgottenRadar.Reason.LINGERING -> null
                },
                item.notes.trim().takeIf { it.isNotEmpty() },
            )
            detail.forEach {
                Spacer(Modifier.height(10.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.inkSoft,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }

            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Choice("Done", Icons.Rounded.Check, p.green, onDone)
                Choice("Later", Icons.Outlined.Schedule, p.violet, onLater)
                Choice("Not important", Icons.Outlined.DeleteOutline, p.rose, onDiscard)
            }
        }
    }
}

@Composable
private fun Choice(label: String, icon: ImageVector, tone: Tone, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(66.dp).clip(CircleShape).background(tone.bg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tone.fg, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = Recall.colors.ink)
    }
}

@Composable
private fun Finished(state: ReviewState, onAgain: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        EmptyState(
            Icons.Outlined.CheckCircle,
            "That's the lot",
            when (state.handled) {
                0 -> "Nothing changed, and that's fine. They'll be here next time."
                1 -> "One thing off your mind."
                else -> "${state.handled} things off your mind."
            },
        )
        if (state.waiting > 0) {
            TextButton(onClick = onAgain) {
                Text(
                    if (state.waiting == 1) "Look at one more" else "Look at ${state.waiting} more",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
