package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import java.nio.file.Files
import java.nio.file.Path
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.content.skills.construction.data.DonorBuildable
import org.rsmod.content.skills.construction.data.DonorConstructionData
import org.rsmod.content.skills.construction.data.DonorHotspot
import org.rsmod.content.skills.construction.data.DonorRoom
import org.rsmod.events.EventBus
import org.rsmod.map.CoordGrid

internal const val HOUSE_TEST2_REPORT_PATH = "data/reports/housetest2-audit.txt"
private const val HOUSE_TEST2_ROOMS_PER_PAGE = 63

internal data class HouseTest2RoomCopy(
    val room: DonorRoom,
    val placement: HouseRoom,
    val rotation: Int,
    val copy: Int,
    val copiesAtRotation: Int,
    val wing: String,
    val furniture: Map<String, String>,
)

internal data class HouseTest2Placement(
    val page: Int,
    val wing: String,
    val roomName: String,
    val roomStableId: String,
    val hotspotName: String,
    val rotation: Int,
    val copy: Int,
    val copiesAtRotation: Int,
    val builtLocIds: List<Int>,
)

internal data class HouseTest2AuditEntry(
    val index: Int,
    val buildable: DonorBuildable,
    val placements: List<HouseTest2Placement>,
)

internal data class HouseTest2Page(
    val index: Int,
    val label: String,
    val blueprint: HouseBlueprint,
    val rooms: List<HouseTest2RoomCopy>,
)

internal data class HouseTest2Plan(
    val pages: List<HouseTest2Page>,
    val entries: List<HouseTest2AuditEntry>,
    val roomDefinitions: Set<String>,
    val roomRotations: Set<Pair<String, Int>>,
    val enabledBuildables: Set<String>,
    val inactiveBuildables: Set<String>,
    val unreachableBuildables: Set<String>,
) {
    val totalRooms: Int
        get() = DonorConstructionData.rooms.size

    val representedRooms: Int
        get() = roomDefinitions.size

    val totalRotations: Int
        get() = DonorConstructionData.rooms.size * 4

    val representedRotations: Int
        get() = roomRotations.size

    val totalBuildables: Int
        get() = enabledBuildables.size

    val representedBuildables: Int
        get() =
            entries.count { it.buildable.name in enabledBuildables && it.placements.isNotEmpty() }

    val constructedComponents: Int
        get() =
            entries.sumOf { entry ->
                entry.placements.sumOf { placement -> placement.builtLocIds.count { it > 0 } }
            }
}

internal data class HouseTest2PageValidation(
    val page: Int,
    val placedBuildables: Set<String>,
    val placementFailures: Map<String, List<String>>,
)

internal data class HouseTest2Session(
    val plan: HouseTest2Plan,
    val returnCoord: CoordGrid,
    var page: Int = 0,
    var currentEntry: Int = 0,
    var lastEntry: Int = 0,
    val pageValidation: MutableMap<Int, HouseTest2PageValidation> = mutableMapOf(),
)

