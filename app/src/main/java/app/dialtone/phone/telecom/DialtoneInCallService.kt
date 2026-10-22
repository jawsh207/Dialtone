package app.dialtone.phone.telecom

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import app.dialtone.phone.incall.InCallActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DialtoneInCallService : InCallService() {

    private val scope = MainScope()

    override fun onCreate() {
        super.onCreate()
        CallManager.service = this
        CallNotifications.ensureChannels(this)
        scope.launch {
            CallManager.version.collect {
                CallNotifications.update(this@DialtoneInCallService, CallManager.calls.value)
            }
        }
    }

    override fun onCallAdded(call: Call) {
        CallManager.onCallAdded(call)
        // Ringing calls surface through the full-screen notification (heads-up when
        // the phone is in use). Outgoing calls open the in-call screen directly.
        if (call.stateCompat != Call.STATE_RINGING) {
            startActivity(InCallActivity.intent(this))
        }
    }

    override fun onCallRemoved(call: Call) {
        CallManager.onCallRemoved(call)
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        CallManager.onAudioStateChanged(audioState)
    }

    override fun onDestroy() {
        scope.cancel()
        CallManager.service = null
        CallNotifications.cancel(this)
        super.onDestroy()
    }
}
