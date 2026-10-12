package org.rsmod.content.areas.wilderness.interactions

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.events.interact.LocCoordEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.player.PlayerRegistry
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.map.Direction
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

public enum class WildernessObeliskState {
    Idle,
    Charging,
    Cooldown,
}

public data class WildernessObeliskDefinition(
    public val wildernessLevel: Int,
    public val locId: Int,
    public val center: CoordGrid,
    public val pillars: Set<CoordGrid>,
) {
    public fun contains(coords: CoordGrid): Boolean =
        coords.level == center.level &&
            coords.x in center.x - 1..center.x + 1 &&
            coords.z in center.z - 1..center.z + 1

    public fun padTiles(): List<CoordGrid> = buildList {
        for (x in center.x - 1..center.x + 1) {
            for (z in center.z - 1..center.z + 1) add(CoordGrid(x, z, center.level))
        }
    }
}

public object WildernessObeliskRegistry {
    public val definitions: List<WildernessObeliskDefinition> =
        listOf(
            definition(13, 14829, CoordGrid(3156, 3620)),
            definition(19, 14830, CoordGrid(3227, 3667)),
            definition(27, 14827, CoordGrid(3035, 3732)),
            definition(35, 14828, CoordGrid(3106, 3794)),
            definition(44, 14826, CoordGrid(2980, 3866)),
            definition(50, 14831, CoordGrid(3307, 3916)),
        )

    public fun destination(
        source: WildernessObeliskDefinition,
        index: Int,
    ): WildernessObeliskDefinition {
        val candidates = definitions.filterNot { it == source }
        require(index in candidates.indices)
        return candidates[index]
    }

    public fun destination(wildernessLevel: Int): WildernessObeliskDefinition? =
        definitions.firstOrNull { it.wildernessLevel == wildernessLevel }

    public fun arrivalCandidates(
        playerCoords: CoordGrid,
        source: WildernessObeliskDefinition,
        destination: WildernessObeliskDefinition,
    ): List<CoordGrid> {
        val preferred =
            destination.center.translate(
                playerCoords.x - source.center.x,
                playerCoords.z - source.center.z,
            )
        return destination
            .padTiles()
            .sortedWith(
                compareBy<CoordGrid> { it.chebyshevDistance(preferred) }
                    .thenBy { it.x }
                    .thenBy { it.z }
            )
    }

    private fun definition(level: Int, locId: Int, center: CoordGrid): WildernessObeliskDefinition =
        WildernessObeliskDefinition(
            wildernessLevel = level,
            locId = locId,
            center = center,
            pillars =
                setOf(
                    center.translate(-2, -2),
                    center.translate(-2, 2),
                    center.translate(2, -2),
                    center.translate(2, 2),
                ),
        )
}

