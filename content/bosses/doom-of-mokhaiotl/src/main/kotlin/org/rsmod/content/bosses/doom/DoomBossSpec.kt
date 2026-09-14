package org.rsmod.content.bosses.doom

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
 * Retains DoomRules' characterized constants and the existing three-stream selection policy.
 * Ordinary launches and their cadence are native DSL effects; spatial rock volleys stay owned by
 * the encounter handler. This does not assert new OSRS timing or damage evidence.
 */
internal fun doomBossSpec(): BossSpec {
    val abilities = linkedMapOf<String, Effect>()
    for (delve in 1..8) {
        val maxHit = DoomRules.standardMaxHit(delve)
        for (style in DoomAttackStyle.entries) {
            val attack =
                when (style) {
                    DoomAttackStyle.MELEE -> CombatAttack.NpcMelee(MeleeAttackType.Crush)
                    DoomAttackStyle.RANGED -> CombatAttack.NpcRanged(RangedAttackType.Standard)
                    DoomAttackStyle.MAGIC -> CombatAttack.NpcMagic(maxHit)
                }
            val gfx =
                when (style) {
                    DoomAttackStyle.MELEE -> DoomAssets.GFX_MELEE_PROJECTILE
                    DoomAttackStyle.RANGED -> DoomAssets.GFX_RANGED_PROJECTILE
                    DoomAttackStyle.MAGIC -> DoomAssets.GFX_MAGIC_PROJECTILE
                }
            abilities[doomAbility(delve, DoomPrimaryAttack.STANDARD, style)] =
                Effect.Sequence(
                    listOf(
                        Effect.SetAttackRate(DoomRules.attackRate(delve)),
                        Effect.NpcAttack(
                            attack = attack,
                            maxHit = maxHit,
                            animation = doomSequence(DoomAssets.ANIM_STANDARD),
                            projectile =
                                NpcProjectile(
                                    doomSpot(gfx),
                                    requireNotNull(
                                        ServerCacheManager.getProjectile(
                                            (if (style == DoomAttackStyle.RANGED) "projanim.arrow"
                                                else "projanim.magic_spell")
                                                .asRSCM()
                                        )
                                    ),
                                ),
                            impactDelay = DoomRules.standardProjectileTravelTicks(delve),
                            parameters = DOOM_ATTACK_PARAMETERS,
                            parameterData = DoomPrimaryAttack.STANDARD,
                            survivesSourceDeath = true,
                        ),
                    )
                )
        }
        abilities[doomAbility(delve, DoomPrimaryAttack.TONGUE)] =
            Effect.Sequence(
                listOf(
                    Effect.SetAttackRate(DoomRules.tongueAttackRate(delve)),
                    Effect.NpcAttack(
                        attack = CombatAttack.NpcMelee(MeleeAttackType.Crush),
                        animation = doomSequence(DoomAssets.ANIM_TONGUE),
                        maxHit = 40,
                        protectionPrayer = ProtectionPrayer.Reduce(50),
                        parameters = DOOM_ATTACK_PARAMETERS,
                        parameterData = DoomPrimaryAttack.TONGUE,
                        survivesSourceDeath = true,
                    ),
                )
            )
        abilities[doomAbility(delve, DoomPrimaryAttack.ROCKS)] =
            Effect.Sequence(
                listOf(
                    Effect.SetAttackRate(DoomRules.attackRate(delve)),
                    Effect.External(DOOM_ROCK_ATTACK),
                )
            )
    }
    return BossSpec(
        npcTypes =
            listOf(DoomAssets.BOSS, DoomAssets.SHIELDED, DoomAssets.BURROWED).map {
                requireNotNull(RSCM.getReverseMapping(RSCMType.NPC, it))
            },
        stats = BossStats(attackRate = 1),
        abilities = abilities,
        // Only the existing encounter's admitted, prepared turn can force a named ability.
        phases = mapOf("active" to PhaseSpec("active", lockMovement = true)),
        triggers = emptyList(),
    )
}

internal fun doomAbility(
    delve: Int,
    primary: DoomPrimaryAttack,
    style: DoomAttackStyle = DoomAttackStyle.RANGED,
): String =
    "${delve.coerceIn(1, 8)}.$primary" +
        if (primary == DoomPrimaryAttack.STANDARD) ".$style" else ""

internal const val DOOM_ATTACK_PARAMETERS = "doom.native-launch"
internal const val DOOM_ROCK_ATTACK = "doom.rock-volley"

private fun doomSequence(id: Int) = requireNotNull(RSCM.getReverseMapping(RSCMType.SEQ, id))

private fun doomSpot(id: Int) = requireNotNull(RSCM.getReverseMapping(RSCMType.SPOTANIM, id))
