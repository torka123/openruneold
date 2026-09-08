package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.types.ObjectServerType
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.instances.events.InstanceEndedEvent
import org.rsmod.api.instances.events.InstancePlayerLeaveEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.IfModalButton
import org.rsmod.api.player.ui.IfModalPauseButton
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpLoc5
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.loc.LocLayerConstants

internal const val EDGEVILLE_HOUSE_PORTAL_ID = 15477
internal val EDGEVILLE_HOUSE_PORTAL_COORDS = CoordGrid(3087, 3494, 0)

internal class ConstructionWorld
@Inject
constructor(private val houses: ConstructionHouseService, private val locs: LocRepository) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onProtectedEvent<IfModalButton>(FURNITURE_INTERFACE shl 16 or FURNITURE_LIST_COMPONENT) {
            houses.buildSelected(this, it.comsub)
        }
        onProtectedEvent<IfModalPauseButton>(
            FURNITURE_INTERFACE shl 16 or FURNITURE_LIST_COMPONENT
        ) {
            houses.buildSelected(this, it.comsub)
        }
        onProtectedEvent<IfModalButton>(ROOM_INTERFACE shl 16 or ROOM_LIST_COMPONENT) {
            houses.buildRoomSelected(this, it.comsub)
        }
        onProtectedEvent<IfModalPauseButton>(ROOM_INTERFACE shl 16 or ROOM_LIST_COMPONENT) {
            houses.buildRoomSelected(this, it.comsub)
        }
        onIfClose("interface.poh_furniture_creation") { houses.closeFurnitureMenu(player) }
        onIfClose("interface.poh_add_room") { houses.closeRoomSelection(player) }
        onEvent<InstancePlayerLeaveEvent>(instanceEventId(HOUSE_INSTANCE_KEY)) {
            houses.onHousePlayerLeave(player, instanceId, ownerId, isOwner)
        }
        onEvent<InstanceEndedEvent>(instanceEventId(HOUSE_INSTANCE_KEY)) {
            houses.onHouseEnded(instanceId, ownerId)
        }

        houses.registeredHotspotIds().forEach { id ->
            val type = ServerCacheManager.getObject(id) ?: return@forEach
            val buildOp =
                (0 until 5).firstOrNull {
                    type.actions.getOpOrNull(it).equals("Build", ignoreCase = true)
                }
            registerLocAction(type, buildOp) { loc -> houses.handleHouseLoc(this, loc) }
        }
        houses.registeredRoomDoorIds().forEach { id ->
            val type =
                requireNotNull(ServerCacheManager.getObject(id)) {
                    "Missing revision-240 POH door hotspot object $id"
                }
            val buildOp =
                (0 until 5).firstOrNull {
                    type.actions.getOpOrNull(it).equals("Build", ignoreCase = true)
                }
            checkNotNull(buildOp) {
                "POH door hotspot $id has no Build interaction: ${type.actions}"
            }
            registerLocAction(type, buildOp) { loc -> houses.openRoomFromDoor(this, loc) }
        }
        houses.registeredHouseStyleDoorIds().forEach { id ->
            val type =
                requireNotNull(ServerCacheManager.getObject(id)) {
                    "Missing revision-240 POH house-style door object $id"
                }
            val toggleOp =
                (0 until 5).firstOrNull {
                    type.actions.getOpOrNull(it).equals("Open", true) ||
                        type.actions.getOpOrNull(it).equals("Close", true)
                }
            checkNotNull(toggleOp) { "POH door $id has no Open/Close interaction: ${type.actions}" }
            registerLocAction(type, toggleOp) { loc -> houses.toggleHouseDoor(this, loc) }
        }
        houses.registeredBuiltIds().forEach { id ->
            val type = ServerCacheManager.getObject(id) ?: return@forEach
            val upgradeOp =
                (0 until 5).firstOrNull {
                    type.actions.getOpOrNull(it).equals("Upgrade", ignoreCase = true) ||
                        type.actions.getOpOrNull(it).equals("Build", ignoreCase = true)
                }
            val removeOp =
                (0 until 5).firstOrNull {
                    type.actions.getOpOrNull(it).equals("Remove", ignoreCase = true)
                }
            registerLocAction(type, upgradeOp) { loc -> houses.openFurniture(this, loc) }
            registerLocAction(type, removeOp) { loc -> houses.removeFurniture(this, loc) }
        }
        houses.registeredExitPortalIds().forEach { id ->
            val type =
                requireNotNull(ServerCacheManager.getObject(id)) {
                    "Missing revision-240 inside-house exit portal object $id"
                }
            val enterOp =
                (0 until 5).firstOrNull {
                    type.actions.getOpOrNull(it).equals("Enter", ignoreCase = true)
                }
            checkNotNull(enterOp) {
                "Inside-house exit portal $id has no Enter interaction: ${type.actions}"
            }
            registerLocAction(type, enterOp) { houses.leave(this) }
        }
        houses.registeredStaircaseIds().forEach { id ->
            val type = ServerCacheManager.getObject(id) ?: return@forEach
            (0 until 5).forEach { op ->
                val direction =
                    when (type.actions.getOpOrNull(op)?.lowercase()) {
                        "climb-up" -> 1
                        "climb-down" -> -1
                        "climb" -> null
                        else -> return@forEach
                    }
                registerLocAction(type, op) { loc -> houses.climbStaircase(this, loc, direction) }
            }
        }
        ThroneRoomTrapLayout.requiredLocIds.forEach { id ->
            val type =
                requireNotNull(ServerCacheManager.getObject(id)) {
                    "Missing revision-240 throne-room trap object $id"
                }
            if (id in ThroneRoomTrapLayout.cageLocIds) {
                type.blockWalk = 1
                type.blockRange = true
            }
        }
        houses.registeredThroneLeverIds().forEach { id ->
            val type = requireNotNull(ServerCacheManager.getObject(id))
            val pullOp =
                (0 until 5).firstOrNull {
                    type.actions.getOpOrNull(it).equals("Pull", ignoreCase = true)
                }
            registerLocAction(type, pullOp) { loc -> houses.pullThroneLever(this, loc) }
        }

        val edgePortal =
            requireNotNull(ServerCacheManager.getObject(EDGEVILLE_HOUSE_PORTAL_ID)) {
                "Missing Edgeville house portal object $EDGEVILLE_HOUSE_PORTAL_ID"
            }
        spawnEdgevillePortal(edgePortal)
        HouseLocation.entries
            .mapTo(mutableSetOf()) { it.portalId }
            .forEach { id ->
                val portal = ServerCacheManager.getObject(id) ?: return@forEach
                onOpLoc1(portal) { openHousePortalMenu(it.loc) }
                onOpLoc2(portal) { enterFromPortal(it.loc, buildMode = null) }
                onOpLoc3(portal) { enterFromPortal(it.loc, buildMode = true) }
                onOpLoc4(portal) { visitFriendHouse() }
            }

        onCommand("house") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Enter or manage your player-owned house"
            invalidArgs = "Use ::house [build|visit|exit|room <x> <z> <floor>|allrooms|reset]"
            cheat {
                when (args.firstOrNull()?.lowercase()) {
                    null -> houses.enter(player, true)
                    "visit" -> houses.enter(player, false)
                    "exit" -> houses.emergencyExit(player)
                    "build",
                    "reset" -> houses.reset(player)
                    "allrooms" -> houses.populateAllRooms(player)
                    "room" -> {
                        val x = args.getOrNull(1)?.toIntOrNull()
                        val z = args.getOrNull(2)?.toIntOrNull()
                        val level = args.getOrNull(3)?.toIntOrNull()
                        if (x == null || z == null || level == null) {
                            player.mes("Use ::house room <x> <z> <floor> while in building mode.")
                        } else {
                            houses.openRoomSelection(player, x, z, level)
                        }
                    }
                    else ->
                        player.mes(
                            "Use ::house, ::house visit, ::house room <x> <z> <floor>, " +
                                "::house allrooms, or ::house reset."
                        )
                }
            }
        }
        onCommand("housetest") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Enter a disposable Construction test house with no requirements"
            cheat { houses.enterTest(player) }
        }
        onCommand("housetest2") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Open the complete non-persistent POH buildable interaction audit house"
            invalidArgs = "Use ::housetest2 [rebuild|next|previous|room|item|report|resume|exit]"
            cheat { houses.houseTest2(player, args) }
        }
    }

    private fun spawnEdgevillePortal(type: ObjectServerType) {
        val shape = LocShape.CentrepieceStraight
        val angle = LocAngle.North
        val layer = LocLayerConstants.of(shape.id)
        val existing = locs.findAll(EDGEVILLE_HOUSE_PORTAL_COORDS).firstOrNull { it.layer == layer }
        if (existing != null) {
            check(existing.id == type.id) {
                "Cannot place the Edgeville house portal: centrepiece ${existing.id} already " +
                    "occupies $EDGEVILLE_HOUSE_PORTAL_COORDS"
            }
            return
        }
        val portal =
            LocInfo(
                layer = layer,
                coords = EDGEVILLE_HOUSE_PORTAL_COORDS,
                entity = LocEntity(type.id, shape.id, angle.id),
            )
        check(locs.add(portal, Int.MAX_VALUE)) {
            "Unable to spawn the Edgeville house portal at $EDGEVILLE_HOUSE_PORTAL_COORDS"
        }
    }

    private suspend fun ProtectedAccess.openHousePortalMenu(loc: BoundLocInfo) {
        when (
            choice4(
                "Enter your house.",
                1,
                "Enter your house in building mode.",
                2,
                "Enter a friend's house.",
                3,
                "Never mind.",
                4,
                title = "House portal",
            )
        ) {
            1 -> enterFromPortal(loc, buildMode = null)
            2 -> enterFromPortal(loc, buildMode = true)
            3 -> visitFriendHouse()
        }
    }

    private suspend fun ProtectedAccess.visitFriendHouse() {
        val name = stringDialog("Enter the house owner's name:").trim()
        if (name.isEmpty()) {
            player.mes("No player name entered.")
            return
        }
        houses.visit(player, name)
    }

    private fun ProtectedAccess.enterFromPortal(loc: BoundLocInfo, buildMode: Boolean?) {
        if (!player.hasConstructionHouse()) {
            player.mes("You need to purchase a house from an Estate agent first.")
            return
        }
        val isEdgevillePortal =
            loc.id == EDGEVILLE_HOUSE_PORTAL_ID && loc.coords == EDGEVILLE_HOUSE_PORTAL_COORDS
        val selected = player.loadHouseBlueprint().location
        if (!isEdgevillePortal && HouseLocation.fromPortalId(loc.id) != selected) {
            player.mes(
                "Your house is in ${selected.displayName}. Speak to an Estate agent to move it."
            )
            return
        }
        houses.enter(player, buildMode ?: houses.defaultBuildMode(player))
    }

    private fun ScriptContext.registerLocAction(
        type: ObjectServerType,
        op: Int?,
        action: suspend ProtectedAccess.(BoundLocInfo) -> Unit,
    ) {
        when (op) {
            0 -> onOpLoc1(type) { action(this, it.loc) }
            1 -> onOpLoc2(type) { action(this, it.loc) }
            2 -> onOpLoc3(type) { action(this, it.loc) }
            3 -> onOpLoc4(type) { action(this, it.loc) }
            4 -> onOpLoc5(type) { action(this, it.loc) }
        }
    }

    private companion object {
        const val FURNITURE_INTERFACE = 458
        const val FURNITURE_LIST_COMPONENT = 2
        const val ROOM_INTERFACE = 212
        const val ROOM_LIST_COMPONENT = 4
    }
}
