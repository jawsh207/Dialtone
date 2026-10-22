package app.dialtone.phone.telecom

import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.widget.Toast
import app.dialtone.phone.util.hasPermission

/** Small helpers for actions started from the UI. */
object Dialer {

    fun isDefaultDialer(context: Context): Boolean =
        context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_DIALER) == true

    fun call(context: Context, number: String) =
        place(context, Uri.fromParts("tel", number, null))

    /** "voicemail:" lets Telecom pick the right voicemail number for the SIM. */
    fun callVoicemail(context: Context) =
        place(context, Uri.fromParts("voicemail", "", null))

    @SuppressLint("MissingPermission")
    private fun place(context: Context, uri: Uri) {
        if (!context.hasPermission(Manifest.permission.CALL_PHONE)) {
            Toast.makeText(context, "Phone permission is needed to place calls", Toast.LENGTH_SHORT).show()
            return
        }
        context.getSystemService(TelecomManager::class.java)?.placeCall(uri, Bundle())
    }

    fun sms(context: Context, number: String) = safeStart(
        context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")),
    )

    fun addContact(context: Context, number: String) = safeStart(
        context,
        Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
            type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, number)
        },
    )

    fun newContact(context: Context) = safeStart(
        context, Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI),
    )

    fun copy(context: Context, number: String) {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("Phone number", number))
    }

    private fun safeStart(context: Context, intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toast.makeText(context, "No app can handle that", Toast.LENGTH_SHORT).show() }
    }
}
