package com.payup.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DealerTest {
    @Test
    fun stopsAtTheFirstAceAndOnlyTheLastCardIsAnAce() {
        repeat(200) { seed ->
            val dealt = Dealer.deal(5, Random(seed))
            assertTrue(dealt.last().card.isAce)
            assertTrue(dealt.dropLast(1).none { it.card.isAce })
        }
    }

    @Test
    fun cardsGoRoundTheTableInSeatOrder() {
        val dealt = Dealer.deal(4, Random(7))
        dealt.forEachIndexed { i, d -> assertEquals(i % 4, d.playerIndex) }
    }

    @Test
    fun noCardIsDealtTwice() {
        repeat(50) { seed ->
            val cards = Dealer.deal(3, Random(seed)).map { it.card }
            assertEquals(cards.size, cards.toSet().size)
        }
    }

    @Test
    fun worksForASinglePlayer() {
        assertEquals(0, Dealer.deal(1, Random(1)).last().playerIndex)
    }
}
