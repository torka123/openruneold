package org.rsmod.content.bosses.zulrah

import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.instances.*
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitScope
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid

internal val ZULRAH_ARENA_STATE = InstanceStateKey<ZulrahArenaState>("zulrah.arena")
internal val ZULRAH_ENTRY_OPTIONS = InstanceStateKey<ZulrahEntryOptions>("zulrah.entry")

internal data class ZulrahEntryOptions(
    val rotation: Int? = null,
    val phase: Int = 1,
    val seed: Long? = null,
)

internal fun InstanceManager.zulrahState(player: Player): ZulrahArenaState? =
    sessionForPlayer(player)?.stateOrNull(ZULRAH_ARENA_STATE)

internal fun InstanceManager.zulrahState(npc: Npc): ZulrahArenaState? =
    instanceForNpc(npc)?.let(::sessionForId)?.stateOrNull(ZULRAH_ARENA_STATE)

internal fun InstanceManager.zulrahCoord(session: InstanceSession, tile: ShrineTile): CoordGrid =
    requireNotNull(
        localCoord(
            session,
            RegionLocal(0, tile.x shr 6, tile.z shr 6, tile.x and 63, tile.z and 63),
        )
    )

/** Only surface presentation, resource ownership and per-completion data; no private selector. */
internal class ZulrahArenaState(
    val player: Player,
    val session: InstanceSession,
    val boss: Npc,
    var encounter: BossEncounter,
    var surface: ZulrahSurface,
    val debug: Boolean,
    var selectionRandom: GameRandom,
    var startCycle: Int,
) {
    val state: ZulrahState
        get() {
            val phase = encounter.currentPhaseName
            return when {
                session.isEnded -> ZulrahState.DISPOSED
                phase.endsWith("-diving") -> ZulrahState.DIVING
                phase.endsWith("-emerging") || phase.endsWith("-spawning") -> ZulrahState.EMERGING
                phase == surface.key -> ZulrahState.ACTIVE
                else ->
                    ZulrahState.entries.firstOrNull { it.name.equals(phase, true) }
                        ?: ZulrahState.DISPOSED
            }
        }

    var pendingRespawn = false
    var killTicks = 0
    val hazardScope = HitScope()
    val clouds = mutableListOf<ActiveCloud>()
    val snakelings = linkedMapOf<Npc, Boolean>()

    fun owns(npc: Npc): Boolean = npc === boss || npc in snakelings
}

internal enum class ZulrahState {
    EMERGING,
    ACTIVE,
    DIVING,
    DYING,
    RESPAWNING,
    COMPLETED,
    DISPOSED,
}

internal data class ActiveCloud(val center: CoordGrid, val endCycle: Int, val visual: LocInfo)

internal data class ZulrahEntryScene(
    val camera: CoordGrid,
    val focus: CoordGrid,
    val cameraHeight: Int,
    val focusHeight: Int,
    val durationTicks: Int,
)
