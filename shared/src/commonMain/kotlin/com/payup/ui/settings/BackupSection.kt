package com.payup.ui.settings

import androidx.compose.material3.SnackbarHostState
import com.payup.platform.LocalPlatform
import com.payup.platform.PickedFile
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
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

/** The two backup buttons for Settings, and the dialogs that go with restoring. */
@Composable
fun BackupActions(snackbar: SnackbarHostState) {
    val files = LocalPlatform.current.backupFiles
    val vm = payUpViewModel { BackupViewModel(it.repository) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
        ActionCard(stringResource(Res.string.backup_save), stringResource(Res.string.backup_save_hint)) {
            scope.launch { files.share(backupFileName(), vm.backupText()) }
        }
        ActionCard(stringResource(Res.string.backup_download), stringResource(Res.string.backup_download_hint)) {
            scope.launch {
                val saved = files.saveAs(backupFileName(), vm.backupText()) ?: return@launch
                snackbar.showSnackbar(getString(if (saved) Res.string.backup_saved else Res.string.backup_save_failed))
            }
        }
        ActionCard(stringResource(Res.string.backup_restore), stringResource(Res.string.backup_restore_hint)) {
            scope.launch {
                when (val picked = files.pick()) {
                    PickedFile.Cancelled -> Unit
                    PickedFile.Unreadable -> vm.readFile(null)
                    is PickedFile.Text -> vm.readFile(picked.text)
                }
            }
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

private fun backupFileName(): String {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "payup-backup-$today.json"
}
