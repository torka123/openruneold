package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.UUID
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.InstanceSettings
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.constructionLvl
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.player.ui.ifCloseSub
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.construction.data.DonorBuildable
import org.rsmod.content.skills.construction.data.DonorConstructionData
import org.rsmod.content.skills.construction.data.DonorHotspot
import org.rsmod.content.skills.construction.data.DonorRequirement
import org.rsmod.content.skills.construction.data.DonorRoom
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.map.util.Translation
import org.rsmod.map.zone.ZoneKey
import org.slf4j.LoggerFactory

internal const val HOUSE_INSTANCE_KEY = "construction_house"
private const val HOUSE_MAX_OCCUPANTS = 100
private const val HOUSE_ZONE_SIZE = 8
private const val HOUSE_ENTER_LOCAL_X = 3
private const val HOUSE_ENTER_LOCAL_Z = 2
private const val FURNITURE_INTERFACE = 458
private const val FURNITURE_LIST_COMPONENT = 2
private const val ROOM_INTERFACE = 212
private const val ROOM_LIST_COMPONENT = 4
private const val HAMMER = 2347
private const val IMCANDO_HAMMER = 22398
private const val SAW = 8794
private const val CRYSTAL_SAW = 21754
private const val HOUSE_WINDOW_TEMPLATE_LOC = 13830
private const val POH_BUILDING_MODE_VARBIT = 2176
private const val POH_TELEPORT_INSIDE_VARBIT = 4744
private const val POH_DOORS_VARBIT = 6269
private const val POH_DEFAULT_BUILD_MODE_VARBIT = 14670
private const val POH_VIEWER_INTERFACE = 422
private const val POH_VIEWER_MAP_COMPONENT = 5
private const val POH_VIEWER_SELECTED_VARBIT = 5329
private const val POH_VIEWER_DESTINATION_VARBIT = 5330
private const val POH_VIEWER_ROTATION_VARBIT = 5331
private const val POH_VIEWER_ALLOW_ROTATION_VARBIT = 5332
private const val POH_VIEWER_ROOM_ID_VARBIT = 5333
private const val POH_VIEWER_SELECTED_DOORS_VARBIT = 5334
private const val POH_VIEWER_ADJACENT_DOORS_VARBIT = 5335
private const val POH_VIEWER_ROOM_SCRIPT = 1376
private const val POH_VIEWER_SETUP_SCRIPT = 1382
private const val POH_VIEWER_GRID_SIZE = 9
private const val POH_VIEWER_FLOOR_CELLS = POH_VIEWER_GRID_SIZE * POH_VIEWER_GRID_SIZE
private const val POH_VIEWER_CELL_COUNT = POH_VIEWER_FLOOR_CELLS * 3
private const val POH_VIEWER_MOVE_SENTINEL = POH_VIEWER_CELL_COUNT + 1
private val furnitureInterfaceInternal =
    RSCM.getReverseMapping(RSCMType.INTERFACE, FURNITURE_INTERFACE)
private val roomInterfaceInternal = RSCM.getReverseMapping(RSCMType.INTERFACE, ROOM_INTERFACE)

internal data class ConstructionDoorPlacement(val coords: CoordGrid, val angle: LocAngle)

/**
 * Returns the closed right-leaf tile for a closed left leaf. This is the same orientation-aware
 * relationship used by the engine's double-door implementation; unlike an object-ID lookup it also
 * works for newer POH templates where both hotspot leaves deliberately share one ID.
 */
internal fun constructionDoorRightLeafCoords(
    leftCoords: CoordGrid,
    closedAngle: LocAngle,
): CoordGrid =
    when (closedAngle.id) {
        0 -> leftCoords.translate(0, 1, 0)
        1 -> leftCoords.translate(1, 0, 0)
        2 -> leftCoords.translate(0, -1, 0)
        else -> leftCoords.translate(-1, 0, 0)
    }

/** Opens one POH double-door leaf around its own hinge, away from the doorway centre. */
internal fun openedConstructionDoorPlacement(
    closedCoords: CoordGrid,
    closedAngle: LocAngle,
    leftLeaf: Boolean,
): ConstructionDoorPlacement {
    val coords =
        when (closedAngle.id) {
            0 -> closedCoords.translate(-1, 0, 0)
            1 -> closedCoords.translate(0, 1, 0)
            2 -> closedCoords.translate(1, 0, 0)
            else -> closedCoords.translate(0, -1, 0)
        }
    val turn = if (leftLeaf) -1 else 1
    val angle = LocAngle.entries[Math.floorMod(closedAngle.id + turn, 4)]
    return ConstructionDoorPlacement(coords, angle)
}

/*
 * Revision 240 moved the player-owned-house template south by 21 map squares.
 * Donor room data remains in its original coordinate system and is translated here.
 */
private const val REVISION_240_POH_ZONE_Z_OFFSET = 21 * 8

/**
 * Translates a donor POH zone into its revision-240 cache location.
 *
 * House themes are stacked on source map levels. The donor's changeZ field names that level; it is
 * not a north/south zone offset. Tropical and fancy themes additionally use the second
 * eight-zone-wide source block.
 */
internal fun revision240HouseZone(donorZoneX: Int, donorZoneZ: Int, style: HouseStyle): ZoneKey =
    ZoneKey(
        donorZoneX + style.zoneXOffset,
        donorZoneZ + REVISION_240_POH_ZONE_Z_OFFSET,
        style.sourceLevel,
    )

