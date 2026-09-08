package org.rsmod.content.areas.wilderness.interactions

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import dev.openrune.types.SequenceServerType
import jakarta.inject.Inject
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.events.interact.LocCoordEvents
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

public data class WildernessVerticalRoute(
    public val locId: Int,
    public val option: Int,
    public val source: CoordGrid,
    public val destinationAnchor: CoordGrid,
    public val reverseLocId: Int?,
    public val reverseOption: Int?,
    public val directionality: String,
    public val movement: String,
    public val preserveApproachOffset: Boolean = true,
    public val animate: Boolean = true,
    public val registerHandler: Boolean = true,
) {
    public val xOffset: Int
        get() = destinationAnchor.x - source.x

    public val zOffset: Int
        get() = destinationAnchor.z - source.z

    public val levelOffset: Int
        get() = destinationAnchor.level - source.level
}

public data class WildernessVerticalChoice(
    public val locId: Int,
    public val source: CoordGrid,
    public val down: WildernessVerticalRoute?,
    public val up: WildernessVerticalRoute,
)

/**
 * Exact revision-240 vertical links. Entries are coordinate-scoped even when a loc id is reused.
 */
public object WildernessVerticalRouteTable {
    private const val LADDER_BOTTOM = 14745
    private const val LADDER_TOP = 14746
    private const val LADDER_MIDDLE = 14747
    private const val SPIRAL_BOTTOM = 14735
    private const val SPIRAL_MIDDLE = 14736
    private const val SPIRAL_TOP = 14737
    private const val FER0X_STAIRS_UP = 39643
    private const val FER0X_STAIRS_DOWN = 39650

    private val MULTI_FLOOR_LADDERS =
        listOf(3022 to 3625, 3022 to 3638, 3036 to 3625, 3036 to 3638, 3184 to 3610)
    private val UNUSABLE_BOTTOM_LADDERS = setOf(CoordGrid(3022, 3638, 0))
    private val SIMPLE_LADDERS =
        listOf(
            Triple(3125, 3633, 0),
            Triple(3125, 3639, 0),
            Triple(3126, 3618, 1),
            Triple(3126, 3625, 1),
            Triple(3130, 3633, 1),
            Triple(3130, 3639, 1),
            Triple(3143, 3618, 1),
            Triple(3143, 3625, 1),
        )
    private val FER0X_STAIRS =
        listOf(
            Triple(3132, 3619, 0),
            Triple(3132, 3623, 0),
            Triple(3137, 3619, 0),
            Triple(3137, 3623, 0),
            Triple(3138, 3636, 0),
            Triple(3138, 3637, 0),
            Triple(3144, 3636, 0),
            Triple(3144, 3637, 0),
            Triple(3141, 3635, 1),
            Triple(3142, 3635, 1),
        )

    public val choices: List<WildernessVerticalChoice> = buildList {
        for ((x, z) in MULTI_FLOOR_LADDERS) {
            for (plane in 1..2) {
                val source = CoordGrid(x, z, plane)
                add(
                    WildernessVerticalChoice(
                        locId = LADDER_MIDDLE,
                        source = source,
                        down =
                            if (source == CoordGrid(3022, 3638, 1)) {
                                null
                            } else {
                                levelRoute(
                                    LADDER_MIDDLE,
                                    3,
                                    source,
                                    -1,
                                    if (plane == 1) LADDER_BOTTOM else LADDER_MIDDLE,
                                    if (plane == 1) 1 else 2,
                                )
                            },
                        up =
                            levelRoute(
                                LADDER_MIDDLE,
                                2,
                                source,
                                1,
                                if (plane == 2) LADDER_TOP else LADDER_MIDDLE,
                                if (plane == 2) 1 else 3,
                            ),
                    )
                )
            }
        }
        for (plane in 1..2) {
            val source = CoordGrid(3280, 3936, plane)
            add(
                WildernessVerticalChoice(
                    locId = SPIRAL_MIDDLE,
                    source = source,
                    down =
                        WildernessVerticalRoute(
                            SPIRAL_MIDDLE,
                            3,
                            source,
                            CoordGrid(3280, 3935, plane - 1),
                            if (plane == 1) SPIRAL_BOTTOM else SPIRAL_MIDDLE,
                            if (plane == 1) 1 else 2,
                            "BIDIRECTIONAL",
                            "spiral-stair fixed arrival",
                            preserveApproachOffset = false,
                            animate = false,
                        ),
                    up =
                        WildernessVerticalRoute(
                            SPIRAL_MIDDLE,
                            2,
                            source,
                            CoordGrid(3281, 3936, plane + 1),
                            if (plane == 2) SPIRAL_TOP else SPIRAL_MIDDLE,
                            if (plane == 2) 1 else 3,
                            "BIDIRECTIONAL",
                            "spiral-stair fixed arrival",
                            preserveApproachOffset = false,
                            animate = false,
                        ),
                )
            )
        }
    }

