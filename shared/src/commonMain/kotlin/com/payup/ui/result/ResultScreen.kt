package com.payup.ui.result

import com.payup.platform.LocalPlatform
import com.payup.ui.common.isRtl
import com.payup.resources.Res
import com.payup.resources.broke_even
import com.payup.resources.done
import com.payup.resources.losers
import com.payup.resources.nobody_pays
import com.payup.resources.pays
import com.payup.resources.payup
import com.payup.resources.pot_value
import com.payup.resources.results
import com.payup.resources.share
import com.payup.resources.share_image
import com.payup.resources.share_text
import com.payup.resources.skip
import com.payup.resources.voided_extra
import com.payup.resources.voided_missing
import com.payup.resources.who_pays_whom
import com.payup.resources.winners
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableStateOf
import com.payup.ui.common.PayUpTopBar
import com.payup.ui.common.ProvideCurrency
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.payup.data.GameWithDetails
import com.payup.data.GameRepository
import com.payup.data.TransferEntity
import com.payup.data.results
import com.payup.ui.anim.ConfettiBurst
import com.payup.ui.anim.ResultRow
import com.payup.ui.common.money
import com.payup.ui.common.payUpViewModel
import com.payup.ui.theme.Gold
import com.payup.ui.theme.LocalPayUp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlin.math.abs

private object Stage {
    const val START = 0
    const val TITLE = 1
    const val WINNERS = 2
    const val LOSERS = 3
    const val TRANSFERS = 4
}

@Composable
fun ResultScreen(gameId: Long, celebrate: Boolean, onDone: () -> Unit) {
    val vm = payUpViewModel { ResultViewModel(it.repository, gameId, celebrate) }
    val details by vm.game.collectAsStateWithLifecycle()
    val game = details ?: return
    ProvideCurrency(game.game.currency) { ResultContent(vm, game, onDone) }
}

@Composable
private fun ResultContent(vm: ResultViewModel, game: GameWithDetails, onDone: () -> Unit) {
    val platform = LocalPlatform.current
    val rtl = isRtl()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val palette = LocalPayUp.current
    var detailsOf by remember { mutableStateOf<String?>(null) }
    val results = remember(game) { game.results() }
    val winners = results.filter { it.net > 0 }
    val losers = results.filter { it.net < 0 }
    val even = results.filter { it.net == 0 }

    var stage by rememberSaveable { mutableIntStateOf(if (vm.celebrate) Stage.START else Stage.TRANSFERS) }
    val animate = vm.celebrate && stage < Stage.TRANSFERS
    var confetti by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        if (stage >= Stage.TRANSFERS) return@LaunchedEffect
        // maxOf so a "Skip" tap in the middle is never undone by a later step.
        stage = maxOf(stage, Stage.TITLE)
        delay(600)
        stage = maxOf(stage, Stage.WINNERS)
        if (winners.isNotEmpty()) confetti++
        delay(winners.size * 160L + 1100)
        stage = maxOf(stage, Stage.LOSERS)
        delay(losers.size * 160L + 1000)
        stage = Stage.TRANSFERS
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (stage >= Stage.TRANSFERS) {
                    PayUpTopBar(
                        title = "",
                        actions = {
                            var menuOpen by remember { mutableStateOf(false) }
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.Share, contentDescription = stringResource(Res.string.share))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.share_text)) },
                                    onClick = { menuOpen = false; scope.launch { platform.share.shareText(buildShareText(game, rtl, platform.dates)) } },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.share_image)) },
                                    onClick = { menuOpen = false; scope.launch { platform.share.shareResultImage(game, palette, rtl) } },
                                )
                            }
                        },
                    )
                }
            },
            bottomBar = {
                Button(
                    onClick = onDone,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .fillMaxWidth()
                        .height(56.dp),
                ) { Text(stringResource(Res.string.done), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            },
        ) { padding ->
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(padding),
            ) {
                item { Title(game, celebrate = vm.celebrate, show = stage >= Stage.TITLE) }

                if (winners.isNotEmpty()) item { SectionLabel(stringResource(Res.string.winners)) }
                winners.forEachIndexed { i, r ->
                    item(key = "w-${r.name}") {
                        ResultRow(r.name, r.net, r.outcome, stage >= Stage.WINNERS, animate, i, onClick = { detailsOf = r.name })
                    }
                }
                if (losers.isNotEmpty()) item { SectionLabel(stringResource(Res.string.losers)) }
                losers.forEachIndexed { i, r ->
                    item(key = "l-${r.name}") {
                        ResultRow(r.name, r.net, r.outcome, stage >= Stage.LOSERS, animate, i, onClick = { detailsOf = r.name })
                    }
                }
                if (even.isNotEmpty()) item { SectionLabel(stringResource(Res.string.broke_even)) }
                even.forEachIndexed { i, r ->
                    item(key = "e-${r.name}") {
                        ResultRow(r.name, r.net, r.outcome, stage >= Stage.LOSERS, animate, i, onClick = { detailsOf = r.name })
                    }
                }

                item {
                    AnimatedVisibility(
                        visible = stage >= Stage.TRANSFERS,
                        enter = fadeIn() + slideInVertically { it / 3 },
                    ) {
                        TransfersSection(game.transfers, game.game.voidedDifference)
                    }
                }
            }
        }

        ConfettiBurst(trigger = confetti)

        detailsOf?.let { PlayerDetailsSheet(game, it, onDismiss = { detailsOf = null }) }

        if (stage < Stage.TRANSFERS) {
            TextButton(
                onClick = { stage = Stage.TRANSFERS },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp),
            ) { Text(stringResource(Res.string.skip)) }
        }
    }
}

@Composable
private fun Title(game: GameWithDetails, celebrate: Boolean, show: Boolean) {
    val scale = remember { Animatable(if (celebrate) 0f else 1f) }
    LaunchedEffect(show) {
        if (show) scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow))
    }
    val dates = LocalPlatform.current.dates
    val date = remember(game.game.id) { game.dateText(dates) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 8.dp),
    ) {
        Text(
            if (celebrate) stringResource(Res.string.payup) else stringResource(Res.string.results),
            fontSize = 52.sp,
            fontWeight = FontWeight.Black,
            color = Gold,
            modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        )
        Text(
            "$date · " + stringResource(Res.string.pot_value, money(game.pot)),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun TransfersSection(transfers: List<TransferEntity>, voidedDifference: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
        Text(stringResource(Res.string.who_pays_whom), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (transfers.isEmpty()) {
            Text(stringResource(Res.string.nobody_pays), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
        transfers.forEach { TransferRow(it) }
        if (voidedDifference != 0) {
            val amount = money(abs(voidedDifference))
            Text(
                if (voidedDifference < 0) stringResource(Res.string.voided_missing, amount)
                else stringResource(Res.string.voided_extra, amount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TransferRow(transfer: TransferEntity) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Text(transfer.fromName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(Res.string.pays),
                tint = Gold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                transfer.toName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(money(transfer.amount), color = Gold, fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
    }
}
