package org.rsmod.content.bosses.yama

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.bosses.spec.*

/**
 * Native cadence and counted action order. Retains the characterized YamaRules constants; ordinary
 * multi-recipient launches and their RNG order remain in the existing NvPCombat adapter. This is an
 * architecture migration, not a change to the retained revision-235 mechanics.
 */
internal fun yamaBossSpec(
    npcType: String = requireNotNull(RSCM.getReverseMapping(RSCMType.NPC, YamaAssets.BOSS))
): BossSpec {
    val abilities = linkedMapOf<String, Effect>()
    val phases =
        linkedMapOf(YAMA_INACTIVE_PHASE to PhaseSpec(YAMA_INACTIVE_PHASE, lockMovement = true))
    val standard = Effect.External(YAMA_STANDARD_ATTACK)
    for (contract in YamaContract.entries) for (phase in 1..3) {
        val key = yamaCombatPhase(phase, contract)
        val normalName = "$key.normal"
        val specialName = "$key.special"
        val regulars = YamaRules.profile(contract).regularsBeforeSpecial
        val normal =
            if (phase == 3)
                Effect.Sequence(listOf(standard, Effect.External(YAMA_PHASE_THREE_FOLLOWUP)))
            else standard
        abilities[normalName] = normal
        val specials =
            when (phase) {
                1 ->
                    listOf(
                        YamaSpecial.GLYPH_ATTACK,
                        YamaSpecial.SHADOW_WAVES,
                        YamaSpecial.FIRE_STREAKS,
                        YamaSpecial.VOID_FLARES,
                    )
                2 ->
                    listOf(
                        YamaSpecial.METEOR_STRIKE,
                        YamaSpecial.SHADOW_STOMP,
                        YamaSpecial.VOID_FLARES,
                    )
                else -> listOf(YamaSpecial.SHADOW_WAVES, YamaSpecial.VOID_FLARES)
            }
        val special =
            Effect.Choose(
                Selector.Rotation(specials.map { it.name }),
                specials.associate { it.name to Effect.External(YAMA_SPECIAL_ATTACK, it) },
            )
        abilities[specialName] =
            if (phase == 3) Effect.Sequence(listOf(normal, special)) else special
        phases[key] =
            PhaseSpec(
                key,
                lockMovement = phase == 3,
                attackRate = YamaRules.attackRate(phase, contract),
                selector = Selector.Rotation(listOf(normalName)),
                forceAbilities =
                    listOf(
                        ForcedAbility(
                            0,
                            specialName,
                            regulars,
                            regulars,
                            firstAttackCount = if (phase == 3) regulars else 1,
                        )
                    ),
            )
    }
    return BossSpec(
        npcTypes = listOf(npcType),
        stats =
            BossStats(
                attackRate = YamaRules.NORMAL_ATTACK_RATE,
                aggressionRadius = YamaRules.ACTIVE_FIGHT_RANGE,
            ),
        abilities = abilities,
        phases = phases,
        triggers = emptyList(),
    )
}

internal fun yamaCombatPhase(phase: Int, contract: YamaContract): String = "${contract.name}.$phase"

internal const val YAMA_INACTIVE_PHASE = "inactive"
internal const val YAMA_STANDARD_ATTACK = "yama.standard-attack"
internal const val YAMA_SPECIAL_ATTACK = "yama.special-attack"
internal const val YAMA_PHASE_THREE_FOLLOWUP = "yama.phase-three-followup"
