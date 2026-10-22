package app.dialtone.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The Contacts app's bottom bar: Material 3's 64dp ShortNavigationBar in its default
 * colours. It handles the gesture-bar inset itself, so callers add no padding around it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ContactsMatchBottomBar(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    ShortNavigationBar(
        modifier = modifier,
        windowInsets = ShortNavigationBarDefaults.windowInsets.union(WindowInsets.ime),
        content = content,
    )
}
