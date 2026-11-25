package com.recall.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ItemType { THOUGHT, TASK, NOTE, IDEA, SHOPPING }

enum class ItemStatus { ACTIVE, DONE, ARCHIVED, DISMISSED }

enum class Priority { NONE, LOW, MEDIUM, HIGH }

enum class Repeat { NONE, DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * Everything the user captures is one row. Only [title] is ever required;
 * every other field is optional and filled in later, if at all.
 *
 * All timestamps are epoch milliseconds. [dueAt] is stored as the local start of the due day.
 */
@Entity(tableName = "items", indices = [Index("status")])
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val type: ItemType = ItemType.THOUGHT,
    val status: ItemStatus = ItemStatus.ACTIVE,
    val priority: Priority = Priority.NONE,
    val category: String? = null,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    /** Last time the user opened, edited or acted on this item. Drives Forgotten Radar. */
    val touchedAt: Long = createdAt,
    val dueAt: Long? = null,
    val reminderAt: Long? = null,
    /** Hidden from Home and Radar until this moment. */
    val snoozedUntil: Long? = null,
    /** Snoozed with no return date. */
    val someday: Boolean = false,
    val snoozeCount: Int = 0,
    val completedAt: Long? = null,
    val repeat: Repeat = Repeat.NONE,
)

val Item.isActive: Boolean get() = status == ItemStatus.ACTIVE

fun Item.isSnoozed(now: Long): Boolean =
    isActive && (someday || (snoozedUntil != null && snoozedUntil > now))