/** One world-scoped state machine for the six revision-240 Wilderness obelisks. */
public class WildernessObelisks
@Inject
constructor(
    private val players: PlayerRegistry,
    private val locRepo: LocRepository,
    private val worldQueues: WorldQueueList,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
) : PluginScript() {
    private val states =
        WildernessObeliskRegistry.definitions
            .associateWith { WildernessObeliskState.Idle }
            .toMutableMap()

    private val selectedDestinations =
        mutableMapOf<WildernessObeliskDefinition, WildernessObeliskDefinition>()

    override fun ScriptContext.startup() {
        for (obelisk in WildernessObeliskRegistry.definitions) {
            validateDefinition(obelisk)
            for (pillar in obelisk.pillars) {
                val key = LocCoordEvents.key(obelisk.locId, pillar)
                onProtectedEvent<LocCoordEvents.Op1>(key) { activate(obelisk) }
                onProtectedEvent<LocCoordEvents.Op2>(key) { activateSelectedDestination(obelisk) }
                onProtectedEvent<LocCoordEvents.Op3>(key) { setDestination() }
            }
        }
    }

    private suspend fun ProtectedAccess.activate(
        obelisk: WildernessObeliskDefinition,
        destination: WildernessObeliskDefinition? = null,
    ) {
        arriveDelay()
        when (states.getValue(obelisk)) {
            WildernessObeliskState.Charging -> {
                mes("This obelisk is already active.")
                return
            }
            WildernessObeliskState.Cooldown -> {
                mes("The obelisk is still recharging.")
                return
            }
            WildernessObeliskState.Idle -> Unit
        }
        states[obelisk] = WildernessObeliskState.Charging
        if (destination == null) {
            selectedDestinations.remove(obelisk)
        } else {
            selectedDestinations[obelisk] = destination
        }
        if (!showActivatedPillars(obelisk)) {
            states[obelisk] = WildernessObeliskState.Idle
            selectedDestinations.remove(obelisk)
            mes("The obelisk cannot be activated right now.")
            return
        }
        mes("You activate the obelisk. Its ancient magic begins to build.")
        worldQueues.add(OBELISK_CHARGE_TICKS) { completeActivation(obelisk) }
    }

    private suspend fun ProtectedAccess.activateSelectedDestination(
        obelisk: WildernessObeliskDefinition
    ) {
        val destination = player.obeliskDestination()
        if (destination == null) {
            arriveDelay()
            mes("Set an obelisk destination first using the Set Destination option.")
            return
        }
        activate(obelisk, destination)
    }

    private suspend fun ProtectedAccess.setDestination() {
        arriveDelay()
        val definitions = WildernessObeliskRegistry.definitions
        val selected =
            menu(
                title = "Set Wilderness Obelisk Destination",
                hotkeys = false,
                choices = definitions.map { "Level ${it.wildernessLevel} Wilderness" },
            )
        val destination = definitions.getOrNull(selected) ?: return
        player.attr[WILDERNESS_OBELISK_DESTINATION] = destination.wildernessLevel
        mes("Your obelisk destination is now level ${destination.wildernessLevel} Wilderness.")
    }

    private fun showActivatedPillars(obelisk: WildernessObeliskDefinition): Boolean {
        val originals =
            obelisk.pillars.mapNotNull { coords ->
                locRepo.findAll(coords).firstOrNull { it.id == obelisk.locId }
            }
        if (originals.size != obelisk.pillars.size) return false
        originals.forEach { original ->
            locRepo.del(original, OBELISK_CHARGE_TICKS)
            locRepo.add(
                original.coords,
                OBELISK_GLOWING,
                OBELISK_CHARGE_TICKS,
                original.angle,
                original.shape,
            )
        }
        return true
    }

    private fun completeActivation(source: WildernessObeliskDefinition) {
        if (states[source] != WildernessObeliskState.Charging) return
        val destination =
            selectedDestinations.remove(source)
                ?: WildernessObeliskRegistry.destination(
                    source,
                    random.of(WildernessObeliskRegistry.definitions.size - 1),
                )
        players.forEachOnline { player ->
            if (source.contains(player.coords)) teleportEligiblePlayer(player, source, destination)
        }
        states[source] = WildernessObeliskState.Cooldown
        worldQueues.add(OBELISK_COOLDOWN_TICKS) { states[source] = WildernessObeliskState.Idle }
    }

    private fun teleportEligiblePlayer(
        player: Player,
        source: WildernessObeliskDefinition,
        destination: WildernessObeliskDefinition,
    ) {
        if (player.vars["varbit.teleblock_cycles"] > 0) {
            player.mes("A magical force prevents the obelisk from teleporting you.")
            return
        }
        val arrival =
            WildernessObeliskRegistry.arrivalCandidates(player.coords, source, destination)
                .firstOrNull { collision.isZoneValid(it) && !collision.isWalkBlocked(it) }
        if (arrival == null) {
            player.mes("The obelisk's destination is obstructed.")
            return
        }
        PathingEntityCommon.telejump(player, collision, arrival)
        player.faceDirection(Direction.South)
        player.mes(
            "Ancient magic teleports you to level ${destination.wildernessLevel} Wilderness."
        )
    }

    private fun validateDefinition(obelisk: WildernessObeliskDefinition) {
        val type = requireNotNull(ServerCacheManager.getObject(obelisk.locId))
        require(type.actions.getOpOrNull(0) == "Activate")
        require(type.actions.getOpOrNull(1) == "Teleport to Destination")
        require(type.actions.getOpOrNull(2) == "Set Destination")
        require(obelisk.pillars.size == 4)
    }
}

internal const val OBELISK_GLOWING: String = "loc.wilderness_portal_stone_glowing"
// OSRS leaves the obelisk charged for roughly four seconds before teleporting everyone on the pad.
// Seven 600 ms game ticks gives the closest whole-tick duration: 4.2 seconds.
internal const val OBELISK_CHARGE_TICKS: Int = 7
internal const val OBELISK_COOLDOWN_TICKS: Int = 10

private fun Player.obeliskDestination(): WildernessObeliskDefinition? =
    WildernessObeliskRegistry.destination(
        attr.getOrDefault(WILDERNESS_OBELISK_DESTINATION, NO_OBELISK_DESTINATION)
    )

internal const val NO_OBELISK_DESTINATION: Int = -1
internal val WILDERNESS_OBELISK_DESTINATION: AttributeKey<Int> =
    AttributeKey(persistenceKey = "wilderness_obelisk_destination_level")
