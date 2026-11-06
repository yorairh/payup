package com.payup.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.payup.R
import com.payup.data.GameRepository
import com.payup.ui.common.ConfirmDialog
import com.payup.ui.common.payUpViewModel
import com.payup.ui.theme.Gold
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(repository: GameRepository) : ViewModel() {
    val activeGameId = repository.activeGameId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun HomeScreen(
    onNewGame: () -> Unit,
    onContinue: (Long) -> Unit,
    onHistory: () -> Unit,
    onLeaderboard: () -> Unit,
    onFriends: () -> Unit,
    onSettings: () -> Unit,
) {
    val vm = payUpViewModel { repo, _ -> HomeViewModel(repo) }
    val activeGameId by vm.activeGameId.collectAsStateWithLifecycle()
    var confirmDiscard by remember { mutableStateOf(false) }

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
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("♠ ♥ ♣ ♦", fontSize = 28.sp, color = Gold)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.app_name),
                fontSize = 64.sp,
                fontWeight = FontWeight.Black,
                color = Gold,
                modifier = Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse },
            )
            Text(stringResource(R.string.tagline), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(48.dp))

            val buttonModifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
            activeGameId?.let { id ->
                Button(onClick = { onContinue(id) }, modifier = buttonModifier) {
                    Text(stringResource(R.string.continue_game), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
            }
            if (activeGameId == null) {
                Button(onClick = onNewGame, modifier = buttonModifier) {
                    Text(stringResource(R.string.new_game), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(onClick = { confirmDiscard = true }, modifier = buttonModifier) {
                    Text(stringResource(R.string.new_game), fontSize = 18.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onHistory, modifier = buttonModifier) {
                Text(stringResource(R.string.history), fontSize = 18.sp)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onLeaderboard, modifier = buttonModifier) {
                Text(stringResource(R.string.leaderboard), fontSize = 18.sp)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onFriends, modifier = buttonModifier) {
                Text(stringResource(R.string.friends), fontSize = 18.sp)
            }
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.discard_active_title),
            message = stringResource(R.string.discard_active_message),
            confirmLabel = stringResource(R.string.new_game),
            onConfirm = { confirmDiscard = false; onNewGame() },
            onDismiss = { confirmDiscard = false },
        )
    }
}
