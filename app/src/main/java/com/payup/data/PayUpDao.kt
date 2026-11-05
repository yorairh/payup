package com.payup.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PayUpDao {

    // Friends

    @Query("SELECT * FROM friends ORDER BY name COLLATE NOCASE")
    fun friends(): Flow<List<FriendEntity>>

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

    /** Just the currency column, so the leaderboard never loads every game's players and buy-ins. */
    @Query("SELECT currency FROM games WHERE finished = 1 ORDER BY COALESCE(endedAt, startedAt) DESC LIMIT 1")
    fun latestFinishedCurrencyCode(): Flow<String?>

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
