package org.rsmod.content.bosses.corporealbeast

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.npc.heal
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitType

/**
 * Public-room hazards and recovery only. BossCombat owns ordinary attacks and encounter lifetime.
 */
@Singleton
internal class CorporealBeastRoomEffects
@Inject
constructor(private val deps: BossDeps, private val npcList: NpcList) {
    val cores = CorporealBeastCoreEffects(deps)
    val area = CorporealBeastAreaEffects(deps)

    fun state(npc: Npc): CorpState {
        val encounter = deps.encounterRegistry.of(npc)
        return encounter.state(CORP_STATE) {
            CorpState(
                    deps.mapClock.cycle,
                    deps.mapClock.cycle + 7,
                    deps.mapClock.cycle + 7,
                    deps.mapClock.cycle + 20,
                    deps.mapClock.cycle + 2,
                )
                .also { state -> encounter.onCleanup { cores.remove(encounter, state) } }
        }
    }

    fun afterAttack(npc: Npc) {
        val state = state(npc)
        state.lastCombatTick = deps.mapClock.cycle
        if (
            CorporealBeastRules.coreSpawnFromAttack(
                npc.hitpoints,
                deps.random.of(Int.MAX_VALUE),
                state.coreAlive,
            )
        )
            cores.spawn(npc, deps.encounterRegistry.of(npc), state)
    }

    fun onBossImpact(npc: Npc, damage: Int) {
        if (npc.hitpoints <= 0) return
        val state = state(npc)
        state.lastCombatTick = deps.mapClock.cycle
        if (
            CorporealBeastRules.coreSpawnFromHit(
                damage,
                deps.random.of(Int.MAX_VALUE),
                state.coreAlive,
            )
        )
            cores.spawn(npc, deps.encounterRegistry.of(npc), state)
    }

    fun coreDied(core: Npc) {
        // Two public spawns: native ownership lookup, no secondary core-to-boss lifecycle map.
        for (boss in npcList) {
            if (boss.id != CorporealBeastAssets.BOSS_NPC_ID) continue
            val encounter = deps.encounterRegistry.existing(boss) ?: continue
            val state = encounter.stateOrNull<CorpState>(CORP_STATE) ?: continue
            if (state.core === core) {
                cores.died(encounter, state, core)
                return
            }
        }
    }

    /**
     * Preserve the baseline post-movement LateCycle hazard snapshot, not an ordinary attack loop.
     */
    fun tick(npc: Npc) {
        val encounter = deps.encounterRegistry.existing(npc) ?: return
        val state = encounter.stateOrNull<CorpState>(CORP_STATE) ?: return
        if (!npc.isSlotAssigned || npc.hitpoints <= 0) {
            deps.encounterRegistry.remove(npc)
            return
        }
        processStomp(npc, state, encounter)
        cores.tick(npc, encounter, state)
        processEmptyRoomHealing(npc, state)
        processStatRestore(npc, state)
        if (CorporealBeastRules.shouldReset(deps.mapClock.cycle, state.lastCombatTick)) {
            deps.encounterRegistry.remove(npc)
            npc.clearInteraction()
            PathingEntityCommon.telejump(npc, deps.collision, npc.spawnCoords)
            npc.copyStats(npc.type)
            npc.defaultMode()
        }
    }

    private fun processStomp(npc: Npc, state: CorpState, encounter: BossEncounter) {
        if (deps.mapClock.cycle < state.nextStompTick) return
        val targets =
            corpRoomPlayers(deps.playerList, npc).filter { player ->
                CorporealBeastRules.isUnderFootprint(
                    npc.coords.x,
                    npc.coords.z,
                    npc.visType.size,
                    player.coords.x,
                    player.coords.z,
                )
            }
        if (targets.isEmpty()) return
        state.nextStompTick = deps.mapClock.cycle + CorporealBeastRules.STOMP_INTERVAL_TICKS
        npc.anim(CorporealBeastAssets.STOMP_ANIMATION)
        targets.forEach { player ->
            player.queueHit(
                source = npc,
                delay = 1,
                type = HitType.Typeless,
                damage =
                    deps.random.of(
                        CorporealBeastRules.STOMP_MIN_HIT..CorporealBeastRules.STOMP_MAX_HIT
                    ),
                modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                validity = corpHitValidity(deps, encounter, player),
            )
        }
    }

    private fun processEmptyRoomHealing(npc: Npc, state: CorpState) {
        if (
            corpRoomPlayers(deps.playerList, npc).isNotEmpty() ||
                deps.mapClock.cycle < state.nextEmptyHealTick
        )
            return
        state.nextEmptyHealTick =
            deps.mapClock.cycle + CorporealBeastRules.EMPTY_ROOM_HEAL_INTERVAL_TICKS
        npc.heal(CorporealBeastRules.EMPTY_ROOM_HEAL, showHitsplat = false)
    }

    private fun processStatRestore(npc: Npc, state: CorpState) {
        if (deps.mapClock.cycle < state.nextStatRestoreTick) return
        state.nextStatRestoreTick =
            deps.mapClock.cycle + CorporealBeastRules.STAT_RESTORE_INTERVAL_TICKS
        npc.attackLvl = stepToward(npc.attackLvl, npc.baseAttackLvl)
        npc.strengthLvl = stepToward(npc.strengthLvl, npc.baseStrengthLvl)
        npc.defenceLvl = stepToward(npc.defenceLvl, npc.baseDefenceLvl)
        npc.rangedLvl = stepToward(npc.rangedLvl, npc.baseRangedLvl)
        npc.magicLvl = stepToward(npc.magicLvl, npc.baseMagicLvl)
        npc.magicDefenceLvl = stepToward(npc.magicDefenceLvl, npc.baseMagicDefenceLvl)
    }

    private fun stepToward(current: Int, base: Int): Int =
        when {
            current < base -> current + 1
            current > base -> current - 1
            else -> current
        }
}
