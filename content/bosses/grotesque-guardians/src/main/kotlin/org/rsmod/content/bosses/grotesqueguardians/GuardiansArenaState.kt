package org.rsmod.content.bosses.grotesqueguardians

import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.InstanceStateKey
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid

internal val GUARDIANS_ARENA = InstanceStateKey<GuardiansArenaState>("guardians.arena")

internal fun InstanceManager.guardiansState(player: Player): GuardiansArenaState? =
    sessionForPlayer(player)?.stateOrNull(GUARDIANS_ARENA)

internal fun InstanceManager.guardiansState(npc: Npc): GuardiansArenaState? =
    instanceForNpc(npc)?.let(::sessionForId)?.stateOrNull(GUARDIANS_ARENA)

internal data class EnergySphere(val loc: LocInfo, val spawnCycle: Int)

internal data class Lightning(val tile: CoordGrid, val activeCycle: Int, val endCycle: Int)

/**
 * Paired presentation and arena resources only; native encounters own phases and attack cadence.
 */
internal class GuardiansArenaState(
    val player: Player,
    val session: InstanceSession,
    val dawn: Npc,
    val dusk: Npc,
    val dawnEncounter: BossEncounter,
    val duskEncounter: BossEncounter,
    private val clock: MapClock,
) {
    var nextDebrisCycle = 0
    val spheres = mutableListOf<EnergySphere>()
    val lightning = mutableListOf<Lightning>()
    var phase: GrotesquePhase
        get() = GrotesquePhase.valueOf(duskEncounter.currentPhaseName.uppercase())
        set(value) {
            dawnEncounter.transitionTo(value.name.lowercase(), clock.cycle)
            duskEncounter.transitionTo(value.name.lowercase(), clock.cycle)
        }
}
