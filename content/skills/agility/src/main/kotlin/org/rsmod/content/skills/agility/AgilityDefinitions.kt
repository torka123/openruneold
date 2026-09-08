package org.rsmod.content.skills.agility

import org.rsmod.map.CoordGrid

public enum class AgilityMovementStyle {
    Climb,
    Jump,
    Balance,
    Pipe,
}

public data class AgilityMovement(
    val destination: CoordGrid? = null,
    val deltaX: Int = 0,
    val deltaZ: Int = 0,
    val deltaLevel: Int = 0,
    val style: AgilityMovementStyle,
    val animation: Int,
    val durationTicks: Int = 2,
    val travelAnimation: Int? = null,
    val landingAnimation: Int? = null,
)

public data class AgilityObstacle(
    val objectId: Int,
    val action: String,
    val stage: Int,
    val baseXp: Double,
    val movement: AgilityMovement,
    val coordinate: CoordGrid? = null,
    val finalObstacle: Boolean = false,
    val lapBonusXp: Double = 0.0,
    val strengthBonusXp: Double = 0.0,
    val awardBaseWhenUnordered: Boolean = false,
)

public data class AgilityCourse(
    val key: String,
    val displayName: String,
    val requiredLevel: Int,
    val rooftop: Boolean,
    val obstacles: List<AgilityObstacle>,
    val markSpawns: List<CoordGrid> = emptyList(),
    val markNumerator: Int = 0,
    val markDenominator: Int = 1,
) {
    init {
        require(obstacles.isNotEmpty())
        require(obstacles.map { it.stage }.toSet().size <= obstacles.size)
        require(obstacles.any { it.stage == 1 })
        require(obstacles.any { it.finalObstacle })
        require(markNumerator in 0..markDenominator)
    }

    public val finalStage: Int = obstacles.maxOf { it.stage }
    /**
     * Alternative objects can complete the same stage (for example, either Gnome pipe). Count a
     * stage once so course totals and reporting never double-count alternatives.
     */
    public val fullLapBaseXp: Double =
        obstacles
            .groupBy { it.stage }
            .values
            .sumOf { alternatives ->
                val obstacle = alternatives.first()
                obstacle.baseXp + obstacle.lapBonusXp
            }
}

private fun absolute(
    x: Int,
    z: Int,
    level: Int,
    style: AgilityMovementStyle,
    animation: Int,
    ticks: Int = 2,
): AgilityMovement =
    AgilityMovement(
        CoordGrid(x, z, level),
        style = style,
        animation = animation,
        durationTicks = ticks,
    )

private fun relative(
    dx: Int,
    dz: Int,
    dl: Int = 0,
    style: AgilityMovementStyle,
    animation: Int,
    ticks: Int = 2,
    travelAnimation: Int? = null,
    landingAnimation: Int? = null,
): AgilityMovement =
    AgilityMovement(
        deltaX = dx,
        deltaZ = dz,
        deltaLevel = dl,
        style = style,
        animation = animation,
        durationTicks = ticks,
        travelAnimation = travelAnimation,
        landingAnimation = landingAnimation,
    )

private fun obstacle(
    id: Int,
    action: String,
    stage: Int,
    xp: Double,
    movement: AgilityMovement,
    coordinate: CoordGrid? = null,
    final: Boolean = false,
    bonus: Double = 0.0,
    strength: Double = 0.0,
    unorderedBase: Boolean = false,
): AgilityObstacle =
    AgilityObstacle(
        id,
        action,
        stage,
        xp,
        movement,
        coordinate,
        final,
        bonus,
        strength,
        unorderedBase,
    )

/**
 * Current OSRS base XP and revision-240 obstacle IDs. XP is deliberately not multiplied here;
 * [org.rsmod.api.player.protect.ProtectedAccess.statAdvance] owns OpenRune's XP multiplier.
 */
public object AgilityDefinitions {
    private const val CLIMB = 828
    private const val CLIMB_DOWN = 827
    private const val JUMP = 2586
    private const val BALANCE = 763
    private const val PIPE_ENTER = 749
    private const val PIPE_CRAWL = 749
    private const val PIPE_EXIT = 749
    private const val PIPE = 827
    private const val WALL = 839

