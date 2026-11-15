package app.dialtone.phone.ui.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * First-run screen, shown until Dialtone is the default phone app. Android only lets the
 * default phone app place and answer calls through its own screens, read the call log and
 * voicemail, and receive visual voicemail messages, so nothing else works until then.
 * "Set as default" asks Android for the phone app role ([onRequest]); a spinning cookie shape
 * behind the phone icon gives it some Material 3 Expressive motion.
 */
@Composable
fun SetDefaultScreen(onRequest: () -> Unit) {
    val shape = MaterialShapes.Cookie12Sided.toShape()
    val angle by rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(20_000, easing = LinearEasing)), label = "angle",
    )
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(176.dp)
                        .graphicsLayer { rotationZ = angle }
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                )
                Icon(
                    Icons.Rounded.Call, null,
                    Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(40.dp))
            Text(
                "Make Dialtone your phone app",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Dialtone needs to be your default phone app to place and answer calls, " +
                    "show your call history and play your voicemail.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            Button(onClick = onRequest, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Text("Set as default", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
