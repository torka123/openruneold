package org.rsmod.content.areas.wilderness.interactions

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCM.getReverseMapping
import dev.openrune.rscm.RSCMType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.events.interact.LocCoordEvents
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.content.generic.locs.doors.DoorTranslations
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.game.obj.Obj
import org.rsmod.game.obj.ObjEntity
import org.rsmod.game.obj.ObjScope
import org.rsmod.game.type.getInvObj
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.loc.LocLayerConstants

public data class WildernessLeverRoute(
    public val locId: Int,
    public val source: CoordGrid,
    public val destination: CoordGrid,
    public val reverseLocId: Int?,
    public val directionality: String,
)

public object WildernessLeverRouteTable {
    public val routes: List<WildernessLeverRoute> =
        listOf(
            WildernessLeverRoute(
                5959,
                CoordGrid(3090, 3956),
                CoordGrid(2540, 4712),
                5960,
                "BIDIRECTIONAL",
            ),
            WildernessLeverRoute(
                5960,
                CoordGrid(2539, 4712),
                CoordGrid(3090, 3957),
                5959,
                "BIDIRECTIONAL",
            ),
            WildernessLeverRoute(
                9706,
                CoordGrid(3104, 3956),
                CoordGrid(3105, 3951),
                9707,
                "BIDIRECTIONAL",
            ),
            WildernessLeverRoute(
                9707,
                CoordGrid(3105, 3952),
                CoordGrid(3105, 3957),
                9706,
                "BIDIRECTIONAL",
            ),
            WildernessLeverRoute(
                1815,
                CoordGrid(3153, 3923),
                CoordGrid(2561, 3311),
                null,
                "INTENTIONAL_ONE_WAY",
            ),
            WildernessLeverRoute(
                1816,
                CoordGrid(3067, 10253),
                CoordGrid(2273, 4680),
                1817,
                "BIDIRECTIONAL",
            ),
            WildernessLeverRoute(
                1817,
                CoordGrid(2271, 4680),
                CoordGrid(3067, 10254),
                1816,
                "BIDIRECTIONAL",
            ),
        )
}

/**
 * Exact-map stateful Wilderness access.
 *
 * Doors with real open variants are configured through the shared door/picket-gate engine in
 * `wilderness_interaction_doors.toml`. Objects that have no revision-240 open variant are removed
 * temporarily here, which updates the shared loc registry and collision for every nearby player.
 */
