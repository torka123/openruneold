package org.rsmod.content.areas.wilderness.interactions

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.config.constants
import org.rsmod.api.player.events.interact.LocCoordEvents
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.player.PlayerRegistry
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onApLoc1
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.content.generic.locs.gate.GateTranslations.leftGateOpen
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

public enum class WildernessTraversalMode {
    ExactMove,
    Tunnel,
}

public data class WildernessEnvironmentalRoute(
    public val locId: Int,
    public val option: Int,
    public val source: CoordGrid,
    public val destinationA: CoordGrid,
    public val destinationB: CoordGrid? = null,
    public val level: Int = 1,
    public val xp: Double = 0.0,
    public val animation: Int = 749,
    public val mode: WildernessTraversalMode = WildernessTraversalMode.ExactMove,
    public val revenantShortcut: Boolean = false,
    public val interactionDistance: Int = 1,
    public val failureDamage: IntRange? = null,
    public val directionality: String = "BIDIRECTIONAL",
)

public object WildernessEnvironmentalRouteTable {
    public val routes: List<WildernessEnvironmentalRoute> = buildList {
        for (z in 3530..3533) {
            add(
                WildernessEnvironmentalRoute(
                    23271,
                    1,
                    CoordGrid(2996, z),
                    CoordGrid(2996, 3529),
                    CoordGrid(2996, 3534),
                    animation = 6132,
                )
            )
        }

        for (source in
            listOf(CoordGrid(2947, 3678), CoordGrid(2948, 3679), CoordGrid(2949, 3680))) {
            add(
                WildernessEnvironmentalRoute(
                    16545,
                    1,
                    source,
                    CoordGrid(2945, 3676),
                    CoordGrid(2951, 3682),
                    40,
                    animation = 740,
                )
            )
        }
        for (source in
            listOf(
                CoordGrid(2944, 3768),
                CoordGrid(2945, 3767),
                CoordGrid(2946, 3767),
                CoordGrid(2947, 3767),
            )) {
            add(
                WildernessEnvironmentalRoute(
                    26406,
                    1,
                    source,
                    CoordGrid(2943, 3767),
                    CoordGrid(2948, 3767),
                    47,
                    animation = 740,
                )
            )
        }
        add(
            WildernessEnvironmentalRoute(
                26404,
                1,
                CoordGrid(2949, 3767),
                CoordGrid(2948, 3767),
                CoordGrid(2950, 3767),
                47,
                animation = 740,
            )
        )

        add(
            WildernessEnvironmentalRoute(
                26769,
                1,
                CoordGrid(3035, 10158),
                CoordGrid(3061, 10131),
                level = 60,
                mode = WildernessTraversalMode.Tunnel,
            )
        )
        add(
            WildernessEnvironmentalRoute(
                26769,
                1,
                CoordGrid(3062, 10131),
                CoordGrid(3036, 10158),
                level = 60,
                mode = WildernessTraversalMode.Tunnel,
            )
        )
        add(
            WildernessEnvironmentalRoute(
                19043,
                1,
                CoordGrid(3046, 10327),
                CoordGrid(3048, 10334),
                level = 51,
                mode = WildernessTraversalMode.Tunnel,
            )
        )
        add(
            WildernessEnvironmentalRoute(
                19043,
                1,
                CoordGrid(3048, 10335),
                CoordGrid(3046, 10328),
                level = 51,
                mode = WildernessTraversalMode.Tunnel,
            )
        )

        val slayerCrevices =
            listOf(
                CoordGrid(3334, 10119) to CoordGrid(3334, 10121),
                CoordGrid(3342, 10149) to CoordGrid(3342, 10153),
                CoordGrid(3434, 10091) to CoordGrid(3434, 10093),
                CoordGrid(3435, 10112) to CoordGrid(3435, 10115),
            )
        for ((first, second) in slayerCrevices) {
            val dx = (second.x - first.x).coerceIn(-1, 1)
            val dz = (second.z - first.z).coerceIn(-1, 1)
            val beyondSecond = second.translate(dx, dz)
            val beyondFirst = first.translate(-dx, -dz)
            add(
                WildernessEnvironmentalRoute(
                    53259,
                    1,
                    first,
                    beyondSecond,
                    level = 77,
                    xp = 10.0,
                    mode = WildernessTraversalMode.Tunnel,
                )
            )
            add(
                WildernessEnvironmentalRoute(
                    53259,
                    1,
                    second,
                    beyondFirst,
                    level = 77,
                    xp = 10.0,
                    mode = WildernessTraversalMode.Tunnel,
                )
            )
        }

        add(
            WildernessEnvironmentalRoute(
                14917,
                1,
                CoordGrid(3092, 3879),
                CoordGrid(3091, 3879),
                CoordGrid(3093, 3879),
                82,
                animation = 741,
            )
        )
        add(
            WildernessEnvironmentalRoute(
                14918,
                1,
                CoordGrid(3201, 3808),
                CoordGrid(3200, 3808),
                CoordGrid(3202, 3808),
                74,
                animation = 741,
            )
        )
        add(
            WildernessEnvironmentalRoute(
                53237,
                1,
                CoordGrid(3268, 3627),
                CoordGrid(3267, 3627),
                CoordGrid(3269, 3627),
                72,
                animation = 741,
            )
        )

        val revPillars =
            listOf(
                RevenantPillar(
                    CoordGrid(3180, 10209),
                    CoordGrid(3180, 10206),
                    CoordGrid(3180, 10212),
                    75,
                ),
                RevenantPillar(
                    CoordGrid(3220, 10086),
                    CoordGrid(3220, 10083),
                    CoordGrid(3220, 10089),
                    65,
                ),
                RevenantPillar(
                    CoordGrid(3200, 10136),
                    CoordGrid(3198, 10136),
                    CoordGrid(3202, 10136),
                    75,
                ),
                RevenantPillar(
                    CoordGrid(3241, 10145),
                    CoordGrid(3239, 10145),
                    CoordGrid(3243, 10145),
                    89,
                ),
                RevenantPillar(
                    CoordGrid(3202, 10196),
                    CoordGrid(3200, 10196),
                    CoordGrid(3204, 10196),
                    75,
                ),
            )
        for ((source, endpointA, endpointB, level) in revPillars) {
            add(
                WildernessEnvironmentalRoute(
                    31561,
                    1,
                    source,
                    endpointA,
                    endpointB,
                    level = level,
                    xp = level / 10.0,
                    animation = 741,
                    revenantShortcut = true,
                    interactionDistance =
                        maxOf(
                            source.chebyshevDistance(endpointA),
                            source.chebyshevDistance(endpointB),
                        ),
                    failureDamage = 4..11,
                )
            )
        }
    }
}

