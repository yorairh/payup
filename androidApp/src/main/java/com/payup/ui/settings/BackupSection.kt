package com.payup.ui.settings

import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.StringResource
import com.payup.resources.Res
import com.payup.resources.backup_add_games
import com.payup.resources.backup_all_present
import com.payup.resources.backup_done_body
import com.payup.resources.backup_download
import com.payup.resources.backup_download_hint
import com.payup.resources.backup_error_invalid
import com.payup.resources.backup_error_not_backup
import com.payup.resources.backup_error_read
import com.payup.resources.backup_error_too_new
import com.payup.resources.backup_failed_title
import com.payup.resources.backup_preview_body
import com.payup.resources.backup_restore
import com.payup.resources.backup_restore_hint
import com.payup.resources.backup_save
import com.payup.resources.backup_save_failed
import com.payup.resources.backup_save_hint
import com.payup.resources.backup_saved
import com.payup.resources.backup_skipped_unfinished
import com.payup.resources.cancel
import com.payup.resources.close
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.payup.data.GameRepository
import com.payup.domain.Backup
import com.payup.domain.BackupFile
import com.payup.domain.BackupMerge
import com.payup.domain.BackupReadResult
import com.payup.ui.common.payUpViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.LocalDate

private fun readLimited(resolver: ContentResolver, uri: Uri): String? {
    try {
        resolver.openInputStream(uri)?.use { input ->
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

/** The two backup buttons for Settings, and the dialogs that go with restoring. */
@Composable
fun BackupActions() {
    val context = LocalContext.current
    val vm = payUpViewModel { BackupViewModel(it.repository) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.readFile { withContext(Dispatchers.IO) { readLimited(context.contentResolver, uri) } }
    }

    // The user picks the folder (Downloads, Drive...) in the system file dialog, so no storage permission is needed.
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val saved = saveBackup(context, uri, vm.backupText())
            Toast.makeText(context, getString(if (saved) Res.string.backup_saved else Res.string.backup_save_failed), Toast.LENGTH_SHORT).show()
        }
    }

    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
        ActionCard(stringResource(Res.string.backup_save), stringResource(Res.string.backup_save_hint)) {
            scope.launch { shareBackup(context, vm.backupText()) }
        }
        ActionCard(stringResource(Res.string.backup_download), stringResource(Res.string.backup_download_hint)) {
            saver.launch(backupFileName())
        }
        // Backups sent through chat apps often lose their file type, so any file can be picked.
        ActionCard(stringResource(Res.string.backup_restore), stringResource(Res.string.backup_restore_hint)) {
            picker.launch(arrayOf("*/*"))
        }
    }

    when (val state = ui) {
        BackupUi.Idle -> Unit
        is BackupUi.Preview -> {
            val plan = state.plan
            val nothingToAdd = plan.toAdd.isEmpty()
            AlertDialog(
                onDismissRequest = vm::dismiss,
                title = { Text(stringResource(Res.string.backup_restore)) },
                text = { Text(previewText(plan)) },
                confirmButton = {
                    if (nothingToAdd) {
                        TextButton(onClick = vm::dismiss) { Text(stringResource(Res.string.close)) }
                    } else {
                        TextButton(onClick = vm::restore) { Text(stringResource(Res.string.backup_add_games)) }
                    }
                },
                dismissButton = if (nothingToAdd) null else {
                    { TextButton(onClick = vm::dismiss) { Text(stringResource(Res.string.cancel)) } }
                },
            )
        }
        is BackupUi.Done -> AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text(stringResource(Res.string.backup_restore)) },
            text = { Text(stringResource(Res.string.backup_done_body, state.plan.toAdd.size)) },
            confirmButton = { TextButton(onClick = vm::dismiss) { Text(stringResource(Res.string.close)) } },
        )
        is BackupUi.Failed -> AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text(stringResource(Res.string.backup_failed_title)) },
            text = { Text(stringResource(state.reason.message())) },
            confirmButton = { TextButton(onClick = vm::dismiss) { Text(stringResource(Res.string.close)) } },
        )
    }
}

@Composable
private fun previewText(plan: BackupMerge.Plan): String = buildList {
    if (plan.toAdd.isEmpty() && plan.skippedUnfinished == 0) {
        add(stringResource(Res.string.backup_all_present))
    } else {
        add(stringResource(Res.string.backup_preview_body, plan.total, plan.toAdd.size))
    }
    if (plan.skippedUnfinished > 0) add(stringResource(Res.string.backup_skipped_unfinished, plan.skippedUnfinished))
}.joinToString("\n")

private fun BackupFailure.message(): StringResource = when (this) {
    BackupFailure.NOT_BACKUP -> Res.string.backup_error_not_backup
    BackupFailure.TOO_NEW -> Res.string.backup_error_too_new
    BackupFailure.INVALID -> Res.string.backup_error_invalid
    BackupFailure.UNREADABLE -> Res.string.backup_error_read
}

@Composable
private fun ActionCard(title: String, hint: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(hint, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun backupFileName() = "payup-backup-${LocalDate.now()}.json"

/** Writes the backup to the file the user just created in the system file dialog; false if it could not be written. */
private suspend fun saveBackup(context: Context, uri: Uri, text: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: return@withContext false
        out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        true
    } catch (e: IOException) {
        false
    } catch (e: SecurityException) {
        false
    }
}

/** Writes the backup into the app's cache and hands it to the share menu (Drive, WhatsApp, e-mail...). */
private suspend fun shareBackup(context: Context, text: String) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        File(dir, backupFileName()).also { it.writeText(text, Charsets.UTF_8) }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/json")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val chooser = Intent.createChooser(send, getString(Res.string.backup_save))
    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(chooser)
}
