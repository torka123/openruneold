package org.rsmod.api.bosses.spec

sealed interface Selector {
    data class WeightedRandom(
        val entries: List<WeightedRef> = emptyList(),
        val noRepeatBias: Double = 0.5,
    ) : Selector {
        constructor(
            vararg entries: WeightedRef,
            noRepeatBias: Double = 0.5,
        ) : this(entries.toList(), noRepeatBias)
    }

    data class Rotation(val sequence: List<String>) : Selector

    /**
     * Independent encounter-seeded choices, committed only after an accepted native launch. Entry
     * order and weights are explicit; previous-outcome weights never use combat RNG. Initialize the
     * encounter's decision seed before executing this selector.
     */
    data class SeededWeighted(
        val stream: String,
        val salt: Long,
        val entries: List<WeightedRef>,
        val entriesAfterPrevious: Map<String, List<WeightedRef>> = emptyMap(),
        val preparedChoice: PreparedChoice? = null,
    ) : Selector {
        init {
            require(stream.isNotBlank()) { "Decision stream must be named." }
            for (weights in listOf(entries) + entriesAfterPrevious.values) {
                require(weights.isNotEmpty() && weights.all { it.weight > 0 }) {
                    "Seeded choices require positive weights."
                }
                require(weights.all { it.ability.isNotBlank() && it.cooldown >= 0 }) {
                    "Seeded choices must be named with non-negative cooldowns."
                }
                require(weights.sumOf { it.weight.toLong() } <= Int.MAX_VALUE) {
                    "Seeded choice weight sum exceeds the supported range."
                }
                require(weights.map { it.ability }.distinct().size == weights.size) {
                    "Seeded choice names must be unique."
                }
            }
        }
    }

    /**
     * Repeats a selected ordinary ability on subsequent eligible opportunities, not on a second
     * timeline. Forced specials retain their normal precedence and do not consume repetitions.
     * Ineligible opportunities clear the continuation and select the fallback without RNG.
     */
    data class RepeatSelected(
        val delegate: Selector,
        val repeatedAbility: String,
        val extraSelections: Int,
        val eligible: Condition,
        val ineligibleAbility: String,
        val sameTarget: Boolean = true,
    ) : Selector {
        init {
            require(extraSelections >= 0) { "Additional selection count must not be negative." }
            require(repeatedAbility.isNotBlank() && ineligibleAbility.isNotBlank()) {
                "Continuation and fallback abilities must be named."
            }
        }
    }

    /** Keeps a ranged/magic script in order while allowing adjacent targets to receive melee. */
    data class MeleeOverrideRotation(val meleeAbility: String, val sequence: List<String>) :
        Selector

    data class Conditional(val branches: List<Pair<Condition, String>>, val fallback: String) :
        Selector
}

/** A tick-scoped, declared choice name supplied by an existing spatial mechanic. */
data class PreparedChoice(val input: String, val drawBeforeOverride: Boolean) {
    init {
        require(input.isNotBlank()) { "Prepared choice input must be named." }
    }
}

data class WeightedRef(
    val ability: String,
    val weight: Int = 1,
    val cooldown: Int = 0,
    val requires: Condition = Condition.Always,
)

public data class AbilityRef(public val name: String)

public data class PhaseRef(public val name: String)

infix fun String.weight(w: Int): WeightedRef = WeightedRef(this, weight = w)

infix fun AbilityRef.weight(w: Int): WeightedRef = WeightedRef(name, weight = w)

infix fun WeightedRef.cooldown(t: Int): WeightedRef = copy(cooldown = t)

infix fun WeightedRef.requires(c: Condition): WeightedRef = copy(requires = c)