public class WildernessStatefulAccess
@Inject
constructor(
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
) : PluginScript() {
    private val logger = InlineLogger()

    override fun ScriptContext.startup() {
        ensureLavaMazeWebKnife()
        registerLevers()
        registerWebs()
        registerMetalGates()
        registerLockedDoors()
    }

    private fun ensureLavaMazeWebKnife() {
        val knifeId = WEB_KNIFE_OBJ.asRSCM(RSCMType.OBJ)
        val alreadySpawned =
            objRepo.findAll(LAVA_MAZE_WEB_KNIFE_SPAWN).any {
                it.type == knifeId && it.scope == ObjScope.Perm
            }
        if (alreadySpawned) return
        val knife =
            Obj(
                coords = LAVA_MAZE_WEB_KNIFE_SPAWN,
                entity = ObjEntity(knifeId, count = 1, scope = ObjScope.Perm.id),
                creationCycle = 0,
                receiverId = Obj.NULL_OBSERVER_ID,
            )
        check(objRepo.add(knife, duration = Int.MAX_VALUE)) {
            "Unable to register the permanent Lava Maze web knife at $LAVA_MAZE_WEB_KNIFE_SPAWN"
        }
    }

    private fun ScriptContext.registerLevers() {
        for (route in WildernessLeverRouteTable.routes) {
            validateOption(route.locId, 1)
            onProtectedEvent<LocCoordEvents.Op1>(LocCoordEvents.key(route.locId, route.source)) {
                useLever(route)
            }
        }
    }

    private fun ScriptContext.registerWebs() {
        validateOption(WEB, 1)
        for (source in WEB_SOURCES) {
            onProtectedEvent<LocCoordEvents.Op1>(LocCoordEvents.key(WEB, source)) {
                slashWeb(it.loc)
            }
        }
    }

    private fun ScriptContext.registerMetalGates() {
        validateMetalGateDefinitions()
        for (pair in WILDERNESS_METAL_GATE_PAIRS) {
            for (state in pair.halves) {
                onProtectedEvent<LocCoordEvents.Op1>(
                    LocCoordEvents.key(state.closed.objectId, state.closed.coords)
                ) {
                    setMetalGateState(pair, opening = true)
                }
                onProtectedEvent<LocCoordEvents.Op1>(
                    LocCoordEvents.key(state.opened.objectId, state.opened.coords)
                ) {
                    setMetalGateState(pair, opening = false)
                }
            }
        }
    }

    private fun ScriptContext.registerLockedDoors() {
        validateLockedDoorDefinitions()
        for (definition in LOCKED_DOORS) {
            val closedKey = LocCoordEvents.key(definition.closed.objectId, definition.closed.coords)
            onProtectedEvent<LocCoordEvents.Op1>(closedKey) { pickLockedDoor(definition, it.loc) }
            onProtectedEvent<LocCoordEvents.Op2>(closedKey) { pickLockedDoor(definition, it.loc) }

            val openedKey = LocCoordEvents.key(definition.opened.objectId, definition.opened.coords)
            onProtectedEvent<LocCoordEvents.Op1>(openedKey) { closeLockedDoor(definition, it.loc) }
        }
    }

    private suspend fun ProtectedAccess.useLever(route: WildernessLeverRoute) {
        arriveDelay()
        anim(2140)
        soundSynth("synth.lever")
        delay(1)
        val destination = safeDestination(route.destination)
        if (destination == null) {
            mes("The lever has no safe destination.")
            return
        }
        telejump(destination, TeleportType.Exempt)
        if (coords == destination) mes("You pull the lever and are teleported away.")
    }

    private suspend fun ProtectedAccess.slashWeb(web: BoundLocInfo) {
        arriveDelay()
        val weaponObj = player.worn[Wearpos.RightHand.slot]
        val weapon = weaponObj?.let(::getInvObj)
        val slashBonus = weapon?.param(params.attack_slash) ?: 0
        val knifeId = WEB_KNIFE_OBJ.asRSCM(RSCMType.OBJ)
        val hasKnife =
            inv("inv.backpack").any { obj -> obj != null && getInvObj(obj).id == knifeId }
        val wildernessSword =
            playerContainsAnyObj(
                "obj.wilderness_sword_easy",
                "obj.wilderness_sword_medium",
                "obj.wilderness_sword_hard",
                "obj.wilderness_sword_elite",
            )
        if (!canAttemptWebSlash(hasKnife, wildernessSword, slashBonus)) {
            mes("You need a knife or a weapon with a positive slash bonus to cut through the web.")
            return
        }
        if (weapon == null && hasKnife) {
            anim(WEB_KNIFE_SLASH_SEQUENCE)
        } else {
            weapon?.paramOrNull(params.attack_anim)?.let {
                anim(getReverseMapping(RSCMType.SEQ, it.id))
            }
        }
        delay(1)
        val success =
            guaranteesWebSlash(hasKnife, wildernessSword) ||
                random.of(100) < (50 + slashBonus).coerceIn(50, 100)
        if (!success) {
            mes("You fail to cut through the web.")
            return
        }
        if (locRepo.del(web, WEB_RESPAWN_TICKS)) {
            mes("You slash through the web.")
        }
    }

    private suspend fun ProtectedAccess.setMetalGateState(
        pair: WildernessMetalGatePair,
        opening: Boolean,
    ) {
        arriveDelay()
        if (!replaceMetalGatePair(pair, opening)) {
            logger.warn {
                "Unmapped or out-of-sync Wilderness metal gate pair '${pair.name}' " +
                    "while ${if (opening) "opening" else "closing"}."
            }
            mes("The gate is already being used.")
            return
        }
        soundSynth(if (opening) "synth.picketgate_open" else "synth.picketgate_close")
    }

    /**
     * Replaces both leaves without yielding. Player interactions run on the game thread, so the
     * exact-state checks plus successful deletes make this a single authoritative transition for
     * every nearby player. A failed half is rolled back instead of leaving a duplicated or split
     * gate behind.
     */
    private fun replaceMetalGatePair(pair: WildernessMetalGatePair, opening: Boolean): Boolean {
        val from = pair.halves.map { if (opening) it.closed else it.opened }
        val into = pair.halves.map { if (opening) it.opened else it.closed }
        val current = from.map { findMetalGate(it) ?: return false }
        val duration = if (opening) METAL_GATE_AUTOCLOSE_TICKS else Int.MAX_VALUE
        val removed = ArrayList<LocInfo>(current.size)
        for (loc in current) {
            if (!locRepo.del(loc, duration)) {
                removed.forEach { locRepo.add(it, Int.MAX_VALUE) }
                return false
            }
            removed += loc
        }

        val added = ArrayList<LocInfo>(into.size)
        for (placement in into) {
            val loc = placement.toLocInfo()
            if (!locRepo.add(loc, duration)) {
                added.forEach { locRepo.del(it, Int.MAX_VALUE) }
                removed.forEach { locRepo.add(it, Int.MAX_VALUE) }
                return false
            }
            added += loc
        }
        return true
    }

    private fun findMetalGate(placement: WildernessMetalGatePlacement): LocInfo? =
        locRepo.findAll(placement.coords).firstOrNull {
            it.id == placement.objectId &&
                it.shape == placement.shape &&
                it.angle == placement.angle
        }

    private fun validateMetalGateDefinitions() {
        val expectedActions = mutableMapOf<Int, String>()
        for (state in WILDERNESS_METAL_GATE_STATES) {
            expectedActions.merge(state.closed.objectId, "Open") { existing, required ->
                require(existing == required)
                existing
            }
            expectedActions.merge(state.opened.objectId, "Close") { existing, required ->
                require(existing == required)
                existing
            }
        }
        for ((objectId, expectedAction) in expectedActions) {
            val type = ServerCacheManager.getObject(objectId)
            if (type == null) {
                logger.warn {
                    "Wilderness metal-gate object $objectId has no cache definition or known pair."
                }
                error("Missing revision-240 Wilderness metal-gate object: $objectId")
            }
            val action = type.actions.getOpOrNull(0)
            if (!action.equals(expectedAction, ignoreCase = true)) {
                logger.warn {
                    "Wilderness metal-gate object $objectId expected '$expectedAction' but found '$action'."
                }
                error("Invalid revision-240 Wilderness metal-gate action for object $objectId")
            }
        }
    }

    private suspend fun ProtectedAccess.pickLockedDoor(
        definition: WildernessLockedDoorDefinition,
        door: BoundLocInfo,
    ) {
        arriveDelay()
        if (!definition.closed.matches(door)) {
            mes("That door is no longer closed.")
            return
        }
        faceLoc(door)

        val rule = definition.rule
        if (stat("stat.thieving") < rule.level) {
            mes("You need a Thieving level of ${rule.level} to pick this lock.")
            return
        }
        if (!inv.contains("obj.lockpick")) {
            mes("You need a lockpick to pick this lock.")
            return
        }
        anim("seq.human_picklock_cagedoor")
        delay(LOCKPICK_ANIMATION_TICKS)
        val successChance = (35 + stat("stat.thieving")).coerceAtMost(95)
        if (random.of(100) >= successChance) {
            resetAnim()
            mes("You fail to pick the lock.")
            return
        }

        resetAnim()
        if (findLockedDoor(definition.closed) == null) {
            mes("Someone else has already opened the door.")
            return
        }
        val destination = lockedDoorDestination(definition.closed, player.coords)
        if (!collision.isZoneValid(destination)) {
            mes("The doorway is blocked.")
            return
        }
        if (!replaceLockedDoor(definition, opening = true)) {
            mes("Someone else has already opened the door.")
            return
        }

        soundSynth("synth.door_open")
        // The closed wall contributes a directional collision flag to the destination tile, so
        // checking isWalkBlocked before the swap rejects a valid doorway. Route only after the
        // real open object is active, and always suspend for one cycle so a one-tile walk can
        // complete before the door is restored.
        playerWalkWithMinDelay(destination)
        if (coords != destination) {
            if (replaceLockedDoor(definition, opening = false)) {
                soundSynth("synth.door_close")
            }
            mes("The doorway is blocked.")
            return
        }
        if (replaceLockedDoor(definition, opening = false)) {
            soundSynth("synth.door_close")
        }
        statAdvance("stat.thieving", rule.experience)
        mes("You pick the lock and the door shuts behind you.")
    }

    private suspend fun ProtectedAccess.closeLockedDoor(
        definition: WildernessLockedDoorDefinition,
        door: BoundLocInfo,
    ) {
        arriveDelay()
        if (!definition.opened.matches(door)) {
            mes("That door is already closed.")
            return
        }
        faceLoc(door)
        if (replaceLockedDoor(definition, opening = false)) {
            soundSynth("synth.door_close")
        }
    }

    /**
     * Swaps the complete wall-door identity without yielding. LocRepository owns the dynamic
     * collision and zone broadcasts, while the paired fallback timers restore the map state if a
     * player disconnects or loses protected access before the normal close-behind transition.
     */
    private fun replaceLockedDoor(
        definition: WildernessLockedDoorDefinition,
        opening: Boolean,
    ): Boolean {
        val from = if (opening) definition.closed else definition.opened
        val into = if (opening) definition.opened else definition.closed
        val current = findLockedDoor(from) ?: return false
        val duration = if (opening) LOCKED_DOOR_AUTOCLOSE_TICKS else Int.MAX_VALUE
        if (!locRepo.del(current, duration)) {
            return false
        }
        if (!locRepo.add(into.toLocInfo(), duration)) {
            locRepo.add(current, Int.MAX_VALUE)
            return false
        }
        return true
    }

    private fun findLockedDoor(placement: WildernessLockedDoorPlacement): LocInfo? =
        locRepo.findAll(placement.coords).firstOrNull {
            it.id == placement.objectId &&
                it.shape == placement.shape &&
                it.angle == placement.angle
        }

    private fun validateLockedDoorDefinitions() {
        require(
            LOCKED_DOORS.distinctBy {
                    Triple(it.closed.objectId, it.closed.coords, it.closed.angle)
                }
                .size == LOCKED_DOORS.size
        ) {
            "Duplicate Wilderness locked-door closed identity."
        }
        require(
            LOCKED_DOORS.distinctBy {
                    Triple(it.opened.objectId, it.opened.coords, it.opened.angle)
                }
                .size == LOCKED_DOORS.size
        ) {
            "Duplicate Wilderness locked-door opened identity."
        }
        for (definition in LOCKED_DOORS) {
            require(definition.closed.shape == LocShape.WallStraight)
            require(definition.opened.shape == definition.closed.shape)
            require(definition.opened.coords.level == definition.closed.coords.level)
            val closedType =
                requireNotNull(ServerCacheManager.getObject(definition.closed.objectId)) {
                    "Missing revision-240 locked door ${definition.closed.objectId}."
                }
            val openedType =
                requireNotNull(ServerCacheManager.getObject(definition.opened.objectId)) {
                    "Missing revision-240 opened door ${definition.opened.objectId}."
                }
            require(closedType.actions.getOpOrNull(0).equals("Open", ignoreCase = true)) {
                "Locked door ${definition.closed.objectId} has no Open option."
            }
            require(closedType.actions.getOpOrNull(1).equals("Pick-lock", ignoreCase = true)) {
                "Locked door ${definition.closed.objectId} has no Pick-lock option."
            }
            require(openedType.actions.getOpOrNull(0).equals("Close", ignoreCase = true)) {
                "Opened door ${definition.opened.objectId} has no Close option."
            }
        }
    }

    private fun safeDestination(preferred: CoordGrid): CoordGrid? =
        buildList {
                add(preferred)
                for (radius in 1..2) {
                    for (dx in -radius..radius) for (dz in -radius..radius) {
                        if (kotlin.math.max(kotlin.math.abs(dx), kotlin.math.abs(dz)) == radius) {
                            add(preferred.translate(dx, dz))
                        }
                    }
                }
            }
            .firstOrNull { collision.isZoneValid(it) && !collision.isWalkBlocked(it) }

    private fun validateOption(locId: Int, option: Int) {
        val type =
            requireNotNull(ServerCacheManager.getObject(locId)) {
                "Missing revision-240 Wilderness stateful object: $locId"
            }
        require(!type.actions.getOpOrNull(option - 1).isNullOrBlank() || type.multiLoc != null) {
            "Revision-240 Wilderness stateful object $locId has no option $option"
        }
    }
}

