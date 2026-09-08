package org.rsmod.content.minigames.tob

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

internal enum class TobCombatStyle {
    NONE,
    MELEE,
    RANGED,
    MAGIC,
}

internal data class TobCombatProfile(val style: TobCombatStyle, val maxHit: Int, val rate: Int)

internal enum class TobNpcDeathRoute {
    RAID_NO_DROPS,
    STANDARD_DROPS,
}

internal fun tobNpcDeathRoute(raidOwned: Boolean): TobNpcDeathRoute =
    if (raidOwned) TobNpcDeathRoute.RAID_NO_DROPS else TobNpcDeathRoute.STANDARD_DROPS

internal class TobCombat
@Inject
constructor(
    private val combat: NvPCombat,
    private val raids: TobRaidManager,
    private val death: NpcDeath,
    private val players: PlayerList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        TobNpcs.allCore.forEach { id ->
            val type =
                requireNotNull(ServerCacheManager.getNpc(id)) { "Missing ToB combat npc $id" }
            onAiOpPlayer2(type) { attack(it.target) }
            onAiApPlayer2(type) { attack(it.target) }
            onModifyNpcHit(type) {
                val player =
                    if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players)
                    else null
                if (player == null) hit.damage = 0 else raids.modifyNpcHit(player, npc, hit)
            }
            onNpcQueue(type, "queue.death") {
                if (
                    tobNpcDeathRoute(raids.raidFor(npc) != null) == TobNpcDeathRoute.STANDARD_DROPS
                ) {
                    // The cache NPC type can still be used outside Theatre instances. Preserve the
                    // normal death/drop pipeline for any such spawn.
                    death.deathWithDrops(this)
                } else {
                    // ToB owns both rewards and room progression. Preserve the NPC's full death
                    // animation, omit world drops, then advance the room through the authoritative
                    // manager callback. suppressRewards cannot be used with the generic death
                    // pipeline because it also suppresses NpcDeathKillHook callbacks.
                    val hero = npc.findHero(players)
                    death.deathNoDrops(this)
                    raids.handleOwnedNpcDeath(npc, hero)
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
        if (profile.style == TobCombatStyle.NONE) {
            resetMode()
            return
        }
        val attack =
            when (profile.style) {
                TobCombatStyle.MELEE -> CombatAttack.NpcMelee(MeleeAttackType.Crush)
                TobCombatStyle.RANGED -> CombatAttack.NpcRanged(RangedAttackType.Standard)
                TobCombatStyle.MAGIC -> CombatAttack.NpcMagic(profile.maxHit)
                TobCombatStyle.NONE -> return
            }
        combat.attack(this, target, attack, attackOverride(npc, profile))
    }

    private fun attackOverride(npc: Npc, profile: TobCombatProfile): NpcAttackOverride {
        val raid = raids.raidFor(npc)
        val isMaiden =
            raid?.currentRoom?.room == TobRoom.MAIDEN && raid.npcRoles[npc] == TobNpcRole.BOSS
        val nylocasStyle =
            raid?.let {
                val activeId = TobNpcs.nylocasActiveStyleId(npc.id, npc.visType.id, it.mode)
                TobNpcs.nylocasStyleIndex(activeId, it.mode)
            }
        val animation =
            when {
                isMaiden -> sequence(TobAssets.MAIDEN_MAGIC_CAST)
                nylocasStyle != null -> sequence(TobAssets.nylocasAttackAnimation(nylocasStyle))
                else -> null
            }
        val projectile =
            when {
                isMaiden -> projectile(TobAssets.MAIDEN_MAGIC_PROJECTILE)
                nylocasStyle == 1 -> projectile(TobAssets.NYLOCAS_RANGED_PROJECTILE)
                nylocasStyle == 2 -> projectile(TobAssets.NYLOCAS_MAGIC_PROJECTILE)
                else -> null
            }
        return NpcAttackOverride(
            attackAnimation = animation,
            maxHit = profile.maxHit,
            forceAccurate = isMaiden,
            projectile = projectile,
            attackRate = profile.rate,
        )
    }

    private fun sequence(id: Int): String =
        requireNotNull(RSCM.getReverseMapping(RSCMType.SEQ, id)) { "Missing ToB sequence $id" }

    private fun projectile(id: Int): NpcProjectile =
        NpcProjectile(
            requireNotNull(RSCM.getReverseMapping(RSCMType.SPOTANIM, id)) {
                "Missing ToB projectile spotanim $id"
            },
            requireNotNull(
                ServerCacheManager.getProjectile("projanim.magic_spell".asRSCM(RSCMType.PROJANIM))
            ) {
                "Missing ToB projectile controller"
            },
        )
}

internal fun baseCombatProfile(npc: Npc, raid: TobRaidSession): TobCombatProfile {
    val role = raid.npcRoles[npc]
    val bossStyles = TobNpcs.nylocasBossStyles(raid.mode)
    val nylocasId = TobNpcs.nylocasActiveStyleId(npc.id, npc.visType.id, raid.mode)
    val nylocasStyle = TobNpcs.nylocasStyleIndex(nylocasId, raid.mode)
    val base =
        when {
            npc.id == TobNpcs.boss(TobRoom.BLOAT, raid.mode) ->
                TobCombatProfile(TobCombatStyle.NONE, 0, 4)
            raid.currentRoom.room == TobRoom.MAIDEN && role == TobNpcRole.BOSS -> {
                val state = raid.encounter as? MaidenState
                TobCombatProfile(
                    TobCombatStyle.MAGIC,
                    state?.magicMaxHit() ?: 36,
                    (10 - (state?.empoweredNylocas ?: 0)).coerceAtLeast(4),
                )
            }
            npc.id == TobNpcs.boss(TobRoom.SOTETSEG, raid.mode) ->
                TobCombatProfile(TobCombatStyle.NONE, 0, 5)
            npc.id == TobNpcs.boss(TobRoom.XARPUS, raid.mode) ->
                TobCombatProfile(TobCombatStyle.NONE, 0, 4)
            npc.id in TobNpcs.verzikPhases(raid.mode) -> TobCombatProfile(TobCombatStyle.NONE, 0, 4)
            nylocasId == bossStyles[0] -> TobCombatProfile(TobCombatStyle.MELEE, 72, 4)
            nylocasId == bossStyles[1] -> TobCombatProfile(TobCombatStyle.RANGED, 77, 4)
            nylocasId == bossStyles[2] -> TobCombatProfile(TobCombatStyle.MAGIC, 78, 4)
            nylocasStyle == 0 -> TobCombatProfile(TobCombatStyle.MELEE, 15, 4)
            nylocasStyle == 1 -> TobCombatProfile(TobCombatStyle.RANGED, 15, 4)
            nylocasStyle == 2 -> TobCombatProfile(TobCombatStyle.MAGIC, 15, 4)
            role in
                setOf(
                    TobNpcRole.HAZARD,
                    TobNpcRole.PILLAR,
                    TobNpcRole.VERZIK_TRANSITION,
                    TobNpcRole.MAIDEN_ADD,
                    TobNpcRole.VERZIK_ADD,
                ) -> TobCombatProfile(TobCombatStyle.NONE, 0, 4)
            else -> TobCombatProfile(TobCombatStyle.MELEE, 30, 4)
        }
    return base.copy(maxHit = TobScaling.maxHit(base.maxHit.coerceAtLeast(1), raid.mode))
}
