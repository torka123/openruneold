package org.rsmod.content.bosses.dt2

import org.rsmod.api.bosses.spec.*

/** Native cadence owner; retained orb/bite geometry and impact mechanics use the named adapter. */
internal fun leviathanBossSpec(): BossSpec {
    val phases =
        linkedMapOf(LEVIATHAN_INACTIVE to PhaseSpec(LEVIATHAN_INACTIVE, lockMovement = true))
    for (rate in 1..3) {
        val name = leviathanOrdinaryPhase(rate)
        phases[name] =
            PhaseSpec(
                name,
                lockMovement = true,
                attackRate = rate,
                selector = Selector.Rotation(listOf("ordinary")),
            )
    }
    return BossSpec(
        npcTypes = listOf(Dt2Boss.LEVIATHAN.normalNpc),
        stats = BossStats(attackRate = 3, aggressionRadius = Dt2Rules.LEVIATHAN_AP_RANGE),
        abilities = mapOf("ordinary" to Effect.External(LEVIATHAN_ORDINARY_ATTACK)),
        phases = phases,
        triggers = emptyList(),
    )
}

internal fun leviathanOrdinaryPhase(rate: Int): String = "ordinary.$rate"

internal const val LEVIATHAN_INACTIVE = "inactive"
internal const val LEVIATHAN_ORDINARY_ATTACK = "leviathan.ordinary-launch"
