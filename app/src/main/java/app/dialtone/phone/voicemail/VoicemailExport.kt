package app.dialtone.phone.voicemail

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.VoicemailContract.Voicemails
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.FileProvider
import app.dialtone.phone.data.Voicemail
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Save and Share for a voicemail's audio: the safe kind of archive.
 *
 * Both only copy the recording out of Android's voicemail store; nothing changes on the carrier's
 * server, and the copy outlives Dialtone (an uninstall, or turning visual voicemail off, which
 * clears Dialtone's own copies). Save uses Android's own file picker (the Storage Access
 * Framework), so the user chooses the folder and no storage permission is needed. Share copies the
 * file into Dialtone's cache and hands it out through a FileProvider, which grants the receiving
 * app read access to that one file only.
 */
object VoicemailExport {

    private const val SHARE_DIR = "voicemail-share"

    /** The recording's type as the engine stored it (e.g. audio/amr), without parameters. */
    suspend fun mimeType(context: Context, vm: Voicemail): String = withContext(Dispatchers.IO) {
        val stored = runCatching {
            context.contentResolver.query(vm.uri, arrayOf(Voicemails.MIME_TYPE), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        cleanMime(stored)
    }

    /** Copies the recording to [target] (a document the user picked). True on success. */
    suspend fun saveTo(context: Context, vm: Voicemail, target: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            resolver.openInputStream(vm.uri).use { input ->
                resolver.openOutputStream(target, "w").use { output ->
                    requireNotNull(input).copyTo(requireNotNull(output))
                }
            }
        }.isSuccess
    }

    /**
     * A share sheet for the recording, or null if it couldn't be copied. Older shared copies are
     * removed first so the cache only ever holds the latest one.
     */
    suspend fun shareIntent(context: Context, vm: Voicemail, caller: String): Intent? = withContext(Dispatchers.IO) {
        runCatching {
            val mime = mimeType(context, vm)
            val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, fileName(caller, vm.date, extensionFor(mime), ZoneId.systemDefault()))
            context.contentResolver.openInputStream(vm.uri).use { input ->
                file.outputStream().use { requireNotNull(input).copyTo(it) }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val send = Intent(Intent.ACTION_SEND)
                .setType(mime)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, "Voicemail from $caller")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // ClipData too, so the read grant reaches the app chosen in the share sheet.
            send.clipData = ClipData.newRawUri(file.name, uri)
            Intent.createChooser(send, "Share voicemail")
        }.getOrNull()
    }

    /** "audio/AMR; name=x" → "audio/amr"; unknown or missing → "audio/amr" (what carriers send). */
    fun cleanMime(stored: String?): String {
        val base = stored?.substringBefore(';')?.trim()?.lowercase()
        return if (base.isNullOrEmpty() || !base.contains('/')) "audio/amr" else base
    }

    /** A file extension players recognise for [mime]. */
    fun extensionFor(mime: String): String = when (cleanMime(mime)) {
        "audio/amr" -> "amr"
        "audio/amr-wb" -> "awb"
        "audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave" -> "wav"
        "audio/mpeg", "audio/mp3" -> "mp3"
        "audio/mp4", "audio/aac", "audio/x-m4a", "audio/m4a" -> "m4a"
        "audio/ogg", "audio/opus" -> "ogg"
        "audio/3gpp" -> "3gp"
        "audio/qcelp" -> "qcp"
        else -> "audio"
    }

    /** "Voicemail from Alex Doe 2026-11-22 21.36.amr", with characters file systems refuse removed. */
    fun fileName(caller: String, dateMillis: Long, extension: String, zone: ZoneId): String {
        val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm").withZone(zone).format(Instant.ofEpochMilli(dateMillis))
        val who = caller.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "").trim().ifEmpty { "Unknown" }
        return "Voicemail from $who $stamp.$extension"
    }

    /** Android's "save as" picker; input is (file name, MIME type), result the chosen document. */
    class CreateAudioDocument : ActivityResultContract<Pair<String, String>, Uri?>() {
        override fun createIntent(context: Context, input: Pair<String, String>): Intent =
            Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(input.second)
                .putExtra(Intent.EXTRA_TITLE, input.first)

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            if (resultCode == Activity.RESULT_OK) intent?.data else null
    }
}