private data class RevenantPillar(
    val source: CoordGrid,
    val endpointA: CoordGrid,
    val endpointB: CoordGrid,
    val level: Int,
)

/** Shared exact-movement framework for Wilderness boundaries and shortcuts. */
public class WildernessEnvironmentalRoutes
@Inject
constructor(private val collision: CollisionFlagMap, private val random: GameRandom) :
    PluginScript() {
    override fun ScriptContext.startup() {
        for (route in WildernessEnvironmentalRouteTable.routes) {
            validateOption(route.locId, route.option)
            val key = LocCoordEvents.key(route.locId, route.source)
            onProtectedEvent<LocCoordEvents.Op1>(key) { traverse(route) }
        }
        onApLoc1(REVENANT_SHORTCUT_LOC) { event ->
            val route =
                WildernessEnvironmentalRouteTable.routes.firstOrNull {
                    it.revenantShortcut && it.source == event.loc.coords
                }
            if (route != null && isWithinApRange(event.loc, route.interactionDistance)) {
                traverse(route)
            }
        }
    }

    private suspend fun ProtectedAccess.traverse(route: WildernessEnvironmentalRoute) {
        if (!route.revenantShortcut) {
            arriveDelay()
        }
        if (stat("stat.agility") < route.level) {
            mes("You need an Agility level of ${route.level} to use this shortcut.")
            return
        }
        if (route.revenantShortcut && (player.isFrozen || player.isBound)) {
            mes("You cannot use this shortcut while you are frozen or bound.")
            return
        }
        if (route.revenantShortcut) {
            val approach = revenantShortcutApproach(route, player.coords)
            if (approach == null) {
                mes("Move directly in front of either side of the shortcut before crossing.")
                return
            }
            playerWalkWithMinDelay(approach)
            if (player.coords != approach) {
                mes("You cannot reach that side of the shortcut.")
                return
            }
        }
        val destination = resolveDestination(route)
        if (destination == null) {
            mes("That shortcut has no safe arrival tile.")
            return
        }
        faceSquare(destination)
        anim(route.animation)
        val failed =
            route.failureDamage != null &&
                random.of(100) >= (stat("stat.agility") + 5).coerceAtMost(95)
        when (route.mode) {
            WildernessTraversalMode.ExactMove -> {
                val direction =
                    if (route.revenantShortcut) {
                        revenantShortcutDirection(player.coords, destination)
                    } else {
                        0
                    }
                exactMove(player.coords, destination, 0, 60, direction, TeleportType.Exempt)
                delay(2)
            }
            WildernessTraversalMode.Tunnel -> {
                delay(2)
                telejump(destination, TeleportType.Exempt)
            }
        }
        if (route.xp > 0.0) statAdvance("stat.agility", if (failed) route.xp / 2.0 else route.xp)
        if (failed) {
            val damage = random.of(route.failureDamage!!)
            takeInstantHit(
                HitType.Typeless,
                damage.coerceAtMost((player.hitpoints - 1).coerceAtLeast(0)),
            )
            mes("You slip, but manage to make it across.")
        }
    }

    private fun ProtectedAccess.resolveDestination(
        route: WildernessEnvironmentalRoute
    ): CoordGrid? {
        val preferred =
            if (route.destinationB == null) {
                route.destinationA
            } else if (player.coords == route.destinationA) {
                route.destinationB
            } else {
                route.destinationA
            }
        return safeDestination(preferred)
    }

    private fun safeDestination(preferred: CoordGrid): CoordGrid? =
        if (collision.isZoneValid(preferred) && !collision.isWalkBlocked(preferred)) preferred
        else null

    private fun validateOption(locId: Int, option: Int) {
        val type =
            requireNotNull(ServerCacheManager.getObject(locId)) {
                "Missing Wilderness environmental object $locId"
            }
        require(!type.actions.getOpOrNull(option - 1).isNullOrBlank()) {
            "Wilderness environmental object $locId has no option $option"
        }
    }
}

