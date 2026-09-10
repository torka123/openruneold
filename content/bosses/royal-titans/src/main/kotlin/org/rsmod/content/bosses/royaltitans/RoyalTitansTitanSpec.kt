package org.rsmod.content.bosses.royaltitans

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.bosses.spec.*

/**
 * Characterizes the draft's first36/subsequent29-tick arena program. The OSRS choice between two
 * and three ordinary actions remains unresolved; this migration does not invent that rule. Platform
 * patrol/arrival and paired hazard geometry remain specialized external handlers.
 */
internal fun royalTitansTitanSpec(element: RoyalElement): BossSpec {
    val id =
        if (element == RoyalElement.FIRE) RoyalTitansAssets.BRANDA_ACTIVE
        else RoyalTitansAssets.ELDRIC_ACTIVE
    fun phase(name: String, ticks: Int, next: String, action: RoyalTitansArenaAction? = null) =
        PhaseSpec(
            name,
            lockMovement = true,
            exitAfter = ticks,
            nextPhase = next,
            entry = action?.name,
        )
    return BossSpec(
        npcTypes = listOf(requireNotNull(RSCM.getReverseMapping(RSCMType.NPC, id))),
        stats = BossStats(attackRate = 7, retaliateOnHit = false),
        abilities =
            RoyalTitansArenaAction.entries.associate {
                it.name to Effect.External("royal-titans.arena-action", it)
            },
        phases =
            linkedMapOf(
                "platform" to PhaseSpec("platform"),
                // Starts in LateCycle; native world deadlines execute before the following MapClock
                // tick.
                "arena-opening" to
                    phase(
                        "arena-opening",
                        RoyalTitansRules.ARENA_OPENING_TICKS - 1,
                        "first-charge",
                    ),
                "first-charge" to
                    phase("first-charge", 14, "first-summon", RoyalTitansArenaAction.CHARGE),
                "first-summon" to
                    phase("first-summon", 7, "first-stomp", RoyalTitansArenaAction.SUMMON),
                "first-stomp" to
                    phase("first-stomp", 7, "extra-stomp", RoyalTitansArenaAction.STOMP),
                "extra-stomp" to phase("extra-stomp", 8, "charge", RoyalTitansArenaAction.STOMP),
                "charge" to phase("charge", 14, "summon", RoyalTitansArenaAction.CHARGE),
                "summon" to phase("summon", 7, "stomp", RoyalTitansArenaAction.SUMMON),
                "stomp" to phase("stomp", 8, "charge", RoyalTitansArenaAction.STOMP),
                "inactive" to PhaseSpec("inactive", lockMovement = true),
            ),
        triggers = emptyList(),
    )
}

internal enum class RoyalTitansArenaAction {
    CHARGE,
    SUMMON,
    STOMP,
}
