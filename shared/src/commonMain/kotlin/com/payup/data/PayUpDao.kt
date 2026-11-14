package com.payup.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PayUpDao {

    // Friends

    @Query("SELECT * FROM friends ORDER BY name COLLATE NOCASE")
    fun friends(): Flow<List<FriendEntity>>

    @Query("SELECT * FROM friends ORDER BY name COLLATE NOCASE")
    suspend fun friendsOnce(): List<FriendEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFriend(friend: FriendEntity): Long

    @Query("SELECT * FROM friends WHERE id = :id")
    suspend fun friendById(id: Long): FriendEntity?

    @Query("SELECT * FROM friends WHERE name = :name LIMIT 1")
    suspend fun friendByName(name: String): FriendEntity?

    @Query("UPDATE friends SET name = :name WHERE id = :id")
    suspend fun renameFriend(id: Long, name: String)

    @Query("UPDATE players SET name = :newName WHERE name = :oldName")
    suspend fun renamePlayers(oldName: String, newName: String)

    @Query(
        """UPDATE transfers SET
             fromName = CASE WHEN fromName = :oldName THEN :newName ELSE fromName END,
             toName = CASE WHEN toName = :oldName THEN :newName ELSE toName END
           WHERE fromName = :oldName OR toName = :oldName"""
    )
    suspend fun renameInTransfers(oldName: String, newName: String)

    @Query("DELETE FROM friends WHERE id = :id")
    suspend fun deleteFriend(id: Long)

    // Games

    @Insert
    suspend fun insertGame(game: GameEntity): Long

    @Update
    suspend fun updateGame(game: GameEntity)

    @Transaction
    @Query("SELECT * FROM games WHERE id = :id")
    fun game(id: Long): Flow<GameWithDetails?>

    @Transaction
    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun gameOnce(id: Long): GameWithDetails?

    /** The shared game this device hosts that was started last, finished or not. */
    @Transaction
    @Query("SELECT * FROM games WHERE shareId IS NOT NULL AND isHost = 1 ORDER BY finished ASC, startedAt DESC, id DESC LIMIT 1")
    fun latestHostedGame(): Flow<GameWithDetails?>

    /** The game on this device that is the shared game [shareId] (hosted here, or watched and saved), if any. */
    @Query("SELECT * FROM games WHERE shareId = :shareId LIMIT 1")
    suspend fun gameBySharedId(shareId: String): GameEntity?

    /** The online id of game [gameId] if this device hosts it. */
    @Query("SELECT shareId FROM games WHERE id = :gameId AND isHost = 1")
    suspend fun hostedShareId(gameId: Long): String?

    /** The online id of the unfinished game this device hosts, if any. */
    @Query("SELECT shareId FROM games WHERE finished = 0 AND isHost = 1 AND shareId IS NOT NULL LIMIT 1")
    suspend fun activeHostedShareId(): String?

    @Query("UPDATE games SET shareId = :shareId, isHost = 1, hostKey = :hostKey WHERE id = :gameId")
    suspend fun markShared(gameId: Long, shareId: String, hostKey: String)

    /** Just the currency column, so the leaderboard never loads every game's players and buy-ins. */
    @Query("SELECT currency FROM games WHERE finished = 1 ORDER BY COALESCE(endedAt, startedAt) DESC LIMIT 1")
    fun latestFinishedCurrencyCode(): Flow<String?>

    /** Every game with its players, for backups. */
    @Transaction
    @Query("SELECT * FROM games ORDER BY startedAt, id")
    suspend fun allGames(): List<GameWithDetails>

    @Query("SELECT id FROM games WHERE finished = 0 ORDER BY startedAt DESC LIMIT 1")
    fun activeGameId(): Flow<Long?>

    @Query("SELECT id FROM games WHERE finished = 0")
    suspend fun activeGameIds(): List<Long>

    @Query("SELECT defaultBuyIn FROM games ORDER BY startedAt DESC LIMIT 1")
    suspend fun lastDefaultBuyIn(): Int?

    @Transaction
    @Query("SELECT * FROM games WHERE finished = 1 ORDER BY endedAt DESC")
    fun history(): Flow<List<GameWithDetails>>

    // Deleting children explicitly so we don't depend on SQLite foreign-key enforcement being on.
    @Query("DELETE FROM buy_ins WHERE playerId IN (SELECT id FROM players WHERE gameId = :gameId)")
    suspend fun deleteBuyInsOfGame(gameId: Long)

    @Query("DELETE FROM players WHERE gameId = :gameId")
    suspend fun deletePlayersOfGame(gameId: Long)

    @Query("DELETE FROM transfers WHERE gameId = :gameId")
    suspend fun deleteTransfersOfGame(gameId: Long)

    @Query("DELETE FROM games WHERE id = :gameId")
    suspend fun deleteGameRow(gameId: Long)

    // Players and buy-ins

    @Insert
    suspend fun insertPlayer(player: PlayerEntity): Long

    @Query("SELECT name FROM players WHERE id = :id")
    suspend fun playerName(id: Long): String?

    @Query("UPDATE players SET finalAmount = :amount WHERE id = :id")
    suspend fun setFinalAmount(id: Long, amount: Int?)

    @Query("UPDATE players SET chips = :chips WHERE id = :id")
    suspend fun setChips(id: Long, chips: Int?)

    @Query("UPDATE games SET dealerPlayerId = :playerId WHERE id = :gameId")
    suspend fun setDealer(gameId: Long, playerId: Long?)

    @Query("UPDATE games SET chipValue = :chipValue, roundToWhole = :roundToWhole WHERE id = :gameId")
    suspend fun setChipSettings(gameId: Long, chipValue: Int?, roundToWhole: Boolean)

    /** Chip settings of the most recent game that was counted in chips, to start the next one with. */
    @Query("SELECT * FROM games WHERE chipValue IS NOT NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun lastChipGame(): GameEntity?

    @Query("UPDATE players SET net = :net WHERE id = :id")
    suspend fun setNet(id: Long, net: Int)

    @Insert
    suspend fun insertBuyIn(buyIn: BuyInEntity): Long

    @Query(
        """SELECT b.* FROM buy_ins b JOIN players p ON b.playerId = p.id
           WHERE p.gameId = :gameId ORDER BY b.createdAt DESC, b.id DESC LIMIT 1"""
    )
    suspend fun lastBuyIn(gameId: Long): BuyInEntity?

    @Query("DELETE FROM buy_ins WHERE id = :id")
    suspend fun deleteBuyIn(id: Long)

    @Query("UPDATE buy_ins SET amount = :amount WHERE id = :id")
    suspend fun updateBuyInAmount(id: Long, amount: Int)

    @Query("DELETE FROM buy_ins WHERE playerId = :playerId")
    suspend fun deleteBuyInsOfPlayer(playerId: Long)

    @Query("DELETE FROM players WHERE id = :id")
    suspend fun deletePlayer(id: Long)

    @Insert
    suspend fun insertTransfers(transfers: List<TransferEntity>)
}
