package app.dialtone.data

import android.net.Uri

data class PhoneNumber(val number: String, val label: String)

data class Contact(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val photoUri: String?,
    val starred: Boolean,
    val numbers: List<PhoneNumber>,
)

enum class CallType { Incoming, Outgoing, Missed, Rejected, Blocked, Voicemail }

data class RecentCall(
    val id: Long,
    val number: String,
    val cachedName: String?,
    val photoUri: String?,
    val type: CallType,
    val date: Long,
    val durationSec: Long,
    /** Consecutive calls with the same number and type are collapsed into one row. */
    val count: Int = 1,
)

data class Voicemail(
    val id: Long,
    val number: String,
    val date: Long,
    val durationSec: Long,
    val isRead: Boolean,
    val transcription: String?,
    val hasContent: Boolean,
    val uri: Uri,
    /** App that synced this message (Dialtone's engine, or a carrier voicemail app). */
    val sourcePackage: String?,
)

/** One SIM's visual voicemail state for the settings sheet. */
data class VvmAccount(
    val id: String,
    val label: String,
    val supported: Boolean,
    val protocol: String?,
    val enabled: Boolean,
    val activated: Boolean,
    val carrierAppInstalled: Boolean,
)

/** Latest status the engine reported to the system voicemail store. */
enum class VvmHealth { Unknown, Off, Ok, Configuring, NoConnection, Failed }
