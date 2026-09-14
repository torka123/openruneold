package org.rsmod.content.minigames.toa

import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.Selector
import org.rsmod.api.bosses.spec.WeightedRef

private val memory = AkkhaSpecial.MEMORY_BLAST
private val trailing = AkkhaSpecial.TRAILING_ORBS
private val detonate = AkkhaSpecial.DETONATE

// Literal retained pool order, including exclusion of the previous logical special.
private val akkhaSolo =
    mapOf(
        null to akkhaChoice(memory, trailing),
        memory to akkhaChoice(trailing),
        trailing to akkhaChoice(memory),
        detonate to akkhaChoice(memory, trailing),
    )
private val akkhaTeam =
    mapOf(
        null to akkhaChoice(memory, trailing, detonate),
        memory to akkhaChoice(trailing, detonate),
        trailing to akkhaChoice(memory, detonate),
        detonate to akkhaChoice(memory, trailing),
    )
private val soloDouble = listOf(memory, trailing)

private fun akkhaChoice(vararg specials: AkkhaSpecial): Effect.Choose =
    Effect.Choose(
        Selector.WeightedRandom(specials.map { WeightedRef(it.name) }),
        specials.associate { it.name to Effect.NoOp },
    )

internal class AkkhaSpecialHistory(var previous: AkkhaSpecial? = null)

internal fun BossEncounter.akkhaSpecialHistory(): AkkhaSpecialHistory =
    state("toa.akkha-special-history") { AkkhaSpecialHistory() }

/**
 * Resolve the whole ordered packet before launching either payload. The caller records previous at
 * the existing startSpecial boundary AFTER those payloads; selection does not move that edge.
 */
internal fun BossEncounter.selectAkkhaSpecials(
    tick: Int,
    partySize: Int,
    doubleTrouble: Boolean,
): List<AkkhaSpecial> {
    check(!isEnded) { "Akkha encounter has ended." }
    if (doubleTrouble && partySize == 1) return soloDouble
    val choices = if (partySize > 1) akkhaTeam else akkhaSolo
    val first =
        AkkhaSpecial.valueOf(
            checkNotNull(selectBranch(choices.getValue(akkhaSpecialHistory().previous), tick, null))
        )
    if (!doubleTrouble) return listOf(first)
    val second =
        AkkhaSpecial.valueOf(checkNotNull(selectBranch(choices.getValue(first), tick, null)))
    return listOf(first, second)
}
