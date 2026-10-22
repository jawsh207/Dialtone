package app.dialtone.phone.data

import android.content.ContentValues
import android.content.Context
import android.provider.VoicemailContract.Voicemails
import android.util.Log

/**
 * One-time repair for Dialtone 0.2.0–0.2.1, which asked the voicemail provider to delete other
 * apps' copies of messages (duplicate cleanup, and turning visual voicemail off). The provider
 * never deletes another app's rows: it only flags them deleted, so their audio is still on the
 * phone. This clears that flag on other apps' copies that still have audio, so they show again.
 *
 * Dialtone's own copies aren't touched (anything of ours still flagged is a real pending
 * delete), and nothing is sent to the carrier: only the owning app syncs a row to the server.
 */
object VoicemailRestore {
    private const val PREFS = "voicemail_repair"
    private const val KEY_DONE = "restored_other_app_copies_v1"

    fun runOnce(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DONE, false)) return
        val restored = runCatching {
            context.contentResolver.update(
                Voicemails.CONTENT_URI,
                ContentValues().apply { put(Voicemails.DELETED, 0) },
                "${Voicemails.DELETED} = 1 AND ${Voicemails.HAS_CONTENT} = 1 AND " +
                    "${Voicemails.SOURCE_PACKAGE} != ?",
                arrayOf(context.packageName),
            )
        }.onFailure {
            // Not the default phone app yet (or no provider): try again next launch.
            Log.w("VoicemailRestore", "restore failed: $it")
        }.getOrNull() ?: return
        Log.i("VoicemailRestore", "restored $restored voicemails")
        prefs.edit().putBoolean(KEY_DONE, true).apply()
    }
}
