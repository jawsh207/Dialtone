package app.dialtone.phone.shortcuts

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.dialtone.phone.data.CallLogRepository
import app.dialtone.phone.data.ContactsRepository
import app.dialtone.phone.data.FavoriteRanking
import app.dialtone.phone.util.hasPermission
import app.dialtone.phone.util.initialOf
import app.dialtone.phone.widget.WidgetUi

/**
 * The long-press menu on Dialtone's app icon.
 *
 * Keypad and Voicemail are fixed (res/xml/shortcuts.xml). Below them, the favorites you call most
 * ([FavoriteRanking]) appear as "call this person" shortcuts; this file keeps those current, and
 * [app.dialtone.phone.widget.Widgets] calls it whenever contacts or the call log change.
 *
 * Launchers show only four or five entries in all, so at most three favorites. Android limits
 * how often a background app may change its shortcuts, so nothing is republished when the list
 * hasn't changed (which is most calls).
 */
object AppShortcuts {

    private const val MAX_FAVORITES = 3
    private const val STATIC_COUNT = 2
    private const val ID_PREFIX = "fav-"

    suspend fun update(context: Context) {
        if (!context.hasPermission(Manifest.permission.READ_CONTACTS)) return
        val favorites = ContactsRepository(context).load().filter { it.starred && it.numbers.isNotEmpty() }
        val calls = if (context.hasPermission(Manifest.permission.READ_CALL_LOG)) CallLogRepository(context).load() else emptyList()
        val room = (ShortcutManagerCompat.getMaxShortcutCountPerActivity(context) - STATIC_COUNT).coerceIn(0, MAX_FAVORITES)
        val top = FavoriteRanking.rank(favorites, calls, System.currentTimeMillis()).take(room)

        // Same people, order, names and numbers as now: leave them alone.
        val wanted = top.map { "$ID_PREFIX${it.lookupKey.ifEmpty { it.id.toString() }}" to it }
        val current = ShortcutManagerCompat.getDynamicShortcuts(context).sortedBy { it.rank }
        val unchanged = current.size == wanted.size && current.zip(wanted).all { (s, w) ->
            s.id == w.first && s.shortLabel.toString() == w.second.name &&
                s.intent.data?.schemeSpecificPart == w.second.numbers.first().number
        }
        if (unchanged) return

        val sizePx = (108 * context.resources.displayMetrics.density).toInt()
        val shortcuts = wanted.mapIndexed { rank, (id, contact) ->
            val number = contact.numbers.first().number
            val photo = WidgetUi.loadPhoto(context, contact.photoUri, sizePx, circle = false)
            ShortcutInfoCompat.Builder(context, id)
                .setShortLabel(contact.name)
                .setLongLabel("Call ${contact.name}")
                .setIcon(IconCompat.createWithAdaptiveBitmap(photo ?: initialIcon(context, contact.name, sizePx)))
                .setIntent(WidgetUi.quickCall(context, number))
                .setRank(rank)
                .build()
        }
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }

    /**
     * A contact's initial on a tinted square, for people without a photo. Adaptive icons are
     * cropped by the launcher to its own shape; the letter sits inside the safe middle area.
     */
    private fun initialIcon(context: Context, name: String, sizePx: Int): Bitmap {
        val (bg, fg) = if (Build.VERSION.SDK_INT >= 31) {
            context.getColor(android.R.color.system_accent1_100) to context.getColor(android.R.color.system_accent1_900)
        } else {
            Color.rgb(0xC4, 0xEE, 0xD0) to Color.rgb(0x07, 0x2E, 0x15)
        }
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(bg)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg
            textSize = sizePx * 0.3f
            textAlign = Paint.Align.CENTER
        }
        val letter = initialOf(name) ?: "#"
        val y = sizePx / 2f - (paint.descent() + paint.ascent()) / 2
        canvas.drawText(letter, sizePx / 2f, y, paint)
        return out
    }
}
