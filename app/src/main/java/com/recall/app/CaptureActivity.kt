package com.recall.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recall.app.ui.components.CaptureSheet
import com.recall.app.ui.theme.RecallTheme

/**
 * Capture without opening the app: a bottom sheet over whatever the user was doing.
 * Reached from the home-screen widget, the launcher shortcut, and "Share" in other apps.
 */
class CaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as RecallApp).container
        val shared = intent.takeIf { it.action == Intent.ACTION_SEND }
            ?.let { listOfNotNull(it.getStringExtra(Intent.EXTRA_SUBJECT), it.getStringExtra(Intent.EXTRA_TEXT)) }
            ?.joinToString(" — ")
            .orEmpty()
        // The launcher shortcut can only pass strings, the widget passes a boolean; accept both.
        @Suppress("DEPRECATION")
        val voice = intent.extras?.get(EXTRA_VOICE)?.toString() == "true"

        setContent {
            val settings by container.settings.flow.collectAsStateWithLifecycle(initialValue = null)
            val s = settings ?: return@setContent
            RecallTheme(s.theme, s.accent) {
                CaptureSheet(
                    onDismiss = ::finish,
                    onCapture = { text, type ->
                        container.actions.capture(text, type, announce = false)
                        Toast.makeText(this, "Saved to Recall", Toast.LENGTH_SHORT).show()
                    },
                    initialText = shared,
                    startWithVoice = voice,
                )
            }
        }
    }

    companion object {
        const val EXTRA_VOICE = "voice"
    }
}
