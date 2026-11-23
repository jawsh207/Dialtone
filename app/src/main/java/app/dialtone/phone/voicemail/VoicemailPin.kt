package app.dialtone.phone.voicemail

import app.dialtone.phone.vvm.PinChanger

/**
 * The rules and wording for changing the voicemail PIN, kept apart from the screen so they can be
 * checked without a phone.
 *
 * The carrier sends its PIN length rules when visual voicemail is set up (as "min-max"); some
 * don't, and then only "digits only" is checked here and the carrier has the final say. The
 * carrier's own refusals (too short, too weak, wrong current PIN…) come back as PinChanger codes
 * and are turned into plain sentences by [resultMessage].
 */
object VoicemailPin {

    /** Why the new PIN can't be sent yet, or null when it can. */
    fun problem(currentPin: String, newPin: String, confirmPin: String, minLength: Int, maxLength: Int): String? = when {
        currentPin.isEmpty() -> "Enter your current PIN"
        newPin.isEmpty() -> "Enter a new PIN"
        !newPin.all { it in '0'..'9' } -> "Use numbers only"
        minLength > 0 && newPin.length < minLength -> "Use at least $minLength digits"
        maxLength > 0 && newPin.length > maxLength -> "Use no more than $maxLength digits"
        newPin == currentPin -> "The new PIN is the same as the current one"
        confirmPin != newPin -> "The PINs don't match"
        else -> null
    }

    /** "4 to 7 digits", "At least 4 digits", or null when the carrier gave no rules. */
    fun lengthHint(minLength: Int, maxLength: Int): String? = when {
        minLength > 0 && maxLength > 0 && minLength == maxLength -> "$minLength digits"
        minLength > 0 && maxLength > 0 -> "$minLength to $maxLength digits"
        minLength > 0 -> "At least $minLength digits"
        maxLength > 0 -> "Up to $maxLength digits"
        else -> null
    }

    /** What to tell the user after the carrier answers. */
    fun resultMessage(result: Int, minLength: Int, maxLength: Int): String = when (result) {
        PinChanger.CHANGE_PIN_SUCCESS -> "Voicemail PIN changed"
        PinChanger.CHANGE_PIN_TOO_SHORT ->
            if (minLength > 0) "Your carrier needs at least $minLength digits" else "That PIN is too short for your carrier"
        PinChanger.CHANGE_PIN_TOO_LONG ->
            if (maxLength > 0) "Your carrier allows at most $maxLength digits" else "That PIN is too long for your carrier"
        PinChanger.CHANGE_PIN_TOO_WEAK -> "That PIN is too easy to guess. Avoid repeats and runs like 1111 or 1234"
        PinChanger.CHANGE_PIN_MISMATCH -> "The current PIN isn't right"
        PinChanger.CHANGE_PIN_INVALID_CHARACTER -> "Use numbers only"
        else -> "Couldn't reach your voicemail. Visual voicemail needs mobile data; try again"
    }
}
