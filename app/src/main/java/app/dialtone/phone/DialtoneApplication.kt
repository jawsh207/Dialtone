package app.dialtone.phone

import android.app.Application
import app.dialtone.phone.telecom.CallNotifications
import app.dialtone.phone.transcription.Transcription
import app.dialtone.phone.voicemail.VoicemailNotifier
import app.dialtone.phone.vvm.DialtoneVoicemail

class DialtoneApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // The engine can start from a system bind (visual voicemail SMS) with no UI running.
        DialtoneVoicemail.init(this)
        CallNotifications.ensureChannels(this)
        VoicemailNotifier.ensureChannel(this)
        // Re-arm the transcription watcher (content-trigger jobs don't survive a reboot or
        // force-stop). Off the main thread: it checks for the bundled model.
        Thread { Transcription.schedule(this) }.start()
    }
}
