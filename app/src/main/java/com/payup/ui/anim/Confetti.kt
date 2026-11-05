package com.payup.ui.anim

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.random.Random

private const val DURATION_SECONDS = 3.5f

private val ConfettiColors = listOf(
    Color(0xFFF2C14E), Color(0xFFFFE08A), Color(0xFF6BEB98), Color(0xFFFF7A7A),
    Color(0xFF7AB8FF), Color(0xFFFF9800), Color(0xFFE58CFF),
)

/** One piece of paper. Positions and speeds are fractions of the screen so it looks the same on any size. */
private class Piece(random: Random) {
    val startX = 0.35f + random.nextFloat() * 0.3f
    val startY = 0.25f + random.nextFloat() * 0.1f
    val vx = (random.nextFloat() - 0.5f) * 0.9f
    val vy = -(0.5f + random.nextFloat() * 0.7f)
    val delay = random.nextFloat() * 0.25f
    val sizeDp = 6f + random.nextFloat() * 8f
    val spin = (random.nextFloat() - 0.5f) * 1440f
    val color = ConfettiColors[random.nextInt(ConfettiColors.size)]
}

/**
 * A burst of confetti from the upper middle of the screen that falls with gravity and fades out.
 * Each new [trigger] value (above 0) fires a fresh burst.
 */
@Composable
fun ConfettiBurst(trigger: Int, modifier: Modifier = Modifier) {
    if (trigger <= 0) return
    val pieces = remember(trigger) { Random(trigger * 7919).let { r -> List(160) { Piece(r) } } }
    val time = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) {
        time.animateTo(DURATION_SECONDS, tween((DURATION_SECONDS * 1000).toInt(), easing = LinearEasing))
    }

    Canvas(modifier.fillMaxSize()) {
        val t = time.value
        if (t >= DURATION_SECONDS) return@Canvas
        val gravity = 1.1f
        for (piece in pieces) {
            val local = t - piece.delay
            if (local <= 0f) continue
            val x = size.width * (piece.startX + piece.vx * local)
            val y = size.height * (piece.startY + piece.vy * local + gravity * local * local / 2f)
            if (y > size.height + 40f) continue
            val alpha = (1f - (t / DURATION_SECONDS)).coerceIn(0f, 1f).let { if (it > 0.3f) 1f else it / 0.3f }
            val w = piece.sizeDp.dp.toPx()
            rotate(degrees = piece.spin * local, pivot = Offset(x, y)) {
                drawRect(
                    color = piece.color.copy(alpha = alpha),
                    topLeft = Offset(x - w / 2f, y - w / 4f),
                    size = Size(w, w / 2f),
                )
            }
        }
    }
}