/** Registry-driven, deterministic audit-house coverage planner. */
internal object HouseTest2Planner {
    fun build(): HouseTest2Plan {
        val hotspots = DonorConstructionData.hotspots.associateBy(DonorHotspot::name)
        val rooms = DonorConstructionData.rooms.sortedWith(compareBy(::wingFor, DonorRoom::name))
        val roomSeeds = rooms.flatMap { room -> roomCopies(room, hotspots) }
        val gardenSeeds = roomSeeds.filter { it.room.name == "GARDEN" }
        val remaining = roomSeeds.filterNot { it.room.name == "GARDEN" }
        val pageCount =
            maxOf(
                1,
                gardenSeeds.size,
                (remaining.size + HOUSE_TEST2_ROOMS_PER_PAGE - 1) / HOUSE_TEST2_ROOMS_PER_PAGE,
            )
        val pageSeeds = MutableList(pageCount) { mutableListOf<RoomSeed>() }
        remaining.forEachIndexed { index, seed ->
            pageSeeds[index / HOUSE_TEST2_ROOMS_PER_PAGE] += seed
        }
        val defaultGarden =
            gardenSeeds.firstOrNull { it.furniture["CENTERPIECE"] == "EXIT_PORTAL" }
                ?: gardenSeeds.firstOrNull()
                ?: error("HouseTest2 requires the production GARDEN room")

        val pages =
            pageSeeds.mapIndexed { pageIndex, seeds ->
                val garden = gardenSeeds.getOrNull(pageIndex) ?: defaultGarden
                createPage(pageIndex, garden, seeds)
            }
        val placements = mutableMapOf<String, MutableList<HouseTest2Placement>>()
        pages.forEach { page ->
            page.rooms.forEach { copy ->
                copy.furniture.forEach { (hotspotName, buildableName) ->
                    val buildable =
                        DonorConstructionData.buildables.firstOrNull { it.name == buildableName }
                            ?: return@forEach
                    placements.getOrPut(buildableName, ::mutableListOf) +=
                        HouseTest2Placement(
                            page = page.index,
                            wing = copy.wing,
                            roomName = copy.room.name,
                            roomStableId = copy.placement.stableId,
                            hotspotName = hotspotName,
                            rotation = copy.rotation,
                            copy = copy.copy,
                            copiesAtRotation = copy.copiesAtRotation,
                            builtLocIds = buildable.builtLocIds.toList(),
                        )
                }
            }
        }
        val entries =
            DonorConstructionData.buildables.sortedBy(DonorBuildable::name).mapIndexed {
                index,
                buildable ->
                HouseTest2AuditEntry(
                    index = index,
                    buildable = buildable,
                    placements = placements[buildable.name].orEmpty(),
                )
            }
        val enabledBuildables = hotspots.values.flatMap { it.buildables }.toSet()
        val allBuildables = DonorConstructionData.buildables.mapTo(mutableSetOf()) { it.name }
        return HouseTest2Plan(
            pages = pages,
            entries = entries,
            roomDefinitions = pages.flatMap { it.rooms }.mapTo(mutableSetOf()) { it.room.name },
            roomRotations =
                pages.flatMap { it.rooms }.mapTo(mutableSetOf()) { it.room.name to it.rotation },
            enabledBuildables = enabledBuildables,
            inactiveBuildables = allBuildables - enabledBuildables,
            unreachableBuildables =
                enabledBuildables.filterTo(mutableSetOf()) { name ->
                    entries.first { it.buildable.name == name }.placements.isEmpty()
                },
        )
    }

    private fun roomCopies(room: DonorRoom, hotspots: Map<String, DonorHotspot>): List<RoomSeed> {
        val physical = room.hotspots.mapNotNull(hotspots::get).filter { it.name != "EMPTY" }
        val copies = maxOf(1, physical.maxOfOrNull { it.buildables.size } ?: 1)
        return buildList {
            for (rotation in 0..3) {
                for (copy in 0 until copies) {
                    val furniture =
                        physical
                            .mapNotNull { hotspot ->
                                val option =
                                    hotspot.buildables.getOrNull(copy % hotspot.buildables.size)
                                option?.let { hotspot.name to it }
                            }
                            .toMap()
                    add(
                        RoomSeed(
                            room = room,
                            rotation = rotation,
                            copy = copy + 1,
                            copiesAtRotation = copies,
                            wing = wingFor(room),
                            furniture = furniture,
                        )
                    )
                }
            }
        }
    }

    private fun createPage(
        pageIndex: Int,
        garden: RoomSeed,
        roomSeeds: List<RoomSeed>,
    ): HouseTest2Page {
        require(roomSeeds.size <= HOUSE_TEST2_ROOMS_PER_PAGE)
        val slots = buildList { for (z in 0..7) for (x in 0..7) if (x != 3 || z != 3) add(x to z) }
        val copies = mutableListOf<HouseTest2RoomCopy>()
        val gardenPlacement = garden.toPlacement(3, 3, 1, pageIndex)
        copies += garden.toCopy(gardenPlacement)
        roomSeeds.forEachIndexed { index, seed ->
            val (x, z) = slots[index]
            val placement = seed.toPlacement(x, z, naturalLevel(seed.room), pageIndex)
            copies += seed.toCopy(placement)
        }
        val furniture = mutableMapOf<String, String>()
        copies.forEach { copy ->
            copy.furniture.forEach { (hotspot, buildable) ->
                furniture[furnitureKey(copy.placement, hotspot)] = buildable
            }
        }
        val blueprint =
            HouseBlueprint(
                rooms = copies.mapTo(mutableListOf()) { it.placement },
                furniture = furniture,
            )
        val label =
            roomSeeds.groupingBy(RoomSeed::wing).eachCount().maxByOrNull { it.value }?.key
                ?: garden.wing
        return HouseTest2Page(pageIndex, label, blueprint, copies)
    }

    private fun naturalLevel(room: DonorRoom): Int =
        when {
            room.basement -> 0
            room.name.endsWith("_UP") -> 2
            else -> 1
        }

