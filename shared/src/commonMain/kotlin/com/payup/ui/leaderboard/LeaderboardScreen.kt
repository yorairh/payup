package com.payup.ui.leaderboard

import com.payup.resources.Res
import com.payup.resources.leaderboard
import com.payup.resources.leaderboard_empty
import com.payup.resources.metric_avg
import com.payup.resources.metric_best
import com.payup.resources.metric_profit
import com.payup.resources.metric_win_rate
import com.payup.resources.percent
import com.payup.resources.period_all
import com.payup.resources.period_month
import com.payup.resources.period_year
import com.payup.resources.stat_avg
import com.payup.resources.stat_best
import com.payup.resources.stat_buy_ins
import com.payup.resources.stat_games
import com.payup.resources.stat_profit
import com.payup.resources.stat_win_rate
import com.payup.resources.stat_worst
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.payup.data.GameRepository
import com.payup.domain.Leaderboard
import com.payup.domain.LeaderboardMetric
import com.payup.domain.Period
import com.payup.domain.PlayerStats
import com.payup.ui.anim.GoldBrush
import com.payup.ui.anim.OnGold
import com.payup.ui.anim.goldGlow
import com.payup.ui.common.PayUpTopBar
import com.payup.ui.common.money
import com.payup.ui.common.payUpViewModel
import com.payup.ui.common.signedMoney
import com.payup.ui.theme.Cream
import com.payup.ui.theme.Felt
import com.payup.ui.theme.LoseRed
import com.payup.ui.theme.WinGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.math.roundToInt

@Composable
fun LeaderboardScreen(onBack: () -> Unit) {
    val vm = payUpViewModel { LeaderboardViewModel(it.repository) }
    val stats by vm.stats.collectAsStateWithLifecycle()
    val metric by vm.metric.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()

    // Cards slide in only on the first load; later changes just reorder them.
    var firstLoad by remember { mutableStateOf(true) }
    LaunchedEffect(stats != null) {
        if (stats != null) {
            delay(1_000)
            firstLoad = false
        }
    }

    Scaffold(topBar = { PayUpTopBar(stringResource(Res.string.leaderboard), onBack) }) { padding ->
        Column(Modifier.padding(padding)) {
            ChipRow {
                Period.entries.forEach { p ->
                    Chip(selected = p == period, label = periodLabel(p)) { vm.period.value = p }
                }
            }
            ChipRow {
                LeaderboardMetric.entries.forEach { m ->
                    Chip(selected = m == metric, label = metricLabel(m)) { vm.metric.value = m }
                }
            }

            val list = stats ?: return@Column
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(Res.string.leaderboard_empty), textAlign = TextAlign.Center)
                }
                return@Column
            }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(list, key = { _, s -> s.name }) { index, player ->
                    LeaderCard(
                        rank = index + 1,
                        stats = player,
                        metric = metric,
                        slideIn = firstLoad,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 2.dp),
    ) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Chip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun periodLabel(period: Period): String = stringResource(
    when (period) {
        Period.MONTH -> Res.string.period_month
        Period.YEAR -> Res.string.period_year
        Period.ALL -> Res.string.period_all
    }
)

@Composable
private fun metricLabel(metric: LeaderboardMetric): String = stringResource(
    when (metric) {
        LeaderboardMetric.PROFIT -> Res.string.metric_profit
        LeaderboardMetric.WIN_RATE -> Res.string.metric_win_rate
        LeaderboardMetric.AVG_PER_GAME -> Res.string.metric_avg
        LeaderboardMetric.BIGGEST_WIN -> Res.string.metric_best
    }
)

@Composable
private fun percent(rate: Float): String = stringResource(Res.string.percent, (rate * 100).roundToInt())

@Composable
private fun LeaderCard(
    rank: Int,
    stats: PlayerStats,
    metric: LeaderboardMetric,
    slideIn: Boolean,
    modifier: Modifier = Modifier,
) {
    val enter = remember { Animatable(if (slideIn) 0f else 1f) }
    LaunchedEffect(Unit) {
        delay((rank - 1).coerceAtMost(10) * 70L)
        enter.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    val slide = with(LocalDensity.current) { 60.dp.toPx() }

    val podium = rank <= 3
    val shape = RoundedCornerShape(20.dp)
    val textColor = if (podium) OnGold else Cream
    val background = if (podium) GoldBrush else Brush.horizontalGradient(listOf(Felt, Felt))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = (1f - enter.value) * slide
                alpha = enter.value.coerceIn(0f, 1f)
            }
            .then(if (rank == 1) Modifier.goldGlow(shape) else Modifier)
            .background(background, shape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (rank) {
                    1 -> "🥇"
                    2 -> "🥈"
                    3 -> "🥉"
                    else -> "#$rank"
                },
                fontSize = if (podium) 30.sp else 18.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                stats.name,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                modifier = Modifier.weight(1f),
            )
            val (value, amount) = when (metric) {
                LeaderboardMetric.PROFIT -> signedMoney(stats.profit) to stats.profit
                LeaderboardMetric.WIN_RATE -> percent(stats.winRate) to null
                LeaderboardMetric.AVG_PER_GAME -> signedMoney(stats.avgPerGame) to stats.avgPerGame
                LeaderboardMetric.BIGGEST_WIN -> signedMoney(stats.bestGame) to stats.bestGame
            }
            Text(
                value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = if (podium) OnGold else amountColor(amount),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row {
            StatCell(stringResource(Res.string.stat_profit), signedMoney(stats.profit), textColor)
            StatCell(stringResource(Res.string.stat_games), stats.games.toString(), textColor)
            StatCell(stringResource(Res.string.stat_win_rate), percent(stats.winRate), textColor)
            StatCell(stringResource(Res.string.stat_avg), signedMoney(stats.avgPerGame), textColor)
        }
        Spacer(Modifier.height(8.dp))
        Row {
            StatCell(stringResource(Res.string.stat_best), signedMoney(stats.bestGame), textColor)
            StatCell(stringResource(Res.string.stat_worst), signedMoney(stats.worstGame), textColor)
            StatCell(stringResource(Res.string.stat_buy_ins), money(stats.totalIn), textColor)
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun amountColor(amount: Int?): Color = when {
    amount == null -> Cream
    amount > 0 -> WinGreen
    amount < 0 -> LoseRed
    else -> Cream
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StatCell(label: String, value: String, color: Color) {
    Column(Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.75f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
    }
}
