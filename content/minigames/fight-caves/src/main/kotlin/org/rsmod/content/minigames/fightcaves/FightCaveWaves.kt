package org.rsmod.content.minigames.fightcaves

import org.rsmod.map.CoordGrid

internal enum class FightCaveMonster(
    val cacheId: Int,
    val internalName: String,
    val displayName: String,
    val combatLevel: Int,
    val attackRange: Int,
    val footprintSize: Int,
) {
    TzKih(3116, "npc.tzhaar_fightcave_swarm_1a", "Tz-Kih", 22, 1, 1),
    TzKek(3118, "npc.tzhaar_fightcave_swarm_2a", "Tz-Kek", 45, 1, 2),
    TzKekSpawn(3120, "npc.tzhaar_fightcave_swarm_2spawn", "Tz-Kek", 22, 1, 1),
    TokXil(3121, "npc.tzhaar_fightcave_swarm_3a", "Tok-Xil", 90, 15, 3),
    YtMejKot(3123, "npc.tzhaar_fightcave_swarm_4a", "Yt-MejKot", 180, 1, 4),
    KetZek(3125, "npc.tzhaar_fightcave_swarm_5a", "Ket-Zek", 360, 15, 5),
    KetZekOrange(3126, "npc.tzhaar_fightcave_swarm_5b", "Ket-Zek", 360, 15, 5),
    TzTokJad(3127, "npc.tzhaar_fightcave_swarm_boss", "TzTok-Jad", 702, 15, 5),
    YtHurKot(3128, "npc.tzhaar_fightcave_swarm_boss_cleric", "Yt-HurKot", 108, 1, 1);

    val splitsWhenKilled: Boolean
        get() = this == TzKek
}

/**
 * One deterministic Fight Cave layout. These points sit inside the revision-240 arena and keep
 * every 1x1 through 5x5 footprint inside its walkable map bounds. Runtime collision validation
 * deterministically selects the next point if another spawn already occupies a footprint.
 */
internal enum class FightCaveSpawnPoint(
    val templateCoords: CoordGrid,
    val directionX: Int,
    val directionZ: Int,
) {
    North(CoordGrid(2411, 5111), 0, 1),
    NorthEast(CoordGrid(2418, 5111), 1, 1),
    East(CoordGrid(2418, 5104), 1, 0),
    SouthEast(CoordGrid(2418, 5104), 1, -1),
    South(CoordGrid(2411, 5104), 0, -1),
    SouthWest(CoordGrid(2404, 5111), -1, -1),
    West(CoordGrid(2404, 5111), -1, 0),
    NorthWest(CoordGrid(2404, 5104), -1, 1),
}


internal data class FightCaveSpawn(val monster: FightCaveMonster, val point: FightCaveSpawnPoint)

internal data class FightCaveWave(
    val displayNumber: Int,
    val osrsNumber: Int,
    val spawns: List<FightCaveSpawn>,
)

internal data class FightCaveRotation(val name: String, val waves: List<FightCaveWave>)

internal val FIGHT_CAVE_ROTATION =
    FightCaveRotation(
        name = "Fixed rotation 1",
        waves =
            listOf(
                FightCaveWave(
                    displayNumber = 1,
                    osrsNumber = 57,
                    spawns =
                        listOf(
                            FightCaveSpawn(FightCaveMonster.KetZek, FightCaveSpawnPoint.North),
                            FightCaveSpawn(FightCaveMonster.YtMejKot, FightCaveSpawnPoint.East),
                            FightCaveSpawn(FightCaveMonster.TokXil, FightCaveSpawnPoint.South),
                            FightCaveSpawn(FightCaveMonster.TzKek, FightCaveSpawnPoint.West),
                            FightCaveSpawn(FightCaveMonster.TzKih, FightCaveSpawnPoint.NorthWest),
                        ),
                ),
                FightCaveWave(
                    displayNumber = 2,
                    osrsNumber = 58,
                    spawns =
                        listOf(
                            FightCaveSpawn(FightCaveMonster.KetZek, FightCaveSpawnPoint.North),
                            FightCaveSpawn(FightCaveMonster.YtMejKot, FightCaveSpawnPoint.East),
                            FightCaveSpawn(FightCaveMonster.TokXil, FightCaveSpawnPoint.South),
                            FightCaveSpawn(FightCaveMonster.TzKek, FightCaveSpawnPoint.West),
                            FightCaveSpawn(FightCaveMonster.TzKih, FightCaveSpawnPoint.NorthEast),
                            FightCaveSpawn(FightCaveMonster.TzKih, FightCaveSpawnPoint.NorthWest),
                        ),
                ),
                FightCaveWave(
                    displayNumber = 3,
                    osrsNumber = 59,
                    spawns =
                        listOf(
                            FightCaveSpawn(FightCaveMonster.KetZek, FightCaveSpawnPoint.North),
                            FightCaveSpawn(FightCaveMonster.YtMejKot, FightCaveSpawnPoint.East),
                            FightCaveSpawn(FightCaveMonster.TokXil, FightCaveSpawnPoint.South),
                            FightCaveSpawn(FightCaveMonster.TzKek, FightCaveSpawnPoint.NorthWest),
                            FightCaveSpawn(FightCaveMonster.TzKek, FightCaveSpawnPoint.SouthWest),
                        ),
                ),
                FightCaveWave(
                    displayNumber = 4,
                    osrsNumber = 60,
                    spawns =
                        listOf(
                            FightCaveSpawn(FightCaveMonster.KetZek, FightCaveSpawnPoint.North),
                            FightCaveSpawn(FightCaveMonster.YtMejKot, FightCaveSpawnPoint.East),
                            FightCaveSpawn(FightCaveMonster.TokXil, FightCaveSpawnPoint.South),
                            FightCaveSpawn(FightCaveMonster.TokXil, FightCaveSpawnPoint.West),
                        ),
                ),
                FightCaveWave(
                    displayNumber = 5,
                    osrsNumber = 61,
                    spawns =
                        listOf(
                            FightCaveSpawn(FightCaveMonster.KetZek, FightCaveSpawnPoint.North),
                            FightCaveSpawn(
                                FightCaveMonster.YtMejKot,
                                FightCaveSpawnPoint.SouthEast,
                            ),
                            FightCaveSpawn(FightCaveMonster.YtMejKot, FightCaveSpawnPoint.SouthWest),
                        ),
                ),
                FightCaveWave(
                    displayNumber = 6,
                    osrsNumber = 62,
                    spawns =
                        listOf(
                            FightCaveSpawn(FightCaveMonster.KetZek, FightCaveSpawnPoint.North),
                            FightCaveSpawn(FightCaveMonster.KetZekOrange, FightCaveSpawnPoint.South),
                        ),
                ),
                FightCaveWave(
                    displayNumber = 7,
                    osrsNumber = 63,
                    spawns =
                        listOf(FightCaveSpawn(FightCaveMonster.TzTokJad, FightCaveSpawnPoint.South)),
                ),
            ),
    )

