package app.dialtone.voicemail

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.VoicemailContract.Voicemails
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.dialtone.MainActivity
import app.dialtone.R
import app.dialtone.telecom.CallerLookup
import app.dialtone.util.formatNumber
import app.dialtone.util.hasPermission

/**
 * Voicemail notifications. As the default phone app, Dialtone is responsible for both:
 *  - visual voicemail: one notification summarizing new, unread messages in the voicemail store
 *  - message-waiting (MWI): the carrier's basic "you have voicemail" signal, shown when visual
 *    voicemail isn't active for that SIM
 */
object VoicemailNotifier {

    private const val CHANNEL = "voicemail"
    private const val ID_VISUAL = 100
    private const val ID_MWI = 101
    private const val PREFS = "voicemail_notifier"
    private const val KEY_LAST_NOTIFIED = "last_notified_date"

    const val EXTRA_OPEN_VOICEMAIL = "app.dialtone.extra.OPEN_VOICEMAIL"

    fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, "Voicemail", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private fun canNotify(context: Context) =
        Build.VERSION.SDK_INT < 33 || context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)

    /** Called when the voicemail store reports a new message. Runs a quick provider query. */
    @SuppressLint("MissingPermission")
    fun onNewVoicemail(context: Context) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val since = prefs.getLong(KEY_LAST_NOTIFIED, 0L)

        data class Row(val id: Long, val number: String, val date: Long, val transcript: String?)
        val unread = mutableListOf<Row>()
        runCatching {
            context.contentResolver.query(
                Voicemails.CONTENT_URI,
                arrayOf(Voicemails._ID, Voicemails.NUMBER, Voicemails.DATE, Voicemails.TRANSCRIPTION),
                "${Voicemails.IS_READ} = 0 AND ${Voicemails.DELETED} = 0",
                null,
                "${Voicemails.DATE} DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    unread += Row(c.getLong(0), c.getString(1).orEmpty(), c.getLong(2), c.getString(3))
                }
            }
        }
        if (unread.isEmpty()) {
            cancelVisual(context)
            return
        }
        val newest = unread.first()
        val hasNew = newest.date > since
        prefs.edit().putLong(KEY_LAST_NOTIFIED, maxOf(since, newest.date)).apply()

        val name = CallerLookup.lookup(context, newest.number).name
            ?: newest.number.takeIf { it.isNotBlank() }?.let(::formatNumber)
            ?: "Unknown"
        val title = if (unread.size == 1) "New voicemail from $name" else "${unread.size} new voicemails"
        val body = if (unread.size == 1) {
            newest.transcript?.takeIf { it.isNotBlank() } ?: "Tap to listen"
        } else {
            "Latest from $name"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_voicemail)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setNumber(unread.size)
            .setWhen(newest.date)
            .setShowWhen(true)
            .setAutoCancel(true)
            // Only buzz for messages we haven't announced yet.
            .setOnlyAlertOnce(!hasNew)
            .setContentIntent(openVoicemailTab(context))
        if (unread.size == 1 && newest.number.isNotBlank()) {
            builder.addAction(0, "Call back", callIntent(context, Uri.fromParts("tel", newest.number, null), 1))
        }
        NotificationManagerCompat.from(context).notify(ID_VISUAL, builder.build())
    }

    fun cancelVisual(context: Context) = NotificationManagerCompat.from(context).cancel(ID_VISUAL)

    /** Basic message-waiting notification; [count] of 0 clears it. */
    @SuppressLint("MissingPermission")
    fun showMessageWaiting(context: Context, count: Int, callVoicemail: PendingIntent?) {
        if (count <= 0) {
            NotificationManagerCompat.from(context).cancel(ID_MWI)
            return
        }
        if (!canNotify(context)) return
        ensureChannel(context)
        val title = if (count == 1) "New voicemail" else "$count new voicemails"
        val dial = callVoicemail ?: callIntent(context, Uri.fromParts("voicemail", "", null), 2)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_voicemail)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentTitle(title)
            .setContentText("Tap to call voicemail")
            .setNumber(count)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(dial)
            .build()
        NotificationManagerCompat.from(context).notify(ID_MWI, notification)
    }

    private fun openVoicemailTab(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 10,
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_VOICEMAIL, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun callIntent(context: Context, uri: Uri, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode,
            Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
