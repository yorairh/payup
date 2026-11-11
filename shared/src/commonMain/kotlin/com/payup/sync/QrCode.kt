package com.payup.sync

/**
 * A QR code for a short text (a link), made by hand so it works the same on Android and the web with no library.
 * Byte mode, error correction level M, versions 1 to 10 (up to 213 bytes of UTF-8). Layout follows ISO/IEC 18004.
 */
class QrCode private constructor(val size: Int, private val dark: BooleanArray) {
    fun isDark(x: Int, y: Int): Boolean = x in 0 until size && y in 0 until size && dark[y * size + x]

    companion object {
        private const val MAX_VERSION = 10
        private val ECC_PER_BLOCK = intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26)
        private val BLOCKS = intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5)

        /** Longest text [encode] takes, in UTF-8 bytes. */
        const val MAX_BYTES = 213

        /** Encodes [text]; [mask] (0..7) forces a mask pattern and is only for tests, normally the best one is picked. */
        fun encode(text: String, mask: Int? = null): QrCode {
            val bytes = text.encodeToByteArray()
            val version = (1..MAX_VERSION).firstOrNull { fits(bytes.size, it) }
                ?: throw IllegalArgumentException("Text too long for a QR code: ${bytes.size} bytes")
            val codewords = interleave(dataCodewords(bytes, version), version)
            return Builder(version, codewords).build(mask)
        }

        private fun rawModules(version: Int): Int {
            var result = (16 * version + 128) * version + 64
            if (version >= 2) {
                val align = version / 7 + 2
                result -= (25 * align - 10) * align - 55
                if (version >= 7) result -= 36
            }
            return result
        }

        private fun dataCapacity(version: Int) = rawModules(version) / 8 - ECC_PER_BLOCK[version] * BLOCKS[version]

        private fun countBits(version: Int) = if (version < 10) 8 else 16

        private fun fits(length: Int, version: Int) = 4 + countBits(version) + 8 * length <= dataCapacity(version) * 8

        private fun dataCodewords(bytes: ByteArray, version: Int): IntArray {
            val bits = ArrayList<Boolean>()
            fun put(value: Int, count: Int) {
                for (i in count - 1 downTo 0) bits.add((value ushr i) and 1 == 1)
            }
            put(0b0100, 4)
            put(bytes.size, countBits(version))
            bytes.forEach { put(it.toInt() and 0xFF, 8) }
            val capacityBits = dataCapacity(version) * 8
            put(0, minOf(4, capacityBits - bits.size))
            while (bits.size % 8 != 0) bits.add(false)
            val result = IntArray(dataCapacity(version))
            for (i in 0 until bits.size / 8) {
                for (j in 0 until 8) if (bits[i * 8 + j]) result[i] = result[i] or (0x80 ushr j)
            }
            var pad = 0xEC
            for (i in bits.size / 8 until result.size) {
                result[i] = pad
                pad = pad xor (0xEC xor 0x11)
            }
            return result
        }

        /** Adds the error correction to each block and interleaves the blocks, as the standard lays them out. */
        private fun interleave(data: IntArray, version: Int): IntArray {
            val blocks = BLOCKS[version]
            val eccLength = ECC_PER_BLOCK[version]
            val raw = rawModules(version) / 8
            val shortBlocks = blocks - raw % blocks
            val shortLength = raw / blocks
            val divisor = reedSolomonDivisor(eccLength)
            val dataBlocks = ArrayList<IntArray>()
            val eccBlocks = ArrayList<IntArray>()
            var start = 0
            for (i in 0 until blocks) {
                val length = shortLength - eccLength + if (i < shortBlocks) 0 else 1
                val block = data.copyOfRange(start, start + length)
                start += length
                dataBlocks.add(block)
                eccBlocks.add(reedSolomonRemainder(block, divisor))
            }
            val result = ArrayList<Int>()
            for (i in 0 until shortLength - eccLength + 1) {
                dataBlocks.forEach { if (i < it.size) result.add(it[i]) }
            }
            for (i in 0 until eccLength) eccBlocks.forEach { result.add(it[i]) }
            return result.toIntArray()
        }

        private fun gfMultiply(x: Int, y: Int): Int {
            var z = 0
            for (i in 7 downTo 0) {
                z = (z shl 1) xor ((z ushr 7) * 0x11D)
                z = z xor (((y ushr i) and 1) * x)
            }
            return z
        }

        private fun reedSolomonDivisor(degree: Int): IntArray {
            val result = IntArray(degree)
            result[degree - 1] = 1
            var root = 1
            repeat(degree) {
                for (j in result.indices) {
                    result[j] = gfMultiply(result[j], root)
                    if (j + 1 < result.size) result[j] = result[j] xor result[j + 1]
                }
                root = gfMultiply(root, 2)
            }
            return result
        }

        private fun reedSolomonRemainder(data: IntArray, divisor: IntArray): IntArray {
            val result = IntArray(divisor.size)
            for (b in data) {
                val factor = b xor result[0]
                for (i in 0 until result.size - 1) result[i] = result[i + 1]
                result[result.size - 1] = 0
                for (i in result.indices) result[i] = result[i] xor gfMultiply(divisor[i], factor)
            }
            return result
        }
    }

    private class Builder(val version: Int, val codewords: IntArray) {
        val size = version * 4 + 17
        val modules = BooleanArray(size * size)
        val isFunction = BooleanArray(size * size)

        fun build(forcedMask: Int?): QrCode {
            drawFunctionPatterns()
            placeCodewords()
            var best = forcedMask ?: 0
            if (forcedMask == null) {
                var bestPenalty = Int.MAX_VALUE
                for (m in 0..7) {
                    applyMask(m)
                    drawFormatBits(m)
                    val penalty = penalty()
                    if (penalty < bestPenalty) {
                        best = m
                        bestPenalty = penalty
                    }
                    applyMask(m)
                }
            }
            applyMask(best)
            drawFormatBits(best)
            return QrCode(size, modules)
        }

        private fun set(x: Int, y: Int, dark: Boolean) {
            modules[y * size + x] = dark
            isFunction[y * size + x] = true
        }

        private fun drawFunctionPatterns() {
            for (i in 0 until size) {
                set(6, i, i % 2 == 0)
                set(i, 6, i % 2 == 0)
            }
            finder(3, 3)
            finder(size - 4, 3)
            finder(3, size - 4)
            val positions = alignmentPositions()
            val last = positions.size - 1
            for (i in positions.indices) for (j in positions.indices) {
                if (!((i == 0 && j == 0) || (i == 0 && j == last) || (i == last && j == 0))) alignment(positions[i], positions[j])
            }
            drawFormatBits(0)
            drawVersion()
        }

        private fun finder(cx: Int, cy: Int) {
            for (dy in -4..4) for (dx in -4..4) {
                val x = cx + dx
                val y = cy + dy
                if (x in 0 until size && y in 0 until size) {
                    val distance = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                    set(x, y, distance != 2 && distance != 4)
                }
            }
        }

        private fun alignment(cx: Int, cy: Int) {
            for (dy in -2..2) for (dx in -2..2) set(cx + dx, cy + dy, maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1)
        }

        private fun alignmentPositions(): IntArray {
            if (version == 1) return IntArray(0)
            val count = version / 7 + 2
            val step = (version * 4 + count * 2 + 1) / (count * 2 - 2) * 2
            val result = IntArray(count)
            result[0] = 6
            var pos = size - 7
            for (i in count - 1 downTo 1) {
                result[i] = pos
                pos -= step
            }
            return result
        }

        private fun bit(value: Int, i: Int) = (value ushr i) and 1 == 1

        private fun drawFormatBits(mask: Int) {
            val data = mask // error correction level M has format bits 00
            var rem = data
            repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
            val bits = ((data shl 10) or rem) xor 0x5412
            for (i in 0..5) set(8, i, bit(bits, i))
            set(8, 7, bit(bits, 6))
            set(8, 8, bit(bits, 7))
            set(7, 8, bit(bits, 8))
            for (i in 9..14) set(14 - i, 8, bit(bits, i))
            for (i in 0..7) set(size - 1 - i, 8, bit(bits, i))
            for (i in 8..14) set(8, size - 15 + i, bit(bits, i))
            set(8, size - 8, true)
        }

        private fun drawVersion() {
            if (version < 7) return
            var rem = version
            repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
            val bits = (version shl 12) or rem
            for (i in 0..17) {
                val color = bit(bits, i)
                val a = size - 11 + i % 3
                val b = i / 3
                set(a, b, color)
                set(b, a, color)
            }
        }

        private fun placeCodewords() {
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) for (j in 0..1) {
                    val x = right - j
                    val upward = ((right + 1) and 2) == 0
                    val y = if (upward) size - 1 - vert else vert
                    if (!isFunction[y * size + x] && i < codewords.size * 8) {
                        modules[y * size + x] = bit(codewords[i ushr 3], 7 - (i and 7))
                        i++
                    }
                }
                right -= 2
            }
        }

        private fun applyMask(mask: Int) {
            for (y in 0 until size) for (x in 0 until size) {
                val invert = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                if (invert && !isFunction[y * size + x]) modules[y * size + x] = !modules[y * size + x]
            }
        }

        /** The standard's four penalty rules, so the mask with the fewest hard-to-scan patterns wins. */
        private fun penalty(): Int {
            var total = 0
            fun at(x: Int, y: Int) = modules[y * size + x]
            for (line in 0 until size) for (horizontal in listOf(true, false)) {
                fun cell(i: Int) = if (horizontal) at(i, line) else at(line, i)
                var run = 1
                for (i in 1 until size) {
                    if (cell(i) == cell(i - 1)) {
                        run++
                        if (run == 5) total += 3 else if (run > 5) total++
                    } else run = 1
                }
                for (i in 0..size - 11) {
                    val a = (0 until 11).all { cell(i + it) == FINDER_LIKE[it] }
                    val b = (0 until 11).all { cell(i + it) == FINDER_LIKE_REVERSED[it] }
                    if (a) total += 40
                    if (b) total += 40
                }
            }
            for (y in 0 until size - 1) for (x in 0 until size - 1) {
                val c = at(x, y)
                if (c == at(x + 1, y) && c == at(x, y + 1) && c == at(x + 1, y + 1)) total += 3
            }
            val darkCount = modules.count { it }
            val all = size * size
            val deviation = kotlin.math.abs(darkCount * 20 - all * 10)
            total += ((deviation + all - 1) / all - 1) * 10
            return total
        }
    }
}

private val FINDER_LIKE = booleanArrayOf(true, false, true, true, true, false, true, false, false, false, false)
private val FINDER_LIKE_REVERSED = FINDER_LIKE.reversedArray()
