package com.payup.ui.home

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.payup.resources.Res
import com.payup.resources.cancel
import com.payup.resources.watch_a_game
import com.payup.resources.watch_link_label
import com.payup.resources.watch_start
import com.payup.sync.ShareLinks
import org.jetbrains.compose.resources.stringResource

/** Asks for a pasted game link (the phone has no links that open the app yet) and hands back the game id in it. */
@Composable
fun WatchLinkDialog(onWatch: (String) -> Unit, onTakeOver: (String, String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val hostLink = ShareLinks.parseHostLink(text)
    val id = ShareLinks.parseViewerId(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.watch_a_game)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(300) },
                label = { Text(stringResource(Res.string.watch_link_label)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (hostLink != null) onTakeOver(hostLink.gameId, hostLink.hostKey) else id?.let(onWatch) },
                enabled = hostLink != null || id != null,
            ) { Text(stringResource(Res.string.watch_start)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}
