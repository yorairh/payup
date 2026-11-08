package com.payup.domain

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

sealed interface BackupReadResult {
    data class Ok(val backup: Backup) : BackupReadResult

    /** Valid JSON or not, it is not one of our files. */
    data object NotBackup : BackupReadResult

    /** Made by a newer app than this one; its shape is not guessed at. */
    data class TooNew(val version: Int) : BackupReadResult

    /** One of our files, but damaged or holding values the app would never produce. */
    data object Invalid : BackupReadResult
}

/** Writes and reads backup files, and refuses anything that could put bad data into the database. */
object BackupFile {
    const val FORMAT = "payup-backup"
    const val VERSION = 1

    /** Largest file the app will even try to read. */
    const val MAX_BYTES = 20 * 1024 * 1024

    private const val MAX_GAMES = 10_000
    private const val MAX_FRIENDS = 10_000
    private const val MAX_PLAYERS = 100
    private const val MAX_BUY_INS = 1_000
    private const val MAX_TRANSFERS = 1_000
    private const val MAX_NAME = 100
    private const val MAX_AMOUNT = 1_000_000_000

    // Unknown keys are ignored so a later release can add optional fields without bumping the version.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun write(backup: Backup): String = json.encodeToString(Backup.serializer(), backup)

    fun read(text: String): BackupReadResult {
        val root = try {
            json.parseToJsonElement(text.removePrefix("﻿")) as? JsonObject // Notepad adds a byte-order mark
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } ?: return BackupReadResult.NotBackup
        if ((root["format"] as? JsonPrimitive)?.takeIf { it.isString }?.content != FORMAT) return BackupReadResult.NotBackup
        val version = (root["version"] as? JsonPrimitive)?.intOrNull ?: return BackupReadResult.Invalid
        if (version > VERSION) return BackupReadResult.TooNew(version)
        if (version < 1) return BackupReadResult.Invalid
        val backup = try {
            json.decodeFromJsonElement(Backup.serializer(), root)
        } catch (e: SerializationException) {
            return BackupReadResult.Invalid
        } catch (e: IllegalArgumentException) {
            return BackupReadResult.Invalid
        }
        return if (isSane(backup)) BackupReadResult.Ok(backup) else BackupReadResult.Invalid
    }

    private fun isSane(backup: Backup): Boolean =
        backup.exportedAt >= 0 &&
            backup.friends.size <= MAX_FRIENDS &&
            backup.friends.all(::goodName) &&
            backup.games.size <= MAX_GAMES &&
            backup.games.all(::isSane)

    private fun isSane(game: BackupGame): Boolean =
        game.startedAt >= 0 &&
            (game.endedAt ?: 0) >= 0 &&
            money(game.defaultBuyIn) &&
            money(game.voidedDifference) &&
            (game.chipValue == null || game.chipValue in 1..MAX_AMOUNT) &&
            Currency.entries.any { it.name == game.currency } &&
            game.players.size in 1..MAX_PLAYERS &&
            (game.dealerIndex == null || game.dealerIndex in game.players.indices) &&
            game.transfers.size <= MAX_TRANSFERS &&
            game.players.all(::isSane) &&
            game.transfers.all { goodName(it.from) && goodName(it.to) && it.amount in 1..MAX_AMOUNT }

    private fun isSane(player: BackupPlayer): Boolean =
        goodName(player.name) &&
            (player.finalAmount == null || money(player.finalAmount)) &&
            (player.net == null || money(player.net)) &&
            (player.chips == null || player.chips in 0..MAX_AMOUNT) &&
            player.buyIns.size <= MAX_BUY_INS &&
            player.buyIns.all { it.amount in 1..MAX_AMOUNT && it.createdAt >= 0 }

    private fun goodName(name: String) = name.isNotBlank() && name.length <= MAX_NAME

    private fun money(amount: Int) = amount in -MAX_AMOUNT..MAX_AMOUNT
}
