package app.dialtone.phone.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.dialtone.phone.R

/** Google Sans Flex: one variable font file, one entry per weight (SIL OFL 1.1). */
val GoogleSansFlex: FontFamily = FontFamily(
    listOf(
        FontWeight.Thin, FontWeight.ExtraLight, FontWeight.Light, FontWeight.Normal, FontWeight.Medium,
        FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold, FontWeight.Black,
    ).map { weight -> Font(R.font.google_sans_flex, weight) }
)

/** Every Material type style, re-set in [family]. */
fun Typography.withFontFamily(family: FontFamily): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

/**
 * Same theme as the Contacts app. Colour roles: pages use background/surface
 * (surfaceContainerLow), the bottom bar keeps its default surfaceContainer, cards use
 * surfaceBright, search bars use surfaceContainerHighest.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ContactsMatchTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Dynamic (wallpaper) colour needs Android 12; minSdk is 29, so fall back to the
    // standard Material light/dark scheme on Android 10–11.
    val wallpaper = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    val colorScheme = wallpaper.copy(
        background = wallpaper.surfaceContainerLow,
        surface = wallpaper.surfaceContainerLow,
    )
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = Typography().withFontFamily(GoogleSansFlex),
        content = content,
    )
}

/**
 * Call-green for Call / Answer buttons. Not a Material colour role: a dialer convention the
 * Contacts theme has no equivalent for, so it stays alongside the scheme.
 */
object CallColors {
    @Composable
    fun container(): Color = if (isSystemInDarkTheme()) Color(0xFF6DD58C) else Color(0xFF146C2E)

    @Composable
    fun content(): Color = if (isSystemInDarkTheme()) Color(0xFF00391A) else Color.White
}
