package app.dialtone.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DECLINE -> CallManager.ringingCall()?.let(CallManager::reject)
            ACTION_HANGUP -> CallManager.primaryCall()?.let(CallManager::hangup)
        }
    }

    companion object {
        const val ACTION_DECLINE = "app.dialtone.action.DECLINE"
        const val ACTION_HANGUP = "app.dialtone.action.HANGUP"
    }
}
