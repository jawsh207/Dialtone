package app.dialtone.phone.screening

import android.content.Context
import app.dialtone.phone.util.numberKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Numbers whose incoming calls go straight to voicemail. Kept by Dialtone (not the contacts'
 * own flag), so it works for any number, saved or not. Numbers are stored as entered and
 * matched by [numberKey], so "+1 (555) 123-4567" and "5551234567" are the same caller.
 *
 * Enforced by [SendToVoicemailScreeningService], which rejects matching calls before they
 * ring; the carrier then forwards the caller to voicemail as it does for a declined call.
 */
object SendToVoicemail {
    private const val PREFS = "send_to_voicemail"
    private const val KEY_NUMBERS = "numbers"

    private val state = MutableStateFlow<List<String>?>(null)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun load(context: Context): List<String> =
        state.value ?: (prefs(context).getStringSet(KEY_NUMBERS, emptySet()) ?: emptySet())
            .sortedBy { numberKey(it) }
            .also { state.value = it }

    /** The list, kept current as numbers are added and removed. */
    fun numbers(context: Context): StateFlow<List<String>?> {
        load(context)
        return state.asStateFlow()
    }

    fun contains(context: Context, number: String): Boolean {
        val key = numberKey(number)
        return key.isNotEmpty() && load(context).any { numberKey(it) == key }
    }

    fun add(context: Context, numbers: Collection<String>) {
        val current = load(context)
        val keys = current.map(::numberKey).toMutableSet()
        val added = numbers.map { it.trim() }.filter { n ->
            val key = numberKey(n)
            key.isNotEmpty() && keys.add(key)
        }
        if (added.isNotEmpty()) save(context, current + added)
    }

    fun add(context: Context, number: String) = add(context, listOf(number))

    fun remove(context: Context, numbers: Collection<String>) {
        val keys = numbers.map(::numberKey).toSet()
        val current = load(context)
        val kept = current.filterNot { numberKey(it) in keys }
        if (kept.size != current.size) save(context, kept)
    }

    fun remove(context: Context, number: String) = remove(context, listOf(number))

    private fun save(context: Context, numbers: List<String>) {
        val sorted = numbers.sortedBy { numberKey(it) }
        prefs(context).edit().putStringSet(KEY_NUMBERS, sorted.toSet()).apply()
        state.value = sorted
    }
}
