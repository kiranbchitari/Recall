package com.recall.app.data

import com.recall.app.domain.Recurrence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Reverses the mutation that returned it. */
typealias Undo = suspend () -> Unit

/** Keeps scheduled reminder work in step with what is stored. */
interface ReminderSync {
    fun sync(item: Item)
    fun cancel(itemId: Long)
}

/**
 * Single entry point for reading and changing items. Room is the source of truth;
 * every mutation returns an [Undo] so the UI can always offer a way back.
 */
class ItemRepository(
    private val dao: ItemDao,
    private val reminders: ReminderSync,
    private val onChanged: () -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val items: Flow<List<Item>> = dao.observeAll()

    fun observe(id: Long): Flow<Item?> = dao.observe(id)

    suspend fun get(id: Long): Item? = dao.get(id)

    suspend fun getAll(): List<Item> = dao.getAll()

    /** Saves a thought straight away. Returns null when there is nothing to save. */
    suspend fun capture(text: String, type: ItemType? = null): Item? {
        val title = text.trim().replace(Regex("\\s*\\n\\s*"), " ")
        if (title.isEmpty()) return null
        val now = clock()
        val item = Item(title = title, type = type ?: inferType(title), createdAt = now)
        val id = dao.insert(item)
        onChanged()
        return item.copy(id = id)
    }

    /** Applies an edit to the stored item. Counts as the user touching it. */
    suspend fun edit(id: Long, transform: (Item) -> Item): Undo? = change(id) { item, _ -> transform(item) }

    /** Marks an item as looked at, without changing anything else. */
    suspend fun touch(id: Long) {
        val prev = dao.get(id) ?: return
        dao.update(prev.copy(touchedAt = clock()))
        onChanged()
    }

    suspend fun complete(id: Long): Undo? {
        val prev = dao.get(id) ?: return null
        val now = clock()
        val done = prev.copy(
            status = ItemStatus.DONE,
            completedAt = now,
            updatedAt = now,
            touchedAt = now,
            snoozedUntil = null,
            someday = false,
        )
        dao.update(done)
        reminders.cancel(id)
        // A repeating item leaves its next occurrence behind.
        val nextId = Recurrence.next(prev, now)?.let { next ->
            dao.insert(next).also { reminders.sync(next.copy(id = it)) }
        }
        onChanged()
        return {
            if (nextId != null) {
                dao.delete(nextId)
                reminders.cancel(nextId)
            }
            restore(prev)
        }
    }

    /** Brings a done, archived or dismissed item back into the inbox. */
    suspend fun reopen(id: Long): Undo? = change(id) { item, _ ->
        item.copy(status = ItemStatus.ACTIVE, completedAt = null)
    }

    /** Hides the item until [until]; null means "someday", with no return date. */
    suspend fun snooze(id: Long, until: Long?): Undo? = change(id) { item, _ ->
        item.copy(snoozedUntil = until, someday = until == null, snoozeCount = item.snoozeCount + 1)
    }

    /** Snooze from a reminder notification: the reminder itself moves too. */
    suspend fun remindLater(id: Long, until: Long): Undo? = change(id) { item, _ ->
        item.copy(reminderAt = until, snoozedUntil = until, someday = false, snoozeCount = item.snoozeCount + 1)
    }

    suspend fun wake(id: Long): Undo? = change(id) { item, _ ->
        item.copy(snoozedUntil = null, someday = false)
    }

    suspend fun dismiss(id: Long): Undo? = setStatus(id, ItemStatus.DISMISSED)

    suspend fun archive(id: Long): Undo? = setStatus(id, ItemStatus.ARCHIVED)

    suspend fun delete(id: Long): Undo? {
        val prev = dao.get(id) ?: return null
        dao.delete(id)
        reminders.cancel(id)
        onChanged()
        return { restore(prev) }
    }

    /** Replaces everything, e.g. when restoring a backup. */
    suspend fun replaceAll(items: List<Item>) {
        dao.getAll().forEach { reminders.cancel(it.id) }
        dao.replaceAll(items)
        items.forEach(reminders::sync)
        onChanged()
    }

    suspend fun resyncReminders() = dao.getAll().forEach(reminders::sync)

    private suspend fun setStatus(id: Long, status: ItemStatus): Undo? = change(id) { item, _ ->
        item.copy(status = status, snoozedUntil = null, someday = false)
    }

    // Edits arrive from several places at once (typing, chips, notifications); the lock makes
    // each read-modify-write see the result of the one before it.
    private val writes = Mutex()

    private suspend fun change(id: Long, transform: (Item, Long) -> Item): Undo? = writes.withLock {
        val prev = dao.get(id) ?: return null
        val now = clock()
        val next = transform(prev, now).copy(id = prev.id, updatedAt = now, touchedAt = now)
        dao.update(next)
        reminders.sync(next)
        onChanged()
        return { restore(prev) }
    }

    private suspend fun restore(item: Item) {
        dao.insert(item)
        reminders.sync(item)
        onChanged()
    }

    companion object {
        private val SHOPPING = Regex("^(buy|order|purchase|get more|pick up)\\b.*", RegexOption.IGNORE_CASE)

        /** Capture never asks for a type; the only guess we make is the obvious shopping one. */
        fun inferType(title: String): ItemType =
            if (SHOPPING.matches(title)) ItemType.SHOPPING else ItemType.THOUGHT
    }
}
