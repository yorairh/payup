package com.payup.ui.deal

import com.payup.resources.Res
import com.payup.resources.dealer_is
import com.payup.resources.dealing_hint
import com.payup.resources.dealing_title
import com.payup.resources.skip
import com.payup.resources.to_the_table
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.payup.data.GameRepository
import com.payup.domain.Dealer
import com.payup.domain.DealtCard
import com.payup.domain.PlayingCard
import com.payup.ui.anim.ConfettiBurst
import com.payup.ui.common.payUpViewModel
import com.payup.ui.theme.Gold
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class DealState(
    val names: List<String>,
    /** The cards in dealing order; empty when the dealer was already picked earlier (app restarted). */
    val dealt: List<DealtCard>,
    val dealerIndex: Int,
)

class DealViewModel(private val repository: GameRepository, handle: SavedStateHandle) : ViewModel() {
    private val gameId: Long = checkNotNull(handle["gameId"])
    val state = MutableStateFlow<DealState?>(null)

    init {
        viewModelScope.launch {
            val details = repository.game(gameId).filterNotNull().first()
            val players = details.players
            val names = players.map { it.player.name }
            val existing = players.indexOfFirst { it.player.id == details.game.dealerPlayerId }
            if (existing >= 0) {
                state.value = DealState(names, emptyList(), existing)
            } else {
                val dealt = Dealer.deal(players.size)
                val dealerIndex = dealt.last().playerIndex
                repository.setDealer(gameId, players[dealerIndex].player.id)
                state.value = DealState(names, dealt, dealerIndex)
            }
        }
    }
}

@Composable
fun DealScreen(onContinue: () -> Unit) {
    val vm = payUpViewModel { repo, handle -> DealViewModel(repo, handle) }
    val state by vm.state.collectAsStateWithLifecycle()
    val deal = state ?: return

    var revealed by remember { mutableIntStateOf(0) }
    var confetti by remember { mutableIntStateOf(0) }
    val finished = revealed >= deal.dealt.size

    LaunchedEffect(deal) {
        while (revealed < deal.dealt.size) {
            delay(if (revealed == 0) 500 else 700)
            revealed++
        }
        confetti++
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                AnimatedVisibility(visible = finished) {
                    Button(
                        onClick = onContinue,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(16.dp)
                            .fillMaxWidth()
                            .height(56.dp),
                    ) { Text(stringResource(Res.string.to_the_table), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                }
            },
        ) { padding ->
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    stringResource(Res.string.dealing_title),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = Gold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                )
                Text(
                    if (finished) stringResource(Res.string.dealer_is, deal.names[deal.dealerIndex]) else stringResource(Res.string.dealing_hint),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                deal.names.forEachIndexed { index, name ->
                    // Cards this player has been dealt so far.
                    val cards = deal.dealt.take(revealed).filter { it.playerIndex == index }.map { it.card }
                    val isDealer = finished && index == deal.dealerIndex
                    SeatRow(name, cards, isDealer, showAlreadyDealt = deal.dealt.isEmpty() && index == deal.dealerIndex)
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        ConfettiBurst(trigger = confetti)

        if (!finished) {
            TextButton(
                onClick = { revealed = deal.dealt.size },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp),
            ) { Text(stringResource(Res.string.skip)) }
        }
    }
}

@Composable
private fun SeatRow(name: String, cards: List<PlayingCard>, isDealer: Boolean, showAlreadyDealt: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isDealer) Gold.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = if (isDealer) BorderStroke(2.dp, Gold) else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp).fillMaxWidth()) {
            Text(
                name + if (isDealer || showAlreadyDealt) "  D" else "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isDealer || showAlreadyDealt) Gold else Color.Unspecified,
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.height(66.dp)) {
                cards.forEach { CardFace(it) }
            }
        }
    }
}

private val RedSuit = Color(0xFFD32F2F)
private val BlackSuit = Color(0xFF212121)

@Composable
private fun CardFace(card: PlayingCard) {
    val scale = remember { Animatable(0.2f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
    val rank = when (card.rank) {
        1 -> "A"
        11 -> "J"
        12 -> "Q"
        13 -> "K"
        else -> card.rank.toString()
    }
    val suit = listOf("♠", "♥", "♣", "♦")[card.suit]
    val color = if (card.suit == 1 || card.suit == 3) RedSuit else BlackSuit
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .size(width = 46.dp, height = 64.dp)
            .background(Color(0xFFFAF7EE), RoundedCornerShape(8.dp))
            .padding(2.dp),
    ) {
        Text(rank, color = color, fontWeight = FontWeight.Black, fontSize = 20.sp)
        Text(suit, color = color, fontSize = 20.sp)
    }
}
