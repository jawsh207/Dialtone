package app.dialtone.phone.screening

import android.telecom.Call
import android.telecom.CallScreeningService
import app.dialtone.phone.vvm.VvmLog

/**
 * Telecom asks the default phone app's screening service about every incoming call before it
 * rings. Calls from numbers on the "Send to voicemail" list are rejected as if Decline were
 * tapped, so the carrier forwards them to voicemail; everything else is let through untouched.
 * The call still appears in the call log, without a missed-call notification.
 */
class SendToVoicemailScreeningService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        val number = details.handle?.schemeSpecificPart
        val incoming = details.callDirection == Call.Details.DIRECTION_INCOMING
        val send = incoming && !number.isNullOrBlank() &&
            runCatching { SendToVoicemail.contains(this, number) }.getOrDefault(false)

        val response = if (send) {
            VvmLog.i("SendToVoicemail", "sent an incoming call to voicemail")
            CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipNotification(true)
                .setSkipCallLog(false)
                .build()
        } else {
            CallResponse.Builder().build()
        }
        respondToCall(details, response)
    }
}
