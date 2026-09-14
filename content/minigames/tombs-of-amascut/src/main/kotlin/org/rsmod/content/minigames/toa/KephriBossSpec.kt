package org.rsmod.content.minigames.toa

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.spec.*
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/** Native LateCycle opportunities retain the established spatial bomb, dung and egg launchers. */
internal fun registerKephriBossCombat(
    context: ScriptContext,
    deps: BossDeps,
    canTarget: (Npc, Player) -> Boolean,
    launch: (Npc, Player, KephriAttackKind, Int) -> Unit,
    modifyHit: NpcHitEvents.Modify.() -> Unit,
) {
    for (kind in KephriAttackKind.entries) {
        deps.extensionRegistry.register(kephriHandler(kind)) { _, npc, target, _ ->
            val encounter = deps.encounterRegistry.of(npc)
            // Prepared choices opt into post-launch commits. The old invocation counter was
            // incremented before resolving the launch profile and reset at each shield break.
            launch(npc, target, kind, encounter.phaseAbilityCount + 1)
            encounter.markDecisionLaunchAccepted()
        }
    }
    BossCombat.register(
        context,
        kephriBossSpec(),
        deps,
        ownerSlug = "toa-kephri",
        canTarget = canTarget,
        onModifyHit = modifyHit,
    )
}

/** KephriAttackCycle's retained bomb counts, logical alternation and 6/13-tick cadence. */
internal fun kephriBossSpec(): BossSpec {
    fun launch(kind: KephriAttackKind): Effect =
        Effect.Sequence(
            listOf(
                Effect.SetAttackRate(
                    if (kind == KephriAttackKind.DUNG_STRIKE) KephriAttackCycle.DUNG_DELAY
                    else KephriAttackCycle.NORMAL_DELAY
                ),
                Effect.External(kephriHandler(kind)),
            )
        )
    val dung = launch(KephriAttackKind.DUNG_STRIKE)
    val incubation =
        Effect.Choose(
            Selector.SeededWeighted(
                stream = KEPHRI_INCUBATION_STREAM,
                salt = 0x4B4550485249L,
                entries = listOf(WeightedRef(KEPHRI_INCUBATION), WeightedRef(KEPHRI_DUNG)),
                preparedChoice = PreparedChoice(KEPHRI_AGILE_INPUT, drawBeforeOverride = false),
            ),
            mapOf(
                KEPHRI_INCUBATION to launch(KephriAttackKind.MASS_INCUBATION),
                KEPHRI_DUNG to dung,
            ),
        )
    val abilities =
        mapOf(
            KEPHRI_BOMB to launch(KephriAttackKind.BOMB),
            KEPHRI_DUNG to dung,
            KEPHRI_SPECIAL to
                Effect.Choose(
                    Selector.Rotation(listOf(KEPHRI_DUNG, KEPHRI_INCUBATION)),
                    mapOf(KEPHRI_DUNG to dung, KEPHRI_INCUBATION to incubation),
                ),
        )
    val phases = linkedMapOf(KEPHRI_INACTIVE to PhaseSpec(KEPHRI_INACTIVE, lockMovement = true))
    for (shield in 0..KephriEncounter.FINAL_PHASE) {
        val final = shield == KephriEncounter.FINAL_PHASE
        val first =
            when (shield) {
                0 -> KephriAttackCycle.INITIAL_BOMBS
                1 -> 4 // Retained finishKephriRecovery first-shield count.
                else -> KephriAttackCycle.FINAL_FIRST_DUNG_BOMBS
            }
        val repeat =
            if (final) KephriAttackCycle.FINAL_REPEAT_DUNG_BOMBS
            else KephriAttackCycle.BETWEEN_SHIELDED_SPECIALS
        val phase = kephriCombatPhase(shield)
        phases[phase] =
            PhaseSpec(
                phase,
                lockMovement = true,
                attackRate = KephriAttackCycle.NORMAL_DELAY,
                selector = Selector.Rotation(listOf(KEPHRI_BOMB)),
                forceAbilities =
                    listOf(
                        ForcedAbility(
                            period = 0,
                            ability = if (final) KEPHRI_DUNG else KEPHRI_SPECIAL,
                            attackMin = repeat,
                            attackMax = repeat,
                            firstAttackCount = first,
                        )
                    ),
            )
    }
    return BossSpec(
        npcTypes =
            (KephriEncounter.ACTIVE..KephriEncounter.DEAD).map {
                requireNotNull(RSCM.getReverseMapping(RSCMType.NPC, it))
            },
        stats = BossStats(attackRate = KephriAttackCycle.NORMAL_DELAY),
        abilities = abilities,
        phases = phases,
        triggers = emptyList(),
    )
}

internal fun kephriCombatPhase(shield: Int): String {
    require(shield in 0..KephriEncounter.FINAL_PHASE)
    return "shield.$shield"
}

private fun kephriHandler(kind: KephriAttackKind) = "toa.kephri.${kind.name.lowercase()}"

internal const val KEPHRI_INACTIVE = "inactive"
private const val KEPHRI_BOMB = "bomb"
private const val KEPHRI_SPECIAL = "special"
internal const val KEPHRI_DUNG = "dung"
internal const val KEPHRI_INCUBATION = "incubation"
internal const val KEPHRI_AGILE_INPUT = "kephri.agile-incubation"
internal const val KEPHRI_INCUBATION_STREAM = "kephri.incubation-override"
