package app.dialtone.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight

private val base = Typography()

private val DialtoneTypography = base.copy(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Normal),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Normal),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Medium),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Medium),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Medium),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun DialtoneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme()
        else -> expressiveLightColorScheme()
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = DialtoneTypography,
        content = content,
    )
}

/** Call-green and its content color, tuned per theme (not part of the M3 scheme). */
object CallColors {
    @Composable
    fun container(): Color = if (isSystemInDarkTheme()) Color(0xFF6DD58C) else Color(0xFF146C2E)

    @Composable
    fun content(): Color = if (isSystemInDarkTheme()) Color(0xFF00391A) else Color.White
}
