package org.rsmod.content.bosses.dt2

import org.rsmod.api.bosses.spec.*

/** Native cadence owner; retained orb/bite geometry and impact mechanics use the named adapter. */
internal fun leviathanBossSpec(): BossSpec {
    val phases =
        linkedMapOf(LEVIATHAN_INACTIVE to PhaseSpec(LEVIATHAN_INACTIVE, lockMovement = true))
    for (action in LEVIATHAN_SPECIAL_ACTIONS) {
        val name = leviathanActionPhase(action)
        phases[name] = PhaseSpec(name, lockMovement = true)
    }
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

internal val LEVIATHAN_SPECIAL_ACTIONS =
    setOf(
        LeviathanAction.STUNNED,
        LeviathanAction.ROAR,
        LeviathanAction.LIGHTNING_BARRAGE,
        LeviathanAction.SMOKE_BLAST,
    )

internal fun leviathanActionPhase(action: LeviathanAction, rate: Int = 3): String =
    when (action) {
        LeviathanAction.STANDARD_VOLLEY -> leviathanOrdinaryPhase(rate)
        in LEVIATHAN_SPECIAL_ACTIONS -> action.name.lowercase()
        else -> LEVIATHAN_INACTIVE
    }

internal fun leviathanPhaseAction(phase: String): LeviathanAction =
    if (phase.startsWith("ordinary.")) LeviathanAction.STANDARD_VOLLEY
    else
        LEVIATHAN_SPECIAL_ACTIONS.firstOrNull { leviathanActionPhase(it) == phase }
            ?: LeviathanAction.DORMANT

/** The retained spatial axis draw chooses a declared first special without another RNG draw. */
internal val LEVIATHAN_FIRST_SPECIAL_SELECTORS =
    mapOf(
        LeviathanDebrisAxis.EAST_WEST to
            Selector.Rotation(listOf(LeviathanSpecial.LIGHTNING_BARRAGE.name)),
        LeviathanDebrisAxis.NORTH_SOUTH to
            Selector.Rotation(listOf(LeviathanSpecial.SMOKE_BLAST.name)),
    )

/** This is selected before leaving the completed special phase, then ordinary rotation resets. */
internal val LEVIATHAN_NEXT_SPECIAL_SELECTOR =
    Selector.Conditional(
        listOf(
            Condition.InPhase(leviathanActionPhase(LeviathanAction.LIGHTNING_BARRAGE)) to
                LeviathanSpecial.SMOKE_BLAST.name
        ),
        LeviathanSpecial.LIGHTNING_BARRAGE.name,
    )

internal const val LEVIATHAN_INACTIVE = "inactive"
internal const val LEVIATHAN_ORDINARY_ATTACK = "leviathan.ordinary-launch"
