import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.payup.data.GameWithDetails
import com.payup.data.results
import com.payup.platform.DateFormatter
import com.payup.resources.Res
import com.payup.resources.nobody_pays
import com.payup.resources.payup
import com.payup.resources.players_count
import com.payup.resources.pot_value
import com.payup.resources.results
import com.payup.resources.who_pays_whom
import com.payup.ui.common.moneyText
import com.payup.ui.result.currency
import com.payup.ui.result.dateText
import com.payup.ui.theme.PayUpPalette
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import kotlin.math.max

/**
 * Draws the results card for [game] and returns it as PNG bytes. It is the same layout as the Android share picture
 * (androidApp/.../ShareImage.kt), drawn with Compose text so it uses the bundled fonts and handles right-to-left.
 */
suspend fun renderSharePng(
    game: GameWithDetails,
    p: PayUpPalette,
    rtl: Boolean,
    dates: DateFormatter,
    fonts: FontFamily.Resolver,
): ByteArray {
    val width = 1080
    val pad = 64f
    val rowHeight = 92f
    val results = game.results()
    val transfers = game.transfers
    val direction = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    val density = Density(1f)

    val rows = results.size + max(transfers.size, 1)
    val height = (pad + 150f + 70f + 2 * 90f + rows * rowHeight + pad + 40f).toInt()

    // All the text is looked up first, because the drawing itself cannot suspend.
    val currency = game.currency()
    val title = getString(Res.string.payup)
    val subtitle = "${game.dateText(dates)} · " + getString(Res.string.pot_value, moneyText(game.pot, currency, rtl)) +
        " · " + getPluralString(Res.plurals.players_count, game.players.size, game.players.size)
    val resultsTitle = getString(Res.string.results)
    val whoPays = getString(Res.string.who_pays_whom)
    val nobodyPays = getString(Res.string.nobody_pays)

    val measurer = TextMeasurer(fonts, density, direction)
    val bitmap = ImageBitmap(width, height)
    CanvasDrawScope().draw(density, direction, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
        drawRect(p.feltDark)
        drawRoundRect(
            p.feltLight,
            topLeft = Offset(24f, 24f),
            size = Size(width - 48f, height - 48f),
            cornerRadius = CornerRadius(40f),
            style = Stroke(4f),
        )

        fun DrawScope.text(text: String, size: Float, color: Color, bold: Boolean, maxWidth: Float) =
            measurer.measure(
                text = text,
                // The direction comes from each text's first letter ("PAYUP!" stays left to right in Hebrew), like Android's canvas.
                style = TextStyle(
                    fontSize = size.sp,
                    color = color,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                    textDirection = TextDirection.Content,
                ),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                constraints = Constraints(maxWidth = maxWidth.toInt().coerceAtLeast(1)),
                layoutDirection = direction,
                density = density,
            )

        /** Draws a line whose baseline is at [baseline], its left edge at [x]. */
        fun DrawScope.draw(layout: androidx.compose.ui.text.TextLayoutResult, x: Float, baseline: Float) =
            drawText(layout, topLeft = Offset(x, baseline - layout.firstBaseline))

        fun DrawScope.centered(text: String, size: Float, color: Color, bold: Boolean, baseline: Float) {
            val layout = text(text, size, color, bold, width - 2 * pad)
            draw(layout, (width - layout.size.width) / 2f, baseline)
        }

        /** One line: [left] at the start edge (cut with an ellipsis when long), [right] at the end edge. */
        fun DrawScope.row(left: String, leftSize: Float, leftColor: Color, leftBold: Boolean, right: String, rightColor: Color, baseline: Float) {
            val rightLayout = text(right, 48f, rightColor, true, width - 2 * pad)
            val rightWidth = if (right.isEmpty()) 0f else rightLayout.size.width.toFloat()
            val room = width - 2 * pad - rightWidth - 32f
            val leftLayout = text(left, leftSize, leftColor, leftBold, room)
            val leftX = if (rtl) width - pad - leftLayout.size.width else pad
            val rightX = if (rtl) pad else width - pad - rightWidth
            draw(leftLayout, leftX, baseline)
            if (right.isNotEmpty()) draw(rightLayout, rightX, baseline)
        }

        var y = pad + 110f
        centered(title, 120f, p.gold, true, y)
        y += 70f
        centered(subtitle, 40f, p.cream, false, y)

        y += 100f
        row(resultsTitle, 46f, p.cream, true, "", p.cream, y)
        y += 20f
        results.forEach {
            y += rowHeight
            val color = when {
                it.net > 0 -> p.win
                it.net < 0 -> p.lose
                else -> p.cream
            }
            row(it.name, 48f, p.cream, true, moneyText(it.net, currency, rtl, showPlus = true), color, y)
        }

        y += 110f
        row(whoPays, 46f, p.cream, true, "", p.cream, y)
        y += 20f
        if (transfers.isEmpty()) {
            y += rowHeight
            row(nobodyPays, 40f, p.cream, false, "", p.cream, y)
        }
        transfers.forEach {
            y += rowHeight
            // "from → to" at the start edge and the amount at the end edge. The arrow is drawn as lines, because the
            // bundled fonts have no arrow glyph, and it points toward the end edge in either direction.
            val amount = text(moneyText(it.amount, currency, rtl), 48f, p.gold, true, width - 2 * pad)
            val arrowWidth = 64f
            val nameRoom = (width - 2 * pad - amount.size.width - 32f - arrowWidth) / 2f
            val from = text(it.fromName, 48f, p.cream, true, nameRoom)
            val to = text(it.toName, 48f, p.cream, true, nameRoom)
            /** The left edge of an item that starts [offset] from the start edge and is [w] wide. */
            fun place(offset: Float, w: Float) = if (rtl) width - pad - offset - w else pad + offset
            draw(from, place(0f, from.size.width.toFloat()), y)
            val arrowStart = from.size.width + 16f
            val ay = y - 17f
            val tail = place(arrowStart, 0f)
            val tip = place(arrowStart + arrowWidth - 16f, 0f)
            val back = if (rtl) 14f else -14f
            drawLine(p.gold, Offset(tail, ay), Offset(tip, ay), strokeWidth = 5f)
            drawLine(p.gold, Offset(tip, ay), Offset(tip + back, ay - 14f), strokeWidth = 5f)
            drawLine(p.gold, Offset(tip, ay), Offset(tip + back, ay + 14f), strokeWidth = 5f)
            draw(to, place(arrowStart + arrowWidth, to.size.width.toFloat()), y)
            draw(amount, if (rtl) pad else width - pad - amount.size.width, y)
        }
    }
    val encoded = Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)
    return checkNotNull(encoded) { "Could not encode the share picture" }.bytes
}
