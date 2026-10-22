package app.dialtone.phone.transcription

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.provider.VoicemailContract.Voicemails

/**
 * On-device voicemail transcription: settings and scheduling.
 *
 * When on, a JobScheduler job watches the voicemail store and transcribes messages whose
 * audio has arrived but have no transcript. Carrier transcripts are never replaced.
 */
object Transcription {
    private const val PREFS = "transcription"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_DONE = "transcribed_ids"   // written by Dialtone (for the label)
    private const val KEY_TRIED = "attempted_ids"    // tried, success or not (no retry loops)

    // Distinct from the voicemail engine's job ids (50–52).
    private const val JOB_RUN_NOW = 60
    private const val JOB_WATCH = 61

    /** The engine and model are in this build (64-bit ARM). */
    fun isAvailable(context: Context): Boolean =
        MoonshineEngine.isSupported && ModelStore.isBundled(context)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false) && isAvailable(context)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) {
            schedule(context, runNow = true)
        } else {
            val js = context.getSystemService(JobScheduler::class.java)
            js?.cancel(JOB_RUN_NOW)
            js?.cancel(JOB_WATCH)
            Thread { ModelStore.delete(context.applicationContext) }.start()
        }
    }

    /** (Re)arms the watcher; call at app start and boot, since content-trigger jobs don't persist. */
    fun schedule(context: Context, runNow: Boolean = false) {
        if (!isEnabled(context)) return
        val js = context.getSystemService(JobScheduler::class.java) ?: return
        val component = ComponentName(context, TranscriptionJobService::class.java)
        js.schedule(
            JobInfo.Builder(JOB_WATCH, component)
                .addTriggerContentUri(
                    JobInfo.TriggerContentUri(
                        Voicemails.CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS,
                    ),
                )
                .setTriggerContentUpdateDelay(3_000)
                .setTriggerContentMaxDelay(30_000)
                .build(),
        )
        if (runNow) {
            js.schedule(
                JobInfo.Builder(JOB_RUN_NOW, component)
                    .setOverrideDeadline(1_000)
                    .build(),
            )
        }
    }

    fun transcribedIds(context: Context): Set<Long> = ids(context, KEY_DONE)
    fun attemptedIds(context: Context): Set<Long> = ids(context, KEY_TRIED)

    internal fun markAttempted(context: Context, id: Long, transcribed: Boolean) {
        val p = prefs(context)
        val tried = (p.getStringSet(KEY_TRIED, emptySet()) ?: emptySet()) + id.toString()
        val editor = p.edit().putStringSet(KEY_TRIED, tried.toList().takeLast(500).toSet())
        if (transcribed) {
            val done = (p.getStringSet(KEY_DONE, emptySet()) ?: emptySet()) + id.toString()
            editor.putStringSet(KEY_DONE, done.toList().takeLast(500).toSet())
        }
        editor.apply()
    }

    private fun ids(context: Context, key: String): Set<Long> =
        (prefs(context).getStringSet(key, emptySet()) ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()
}
