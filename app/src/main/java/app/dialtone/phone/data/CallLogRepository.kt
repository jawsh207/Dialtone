package app.dialtone.phone.data

import android.Manifest
import android.content.Context
import android.provider.CallLog.Calls
import app.dialtone.phone.util.dayBucket
import app.dialtone.phone.util.hasPermission
import app.dialtone.phone.util.numberKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the phone's call log (Android keeps it; Dialtone, as the phone app, can read and edit
 * it) into rows for the Home tab, newest first, merging back-to-back calls from the same
 * number into one row the way the stock dialer does.
 */
class CallLogRepository(private val context: Context) {

    suspend fun load(limit: Int = 300): List<RecentCall> = withContext(Dispatchers.IO) {
        if (!context.hasPermission(Manifest.permission.READ_CALL_LOG)) return@withContext emptyList()
        val uri = Calls.CONTENT_URI.buildUpon()
            .appendQueryParameter(Calls.LIMIT_PARAM_KEY, limit.toString())
            .build()
        val projection = arrayOf(
            Calls._ID, Calls.NUMBER, Calls.CACHED_NAME, Calls.CACHED_PHOTO_URI,
            Calls.TYPE, Calls.DATE, Calls.DURATION,
        )
        val raw = mutableListOf<RecentCall>()
        runCatching {
            context.contentResolver.query(uri, projection, null, null, "${Calls.DATE} DESC")?.use { c ->
                while (c.moveToNext()) {
                    raw += RecentCall(
                        id = c.getLong(0),
                        number = c.getString(1).orEmpty(),
                        cachedName = c.getString(2),
                        photoUri = c.getString(3),
                        type = when (c.getInt(4)) {
                            Calls.OUTGOING_TYPE -> CallType.Outgoing
                            Calls.MISSED_TYPE -> CallType.Missed
                            Calls.VOICEMAIL_TYPE -> CallType.Voicemail
                            Calls.REJECTED_TYPE -> CallType.Rejected
                            Calls.BLOCKED_TYPE -> CallType.Blocked
                            else -> CallType.Incoming
                        },
                        date = c.getLong(5),
                        durationSec = c.getLong(6),
                    )
                }
            }
        }
        collapseCalls(raw)
    }

}

/** Group back-to-back calls (same number, same type, same day) like the stock dialer. */
internal fun collapseCalls(calls: List<RecentCall>): List<RecentCall> {
    val out = mutableListOf<RecentCall>()
    for (call in calls) {
        val last = out.lastOrNull()
        if (last != null &&
            numberKey(last.number) == numberKey(call.number) &&
            last.type == call.type &&
            dayBucket(last.date) == dayBucket(call.date)
        ) {
            out[out.lastIndex] = last.copy(count = last.count + 1, ids = last.ids + call.id)
        } else {
            out += call
        }
    }
    return out
}

/** Removes calls from the system call log (the list refreshes itself when it changes). */
object CallLogEdits {
    fun delete(context: Context, ids: List<Long>) {
        if (ids.isEmpty() || !context.hasPermission(Manifest.permission.WRITE_CALL_LOG)) return
        runCatching {
            context.contentResolver.delete(
                Calls.CONTENT_URI,
                "${Calls._ID} IN (${ids.joinToString(",") { "?" }})",
                ids.map { it.toString() }.toTypedArray(),
            )
        }
    }
}
