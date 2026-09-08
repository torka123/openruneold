package org.rsmod.content.slayer.teleport

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.area.checker.wildernessLevel
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.table.slayer.SlayerAreaRow
import org.rsmod.api.table.slayer.SlayerMastersRow
import org.rsmod.api.table.slayer.SlayerTaskRow
import org.rsmod.content.slayer.core.SlayerBossTasks
import org.rsmod.content.slayer.core.CanonicalSlayerRegistry
import org.rsmod.content.slayer.core.SlayerTaskManager
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Canonical resolver for Slayer-gem destinations.
 *
 * Normal destinations come from permanent revision-240 NPC spawns which already carry the
 * canonical Slayer task IDs. Konar and Wilderness assignments filter those same spawn records by
 * the saved assignment constraints. Boss destinations are resolved beside the encounter-entry
 * objects already registered by the boss modules.
 */
@Singleton
internal class SlayerTaskTeleportRegistry
@Inject
constructor(
    private val npcs: NpcList,
    private val npcRepo: NpcRepository,
    private val locs: LocRepository,
    private val collision: CollisionFlagMap,
    private val areaChecker: AreaChecker,
) {
    private val stepValidator = StepValidator(collision)
    private val taskSpawnIndex: Map<Int, List<Npc>> by lazy {
        val byTask = mutableMapOf<Int, MutableList<Npc>>()
        for (npc in npcs) {
            if (!npc.respawns || RegionRegistry.inWorkingArea(npc.spawnCoords)) continue
            val taskIds =
                npc.visType.slayerTaskIds.takeIf { it.isNotEmpty() }
                    ?: intArrayOf(npc.visType.paramOrNull(BaseParams.slayer_task_id) ?: 0)
            for (taskId in taskIds) {
                if (taskId > 0) byTask.getOrPut(taskId, ::mutableListOf).add(npc)
            }
        }
        byTask.mapValues { (_, values) ->
            values.sortedWith(compareBy<Npc>({ it.spawnCoords.x }, { it.spawnCoords.z }, { it.id }))
        }
    }

    private fun resolve(
        task: SlayerTaskRow,
        master: SlayerMastersRow?,
        locationId: Int,
    ): SlayerTeleportResolution {
        if (SlayerBossTasks.isBossTask(task.id)) {
            val boss = resolveBoss(task)
            if (master?.masterId == KONAR_MASTER_ID && locationId > 0) {
                val area = area(locationId)
                    ?: return SlayerTeleportResolution.Missing(task, locationId, null)
                val success = boss as? SlayerTeleportResolution.Success
                    ?: return SlayerTeleportResolution.Missing(task, locationId, areaName(area))
                if (!areaChecker.inArea(areaKey(area), success.teleport.destination)) {
                    return SlayerTeleportResolution.Missing(task, locationId, areaName(area))
                }
            }
            if (master?.let(SlayerTaskManager::isWildernessMaster) == true) {
                val success = boss as? SlayerTeleportResolution.Success
                    ?: return boss
                if (success.teleport.wildernessLevel <= 0) {
                    return SlayerTeleportResolution.Missing(task, locationId, "the Wilderness")
                }
            }
            return boss
        }

        if (master?.masterId == KONAR_MASTER_ID) {
            val area = area(locationId)
                ?: return SlayerTeleportResolution.Missing(task, locationId, null)
            val key = areaKey(area)
            val spawn = taskSpawns(task.id)
                .firstOrNull { npc -> areaChecker.inArea(key, npc.spawnCoords) }
                ?: return SlayerTeleportResolution.Missing(task, locationId, areaName(area))
            val destination = safeTileBeside(spawn) { tile -> areaChecker.inArea(key, tile) }
                ?: return SlayerTeleportResolution.Unsafe(task, locationId, areaName(area))
            return success(
                task = task,
                destination = destination,
                description = areaName(area),
                locationId = locationId,
                locationKey = key,
                entranceOnly = false,
                sourceNpcId = spawn.id,
            )
        }

        if (master?.let(SlayerTaskManager::isWildernessMaster) == true) {
            for (spawn in taskSpawns(task.id)) {
                if (spawn.spawnCoords.wildernessLevel(areaChecker) <= 0) continue
                val destination = safeTileBeside(spawn) { tile ->
                    tile.wildernessLevel(areaChecker) > 0
                } ?: continue
                return success(
                    task = task,
                    destination = destination,
                    description = "the Wilderness",
                    locationId = locationId,
                    locationKey = WILDERNESS_LOCATION_KEY,
                    entranceOnly = false,
                    sourceNpcId = spawn.id,
                )
            }
            return SlayerTeleportResolution.Missing(task, locationId, "the Wilderness")
        }

        for (spawn in taskSpawns(task.id)) {
            if (spawn.spawnCoords.wildernessLevel(areaChecker) > 0) continue
            val destination = safeTileBeside(spawn) { tile ->
                tile.wildernessLevel(areaChecker) <= 0
            } ?: continue
            return success(
                task = task,
                destination = destination,
                description = "the ${task.nameLowercase} area",
                locationId = 0,
                locationKey = null,
                entranceOnly = false,
                sourceNpcId = spawn.id,
            )
        }
        return SlayerTeleportResolution.Missing(task, locationId, null)
    }

    fun resolveCanonical(
        task: SlayerTaskRow,
        category: CanonicalSlayerRegistry.Category,
    ): SlayerTeleportResolution {
        for (spawn in npcs) {
            if (!spawn.respawns || RegionRegistry.inWorkingArea(spawn.spawnCoords)) continue
            if (
                !CanonicalSlayerRegistry.isAuthorizedKill(
                    category.id,
                    spawn.visType.id,
                    spawn.spawnCoords,
                    areaChecker,
                )
            ) continue
            val destination =
                safeTileBeside(spawn) { tile ->
                    CanonicalSlayerRegistry.isAuthorizedTile(category, tile, areaChecker)
                } ?: continue
            return success(
                task = task,
                destination = destination,
                description = "the ${category.displayName} task area",
                locationId = 0,
                locationKey = category.key,
                entranceOnly = category.boss,
                sourceNpcId = spawn.id,
            )
        }
        return SlayerTeleportResolution.Missing(task, 0, "the ${category.displayName} task area")
    }

    fun isSafeDestination(destination: CoordGrid): Boolean = validateDestination(destination).safe

    fun validateDestination(destination: CoordGrid): SlayerDestinationValidation =
        SlayerDestinationValidation(
            plane = destination.level,
            loaded = collision.isZoneValid(destination),
            walkable = !collision.isWalkBlocked(destination),
            blockingObject = hasBlockingObject(destination),
            occupiedByNpc = isNpcOccupied(destination),
            inUnsupportedInstance = RegionRegistry.inWorkingArea(destination),
        )

    fun audit(): SlayerTeleportAudit {
        val normalMapped = linkedSetOf<String>()
        val bossMapped = linkedSetOf<String>()
        val missing = linkedSetOf<String>()
        val bossMissing = linkedSetOf<String>()
        val mappedDestinations = linkedMapOf<String, CoordGrid>()

        for (category in CanonicalSlayerRegistry.categories) {
            val task = SlayerTaskManager.canonicalBackingTask(category)
            if (task == null) {
                missing += "${category.key}:missing-cache-task-${category.baseTaskId}"
                if (category.boss) bossMissing += category.key
                continue
            }
            when (val resolution = resolveCanonical(task.task, category)) {
                is SlayerTeleportResolution.Success -> {
                    mappedDestinations[category.key] = resolution.teleport.destination
                    if (category.boss) {
                        bossMapped += category.key
                    } else {
                        normalMapped += category.key
                    }
                }
                else -> {
                    missing += category.key
                    if (category.boss) bossMissing += category.key
                }
            }
        }

        val destinationChecks = mappedDestinations.mapValues { validateDestination(it.value) }
        return SlayerTeleportAudit(
            totalCanonicalTasks = CanonicalSlayerRegistry.categories.size,
            normalTasksMapped = normalMapped.size,
            konarCombinationsMapped = 0,
            konarCombinationsMissing = emptyList(),
            wildernessTasksMapped = normalMapped.size + bossMapped.size,
            wildernessTasksMissing = missing.sorted(),
            bossEntrancesMapped = bossMapped.size,
            bossEntrancesMissing = bossMissing.sorted(),
            unsupportedBossTasks = emptyList(),
            tasksWithoutDestination = missing.sorted(),
            duplicateTaskKeys =
                CanonicalSlayerRegistry.categories
                    .groupBy(CanonicalSlayerRegistry.Category::key)
                    .filterValues { it.size > 1 }
                    .keys
                    .sorted(),
            duplicateLocationKeys = emptyList(),
            unloadedDestinations =
                destinationChecks.filterValues { !it.loaded }.keys.sorted(),
            blockedDestinations =
                destinationChecks
                    .filterValues { !it.walkable || it.blockingObject || it.occupiedByNpc }
                    .keys
                    .sorted(),
            unsupportedInstanceDestinations =
                destinationChecks.filterValues { it.inUnsupportedInstance }.keys.sorted(),
            invalidDestinationPlanes =
                destinationChecks.filterValues { it.plane !in 0..3 }.keys.sorted(),
        )
    }

    @Suppress("unused")
    private fun legacyAudit(): SlayerTeleportAudit {
        val tasks = SlayerTaskManager.slayerTargets
        val bosses = tasks.filter { SlayerBossTasks.isBossTask(it.id) }
        val supportedBosses = bosses.filter(SlayerBossTasks::isEncounterSupported)
        val konar = SlayerTaskManager.tasks.keys.find { it.masterId == KONAR_MASTER_ID }
        val wilderness = SlayerTaskManager.tasks.keys.find(SlayerTaskManager::isWildernessMaster)

        val normalMapped = linkedSetOf<String>()
        val missingTasks = linkedSetOf<String>()
        val mappedDestinations = linkedMapOf<String, CoordGrid>()
        for (task in tasks.filterNot { SlayerBossTasks.isBossTask(it.id) }) {
            when (val resolution = resolve(task, master = null, locationId = 0)) {
                is SlayerTeleportResolution.Success -> {
                    val key = taskKey(task)
                    normalMapped += key
                    mappedDestinations[key] = resolution.teleport.destination
                }
                else -> missingTasks += taskKey(task)
            }
        }

        var konarMapped = 0
        val konarMissing = linkedSetOf<String>()
        if (konar != null) {
            for (masterTask in SlayerTaskManager.tasks[konar].orEmpty()) {
                for (taskArea in masterTask.areas) {
                    when (val resolution = resolve(masterTask.task, konar, taskArea.areaId)) {
                        is SlayerTeleportResolution.Success -> {
                            val key = "${taskKey(masterTask.task)}:${areaKey(taskArea)}"
                            konarMapped++
                            mappedDestinations[key] = resolution.teleport.destination
                        }
                        else -> konarMissing += "${taskKey(masterTask.task)}:${areaKey(taskArea)}"
                    }
                }
            }
        }

        var wildernessMapped = 0
        val wildernessMissing = linkedSetOf<String>()
        if (wilderness != null) {
            for (masterTask in SlayerTaskManager.tasks[wilderness].orEmpty()) {
                when (val resolution = resolve(masterTask.task, wilderness, 0)) {
                    is SlayerTeleportResolution.Success -> {
                        wildernessMapped++
                        mappedDestinations["${taskKey(masterTask.task)}:wilderness"] =
                            resolution.teleport.destination
                    }
                    else -> wildernessMissing += taskKey(masterTask.task)
                }
            }
        }

        var bossMapped = 0
        val bossMissing = linkedSetOf<String>()
        for (task in supportedBosses) {
            when (val resolution = resolveBoss(task)) {
                is SlayerTeleportResolution.Success -> {
                    bossMapped++
                    mappedDestinations["${taskKey(task)}:entrance"] =
                        resolution.teleport.destination
                }
                else -> bossMissing += taskKey(task)
            }
        }

        val duplicateTasks =
            tasks.groupBy(::taskKey).filterValues { it.size > 1 }.keys.sorted()
        val duplicateLocations =
            SlayerAreaRow.all().groupBy(::areaKey).filterValues { it.size > 1 }.keys.sorted()
        val destinationChecks = mappedDestinations.mapValues { validateDestination(it.value) }

        return SlayerTeleportAudit(
            totalCanonicalTasks = tasks.size,
            normalTasksMapped = normalMapped.size,
            konarCombinationsMapped = konarMapped,
            konarCombinationsMissing = konarMissing.sorted(),
            wildernessTasksMapped = wildernessMapped,
            wildernessTasksMissing = wildernessMissing.sorted(),
            bossEntrancesMapped = bossMapped,
            bossEntrancesMissing = bossMissing.sorted(),
            unsupportedBossTasks =
                bosses.filterNot(SlayerBossTasks::isEncounterSupported)
                    .map { taskKey(it).ifBlank { "unnamed_task_${it.id}" } }.sorted(),
            tasksWithoutDestination = missingTasks.sorted(),
            duplicateTaskKeys = duplicateTasks,
            duplicateLocationKeys = duplicateLocations,
            unloadedDestinations =
                destinationChecks.filterValues { !it.loaded }.keys.sorted(),
            blockedDestinations =
                destinationChecks
                    .filterValues { !it.walkable || it.blockingObject || it.occupiedByNpc }
                    .keys
                    .sorted(),
            unsupportedInstanceDestinations =
                destinationChecks.filterValues { it.inUnsupportedInstance }.keys.sorted(),
            invalidDestinationPlanes =
                destinationChecks.filterValues { it.plane !in 0..3 }.keys.sorted(),
        )
    }

    private fun resolveBoss(task: SlayerTaskRow): SlayerTeleportResolution {
        val normalized = normalize(task.nameLowercase)
        val spec = BOSS_SPECS.firstOrNull { normalized.contains(it.taskName) }
            ?: return SlayerTeleportResolution.Missing(task, 0, null)

        spec.fixedDestination?.let { destination ->
            val safe =
                sequenceOf(destination)
                    .plus(tilesAround(destination, radius = 8).asSequence())
                    .firstOrNull(::isSafeDestination)
                    ?: return SlayerTeleportResolution.Unsafe(task, 0, spec.description)
            return success(
                task = task,
                destination = safe,
                description = spec.description,
                locationId = 0,
                locationKey = spec.taskName.replace(' ', '_'),
                entranceOnly = true,
                sourceNpcId = null,
            )
        }

        val anchor = spec.entryAnchor
            ?: return SlayerTeleportResolution.Missing(task, 0, spec.description)
        val locId = spec.entryLoc?.asRSCM(RSCMType.LOC)
            ?: return SlayerTeleportResolution.Missing(task, 0, spec.description)
        if (locs.findAll(anchor).none { it.id == locId }) {
            return SlayerTeleportResolution.Missing(task, 0, spec.description)
        }
        val candidates =
            tilesAround(anchor, radius = 1)
                .filter(::isSafeDestination)
                .let { values ->
                    spec.bossCenter?.let { center ->
                        values.sortedByDescending { it.chebyshevDistance(center) }
                    } ?: values
                }
        val destination = candidates.firstOrNull()
            ?: return SlayerTeleportResolution.Unsafe(task, 0, spec.description)
        return success(
                task = task,
                destination = destination,
                description = spec.description,
                locationId = 0,
                locationKey = spec.taskName.replace(' ', '_'),
                entranceOnly = true,
                sourceNpcId = null,
            )
    }

    private fun taskSpawns(taskId: Int): List<Npc> = taskSpawnIndex[taskId].orEmpty()

    private fun safeTileBeside(
        npc: Npc,
        extraCheck: (CoordGrid) -> Boolean,
    ): CoordGrid? =
        tilesAroundNpc(npc, radius = 3)
            .firstOrNull { tile ->
                isSafeDestination(tile) && extraCheck(tile) && hasWalkableExit(tile)
            }

    /** Reject isolated floor tiles while still allowing shore tiles beside aquatic tasks. */
    private fun hasWalkableExit(tile: CoordGrid): Boolean =
        NEIGHBOUR_OFFSETS.any { (dx, dz) ->
            val neighbour = tile.translate(dx, dz)
            collision.isZoneValid(neighbour) &&
                !collision.isWalkBlocked(neighbour) &&
                !hasBlockingObject(neighbour) &&
                !RegionRegistry.inWorkingArea(neighbour) &&
                stepValidator.canTravel(
                    level = tile.level,
                    x = tile.x,
                    z = tile.z,
                    offsetX = dx,
                    offsetZ = dz,
                    size = 1,
                )
        }

    private fun tilesAroundNpc(npc: Npc, radius: Int): Sequence<CoordGrid> = sequence {
        val size = npc.visType.size.coerceAtLeast(1)
        val base = npc.spawnCoords
        for (distance in 1..radius) {
            val minX = base.x - distance
            val maxX = base.x + size - 1 + distance
            val minZ = base.z - distance
            val maxZ = base.z + size - 1 + distance
            for (x in minX..maxX) {
                yield(CoordGrid(x, minZ, base.level))
                yield(CoordGrid(x, maxZ, base.level))
            }
            for (z in (minZ + 1) until maxZ) {
                yield(CoordGrid(minX, z, base.level))
                yield(CoordGrid(maxX, z, base.level))
            }
        }
    }.distinct()

    private fun tilesAround(anchor: CoordGrid, radius: Int): List<CoordGrid> = buildList {
        for (distance in 1..radius) {
            for (dx in -distance..distance) {
                add(anchor.translate(dx, -distance))
                add(anchor.translate(dx, distance))
            }
            for (dz in (-distance + 1) until distance) {
                add(anchor.translate(-distance, dz))
                add(anchor.translate(distance, dz))
            }
        }
    }.distinct()

    private fun isNpcOccupied(tile: CoordGrid): Boolean =
        npcRepo.findAll(ZoneKey.from(tile), zoneRadius = 1).any { npc ->
        if (npc.coords.level != tile.level) return@any false
        val size = npc.visType.size.coerceAtLeast(1)
        tile.x in npc.coords.x until (npc.coords.x + size) &&
            tile.z in npc.coords.z until (npc.coords.z + size)
    }

    private fun hasBlockingObject(tile: CoordGrid): Boolean =
        locs.findAll(tile).any { loc ->
            ServerCacheManager.getObject(loc.id)?.blockWalk?.let { it != 0 } == true
        }

    private fun success(
        task: SlayerTaskRow,
        destination: CoordGrid,
        description: String,
        locationId: Int,
        locationKey: String?,
        entranceOnly: Boolean,
        sourceNpcId: Int?,
    ): SlayerTeleportResolution.Success =
        SlayerTeleportResolution.Success(
            SlayerTaskTeleport(
                taskId = task.id,
                taskKey = taskKey(task),
                destination = destination,
                description = description,
                requiredLocationId = locationId.takeIf { it > 0 },
                requiredLocationKey = locationKey,
                wildernessLevel = destination.wildernessLevel(areaChecker).coerceAtLeast(0),
                entranceOnly = entranceOnly,
                sourceNpcId = sourceNpcId,
            )
        )

    private fun area(id: Int): SlayerAreaRow? = SlayerAreaRow.all().find { it.areaId == id }

    fun areaKey(area: SlayerAreaRow): String =
        runCatching { RSCM.getReverseMapping(RSCMType.AREA, area.areaId) }
            .getOrElse { normalize(area.areaText).replace(' ', '_') }

    private fun areaName(area: SlayerAreaRow): String =
        area.areaNameInHelper.takeIf(String::isNotBlank) ?: area.areaText

    companion object {
        const val KONAR_MASTER_ID = 8
        const val WILDERNESS_LOCATION_KEY = "wilderness"
        private val NEIGHBOUR_OFFSETS =
            listOf(
                -1 to -1,
                0 to -1,
                1 to -1,
                -1 to 0,
                1 to 0,
                -1 to 1,
                0 to 1,
                1 to 1,
            )

        fun taskKey(task: SlayerTaskRow): String = normalize(task.nameLowercase).replace(' ', '_')

        fun normalize(value: String): String =
            value.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

        /* Entry locs and coordinates are verified against the active revision-240 map/cache. */
        private val BOSS_SPECS = listOf(
            BossSpec(
                taskName = "barrows",
                description = "the Barrows entrance",
                fixedDestination = CoordGrid(3565, 3288, 0),
            ),
            BossSpec(
                taskName = "commander zilyana",
                description = "the Saradomin boss entrance",
                entryLoc = "loc.godwars_dungeon_door_saradomin",
                entryAnchor = CoordGrid(2908, 5265, 0),
                bossCenter = CoordGrid(2897, 5269, 0),
            ),
            BossSpec(
                taskName = "general graardor",
                description = "the Bandos boss entrance",
                entryLoc = "loc.godwars_dungeon_door_bandos",
                entryAnchor = CoordGrid(2863, 5354, 2),
                bossCenter = CoordGrid(2872, 5358, 2),
            ),
            BossSpec(
                taskName = "king black dragon",
                description = "the King Black Dragon encounter lever",
                entryLoc = "loc.dragonkinginlever",
                entryAnchor = CoordGrid(3067, 10253, 0),
            ),
            BossSpec(
                taskName = "kree arra",
                description = "the Armadyl boss entrance",
                entryLoc = "loc.godwars_dungeon_door_armadyl",
                entryAnchor = CoordGrid(2839, 5295, 2),
                bossCenter = CoordGrid(2832, 5302, 2),
            ),
            BossSpec(
                taskName = "k ril tsutsaroth",
                description = "the Zamorak boss entrance",
                entryLoc = "loc.godwars_dungeon_door_zamorak",
                entryAnchor = CoordGrid(2925, 5332, 2),
                bossCenter = CoordGrid(2925, 5322, 2),
            ),
        )
    }
}

