package org.rsmod.content.minigames.toa

import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.Selector
import org.rsmod.api.bosses.spec.WeightedRef
import org.rsmod.api.random.GameRandom

private val rockfall = BabaSpecial.ROCKFALL
private val throwRock = BabaSpecial.ROCK_THROW
private val shockwave = BabaSpecial.SHOCKWAVE
private val discharge = BabaSpecial.BABOON_DISCHARGE

// Entry order is the retained BabaSpecialSchedule order. Stable branch identity is separate
// from ordinary attack selection; even a singleton retains its native bounded RNG draw.
private val babaWithRubble =
    mapOf(
        null to babaChoice(rockfall, shockwave, discharge),
        rockfall to babaChoice(shockwave, discharge),
        throwRock to babaChoice(rockfall, shockwave, discharge),
        shockwave to babaChoice(rockfall, discharge),
        discharge to babaChoice(rockfall, shockwave),
    )
private val babaWithoutRubble =
    mapOf(
        null to babaChoice(shockwave, discharge),
        rockfall to babaChoice(shockwave, discharge),
        throwRock to babaChoice(shockwave, discharge),
        shockwave to babaChoice(discharge),
        discharge to babaChoice(shockwave),
    )

private fun babaChoice(vararg specials: BabaSpecial): Effect.Choose =
    Effect.Choose(
        Selector.WeightedRandom(specials.map { WeightedRef(it.name) }),
        specials.associate { it.name to Effect.NoOp },
    )

internal class BabaSpecialHistory(var previous: BabaSpecial? = null)

internal fun BossEncounter.babaSpecialHistory(): BabaSpecialHistory =
    state("toa.baba-special-history") { BabaSpecialHistory() }

/** Called only for a normal special opportunity; forced Rockfall and its Throw never call it. */
internal fun BossEncounter.selectBabaSpecial(tick: Int, allowRockfall: Boolean): BabaSpecial {
    check(!isEnded) { "Ba-Ba encounter has ended." }
    val history = babaSpecialHistory()
    val choices = if (allowRockfall) babaWithRubble else babaWithoutRubble
    val selected =
        BabaSpecial.valueOf(
            checkNotNull(selectBranch(choices.getValue(history.previous), tick, null))
        )
    history.previous = selected
    return selected
}

/**
 * Install at FIRST EncounterRegistry.of with BabaEncounter::nextInt or AkkhaEncounter::nextInt.
 * Weighted branches call of(bound) directly, preserving the existing stream shared with geometry.
 */
internal class ToaSpecialSelectionRandom(private val nextInt: (Int) -> Int) : GameRandom {
    override fun of(maxExclusive: Int): Int = nextInt(maxExclusive)

    override fun of(minInclusive: Int, maxInclusive: Int): Int {
        val width = maxInclusive.toLong() - minInclusive + 1
        require(width in 1..Int.MAX_VALUE.toLong()) { "Unsupported bounded selection interval." }
        return minInclusive + nextInt(width.toInt())
    }

    override fun randomDouble(): Double = nextInt(Int.MAX_VALUE).toDouble() / Int.MAX_VALUE
}
