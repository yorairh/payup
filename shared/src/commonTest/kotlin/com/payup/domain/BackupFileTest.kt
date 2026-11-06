package com.payup.domain

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class BackupFileTest {
    private fun player(name: String, buyIns: List<Int> = listOf(10000), net: Int? = 0) =
        BackupPlayer(name, finalAmount = 10000, net = net, chips = null, buyIns = buyIns.map { BackupBuyIn(it, 1_000L) })

    private fun game(
        startedAt: Long = 1_000L,
        players: List<BackupPlayer> = listOf(player("Dan", net = 5000), player("Avi", net = -5000)),
        finished: Boolean = true,
    ) = BackupGame(
        startedAt = startedAt,
        endedAt = if (finished) startedAt + 60_000 else null,
        defaultBuyIn = 10000,
        finished = finished,
        dealerIndex = 1,
        currency = "USD",
        players = players,
        transfers = listOf(BackupTransfer("Avi", "Dan", 5000)),
    )

    private fun backup(vararg games: BackupGame) = Backup(exportedAt = 5L, friends = listOf("Dan", "Avi"), games = games.toList())

    private fun read(backup: Backup) = BackupFile.read(BackupFile.write(backup))

    @Test fun roundTripKeepsEverything() {
        val original = backup(game(), game(startedAt = 2_000L, finished = false))
        assertEquals(BackupReadResult.Ok(original), read(original))
    }

    @Test fun hebrewNamesAndSymbolsSurvive() {
        val original = backup(game(players = listOf(player("דן ₪"), player("عَلِي"))))
        assertEquals(BackupReadResult.Ok(original), read(original))
    }

    @Test fun leadingByteOrderMarkIsIgnored() {
        val original = backup(game())
        assertEquals(BackupReadResult.Ok(original), BackupFile.read("﻿" + BackupFile.write(original)))
    }

    @Test fun fileSaysWhatItIs() {
        val text = BackupFile.write(backup())
        assertTrue(text.contains("\"format\": \"payup-backup\""))
        assertTrue(text.contains("\"version\": 1"))
    }

    @Test fun otherJsonIsNotABackup() {
        assertEquals(BackupReadResult.NotBackup, BackupFile.read("""{"hello": "world"}"""))
        assertEquals(BackupReadResult.NotBackup, BackupFile.read("""["a", "b"]"""))
        assertEquals(BackupReadResult.NotBackup, BackupFile.read("""{"format": "something-else", "version": 1}"""))
        assertEquals(BackupReadResult.NotBackup, BackupFile.read("""{"format": 5, "version": 1}"""))
    }

    @Test fun textThatIsNotJsonIsNotABackup() {
        assertEquals(BackupReadResult.NotBackup, BackupFile.read("just some text"))
        assertEquals(BackupReadResult.NotBackup, BackupFile.read(""))
        assertEquals(BackupReadResult.NotBackup, BackupFile.read("""{"format": "payup-backup", "version": 1"""))
    }

    @Test fun newerVersionIsRefusedWithoutReadingTheRest() {
        val text = """{"format": "payup-backup", "version": 2, "somethingNew": {"games": 7}}"""
        assertEquals(BackupReadResult.TooNew(2), BackupFile.read(text))
    }

    @Test fun missingOrBadVersionIsInvalid() {
        assertEquals(BackupReadResult.Invalid, BackupFile.read("""{"format": "payup-backup", "exportedAt": 1}"""))
        assertEquals(BackupReadResult.Invalid, BackupFile.read("""{"format": "payup-backup", "version": 0, "exportedAt": 1}"""))
        assertEquals(BackupReadResult.Invalid, BackupFile.read("""{"format": "payup-backup", "version": "one", "exportedAt": 1}"""))
    }

    @Test fun missingRequiredFieldIsInvalid() {
        val text = """{"format": "payup-backup", "version": 1, "exportedAt": 1, "games": [{"startedAt": 1}]}"""
        assertEquals(BackupReadResult.Invalid, BackupFile.read(text))
    }

    @Test fun unknownFieldsAreIgnored() {
        val text = BackupFile.write(backup(game())).replaceFirst("\"exportedAt\"", "\"addedLater\": true,\n  \"exportedAt\"")
        assertEquals(BackupReadResult.Ok(backup(game())), BackupFile.read(text))
    }

    @Test fun emptyBackupIsFine() {
        assertEquals(BackupReadResult.Ok(Backup(exportedAt = 0)), read(Backup(exportedAt = 0)))
    }

    @Test fun valuesTheAppWouldNeverWriteAreInvalid() {
        val bad = listOf(
            "no players" to game(players = emptyList()),
            "blank name" to game(players = listOf(player("  "))),
            "name too long" to game(players = listOf(player("x".repeat(101)))),
            "zero buy-in" to game(players = listOf(player("Dan", buyIns = listOf(0)))),
            "negative buy-in" to game(players = listOf(player("Dan", buyIns = listOf(-100)))),
            "huge amount" to game(players = listOf(player("Dan", net = 2_000_000_000))),
            "unknown currency" to game().copy(currency = "XYZ"),
            "dealer out of range" to game().copy(dealerIndex = 5),
            "negative dealer" to game().copy(dealerIndex = -1),
            "zero chip value" to game().copy(chipValue = 0),
            "negative start" to game().copy(startedAt = -1),
            "zero transfer" to game().copy(transfers = listOf(BackupTransfer("Avi", "Dan", 0))),
            "blank transfer name" to game().copy(transfers = listOf(BackupTransfer("", "Dan", 100))),
        )
        bad.forEach { (why, game) ->
            assertEquals(BackupReadResult.Invalid, read(backup(game)), why)
        }
    }

    @Test fun blankFriendNameIsInvalid() {
        assertEquals(BackupReadResult.Invalid, read(Backup(exportedAt = 1, friends = listOf("Dan", " "))))
    }

    @Test fun tooManyGamesIsInvalid() {
        val games = List(10_001) { game(startedAt = it.toLong()) }
        assertEquals(BackupReadResult.Invalid, read(Backup(exportedAt = 1, games = games)))
    }
}
