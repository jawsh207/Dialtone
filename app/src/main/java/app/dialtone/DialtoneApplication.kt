package app.dialtone

import android.app.Application
import app.dialtone.telecom.CallNotifications
import app.dialtone.voicemail.VoicemailNotifier
import app.dialtone.vvm.DialtoneVoicemail

class DialtoneApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // The engine can start from a system bind (visual voicemail SMS) with no UI running.
        DialtoneVoicemail.init(this)
        CallNotifications.ensureChannels(this)
        VoicemailNotifier.ensureChannel(this)
    }
}
