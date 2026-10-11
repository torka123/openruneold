package org.rsmod.content.bosses.cerberus

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttackOverride
import org.rsmod.api.combat.NpcHitParameters
import org.rsmod.api.combat.formulas.attributes.collector.DamageReductionAttributeCollector
import org.rsmod.api.combat.formulas.maxhit.MaxHitOperations
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.hit.modifier.NpcCalculatedProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.output.soundSynth
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitValidity
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid

/** Geometry/distribution/presentation only. The native combat pipeline launches and owns hits. */
internal class CerberusAttackParameters
@Inject
constructor(
    private val encounters: CerberusEncounterManager,
    private val reductions: DamageReductionAttributeCollector,
    private val bonuses: WornBonuses,
) {
    fun resolve(npc: Npc, target: Player, attack: CerberusHeadAttack): NpcAttackOverride {
        val style = attack.style
        val prayer =
            when (style) {
                CerberusAttackStyle.MAGIC -> "varbit.prayer_protectfrommagic"
                CerberusAttackStyle.RANGED -> "varbit.prayer_protectfrommissiles"
                CerberusAttackStyle.MELEE -> "varbit.prayer_protectfrommelee"
            }
        val (x, z) =
            CerberusRules.bossProjectileSource(
                npc.coords.x,
                npc.coords.z,
                npc.visType.size,
                target.coords.x,
                target.coords.z,
                attack.head,
            )
        val projectile =
            when (style) {
                CerberusAttackStyle.MAGIC ->
                    ProjAnim.fromNpcToPlayer(
                        npc,
                        target,
                        spot(CerberusAssets.MAGIC_PROJECTILE_SPOTANIM),
                        CerberusAssets.MAGIC_PROJECTILE,
                    )
                CerberusAttackStyle.RANGED ->
                    ProjAnim.fromNpcToPlayer(
                        npc,
                        target,
                        spot(CerberusAssets.RANGED_PROJECTILE_SPOTANIM),
                        CerberusAssets.RANGED_PROJECTILE,
                    )
                CerberusAttackStyle.MELEE -> null
            }?.copy(startCoord = CoordGrid(x, z, npc.coords.level))
        val impactSpot =
            when (style) {
                CerberusAttackStyle.MAGIC -> CerberusAssets.MAGIC_IMPACT_SPOTANIM
                CerberusAttackStyle.RANGED -> CerberusAssets.RANGED_IMPACT_SPOTANIM
                CerberusAttackStyle.MELEE -> null
            }
        return NpcAttackOverride(
            playAttackFx = false,
            projectileFactory = projectile?.let { { _, _ -> it } },
            impactDelay = projectile?.serverCycles?.coerceAtLeast(1) ?: 1,
            hitModifier =
                NpcCalculatedProtectionPrayerPlayerHitModifier(target.vars[prayer] == 1, 100),
            hitValidity = HitValidity { encounters.isActiveTarget(npc, target) },
            hits =
                listOf(
                    NpcHitParameters(
                        damageRoll = { maximum, accurate, random ->
                            if (!accurate) 0
                            else {
                                val defenceBonus =
                                    when (style) {
                                        CerberusAttackStyle.MELEE ->
                                            bonuses.defensiveStabBonus(target)
                                        CerberusAttackStyle.MAGIC ->
                                            bonuses.defensiveMagicBonus(target)
                                        CerberusAttackStyle.RANGED ->
                                            bonuses.defensiveRangedBonus(target)
                                    }
                                val reduced =
                                    MaxHitOperations.applyDamageReductions(
                                        maximum,
                                        defenceBonus,
                                        reductions.collectNvP(target, random),
                                    )
                                random.of(reduced + 1)
                            }
                        }
                    )
                ),
            onAttack = {
                npc.facePlayer(target)
                if (style == CerberusAttackStyle.MAGIC) {
                    npc.spotanim(spot(CerberusAssets.MAGIC_CAST_SPOTANIM))
                    target.soundSynth(CerberusAssets.MAGIC_CAST_SOUND)
                }
            },
            onImpact = { _, impact ->
                if (impactSpot != null && target.isSlotAssigned && target.hitpoints > 0) {
                    target.spotanim(spot(impactSpot))
                    if (style == CerberusAttackStyle.MAGIC)
                        target.soundSynth(CerberusAssets.MAGIC_IMPACT_SOUND)
                }
                if (style == CerberusAttackStyle.MELEE && impact.damage > 0)
                    encounters.recordMeleeImpact(npc)
            },
        )
    }

    private fun spot(id: Int): Int {
        CerberusSpotanimValidation.requireLoaded(id)
        return id
    }
}
