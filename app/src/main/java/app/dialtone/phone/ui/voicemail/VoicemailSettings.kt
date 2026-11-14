package app.dialtone.phone.ui.voicemail

import android.telecom.TelecomManager
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Edit
import app.dialtone.phone.telecom.QuickResponses
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.KeyboardType
import app.dialtone.phone.data.Contact
import app.dialtone.phone.screening.SendToVoicemail
import app.dialtone.phone.ui.components.ContactPickerSheet
import app.dialtone.phone.util.formatNumber
import app.dialtone.phone.util.numberKey
import androidx.compose.material3.IconButton
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Voicemail
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dialtone.phone.data.TranscriptionUi
import android.provider.Settings
import android.content.Intent
import app.dialtone.phone.data.VvmAccount
import app.dialtone.phone.data.VvmHealth
import app.dialtone.phone.ui.components.segmentShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StatusBannerModel(
    val icon: ImageVector,
    val title: String,
    val body: String,
    /** Button label; null for no button. */
    val actionLabel: String?,
    /** true → action retries sync; false → action opens settings. */
    val retry: Boolean,
    val isError: Boolean,
)

/** A banner only when something needs the user's attention. */
fun statusBannerFor(accounts: List<VvmAccount>, health: VvmHealth): StatusBannerModel? {
    if (accounts.isEmpty()) return null
    val ours = accounts.filter { it.supported && it.enabled && !it.carrierAppInstalled }
    if (accounts.none { it.supported }) {
        return StatusBannerModel(
            Icons.Rounded.Voicemail,
            "Visual voicemail isn't available",
            "Your carrier isn't in Dialtone's list yet. New voicemails still show up as a " +
                "notification, and you can dial in to listen.",
            actionLabel = null, retry = false, isError = false,
        )
    }
    if (ours.isEmpty()) return null
    return when (health) {
        VvmHealth.Configuring -> StatusBannerModel(
            Icons.Rounded.Sync,
            "Setting up visual voicemail",
            "Talking to your carrier. This can take a few minutes the first time.",
            "Check again", retry = true, isError = false,
        )
        VvmHealth.NoConnection -> StatusBannerModel(
            Icons.Rounded.CloudOff,
            "Can't reach your voicemail",
            "Visual voicemail needs mobile data. Check that mobile data is on, then try again.",
            "Try again", retry = true, isError = true,
        )
        VvmHealth.Failed -> StatusBannerModel(
            Icons.Rounded.ErrorOutline,
            "Visual voicemail setup failed",
            "Your carrier didn't accept the setup request. Try again, or open settings to see the log.",
            "Try again", retry = true, isError = true,
        )
        else -> null
    }
}

@Composable
fun StatusBanner(banner: StatusBannerModel, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (banner.isError) scheme.errorContainer else scheme.secondaryContainer,
        contentColor = if (banner.isError) scheme.onErrorContainer else scheme.onSecondaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(banner.icon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(banner.title, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.padding(top = 6.dp))
            Text(banner.body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
            val label = banner.actionLabel
            if (label != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onAction) { Text(label) }
                }
            } else {
                Spacer(Modifier.padding(top = 8.dp))
            }
        }
    }
}

