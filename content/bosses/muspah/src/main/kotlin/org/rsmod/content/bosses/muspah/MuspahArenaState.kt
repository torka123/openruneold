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

/** Session-owned presentation and specialized mechanics; phase timers migrate in the next step. */
internal class MuspahArenaState(
    val player: Player,
    val session: InstanceSession,
    val boss: Npc,
    var encounter: BossEncounter,
    var pendingRespawn: Boolean = false,
    var state: MuspahState = MuspahState.STARTING,
    var currentForm: MuspahForm = MuspahForm.RANGED,
    var initialForm: MuspahForm = MuspahForm.RANGED,
    var resumeState: MuspahState = MuspahState.RANGED,
    var transformTarget: MuspahForm? = null,
    var firstSwapComplete: Boolean = false,
    var positiveAttacks: Int = 0,
    var eligibleDamage: Int = 0,
    var lastPositiveAttackCycle: Int = Int.MIN_VALUE,
    var firstSpecialFired: Boolean = false,
    var secondSpecialFired: Boolean = false,
    var shieldPoints: Int = 0,
    var stationaryMeleeAttacks: Int = 0,
    var startReadyCycle: Int = Int.MAX_VALUE,
    var transformCycle: Int = Int.MAX_VALUE,
    var homingEndCycle: Int = Int.MAX_VALUE,
    var lightningIndex: Int = 0,
    var nextLightningCycle: Int = Int.MAX_VALUE,
    var shockwaveCycle: Int = Int.MAX_VALUE,
    var shieldApplyCycle: Int = Int.MAX_VALUE,
    var shockwaveFired: Boolean = false,
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
)
