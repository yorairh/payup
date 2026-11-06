package com.payup.domain

import com.payup.domain.Outcome.Even
import com.payup.domain.Outcome.Loser
import com.payup.domain.Outcome.TopWinner
import com.payup.domain.Outcome.Winner
import kotlin.test.assertEquals
import kotlin.test.Test

class GameOutcomesTest {
    private fun outcomes(vararg nets: Pair<String, Int>) =
        GameOutcomes.rank(nets.toList()).map { it.name to it.outcome }

    @Test fun clearTopWinnerThenWinnersThenLosers() {
        assertEquals(
            listOf("Dan" to TopWinner, "Gil" to Winner, "Avi" to Loser),
            outcomes("Avi" to -150, "Dan" to 100, "Gil" to 50),
        )
    }

    @Test fun sortsBiggestWinnerFirst() {
        assertEquals(listOf("B", "C", "A"), GameOutcomes.rank(listOf("A" to -5, "B" to 9, "C" to 1)).map { it.name })
    }

    @Test fun tieForFirstGivesTheCrownToWhoeverWasListedFirst() {
        assertEquals(
            listOf("Dan" to TopWinner, "Gil" to Winner, "Avi" to Loser),
            outcomes("Dan" to 100, "Gil" to 100, "Avi" to -200),
        )
    }

    @Test fun equalNetsKeepTheirGivenOrder() {
        assertEquals(listOf("B", "A", "C"), GameOutcomes.rank(listOf("B" to 0, "A" to 0, "C" to 0)).map { it.name })
    }

    @Test fun everyoneEvenMeansNoWinnerAndNoLoser() {
        assertEquals(listOf("A" to Even, "B" to Even), outcomes("A" to 0, "B" to 0))
    }

    @Test fun zeroNetPlayerIsEvenBetweenWinnersAndLosers() {
        assertEquals(
            listOf("A" to TopWinner, "B" to Even, "C" to Loser),
            outcomes("C" to -10, "B" to 0, "A" to 10),
        )
    }

    @Test fun singlePlayerWhoBrokeEvenIsEven() {
        assertEquals(listOf("A" to Even), outcomes("A" to 0))
    }

    @Test fun noPlayersGivesNoResults() {
        assertEquals(emptyList<PlayerResult>(), GameOutcomes.rank(emptyList()))
    }

    @Test fun whenNobodyWonTheBiggestLoserIsNotTopWinner() {
        assertEquals(listOf("A" to Loser, "B" to Loser), outcomes("A" to -1, "B" to -2))
    }
}
