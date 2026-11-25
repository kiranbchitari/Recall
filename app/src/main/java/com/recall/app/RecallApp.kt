package com.recall.app

import android.app.Application
import androidx.glance.appwidget.updateAll
import com.recall.app.backup.BackupManager
import com.recall.app.data.Item
import com.recall.app.data.ItemRepository
import com.recall.app.data.ItemType
import com.recall.app.data.RecallDatabase
import com.recall.app.data.SettingsStore
import com.recall.app.data.Undo
import com.recall.app.domain.TimeText
import com.recall.app.notify.Notifier
import com.recall.app.notify.Scheduler
import com.recall.app.widget.RecallWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class RecallApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        container = AppContainer(this)
        container.start()
    }
}

/** Hand-rolled dependency container: the app is small enough that a DI framework would be noise. */
class AppContainer(private val app: Application) {
    /** Outlives any screen, so a save or undo is never cut short by navigation. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings = SettingsStore(app)
    val scheduler = Scheduler(app)
    private val database by lazy { RecallDatabase.create(app) }
    val repo by lazy { ItemRepository(database.items(), scheduler, onChanged = ::refreshWidget) }
    val backup by lazy { BackupManager(app, repo, settings) }
    val messages = Messages()
    val actions by lazy { ItemActions(repo, messages, scope) }

    fun start() {
        scope.launch {
            settings.flow
                .map { Triple(it.dailyReview, it.dailyReviewAt, it.forgottenNudge) to (it.driveEnabled && it.autoBackup) }
                .distinctUntilChanged()
                .collect { (_, autoBackup) ->
                    scheduler.apply(settings.current())
                    BackupManager.scheduleAuto(app, autoBackup)
                }
        }
    }

    private var widgetJob: Job? = null

    private fun refreshWidget() {
        widgetJob?.cancel()
        widgetJob = scope.launch(Dispatchers.Default) { runCatching { RecallWidget().updateAll(app) } }
    }
}

data class UiMessage(val text: String, val undo: Undo? = null)

/** One-line confirmations shown as a snackbar, each with an optional way back. */
class Messages {
    private val _flow = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val flow = _flow.asSharedFlow()

    fun show(text: String, undo: Undo? = null) {
        _flow.tryEmit(UiMessage(text, undo))
    }
}

/** What the user can do to an item, from any screen. Every action confirms itself and can be undone. */
class ItemActions(
    private val repo: ItemRepository,
    private val messages: Messages,
    private val scope: CoroutineScope,
) {
    fun capture(text: String, type: ItemType? = null, announce: Boolean = true) = scope.launch {
        val item = repo.capture(text, type) ?: return@launch
        if (announce) messages.show("Saved to your inbox") { repo.delete(item.id) }
    }

    fun done(item: Item) = act("Done") { repo.complete(item.id) }

    fun reopen(item: Item) = act("Back in your inbox") { repo.reopen(item.id) }

    fun snooze(item: Item, until: Long?) = act(
        if (until == null) "Kept for someday"
        else "Coming back ${TimeText.dateTime(until, System.currentTimeMillis()).replaceFirstChar { it.lowercase() }}"
    ) { repo.snooze(item.id, until) }

    fun wake(item: Item) = act("Back in view") { repo.wake(item.id) }

    fun notImportant(item: Item) = act("Let go") { repo.dismiss(item.id) }

    fun archive(item: Item) = act("Archived") { repo.archive(item.id) }

    fun delete(item: Item) = act("Deleted") { repo.delete(item.id) }

    fun edit(id: Long, transform: (Item) -> Item) = scope.launch { repo.edit(id, transform) }

    fun touch(id: Long) = scope.launch { repo.touch(id) }

    private fun act(text: String, block: suspend () -> Undo?) = scope.launch {
        val undo = block() ?: return@launch
        messages.show(text, undo)
    }
}