internal fun lockedDoorDestination(
    door: WildernessLockedDoorPlacement,
    player: CoordGrid,
): CoordGrid =
    with(door) {
        when (angle) {
            LocAngle.West,
            LocAngle.East ->
                if (player.x <= coords.x) coords.translateX(1) else coords.translateX(-1)
            LocAngle.North,
            LocAngle.South ->
                if (player.z <= coords.z) coords.translateZ(1) else coords.translateZ(-1)
        }
    }

internal const val WEB: Int = 733
internal const val WEB_KNIFE_OBJ: String = "obj.knife"
internal const val WEB_KNIFE_SLASH_SEQUENCE: String = "seq.human_sword_slash"
internal val LAVA_MAZE_WEB_KNIFE_SPAWN: CoordGrid = CoordGrid(3031, 3849)

internal fun canAttemptWebSlash(
    hasKnife: Boolean,
    hasWildernessSword: Boolean,
    slashBonus: Int,
): Boolean = hasKnife || hasWildernessSword || slashBonus > 0

internal fun guaranteesWebSlash(hasKnife: Boolean, hasWildernessSword: Boolean): Boolean =
    hasKnife || hasWildernessSword

internal val WEB_SOURCES: Set<CoordGrid> =
    setOf(
        CoordGrid(3030, 3852),
        CoordGrid(3115, 3859),
        CoordGrid(3092, 3957),
        CoordGrid(3095, 3957),
        CoordGrid(3105, 3958),
        CoordGrid(3106, 3958),
        CoordGrid(3147, 3727),
        CoordGrid(3163, 3736),
        CoordGrid(3183, 3733),
        CoordGrid(3158, 3951),
    )

