package app.dialtone.phone.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.ContactsContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalGlanceId
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Speed dial widget: one person's face and name on the home screen; tap to call them.
 *
 * Each widget remembers its own contact and number, picked in [SpeedDialConfigActivity] when the
 * widget is placed (and again from the launcher's "reconfigure" option on Android 12+). The name
 * and photo are re-read from the contact every time it's drawn, so edits show up; if the contact
 * is deleted the widget keeps the name and number it was set up with and still calls it.
 *
 * Unlike the other widgets it has no background of its own, like an app icon.
 */
class SpeedDialWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    private class Shown(val name: String, val number: String, val photo: Bitmap?)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme { Content() }
        }
    }

    @Composable
    private fun Content() {
        val context = LocalContext.current
        val glanceId = LocalGlanceId.current
        val appWidgetId = remember(glanceId) {
            runCatching { GlanceAppWidgetManager(context).getAppWidgetId(glanceId) }
                .getOrDefault(AppWidgetManager.INVALID_APPWIDGET_ID)
        }
        val tick by Widgets.tick.collectAsState()
        val size = LocalSize.current
        // Room for the name under the face; the rest goes to the face (48–72dp).
        val avatar = (minOf(size.width.value, size.height.value - 24f)).coerceIn(40f, 72f).dp
        val shown by produceState<Shown?>(null, tick, avatar) {
            value = load(context, appWidgetId, WidgetUi.dpToPx(context, avatar))
        }
        val s = shown
        val tap = if (s != null) {
            WidgetUi.quickCall(context, s.number)
        } else {
            // Not set up (the picker was skipped): tapping picks someone.
            Intent(context, SpeedDialConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier.fillMaxSize().padding(2.dp).clickable(actionStartActivity(tap)),
        ) {
            WidgetAvatar(s?.name ?: "+", s?.photo, avatar)
            Spacer(GlanceModifier.height(4.dp))
            Text(
                s?.name?.substringBefore(' ') ?: "Choose",
                maxLines = 1,
                // Over the wallpaper, like app icon labels.
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }

    private suspend fun load(context: Context, appWidgetId: Int, px: Int): Shown? = withContext(Dispatchers.IO) {
        val saved = SpeedDialStore.get(context, appWidgetId) ?: return@withContext null
        var name = saved.name
        var photoUri: String? = null
        runCatching {
            val uri = ContactsContract.Contacts.getLookupUri(saved.contactId, saved.lookupKey)
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI),
                null, null, null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    photoUri = c.getString(1)
                }
            }
        }
        Shown(name, saved.number, WidgetUi.loadPhoto(context, photoUri, px))
    }
}

/** Registers [SpeedDialWidget] with the launcher's widget picker; forgets removed widgets' contacts. */
class SpeedDialWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SpeedDialWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { SpeedDialStore.remove(context, it) }
    }
}

/**
 * Which contact and number each Speed dial widget calls, by widget id. Plain preferences: the
 * launcher's widget id is the only key we get, and there are only ever a handful.
 */
object SpeedDialStore {
    data class Entry(val contactId: Long, val lookupKey: String, val name: String, val number: String)

    private const val PREFS = "speed_dial_widgets"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(context: Context, appWidgetId: Int): Entry? {
        val p = prefs(context)
        val number = p.getString("$appWidgetId.number", null) ?: return null
        return Entry(
            contactId = p.getLong("$appWidgetId.id", 0),
            lookupKey = p.getString("$appWidgetId.lookup", "").orEmpty(),
            name = p.getString("$appWidgetId.name", number) ?: number,
            number = number,
        )
    }

    fun put(context: Context, appWidgetId: Int, entry: Entry) {
        prefs(context).edit()
            .putLong("$appWidgetId.id", entry.contactId)
            .putString("$appWidgetId.lookup", entry.lookupKey)
            .putString("$appWidgetId.name", entry.name)
            .putString("$appWidgetId.number", entry.number)
            .apply()
    }

    fun remove(context: Context, appWidgetId: Int) {
        prefs(context).edit()
            .remove("$appWidgetId.id").remove("$appWidgetId.lookup")
            .remove("$appWidgetId.name").remove("$appWidgetId.number")
            .apply()
    }
}
