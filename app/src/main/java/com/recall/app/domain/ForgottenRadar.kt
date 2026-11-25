package com.recall.app.domain

import com.recall.app.data.Item
import com.recall.app.data.ItemStatus
import com.recall.app.data.ItemType
import java.time.ZoneId

/**
 * Finds things the user has probably forgotten, using plain local rules — no network, no AI.
 *
 * An item surfaces when it is still open, is not currently snoozed, and at least one signal fires:
 *  - it is past its due date,
 *  - it has been postponed several times,
 *  - nobody has touched it for a while, or
 *  - it has been sitting incomplete for a long time.
 *
 * Notes are reference material rather than something to act on, so they are left alone.
 */
object ForgottenRadar {

    enum class Reason { OVERDUE, POSTPONED, UNTOUCHED, LINGERING }

    data class Config(
        /** Days without a touch before an item counts as forgotten. */
        val quietDays: Int = 5,
        /** Snoozes after which an item is "repeatedly postponed". */
        val snoozeLimit: Int = 3,
        /** Days since capture after which an open item is "lingering". */
        val lingerDays: Int = 14,
        /** "Someday" items come back on their own after this many quiet days. */
        val somedayDays: Int = 30,
    )

    data class Hit(
        val item: Item,
        val reason: Reason,
        /** Whole days since the item was captured. */
        val daysCaptured: Int,
        /** Whole days since the item was last touched. */
        val daysQuiet: Int,
        /** Whole days past due; 0 when not overdue. */
        val daysOverdue: Int,
        val score: Int,
    )

    fun scan(
        items: List<Item>,
        now: Long,
        config: Config = Config(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Hit> = items.mapNotNull { evaluate(it, now, config, zone) }
        .sortedWith(compareByDescending<Hit> { it.score }.thenBy { it.item.createdAt })

    fun evaluate(item: Item, now: Long, config: Config, zone: ZoneId): Hit? {
        if (item.status != ItemStatus.ACTIVE) return null
        if (item.type == ItemType.NOTE) return null
        if (item.snoozedUntil != null && item.snoozedUntil > now) return null

        val captured = Days.between(item.createdAt, now, zone)
        val quiet = Days.between(item.touchedAt, now, zone)
        val overdue = item.dueAt?.let { Days.between(it, now, zone) }?.coerceAtLeast(0) ?: 0

        fun hit(reason: Reason, score: Int) = Hit(item, reason, captured, quiet, overdue, score)

        if (item.someday) {
            return if (quiet >= config.somedayDays) hit(Reason.UNTOUCHED, 100 + quiet) else null
        }
        if (overdue > 0) return hit(Reason.OVERDUE, 300 + overdue)
        if (item.snoozeCount >= config.snoozeLimit) return hit(Reason.POSTPONED, 200 + item.snoozeCount)

        // Something with a date or reminder still ahead of it has a plan; it isn't forgotten.
        val planned = (item.dueAt != null && item.dueAt >= Days.startOfDay(now, zone)) ||
            (item.reminderAt != null && item.reminderAt > now)
        if (planned) return null

        if (quiet >= config.quietDays) return hit(Reason.UNTOUCHED, 100 + quiet)
        if (captured >= config.lingerDays && quiet >= 2) return hit(Reason.LINGERING, 50 + captured)
        return null
    }
}
