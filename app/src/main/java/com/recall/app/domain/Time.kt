package com.recall.app.domain

import com.recall.app.data.Item
import com.recall.app.data.Repeat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

object Days {
    fun date(ms: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    fun startOfDay(ms: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        date(ms, zone).atStartOfDay(zone).toInstant().toEpochMilli()

    /** Calendar days from [from] to [to]; negative when [from] is later. */
    fun between(from: Long, to: Long, zone: ZoneId = ZoneId.systemDefault()): Int =
        ChronoUnit.DAYS.between(date(from, zone), date(to, zone)).toInt()

    fun isToday(ms: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        between(ms, now, zone) == 0
}

/** The handful of ways to push something away. Deliberately not a scheduling UI. */
object Snooze {
    enum class Kind { LATER_TODAY, TOMORROW, WEEKEND, NEXT_WEEK, SOMEDAY }

    data class Option(val kind: Kind, val label: String, val hint: String, val until: Long?)

    private const val MORNING = 9

    fun options(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<Option> {
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        val out = mutableListOf<Option>()

        laterToday(now)?.let { out += Option(Kind.LATER_TODAY, "Later today", timeOf(it), it.toMs()) }

        val tomorrow = tomorrowMorning(now)
        out += Option(Kind.TOMORROW, "Tomorrow", "${dayOf(tomorrow)}, ${timeOf(tomorrow)}", tomorrow.toMs())

        val weekend = weekend(now)
        val thisWeekend = ChronoUnit.DAYS.between(now.toLocalDate(), weekend.toLocalDate()) < 7 &&
            now.dayOfWeek != DayOfWeek.SATURDAY && now.dayOfWeek != DayOfWeek.SUNDAY
        out += Option(
            Kind.WEEKEND,
            if (thisWeekend) "This weekend" else "Next weekend",
            "${dayOf(weekend)}, ${timeOf(weekend)}",
            weekend.toMs(),
        )

        val nextWeek = nextWeek(now)
        out += Option(Kind.NEXT_WEEK, "Next week", "${dayOf(nextWeek)}, ${timeOf(nextWeek)}", nextWeek.toMs())

        out += Option(Kind.SOMEDAY, "Someday", "No date", null)
        return out
    }

    /** Three hours on, rounded to the half hour; not offered late in the evening. */
    fun laterToday(now: ZonedDateTime): ZonedDateTime? {
        val t = now.plusHours(3).truncatedTo(ChronoUnit.HOURS)
            .plusMinutes(if (now.minute >= 30) 30 else 0)
        return if (t.toLocalDate() == now.toLocalDate() && t.hour <= 22) t else null
    }

    fun tomorrowMorning(now: ZonedDateTime): ZonedDateTime =
        now.toLocalDate().plusDays(1).atTime(MORNING, 0).atZone(now.zone)

    /** The coming Saturday at 10; on a weekend, the Saturday after. */
    fun weekend(now: ZonedDateTime): ZonedDateTime =
        now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.SATURDAY)).atTime(10, 0).atZone(now.zone)

    fun nextWeek(now: ZonedDateTime): ZonedDateTime =
        now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atTime(MORNING, 0).atZone(now.zone)

    /** A picked calendar day comes back that morning. */
    fun onDate(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atTime(MORNING, 0).atZone(zone).toMs()

    private fun ZonedDateTime.toMs() = toInstant().toEpochMilli()
    private fun timeOf(t: ZonedDateTime) = TimeText.time(t.toLocalTime())
    private fun dayOf(t: ZonedDateTime) = t.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
}

object Recurrence {
    /**
     * The next occurrence of a repeating item that was just completed, or null if it doesn't repeat.
     * Due date and reminder both roll forward to the first slot after [now]. An item with neither
     * simply stays out of the way until its next turn.
     */
    fun next(item: Item, now: Long, zone: ZoneId = ZoneId.systemDefault()): Item? {
        if (item.repeat == Repeat.NONE) return null

        fun advance(ms: Long): Long {
            var t = Instant.ofEpochMilli(ms).atZone(zone)
            do {
                t = when (item.repeat) {
                    Repeat.DAILY -> t.plusDays(1)
                    Repeat.WEEKLY -> t.plusWeeks(1)
                    Repeat.MONTHLY -> t.plusMonths(1)
                    Repeat.YEARLY -> t.plusYears(1)
                    Repeat.NONE -> t
                }
            } while (t.toInstant().toEpochMilli() <= now)
            return t.toInstant().toEpochMilli()
        }

        val due = item.dueAt?.let(::advance)
        val reminder = item.reminderAt?.let(::advance)
        val wake = if (due == null && reminder == null) {
            advance(Days.date(now, zone).atTime(9, 0).atZone(zone).toInstant().toEpochMilli())
        } else null

        return item.copy(
            id = 0,
            createdAt = now,
            updatedAt = now,
            touchedAt = now,
            completedAt = null,
            dueAt = due,
            reminderAt = reminder,
            snoozedUntil = wake,
            someday = false,
            snoozeCount = 0,
        )
    }
}

/** Human, low-pressure wording for dates. */
object TimeText {
    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM")
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy")

    fun time(t: LocalTime): String = t.format(timeFmt)

    fun minutesOfDay(minutes: Int): String = time(LocalTime.of(minutes / 60, minutes % 60))

    fun ago(ms: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val days = Days.between(ms, now, zone)
        val minutes = ((now - ms) / 60_000).coerceAtLeast(0)
        return when {
            days <= 0 && minutes < 1 -> "Just now"
            days <= 0 && minutes < 60 -> "$minutes min ago"
            days <= 0 -> (minutes / 60).let { if (it == 1L) "1 hour ago" else "$it hours ago" }
            days == 1 -> "Yesterday"
            days < 60 -> "$days days ago"
            days < 365 -> "${days / 30} months ago"
            else -> date(ms, now, zone)
        }
    }

    fun days(n: Int): String = if (n == 1) "1 day" else "$n days"

    /** "Today", "Tomorrow", "Fri", "12 Dec", "12 Dec 2027". */
    fun date(ms: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val d = Days.date(ms, zone)
        val today = Days.date(now, zone)
        val diff = ChronoUnit.DAYS.between(today, d)
        return when {
            diff == 0L -> "Today"
            diff == 1L -> "Tomorrow"
            diff == -1L -> "Yesterday"
            diff in 2..6 -> d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            d.year == today.year -> d.format(dayMonth)
            else -> d.format(dayMonthYear)
        }
    }

    fun dateTime(ms: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        "${date(ms, now, zone)}, ${time(Instant.ofEpochMilli(ms).atZone(zone).toLocalTime())}"

    fun due(dueAt: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val late = Days.between(dueAt, now, zone)
        return when {
            late > 1 -> "Was due $late days ago"
            late == 1 -> "Was due yesterday"
            else -> "Due ${date(dueAt, now, zone).replaceFirstChar { it.lowercase() }.let(::keepWeekdayCase)}"
        }
    }

    fun returns(item: Item, now: Long, zone: ZoneId = ZoneId.systemDefault()): String? = when {
        item.someday -> "Someday"
        item.snoozedUntil != null && item.snoozedUntil > now -> {
            val until = item.snoozedUntil
            if (Days.isToday(until, now, zone)) {
                "Back at ${time(Instant.ofEpochMilli(until).atZone(zone).toLocalTime())}"
            } else {
                "Back ${date(until, now, zone).replaceFirstChar { it.lowercase() }.let(::keepWeekdayCase)}"
            }
        }
        else -> null
    }

    /** "today"/"tomorrow" read better lower-case mid-sentence; weekdays and dates keep their capital. */
    private fun keepWeekdayCase(s: String): String =
        if (s == "today" || s == "tomorrow" || s == "yesterday") s else s.replaceFirstChar { it.uppercase() }
}