    public val routes: List<WildernessVerticalRoute> = buildList {
        for ((x, z) in MULTI_FLOOR_LADDERS) {
            if (CoordGrid(x, z, 0) !in UNUSABLE_BOTTOM_LADDERS) {
                add(levelRoute(LADDER_BOTTOM, 1, CoordGrid(x, z, 0), 1, LADDER_MIDDLE, 3))
            }
            add(levelRoute(LADDER_TOP, 1, CoordGrid(x, z, 3), -1, LADDER_MIDDLE, 2))
        }

        for ((x, z, bottomPlane) in SIMPLE_LADDERS) {
            add(levelRoute(LADDER_BOTTOM, 1, CoordGrid(x, z, bottomPlane), 1, LADDER_TOP, 1))
            add(levelRoute(LADDER_TOP, 1, CoordGrid(x, z, bottomPlane + 1), -1, LADDER_BOTTOM, 1))
        }

        for ((x, z, bottomPlane) in FER0X_STAIRS) {
            add(
                levelRoute(
                    FER0X_STAIRS_UP,
                    1,
                    CoordGrid(x, z, bottomPlane),
                    1,
                    FER0X_STAIRS_DOWN,
                    1,
                    animate = false,
                )
            )
            add(
                levelRoute(
                    FER0X_STAIRS_DOWN,
                    1,
                    CoordGrid(x, z, bottomPlane + 1),
                    -1,
                    FER0X_STAIRS_UP,
                    1,
                    animate = false,
                )
            )
        }

        add(
            WildernessVerticalRoute(
                SPIRAL_BOTTOM,
                1,
                CoordGrid(3280, 3936, 0),
                CoordGrid(3281, 3936, 1),
                SPIRAL_MIDDLE,
                3,
                "BIDIRECTIONAL",
                "spiral-stair fixed arrival",
                preserveApproachOffset = false,
                animate = false,
            )
        )
        add(
            WildernessVerticalRoute(
                SPIRAL_TOP,
                1,
                CoordGrid(3281, 3936, 3),
                CoordGrid(3281, 3935, 2),
                SPIRAL_MIDDLE,
                2,
                "BIDIRECTIONAL",
                "spiral-stair fixed arrival",
                preserveApproachOffset = false,
                animate = false,
            )
        )
        choices.forEach {
            it.down?.let(::add)
            add(it.up)
        }

        add(
            levelRoute(
                17028,
                1,
                CoordGrid(3154, 3743, 0),
                1,
                null,
                null,
                directionality = "ONE_WAY_UNVERIFIED",
            )
        )

        add(dungeonRoute(14758, CoordGrid(3005, 3963), 6400, 17385, registerHandler = true))
        add(dungeonRoute(17385, CoordGrid(3005, 10363), -6400, 14758, registerHandler = false))
        add(fixedDungeonRoute(18987, CoordGrid(3017, 3849), CoordGrid(3069, 10255), 18988))
        add(fixedDungeonRoute(18988, CoordGrid(3069, 10256), CoordGrid(3017, 3848), 18987))
        add(fixedDungeonRoute(18989, CoordGrid(3069, 3856), CoordGrid(3017, 10248), 18990))
        add(fixedDungeonRoute(18990, CoordGrid(3017, 10249), CoordGrid(3069, 3855), 18989))
        add(dungeonRoute(16664, CoordGrid(3044, 3924), 6400, 16665, animate = false))
        add(dungeonRoute(16665, CoordGrid(3044, 10324), -6400, 16664, animate = false))
        add(dungeonRoute(16680, CoordGrid(3088, 3571), 6400, 17385))
        add(dungeonRoute(17385, CoordGrid(3088, 9971), -6400, 16680))
        add(
            WildernessVerticalRoute(
                locId = 1581,
                option = 1,
                source = CoordGrid(3097, 3468),
                destinationAnchor = CoordGrid(3097, 9866),
                reverseLocId = 17385,
                reverseOption = 1,
                directionality = "BIDIRECTIONAL",
                movement = "fixed Edgeville front trapdoor landing",
                preserveApproachOffset = false,
            )
        )
        add(
            WildernessVerticalRoute(
                locId = 17385,
                option = 1,
                source = CoordGrid(3097, 9867),
                destinationAnchor = CoordGrid(3097, 3467),
                reverseLocId = 1581,
                reverseOption = 1,
                directionality = "BIDIRECTIONAL",
                movement = "fixed Edgeville front ladder landing",
                preserveApproachOffset = false,
            )
        )

        add(
            dungeonRoute(
                47315,
                CoordGrid(2975, 9951),
                -6400,
                null,
                directionality = "INTENTIONAL_ONE_WAY",
                animate = false,
            )
        )

        add(levelRoute(272, 1, CoordGrid(3018, 3957, 0), 1, 273, 1))
        add(levelRoute(273, 1, CoordGrid(3018, 3957, 1), -1, 272, 1))
        for (x in listOf(3017, 3019)) {
            add(levelRoute(245, 1, CoordGrid(x, 3959, 1), 1, 246, 1))
            add(levelRoute(246, 1, CoordGrid(x, 3959, 2), -1, 245, 1))
        }

        for ((locId, z) in listOf(22254 to 3624, 22252 to 3625, 22253 to 3626)) {
            add(
                WildernessVerticalRoute(
                    locId,
                    1,
                    CoordGrid(3368, z, 3),
                    CoordGrid(3367, z, 2),
                    null,
                    null,
                    "INTENTIONAL_ONE_WAY",
                    "brain boat top-deck stairs to the collision-clear lower landing",
                    preserveApproachOffset = false,
                    animate = false,
                )
            )
        }
    }

