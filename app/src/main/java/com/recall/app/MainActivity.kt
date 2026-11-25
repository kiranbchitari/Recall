package com.recall.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.recall.app.data.DemoData
import com.recall.app.notify.Notifier
import com.recall.app.ui.Launch
import com.recall.app.ui.RecallRoot
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var launch by mutableStateOf<Launch?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            launch = intent.toLaunch()
            seedDemoDataIfAsked(intent)
        }
        setContent { RecallRoot(launch, onLaunchHandled = { launch = null }) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        launch = intent.toLaunch()
    }

    private fun Intent.toLaunch(): Launch? {
        val itemId = getLongExtra(Notifier.EXTRA_ITEM_ID, -1)
        return when {
            itemId >= 0 -> Launch.OpenItem(itemId)
            getBooleanExtra(Notifier.EXTRA_OPEN_REVIEW, false) -> Launch.OpenReview
            else -> null
        }
    }

    /**
     * Debug builds only: `adb shell am start -n com.recall.app/.MainActivity --ez seed true`
     * replaces everything with sample items, for screenshots and manual testing.
     */
    private fun seedDemoDataIfAsked(intent: Intent) {
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!debuggable || !intent.getBooleanExtra("seed", false)) return
        val container = (application as RecallApp).container
        // `--ei remind_in 30` also makes the first sample item remind in 30 seconds, to try notifications.
        val remindIn = intent.getIntExtra("remind_in", 0)
        container.scope.launch {
            val now = System.currentTimeMillis()
            container.repo.replaceAll(
                DemoData.items(now).mapIndexed { index, item ->
                    if (index == 0 && remindIn > 0) item.copy(reminderAt = now + remindIn * 1000L) else item
                }
            )
        }
    }
}
