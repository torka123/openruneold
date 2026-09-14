package org.rsmod.content.bosses.dt2

import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.Selector
import org.rsmod.api.bosses.spec.WeightedRef
import org.rsmod.api.random.GameRandom

private val headUnlocked =
    Condition.HpBelow(
        Dt2Rules.VARDORVIS_HEAD_UNLOCK_HITPOINTS.toDouble() / Dt2Rules.VARDORVIS_NORMAL_HITPOINTS
    )

// Stable identities give the native branch selector its own state, separate from ordinary melee.
private val specials = specialChoice(includeStrangle = true)
private val afterStrangle = specialChoice(includeStrangle = false)

private fun specialChoice(includeStrangle: Boolean): Effect.Choose {
    val entries = buildList {
        add(WeightedRef(VardorvisSpecial.DARTING_SPIKES.name))
        add(WeightedRef(VardorvisSpecial.HEAD_GAZE.name, requires = headUnlocked))
        if (includeStrangle)
            add(WeightedRef(VardorvisSpecial.STRANGLE.name, requires = headUnlocked))
    }
    // These branches select a named environmental payload; its geometry runs at the retained
    // native queue boundary after the subsequent jitter draw, not during ordinary attack dispatch.
    return Effect.Choose(
        Selector.WeightedRandom(entries),
        entries.associate { it.ability to Effect.NoOp },
    )
}

/** Logical selection history precedes the existing locked-HP geometry fallback. */
internal class VardorvisSpecialHistory(var previous: VardorvisSpecial? = null)

internal fun BossEncounter.vardorvisSpecialHistory(): VardorvisSpecialHistory =
    state("vardorvis.special-history") { VardorvisSpecialHistory() }

internal fun BossEncounter.selectVardorvisSpecial(tick: Int, requested: String?): VardorvisSpecial {
    val history = vardorvisSpecialHistory()
    val explicit =
        requested?.let { key -> VardorvisSpecial.entries.firstOrNull { it.name.equals(key, true) } }
    val selected =
        explicit
            ?: VardorvisSpecial.valueOf(
                checkNotNull(
                    selectBranch(
                        if (history.previous == VardorvisSpecial.STRANGLE) afterStrangle
                        else specials,
                        tick,
                        null,
                    )
                )
            )
    val resolved =
        if (selected == VardorvisSpecial.STRANGLE && history.previous == VardorvisSpecial.STRANGLE)
            VardorvisSpecial.DARTING_SPIKES
        else selected
    history.previous = resolved
    return resolved
}

/** Preserves the old MAX-bound draw before native weighted selection reduces it to its bucket. */
internal class VardorvisSelectionRandom(private val source: Dt2SeededRng) : GameRandom {
    override fun of(maxExclusive: Int): Int {
        require(maxExclusive > 0)
        return source.nextInt(Int.MAX_VALUE) % maxExclusive
    }

    override fun of(minInclusive: Int, maxInclusive: Int): Int {
        require(minInclusive <= maxInclusive)
        val width = maxInclusive.toLong() - minInclusive + 1
        return (minInclusive + source.nextInt(Int.MAX_VALUE).toLong() % width).toInt()
    }

    override fun randomDouble(): Double = source.nextInt(Int.MAX_VALUE).toDouble() / Int.MAX_VALUE
}