@Singleton
class ConstructionHouseService
@Inject
constructor(
    private val instances: InstanceManager,
    private val locs: LocRepository,
    private val xpMods: XpModifiers,
    private val collision: org.rsmod.routefinder.collision.CollisionFlagMap,
    private val clock: org.rsmod.game.MapClock,
    private val events: EventBus,
    private val players: PlayerList,
) {
    private val rooms = DonorConstructionData.rooms.associateBy(DonorRoom::name)
    private val hotspots = DonorConstructionData.hotspots.associateBy(DonorHotspot::name)
    private val buildables = DonorConstructionData.buildables.associateBy(DonorBuildable::name)
    private val hotspotByLocId =
        hotspots.values.flatMap { hotspot -> hotspot.locIds.map { it to hotspot } }.toMap()
    private val roomDoorIds =
        ServerCacheManager.getObjects()
            .values
            .asSequence()
            .filter { it.name.equals("Door hotspot", ignoreCase = true) }
            .map { it.id }
            .toSet()
    private val builtIds =
        buildables.values.flatMap { it.builtLocIds.asIterable() }.filter { it > 0 }.toSet()

    private val exitPortalIds =
        buildables.values
            .filter { it.name.endsWith("EXIT_PORTAL") }
            .flatMap { it.builtLocIds.asIterable() }
            .filter { it > 0 }
            .toSet()
    private val staircaseIds =
        buildables.values
            .filter { it.name.contains("STAIRCASE") }
            .flatMap { it.builtLocIds.asIterable() }
            .filter { it > 0 }
            .toSet()
    private val active = mutableMapOf<Long, HouseRuntime>()
    private val returnCoords = mutableMapOf<Long, CoordGrid>()
    private val pending = mutableMapOf<Long, PendingBuild>()
    private val pendingRooms = mutableMapOf<Long, PendingRoom>()
    private val viewerSessions = mutableMapOf<Long, ViewerEditSession>()
    private val houseTest2Sessions = mutableMapOf<Long, HouseTest2Session>()
    private val houseTest2Transitions = mutableSetOf<InstanceId>()

    fun registeredHotspotIds(): Set<Int> = hotspotByLocId.keys

    fun registeredRoomDoorIds(): Set<Int> = roomDoorIds

    fun registeredBuiltIds(): Set<Int> = builtIds

    fun registeredExitPortalIds(): Set<Int> = exitPortalIds

    fun registeredHouseStyleDoorIds(): Set<Int> =
        HouseStyle.entries.flatMapTo(mutableSetOf()) {
            listOf(it.doorLocId1, it.doorLocId2, it.openedDoorLocId1, it.openedDoorLocId2)
        }

    fun registeredStaircaseIds(): Set<Int> = staircaseIds

    fun registeredThroneLeverIds(): Set<Int> = ThroneRoomTrapLayout.leverLocIds

    fun enter(player: Player, buildMode: Boolean) =
        enter(player, buildMode, player.loadHouseBlueprint(), false, null, null)

    fun enterTest(player: Player) = enter(player, true, allRoomsBlueprint(), true, null, null)

    fun houseTest2(player: Player, args: List<String>) {
        when (args.firstOrNull()?.lowercase()) {
            null,
            "rebuild" -> createHouseTest2(player)
            "next" -> moveHouseTest2(player, 1)
            "previous",
            "prev" -> moveHouseTest2(player, -1)
            "room" -> findHouseTest2Room(player, args.drop(1).joinToString(" "))
            "item" -> findHouseTest2Item(player, args.drop(1).joinToString(" "))
            "report" -> showHouseTest2Report(player)
            "resume" -> resumeHouseTest2(player)
            "exit" -> exitHouseTest2(player)
            else ->
                player.mes(
                    "Use ::housetest2 [rebuild|next|previous|room <name>|item <name/id>|report|resume|exit]."
                )
        }
    }

    private fun createHouseTest2(player: Player) {
        val key = player.houseKey()
        val current = instances.sessionForPlayer(player)
        val currentRuntime = runtimeFor(player)
        if (
            current != null &&
                (current.key != HOUSE_INSTANCE_KEY || currentRuntime?.auditMode != true)
        ) {
            player.mes("Leave your current instance before creating the Construction audit house.")
            return
        }
        val previous = houseTest2Sessions[key]
        val returnCoord = previous?.returnCoord ?: returnCoords[key] ?: player.coords
        if (current != null) releaseHouseTest2Page(player, current)
        val session = HouseTest2Session(HouseTest2Planner.build(), returnCoord)
        houseTest2Sessions[key] = session
        if (!enterHouseTest2Page(player, session, 0)) {
            houseTest2Sessions.remove(key)
            PathingEntityCommon.telejump(player, collision, returnCoord)
            return
        }
        val summary = writeHouseTest2Report(session)
        player.mes("HouseTest2 generated from the production Construction registry.")
        player.mes(
            "Rooms ${session.plan.representedRooms}/${session.plan.totalRooms}; rotations " +
                "${session.plan.representedRotations}/${session.plan.totalRotations}; buildables " +
                "${session.plan.representedBuildables}/${session.plan.totalBuildables}."
        )
        player.mes(
            "Pages: ${session.plan.pages.size}. Report: ${summary.path} (missing handlers: " +
                "${summary.missingHandlers})."
        )
        showHouseTest2Page(player, session)
    }

    private fun releaseHouseTest2Page(player: Player, current: InstanceSession) {
        houseTest2Transitions += current.id
        pending.remove(player.houseKey())
        pendingRooms.remove(player.houseKey())
        viewerSessions.remove(player.houseKey())
        player.ifClose(events)
        instances.leave(player, current, clock.cycle)
    }

    private fun enterHouseTest2Page(
        player: Player,
        audit: HouseTest2Session,
        pageIndex: Int,
    ): Boolean {
        val page = audit.plan.pages.getOrNull(pageIndex) ?: return false
        val current = instances.sessionForPlayer(player)
        if (current != null) {
            val runtime = runtimeFor(player)
            if (current.key != HOUSE_INSTANCE_KEY || runtime?.auditMode != true) {
                player.mes("You are not inside this HouseTest2 session.")
                return false
            }
            releaseHouseTest2Page(player, current)
        }
        val entered =
            enter(
                player = player,
                buildMode = false,
                blueprint = page.blueprint,
                testMode = true,
                returnCoordOverride = audit.returnCoord,
                preferredHouseOffset = null,
                auditMode = true,
            )
        if (!entered) return false
        audit.page = pageIndex
        val runtime = ownedRuntime(player) ?: return false
        audit.pageValidation[pageIndex] = validateHouseTest2Page(page, runtime)
        writeHouseTest2Report(audit)
        return true
    }

    private fun validateHouseTest2Page(
        page: HouseTest2Page,
        runtime: HouseRuntime,
    ): HouseTest2PageValidation {
        val placed = mutableSetOf<String>()
        val failures = mutableMapOf<String, MutableList<String>>()
        page.blueprint.furniture.forEach { (key, buildableName) ->
            val target = runtime.byKey[key]
            val buildable = buildables[buildableName]
            val errors = mutableListOf<String>()
            if (target == null) {
                errors += "hotspot metadata was not indexed ($key)"
            } else if (buildable == null) {
                errors += "build definition is missing"
            } else {
                target.locs.forEach { hotspotLoc ->
                    val expected = builtLocId(target, buildable, hotspotLoc)
                    if (expected < 0) return@forEach
                    val actual =
                        locs.findAll(hotspotLoc.coords).firstOrNull {
                            it.layer == hotspotLoc.layer && it.id == expected
                        }
                    if (actual == null) {
                        errors += "object $expected missing at ${hotspotLoc.coords}"
                    }
                }
            }
            if (errors.isEmpty()) placed += buildableName
            else failures.getOrPut(buildableName, ::mutableListOf).addAll(errors)
        }
        return HouseTest2PageValidation(page.index, placed, failures)
    }

    private fun moveHouseTest2(player: Player, amount: Int) {
        val audit = houseTest2Sessions[player.houseKey()]
        if (audit == null) {
            player.mes("Use ::housetest2 first.")
            return
        }
        val next = Math.floorMod(audit.currentEntry + amount, audit.plan.entries.size)
        focusHouseTest2Entry(player, audit, next)
    }

    private fun resumeHouseTest2(player: Player) {
        val audit = houseTest2Sessions[player.houseKey()]
        if (audit == null) {
            player.mes("Use ::housetest2 first.")
            return
        }
        focusHouseTest2Entry(player, audit, audit.lastEntry)
    }

    private fun findHouseTest2Room(player: Player, query: String) {
        val audit = houseTest2Sessions[player.houseKey()]
        if (audit == null) {
            player.mes("Use ::housetest2 first.")
            return
        }
        val clean = query.trim().replace(' ', '_')
        if (clean.isEmpty()) {
            player.mes("Use ::housetest2 room <room name>.")
            return
        }
        val names =
            audit.plan.pages
                .flatMap { it.rooms }
                .map { it.room.name }
                .distinct()
                .filter { it.contains(clean, ignoreCase = true) }
        if (names.isEmpty()) {
            player.mes("No audit room matches '$query'.")
            return
        }
        if (names.size > 1) {
            player.mes(
                "Matches: ${names.take(12).joinToString { it.lowercase().replace('_', ' ') }}"
            )
            return
        }
        val room =
            audit.plan.pages
                .asSequence()
                .flatMap { it.rooms.asSequence() }
                .first { it.room.name == names.single() }
        val page = audit.plan.pages.first { room in it.rooms }
        if (page.index != audit.page && !enterHouseTest2Page(player, audit, page.index)) return
        val runtime = ownedRuntime(player) ?: return
        val destination =
            CoordGrid(
                runtime.base.x + room.placement.zoneX * HOUSE_ZONE_SIZE + 3,
                runtime.base.z + room.placement.zoneZ * HOUSE_ZONE_SIZE + 3,
                runtime.base.level + room.placement.level,
            )
        PathingEntityCommon.telejump(player, collision, destination)
        player.mes(
            "HouseTest2 | Room: ${room.room.name.lowercase().replace('_', ' ')} | " +
                "Rotation: ${room.rotation} | Copy: ${room.copy}/${room.copiesAtRotation} | " +
                "Objects: ${room.furniture.size} | Page: ${page.index + 1}/${audit.plan.pages.size}"
        )
    }

    private fun findHouseTest2Item(player: Player, query: String) {
        val audit = houseTest2Sessions[player.houseKey()]
        if (audit == null) {
            player.mes("Use ::housetest2 first.")
            return
        }
        val clean = query.trim()
        if (clean.isEmpty()) {
            player.mes("Use ::housetest2 item <object name or object id>.")
            return
        }
        val objectId = clean.toIntOrNull()
        val matches =
            audit.plan.entries.filter { entry ->
                (objectId != null && objectId in entry.buildable.builtLocIds) ||
                    entry.buildable.name.contains(clean.replace(' ', '_'), ignoreCase = true) ||
                    ConstructionFurnitureMenuCatalog.canonicalName(entry.buildable)
                        .contains(clean, ignoreCase = true)
            }
        if (matches.isEmpty()) {
            player.mes("No Construction buildable matches '$query'.")
            return
        }
        val exact =
            matches.singleOrNull {
                it.buildable.name.equals(clean.replace(' ', '_'), true) ||
                    (objectId != null && objectId in it.buildable.builtLocIds)
            }
        if (matches.size > 1 && exact == null) {
            player.mes(
                "Matches: ${matches.take(12).joinToString { ConstructionFurnitureMenuCatalog.canonicalName(it.buildable) }}"
            )
            return
        }
        focusHouseTest2Entry(player, audit, (exact ?: matches.single()).index)
    }

    private fun focusHouseTest2Entry(player: Player, audit: HouseTest2Session, index: Int) {
        val entry = audit.plan.entries.getOrNull(index) ?: return
        val placement =
            entry.placements.firstOrNull { it.page == audit.page } ?: entry.placements.firstOrNull()
        if (placement == null) {
            player.mes("${entry.buildable.name} is not reachable from any registered room hotspot.")
            return
        }
        if (placement.page != audit.page && !enterHouseTest2Page(player, audit, placement.page))
            return
        val runtime = ownedRuntime(player) ?: return
        val key = "${placement.roomStableId}:${placement.hotspotName}"
        val target = runtime.byKey[key]
        if (target == null) {
            player.mes("The audit hotspot for ${entry.buildable.name} was not indexed.")
            return
        }
        val objectLoc = target.locs.first()
        val room = runtime.blueprint.rooms.first { it.stableId == placement.roomStableId }
        val roomX = runtime.base.x + room.zoneX * HOUSE_ZONE_SIZE
        val roomZ = runtime.base.z + room.zoneZ * HOUSE_ZONE_SIZE
        val destination =
            listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
                .map { (x, z) -> objectLoc.coords.translate(x, z) }
                .firstOrNull { it.x in roomX..roomX + 7 && it.z in roomZ..roomZ + 7 }
                ?: CoordGrid(roomX + 3, roomZ + 3, objectLoc.coords.level)
        PathingEntityCommon.telejump(player, collision, destination)
        audit.currentEntry = index
        audit.lastEntry = index
        val current =
            locs.findAll(objectLoc.coords).firstOrNull {
                it.layer == objectLoc.layer && it.id in entry.buildable.builtLocIds
            }
        val options =
            current
                ?.id
                ?.let(ServerCacheManager::getObject)
                ?.let { type ->
                    (0 until 5)
                        .mapNotNull { op -> type.actions.getOpOrNull(op)?.let { "${op + 1}:$it" } }
                        .joinToString()
                }
                .orEmpty()
                .ifEmpty { "Examine only" }
        val routes =
            current
                ?.id
                ?.let(ServerCacheManager::getObject)
                ?.let { type ->
                    (0 until 5)
                        .mapNotNull { op ->
                            type.actions.getOpOrNull(op)?.let { action ->
                                "$action=${if (hasLocHandler(type.id, op)) "registered" else "missing"}"
                            }
                        }
                        .joinToString()
                }
                .orEmpty()
                .ifEmpty { "none expected" }
        player.mes(
            "HouseTest2 [${index + 1}/${audit.plan.entries.size}] " +
                ConstructionFurnitureMenuCatalog.canonicalName(entry.buildable)
        )
        player.mes(
            "Key ${entry.buildable.name}; room ${placement.roomName}; hotspot " +
                "${placement.hotspotName}; rotation ${placement.rotation}; copy " +
                "${placement.copy}/${placement.copiesAtRotation}."
        )
        player.mes(
            "Built IDs ${entry.buildable.builtLocIds.joinToString()}; active ${current?.id ?: "missing"}; " +
                "world ${objectLoc.coords}; options $options."
        )
        player.mes("Interaction routes: $routes")
    }

    private fun showHouseTest2Page(player: Player, audit: HouseTest2Session) {
        val page = audit.plan.pages[audit.page]
        player.mes(
            "HouseTest2 | Page ${page.index + 1}/${audit.plan.pages.size}: ${page.label} | " +
                "Rooms: ${page.rooms.size} | visit mode | hotspots hidden"
        )
    }

    private fun showHouseTest2Report(player: Player) {
        val audit = houseTest2Sessions[player.houseKey()]
        if (audit == null) {
            player.mes("Use ::housetest2 first.")
            return
        }
        val summary = writeHouseTest2Report(audit)
        player.mes(
            "HouseTest2 report: ${summary.path}; pages generated ${audit.pageValidation.size}/" +
                "${audit.plan.pages.size}; missing objects ${summary.missingObjectIds}; missing " +
                "handlers ${summary.missingHandlers}; placement failures ${summary.placementFailures}."
        )
    }

    private fun writeHouseTest2Report(audit: HouseTest2Session): HouseTest2ReportSummary =
        HouseTest2Report.write(audit.plan, audit.pageValidation, ::hasLocHandler)

    private fun hasLocHandler(objectId: Int, option: Int): Boolean {
        val key = objectId
        return when (option) {
            0 -> events.contains(LocEvents.Op1::class.java, key)
            1 -> events.contains(LocEvents.Op2::class.java, key)
            2 -> events.contains(LocEvents.Op3::class.java, key)
            3 -> events.contains(LocEvents.Op4::class.java, key)
            4 -> events.contains(LocEvents.Op5::class.java, key)
            else -> false
        }
    }

    private fun exitHouseTest2(player: Player) {
        if (houseTest2Sessions.remove(player.houseKey()) == null) {
            player.mes("You do not have an active HouseTest2 session.")
            return
        }
        emergencyExit(player)
    }

    private fun enter(
        player: Player,
        buildMode: Boolean,
        blueprint: HouseBlueprint,
        testMode: Boolean,
        returnCoordOverride: CoordGrid?,
        preferredHouseOffset: Translation?,
        auditMode: Boolean = false,
    ): Boolean {
        if (!testMode && !player.hasConstructionHouse()) {
            player.mes("You need to purchase a house from an Estate agent first.")
            return false
        }
        if (instances.sessionForPlayer(player) != null) {
            player.mes("You are already inside an instance.")
            return false
        }
        val area = houseArea(blueprint)
        val spec =
            InstanceSettings(
                    maxPlayers = if (buildMode || testMode) 1 else HOUSE_MAX_OCCUPANTS,
                    destroyWhenEmpty = true,
                    description =
                        if (auditMode) "HouseTest2 audit house"
                        else if (testMode) "Construction test house" else "Player-owned house",
                )
                .withArea(area, settingsRowId = 0)
        val created =
            instances.create(
                owner = player,
                key = HOUSE_INSTANCE_KEY,
                spec = spec,
                access =
                    if (buildMode || testMode) InstanceAccess.Private else InstanceAccess.Friends,
                currentTick = clock.cycle,
            )
        if (created !is InstanceManager.Result.Created) {
            player.mes(
                (created as? InstanceManager.Result.Failed)?.reason ?: "Your house is unavailable."
            )
            return false
        }
        player.ifClose(events)
        returnCoords[player.houseKey()] = returnCoordOverride ?: player.coords
        PathingEntityCommon.telejump(player, collision, created.enter)
        instances.finalizeEntry(player, created.session, clock.cycle)
        val garden = blueprint.rooms.single { it.room == "GARDEN" && it.level == 1 }
        val entranceLocal =
            HouseViewerLayout.rotateLocal(HOUSE_ENTER_LOCAL_X, HOUSE_ENTER_LOCAL_Z, garden.rotation)
        val base =
            CoordGrid(
                created.enter.x - garden.zoneX * HOUSE_ZONE_SIZE - entranceLocal.first,
                created.enter.z - garden.zoneZ * HOUSE_ZONE_SIZE - entranceLocal.second,
                created.enter.level - garden.level,
            )
        val runtime = HouseRuntime(created.session, blueprint, buildMode, testMode, auditMode, base)
        active[player.houseKey()] = runtime
        indexHotspots(runtime)
        val restoredFurniture = applySavedFurniture(runtime)
        renderArchitecturalWindows(runtime, restoredFurniture)
        if (!buildMode) renderArchitecturalDoors(runtime)
        if (!buildMode) hideUnbuiltHotspots(runtime, restoredFurniture)
        preferredHouseOffset?.let { offset ->
            val preferred = runtime.base.translate(offset)
            val roomX = Math.floorDiv(preferred.x - runtime.base.x, HOUSE_ZONE_SIZE)
            val roomZ = Math.floorDiv(preferred.z - runtime.base.z, HOUSE_ZONE_SIZE)
            val roomLevel = preferred.level - runtime.base.level
            val valid =
                blueprint.rooms.any {
                    it.zoneX == roomX && it.zoneZ == roomZ && it.level == roomLevel
                }
            if (valid) PathingEntityCommon.telejump(player, collision, preferred)
        }
        syncHouseOptions(player)
        player.mes(
            when {
                auditMode ->
                    "You enter HouseTest2 in visit mode. Use ::housetest2 next to inspect the next wing."
                testMode ->
                    "You enter a disposable Construction test house. All furniture is free and nothing here is saved."
                buildMode -> "You enter your house in building mode."
                else -> "You enter your house."
            }
        )
        return true
    }

    fun visit(player: Player, ownerName: String) {
        if (instances.sessionForPlayer(player) != null) {
            player.mes("You are already inside an instance.")
            return
        }
        val trimmed = ownerName.trim()
        if (trimmed.isEmpty()) {
            player.mes("You must enter a player's name.")
            return
        }
        val session =
            instances.sessionsForKey(HOUSE_INSTANCE_KEY).firstOrNull { candidate ->
                instances.memberLabel(candidate.owner)?.equals(trimmed, ignoreCase = true) == true
            }
        val runtime = session?.let { active[it.owner] }
        if (
            session == null ||
                runtime == null ||
                runtime.testMode ||
                runtime.buildMode ||
                session.owner !in session.occupants
        ) {
            player.mes("That player is not currently in a house you can visit.")
            return
        }
        when (val joined = instances.join(player, session, clock.cycle)) {
            is InstanceManager.Result.Joined -> {
                player.ifClose(events)
                returnCoords[player.houseKey()] = player.coords
                PathingEntityCommon.telejump(player, collision, joined.enter)
                instances.finalizeEntry(player, joined.session, clock.cycle)
                val label = instances.memberLabel(session.owner) ?: trimmed
                player.mes("You enter $label's house.")
            }
            is InstanceManager.Result.Failed -> player.mes(joined.reason)
            is InstanceManager.Result.Created -> error("Joining a house unexpectedly created one.")
        }
    }

    /** Admin recovery route for a POH whose exit portal cannot currently be reached. */
    fun emergencyExit(player: Player) {
        val key = player.houseKey()
        houseTest2Sessions.remove(key)
        val session = instances.sessionForPlayer(player)?.takeIf { it.key == HOUSE_INSTANCE_KEY }
        val exit =
            returnCoords.remove(key) ?: session?.placement?.exitCoord ?: CoordGrid(3079, 3496, 0)
        pending.remove(key)
        pendingRooms.remove(key)
        viewerSessions.remove(key)
        if (session != null) instances.leave(player, session, clock.cycle)
        player.ifClose(events)
        PathingEntityCommon.telejump(player, collision, exit)
        player.mes("You leave your house.")
    }

    fun leave(access: ProtectedAccess): Boolean {
        val session = instances.sessionForPlayer(access.player) ?: return false
        if (session.key != HOUSE_INSTANCE_KEY) return false
        val key = access.player.houseKey()
        houseTest2Sessions.remove(key)
        val exit = returnCoords.remove(key) ?: session.placement.exitCoord
        pending.remove(key)
        pendingRooms.remove(key)
        viewerSessions.remove(key)
        instances.leave(access.player, session, clock.cycle)
        access.player.ifClose(events)
        access.teleport(exit)
        access.mes("You leave the house.")
        return true
    }

    fun onHousePlayerLeave(
        player: Player,
        instanceId: InstanceId,
        ownerId: Long,
        isOwner: Boolean,
    ) {
        val playerId = player.houseKey()
        val transitioning = instanceId in houseTest2Transitions
        if (!transitioning) returnCoords.remove(playerId)
        if (!transitioning && isOwner) houseTest2Sessions.remove(ownerId)
        pending.remove(playerId)
        pendingRooms.remove(playerId)
        viewerSessions.remove(playerId)
        if (transitioning) return
        if (!isOwner) return
        val session = instances.sessionForId(instanceId) ?: return
        if (session.key != HOUSE_INSTANCE_KEY || session.owner != ownerId) return
        val guests = session.occupants.toList()
        for (guestId in guests) {
            val guest = players.firstOrNull { it.uuid == guestId } ?: continue
            val exit = returnCoords.remove(guestId) ?: session.placement.exitCoord
            pending.remove(guestId)
            pendingRooms.remove(guestId)
            instances.leave(guest, session, clock.cycle)
            PathingEntityCommon.telejump(guest, collision, exit)
            guest.mes("The house owner has left, so you return to the portal.")
        }
    }

    fun onHouseEnded(instanceId: InstanceId, ownerId: Long) {
        houseTest2Transitions.remove(instanceId)
        val runtime = active[ownerId] ?: return
        if (runtime.session.id != instanceId) return
        clearAllThroneTraps(runtime)
        active.remove(ownerId)
        viewerSessions.remove(ownerId)
    }

    fun syncHouseOptions(player: Player) {
        val blueprint = player.loadHouseBlueprint()
        val runtime = runtimeFor(player)
        val liveBuildMode =
            runtime != null && runtime.session.owner == player.uuid && runtime.buildMode
        setVarbit(player, POH_BUILDING_MODE_VARBIT, if (liveBuildMode) 1 else 0)
        setVarbit(
            player,
            POH_TELEPORT_INSIDE_VARBIT,
            if (blueprint.options.teleportInside) 1 else 0,
        )
        setVarbit(
            player,
            POH_DEFAULT_BUILD_MODE_VARBIT,
            if (blueprint.options.defaultBuildMode) 1 else 0,
        )
        setVarbit(player, POH_DOORS_VARBIT, blueprint.options.doors.cacheValue)
        player.ifSetText("component.poh_options:roomcount", "Rooms: ${blueprint.rooms.size}")
    }

    fun setTeleportInside(player: Player, enabled: Boolean) {
        if (!player.hasConstructionHouse()) {
            player.mes("You need to purchase a house from an Estate agent first.")
            syncHouseOptions(player)
            return
        }
        updateOptions(player) { copy(teleportInside = enabled) }
        syncHouseOptions(player)
    }

    fun setDefaultBuildMode(player: Player, enabled: Boolean) {
        if (!player.hasConstructionHouse()) {
            player.mes("You need to purchase a house from an Estate agent first.")
            syncHouseOptions(player)
            return
        }
        updateOptions(player) { copy(defaultBuildMode = enabled) }
        syncHouseOptions(player)
    }

    fun defaultBuildMode(player: Player): Boolean =
        player.loadHouseBlueprint().options.defaultBuildMode

    fun ownsHouse(player: Player): Boolean = player.hasConstructionHouse()

    fun selectedHouseOutside(player: Player): CoordGrid =
        player.loadHouseBlueprint().location.outside

    fun enterFromTeleport(player: Player) = enter(player, buildMode = false)

    fun teleportInside(player: Player): Boolean = player.loadHouseBlueprint().options.teleportInside

    fun setDoors(access: ProtectedAccess, doors: HouseDoorsMode) {
        if (!access.player.hasConstructionHouse()) {
            access.mes("You need to purchase a house from an Estate agent first.")
            syncHouseOptions(access.player)
            return
        }
        val blueprint = access.player.loadHouseBlueprint()
        if (blueprint.options.doors == doors) {
            syncHouseOptions(access.player)
            return
        }
        val updated = blueprint.copy(options = blueprint.options.copy(doors = doors))
        val runtime = ownedRuntime(access.player)
        if (runtime != null && !runtime.buildMode) {
            if (rebuildOwnerHouse(access.player, runtime, updated, runtime.buildMode)) {
                access.player.saveHouseBlueprint(updated)
                syncHouseOptions(access.player)
            }
        } else {
            access.player.saveHouseBlueprint(updated)
            syncHouseOptions(access.player)
        }
    }

    fun setBuildMode(access: ProtectedAccess, enabled: Boolean) {
        val runtime = ownedRuntime(access.player)
        if (runtime == null) {
            access.mes("You can only change building mode inside your own house.")
            syncHouseOptions(access.player)
            return
        }
        if (runtime.buildMode == enabled) {
            syncHouseOptions(access.player)
            return
        }
        if (enabled && runtime.session.occupants.any { it != runtime.session.owner }) {
            access.mes("You cannot enter building mode while guests are in your house.")
            syncHouseOptions(access.player)
            return
        }
        if (viewerSession(access.player) != null) {
            access.mes("Finish or cancel the House Viewer before changing building mode.")
            syncHouseOptions(access.player)
            return
        }
        rebuildOwnerHouse(access.player, runtime, runtime.blueprint, enabled)
    }

    fun openViewer(access: ProtectedAccess) {
        val runtime = ownedRuntime(access.player)
        if (runtime == null) {
            access.mes("The House Viewer is only available inside your own house.")
            return
        }
        if (!runtime.buildMode) {
            access.mes("You must enable building mode before using the House Viewer.")
            return
        }
        val key = access.player.houseKey()
        if (pending.containsKey(key) || pendingRooms.containsKey(key)) {
            access.mes("Finish your current Construction action before opening the House Viewer.")
            return
        }
        val working =
            runtime.blueprint.copy(
                rooms = runtime.blueprint.rooms.map { it.copy() }.toMutableList(),
                furniture = runtime.blueprint.furniture.toMutableMap(),
            )
        val session =
            ViewerEditSession(
                instanceId = runtime.session.id,
                originalRevision = runtime.blueprint.layoutRevision,
                working = working,
            )
        viewerSessions[key] = session
        openViewerInterface(access, session)
    }

    fun selectViewerRoom(access: ProtectedAccess, roomIndex: Int) {
        val session = viewerSession(access.player) ?: return
        val roomId = session.roomSlots.getOrNull(roomIndex) ?: return
        val room = session.working.rooms.firstOrNull { it.stableId == roomId } ?: return
        session.selectedRoomId = room.stableId
        session.mode = ViewerMode.IDLE
        setVarbit(access.player, POH_VIEWER_SELECTED_VARBIT, roomIndex + 1)
        setVarbit(access.player, POH_VIEWER_DESTINATION_VARBIT, 0)
        setVarbit(access.player, POH_VIEWER_ROTATION_VARBIT, room.rotation)
        setVarbit(access.player, POH_VIEWER_ALLOW_ROTATION_VARBIT, 0)
        setVarbit(access.player, POH_VIEWER_ROOM_ID_VARBIT, rooms.getValue(room.room).roomId)
        setVarbit(access.player, POH_VIEWER_SELECTED_DOORS_VARBIT, viewerDoorMask(room))
        setVarbit(
            access.player,
            POH_VIEWER_ADJACENT_DOORS_VARBIT,
            viewerAdjacentDoorMask(session.working.rooms, room),
        )
        access.ifSetText(
            "component.poh_viewer:name",
            rooms
                .getValue(room.room)
                .name
                .lowercase()
                .replace('_', ' ')
                .replaceFirstChar(Char::uppercase),
        )
    }

    fun goToViewerPortal(access: ProtectedAccess) {
        viewerSession(access.player) ?: return
        val runtime = ownedRuntime(access.player) ?: return
        val garden =
            runtime.blueprint.rooms.singleOrNull { it.room == "GARDEN" && it.level == 1 } ?: return
        val local =
            HouseViewerLayout.rotateLocal(HOUSE_ENTER_LOCAL_X, HOUSE_ENTER_LOCAL_Z, garden.rotation)
        access.teleport(
            CoordGrid(
                runtime.base.x + garden.zoneX * HOUSE_ZONE_SIZE + local.first,
                runtime.base.z + garden.zoneZ * HOUSE_ZONE_SIZE + local.second,
                runtime.base.level + garden.level,
            )
        )
    }

    fun rotateViewerRoom(access: ProtectedAccess, clockwise: Boolean) {
        val session = viewerSession(access.player) ?: return
        val selected =
            session.selectedRoomId?.let { id ->
                session.working.rooms.firstOrNull { it.stableId == id }
            }
        if (selected == null) {
            access.mes("Select a room before rotating it.")
            return
        }
        val linked = viewerLinkedStack(session.working, selected)
        val delta = if (clockwise) 1 else -1
        val updatedRooms = HouseViewerLayout.rotateRooms(session.working.rooms, linked, delta)
        session.working.rooms.clear()
        session.working.rooms += updatedRooms
        session.mode = ViewerMode.IDLE
        val rotated = session.working.rooms.first { it.stableId == selected.stableId }
        linked.forEach { roomId ->
            val room = session.working.rooms.firstOrNull { it.stableId == roomId } ?: return@forEach
            val slot = session.roomSlots.indexOf(roomId)
            if (slot >= 0) {
                sendViewerRoom(access.player, slot, room, session.working)
            }
        }
        setVarbit(access.player, POH_VIEWER_ROTATION_VARBIT, rotated.rotation)
        setVarbit(
            access.player,
            POH_VIEWER_DESTINATION_VARBIT,
            HouseViewerLayout.gridPos(rotated.zoneX, rotated.zoneZ, rotated.level),
        )
        setVarbit(access.player, POH_VIEWER_ALLOW_ROTATION_VARBIT, 1)
    }

    /** Enters the client's rotate-confirm state without rebuilding and resetting the Viewer. */
    fun beginViewerRotation(access: ProtectedAccess) {
        val session = viewerSession(access.player) ?: return
        val selected =
            session.selectedRoomId?.let { id ->
                session.working.rooms.firstOrNull { it.stableId == id }
            }
        if (selected == null) {
            access.mes("Select a room before rotating it.")
            return
        }
        session.mode = ViewerMode.IDLE
        setVarbit(
            access.player,
            POH_VIEWER_DESTINATION_VARBIT,
            HouseViewerLayout.gridPos(selected.zoneX, selected.zoneZ, selected.level),
        )
        setVarbit(access.player, POH_VIEWER_ROTATION_VARBIT, selected.rotation)
        setVarbit(access.player, POH_VIEWER_ALLOW_ROTATION_VARBIT, 1)
    }

    fun beginViewerMove(access: ProtectedAccess) {
        val session = viewerSession(access.player) ?: return
        if (session.selectedRoomId == null) {
            access.mes("Select a room before moving it.")
            return
        }
        session.mode = ViewerMode.MOVING
        setVarbit(access.player, POH_VIEWER_DESTINATION_VARBIT, POH_VIEWER_MOVE_SENTINEL)
    }

    fun handleViewerMap(access: ProtectedAccess, comsub: Int, op: IfButtonOp) {
        val session = viewerSession(access.player) ?: return
        val cell = HouseViewerLayout.decodeCell(comsub)
        if (cell == null) {
            access.mes("That space is outside the supported house grid.")
            return
        }
        when (op) {
            IfButtonOp.Op1 -> moveViewerRoom(access, session, cell)
            IfButtonOp.Op6 -> openViewerRoomSelection(access, session, cell)
            else -> access.mes("That House Viewer action is unavailable.")
        }
    }

    private fun moveViewerRoom(
        access: ProtectedAccess,
        session: ViewerEditSession,
        destination: HouseViewerCell,
    ) {
        if (session.mode != ViewerMode.MOVING) {
            access.mes("Select Move before choosing a destination.")
            return
        }
        val selected =
            session.selectedRoomId?.let { id ->
                session.working.rooms.firstOrNull { it.stableId == id }
            } ?: return
        val linked = viewerLinkedStack(session.working, selected)
        val dx = destination.x - selected.zoneX
        val dz = destination.z - selected.zoneZ
        val dl = destination.level - selected.level
        val proposed = HouseViewerLayout.moveRooms(session.working.rooms, linked, dx, dz, dl)
        val problem = validateViewerRooms(proposed, session.working.furniture)
        if (problem != null) {
            access.mes(problem)
            return
        }
        session.working.rooms.clear()
        session.working.rooms += proposed
        session.mode = ViewerMode.IDLE
        val moved = session.working.rooms.first { it.stableId == selected.stableId }
        /*
         * The stock client already moves the selected room in its map preview. Reopening the
         * Viewer here clears POH_VIEWER_DESTINATION/ALLOW_ROTATION, disables Done, and leaves the
         * saved instance unchanged. Keep the edit session staged and enter the normal confirm
         * state so Done can atomically save and rebuild the physical house.
         */
        setVarbit(
            access.player,
            POH_VIEWER_DESTINATION_VARBIT,
            HouseViewerLayout.gridPos(moved.zoneX, moved.zoneZ, moved.level),
        )
        setVarbit(access.player, POH_VIEWER_ROTATION_VARBIT, moved.rotation)
        setVarbit(access.player, POH_VIEWER_ALLOW_ROTATION_VARBIT, 1)
        if (linked.size > 1) {
            access.mes("The linked staircase rooms move together.")
        }
    }

    private fun openViewerRoomSelection(
        access: ProtectedAccess,
        session: ViewerEditSession,
        destination: HouseViewerCell,
    ) {
        if (session.mode != ViewerMode.IDLE) {
            access.mes("Finish or cancel the current room action first.")
            return
        }
        if (
            session.working.rooms.any {
                it.zoneX == destination.x &&
                    it.zoneZ == destination.z &&
                    it.level == destination.level
            }
        ) {
            access.mes("That room space is already occupied.")
            return
        }
        val key = access.player.houseKey()
        pendingRooms[key] =
            PendingRoom(destination.x, destination.z, destination.level, fromViewer = true)
        session.interfaceTransition = true
        access.player.ifOpenMainModal(ROOM_INTERFACE, events)
        access.player.ifSetEvents(
            ServerCacheManager.fromComponent((ROOM_INTERFACE shl 16) or ROOM_LIST_COMPONENT),
            1..rooms.values.maxOf { it.roomId },
            IfEvent.Op1,
            IfEvent.PauseButton,
        )
    }

    suspend fun deleteViewerRoom(access: ProtectedAccess) {
        val session = viewerSession(access.player) ?: return
        val selected =
            session.selectedRoomId?.let { id ->
                session.working.rooms.firstOrNull { it.stableId == id }
            }
        if (selected == null) {
            access.mes("Select a room before deleting it.")
            return
        }
        if (selected.room == "GARDEN") {
            access.mes("The entrance garden and its portal cannot be deleted.")
            return
        }
        val linked = viewerLinkedStack(session.working, selected)
        val protected =
            linked
                .mapNotNull { id -> session.working.rooms.firstOrNull { it.stableId == id } }
                .firstOrNull { hasUnsafeStoredContents(session.working, it) }
        if (protected != null) {
            access.mes("Empty that room's storage before deleting it.")
            return
        }
        val label =
            if (linked.size == 1) {
                rooms.getValue(selected.room).name.lowercase().replace('_', ' ')
            } else {
                "${linked.size} linked staircase rooms"
            }
        if (
            access.choice2("Yes, delete $label.", 1, "No, keep it.", 2, title = "Delete room?") != 1
        ) {
            return
        }
        val deletion =
            HouseViewerLayout.deleteRooms(session.working.rooms, session.working.furniture, linked)
        val problem = validateViewerRooms(deletion.rooms, deletion.furniture)
        if (problem != null) {
            access.mes(problem)
            return
        }
        session.working.rooms.clear()
        session.working.rooms += deletion.rooms
        session.working.furniture.clear()
        session.working.furniture.putAll(deletion.furniture)
        linked.forEach(session.addedRoomCosts::remove)
        session.selectedRoomId = null
        session.mode = ViewerMode.IDLE
        /* OSRS confirms a room deletion immediately, rebuilds the house, then reopens Viewer. */
        finishViewer(access)
        if (
            viewerSession(access.player) == null && ownedRuntime(access.player)?.buildMode == true
        ) {
            openViewer(access)
        }
    }

    fun finishViewer(access: ProtectedAccess) {
        val session = viewerSession(access.player) ?: return
        if (session.mode != ViewerMode.IDLE) {
            access.mes("Finish or cancel the current room action first.")
            return
        }
        val validation =
            validateViewerRooms(
                session.working.rooms,
                session.working.furniture,
                access.player.constructionLvl,
            )
        if (validation != null) {
            access.mes(validation)
            return
        }
        val runtime = ownedRuntime(access.player) ?: return
        val persisted = access.player.loadHouseBlueprint()
        val persistedRevision = persisted.layoutRevision
        if (
            runtime.blueprint.layoutRevision != session.originalRevision ||
                persistedRevision != session.originalRevision
        ) {
            access.mes("Your house changed while the Viewer was open. Reopen it and try again.")
            cancelViewer(access)
            return
        }
        val changed =
            runtime.blueprint.rooms != session.working.rooms ||
                runtime.blueprint.furniture != session.working.furniture
        if (!changed) {
            cancelViewer(access)
            return
        }
        val totalCost = session.addedRoomCosts.values.sum()
        val invalidAddition =
            session.addedRoomCosts.keys
                .mapNotNull { id -> session.working.rooms.firstOrNull { it.stableId == id } }
                .firstOrNull { placement ->
                    val definition = rooms[placement.room]
                    definition == null || access.player.constructionLvl < definition.level
                }
        if (invalidAddition != null) {
            access.mes("You no longer meet the Construction level for a staged room.")
            return
        }
        if (inventoryCount(access.player, 995) < totalCost) {
            access.mes("You need $totalCost coins to build the new rooms.")
            return
        }
        if (
            totalCost > 0 &&
                access.player
                    .invDel(
                        access.player.inv,
                        RSCM.getReverseMapping(RSCMType.OBJ, 995),
                        totalCost,
                        strict = true,
                    )
                    .failure
        ) {
            access.mes("You no longer have enough coins to build the new rooms.")
            return
        }
        val updated =
            persisted.copy(
                rooms = session.working.rooms.map { it.copy() }.toMutableList(),
                furniture = session.working.furniture.toMutableMap(),
                layoutRevision = session.originalRevision + 1L,
            )
        access.player.saveHouseBlueprint(updated)
        viewerSessions.remove(access.player.houseKey())
        if (rebuildOwnerHouse(access.player, runtime, updated, buildMode = true)) {
            syncHouseOptions(access.player)
            return
        }
        access.player.saveHouseBlueprint(runtime.blueprint)
        if (totalCost > 0) {
            access.player.invAdd(
                access.player.inv,
                RSCM.getReverseMapping(RSCMType.OBJ, 995),
                totalCost,
                strict = false,
            )
        }
    }

    fun cancelViewer(access: ProtectedAccess, closeInterface: Boolean = true) {
        val key = access.player.houseKey()
        viewerSessions.remove(key)
        pendingRooms.remove(key)?.takeUnless(PendingRoom::fromViewer)?.let {
            pendingRooms[key] = it
        }
        clearViewerSelection(access.player)
        if (closeInterface) access.player.ifClose(events)
    }

    fun closeViewer(player: Player) {
        val key = player.houseKey()
        val session = viewerSessions[key]
        if (session?.interfaceTransition == true) {
            session.interfaceTransition = false
            return
        }
        viewerSessions.remove(key)
        pendingRooms.remove(key)?.takeUnless(PendingRoom::fromViewer)?.let {
            pendingRooms[key] = it
        }
        clearViewerSelection(player)
    }

    fun closeRoomSelection(player: Player) {
        val key = player.houseKey()
        val session = viewerSessions[key]
        if (session?.interfaceTransition == true) {
            session.interfaceTransition = false
            pendingRooms.remove(key)
            return
        }
        val room = pendingRooms.remove(key) ?: return
        if (room.fromViewer) {
            viewerSessions.remove(key)
            clearViewerSelection(player)
        }
    }

    fun viewerUnsupported(access: ProtectedAccess, operation: String) {
        if (viewerSession(access.player) == null) return
        access.mes("House Viewer $operation is not available yet; no house data was changed.")
    }

    fun addRoom(
        player: Player,
        roomName: String,
        x: Int,
        z: Int,
        level: Int,
        rotation: Int,
    ): String {
        val room = rooms[roomName] ?: return "Unknown room: $roomName."
        if (x !in 0..7 || z !in 0..7 || level !in 0..2 || rotation !in 0..3)
            return "Room position must be x/z 0-7, level 0-2, rotation 0-3."
        if (room.basement && level != 0) return "${room.name} can only be built in the basement."
        if (!room.basement && level == 0) return "${room.name} cannot be built in the basement."
        val blueprint = player.loadHouseBlueprint()
        if (blueprint.rooms.any { it.zoneX == x && it.zoneZ == z && it.level == level })
            return "That room space is already occupied."
        blueprint.rooms +=
            HouseRoom(room.name, x, z, level, rotation, stableId = UUID.randomUUID().toString())
        player.saveHouseBlueprint(blueprint.copy(layoutRevision = blueprint.layoutRevision + 1L))
        return "Added ${room.name.lowercase().replace('_', ' ')}. Enter your house again to build there."
    }

    fun openRoomSelection(player: Player, x: Int, z: Int, level: Int) {
        if (viewerSession(player) != null) {
            player.mes("Use an empty space in the House Viewer to add a room.")
            return
        }
        val runtime = active[player.houseKey()]
        if (runtime == null || !runtime.buildMode) {
            player.mes("Enter your house in building mode before adding a room.")
            return
        }
        if (x !in 0..7 || z !in 0..7 || level !in 0..2) {
            player.mes("Room coordinates must be x/z 0-7 and floor 0-2.")
            return
        }
        if (runtime.blueprint.rooms.any { it.zoneX == x && it.zoneZ == z && it.level == level }) {
            player.mes("That room space is already occupied.")
            return
        }
        pendingRooms[player.houseKey()] = PendingRoom(x, z, level)
        player.ifOpenMainModal(ROOM_INTERFACE, events)
        player.ifSetEvents(
            ServerCacheManager.fromComponent((ROOM_INTERFACE shl 16) or ROOM_LIST_COMPONENT),
            1..rooms.values.maxOf { it.roomId },
            IfEvent.Op1,
            IfEvent.PauseButton,
        )
    }

    suspend fun buildRoomSelected(access: ProtectedAccess, roomId: Int) {
        val key = access.player.houseKey()
        log.info("[Construction] room menu click player={} roomId={}", key, roomId)
        val pendingRoom = pendingRooms[key]
        if (pendingRoom == null) {
            log.warn(
                "[Construction] ignored room click with no pending door hotspot player={} roomId={}",
                key,
                roomId,
            )
            access.mes("That room selection is no longer available. Use a door hotspot again.")
            return
        }
        val room = rooms.values.firstOrNull { it.roomId == roomId }
        if (room == null) {
            log.warn(
                "[Construction] unknown revision-240 room selection player={} roomId={}",
                key,
                roomId,
            )
            access.mes("That room option is unavailable.")
            return
        }
        val runtime = active[key]
        if (runtime == null || !runtime.buildMode) {
            log.warn(
                "[Construction] room click outside active build mode player={} roomId={}",
                key,
                roomId,
            )
            pendingRooms.remove(key)
            access.mes("You are no longer in a house in building mode.")
            return
        }
        if (runtime.testMode) {
            pendingRooms.remove(key)
            access.ifCloseSub(roomInterfaceInternal)
            access.mes("The Construction test house already contains every available room.")
            return
        }
        if (access.player.constructionLvl < room.level) {
            access.mes("You need Construction level ${room.level} to build that room.")
            return
        }
        val viewer = viewerSessions[key]?.takeIf { pendingRoom.fromViewer }
        val targetBlueprint = viewer?.working ?: runtime.blueprint
        val placementProblem =
            roomPlacementProblem(
                targetBlueprint,
                room,
                pendingRoom.x,
                pendingRoom.z,
                pendingRoom.level,
                access.player.constructionLvl,
            )
        if (placementProblem != null) {
            access.mes(placementProblem)
            return
        }
        val stagedCost = viewer?.addedRoomCosts?.values?.sum() ?: 0
        if (inventoryCount(access.player, 995) < stagedCost + room.cost) {
            access.mes("You need ${stagedCost + room.cost} coins for the staged rooms.")
            return
        }
        if (viewer != null) {
            val placement =
                HouseRoom(
                    room.name,
                    pendingRoom.x,
                    pendingRoom.z,
                    pendingRoom.level,
                    stableId = UUID.randomUUID().toString(),
                )
            viewer.working.rooms += placement
            viewer.addedRoomCosts[placement.stableId] = room.cost
            viewer.selectedRoomId = placement.stableId
            viewer.mode = ViewerMode.IDLE
            pendingRooms.remove(key)
            viewer.interfaceTransition = true
            openViewerInterface(access, viewer, placement.stableId)
            access.mes("Room staged. Its cost will be charged when you select Done.")
            return
        }
        if (
            access.player
                .invDel(
                    access.player.inv,
                    RSCM.getReverseMapping(RSCMType.OBJ, 995),
                    room.cost,
                    strict = true,
                )
                .failure
        ) {
            access.mes("You no longer have enough coins to build that room.")
            return
        }
        runtime.blueprint.rooms +=
            HouseRoom(
                room.name,
                pendingRoom.x,
                pendingRoom.z,
                pendingRoom.level,
                stableId = UUID.randomUUID().toString(),
            )
        access.player.saveHouseBlueprint(
            runtime.blueprint.copy(layoutRevision = runtime.blueprint.layoutRevision + 1L)
        )
        pendingRooms.remove(key)
        access.ifCloseSub(roomInterfaceInternal)
        log.info(
            "[Construction] built room player={} room={} roomId={} slot={},{},{} cost={}",
            key,
            room.name,
            room.roomId,
            pendingRoom.x,
            pendingRoom.z,
            pendingRoom.level,
            room.cost,
        )
        access.mes("You build a ${room.name.lowercase().replace('_', ' ')}.")
        leave(access)
        enter(access.player, buildMode = true)
    }

    private fun allRoomsBlueprint(): HouseBlueprint {
        val blueprint = HouseBlueprint.default()
        val surfaceSlots =
            buildList {
                    for (z in 0..7) for (x in 0..7) if (!((x == 3 && z == 3) || (x == 3 && z == 4)))
                        add(x to z)
                }
                .iterator()
        val basementSlots = buildList { for (z in 0..7) for (x in 0..7) add(x to z) }.iterator()
        val upstairsPairs =
            mapOf("SKILL_HALL_DOWN" to "SKILL_HALL_UP", "QUEST_HALL_DOWN" to "QUEST_HALL_UP")
        rooms.values.forEach { room ->
            if (room.name in setOf("GARDEN", "PARLOUR")) return@forEach
            val lowerRoom =
                upstairsPairs[room.name]?.let { lowerName ->
                    blueprint.rooms.firstOrNull { it.room == lowerName && it.level == 1 }
                }
            if (lowerRoom != null) {
                blueprint.rooms +=
                    HouseRoom(
                        room.name,
                        lowerRoom.zoneX,
                        lowerRoom.zoneZ,
                        2,
                        stableId = UUID.randomUUID().toString(),
                    )
            } else {
                val (x, z) = if (room.basement) basementSlots.next() else surfaceSlots.next()
                blueprint.rooms +=
                    HouseRoom(
                        room.name,
                        x,
                        z,
                        if (room.basement) 0 else 1,
                        stableId = UUID.randomUUID().toString(),
                    )
            }
        }
        return blueprint
    }

    fun populateAllRooms(player: Player) {
        val blueprint = allRoomsBlueprint()
        player.saveHouseBlueprint(blueprint)
        player.mes(
            "Your test house now contains every donor Construction room. Enter building mode to inspect it."
        )
    }

    fun reset(player: Player) {
        player.saveHouseBlueprint(HouseBlueprint.default())
        player.mes("Your house has been reset to the starter garden and parlour.")
    }

    fun openFurniture(access: ProtectedAccess, loc: BoundLocInfo): Boolean {
        if (viewerSession(access.player) != null) {
            access.mes("Finish or cancel the House Viewer before changing furniture.")
            return true
        }
        val runtime = runtimeFor(access.player) ?: return false
        val target =
            runtime.byKey.values.firstOrNull { candidate ->
                val currentBuildable =
                    runtime.blueprint.furniture[candidate.key]?.let(buildables::get)
                candidate.locs.any { hotspot ->
                    val matchesHotspot = hotspot.id == loc.id
                    val matchesBuiltFurniture =
                        currentBuildable != null &&
                            builtLocId(candidate, currentBuildable, hotspot) == loc.id
                    (matchesHotspot || matchesBuiltFurniture) &&
                        hotspot.coords == loc.coords &&
                        hotspot.layer == loc.layer
                }
            } ?: return false
        val declaredChoices = target.hotspot.buildables.mapNotNull(buildables::get)
        val choices = declaredChoices.mapNotNull(ConstructionFurnitureMenuCatalog::resolve)
        if (choices.size != declaredChoices.size) {
            val unresolved =
                declaredChoices.filter { ConstructionFurnitureMenuCatalog.resolve(it) == null }
            log.error(
                "[Construction] cache menu rows unresolved hotspot={} entries={}",
                target.hotspot.name,
                unresolved.joinToString { it.name },
            )
        }
        if (choices.isEmpty()) return false
        if (!runtime.buildMode) {
            val current = runtime.blueprint.furniture[target.key]
            if (current == "EXIT_PORTAL") {
                leave(access)
            } else {
                access.mes("Enter your house in building mode to change furniture.")
            }
            return true
        }
        log.info(
            "[Construction] opening furniture menu player={} hotspot={} coords={} choices={}",
            access.player.houseKey(),
            target.hotspot.name,
            loc.coords,
            choices.size,
        )
        pending[access.player.houseKey()] = PendingBuild(target, choices)
        access.ifOpenMainModal(furnitureInterfaceInternal)
        choices.forEachIndexed { index, choice ->
            val buildable = choice.buildable
            access.runClientScript(
                1404,
                index + 1,
                choice.furnitureRowId,
                buildable.level,
                menuText(choice),
                if (runtime.testMode || canBuild(access.player, buildable, target)) 1 else 0,
            )
        }
        // Match the revision-240 furniture-creation lifecycle. Script 1406 enables the
        // clickable rows and script 2157 enters the construction selection input mode.
        // Both 2157 and 2158 are zero-argument scripts in this cache revision.
        access.runClientScript(1406, choices.size, 0)
        access.player.ifSetEvents(
            ServerCacheManager.fromComponent(
                (FURNITURE_INTERFACE shl 16) or FURNITURE_LIST_COMPONENT
            ),
            1..choices.size,
            IfEvent.Op1,
            IfEvent.PauseButton,
        )
        access.runClientScript(2157)
        return true
    }

    fun closeFurnitureMenu(player: Player) {
        val key = player.houseKey()
        // The revision-240 client closes this modal as part of selecting a row. Depending on
        // packet order, IfCloseSub can be published before the row's IfModalButton event. Do not
        // discard the pending selection here: buildSelected consumes it atomically, while opening
        // another hotspot, leaving the house, and entering a house all replace or clear it safely.
        log.info(
            "[Construction] furniture menu closed player={} pending={}",
            key,
            pending.containsKey(key),
        )
        ClientScripts.chatDefaultRestoreInput(player)
    }

    suspend fun buildSelected(access: ProtectedAccess, comsub: Int) {
        val key = access.player.houseKey()
        val selection = pending.remove(key)
        if (selection == null) {
            log.warn(
                "[Construction] ignored furniture click with no pending menu player={} comsub={}",
                key,
                comsub,
            )
            access.mes("That furniture selection is no longer available. Open the hotspot again.")
            return
        }
        // A furniture row is single-use even if a client repeats the same button packet.
        val choice = selection.choices.getOrNull(comsub - 1)
        if (choice == null) {
            log.warn(
                "[Construction] invalid furniture row player={} comsub={} choices={}",
                key,
                comsub,
                selection.choices.size,
            )
            access.mes("That furniture option is unavailable.")
            return
        }
        val buildable = choice.buildable
        log.info(
            "[Construction] build click player={} row={} buildable={}",
            key,
            comsub,
            buildable.name,
        )
        val runtime = active[key]
        if (runtime == null || !runtime.buildMode) {
            access.mes("You are no longer in a house in building mode.")
            return
        }
        if (selection.target.hotspot.name == "FLOOR_SPACE") {
            clearActiveThroneTrap(runtime, selection.target.roomKey)
        }
        if (!runtime.testMode) {
            val problem = buildProblem(access.player, buildable, selection.target)
            if (problem != null) {
                log.info(
                    "[Construction] rejected build player={} buildable={} reason={}",
                    key,
                    buildable.name,
                    problem,
                )
                access.mes(buildProblemMessage(problem))
                return
            }
            if (!takeMaterials(access.player, buildable)) {
                access.mes("You no longer have all of the required materials.")
                return
            }
        }
        constructionAnim(access, buildable)
        if (!replace(selection.target, buildable)) {
            if (!runtime.testMode) restoreMaterials(access.player, buildable)
            access.mes("The furniture could not be placed. Your materials were returned.")
            return
        }
        runtime.blueprint.furniture[selection.target.key] = buildable.name
        if (!runtime.testMode) {
            access.player.saveHouseBlueprint(runtime.blueprint)
            access.statAdvance(
                "stat.construction",
                buildable.experience.toDouble() * xpMods.get(access.player, "stat.construction"),
            )
        }
        access.ifCloseSub(furnitureInterfaceInternal)
        pendingRooms.remove(access.player.houseKey())
        access.mes("You build the ${choice.displayName.lowercase()}.")
    }

    fun removeFurniture(access: ProtectedAccess, loc: BoundLocInfo): Boolean {
        val runtime = active[access.player.houseKey()] ?: return false
        if (!runtime.buildMode) return false
        val target =
            runtime.byKey.values.firstOrNull { candidate ->
                val candidateName =
                    runtime.blueprint.furniture[candidate.key] ?: return@firstOrNull false
                val candidateBuildable = buildables[candidateName] ?: return@firstOrNull false
                candidate.locs.any { hotspot ->
                    builtLocId(candidate, candidateBuildable, hotspot) == loc.id &&
                        hotspot.coords == loc.coords &&
                        hotspot.layer == loc.layer
                }
            } ?: return false
        val builtName = runtime.blueprint.furniture[target.key] ?: return false
        if (
            builtName == "EXIT_PORTAL" &&
                runtime.blueprint.furniture.values.count { it == "EXIT_PORTAL" } == 1
        ) {
            access.mes("Your house must have at least one exit portal.")
            return true
        }
        val buildable = buildables[builtName] ?: return false
        if (target.hotspot.name == "FLOOR_SPACE") {
            clearActiveThroneTrap(runtime, target.roomKey)
        }
        if (!restoreHotspot(target, buildable)) {
            access.mes("The furniture could not be removed.")
            return true
        }
        runtime.blueprint.furniture.remove(target.key)
        if (!runtime.testMode) access.player.saveHouseBlueprint(runtime.blueprint)
        access.mes(
            "You remove the ${ConstructionFurnitureMenuCatalog.canonicalName(buildable).lowercase()}."
        )
        return true
    }

    fun openRoomFromDoor(access: ProtectedAccess, loc: BoundLocInfo): Boolean {
        val runtime = active[access.player.houseKey()] ?: return false
        if (!runtime.buildMode) return false
        val door =
            runtime.roomDoors.firstOrNull { candidate ->
                candidate.loc.id == loc.id &&
                    candidate.loc.coords == loc.coords &&
                    candidate.loc.layer == loc.layer
            } ?: return false
        openRoomSelection(access.player, door.x, door.z, door.level)
        return true
    }

    suspend fun pullThroneLever(access: ProtectedAccess, loc: BoundLocInfo) {
        val runtime = runtimeFor(access.player) ?: return
        val leverTarget = findBuiltTarget(runtime, loc, "LEVER") ?: return
        val floorTarget = runtime.byKey["${leverTarget.roomKey}:FLOOR_SPACE"]
        if (floorTarget == null) {
            access.mes("This throne room has no floor trap space.")
            return
        }
        val builtName = runtime.blueprint.furniture[floorTarget.key]
        val trap = builtName?.let(ThroneFloorTrap::fromBuildable)
        if (trap == null || trap == ThroneFloorTrap.FLOOR_DECORATION) {
            access.anim(3611)
            access.mes("Nothing interesting happens.")
            return
        }
        if (runtime.activeThroneTraps.containsKey(leverTarget.roomKey)) {
            access.anim(3661)
            clearActiveThroneTrap(runtime, leverTarget.roomKey)
            access.mes("You release the throne-room trap.")
            return
        }
        when (trap) {
            ThroneFloorTrap.STEEL_CAGE -> {
                access.anim(3611)
                val roomRotation = roomPlacement(runtime, leverTarget.roomKey)?.rotation ?: 0
                val pieces =
                    runCatching {
                            ThroneRoomTrapLayout.steelCagePieces(
                                floorTarget.locs.map(LocInfo::coords),
                                roomRotation,
                            )
                        }
                        .getOrElse {
                            log.error("[Construction] invalid throne steel-cage layout", it)
                            access.mes("The throne-room trap could not be activated.")
                            return
                        }
                if (!spawnActiveThroneTrap(runtime, leverTarget.roomKey, pieces)) {
                    access.mes("The throne-room trap could not be activated.")
                }
            }
            ThroneFloorTrap.LESSER_MAGIC_CAGE,
            ThroneFloorTrap.GREATER_MAGIC_CAGE -> {
                access.anim(3611)
                val piece =
                    runCatching {
                            ThroneRoomTrapLayout.magicCagePiece(
                                floorTarget.locs.map(LocInfo::coords),
                                trap,
                            )
                        }
                        .getOrElse {
                            log.error("[Construction] invalid throne magic-cage layout", it)
                            access.mes("The throne-room trap could not be activated.")
                            return
                        }
                if (!spawnActiveThroneTrap(runtime, leverTarget.roomKey, listOf(piece))) {
                    access.mes("The throne-room trap could not be activated.")
                }
            }
            ThroneFloorTrap.PITFALL ->
                activatePitfall(access, runtime, leverTarget.roomKey, floorTarget)
            ThroneFloorTrap.FLOOR_DECORATION -> Unit
        }
    }

    private suspend fun activatePitfall(
        access: ProtectedAccess,
        runtime: HouseRuntime,
        roomKey: String,
        floorTarget: HouseTarget,
    ) {
        val placement = roomPlacement(runtime, roomKey)
        val hasOubliette =
            placement != null &&
                runtime.blueprint.rooms.any {
                    it.zoneX == placement.zoneX &&
                        it.zoneZ == placement.zoneZ &&
                        it.level == placement.level - 1 &&
                        it.room == "OUBLIETTE"
                }
        if (!hasOubliette) {
            access.mes("There is a pitfall trap installed, but no Oubliette below this room.")
            return
        }
        val buildable = buildables[ThroneFloorTrap.PITFALL.buildableName] ?: return
        val original = mutableListOf<LocInfo>()
        val pitfalls = mutableListOf<LocInfo>()
        for (hotspot in floorTarget.locs) {
            val builtId = builtLocId(floorTarget, buildable, hotspot)
            val current =
                locs.findAll(hotspot.coords).firstOrNull {
                    it.layer == hotspot.layer && it.id == builtId
                }
                    ?: run {
                        rollbackLocSwap(original, pitfalls)
                        access.mes("The throne-room trap could not be activated.")
                        return
                    }
            if (!locs.del(current, Int.MAX_VALUE)) {
                rollbackLocSwap(original, pitfalls)
                access.mes("The throne-room trap could not be activated.")
                return
            }
            original += current
            val pitfall =
                LocInfo(
                    hotspot.layer,
                    hotspot.coords,
                    LocEntity(
                        ThroneFloorTrap.PITFALL.activatedLocId!!,
                        hotspot.shape.id,
                        hotspot.angle.id,
                    ),
                )
            if (!locs.add(pitfall, Int.MAX_VALUE)) {
                rollbackLocSwap(original, pitfalls)
                access.mes("The throne-room trap could not be activated.")
                return
            }
            pitfalls += pitfall
        }
        access.anim(3611)
        val trapTiles = floorTarget.locs.mapTo(mutableSetOf(), LocInfo::coords)
        val playerWillFall = access.player.coords in trapTiles
        try {
            if (playerWillFall) access.anim(1950)
            access.delay(5)
            if (playerWillFall) {
                val current = access.player.coords
                access.teleport(CoordGrid(current.x, current.z, current.level - 1))
                access.anim(3640)
            }
            access.delay(3)
        } finally {
            pitfalls.asReversed().forEach { locs.del(it, Int.MAX_VALUE) }
            original.forEach { locs.add(it, Int.MAX_VALUE) }
        }
    }

    private fun spawnActiveThroneTrap(
        runtime: HouseRuntime,
        roomKey: String,
        pieces: List<LocInfo>,
    ): Boolean {
        val added = mutableListOf<LocInfo>()
        for (piece in pieces) {
            if (!locs.add(piece, Int.MAX_VALUE)) {
                added.asReversed().forEach { locs.del(it, Int.MAX_VALUE) }
                return false
            }
            added += piece
        }
        runtime.activeThroneTraps[roomKey] = added
        return true
    }

    private fun clearActiveThroneTrap(runtime: HouseRuntime, roomKey: String) {
        runtime.activeThroneTraps.remove(roomKey)?.asReversed()?.forEach { piece ->
            locs
                .findAll(piece.coords)
                .firstOrNull { it.layer == piece.layer && it.id == piece.id }
                ?.let { locs.del(it, Int.MAX_VALUE) }
        }
    }

    private fun clearAllThroneTraps(runtime: HouseRuntime) {
        runtime.activeThroneTraps.keys.toList().forEach { clearActiveThroneTrap(runtime, it) }
    }

    private fun findBuiltTarget(
        runtime: HouseRuntime,
        loc: BoundLocInfo,
        hotspotName: String,
    ): HouseTarget? =
        runtime.byKey.values.firstOrNull { candidate ->
            if (candidate.hotspot.name != hotspotName) return@firstOrNull false
            val builtName = runtime.blueprint.furniture[candidate.key] ?: return@firstOrNull false
            val buildable = buildables[builtName] ?: return@firstOrNull false
            candidate.locs.any { hotspot ->
                builtLocId(candidate, buildable, hotspot) == loc.id &&
                    hotspot.coords == loc.coords &&
                    hotspot.layer == loc.layer
            }
        }

    private fun roomPlacement(runtime: HouseRuntime, roomKey: String): HouseRoom? {
        return runtime.blueprint.rooms.firstOrNull { it.stableId == roomKey }
    }

    fun climbStaircase(access: ProtectedAccess, loc: BoundLocInfo, requestedDirection: Int?) {
        val runtime = runtimeFor(access.player) ?: return
        val target =
            runtime.byKey.values.firstOrNull { candidate ->
                val builtName =
                    runtime.blueprint.furniture[candidate.key] ?: return@firstOrNull false
                val buildable = buildables[builtName] ?: return@firstOrNull false
                candidate.locs.any { hotspot ->
                    builtLocId(candidate, buildable, hotspot) == loc.id &&
                        hotspot.coords == loc.coords &&
                        hotspot.layer == loc.layer
                }
            } ?: return
        val placement = roomPlacement(runtime, target.roomKey) ?: return
        val zoneX = placement.zoneX
        val zoneZ = placement.zoneZ
        val level = placement.level
        val canTravel: (Int) -> Boolean = { direction ->
            val destinationLevel = level + direction
            destinationLevel in 0..2 &&
                runtime.blueprint.rooms.any {
                    it.zoneX == zoneX && it.zoneZ == zoneZ && it.level == destinationLevel
                }
        }
        val direction =
            when {
                requestedDirection != null && canTravel(requestedDirection) -> requestedDirection
                requestedDirection != null -> null
                canTravel(1) -> 1
                canTravel(-1) -> -1
                else -> null
            }
        if (direction == null) {
            access.mes("There is no room on that floor.")
            return
        }
        val translation =
            if (direction > 0) loc.climbUpTranslation() else loc.climbDownTranslation()
        access.teleport(loc.coords.translate(translation))
    }

    private fun BoundLocInfo.climbDownTranslation(): Translation =
        when (angle) {
            LocAngle.West -> Translation(x = adjustedWidth - 1, z = -1, level = -1)
            LocAngle.North -> Translation(x = -adjustedWidth, z = 0, level = -1)
            LocAngle.East -> Translation(x = 0, z = adjustedLength, level = -1)
            LocAngle.South -> Translation(x = adjustedWidth, z = adjustedLength - 1, level = -1)
        }

    private fun BoundLocInfo.climbUpTranslation(): Translation =
        when (angle) {
            LocAngle.West -> Translation(x = adjustedWidth, z = 0, level = 1)
            LocAngle.North -> Translation(x = 0, z = -(adjustedLength - 1), level = 1)
            LocAngle.East ->
                Translation(x = -(adjustedWidth - 1), z = adjustedLength - 1, level = 1)
            LocAngle.South -> Translation(x = adjustedWidth - 1, z = adjustedLength, level = 1)
        }

    /**
     * Donor build arrays are keyed by the source hotspot ID, not by repository iteration order. A
     * hotspot ID can occur many times (hedge runs, rugs, floors), and every occurrence must use the
     * built ID at the matching source-ID index.
     */
    private fun builtLocId(
        target: HouseTarget,
        buildable: DonorBuildable,
        hotspotLoc: LocInfo,
    ): Int {
        val sourceIndex = target.hotspot.locIds.indexOf(hotspotLoc.id)
        if (sourceIndex < 0) return -1
        return buildable.builtLocIds.getOrElse(sourceIndex) {
            buildable.builtLocIds.firstOrNull() ?: -1
        }
    }

    fun handleHouseLoc(access: ProtectedAccess, loc: BoundLocInfo): Boolean =
        openFurniture(access, loc)

    private fun houseArea(blueprint: HouseBlueprint): InstanceArea.Template {
        val template =
            RegionTemplate.createLarge {
                /*
                 * The donor fills every house zone before placing individual rooms.
                 * Without these base zones the client rebuild contains uninitialised map
                 * sections and renders the house as a blank beige scene.
                 */
                for (zoneX in 0 until 8) {
                    for (zoneZ in 0 until 8) {
                        if (
                            blueprint.rooms.none {
                                it.zoneX == zoneX && it.zoneZ == zoneZ && it.level == 0
                            }
                        ) {
                            this[zoneX, zoneZ, 0] = revision240HouseZone(235, 712, blueprint.style)
                        }
                        if (
                            blueprint.rooms.none {
                                it.zoneX == zoneX && it.zoneZ == zoneZ && it.level == 1
                            }
                        ) {
                            this[zoneX, zoneZ, 1] = revision240HouseZone(233, 712, blueprint.style)
                        }
                        if (
                            blueprint.rooms.none {
                                it.zoneX == zoneX && it.zoneZ == zoneZ && it.level == 2
                            }
                        ) {
                            this[zoneX, zoneZ, 2] = revision240HouseZone(236, 713, blueprint.style)
                        }
                        this[zoneX, zoneZ, 3] = revision240HouseZone(236, 713, blueprint.style)
                    }
                }
                blueprint.rooms.forEach { placement ->
                    val room = rooms.getValue(placement.room)
                    val source = revision240HouseZone(room.chunkX, room.chunkZ, blueprint.style)
                    when (placement.rotation) {
                        0 -> this[placement.zoneX, placement.zoneZ, placement.level] = source
                        1 ->
                            this[placement.zoneX, placement.zoneZ, placement.level] =
                                source.rotate90()
                        2 ->
                            this[placement.zoneX, placement.zoneZ, placement.level] =
                                source.rotate180()
                        3 ->
                            this[placement.zoneX, placement.zoneZ, placement.level] =
                                source.rotate270()
                    }
                }
            }
        val garden = rooms.getValue("GARDEN")
        val gardenSource = revision240HouseZone(garden.chunkX, garden.chunkZ, blueprint.style)
        return InstanceArea.template(
            template,
            enterCoord = donorZoneLocal(gardenSource, 3, 2),
            exitCoord = CoordGrid(3079, 3496, 0),
        )
    }

    /**
     * Converts a donor 8x8-zone position into the region-local format used by the instance API.
     *
     * Donor Construction room coordinates are [ZoneKey] coordinates, whereas [RegionLocal] expects
     * a 64x64 map region plus a tile offset within that region.
     */
    private fun donorZoneLocal(zone: ZoneKey, localX: Int = 0, localZ: Int = 0): RegionLocal {
        require(localX in 0 until 8 && localZ in 0 until 8) {
            "Construction donor-zone offset must be within an 8x8 zone: ($localX,$localZ)"
        }
        val origin = zone.toCoords()
        val x = origin.x + localX
        val z = origin.z + localZ
        return RegionLocal(
            level = origin.level,
            regionZoneX = x / 64,
            regionZoneZ = z / 64,
            localX = x % 64,
            localZ = z % 64,
        )
    }

    private fun indexHotspots(runtime: HouseRuntime) {
        val layouts = roomLayouts(runtime)
        layouts.forEach { layout ->
            indexRoomDoors(runtime, layout, layouts)
            layout.definition.hotspots.forEach { hotspotName ->
                val hotspot = hotspots[hotspotName] ?: return@forEach
                // Multi-piece furniture arrays use the same positional order as the
                // hotspot ID array in the cache/donor data. Repository iteration order
                // is not a stable substitute for that contract.
                val matches = hotspot.locIds.flatMap { id -> layout.locs.filter { it.id == id } }
                if (matches.isEmpty()) return@forEach
                val target =
                    HouseTarget(furnitureKey(layout.placement, hotspot.name), hotspot, matches)
                runtime.byKey[target.key] = target
            }
        }
    }

    private fun roomLayouts(runtime: HouseRuntime): List<RoomLayoutSnapshot> =
        runtime.blueprint.rooms.mapNotNull { placement ->
            val definition = rooms[placement.room] ?: return@mapNotNull null
            val origin =
                CoordGrid(
                    runtime.base.x + placement.zoneX * HOUSE_ZONE_SIZE,
                    runtime.base.z + placement.zoneZ * HOUSE_ZONE_SIZE,
                    runtime.base.level + placement.level,
                )
            RoomLayoutSnapshot(
                placement = placement,
                definition = definition,
                origin = origin,
                locs = locs.findAll(ZoneKey.from(origin)).toList(),
            )
        }

    private fun indexRoomDoors(
        runtime: HouseRuntime,
        layout: RoomLayoutSnapshot,
        layouts: List<RoomLayoutSnapshot>,
    ) {
        layout.locs
            .filter { it.id in roomDoorIds }
            .forEach { door ->
                val destination = roomDoorDestination(layout.placement, layout.origin, door.coords)
                if (!runtime.buildMode) {
                    // Visit-mode doors are replaced after every room has been indexed.
                    return@forEach
                }
                val inBounds = destination != null && destination.x in 0..7 && destination.z in 0..7
                val adjacent =
                    destination?.let { target ->
                        layouts.firstOrNull {
                            it.placement.zoneX == target.x &&
                                it.placement.zoneZ == target.z &&
                                it.placement.level == target.level
                        }
                    }
                when {
                    !inBounds -> {
                        if (layout.definition.outdoors) removeExactLoc(door)
                        else replaceRoomBoundary(door, runtime.blueprint.style.wallLocId)
                    }
                    adjacent == null -> {
                        runtime.roomDoors +=
                            RoomDoorTarget(door, destination!!.x, destination.z, destination.level)
                    }
                    layout.definition.outdoors -> removeExactLoc(door)
                    hasMatchingDoorway(layout, door, adjacent) -> removeExactLoc(door)
                    else -> replaceRoomBoundary(door, runtime.blueprint.style.wallLocId)
                }
            }
    }

    private fun roomDoorDestination(
        placement: HouseRoom,
        roomOrigin: CoordGrid,
        door: CoordGrid,
    ): PendingRoom? {
        val localX = door.x - roomOrigin.x
        val localZ = door.z - roomOrigin.z
        return when {
            localX == 0 -> PendingRoom(placement.zoneX - 1, placement.zoneZ, placement.level)
            localX == HOUSE_ZONE_SIZE - 1 ->
                PendingRoom(placement.zoneX + 1, placement.zoneZ, placement.level)
            localZ == 0 -> PendingRoom(placement.zoneX, placement.zoneZ - 1, placement.level)
            localZ == HOUSE_ZONE_SIZE - 1 ->
                PendingRoom(placement.zoneX, placement.zoneZ + 1, placement.level)
            else -> null
        }
    }

    /**
     * A shared wall is an opening only when both room templates expose the same doorway tiles.
     * Merely having a room in the adjacent grid cell is insufficient: after a House Viewer rotation
     * that room may present a solid wall on this side. The old adjacency-only check deleted the
     * remaining door-hotspot wall pieces and produced broken walls in game.
     */
    private fun hasMatchingDoorway(
        sourceLayout: RoomLayoutSnapshot,
        sourceDoor: LocInfo,
        adjacentLayout: RoomLayoutSnapshot,
    ): Boolean {
        val destination =
            roomDoorDestination(sourceLayout.placement, sourceLayout.origin, sourceDoor.coords)
                ?: return false
        if (
            destination.x != adjacentLayout.placement.zoneX ||
                destination.z != adjacentLayout.placement.zoneZ ||
                destination.level != adjacentLayout.placement.level
        ) {
            return false
        }
        val sourceLocalX = sourceDoor.coords.x - sourceLayout.origin.x
        val sourceLocalZ = sourceDoor.coords.z - sourceLayout.origin.z
        val eastWestBoundary = sourceLocalX == 0 || sourceLocalX == HOUSE_ZONE_SIZE - 1
        val northSouthBoundary = sourceLocalZ == 0 || sourceLocalZ == HOUSE_ZONE_SIZE - 1
        if (!eastWestBoundary && !northSouthBoundary) return false

        return adjacentLayout.locs
            .asSequence()
            .filter { it.id in roomDoorIds }
            .any { candidate ->
                val candidateDestination =
                    roomDoorDestination(
                        adjacentLayout.placement,
                        adjacentLayout.origin,
                        candidate.coords,
                    )
                val pointsBack =
                    candidateDestination != null &&
                        candidateDestination.x == sourceLayout.placement.zoneX &&
                        candidateDestination.z == sourceLayout.placement.zoneZ &&
                        candidateDestination.level == sourceLayout.placement.level
                pointsBack &&
                    when {
                        eastWestBoundary -> candidate.coords.z == sourceDoor.coords.z
                        northSouthBoundary -> candidate.coords.x == sourceDoor.coords.x
                        else -> false
                    }
            }
    }

    /** Applies the OSRS/Kronos room-boundary rules and the persisted House Options door mode. */
    private fun renderArchitecturalDoors(runtime: HouseRuntime) {
        val layouts = roomLayouts(runtime)
        layouts.forEach { layout ->
            val roomDoors = layout.locs.filter { it.id in roomDoorIds && it.shape.id == 0 }
            roomDoors.forEach { source ->
                val destination =
                    roomDoorDestination(layout.placement, layout.origin, source.coords)
                val inBounds = destination != null && destination.x in 0..7 && destination.z in 0..7
                val adjacent =
                    destination?.let { target ->
                        layouts.firstOrNull {
                            it.placement.zoneX == target.x &&
                                it.placement.zoneZ == target.z &&
                                it.placement.level == target.level
                        }
                    }
                val adjacentOutdoors = adjacent?.definition?.outdoors == true
                val matchingDoorway =
                    adjacent != null && hasMatchingDoorway(layout, source, adjacent)
                when {
                    layout.definition.outdoors -> removeExactLoc(source)
                    !inBounds -> replaceRoomBoundary(source, runtime.blueprint.style.wallLocId)
                    layout.placement.level == 0 && adjacent == null ->
                        replaceRoomBoundary(source, runtime.blueprint.style.wallLocId)
                    layout.placement.level > 1 && adjacent == null ->
                        replaceRoomBoundary(source, runtime.blueprint.style.wallLocId)
                    adjacent != null && !matchingDoorway ->
                        replaceRoomBoundary(source, runtime.blueprint.style.wallLocId)
                    adjacent != null && !adjacentOutdoors -> removeExactLoc(source)
                    runtime.blueprint.options.doors == HouseDoorsMode.NONE -> removeExactLoc(source)
                    else -> placeHouseStyleDoor(runtime, source, roomDoors)
                }
            }
        }
    }

    private fun replaceRoomBoundary(source: LocInfo, replacementId: Int) {
        replaceLocId(source, roomDoorIds, replacementId)
    }

    private fun placeHouseStyleDoor(
        runtime: HouseRuntime,
        source: LocInfo,
        roomDoors: List<LocInfo>,
    ) {
        val rightLeafCoords = constructionDoorRightLeafCoords(source.coords, source.angle)
        val leftLeaf =
            roomDoors.any { candidate ->
                candidate !== source &&
                    candidate.coords == rightLeafCoords &&
                    candidate.layer == source.layer &&
                    candidate.shape == source.shape &&
                    candidate.angle == source.angle
            }
        val closedDoorId =
            if (leftLeaf) runtime.blueprint.style.doorLocId2 else runtime.blueprint.style.doorLocId1
        val openedDoorId =
            if (leftLeaf) runtime.blueprint.style.openedDoorLocId2
            else runtime.blueprint.style.openedDoorLocId1
        if (!locs.del(source, Int.MAX_VALUE)) return
        val closed =
            LocInfo(
                source.layer,
                source.coords,
                LocEntity(closedDoorId, source.shape.id, source.angle.id),
            )
        val open = openedConstructionDoor(closed, openedDoorId, leftLeaf)
        val startsOpen = runtime.blueprint.options.doors == HouseDoorsMode.OPEN
        val initial = if (startsOpen) open else closed
        if (!locs.add(initial, Int.MAX_VALUE)) {
            locs.add(source, Int.MAX_VALUE)
            return
        }
        runtime.houseDoors += HouseDoorState(closed, open, leftLeaf, startsOpen)
    }

    private fun openedConstructionDoor(
        closed: LocInfo,
        openedDoorId: Int,
        leftLeaf: Boolean,
    ): LocInfo {
        val placement = openedConstructionDoorPlacement(closed.coords, closed.angle, leftLeaf)
        return LocInfo(
            closed.layer,
            placement.coords,
            LocEntity(openedDoorId, closed.shape.id, placement.angle.id),
        )
    }

    fun toggleHouseDoor(access: ProtectedAccess, clicked: BoundLocInfo) {
        val runtime = runtimeFor(access.player) ?: return
        if (runtime.buildMode) return
        val state =
            runtime.houseDoors.firstOrNull { door ->
                val current = if (door.open) door.opened else door.closed
                current.id == clicked.id &&
                    current.coords == clicked.coords &&
                    current.layer == clicked.layer
            } ?: return
        val targetOpen = !state.open
        val leaves = buildList {
            add(state)
            runtime.houseDoors
                .firstOrNull { candidate -> candidate.isOtherHalfOf(state) }
                ?.let(::add)
        }
        val current = leaves.map { door -> if (door.open) door.opened else door.closed }
        val replacement = leaves.map { door -> if (targetOpen) door.opened else door.closed }
        val removed = mutableListOf<LocInfo>()
        for (loc in current) {
            if (!locs.del(loc, Int.MAX_VALUE)) {
                removed.forEach { locs.add(it, Int.MAX_VALUE) }
                return
            }
            removed += loc
        }
        val added = mutableListOf<LocInfo>()
        for (loc in replacement) {
            if (!locs.add(loc, Int.MAX_VALUE)) {
                added.forEach { locs.del(it, Int.MAX_VALUE) }
                current.forEach { locs.add(it, Int.MAX_VALUE) }
                return
            }
            added += loc
        }
        leaves.forEach { it.open = targetOpen }
        access.soundSynth(if (targetOpen) "synth.door_open" else "synth.door_close")
    }

    private fun HouseDoorState.isOtherHalfOf(other: HouseDoorState): Boolean {
        if (this === other || leftLeaf == other.leftLeaf) return false
        if (closed.layer != other.closed.layer || closed.angle != other.closed.angle) return false
        if (closed.coords.level != other.closed.coords.level) return false
        val distance =
            kotlin.math.abs(closed.coords.x - other.closed.coords.x) +
                kotlin.math.abs(closed.coords.z - other.closed.coords.z)
        return distance == 1
    }

    private fun hideUnbuiltHotspots(runtime: HouseRuntime, restoredFurniture: Set<String>) {
        runtime.byKey.values
            .filter { it.key !in restoredFurniture }
            .flatMap { it.locs }
            .forEach(::removeExactLoc)
        runtime.blueprint.rooms.forEach { placement ->
            val origin =
                CoordGrid(
                    runtime.base.x + placement.zoneX * HOUSE_ZONE_SIZE,
                    runtime.base.z + placement.zoneZ * HOUSE_ZONE_SIZE,
                    runtime.base.level + placement.level,
                )
            val boundaryLocs = buildList {
                for (x in (origin.x - 1)..(origin.x + HOUSE_ZONE_SIZE)) {
                    for (z in (origin.z - 1)..(origin.z + HOUSE_ZONE_SIZE)) {
                        addAll(locs.findAll(CoordGrid(x, z, origin.level)))
                    }
                }
            }
            boundaryLocs
                .filter { loc -> loc.id in hotspotByLocId || loc.id in roomDoorIds }
                .forEach(::removeExactLoc)
        }

        removeVisitorDoorHotspots(runtime)
    }

    /**
     * Template door hotspots can sit just outside a room's nominal 8x8 zone. After architectural
     * doors have been placed, sweep the whole live house footprint. Visitors must only see a
     * finished door or wall, never a Build hotspot.
     */
    private fun removeVisitorDoorHotspots(runtime: HouseRuntime) {
        if (runtime.buildMode || runtime.blueprint.rooms.isEmpty()) return
        val origins =
            runtime.blueprint.rooms.map { placement ->
                CoordGrid(
                    runtime.base.x + placement.zoneX * HOUSE_ZONE_SIZE,
                    runtime.base.z + placement.zoneZ * HOUSE_ZONE_SIZE,
                    runtime.base.level + placement.level,
                )
            }
        origins.groupBy(CoordGrid::level).forEach { (level, floorOrigins) ->
            val xRange =
                (floorOrigins.minOf(CoordGrid::x) - 2)..
                    (floorOrigins.maxOf(CoordGrid::x) + HOUSE_ZONE_SIZE + 1)
            val zRange =
                (floorOrigins.minOf(CoordGrid::z) - 2)..
                    (floorOrigins.maxOf(CoordGrid::z) + HOUSE_ZONE_SIZE + 1)
            for (x in xRange) {
                for (z in zRange) {
                    locs.findAll(CoordGrid(x, z, level))
                        .filter { it.id in roomDoorIds }
                        .toList()
                        .forEach(::removeExactLoc)
                }
            }
        }
    }

    /**
     * Revision 240 room templates contain loc 13830 as a neutral, translucent window marker.
     * Jagex's POH renderer replaces that marker after the room layout is known. Leaving it in the
     * copied instance is what made finished visit-mode rooms show ghost/invisible wall panels.
     *
     * Chapel window hotspots follow the same architectural rule: keep a real built window on an
     * exterior wall, keep the hotspot only while building, otherwise use the house-style window. A
     * window position shared by two indoor rooms is always rendered as the house-style wall.
     */
    private fun renderArchitecturalWindows(runtime: HouseRuntime, restoredFurniture: Set<String>) {
        runtime.blueprint.rooms.forEach { placement ->
            val room = rooms[placement.room] ?: return@forEach
            val origin =
                CoordGrid(
                    runtime.base.x + placement.zoneX * HOUSE_ZONE_SIZE,
                    runtime.base.z + placement.zoneZ * HOUSE_ZONE_SIZE,
                    runtime.base.level + placement.level,
                )
            val roomLocs = locs.findAll(ZoneKey.from(origin)).toList()
            roomLocs
                .filter { it.id == HOUSE_WINDOW_TEMPLATE_LOC }
                .forEach { window ->
                    val targetId =
                        architecturalWindowTarget(runtime, placement, origin, window.coords)
                    replaceLocId(window, setOf(HOUSE_WINDOW_TEMPLATE_LOC), targetId)
                }

            if (room.name != "CHAPEL") return@forEach
            val target = runtime.byKey[furnitureKey(placement, "WINDOW")] ?: return@forEach
            val hasBuiltWindow = target.key in restoredFurniture
            target.locs.forEach { window ->
                val targetId = architecturalWindowTarget(runtime, placement, origin, window.coords)
                val exterior = targetId == runtime.blueprint.style.windowLocId
                if (runtime.buildMode && exterior && !hasBuiltWindow) return@forEach
                if (exterior && hasBuiltWindow) return@forEach
                replaceLocId(window, target.hotspot.locIds.toSet() + builtIds, targetId)
            }
        }
    }

    private fun architecturalWindowTarget(
        runtime: HouseRuntime,
        placement: HouseRoom,
        roomOrigin: CoordGrid,
        window: CoordGrid,
    ): Int {
        val destination = roomDoorDestination(placement, roomOrigin, window)
        val adjacent =
            destination?.let { target ->
                runtime.blueprint.rooms.firstOrNull {
                    it.zoneX == target.x && it.zoneZ == target.z && it.level == target.level
                }
            }
        val adjacentOutdoors = adjacent?.let { rooms[it.room]?.outdoors ?: false }
        return runtime.blueprint.style.architecturalWindowLocId(
            level = placement.level,
            adjacentRoomOutdoors = adjacentOutdoors,
        )
    }

    private fun replaceLocId(source: LocInfo, currentIds: Set<Int>, replacementId: Int): Boolean {
        val current =
            locs.findAll(source.coords).firstOrNull {
                it.layer == source.layer && it.id in currentIds
            } ?: return false
        if (current.id == replacementId) return true
        if (!locs.del(current, Int.MAX_VALUE)) return false
        val replacement =
            LocInfo(
                current.layer,
                current.coords,
                LocEntity(replacementId, current.shape.id, current.angle.id),
            )
        if (locs.add(replacement, Int.MAX_VALUE)) return true
        locs.add(current, Int.MAX_VALUE)
        return false
    }

    private fun removeExactLoc(loc: LocInfo) {
        val current =
            locs.findAll(loc.coords).firstOrNull {
                it.id == loc.id && it.layer == loc.layer && it.coords == loc.coords
            } ?: return
        locs.del(current, Int.MAX_VALUE)
    }

    private fun applySavedFurniture(runtime: HouseRuntime): Set<String> {
        val restored = mutableSetOf<String>()
        runtime.blueprint.furniture.forEach { (key, buildableName) ->
            val target = runtime.byKey[key] ?: return@forEach
            val buildable = buildables[buildableName] ?: return@forEach
            if (replace(target, buildable)) restored += key
        }
        return restored
    }

    /**
     * Swaps the hotspot (or an existing upgrade) for the selected furniture. Dynamic locs are keyed
     * by tile and layer, so the old object must be removed before the new one is placed. This also
     * keeps clipping synchronized with the object players can actually see.
     */
    private fun replace(target: HouseTarget, buildable: DonorBuildable): Boolean {
        val removed = mutableListOf<LocInfo>()
        val added = mutableListOf<LocInfo>()
        for (loc in target.locs) {
            val objectId = builtLocId(target, buildable, loc)
            val current =
                locs.findAll(loc.coords).firstOrNull { candidate ->
                    candidate.layer == loc.layer &&
                        (candidate.id in target.hotspot.locIds || candidate.id in builtIds)
                }
            if (current == null || !locs.del(current, Int.MAX_VALUE)) {
                rollbackLocSwap(removed, added)
                return false
            }
            removed += current
            // -1 is an intentional empty piece in the POH layout (for example the
            // rug sections hidden by stairs), not object ID 1 and not a no-op.
            if (objectId < 0) continue
            val replacement =
                LocInfo(
                    current.layer,
                    current.coords,
                    LocEntity(objectId, current.shape.id, current.angle.id),
                )
            if (!locs.add(replacement, Int.MAX_VALUE)) {
                rollbackLocSwap(removed, added)
                return false
            }
            added += replacement
        }
        return removed.isNotEmpty()
    }

    private fun restoreHotspot(target: HouseTarget, buildable: DonorBuildable): Boolean {
        val removed = mutableListOf<LocInfo>()
        val added = mutableListOf<LocInfo>()
        for (hotspot in target.locs) {
            val builtId = builtLocId(target, buildable, hotspot)
            if (builtId >= 0) {
                val current =
                    locs.findAll(hotspot.coords).firstOrNull { candidate ->
                        candidate.layer == hotspot.layer && candidate.id == builtId
                    }
                if (current == null || !locs.del(current, Int.MAX_VALUE)) {
                    rollbackLocSwap(removed, added)
                    return false
                }
                removed += current
            }
            if (!locs.add(hotspot, Int.MAX_VALUE)) {
                rollbackLocSwap(removed, added)
                return false
            }
            added += hotspot
        }
        return added.isNotEmpty()
    }

    private fun rollbackLocSwap(removed: List<LocInfo>, added: List<LocInfo>) {
        added.asReversed().forEach { locs.del(it, Int.MAX_VALUE) }
        removed.forEach { locs.add(it, Int.MAX_VALUE) }
    }

    private companion object {
        val log = LoggerFactory.getLogger(ConstructionHouseService::class.java)
    }

    private fun roomPlacementProblem(
        blueprint: HouseBlueprint,
        room: DonorRoom,
        x: Int,
        z: Int,
        level: Int,
        constructionLevel: Int,
    ): String? {
        if (room.basement && level != 0) return "That room can only be built in the basement."
        if (!room.basement && level == 0) return "That room cannot be built in the basement."
        if (room.outdoors && level != 1)
            return "Outdoor rooms can only be built on the ground floor."
        if (level == 2) {
            val below =
                blueprint.rooms.firstOrNull { it.zoneX == x && it.zoneZ == z && it.level == 1 }
            if (below == null || rooms[below.room]?.outdoors == true)
                return "An upstairs room needs an indoor room below it."
        }
        if (blueprint.rooms.size >= maxRooms(constructionLevel))
            return "You have reached the room limit for your Construction level."
        if (room.name == "COSTUME_ROOM" && blueprint.rooms.any { it.room == "COSTUME_ROOM" })
            return "You may only have one costume room."
        if (
            room.name in setOf("MENAGERIE_INDOORS", "MENAGERIE_OUTDOORS") &&
                blueprint.rooms.any { it.room in setOf("MENAGERIE_INDOORS", "MENAGERIE_OUTDOORS") }
        )
            return "You may only have one menagerie."
        return null
    }

    private fun maxRooms(level: Int): Int =
        when {
            level >= 99 -> 33
            level >= 96 -> 32
            level >= 50 -> 24 + ((level - 50) / 6)
            else -> 23
        }

    private fun canBuild(
        player: Player,
        buildable: DonorBuildable,
        target: HouseTarget? = null,
    ): Boolean = buildProblem(player, buildable, target) == null

    private fun buildProblem(
        player: Player,
        buildable: DonorBuildable,
        target: HouseTarget? = null,
    ): ConstructionBuildProblem? =
        ConstructionBuildRequirements.firstProblem(
            buildable = buildable,
            constructionLevel = player.constructionLvl,
            hasHammer =
                inventoryCount(player, HAMMER) > 0 || inventoryCount(player, IMCANDO_HAMMER) > 0,
            hasSaw = inventoryCount(player, SAW) > 0 || inventoryCount(player, CRYSTAL_SAW) > 0,
            hasCrystalSaw = inventoryCount(player, CRYSTAL_SAW) > 0,
            currentBuildable =
                target?.let { active[player.houseKey()]?.blueprint?.furniture?.get(it.key) },
            itemCount = { itemId -> inventoryCount(player, itemId) },
        )

    private fun buildProblemMessage(problem: ConstructionBuildProblem): String =
        when (problem) {
            is ConstructionBuildProblem.Level ->
                "You need a Construction level of ${problem.required} to build that."
            ConstructionBuildProblem.Hammer -> "You need a hammer to build that."
            ConstructionBuildProblem.Saw -> "You need a saw to build that."
            is ConstructionBuildProblem.Material -> {
                val itemName =
                    ServerCacheManager.getItem(problem.itemId)?.name ?: "item ${problem.itemId}"
                "You need ${problem.required} x $itemName to build that; you have ${problem.available}."
            }
            is ConstructionBuildProblem.Upgrade -> {
                val required =
                    problem.requiredBuildable.lowercase().replace('_', ' ').replaceFirstChar {
                        it.uppercase()
                    }
                "You must build the $required first."
            }
        }

    private fun takeMaterials(player: Player, buildable: DonorBuildable): Boolean {
        val result =
            player.invTransaction(player.inv) {
                val inventory = select(player.inv)
                ConstructionBuildRequirements.materialTotals(buildable).forEach { (itemId, amount)
                    ->
                    val internal = RSCM.getReverseMapping(RSCMType.OBJ, itemId).asRSCM(RSCMType.OBJ)
                    delete(inventory, internal, amount, strict = true)
                }
            }
        if (!result.success) {
            log.warn(
                "[Construction] atomic material removal failed player={} buildable={}",
                player.houseKey(),
                buildable.name,
            )
        }
        return result.success
    }

    private fun restoreMaterials(player: Player, buildable: DonorBuildable) {
        buildable.requirements
            .filterIsInstance<DonorRequirement.ITEM>()
            .groupBy { it.itemId }
            .forEach { (itemId, entries) ->
                player.invAdd(
                    player.inv,
                    RSCM.getReverseMapping(RSCMType.OBJ, itemId),
                    entries.sumOf { it.amount },
                    strict = false,
                )
            }
    }

    private fun inventoryCount(player: Player, itemId: Int): Int =
        runCatching { player.inv.count(RSCM.getReverseMapping(RSCMType.OBJ, itemId)) }
            .getOrDefault(0)

    private suspend fun constructionAnim(access: ProtectedAccess, buildable: DonorBuildable) {
        val animation =
            when (buildable.animation) {
                "MID_BUILD" -> 3676
                "LOW_BUILD" -> 3683
                "HIGH_BUILD" -> 3684
                "GROUND_PLANT" -> 827
                else -> -1
            }
        if (animation >= 0) {
            val internal = RSCM.getReverseMapping(RSCMType.SEQ, animation)
            access.anim(internal)
            access.delay(2)
        }
    }

    private fun menuText(choice: ConstructionFurnitureMenuEntry): String {
        val buildable = choice.buildable
        val displayName = choice.displayName
        val materials = buildable.requirements.filterIsInstance<DonorRequirement.ITEM>()
        val parts = buildString {
            repeat(4) { index ->
                val requirement = materials.getOrNull(index)
                if (requirement != null) {
                    val itemName =
                        if (requirement.itemId == 4819) {
                            "Nails"
                        } else {
                            ServerCacheManager.getItem(requirement.itemId)?.name
                                ?: "Item ${requirement.itemId}"
                        }
                    append("$itemName: ${"%,d".format(requirement.amount)}")
                }
                append("<br>")
            }
        }
        return "$displayName|$parts"
    }

    private fun updateOptions(player: Player, update: HouseOptions.() -> HouseOptions) {
        val blueprint = player.loadHouseBlueprint()
        player.saveHouseBlueprint(blueprint.copy(options = blueprint.options.update()))
    }

    private fun setVarbit(player: Player, id: Int, value: Int) {
        val type =
            requireNotNull(ServerCacheManager.getVarbit(id)) {
                "Missing revision-240 POH varbit $id"
            }
        VarPlayerIntMapSetter.set(player, type, value)
    }

    private fun ownedRuntime(player: Player): HouseRuntime? {
        val runtime = runtimeFor(player) ?: return null
        return runtime.takeIf {
            it.session.owner == player.uuid &&
                instances.sessionForPlayer(player)?.id == it.session.id
        }
    }

    private fun rebuildOwnerHouse(
        player: Player,
        runtime: HouseRuntime,
        blueprint: HouseBlueprint,
        buildMode: Boolean,
    ): Boolean {
        val key = player.houseKey()
        val returnCoord = returnCoords[key] ?: runtime.session.placement.exitCoord
        val oldOffset =
            Translation(
                x = player.coords.x - runtime.base.x,
                z = player.coords.z - runtime.base.z,
                level = player.coords.level - runtime.base.level,
            )
        val newOffset = viewerRebuildOffset(runtime.blueprint, blueprint, oldOffset)
        pending.remove(key)
        pendingRooms.remove(key)
        viewerSessions.remove(key)
        player.ifClose(events)
        instances.leave(player, runtime.session, clock.cycle)
        if (
            enter(
                player,
                buildMode,
                blueprint,
                runtime.testMode,
                returnCoord,
                newOffset,
                runtime.auditMode,
            )
        ) {
            return true
        }

        player.mes("Your house change could not be loaded, so the previous layout was restored.")
        val restored =
            enter(
                player,
                runtime.buildMode,
                runtime.blueprint,
                runtime.testMode,
                returnCoord,
                oldOffset,
                runtime.auditMode,
            )
        if (!restored && instances.sessionForPlayer(player) == null) {
            PathingEntityCommon.telejump(player, collision, returnCoord)
        }
        return false
    }

    private fun viewerSession(player: Player): ViewerEditSession? {
        val key = player.houseKey()
        val edit = viewerSessions[key] ?: return null
        val runtime = ownedRuntime(player)
        if (runtime == null || !runtime.buildMode || runtime.session.id != edit.instanceId) {
            viewerSessions.remove(key)
            return null
        }
        return edit
    }

    private fun clearViewerSelection(player: Player) {
        setVarbit(player, POH_VIEWER_SELECTED_VARBIT, 0)
        setVarbit(player, POH_VIEWER_DESTINATION_VARBIT, 0)
        setVarbit(player, POH_VIEWER_ROTATION_VARBIT, 0)
        setVarbit(player, POH_VIEWER_ALLOW_ROTATION_VARBIT, 0)
        setVarbit(player, POH_VIEWER_ROOM_ID_VARBIT, 0)
        setVarbit(player, POH_VIEWER_ADJACENT_DOORS_VARBIT, 0)
        setVarbit(player, POH_VIEWER_SELECTED_DOORS_VARBIT, 0)
    }

    private fun sendViewerRoom(
        player: Player,
        index: Int,
        placement: HouseRoom,
        blueprint: HouseBlueprint,
    ) {
        val definition = rooms[placement.room] ?: return
        val choices =
            definition.hotspots.take(9).map { hotspotName ->
                val hotspot = hotspots[hotspotName] ?: return@map 0
                val built = blueprint.furniture[furnitureKey(placement, hotspotName)]
                hotspot.buildables.indexOf(built).let { if (it < 0) 0 else it + 1 }
            }
        val packed = HouseViewerLayout.packFurnitureChoices(choices)
        packed[0] =
            packed[0] or
                placement.zoneX or
                (placement.zoneZ shl 3) or
                (placement.level shl 6) or
                (placement.rotation shl 8) or
                (definition.roomId shl 10)
        player.runClientScript(
            POH_VIEWER_ROOM_SCRIPT,
            index + 1,
            definition.viewerEnumId,
            11,
            packed[0],
            packed[1],
            packed[2],
        )
    }

    private fun packViewerBits(target: IntArray, bitOffset: Int, value: Int, bitCount: Int) {
        repeat(bitCount) { bit ->
            if ((value and (1 shl bit)) == 0) return@repeat
            val absolute = bitOffset + bit
            val word = absolute / Int.SIZE_BITS
            if (word in target.indices)
                target[word] = target[word] or (1 shl (absolute % Int.SIZE_BITS))
        }
    }

    private fun openViewerInterface(
        access: ProtectedAccess,
        session: ViewerEditSession,
        selectedRoomId: String? = session.selectedRoomId,
    ) {
        val placements = HouseViewerLayout.orderedRooms(session.working.rooms)
        if (placements.isEmpty()) return
        session.roomSlots.clear()
        session.roomSlots += placements.map(HouseRoom::stableId)
        clearViewerSelection(access.player)
        access.ifOpenMainModal("interface.poh_viewer")
        val min =
            HouseViewerLayout.packCoord(
                placements.minOf(HouseRoom::level),
                placements.minOf(HouseRoom::zoneX),
                placements.minOf(HouseRoom::zoneZ),
            )
        val max =
            HouseViewerLayout.packCoord(
                placements.maxOf(HouseRoom::level),
                placements.maxOf(HouseRoom::zoneX),
                placements.maxOf(HouseRoom::zoneZ),
            )
        val runtime = ownedRuntime(access.player)
        val startingFloor =
            selectedRoomId?.let { id -> placements.firstOrNull { it.stableId == id }?.level }
                ?: runtime?.let { (access.player.coords.level - it.base.level).coerceIn(0, 2) }
                ?: 1
        placements.forEachIndexed { index, placement ->
            sendViewerRoom(access.player, index, placement, session.working)
        }
        access.runClientScript(POH_VIEWER_SETUP_SCRIPT, placements.size, min, max, startingFloor)
        access.player.ifSetEvents(
            ServerCacheManager.fromComponent(
                (POH_VIEWER_INTERFACE shl 16) or POH_VIEWER_MAP_COMPONENT
            ),
            0 until POH_VIEWER_CELL_COUNT,
            IfEvent.Op1,
            IfEvent.Op6,
        )
        selectedRoomId
            ?.let { id -> placements.indexOfFirst { it.stableId == id } }
            ?.takeIf { it >= 0 }
            ?.let { index -> selectViewerRoom(access, index) }
    }

    private fun refreshViewerInterface(
        access: ProtectedAccess,
        session: ViewerEditSession,
        selectedRoomId: String? = session.selectedRoomId,
    ) {
        session.interfaceTransition = true
        openViewerInterface(access, session, selectedRoomId)
    }

    private fun viewerDoorMask(room: HouseRoom): Int = 0xF

    private fun viewerAdjacentDoorMask(proposed: List<HouseRoom>, selected: HouseRoom): Int {
        var mask = 0
        proposed.forEach { candidate ->
            if (candidate.level != selected.level) return@forEach
            when {
                candidate.zoneX == selected.zoneX && candidate.zoneZ == selected.zoneZ + 1 ->
                    mask = mask or 0x1
                candidate.zoneX == selected.zoneX + 1 && candidate.zoneZ == selected.zoneZ ->
                    mask = mask or 0x2
                candidate.zoneX == selected.zoneX && candidate.zoneZ == selected.zoneZ - 1 ->
                    mask = mask or 0x4
                candidate.zoneX == selected.zoneX - 1 && candidate.zoneZ == selected.zoneZ ->
                    mask = mask or 0x8
            }
        }
        return mask
    }

    private fun hasVerticalConnector(blueprint: HouseBlueprint, room: HouseRoom): Boolean {
        return blueprint.furniture.any { (key, value) ->
            key.startsWith("${room.stableId}:") &&
                (value.contains("STAIR", true) ||
                    value.contains("TRAPDOOR", true) ||
                    value.contains("LADDER", true))
        }
    }

    private fun viewerLinkedStack(blueprint: HouseBlueprint, selected: HouseRoom): Set<String> =
        HouseViewerLayout.linkedStack(blueprint.rooms, selected) {
            hasVerticalConnector(blueprint, it)
        }

    private fun hasUnsafeStoredContents(blueprint: HouseBlueprint, room: HouseRoom): Boolean {
        if (
            room.room !in
                setOf(
                    "COSTUME_ROOM",
                    "MENAGERIE_INDOORS",
                    "MENAGERIE_OUTDOORS",
                    "PORTAL_NEXUS_ROOM",
                )
        ) {
            return false
        }
        return blueprint.furniture.keys.any { it.startsWith("${room.stableId}:") }
    }

    private fun viewerRebuildOffset(
        previous: HouseBlueprint,
        updated: HouseBlueprint,
        oldOffset: Translation,
    ): Translation {
        val level = oldOffset.level
        val roomX = Math.floorDiv(oldOffset.x, HOUSE_ZONE_SIZE)
        val roomZ = Math.floorDiv(oldOffset.z, HOUSE_ZONE_SIZE)
        val oldRoom =
            previous.rooms.firstOrNull {
                it.zoneX == roomX && it.zoneZ == roomZ && it.level == level
            }
        val newRoom =
            oldRoom?.let { old -> updated.rooms.firstOrNull { it.stableId == old.stableId } }
        if (oldRoom != null && newRoom != null) {
            val localX = Math.floorMod(oldOffset.x, HOUSE_ZONE_SIZE)
            val localZ = Math.floorMod(oldOffset.z, HOUSE_ZONE_SIZE)
            val remapped =
                HouseViewerLayout.remapLocal(localX, localZ, oldRoom.rotation, newRoom.rotation)
            return Translation(
                newRoom.zoneX * HOUSE_ZONE_SIZE + remapped.first,
                newRoom.zoneZ * HOUSE_ZONE_SIZE + remapped.second,
                newRoom.level,
            )
        }
        val garden = updated.rooms.single { it.room == "GARDEN" && it.level == 1 }
        val local =
            HouseViewerLayout.rotateLocal(HOUSE_ENTER_LOCAL_X, HOUSE_ENTER_LOCAL_Z, garden.rotation)
        return Translation(
            garden.zoneX * HOUSE_ZONE_SIZE + local.first,
            garden.zoneZ * HOUSE_ZONE_SIZE + local.second,
            garden.level,
        )
    }

    private fun validateViewerRooms(
        proposed: List<HouseRoom>,
        furniture: Map<String, String>,
        constructionLevel: Int? = null,
    ): String? {
        if (proposed.size !in 1..38) return "That layout has an invalid number of rooms."
        if (constructionLevel != null && proposed.size > maxRooms(constructionLevel)) {
            return "You have reached the room limit for your Construction level."
        }
        if (proposed.map(HouseRoom::stableId).distinct().size != proposed.size) {
            return "The layout contains duplicate room identities."
        }
        if (proposed.distinctBy { Triple(it.zoneX, it.zoneZ, it.level) }.size != proposed.size) {
            return "Two rooms cannot occupy the same house space."
        }
        if (proposed.count { it.room == "COSTUME_ROOM" } > 1) {
            return "You may only have one costume room."
        }
        if (proposed.count { it.room in setOf("MENAGERIE_INDOORS", "MENAGERIE_OUTDOORS") } > 1) {
            return "You may only have one menagerie."
        }
        proposed.forEach { placement ->
            val definition = rooms[placement.room] ?: return "The layout contains an unknown room."
            if (placement.zoneX !in 0..7 || placement.zoneZ !in 0..7 || placement.level !in 0..2) {
                return "A room is outside the supported house grid."
            }
            if (placement.rotation !in 0..3) return "A room has an invalid rotation."
            if (definition.basement != (placement.level == 0)) {
                return "${definition.name.lowercase().replace('_', ' ')} is on an invalid floor."
            }
            if (definition.outdoors && placement.level != 1) {
                return "Outdoor rooms can only be placed on the ground floor."
            }
            if (placement.level == 2) {
                val below =
                    proposed.firstOrNull {
                        it.zoneX == placement.zoneX && it.zoneZ == placement.zoneZ && it.level == 1
                    }
                if (below == null || rooms[below.room]?.outdoors == true) {
                    return "An upstairs room needs an indoor room below it."
                }
            }
        }
        val garden =
            proposed.singleOrNull { it.room == "GARDEN" && it.level == 1 }
                ?: return "The house must contain exactly one ground-floor entrance garden."
        if (furniture[furnitureKey(garden, "CENTERPIECE")] != "EXIT_PORTAL") {
            return "The entrance garden must retain its exit portal."
        }
        val roomPrefixes = proposed.map { "${it.stableId}:" }
        if (furniture.keys.any { key -> roomPrefixes.none(key::startsWith) }) {
            return "The layout contains furniture that is not attached to a room."
        }
        val workingBlueprint = HouseBlueprint(proposed.toMutableList(), furniture.toMutableMap())
        val connected =
            HouseViewerLayout.connectedRoomIds(proposed) { room ->
                hasVerticalConnector(workingBlueprint, room)
            }
        if (connected.size != proposed.size) {
            return "Every room must remain connected to the entrance garden."
        }
        return null
    }

    private fun Player.houseKey(): Long =
        requireNotNull(uuid) { "A construction house requires an authenticated player UUID." }

    private fun runtimeFor(player: Player): HouseRuntime? {
        val playerId = player.houseKey()
        active[playerId]?.let {
            return it
        }
        val session = instances.sessionForPlayer(player) ?: return null
        return active[session.owner]?.takeIf { it.session.id == session.id }
    }

    data class HouseTarget(val key: String, val hotspot: DonorHotspot, val locs: List<LocInfo>) {
        val roomKey: String
            get() = key.substringBeforeLast(':')
    }

    private data class RoomLayoutSnapshot(
        val placement: HouseRoom,
        val definition: DonorRoom,
        val origin: CoordGrid,
        val locs: List<LocInfo>,
    )

    data class HouseRuntime(
        val session: InstanceSession,
        val blueprint: HouseBlueprint,
        var buildMode: Boolean,
        val testMode: Boolean = false,
        val auditMode: Boolean = false,
        val base: CoordGrid,
        val byKey: MutableMap<String, HouseTarget> = mutableMapOf(),
        val roomDoors: MutableList<RoomDoorTarget> = mutableListOf(),
        val houseDoors: MutableList<HouseDoorState> = mutableListOf(),
        val activeThroneTraps: MutableMap<String, List<LocInfo>> = mutableMapOf(),
    )

    data class RoomDoorTarget(val loc: LocInfo, val x: Int, val z: Int, val level: Int)

    data class HouseDoorState(
        val closed: LocInfo,
        val opened: LocInfo,
        val leftLeaf: Boolean,
        var open: Boolean,
    )

    private data class PendingBuild(
        val target: HouseTarget,
        val choices: List<ConstructionFurnitureMenuEntry>,
    )

    data class PendingRoom(val x: Int, val z: Int, val level: Int, val fromViewer: Boolean = false)

    private enum class ViewerMode {
        IDLE,
        MOVING,
    }

    private data class ViewerEditSession(
        val instanceId: InstanceId,
        val originalRevision: Long,
        val working: HouseBlueprint,
        val roomSlots: MutableList<String> = mutableListOf(),
        var selectedRoomId: String? = null,
        var mode: ViewerMode = ViewerMode.IDLE,
        val addedRoomCosts: MutableMap<String, Int> = mutableMapOf(),
        var interfaceTransition: Boolean = false,
    )
}
