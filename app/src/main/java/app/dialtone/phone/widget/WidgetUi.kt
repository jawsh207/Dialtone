package app.dialtone.phone.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.dialtone.phone.MainActivity
import app.dialtone.phone.util.initialOf

/**
 * Pieces shared by Dialtone's home screen widgets and app shortcuts: contact avatars, photo
 * loading, and the intents they open.
 *
 * Widgets are drawn by the launcher from RemoteViews (built here with Glance), so they can't use
 * the app's Compose shapes. Photos are cut into circles as bitmaps here instead, which also works
 * on Android 10–11, where widget views can't round their corners.
 */
object WidgetUi {

    /** Opens Dialtone on the Voicemail tab (see [MainActivity]). */
    const val ACTION_OPEN_VOICEMAIL = "app.dialtone.phone.action.OPEN_VOICEMAIL"

    /** Places a call to the intent's tel: number (see [QuickCallActivity]). */
    const val ACTION_QUICK_CALL = "app.dialtone.phone.action.QUICK_CALL"

    fun openApp(context: Context): Intent =
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun openVoicemail(context: Context): Intent =
        openApp(context).setAction(ACTION_OPEN_VOICEMAIL)

    fun quickCall(context: Context, number: String): Intent =
        Intent(ACTION_QUICK_CALL, Uri.fromParts("tel", number, null), context, QuickCallActivity::class.java)

    /**
     * A contact's thumbnail, scaled to [sizePx] and cut into a circle if [circle]; null when there
     * is no photo or it can't be read. Kept small: a widget's bitmaps all travel to the launcher
     * in one parcel, which has a size limit.
     */
    fun loadPhoto(context: Context, uri: String?, sizePx: Int, circle: Boolean = true): Bitmap? {
        if (uri.isNullOrBlank()) return null
        val decoded = runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use(BitmapFactory::decodeStream)
        }.getOrNull() ?: return null
        val side = minOf(decoded.width, decoded.height)
        val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, sizePx, sizePx, true)
        if (!circle) return scaled
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        Canvas(out).drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)
        return out
    }

    fun dpToPx(context: Context, dp: Dp): Int = (dp.value * context.resources.displayMetrics.density).toInt()
}

/** A contact's photo, or their initial on a tinted circle. */
@Composable
fun WidgetAvatar(name: String, photo: Bitmap?, size: Dp) {
    if (photo != null) {
        Image(
            provider = ImageProvider(photo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = GlanceModifier.size(size),
        )
        return
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = GlanceModifier
            .size(size)
            .cornerRadius(size / 2)
            .background(GlanceTheme.colors.primaryContainer),
    ) {
        Text(
            initialOf(name) ?: name.trim().take(1).ifEmpty { "#" },
            style = TextStyle(
                color = GlanceTheme.colors.onPrimaryContainer,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

/** Standard widget padding inside the launcher's rounded background. */
val WidgetPadding = 12.dp