    /**
     * The revision-240 Brain boat ladder is decorative and must not inherit generic ladder travel.
     */
    public val blockedRoutes: Set<Pair<Int, CoordGrid>> =
        setOf(22274 to CoordGrid(3363, 3625, 0), 22275 to CoordGrid(3363, 3625, 2))

    public fun route(locId: Int, option: Int, source: CoordGrid): WildernessVerticalRoute? =
        routes.firstOrNull { it.locId == locId && it.option == option && it.source == source }

    public fun choice(locId: Int, source: CoordGrid): WildernessVerticalChoice? =
        choices.firstOrNull { it.locId == locId && it.source == source }

    private fun levelRoute(
        locId: Int,
        option: Int,
        source: CoordGrid,
        levelOffset: Int,
        reverseLocId: Int?,
        reverseOption: Int?,
        directionality: String = "BIDIRECTIONAL",
        animate: Boolean = true,
    ): WildernessVerticalRoute =
        WildernessVerticalRoute(
            locId,
            option,
            source,
            source.translateLevel(levelOffset),
            reverseLocId,
            reverseOption,
            directionality,
            "preserve approach tile; change plane by $levelOffset",
            animate = animate,
        )

    private fun fixedDungeonRoute(
        locId: Int,
        source: CoordGrid,
        destination: CoordGrid,
        reverseLocId: Int,
    ): WildernessVerticalRoute =
        WildernessVerticalRoute(
            locId = locId,
            option = 1,
            source = source,
            destinationAnchor = destination,
            reverseLocId = reverseLocId,
            reverseOption = 1,
            directionality = "BIDIRECTIONAL",
            movement = "fixed collision-clear KBD ladder landing",
            preserveApproachOffset = false,
        )

