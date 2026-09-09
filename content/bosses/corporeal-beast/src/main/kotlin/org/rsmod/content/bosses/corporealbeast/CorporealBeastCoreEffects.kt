package org.rsmod.content.bosses.corporealbeast

import dev.openrune.ServerCacheManager
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.npc.heal
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitImpactHandler
import org.rsmod.game.hit.HitType
import org.rsmod.game.hit.HitValidity
import org.rsmod.game.proj.ProjAnim

/** Owned minion jump, contact siphon and retirement; no parent attack or membership loop. */
internal class CorporealBeastCoreEffects(private val deps: BossDeps) {
    fun tick(npc: Npc, encounter: BossEncounter, state: CorpState) {
        val core = state.core ?: return
        if (!core.isSlotAssigned || core.hitpoints <= 0) {
            died(encounter, state, core)
            return
        }
        if (deps.mapClock.cycle < state.nextCoreTick || state.coreJumping) return
        state.nextCoreTick = deps.mapClock.cycle + CorporealBeastRules.DARK_CORE_ATTACK_RATE_TICKS
        val roomPlayers = corpRoomPlayers(deps.playerList, npc)
        val adjacent =
            roomPlayers
                .filter { it.coords.chebyshevDistance(core.coords) <= 1 }
                .minByOrNull { it.slotId }
        if (adjacent != null) {
            adjacent.queueHit(
                source = core,
                delay = 1,
                type = HitType.Typeless,
                damage =
                    deps.random.of(
                        CorporealBeastRules.DARK_CORE_MIN_HIT..CorporealBeastRules.DARK_CORE_MAX_HIT
                    ),
                modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                validity =
                    corpHitValidity(deps, encounter, adjacent).and {
                        state.core === core && core.isSlotAssigned && core.hitpoints > 0
                    },
                impactHandler =
                    HitImpactHandler { impact -> npc.heal(impact.damage, showHitsplat = false) },
            )
            return
        }
        val target =
            roomPlayers.minWithOrNull(
                compareBy<Player> { it.coords.chebyshevDistance(core.coords) }.thenBy { it.slotId }
            ) ?: return
        state.coreJumping = true
        val destination = target.coords
        val projectile =
            ProjAnim.fromNpcToCoord(
                core,
                destination,
                CorporealBeastAssets.DARK_CORE_JUMP_SPOTANIM,
                CorporealBeastAssets.DARK_CORE_JUMP_PROJECTILE,
            )
        deps.worldRepo.projAnim(projectile)
        encounter.schedule(deps.worldQueues, projectile.serverCycles.coerceAtLeast(1)) {
            if (
                deps.encounterRegistry.isCurrent(npc, encounter) &&
                    state.core === core &&
                    core.isSlotAssigned &&
                    core.hitpoints > 0
            ) {
                PathingEntityCommon.telejump(core, deps.collision, destination)
                state.coreJumping = false
            }
        }
    }

    fun spawn(npc: Npc, encounter: BossEncounter, state: CorpState) {
        if (state.coreAlive) return
        val type = requireNotNull(ServerCacheManager.getNpc(CorporealBeastAssets.DARK_CORE_NPC_ID))
        val center = npc.coords.translate(npc.visType.size / 2, npc.visType.size / 2)
        val core = Npc(type, center)
        core.baseHitpointsLvl = CorporealBeastRules.DARK_CORE_HITPOINTS
        core.hitpoints = CorporealBeastRules.DARK_CORE_HITPOINTS
        core.mode = null
        core.movementLocked = true
        core.respawns = false
        core.suppressRewards = true
        deps.npcRepo.add(core, DARK_CORE_LIFETIME_TICKS)
        state.core = core
        state.nextCoreTick = deps.mapClock.cycle + CorporealBeastRules.DARK_CORE_ATTACK_RATE_TICKS
        encounter.own(core)
    }

    fun died(encounter: BossEncounter, state: CorpState, core: Npc) {
        if (state.core !== core) return
        state.core = null
        state.coreJumping = false
        encounter.disown(core)
    }

    fun remove(encounter: BossEncounter, state: CorpState) {
        val core = state.core ?: return
        died(encounter, state, core)
        core.clearInteraction()
        if (core.isSlotAssigned) deps.npcRepo.del(core, Int.MAX_VALUE)
    }

    private fun HitValidity.and(condition: () -> Boolean) = HitValidity { isValid() && condition() }

    private companion object {
        const val DARK_CORE_LIFETIME_TICKS = 3_600
    }
}
