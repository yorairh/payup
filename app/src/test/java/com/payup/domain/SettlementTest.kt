package com.payup.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SettlementTest {

    /** Applies the transfers and checks everyone ends at zero. */
    private fun assertSettles(nets: List<Int>, transfers: List<Transfer>) {
        val balance = nets.toMutableList()
        transfers.forEach {
            assertTrue("amount must be positive: $it", it.amount > 0)
            balance[it.from] += it.amount
            balance[it.to] -= it.amount
        }
        assertTrue("not settled: $balance", balance.all { it == 0 })
    }

    @Test
    fun twoPlayers() {
        val nets = listOf(-50, 50)
        val transfers = Settlement.settle(nets)
        assertEquals(listOf(Transfer(0, 1, 50)), transfers)
    }

    @Test
    fun everyoneBreaksEven() {
        assertTrue(Settlement.settle(listOf(0, 0, 0)).isEmpty())
    }

    @Test
    fun exactMatchesPayOnlyOnePerson() {
        // Zero-sum groups {-100,+100} and {-70,-30,+80,+20}: 1 + 3 = 4 transfers.
        val nets = listOf(-100, -70, -30, 100, 80, 20)
        val transfers = Settlement.settle(nets)
        assertSettles(nets, transfers)
        assertEquals(4, transfers.size)
        assertTrue(transfers.contains(Transfer(0, 3, 100)))
    }

    @Test
    fun beatsGreedyWhereGreedyNeedsMoreTransfers() {
        // Largest-to-largest greedy needs 4 transfers here; splitting into {-7,+7} and {-5,-3,+8} needs 3.
        val nets = listOf(-7, -5, -3, 8, 7)
        val transfers = Settlement.settle(nets)
        assertSettles(nets, transfers)
        assertEquals(3, transfers.size)
        assertTrue(transfers.contains(Transfer(0, 4, 7)))
    }

    @Test
    fun manyPlayersStillSettlesWithAtMostNMinusOne() {
        val random = Random(42)
        repeat(50) {
            val n = random.nextInt(2, 15)
            val nets = MutableList(n - 1) { random.nextInt(-10, 11) * 50 }
            nets += -nets.sum()
            val transfers = Settlement.settle(nets)
            assertSettles(nets, transfers)
            assertTrue(transfers.size <= nets.count { it != 0 }.coerceAtLeast(1) - 1)
        }
    }

    @Test
    fun hugeTableUsesFallbackAndStillSettles() {
        val nets = MutableList(30) { if (it % 2 == 0) -50 * (it + 1) else 50 * it }
        nets += -nets.sum()
        assertSettles(nets, Settlement.settle(nets))
    }

    @Test
    fun voidMissingMoneyShrinksLosses() {
        // Pot 200; counted 180 -> 20 missing. Winner +50, losers -40 and -30 (sum -20).
        val adjusted = Settlement.applyVoid(listOf(50, -40, -30))
        assertEquals(0, adjusted.sum())
        assertEquals(50, adjusted[0])
        assertEquals(listOf(-29, -21), adjusted.subList(1, 3)) // 40*50/70=28.57 -> 29, 30*50/70=21.4 -> 21
    }

    @Test
    fun voidExtraMoneyShrinksGains() {
        val adjusted = Settlement.applyVoid(listOf(60, 40, -80))
        assertEquals(0, adjusted.sum())
        assertEquals(listOf(48, 32, -80), adjusted)
    }

    @Test
    fun voidRoundingRemainderIsDistributed() {
        val adjusted = Settlement.applyVoid(listOf(10, 10, 10, -29))
        assertEquals(0, adjusted.sum())
        assertTrue(adjusted.take(3).all { it in 9..10 })
    }

    @Test
    fun voidWhenOneSideIsEmptyZeroesEveryone() {
        assertEquals(listOf(0, 0), Settlement.applyVoid(listOf(-50, -20)))
    }

    @Test
    fun voidOnBalancedNetsIsNoOp() {
        val nets = listOf(30, -30)
        assertEquals(nets, Settlement.applyVoid(nets))
    }
}
