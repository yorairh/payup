package com.payup.sync

import com.payup.data.BuyInEntity
import com.payup.data.GameEntity
import com.payup.data.GameWithDetails
import com.payup.data.PlayerEntity
import com.payup.data.PlayerWithBuyIns
import com.payup.data.TransferEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// The online shape of a game (docs/SHARED_GAMES.md). Players are listed by position, so no database ids leave the device.
// Money is in hundredths, like everywhere else. The Firestore rules (firebase/firestore.rules) check these field names.

@Serializable
data class SharedBuyIn(val amount: Int, val at: Long)

@Serializable
data class SharedPlayer(
    val name: String,
    val buyIns: List<SharedBuyIn> = emptyList(),
    val chips: Int? = null,
    val finalAmount: Int? = null,
    val net: Int? = null,
)

@Serializable
data class SharedTransfer(val fromName: String, val toName: String, val amount: Int)

/** The game itself: what the host uploads and viewers see. The bookkeeping fields (revision, host) are not part of it. */
@Serializable
data class SharedGame(
    val schemaVersion: Int = SCHEMA_VERSION,
    val startedAt: Long,
    val endedAt: Long? = null,
    val defaultBuyIn: Int,
    val finished: Boolean,
    val voidedDifference: Int = 0,
    val chipValue: Int? = null,
    val roundToWhole: Boolean = true,
    val currency: String,
    /** Position of the dealer in [players], or null when the game started without the card deal. */
    val dealerIndex: Int? = null,
    val players: List<SharedPlayer>,
    val transfers: List<SharedTransfer> = emptyList(),
) {
    fun toJson(): String = CODEC.encodeToString(serializer(), this)

    companion object {
        const val SCHEMA_VERSION = 1

        /** Absent optional fields are left out (never null), which is what the rules expect; unknown fields are ignored. */
        internal val CODEC = Json { explicitNulls = false; ignoreUnknownKeys = true; encodeDefaults = true }

        fun fromJson(json: String): SharedGame = CODEC.decodeFromString(serializer(), json)
    }
}

/** A game as it is stored online: the game plus who hosts it and how many times it was written. */
data class OnlineGame(val revision: Int, val hostUid: String, val game: SharedGame) {
    companion object {
        @Serializable
        private data class Meta(val revision: Int, val hostUid: String)

        /** Reads a whole Firestore document written as JSON; the extra fields in it are ignored by [SharedGame]. */
        fun fromJson(json: String): OnlineGame {
            val meta = SharedGame.CODEC.decodeFromString(Meta.serializer(), json)
            return OnlineGame(meta.revision, meta.hostUid, SharedGame.fromJson(json))
        }
    }
}

fun GameWithDetails.toSharedGame(): SharedGame = SharedGame(
    startedAt = game.startedAt,
    endedAt = game.endedAt,
    defaultBuyIn = game.defaultBuyIn,
    finished = game.finished,
    voidedDifference = game.voidedDifference,
    chipValue = game.chipValue,
    roundToWhole = game.roundToWhole,
    currency = game.currency,
    dealerIndex = players.indexOfFirst { it.player.id == game.dealerPlayerId }.takeIf { it >= 0 },
    players = players.map { p ->
        SharedPlayer(
            name = p.player.name,
            buyIns = p.buyIns.sortedWith(compareBy({ it.createdAt }, { it.id })).map { SharedBuyIn(it.amount, it.createdAt) },
            chips = p.player.chips,
            finalAmount = p.player.finalAmount,
            net = p.player.net,
        )
    },
    transfers = transfers.map { SharedTransfer(it.fromName, it.toName, it.amount) },
)

/**
 * The game as the screens expect it, for showing a watched game read-only. Database ids are made up (1, 2, 3...) and only
 * link the pieces together; [shareId] is the online id.
 */
fun SharedGame.toGameWithDetails(shareId: String): GameWithDetails {
    val playerIds = players.indices.map { it + 1L }
    var buyInId = 0L
    return GameWithDetails(
        game = GameEntity(
            id = 1,
            startedAt = startedAt,
            endedAt = endedAt,
            defaultBuyIn = defaultBuyIn,
            finished = finished,
            voidedDifference = voidedDifference,
            chipValue = chipValue,
            roundToWhole = roundToWhole,
            dealerPlayerId = dealerIndex?.let { playerIds.getOrNull(it) },
            currency = currency,
            shareId = shareId,
            isHost = false,
        ),
        unorderedPlayers = players.mapIndexed { i, p ->
            PlayerWithBuyIns(
                player = PlayerEntity(playerIds[i], 1, p.name, p.finalAmount, p.net, p.chips),
                buyIns = p.buyIns.map { BuyInEntity(++buyInId, playerIds[i], it.amount, it.at) },
            )
        },
        unorderedTransfers = transfers.mapIndexed { i, t -> TransferEntity(i + 1L, 1, t.fromName, t.toName, t.amount) },
    )
}
