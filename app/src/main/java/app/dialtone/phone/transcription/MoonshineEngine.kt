package app.dialtone.phone.transcription

import android.util.Log
import java.io.Closeable

/**
 * Moonshine speech-to-text via transcribe.cpp (native, CPU only). Not thread-safe: use one
 * instance from one thread, and [close] it when done.
 */
class MoonshineEngine private constructor(private var handle: Long) : Closeable {

    /** Longest audio one [transcribe] call accepts, in ms; 0 = no practical limit. */
    val maxAudioMs: Long = nativeMaxAudioMs(handle)

    /** [pcm16k]: mono float samples at 16 kHz in [-1, 1]. Returns null if the engine failed. */
    fun transcribe(pcm16k: FloatArray): String? {
        check(handle != 0L) { "engine closed" }
        if (pcm16k.isEmpty()) return ""
        return nativeTranscribe(handle, pcm16k)?.trim()
            ?: null.also { Log.w(TAG, "transcribe failed: ${nativeLastError()}") }
    }

    override fun close() {
        if (handle != 0L) {
            nativeClose(handle)
            handle = 0L
        }
    }

    companion object {
        private const val TAG = "MoonshineEngine"

        /** True when the native library is present and loads (64-bit ARM builds). */
        val isSupported: Boolean by lazy {
            try {
                System.loadLibrary("dialtone_transcriber")
                true
            } catch (e: UnsatisfiedLinkError) {
                Log.i(TAG, "on-device transcription unavailable: ${e.message}")
                false
            }
        }

        /** Loads the model at [modelPath]; null on failure. */
        fun open(modelPath: String): MoonshineEngine? {
            if (!isSupported) return null
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
            val handle = nativeOpen(modelPath, threads)
            if (handle == 0L) {
                Log.w(TAG, "model load failed: ${nativeLastError()}")
                return null
            }
            return MoonshineEngine(handle)
        }

        @JvmStatic private external fun nativeOpen(modelPath: String, nThreads: Int): Long
        @JvmStatic private external fun nativeMaxAudioMs(handle: Long): Long
        @JvmStatic private external fun nativeTranscribe(handle: Long, pcm: FloatArray): String?
        @JvmStatic private external fun nativeClose(handle: Long)
        @JvmStatic private external fun nativeLastError(): String
    }
}
