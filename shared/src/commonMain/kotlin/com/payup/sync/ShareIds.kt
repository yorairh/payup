package com.payup.sync

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Cryptographically secure random bytes; each platform supplies its own generator. */
fun interface RandomBytes {
    fun next(count: Int): ByteArray
}

/** The unguessable id of a shared game and the secret host key: 128 random bits each, as 22 URL-safe characters. */
@OptIn(ExperimentalEncodingApi::class)
class ShareIds(private val random: RandomBytes) {
    fun newGameId(): String = token()

    fun newHostKey(): String = token()

    private fun token(): String = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(random.next(16))
}
