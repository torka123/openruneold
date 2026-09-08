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

            // Draynor Village rooftop
            "draynor:1" to listOf(tele(3102, 3279, 3, 828)),
            "draynor:2" to
                listOf(
                    move(3090, 3277, 3, 763, 8, renderStyle = AgilityRenderStyle.Balance),
                    move(3090, 3276, 3, ticks = 1),
                ),
            "draynor:3" to
                listOf(
                    move(3092, 3276, 3, ticks = 2),
                    move(3092, 3267, 3, 763, 9, renderStyle = AgilityRenderStyle.Balance),
                    move(3092, 3266, 3),
                ),
            "draynor:4" to
                listOf(
                    move(3089, 3262, 3, 753, 2, renderStyle = AgilityRenderStyle.Wall),
                    move(3088, 3261, 3, renderStyle = AgilityRenderStyle.Wall),
                    move(3088, 3261, 3, 759),
                ),
            "draynor:5" to listOf(move(3088, 3256, 3, 2583, 2), move(3088, 3255, 3, 2585)),
            "draynor:6" to listOf(tele(3096, 3256, 3, 2586, land = 2588)),
            "draynor:7" to
                listOf(
                    tele(3102, 3261, 1, 2586, land = 2588),
                    tele(3103, 3261, 0, 2586, land = 2588),
                ),

            // Al Kharid rooftop
            "al_kharid:1" to listOf(tele(3273, 3192, 3, 828)),
            "al_kharid:2" to
                listOf(move(3272, 3172, 3, 763, 9, renderStyle = AgilityRenderStyle.Balance)),
            "al_kharid:3" to listOf(move(3269, 3166, 3, 1995), move(3284, 3166, 3, 751, 2)),
            "al_kharid:4" to
                listOf(
                    tele(3303, 3163, 1, 2586),
                    move(3304, 3163, 1, 1601),
                    move(3305, 3163, 1, 1602),
                    move(3306, 3163, 1),
                    move(3307, 3163, 1),
                    move(3308, 3163, 1),
                    move(3309, 3163, 1),
                    move(3310, 3163, 1),
                    move(3311, 3163, 1),
                    move(3312, 3163, 1),
                    move(3313, 3163, 1),
                    move(3314, 3163, 1),
                    move(3315, 3163, 1),
                ),
            "al_kharid:5" to
                listOf(
                    move(3318, 3169, 1, 2583, 2),
                    move(3318, 3170, 1, 1122, 2),
                    move(3317, 3174, 2, 1124, land = 2588),
                ),
            "al_kharid:6" to listOf(tele(3316, 3180, 3, 828)),
            "al_kharid:7" to
                listOf(
                    move(3302, 3186, 3, 763, 12, renderStyle = AgilityRenderStyle.Balance),
                    move(3302, 3187, 3),
                ),
            "al_kharid:8" to listOf(tele(3299, 3194, 0, 2586)),

            // Varrock rooftop
            "varrock:1" to listOf(tele(3220, 3414, 3, 828), move(3219, 3414, 3, 2585)),
            "varrock:2" to
                listOf(
                    move(3212, 3414, 3, 741, 2),
                    move(3210, 3414, 3, 741),
                    move(3208, 3414, 3, 741),
                ),
            "varrock:3" to listOf(tele(3197, 3416, 1, 2586, land = 2588)),
            "varrock:4" to
                listOf(
                    move(3193, 3416, 1, 1995),
                    tele(3190, 3414, 1, 2583),
                    move(3190, 3413, 1, 1122, 2, facing = AgilityRouteFacing.West),
                    move(3190, 3412, 1, 1122, 2, facing = AgilityRouteFacing.West),
                    move(3190, 3411, 1, 1122, 2, facing = AgilityRouteFacing.West),
                    move(3190, 3410, 1, 1122, 2, facing = AgilityRouteFacing.West),
                    move(3190, 3409, 1, 1122, 2, facing = AgilityRouteFacing.West),
                    move(3190, 3407, 1, 753, 3, renderStyle = AgilityRenderStyle.Wall),
                    tele(3192, 3406, 3, 741),
                ),
            "varrock:5" to listOf(move(3192, 3399, 3, 2583, 2), move(3192, 3398, 3, 2585)),
            "varrock:6" to
                listOf(
                    move(3208, 3397, 3, 1995, ticks = 0),
                    move(3215, 3399, 3, 4789, 2, facing = AgilityRouteFacing.East),
                    move(3217, 3399, 3, 2583),
                    move(3218, 3399, 3, 2585),
                ),
            "varrock:7" to listOf(tele(3236, 3403, 3, 2586, land = 2588)),
            "varrock:8" to listOf(move(3236, 3410, 3, 1603, 3)),
            "varrock:9" to listOf(tele(3236, 3416, 2, 741), tele(3236, 3417, 0, 2586, land = 2588)),

            // Canifis rooftop
            "canifis:1" to
                listOf(
                    move(3507, 3489, 0, 1765, 2, facing = AgilityRouteFacing.North),
                    move(3505, 3489, 0, ticks = 4, facing = AgilityRouteFacing.East),
                    tele(3506, 3492, 2, ticks = 0),
                ),
            "canifis:2" to
                listOf(move(3506, 3504, 2, 1995), tele(3502, 3504, 2, 2586, land = 2588)),
            "canifis:3" to
                listOf(move(3498, 3504, 2, ticks = 2), tele(3493, 3504, 2, 2586, land = 2588)),
            "canifis:4" to
                listOf(
                    move(3487, 3499, 2, ticks = 2),
                    move(3481, 3499, 2, 2583),
                    tele(3479, 3499, 3, 2585),
                ),
            "canifis:5" to listOf(tele(3478, 3486, 2, 2586, land = 2588)),
            "canifis:6" to
                listOf(
                    move(3478, 3487, 2, 1995, facing = AgilityRouteFacing.East),
                    move(3480, 3484, 2, 7132, facing = AgilityRouteFacing.East),
                    move(3483, 3482, 2, facing = AgilityRouteFacing.East),
                    move(3484, 3481, 2, facing = AgilityRouteFacing.East),
                    move(3485, 3480, 2, facing = AgilityRouteFacing.East),
                    move(3486, 3479, 2, facing = AgilityRouteFacing.East),
                    move(3487, 3478, 2, facing = AgilityRouteFacing.East),
                    move(3488, 3477, 2, facing = AgilityRouteFacing.East),
                    move(3489, 3476, 2, facing = AgilityRouteFacing.East),
                    tele(3489, 3476, 3, ticks = 0, land = 2588),
                ),
            "canifis:7" to
                listOf(move(3502, 3476, 3, ticks = 2), tele(3510, 3476, 2, 2586, land = 2588)),
            "canifis:8" to listOf(tele(3510, 3485, 0, 2586, land = 2588)),

            // Falador rooftop
            "falador:1" to listOf(tele(3036, 3342, 3, 828)),
            "falador:2" to
                listOf(move(3047, 3343, 3, 763, 5, renderStyle = AgilityRenderStyle.Balance)),
            "falador:3" to
                listOf(
                    tele(3050, 3351, 2, 2583),
                    move(3051, 3352, 2, 1118, facing = AgilityRouteFacing.West),
                    move(3051, 3353, 2, 1118, 2, facing = AgilityRouteFacing.West),
                    move(3051, 3354, 2, 1118, 2, facing = AgilityRouteFacing.West),
                    move(3051, 3355, 2, 1118, 2, facing = AgilityRouteFacing.West),
                    tele(3050, 3357, 3, 1120, 2),
                ),
            "falador:4" to listOf(move(3048, 3361, 3, 741, 2)),
            "falador:5" to listOf(move(3041, 3361, 3, 741, 3)),
            "falador:6" to
                listOf(
                    move(3033, 3361, 3, 763, 3, renderStyle = AgilityRenderStyle.Balance),
                    move(3028, 3356, 3, ticks = 3, renderStyle = AgilityRenderStyle.Balance),
                    move(3028, 3354, 3, ticks = 1, renderStyle = AgilityRenderStyle.Balance),
                ),
            "falador:7" to
                listOf(
                    move(3026, 3353, 3, ticks = 1),
                    move(3021, 3353, 3, 7134, 3),
                    move(3020, 3353, 3, ticks = 1),
                ),
            "falador:8" to listOf(move(3016, 3349, 3, 1603, facing = AgilityRouteFacing.South)),
            "falador:9" to listOf(move(3014, 3349, 3, 1603, facing = AgilityRouteFacing.West)),
            "falador:10" to listOf(move(3014, 3347, 3, 1603, facing = AgilityRouteFacing.South)),
            "falador:11" to listOf(move(3014, 3345, 3, 1603, facing = AgilityRouteFacing.South)),
            "falador:12" to listOf(move(3016, 3345, 3, 1603, facing = AgilityRouteFacing.South)),
            "falador:13" to listOf(move(3029, 3335, 3, 1603, 2), tele(3029, 3333, 0, 2586)),

            // Seers' Village rooftop
            "seers:1" to listOf(tele(2729, 3488, 1, 737), tele(2729, 3491, 3, 1118)),
            "seers:2" to
                listOf(
                    tele(2719, 3495, 2, 2586, land = 2588),
                    tele(2713, 3494, 2, 2586, land = 2588),
                ),
            "seers:3" to
                listOf(move(2710, 3481, 2, 763, 7, renderStyle = AgilityRenderStyle.Balance)),
            "seers:4" to listOf(move(2710, 3474, 3, ticks = 1), move(2710, 3472, 3, 2585)),
            "seers:5" to listOf(tele(2702, 3465, 2, 2586, land = 2588)),
            "seers:6" to listOf(tele(2704, 3464, 0, 2462, land = 2588)),

            // Pollnivneach rooftop
            "pollnivneach:1" to listOf(tele(3351, 2964, 1, 828)),
            "pollnivneach:2" to
                listOf(tele(3350, 2972, 1, 2586, land = 2588), tele(3352, 2973, 1, 2588, 3)),
            "pollnivneach:3" to listOf(move(3357, 2978, 1, 751, 2), move(3360, 2978, 1)),
            "pollnivneach:4" to
                listOf(
                    move(3362, 2977, 1, 1995),
                    move(3366, 2976, 1, 1603, 2, facing = AgilityRouteFacing.East),
                ),
            "pollnivneach:5" to
                listOf(
                    move(3367, 2979, 1, 2583, 2),
                    move(3367, 2980, 1, 1122, 2),
                    move(3366, 2982, 1, 1124, land = 2588),
                ),
            "pollnivneach:6" to listOf(tele(3365, 2983, 2, 828)),
            "pollnivneach:7" to
                listOf(
                    move(3365, 2984, 2, 742),
                    move(3365, 2994, 2, ticks = 7, renderStyle = AgilityRenderStyle.MonkeyBars),
                    move(3365, 2994, 2, 743),
                ),
            "pollnivneach:8" to listOf(move(3359, 2997, 2, 1603, 2), move(3359, 3000, 2, 1603, 2)),
            "pollnivneach:9" to
                listOf(tele(3364, 3000, 2, 2586, land = 2588), tele(3364, 2998, 0, 2588, 5)),

            // Rellekka rooftop
            "rellekka:1" to listOf(tele(2626, 3676, 3, 828)),
            "rellekka:2" to
                listOf(
                    move(2626, 3676, 3, 1995),
                    move(2625, 3672, 3, 1603, 2, facing = AgilityRouteFacing.South),
                ),
            "rellekka:3" to
                listOf(move(2627, 3654, 3, 763, 8, renderStyle = AgilityRenderStyle.Balance)),
            "rellekka:4" to
                listOf(
                    move(2628, 3657, 3, facing = AgilityRouteFacing.North),
                    move(2635, 3658, 3, 752, 6, renderStyle = AgilityRenderStyle.Jump),
                    move(2639, 3654, 3, 763, 3, renderStyle = AgilityRenderStyle.Balance),
                    move(2639, 3653, 3, renderStyle = AgilityRenderStyle.Balance),
                ),
            "rellekka:5" to listOf(move(2639, 3653, 3, 1995), move(2639, 3657, 3, 1603, 2)),
            "rellekka:6" to
                listOf(
                    move(2647, 3663, 3, 763, 3, renderStyle = AgilityRenderStyle.Balance),
                    move(2654, 3670, 3, ticks = 4, renderStyle = AgilityRenderStyle.Balance),
                    move(2655, 3670, 3, renderStyle = AgilityRenderStyle.Balance),
                ),
            "rellekka:7" to listOf(tele(2653, 3676, 0, 2586, land = 2588), move(2652, 3676, 0)),

            // Ardougne rooftop
            "ardougne:1" to
                listOf(
                    tele(2673, 3298, 1, 737),
                    tele(2673, 3298, 2, 737),
                    tele(2671, 3299, 3, 737, land = 2588),
                ),
            "ardougne:2" to
                listOf(
                    tele(2667, 3311, 1, 2586, land = 2588),
                    tele(2665, 3315, 1, 2586, land = 2588),
                    tele(2665, 3318, 3, 2583, land = 2588),
                ),
            "ardougne:3" to
                listOf(move(2656, 3318, 3, 763, 9, renderStyle = AgilityRenderStyle.Balance)),
            "ardougne:4" to listOf(tele(2653, 3314, 3, 2586, land = 2588)),
            "ardougne:5" to listOf(tele(2651, 3309, 3, 7133, land = 2588)),
            "ardougne:6" to
                listOf(
                    move(2654, 3299, 3, 753, 2, renderStyle = AgilityRenderStyle.Wall),
                    move(2656, 3297, 3, renderStyle = AgilityRenderStyle.Wall),
                    move(2656, 3297, 3, 759, 2),
                ),
            "ardougne:7" to
                listOf(
                    tele(2658, 3298, 1, 2586, land = 2588),
                    move(2661, 3298, 1, ticks = 2),
                    move(2663, 3297, 1, 741, 2, facing = AgilityRouteFacing.East),
                    move(2666, 3297, 1, ticks = 2),
                    move(2667, 3297, 1, 741),
                    tele(2668, 3297, 0, 2586, land = 2588),
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

    private val approaches: Map<String, CoordGrid> =
        mapOf(
            "al_kharid:5" to CoordGrid(3318, 3165, 1),
            "al_kharid:7" to CoordGrid(3314, 3186, 3),
            "canifis:1" to CoordGrid(3507, 3488, 0),
            "canifis:6" to CoordGrid(3480, 3485, 2),
            "falador:4" to CoordGrid(3048, 3358, 3),
            "falador:5" to CoordGrid(3045, 3361, 3),
            "falador:8" to CoordGrid(3016, 3353, 3),
            "varrock:4" to CoordGrid(3194, 3416, 1),
            "varrock:6" to CoordGrid(3208, 3397, 3),
            "pollnivneach:2" to CoordGrid(3350, 2968, 1),
            "pollnivneach:3" to CoordGrid(3355, 2976, 1),
            "pollnivneach:4" to CoordGrid(3362, 2977, 1),
            "pollnivneach:5" to CoordGrid(3367, 2976, 1),
            "pollnivneach:6" to CoordGrid(3366, 2982, 1),
            "pollnivneach:7" to CoordGrid(3365, 2983, 2),
            "pollnivneach:8" to CoordGrid(3359, 2995, 2),
            "pollnivneach:9" to CoordGrid(3362, 3002, 2),
            "rellekka:4" to CoordGrid(2628, 3654, 3),
        )

    public fun routeFor(courseKey: String, stage: Int): List<AgilityRouteStep> =
        routes["$courseKey:$stage"].orEmpty()

    public fun approachFor(courseKey: String, stage: Int): CoordGrid? =
        approaches["$courseKey:$stage"]
}
