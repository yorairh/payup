package com.payup.data

import androidx.room3.withWriteTransaction
import com.payup.domain.Backup
import com.payup.domain.BackupGame
import com.payup.domain.BackupMerge
import com.payup.domain.CashOutWrite
import com.payup.domain.Currency
import com.payup.domain.GameResult
import com.payup.domain.Settlement
import com.payup.sync.SharedGame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

class GameRepository(private val db: AppDatabase) {
    private val dao = db.dao()

    val friends: Flow<List<FriendEntity>> = dao.friends()
    val activeGameId: Flow<Long?> = dao.activeGameId()
    val history: Flow<List<GameWithDetails>> = dao.history()

    fun game(id: Long): Flow<GameWithDetails?> = dao.game(id)

    /** The shared game this device hosts that was started last; the one kept in step with its online copy. */
    val latestHostedGame: Flow<GameWithDetails?> = dao.latestHostedGame()

    /** The online id of game [gameId] if this device hosts it, else null. */
    suspend fun hostedShareId(gameId: Long): String? = dao.hostedShareId(gameId)

    /** The online id of the unfinished game this device hosts, if there is one. */
    suspend fun activeHostedShareId(): String? = dao.activeHostedShareId()

    /** The game on this device that is the shared game [shareId], if any. */
    suspend fun gameBySharedId(shareId: String): GameEntity? = dao.gameBySharedId(shareId)

    /**
     * Saves a watched game that has finished into the history, once: if [shareId] is already here, nothing is added and
     * the existing game's id is returned.
     */
    suspend fun saveWatchedGame(shareId: String, game: SharedGame): Long = db.withWriteTransaction {
        dao.gameBySharedId(shareId)?.let { return@withWriteTransaction it.id }
        insertSharedGame(shareId, isHost = false, hostKey = null, game)
    }

    /** True if an unfinished game is on this device; taking over a game would discard it. */
    suspend fun hasActiveGame(): Boolean = dao.activeGameIds().isNotEmpty()

    /**
     * Makes the online game [shareId] this device's own running game, hosted here with [hostKey]. An older copy of the
     * same game and any other unfinished game on this device are replaced. Returns the new local id.
     */
    suspend fun adoptHostedGame(shareId: String, hostKey: String, game: SharedGame): Long = db.withWriteTransaction {
        dao.gameBySharedId(shareId)?.let { deleteGameRows(it.id) }
        dao.activeGameIds().forEach { deleteGameRows(it) }
        insertSharedGame(shareId, isHost = true, hostKey = hostKey, game)
    }

    /** Removes this device's hosted copy of [shareId] (after someone else took over hosting). */
    suspend fun deleteHostedGame(shareId: String) = db.withWriteTransaction {
        dao.gameBySharedId(shareId)?.takeIf { it.isHost }?.let { deleteGameRows(it.id) }
    }

    private suspend fun insertSharedGame(shareId: String, isHost: Boolean, hostKey: String?, game: SharedGame): Long {
        val gameId = dao.insertGame(
            GameEntity(
                startedAt = game.startedAt,
                endedAt = game.endedAt,
                defaultBuyIn = game.defaultBuyIn,
                finished = game.finished,
                voidedDifference = game.voidedDifference,
                chipValue = game.chipValue,
                roundToWhole = game.roundToWhole,
                currency = game.currency,
                shareId = shareId,
                isHost = isHost,
                hostKey = hostKey,
            )
        )
        val playerIds = game.players.map { p ->
            val playerId = dao.insertPlayer(PlayerEntity(gameId = gameId, name = p.name, finalAmount = p.finalAmount, net = p.net, chips = p.chips))
            p.buyIns.forEach { dao.insertBuyIn(BuyInEntity(playerId = playerId, amount = it.amount, createdAt = it.at)) }
            playerId
        }
        game.dealerIndex?.let { dao.setDealer(gameId, playerIds.getOrNull(it)) }
        dao.insertTransfers(game.transfers.map { TransferEntity(gameId = gameId, fromName = it.fromName, toName = it.toName, amount = it.amount) })
        return gameId
    }

    /** Records that game [gameId] is online as [shareId] and that this device hosts it with [hostKey]. */
    suspend fun markShared(gameId: Long, shareId: String, hostKey: String) = dao.markShared(gameId, shareId, hostKey)

