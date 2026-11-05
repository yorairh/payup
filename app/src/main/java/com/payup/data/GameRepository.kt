package com.payup.data

import androidx.room.withTransaction
import com.payup.domain.Currency
import com.payup.domain.GameResult
import com.payup.domain.Settlement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class GameRepository(private val db: AppDatabase) {
    private val dao = db.dao()

    val friends: Flow<List<FriendEntity>> = dao.friends()
    val activeGameId: Flow<Long?> = dao.activeGameId()
    val history: Flow<List<GameWithDetails>> = dao.history()

    fun game(id: Long): Flow<GameWithDetails?> = dao.game(id)

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
    suspend fun renameFriend(id: Long, name: String): Boolean = db.withTransaction {
        val trimmed = name.trim()
        val friend = dao.friendById(id)
        if (trimmed.isEmpty() || friend == null) return@withTransaction false
        if (friend.name == trimmed) return@withTransaction true
        if (dao.friendByName(trimmed) != null) return@withTransaction false
        dao.renameFriend(id, trimmed)
        dao.renamePlayers(friend.name, trimmed)
        dao.renameInTransfers(friend.name, trimmed)
        true
    }

    suspend fun deleteFriend(id: Long) = dao.deleteFriend(id)

    /** Starts a game where every player has already put in one buy-in. Any unfinished game is discarded. */
    suspend fun startGame(defaultBuyIn: Int, names: List<String>, currency: Currency): Long = db.withTransaction {
        dao.activeGameIds().forEach { deleteGameRows(it) }
        val now = System.currentTimeMillis()
        val gameId = dao.insertGame(GameEntity(startedAt = now, defaultBuyIn = defaultBuyIn, currency = currency.name))
        names.forEach { insertPlayerWithBuyIn(gameId, it, defaultBuyIn, now) }
        gameId
    }

    suspend fun addPlayer(gameId: Long, name: String, buyIn: Int) = db.withTransaction {
        dao.insertFriend(FriendEntity(name = name.trim()))
        insertPlayerWithBuyIn(gameId, name.trim(), buyIn, System.currentTimeMillis())
    }

    suspend fun addBuyIn(playerId: Long, amount: Int) {
        dao.insertBuyIn(BuyInEntity(playerId = playerId, amount = amount, createdAt = System.currentTimeMillis()))
    }

    suspend fun removeBuyIn(buyInId: Long) = dao.deleteBuyIn(buyInId)

    suspend fun updateBuyIn(buyInId: Long, amount: Int) {
        if (amount > 0) dao.updateBuyInAmount(buyInId, amount)
    }

    /** Removes a player from a running game together with all their buy-ins. */
    suspend fun removePlayer(playerId: Long) = db.withTransaction {
        dao.deleteBuyInsOfPlayer(playerId)
        dao.deletePlayer(playerId)
    }

    /** Removes the most recent buy-in of the game and returns who it belonged to and how much it was. */
    suspend fun undoLastBuyIn(gameId: Long): Pair<String, Int>? = db.withTransaction {
        val last = dao.lastBuyIn(gameId) ?: return@withTransaction null
        dao.deleteBuyIn(last.id)
        (dao.playerName(last.playerId) ?: "") to last.amount
    }

    suspend fun setFinalAmount(playerId: Long, amount: Int?) = dao.setFinalAmount(playerId, amount)

    suspend fun setChips(playerId: Long, chips: Int?) = dao.setChips(playerId, chips)

    suspend fun setDealer(gameId: Long, playerId: Long?) = dao.setDealer(gameId, playerId)

    suspend fun setChipSettings(gameId: Long, chipValue: Int?, roundToWhole: Boolean) =
        dao.setChipSettings(gameId, chipValue, roundToWhole)

    /** (chip value, round) from the last game counted in chips, or null if there was none. */
    suspend fun lastChipSettings(): Pair<Int, Boolean>? =
        dao.lastChipGame()?.let { game -> game.chipValue?.let { it to game.roundToWhole } }

    /**
     * Settles the game: computes each player's net, voids any counting difference if [voidDifference]
     * is set, stores the transfers and moves the game to history.
     */
    suspend fun finishGame(gameId: Long, voidDifference: Boolean) = db.withTransaction {
        val details = dao.gameOnce(gameId) ?: return@withTransaction
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
            details.game.copy(finished = true, endedAt = System.currentTimeMillis(), voidedDifference = difference)
        )
    }

    suspend fun deleteGame(gameId: Long) = db.withTransaction { deleteGameRows(gameId) }

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
