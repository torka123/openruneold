package org.rsmod.content.skills.agility

import org.rsmod.map.CoordGrid

public enum class AgilityRenderStyle(
    public val readyAnimation: Int,
    public val movementAnimation: Int,
) {
    Balance(763, 762),
    Wall(757, 756),
    Jump(755, 754),
    MonkeyBars(744, 744),
}

public enum class AgilityRouteFacing {
    North,
    NorthEast,
    East,
    SouthEast,
    South,
    SouthWest,
    West,
    NorthWest,
}

/** A single cache-accurate leg of an obstacle rather than a one-step endpoint shortcut. */
public data class AgilityRouteStep(
    val destination: CoordGrid,
    val animation: Int? = null,
    val durationTicks: Int = 1,
    val teleport: Boolean = false,
    val renderStyle: AgilityRenderStyle? = null,
    val landingAnimation: Int? = null,
    val facing: AgilityRouteFacing? = null,
)

/**
 * Revision-240 course routes reconstructed from the donor movement scripts. The donor is used only
 * for route/sequence data; XP remains the current OSRS data in [AgilityDefinitions].
 */
public object AgilityRoutes {
    private fun move(
        x: Int,
        z: Int,
        level: Int,
        animation: Int? = null,
        ticks: Int = 1,
        renderStyle: AgilityRenderStyle? = null,
        land: Int? = null,
        facing: AgilityRouteFacing? = null,
    ): AgilityRouteStep =
        AgilityRouteStep(
            CoordGrid(x, z, level),
            animation,
            ticks,
            renderStyle = renderStyle,
            landingAnimation = land,
            facing = facing,
        )

    private fun tele(
        x: Int,
        z: Int,
        level: Int,
        animation: Int? = null,
        ticks: Int = 1,
        land: Int? = null,
    ): AgilityRouteStep =
        AgilityRouteStep(
            CoordGrid(x, z, level),
            animation,
            ticks,
            teleport = true,
            landingAnimation = land,
        )

    private val routes: Map<String, List<AgilityRouteStep>> =
        mapOf(
            // Gnome Stronghold
            "gnome:1" to
                listOf(move(2474, 3429, 0, 763, 5, renderStyle = AgilityRenderStyle.Balance)),
            "gnome:2" to listOf(tele(2474, 3424, 1, 828)),
            "gnome:3" to listOf(tele(2473, 3420, 2, 828)),
            "gnome:4" to
                listOf(move(2483, 3420, 2, 763, 5, renderStyle = AgilityRenderStyle.Balance)),
            "gnome:5" to listOf(tele(2485, 3419, 0, 828)),

            // Barbarian Outpost
            "barbarian:1" to listOf(move(2551, 3549, 0, 751, 3)),
            "barbarian:2" to
                listOf(move(2541, 3546, 0, 763, 8, renderStyle = AgilityRenderStyle.Balance)),
            "barbarian:3" to listOf(tele(2536, 3546, 1, 828)),
            "barbarian:4" to
                listOf(
                    move(2532, 3547, 1, 753, 2, renderStyle = AgilityRenderStyle.Wall),
                    move(2532, 3546, 1, renderStyle = AgilityRenderStyle.Wall),
                ),

            // Prifddinas
            "prifddinas:1" to listOf(tele(3255, 6109, 2, 828)),
            "prifddinas:2" to
                listOf(
                    move(3258, 6105, 2, ticks = 2),
                    move(3272, 6105, 2, 763, 14, renderStyle = AgilityRenderStyle.Balance),
                ),
            "prifddinas:3" to
                listOf(
                    move(3273, 6105, 2),
                    move(3273, 6106, 2, 2583),
                    move(3273, 6107, 2, 2585),
                    move(3269, 6112, 2, 741, 3),
                ),
            "prifddinas:4" to listOf(tele(3269, 6117, 0, 2586, land = 2588)),
            "prifddinas:5" to listOf(tele(3293, 6141, 0, 827, 6)),
            "prifddinas:6" to listOf(tele(3293, 6145, 2, 828)),
            "prifddinas:7" to
                listOf(move(3281, 6142, 2, 763, 6, renderStyle = AgilityRenderStyle.Balance)),
            "prifddinas:8" to
                listOf(
                    move(3277, 6143, 2, ticks = 2),
                    move(3271, 6149, 2, 763, 6, renderStyle = AgilityRenderStyle.Balance),
                ),
            "prifddinas:9" to
                listOf(
                    move(3270, 6150, 2),
                    move(3270, 6158, 2, 763, 6, renderStyle = AgilityRenderStyle.Balance),
                ),
            "prifddinas:10" to
                listOf(
                    move(3267, 6161, 2, ticks = 2),
                    move(3274, 6168, 2, 763, 6, renderStyle = AgilityRenderStyle.Balance),
                ),
            "prifddinas:11" to
                listOf(
                    move(3278, 6171, 2, ticks = 2),
                    move(3284, 6177, 2, 763, 6, renderStyle = AgilityRenderStyle.Balance),
                    tele(3284, 6177, 0),
                ),
            "prifddinas:12" to listOf(tele(3240, 6109, 0, 827, 6)),
        )

    private val approaches: Map<String, CoordGrid> = emptyMap()

    public fun routeFor(courseKey: String, stage: Int): List<AgilityRouteStep> =
        routes["$courseKey:$stage"].orEmpty()

    public fun approachFor(courseKey: String, stage: Int): CoordGrid? =
        approaches["$courseKey:$stage"]
}