internal enum class WildernessMetalGateHalf {
    Left,
    Right,
}

internal data class WildernessMetalGatePlacement(
    val objectId: Int,
    val coords: CoordGrid,
    val shape: LocShape,
    val angle: LocAngle,
)

internal data class WildernessMetalGateState(
    val half: WildernessMetalGateHalf,
    val closed: WildernessMetalGatePlacement,
    val opened: WildernessMetalGatePlacement,
)

internal data class WildernessMetalGatePair(
    val name: String,
    val left: WildernessMetalGateState,
    val right: WildernessMetalGateState,
) {
    val halves: List<WildernessMetalGateState>
        get() = listOf(left, right)
}

private fun wildernessMetalGatePair(
    name: String,
    leftClosedObjectId: Int,
    leftOpenObjectId: Int,
    leftClosedCoords: CoordGrid,
    rightClosedObjectId: Int,
    rightOpenObjectId: Int,
    rightClosedCoords: CoordGrid,
    closedAngle: LocAngle,
): WildernessMetalGatePair {
    val shape = LocShape.WallStraight
    val leftClosed =
        WildernessMetalGatePlacement(leftClosedObjectId, leftClosedCoords, shape, closedAngle)
    val rightClosed =
        WildernessMetalGatePlacement(rightClosedObjectId, rightClosedCoords, shape, closedAngle)
    val left =
        WildernessMetalGateState(
            half = WildernessMetalGateHalf.Left,
            closed = leftClosed,
            opened =
                WildernessMetalGatePlacement(
                    leftOpenObjectId,
                    DoorTranslations.translateOpen(leftClosedCoords, shape, closedAngle),
                    shape,
                    closedAngle.turn(rotations = 3),
                ),
        )
    val right =
        WildernessMetalGateState(
            half = WildernessMetalGateHalf.Right,
            closed = rightClosed,
            opened =
                WildernessMetalGatePlacement(
                    rightOpenObjectId,
                    DoorTranslations.translateOpen(rightClosedCoords, shape, closedAngle),
                    shape,
                    closedAngle.turn(rotations = 1),
                ),
        )
    return WildernessMetalGatePair(name, left, right)
}

