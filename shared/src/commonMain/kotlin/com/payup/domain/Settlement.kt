package com.payup.domain

import kotlin.math.abs

/** A payment of [amount] from player index [from] to player index [to]. */
data class Transfer(val from: Int, val to: Int, val amount: Int)

object Settlement {

    /** Above this many non-zero players the exact search gets too expensive and we fall back to greedy. */
    private const val EXACT_LIMIT = 18

    /**
     * Makes [nets] sum to zero by shrinking the over-claimed side ("void").
     *
     * - Missing money (sum < 0): losers owe more than winners are owed, so losses are reduced.
     * - Extra money (sum > 0): winners are owed more than losers owe, so gains are reduced.
     *
     * The reduction is proportional to each player's amount; rounding leftovers go to the
     * largest fractional parts (ties: larger amounts first).
     */
    fun applyVoid(nets: List<Int>): List<Int> {
        val sum = nets.sum()
        if (sum == 0) return nets
        val shrinkPositives = sum > 0
        val side = nets.indices.filter { if (shrinkPositives) nets[it] > 0 else nets[it] < 0 }
        val sideTotal = side.sumOf { abs(nets[it]).toLong() }
        val target = sideTotal - abs(sum) // what that side should total after the void
        val result = nets.toMutableList()
        if (target <= 0) {
            side.forEach { result[it] = 0 }
            return result
        }
        val scaled = side.map { i ->
            val numerator = abs(nets[i]).toLong() * target
            Triple(i, numerator / sideTotal, numerator % sideTotal)
        }
        var leftover = target - scaled.sumOf { it.second }
        val order = scaled.sortedWith(
            compareByDescending<Triple<Int, Long, Long>> { it.third }.thenByDescending { abs(nets[it.first]) }
        )
        val bonus = HashSet<Int>()
        for (entry in order) {
            if (leftover <= 0) break
            bonus += entry.first
            leftover--
        }
        for ((i, base, _) in scaled) {
            val magnitude = (base + if (i in bonus) 1 else 0).toInt()
            result[i] = if (shrinkPositives) magnitude else -magnitude
        }
        return result
    }

    /**
     * Returns the transfers that settle [nets] (positive = is owed money, negative = owes money).
     * [nets] must sum to zero.
     *
     * The result uses the fewest transfers possible: players are split into the largest number of
     * independent zero-sum groups, and each group of k players settles with k-1 transfers. A loser
     * whose debt exactly matches a winner's gain pays only that one winner.
     */
    fun settle(nets: List<Int>): List<Transfer> {
        require(nets.sum() == 0) { "Nets must sum to zero, got ${nets.sum()}" }
        val active = nets.indices.filter { nets[it] != 0 }
        if (active.isEmpty()) return emptyList()
        val groups = if (active.size <= EXACT_LIMIT) {
            maxZeroSumGroups(active.map { nets[it] }).map { group -> group.map { active[it] } }
        } else {
            exactPairsThenRest(active, nets)
        }
        return groups.flatMap { settleGroup(it, nets) }
    }

    /**
     * Splits [values] (summing to zero) into the maximum number of zero-sum groups.
     * Bitmask DP: best[mask] = most complete groups formed when adding the members of mask one by one.
     */
    private fun maxZeroSumGroups(values: List<Int>): List<List<Int>> {
        val n = values.size
        val full = (1 shl n) - 1
        val sums = LongArray(1 shl n)
        for (mask in 1..full) {
            val low = mask.countTrailingZeroBits()
            sums[mask] = sums[mask and (mask - 1)] + values[low]
        }
        val best = IntArray(1 shl n)
        for (mask in 1..full) {
            var top = 0
            var bits = mask
            while (bits != 0) {
                val i = bits.countTrailingZeroBits()
                bits = bits and (bits - 1)
                top = maxOf(top, best[mask xor (1 shl i)])
            }
            best[mask] = top + if (sums[mask] == 0L) 1 else 0
        }

        // Walk back from the full set; every time the remaining set sums to zero, a group is complete.
        val groups = mutableListOf<List<Int>>()
        var current = mutableListOf<Int>()
        var mask = full
        while (mask != 0) {
            if (sums[mask] == 0L && current.isNotEmpty()) {
                groups += current
                current = mutableListOf()
            }
            val gain = if (sums[mask] == 0L) 1 else 0
            var bits = mask
            while (bits != 0) {
                val i = bits.countTrailingZeroBits()
                bits = bits and (bits - 1)
                if (best[mask xor (1 shl i)] + gain == best[mask]) {
                    current += i
                    mask = mask xor (1 shl i)
                    break
                }
            }
        }
        if (current.isNotEmpty()) groups += current
        return groups
    }

    /** Fallback for very large tables: settle exact debtor/creditor matches as their own pairs. */
    private fun exactPairsThenRest(active: List<Int>, nets: List<Int>): List<List<Int>> {
        val remaining = active.toMutableList()
        val groups = mutableListOf<List<Int>>()
        val iterator = active.iterator()
        while (iterator.hasNext()) {
            val i = iterator.next()
            if (i !in remaining || nets[i] >= 0) continue
            val match = remaining.firstOrNull { nets[it] == -nets[i] }
            if (match != null) {
                groups += listOf(i, match)
                remaining.remove(i)
                remaining.remove(match)
            }
        }
        if (remaining.isNotEmpty()) groups += remaining
        return groups
    }

    /** Greedy within a zero-sum group: largest debtor pays largest creditor. Gives k-1 transfers at most. */
    private fun settleGroup(group: List<Int>, nets: List<Int>): List<Transfer> {
        val balance = group.associateWith { nets[it] }.toMutableMap()
        val transfers = mutableListOf<Transfer>()
        while (true) {
            val debtor = balance.filterValues { it < 0 }.minByOrNull { it.value } ?: break
            val creditor = balance.filterValues { it > 0 }.maxByOrNull { it.value } ?: break
            val amount = minOf(-debtor.value, creditor.value)
            transfers += Transfer(debtor.key, creditor.key, amount)
            balance[debtor.key] = debtor.value + amount
            balance[creditor.key] = creditor.value - amount
        }
        return transfers
    }
}