internal data class SlayerTaskTeleport(
    val taskId: Int,
    val taskKey: String,
    val destination: CoordGrid,
    val description: String,
    val requiredLocationId: Int?,
    val requiredLocationKey: String?,
    val wildernessLevel: Int,
    val entranceOnly: Boolean,
    val sourceNpcId: Int?,
)

internal sealed interface SlayerTeleportResolution {
    data class Success(val teleport: SlayerTaskTeleport) : SlayerTeleportResolution

    data class Missing(
        val task: SlayerTaskRow,
        val locationId: Int,
        val locationName: String?,
    ) : SlayerTeleportResolution

    data class Unsafe(
        val task: SlayerTaskRow,
        val locationId: Int,
        val locationName: String?,
    ) : SlayerTeleportResolution
}

internal data class SlayerTeleportAudit(
    val totalCanonicalTasks: Int,
    val normalTasksMapped: Int,
    val konarCombinationsMapped: Int,
    val konarCombinationsMissing: List<String>,
    val wildernessTasksMapped: Int,
    val wildernessTasksMissing: List<String>,
    val bossEntrancesMapped: Int,
    val bossEntrancesMissing: List<String>,
    val unsupportedBossTasks: List<String>,
    val tasksWithoutDestination: List<String>,
    val duplicateTaskKeys: List<String>,
    val duplicateLocationKeys: List<String>,
    val unloadedDestinations: List<String>,
    val blockedDestinations: List<String>,
    val unsupportedInstanceDestinations: List<String>,
    val invalidDestinationPlanes: List<String>,
)

internal data class SlayerDestinationValidation(
    val plane: Int,
    val loaded: Boolean,
    val walkable: Boolean,
    val blockingObject: Boolean,
    val occupiedByNpc: Boolean,
    val inUnsupportedInstance: Boolean,
) {
    val safe: Boolean
        get() =
            SlayerGemTeleportPolicy.destinationIsSafe(
                plane = plane,
                loaded = loaded,
                walkable = walkable,
                blockingObject = blockingObject,
                occupiedByNpc = occupiedByNpc,
                inUnsupportedInstance = inUnsupportedInstance,
            )
}

private data class BossSpec(
    val taskName: String,
    val description: String,
    val entryLoc: String? = null,
    val entryAnchor: CoordGrid? = null,
    val bossCenter: CoordGrid? = null,
    val fixedDestination: CoordGrid? = null,
)