    /** Currency of the most recently finished game; the leaderboard adds amounts up without converting. */
    val latestCurrency: Flow<Currency> = dao.latestFinishedCurrencyCode()
        .map { Currency.fromCode(it) }
        .distinctUntilChanged()

    suspend fun lastDefaultBuyIn(): Int? = dao.lastDefaultBuyIn()

    suspend fun addFriend(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) dao.insertFriend(FriendEntity(name = trimmed))
    }

    /** Every player's result in every finished game, for the leaderboard. */
    val leaderboardResults: Flow<List<GameResult>> = history.map { games ->
        games.flatMap { details ->
            val endedAt = details.game.endedAt ?: details.game.startedAt
            details.players.map { GameResult(endedAt, it.player.name, it.player.net ?: 0, it.totalIn) }
        }
    }

    /**
     * Renames a friend and carries their past games and transfers over to the new name.
     * Returns false (and changes nothing) if another friend already has that name.
     */
    suspend fun renameFriend(id: Long, name: String): Boolean = db.withWriteTransaction {
        val trimmed = name.trim()
        val friend = dao.friendById(id)
        if (trimmed.isEmpty() || friend == null) return@withWriteTransaction false
        if (friend.name == trimmed) return@withWriteTransaction true
        if (dao.friendByName(trimmed) != null) return@withWriteTransaction false
        dao.renameFriend(id, trimmed)
        dao.renamePlayers(friend.name, trimmed)
        dao.renameInTransfers(friend.name, trimmed)
        true
    }

    suspend fun deleteFriend(id: Long) = dao.deleteFriend(id)

    /** Starts a game where every player has already put in one buy-in. Any unfinished game is discarded. */
    suspend fun startGame(defaultBuyIn: Int, names: List<String>, currency: Currency): Long = db.withWriteTransaction {
        dao.activeGameIds().forEach { deleteGameRows(it) }
        val now = nowMillis()
        val gameId = dao.insertGame(GameEntity(startedAt = now, defaultBuyIn = defaultBuyIn, currency = currency.name))
        names.forEach { insertPlayerWithBuyIn(gameId, it, defaultBuyIn, now) }
        gameId
    }

    suspend fun addPlayer(gameId: Long, name: String, buyIn: Int) = db.withWriteTransaction {
        dao.insertFriend(FriendEntity(name = name.trim()))
        insertPlayerWithBuyIn(gameId, name.trim(), buyIn, nowMillis())
    }

    suspend fun addBuyIn(playerId: Long, amount: Int) {
        dao.insertBuyIn(BuyInEntity(playerId = playerId, amount = amount, createdAt = nowMillis()))
    }

    suspend fun removeBuyIn(buyInId: Long) = dao.deleteBuyIn(buyInId)

    suspend fun updateBuyIn(buyInId: Long, amount: Int) {
        if (amount > 0) dao.updateBuyInAmount(buyInId, amount)
    }

    /** Removes a player from a running game together with all their buy-ins. */
    suspend fun removePlayer(playerId: Long) = db.withWriteTransaction {
        dao.deleteBuyInsOfPlayer(playerId)
        dao.deletePlayer(playerId)
    }

    /** Removes the most recent buy-in of the game and returns who it belonged to and how much it was. */
    suspend fun undoLastBuyIn(gameId: Long): Pair<String, Int>? = db.withWriteTransaction {
        val last = dao.lastBuyIn(gameId) ?: return@withWriteTransaction null
        dao.deleteBuyIn(last.id)
        (dao.playerName(last.playerId) ?: "") to last.amount
    }

    /** Stores one cash-out event's changes together, so a game never holds half of them. */
    suspend fun saveCashOut(gameId: Long, writes: List<CashOutWrite>) = db.withWriteTransaction {
        writes.forEach { store(gameId, it) }
    }

    private suspend fun store(gameId: Long, write: CashOutWrite) = when (write) {
        is CashOutWrite.FinalAmount -> dao.setFinalAmount(write.playerId, write.amount)
        is CashOutWrite.Chips -> dao.setChips(write.playerId, write.chips)
        is CashOutWrite.ChipSettingsChanged -> dao.setChipSettings(gameId, write.chipValue, write.roundToWhole)
    }

    suspend fun setDealer(gameId: Long, playerId: Long?) = dao.setDealer(gameId, playerId)

    /** (chip value, round) from the last game counted in chips, or null if there was none. */
    suspend fun lastChipSettings(): Pair<Int, Boolean>? =
        dao.lastChipGame()?.let { game -> game.chipValue?.let { it to game.roundToWhole } }

    /**
     * Settles the game: stores [finalWrites], computes each player's net, voids any counting difference if
     * [voidDifference] is set, stores the transfers and moves the game to history. All of it happens or none does.
     */
    suspend fun finishGame(gameId: Long, finalWrites: List<CashOutWrite>, voidDifference: Boolean) = db.withWriteTransaction {
        finalWrites.forEach { store(gameId, it) }
        val details = dao.gameOnce(gameId) ?: return@withWriteTransaction
        val players = details.players
        val rawNets = players.map { (it.player.finalAmount ?: 0) - it.totalIn }
        val difference = rawNets.sum()
        check(difference == 0 || voidDifference) { "Counted amount differs from the pot by $difference" }

        val nets = Settlement.applyVoid(rawNets)
        val transfers = Settlement.settle(nets)
        players.forEachIndexed { i, p -> dao.setNet(p.player.id, nets[i]) }
        dao.insertTransfers(transfers.map {
            TransferEntity(
                gameId = gameId,
                fromName = players[it.from].player.name,
                toName = players[it.to].player.name,
                amount = it.amount,
            )
        })
        dao.updateGame(
            details.game.copy(finished = true, endedAt = nowMillis(), voidedDifference = difference)
        )
    }

    /** Every game and friend on this phone, ready to write to a backup file. */
    suspend fun exportBackup(): Backup = db.withWriteTransaction {
        Backup(
            exportedAt = nowMillis(),
            friends = dao.friendsOnce().map { it.name },
            games = dao.allGames().map { it.toBackup() },
        )
    }

    /** What restoring [backup] would add, without changing anything. */
    suspend fun previewImport(backup: Backup): BackupMerge.Plan = db.withWriteTransaction { planImport(backup) }

    /** Adds the games from [backup] that are not here yet, all or nothing. Nothing on the phone is changed or deleted. */
    suspend fun importBackup(backup: Backup): BackupMerge.Plan = db.withWriteTransaction {
        val plan = planImport(backup)
        backup.friends.forEach { dao.insertFriend(FriendEntity(name = it.trim())) }
        plan.toAdd.forEach { insertBackupGame(it) }
        plan
    }

    private suspend fun planImport(backup: Backup): BackupMerge.Plan =
        BackupMerge.plan(backup, dao.allGames().map { it.key() }, hasUnfinishedHere = dao.activeGameIds().isNotEmpty())

    private suspend fun insertBackupGame(game: BackupGame) {
        val gameId = dao.insertGame(game.toEntity())
        val playerIds = game.players.map { p ->
            val playerId = dao.insertPlayer(
                PlayerEntity(gameId = gameId, name = p.name, finalAmount = p.finalAmount, net = p.net, chips = p.chips)
            )
            p.buyIns.forEach { dao.insertBuyIn(BuyInEntity(playerId = playerId, amount = it.amount, createdAt = it.createdAt)) }
            playerId
        }
        game.dealerIndex?.let { dao.setDealer(gameId, playerIds[it]) }
        dao.insertTransfers(game.transfers.map { TransferEntity(gameId = gameId, fromName = it.from, toName = it.to, amount = it.amount) })
    }

    suspend fun deleteGame(gameId: Long) = db.withWriteTransaction { deleteGameRows(gameId) }

    private suspend fun deleteGameRows(gameId: Long) {
        dao.deleteBuyInsOfGame(gameId)
        dao.deletePlayersOfGame(gameId)
        dao.deleteTransfersOfGame(gameId)
        dao.deleteGameRow(gameId)
    }

    private suspend fun insertPlayerWithBuyIn(gameId: Long, name: String, buyIn: Int, now: Long) {
        val playerId = dao.insertPlayer(PlayerEntity(gameId = gameId, name = name))
        dao.insertBuyIn(BuyInEntity(playerId = playerId, amount = buyIn, createdAt = now))
    }
}

private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