    private fun dungeonRoute(
        locId: Int,
        source: CoordGrid,
        zOffset: Int,
        reverseLocId: Int?,
        directionality: String = "BIDIRECTIONAL",
        animate: Boolean = true,
        registerHandler: Boolean = true,
    ): WildernessVerticalRoute =
        WildernessVerticalRoute(
            locId,
            1,
            source,
            source.translateZ(zOffset),
            reverseLocId,
            reverseOption = reverseLocId?.let { 1 },
            directionality,
            "preserve approach tile; translate Y by $zOffset",
            animate = animate,
            registerHandler = registerHandler,
        )
}

public class WildernessVerticalRoutes @Inject constructor(private val collision: CollisionFlagMap) :
    PluginScript() {
    override fun ScriptContext.startup() {
        val registrations =
            WildernessVerticalRouteTable.routes
                .filter { it.registerHandler }
                .map { Triple(it.locId, it.option, it.source) }
                .plus(WildernessVerticalRouteTable.choices.map { Triple(it.locId, 1, it.source) })
                .distinct()
        for ((locId, option, source) in registrations) {
            val key = LocCoordEvents.key(locId, source)
            when (option) {
                1 ->
                    onProtectedEvent<LocCoordEvents.Op1>(key) {
                        handle(locId, option, it.loc.coords, it.type)
                    }
                2 ->
                    onProtectedEvent<LocCoordEvents.Op2>(key) {
                        handle(locId, option, it.loc.coords, it.type)
                    }
                3 ->
                    onProtectedEvent<LocCoordEvents.Op3>(key) {
                        handle(locId, option, it.loc.coords, it.type)
                    }
                else -> error("Unsupported Wilderness vertical option: $option")
            }
        }
        for ((locId, source) in WildernessVerticalRouteTable.blockedRoutes) {
            validateDefinitions(listOf(locId to 1))
            val key = LocCoordEvents.key(locId, source)
            onProtectedEvent<LocCoordEvents.Op1>(key) {
                arriveDelay()
                mes("Nothing interesting happens.")
            }
        }
        validateDefinitions(registrations.map { it.first to it.second }.distinct())
    }

    private suspend fun ProtectedAccess.handle(
        locId: Int,
        option: Int,
        source: CoordGrid,
        type: ObjectServerType,
    ) {
        arriveDelay()
        if (option == 1) {
            val choice = WildernessVerticalRouteTable.choice(locId, source)
            if (choice != null) {
                val down = choice.down
                if (down == null) {
                    travel(choice.up, type)
                } else {
                    startDialogue {
                        val route =
                            choice2(
                                "Climb down.",
                                down,
                                "Climb up.",
                                choice.up,
                                title = "Climb up or down?",
                            )
                        travel(route, type)
                    }
                }
                return
            }
        }
        val route = WildernessVerticalRouteTable.route(locId, option, source)
        if (route == null) {
            mes("That vertical route is unavailable from here.")
            return
        }
        travel(route, type)
    }

    private suspend fun ProtectedAccess.travel(
        route: WildernessVerticalRoute,
        type: ObjectServerType,
    ) {
        val destination =
            if (route.preserveApproachOffset) {
                player.coords.translate(route.xOffset, route.zOffset, route.levelOffset)
            } else {
                route.destinationAnchor
            }
        if (!collision.isZoneValid(destination) || collision.isWalkBlocked(destination)) {
            mes("That route is obstructed and cannot be used safely.")
            return
        }
        if (route.animate) {
            val animation: SequenceServerType? = type.paramOrNull(params.climb_anim)
            if (animation != null) {
                anim(RSCM.getReverseMapping(RSCMType.SEQ, animation.id))
                delay(1)
            }
        }
        telejump(destination, TeleportType.Exempt)
    }

    private fun validateDefinitions(registrations: List<Pair<Int, Int>>) {
        for ((locId, option) in registrations) {
            val type =
                requireNotNull(ServerCacheManager.getObject(locId)) {
                    "Missing revision-240 Wilderness vertical object: $locId"
                }
            require(!type.actions.getOpOrNull(option - 1).isNullOrBlank()) {
                "Wilderness vertical object $locId is missing option $option"
            }
        }
    }
}
