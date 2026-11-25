package com.recall.app.data

import com.recall.app.domain.Days

/** Sample items for debug builds. Never used in release. */
object DemoData {
    fun items(now: Long): List<Item> {
        val day = 24 * 60 * 60 * 1000L
        val hour = 60 * 60 * 1000L
        val today = Days.startOfDay(now)
        fun item(title: String, daysAgo: Int, type: ItemType = ItemType.THOUGHT, touchedDaysAgo: Int = daysAgo) =
            Item(title = title, type = type, createdAt = now - daysAgo * day - hour, touchedAt = now - touchedDaysAgo * day - hour)

        return listOf(
            item("Renew driving licence", 18),
            item("Check SIP autopay", 12, ItemType.TASK),
            item("Explore MCP server architecture", 12, ItemType.TASK, touchedDaysAgo = 1).copy(priority = Priority.HIGH),
            item("Buy laptop bag", 9, ItemType.SHOPPING, touchedDaysAgo = 2),
            item("Check why SIP didn't execute", 7, ItemType.NOTE).copy(
                priority = Priority.HIGH,
                notes = "October instalment was skipped. Mandate may have expired.",
            ),
            item("Book vehicle service", 7),
            item("Call the electrician", 5),
            item("Plan Kerala trip (5 days)", 3, ItemType.IDEA).copy(category = "Travel"),
            item("Pay electricity bill", 2, ItemType.TASK).copy(dueAt = today, category = "Home"),
            item("Send invoice to client", 1, ItemType.TASK).copy(reminderAt = today + 17 * hour),
            item("Water the plants", 1, ItemType.TASK).copy(dueAt = today, repeat = Repeat.WEEKLY),
            item("Pick up dry cleaning", 4, touchedDaysAgo = 1).copy(snoozedUntil = today + 8 * hour, snoozeCount = 1),
            item("Dentist appointment", 2, ItemType.TASK).copy(dueAt = today + 3 * day),
            item("Read the Compose performance guide", 6, ItemType.IDEA, touchedDaysAgo = 0).copy(
                snoozedUntil = today + 3 * day + 9 * hour, snoozeCount = 1,
            ),
            item("Gift idea for Amma: a good reading lamp", 0, ItemType.IDEA),
            item("Renew gym membership", 20, ItemType.TASK).copy(status = ItemStatus.DONE, completedAt = now - 2 * day),
            item("Old router login details", 40, ItemType.NOTE).copy(status = ItemStatus.ARCHIVED),
        ).mapIndexed { index, item -> item.copy(id = index + 1L) }
    }
}