internal val FIGHT_CAVE_WAVES: List<FightCaveWave>
    get() = FIGHT_CAVE_ROTATION.waves

internal val FIGHT_CAVE_TEMPLATE_ENTRY = CoordGrid(2413, 5117)
internal val FIGHT_CAVE_TEMPLATE_EXIT_OBJECT = CoordGrid(2412, 5118)
internal val FIGHT_CAVE_ENTRANCE_OBJECT = CoordGrid(2437, 5166)
internal val FIGHT_CAVE_OUTSIDE = CoordGrid(2438, 5168)
/** Full source bounds copied by regions 9551 and 9552 into each private instance. */
internal val FIGHT_CAVE_TEMPLATE_MIN = CoordGrid(2368, 5056)
internal val FIGHT_CAVE_TEMPLATE_MAX = CoordGrid(2431, 5183)

internal const val FIGHT_CAVE_ENTRANCE_LOC = "loc.tzhaar_fightcave_wall_entrance"
internal const val FIGHT_CAVE_EXIT_LOC = "loc.tzhaar_fightcave_wall_exit"
internal const val FIGHT_CAVE_INSTANCE_KEY = "fight_caves_shortened"
internal const val FIGHT_CAVE_COUNTDOWN_TICKS = 17
internal const val FIGHT_CAVE_INTERMISSION_TICKS = 17
internal const val TZHAAR_HEAL_SPOTANIM = "spotanim.tzhaar_heal"
internal const val TZHAAR_HEAL_SPOTANIM_ID = 444

internal fun crossedHalfHealth(previous: Int, current: Int, maximum: Int): Boolean =
    previous > 0 && previous * 2 > maximum && current > 0 && current * 2 <= maximum

/**
 * Builds deterministic rings around the player's current tile. The returned coordinates are the
 * south-west footprint anchors, so even 5x5 monsters remain evenly separated from the player.
 */
internal fun surroundingSpawnCandidates(
    player: CoordGrid,
    npcSize: Int,
    preferred: FightCaveSpawnPoint,
): List<CoordGrid> {
    require(npcSize > 0) { "npcSize must be positive: $npcSize" }
    val directions =
        FightCaveSpawnPoint.entries.let { points ->
            points.drop(preferred.ordinal) + points.take(preferred.ordinal)
        }
    return FIGHT_CAVE_SURROUND_RADII.flatMap { radius ->
        directions.map { point ->
            val x =
                when (point.directionX) {
                    1 -> player.x + radius
                    -1 -> player.x - radius - npcSize + 1
                    else -> player.x - npcSize / 2
                }
            val z =
                when (point.directionZ) {
                    1 -> player.z + radius
                    -1 -> player.z - radius - npcSize + 1
                    else -> player.z - npcSize / 2
                }
            CoordGrid(x, z, player.level)
        }
    }
}

private val FIGHT_CAVE_SURROUND_RADII = listOf(6, 9, 12)

internal fun isFightCaveEntrance(coords: CoordGrid): Boolean = coords == FIGHT_CAVE_ENTRANCE_OBJECT

internal fun splitChildCoords(parent: CoordGrid): List<CoordGrid> =
    listOf(parent, parent.translateX(1))
