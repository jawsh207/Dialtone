package app.dialtone.phone.transcription

import android.content.Context
import java.io.File

/**
 * The Moonshine model ships inside the APK (assets/models). The engine memory-maps a real
 * file, so it's copied out once while transcription is on, and deleted when it's turned off.
 */
object ModelStore {
    private const val ASSET = "models/moonshine-tiny-Q8_0.gguf"

    private fun target(context: Context) =
        File(context.noBackupFilesDir, "models/moonshine-tiny-Q8_0.gguf")

    private fun assetSize(context: Context): Long =
        runCatching { context.assets.openFd(ASSET).use { it.length } }.getOrDefault(-1L)

    /** Path to the extracted model, copying it out of the APK if needed; null if missing. */
    @Synchronized
    fun ensureExtracted(context: Context): File? {
        val file = target(context)
        val expected = assetSize(context)
        if (expected <= 0) return null // not bundled in this build
        if (file.isFile && file.length() == expected) return file
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        return runCatching {
            context.assets.open(ASSET).use { input ->
                tmp.outputStream().use { output -> input.copyTo(output, 1 shl 16) }
            }
            if (!tmp.renameTo(file)) error("rename failed")
            file
        }.onFailure { tmp.delete() }.getOrNull()
    }

    fun isBundled(context: Context): Boolean = assetSize(context) > 0

    @Synchronized
    fun delete(context: Context) {
        target(context).delete()
    }
}
