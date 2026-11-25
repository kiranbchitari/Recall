package com.recall.app.ui.inbox

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.recall.app.AppContainer
import com.recall.app.data.Item
import com.recall.app.data.ItemStatus
import com.recall.app.data.ItemType
import com.recall.app.data.Priority
import com.recall.app.data.isSnoozed
import com.recall.app.domain.Days
import com.recall.app.ui.components.Dot
import com.recall.app.ui.components.EmptyState
import com.recall.app.ui.components.ItemCard
import com.recall.app.ui.components.ItemSheetsHost
import com.recall.app.ui.components.PillChip
import com.recall.app.ui.components.RecallSheet
import com.recall.app.ui.components.ScreenHeader
import com.recall.app.ui.components.SectionHeader
import com.recall.app.ui.components.SwipeActionBox
import com.recall.app.ui.components.appViewModel
import com.recall.app.ui.components.container
import com.recall.app.ui.components.icon
import com.recall.app.ui.components.plural
import com.recall.app.ui.components.rememberItemSheets
import com.recall.app.ui.components.softShadow
import com.recall.app.ui.components.tone
import com.recall.app.ui.minuteTicker
import com.recall.app.ui.theme.Recall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class InboxFilter(val label: String) {
    ALL("All"), TASKS("Tasks"), NOTES("Notes"), IDEAS("Ideas"), SHOPPING("Shopping"),
    DONE("Completed"), ARCHIVED("Archived"),
}

enum class InboxSort(val label: String) {
    NEWEST("Newest first"), OLDEST("Oldest first"), DUE("Due date"), TITLE("A to Z"),
}

enum class InboxGroup(val label: String) {
    RECENT("When captured"), TYPE("Type"), STATUS("Status"), NONE("No groups"),
}

data class InboxOptions(
    val filter: InboxFilter = InboxFilter.ALL,
    val sort: InboxSort = InboxSort.NEWEST,
    val group: InboxGroup = InboxGroup.RECENT,
    val withDueDate: Boolean = false,
    val withReminder: Boolean = false,
    val snoozed: Boolean = false,
    val highPriority: Boolean = false,
    val category: String? = null,
) {
    /** How many of the extra filters in the sheet are narrowing the list. */
    val refinements: Int
        get() = listOf(withDueDate, withReminder, snoozed, highPriority, category != null).count { it }
}

data class InboxSection(val title: String, val items: List<Item>)

data class InboxState(
    val query: String = "",
    val options: InboxOptions = InboxOptions(),
    val sections: List<InboxSection> = emptyList(),
    val categories: List<String> = emptyList(),
    val hasAnything: Boolean = false,
    val loaded: Boolean = false,
    val now: Long = System.currentTimeMillis(),
)

