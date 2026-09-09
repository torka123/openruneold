package org.rsmod.content.bosses.alchemicalhydra

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import org.rsmod.api.player.combatClearQueue
import org.rsmod.game.MapClock

/** Four-tick form presentation at the native LateCycle stage; BossEncounter owns the phase. */
internal class AlchemicalHydraTransforms @Inject constructor(private val clock: MapClock) {
    fun completeIfReady(run: HydraArenaState): Boolean {
        if (clock.cycle < run.transformEndsAt) return false
        finishTransition(run)
        return true
    }

    fun begin(run: HydraArenaState) {
        val transition = AlchemicalHydraRules.transitionNpc(run.phase) ?: return
        run.state = HydraState.TRANSITION
        run.transformEndsAt = clock.cycle + AlchemicalHydraRules.TRANSITION_TICKS
        run.boss.clearInteraction()
        run.boss.movementLocked = true
        run.boss.transmog(requireNpc(transition), Int.MAX_VALUE)
        run.boss.anim(transitionAnimation(run.phase))
        run.lightningCurrents.clear()
        run.pendingLightningCycle = Int.MAX_VALUE
        run.trackingFlame = null
        run.player.combatClearQueue()
    }

    private fun finishTransition(run: HydraArenaState) {
        val previousPhase = run.phase
        val next = AlchemicalHydraRules.nextPhase(previousPhase) ?: return
        val hp = run.boss.hitpoints
        val type = requireNpc(AlchemicalHydraRules.activeNpc(next))
        run.boss.transmog(type, Int.MAX_VALUE)
        run.boss.copyStats(type)
        run.boss.baseHitpointsLvl = HYDRA_MAX_HITPOINTS
        run.boss.hitpoints = hp
        run.boss.anim(emergeAnimation(next))
        val nextStyle = if (next == HydraPhase.FINAL) run.lastBasicStyle.opposite() else run.style
        run.initialStyle = nextStyle
        run.encounter.transitionTo(hydraPhaseName(next, nextStyle), clock.cycle)
        run.encounter.attackRateOverride = null
        run.encounter.lastAbilityTick = clock.cycle - AlchemicalHydraRules.attackRate(next)
        run.state = HydraState.ACTIVE
        run.weakened = next == HydraPhase.FINAL
        run.wrongVentStacks = 0
        run.nextVentCycle = clock.cycle + AlchemicalHydraRules.VENT_INITIAL_DELAY
        run.boss.movementLocked = false
        run.boss.actionDelay = clock.cycle + 1
    }

    private fun requireNpc(id: Int) =
        requireNotNull(ServerCacheManager.getNpc(id)) { "Missing revision-240 Hydra NPC $id" }

    private fun transitionAnimation(phase: HydraPhase): Int =
        when (phase) {
            HydraPhase.GREEN -> AlchemicalHydraAssets.GREEN_TRANSITION_ANIM
            HydraPhase.BLUE -> AlchemicalHydraAssets.BLUE_TRANSITION_ANIM
            HydraPhase.RED -> AlchemicalHydraAssets.RED_TRANSITION_ANIM
            HydraPhase.FINAL -> AlchemicalHydraAssets.DEATH_ANIM
        }

    private fun emergeAnimation(phase: HydraPhase): Int =
        when (phase) {
            HydraPhase.BLUE -> AlchemicalHydraAssets.BLUE_EMERGE_ANIM
            HydraPhase.RED -> AlchemicalHydraAssets.RED_EMERGE_ANIM
            HydraPhase.FINAL -> AlchemicalHydraAssets.FINAL_EMERGE_ANIM
            HydraPhase.GREEN -> AlchemicalHydraAssets.GREEN_RANGE_ANIM
        }
}
