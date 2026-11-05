package com.payup.ui.anim

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payup.domain.Outcome
import com.payup.ui.common.signedMoney
import com.payup.ui.theme.Cream
import com.payup.ui.theme.Felt
import com.payup.ui.theme.FeltLight
import com.payup.ui.theme.Gold
import com.payup.ui.theme.GoldDeep
import com.payup.ui.theme.LocalPayUp
import com.payup.ui.theme.LoseRed
import com.payup.ui.theme.OnGoldColor
import com.payup.ui.theme.WinGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** The "champion" card look shared by the result screen and the leaderboard. */
val GoldBrush: Brush @Composable get() = Brush.horizontalGradient(listOf(GoldDeep, Gold))
val OnGold: Color @Composable get() = OnGoldColor

@Composable
fun Modifier.goldGlow(shape: Shape): Modifier = shadow(18.dp, shape, ambientColor = Gold, spotColor = Gold)

/**
 * A player's result row.
 * Winners rise up from below with a bounce, losers sink in from above; the amount counts up.
 * When [animate] is false (history, or after "skip") the row is shown in its final state right away.
 */
@Composable
fun ResultRow(
    name: String,
    net: Int,
    outcome: Outcome,
    visible: Boolean,
    animate: Boolean,
    staggerIndex: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val startOffset = with(density) { if (outcome == Outcome.Loser) (-48).dp.toPx() else 96.dp.toPx() }
    val offset = remember { Animatable(if (animate) startOffset else 0f) }
    val alpha = remember { Animatable(if (animate) 0f else 1f) }
    val shown = remember { Animatable(if (animate) 0f else abs(net).toFloat()) }

    LaunchedEffect(visible, animate) {
        if (!visible) return@LaunchedEffect
        if (!animate) {
            offset.snapTo(0f); alpha.snapTo(1f); shown.snapTo(abs(net).toFloat())
            return@LaunchedEffect
        }
        delay(staggerIndex * 160L)
        launch { alpha.animateTo(1f, tween(250)) }
        launch {
            offset.animateTo(
                0f,
                spring(
                    dampingRatio = if (outcome == Outcome.Loser) Spring.DampingRatioLowBouncy else Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        }
        shown.animateTo(abs(net).toFloat(), tween(900, easing = FastOutSlowInEasing))
    }

    val shape = RoundedCornerShape(20.dp)
    val background = when (outcome) {
        Outcome.TopWinner -> GoldBrush
        Outcome.Winner -> Brush.horizontalGradient(listOf(FeltLight, Felt))
        Outcome.Even -> Brush.horizontalGradient(listOf(Felt, Felt))
        Outcome.Loser -> LocalPayUp.current.let { Brush.horizontalGradient(listOf(it.loserRowA, it.loserRowB)) }
    }
    val textColor = if (outcome == Outcome.TopWinner) OnGold else Cream
    val amountColor = when (outcome) {
        Outcome.TopWinner -> OnGold
        Outcome.Winner -> WinGreen
        Outcome.Even -> Cream
        Outcome.Loser -> LoseRed
    }
    val glow = if (outcome == Outcome.TopWinner) Modifier.goldGlow(shape) else Modifier

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = offset.value
                this.alpha = alpha.value
            }
            .then(glow)
            .clip(shape)
            .background(background, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = if (outcome == Outcome.TopWinner) 20.dp else 14.dp),
    ) {
        OutcomeEmoji(outcome)
        Spacer(Modifier.width(12.dp))
        Text(
            name,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = if (outcome == Outcome.TopWinner) 24.sp else 20.sp,
            modifier = Modifier.weight(1f),
        )
        val sign = if (net < 0) -1 else 1
        Text(
            signedMoney(sign * shown.value.toInt()),
            color = amountColor,
            fontWeight = FontWeight.Black,
            fontSize = if (outcome == Outcome.TopWinner) 26.sp else 20.sp,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun OutcomeEmoji(outcome: Outcome) {
    val transition = rememberInfiniteTransition(label = "emoji")
    val bounce by transition.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bounce",
    )
    val wobble by transition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "wobble",
    )
    val (emoji, motion) = when (outcome) {
        Outcome.TopWinner -> "👑" to Modifier.graphicsLayer { translationY = bounce * density }
        Outcome.Winner -> "🏆" to Modifier.graphicsLayer { translationY = bounce * density * 0.4f }
        Outcome.Even -> "🤝" to Modifier
        Outcome.Loser -> "🌧️" to Modifier.graphicsLayer { rotationZ = wobble }
    }
    Text(emoji, fontSize = if (outcome == Outcome.TopWinner) 34.sp else 26.sp, modifier = motion)
}