class InboxViewModel(container: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")
    private val options = MutableStateFlow(InboxOptions())

    val state = combine(container.repo.items, query, options, minuteTicker()) { items, q, o, now ->
        InboxState(
            query = q,
            options = o,
            sections = group(sort(filter(items, q, o, now), o.sort), o, now),
            categories = items.mapNotNull { it.category }.distinct().sorted(),
            hasAnything = items.isNotEmpty(),
            loaded = true,
            now = now,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxState())

    fun setQuery(value: String) = query.update { value }
    fun setOptions(transform: (InboxOptions) -> InboxOptions) = options.update(transform)

    private fun filter(items: List<Item>, q: String, o: InboxOptions, now: Long): List<Item> {
        val needle = q.trim()
        return items.filter { item ->
            val inScope = when (o.filter) {
                // Searching "All" looks through finished things too; the archive stays opt-in.
                InboxFilter.ALL -> item.status == ItemStatus.ACTIVE || (needle.isNotEmpty() && item.status == ItemStatus.DONE)
                InboxFilter.TASKS -> item.status == ItemStatus.ACTIVE && item.type == ItemType.TASK
                InboxFilter.NOTES -> item.status == ItemStatus.ACTIVE && item.type == ItemType.NOTE
                InboxFilter.IDEAS -> item.status == ItemStatus.ACTIVE && item.type == ItemType.IDEA
                InboxFilter.SHOPPING -> item.status == ItemStatus.ACTIVE && item.type == ItemType.SHOPPING
                InboxFilter.DONE -> item.status == ItemStatus.DONE
                InboxFilter.ARCHIVED -> item.status == ItemStatus.ARCHIVED || item.status == ItemStatus.DISMISSED
            }
            inScope &&
                (!o.withDueDate || item.dueAt != null) &&
                (!o.withReminder || item.reminderAt != null) &&
                (!o.snoozed || item.isSnoozed(now)) &&
                (!o.highPriority || item.priority == Priority.HIGH) &&
                (o.category == null || item.category == o.category) &&
                (needle.isEmpty() || item.title.contains(needle, true) || item.notes.contains(needle, true) ||
                    item.category?.contains(needle, true) == true)
        }
    }

    private fun sort(items: List<Item>, sort: InboxSort): List<Item> = when (sort) {
        InboxSort.NEWEST -> items.sortedByDescending { it.createdAt }
        InboxSort.OLDEST -> items.sortedBy { it.createdAt }
        InboxSort.DUE -> items.sortedWith(compareBy<Item> { it.dueAt ?: Long.MAX_VALUE }.thenByDescending { it.createdAt })
        InboxSort.TITLE -> items.sortedBy { it.title.lowercase() }
    }

    private fun group(items: List<Item>, o: InboxOptions, now: Long): List<InboxSection> {
        if (items.isEmpty()) return emptyList()
        val grouped: Map<String, List<Item>> = when (o.group) {
            InboxGroup.NONE -> return listOf(InboxSection("", items))
            InboxGroup.RECENT -> items.groupBy {
                val days = Days.between(it.createdAt, now)
                when {
                    days <= 0 -> "Today"
                    days <= 7 -> "This week"
                    else -> "Earlier"
                }
            }
            InboxGroup.TYPE -> items.groupBy { it.type.plural }
            InboxGroup.STATUS -> items.groupBy {
                when {
                    it.status == ItemStatus.DONE -> "Completed"
                    it.status != ItemStatus.ACTIVE -> "Archived"
                    it.isSnoozed(now) -> "Snoozed"
                    else -> "Open"
                }
            }
        }
        val order = listOf("Today", "This week", "Earlier", "Open", "Snoozed", "Completed", "Archived") +
            listOf(ItemType.TASK, ItemType.SHOPPING, ItemType.IDEA, ItemType.NOTE, ItemType.THOUGHT).map { it.plural }
        // With an oldest-first sort the time buckets read better oldest-first too.
        val keys = grouped.keys.sortedBy { order.indexOf(it) }
            .let { if (o.group == InboxGroup.RECENT && o.sort == InboxSort.OLDEST) it.reversed() else it }
        return keys.map { InboxSection(it, grouped.getValue(it)) }
    }
}

@Composable
fun InboxScreen(contentPadding: PaddingValues, onOpenItem: (Long) -> Unit) {
    val vm = appViewModel { InboxViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val actions = container().actions
    val sheets = rememberItemSheets()
    val p = Recall.colors

    var searching by rememberSaveable { mutableStateOf(false) }
    var showOptions by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp)) {
            ScreenHeader("Inbox", "Everything you've remembered.", Icons.Outlined.Inbox) {
                IconButton(onClick = {
                    searching = !searching
                    if (!searching) vm.setQuery("")
                }) {
                    Icon(
                        if (searching) Icons.Rounded.Close else Icons.Rounded.Search,
                        if (searching) "Close search" else "Search",
                        tint = p.ink,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Box {
                    IconButton(onClick = { showOptions = true }) {
                        Icon(Icons.Rounded.Tune, "Sort and filter", tint = p.ink, modifier = Modifier.size(28.dp))
                    }
                    if (state.options.refinements > 0) {
                        Dot(p.primary, Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp))
                    }
                }
            }
        }

        AnimatedVisibility(searching) {
            SearchField(state.query, vm::setQuery, Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp))
        }

        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            InboxFilter.entries.forEach { filter ->
                PillChip(
                    label = filter.label,
                    selected = state.options.filter == filter,
                    onClick = { vm.setOptions { it.copy(filter = filter) } },
                    icon = filter.icon,
                    tone = filter.type?.let(p::tone),
                )
            }
        }

        if (state.loaded && state.sections.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when {
                    state.query.isNotBlank() ->
                        EmptyState(Icons.Outlined.SearchOff, "Nothing matches", "No captured item mentions “${state.query.trim()}”.")
                    !state.hasAnything ->
                        EmptyState(Icons.Outlined.Inbox, "Nothing here yet", "Whatever you capture on Home lands here, ready when you are.")
                    state.options.filter == InboxFilter.ALL && state.options.refinements == 0 ->
                        EmptyState(Icons.Outlined.CheckCircle, "All clear", "Nothing open right now. Enjoy the quiet.")
                    else ->
                        EmptyState(Icons.Outlined.Inbox, "Nothing in this view", "Try another filter, or capture something new.")
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.sections.forEach { section ->
                    if (section.title.isNotEmpty()) {
                        item(key = "header-${section.title}", contentType = "header") {
                            SectionHeader(
                                section.title,
                                sectionIcon(section.title),
                                Modifier.animateItem(),
                                trailing = section.items.size.toString(),
                            )
                        }
                    }
                    items(section.items, key = { it.id }, contentType = { "item" }) { item ->
                        val open = item.status == ItemStatus.ACTIVE
                        SwipeActionBox(
                            rightLabel = if (open) "Done" else "Move back to inbox",
                            rightIcon = if (open) Icons.Rounded.Check else Icons.Rounded.Replay,
                            rightTone = if (open) p.green else p.violet,
                            onSwipeRight = { if (open) actions.done(item) else actions.reopen(item) },
                            leftLabel = "Later",
                            onSwipeLeft = if (open) ({ sheets.snooze = item }) else null,
                            modifier = Modifier.animateItem(),
                        ) {
                            ItemCard(
                                item = item,
                                now = state.now,
                                onClick = { onOpenItem(item.id) },
                                onLongClick = { sheets.actions = item },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showOptions) {
        OptionsSheet(state.options, state.categories, vm::setOptions, onDismiss = { showOptions = false })
    }
    ItemSheetsHost(sheets, onOpen = onOpenItem, housekeeping = true)
}

private val InboxFilter.type: ItemType?
    get() = when (this) {
        InboxFilter.TASKS -> ItemType.TASK
        InboxFilter.NOTES -> ItemType.NOTE
        InboxFilter.IDEAS -> ItemType.IDEA
        InboxFilter.SHOPPING -> ItemType.SHOPPING
        else -> null
    }

private val InboxFilter.icon: ImageVector?
    get() = when (this) {
        InboxFilter.ALL -> null
        InboxFilter.DONE -> Icons.Outlined.CheckCircle
        InboxFilter.ARCHIVED -> Icons.Outlined.Archive
        else -> type?.icon
    }

private fun sectionIcon(title: String): ImageVector = when (title) {
    "Today" -> Icons.Outlined.Schedule
    "This week" -> Icons.Outlined.CalendarToday
    "Earlier" -> Icons.Outlined.History
    "Snoozed" -> Icons.Outlined.Snooze
    "Completed" -> Icons.Outlined.CheckCircle
    "Archived" -> Icons.Outlined.Archive
    "Open" -> Icons.Outlined.Inbox
    else -> ItemType.entries.firstOrNull { it.plural == title }?.icon ?: Icons.Outlined.CheckBox
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val p = Recall.colors
    val focus = remember { FocusRequester() }
    val shape = RoundedCornerShape(50)
    Row(
        modifier.fillMaxWidth().softShadow(shape, 4.dp).clip(shape).background(p.surface)
            .defaultMinSize(minHeight = 52.dp).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = p.inkFaint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search titles and notes", style = MaterialTheme.typography.bodyLarge, color = p.inkFaint)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = p.ink),
                cursorBrush = SolidColor(p.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionsSheet(
    options: InboxOptions,
    categories: List<String>,
    update: ((InboxOptions) -> InboxOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = Recall.colors

    @Composable
    fun Group(title: String, content: @Composable () -> Unit) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = p.inkSoft, modifier = Modifier.padding(top = 14.dp, bottom = 10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }

    RecallSheet(onDismiss, title = "Sort and filter") {
        Group("Sort by") {
            InboxSort.entries.forEach { sort ->
                PillChip(sort.label, options.sort == sort, { update { it.copy(sort = sort) } })
            }
        }
        Group("Group by") {
            InboxGroup.entries.forEach { group ->
                PillChip(group.label, options.group == group, { update { it.copy(group = group) } })
            }
        }
        Group("Only show") {
            PillChip("With a due date", options.withDueDate, { update { it.copy(withDueDate = !it.withDueDate) } })
            PillChip("With a reminder", options.withReminder, { update { it.copy(withReminder = !it.withReminder) } })
            PillChip("Snoozed", options.snoozed, { update { it.copy(snoozed = !it.snoozed) } })
            PillChip("High priority", options.highPriority, { update { it.copy(highPriority = !it.highPriority) } })
        }
        if (categories.isNotEmpty()) {
            Group("Category") {
                categories.forEach { category ->
                    PillChip(
                        category,
                        options.category == category,
                        { update { it.copy(category = if (it.category == category) null else category) } },
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (options.refinements > 0) {
                TextButton(onClick = {
                    update { it.copy(withDueDate = false, withReminder = false, snoozed = false, highPriority = false, category = null) }
                }) { Text("Clear filters") }
            }
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    }
}