    private fun wingFor(room: DonorRoom): String {
        val name = room.name
        return when {
            name.contains("GARDEN") || name.contains("MENAGERIE") -> "Gardens and menagerie"
            name.contains("KITCHEN") || name.contains("DINING") -> "Kitchen and dining"
            name.contains("WORKSHOP") -> "Workshop"
            name.contains("BEDROOM") -> "Bedroom and servants"
            name.contains("CHAPEL") -> "Chapel"
            name.contains("STUDY") -> "Study"
            name.contains("PORTAL") || name.contains("NEXUS") -> "Teleportation"
            name.contains("COSTUME") -> "Costume storage"
            name.contains("COMBAT") || name.contains("GAME") -> "Combat and games"
            name.contains("SKILL") || name.contains("QUEST") || name.contains("ACHIEVEMENT") ->
                "Halls and achievements"
            name.contains("THRONE") || name.contains("TREASURE") -> "Throne and treasure"
            name.contains("DUNGEON") ||
                name.contains("OUBLIETTE") ||
                name.contains("CORRIDOR") ||
                name.contains("JUNCTION") ||
                name.contains("STAIRS") -> "Dungeon"
            else -> "Entrance and basic rooms"
        }
    }

    private data class RoomSeed(
        val room: DonorRoom,
        val rotation: Int,
        val copy: Int,
        val copiesAtRotation: Int,
        val wing: String,
        val furniture: Map<String, String>,
    ) {
        fun toPlacement(x: Int, z: Int, level: Int, page: Int): HouseRoom =
            HouseRoom(
                room = room.name,
                zoneX = x,
                zoneZ = z,
                level = level,
                rotation = rotation,
                stableId = "housetest2-p${page}-${room.name.lowercase()}-r${rotation}-c${copy}",
            )

        fun toCopy(placement: HouseRoom): HouseTest2RoomCopy =
            HouseTest2RoomCopy(
                room = room,
                placement = placement,
                rotation = rotation,
                copy = copy,
                copiesAtRotation = copiesAtRotation,
                wing = wing,
                furniture = furniture,
            )
    }
}

