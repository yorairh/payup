package com.payup.domain

import kotlin.test.assertEquals
import kotlin.test.Test

class BackupMergeTest {
    private fun game(startedAt: Long, vararg names: String, finished: Boolean = true) = BackupGame(
        startedAt = startedAt,
        defaultBuyIn = 10000,
        finished = finished,
        players = names.map { BackupPlayer(it) },
    )

    private fun backup(vararg games: BackupGame) = Backup(exportedAt = 0, games = games.toList())

    private fun plan(backup: Backup, existing: List<GameKey> = emptyList(), unfinishedHere: Boolean = false) =
        BackupMerge.plan(backup, existing, unfinishedHere)

    @Test fun everythingIsNewOnAnEmptyPhone() {
        val b = backup(game(1, "Dan", "Avi"), game(2, "Dan", "Gil"))
        val p = plan(b)
        assertEquals(b.games, p.toAdd)
        assertEquals(2, p.total)
        assertEquals(0, p.duplicates)
        assertEquals(0, p.skippedUnfinished)
    }

    @Test fun aGameAlreadyHereIsSkipped() {
        val b = backup(game(1, "Dan", "Avi"), game(2, "Dan", "Gil"))
        val p = plan(b, existing = listOf(GameKey(1, listOf("Avi", "Dan"))))
        assertEquals(listOf(b.games[1]), p.toAdd)
        assertEquals(1, p.duplicates)
    }

    @Test fun seatingOrderDoesNotMakeAGameNew() {
        val b = backup(game(1, "Dan", "Avi"))
        assertEquals(emptyList<BackupGame>(), plan(b, existing = listOf(GameKey(1, listOf("Avi", "Dan")))).toAdd)
    }

    @Test fun sameTimeButDifferentPlayersIsANewGame() {
        val b = backup(game(1, "Dan", "Gil"))
        assertEquals(1, plan(b, existing = listOf(GameKey(1, listOf("Avi", "Dan")))).toAdd.size)
    }

    @Test fun sameGameTwiceInTheFileIsAddedOnce() {
        val b = backup(game(1, "Dan", "Avi"), game(1, "Avi", "Dan"))
        val p = plan(b)
        assertEquals(1, p.toAdd.size)
        assertEquals(1, p.duplicates)
    }

    @Test fun importingTwiceAddsNothingTheSecondTime() {
        val b = backup(game(1, "Dan", "Avi"), game(2, "Dan", "Gil"))
        val first = plan(b)
        val second = plan(b, existing = first.toAdd.map(BackupMerge::keyOf))
        assertEquals(emptyList<BackupGame>(), second.toAdd)
        assertEquals(2, second.duplicates)
    }

    @Test fun unfinishedGameIsSkippedWhenOneIsAlreadyRunningHere() {
        val b = backup(game(1, "Dan", "Avi"), game(2, "Dan", "Gil", finished = false))
        val p = plan(b, unfinishedHere = true)
        assertEquals(listOf(b.games[0]), p.toAdd)
        assertEquals(1, p.skippedUnfinished)
    }

    @Test fun unfinishedGameIsAddedWhenNoneIsRunningHere() {
        val b = backup(game(1, "Dan", "Avi"), game(2, "Dan", "Gil", finished = false))
        val p = plan(b)
        assertEquals(b.games, p.toAdd)
        assertEquals(0, p.skippedUnfinished)
    }

    @Test fun onlyTheNewestOfSeveralUnfinishedGamesIsAdded() {
        val b = backup(
            game(5, "A", "B", finished = false),
            game(9, "C", "D", finished = false),
            game(7, "E", "F", finished = false),
            game(1, "G", "H"),
        )
        val p = plan(b)
        assertEquals(listOf(b.games[1], b.games[3]), p.toAdd)
        assertEquals(2, p.skippedUnfinished)
    }

    @Test fun filesOrderIsKept() {
        val b = backup(game(3, "A", "B"), game(1, "C", "D"), game(2, "E", "F"))
        assertEquals(b.games, plan(b).toAdd)
    }

    @Test fun emptyBackupAddsNothing() {
        val p = plan(backup())
        assertEquals(0, p.total)
        assertEquals(emptyList<BackupGame>(), p.toAdd)
    }
}
