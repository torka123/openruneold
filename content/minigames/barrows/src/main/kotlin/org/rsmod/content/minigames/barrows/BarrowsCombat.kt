package org.rsmod.content.minigames.barrows

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttackOverride
import org.rsmod.api.combat.NpcProjectile
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.heal
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.stat.statDrain
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onNpcHit
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** One explicit NPC-type registration covers all Barrows spawn locations. */
internal class BarrowsCombat @Inject constructor(
    private val combat: NvPCombat,
) : PluginScript() {

    override fun ScriptContext.startup() {
        definitions.forEach { definition -> register(definition) }
    }

    private fun ScriptContext.register(definition: BrotherDefinition) {
        val npcType =
            requireNotNull(ServerCacheManager.getNpc(definition.npcInternal.asRSCM(RSCMType.NPC))) {
                "Missing Barrows npc definition: ${definition.npcInternal}"
            }
        onAiOpPlayer2(npcType) {
            combat.attack(this, it.target, definition.attack, definition.overrideFactory(this, it.target))
        }
        onAiApPlayer2(npcType) {
            combat.attack(this, it.target, definition.attack, definition.overrideFactory(this, it.target))
        }
        onNpcHit(npcType) {
            if (npc.hitpoints > 0) npc.anim(definition.blockAnimation)
        }
    }

    private data class BrotherDefinition(
        val npcInternal: String,
        val attack: CombatAttack.NpcAttack,
        val blockAnimation: String,
        val overrideFactory: StandardNpcAccess.(Player) -> NpcAttackOverride,
    )

    private val definitions =
        listOf(
            BrotherDefinition(
                "npc.barrows_ahrim",
                CombatAttack.NpcMagic(maxHit = 20),
                "seq.barrows_quarterstaff_defend",
            ) { target -> ahrimOverride(target) },
            BrotherDefinition(
                "npc.barrows_dharok",
                CombatAttack.NpcMelee(MeleeAttackType.Crush),
                "seq.human_unarmedblock",
            ) { _ ->
                NpcAttackOverride(
                    attackAnimation = "seq.barrow_dharok_crush",
                    maxHit = dharokMaxHit(npc),
                )
            },
            BrotherDefinition(
                "npc.barrows_guthan",
                CombatAttack.NpcMelee(MeleeAttackType.Crush),
                "seq.human_spear_block",
            ) { _ -> guthanOverride() },
            BrotherDefinition(
                "npc.barrows_karil",
                CombatAttack.NpcRanged(RangedAttackType.Standard),
                "seq.human_unarmedblock",
            ) { target -> karilOverride(target) },
            BrotherDefinition(
                "npc.barrows_torag",
                CombatAttack.NpcMelee(MeleeAttackType.Crush),
                "seq.human_unarmedblock",
            ) { target -> toragOverride(target) },
            BrotherDefinition(
                "npc.barrows_verac",
                CombatAttack.NpcMelee(MeleeAttackType.Crush),
                "seq.human_unarmedblock",
            ) { target -> veracOverride(target) },
        )

    private fun StandardNpcAccess.ahrimOverride(target: Player): NpcAttackOverride =
        NpcAttackOverride(
            attackAnimation = "seq.barrows_quarterstaff_attack",
            maxHit = 20,
            onImpact = { _, impact ->
                if (impact.damage > 0) {
                    val curse =
                        when (random.of(3)) {
                            0 -> "stat.attack"
                            1 -> "stat.strength"
                            else -> "stat.defence"
                        }
                    target.statDrain(curse, constant = 0, percent = 5)
                    if (random.of(5) == 0) {
                        target.statSub("stat.strength", constant = 5, percent = 0)
                        target.spotanim("spotanim.barrows_ahirm_blighted_aura")
                    }
                }
            },
        )

    private fun StandardNpcAccess.guthanOverride(): NpcAttackOverride {
        val infestation = random.of(4) == 0
        return NpcAttackOverride(
            attackAnimation = "seq.barrows_war_spear_stab",
            maxHit = 24,
            onImpact =
                if (infestation) {
                    { source, impact ->
                        if (impact.damage > 0) {
                            source.heal(impact.damage, showHitsplat = true)
                            source.spotanim("spotanim.barrows_guthan_effect")
                        }
                    }
                } else {
                    null
                },
        )
    }

    private fun StandardNpcAccess.karilOverride(target: Player): NpcAttackOverride =
        NpcAttackOverride(
            attackAnimation = "seq.barrows_repeating_crossbow_fire",
            maxHit = 20,
            projectile = karilProjectile(),
            onImpact = { _, impact ->
                if (impact.damage > 0 && random.of(4) == 0) {
                    target.statDrain("stat.agility", constant = 0, percent = 20)
                    target.spotanim("spotanim.barrows_karil_tainted_shot")
                }
            },
        )

    private fun StandardNpcAccess.toragOverride(target: Player): NpcAttackOverride =
        NpcAttackOverride(
            attackAnimation = "seq.barrow_torag_crush",
            maxHit = 24,
            onImpact = { _, impact ->
                if (impact.damage > 0 && random.of(4) == 0) {
                    target.runEnergy = (target.runEnergy * 80) / 100
                    UpdateRun.energy(target, target.runEnergy)
                    target.spotanim("spotanim.barrows_torag_effect")
                }
            },
        )

    private fun StandardNpcAccess.veracOverride(target: Player): NpcAttackOverride {
        val defiler = random.of(4) == 0
        val protected = target.vars["varbit.prayer_protectfrommelee"] == 1
        return NpcAttackOverride(
            attackAnimation = "seq.barrow_guthan_crush",
            maxHit = if (defiler && protected) 15 else 23,
            forceAccurate = defiler,
            hitModifier =
                if (defiler) NpcBypassProtectionPrayerPlayerHitModifier else StandardPlayerHitModifier,
            onImpact =
                if (defiler) {
                    { _, impact ->
                        if (impact.damage > 0) {
                            target.spotanim("spotanim.barrows_verac_desolation")
                        }
                    }
                } else {
                    null
                },
        )
    }

    private fun karilProjectile(): NpcProjectile =
        NpcProjectile(
            travelSpotanim = "spotanim.barrows_repeating_crossbow_fire_spotanim",
            type =
                requireNotNull(
                    ServerCacheManager.getProjectiles()[
                        "projanim.bolt".asRSCM(RSCMType.PROJANIM)
                    ],
                ),
        )

    private fun dharokMaxHit(npc: Npc): Int {
        val missingHitpoints = (npc.baseHitpointsLvl - npc.hitpoints).coerceIn(0, npc.baseHitpointsLvl)
        return (DHAROK_BASE_MAX_HIT * (100 + missingHitpoints)) / 100
    }

    private companion object {
        private const val DHAROK_BASE_MAX_HIT = 29
    }
}
