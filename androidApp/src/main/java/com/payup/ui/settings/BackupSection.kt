package com.payup.ui.settings

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.payup.R
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

enum class BackupFailure { NOT_BACKUP, TOO_NEW, INVALID, UNREADABLE }

sealed interface BackupUi {
    data object Idle : BackupUi
    data class Preview(val backup: Backup, val plan: BackupMerge.Plan) : BackupUi
    data class Done(val plan: BackupMerge.Plan) : BackupUi
    data class Failed(val reason: BackupFailure) : BackupUi
}

class BackupViewModel(private val repository: GameRepository) : ViewModel() {
    private val _ui = MutableStateFlow<BackupUi>(BackupUi.Idle)
    val ui: StateFlow<BackupUi> = _ui

    /** The backup file's text for every game on this phone. */
    suspend fun backupText(): String {
        val backup = repository.exportBackup()
        return withContext(Dispatchers.Default) { BackupFile.write(backup) }
    }

    /** Reads a chosen file and shows what restoring it would add; nothing is changed yet. */
    fun readFile(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { readLimited(resolver, uri) }
            if (text == null) {
                _ui.value = BackupUi.Failed(BackupFailure.UNREADABLE)
                return@launch
            }
            _ui.value = when (val result = withContext(Dispatchers.Default) { BackupFile.read(text) }) {
                is BackupReadResult.Ok -> BackupUi.Preview(result.backup, repository.previewImport(result.backup))
                BackupReadResult.NotBackup -> BackupUi.Failed(BackupFailure.NOT_BACKUP)
                is BackupReadResult.TooNew -> BackupUi.Failed(BackupFailure.TOO_NEW)
                BackupReadResult.Invalid -> BackupUi.Failed(BackupFailure.INVALID)
            }
        }
    }

    fun restore() {
        val preview = _ui.value as? BackupUi.Preview ?: return
        _ui.value = BackupUi.Idle
        viewModelScope.launch { _ui.value = BackupUi.Done(repository.importBackup(preview.backup)) }
    }

    fun dismiss() {
        _ui.value = BackupUi.Idle
    }

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
}

/** The two backup buttons for Settings, and the dialogs that go with restoring. */
@Composable
fun BackupActions() {
    val context = LocalContext.current
    val vm = payUpViewModel { repo, _ -> BackupViewModel(repo) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.readFile(context.contentResolver, uri)
    }

    // The user picks the folder (Downloads, Drive...) in the system file dialog, so no storage permission is needed.
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val saved = saveBackup(context, uri, vm.backupText())
            Toast.makeText(context, if (saved) R.string.backup_saved else R.string.backup_save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
        ActionCard(stringResource(R.string.backup_save), stringResource(R.string.backup_save_hint)) {
            scope.launch { shareBackup(context, vm.backupText()) }
        }
        ActionCard(stringResource(R.string.backup_download), stringResource(R.string.backup_download_hint)) {
            saver.launch(backupFileName())
        }
        // Backups sent through chat apps often lose their file type, so any file can be picked.
        ActionCard(stringResource(R.string.backup_restore), stringResource(R.string.backup_restore_hint)) {
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
                title = { Text(stringResource(R.string.backup_restore)) },
                text = { Text(previewText(plan)) },
                confirmButton = {
                    if (nothingToAdd) {
                        TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.close)) }
                    } else {
                        TextButton(onClick = vm::restore) { Text(stringResource(R.string.backup_add_games)) }
                    }
                },
                dismissButton = if (nothingToAdd) null else {
                    { TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.cancel)) } }
                },
            )
        }
        is BackupUi.Done -> AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text(stringResource(R.string.backup_restore)) },
            text = { Text(stringResource(R.string.backup_done_body, state.plan.toAdd.size)) },
            confirmButton = { TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.close)) } },
        )
        is BackupUi.Failed -> AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text(stringResource(R.string.backup_failed_title)) },
            text = { Text(stringResource(state.reason.message())) },
            confirmButton = { TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.close)) } },
        )
    }
}

@Composable
private fun previewText(plan: BackupMerge.Plan): String = buildList {
    if (plan.toAdd.isEmpty() && plan.skippedUnfinished == 0) {
        add(stringResource(R.string.backup_all_present))
    } else {
        add(stringResource(R.string.backup_preview_body, plan.total, plan.toAdd.size))
    }
    if (plan.skippedUnfinished > 0) add(stringResource(R.string.backup_skipped_unfinished, plan.skippedUnfinished))
}.joinToString("\n")

private fun BackupFailure.message(): Int = when (this) {
    BackupFailure.NOT_BACKUP -> R.string.backup_error_not_backup
    BackupFailure.TOO_NEW -> R.string.backup_error_too_new
    BackupFailure.INVALID -> R.string.backup_error_invalid
    BackupFailure.UNREADABLE -> R.string.backup_error_read
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
    val chooser = Intent.createChooser(send, context.getString(R.string.backup_save))
    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(chooser)
}