internal val WILDERNESS_METAL_GATE_PAIRS: List<WildernessMetalGatePair> =
    listOf(
        wildernessMetalGatePair(
            "western surface gate",
            1727,
            1571,
            CoordGrid(2948, 3904),
            1728,
            1572,
            CoordGrid(2947, 3904),
            LocAngle.South,
        ),
        wildernessMetalGatePair(
            "north-west surface gate",
            1727,
            1571,
            CoordGrid(3008, 3849),
            1728,
            1572,
            CoordGrid(3008, 3850),
            LocAngle.West,
        ),
        wildernessMetalGatePair(
            "western central surface gate",
            1727,
            1571,
            CoordGrid(3071, 3857),
            1728,
            1572,
            CoordGrid(3071, 3856),
            LocAngle.East,
        ),
        wildernessMetalGatePair(
            "central surface metal gate",
            1568,
            1571,
            CoordGrid(3075, 3867),
            1569,
            1572,
            CoordGrid(3076, 3867),
            LocAngle.North,
        ),
        wildernessMetalGatePair(
            "eastern central surface gate",
            1727,
            1571,
            CoordGrid(3202, 3856),
            1728,
            1572,
            CoordGrid(3201, 3856),
            LocAngle.South,
        ),
        wildernessMetalGatePair(
            "north-east surface gate",
            1727,
            1571,
            CoordGrid(3225, 3904),
            1728,
            1572,
            CoordGrid(3224, 3904),
            LocAngle.South,
        ),
        wildernessMetalGatePair(
            "eastern surface gate",
            1727,
            1571,
            CoordGrid(3337, 3896),
            1728,
            1572,
            CoordGrid(3336, 3896),
            LocAngle.South,
        ),
        wildernessMetalGatePair(
            "south-west Wilderness dungeon gate",
            1568,
            1571,
            CoordGrid(3022, 10312),
            1569,
            1572,
            CoordGrid(3022, 10311),
            LocAngle.East,
        ),
        wildernessMetalGatePair(
            "western Wilderness dungeon gate",
            1568,
            1571,
            CoordGrid(3040, 10308),
            1569,
            1572,
            CoordGrid(3040, 10307),
            LocAngle.East,
        ),
        wildernessMetalGatePair(
            "northern Wilderness dungeon gate",
            1568,
            1571,
            CoordGrid(3044, 10342),
            1569,
            1572,
            CoordGrid(3044, 10341),
            LocAngle.East,
        ),
        wildernessMetalGatePair(
            "southern Wilderness dungeon gate",
            1568,
            1571,
            CoordGrid(3105, 9944),
            1569,
            1572,
            CoordGrid(3106, 9944),
            LocAngle.North,
        ),
        wildernessMetalGatePair(
            "Edgeville dungeon monkey-bars gate",
            1568,
            1571,
            CoordGrid(3103, 9910),
            1569,
            1572,
            CoordGrid(3103, 9909),
            LocAngle.East,
        ),
        wildernessMetalGatePair(
            "Edgeville dungeon members gate",
            1727,
            1571,
            CoordGrid(3131, 9917),
            1728,
            1572,
            CoordGrid(3132, 9917),
            LocAngle.North,
        ),
        wildernessMetalGatePair(
            "Edgeville dungeon eastern gate",
            1568,
            1571,
            CoordGrid(3145, 9871),
            1569,
            1572,
            CoordGrid(3145, 9870),
            LocAngle.East,
        ),
    )