internal object HouseTest2Report {
    fun write(
        plan: HouseTest2Plan,
        validations: Map<Int, HouseTest2PageValidation>,
        hasHandler: (objectId: Int, option: Int) -> Boolean,
        output: Path = Path.of(HOUSE_TEST2_REPORT_PATH),
    ): HouseTest2ReportSummary {
        val missingObjects = mutableSetOf<Int>()
        val missingHandlers = mutableListOf<String>()
        val placementFailures =
            validations.values.flatMap { validation ->
                validation.placementFailures.flatMap { (name, failures) ->
                    failures.map { "page ${validation.page + 1}: $name - $it" }
                }
            }
        val text = buildString {
            appendLine("HouseTest2 - complete POH buildable interaction audit")
            appendLine(
                "Generated from the live DonorConstructionData registry and revision-240 cache."
            )
            appendLine()
            appendLine("Rooms represented: ${plan.representedRooms}/${plan.totalRooms}")
            appendLine(
                "Room rotations represented: ${plan.representedRotations}/${plan.totalRotations}"
            )
            appendLine(
                "Build definitions represented: ${plan.representedBuildables}/${plan.totalBuildables}"
            )
            appendLine("Registry definitions reported: ${plan.entries.size}")
            appendLine("Constructed object components planned: ${plan.constructedComponents}")
            appendLine("Audit pages: ${plan.pages.size}")
            appendLine("Runtime pages generated: ${validations.size}/${plan.pages.size}")
            appendLine()
            plan.entries.forEach { entry ->
                val buildable = entry.buildable
                val canonical = entry.placements.firstOrNull()
                val optionLines = mutableListOf<String>()
                val routes = mutableListOf<String>()
                buildable.builtLocIds
                    .filter { it > 0 }
                    .distinct()
                    .forEach { objectId ->
                        val type = ServerCacheManager.getObject(objectId)
                        if (type == null) {
                            missingObjects += objectId
                            optionLines += "$objectId=<missing>"
                        } else {
                            val options =
                                (0 until 5).mapNotNull { op ->
                                    type.actions.getOpOrNull(op)?.let { op to it }
                                }
                            optionLines +=
                                "$objectId ${type.name}: " +
                                    options.joinToString { (op, action) -> "${op + 1}:$action" }
                            options
                                .filterNot { (_, action) ->
                                    action.equals("Build", true) ||
                                        action.equals("Upgrade", true) ||
                                        action.equals("Remove", true)
                                }
                                .forEach { (op, action) ->
                                    if (hasHandler(objectId, op)) {
                                        routes += "$objectId/$action -> registered Loc Op${op + 1}"
                                    } else {
                                        val warning = "${buildable.name}: $objectId/$action"
                                        missingHandlers += warning
                                        routes += "$objectId/$action -> MISSING"
                                    }
                                }
                        }
                    }
                val generatedPlacements = entry.placements.count { it.page in validations.keys }
                val failed = validations.values.any { buildable.name in it.placementFailures }
                appendLine(
                    "[${entry.index + 1}] ${ConstructionFurnitureMenuCatalog.canonicalName(buildable)}"
                )
                appendLine("key=${buildable.name}")
                appendLine("level=${buildable.level} xp=${buildable.experience}")
                appendLine(
                    "room=${canonical?.roomName ?: "UNREACHABLE"} " +
                        "hotspot=${canonical?.hotspotName ?: "UNMAPPED"} " +
                        "wing=${canonical?.wing ?: "UNMAPPED"}"
                )
                appendLine(
                    "primary_page=${canonical?.page?.plus(1) ?: 0} " +
                        "rotation=${canonical?.rotation ?: -1} " +
                        "copy=${canonical?.copy ?: 0}/${canonical?.copiesAtRotation ?: 0}"
                )
                appendLine("built_ids=${buildable.builtLocIds.joinToString()}")
                appendLine(
                    "object_options=${optionLines.joinToString(" | ").ifEmpty { "Examine only" }}"
                )
                appendLine(
                    "handlers=${routes.joinToString(" | ").ifEmpty { "No non-build action expected" }}"
                )
                val placementStatus =
                    when {
                        buildable.name in plan.inactiveBuildables -> "NOT_ENABLED"
                        entry.placements.isEmpty() -> "UNREACHABLE"
                        failed -> "FAILED"
                        else -> "PLANNED"
                    }
                appendLine(
                    "placement=$placementStatus " +
                        "runtime_placements=$generatedPlacements/${entry.placements.size}"
                )
                appendLine(
                    "all_placements=" +
                        entry.placements.joinToString { placement ->
                            "p${placement.page + 1}:${placement.roomName}/r${placement.rotation}/" +
                                "c${placement.copy}:${placement.hotspotName}"
                        }
                )
                appendLine()
            }
            appendLine("=== GROUPED FAILURES ===")
            appendLine(
                "Missing room definitions: ${
                DonorConstructionData.rooms.map { it.name }.filterNot(plan.roomDefinitions::contains).joinToString().ifEmpty { "none" }
            }"
            )
            appendLine(
                "Non-menu/internal definitions: ${plan.inactiveBuildables.joinToString().ifEmpty { "none" }}"
            )
            appendLine(
                "Unreachable build definitions: ${plan.unreachableBuildables.joinToString().ifEmpty { "none" }}"
            )
            appendLine("Missing object IDs: ${missingObjects.joinToString().ifEmpty { "none" }}")
            appendLine("Missing interaction handlers (${missingHandlers.size}):")
            missingHandlers.distinct().sorted().forEach { appendLine("- $it") }
            if (missingHandlers.isEmpty()) appendLine("- none")
            appendLine("Placement failures (${placementFailures.size}):")
            placementFailures.forEach { appendLine("- $it") }
            if (placementFailures.isEmpty()) appendLine("- none")
        }
        output.parent?.let(Files::createDirectories)
        Files.writeString(output, text)
        return HouseTest2ReportSummary(
            path = output,
            missingObjectIds = missingObjects.size,
            missingHandlers = missingHandlers.distinct().size,
            placementFailures = placementFailures.size,
        )
    }
}

internal data class HouseTest2ReportSummary(
    val path: Path,
    val missingObjectIds: Int,
    val missingHandlers: Int,
    val placementFailures: Int,
)

/** Standalone audit entry point backed by the fully booted production event graph. */
public class HouseTest2RegistryAuditWriter @Inject constructor(private val events: EventBus) {
    public fun write(output: Path = Path.of(HOUSE_TEST2_REPORT_PATH)): Path =
        HouseTest2Report.write(
                plan = HouseTest2Planner.build(),
                validations = emptyMap(),
                hasHandler = ::hasLocHandler,
                output = output,
            )
            .path

    private fun hasLocHandler(objectId: Int, option: Int): Boolean =
        when (option) {
            0 -> events.contains(LocEvents.Op1::class.java, objectId)
            1 -> events.contains(LocEvents.Op2::class.java, objectId)
            2 -> events.contains(LocEvents.Op3::class.java, objectId)
            3 -> events.contains(LocEvents.Op4::class.java, objectId)
            4 -> events.contains(LocEvents.Op5::class.java, objectId)
            else -> false
        }
}
