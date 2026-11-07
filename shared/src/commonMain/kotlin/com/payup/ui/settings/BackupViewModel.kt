package com.payup.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.payup.data.GameRepository
import com.payup.domain.Backup
import com.payup.domain.BackupFile
import com.payup.domain.BackupMerge
import com.payup.domain.BackupReadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    /** Shows what restoring the chosen file's [text] would add (null: it could not be read); nothing is changed yet. */
    fun readFile(text: String?) {
        viewModelScope.launch {
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
}
