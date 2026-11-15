package app.dialtone.phone.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles the buttons on Dialtone's notifications that don't need to open the app: Decline on
 * an incoming call, Hang up on an ongoing one, and Call back / dismiss on a missed call.
 * (Answer opens the in-call screen instead, because Android 12+ doesn't let a notification
 * button start an activity from here.) Not exported: only Dialtone's own notifications can
 * reach it, so no other app can make Dialtone place a call.
 */
class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DECLINE -> CallManager.ringingCall()?.let(CallManager::reject)
            ACTION_HANGUP -> CallManager.primaryCall()?.let(CallManager::hangup)
            ACTION_CALL_BACK -> {
                intent.getStringExtra(EXTRA_NUMBER)?.takeIf { it.isNotBlank() }?.let { Dialer.call(context, it) }
                seen(context)
            }
            ACTION_MISSED_DISMISSED -> seen(context)
        }
    }

    companion object {
        const val ACTION_DECLINE = "app.dialtone.phone.action.DECLINE"
        const val ACTION_HANGUP = "app.dialtone.phone.action.HANGUP"
        const val ACTION_CALL_BACK = "app.dialtone.phone.action.CALL_BACK"
        const val ACTION_MISSED_DISMISSED = "app.dialtone.phone.action.MISSED_DISMISSED"
        const val EXTRA_NUMBER = "app.dialtone.phone.extra.NUMBER"

        private fun seen(context: Context) {
            val app = context.applicationContext
            Thread { MissedCalls.markSeen(app) }.start()
        }
    }
}
