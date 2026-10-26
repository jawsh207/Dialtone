package app.dialtone.phone.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.VoicemailContract
import android.provider.VoicemailContract.Status
import android.provider.VoicemailContract.Voicemails
import android.os.Build
import app.dialtone.phone.transcription.SystemRecognizer
import app.dialtone.phone.transcription.Transcription
import app.dialtone.phone.vvm.DialtoneVoicemail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the system voicemail store (VoicemailContract). As the default phone app we hold
 * READ/WRITE_VOICEMAIL, so we see every message carrier visual-voicemail clients have
 * synced there, including audio and transcripts.
 */
class VoicemailRepository(private val context: Context) {

    suspend fun load(): List<Voicemail> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            Voicemails._ID, Voicemails.NUMBER, Voicemails.DATE, Voicemails.DURATION,
            Voicemails.IS_READ, Voicemails.TRANSCRIPTION, Voicemails.HAS_CONTENT,
            Voicemails.SOURCE_PACKAGE, Voicemails.SOURCE_DATA,
        )
        val out = mutableListOf<Voicemail>()
        val rows = mutableListOf<VoicemailDuplicates.Row>()
        try {
            context.contentResolver.query(
                Voicemails.CONTENT_URI, projection,
                "${Voicemails.DELETED} = 0", null,
                "${Voicemails.DATE} DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    out += Voicemail(
                        id = id,
                        number = c.getString(1).orEmpty(),
                        date = c.getLong(2),
                        durationSec = c.getLong(3),
                        isRead = c.getInt(4) == 1,
                        transcription = c.getString(5),
                        hasContent = c.getInt(6) == 1,
                        uri = ContentUris.withAppendedId(Voicemails.CONTENT_URI, id),
                        sourcePackage = c.getString(7),
                    )
                    rows += VoicemailDuplicates.Row(
                        id = id, sourcePackage = c.getString(7), sourceData = c.getString(8),
                        number = c.getString(1).orEmpty(), date = c.getLong(2), durationSec = c.getLong(3),
                    )
                }
            }
        } catch (_: SecurityException) {
            // Not the default phone app yet.
        } catch (_: IllegalArgumentException) {
            // Some OEM builds lack a voicemail provider.
        }
        merge(out, VoicemailDuplicates.match(rows, context.packageName))
    }

    /**
     * Shows each message once, preferring Dialtone's copy (it syncs read/delete to the server).
     * The hidden copy still fills gaps: its audio if ours hasn't downloaded yet, its transcript,
     * and its read state.
     */
    private fun merge(list: List<Voicemail>, dupes: Map<Long, Long>): List<Voicemail> {
        val byId = list.associateBy { it.id }
        val copiesOf = dupes.entries.groupBy({ it.value }, { byId.getValue(it.key) })
        return list.filter { it.id !in dupes }
            .map { main ->
                val copies = copiesOf[main.id].orEmpty()
                if (copies.isEmpty()) return@map main
                val audio = if (main.hasContent) main else copies.firstOrNull { it.hasContent } ?: main
                main.copy(
                    isRead = main.isRead || copies.any { it.isRead },
                    transcription = main.transcription?.takeIf { it.isNotBlank() }
                        ?: copies.firstNotNullOfOrNull { c -> c.transcription?.takeIf { it.isNotBlank() } },
                    hasContent = audio.hasContent,
                    uri = audio.uri,
                    duplicateIds = copies.map { it.id },
                )
            }
            .sortedByDescending { it.date }
    }

    suspend fun markRead(vm: Voicemail) = withContext(Dispatchers.IO) {
        for (id in listOf(vm.id) + vm.duplicateIds) {
            runCatching {
                context.contentResolver.update(
                    ContentUris.withAppendedId(Voicemails.CONTENT_URI, id),
                    ContentValues().apply { put(Voicemails.IS_READ, 1) },
                    null, null,
                )
            }
        }
        DialtoneVoicemail.requestUpload(context)
    }

    /** Flag as deleted so the carrier client also removes it from the server on next sync. */
    suspend fun delete(vm: Voicemail) = withContext(Dispatchers.IO) {
        // Each row on its own: the user asked to delete this message, so flagging every copy
        // (which the provider turns into a server delete by its owning app) is intended here.
        for (id in listOf(vm.id) + vm.duplicateIds) {
            val uri = ContentUris.withAppendedId(Voicemails.CONTENT_URI, id)
            val flagged = runCatching {
                context.contentResolver.update(
                    uri, ContentValues().apply { put(Voicemails.DELETED, 1) }, null, null,
                )
            }.getOrDefault(0)
            if (flagged == 0) runCatching { context.contentResolver.delete(uri, null, null) }
        }
        DialtoneVoicemail.requestUpload(context)
    }

    /** Ask the app that owns this message to download its audio (when not prefetched). */
    fun requestContent(vm: Voicemail) {
        val owner = vm.sourcePackage ?: return
        runCatching {
            context.sendBroadcast(
                Intent(VoicemailContract.ACTION_FETCH_VOICEMAIL, vm.uri).setPackage(owner),
            )
        }
    }

    /** Health of Dialtone's own visual voicemail, from the rows it writes to the status table. */
    suspend fun health(): VvmHealth = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(
                Status.CONTENT_URI,
                arrayOf(Status.CONFIGURATION_STATE, Status.DATA_CHANNEL_STATE),
                "${Status.SOURCE_PACKAGE} = ?",
                arrayOf(context.packageName),
                null,
            )?.use { c ->
                var result = VvmHealth.Unknown
                while (c.moveToNext()) {
                    val config = c.getInt(0)
                    val data = c.getInt(1)
                    val row = when {
                        config == Status.CONFIGURATION_STATE_DISABLED -> VvmHealth.Off
                        config == Status.CONFIGURATION_STATE_CONFIGURING -> VvmHealth.Configuring
                        config == Status.CONFIGURATION_STATE_FAILED ||
                            config == Status.CONFIGURATION_STATE_NOT_CONFIGURED -> VvmHealth.Failed
                        data != Status.DATA_CHANNEL_STATE_OK -> VvmHealth.NoConnection
                        else -> VvmHealth.Ok
                    }
                    // Report the most actionable state across SIMs (enum is ordered by urgency).
                    if (row.ordinal > result.ordinal) result = row
                }
                result
            }
        }.getOrNull() ?: VvmHealth.Unknown
    }

    suspend fun accounts(): List<VvmAccount> = withContext(Dispatchers.IO) {
        DialtoneVoicemail.getAccounts(context).map {
            VvmAccount(
                id = it.handle.id,
                label = it.label,
                supported = it.supported,
                protocol = it.protocol,
                enabled = it.enabled,
                activated = it.activated,
                carrierAppInstalled = it.carrierAppInstalled,
            )
        }
    }

    suspend fun setEnabled(accountId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        DialtoneVoicemail.getAccounts(context).firstOrNull { it.handle.id == accountId }?.let {
            DialtoneVoicemail.setEnabled(context, it.handle, enabled)
        }
    }

    fun syncNow() = DialtoneVoicemail.syncNow(context)

    suspend fun transcriptionState(): TranscriptionUi = withContext(Dispatchers.IO) {
        val recognizer = SystemRecognizer.current(context)
        TranscriptionUi(
            supported = Build.VERSION.SDK_INT >= 33,
            available = Transcription.isAvailable(context),
            recognizerName = recognizer?.label,
            isDistillery = recognizer?.isDistillery == true,
            enabled = Transcription.isEnabled(context),
            onDevice = Transcription.transcribedIds(context),
            attempted = Transcription.attemptedIds(context),
        )
    }

    /** Retry messages left for later (e.g. the recognizer refused while Dialtone was in the background). */
    fun retryTranscription() = Transcription.schedule(context, runNow = true)

    suspend fun setTranscriptionEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        Transcription.setEnabled(context, enabled)
    }

    fun readLog(): String = DialtoneVoicemail.readLog(context)

    fun clearLog() = DialtoneVoicemail.clearLog(context)
}