internal fun revenantShortcutApproach(
    route: WildernessEnvironmentalRoute,
    coords: CoordGrid,
): CoordGrid? {
    val second = route.destinationB ?: return null
    if (coords == route.source) return null
    return listOf(route.destinationA, second)
        .minByOrNull { coords.chebyshevDistance(it) }
        ?.takeIf { coords.chebyshevDistance(it) <= route.interactionDistance }
}

internal fun revenantShortcutDirection(from: CoordGrid, to: CoordGrid): Int =
    when {
        to.x > from.x -> constants.em_face_east
        to.x < from.x -> constants.em_face_west
        to.z > from.z -> constants.em_face_north
        else -> constants.em_face_south
    }

private const val REVENANT_SHORTCUT_LOC = "loc.wild_cave_agility_jump"

public class WildernessResourceGate
@Inject
constructor(private val players: PlayerRegistry, private val locRepo: LocRepository) :
    PluginScript() {
    override fun ScriptContext.startup() {
        val closedKey = LocCoordEvents.key(RESOURCE_GATE_LOC, RESOURCE_GATE_SOURCE)
        onProtectedEvent<LocCoordEvents.Op1>(closedKey) { openAndCrossResourceGate(it.loc) }
        onProtectedEvent<LocCoordEvents.Op2>(closedKey) { peekResourceArea() }
    }

    private suspend fun ProtectedAccess.openAndCrossResourceGate(gate: BoundLocInfo) {
        arriveDelay()
        val destination = resourceGateDestination(player.coords)
        val entering = destination == RESOURCE_GATE_INSIDE
        if (entering && inv.count("obj.coins") < RESOURCE_AREA_FEE) {
            mes("You need 7,500 coins to enter the Resource Area.")
            return
        }
        if (entering && invDel(inv, "obj.coins", RESOURCE_AREA_FEE).failure) {
            mes("The fee could not be collected. No coins were taken.")
            return
        }
        val internal = requireNotNull(ServerCacheManager.getObject(gate.id)?.internalName)
        val mapDefinedOpen = gate.coords + leftGateOpen(gate.shape, gate.angle)
        check(mapDefinedOpen == RESOURCE_GATE_OUTSIDE) {
            "Resource Area gate has unexpected map geometry: $mapDefinedOpen"
        }
        val openedCoords = mapDefinedOpen
        val openRotations = RESOURCE_GATE_OPEN_ROTATIONS
        if (!locRepo.del(gate, RESOURCE_GATE_CROSS_TICKS)) {
            if (entering) check(invAdd(inv, "obj.coins", RESOURCE_AREA_FEE).success)
            mes("The gate is already being used.")
            return
        }
        locRepo.add(
            openedCoords,
            internal,
            RESOURCE_GATE_CROSS_TICKS,
            gate.turnAngle(rotations = openRotations),
            gate.shape,
        )
        soundSynth("synth.picketgate_open")
        playerWalk(destination)
        delay(RESOURCE_GATE_FINISH_WALK_TICKS)
        soundSynth("synth.picketgate_close")
        if (entering) mes("You pay 7,500 coins and enter the Resource Area.")
    }

    private suspend fun ProtectedAccess.peekResourceArea() {
        arriveDelay()
        if (player.coords.z < RESOURCE_GATE_SOURCE.z) {
            mes("All you see is the barren wasteland of the Wilderness.")
            return
        }
        var count = 0
        players.forEachOnline { other ->
            if (
                other !== player &&
                    other.coords.x in RESOURCE_AREA_X_RANGE &&
                    other.coords.z in RESOURCE_AREA_Z_RANGE
            ) {
                count++
            }
        }
        mes(
            "There ${if (count == 1) "is" else "are"} $count adventurer${if (count == 1) "" else "s"} inside the Resource Area."
        )
    }
}

