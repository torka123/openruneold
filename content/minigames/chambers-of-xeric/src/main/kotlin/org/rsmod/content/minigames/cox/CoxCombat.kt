package org.rsmod.content.minigames.cox

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.combat.NpcAttackOverride
import org.rsmod.api.combat.NpcProjectile
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit as queueNpcHit
import org.rsmod.api.player.cheat.adminOneHit
import org.rsmod.api.player.hit.modifier.NpcPartialProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class CoxCombat
@Inject
constructor(
    private val combat: NvPCombat,
    private val raids: CoxRaidManager,
    private val death: NpcDeath,
    private val players: PlayerList,
    private val random: GameRandom,
    private val world: WorldRepository,
    private val npcHitModifier: NpcHitModifier,
) : PluginScript() {
    private val largeMuttadileDistanceCycles = WeakHashMap<Npc, MuttadileDistanceAttackCycle>()
    private val olmBasicAttackCycles = WeakHashMap<Npc, GreatOlmBasicAttackCycle>()
    private val olmAttackCycles = WeakHashMap<Npc, GreatOlmAttackCycle>()

    override fun ScriptContext.startup() {
        validateCombatAssets()
        CoxNpcs.all.forEach { id ->
            val type =
                requireNotNull(ServerCacheManager.getNpc(id)) { "Missing CoX combat npc $id" }
            onAiOpPlayer2(type) { attack(it.target) }
            onAiApPlayer2(type) { attack(it.target) }
            onModifyNpcHit(type) {
                if (raids.raidFor(npc) == null) return@onModifyNpcHit
                val player =
                    if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players)
                    else null
                if (player == null) hit.damage = 0 else modifyRaidHit(player, npc, hit)
            }
            val defendSequence =
                VanguardCombatRules[id]?.defendSequence
                    ?: SkeletalMysticAnimations.defendSequence(id)
                    ?: DeathlyNpcAnimations.defendSequence(id)
            onNpcHit(type) {
                if (raids.raidFor(npc) != null) {
                    val attacker =
                        if (hit.isFromPlayer) hit.resolvePlayerSource(players) else null
                    if (attacker != null) raids.awardCombatPoints(attacker, npc, hit.damage)
                    if (defendSequence != null && npc.hitpoints > 0) {
                        npc.anim(defendSequence, priority = 254)
                    }
                }
            }
            onNpcQueue(type, "queue.death") {
                if (raids.raidFor(npc) == null) {
                    death.deathWithDrops(this)
                } else {
                    val hero = npc.findHero(players)
                    // Record progression while the dying NPC is still attached to its instance.
                    // Calling this after deathNoDrops loses instance ownership and used to stop
                    // the adult Muttadile from emerging after the juvenile died.
                    raids.handleNpcKill(hero, npc)
                    val combatId = npc.visType.id
                    largeMuttadileDistanceCycles.remove(npc)
                    olmBasicAttackCycles.remove(npc)
                    olmAttackCycles.remove(npc)
                    death.deathNoDrops(
                        this,
                        deathAnimationId =
                            VanguardCombatRules[combatId]?.deathSequence
                                ?: SkeletalMysticAnimations.deathSequence(combatId)
                                ?: DeathlyNpcAnimations.deathSequence(combatId),
                    )
                }
            }
        }
    }

    private fun StandardNpcAccess.attack(target: Player) {
        if (!raids.attackTargetValid(target, npc)) {
            resetMode()
            return
        }
        val profile = raids.combatProfile(npc) ?: return
        if (profile.style == CoxCombatStyle.PASSIVE) {
            resetMode()
            return
        }
        if (attackOlm(target)) return
        if (attackMuttadile(target)) return
        val attack =
            when (profile.style) {
                CoxCombatStyle.MELEE -> CombatAttack.NpcMelee(MeleeAttackType.Crush)
                CoxCombatStyle.RANGED -> CombatAttack.NpcRanged(RangedAttackType.Standard)
                CoxCombatStyle.MAGIC,
                CoxCombatStyle.OLM -> CombatAttack.NpcMagic(profile.maxHit)
                CoxCombatStyle.PASSIVE -> error("Passive CoX NPC reached combat execution")
            }
        combat.attack(this, target, attack, attackOverride(npc, profile))
    }

    private fun StandardNpcAccess.attackOlm(primary: Player): Boolean {
        if (npc.id != CoxNpcs.OLM_HEAD) return false
        if (actionDelay > mapClock) return true
        val attackCycle = olmAttackCycles.getOrPut(npc, ::GreatOlmAttackCycle)
        when (attackCycle.current) {
            GreatOlmAttackSlot.SPECIAL -> {
                raids.performOlmSpecial(npc)
                actionDelay = mapClock + OLM_ATTACK_RATE
                attackCycle.advance()
                return true
            }
            GreatOlmAttackSlot.EMPTY -> {
                actionDelay = mapClock + OLM_ATTACK_RATE
                attackCycle.advance()
                return true
            }
            GreatOlmAttackSlot.BASIC -> Unit
        }
        val cycle = olmBasicAttackCycles.getOrPut(npc, ::GreatOlmBasicAttackCycle)
        val style = cycle.current
        val basicMaxHit = raids.olmBasicMaxHit(npc)
        val configuredProjectile =
            when (style) {
                GreatOlmBasicAttackStyle.MAGIC ->
                    projectile(OLM_MAGIC_PROJECTILE, "projanim.magic_spell")
                GreatOlmBasicAttackStyle.RANGED ->
                    projectile(OLM_RANGED_PROJECTILE, "projanim.arrow")
            }
        val attack =
            when (style) {
                GreatOlmBasicAttackStyle.MAGIC -> CombatAttack.NpcMagic(basicMaxHit)
                GreatOlmBasicAttackStyle.RANGED ->
                    CombatAttack.NpcRanged(RangedAttackType.Standard)
            }
        val hitType =
            when (style) {
                GreatOlmBasicAttackStyle.MAGIC -> HitType.Magic
                GreatOlmBasicAttackStyle.RANGED -> HitType.Ranged
            }
        val protection = NpcPartialProtectionPrayerPlayerHitModifier(75)
        combat.attack(
            this,
            primary,
            attack,
            NpcAttackOverride(
                maxHit = basicMaxHit,
                forceAccurate = true,
                projectile = configuredProjectile,
                attackRate = OLM_ATTACK_RATE,
                hitModifier = protection,
                onAttack = { primaryTarget ->
                    raids.animateOlmHeadBasicAttack(npc)
                    raids.activeOlmTargets(npc)
                        .filter { it !== primaryTarget }
                        .forEach { secondary ->
                            val projectile =
                                world.projAnimSourced(
                                    npc,
                                    secondary,
                                    SpotanimType(
                                        RSCM.getRSCM(configuredProjectile.travelSpotanim)
                                    ),
                                    configuredProjectile.type,
                                )
                            secondary.queueHit(
                                source = npc,
                                delay = projectile.serverCycles,
                                type = hitType,
                                damage = random.of(0..basicMaxHit),
                                modifier = protection,
                            )
                        }
                    raids.activeOlmTestTargets(npc).forEach { testTarget ->
                        val projectile =
                            world.projAnimSourced(
                                npc,
                                testTarget,
                                SpotanimType(
                                    RSCM.getRSCM(configuredProjectile.travelSpotanim)
                                ),
                                configuredProjectile.type,
                            )
                        testTarget.queueNpcHit(
                            source = npc,
                            delay = projectile.serverCycles,
                            type = hitType,
                            damage = random.of(0..basicMaxHit),
                            modifier = npcHitModifier,
                        )
                    }
                    cycle.advance(switchStyle = random.of(4) == 0)
                    attackCycle.advance()
                },
            ),
        )
        return true
    }

    private fun StandardNpcAccess.attackMuttadile(target: Player): Boolean {
        val style =
            when (npc.id) {
                CoxNpcs.MUTTADILE_LARGE_UNDERWATER -> MuttadileAttackStyle.MAGIC
                CoxNpcs.MUTTADILE_SMALL ->
                    MuttadileCombatRules.smallAttack(inMeleeRange(target), random.of(3))
                CoxNpcs.MUTTADILE_LARGE ->
                    if (inMeleeRange(target)) {
                        MuttadileCombatRules.largeCloseAttack(random.of(4))
                    } else {
                        largeMuttadileDistanceCycles
                            .getOrPut(npc, ::MuttadileDistanceAttackCycle)
                            .next(random.of(2))
                    }
                else -> return false
            }
        val attack =
            when (style) {
                MuttadileAttackStyle.MELEE,
                MuttadileAttackStyle.STOMP -> CombatAttack.NpcMelee(MeleeAttackType.Crush)
                MuttadileAttackStyle.RANGED -> CombatAttack.NpcRanged(RangedAttackType.Standard)
                MuttadileAttackStyle.MAGIC ->
                    CombatAttack.NpcMagic(MuttadileCombatRules.LARGE_MAGIC_MAX_HIT)
            }
        combat.attack(this, target, attack, muttadileAttackOverride(style))
        return true
    }

    private fun StandardNpcAccess.inMeleeRange(target: Player): Boolean =
        npc.coords.chebyshevDistance(target.coords) <= npc.visType.size

    private fun StandardNpcAccess.muttadileAttackOverride(
        style: MuttadileAttackStyle
    ): NpcAttackOverride {
        val juvenile = npc.id == CoxNpcs.MUTTADILE_SMALL
        val submerged = npc.id == CoxNpcs.MUTTADILE_LARGE_UNDERWATER
        return when (style) {
            MuttadileAttackStyle.MELEE ->
                NpcAttackOverride(
                    maxHit =
                        if (juvenile) MuttadileCombatRules.SMALL_MELEE_MAX_HIT
                        else MuttadileCombatRules.LARGE_MELEE_MAX_HIT,
                    attackRate = MuttadileCombatRules.ATTACK_RATE,
                )
            MuttadileAttackStyle.RANGED ->
                NpcAttackOverride(
                    attackAnimation = sequence(MUTTADILE_RANGED_SEQUENCE),
                    maxHit =
                        if (juvenile) MuttadileCombatRules.SMALL_RANGED_MAX_HIT
                        else MuttadileCombatRules.LARGE_RANGED_MAX_HIT,
                    projectile = projectile(MUTTADILE_RANGED_PROJECTILE, "projanim.arrow"),
                    attackRate = MuttadileCombatRules.ATTACK_RATE,
                    hitModifier = NpcPartialProtectionPrayerPlayerHitModifier(50),
                )
            MuttadileAttackStyle.MAGIC ->
                NpcAttackOverride(
                    attackAnimation = sequence(MUTTADILE_MAGIC_SEQUENCE),
                    maxHit =
                        if (submerged) MuttadileCombatRules.SUBMERGED_MAGIC_MAX_HIT
                        else MuttadileCombatRules.LARGE_MAGIC_MAX_HIT,
                    projectile = projectile(MUTTADILE_MAGIC_PROJECTILE, "projanim.magic_spell"),
                    attackRate = MuttadileCombatRules.ATTACK_RATE,
                    hitModifier = NpcPartialProtectionPrayerPlayerHitModifier(50),
                )
            MuttadileAttackStyle.STOMP ->
                NpcAttackOverride(
                    attackAnimation = sequence(MUTTADILE_STOMP_SEQUENCE),
                    maxHit = MuttadileCombatRules.LARGE_STOMP_MAX_HIT,
                    attackRate = MuttadileCombatRules.ATTACK_RATE,
                    hitModifier = NpcPartialProtectionPrayerPlayerHitModifier(50),
                )
        }
    }

    private fun modifyRaidHit(player: Player, npc: Npc, hit: HitBuilder) {
        val combatId = npc.visType.id
        val allowed =
            when (combatId) {
                CoxNpcs.GUARDIAN,
                CoxNpcs.GUARDIAN_ALT ->
                    hit.righthandType()?.isContentType("content.mining_pickaxe") == true
                CoxNpcs.OLM_RIGHT_HAND -> hit.type == HitType.Magic
                CoxNpcs.OLM_LEFT_HAND -> hit.type == HitType.Melee
                CoxNpcs.OLM_HEAD -> hit.type == HitType.Magic || hit.type == HitType.Ranged
                CoxNpcs.VANGUARD_MELEE -> hit.type == HitType.Magic
                CoxNpcs.VANGUARD_RANGED -> hit.type == HitType.Melee
                CoxNpcs.VANGUARD_MAGIC -> hit.type == HitType.Ranged
                else -> true
            }
        if (!allowed) {
            hit.damage = 0
            return
        }
        if (player.adminOneHit) hit.damage = npc.hitpoints
    }

    private fun attackOverride(npc: Npc, profile: CoxCombatProfile): NpcAttackOverride {
        val combatId = npc.visType.id
        val animation =
            when (combatId) {
                CoxNpcs.DEATHLY_RANGER -> sequence(426)
                CoxNpcs.SKELETAL_MYSTIC,
                CoxNpcs.SKELETAL_MYSTIC_ALT,
                CoxNpcs.SKELETAL_MYSTIC_ALT_2 ->
                    sequence(SkeletalMysticAnimations.ATTACK_SEQUENCE)
                CoxNpcs.VANGUARD_MELEE,
                CoxNpcs.VANGUARD_RANGED,
                CoxNpcs.VANGUARD_MAGIC ->
                    sequence(VanguardCombatRules.getValue(combatId).attackSequence)
                else -> null
            }
        val projectile =
            when (combatId) {
                CoxNpcs.DEATHLY_RANGER -> projectile(1_384, "projanim.arrow")
                CoxNpcs.DEATHLY_MAGE,
                CoxNpcs.SKELETAL_MYSTIC,
                CoxNpcs.SKELETAL_MYSTIC_ALT,
                CoxNpcs.SKELETAL_MYSTIC_ALT_2 -> projectile(130, "projanim.magic_spell")
                CoxNpcs.VANGUARD_RANGED -> projectile(1_332, "projanim.arrow")
                CoxNpcs.VANGUARD_MAGIC -> projectile(1_331, "projanim.magic_spell")
                else -> null
            }
        val onAttack: (StandardNpcAccess.(Player) -> Unit)? =
            when (combatId) {
                CoxNpcs.DEATHLY_RANGER -> { _ -> npc.spotanim(1_385, height = 96) }
                CoxNpcs.DEATHLY_MAGE -> { _ -> npc.spotanim(129, height = 92) }
                CoxNpcs.SKELETAL_MYSTIC,
                CoxNpcs.SKELETAL_MYSTIC_ALT,
                CoxNpcs.SKELETAL_MYSTIC_ALT_2 -> { _ -> npc.spotanim(1_322) }
                else -> null
            }
        val onSuccessful: (StandardNpcAccess.(Player, Int) -> Unit)? =
            when (combatId) {
                CoxNpcs.DEATHLY_MAGE,
                CoxNpcs.SKELETAL_MYSTIC,
                CoxNpcs.SKELETAL_MYSTIC_ALT,
                CoxNpcs.SKELETAL_MYSTIC_ALT_2 -> { target, delay ->
                        target.spotanim(131, delay = delay, height = 124)
                    }
                CoxNpcs.VANGUARD_RANGED -> { target, delay ->
                        target.spotanim(305, delay = delay, height = 40)
                    }
                CoxNpcs.VANGUARD_MAGIC -> { target, delay ->
                        target.spotanim(659, delay = delay)
                    }
                else -> null
            }
        return NpcAttackOverride(
            attackAnimation = animation,
            maxHit = profile.maxHit,
            // The donor rolls Deathly attacks without a defence check. Protection prayer halves
            // their damage instead of converting every attack into a zero.
            forceAccurate = DeathlyNpcCombatRules.appliesTo(combatId),
            projectile = projectile,
            attackRate = profile.attackRate,
            hitModifier =
                if (DeathlyNpcCombatRules.appliesTo(combatId)) {
                    NpcPartialProtectionPrayerPlayerHitModifier(
                        DeathlyNpcCombatRules.PROTECTION_PRAYER_REDUCTION_PERCENT
                    )
                } else StandardPlayerHitModifier,
            onAttack = onAttack,
            onSuccessfulAttack = onSuccessful,
        )
    }

    private fun validateCombatAssets() {
        listOf(
                MUTTADILE_RANGED_SEQUENCE,
                MUTTADILE_MAGIC_SEQUENCE,
                MUTTADILE_STOMP_SEQUENCE,
            )
            .forEach(::sequence)
        VanguardCombatRules.sequenceIds.forEach(::sequence)
        SkeletalMysticAnimations.sequenceIds.forEach(::sequence)
        projectile(1_332, "projanim.arrow")
        projectile(1_331, "projanim.magic_spell")
        projectile(MUTTADILE_RANGED_PROJECTILE, "projanim.arrow")
        projectile(MUTTADILE_MAGIC_PROJECTILE, "projanim.magic_spell")
        projectile(OLM_MAGIC_PROJECTILE, "projanim.magic_spell")
        projectile(OLM_RANGED_PROJECTILE, "projanim.arrow")
    }

    private fun sequence(id: Int): String =
        requireNotNull(RSCM.getReverseMapping(RSCMType.SEQ, id)) { "Missing CoX sequence $id" }

    private fun projectile(id: Int, controller: String): NpcProjectile =
        NpcProjectile(
            requireNotNull(RSCM.getReverseMapping(RSCMType.SPOTANIM, id)) {
                "Missing CoX projectile spotanim $id"
            },
            requireNotNull(ServerCacheManager.getProjectile(controller.asRSCM(RSCMType.PROJANIM))) {
                "Missing CoX projectile controller $controller"
            },
        )

    private companion object {
        private const val MUTTADILE_RANGED_SEQUENCE = 7421
        private const val MUTTADILE_MAGIC_SEQUENCE = 7422
        private const val MUTTADILE_STOMP_SEQUENCE = 7424
        private const val MUTTADILE_RANGED_PROJECTILE = 1291
        private const val MUTTADILE_MAGIC_PROJECTILE = 1046
        private const val OLM_ATTACK_RATE = 4
        private const val OLM_MAGIC_PROJECTILE = 1339
        private const val OLM_RANGED_PROJECTILE = 1340
    }
}
