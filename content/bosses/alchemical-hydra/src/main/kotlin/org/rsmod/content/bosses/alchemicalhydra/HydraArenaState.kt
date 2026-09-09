package org.rsmod.content.bosses.alchemicalhydra

import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.InstanceStateKey
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/**
 * Arena resources belong to one native instance session; phase and rotation belong to
 * BossEncounter.
 */
internal val HYDRA_ARENA_STATE = InstanceStateKey<HydraArenaState>("hydra.arena")

internal fun InstanceManager.hydraState(player: Player): HydraArenaState? =
    sessionForPlayer(player)?.stateOrNull(HYDRA_ARENA_STATE)

internal fun InstanceManager.hydraState(npc: Npc): HydraArenaState? =
    instanceForNpc(npc)?.let(::sessionForId)?.stateOrNull(HYDRA_ARENA_STATE)

internal data class PendingPoison(val tile: CoordGrid, val impactCycle: Int)

internal data class PoisonPool(val tile: CoordGrid, val endCycle: Int)

internal data class LightningCurrent(var tile: CoordGrid, val endCycle: Int)

internal data class HydraArenaState(
    val player: Player,
    val session: InstanceSession,
    val boss: Npc,
    var initialStyle: HydraAttackStyle,
    var encounter: BossEncounter,
    var state: HydraState = HydraState.OBSERVATION,
    var lastBasicStyle: HydraAttackStyle = initialStyle,
    var weakened: Boolean = false,
    var wrongVentStacks: Int = 0,
    var transformEndsAt: Int = 0,
    var pendingRespawn: Boolean = false,
    var nextVentCycle: Int = 0,
    val pendingPoison: MutableList<PendingPoison> = mutableListOf(),
    val poisonPools: MutableList<PoisonPool> = mutableListOf(),
    var lastPoolDamageCycle: Int = -1,
    var pendingLightningCycle: Int = Int.MAX_VALUE,
    val lightningCurrents: MutableList<LightningCurrent> = mutableListOf(),
    val flameTiles: MutableSet<CoordGrid> = linkedSetOf(),
    var trackingFlame: CoordGrid? = null,
    var flameEndCycle: Int = 0,
    var fireBurnsRemaining: Int = 0,
    var nextBurnCycle: Int = 0,
    var lastFireContactCycle: Int = -1,
) {
    val phase: HydraPhase
        get() = HydraPhase.valueOf(encounter.currentPhaseName.substringBefore('.').uppercase())

    val style: HydraAttackStyle
        get() {
            val group = if (phase == HydraPhase.FINAL) 1 else 3
            val nextIndex = (encounter.lastRotationIndex + 1) % (group * 2)
            return if (nextIndex < group) initialStyle else initialStyle.opposite()
        }
}
