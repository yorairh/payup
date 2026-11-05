package com.payup.domain

enum class Outcome { TopWinner, Winner, Even, Loser }

data class PlayerResult(val name: String, val net: Int, val outcome: Outcome)

object GameOutcomes {
    /**
     * Ranks players by net, biggest winner first (equal nets keep their given order), and labels each one.
     * The first player is the [Outcome.TopWinner] if they won; with a tie for first, that is whoever was
     * listed first and the others at the same amount are plain [Outcome.Winner]s.
     */
    fun rank(nets: List<Pair<String, Int>>): List<PlayerResult> {
        val sorted = nets.sortedByDescending { it.second }
        val top = sorted.firstOrNull()?.second ?: 0
        return sorted.mapIndexed { i, (name, net) ->
            val outcome = when {
                net > 0 && i == 0 && net == top -> Outcome.TopWinner
                net > 0 -> Outcome.Winner
                net < 0 -> Outcome.Loser
                else -> Outcome.Even
            }
            PlayerResult(name, net, outcome)
        }
    }
}
