package com.payup.data

import com.payup.domain.GameOutcomes
import com.payup.domain.PlayerResult

/** Every player's net and outcome in this game, biggest winner first. */
fun GameWithDetails.results(): List<PlayerResult> =
    GameOutcomes.rank(players.map { it.player.name to (it.player.net ?: 0) })
