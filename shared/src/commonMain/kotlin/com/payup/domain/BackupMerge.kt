package com.payup.domain

/** Identifies a game across phones: when it started and who played (names sorted, so seating order does not matter). */
data class GameKey(val startedAt: Long, val names: List<String>)

object BackupMerge {
    /**
     * @property total games in the file
     * @property toAdd games that will be added, in file order
     * @property duplicates games already on this phone (or repeated in the file)
     * @property skippedUnfinished unfinished games left out, because only one game can be running at a time
     */
    data class Plan(
        val total: Int,
        val toAdd: List<BackupGame>,
        val duplicates: Int,
        val skippedUnfinished: Int,
    )

    fun keyOf(game: BackupGame) = GameKey(game.startedAt, game.players.map { it.name }.sorted())

    /**
     * Merging only ever adds: a game that matches one already here is skipped. If this phone has an unfinished game,
     * none from the file are added; otherwise only the most recently started one is.
     */
    fun plan(backup: Backup, existing: Collection<GameKey>, hasUnfinishedHere: Boolean): Plan {
        val seen = existing.toMutableSet()
        val fresh = backup.games.filter { seen.add(keyOf(it)) }
        val duplicates = backup.games.size - fresh.size
        val unfinished = fresh.filterNot { it.finished }
        val keep = if (hasUnfinishedHere) null else unfinished.maxByOrNull { it.startedAt }
        val toAdd = fresh.filter { it.finished || it === keep }
        return Plan(backup.games.size, toAdd, duplicates, skippedUnfinished = unfinished.size - (if (keep != null) 1 else 0))
    }
}
