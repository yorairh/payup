package com.payup.ui.home

import com.payup.ui.Route
import com.payup.resources.Res
import com.payup.resources.app_name
import com.payup.resources.continue_game
import com.payup.resources.discard_active_message
import com.payup.resources.discard_active_title
import com.payup.resources.friends
import com.payup.resources.history
import com.payup.resources.leaderboard
import com.payup.resources.new_game
import com.payup.resources.settings
import com.payup.resources.tagline
import com.payup.resources.watch_a_game
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.payup.ui.common.PayUpTopBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.payup.LocalAppDependencies
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.payup.data.GameRepository
import com.payup.ui.common.ConfirmDialog
import com.payup.ui.common.payUpViewModel
import com.payup.ui.theme.Gold
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@Composable
fun HomeScreen(
    onNewGame: () -> Unit,
    onContinue: (Long) -> Unit,
    onHistory: () -> Unit,
    onLeaderboard: () -> Unit,
    onFriends: () -> Unit,
    onOpenLink: (Route) -> Unit,
    onSettings: () -> Unit,
) {
    val vm = payUpViewModel { HomeViewModel(it.repository) }
    val activeGameId by vm.activeGameId.collectAsStateWithLifecycle()
    var confirmDiscard by remember { mutableStateOf(false) }
    var watching by remember { mutableStateOf(false) }

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "scale",
    )

    Scaffold(
        topBar = {
            PayUpTopBar(
                title = "",
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(Res.string.settings))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("♠ ♥ ♣ ♦", fontSize = 28.sp, color = Gold)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(Res.string.app_name),
                fontSize = 64.sp,
                fontWeight = FontWeight.Black,
                color = Gold,
                modifier = Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse },
            )
            Text(stringResource(Res.string.tagline), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(48.dp))

            val buttonModifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
            activeGameId?.let { id ->
                Button(onClick = { onContinue(id) }, modifier = buttonModifier) {
                    Text(stringResource(Res.string.continue_game), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
            }
            if (activeGameId == null) {
                Button(onClick = onNewGame, modifier = buttonModifier) {
                    Text(stringResource(Res.string.new_game), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(onClick = { confirmDiscard = true }, modifier = buttonModifier) {
                    Text(stringResource(Res.string.new_game), fontSize = 18.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onHistory, modifier = buttonModifier) {
                Text(stringResource(Res.string.history), fontSize = 18.sp)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onLeaderboard, modifier = buttonModifier) {
                Text(stringResource(Res.string.leaderboard), fontSize = 18.sp)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onFriends, modifier = buttonModifier) {
                Text(stringResource(Res.string.friends), fontSize = 18.sp)
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { watching = true }) { Text(stringResource(Res.string.watch_a_game)) }
        }
        // The release name, tiny, in the bottom left corner. The corner is not mirrored in right-to-left languages.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(Modifier.fillMaxSize()) {
                Text(
                    "v" + LocalAppDependencies.current.appVersion,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 4.dp),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
        }
        }
    }

    if (watching) {
        WatchLinkDialog(
            onOpen = { watching = false; onOpenLink(it) },
            onDismiss = { watching = false },
        )
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(Res.string.discard_active_title),
            message = stringResource(Res.string.discard_active_message),
            confirmLabel = stringResource(Res.string.new_game),
            onConfirm = { confirmDiscard = false; onNewGame() },
            onDismiss = { confirmDiscard = false },
        )
    }
}
