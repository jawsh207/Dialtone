package app.dialtone.phone.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
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
import app.dialtone.phone.R
import app.dialtone.phone.data.VoicemailRepository
import app.dialtone.phone.telecom.CallerLookup
import app.dialtone.phone.util.formatDuration
import app.dialtone.phone.util.formatNumber
import app.dialtone.phone.util.formatShortDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Voicemail widget: how many messages are new, and the newest one's caller, time and the start
 * of its transcript. Tapping it opens the Voicemail tab.
 *
 * It reads the same voicemail store as the Voicemail tab (so duplicates from a carrier app are
 * merged the same way) and uses whatever transcript the tab would show: the carrier's or the one
 * from your speech-to-text app. At its smallest size it drops the transcript.
 */
class VoicemailWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, LARGE))

    private class Newest(val caller: String, val time: String, val text: String)
    private class State(val unread: Int, val newest: Newest?)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme { Content() }
        }
    }

    @Composable
    private fun Content() {
        val context = LocalContext.current
        val tick by Widgets.tick.collectAsState()
        val state by produceState<State?>(null, tick) { value = load(context) }
        val roomy = LocalSize.current.height >= LARGE.height
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .cornerRadius(24.dp)
                .background(GlanceTheme.colors.widgetBackground)
                .padding(horizontal = 16.dp, vertical = WidgetPadding)
                .clickable(actionStartActivity(WidgetUi.openVoicemail(context))),
            verticalAlignment = if (roomy) Alignment.Top else Alignment.CenterVertically,
        ) {
            val s = state ?: return@Column
            Header(s.unread)
            val newest = s.newest
            if (newest == null) {
                Spacer(GlanceModifier.height(6.dp))
                Text("No voicemail", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
                return@Column
            }
            Spacer(GlanceModifier.height(if (roomy) 10.dp else 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    newest.caller,
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(newest.time, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
            }
            if (roomy) {
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    newest.text,
                    maxLines = 3,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                )
            }
        }
    }

    @Composable
    private fun Header(unread: Int) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            Image(
                provider = ImageProvider(R.drawable.ic_stat_voicemail),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                modifier = GlanceModifier.size(20.dp),
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                "Voicemail",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                modifier = GlanceModifier.defaultWeight(),
            )
            if (unread > 0) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = GlanceModifier
                        .cornerRadius(12.dp)
                        .background(GlanceTheme.colors.primary)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        "$unread new",
                        style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    )
                }
            }
        }
    }

    private suspend fun load(context: Context): State = withContext(Dispatchers.IO) {
        val all = VoicemailRepository(context).load()
        val newest = all.firstOrNull()?.let { vm ->
            val caller = when {
                vm.number.isBlank() -> "Unknown caller"
                else -> CallerLookup.lookup(context, vm.number).name ?: formatNumber(vm.number)
            }
            val text = vm.transcription?.trim()?.takeIf { it.isNotEmpty() }
                ?: "No transcript · ${formatDuration(vm.durationSec)}"
            Newest(caller, formatShortDate(context, vm.date), text)
        }
        State(all.count { !it.isRead }, newest)
    }

    private companion object {
        val SMALL = DpSize(180.dp, 48.dp)
        val LARGE = DpSize(180.dp, 110.dp)
    }
}

/** Registers [VoicemailWidget] with the launcher's widget picker. */
class VoicemailWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VoicemailWidget()
}
