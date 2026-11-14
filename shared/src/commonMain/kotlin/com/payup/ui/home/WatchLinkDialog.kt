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
import com.payup.ui.Route
import com.payup.ui.routeForLink
import org.jetbrains.compose.resources.stringResource

/** Asks for a pasted game link (a viewer link, a host link or a bare game id) and hands back the screen it opens. */
@Composable
fun WatchLinkDialog(onOpen: (Route) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val route = routeForLink(text, allowBareId = true)
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
            TextButton(onClick = { route?.let(onOpen) }, enabled = route != null) { Text(stringResource(Res.string.watch_start)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}
