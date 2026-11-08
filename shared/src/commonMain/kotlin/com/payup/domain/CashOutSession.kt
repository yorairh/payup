package com.payup.domain

/**
 * A player at the table. [finalAmount] and [chips] are what was already stored for them and are only read
 * by [CashOutSession.start] to restore a half-finished cash-out.
 */
data class CashOutPlayer(
    val id: Long,
    val name: String,
    val totalIn: Int,
    val finalAmount: Int? = null,
    val chips: Int? = null,
)

/** A value the cash-out wants stored; the caller saves it, the session never touches the database. */
sealed interface CashOutWrite {
    data class FinalAmount(val playerId: Long, val amount: Int?) : CashOutWrite
    data class Chips(val playerId: Long, val chips: Int?) : CashOutWrite
    data class ChipSettingsChanged(val chipValue: Int?, val roundToWhole: Boolean) : CashOutWrite
}

/** The session after an event, and what to store because of it (in order). */
data class CashOutStep(val session: CashOutSession, val writes: List<CashOutWrite> = emptyList())

/**
 * Everything that happens while the players type what they finished with: typed amounts, chip counts, chip mode,
 * filling in the last blank, and re-deriving everyone's money from chips. Immutable: every event returns a new
 * session plus the values to store.
 */
data class CashOutSession(
    private val players: List<CashOutPlayer>,
    private val pot: Int,
    private val inputs: Map<Long, String>,
    private val chipInputs: Map<Long, String>,
    private val chips: ChipSettings,
    /** The player whose amount was filled in by the app, until they edit it themselves. */
    private val autoFilledId: Long?,
) {
    val state: CashOutState
        get() = CashOutState(
            rows = players.map {
                CashOutRow(
                    playerId = it.id,
                    name = it.name,
                    totalIn = it.totalIn,
                    input = inputs[it.id].orEmpty(),
                    chipsInput = chipInputs[it.id].orEmpty(),
                    autoFilled = it.id == autoFilledId,
                )
            },
            pot = pot,
            chips = chips,
        )

    /** The table changed underneath (players or pot reloaded); what was typed is kept. */
    fun withTable(players: List<CashOutPlayer>, pot: Int): CashOutSession = copy(players = players, pot = pot)

    fun typed(playerId: Long, text: String): CashOutStep {
        val clean = Money.sanitizeInput(text)
        val next = copy(
            inputs = inputs + (playerId to clean),
            autoFilledId = autoFilledId.takeUnless { it == playerId },
        )
        return CashOutStep(next, listOf(CashOutWrite.FinalAmount(playerId, Money.parseAgorot(clean))))
    }

    fun typedChips(playerId: Long, text: String): CashOutStep {
        val clean = text.filter(Char::isDigit).take(MAX_CHIP_DIGITS)
        val next = copy(
            chipInputs = chipInputs + (playerId to clean),
            autoFilledId = autoFilledId.takeUnless { it == playerId },
        )
        return next.recalculatedFromChips().after(CashOutWrite.Chips(playerId, clean.toIntOrNull()))
    }

    fun setChipMode(enabled: Boolean) = changeChipSettings { it.copy(enabled = enabled) }

    fun setChipValue(text: String) = changeChipSettings { it.copy(valueText = Money.sanitizeInput(text)) }

    fun setRoundToWhole(round: Boolean) = changeChipSettings { it.copy(roundToWhole = round) }

    private fun changeChipSettings(change: (ChipSettings) -> ChipSettings): CashOutStep {
        val settings = change(chips)
        val write = CashOutWrite.ChipSettingsChanged(settings.value.takeIf { settings.enabled }, settings.roundToWhole)
        return copy(chips = settings).recalculatedFromChips().after(write)
    }

    /**
     * Called when the user is done with a field (focus left it). If that was the second-to-last player,
     * the one left blank gets what remains of the pot. Done once: later edits never change it.
     */
    fun fieldDone(playerId: Long): CashOutStep {
        val rows = state.rows
        if (chips.enabled) {
            // Chips are whole numbers, so the last count is only filled when the pot divides into chips.
            val chipValue = chips.value ?: return CashOutStep(this)
            if (rows.firstOrNull { it.playerId == playerId }?.chipsInput.isNullOrBlank()) return CashOutStep(this)
            if (pot % chipValue != 0) return CashOutStep(this)
            val counts = rows.map { chipInputs[it.playerId]?.toIntOrNull() }
            val (index, count) = AutoFill.lastFieldRemainder(pot / chipValue, counts) ?: return CashOutStep(this)
            val target = rows[index].playerId
            val next = copy(chipInputs = chipInputs + (target to count.toString()), autoFilledId = target)
            return next.recalculatedFromChips().after(CashOutWrite.Chips(target, count))
        }
        if (rows.firstOrNull { it.playerId == playerId }?.input.isNullOrBlank()) return CashOutStep(this)
        val (index, amount) = AutoFill.lastFieldRemainder(pot, rows.map { it.amount }) ?: return CashOutStep(this)
        val target = rows[index].playerId
        val next = copy(inputs = inputs + (target to Money.format(amount)), autoFilledId = target)
        return CashOutStep(next, listOf(CashOutWrite.FinalAmount(target, amount)))
    }

    /** Every player's amount to store before settling; an empty field counts as 0. */
    fun finalWrites(): List<CashOutWrite> = state.rows.map { CashOutWrite.FinalAmount(it.playerId, it.amount ?: 0) }

    /**
     * In chip mode the money is always derived from the chips (blank chips = nothing), and re-derived for
     * everyone since rounding can move a shekel between players.
     */
    private fun recalculatedFromChips(): CashOutStep {
        if (!chips.enabled) return CashOutStep(this)
        val counts = players.map { chipInputs[it.id]?.toIntOrNull() }
        val chipValue = chips.value
        val converted: List<Int?> = if (chipValue == null) {
            players.map { null }
        } else {
            ChipConversion.convert(counts, players.map { it.totalIn }, chipValue, chips.roundToWhole)
        }
        val next = copy(inputs = inputs + players.indices.map { players[it].id to (converted[it]?.let(Money::format) ?: "") })
        return CashOutStep(next, players.indices.map { CashOutWrite.FinalAmount(players[it].id, converted[it]) })
    }

    private fun CashOutStep.after(first: CashOutWrite) = CashOutStep(session, listOf(first) + writes)

    companion object {
        private const val MAX_CHIP_DIGITS = 7

        /**
         * A fresh cash-out for the table, restoring what was stored. Chip settings come from the game if it was
         * counted in chips, else (switched off) from the last game that was: [lastChipSettings] is (value, round).
         */
        fun start(
            players: List<CashOutPlayer>,
            pot: Int,
            savedChipValue: Int?,
            savedRoundToWhole: Boolean,
            lastChipSettings: Pair<Int, Boolean>?,
        ): CashOutSession {
            val chips = when {
                savedChipValue != null ->
                    ChipSettings(enabled = true, valueText = Money.format(savedChipValue), roundToWhole = savedRoundToWhole)
                lastChipSettings != null ->
                    ChipSettings(enabled = false, valueText = Money.format(lastChipSettings.first), roundToWhole = lastChipSettings.second)
                else -> ChipSettings()
            }
            return CashOutSession(
                players = players,
                pot = pot,
                inputs = players.associate { it.id to (it.finalAmount?.let(Money::format) ?: "") },
                chipInputs = players.associate { it.id to (it.chips?.toString() ?: "") },
                chips = chips,
                autoFilledId = null,
            )
        }
    }
}
