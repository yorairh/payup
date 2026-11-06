package com.payup.ui.result

import com.payup.resources.Res
import com.payup.resources.nobody_pays
import com.payup.resources.payup
import com.payup.resources.players_count
import com.payup.resources.pot_value
import com.payup.resources.results
import com.payup.resources.share_chooser
import com.payup.resources.share_header
import com.payup.resources.who_pays_whom
import android.content.Context
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import com.payup.data.GameWithDetails
import com.payup.data.results
import com.payup.domain.Currency
import com.payup.ui.common.isRtl
import com.payup.ui.common.moneyText
import com.payup.ui.theme.PayUpPalette
import java.io.File
import java.text.DateFormat
import java.util.Date

internal fun GameWithDetails.dateText(): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(game.endedAt ?: game.startedAt))

private fun GameWithDetails.currency(): Currency = Currency.fromCode(game.currency)

private fun Context.signed(amount: Int, currency: Currency): String =
    moneyText(this, amount, currency, showPlus = true)

private suspend fun Context.header(game: GameWithDetails): String = getString(
    Res.string.share_header,
    game.dateText(),
    getString(Res.string.pot_value, moneyText(this, game.pot, game.currency())),
    getPluralString(Res.plurals.players_count, game.players.size, game.players.size),
)

suspend fun buildShareText(context: Context, game: GameWithDetails): String = buildString {
    appendLine(context.header(game))
    appendLine()
    appendLine(getString(Res.string.results))
    game.results().forEach { appendLine("${it.name} ${context.signed(it.net, game.currency())}") }
    appendLine()
    appendLine(getString(Res.string.who_pays_whom))
    if (game.transfers.isEmpty()) {
        appendLine(getString(Res.string.nobody_pays))
    } else {
        game.transfers.forEach { appendLine("${it.fromName} → ${it.toName} ${moneyText(context, it.amount, game.currency())}") }
    }
}.trimEnd()

suspend fun shareText(context: Context, game: GameWithDetails) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, buildShareText(context, game))
    context.startActivity(Intent.createChooser(send, getString(Res.string.share_chooser)))
}

suspend fun shareImage(context: Context, game: GameWithDetails, palette: PayUpPalette) {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, "payup-${game.game.id}.png")
    val bitmap = renderShareImage(context, game, palette)
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val chooser = Intent.createChooser(send, getString(Res.string.share_chooser))
    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(chooser)
}

/** Draws the results card straight onto a bitmap, so sharing never depends on what is on screen. */
private suspend fun renderShareImage(context: Context, game: GameWithDetails, p: PayUpPalette): Bitmap {
    val width = 1080
    val pad = 64f
    val rowHeight = 92f
    val rtl = context.isRtl()
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
        "${game.dateText()} · " + getString(Res.string.pot_value, moneyText(context, game.pot, game.currency())) +
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
        row(it.name, namePaint, context.signed(it.net, game.currency()), paint(48f, color, bold = true), y)
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
        row("${it.fromName} → ${it.toName}", namePaint, moneyText(context, it.amount, game.currency()), paint(48f, p.gold.toArgb(), bold = true), y)
    }
    return bitmap
}
