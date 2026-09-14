package org.rsmod.content.minigames.toa

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.combat.NpcAttackOverride
import org.rsmod.api.combat.NpcProjectile
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.hit.modifier.NpcPartialProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.isValidTarget
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
    private val deps: BossDeps,
    private val crondisZebak: ToaCrondisZebakRuntime,
) : PluginScript() {
    override fun ScriptContext.startup() {
        deps.extensionRegistry.registerAttackParameters(TOA_BABA_ATTACK_PARAMETERS) { npc, _, _ ->
            babaAttackParameters(npc)
        }
        BossCombat.register(
            this,
            toaBabaBossSpec(),
            deps,
            ownerSlug = "toa-baba",
            canTarget = ::prepareBabaOpportunity,
            onModifyHit = {
                val player =
                    if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players)
                    else null
                if (player == null) hit.damage = 0 else raids.modifyNpcHit(player, npc, hit)
            },
        )
        deps.extensionRegistry.registerAttackParameters(TOA_AKKHA_ATTACK_PARAMETERS) { npc, _, data
            ->
            akkhaAttackParameters(npc, data as ToaCombatStyle)
        }
        BossCombat.register(
            this,
            toaAkkhaBossSpec(),
            deps,
            ownerSlug = "toa-akkha",
            canTarget = ::prepareAkkhaOpportunity,
            onModifyHit = {
                val player =
                    if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players)
                    else null
                if (player == null) hit.damage = 0 else raids.modifyNpcHit(player, npc, hit)
            },
        )
        registerKephriBossCombat(
            this,
            deps,
            canTarget = raids::prepareKephriOpportunity,
            launch = raids::launchKephriAttack,
            modifyHit = {
                val player =
                    if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players)
                    else null
                if (player == null) hit.damage = 0 else raids.modifyNpcHit(player, npc, hit)
            },
        )
        crondisZebak.registerOrdinaryCombat(
            this,
            onModifyHit = {
                val player =
                    if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players)
                    else null
                if (player == null) hit.damage = 0 else raids.modifyNpcHit(player, npc, hit)
            },
        )
        registerWardenPhaseTwoBossCombat(
            this,
            deps,
            canTarget = raids::prepareWardenPhaseTwoOpportunity,
            launch = raids::launchWardenPhaseTwoAttack,
            modifyHit = {
                val player =
                    if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players)
                    else null
                if (player == null) hit.damage = 0 else raids.modifyNpcHit(player, npc, hit)
            },
        )
        ToaNpcs.allCombat.forEach { id ->
            if (
                id in TOA_BABA_BOSS_FORMS ||
                    id in ToaNpcs.akkhaBossForms ||
                    id in KephriEncounter.ACTIVE..KephriEncounter.DEAD ||
                    id in TOA_ZEBAK_BOSS_FORMS ||
                    id in WARDEN_PHASE_TWO_FORMS
            )
                return@forEach
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
            val raid = raids.captureNpcDeath(npc)
            if (toaSiphonDeathRoute(raid != null) == ToaNpcDeathRoute.STANDARD_DROPS) {
                death.deathWithDrops(this)
            } else {
                // ToA owns Siphon progression and rewards. Run the normal death animation but
                // omit the cache fallback remains (bones), then invoke the raid callback because
                // deathNoDrops deliberately skips the global NpcDeathKillHook.
                val hero = npc.findHero(players)
                death.deathNoDrops(this)
                if (hero != null) raids.handleNpcKill(hero, npc, raid)
            }
        }
    }

    private fun prepareBabaOpportunity(npc: Npc, target: Player): Boolean {
        if (!raids.attackTargetValid(target, npc)) {
            npc.resetMode()
            return false
        }
        val tick = deps.mapClock.cycle
        if (npc.actionDelay > tick) return false
        if (raids.isBabaBoss(npc)) {
            val specialDelay = raids.performBabaSpecialIfDue(npc)
            if (specialDelay != null) {
                npc.actionDelay = tick + specialDelay
                return false
            }
            if (raids.suppressBabaNormalAttacks(npc)) {
                npc.resetMode()
                return false
            }
        }
        if (raids.combatProfile(npc) == null) {
            npc.resetMode()
            return false
        }
        // Preserve NvP's reset for invalid targets before the interpreter's hit-validity gate.
        if (!target.isValidTarget()) {
            npc.resetMode()
            return false
        }
        // The established native option defers cadence commits until NvP accepts the launch.
        // This singleton rotation has no decision or combat RNG draw.
        deps.encounterRegistry.of(npc).initializeDecisionSeed(0)
        return true
    }

    private fun babaAttackParameters(npc: Npc): NpcAttackOverride {
        val profile = requireNotNull(raids.combatProfile(npc))
        return NpcAttackOverride(
            maxHit = profile.maxHit,
            hitModifier =
                NpcPartialProtectionPrayerPlayerHitModifier(raids.babaPrayerReduction(npc)),
            onAttack = { target -> raids.recordBabaStandardAttack(npc, target) },
            onImpact = raids::onPrayerMitigatedImpact,
        )
    }

    private fun prepareAkkhaOpportunity(npc: Npc, target: Player): Boolean {
        if (!raids.attackTargetValid(target, npc)) {
            npc.resetMode()
            return false
        }
        val tick = deps.mapClock.cycle
        if (npc.actionDelay > tick) return false
        val specialDelay = raids.performAkkhaSpecialIfDue(npc, target)
        if (specialDelay != null) {
            npc.actionDelay = tick + specialDelay
            return false
        }
        if (raids.suppressAkkhaNormalAttacks(npc)) {
            npc.resetMode()
            return false
        }
        val profile = raids.combatProfile(npc)
        if (profile == null) {
            npc.resetMode()
            return false
        }
        if (profile.style == ToaCombatStyle.MELEE && !npc.isWithinDistance(target, 1)) return false
        val encounter = deps.encounterRegistry.of(npc)
        requireNotNull(encounter.stateOrNull<AkkhaCombatPhases>(AKKHA_COMBAT_PHASES))
            .prepareStyle(profile.style)
        // Preserve the emission-time face, including native target rejection after this point.
        npc.faceSquare(target.coords)
        if (!target.isValidTarget()) {
            npc.resetMode()
            return false
        }
        encounter.initializeDecisionSeed(0)
        return true
    }

    private fun StandardNpcAccess.attack(target: Player) {
        if (!raids.attackTargetValid(target, npc)) {
            resetMode()
            return
        }
        val profile =
            raids.combatProfile(npc)
                ?: run {
                    resetMode()
                    return
                }
        val attack =
            when (profile.style) {
                ToaCombatStyle.MELEE -> CombatAttack.NpcMelee(MeleeAttackType.Crush)
                ToaCombatStyle.RANGED -> CombatAttack.NpcRanged(RangedAttackType.Standard)
                ToaCombatStyle.MAGIC -> CombatAttack.NpcMagic(profile.maxHit)
            }
        combat.attack(this, target, attack, attackOverride(npc, profile))
    }

    private fun attackOverride(npc: Npc, profile: ToaCombatProfile): NpcAttackOverride {
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
        return NpcAttackOverride(
            maxHit = profile.maxHit,
            attackRate = profile.rate,
            hitModifier = prayerModifier(npc),
            onImpact = raids::onPrayerMitigatedImpact,
        )
    }

    private fun akkhaAttackParameters(npc: Npc, style: ToaCombatStyle): NpcAttackOverride {
        val profile = requireNotNull(raids.combatProfile(npc))
        check(profile.style == style)
        val projectile =
            when (style) {
                ToaCombatStyle.MELEE -> null
                ToaCombatStyle.RANGED -> projectile(ToaAssets.AKKHA_RANGE)
                ToaCombatStyle.MAGIC -> projectile(ToaAssets.AKKHA_MAGIC)
            }
        val prayerReduction =
            when (style) {
                ToaCombatStyle.MELEE -> 75
                ToaCombatStyle.RANGED,
                ToaCombatStyle.MAGIC -> 85
            }
        return NpcAttackOverride(
            maxHit = profile.maxHit,
            projectile = projectile,
            hitModifier =
                NpcPartialProtectionPrayerPlayerHitModifier(
                    raids.prayerReduction(npc, prayerReduction)
                ),
            onAttack = { attackedTarget ->
                raids.recordAkkhaStandardAttack(npc, attackedTarget, style)
            },
            onImpact = { source, impact ->
                raids.onPrayerMitigatedImpact(source, impact)
                if (style == ToaCombatStyle.MAGIC) {
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
