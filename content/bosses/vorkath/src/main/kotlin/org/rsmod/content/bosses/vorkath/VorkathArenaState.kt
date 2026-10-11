package org.rsmod.content.bosses.vorkath

import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.InstanceStateKey
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid

internal val VORKATH_ARENA_STATE = InstanceStateKey<VorkathArenaState>("vorkath-arena")
internal val VORKATH_COMBAT_STATES =
    setOf(VorkathState.ACTIVE, VorkathState.ACID_SPECIAL, VorkathState.ZOMBIFIED_SPAWN_SPECIAL)

/** Session-owned arena data. Combat phase and cadence are read from the native BossEncounter. */
internal data class VorkathArenaState(
    val player: Player,
    val session: InstanceSession,
    val generation: Long,
    var boss: Npc,
    val firstSpecial: VorkathSpecial = VorkathSpecial.ACID,
    var combat: BossEncounter,
    private val clock: MapClock,
    var nextSpecial: VorkathSpecial = firstSpecial,
    var wakeCycle: Int = Int.MAX_VALUE,
    var specialStartCycle: Int = Int.MAX_VALUE,
    var shotsFired: Int = 0,
    var startCycle: Int = 0,
    var spawnArrivalCycle: Int = Int.MAX_VALUE,
    var freezeCycle: Int = Int.MAX_VALUE,
    var spawnLaunchCycle: Int = Int.MAX_VALUE,
    var spawnExplosionCycle: Int = Int.MAX_VALUE,
    var spawnDeathCycle: Int = Int.MAX_VALUE,
    var spawnRetireCycle: Int = Int.MAX_VALUE,
    var ownsFreeze: Boolean = false,
    var hitEpoch: Long = 0,
    val retiringSpawns: MutableList<Npc> = mutableListOf(),
    val pendingFireballs: MutableList<PendingFireball> = mutableListOf(),
    val pendingHeals: MutableList<Pair<Int, Int>> = mutableListOf(),
    var spawn: Npc? = null,
    var pendingSpawnTile: CoordGrid? = null,
    var killcount: Int = 0,
    var killTicks: Int = 0,
    var rewardEligible: Boolean = true,
    var resourcesReleased: Boolean = false,
    val acidTiles: MutableSet<CoordGrid> = linkedSetOf(),
    val acidSafeLane: MutableSet<CoordGrid> = linkedSetOf(),
    val acidVisuals: MutableList<LocInfo> = mutableListOf(),
    val pendingAcidPools: MutableList<PendingAcidPool> = mutableListOf(),
    val pendingHits: MutableList<PendingTileHit> = mutableListOf(),
) {
    var state: VorkathState
        get() = VorkathState.entries.single { phaseName(it) == combat.currentPhaseName }
        set(value) {
            combat.transitionTo(phaseName(value), clock.cycle)
        }

    val standardAttacks: Int
        get() =
            combat.attackTracesAfter(combat.phaseEnteredTick - 1).count { it.ability != "special" }

    private fun phaseName(state: VorkathState) =
        when (state) {
            VorkathState.ACID_SPECIAL -> "acid"
            VorkathState.ZOMBIFIED_SPAWN_SPECIAL -> "spawn"
            else -> state.name.lowercase()
        }

    fun owns(npc: Npc): Boolean = npc === boss || npc === spawn || npc in retiringSpawns
}
