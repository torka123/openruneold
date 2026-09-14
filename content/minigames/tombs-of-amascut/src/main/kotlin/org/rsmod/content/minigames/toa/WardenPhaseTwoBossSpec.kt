package org.rsmod.content.minigames.toa

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.bosses.spec.*
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

internal enum class WardenPhaseTwoAttackKind {
    MELEE,
    MAGIC,
    RANGED,
    SPECIAL,
}

/**
 * Native body selection, with the existing projectile and special payloads supplied by the raid.
 */
internal fun registerWardenPhaseTwoBossCombat(
    context: ScriptContext,
    deps: BossDeps,
    canTarget: (Npc, Player) -> Boolean,
    launch: (Npc, Player, WardenPhaseTwoAttackKind, Int) -> Unit,
    modifyHit: NpcHitEvents.Modify.() -> Unit,
    onAbilityCommitted: (Npc, Player, BossEncounter) -> Unit = { _, _, _ -> },
) {
    for (kind in WardenPhaseTwoAttackKind.entries) {
        deps.extensionRegistry.register(wardenPhaseTwoHandler(kind)) { _, npc, target, _ ->
            val encounter = deps.encounterRegistry.of(npc)
            val phases =
                requireNotNull(
                    encounter.stateOrNull<WardenPhaseTwoCombatPhases>(WARDEN_PHASE_TWO_COMBAT)
                )
            check(phases.ownsDispatch(target))
            launch(npc, target, kind, phases.actionCount + 1)
            encounter.markDecisionLaunchAccepted()
        }
    }
    BossCombat.register(
        context,
        wardenPhaseTwoBossSpec(),
        deps,
        ownerSlug = "toa-wardens-phase-two",
        canTarget = canTarget,
        onModifyHit = modifyHit,
        onAbilityCommitted = { npc, target, encounter ->
            onAbilityCommitted(npc, target, encounter)
        },
    )
}

/** Four body forms share fourteen static residues of the retained seventh-attack/parity cycle. */
internal fun wardenPhaseTwoBossSpec(): BossSpec {
    fun emit(kind: WardenPhaseTwoAttackKind): Effect = Effect.External(wardenPhaseTwoHandler(kind))
    val abilities =
        linkedMapOf<String, Effect>(
            WARDEN_PHASE_TWO_SPECIAL to emit(WardenPhaseTwoAttackKind.SPECIAL)
        )
    val phases = linkedMapOf<String, PhaseSpec>()
    for (offset in 0 until WARDEN_PHASE_TWO_OFFSETS) {
        val basic = "basic.$offset"
        val distant =
            Effect.Choose(
                Selector.Conditional(
                    branches =
                        listOf(Condition.AbilityCountModulo(2, (1 - offset).mod(2)) to "magic"),
                    fallback = "ranged",
                ),
                mapOf(
                    "magic" to emit(WardenPhaseTwoAttackKind.MAGIC),
                    "ranged" to emit(WardenPhaseTwoAttackKind.RANGED),
                ),
            )
        abilities[basic] =
            Effect.Choose(
                Selector.SeededWeighted(
                    stream = WARDEN_PHASE_TWO_DISTANCE_STREAM,
                    salt = 0x57415244454EL,
                    entries =
                        listOf(
                            WeightedRef(WARDEN_PHASE_TWO_NEAR),
                            WeightedRef(WARDEN_PHASE_TWO_FAR),
                        ),
                    preparedChoice =
                        PreparedChoice(WARDEN_PHASE_TWO_DISTANCE_INPUT, drawBeforeOverride = false),
                ),
                mapOf(
                    WARDEN_PHASE_TWO_NEAR to emit(WardenPhaseTwoAttackKind.MELEE),
                    WARDEN_PHASE_TWO_FAR to distant,
                ),
            )
        val phase = wardenPhaseTwoCombatPhase(offset)
        phases[phase] =
            PhaseSpec(
                phase,
                lockMovement = true,
                selector =
                    Selector.Conditional(
                        branches =
                            listOf(
                                Condition.AbilityCountModulo(7, (6 - offset).mod(7)) to
                                    WARDEN_PHASE_TWO_SPECIAL
                            ),
                        fallback = basic,
                    ),
            )
    }
    return BossSpec(
        npcTypes =
            WARDEN_PHASE_TWO_FORMS.map { requireNotNull(RSCM.getReverseMapping(RSCMType.NPC, it)) },
        stats = BossStats(attackRate = 8),
        abilities = abilities,
        phases = phases,
        triggers = emptyList(),
    )
}

internal fun wardenPhaseTwoCombatPhase(offset: Int): String {
    require(offset in 0 until WARDEN_PHASE_TWO_OFFSETS)
    return "body.$offset"
}

private fun wardenPhaseTwoHandler(kind: WardenPhaseTwoAttackKind) =
    "toa.warden-phase-two.${kind.name.lowercase()}"

internal val WARDEN_PHASE_TWO_FORMS =
    setOf(
        ElidinisWarden.MAGIC_FORM,
        ElidinisWarden.RANGED_FORM,
        TumekenWarden.RANGED_FORM,
        TumekenWarden.MAGIC_FORM,
    )
internal const val WARDEN_PHASE_TWO_COMBAT = "toa.warden-phase-two-combat"
internal const val WARDEN_PHASE_TWO_OFFSETS = 14
internal const val WARDEN_PHASE_TWO_DISTANCE_INPUT = "warden-phase-two.base-distance"
internal const val WARDEN_PHASE_TWO_DISTANCE_STREAM = "warden-phase-two.base-distance"
internal const val WARDEN_PHASE_TWO_NEAR = "near"
internal const val WARDEN_PHASE_TWO_FAR = "far"
private const val WARDEN_PHASE_TWO_SPECIAL = "special"
