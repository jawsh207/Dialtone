package app.dialtone.phone.ui.voicemail

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.dialtone.phone.data.Voicemail

/** One MediaPlayer shared by the inbox; state is Compose-observable. */
class VoicemailPlayer(private val context: Context) {

    var currentId by mutableStateOf<Long?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableIntStateOf(0)
        private set
    var durationMs by mutableIntStateOf(0)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var player: MediaPlayer? = null

    fun toggle(vm: Voicemail) {
        val p = player
        if (currentId == vm.id && p != null) {
            if (isPlaying) p.pause() else p.start()
            isPlaying = !isPlaying
            return
        }
        release()
        currentId = vm.id
        error = null
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                setDataSource(context, vm.uri)
                setOnPreparedListener {
                    this@VoicemailPlayer.durationMs = it.duration
                    it.start()
                    this@VoicemailPlayer.isPlaying = true
                }
                setOnCompletionListener {
                    this@VoicemailPlayer.isPlaying = false
                    this@VoicemailPlayer.positionMs = this@VoicemailPlayer.durationMs
                }
                setOnErrorListener { _, _, _ ->
                    this@VoicemailPlayer.isPlaying = false
                    this@VoicemailPlayer.error = "Couldn't play this voicemail"
                    true
                }
                prepareAsync()
            }
        }.onFailure { error = "Couldn't open this voicemail" }.getOrNull()
    }

    fun seek(vm: Voicemail, fraction: Float) {
        val p = player ?: return
        if (currentId != vm.id || durationMs <= 0) return
        val target = (fraction.coerceIn(0f, 1f) * durationMs).toInt()
        p.seekTo(target)
        positionMs = target
    }

    fun tick() {
        val p = player ?: return
        if (isPlaying) positionMs = p.currentPosition
    }

    fun release() {
        player?.release()
        player = null
        isPlaying = false
        positionMs = 0
        durationMs = 0
        currentId = null
    }
}
