package com.recall.app.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.LocalSize
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.recall.app.CaptureActivity
import com.recall.app.MainActivity
import com.recall.app.R
import com.recall.app.RecallApp
import com.recall.app.data.AppSettings
import com.recall.app.domain.ForgottenRadar
import com.recall.app.domain.TimeText
import com.recall.app.notify.Notifier

/**
 * Home-screen widget: capture in one tap, plus a glance at what Forgotten Radar has found.
 * It follows the phone's own colours so it sits naturally on any wallpaper.
 */
class RecallWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as RecallApp).container
        provideContent {
            val items by container.repo.items.collectAsState(initial = emptyList())
            val settings by container.settings.flow.collectAsState(initial = AppSettings())
            val hits = ForgottenRadar.scan(items, System.currentTimeMillis(), ForgottenRadar.Config(quietDays = settings.quietDays))
            GlanceTheme { Content(context, hits) }
        }
    }

    @Composable
    private fun Content(context: Context, hits: List<ForgottenRadar.Hit>) {
        val colors = GlanceTheme.colors
        Column(
            GlanceModifier.fillMaxSize().background(colors.widgetBackground).cornerRadius(24.dp).padding(12.dp)
        ) {
            Row(
                GlanceModifier.fillMaxWidth().height(48.dp).background(colors.secondaryContainer).cornerRadius(24.dp)
                    .clickable(actionStartActivity(Intent(context, CaptureActivity::class.java)))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    ImageProvider(R.drawable.ic_widget_edit), null,
                    GlanceModifier.size(20.dp), colorFilter = ColorFilter.tint(colors.primary),
                )
                Spacer(GlanceModifier.width(10.dp))
                Text(
                    "Remember something…",
                    GlanceModifier.defaultWeight(),
                    TextStyle(color = colors.onSecondaryContainer, fontSize = 15.sp),
                    maxLines = 1,
                )
                Image(
                    ImageProvider(R.drawable.ic_widget_mic), "Capture by voice",
                    GlanceModifier.size(36.dp).padding(7.dp).clickable(
                        actionStartActivity(
                            Intent(context, CaptureActivity::class.java)
                                .setData(Uri.parse("recall://capture/voice"))
                                .putExtra(CaptureActivity.EXTRA_VOICE, true)
                        )
                    ),
                    colorFilter = ColorFilter.tint(colors.onSecondaryContainer),
                )
            }
            Spacer(GlanceModifier.height(10.dp))
            if (hits.isEmpty()) {
                Text(
                    "Nothing slipping through right now.",
                    GlanceModifier.padding(horizontal = 6.dp),
                    TextStyle(color = colors.onSurfaceVariant, fontSize = 13.sp),
                )
            } else {
                Text(
                    "Forgotten Radar",
                    GlanceModifier.padding(horizontal = 6.dp),
                    TextStyle(color = colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
                hits.take(3).forEach { hit ->
                    Row(
                        GlanceModifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp).clickable(
                            actionStartActivity(
                                Intent(context, MainActivity::class.java)
                                    .setData(Uri.parse("recall://item/${hit.item.id}"))
                                    .putExtra(Notifier.EXTRA_ITEM_ID, hit.item.id)
                            )
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            hit.item.title,
                            GlanceModifier.defaultWeight(),
                            TextStyle(color = colors.onSurface, fontSize = 14.sp),
                            maxLines = 1,
                        )
                        Spacer(GlanceModifier.width(8.dp))
                        Text(
                            TimeText.days(hit.daysCaptured),
                            style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

class RecallWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecallWidget()
}

/**
 * Icon-sized widget: the app's capture button on the home screen. One tap opens quick capture
 * over whatever is on screen. Unlike the larger widget it keeps the brand colour, so it reads
 * as the same "+" button as inside the app.
 */
class CaptureWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val size = LocalSize.current
            // As large as the cell allows, but never bigger than a comfortable button.
            val diameter = (minOf(size.width, size.height) - 6.dp).coerceIn(40.dp, 68.dp)
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Image(
                    ImageProvider(R.drawable.widget_capture_button),
                    contentDescription = context.getString(R.string.shortcut_capture_long),
                    modifier = GlanceModifier.size(diameter).clickable(
                        actionStartActivity(
                            Intent(context, CaptureActivity::class.java).setData(Uri.parse("recall://capture/button"))
                        )
                    ),
                )
            }
        }
    }
}

class CaptureWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CaptureWidget()
}
