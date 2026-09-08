package org.rsmod.content.slayer.tower

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import kotlin.math.abs
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpLoc5
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SlayerTowerTransportScript
@Inject
constructor(private val locRepo: LocRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        for (type in towerTransportTypes()) {
            type.actionAt(0)?.takeIf(SlayerTowerRoutes::isTransportAction)?.let { action ->
                onOpLoc1(type) { useTransport(it.loc, action) }
            }
            type.actionAt(1)?.takeIf(SlayerTowerRoutes::isTransportAction)?.let { action ->
                onOpLoc2(type) { useTransport(it.loc, action) }
            }
            type.actionAt(2)?.takeIf(SlayerTowerRoutes::isTransportAction)?.let { action ->
                onOpLoc3(type) { useTransport(it.loc, action) }
            }
            type.actionAt(3)?.takeIf(SlayerTowerRoutes::isTransportAction)?.let { action ->
                onOpLoc4(type) { useTransport(it.loc, action) }
            }
            type.actionAt(4)?.takeIf(SlayerTowerRoutes::isTransportAction)?.let { action ->
                onOpLoc5(type) { useTransport(it.loc, action) }
            }
        }
    }

    private suspend fun ProtectedAccess.useTransport(loc: BoundLocInfo, action: String) {
        if (!SlayerTowerRoutes.contains(loc.coords)) {
            return
        }
        arriveDelay()
        val explicit = SlayerTowerRoutes.directionForAction(action)
        val automatic = SlayerTowerRoutes.defaultDirection(loc.level)
        if (explicit == null && automatic == null) {
            startDialogue {
                val chosen =
                    choice2(
                        "Climb up.",
                        SlayerTowerDirection.Up,
                        "Climb down.",
                        SlayerTowerDirection.Down,
                        title = "Climb up or down?",
                    )
                this@useTransport.finishTransport(loc, chosen)
            }
            return
        }
        finishTransport(loc, explicit ?: automatic!!)
    }

    private fun ProtectedAccess.finishTransport(
        loc: BoundLocInfo,
        direction: SlayerTowerDirection,
    ) {
        val targetLevel = SlayerTowerRoutes.destinationLevel(loc.level, direction)
        if (targetLevel == null) {
            mes("You cannot go any further in that direction.")
            return
        }
        val destination = findOppositeLanding(loc, direction, targetLevel)
        if (destination == null) {
            mes("The way between these floors is blocked.")
            return
        }
        telejump(destination)
    }

    private fun ProtectedAccess.findOppositeLanding(
        source: BoundLocInfo,
        direction: SlayerTowerDirection,
        targetLevel: Int,
    ): CoordGrid? {
        val opposite =
            if (direction == SlayerTowerDirection.Up) {
                SlayerTowerDirection.Down
            } else {
                SlayerTowerDirection.Up
            }
        val targetLoc =
            nearbyTransportLocs(source.coords, targetLevel)
                .filter { candidate ->
                    val type = ServerCacheManager.getObject(candidate.id) ?: return@filter false
                    val directions = type.actionTexts().map(SlayerTowerRoutes::directionForAction)
                    directions.any { it == opposite } || directions.all { it == null }
                }
                .minByOrNull { candidate ->
                    abs(candidate.coords.x - source.coords.x) +
                        abs(candidate.coords.z - source.coords.z)
                }

        if (targetLoc != null) {
            val type = ServerCacheManager.getObject(targetLoc.id)!!
            val bound = BoundLocInfo(targetLoc, type)
            findWalkableLanding(bound, source.coords)?.let { return it }
        }

        val projected = CoordGrid(player.coords.x, player.coords.z, targetLevel)
        if (SlayerTowerRoutes.contains(projected) && !mapBlocked(projected)) {
            return projected
        }
        return nearbyWalkableTiles(projected, radius = 3).firstOrNull()
    }

    private fun nearbyTransportLocs(source: CoordGrid, targetLevel: Int): Sequence<LocInfo> =
        sequence {
            val centre = ZoneKey.from(CoordGrid(source.x, source.z, targetLevel))
            for (zoneX in -1..1) {
                for (zoneZ in -1..1) {
                    for (loc in locRepo.findAll(centre.translate(zoneX, zoneZ))) {
                        if (!SlayerTowerRoutes.contains(loc.coords)) {
                            continue
                        }
                        val type = ServerCacheManager.getObject(loc.id) ?: continue
                        if (isTransportType(type)) {
                            yield(loc)
                        }
                    }
                }
            }
        }

    private fun ProtectedAccess.findWalkableLanding(
        landing: BoundLocInfo,
        source: CoordGrid,
    ): CoordGrid? {
        val candidates =
            sequence {
                    val minX = landing.coords.x - 1
                    val maxX = landing.coords.x + landing.adjustedWidth
                    val minZ = landing.coords.z - 1
                    val maxZ = landing.coords.z + landing.adjustedLength
                    for (x in minX..maxX) {
                        yield(CoordGrid(x, minZ, landing.level))
                        yield(CoordGrid(x, maxZ, landing.level))
                    }
                    for (z in (minZ + 1) until maxZ) {
                        yield(CoordGrid(minX, z, landing.level))
                        yield(CoordGrid(maxX, z, landing.level))
                    }
                }
                .distinct()
                .filter { SlayerTowerRoutes.contains(it) && !mapBlocked(it) }
                .sortedBy { abs(it.x - source.x) + abs(it.z - source.z) }
        return candidates.firstOrNull()
    }

    private fun ProtectedAccess.nearbyWalkableTiles(
        centre: CoordGrid,
        radius: Int,
    ): Sequence<CoordGrid> = sequence {
        for (distance in 1..radius) {
            for (x in (centre.x - distance)..(centre.x + distance)) {
                for (z in (centre.z - distance)..(centre.z + distance)) {
                    if (abs(x - centre.x) != distance && abs(z - centre.z) != distance) {
                        continue
                    }
                    val tile = CoordGrid(x, z, centre.level)
                    if (SlayerTowerRoutes.contains(tile) && !mapBlocked(tile)) {
                        yield(tile)
                    }
                }
            }
        }
    }

    private fun towerTransportTypes(): Set<ObjectServerType> {
        val types = mutableSetOf<ObjectServerType>()
        val minZoneX = SlayerTowerRoutes.MIN_X / ZoneGrid.LENGTH
        val maxZoneX = SlayerTowerRoutes.MAX_X / ZoneGrid.LENGTH
        val minZoneZ = SlayerTowerRoutes.MIN_Z / ZoneGrid.LENGTH
        val maxZoneZ = SlayerTowerRoutes.MAX_Z / ZoneGrid.LENGTH
        for (level in SlayerTowerRoutes.MIN_LEVEL..SlayerTowerRoutes.MAX_LEVEL) {
            for (zoneX in minZoneX..maxZoneX) {
                for (zoneZ in minZoneZ..maxZoneZ) {
                    for (loc in locRepo.findAll(ZoneKey(zoneX, zoneZ, level))) {
                        val type = ServerCacheManager.getObject(loc.id) ?: continue
                        if (isTransportType(type) && !hasGenericTransportHandler(type)) {
                            types += type
                        }
                    }
                }
            }
        }
        return types
    }

    private fun isTransportType(type: ObjectServerType): Boolean {
        val identity = "${type.internalName} ${type.name}".lowercase()
        val namedTransport =
            identity.contains("ladder") ||
                identity.contains("stair") ||
                identity.contains("steps")
        return namedTransport && type.actionTexts().any(SlayerTowerRoutes::isTransportAction)
    }

    private fun hasGenericTransportHandler(type: ObjectServerType): Boolean =
        GENERIC_CONTENT_NAMES.any { name ->
            type.contentGroup == name.asRSCM(RSCMType.CONTENT)
        }

    private fun ObjectServerType.actionAt(index: Int): String? = actions.getOpOrNull(index)

    private fun ObjectServerType.actionTexts(): List<String> =
        (0..4).mapNotNull { index -> actionAt(index) }

    private companion object {
        private val GENERIC_CONTENT_NAMES =
            listOf(
                "content.ladder_down",
                "content.ladder_up",
                "content.ladder_option",
                "content.spiralstaircase_down",
                "content.spiralstaircase_up",
                "content.spiralstaircase_option",
            )
    }
}
