package app.dialtone.phone.transcription

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.VoicemailContract.Voicemails
import android.util.Log

/** Finds voicemails that have audio but no transcript, and transcribes them on the phone. */
class VoicemailTranscriber(private val context: Context) {

    /** Processes pending messages, newest first; [shouldStop] lets the job yield early. */
    fun processPending(shouldStop: () -> Boolean) {
        if (!Transcription.isEnabled(context)) return
        val pending = pendingIds()
        if (pending.isEmpty()) return

        val model = ModelStore.ensureExtracted(context) ?: run {
            Log.w(TAG, "model not available")
            return
        }
        val engine = MoonshineEngine.open(model.path) ?: return
        engine.use {
            for (id in pending) {
                if (shouldStop() || !Transcription.isEnabled(context)) return
                transcribeOne(it, id)
            }
        }
    }

    private fun pendingIds(): List<Long> {
        val tried = Transcription.attemptedIds(context)
        val ids = mutableListOf<Long>()
        runCatching {
            context.contentResolver.query(
                Voicemails.CONTENT_URI,
                arrayOf(Voicemails._ID),
                "${Voicemails.HAS_CONTENT} = 1 AND ${Voicemails.DELETED} = 0 AND " +
                    "(${Voicemails.TRANSCRIPTION} IS NULL OR ${Voicemails.TRANSCRIPTION} = '')",
                null,
                "${Voicemails.DATE} DESC",
            )?.use { c -> while (c.moveToNext()) ids += c.getLong(0) }
        }.onFailure { Log.w(TAG, "query failed: $it") }
        return ids.filterNot { it in tried }.take(MAX_PER_RUN)
    }

    private fun transcribeOne(engine: MoonshineEngine, id: Long) {
        val uri = ContentUris.withAppendedId(Voicemails.CONTENT_URI, id)
        val started = System.currentTimeMillis()
        val pcm = AudioDecoder.decodeTo16k(context, uri)?.let(AudioDecoder::normalize)
        if (pcm == null) {
            Transcription.markAttempted(context, id, transcribed = false)
            return
        }
        val text = Chunker.split(pcm, engine.maxAudioMs)
            .mapNotNull { range -> engine.transcribe(pcm.copyOfRange(range.first, range.last + 1)) }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()

        var written = false
        if (text.isNotEmpty()) {
            written = runCatching {
                context.contentResolver.update(
                    uri,
                    ContentValues().apply { put(Voicemails.TRANSCRIPTION, text) },
                    // Never overwrite a transcript that arrived from the carrier meanwhile.
                    "(${Voicemails.TRANSCRIPTION} IS NULL OR ${Voicemails.TRANSCRIPTION} = '')",
                    null,
                ) > 0
            }.onFailure { Log.w(TAG, "write failed: $it") }.getOrDefault(false)
        }
        Transcription.markAttempted(context, id, transcribed = written)
        Log.i(TAG, "voicemail $id: ${pcm.size / AudioDecoder.TARGET_RATE}s audio, " +
            "${text.length} chars in ${System.currentTimeMillis() - started} ms")
    }

    companion object {
        private const val TAG = "VoicemailTranscriber"
        private const val MAX_PER_RUN = 25
    }
}
