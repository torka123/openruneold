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
}
