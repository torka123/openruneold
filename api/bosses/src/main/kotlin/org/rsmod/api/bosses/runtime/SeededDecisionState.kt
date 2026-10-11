package org.rsmod.api.bosses.runtime

import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Selector

/** The named outcome of one stream in the most recently accepted opportunity. */
data class DecisionResult(val choice: String, val rolledChoice: String?, val drew: Boolean)

/**
 * Stages only the opt-in seeded decision streams. Legacy selector cursors/forced-ability queues
 * retain their established semantics. Reads expose committed state, including during hit rolls.
 */
internal class SeededDecisionState(val seed: Long) {
    private data class Stream(
        val salt: Long,
        val state: Long,
        val draws: Long = 0,
        val previous: String? = null,
        val cooldowns: Map<String, Int> = emptyMap(),
    )

    private class Opportunity(
        val choices: Map<String, String>,
        val streams: MutableMap<String, Stream>,
    ) {
        var accepted = false
        val results = mutableMapOf<String, DecisionResult>()
    }

    private val streams = mutableMapOf<String, Stream>()
    private var results: Map<String, DecisionResult> = emptyMap()
    private var inputTick: Int? = null
    private var inputs: Map<String, String> = emptyMap()
    private var opportunity: Opportunity? = null
    val isActive: Boolean
        get() = opportunity != null

    fun prepare(tick: Int, choices: Map<String, String>) {
        check(!isActive) { "Cannot replace inputs during a decision opportunity." }
        inputTick = tick
        inputs = choices.toMap()
    }

    fun begin(tick: Int) {
        check(!isActive) { "A decision opportunity is already active." }
        opportunity =
            Opportunity(if (inputTick == tick) inputs else emptyMap(), streams.toMutableMap())
    }

    fun accept() {
        opportunity?.accepted = true
    }

    fun finish(eligible: Boolean): Boolean {
        val pending = opportunity ?: return false
        opportunity = null
        if (!eligible || !pending.accepted) return false
        streams.clear()
        streams.putAll(pending.streams)
        results = pending.results.toMap()
        return true
    }

    fun result(stream: String): DecisionResult? = results[stream]

    fun drawCount(stream: String): Long = streams[stream]?.draws ?: 0

    fun selectScheduled(
        selector: Selector.SeededWeighted,
        tick: Int,
        eligible: (Condition) -> Boolean,
    ): DecisionResult? {
        check(!isActive) { "A decision opportunity is already active." }
        val previousResults = results
        opportunity = Opportunity(emptyMap(), streams.toMutableMap())
        try {
            val choice = select(selector, tick, eligible) ?: return null
            accept()
            finish(true)
            return results.getValue(selector.stream).also { check(it.choice == choice) }
        } finally {
            finish(false)
            results = previousResults
        }
    }

    fun select(
        selector: Selector.SeededWeighted,
        tick: Int,
        eligible: (Condition) -> Boolean,
    ): String? {
        val pending =
            checkNotNull(opportunity) { "Seeded choices require a native decision opportunity." }
        val stream =
            pending.streams[selector.stream] ?: Stream(selector.salt, seed xor selector.salt)
        require(stream.salt == selector.salt) {
            "Decision stream '${selector.stream}' changed salt."
        }
        val entries = selector.entriesAfterPrevious[stream.previous] ?: selector.entries
        val available =
            entries.filter { ref ->
                (stream.cooldowns[ref.ability]?.let { tick - it >= ref.cooldown } != false) &&
                    eligible(ref.requires)
            }
        val prepared = selector.preparedChoice
        val replacement = prepared?.let { pending.choices[it.input] }
        require(replacement == null || available.any { it.ability == replacement }) {
            "Prepared choice '$replacement' is not eligible in stream '${selector.stream}'."
        }
        if (available.isEmpty()) return null
        val draw = replacement == null || prepared?.drawBeforeOverride == true
        val nextState = if (draw) stream.state + INCREMENT else stream.state
        val rolled =
            if (draw) {
                var mixed = nextState
                mixed = (mixed xor (mixed ushr 30)) * MIX_ONE
                mixed = (mixed xor (mixed ushr 27)) * MIX_TWO
                mixed = mixed xor (mixed ushr 31)
                var roll = ((mixed ushr 1) % available.sumOf { it.weight }.toLong()).toInt()
                available
                    .first { ref ->
                        roll -= ref.weight
                        roll < 0
                    }
                    .ability
            } else null
        val choice = replacement ?: checkNotNull(rolled)
        pending.streams[selector.stream] =
            stream.copy(
                state = nextState,
                draws = stream.draws + if (draw) 1 else 0,
                previous = choice,
                cooldowns = stream.cooldowns + (choice to tick),
            )
        pending.results[selector.stream] = DecisionResult(choice, rolled, draw)
        return choice
    }

    fun clear() {
        opportunity = null
        streams.clear()
        results = emptyMap()
        inputTick = null
        inputs = emptyMap()
    }

    private companion object {
        const val INCREMENT = -7046029254386353131L
        const val MIX_ONE = -4658895280553007687L
        const val MIX_TWO = -7723592293110705685L
    }
}
