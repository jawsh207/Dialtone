package app.dialtone.phone.ui.components

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Voicemail
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val dialKeys = listOf(
    '1' to "", '2' to "ABC", '3' to "DEF",
    '4' to "GHI", '5' to "JKL", '6' to "MNO",
    '7' to "PQRS", '8' to "TUV", '9' to "WXYZ",
    '*' to "", '0' to "+", '#' to "",
)

/** Letter → digit, for T9 contact matching. */
val T9: Map<Char, Char> = buildMap {
    dialKeys.forEach { (digit, letters) -> letters.lowercase().forEach { put(it, digit) } }
}

/**
 * 4×3 dial pad with pill keys that morph to rounded squares while pressed.
 * [onLongKey] fires for long-press on 0 (+) and 1 (voicemail).
 */
@Composable
fun DialPad(
    onKey: (Char) -> Unit,
    modifier: Modifier = Modifier,
    keyHeight: Dp = 68.dp,
    onLongKey: ((Char) -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        dialKeys.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (digit, letters) ->
                    DialKey(
                        digit = digit,
                        letters = letters,
                        onClick = { onKey(digit) },
                        onLongClick = if (onLongKey != null && (digit == '0' || digit == '1')) {
                            { onLongKey(digit) }
                        } else null,
                        modifier = Modifier.weight(1f).height(keyHeight),
                    )
                }
            }
        }
    }
}

@Composable
private fun DialKey(
    digit: Char,
    letters: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateIntAsState(
        if (pressed) 24 else 50, MaterialTheme.motionScheme.fastSpatialSpec(), label = "key",
    )
    val color by animateColorAsState(
        if (pressed) scheme.primaryContainer else scheme.surfaceContainerHigh,
        MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "keyColor",
    )
    Box(
        modifier
            .clip(RoundedCornerShape(percent = corner))
            .background(color)
            .combinedClickable(
                interactionSource = interaction,
                indication = ripple(),
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                digit.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = if (pressed) scheme.onPrimaryContainer else scheme.onSurface,
            )
            when {
                digit == '1' -> Icon(
                    Icons.Rounded.Voicemail, null,
                    Modifier.size(14.dp), tint = scheme.onSurfaceVariant,
                )
                letters.isNotEmpty() -> Text(
                    letters,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Keypad feedback tones; respects the system "dial pad tones" setting. */
class DtmfTones(context: Context) {
    private val generator: ToneGenerator? =
        if (Settings.System.getInt(context.contentResolver, Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1) {
            runCatching { ToneGenerator(AudioManager.STREAM_DTMF, 70) }.getOrNull()
        } else null

    fun play(c: Char) {
        val tone = when (c) {
            in '0'..'9' -> ToneGenerator.TONE_DTMF_0 + (c - '0')
            '*' -> ToneGenerator.TONE_DTMF_S
            '#' -> ToneGenerator.TONE_DTMF_P
            else -> return
        }
        generator?.startTone(tone, 120)
    }

    fun release() {
        generator?.release()
    }
}

@Composable
fun rememberDtmfTones(): DtmfTones {
    val context = LocalContext.current
    val tones = remember { DtmfTones(context) }
    DisposableEffect(Unit) { onDispose { tones.release() } }
    return tones
}