    public val courses: List<AgilityCourse> =
        listOf(
            AgilityCourse(
                key = "gnome",
                displayName = "Gnome Stronghold course",
                requiredLevel = 1,
                rooftop = false,
                obstacles =
                    listOf(
                        obstacle(
                            23145,
                            "Walk-across",
                            1,
                            10.0,
                            absolute(2474, 3429, 0, AgilityMovementStyle.Balance, BALANCE, 5),
                        ),
                        obstacle(
                            23134,
                            "Climb-over",
                            2,
                            10.0,
                            absolute(2474, 3424, 1, AgilityMovementStyle.Climb, CLIMB),
                        ),
                        obstacle(
                            23559,
                            "Climb",
                            3,
                            6.5,
                            absolute(2473, 3420, 2, AgilityMovementStyle.Climb, CLIMB),
                        ),
                        obstacle(
                            23557,
                            "Walk-on",
                            4,
                            10.0,
                            absolute(2483, 3420, 2, AgilityMovementStyle.Balance, BALANCE, 5),
                        ),
                        obstacle(
                            23560,
                            "Climb-down",
                            5,
                            6.5,
                            absolute(2485, 3419, 0, AgilityMovementStyle.Climb, CLIMB),
                        ),
                        obstacle(
                            23135,
                            "Climb-over",
                            6,
                            10.0,
                            relative(0, 3, style = AgilityMovementStyle.Climb, animation = CLIMB),
                        ),
                        obstacle(
                            23138,
                            "Squeeze-through",
                            7,
                            7.5,
                            relative(
                                0,
                                6,
                                style = AgilityMovementStyle.Pipe,
                                animation = PIPE_ENTER,
                                ticks = 6,
                                travelAnimation = PIPE_CRAWL,
                                landingAnimation = PIPE_EXIT,
                            ),
                            final = true,
                            bonus = 50.0,
                            unorderedBase = true,
                        ),
                        obstacle(
                            23139,
                            "Squeeze-through",
                            7,
                            7.5,
                            relative(
                                0,
                                6,
                                style = AgilityMovementStyle.Pipe,
                                animation = PIPE_ENTER,
                                ticks = 6,
                                travelAnimation = PIPE_CRAWL,
                                landingAnimation = PIPE_EXIT,
                            ),
                            final = true,
                            bonus = 50.0,
                            unorderedBase = true,
                        ),
                    ),
                markSpawns =
                    listOf(
                        CoordGrid(2474, 3429),
                        CoordGrid(2473, 3420, 2),
                        CoordGrid(2483, 3420, 2),
                        CoordGrid(2485, 3428),
                    ),
                markNumerator = 1,
                markDenominator = 6,
            ),
            AgilityCourse(
                key = "barbarian",
                displayName = "Barbarian Outpost course",
                requiredLevel = 35,
                rooftop = false,
                obstacles =
                    listOf(
                        obstacle(
                            23131,
                            "Swing-on",
                            1,
                            22.0,
                            relative(0, -5, style = AgilityMovementStyle.Jump, animation = 751),
                        ),
                        obstacle(
                            23144,
                            "Walk-across",
                            2,
                            13.7,
                            absolute(2541, 3546, 0, AgilityMovementStyle.Balance, BALANCE, 8),
                        ),
                        obstacle(
                            20211,
                            "Climb-over",
                            3,
                            8.2,
                            relative(-2, 0, 1, AgilityMovementStyle.Climb, CLIMB),
                        ),
                        obstacle(
                            23547,
                            "Walk-across",
                            4,
                            22.0,
                            absolute(2532, 3546, 1, AgilityMovementStyle.Balance, BALANCE, 5),
                        ),
                        obstacle(
                            16682,
                            "Climb-down",
                            5,
                            0.0,
                            relative(0, 0, -1, AgilityMovementStyle.Climb, CLIMB_DOWN),
                        ),
                        obstacle(
                            1948,
                            "Climb-over",
                            6,
                            13.7,
                            relative(2, 0, style = AgilityMovementStyle.Jump, animation = WALL),
                            CoordGrid(2536, 3553),
                        ),
                        obstacle(
                            1948,
                            "Climb-over",
                            7,
                            13.7,
                            relative(2, 0, style = AgilityMovementStyle.Jump, animation = WALL),
                            CoordGrid(2539, 3553),
                        ),
                        obstacle(
                            1948,
                            "Climb-over",
                            8,
                            13.7,
                            relative(2, 0, style = AgilityMovementStyle.Jump, animation = WALL),
                            CoordGrid(2542, 3553),
                            final = true,
                            bonus = 46.3,
                            strength = 41.3,
                            unorderedBase = true,
                        ),
                    ),
                markSpawns =
                    listOf(CoordGrid(2541, 3546), CoordGrid(2532, 3546, 1), CoordGrid(2538, 3553)),
                markNumerator = 1,
                markDenominator = 5,
            ),
            rooftop(
                "draynor",
                "Draynor Village rooftop",
                1,
                listOf(
                    obstacle(
                        11404,
                        "Climb",
                        1,
                        5.0,
                        absolute(3102, 3279, 3, AgilityMovementStyle.Climb, CLIMB),
                    ),
                    obstacle(
                        11405,
                        "Cross",
                        2,
                        8.0,
                        absolute(3090, 3276, 3, AgilityMovementStyle.Balance, BALANCE, 6),
                    ),
                    obstacle(
                        11406,
                        "Cross",
                        3,
                        7.0,
                        absolute(3092, 3266, 3, AgilityMovementStyle.Balance, BALANCE, 6),
                    ),
                    obstacle(
                        11430,
                        "Balance",
                        4,
                        7.0,
                        absolute(3088, 3261, 3, AgilityMovementStyle.Balance, BALANCE, 4),
                    ),
                    obstacle(
                        11630,
                        "Jump-up",
                        5,
                        10.0,
                        absolute(3088, 3255, 3, AgilityMovementStyle.Jump, JUMP),
                    ),
                    obstacle(
                        11631,
                        "Jump",
                        6,
                        4.0,
                        absolute(3096, 3256, 3, AgilityMovementStyle.Jump, JUMP),
                    ),
                    obstacle(
                        11632,
                        "Climb-down",
                        7,
                        79.0,
                        absolute(3103, 3261, 0, AgilityMovementStyle.Climb, CLIMB),
                        final = true,
                    ),
                ),
                listOf(
                    CoordGrid(3099, 3280, 3),
                    CoordGrid(3089, 3274, 3),
                    CoordGrid(3094, 3266, 3),
                    CoordGrid(3088, 3259, 3),
                    CoordGrid(3092, 3255, 3),
                    CoordGrid(3099, 3257, 3),
                ),
            ),
            rooftop(
                "al_kharid",
                "Al Kharid rooftop",
                20,
                listOf(
                    obstacle(
                        11633,
                        "Climb",
                        1,
                        12.0,
                        absolute(3273, 3192, 3, AgilityMovementStyle.Climb, CLIMB),
                    ),
                    obstacle(
                        14398,
                        "Cross",
                        2,
                        36.0,
                        absolute(3272, 3172, 3, AgilityMovementStyle.Balance, BALANCE, 8),
                    ),
                    obstacle(
                        14402,
                        "Swing-across",
                        3,
                        48.0,
                        absolute(3284, 3166, 3, AgilityMovementStyle.Jump, 751, 4),
                    ),
                    obstacle(
                        14403,
                        "Teeth-grip",
                        4,
                        48.0,
                        absolute(3315, 3163, 1, AgilityMovementStyle.Balance, BALANCE, 8),
                    ),
                    obstacle(
                        14404,
                        "Swing-across",
                        5,
                        12.0,
                        absolute(3317, 3174, 2, AgilityMovementStyle.Jump, JUMP, 4),
                    ),
                    obstacle(
                        11634,
                        "Climb",
                        6,
                        6.0,
                        absolute(3316, 3180, 3, AgilityMovementStyle.Climb, CLIMB),
                    ),
                    obstacle(
                        14409,
                        "Cross",
                        7,
                        18.0,
                        absolute(3302, 3187, 3, AgilityMovementStyle.Balance, BALANCE, 6),
                    ),
                    obstacle(
                        14399,
                        "Jump",
                        8,
                        36.0,
                        absolute(3299, 3194, 0, AgilityMovementStyle.Jump, JUMP),
                        final = true,
                    ),
                ),
                listOf(
                    CoordGrid(3276, 3188, 3),
                    CoordGrid(3273, 3182, 3),
                    CoordGrid(3267, 3171, 3),
                    CoordGrid(3297, 3168, 3),
                    CoordGrid(3316, 3161, 1),
                    CoordGrid(3315, 3183, 3),
                    CoordGrid(3302, 3189, 3),
                ),
            ),
            rooftop(
                "varrock",
                "Varrock rooftop",
                30,
                listOf(
                    obstacle(
                        14412,
                        "Climb",
                        1,
                        13.5,
                        absolute(3219, 3414, 3, AgilityMovementStyle.Climb, CLIMB),
                    ),
                    obstacle(
                        14413,
                        "Cross",
                        2,
                        23.0,
                        absolute(3208, 3414, 3, AgilityMovementStyle.Jump, JUMP, 5),
                    ),
                    obstacle(
                        14414,
                        "Leap",
                        3,
                        19.0,
                        absolute(3197, 3416, 1, AgilityMovementStyle.Jump, JUMP),
                    ),
                    obstacle(
                        14832,
                        "Balance",
                        4,
                        28.0,
                        absolute(3192, 3406, 3, AgilityMovementStyle.Balance, BALANCE, 8),
                    ),
                    obstacle(
                        14833,
                        "Leap",
                        5,
                        10.0,
                        absolute(3192, 3398, 3, AgilityMovementStyle.Jump, JUMP),
                    ),
                    obstacle(
                        14834,
                        "Leap",
                        6,
                        24.5,
                        absolute(3218, 3399, 3, AgilityMovementStyle.Jump, JUMP, 5),
                    ),
                    obstacle(
                        14835,
                        "Leap",
                        7,
                        4.5,
                        absolute(3236, 3403, 3, AgilityMovementStyle.Jump, JUMP),
                    ),
                    obstacle(
                        14836,
                        "Hurdle",
                        8,
                        3.5,
                        absolute(3236, 3410, 3, AgilityMovementStyle.Jump, JUMP),
                    ),
                    obstacle(
                        14841,
                        "Jump-off",
                        9,
                        143.7,
                        absolute(3236, 3417, 0, AgilityMovementStyle.Jump, JUMP),
                        final = true,
                    ),
                ),
                listOf(
                    CoordGrid(3214, 3417, 3),
                    CoordGrid(3202, 3417, 3),
                    CoordGrid(3194, 3416, 1),
                    CoordGrid(3194, 3404, 3),
                    CoordGrid(3196, 3394, 3),
                    CoordGrid(3205, 3395, 3),
                    CoordGrid(3226, 3402, 3),
                    CoordGrid(3236, 3407, 3),
                ),
            ),
            rooftop(
                "canifis",
                "Canifis rooftop",
                40,
                listOf(
                    obstacle(
                        14843,
                        "Climb",
                        1,
                        10.0,
                        absolute(3506, 3492, 2, AgilityMovementStyle.Climb, CLIMB),
                    ),
                    obstacle(
                        14844,
                        "Jump",
                        2,
                        8.0,
                        absolute(3502, 3504, 2, AgilityMovementStyle.Jump, JUMP),
                    ),
                    obstacle(
                        14845,
                        "Jump",
                        3,
                        8.0,
                        absolute(3493, 3504, 2, AgilityMovementStyle.Jump, JUMP),
                    ),
                    obstacle(
                        14848,
                        "Jump",
                        4,
                        10.0,
                        absolute(3479, 3499, 3, AgilityMovementStyle.Jump, JUMP, 4),
                    ),
                    obstacle(
                        14846,
                        "Jump",
                        5,
                        8.0,
                        absolute(3478, 3486, 2, AgilityMovementStyle.Jump, JUMP),
                    ),
                    obstacle(
                        14894,
                        "Vault",
                        6,
                        10.0,
                        absolute(3489, 3476, 3, AgilityMovementStyle.Jump, JUMP, 6),
                    ),
                    obstacle(
                        14847,
                        "Jump",
                        7,
                        11.0,
                        absolute(3510, 3476, 2, AgilityMovementStyle.Jump, JUMP, 4),
                    ),
                    obstacle(
                        14897,
                        "Jump",
                        8,
                        175.0,
                        absolute(3510, 3485, 0, AgilityMovementStyle.Jump, JUMP),
                        final = true,
                    ),
                ),
                listOf(
                    CoordGrid(3508, 3494, 2),
                    CoordGrid(3502, 3506, 2),
                    CoordGrid(3489, 3500, 2),
                    CoordGrid(3476, 3496, 3),
                    CoordGrid(3482, 3486, 2),
                    CoordGrid(3493, 3476, 3),
                    CoordGrid(3513, 3479, 2),
                ),
                markNumerator = 2,
                markDenominator = 3,
            ),
            rooftop(
                "falador",
                "Falador rooftop",
                50,
                listOf(
                    obstacle(
                        14898,
                        "Climb",
                        1,
                        11.0,
                        absolute(3036, 3342, 3, AgilityMovementStyle.Climb, CLIMB),
                    ),
                    obstacle(
                        14899,
                        "Cross",
                        2,
                        22.0,
                        absolute(3047, 3343, 3, AgilityMovementStyle.Balance, BALANCE, 5),
                    ),
                    obstacle(
                        14901,
                        "Cross",
                        3,
                        61.0,
                        absolute(3050, 3357, 3, AgilityMovementStyle.Balance, BALANCE, 6),
                    ),
                    obstacle(
                        14903,
                        "Jump",
                        4,
                        27.0,
                        absolute(3048, 3361, 3, AgilityMovementStyle.Jump, JUMP),
                    ),
                    obstacle(
                        14904,
                        "Jump",
                        5,
                        26.0,
                        absolute(3041, 3361, 3, AgilityMovementStyle.Jump, JUMP),
                    ),
                    obstacle(
                        14905,
                        "Cross",
                        6,
                        61.0,
                        absolute(3028, 3354, 3, AgilityMovementStyle.Balance, BALANCE, 7),
                    ),
                    obstacle(
                        14911,
                        "Cross",
                        7,
                        53.0,
                        absolute(3020, 3353, 3, AgilityMovementStyle.Balance, BALANCE, 5),
                    ),
                    obstacle(
                        14919,
                        "Jump",
                        8,
                        30.0,
                        relative(0, -4, style = AgilityMovementStyle.Jump, animation = 1603),
                    ),
                    obstacle(
                        14920,
                        "Jump",
                        9,
                        14.0,
                        relative(-2, 0, style = AgilityMovementStyle.Jump, animation = 1603),
                    ),
                    obstacle(
                        14921,
                        "Jump",
                        10,
                        13.0,
                        relative(0, -2, style = AgilityMovementStyle.Jump, animation = 1603),
                    ),
                    obstacle(
                        14922,
                        "Jump",
                        11,
                        13.0,
                        relative(0, -2, style = AgilityMovementStyle.Jump, animation = 1603),
                    ),
                    obstacle(
                        14924,
                        "Jump",
                        12,
                        14.0,
                        relative(2, 0, style = AgilityMovementStyle.Jump, animation = 1603),
                    ),
                    obstacle(
                        14925,
                        "Jump",
                        13,
                        241.0,
                        absolute(3029, 3333, 0, AgilityMovementStyle.Jump, JUMP),
                        final = true,
                    ),
                ),
                listOf(
                    CoordGrid(3038, 3343, 3),
                    CoordGrid(3049, 3348, 3),
                    CoordGrid(3049, 3357, 3),
                    CoordGrid(3045, 3365, 3),
                    CoordGrid(3035, 3362, 3),
                    CoordGrid(3028, 3353, 3),
                    CoordGrid(3017, 3345, 3),
                    CoordGrid(3011, 3339, 3),
                    CoordGrid(3016, 3333, 3),
                ),
            ),
            rooftop(
                "seers",
                "Seers' Village rooftop",
                60,
                listOf(
                    obstacle(
                        14927,
                        "Climb-up",
                        1,
                        45.0,
                        absolute(2729, 3491, 3, AgilityMovementStyle.Climb, CLIMB),
                    ),
                    obstacle(
                        14928,
                        "Jump",
                        2,
                        20.0,
                        absolute(2713, 3494, 2, AgilityMovementStyle.Jump, JUMP, 4),
                    ),
                    obstacle(
                        14932,
                        "Cross",
                        3,
                        20.0,
                        absolute(2710, 3481, 2, AgilityMovementStyle.Balance, BALANCE, 7),
                    ),
                    obstacle(
                        14929,
                        "Jump",
                        4,
                        35.0,
                        absolute(2710, 3472, 3, AgilityMovementStyle.Jump, JUMP, 4),
                    ),
                    obstacle(
                        14930,
                        "Jump",
                        5,
                        15.0,
                        absolute(2702, 3465, 2, AgilityMovementStyle.Jump, JUMP),
                    ),
                    obstacle(
                        14931,
                        "Jump",
                        6,
                        435.0,
                        absolute(2704, 3464, 0, AgilityMovementStyle.Jump, JUMP),
                        final = true,
                    ),
                ),
                listOf(
                    CoordGrid(2726, 3492, 3),
                    CoordGrid(2707, 3493, 2),
                    CoordGrid(2712, 3481, 2),
                    CoordGrid(2710, 3472, 3),
                    CoordGrid(2702, 3474, 3),
                    CoordGrid(2698, 3462, 2),
                ),
            ),
            rooftop(
                "pollnivneach",
                "Pollnivneach rooftop",
                70,
                listOf(
                    obstacle(
                        14935,
                        "Climb-on",
                        1,
                        10.0,
                        absolute(3351, 2964, 1, AgilityMovementStyle.Climb, CLIMB),
                    ),
                    obstacle(
                        14936,
                        "Jump-on",
                        2,
                        45.0,
                        absolute(3352, 2973, 1, AgilityMovementStyle.Jump, JUMP),
                    ),
                    obstacle(
                        14937,
                        "Grab",
                        3,
                        65.0,
                        absolute(3360, 2978, 1, AgilityMovementStyle.Jump, 751),
                    ),
                    obstacle(
                        14938,
                        "Leap",
                        4,
                        35.0,
                        absolute(3366, 2976, 1, AgilityMovementStyle.Jump, JUMP),
                    ),
                    obstacle(
                        14939,
                        "Jump-to",
                        5,
                        75.0,
                        absolute(3366, 2982, 1, AgilityMovementStyle.Jump, JUMP, 4),
                    ),
                    obstacle(
                        14940,
                        "Climb",
                        6,
                        5.0,
                        absolute(3365, 2983, 2, AgilityMovementStyle.Climb, CLIMB),
                    ),
                    obstacle(
                        14941,
                        "Cross",
                        7,
                        55.0,
                        absolute(3365, 2994, 2, AgilityMovementStyle.Balance, BALANCE, 7),
                    ),
                    obstacle(
                        14944,
                        "Jump-on",
                        8,
                        60.0,
                        absolute(3359, 3000, 2, AgilityMovementStyle.Jump, JUMP, 4),
                    ),
                    obstacle(
                        14945,
                        "Jump-to",
                        9,
                        540.0,
                        absolute(3364, 2998, 0, AgilityMovementStyle.Jump, JUMP),
                        final = true,
                    ),
                ),
                listOf(
                    CoordGrid(3349, 2967, 1),
                    CoordGrid(3354, 2974, 1),
                    CoordGrid(3362, 2979, 1),
                    CoordGrid(3369, 2974, 1),
                    CoordGrid(3365, 2985, 1),
                    CoordGrid(3361, 2981, 2),
                    CoordGrid(3362, 2994, 2),
                    CoordGrid(3356, 3004, 2),
                ),
            ),
            rooftop(
                "rellekka",
                "Rellekka rooftop",
                80,
                listOf(
                    obstacle(
                        14946,
                        "Climb",
                        1,
                        20.0,
                        absolute(2626, 3676, 3, AgilityMovementStyle.Climb, CLIMB),
                    ),
                    obstacle(
                        14947,
                        "Leap",
                        2,
                        30.0,
                        absolute(2625, 3672, 3, AgilityMovementStyle.Jump, JUMP),
                    ),
                    obstacle(
                        14987,
                        "Cross",
                        3,
                        40.0,
                        absolute(2627, 3654, 3, AgilityMovementStyle.Balance, BALANCE, 8),
                    ),
                    obstacle(
                        14990,
                        "Leap",
                        4,
                        85.0,
                        absolute(2639, 3653, 3, AgilityMovementStyle.Jump, JUMP, 6),
                    ),
                    obstacle(
                        14991,
                        "Hurdle",
                        5,
                        25.0,
                        relative(0, 4, style = AgilityMovementStyle.Jump, animation = JUMP),
                    ),
                    obstacle(
                        14992,
                        "Cross",
                        6,
                        105.0,
                        absolute(2655, 3670, 3, AgilityMovementStyle.Balance, BALANCE, 8),
                    ),
                    obstacle(
                        14994,
                        "Jump-in",
                        7,
                        475.0,
                        absolute(2652, 3676, 0, AgilityMovementStyle.Jump, JUMP),
                        final = true,
                    ),
                ),
                listOf(
                    CoordGrid(2622, 3676, 3),
                    CoordGrid(2617, 3664, 3),
                    CoordGrid(2628, 3652, 3),
                    CoordGrid(2641, 3649, 3),
                    CoordGrid(2649, 3659, 3),
                    CoordGrid(2658, 3674, 3),
                    CoordGrid(2656, 3681, 3),
                ),
                markNumerator = 2,
                markDenominator = 5,
            ),
            rooftop(
                "ardougne",
                "Ardougne rooftop",
                90,
                listOf(
                    obstacle(
                        15608,
                        "Climb-up",
                        1,
                        43.0,
                        absolute(2671, 3299, 3, AgilityMovementStyle.Climb, CLIMB, 5),
                    ),
                    obstacle(
                        15609,
                        "Jump",
                        2,
                        65.0,
                        absolute(2665, 3318, 3, AgilityMovementStyle.Jump, JUMP, 5),
                    ),
                    obstacle(
                        26635,
                        "Walk-on",
                        3,
                        50.0,
                        absolute(2656, 3318, 3, AgilityMovementStyle.Balance, BALANCE, 6),
                    ),
                    obstacle(
                        15610,
                        "Jump",
                        4,
                        21.0,
                        absolute(2653, 3314, 3, AgilityMovementStyle.Jump, JUMP),
                    ),
                    obstacle(
                        15611,
                        "Jump",
                        5,
                        28.0,
                        absolute(2651, 3309, 3, AgilityMovementStyle.Jump, JUMP),
                    ),
                    obstacle(
                        28912,
                        "Balance-across",
                        6,
                        57.0,
                        absolute(2656, 3297, 3, AgilityMovementStyle.Balance, BALANCE, 6),
                    ),
                    obstacle(
                        15612,
                        "Jump",
                        7,
                        625.0,
                        absolute(2668, 3297, 0, AgilityMovementStyle.Jump, JUMP, 6),
                        final = true,
                    ),
                ),
                listOf(
                    CoordGrid(2671, 3304, 3),
                    CoordGrid(2663, 3318, 3),
                    CoordGrid(2654, 3318, 3),
                    CoordGrid(2653, 3313, 3),
                    CoordGrid(2653, 3302, 3),
                ),
                markNumerator = 2,
                markDenominator = 3,
            ),
            AgilityCourse(
                key = "prifddinas",
                displayName = "Prifddinas course",
                requiredLevel = 75,
                rooftop = false,
                obstacles =
                    listOf(
                        obstacle(
                            36221,
                            "Climb",
                            1,
                            11.5,
                            absolute(3255, 6109, 2, AgilityMovementStyle.Climb, CLIMB),
                        ),
                        obstacle(
                            36225,
                            "Cross",
                            2,
                            30.7,
                            absolute(3272, 6105, 2, AgilityMovementStyle.Balance, BALANCE, 7),
                        ),
                        obstacle(
                            36227,
                            "Jump",
                            3,
                            28.1,
                            absolute(3269, 6112, 2, AgilityMovementStyle.Jump, JUMP, 4),
                        ),
                        obstacle(
                            36228,
                            "Jump",
                            4,
                            23.0,
                            absolute(3269, 6117, 0, AgilityMovementStyle.Jump, JUMP),
                        ),
                        obstacle(
                            36229,
                            "Enter",
                            5,
                            11.5,
                            absolute(3293, 6141, 0, AgilityMovementStyle.Pipe, PIPE, 4),
                        ),
                        obstacle(
                            36231,
                            "Climb",
                            6,
                            0.0,
                            absolute(3293, 6145, 2, AgilityMovementStyle.Climb, CLIMB),
                        ),
                        obstacle(
                            36233,
                            "Cross",
                            7,
                            25.6,
                            absolute(3281, 6142, 2, AgilityMovementStyle.Balance, BALANCE, 6),
                        ),
                        obstacle(
                            36234,
                            "Cross",
                            8,
                            30.7,
                            absolute(3271, 6149, 2, AgilityMovementStyle.Balance, BALANCE, 6),
                        ),
                        obstacle(
                            36235,
                            "Cross",
                            9,
                            25.6,
                            absolute(3270, 6158, 2, AgilityMovementStyle.Balance, BALANCE, 5),
                        ),
                        obstacle(
                            36236,
                            "Cross",
                            10,
                            30.7,
                            absolute(3274, 6168, 2, AgilityMovementStyle.Balance, BALANCE, 6),
                        ),
                        obstacle(
                            36237,
                            "Cross",
                            11,
                            30.7,
                            absolute(3284, 6177, 0, AgilityMovementStyle.Balance, BALANCE, 6),
                        ),
                        obstacle(
                            36238,
                            "Enter",
                            12,
                            1037.1,
                            absolute(3240, 6109, 0, AgilityMovementStyle.Pipe, PIPE, 5),
                            final = true,
                        ),
                    ),
                markSpawns =
                    listOf(
                        CoordGrid(3272, 6105, 2),
                        CoordGrid(3281, 6142, 2),
                        CoordGrid(3270, 6158, 2),
                        CoordGrid(3284, 6177, 0),
                    ),
                markNumerator = 1,
                markDenominator = 6,
            ),
        )

    public val obstacleIds: Set<Int> =
        courses.flatMap { it.obstacles }.mapTo(linkedSetOf()) { it.objectId }

    public fun resolve(objectId: Int, coords: CoordGrid): Pair<AgilityCourse, AgilityObstacle>? {
        for (course in courses) {
            val matches = course.obstacles.filter { it.objectId == objectId }
            if (matches.isEmpty()) continue
            val exact = matches.firstOrNull { it.coordinate == coords }
            if (exact != null) return course to exact
            val generic = matches.singleOrNull { it.coordinate == null }
            if (generic != null) return course to generic
        }
        return null
    }

    private fun rooftop(
        key: String,
        name: String,
        level: Int,
        obstacles: List<AgilityObstacle>,
        marks: List<CoordGrid>,
        markNumerator: Int = 2,
        markDenominator: Int = 6,
    ): AgilityCourse =
        AgilityCourse(key, name, level, true, obstacles, marks, markNumerator, markDenominator)
}
