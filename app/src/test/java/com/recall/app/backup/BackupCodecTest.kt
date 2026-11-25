package com.recall.app.backup

import com.recall.app.data.DemoData
import com.recall.app.data.Item
import com.recall.app.data.ItemStatus
import com.recall.app.data.ItemType
import com.recall.app.data.Priority
import com.recall.app.data.Repeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {
    @Test fun `a backup restores to exactly what was saved`() {
        val items = DemoData.items(1_795_000_000_000) + Item(
            id = 99,
            title = "Quotes \"and\" newlines\nand emoji 🎈",
            notes = "multi\nline",
            type = ItemType.SHOPPING,
            status = ItemStatus.DISMISSED,
            priority = Priority.LOW,
            category = "Home",
            createdAt = 1, updatedAt = 2, touchedAt = 3,
            dueAt = 4, reminderAt = 5, snoozedUntil = 6,
            someday = true, snoozeCount = 7, completedAt = 8,
            repeat = Repeat.YEARLY,
        )
        assertEquals(items, BackupCodec.decode(BackupCodec.encode(items)))
    }

    @Test fun `an empty app backs up and restores`() {
        assertEquals(emptyList<Item>(), BackupCodec.decode(BackupCodec.encode(emptyList())))
    }

    @Test fun `files that are not ours are rejected before anything is touched`() {
        assertThrows(BackupException::class.java) { BackupCodec.decode("not json") }
        assertThrows(BackupException::class.java) { BackupCodec.decode("""{"items":[]}""") }
        assertThrows(BackupException::class.java) { BackupCodec.decode("""{"app":"other","items":[]}""") }
    }

    @Test fun `a backup from a newer app version is refused`() {
        assertThrows(BackupException::class.java) {
            BackupCodec.decode("""{"app":"recall","version":99,"items":[]}""")
        }
    }

    @Test fun `unknown enum values fall back instead of failing the whole restore`() {
        val text = """{"app":"recall","version":1,"items":[{"title":"x","createdAt":5,"type":"FUTURE_TYPE"}]}"""
        val item = BackupCodec.decode(text).single()
        assertEquals(ItemType.THOUGHT, item.type)
        assertEquals(5L, item.touchedAt)
    }
}
