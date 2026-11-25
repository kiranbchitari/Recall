package com.recall.app.backup

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.recall.app.RecallApp
import com.recall.app.data.Item
import com.recall.app.data.ItemRepository
import com.recall.app.data.ItemStatus
import com.recall.app.data.ItemType
import com.recall.app.data.Priority
import com.recall.app.data.Repeat
import com.recall.app.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** The backup file format: one self-describing JSON document. */
object BackupCodec {
    private const val VERSION = 1
    private const val APP = "recall"

    fun encode(items: List<Item>, now: Long = System.currentTimeMillis()): String = JSONObject()
        .put("app", APP)
        .put("version", VERSION)
        .put("exportedAt", now)
        .put("items", JSONArray().apply { items.forEach { put(it.toJson()) } })
        .toString()

    /** Parses a backup fully before anything is written, so a bad file can never wipe good data. */
    fun decode(text: String): List<Item> {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw BackupException("This file isn't a Recall backup.")
        }
        if (root.optString("app") != APP || !root.has("items")) throw BackupException("This file isn't a Recall backup.")
        if (root.optInt("version", 0) > VERSION) {
            throw BackupException("This backup was made by a newer version of the app.")
        }
        val array = root.getJSONArray("items")
        return List(array.length()) { array.getJSONObject(it).toItem() }
    }

    private fun Item.toJson() = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("notes", notes)
        .put("type", type.name)
        .put("status", status.name)
        .put("priority", priority.name)
        .put("category", category ?: JSONObject.NULL)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
        .put("touchedAt", touchedAt)
        .put("dueAt", dueAt ?: JSONObject.NULL)
        .put("reminderAt", reminderAt ?: JSONObject.NULL)
        .put("snoozedUntil", snoozedUntil ?: JSONObject.NULL)
        .put("someday", someday)
        .put("snoozeCount", snoozeCount)
        .put("completedAt", completedAt ?: JSONObject.NULL)
        .put("repeat", repeat.name)

    private fun JSONObject.toItem(): Item {
        val created = getLong("createdAt")
        return Item(
            id = optLong("id", 0),
            title = getString("title"),
            notes = optString("notes"),
            type = enumOr(optString("type"), ItemType.THOUGHT),
            status = enumOr(optString("status"), ItemStatus.ACTIVE),
            priority = enumOr(optString("priority"), Priority.NONE),
            category = if (isNull("category")) null else optString("category").ifBlank { null },
            createdAt = created,
            updatedAt = optLong("updatedAt", created),
            touchedAt = optLong("touchedAt", created),
            dueAt = longOrNull("dueAt"),
            reminderAt = longOrNull("reminderAt"),
            snoozedUntil = longOrNull("snoozedUntil"),
            someday = optBoolean("someday"),
            snoozeCount = optInt("snoozeCount"),
            completedAt = longOrNull("completedAt"),
            repeat = enumOr(optString("repeat"), Repeat.NONE),
        )
    }

    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)

    private inline fun <reified T : Enum<T>> enumOr(value: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: default
}

class BackupException(message: String) : Exception(message)

/**
 * Backup and restore, to Google Drive or to any file the user picks.
 *
 * Drive backups are versioned: each one is a new timestamped file in the app's private Drive
 * folder, and only the newest [KEEP] are kept. A restore always saves what was on the phone
 * first, so it can be undone.
 */
class BackupManager(
    private val context: Context,
    private val repo: ItemRepository,
    private val settings: SettingsStore,
) {
    private val safetyCopy get() = File(context.filesDir, "before-restore.json")

    suspend fun backupToDrive(token: String): Int = try {
        val items = repo.getAll()
        val drive = DriveClient(token)
        drive.upload("$PREFIX${System.currentTimeMillis()}.json", BackupCodec.encode(items))
        drive.list(PREFIX).drop(KEEP).forEach { runCatching { drive.delete(it.id) } }
        settings.update { it.copy(driveEnabled = true, lastBackupAt = System.currentTimeMillis(), lastBackupError = "") }
        items.size
    } catch (e: Exception) {
        settings.update { it.copy(lastBackupError = e.message ?: "Backup didn't finish") }
        throw e
    }

    suspend fun listDriveBackups(token: String): List<DriveFile> = DriveClient(token).list(PREFIX)

    suspend fun restoreFromDrive(token: String, fileId: String): Int =
        restore(BackupCodec.decode(DriveClient(token).download(fileId)))

    suspend fun exportTo(uri: Uri): Int = withContext(Dispatchers.IO) {
        val items = repo.getAll()
        val out = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw BackupException("Couldn't write to that location.")
        out.use { it.write(BackupCodec.encode(items).toByteArray()) }
        items.size
    }

    suspend fun importFrom(uri: Uri): Int {
        val text = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        } ?: throw BackupException("Couldn't read that file.")
        return restore(BackupCodec.decode(text))
    }

    /** Puts back whatever was on the phone before the most recent restore. */
    suspend fun undoRestore() {
        val text = withContext(Dispatchers.IO) { safetyCopy.takeIf { it.exists() }?.readText() } ?: return
        repo.replaceAll(BackupCodec.decode(text))
    }

    private suspend fun restore(items: List<Item>): Int {
        val current = BackupCodec.encode(repo.getAll())
        withContext(Dispatchers.IO) { safetyCopy.writeText(current) }
        repo.replaceAll(items)
        return items.size
    }

    companion object {
        const val PREFIX = "recall-backup-"
        const val KEEP = 5
        private const val AUTO_BACKUP = "auto-backup"

        fun scheduleAuto(context: Context, enabled: Boolean) {
            val work = WorkManager.getInstance(context)
            if (!enabled) {
                work.cancelUniqueWork(AUTO_BACKUP)
                return
            }
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                .build()
            work.enqueueUniquePeriodicWork(AUTO_BACKUP, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/** Daily Drive backup on Wi-Fi. Skips quietly when nothing changed since the last one. */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as RecallApp).container
        val settings = container.settings.current()
        if (!settings.driveEnabled || !settings.autoBackup) return Result.success()

        val lastChange = container.repo.getAll().maxOfOrNull { it.updatedAt } ?: return Result.success()
        if (lastChange <= settings.lastBackupAt) return Result.success()

        return when (val auth = DriveAuth.authorize(applicationContext)) {
            is DriveAuth.Outcome.Token ->
                runCatching { container.backup.backupToDrive(auth.value) }
                    .fold({ Result.success() }, { Result.retry() })
            is DriveAuth.Outcome.NeedsConsent -> {
                container.settings.update { it.copy(lastBackupError = "Google Drive needs you to sign in again") }
                Result.success()
            }
            is DriveAuth.Outcome.Failed -> {
                container.settings.update { it.copy(lastBackupError = auth.message) }
                Result.success()
            }
        }
    }
}
