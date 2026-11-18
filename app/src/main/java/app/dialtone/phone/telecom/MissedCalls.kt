package app.dialtone.phone.telecom

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.CallLog.Calls
import android.telecom.TelecomManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.dialtone.phone.MainActivity
import app.dialtone.phone.R
import app.dialtone.phone.util.formatNumber
import app.dialtone.phone.util.hasPermission
import app.dialtone.phone.util.numberKey

/**
 * Missed-call notifications. Android leaves these to the default phone app: when a call is
 * missed (and again after a reboot) it tells Dialtone how many are unseen, and Dialtone shows
 * its own notification instead of Android's plain fallback.
 *
 *  - **One caller:** their name, how many times they called, and Call back / Message.
 *  - **Several callers:** "N missed calls" listing who called.
 *
 * The notification is built from Dialtone's own reading of the call log (unseen missed calls),
 * not from what the message says, so nothing another app sends can change what it shows.
 * Missed calls count as seen once the Home tab (the call log) is opened, the notification is
 * dismissed, or Call back is tapped; the call log entries are then marked read.
 */
object MissedCalls {
    private const val CHANNEL = "missed_calls"
    private const val NOTIFICATION_ID = 43

    const val EXTRA_OPEN_CALL_LOG = "app.dialtone.phone.extra.OPEN_CALL_LOG"

    fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, "Missed calls", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private data class Missed(val number: String, val name: String?, val date: Long)

    /** Unseen missed calls, newest first. */
    private fun unseen(context: Context): List<Missed> {
        if (!context.hasPermission(Manifest.permission.READ_CALL_LOG)) return emptyList()
        val out = mutableListOf<Missed>()
        runCatching {
            context.contentResolver.query(
                Calls.CONTENT_URI,
                arrayOf(Calls.NUMBER, Calls.CACHED_NAME, Calls.DATE),
                "${Calls.TYPE} = ? AND ${Calls.NEW} = 1 AND (${Calls.IS_READ} IS NULL OR ${Calls.IS_READ} = 0)",
                arrayOf(Calls.MISSED_TYPE.toString()),
                "${Calls.DATE} DESC",
            )?.use { c ->
                while (c.moveToNext() && out.size < 50) {
                    out += Missed(c.getString(0).orEmpty(), c.getString(1), c.getLong(2))
                }
            }
        }
        return out
    }

    /** Shows, updates or removes the notification to match the call log. */
    @SuppressLint("MissingPermission")
    fun refresh(context: Context) {
        val calls = unseen(context)
        val nm = NotificationManagerCompat.from(context)
        if (calls.isEmpty()) {
            nm.cancel(NOTIFICATION_ID)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && !context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)) return
        ensureChannel(context)

        fun label(m: Missed): String {
            val name = m.name?.takeIf { it.isNotBlank() }
                ?: CallerLookup.lookup(context, m.number).name
            return name ?: m.number.takeIf { it.isNotBlank() }?.let(::formatNumber) ?: "Unknown caller"
        }

        val byCaller = calls.groupBy { numberKey(it.number).ifEmpty { "?" } }
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_call_missed)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setWhen(calls.first().date)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setNumber(calls.size)
            .setContentIntent(openCallLog(context))
            .setDeleteIntent(broadcast(context, CallActionReceiver.ACTION_MISSED_DISMISSED, null))

        if (byCaller.size == 1) {
            val latest = calls.first()
            val name = label(latest)
            builder
                .setContentTitle(if (calls.size > 1) "$name (${calls.size})" else name)
                .setContentText(if (calls.size > 1) "${calls.size} missed calls" else "Missed call")
            if (latest.number.isNotBlank()) {
                builder.addAction(0, "Call back", broadcast(context, CallActionReceiver.ACTION_CALL_BACK, latest.number))
                builder.addAction(
                    0, "Message",
                    PendingIntent.getActivity(
                        context, 2,
                        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(latest.number)}")),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }
        } else {
            val inbox = NotificationCompat.InboxStyle()
            byCaller.values.take(6).forEach { group ->
                inbox.addLine(if (group.size > 1) "${label(group.first())} (${group.size})" else label(group.first()))
            }
            builder
                .setContentTitle("${calls.size} missed calls")
                .setContentText(byCaller.values.take(3).joinToString(", ") { label(it.first()) })
                .setStyle(inbox)
        }
        nm.notify(NOTIFICATION_ID, builder.build())
    }

    /** Marks unseen missed calls as seen in the call log, and clears the notification. */
    fun markSeen(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        if (!context.hasPermission(Manifest.permission.WRITE_CALL_LOG)) return
        runCatching {
            context.contentResolver.update(
                Calls.CONTENT_URI,
                ContentValues().apply {
                    put(Calls.NEW, 0)
                    put(Calls.IS_READ, 1)
                },
                "${Calls.TYPE} = ? AND ${Calls.NEW} = 1",
                arrayOf(Calls.MISSED_TYPE.toString()),
            )
        }
        // Also tells Telecom, which keeps its own unseen count.
        runCatching { context.getSystemService(TelecomManager::class.java)?.cancelMissedCallsNotification() }
    }

    private fun openCallLog(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 3,
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_CALL_LOG, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun broadcast(context: Context, action: String, number: String?): PendingIntent = PendingIntent.getBroadcast(
        context, action.hashCode(),
        Intent(context, CallActionReceiver::class.java).setAction(action).apply {
            if (number != null) putExtra(CallActionReceiver.EXTRA_NUMBER, number)
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/**
 * Android's "show missed calls" message to the default phone app (sent after each missed
 * call, and at boot). Exported because the system sends it; it only re-reads the call log.
 */
class MissedCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION) return
        val pending = goAsync()
        Thread {
            try {
                MissedCalls.refresh(context.applicationContext)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
