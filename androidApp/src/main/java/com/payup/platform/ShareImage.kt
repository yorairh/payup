package com.payup.platform

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import com.payup.data.GameWithDetails
import com.payup.data.results
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
import com.payup.ui.result.transferText
import com.payup.ui.theme.PayUpPalette
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/** Draws the results card straight onto a bitmap, so sharing never depends on what is on screen. */
internal suspend fun renderShareImage(game: GameWithDetails, p: PayUpPalette, rtl: Boolean, dates: DateFormatter): Bitmap {
    val width = 1080
    val pad = 64f
    val rowHeight = 92f
    val results = game.results()
    val transfers = game.transfers

    fun paint(sizePx: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sizePx
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    val titlePaint = paint(120f, p.gold.toArgb(), bold = true)
    val subPaint = paint(40f, p.cream.toArgb())
    val sectionPaint = paint(46f, p.cream.toArgb(), bold = true)
    val namePaint = paint(48f, p.cream.toArgb(), bold = true)

    val rows = results.size + maxOf(transfers.size, 1)
    val height = (pad + 150f + 70f + 2 * 90f + rows * rowHeight + pad + 40f).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(p.feltDark.toArgb())
    canvas.drawRoundRect(24f, 24f, width - 24f, height - 24f, 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = p.feltLight.toArgb()
    })

    val startX = if (rtl) width - pad else pad
    val endX = if (rtl) pad else width - pad
    val startAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
    val endAlign = if (rtl) Paint.Align.LEFT else Paint.Align.RIGHT

    fun centered(text: String, p: TextPaint, y: Float) {
        p.textAlign = Paint.Align.CENTER
        canvas.drawText(text, width / 2f, y, p)
    }

    /** One line: [left] at the start edge (cut with "…" if long), [right] at the end edge. */
    fun row(left: String, leftPaint: TextPaint, right: String, rightPaint: TextPaint, y: Float) {
        rightPaint.textAlign = endAlign
        canvas.drawText(right, endX, y, rightPaint)
        val room = width - 2 * pad - rightPaint.measureText(right) - 32f
        leftPaint.textAlign = startAlign
        canvas.drawText(TextUtils.ellipsize(left, leftPaint, room, TextUtils.TruncateAt.END).toString(), startX, y, leftPaint)
    }

    var y = pad + 110f
    centered(getString(Res.string.payup), titlePaint, y)
    y += 70f
    centered(
        "${game.dateText(dates)} · " + getString(Res.string.pot_value, moneyText(game.pot, game.currency(), rtl)) +
            " · " + getPluralString(Res.plurals.players_count, game.players.size, game.players.size),
        subPaint,
        y,
    )

    y += 100f
    row(getString(Res.string.results), sectionPaint, "", sectionPaint, y)
    y += 20f
    results.forEach {
        y += rowHeight
        val color = when {
            it.net > 0 -> p.win.toArgb()
            it.net < 0 -> p.lose.toArgb()
            else -> p.cream.toArgb()
        }
        row(it.name, namePaint, moneyText(it.net, game.currency(), rtl, showPlus = true), paint(48f, color, bold = true), y)
    }

    y += 110f
    row(getString(Res.string.who_pays_whom), sectionPaint, "", sectionPaint, y)
    y += 20f
    if (transfers.isEmpty()) {
        y += rowHeight
        row(getString(Res.string.nobody_pays), subPaint, "", subPaint, y)
    }
    transfers.forEach {
        y += rowHeight
        row(transferText(it.fromName, it.toName, rtl), namePaint, moneyText(it.amount, game.currency(), rtl), paint(48f, p.gold.toArgb(), bold = true), y)
    }
    return bitmap
}
