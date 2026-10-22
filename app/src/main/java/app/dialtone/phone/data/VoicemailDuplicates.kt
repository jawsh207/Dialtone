package app.dialtone.phone.data

import kotlin.math.abs

/**
 * After switching from another phone app (e.g. the stock dialer), the same server message can
 * be in the voicemail store twice: the old app's copy and the one Dialtone's own visual
 * voicemail synced. The two apps don't record number or time identically, so copies are
 * matched by the carrier's server message id (SOURCE_DATA, the IMAP UID both engines store)
 * and, failing that, by caller + time + length.
 */
object VoicemailDuplicates {

    data class Row(
        val id: Long,
        val sourcePackage: String?,
        val sourceData: String?,
        val number: String,
        val date: Long,
        val durationSec: Long,
    )

    private const val TIME_WINDOW_MS = 5 * 60_000L
    private const val DURATION_SLACK_SEC = 2L

    /**
     * Maps each other-app row that duplicates one of [ownPackage]'s rows to that row's id.
     * Other-app copies of the same message (several old apps) collapse onto the first one.
     */
    fun match(rows: List<Row>, ownPackage: String): Map<Long, Long> {
        val own = rows.filter { it.sourcePackage == ownPackage }
        val others = rows.filter { it.sourcePackage != ownPackage }
        val result = HashMap<Long, Long>()
        val keptOthers = mutableListOf<Row>()
        for (row in others.sortedByDescending { it.date }) {
            val twin = own.firstOrNull { same(it, row) } ?: keptOthers.firstOrNull { same(it, row) }
            if (twin != null) result[row.id] = result[twin.id] ?: twin.id else keptOthers += row
        }
        return result
    }

    private fun same(a: Row, b: Row): Boolean {
        val uidA = a.sourceData?.trim().orEmpty()
        val uidB = b.sourceData?.trim().orEmpty()
        if (uidA.isNotEmpty() && uidA == uidB && abs(a.date - b.date) <= 24 * 3_600_000L) return true
        val numA = digits(a.number)
        return numA.isNotEmpty() && numA == digits(b.number) &&
            abs(a.date - b.date) <= TIME_WINDOW_MS &&
            (a.durationSec <= 0 || b.durationSec <= 0 || abs(a.durationSec - b.durationSec) <= DURATION_SLACK_SEC)
    }

    /** Last 10 digits, so "+1 (555) 123-4567" and "5551234567" compare equal. */
    private fun digits(number: String): String = number.filter(Char::isDigit).takeLast(10)
}
