package app.dialtone.util

import android.content.Context
import android.content.pm.PackageManager
import android.telephony.PhoneNumberUtils
import android.text.format.DateUtils
import androidx.core.content.ContextCompat
import java.util.Locale

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun formatNumber(number: String): String {
    if (number.isBlank()) return number
    return PhoneNumberUtils.formatNumber(number, Locale.getDefault().country) ?: number
}

/** Last 10 digits — used to match one number across call log, contacts and voicemail. */
fun numberKey(number: String): String {
    val digits = number.filter { it.isDigit() }
    return if (digits.length > 10) digits.takeLast(10) else digits
}

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatTime(context: Context, millis: Long): String =
    DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_TIME)

fun formatShortDate(context: Context, millis: Long): String =
    if (DateUtils.isToday(millis)) formatTime(context, millis)
    else DateUtils.formatDateTime(
        context, millis,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_TIME,
    )

enum class DayBucket(val label: String) { Today("Today"), Yesterday("Yesterday"), Older("Older") }

fun dayBucket(millis: Long): DayBucket = when {
    DateUtils.isToday(millis) -> DayBucket.Today
    DateUtils.isToday(millis + DateUtils.DAY_IN_MILLIS) -> DayBucket.Yesterday
    else -> DayBucket.Older
}

fun initialOf(name: String?): String? =
    name?.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString()
