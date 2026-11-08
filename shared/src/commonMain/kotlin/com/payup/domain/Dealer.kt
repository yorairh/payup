package com.payup.domain

import kotlin.random.Random

/** [rank] 1..13 where 1 is the ace; [suit] 0..3 (spades, hearts, clubs, diamonds). */
data class PlayingCard(val rank: Int, val suit: Int) {
    val isAce: Boolean get() = rank == 1
}

/** One card handed to the player seated at [playerIndex]. */
data class DealtCard(val playerIndex: Int, val card: PlayingCard)

object Dealer {
    /**
     * Deals face-up from a shuffled deck, one card per player in seat order, round after round,
     * and stops at the first ace. The player who gets it (the last card of the result) is the dealer.
     */
    fun deal(playerCount: Int, random: Random = Random): List<DealtCard> {
        require(playerCount >= 1)
        val deck = (0..3).flatMap { suit -> (1..13).map { rank -> PlayingCard(rank, suit) } }.shuffled(random)
        val dealt = mutableListOf<DealtCard>()
        for ((i, card) in deck.withIndex()) {
            dealt += DealtCard(i % playerCount, card)
            if (card.isAce) break
        }
        return dealt
    }
}
