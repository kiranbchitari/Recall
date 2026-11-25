package com.recall.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.recall.app.MainActivity
import com.recall.app.R
import com.recall.app.RecallApp
import com.recall.app.data.AppSettings
import com.recall.app.data.Item
import com.recall.app.data.ItemStatus
import com.recall.app.data.ReminderSync
import com.recall.app.domain.ForgottenRadar
import com.recall.app.domain.Snooze
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Notifications are opt-in extras: reminders the user set themselves, plus two optional,
 * low-frequency nudges. Nothing here is needed for the app to be useful.
 */
object Notifier {
    private const val CHANNEL_REMINDERS = "reminders"
    private const val CHANNEL_NUDGES = "nudges"
    private const val ID_REVIEW = -1
    private const val ID_FORGOTTEN = -2
    private const val BRAND = 0xFF6B4EE6.toInt()

    const val EXTRA_ITEM_ID = "item_id"
    const val EXTRA_OPEN_REVIEW = "open_review"
    const val ACTION_DONE = "com.recall.app.action.DONE"
    const val ACTION_LATER = "com.recall.app.action.LATER"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders you set on your own items"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_NUDGES, "Gentle nudges", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Optional daily review and forgotten-item nudges"
            }
        )
    }

    fun canNotify(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun showReminder(context: Context, item: Item) {
        val id = item.id.toInt()
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_brand_mark)
            .setContentTitle(item.title)
            .setContentText(item.notes.ifBlank { "You asked to be reminded" })
            .setColor(BRAND)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.notes.ifBlank { "You asked to be reminded" }))
            .setContentIntent(openApp(context, id) { putExtra(EXTRA_ITEM_ID, item.id) })
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "Done", action(context, ACTION_DONE, item.id))
            .addAction(0, "Tomorrow", action(context, ACTION_LATER, item.id))
            .build()
        post(context, id, notification)
    }

    fun showDailyReview(context: Context, count: Int) {
        val text = if (count == 1) "One thing is waiting. It takes a minute."
        else "$count things are waiting. A couple of minutes is plenty."
        post(context, ID_REVIEW, nudge(context, "A small review?", text, ID_REVIEW))
    }

    fun showForgotten(context: Context, hits: List<ForgottenRadar.Hit>) {
        val first = hits.first().item.title
        val text = when (hits.size) {
            1 -> "“$first” is still here whenever you're ready."
            2 -> "“$first” and one other are still here."
            else -> "“$first” and ${hits.size - 1} others are still here."
        }
        post(context, ID_FORGOTTEN, nudge(context, "Still on your mind?", text, ID_FORGOTTEN))
    }

    fun cancel(context: Context, itemId: Long) =
        NotificationManagerCompat.from(context).cancel(itemId.toInt())

    private fun nudge(context: Context, title: String, text: String, requestCode: Int) =
        NotificationCompat.Builder(context, CHANNEL_NUDGES)
            .setSmallIcon(R.drawable.ic_brand_mark)
            .setContentTitle(title)
            .setContentText(text)
            .setColor(BRAND)
            .setContentIntent(openApp(context, requestCode) { putExtra(EXTRA_OPEN_REVIEW, true) })
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    @Suppress("MissingPermission") // guarded by canNotify
    private fun post(context: Context, id: Int, notification: android.app.Notification) {
        if (canNotify(context)) NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun openApp(context: Context, requestCode: Int, extras: Intent.() -> Unit): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply(extras),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun action(context: Context, action: String, itemId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            itemId.toInt(),
            Intent(context, NotificationActionReceiver::class.java).setAction(action).putExtra(EXTRA_ITEM_ID, itemId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

/** Schedules reminder and nudge work. WorkManager keeps it across restarts and reboots. */
class Scheduler(private val context: Context) : ReminderSync {
    private val work get() = WorkManager.getInstance(context)

    override fun sync(item: Item) {
        val at = item.reminderAt
        val now = System.currentTimeMillis()
        if (item.status != ItemStatus.ACTIVE || at == null || at <= now) {
            cancel(item.id)
            return
        }
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(at - now, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(Notifier.EXTRA_ITEM_ID to item.id))
            .build()
        work.enqueueUniqueWork(reminderName(item.id), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(itemId: Long) {
        work.cancelUniqueWork(reminderName(itemId))
    }

    /** Applies the nudge-related settings. Safe to call on every change and at start-up. */
    fun apply(settings: AppSettings) {
        if (settings.dailyReview) {
            val now = ZonedDateTime.now()
            var next = now.toLocalDate().atStartOfDay(now.zone).plusMinutes(settings.dailyReviewAt.toLong())
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<NudgeWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(NudgeWorker.KIND to NudgeWorker.REVIEW))
                .build()
            work.enqueueUniquePeriodicWork(DAILY_REVIEW, ExistingPeriodicWorkPolicy.UPDATE, request)
        } else {
            work.cancelUniqueWork(DAILY_REVIEW)
        }

        if (settings.forgottenNudge) {
            // At most twice a week, and only when something is actually waiting.
            val request = PeriodicWorkRequestBuilder<NudgeWorker>(3, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.DAYS)
                .setInputData(workDataOf(NudgeWorker.KIND to NudgeWorker.FORGOTTEN))
                .build()
            work.enqueueUniquePeriodicWork(FORGOTTEN, ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            work.cancelUniqueWork(FORGOTTEN)
        }
    }

    private fun reminderName(id: Long) = "reminder-$id"

    private companion object {
        const val DAILY_REVIEW = "daily-review"
        const val FORGOTTEN = "forgotten-nudge"
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as RecallApp).container
        val item = container.repo.get(inputData.getLong(Notifier.EXTRA_ITEM_ID, -1)) ?: return Result.success()
        if (item.status == ItemStatus.ACTIVE && item.reminderAt != null && container.settings.current().reminders) {
            Notifier.showReminder(applicationContext, item)
        }
        return Result.success()
    }
}

class NudgeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as RecallApp).container
        val settings = container.settings.current()
        val hits = ForgottenRadar.scan(
            container.repo.getAll(),
            System.currentTimeMillis(),
            ForgottenRadar.Config(quietDays = settings.quietDays),
        )
        // Stay silent when nothing is waiting: no notification is better than an empty one.
        if (hits.isEmpty()) return Result.success()
        when (inputData.getString(KIND)) {
            REVIEW -> if (settings.dailyReview) Notifier.showDailyReview(applicationContext, hits.size)
            FORGOTTEN -> if (settings.forgottenNudge) Notifier.showForgotten(applicationContext, hits)
        }
        return Result.success()
    }

    companion object {
        const val KIND = "kind"
        const val REVIEW = "review"
        const val FORGOTTEN = "forgotten"
    }
}

/** Handles "Done" and "Tomorrow" straight from a reminder notification. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(Notifier.EXTRA_ITEM_ID, -1)
        if (id < 0) return
        val repo = (context.applicationContext as RecallApp).container.repo
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    Notifier.ACTION_DONE -> repo.complete(id)
                    Notifier.ACTION_LATER ->
                        repo.remindLater(id, Snooze.tomorrowMorning(ZonedDateTime.now()).toInstant().toEpochMilli())
                }
                Notifier.cancel(context, id)
            } finally {
                pending.finish()
            }
        }
    }
}
