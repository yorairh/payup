package com.payup.platform

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.payup.data.GameWithDetails
import com.payup.domain.BackupFile
import com.payup.resources.Res
import com.payup.resources.backup_save
import com.payup.resources.share_chooser
import com.payup.ui.theme.PayUpPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import kotlin.coroutines.resume

/** The phone's own date formats, in its current language. */
class AndroidDateFormatter : DateFormatter {
    override fun date(millis: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

    override fun dateTime(millis: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

    override fun time(millis: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))
}

/** Puts [file] into the app's cache folder's share area and opens the system share menu for it. */
private fun Context.startShare(file: File, mimeType: String, chooserTitle: String) {
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType(mimeType)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val chooser = Intent.createChooser(send, chooserTitle)
    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(chooser)
}

/** A fresh, empty folder in the cache for files being shared (old ones are cleared first). */
private fun Context.freshShareDir(): File {
    val dir = File(cacheDir, "shared").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    return dir
}

class AndroidShareService(private val context: Context, private val dates: DateFormatter) : ShareService {
    override suspend fun shareText(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, getString(Res.string.share_chooser)))
    }

    override suspend fun shareResultImage(game: GameWithDetails, palette: PayUpPalette, rtl: Boolean) {
        val file = File(context.freshShareDir(), "payup-${game.game.id}.png")
        val bitmap = renderShareImage(game, palette, rtl, dates)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        context.startShare(file, "image/png", getString(Res.string.share_chooser))
    }
}

/** Backup files through the system file dialogs, so no storage permission is needed. */
class AndroidBackupFiles(private val activity: ComponentActivity) : BackupFiles {
    private var requests = 0

    /** Opens a system screen and waits for what it returns. */
    private suspend fun <I, O> ask(contract: ActivityResultContract<I, O>, input: I): O =
        suspendCancellableCoroutine { continuation ->
            lateinit var launcher: ActivityResultLauncher<I>
            launcher = activity.activityResultRegistry.register("payup-backup-${requests++}", contract) { result ->
                launcher.unregister()
                continuation.resume(result)
            }
            continuation.invokeOnCancellation { launcher.unregister() }
            launcher.launch(input)
        }

    override suspend fun share(fileName: String, text: String) {
        val file = withContext(Dispatchers.IO) {
            File(activity.freshShareDir(), fileName).also { it.writeText(text, Charsets.UTF_8) }
        }
        activity.startShare(file, "application/json", getString(Res.string.backup_save))
    }

    override suspend fun saveAs(fileName: String, text: String): Boolean? {
        val uri = ask(ActivityResultContracts.CreateDocument("application/json"), fileName) ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val out = activity.contentResolver.openOutputStream(uri, "wt") ?: return@withContext false
                out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                true
            } catch (e: IOException) {
                false
            } catch (e: SecurityException) {
                false
            }
        }
    }

    override suspend fun pick(): PickedFile {
        // Backups sent through chat apps often lose their file type, so any file can be picked.
        val uri = ask(ActivityResultContracts.OpenDocument(), arrayOf("*/*")) ?: return PickedFile.Cancelled
        val text = withContext(Dispatchers.IO) { readLimited(uri) }
        return if (text == null) PickedFile.Unreadable else PickedFile.Text(text)
    }

    /** The file's text, or null when it cannot be read or is bigger than any backup can be. */
    private fun readLimited(uri: android.net.Uri): String? {
        try {
            activity.contentResolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (out.size() + n > BackupFile.MAX_BYTES) return null
                    out.write(buffer, 0, n)
                }
                return String(out.toByteArray(), Charsets.UTF_8)
            }
        } catch (e: IOException) {
            return null
        } catch (e: SecurityException) {
            return null
        }
        return null
    }
}
