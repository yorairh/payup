package com.payup.domain

import kotlinx.serialization.Serializable

// The backup file: plain JSON, independent of the database. Games refer to players by position (the dealer is an
// index), never by database id, so a backup can be read back by any version of the app that understands it.

@Serializable
data class BackupBuyIn(val amount: Int, val createdAt: Long)

@Serializable
data class BackupPlayer(
    val name: String,
    val finalAmount: Int? = null,
    val net: Int? = null,
    val chips: Int? = null,
    val buyIns: List<BackupBuyIn> = emptyList(),
)

@Serializable
data class BackupTransfer(val from: String, val to: String, val amount: Int)

@Serializable
data class BackupGame(
    val startedAt: Long,
    val endedAt: Long? = null,
    val defaultBuyIn: Int,
    val finished: Boolean,
    val voidedDifference: Int = 0,
    val chipValue: Int? = null,
    val chipsByShare: Boolean = false,
    val roundToWhole: Boolean = true,
    /** Position of the dealer in [players], or null when the game started without the card deal. */
    val dealerIndex: Int? = null,
    /** [Currency] name. */
    val currency: String = Currency.ILS.name,
    val players: List<BackupPlayer>,
    val transfers: List<BackupTransfer> = emptyList(),
)

@Serializable
data class Backup(
    val format: String = BackupFile.FORMAT,
    val version: Int = BackupFile.VERSION,
    val exportedAt: Long,
    val friends: List<String> = emptyList(),
    val games: List<BackupGame> = emptyList(),
)
