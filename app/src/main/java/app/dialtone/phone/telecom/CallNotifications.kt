package app.dialtone.phone.telecom

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import app.dialtone.phone.R
import app.dialtone.phone.incall.InCallActivity
import app.dialtone.phone.util.formatNumber
import app.dialtone.phone.util.hasPermission

object CallNotifications {

    private const val CHANNEL_INCOMING = "incoming_calls"
    private const val CHANNEL_ONGOING = "ongoing_calls"
    private const val NOTIFICATION_ID = 42

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_INCOMING, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
                // Telecom plays the ringtone and vibration itself.
                setSound(null, null)
                enableVibration(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, "Ongoing calls", NotificationManager.IMPORTANCE_LOW),
        )
    }

    @SuppressLint("MissingPermission")
    fun update(context: Context, calls: List<Call>) {
        if (Build.VERSION.SDK_INT >= 33 && !context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)) return
        val nm = NotificationManagerCompat.from(context)
        val ringing = calls.firstOrNull { it.stateCompat == Call.STATE_RINGING }
        val live = calls.firstOrNull { it.stateCompat != Call.STATE_DISCONNECTED }
        when {
            ringing != null -> nm.notify(NOTIFICATION_ID, incoming(context, ringing))
            live != null -> nm.notify(NOTIFICATION_ID, ongoing(context, live))
            else -> nm.cancel(NOTIFICATION_ID)
        }
    }

    fun cancel(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    private fun callerName(context: Context, call: Call): String {
        val number = call.details.handle?.schemeSpecificPart.orEmpty()
        return CallerLookup.lookup(context, number).name
            ?: number.takeIf { it.isNotBlank() }?.let(::formatNumber)
            ?: "Unknown caller"
    }

    private fun incoming(context: Context, call: Call) = NotificationCompat.Builder(context, CHANNEL_INCOMING)
        .setSmallIcon(R.drawable.ic_stat_call)
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(openInCall(context, answer = false))
        .setFullScreenIntent(openInCall(context, answer = false), true)
        .setStyle(
            NotificationCompat.CallStyle.forIncomingCall(
                Person.Builder().setName(callerName(context, call)).setImportant(true).build(),
                broadcast(context, CallActionReceiver.ACTION_DECLINE),
                // Answer goes through an activity: receivers can't start activities
                // from notification actions on Android 12+.
                openInCall(context, answer = true),
            ),
        )
        .build()

    private fun ongoing(context: Context, call: Call): android.app.Notification {
        val connectTime = call.details.connectTimeMillis
        val status = when (call.stateCompat) {
            Call.STATE_ACTIVE -> "Ongoing call"
            Call.STATE_HOLDING -> "On hold"
            else -> "Calling…"
        }
        return NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentTitle(callerName(context, call))
            .setContentText(status)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(connectTime > 0)
            .setWhen(if (connectTime > 0) connectTime else System.currentTimeMillis())
            .setUsesChronometer(connectTime > 0 && call.stateCompat == Call.STATE_ACTIVE)
            .setContentIntent(openInCall(context, answer = false))
            .addAction(0, "Hang up", broadcast(context, CallActionReceiver.ACTION_HANGUP))
            .build()
    }

    private fun openInCall(context: Context, answer: Boolean): PendingIntent =
        PendingIntent.getActivity(
            context,
            if (answer) 1 else 0,
            InCallActivity.intent(context).putExtra(InCallActivity.EXTRA_ANSWER, answer),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun broadcast(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, CallActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
