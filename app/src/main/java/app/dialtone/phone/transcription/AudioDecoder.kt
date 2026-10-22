package app.dialtone.phone.transcription

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Decodes a voicemail (usually AMR-NB at 8 kHz) to mono float PCM at 16 kHz for the model. */
object AudioDecoder {
    const val TARGET_RATE = 16_000
    private const val MAX_SECONDS = 10 * 60 // voicemails are short; cap runaway input
    private const val TIMEOUT_US = 10_000L

    fun decodeTo16k(context: Context, uri: Uri): FloatArray? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val inputFormat = extractor.getTrackFormat(track)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return null

            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(inputFormat, null, null, 0)
                codec.start()
                val (mono, rate) = drain(extractor, codec, inputFormat) ?: return null
                return resample(mono, rate, TARGET_RATE)
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
        } catch (e: Exception) {
            android.util.Log.w("AudioDecoder", "decode failed: $e")
            return null
        } finally {
            extractor.release()
        }
    }

    private fun drain(extractor: MediaExtractor, codec: MediaCodec, inputFormat: MediaFormat): Pair<FloatArray, Int>? {
        var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var floatPcm = false
        val out = FloatBuilder()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val buffer = codec.getInputBuffer(inIndex)!!
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            when (val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val f = codec.outputFormat
                    sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                }
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                else -> if (outIndex >= 0) {
                    val buffer = codec.getOutputBuffer(outIndex)!!.order(ByteOrder.LITTLE_ENDIAN)
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    val ch = max(channels, 1)
                    if (floatPcm) {
                        val fb = buffer.asFloatBuffer()
                        while (fb.remaining() >= ch) {
                            var acc = 0f
                            repeat(ch) { acc += fb.get() }
                            out.add(acc / ch)
                        }
                    } else {
                        val sb = buffer.asShortBuffer()
                        while (sb.remaining() >= ch) {
                            var acc = 0f
                            repeat(ch) { acc += sb.get() / 32768f }
                            out.add(acc / ch)
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    if (out.size > MAX_SECONDS.toLong() * sampleRate) outputDone = true
                }
            }
        }
        if (out.size == 0) return null
        return out.toArray() to sampleRate
    }

    /** Linear-interpolation resampler, with a box low-pass when downsampling. */
    fun resample(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (fromRate == toRate || input.isEmpty()) return input
        var src = input
        if (fromRate > toRate) {
            val width = (fromRate.toFloat() / toRate).roundToInt().coerceAtLeast(1)
            if (width > 1) {
                src = FloatArray(input.size)
                var acc = 0f
                for (i in input.indices) {
                    acc += input[i]
                    if (i >= width) acc -= input[i - width]
                    src[i] = acc / min(i + 1, width)
                }
            }
        }
        val ratio = fromRate.toDouble() / toRate
        val outLen = floor(src.size / ratio).toInt()
        val out = FloatArray(outLen)
        for (i in 0 until outLen) {
            val pos = i * ratio
            val i0 = pos.toInt()
            val i1 = min(i0 + 1, src.size - 1)
            val frac = (pos - i0).toFloat()
            out[i] = src[i0] * (1 - frac) + src[i1] * frac
        }
        return out
    }

    /** Peak-normalizes quiet phone audio to ~0.9 so the model sees a healthy level. */
    fun normalize(pcm: FloatArray): FloatArray {
        var peak = 0f
        for (s in pcm) peak = max(peak, abs(s))
        if (peak < 1e-4f || peak > 0.9f) return pcm
        val gain = min(0.9f / peak, 8f)
        return FloatArray(pcm.size) { pcm[it] * gain }
    }

    private class FloatBuilder {
        private var data = FloatArray(16_000 * 30)
        var size = 0
            private set

        fun add(v: Float) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = v
        }

        fun toArray(): FloatArray = data.copyOf(size)
    }
}
