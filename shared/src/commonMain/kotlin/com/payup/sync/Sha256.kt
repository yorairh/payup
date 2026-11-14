package com.payup.sync

import kotlin.math.cbrt
import kotlin.math.floor
import kotlin.math.sqrt

private val PRIMES = generateSequence(2) { it + 1 }.filter { n -> (2 until n).none { n % it == 0 } }.take(64).toList()

/** The first 32 bits of the fractional part of [x], which is how SHA-256 defines its constants. */
private fun fractionBits(x: Double): Int = ((x - floor(x)) * 4294967296.0).toLong().toInt()

/** Round constants: fractional parts of the cube roots of the first 64 primes. */
private val K = IntArray(64) { fractionBits(cbrt(PRIMES[it].toDouble())) }

private fun Int.ror(n: Int) = (this ushr n) or (this shl (32 - n))

/** SHA-256 of [text] (as UTF-8) in lower-case hex, like the rules' `hashing.sha256(...).toHexString().lower()`. */
fun sha256Hex(text: String): String {
    val message = text.encodeToByteArray()
    val bitLength = message.size.toLong() * 8
    val padded = ByteArray(((message.size + 8) / 64 + 1) * 64)
    message.copyInto(padded)
    padded[message.size] = 0x80.toByte()
    for (i in 0 until 8) padded[padded.size - 1 - i] = (bitLength ushr (8 * i)).toByte()

    // Start values: fractional parts of the square roots of the first 8 primes.
    val h = IntArray(8) { fractionBits(sqrt(PRIMES[it].toDouble())) }
    val w = IntArray(64)
    for (block in padded.indices step 64) {
        for (t in 0 until 16) {
            val o = block + t * 4
            w[t] = ((padded[o].toInt() and 0xff) shl 24) or ((padded[o + 1].toInt() and 0xff) shl 16) or
                ((padded[o + 2].toInt() and 0xff) shl 8) or (padded[o + 3].toInt() and 0xff)
        }
        for (t in 16 until 64) {
            val s0 = w[t - 15].ror(7) xor w[t - 15].ror(18) xor (w[t - 15] ushr 3)
            val s1 = w[t - 2].ror(17) xor w[t - 2].ror(19) xor (w[t - 2] ushr 10)
            w[t] = w[t - 16] + s0 + w[t - 7] + s1
        }
        var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
        var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
        for (t in 0 until 64) {
            val s1 = e.ror(6) xor e.ror(11) xor e.ror(25)
            val ch = (e and f) xor (e.inv() and g)
            val t1 = hh + s1 + ch + K[t] + w[t]
            val s0 = a.ror(2) xor a.ror(13) xor a.ror(22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val t2 = s0 + maj
            hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
        }
        h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += hh
    }
    return buildString { for (word in h) for (shift in 28 downTo 0 step 4) append("0123456789abcdef"[(word ushr shift) and 0xf]) }
}
