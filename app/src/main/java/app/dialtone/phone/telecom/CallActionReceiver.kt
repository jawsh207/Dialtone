package app.dialtone.phone.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles the buttons on call notifications that don't need to open the app: Decline on an
 * incoming call, and Hang up on an ongoing one. (Answer opens the in-call screen instead,
 * because Android 12+ doesn't let a notification button start an activity from here.)
 */
class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DECLINE -> CallManager.ringingCall()?.let(CallManager::reject)
            ACTION_HANGUP -> CallManager.primaryCall()?.let(CallManager::hangup)
        }
    }

    companion object {
        const val ACTION_DECLINE = "app.dialtone.phone.action.DECLINE"
        const val ACTION_HANGUP = "app.dialtone.phone.action.HANGUP"
    }
}
