package com.payup.data

import com.payup.domain.BackupBuyIn
import com.payup.domain.BackupMerge
import com.payup.domain.GameKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupMappingTest {
    private fun game(dealerPlayerId: Long?) = GameWithDetails(
        game = GameEntity(
            id = 7, startedAt = 1_000, endedAt = 9_000, defaultBuyIn = 10000, finished = true,
            voidedDifference = -100, chipValue = 50, roundToWhole = false, dealerPlayerId = dealerPlayerId, currency = "EUR",
        ),
        unorderedPlayers = listOf(
            // Joined second and first on purpose: the backup must follow the table order, not the list order.
            PlayerWithBuyIns(
                PlayerEntity(id = 22, gameId = 7, name = "Gil", finalAmount = 0, net = -10000, chips = 0),
                listOf(BuyInEntity(id = 3, playerId = 22, amount = 10000, createdAt = 5)),
            ),
            PlayerWithBuyIns(
                PlayerEntity(id = 21, gameId = 7, name = "Dan", finalAmount = 25000, net = 10000, chips = 500),
                listOf(
                    BuyInEntity(id = 2, playerId = 21, amount = 5000, createdAt = 8),
                    BuyInEntity(id = 1, playerId = 21, amount = 10000, createdAt = 2),
                ),
            ),
        ),
        unorderedTransfers = listOf(TransferEntity(id = 1, gameId = 7, fromName = "Gil", toName = "Dan", amount = 10000)),
    )

    @Test fun playersFollowTableOrderAndKeepTheirNumbers() {
        val backup = game(dealerPlayerId = null).toBackup()
        assertEquals(listOf("Dan", "Gil"), backup.players.map { it.name })
        assertEquals(25000, backup.players[0].finalAmount)
        assertEquals(10000, backup.players[0].net)
        assertEquals(500, backup.players[0].chips)
    }

    @Test fun buyInsAreWrittenOldestFirst() {
        val dan = game(null).toBackup().players[0]
        assertEquals(listOf(BackupBuyIn(10000, 2), BackupBuyIn(5000, 8)), dan.buyIns)
    }

    @Test fun dealerBecomesAPositionNotAnId() {
        assertEquals(1, game(dealerPlayerId = 22).toBackup().dealerIndex)
        assertEquals(0, game(dealerPlayerId = 21).toBackup().dealerIndex)
    }

    @Test fun noDealerOrAVanishedDealerIsNull() {
        assertNull(game(dealerPlayerId = null).toBackup().dealerIndex)
        assertNull(game(dealerPlayerId = 999).toBackup().dealerIndex)
    }

    @Test fun gameSettingsAndTransfersSurvive() {
        val b = game(null).toBackup()
        assertEquals("EUR", b.currency)
        assertEquals(50, b.chipValue)
        assertEquals(false, b.roundToWhole)
        assertEquals(-100, b.voidedDifference)
        assertEquals(9_000L, b.endedAt)
        assertEquals(listOf("Gil" to "Dan"), b.transfers.map { it.from to it.to })
    }

    @Test fun entityKeyMatchesTheBackupKey() {
        val g = game(null)
        assertEquals(GameKey(1_000, listOf("Dan", "Gil")), g.key())
        assertEquals(g.key(), BackupMerge.keyOf(g.toBackup()))
    }

    @Test fun restoredGameBecomesAnEntityWithoutIds() {
        val e = game(22).toBackup().toEntity()
        assertEquals(0L, e.id)
        assertNull(e.dealerPlayerId)
        assertEquals("EUR", e.currency)
        assertEquals(true, e.finished)
    }
}
