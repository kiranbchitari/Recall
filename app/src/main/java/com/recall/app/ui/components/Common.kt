package com.recall.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.recall.app.AppContainer
import com.recall.app.RecallApp
import com.recall.app.data.Item
import com.recall.app.data.ItemType
import com.recall.app.data.Priority
import com.recall.app.data.Repeat
import com.recall.app.ui.theme.Palette
import com.recall.app.ui.theme.Recall
import com.recall.app.ui.theme.Tone

@Composable
fun container(): AppContainer = (LocalContext.current.applicationContext as RecallApp).container

@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = container()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

// ---- Wording for enums ----

val ItemType.label: String
    get() = when (this) {
        ItemType.THOUGHT -> "Thought"
        ItemType.TASK -> "Task"
        ItemType.NOTE -> "Note"
        ItemType.IDEA -> "Idea"
        ItemType.SHOPPING -> "To buy"
    }

val ItemType.plural: String
    get() = when (this) {
        ItemType.THOUGHT -> "Thoughts"
        ItemType.TASK -> "Tasks"
        ItemType.NOTE -> "Notes"
        ItemType.IDEA -> "Ideas"
        ItemType.SHOPPING -> "To buy"
    }

val Priority.label: String
    get() = when (this) {
        Priority.NONE -> "None"
        Priority.LOW -> "Low"
        Priority.MEDIUM -> "Medium"
        Priority.HIGH -> "High"
    }

val Repeat.label: String
    get() = when (this) {
        Repeat.NONE -> "Doesn't repeat"
        Repeat.DAILY -> "Every day"
        Repeat.WEEKLY -> "Every week"
        Repeat.MONTHLY -> "Every month"
        Repeat.YEARLY -> "Every year"
    }

val ItemType.icon: ImageVector
    get() = when (this) {
        ItemType.THOUGHT -> Icons.Outlined.ChatBubbleOutline
        ItemType.TASK -> Icons.Outlined.CheckBox
        ItemType.NOTE -> Icons.Outlined.Description
        ItemType.IDEA -> Icons.Outlined.Lightbulb
        ItemType.SHOPPING -> Icons.Outlined.ShoppingCart
    }

fun Palette.tone(type: ItemType): Tone = when (type) {
    ItemType.THOUGHT -> pink
    ItemType.TASK -> violet
    ItemType.NOTE -> blue
    ItemType.IDEA -> amber
    ItemType.SHOPPING -> rose
}

data class ItemVisual(val icon: ImageVector, val tone: Tone)

private val phoneWords = Regex("\\b(call|phone|ring|dial)\\b")
private val travelWords = Regex("\\b(trip|flight|fly|travel|visa|passport|holiday|vacation)\\b")
private val carWords = Regex("\\b(car|vehicle|bike|scooter|tyre|tire|petrol|fuel)\\b")
private val moneyWords = Regex("\\b(pay|payment|bill|sip|emi|bank|tax|invoice|refund|rent|insurance)\\b")

/**
 * The little picture beside an item. Mostly its type, but an untyped thought gets a friendlier
 * icon when the wording makes the subject obvious ("Call the electrician").
 */
@Composable
fun itemVisual(item: Item): ItemVisual {
    val p = Recall.colors
    return remember(item.title, item.type, p) {
        val text = item.title.lowercase()
        when {
            item.type != ItemType.THOUGHT && item.type != ItemType.TASK -> ItemVisual(item.type.icon, p.tone(item.type))
            phoneWords.containsMatchIn(text) -> ItemVisual(Icons.Outlined.Call, p.green)
            travelWords.containsMatchIn(text) -> ItemVisual(Icons.Outlined.Flight, p.pink)
            carWords.containsMatchIn(text) -> ItemVisual(Icons.Outlined.DirectionsCar, p.blue)
            moneyWords.containsMatchIn(text) -> ItemVisual(Icons.Outlined.AccountBalanceWallet, p.amber)
            else -> ItemVisual(item.type.icon, p.tone(item.type))
        }
    }
}

// ---- Surfaces ----

/** Soft, tinted shadow in light theme; a hairline border in dark, where shadows don't read. */
@Composable
fun Modifier.softShadow(shape: Shape, elevation: Dp = 10.dp): Modifier {
    val p = Recall.colors
    return if (p.dark) border(1.dp, p.outline, shape)
    else shadow(elevation, shape, clip = false, ambientColor = p.shadow.copy(alpha = 0.35f), spotColor = p.shadow.copy(alpha = 0.35f))
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    color: Color = Recall.colors.surface,
    elevation: Dp = 10.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .softShadow(shape, elevation)
            .clip(shape)
            .background(color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

@Composable
fun IconTile(icon: ImageVector, tone: Tone, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(tone.bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tone.fg, modifier = Modifier.size(size * 0.5f))
    }
}

/** Rounded filter / choice chip. Selected chips are solid; idle ones are quiet white pills. */
@Composable
fun PillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: Tone? = null,
) {
    val p = Recall.colors
    val shape = RoundedCornerShape(50)
    val bg = if (selected) p.primary else p.surface
    val fg = if (selected) p.onPrimary else p.ink
    Row(
        modifier
            .softShadow(shape, if (selected) 6.dp else 3.dp)
            .clip(shape)
            .background(bg)
            .clickable(role = Role.Tab, onClick = onClick)
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) fg else tone?.fg ?: p.primary, modifier = Modifier.size(20.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

/** Tinted chip used for the capture shortcuts and type pickers. */
@Composable
fun ToneChip(
    label: String,
    icon: ImageVector,
    tone: Tone,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .clip(shape)
            .background(tone.bg)
            .then(if (selected) Modifier.border(BorderStroke(1.5.dp, tone.fg), shape) else Modifier)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = tone.fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = Recall.colors.ink, maxLines = 1)
    }
}

@Composable
fun SectionHeader(title: String, icon: ImageVector, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(
        modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp).semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Recall.colors.ink, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Recall.colors.ink, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium, color = Recall.colors.inkFaint)
    }
}

/** Title block at the top of each tab: a mark, a title, a one-line description, and actions. */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Recall.colors.primary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = Recall.colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Recall.colors.inkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        actions()
    }
}

/** How long something has been waiting. Warmer colours for longer, never alarming. */
@Composable
fun DaysPill(days: Int, modifier: Modifier = Modifier) {
    val p = Recall.colors
    val tone = when {
        days >= 14 -> p.rose
        days >= 10 -> p.amber
        else -> Tone(p.amber.bg.copy(alpha = 0.6f), p.amber.fg)
    }
    Text(
        text = if (days == 1) "1 day" else "$days days",
        style = MaterialTheme.typography.labelMedium,
        color = tone.fg,
        modifier = modifier.clip(RoundedCornerShape(50)).background(tone.bg).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
fun Dot(color: Color, modifier: Modifier = Modifier, size: Dp = 9.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Recall.colors.outline))
}
