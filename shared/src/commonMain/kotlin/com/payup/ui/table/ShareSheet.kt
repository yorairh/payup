package com.payup.ui.table

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.payup.platform.LocalPlatform
import com.payup.resources.Res
import com.payup.resources.back
import com.payup.resources.copied_to_clipboard
import com.payup.resources.copy_link
import com.payup.resources.handover_button
import com.payup.resources.handover_message
import com.payup.resources.handover_title
import com.payup.resources.handover_warning
import com.payup.resources.share_link
import com.payup.resources.share_message
import com.payup.resources.share_scan
import com.payup.resources.share_title
import com.payup.sync.QrCode
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * The QR code and buttons for handing out the viewer [link] of a shared game. When [hostLink] is given, a smaller button
 * switches to the same panel for handing over hosting, which comes with a warning.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(link: String, hostLink: String?, onDismiss: () -> Unit) {
    var handingOver by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            if (handingOver && hostLink != null) {
                LinkPanel(
                    title = stringResource(Res.string.handover_title),
                    hint = stringResource(Res.string.handover_warning),
                    link = hostLink,
                    message = Res.string.handover_message,
                )
                TextButton(onClick = { handingOver = false }, modifier = Modifier.padding(bottom = 16.dp)) {
                    Text(stringResource(Res.string.back))
                }
            } else {
                LinkPanel(
                    title = stringResource(Res.string.share_title),
                    hint = stringResource(Res.string.share_scan),
                    link = link,
                    message = Res.string.share_message,
                )
                if (hostLink != null) {
                    TextButton(onClick = { handingOver = true }, modifier = Modifier.padding(bottom = 16.dp)) {
                        Text(stringResource(Res.string.handover_button))
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkPanel(title: String, hint: String, link: String, message: StringResource) {
    val platform = LocalPlatform.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var copied by remember(link) { mutableStateOf(false) }
    Text(title, style = MaterialTheme.typography.titleLarge)
    Text(hint, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
    QrImage(link, Modifier.size(240.dp))
    Text(link, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = {
            clipboard.setText(AnnotatedString(link))
            copied = true
        }) { Text(stringResource(if (copied) Res.string.copied_to_clipboard else Res.string.copy_link)) }
        Button(onClick = {
            scope.launch { platform.share.shareText(getString(message, link)) }
        }) { Text(stringResource(Res.string.share_link)) }
    }
}

/** Draws [text] as a QR code: black on white whatever the theme, with the blank border scanners need. */
@Composable
private fun QrImage(text: String, modifier: Modifier = Modifier) {
    val qr = remember(text) { QrCode.encode(text) }
    Canvas(modifier.background(Color.White, RoundedCornerShape(8.dp))) {
        val border = 4
        val cell = this.size.minDimension / (qr.size + 2 * border)
        for (y in 0 until qr.size) for (x in 0 until qr.size) {
            if (qr.isDark(x, y)) {
                // Slightly larger than a cell so no hairline gaps show between neighbours.
                drawRect(Color.Black, Offset((x + border) * cell, (y + border) * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}
