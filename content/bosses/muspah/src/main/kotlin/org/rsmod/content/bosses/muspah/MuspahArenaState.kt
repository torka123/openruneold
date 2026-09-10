package org.rsmod.content.bosses.muspah

import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.instances.*
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitScope
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid

internal val MUSPAH_ARENA_STATE = InstanceStateKey<MuspahArenaState>("muspah.arena")

internal fun InstanceManager.muspahState(player: Player): MuspahArenaState? =
    sessionForPlayer(player)?.stateOrNull(MUSPAH_ARENA_STATE)

internal fun InstanceManager.muspahState(npc: Npc): MuspahArenaState? =
    instanceForNpc(npc)?.let(::sessionForId)?.stateOrNull(MUSPAH_ARENA_STATE)

/** Session-owned specialized data. Phase identity and deadlines belong to BossEncounter. */
internal class MuspahArenaState(
    val player: Player,
    val session: InstanceSession,
    val boss: Npc,
    var encounter: BossEncounter,
    var pendingRespawn: Boolean = false,
    var currentForm: MuspahForm = MuspahForm.RANGED,
    var initialForm: MuspahForm = MuspahForm.RANGED,
    var transformTarget: MuspahForm? = null,
    var firstSwapComplete: Boolean = false,
    var positiveAttacks: Int = 0,
    var eligibleDamage: Int = 0,
    var lastPositiveAttackCycle: Int = Int.MIN_VALUE,
    var firstSpecialFired: Boolean = false,
    var secondSpecialFired: Boolean = false,
    var shieldPoints: Int = 0,
    var stationaryMeleeAttacks: Int = 0,
    var shieldSkipPending: Boolean = false,
    var killStartCycle: Int = 0,
    var killTicks: Int = 0,
    var lastCloudHitCycle: Int = Int.MIN_VALUE,
    val hazardScope: HitScope = HitScope(),
    val warningSpikes: MutableList<LocInfo> = mutableListOf(),
    val staticSpikes: MutableMap<CoordGrid, LocInfo> = linkedMapOf(),
    val movingSpikes: MutableList<MovingSpike> = mutableListOf(),
    val clouds: MutableList<LightningCloud> = mutableListOf(),
    val shieldDrainSources: MutableSet<String> = linkedSetOf(),
) {
    val state: MuspahState
        get() =
            when (val phase = encounter.currentPhaseName) {
                "RANGED" -> MuspahState.RANGED
                "MELEE" -> MuspahState.MELEE
                "SHIELD" -> MuspahState.PRAYER_SHIELD
                "FINAL" -> MuspahState.FINAL_STAND
                "lightning" -> MuspahState.LIGHTNING_CLOUDS
                "dying" -> MuspahState.DYING
                "respawning" -> MuspahState.RESPAWNING
                "invalidated" -> MuspahState.INVALIDATED
                else ->
                    when {
                        phase.startsWith("spawn-") || phase == "dormant" -> MuspahState.STARTING
                        phase.startsWith("swap-") -> MuspahState.TRANSFORMING
                        phase.startsWith("homing-") -> MuspahState.HOMING_SPIKES
                        phase.startsWith("shockwave-") || phase.startsWith("shield-arrival-") ->
                            MuspahState.SHOCKWAVE
                        else -> error("Unmapped Muspah phase: $phase")
                    }
            }
}
