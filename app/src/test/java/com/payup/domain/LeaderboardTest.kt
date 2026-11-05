package com.payup.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class LeaderboardTest {

    private val zone = ZoneId.of("Asia/Jerusalem")
    private val now = ZonedDateTime.of(2026, 10, 15, 21, 0, 0, 0, zone)

    private fun at(year: Int, month: Int, day: Int, hour: Int = 22) =
        ZonedDateTime.of(year, month, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    private val thisMonth = at(2026, 10, 3)
    private val earlierThisYear = at(2026, 2, 10)
    private val lastYear = at(2025, 12, 31)

    private val results = listOf(
        // Game 1 (this month)
        GameResult(thisMonth, "Dan", 150, 100),
        GameResult(thisMonth, "Avi", -100, 150),
        GameResult(thisMonth, "Noa", -50, 100),
        // Game 2 (earlier this year)
        GameResult(earlierThisYear, "Dan", -200, 250),
        GameResult(earlierThisYear, "Avi", 120, 50),
        GameResult(earlierThisYear, "Noa", 80, 50),
        // Game 3 (last year)
        GameResult(lastYear, "Noa", 0, 50),
        GameResult(lastYear, "Avi", 0, 50),
    )

    private fun build(metric: LeaderboardMetric = LeaderboardMetric.PROFIT, period: Period = Period.ALL) =
        Leaderboard.build(results, metric, period, now)

    @Test
    fun groupsAcrossGames() {
        val avi = build().first { it.name == "Avi" }
        assertEquals(3, avi.games)
        assertEquals(1, avi.wins)
        assertEquals(20, avi.profit)
        assertEquals(250, avi.totalIn)
        assertEquals(120, avi.bestGame)
        assertEquals(-100, avi.worstGame)
    }

    @Test
    fun breakingEvenIsNotAWin() {
        val noa = build().first { it.name == "Noa" }
        assertEquals(3, noa.games)
        assertEquals(1, noa.wins)
    }

    @Test
    fun profitRanking() {
        // Noa +30, Avi +20, Dan -50
        assertEquals(listOf("Noa", "Avi", "Dan"), build().map { it.name })
    }

    @Test
    fun winRateRankingTiesBrokenByProfit() {
        // Dan 1/2 = 50%, Noa 1/3, Avi 1/3 (Noa has more profit)
        assertEquals(listOf("Dan", "Noa", "Avi"), build(LeaderboardMetric.WIN_RATE).map { it.name })
    }

    @Test
    fun averageRanking() {
        // Noa 10, Avi 6.67, Dan -25
        val list = build(LeaderboardMetric.AVG_PER_GAME)
        assertEquals(listOf("Noa", "Avi", "Dan"), list.map { it.name })
        assertEquals(7, list[1].avgPerGame)
    }

    @Test
    fun biggestWinRanking() {
        assertEquals(listOf("Dan", "Avi", "Noa"), build(LeaderboardMetric.BIGGEST_WIN).map { it.name })
    }

    @Test
    fun monthFilterIncludesOnlyThisMonth() {
        val list = build(period = Period.MONTH)
        assertTrue(list.all { it.games == 1 })
        assertEquals(listOf("Dan", "Noa", "Avi"), list.map { it.name })
    }

    @Test
    fun monthFilterEdgeIsLocalMidnight() {
        val edge = listOf(
            GameResult(at(2026, 10, 1, hour = 0), "In", 10, 10),
            GameResult(at(2026, 9, 30, hour = 23), "Out", 10, 10),
        )
        val list = Leaderboard.build(edge, LeaderboardMetric.PROFIT, Period.MONTH, now)
        assertEquals(listOf("In"), list.map { it.name })
    }

    @Test
    fun yearFilterDropsLastYear() {
        val avi = build(period = Period.YEAR).first { it.name == "Avi" }
        assertEquals(2, avi.games)
    }

    @Test
    fun emptyInput() {
        assertTrue(Leaderboard.build(emptyList(), LeaderboardMetric.PROFIT, Period.ALL, now).isEmpty())
    }
}
