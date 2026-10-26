package app.dialtone.phone.transcription

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Voicemail transcription through the phone's speech recognizer: whichever app the user
 * picked as the system's voice input (e.g. Distillery). Dialtone bundles no speech engine.
 *
 * The audio goes in with [RecognizerIntent.EXTRA_AUDIO_SOURCE] (Android 13+) as the format
 * the API documents: raw 16-bit mono PCM at 16 kHz, so any recognizer that accepts
 * recordings can handle it. Recognizers that only listen to the microphone return an error,
 * and the message is simply left without a transcript.
 */
object SystemRecognizer {
    private const val TAG = "SystemRecognizer"

    /** Distillery, Dialtone's companion speech app; known to accept recordings. */
    const val DISTILLERY_PACKAGE = "app.distillery.stt"

    sealed interface Outcome {
        data class Text(val text: String) : Outcome
        /** The recognizer heard no speech: done, no transcript. */
        data object NoSpeech : Outcome
        /** Can't be done with this recognizer/audio: done, no transcript, don't retry. */
        data class Failed(val error: Int) : Outcome
        /** Temporary (busy, model not downloaded yet, blocked in background): try again later. */
        data class RetryLater(val error: Int) : Outcome
    }

    data class Info(val packageName: String?, val label: String?) {
        val isDistillery: Boolean get() = packageName == DISTILLERY_PACKAGE
    }

    fun isSupported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isRecognitionAvailable(context)

    /** The recognizer Android will use: the user's voice input choice, else the only one installed. */
    fun current(context: Context): Info? {
        val pm = context.packageManager
        val chosen = runCatching {
            // Not a public constant, but it's what the system's voice input setting writes.
            Settings.Secure.getString(context.contentResolver, "voice_recognition_service")
        }.getOrNull()?.let(ComponentName::unflattenFromString)
        val pkg = chosen?.packageName ?: run {
            val services = pm.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
            when (services.size) {
                0 -> return null
                1 -> services[0].serviceInfo.packageName
                else -> return Info(null, null) // several installed, choice unknown
            }
        }
        @Suppress("DEPRECATION")
        val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
        return Info(pkg, label)
    }

    /** Opens the system screen where the user picks voice input (falls back to app settings). */
    fun settingsIntent(): Intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)

    /**
     * Transcribes [pcm] (16 kHz mono float) with the system recognizer. Blocks the calling
     * (background) thread; the recognizer itself runs on the main thread as the API requires.
     */
    fun transcribe(context: Context, pcm: FloatArray): Outcome {
        if (Build.VERSION.SDK_INT < 33) return Outcome.Failed(SpeechRecognizer.ERROR_CLIENT)
        val file = File(context.noBackupFilesDir, "transcribe-${System.nanoTime()}.pcm")
        try {
            writePcm16(pcm, file)
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            return pfd.use { run(context, it, audioSeconds = pcm.size / AudioDecoder.TARGET_RATE) }
        } finally {
            file.delete()
        }
    }

    private fun run(context: Context, audio: ParcelFileDescriptor, audioSeconds: Int): Outcome {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, audio)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, AudioDecoder.TARGET_RATE)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
        }
        val done = CountDownLatch(1)
        var outcome: Outcome = Outcome.RetryLater(SpeechRecognizer.ERROR_CLIENT)
        val main = Handler(Looper.getMainLooper())
        var recognizer: SpeechRecognizer? = null

        main.post {
            val r = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
            if (r == null) {
                outcome = Outcome.Failed(SpeechRecognizer.ERROR_CLIENT)
                done.countDown()
                return@post
            }
            recognizer = r
            r.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                    outcome = if (text.isNullOrEmpty()) Outcome.NoSpeech else Outcome.Text(text)
                    done.countDown()
                }

                override fun onError(error: Int) {
                    outcome = classify(error)
                    done.countDown()
                }

                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            runCatching { r.startListening(intent) }.onFailure {
                Log.w(TAG, "startListening failed: $it")
                outcome = Outcome.Failed(SpeechRecognizer.ERROR_CLIENT)
                done.countDown()
            }
        }

        // Generous: model load plus a few times real time on a slow phone.
        val timeoutSec = (60 + audioSeconds * 4L).coerceAtMost(15 * 60L)
        if (!done.await(timeoutSec, TimeUnit.SECONDS)) {
            Log.w(TAG, "recognizer timed out after ${timeoutSec}s")
            outcome = Outcome.RetryLater(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
        }
        val cleanup = CountDownLatch(1)
        main.post {
            runCatching { recognizer?.cancel() }
            runCatching { recognizer?.destroy() }
            cleanup.countDown()
        }
        cleanup.await(5, TimeUnit.SECONDS)
        return outcome
    }

    private fun classify(error: Int): Outcome = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Outcome.NoSpeech
        // Temporary: model still downloading, recognizer busy or restarting, rate-limited, or
        // the framework refused while Dialtone was in the background (retried when it's open).
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS,
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> Outcome.RetryLater(error)
        else -> Outcome.Failed(error)
    }

    private fun writePcm16(pcm: FloatArray, file: File) {
        val buffer = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (v in pcm) {
            val s = if (v.isNaN()) 0f else v.coerceIn(-1f, 1f)
            buffer.putShort((s * 32767f).roundToInt().toShort())
        }
        file.outputStream().use { it.write(buffer.array()) }
    }
}
