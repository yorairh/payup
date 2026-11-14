package com.payup.data

import com.payup.domain.BackupBuyIn
import com.payup.domain.BackupGame
import com.payup.domain.BackupPlayer
import com.payup.domain.BackupTransfer
import com.payup.domain.GameKey

/** The game as written to a backup: players by position, so no database ids leak into the file. */
fun GameWithDetails.toBackup(): BackupGame {
    val dealerIndex = players.indexOfFirst { it.player.id == game.dealerPlayerId }.takeIf { it >= 0 }
    return BackupGame(
        startedAt = game.startedAt,
        endedAt = game.endedAt,
        defaultBuyIn = game.defaultBuyIn,
        finished = game.finished,
        voidedDifference = game.voidedDifference,
        chipValue = game.chipValue,
        chipsByShare = game.chipsByShare,
        roundToWhole = game.roundToWhole,
        dealerIndex = dealerIndex,
        currency = game.currency,
        players = players.map { p ->
            BackupPlayer(
                name = p.player.name,
                finalAmount = p.player.finalAmount,
                net = p.player.net,
                chips = p.player.chips,
                buyIns = p.buyIns.sortedWith(compareBy({ it.createdAt }, { it.id })).map { BackupBuyIn(it.amount, it.createdAt) },
            )
        },
        transfers = transfers.map { BackupTransfer(it.fromName, it.toName, it.amount) },
    )
}

fun GameWithDetails.key() = GameKey(game.startedAt, players.map { it.player.name }.sorted())

fun BackupGame.toEntity() = GameEntity(
    startedAt = startedAt,
    endedAt = endedAt,
    defaultBuyIn = defaultBuyIn,
    finished = finished,
    voidedDifference = voidedDifference,
    chipValue = chipValue,
    chipsByShare = chipsByShare,
    roundToWhole = roundToWhole,
    currency = currency,
)
