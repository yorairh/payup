package com.payup.data

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Relation

@Entity(tableName = "friends", indices = [Index(value = ["name"], unique = true)])
data class FriendEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

// Every money column is in agorot (1 shekel = 100), see com.payup.domain.Money.

@Entity(tableName = "games", indices = [Index(value = ["shareId"], unique = true)])
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val defaultBuyIn: Int,
    val finished: Boolean = false,
    /** Counted minus pot at cash-out when the difference was voided: negative = missing, positive = extra. */
    val voidedDifference: Int = 0,
    /** Value of one chip when the cash-out is counted in chips; null when counting money directly. */
    val chipValue: Int? = null,
    /** Chip mode: round each player down to whole shekels and give the leftover to the biggest loser. */
    @ColumnInfo(defaultValue = "1") val roundToWhole: Boolean = true,
    /** The player picked as dealer by the card deal at game start; null when the game started without it. */
    val dealerPlayerId: Long? = null,
    /** [com.payup.domain.Currency] name; games from before currencies are shekels. */
    @ColumnInfo(defaultValue = "ILS") val currency: String = "ILS",
    /**
     * The online id of a shared game (see docs/SHARED_GAMES.md); null for a game that was never shared. The unique index
     * stops a watched game from being saved twice.
     */
    val shareId: String? = null,
    /** True on the device that hosts the shared game, so only this device edits it. */
    @ColumnInfo(defaultValue = "0") val isHost: Boolean = false,
    /** The secret that proves hosting (the part of the host link after the #); null except on the host's devices. */
    val hostKey: String? = null,
)

/** A player in one game. The name is copied so history survives renaming or deleting a friend. */
@Entity(
    tableName = "players",
    foreignKeys = [ForeignKey(GameEntity::class, ["id"], ["gameId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("gameId")],
)
data class PlayerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: Long,
    val name: String,
    val finalAmount: Int? = null,
    /** Final result after settling (and any void); null until the game is finished. */
    val net: Int? = null,
    /** Chips counted at cash-out; null when this player's money was typed directly. */
    val chips: Int? = null,
)

@Entity(
    tableName = "buy_ins",
    foreignKeys = [ForeignKey(PlayerEntity::class, ["id"], ["playerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("playerId")],
)
data class BuyInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playerId: Long,
    val amount: Int,
    val createdAt: Long,
)

@Entity(
    tableName = "transfers",
    foreignKeys = [ForeignKey(GameEntity::class, ["id"], ["gameId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("gameId")],
)
data class TransferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: Long,
    val fromName: String,
    val toName: String,
    val amount: Int,
)

data class PlayerWithBuyIns(
    @Embedded val player: PlayerEntity,
    @Relation(parentColumns = ["id"], entityColumns = ["playerId"])
    val buyIns: List<BuyInEntity>,
) {
    val totalIn: Int get() = buyIns.sumOf { it.amount }
}

data class GameWithDetails(
    @Embedded val game: GameEntity,
    @Relation(entity = PlayerEntity::class, parentColumns = ["id"], entityColumns = ["gameId"])
    val unorderedPlayers: List<PlayerWithBuyIns>,
    @Relation(parentColumns = ["id"], entityColumns = ["gameId"])
    val unorderedTransfers: List<TransferEntity>,
) {
    /** Players in the order they joined the table. */
    val players: List<PlayerWithBuyIns> get() = unorderedPlayers.sortedBy { it.player.id }
    val transfers: List<TransferEntity> get() = unorderedTransfers.sortedBy { it.id }
    val pot: Int get() = unorderedPlayers.sumOf { it.totalIn }
}
