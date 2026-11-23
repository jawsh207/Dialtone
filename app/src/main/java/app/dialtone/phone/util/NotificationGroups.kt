package app.dialtone.phone.util

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Keeps each kind of notification in its own group: missed calls with missed calls, voicemail
 * with voicemail.
 *
 * Android bundles an app's notifications itself when the app doesn't group them (always on
 * Android 16, which forces it), and that bundle shows the app's icon, a plain handset, so a
 * missed call and a voicemail ended up together under it. Naming a group and posting a
 * summary for it, as Android recommends, prevents that: each kind stays separate, and its
 * summary carries that kind's own icon. While a group has a single notification, Android shows
 * just that notification; the summary only appears as a header if more are added.
 */
object NotificationGroups {
    const val MISSED_CALLS = "app.dialtone.phone.group.MISSED_CALLS"
    const val VOICEMAIL = "app.dialtone.phone.group.VOICEMAIL"

    /**
     * Posts (or updates) the summary for [group]. It never makes a sound itself
     * ([NotificationCompat.GROUP_ALERT_CHILDREN]): the notification inside it does.
     */
    @SuppressLint("MissingPermission")
    fun postSummary(
        context: Context,
        channel: String,
        group: String,
        summaryId: Int,
        @DrawableRes icon: Int,
        title: String,
        contentIntent: PendingIntent?,
    ) {
        val summary = NotificationCompat.Builder(context, channel)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setGroup(group)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        NotificationManagerCompat.from(context).notify(summaryId, summary)
    }

    /** Removes [group]'s summary once none of its notifications are showing. */
    fun cancelSummaryIfEmpty(context: Context, group: String, summaryId: Int) {
        val active = runCatching {
            context.getSystemService(NotificationManager::class.java)?.activeNotifications.orEmpty()
        }.getOrDefault(emptyArray())
        val children = active.count { it.notification.group == group && it.id != summaryId }
        if (children == 0) NotificationManagerCompat.from(context).cancel(summaryId)
    }
}
