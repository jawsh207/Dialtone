package app.dialtone.phone.transcription

import kotlin.math.min

/**
 * Splits long audio into model-sized pieces at the quietest moment near each boundary, so
 * cuts land between words. Moonshine works best on utterances under ~20–30 seconds.
 */
object Chunker {
    private const val RATE = AudioDecoder.TARGET_RATE
    private const val FRAME = RATE / 50 // 20 ms energy frames

    /** Returns [start, end) sample ranges covering [pcm]. */
    fun split(pcm: FloatArray, maxAudioMs: Long, targetSeconds: Int = 20): List<IntRange> {
        val limit = if (maxAudioMs > 0) min(maxAudioMs / 1000 - 1, targetSeconds.toLong()).toInt() else targetSeconds
        val maxLen = limit.coerceAtLeast(5) * RATE
        if (pcm.size <= maxLen) return listOf(0 until pcm.size)

        val chunks = mutableListOf<IntRange>()
        var start = 0
        while (pcm.size - start > maxLen) {
            // Look for the quietest 20 ms frame in the last 40% of the window.
            val searchFrom = start + (maxLen * 0.6).toInt()
            val searchTo = start + maxLen
            var bestAt = searchTo
            var bestEnergy = Float.MAX_VALUE
            var i = searchFrom
            while (i + FRAME <= searchTo) {
                var e = 0f
                for (j in i until i + FRAME) e += pcm[j] * pcm[j]
                if (e < bestEnergy) {
                    bestEnergy = e
                    bestAt = i + FRAME / 2
                }
                i += FRAME
            }
            chunks += start until bestAt
            start = bestAt
        }
        if (start < pcm.size) chunks += start until pcm.size
        return chunks
    }
}
