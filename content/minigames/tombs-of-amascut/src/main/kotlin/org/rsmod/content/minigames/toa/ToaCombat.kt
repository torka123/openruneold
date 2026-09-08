package org.rsmod.content.minigames.toa

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
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
import org.rsmod.api.player.hit.modifier.NpcPartialProtectionPrayerPlayerHitModifier
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal enum class ToaCombatStyle {
    MELEE,
    RANGED,
    MAGIC,
}

internal fun akkhaImpactDelay(style: ToaCombatStyle): Int =
    if (style == ToaCombatStyle.MELEE) 1 else 3

internal data class ToaCombatProfile(val style: ToaCombatStyle, val maxHit: Int, val rate: Int)

internal enum class ToaNpcDeathRoute {
    RAID_NO_DROPS,
    STANDARD_DROPS,
}

internal fun toaSiphonDeathRoute(raidOwned: Boolean): ToaNpcDeathRoute =
    if (raidOwned) ToaNpcDeathRoute.RAID_NO_DROPS else ToaNpcDeathRoute.STANDARD_DROPS

internal class ToaCombat
@Inject
constructor(
    private val combat: NvPCombat,
    private val raids: ToaRaidManager,
    private val death: NpcDeath,
    private val players: PlayerList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        ToaNpcs.allCombat.forEach { id ->
            val type = ServerCacheManager.getNpc(id) ?: return@forEach
            onAiOpPlayer2(type) { attack(it.target) }
            onAiApPlayer2(type) { attack(it.target) }
            onModifyNpcHit(type) {
                val player =
                    if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players)
                    else null
                if (player == null) hit.damage = 0 else raids.modifyNpcHit(player, npc, hit)
            }
        }
        val activeSiphon =
            requireNotNull(ServerCacheManager.getNpc(WardenEnergySiphons.ACTIVE)) {
                "Missing Wardens Energy Siphon ${WardenEnergySiphons.ACTIVE}"
            }
        onNpcQueue(activeSiphon, "queue.death") {
            if (
                toaSiphonDeathRoute(raids.raidFor(npc) != null) ==
                    ToaNpcDeathRoute.STANDARD_DROPS
            ) {
                death.deathWithDrops(this)
            } else {
                // ToA owns Siphon progression and rewards. Run the normal death animation but
                // omit the cache fallback remains (bones), then invoke the raid callback because
                // deathNoDrops deliberately skips the global NpcDeathKillHook.
                val hero = npc.findHero(players)
                death.deathNoDrops(this)
                if (hero != null) raids.handleNpcKill(hero, npc)
            }
        }
    }

    private fun StandardNpcAccess.attack(target: Player) {
        if (!raids.attackTargetValid(target, npc)) {
            resetMode()
            return
        }
        // Phase two is driven by the Wardens encounter clock. Let the normal AI retain its
        // target/pathing, but never let the generic combat hook emit a second, cache-default hit.
        if (raids.usesScriptedWardenPhaseTwoCombat(npc)) {
            actionDelay = mapClock + 1
            return
        }
        if (raids.isAkkhaBoss(npc)) {
            if (actionDelay > mapClock) return
            val specialDelay = raids.performAkkhaSpecialIfDue(npc, target)
            if (specialDelay != null) {
                actionDelay = mapClock + specialDelay
                return
            }
            if (raids.suppressAkkhaNormalAttacks(npc)) {
                resetMode()
                return
            }
        }
        if (raids.isBabaBoss(npc)) {
            if (actionDelay > mapClock) return
            val specialDelay = raids.performBabaSpecialIfDue(npc)
            if (specialDelay != null) {
                actionDelay = mapClock + specialDelay
                return
            }
            if (raids.suppressBabaNormalAttacks(npc)) {
                resetMode()
                return
            }
        }
        val profile =
            raids.combatProfile(npc)
                ?: run {
                    resetMode()
                    return
                }
        if (
            raids.isAkkhaBoss(npc) &&
                profile.style == ToaCombatStyle.MELEE &&
                !npc.isWithinDistance(target, 1)
        ) {
            return
        }
        val attack =
            when (profile.style) {
                ToaCombatStyle.MELEE -> CombatAttack.NpcMelee(MeleeAttackType.Crush)
                ToaCombatStyle.RANGED -> CombatAttack.NpcRanged(RangedAttackType.Standard)
                ToaCombatStyle.MAGIC -> CombatAttack.NpcMagic(profile.maxHit)
            }
        // Interaction state can survive an Akkha teleport or style transform while its cached
        // facing remains on the previous tile. Face the live target on the emission tick so the
        // visible spear animation and the authoritative hit always agree.
        if (raids.isAkkhaBoss(npc)) npc.faceSquare(target.coords)
        combat.attack(this, target, attack, attackOverride(npc, profile))
    }

    private fun attackOverride(npc: Npc, profile: ToaCombatProfile): NpcAttackOverride {
        if (raids.isBabaBoss(npc)) {
            return NpcAttackOverride(
                attackAnimation = sequence(BabaAssets.MELEE_ATTACK),
                maxHit = profile.maxHit,
                attackRate = profile.rate,
                hitModifier =
                    NpcPartialProtectionPrayerPlayerHitModifier(raids.babaPrayerReduction(npc)),
                onAttack = { target -> raids.recordBabaStandardAttack(npc, target) },
                onImpact = raids::onPrayerMitigatedImpact,
            )
        }
        if (raids.isBabaBaboon(npc)) {
            return NpcAttackOverride(
                attackAnimation = sequence(BabaAssets.BABOON_RANGED),
                maxHit = profile.maxHit,
                projectile = projectile(BabaAssets.BABOON_RANGED_TRAVEL),
                attackRate = profile.rate,
                hitModifier = prayerModifier(npc),
                onImpact = raids::onPrayerMitigatedImpact,
            )
        }
        val apmeken = ApmekenCombatRules.definition(npc.id)
        if (apmeken != null && !apmeken.scripted) {
            val animation =
                when (apmeken.style) {
                    ToaCombatStyle.MELEE -> ApmekenRevision240Assets.MELEE_SEQUENCE
                    ToaCombatStyle.RANGED -> ApmekenRevision240Assets.RANGED_SEQUENCE
                    ToaCombatStyle.MAGIC -> ApmekenRevision240Assets.MAGIC_SEQUENCE
                }
            val projectile =
                when (apmeken.style) {
                    ToaCombatStyle.MELEE -> null
                    ToaCombatStyle.RANGED ->
                        projectile(ApmekenRevision240Assets.rangedProjectile.id)
                    ToaCombatStyle.MAGIC -> projectile(ApmekenRevision240Assets.magicProjectile.id)
                }
            return NpcAttackOverride(
                attackAnimation = sequence(animation),
                maxHit = profile.maxHit,
                projectile = projectile,
                attackRate = profile.rate,
                hitModifier = prayerModifier(npc),
                onImpact = raids::onPrayerMitigatedImpact,
            )
        }
        if (npc.id == ScabarasRevision240RuntimeLayout.RANGED_SCARAB_NPC) {
            return NpcAttackOverride(
                attackAnimation = sequence(9587),
                maxHit = profile.maxHit,
                projectile = projectile(1766),
                attackRate = profile.rate,
                hitModifier = prayerModifier(npc),
                onImpact = raids::onPrayerMitigatedImpact,
            )
        }
        if (npc.id == KephriScarabMelee.NPC) {
            return NpcAttackOverride(
                attackAnimation = sequence(KephriScarabMelee.ANIMATION),
                maxHit = profile.maxHit,
                attackRate = profile.rate,
                hitModifier = prayerModifier(npc),
                onImpact = raids::onPrayerMitigatedImpact,
            )
        }
        if (npc.id == KephriScarabRanger.NPC) {
            return NpcAttackOverride(
                attackAnimation = sequence(KephriScarabRanger.ANIMATION),
                maxHit = profile.maxHit,
                projectile = projectile(KephriScarabRanger.PROJECTILE),
                attackRate = profile.rate,
                hitModifier = prayerModifier(npc),
                onAttack = { target -> raids.onKephriSpittingAttack(npc, target) },
                onImpact = raids::onPrayerMitigatedImpact,
            )
        }
        if (npc.id == KephriScarabAgile.NPC) {
            return NpcAttackOverride(
                attackAnimation = sequence(KephriScarabAgile.ATTACK_ANIMATION),
                maxHit = profile.maxHit,
                projectile = projectile(KephriScarabAgile.PROJECTILE),
                attackRate = profile.rate,
                hitModifier = prayerModifier(npc),
                onImpact = raids::onPrayerMitigatedImpact,
            )
        }
        if (!raids.isAkkhaBoss(npc)) {
            return NpcAttackOverride(
                maxHit = profile.maxHit,
                attackRate = profile.rate,
                hitModifier = prayerModifier(npc),
                onImpact = raids::onPrayerMitigatedImpact,
            )
        }
        val animation =
            sequence(
                when (profile.style) {
                    ToaCombatStyle.MELEE -> AkkhaAssets.ATTACK_MELEE_SPEAR
                    ToaCombatStyle.RANGED -> AkkhaAssets.ATTACK_RANGE_SPEAR
                    ToaCombatStyle.MAGIC -> AkkhaAssets.ATTACK_MAGIC_SPEAR
                }
            )
        val projectile =
            when (profile.style) {
                ToaCombatStyle.MELEE -> null
                ToaCombatStyle.RANGED -> projectile(ToaAssets.AKKHA_RANGE)
                ToaCombatStyle.MAGIC -> projectile(ToaAssets.AKKHA_MAGIC)
            }
        val prayerReduction =
            when (profile.style) {
                ToaCombatStyle.MELEE -> 75
                ToaCombatStyle.RANGED,
                ToaCombatStyle.MAGIC -> 85
            }
        return NpcAttackOverride(
            attackAnimation = animation,
            maxHit = profile.maxHit,
            projectile = projectile,
            impactDelay = akkhaImpactDelay(profile.style),
            attackRate = profile.rate,
            hitModifier =
                NpcPartialProtectionPrayerPlayerHitModifier(
                    raids.prayerReduction(npc, prayerReduction)
                ),
            onAttack = { attackedTarget ->
                raids.recordAkkhaStandardAttack(npc, attackedTarget, profile.style)
            },
            onImpact = { source, impact ->
                raids.onPrayerMitigatedImpact(source, impact)
                if (profile.style == ToaCombatStyle.MAGIC) {
                    (impact.target as? Player)?.spotanim(AkkhaAssets.MAGIC_IMPACT)
                }
            },
        )
    }

    private fun prayerModifier(npc: Npc) =
        NpcPartialProtectionPrayerPlayerHitModifier(raids.prayerReduction(npc, 100))

    private fun sequence(id: Int): String =
        requireNotNull(RSCM.getReverseMapping(RSCMType.SEQ, id)) {
            "Missing native revision-240 ToA sequence $id"
        }

    private fun projectile(id: Int): NpcProjectile =
        NpcProjectile(
            requireNotNull(RSCM.getReverseMapping(RSCMType.SPOTANIM, id)) {
                "Missing native revision-240 ToA projectile spotanim $id"
            },
            requireNotNull(
                ServerCacheManager.getProjectile("projanim.magic_spell".asRSCM(RSCMType.PROJANIM))
            ) {
                "Missing native revision-240 ToA projectile controller"
            },
        )
}
