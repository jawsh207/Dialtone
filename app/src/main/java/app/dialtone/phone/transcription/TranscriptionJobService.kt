package app.dialtone.phone.transcription

import android.app.job.JobParameters
import android.app.job.JobService
import kotlin.concurrent.thread

/** Runs [VoicemailTranscriber] off the main thread whenever the voicemail store changes. */
class TranscriptionJobService : JobService() {

    @Volatile private var stopped = false

    override fun onStartJob(params: JobParameters): Boolean {
        stopped = false
        thread(name = "voicemail-transcriber", priority = Thread.MIN_PRIORITY) {
            try {
                VoicemailTranscriber(applicationContext).processPending { stopped }
            } finally {
                jobFinished(params, false)
                // Content-trigger jobs fire once; re-arm for the next new voicemail.
                Transcription.schedule(applicationContext)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        stopped = true
        return true // retry later
    }
}
