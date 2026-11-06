package com.payup.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant

/** One player's result in one finished game. */
data class GameResult(val endedAt: Long, val name: String, val net: Int, val totalIn: Int)

data class PlayerStats(
    val name: String,
    val games: Int,
    val wins: Int,
    val profit: Int,
    val totalIn: Int,
    val bestGame: Int,
    val worstGame: Int,
) {
    val winRate: Float get() = if (games == 0) 0f else wins.toFloat() / games
    val avgPerGame: Int get() = if (games == 0) 0 else (profit.toDouble() / games).roundToInt()
    internal val exactAvg: Double get() = if (games == 0) 0.0 else profit.toDouble() / games
}

enum class LeaderboardMetric { PROFIT, WIN_RATE, AVG_PER_GAME, BIGGEST_WIN }

enum class Period { MONTH, YEAR, ALL }

object Leaderboard {

    /**
     * Groups [results] by player name and ranks them by [metric], best first.
     * Ties go to higher profit, then more games played, then name.
     * A win is a game finished above zero; breaking even counts as played but not won.
     */
    fun build(
        results: List<GameResult>,
        metric: LeaderboardMetric,
        period: Period,
        now: Instant = Clock.System.now(),
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): List<PlayerStats> {
        val from = periodStart(period, now, zone)
        val stats = results
            .filter { from == null || it.endedAt >= from }
            .groupBy { it.name }
            .map { (name, games) ->
                PlayerStats(
                    name = name,
                    games = games.size,
                    wins = games.count { it.net > 0 },
                    profit = games.sumOf { it.net },
                    totalIn = games.sumOf { it.totalIn },
                    bestGame = games.maxOf { it.net },
                    worstGame = games.minOf { it.net },
                )
            }

        val primary: Comparator<PlayerStats> = when (metric) {
            LeaderboardMetric.PROFIT -> compareByDescending { it.profit }
            LeaderboardMetric.WIN_RATE -> compareByDescending { it.winRate }
            LeaderboardMetric.AVG_PER_GAME -> compareByDescending { it.exactAvg }
            LeaderboardMetric.BIGGEST_WIN -> compareByDescending { it.bestGame }
        }
        return stats.sortedWith(
            primary
                .thenByDescending { it.profit }
                .thenByDescending { it.games }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        )
    }

    /** Start of the period (midnight on the 1st, in [zone]) in epoch millis, or null for all time. */
    private fun periodStart(period: Period, now: Instant, zone: TimeZone): Long? {
        val today = now.toLocalDateTime(zone).date
        val startDate = when (period) {
            Period.MONTH -> LocalDate(today.year, today.month, 1)
            Period.YEAR -> LocalDate(today.year, 1, 1)
            Period.ALL -> return null
        }
        return startDate.atStartOfDayIn(zone).toEpochMilliseconds()
    }
}
