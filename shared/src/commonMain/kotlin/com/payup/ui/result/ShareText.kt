package com.payup.ui.result

import com.payup.data.GameWithDetails
import com.payup.data.results
import com.payup.domain.Currency
import com.payup.platform.DateFormatter
import com.payup.resources.Res
import com.payup.resources.nobody_pays
import com.payup.resources.players_count
import com.payup.resources.pot_value
import com.payup.resources.results
import com.payup.resources.share_header
import com.payup.resources.who_pays_whom
import com.payup.ui.common.moneyText
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

fun GameWithDetails.dateText(dates: DateFormatter): String = dates.date(game.endedAt ?: game.startedAt)

fun GameWithDetails.currency(): Currency = Currency.fromCode(game.currency)

/** The line under the title: date, pot and number of players. Shared by the share text and the share image. */
suspend fun shareHeader(game: GameWithDetails, rtl: Boolean, dates: DateFormatter): String = getString(
    Res.string.share_header,
    game.dateText(dates),
    getString(Res.string.pot_value, moneyText(game.pot, game.currency(), rtl)),
    getPluralString(Res.plurals.players_count, game.players.size, game.players.size),
)

/**
 * "payer → receiver" for plain text. In a right-to-left line the payer sits on the right, so the arrow must point left,
 * toward the receiver (the arrow character itself is never mirrored). Each name is isolated, so Hebrew names inside an
 * English line (or the reverse) cannot pull the arrow into their own direction, and the line's direction follows the
 * app language.
 */
fun transferText(from: String, to: String, rtl: Boolean): String {
    val names = "⁨$from⁩ ${if (rtl) '←' else '→'} ⁨$to⁩"
    return if (rtl) "⁧$names⁩" else "⁦$names⁩"
}

suspend fun buildShareText(game: GameWithDetails, rtl: Boolean, dates: DateFormatter): String = buildString {
    appendLine(shareHeader(game, rtl, dates))
    appendLine()
    appendLine(getString(Res.string.results))
    game.results().forEach { appendLine("${it.name} ${moneyText(it.net, game.currency(), rtl, showPlus = true)}") }
    appendLine()
    appendLine(getString(Res.string.who_pays_whom))
    if (game.transfers.isEmpty()) {
        appendLine(getString(Res.string.nobody_pays))
    } else {
        game.transfers.forEach { appendLine("${transferText(it.fromName, it.toName, rtl)} ${moneyText(it.amount, game.currency(), rtl)}") }
    }
}.trimEnd()
