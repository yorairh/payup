package com.payup.sync

import com.payup.data.BuyInEntity
import com.payup.data.GameEntity
import com.payup.data.GameWithDetails
import com.payup.data.PlayerEntity
import com.payup.data.PlayerWithBuyIns
import com.payup.data.TransferEntity
import com.payup.data.results
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Sha256Test {
    // Reference values: FIPS 180-4 examples, and node's crypto for the rest.
    @Test fun emptyString() = assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256Hex(""))

    @Test fun abc() = assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex("abc"))

    @Test fun twoBlocks() = assertEquals(
        "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
        sha256Hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
    )

    @Test fun theKeyUsedByTheRulesTests() =
        assertEquals("4fe2beb189a6791bb0dbe3c0b86d49764f8ab2fda391eef01eba7326458e634c", sha256Hex("hostKeyHostKeyHostKey22"))

    @Test fun textIsHashedAsUtf8() =
        assertEquals("050f1df0e75f1755413f496ba8ecf9f43590a100192d774b0bb431a6f1e6b237", sha256Hex("דדסק"))

    // 55 and 56 bytes are where the length field stops fitting in the same block; 64 is a full block.
    @Test fun paddingEdgeLengths() {
        assertEquals("9f4390f8d30c2dd92ec9f095b65e2b9ae9b0a925a5258e241c9f1e910f734318", sha256Hex("a".repeat(55)))
        assertEquals("b35439a4ac6f0948b6d6f9e3c6af0f5f590ce20f1bde7090ef7970686ec6738a", sha256Hex("a".repeat(56)))
        assertEquals("ffe054fe7ae0cb6dc65c3af9b61d5209f439851db43d0ba5997337df154668eb", sha256Hex("a".repeat(64)))
    }
}

class ShareIdsTest {
    private val counting = RandomBytes { n -> ByteArray(n) { it.toByte() } }

    @Test fun idsAre22UrlSafeCharacters() {
        val id = ShareIds(counting).newGameId()
        assertEquals(22, id.length)
        assertTrue(id.all { it.isLetterOrDigit() || it == '-' || it == '_' }, id)
    }

    @Test fun allBitsAreUsed() {
        assertEquals("AAECAwQFBgcICQoLDA0ODw", ShareIds(counting).newHostKey())
    }

    @Test fun differentRandomnessGivesDifferentIds() {
        var next = 0
        val ids = ShareIds { n -> ByteArray(n) { (next++).toByte() } }
        assertNotEquals(ids.newGameId(), ids.newGameId())
    }
}

class SharedGameTest {
    private val details = GameWithDetails(
        game = GameEntity(
            id = 7, startedAt = 1_000, endedAt = 9_000, defaultBuyIn = 10000, finished = true,
            voidedDifference = -100, chipValue = 50, roundToWhole = false, dealerPlayerId = 22, currency = "EUR",
            shareId = "ignored", isHost = true, hostKey = "secret",
        ),
        unorderedPlayers = listOf(
            // Joined second and first on purpose: the online copy follows the table order, not the list order.
            PlayerWithBuyIns(
                PlayerEntity(id = 22, gameId = 7, name = "גבמר", finalAmount = 0, net = -10000, chips = 0),
                listOf(BuyInEntity(id = 3, playerId = 22, amount = 10000, createdAt = 5)),
            ),
            PlayerWithBuyIns(
                PlayerEntity(id = 21, gameId = 7, name = "Dan", finalAmount = 25000, net = 10000, chips = 500),
                listOf(
                    BuyInEntity(id = 2, playerId = 21, amount = 5000, createdAt = 8),
                    BuyInEntity(id = 1, playerId = 21, amount = 10000, createdAt = 2),
                ),
            ),
        ),
        unorderedTransfers = listOf(TransferEntity(id = 1, gameId = 7, fromName = "גבמר", toName = "Dan", amount = 10000)),
    )

    @Test fun playersFollowTheTableAndDealerIsAPosition() {
        val shared = details.toSharedGame()
        assertEquals(listOf("Dan", "גבמר"), shared.players.map { it.name })
        assertEquals(1, shared.dealerIndex)
        assertEquals(listOf(SharedBuyIn(10000, 2), SharedBuyIn(5000, 8)), shared.players[0].buyIns)
        assertEquals(SharedTransfer("גבמר", "Dan", 10000), shared.transfers.single())
    }

