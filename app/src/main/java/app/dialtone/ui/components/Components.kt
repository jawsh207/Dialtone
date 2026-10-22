package app.dialtone.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dialtone.util.initialOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val expressiveShapes by lazy {
    listOf(
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Cookie7Sided,
        MaterialShapes.Sunny,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.Pentagon,
        MaterialShapes.Arch,
        MaterialShapes.Gem,
        MaterialShapes.Puffy,
    )
}

/**
 * Contact avatar. Lists use circles; [expressive] picks a stable Material shape
 * per contact (favorites, call screen), the signature M3 Expressive touch.
 */
@Composable
fun Avatar(
    name: String?,
    photoUri: String?,
    seed: Long,
    size: Dp,
    modifier: Modifier = Modifier,
    expressive: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val index = seed.hashCode() and Int.MAX_VALUE
    val shape: Shape = remember(expressive, index) {
        if (expressive) expressiveShapes[index % expressiveShapes.size].toShape() else CircleShape
    }
    val (bg, fg) = when (index % 3) {
        0 -> scheme.primaryContainer to scheme.onPrimaryContainer
        1 -> scheme.secondaryContainer to scheme.onSecondaryContainer
        else -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
    val context = LocalContext.current
    val photo by produceState<ImageBitmap?>(null, photoUri) {
        value = photoUri?.let { uri ->
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(uri))?.use {
                        BitmapFactory.decodeStream(it)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }
    val initial = initialOf(name)
    Box(modifier.size(size).clip(shape).background(bg), contentAlignment = Alignment.Center) {
        val p = photo
        when {
            p != null -> Image(p, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            initial != null -> Text(
                initial,
                color = fg,
                style = TextStyle(fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Medium),
            )
            else -> Icon(Icons.Rounded.Person, null, tint = fg, modifier = Modifier.size(size * 0.55f))
        }
    }
}

/** Corner radii for M3 Expressive grouped lists: big outer corners, tight inner ones. */
fun segmentShape(index: Int, count: Int, large: Dp = 24.dp, small: Dp = 6.dp): Shape = when {
    count == 1 -> RoundedCornerShape(large)
    index == 0 -> RoundedCornerShape(topStart = large, topEnd = large, bottomStart = small, bottomEnd = small)
    index == count - 1 -> RoundedCornerShape(topStart = small, topEnd = small, bottomStart = large, bottomEnd = large)
    else -> RoundedCornerShape(small)
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 32.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val shape = remember { MaterialShapes.Cookie9Sided.toShape() }
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(128.dp).clip(shape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}

/**
 * Button whose shape morphs on press (pill → rounded square), like M3 Expressive buttons.
 */
@Composable
fun MorphButton(
    onClick: () -> Unit,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    restingPercent: Int = 50,
    pressedPercent: Int = 28,
    enabled: Boolean = true,
    body: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateIntAsState(
        if (pressed) pressedPercent else restingPercent,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "corner",
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(percent = corner),
        color = container,
        contentColor = content,
        interactionSource = interaction,
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center) { body() }
    }
}

/** In-call toggle: circle when off, rounded square + primary color when on. */
@Composable
fun MorphToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val corner by animateIntAsState(
        if (checked) 30 else 50, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "toggle",
    )
    val container by animateColorAsState(
        if (checked) scheme.primary else scheme.surfaceContainerHighest,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "toggleColor",
    )
    val content = if (checked) scheme.onPrimary else scheme.onSurface
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = { onCheckedChange(!checked) },
            enabled = enabled,
            shape = RoundedCornerShape(percent = corner),
            color = container,
            contentColor = if (enabled) content else content.copy(alpha = 0.38f),
            modifier = Modifier.size(width = 76.dp, height = 64.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = label) }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
    }
}
