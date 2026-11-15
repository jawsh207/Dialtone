package app.dialtone.phone

import app.dialtone.phone.data.VoicemailRestore
import android.app.Application
import app.dialtone.phone.vvm.compat.PersistentLogger
import app.dialtone.phone.telecom.CallNotifications
import app.dialtone.phone.transcription.Transcription
import app.dialtone.phone.voicemail.VoicemailNotifier
import app.dialtone.phone.vvm.DialtoneVoicemail

/**
 * Runs once whenever Android starts Dialtone's process, before any screen, service or
 * receiver. Dialtone is often started with no screen at all (an incoming call, a visual
 * voicemail text from the carrier, a scheduled job), so everything those need is set up here:
 * the visual voicemail engine, the call and voicemail notification channels, and the
 * transcription job, plus a one-time repair of voicemail copies from older versions.
 */
class DialtoneApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Transcription runs in its own process so a stuck or crashing recognizer call can
        // never take down voicemail downloads or notifications. That process only needs the log.
        if (getProcessName().endsWith(":transcriber")) {
            PersistentLogger.init(this)
            return
        }
        // The engine can start from a system bind (visual voicemail SMS) with no UI running.
        DialtoneVoicemail.init(this)
        CallNotifications.ensureChannels(this)
        VoicemailNotifier.ensureChannel(this)
        // Re-arm the transcription watcher (content-trigger jobs don't survive a reboot or
        // force-stop). Off the main thread: it asks the package manager about recognizers.
        Thread {
            VoicemailRestore.runOnce(this)
            Transcription.schedule(this)
        }.start()
    }
}
