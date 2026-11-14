package app.dialtone.phone.telecom

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Quick responses: texts offered when declining an incoming call ("Reply" on the incoming-call
 * screen). Four editable slots, starting with Android's usual wording. Sending goes through
 * Telecom ([android.telecom.Call.reject] with a message), which hands the text to the default
 * SMS app, so Dialtone needs no SMS permission for it.
 */
object QuickResponses {
    private const val PREFS = "quick_responses"

    val DEFAULTS = listOf(
        "Can't talk now. What's up?",
        "I'll call you right back.",
        "I'll call you later.",
        "Can't talk now. Call me later?",
    )

    private val state = MutableStateFlow<List<String>?>(null)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun load(context: Context): List<String> = state.value ?: DEFAULTS.indices.map { i ->
        prefs(context).getString("r$i", null)?.takeIf { it.isNotBlank() } ?: DEFAULTS[i]
    }.also { state.value = it }

    fun responses(context: Context): StateFlow<List<String>?> {
        load(context)
        return state.asStateFlow()
    }

    /** Saves slot [index]; blank text restores that slot's default. */
    fun set(context: Context, index: Int, text: String) {
        val clean = text.trim()
        prefs(context).edit().apply {
            if (clean.isEmpty() || clean == DEFAULTS[index]) remove("r$index") else putString("r$index", clean)
        }.apply()
        state.value = load(context).toMutableList().also { it[index] = clean.ifEmpty { DEFAULTS[index] } }
    }

    fun resetAll(context: Context) {
        prefs(context).edit().clear().apply()
        state.value = DEFAULTS
    }
}
