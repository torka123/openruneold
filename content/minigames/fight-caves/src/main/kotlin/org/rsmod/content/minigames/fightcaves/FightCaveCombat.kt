package org.rsmod.content.minigames.fightcaves

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
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Combat and death authority for every revision-240 Fight Caves creature. */
internal class FightCaveCombat
@Inject
constructor(
    private val combat: NvPCombat,
    private val runs: FightCaveRunManager,
    private val death: NpcDeath,
    private val players: PlayerList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        FightCaveMonster.entries.forEach { monster -> register(monster) }
        verifyCombatVisuals()
    }

    private fun ScriptContext.register(monster: FightCaveMonster) {
        val type =
            requireNotNull(ServerCacheManager.getNpc(monster.internalName.asRSCM(RSCMType.NPC))) {
                "Missing Fight Caves npc definition: ${monster.internalName}"
            }
        check(type.id == monster.cacheId) {
            "Fight Caves npc mapping drifted: ${monster.internalName} " +
                "expected=${monster.cacheId} actual=${type.id}"
        }
        onAiOpPlayer2(type) { attackOwner(it.target, monster) }
        onAiApPlayer2(type) { attackOwner(it.target, monster) }
        onNpcHit(type) {
            val source = if (hit.isFromPlayer) hit.resolvePlayerSource(players) else null
            if (source != null && runs.isOwnedFightCaveNpc(source, npc)) {
                if (
                    monster == FightCaveMonster.TzKek && hit.type == HitType.Melee && hit.damage > 0
                ) {
                    source.queueHit(npc, delay = 1, type = HitType.Typeless, damage = 1)
                }
                if (monster == FightCaveMonster.YtHurKot && npc.hitpoints > 0) {
                    runs.interruptHealer(source, npc)
                }
            }
        }
        onNpcQueue(type, "queue.death") {
            if (runs.isFightCaveNpc(npc)) {
                runs.beforeNpcDeath(npc)
                death.deathNoDrops(this)
                runs.afterNpcDeath(npc)
            } else {
                death.deathWithDrops(this)
            }
        }
    }

    private fun StandardNpcAccess.attackOwner(target: Player, monster: FightCaveMonster) {
        if (!runs.isOwnedFightCaveNpc(target, npc)) {
            resetMode()
            return
        }
        val plan = attackPlan(target, monster)
        combat.attack(this, target, plan.attack, plan.override)
    }

    private fun StandardNpcAccess.attackPlan(
        target: Player,
        monster: FightCaveMonster,
    ): AttackPlan =
        when (monster) {
            FightCaveMonster.TzKih ->
                melee(
                    animation = "seq.firebat_attack",
                    maxHit = 4,
                    onImpact = { damage ->
                        target.statSub(
                            "stat.prayer",
                            constant = (damage + 1).coerceAtLeast(1),
                            percent = 0,
                        )
                    },
                )
            FightCaveMonster.TzKek -> melee("seq.lavabeast_attack", maxHit = 7)
            FightCaveMonster.TzKekSpawn -> melee("seq.lavabeast_attack", maxHit = 4)
            FightCaveMonster.TokXil ->
                if (distanceTo(target) <= 1) {
                    melee("seq.magmaquris_punch", maxHit = 13)
                } else {
                    ranged(
                        "seq.magmaquris_spine_attack",
                        maxHit = 14,
                        projectile =
                            projectile(
                                TOK_XIL_PROJECTILE,
                                TOK_XIL_PROJECTILE_ID,
                                THROWN_PROJANIM,
                                THROWN_PROJANIM_ID,
                            ),
                    )
                }
            FightCaveMonster.YtMejKot ->
                melee(
                    animation = "seq.lizard_cleric_attack",
                    maxHit = 25,
                    onSuccessfulAttack = { runs.tryMejKotHeal(npc) },
                )
            FightCaveMonster.KetZek,
            FightCaveMonster.KetZekOrange ->
                if (distanceTo(target) <= 1) {
                    melee("seq.igniferum_attack", maxHit = 55)
                } else {
                    magic(
                        "seq.igniferum_ranged",
                        maxHit = 52,
                        projectile =
                            projectile(
                                KET_ZEK_PROJECTILE,
                                KET_ZEK_PROJECTILE_ID,
                                MAGIC_PROJANIM,
                                MAGIC_PROJANIM_ID,
                            ),
                        onImpact = { target.spotanim(KET_ZEK_IMPACT) },
                    )
                }
            FightCaveMonster.TzTokJad -> jadAttack(target)
            FightCaveMonster.YtHurKot -> melee("seq.lizard_cleric_attack", maxHit = 14)
        }

    private fun StandardNpcAccess.jadAttack(target: Player): AttackPlan {
        if (distanceTo(target) <= 1) {
            return melee(
                animation = "seq.lordmagmus_attack",
                maxHit = 97,
                impactDelay = 1,
                attackRate = 8,
                modifierOnImpact = true,
            )
        }
        return if (random.of(0..1) == 0) {
            ranged(
                animation = "seq.lordmagmus_smash",
                maxHit = 97,
                impactDelay = 4,
                attackRate = 8,
                modifierOnImpact = true,
                onImpact = { target.spotanim(JAD_RANGE_IMPACT) },
            )
        } else {
            magic(
                animation = "seq.lordmagmus_fire",
                maxHit = 95,
                impactDelay = 4,
                attackRate = 8,
                modifierOnImpact = true,
                projectile =
                    projectile(
                        JAD_MAGIC_PROJECTILE,
                        JAD_MAGIC_PROJECTILE_ID,
                        DRAGONFIRE_PROJANIM,
                        DRAGONFIRE_PROJANIM_ID,
                    ),
                onAttack = { npc.spotanim(JAD_MAGIC_LAUNCH) },
                onImpact = { target.spotanim(JAD_MAGIC_IMPACT) },
            )
        }
    }

    private fun melee(
        animation: String,
        maxHit: Int,
        impactDelay: Int? = null,
        attackRate: Int? = null,
        modifierOnImpact: Boolean = false,
        onImpact: ((Int) -> Unit)? = null,
        onSuccessfulAttack: (() -> Unit)? = null,
    ) =
        AttackPlan(
            CombatAttack.NpcMelee(MeleeAttackType.Crush),
            NpcAttackOverride(
                attackAnimation = animation,
                maxHit = maxHit,
                impactDelay = impactDelay,
                attackRate = attackRate,
                applyModifierOnImpact = modifierOnImpact,
                onImpact = onImpact?.let { effect -> { _, impact -> effect(impact.damage) } },
                onSuccessfulAttack = onSuccessfulAttack?.let { effect -> { _, _ -> effect() } },
            ),
        )

    private fun ranged(
        animation: String,
        maxHit: Int,
        impactDelay: Int? = null,
        attackRate: Int? = null,
        modifierOnImpact: Boolean = false,
        projectile: NpcProjectile? = null,
        onImpact: ((Int) -> Unit)? = null,
    ) =
        AttackPlan(
            CombatAttack.NpcRanged(RangedAttackType.Standard),
            NpcAttackOverride(
                attackAnimation = animation,
                maxHit = maxHit,
                impactDelay = impactDelay,
                attackRate = attackRate,
                applyModifierOnImpact = modifierOnImpact,
                projectile = projectile,
                onImpact = onImpact?.let { effect -> { _, impact -> effect(impact.damage) } },
            ),
        )

    private fun magic(
        animation: String,
        maxHit: Int,
        impactDelay: Int? = null,
        attackRate: Int? = null,
        modifierOnImpact: Boolean = false,
        projectile: NpcProjectile? = null,
        onImpact: ((Int) -> Unit)? = null,
        onAttack: (() -> Unit)? = null,
        onSuccessfulAttack: (() -> Unit)? = null,
    ) =
        AttackPlan(
            CombatAttack.NpcMagic(maxHit = maxHit),
            NpcAttackOverride(
                attackAnimation = animation,
                maxHit = maxHit,
                impactDelay = impactDelay,
                attackRate = attackRate,
                applyModifierOnImpact = modifierOnImpact,
                projectile = projectile,
                onImpact = onImpact?.let { effect -> { _, impact -> effect(impact.damage) } },
                onAttack = onAttack?.let { effect -> { _ -> effect() } },
                onSuccessfulAttack = onSuccessfulAttack?.let { effect -> { _, _ -> effect() } },
            ),
        )

    private fun projectile(
        spotanim: String,
        expectedSpotanimId: Int,
        projanim: String,
        expectedProjanimId: Int,
    ): NpcProjectile {
        requireMapping(RSCMType.SPOTANIM, spotanim, expectedSpotanimId)
        requireMapping(RSCMType.PROJANIM, projanim, expectedProjanimId)
        val type =
            requireNotNull(ServerCacheManager.getProjectile(expectedProjanimId)) {
                "Missing Fight Caves projectile type: $projanim"
            }
        return NpcProjectile(spotanim, type)
    }

    private fun verifyCombatVisuals() {
        requireMapping(RSCMType.SPOTANIM, TOK_XIL_PROJECTILE, TOK_XIL_PROJECTILE_ID)
        requireMapping(RSCMType.SPOTANIM, TZHAAR_HEAL_SPOTANIM, TZHAAR_HEAL_SPOTANIM_ID)
        requireMapping(RSCMType.SPOTANIM, KET_ZEK_PROJECTILE, KET_ZEK_PROJECTILE_ID)
        requireMapping(RSCMType.SPOTANIM, KET_ZEK_IMPACT, KET_ZEK_IMPACT_ID)
        requireMapping(RSCMType.SPOTANIM, JAD_MAGIC_LAUNCH, JAD_MAGIC_LAUNCH_ID)
        requireMapping(RSCMType.SPOTANIM, JAD_MAGIC_PROJECTILE, JAD_MAGIC_PROJECTILE_ID)
        requireMapping(RSCMType.SPOTANIM, JAD_MAGIC_IMPACT, JAD_MAGIC_IMPACT_ID)
        requireMapping(RSCMType.SPOTANIM, JAD_RANGE_IMPACT, JAD_RANGE_IMPACT_ID)
        requireMapping(RSCMType.PROJANIM, THROWN_PROJANIM, THROWN_PROJANIM_ID)
        requireMapping(RSCMType.PROJANIM, MAGIC_PROJANIM, MAGIC_PROJANIM_ID)
        requireMapping(RSCMType.PROJANIM, DRAGONFIRE_PROJANIM, DRAGONFIRE_PROJANIM_ID)
        requireNotNull(ServerCacheManager.getProjectile(THROWN_PROJANIM_ID))
        requireNotNull(ServerCacheManager.getProjectile(MAGIC_PROJANIM_ID))
        requireNotNull(ServerCacheManager.getProjectile(DRAGONFIRE_PROJANIM_ID))
    }

    private fun requireMapping(type: RSCMType, internalName: String, expectedId: Int) {
        val actual = internalName.asRSCM(type)
        check(actual == expectedId) {
            "Fight Caves visual mapping drifted: $internalName expected=$expectedId actual=$actual"
        }
    }

    private data class AttackPlan(
        val attack: CombatAttack.NpcAttack,
        val override: NpcAttackOverride,
    )

    private companion object {
        const val TOK_XIL_PROJECTILE = "spotanim.tzhaar_spine_attack"
        const val TOK_XIL_PROJECTILE_ID = 443
        const val KET_ZEK_PROJECTILE = "spotanim.tzhaar_fire_launch_travel"
        const val KET_ZEK_PROJECTILE_ID = 445
        const val KET_ZEK_IMPACT = "spotanim.tzhaar_fire_launch_impact"
        const val KET_ZEK_IMPACT_ID = 446
        const val JAD_MAGIC_LAUNCH = "spotanim.tzhaar_fire_spit_launch"
        const val JAD_MAGIC_LAUNCH_ID = 447
        const val JAD_MAGIC_PROJECTILE = "spotanim.tzhaar_fire_spit_travel"
        const val JAD_MAGIC_PROJECTILE_ID = 448
        const val JAD_MAGIC_IMPACT = "spotanim.tzhaar_fire_spit_end_travel"
        const val JAD_MAGIC_IMPACT_ID = 450
        const val JAD_RANGE_IMPACT = "spotanim.tzhaar_rock_smash"
        const val JAD_RANGE_IMPACT_ID = 451
        const val THROWN_PROJANIM = "projanim.thrown"
        const val THROWN_PROJANIM_ID = 3
        const val MAGIC_PROJANIM = "projanim.magic_spell"
        const val MAGIC_PROJANIM_ID = 6
        const val DRAGONFIRE_PROJANIM = "projanim.dragonfire"
        const val DRAGONFIRE_PROJANIM_ID = 16
    }
}
