package com.payup.ui.friends

import org.jetbrains.compose.resources.getString
import com.payup.resources.Res
import com.payup.resources.add
import com.payup.resources.add_player
import com.payup.resources.delete
import com.payup.resources.delete_friend_message
import com.payup.resources.delete_friend_title
import com.payup.resources.friend_exists
import com.payup.resources.friends
import com.payup.resources.friends_empty
import com.payup.resources.rename
import com.payup.resources.save
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.payup.data.FriendEntity
import com.payup.data.GameRepository
import com.payup.ui.common.ConfirmDialog
import com.payup.ui.common.NameDialog
import com.payup.ui.common.PayUpTopBar
import com.payup.ui.common.payUpViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Composable
fun FriendsScreen(onBack: () -> Unit) {
    val vm = payUpViewModel { FriendsViewModel(it.repository) }
    val friends by vm.friends.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<FriendEntity?>(null) }
    var deleting by remember { mutableStateOf<FriendEntity?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { PayUpTopBar(stringResource(Res.string.friends), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.add_player))
            }
        },
    ) { padding ->
        val list = friends ?: return@Scaffold
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.friends_empty), textAlign = TextAlign.Center)
            }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(padding),
        ) {
            items(list, key = { it.id }) { friend ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
                        Text(friend.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { renaming = friend }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(Res.string.rename))
                        }
                        IconButton(onClick = { deleting = friend }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.delete))
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        NameDialog(
            title = stringResource(Res.string.add_player),
            confirmLabel = stringResource(Res.string.add),
            onConfirm = { vm.add(it); adding = false },
            onDismiss = { adding = false },
        )
    }
    renaming?.let { friend ->
        NameDialog(
            title = stringResource(Res.string.rename),
            initial = friend.name,
            confirmLabel = stringResource(Res.string.save),
            onConfirm = { newName ->
                renaming = null
                vm.rename(friend.id, newName) {
                    scope.launch { snackbar.showSnackbar(getString(Res.string.friend_exists, newName)) }
                }
            },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { friend ->
        ConfirmDialog(
            title = stringResource(Res.string.delete_friend_title, friend.name),
            message = stringResource(Res.string.delete_friend_message),
            confirmLabel = stringResource(Res.string.delete),
            onConfirm = { vm.delete(friend.id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}
