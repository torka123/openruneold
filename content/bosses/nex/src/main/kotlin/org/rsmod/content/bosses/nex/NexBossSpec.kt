package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.bosses.spec.*
import org.rsmod.api.combat.NpcProjectile
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType

/**
 * One ordinary-combat owner for all Nex forms. This preserves characterized draft attacks;
 * presentation discrepancies are tracked in NEX_OSRS_AUDIT.md. Group specials remain named
 * handlers, never a second ordinary selector or attack clock.
 */
internal fun nexBossSpec(): BossSpec {
    val abilities = linkedMapOf<String, Effect>()
    val phases = linkedMapOf<String, PhaseSpec>()
    for (state in NexState.entries) {
        // These identify vulnerable minions, not additional Nex combat phases.
        if (state in setOf(NexState.FUMUS, NexState.UMBRA, NexState.CRUOR, NexState.GLACIES))
            continue
        if (state !in NEX_COMBAT_STATES) {
            phases[state.name] = PhaseSpec(state.name, lockMovement = true)
            continue
        }
        val final = state == NexState.ZAROS
        val melee =
            Effect.NpcAttack(
                attack = CombatAttack.NpcMelee(MeleeAttackType.Crush),
                maxHit = if (final) 30 else 27,
                animation = nexSequence(9180),
                protectionPrayer = ProtectionPrayer.Reduce(if (final) 66 else 80),
                parameters = NEX_ATTACK_PARAMETERS,
                parameterData = NexNormalAttack(state, NexNormalStyle.MELEE),
            )
        val ranged = state == NexState.SHADOW
        val normal =
            Effect.NpcAttack(
                attack =
                    if (ranged) CombatAttack.NpcRanged(RangedAttackType.Standard)
                    else CombatAttack.NpcMagic(33),
                maxHit = if (ranged) 60 else 33,
                animation = nexSequence(if (final) 9188 else 9189),
                projectile =
                    NpcProjectile(
                        nexSpot(
                            when (state) {
                                NexState.SMOKE -> 1997
                                NexState.BLOOD -> 2000
                                NexState.ICE -> 2004
                                NexState.ZAROS -> 2007
                                else -> 1999
                            }
                        ),
                        requireNotNull(
                            ServerCacheManager.getProjectile(
                                (if (ranged) "projanim.arrow" else "projanim.magic_spell").asRSCM()
                            )
                        ),
                    ),
                protectionPrayer =
                    if (ranged) ProtectionPrayer.Reduce(50) else ProtectionPrayer.Standard,
                parameters = NEX_ATTACK_PARAMETERS,
                parameterData =
                    NexNormalAttack(
                        state,
                        if (ranged) NexNormalStyle.RANGED else NexNormalStyle.MAGIC,
                    ),
                survivesSourceDeath = true,
            )
        // One shared Choose instance for all twelve Zaros wrapper abilities retains a chain
        // across overhead changes. Branch selection must not count a second normal attack.
        val choice =
            Effect.Choose(
                Selector.RepeatSelected(
                    delegate =
                        Selector.WeightedRandom(
                            WeightedRef("melee", 1),
                            WeightedRef("distance", 2),
                        ),
                    repeatedAbility = "melee",
                    extraSelections = 2,
                    eligible = Condition.WithinMeleeRange,
                    ineligibleAbility = "distance",
                ),
                mapOf(
                    "melee" to melee,
                    "distance" to
                        if (ranged) normal
                        else
                            Effect.Sequence(
                                listOf(normal, Effect.External(NEX_MAGIC_SPLASH, state))
                            ),
                ),
            )
        val normalName = "${state.name}.normal"
        abilities[normalName] =
            Effect.Sequence(listOf(Effect.SetAttackRate(NEX_ATTACK_RATE), choice))
        if (final) {
            val rotation =
                listOf(NexOverhead.SOUL_SPLIT, NexOverhead.DEFLECT_MELEE, NexOverhead.NONE)
                    .flatMap { overhead ->
                        val name = "ZAROS.$overhead"
                        abilities[name] =
                            Effect.Sequence(
                                listOf(
                                    Effect.External(NEX_OVERHEAD, overhead),
                                    Effect.Run(normalName),
                                )
                            )
                        List(4) { name }
                    }
            phases[state.name] = PhaseSpec(state.name, selector = Selector.Rotation(rotation))
        } else {
            val specialName = "${state.name}.special"
            val special = NexRules.isPhase(state)
            if (special)
                abilities[specialName] =
                    Effect.Sequence(listOf(Effect.SetAttackRate(5), Effect.External(NEX_SPECIAL)))
            phases[state.name] =
                PhaseSpec(
                    state.name,
                    selector = Selector.Rotation(listOf(normalName)),
                    forceAbilities =
                        if (special) listOf(ForcedAbility(0, specialName, 5, 5)) else emptyList(),
                )
        }
    }
    return BossSpec(
        npcTypes = (11278..11282).map { requireNotNull(RSCM.getReverseMapping(RSCMType.NPC, it)) },
        stats = BossStats(attackRate = NEX_ATTACK_RATE, aggressionRadius = 10),
        abilities = abilities,
        phases = phases,
        triggers = emptyList(),
    )
}

internal data class NexNormalAttack(val phase: NexState, val style: NexNormalStyle)

internal enum class NexNormalStyle {
    MELEE,
    MAGIC,
    RANGED,
}

internal val NEX_COMBAT_STATES =
    setOf(NexState.SMOKE, NexState.SHADOW, NexState.BLOOD, NexState.ICE, NexState.ZAROS)
internal const val NEX_ATTACK_PARAMETERS = "nex.normal-impact"
internal const val NEX_MAGIC_SPLASH = "nex.magic-splash"
internal const val NEX_SPECIAL = "nex.special"
internal const val NEX_OVERHEAD = "nex.overhead"

private fun nexSequence(id: Int) = requireNotNull(RSCM.getReverseMapping(RSCMType.SEQ, id))

private fun nexSpot(id: Int) = requireNotNull(RSCM.getReverseMapping(RSCMType.SPOTANIM, id))