public class WildernessFeroxAndPortals
@Inject
constructor(private val areas: AreaChecker, private val collision: CollisionFlagMap) :
    PluginScript() {
    override fun ScriptContext.startup() {
        for ((identity, endpoints) in FEROX_BARRIERS) {
            val (locId, source) = identity
            onProtectedEvent<LocCoordEvents.Op1>(LocCoordEvents.key(locId, source)) {
                crossFerox(endpoints.first, endpoints.second)
            }
        }
        for (route in SUPPORTED_PORTALS) {
            onProtectedEvent<LocCoordEvents.Op1>(LocCoordEvents.key(route.first, route.second)) {
                portal(route.third)
            }
        }
        for ((identity, reason) in WILDERNESS_UNSUPPORTED_TRANSPORT) {
            onProtectedEvent<LocCoordEvents.Op1>(
                LocCoordEvents.key(identity.first, identity.second)
            ) {
                arriveDelay()
                mes(reason)
            }
        }
    }

    private suspend fun ProtectedAccess.crossFerox(first: CoordGrid, second: CoordGrid) {
        arriveDelay()
        val destination = feroxBarrierDestination(player.coords, first, second)
        val entering =
            !areas.inArea("area.ferox_enclave", player.coords) &&
                areas.inArea("area.ferox_enclave", destination)
        if (entering && player.vars["varbit.teleblock_cycles"] > 0) {
            mes("A magical force prevents you from entering the Enclave while teleblocked.")
            return
        }
        anim(FEROX_BARRIER_ANIMATION)
        val direction = feroxBarrierDirection(first, second, destination)
        exactMove(
            player.coords,
            destination,
            0,
            FEROX_BARRIER_MOVE_CYCLES,
            direction,
            TeleportType.Exempt,
        )
        delay(FEROX_BARRIER_CROSS_TICKS)
    }

    private suspend fun ProtectedAccess.portal(preferred: CoordGrid) {
        arriveDelay()
        val destination =
            listOf(
                    preferred,
                    preferred.translateX(1),
                    preferred.translateZ(1),
                    preferred.translateX(-1),
                    preferred.translateZ(-1),
                )
                .firstOrNull { collision.isZoneValid(it) && !collision.isWalkBlocked(it) }
        if (destination == null) {
            mes("That portal has no safe destination.")
            return
        }
        telejump(destination, TeleportType.Exempt)
    }
}

private const val RESOURCE_AREA_FEE: Int = 7_500
internal const val RESOURCE_GATE_LOC: Int = 26760
internal const val RESOURCE_GATE_CROSS_TICKS: Int = 2
internal const val RESOURCE_GATE_FINISH_WALK_TICKS: Int = 1
internal const val RESOURCE_GATE_OPEN_ROTATIONS: Int = 3
internal val RESOURCE_GATE_SOURCE: CoordGrid = CoordGrid(3184, 3944)
internal val RESOURCE_GATE_INSIDE: CoordGrid = CoordGrid(3184, 3943)
internal val RESOURCE_GATE_OUTSIDE: CoordGrid = CoordGrid(3184, 3945)
private val RESOURCE_AREA_X_RANGE: IntRange = 3174..3196
private val RESOURCE_AREA_Z_RANGE: IntRange = 3924..3943
internal const val FEROX_BARRIER_ANIMATION: Int = 10409
internal const val FEROX_BARRIER_MOVE_CYCLES: Int = 30
internal const val FEROX_BARRIER_CROSS_TICKS: Int = 1

/** Chooses a clear tile across the north/south gate instead of moving sideways into its wall. */
internal fun resourceGateDestination(player: CoordGrid): CoordGrid =
    if (player.z > RESOURCE_GATE_SOURCE.z) RESOURCE_GATE_INSIDE else RESOURCE_GATE_OUTSIDE

