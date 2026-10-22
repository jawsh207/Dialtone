package app.dialtone.phone.voicemail

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.PhoneAccountHandle
import android.telephony.TelephonyManager
import app.dialtone.phone.transcription.Transcription
import app.dialtone.phone.vvm.DialtoneVoicemail
import app.dialtone.phone.vvm.VoicemailClient

/** The voicemail store announces each new message (from our engine or a carrier app). */
class NewVoicemailReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                VoicemailNotifier.onNewVoicemail(context)
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/**
 * Message-waiting indicator. Telephony sends [TelephonyManager.ACTION_SHOW_VOICEMAIL_NOTIFICATION]
 * to the default phone app; the engine sends [VoicemailClient.ACTION_SHOW_LEGACY_VOICEMAIL] when
 * visual voicemail can't be activated.
 */
class MessageWaitingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val handle = intent.parcelable<PhoneAccountHandle>(TelephonyManager.EXTRA_PHONE_ACCOUNT_HANDLE)
        val legacy = intent.getBooleanExtra(VoicemailClient.EXTRA_IS_LEGACY_MODE, false)
        // With working visual voicemail, new messages are announced by NewVoicemailReceiver.
        if (!legacy && DialtoneVoicemail.isActivated(context, handle)) {
            VoicemailNotifier.showMessageWaiting(context, 0, null)
            return
        }
        val count = intent.getIntExtra(TelephonyManager.EXTRA_NOTIFICATION_COUNT, -1)
        val callIntent = intent.parcelable<PendingIntent>(TelephonyManager.EXTRA_CALL_VOICEMAIL_INTENT)
        // Some carriers report -1 for "unknown count but messages waiting".
        VoicemailNotifier.showMessageWaiting(context, if (count < 0) 1 else count, callIntent)
    }
}

/** Mirrors Dialer's VoicemailPowerCycleReceiver. */
class VoicemailPowerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                DialtoneVoicemail.onBoot(context)
                Transcription.schedule(context, runNow = true)
            }
            Intent.ACTION_SHUTDOWN -> DialtoneVoicemail.onShutdown(context)
        }
    }
}

@Suppress("DEPRECATION")
private inline fun <reified T : android.os.Parcelable> Intent.parcelable(key: String): T? =
    if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(key, T::class.java)
    } else {
        getParcelableExtra<android.os.Parcelable>(key) as? T
    }
