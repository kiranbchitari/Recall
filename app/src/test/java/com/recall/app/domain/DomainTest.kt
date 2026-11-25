package com.recall.app.domain

import com.recall.app.data.Item
import com.recall.app.data.ItemRepository
import com.recall.app.data.ItemStatus
import com.recall.app.data.ItemType
import com.recall.app.data.Repeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DomainTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    private fun at(date: String, time: String = "10:00"): Long =
        LocalDateTime.parse("${date}T$time").atZone(zone).toInstant().toEpochMilli()

    // Wednesday
    private val now = at("2026-11-25")

    private fun item(
        title: String = "x",
        captured: String,
        touched: String = captured,
        type: ItemType = ItemType.THOUGHT,
        status: ItemStatus = ItemStatus.ACTIVE,
    ) = Item(title = title, type = type, status = status, createdAt = at(captured), touchedAt = at(touched))

    private fun scan(vararg items: Item) = ForgottenRadar.scan(items.toList(), now, zone = zone)

    // ---- Forgotten Radar ----

    @Test fun `fresh item is not forgotten`() {
        assertTrue(scan(item(captured = "2026-11-23")).isEmpty())
    }

    @Test fun `untouched item surfaces with days since capture`() {
        val hit = scan(item(captured = "2026-11-07")).single()
        assertEquals(ForgottenRadar.Reason.UNTOUCHED, hit.reason)
        assertEquals(18, hit.daysCaptured)
    }

    @Test fun `old item touched yesterday stays quiet, touched three days ago lingers`() {
        assertTrue(scan(item(captured = "2026-10-01", touched = "2026-11-24")).isEmpty())
        assertEquals(
            ForgottenRadar.Reason.LINGERING,
            scan(item(captured = "2026-10-01", touched = "2026-11-22")).single().reason,
        )
    }

    @Test fun `overdue outranks everything and counts days`() {
        val overdue = item("late", captured = "2026-11-20").copy(dueAt = at("2026-11-22", "00:00"))
        val stale = item("stale", captured = "2026-10-01")
        val hits = scan(stale, overdue)
        assertEquals("late", hits.first().item.title)
        assertEquals(3, hits.first().daysOverdue)
    }

    @Test fun `due today is not overdue`() {
        assertTrue(scan(item(captured = "2026-11-24").copy(dueAt = at("2026-11-25", "00:00"))).isEmpty())
    }

    @Test fun `repeatedly postponed item surfaces once it is back`() {
        val postponed = item(captured = "2026-11-24").copy(snoozeCount = 3)
        assertEquals(ForgottenRadar.Reason.POSTPONED, scan(postponed).single().reason)
        assertTrue(scan(postponed.copy(snoozedUntil = now + 60_000)).isEmpty())
    }

    @Test fun `done, archived, dismissed and notes never surface`() {
        val old = "2026-09-01"
        assertTrue(
            scan(
                item(captured = old, status = ItemStatus.DONE),
                item(captured = old, status = ItemStatus.ARCHIVED),
                item(captured = old, status = ItemStatus.DISMISSED),
                item(captured = old, type = ItemType.NOTE),
            ).isEmpty()
        )
    }

    @Test fun `an item with a plan ahead of it is left alone`() {
        val old = item(captured = "2026-10-01")
        assertTrue(scan(old.copy(dueAt = at("2026-12-05", "00:00"))).isEmpty())
        assertTrue(scan(old.copy(reminderAt = at("2026-11-26"))).isEmpty())
    }

    @Test fun `someday items return only after a long quiet spell`() {
        assertTrue(scan(item(captured = "2026-11-05").copy(someday = true)).isEmpty())
        assertEquals(1, scan(item(captured = "2026-10-20").copy(someday = true)).size)
    }

    @Test fun `quiet threshold is configurable`() {
        val i = item(captured = "2026-11-21")
        assertTrue(ForgottenRadar.scan(listOf(i), now, ForgottenRadar.Config(quietDays = 5), zone).isEmpty())
        assertEquals(1, ForgottenRadar.scan(listOf(i), now, ForgottenRadar.Config(quietDays = 3), zone).size)
    }

    // ---- Snooze ----

    private fun option(kind: Snooze.Kind, at: Long = now) =
        Snooze.options(at, zone).firstOrNull { it.kind == kind }

    @Test fun `snooze options on a weekday morning`() {
        assertEquals(at("2026-11-25", "13:00"), option(Snooze.Kind.LATER_TODAY)!!.until)
        assertEquals(at("2026-11-26", "09:00"), option(Snooze.Kind.TOMORROW)!!.until)
        assertEquals(at("2026-11-28", "10:00"), option(Snooze.Kind.WEEKEND)!!.until)
        assertEquals("This weekend", option(Snooze.Kind.WEEKEND)!!.label)
        assertEquals(at("2026-11-30", "09:00"), option(Snooze.Kind.NEXT_WEEK)!!.until)
        assertNull(option(Snooze.Kind.SOMEDAY)!!.until)
    }

    @Test fun `later today disappears late in the evening`() {
        assertNull(option(Snooze.Kind.LATER_TODAY, at("2026-11-25", "21:30")))
        assertNotNull(option(Snooze.Kind.LATER_TODAY, at("2026-11-25", "18:40")))
    }

    @Test fun `on a saturday the weekend option means the next one`() {
        val saturday = at("2026-11-28")
        assertEquals("Next weekend", option(Snooze.Kind.WEEKEND, saturday)!!.label)
        assertEquals(at("2026-12-05", "10:00"), option(Snooze.Kind.WEEKEND, saturday)!!.until)
    }

    @Test fun `picked date returns that morning`() {
        assertEquals(at("2026-12-10", "09:00"), Snooze.onDate(LocalDate.of(2026, 12, 10), zone))
    }

    // ---- Recurrence ----

    @Test fun `non repeating item has no next occurrence`() {
        assertNull(Recurrence.next(item(captured = "2026-11-20"), now, zone))
    }

    @Test fun `weekly due date rolls past today even when overdue for weeks`() {
        val i = item(captured = "2026-11-01").copy(repeat = Repeat.WEEKLY, dueAt = at("2026-11-04", "00:00"), snoozeCount = 2)
        val next = Recurrence.next(i, now, zone)!!
        assertEquals(at("2026-12-02", "00:00"), next.dueAt)
        assertEquals(0L, next.id)
        assertEquals(0, next.snoozeCount)
        assertEquals(now, next.createdAt)
    }

    @Test fun `daily reminder keeps its time of day`() {
        val i = item(captured = "2026-11-20").copy(repeat = Repeat.DAILY, reminderAt = at("2026-11-25", "08:30"))
        assertEquals(at("2026-11-26", "08:30"), Recurrence.next(i, now, zone)!!.reminderAt)
    }

    @Test fun `repeat without dates sleeps until its next turn`() {
        val i = item(captured = "2026-11-20").copy(repeat = Repeat.MONTHLY)
        val next = Recurrence.next(i, now, zone)!!
        assertEquals(at("2026-12-25", "09:00"), next.snoozedUntil)
        assertNull(next.dueAt)
    }

    // ---- Wording ----

    @Test fun `relative time wording`() {
        assertEquals("Just now", TimeText.ago(now - 20_000, now, zone))
        assertEquals("5 min ago", TimeText.ago(now - 5 * 60_000, now, zone))
        assertEquals("Yesterday", TimeText.ago(at("2026-11-24", "23:00"), now, zone))
        assertEquals("18 days ago", TimeText.ago(at("2026-11-07"), now, zone))
    }

    @Test fun `due wording stays gentle`() {
        assertEquals("Due today", TimeText.due(at("2026-11-25", "00:00"), now, zone))
        assertEquals("Due tomorrow", TimeText.due(at("2026-11-26", "00:00"), now, zone))
        assertEquals("Due 12 Dec", TimeText.due(at("2026-12-12", "00:00"), now, zone))
        assertEquals("Was due 3 days ago", TimeText.due(at("2026-11-22", "00:00"), now, zone))
    }

    @Test fun `only obvious shopping phrases are typed automatically`() {
        assertEquals(ItemType.SHOPPING, ItemRepository.inferType("Buy laptop bag"))
        assertEquals(ItemType.THOUGHT, ItemRepository.inferType("Renew driving licence"))
        assertEquals(ItemType.THOUGHT, ItemRepository.inferType("Buyer feedback call"))
    }
}