/** Only a player arriving from north of the boundary is entering and owes the entrance fee. */
internal fun resourceGateChargesEntry(player: CoordGrid): Boolean =
    resourceGateDestination(player) == RESOURCE_GATE_INSIDE

/** Selects the opposite endpoint from the player's actual side, including the end tiles. */
internal fun feroxBarrierDestination(
    player: CoordGrid,
    first: CoordGrid,
    second: CoordGrid,
): CoordGrid {
    require(first.level == second.level)
    return if (first.x != second.x) {
        val west = if (first.x < second.x) first else second
        val east = if (first.x < second.x) second else first
        if (player.x <= west.x) east else west
    } else {
        val south = if (first.z < second.z) first else second
        val north = if (first.z < second.z) second else first
        if (player.z <= south.z) north else south
    }
}

/** Faces the same direction as the one-tick run-through instead of always forcing south. */
internal fun feroxBarrierDirection(
    first: CoordGrid,
    second: CoordGrid,
    destination: CoordGrid,
): Int {
    val origin =
        when (destination) {
            first -> second
            second -> first
            else -> error("Ferox destination must be one of the barrier endpoints.")
        }
    return when {
        destination.x > origin.x -> constants.em_face_east
        destination.x < origin.x -> constants.em_face_west
        destination.z > origin.z -> constants.em_face_north
        destination.z < origin.z -> constants.em_face_south
        else -> error("Ferox barrier endpoints must be on different tiles.")
    }
}

internal val FEROX_BARRIERS: Map<Pair<Int, CoordGrid>, Pair<CoordGrid, CoordGrid>> =
    mapOf(
        (39653 to CoordGrid(3123, 3628)) to (CoordGrid(3122, 3628) to CoordGrid(3123, 3628)),
        (39652 to CoordGrid(3123, 3629)) to (CoordGrid(3122, 3629) to CoordGrid(3123, 3629)),
        (39652 to CoordGrid(3134, 3617)) to (CoordGrid(3134, 3616) to CoordGrid(3134, 3617)),
        (39653 to CoordGrid(3135, 3617)) to (CoordGrid(3135, 3616) to CoordGrid(3135, 3617)),
        (39653 to CoordGrid(3134, 3639)) to (CoordGrid(3134, 3639) to CoordGrid(3134, 3640)),
        (39652 to CoordGrid(3135, 3639)) to (CoordGrid(3135, 3639) to CoordGrid(3135, 3640)),
        (39652 to CoordGrid(3154, 3634)) to (CoordGrid(3154, 3634) to CoordGrid(3155, 3634)),
        (39653 to CoordGrid(3154, 3635)) to (CoordGrid(3154, 3635) to CoordGrid(3155, 3635)),
    )
private val SUPPORTED_PORTALS: List<Triple<Int, CoordGrid, CoordGrid>> =
    listOf(
        Triple(30386, CoordGrid(3146, 3638), CoordGrid(2440, 3090)),
        Triple(40475, CoordGrid(3156, 10025), CoordGrid(2207, 2857)),
        Triple(39637, CoordGrid(3125, 3630), CoordGrid(3163, 5723)),
        Triple(39648, CoordGrid(3162, 10042), CoordGrid(3158, 3635)),
    )
internal val WILDERNESS_UNSUPPORTED_TRANSPORT: Map<Pair<Int, CoordGrid>, String> = buildMap {
    put(1729 to CoordGrid(3123, 3630), "Tournament Clan Wars is not available on this world.")
    put(
        26642 to CoordGrid(3126, 3620),
        "Clan Wars challenge arenas are not available on this world.",
    )
    put(
        26645 to CoordGrid(3127, 3626),
        "The Clan Wars free-for-all arena is not available on this world.",
    )
    put(47346 to CoordGrid(3140, 3621), "Bounty Hunter is not available on this world.")
    put(
        34822 to CoordGrid(3059, 3590),
        "This ruined temple route requires content that is not available on this world.",
    )
    for ((id, source) in
        listOf(
            39640 to CoordGrid(3141, 3639),
            39640 to CoordGrid(3142, 3639),
            39639 to CoordGrid(3141, 3638, 1),
            39639 to CoordGrid(3142, 3638, 1),
            39641 to CoordGrid(3141, 3639, 2),
            39641 to CoordGrid(3142, 3639, 2),
        )) put(id to source, "Bounty Hunter combat arenas are not available on this world.")
    put(
        53289 to CoordGrid(2947, 3822, 1),
        "This quest-specific ledge route is not available on this world.",
    )
}