internal val WILDERNESS_METAL_GATE_STATES: List<WildernessMetalGateState> =
    WILDERNESS_METAL_GATE_PAIRS.flatMap(WildernessMetalGatePair::halves)

private fun WildernessMetalGatePlacement.toLocInfo(): LocInfo =
    LocInfo(
        layer = LocLayerConstants.of(shape.id),
        coords = coords,
        entity = LocEntity(objectId, shape.id, angle.id),
    )

internal data class WildernessLockedDoorPlacement(
    val objectId: Int,
    val coords: CoordGrid,
    val shape: LocShape,
    val angle: LocAngle,
) {
    internal fun matches(loc: BoundLocInfo): Boolean =
        loc.id == objectId && loc.coords == coords && loc.shape == shape && loc.angle == angle
}

internal data class WildernessLockedDoorDefinition(
    val name: String,
    val rule: LockedDoorRule,
    val closed: WildernessLockedDoorPlacement,
    val opened: WildernessLockedDoorPlacement,
)

private fun WildernessLockedDoorPlacement.toLocInfo(): LocInfo =
    LocInfo(
        layer = LocLayerConstants.of(shape.id),
        coords = coords,
        entity = LocEntity(objectId, shape.id, angle.id),
    )

internal val LOCKED_DOORS: List<WildernessLockedDoorDefinition> =
    listOf(
        WildernessLockedDoorDefinition(
            name = "Pirate Hut west door",
            rule = LockedDoorRule(level = 39, experience = 35.0),
            closed =
                WildernessLockedDoorPlacement(
                    11727,
                    CoordGrid(3038, 3956),
                    LocShape.WallStraight,
                    LocAngle.West,
                ),
            opened =
                WildernessLockedDoorPlacement(
                    14750,
                    CoordGrid(3037, 3956),
                    LocShape.WallStraight,
                    LocAngle.North,
                ),
        ),
        WildernessLockedDoorDefinition(
            name = "Pirate Hut north door",
            rule = LockedDoorRule(level = 39, experience = 35.0),
            closed =
                WildernessLockedDoorPlacement(
                    11727,
                    CoordGrid(3041, 3959),
                    LocShape.WallStraight,
                    LocAngle.North,
                ),
            opened =
                WildernessLockedDoorPlacement(
                    14750,
                    CoordGrid(3041, 3960),
                    LocShape.WallStraight,
                    LocAngle.East,
                ),
        ),
        WildernessLockedDoorDefinition(
            name = "Pirate Hut east door",
            rule = LockedDoorRule(level = 39, experience = 35.0),
            closed =
                WildernessLockedDoorPlacement(
                    11727,
                    CoordGrid(3044, 3956),
                    LocShape.WallStraight,
                    LocAngle.East,
                ),
            opened =
                WildernessLockedDoorPlacement(
                    14750,
                    CoordGrid(3045, 3956),
                    LocShape.WallStraight,
                    LocAngle.South,
                ),
        ),
        WildernessLockedDoorDefinition(
            name = "Magic Axe Hut south door",
            rule = LockedDoorRule(level = 23, experience = 22.5),
            closed =
                WildernessLockedDoorPlacement(
                    11726,
                    CoordGrid(3190, 3957),
                    LocShape.WallStraight,
                    LocAngle.North,
                ),
            opened =
                WildernessLockedDoorPlacement(
                    1547,
                    CoordGrid(3190, 3958),
                    LocShape.WallStraight,
                    LocAngle.East,
                ),
        ),
        WildernessLockedDoorDefinition(
            name = "Magic Axe Hut north door",
            rule = LockedDoorRule(level = 23, experience = 22.5),
            closed =
                WildernessLockedDoorPlacement(
                    11726,
                    CoordGrid(3191, 3963),
                    LocShape.WallStraight,
                    LocAngle.South,
                ),
            opened =
                WildernessLockedDoorPlacement(
                    1547,
                    CoordGrid(3191, 3962),
                    LocShape.WallStraight,
                    LocAngle.West,
                ),
        ),
    )

internal data class LockedDoorRule(val level: Int, val experience: Double)

internal fun lockedDoorRule(locId: Int): LockedDoorRule =
    when (locId) {
        11727 -> LockedDoorRule(level = 39, experience = 35.0)
        11726 -> LockedDoorRule(level = 23, experience = 22.5)
        else -> error("No Wilderness lockpick rule for loc $locId")
    }

internal const val LOCKPICK_ANIMATION_TICKS: Int = 4
internal const val LOCKED_DOOR_AUTOCLOSE_TICKS: Int = 10
internal const val METAL_GATE_AUTOCLOSE_TICKS: Int = 30
internal const val WEB_RESPAWN_TICKS: Int = 100