    @Test fun nothingSecretOrLocalIsUploaded() {
        val json = details.toSharedGame().toJson()
        assertFalse("secret" in json)
        assertFalse("ignored" in json)
        assertFalse("hostKey" in json)
    }

    @Test fun jsonRoundTrips() {
        val shared = details.toSharedGame()
        assertEquals(shared, SharedGame.fromJson(shared.toJson()))
    }

    @Test fun absentOptionalFieldsAreLeftOutNotNull() {
        val json = SharedGame(startedAt = 1, defaultBuyIn = 5000, finished = false, currency = "ILS", players = emptyList()).toJson()
        assertFalse("null" in json, json)
        assertFalse("endedAt" in json)
        assertFalse("dealerIndex" in json)
        assertTrue("\"schemaVersion\":1" in json)
    }

    @Test fun aWholeDocumentIsReadWithItsRevisionAndHost() {
        val doc = details.toSharedGame().toJson().dropLast(1) + ",\"revision\":4,\"hostUid\":\"abc\",\"hostKeyHash\":\"" + "0".repeat(64) + "\"}"
        val online = OnlineGame.fromJson(doc)
        assertEquals(4, online.revision)
        assertEquals("abc", online.hostUid)
        assertEquals(details.toSharedGame(), online.game)
    }

    @Test fun viewerGetsTheScreensGameBackWithTheSameOutcome() {
        val back = details.toSharedGame().toGameWithDetails(shareId = "id22id22id22id22id22ab")
        assertEquals("id22id22id22id22id22ab", back.game.shareId)
        assertFalse(back.game.isHost)
        assertNull(back.game.hostKey)
        assertEquals(listOf("Dan", "גבמר"), back.players.map { it.player.name })
        assertEquals(back.players[1].player.id, back.game.dealerPlayerId)
        assertEquals(details.pot, back.pot)
        assertEquals(details.results(), back.results())
        assertEquals(details.transfers.map { Triple(it.fromName, it.toName, it.amount) }, back.transfers.map { Triple(it.fromName, it.toName, it.amount) })
    }

    @Test fun chipShareModeRoundTrips() {
        val shared = details.toSharedGame().copy(chipsByShare = true)
        assertEquals(shared, SharedGame.fromJson(shared.toJson()))
        assertTrue("\"chipsByShare\":true" in shared.toJson())
    }
}

class ShareLinksTest {
    @kotlin.test.Test
    fun links_have_the_agreed_shape() {
        kotlin.test.assertEquals("https://payup.web.app/g/abc", ShareLinks.viewer("abc"))
        kotlin.test.assertEquals("https://payup.web.app/h/abc#key", ShareLinks.host("abc", "key"))
    }

    @kotlin.test.Test
    fun finds_id_and_key_in_a_host_link() {
        val id = "AbCdEfGhIjKlMnOpQrSt-_"
        val key = "ZyXwVuTsRqPoNmLkJiHg-_"
        kotlin.test.assertEquals(ShareLinks.HostLink(id, key), ShareLinks.parseHostLink(ShareLinks.host(id, key)))
        kotlin.test.assertEquals(ShareLinks.HostLink(id, key), ShareLinks.parseHostLink("take over: ${ShareLinks.host(id, key)} thanks"))
        kotlin.test.assertEquals(null, ShareLinks.parseHostLink(ShareLinks.viewer(id)))
        kotlin.test.assertEquals(null, ShareLinks.parseHostLink("https://payup.web.app/h/$id#short"))
        kotlin.test.assertEquals(null, ShareLinks.parseHostLink("https://payup.web.app/h/$id"))
    }

    @kotlin.test.Test
    fun finds_the_id_in_a_pasted_link() {
        val id = "AbCdEfGhIjKlMnOpQrSt-_"
        kotlin.test.assertEquals(id, ShareLinks.parseViewerId(ShareLinks.viewer(id)))
        kotlin.test.assertEquals(id, ShareLinks.parseViewerId("  Join my game: https://payup.web.app/g/$id?x=1 "))
        kotlin.test.assertEquals(id, ShareLinks.parseViewerId(id))
        kotlin.test.assertEquals(null, ShareLinks.parseViewerId("https://payup.web.app/g/short"))
        kotlin.test.assertEquals(null, ShareLinks.parseViewerId("hello"))
    }
}
