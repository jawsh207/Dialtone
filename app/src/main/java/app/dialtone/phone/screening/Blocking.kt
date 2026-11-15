package app.dialtone.phone.screening

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import android.provider.BlockedNumberContract.BlockedNumbers

/**
 * Android's own block list: calls (and texts) from these numbers are rejected by the system
 * before any app sees them. It's the list Settings › Blocked numbers opens; the default phone
 * app is allowed to read and change it. Unlike "Send to voicemail", blocked callers don't
 * reach voicemail either.
 *
 * Every call here is a cross-process lookup, so callers run them off the main thread.
 */
object Blocking {
    fun canBlock(context: Context): Boolean =
        runCatching { BlockedNumberContract.canCurrentUserBlockNumbers(context) }.getOrDefault(false)

    fun isBlocked(context: Context, number: String): Boolean =
        number.isNotBlank() && runCatching { BlockedNumberContract.isBlocked(context, number) }.getOrDefault(false)

    fun block(context: Context, number: String) {
        if (number.isBlank()) return
        runCatching {
            context.contentResolver.insert(
                BlockedNumbers.CONTENT_URI,
                ContentValues().apply { put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) },
            )
        }
    }

    fun unblock(context: Context, number: String) {
        if (number.isBlank()) return
        runCatching { BlockedNumberContract.unblock(context, number) }
    }
}
