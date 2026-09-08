package org.rsmod.api.area.checker

import org.rsmod.map.CoordGrid

public fun CoordGrid.isInWilderness(areaChecker: AreaChecker): Boolean {
    return areaChecker.inArea("area.wilderness", this)
}

public fun CoordGrid.isInWildernessBasic(): Boolean {
    if (isInCorporealBeastLair()) return true
    if (isInWildernessSlayerCave()) return true
    if (isInEdgevilleDungeon()) return true
    if (level != 0) return false
    return x in SURFACE_MIN_X..SURFACE_MAX_X && z in SURFACE_MIN_Z..SURFACE_MAX_Z
}

public fun CoordGrid.isInCorporealBeastLair(): Boolean =
    level == CORPOREAL_BEAST_LAIR_LEVEL &&
        x in CORPOREAL_BEAST_LAIR_MIN_X..CORPOREAL_BEAST_LAIR_MAX_X &&
        (z in CORPOREAL_BEAST_ENTRANCE_MIN_Z..CORPOREAL_BEAST_ENTRANCE_MAX_Z ||
            z in CORPOREAL_BEAST_LAIR_MIN_Z..CORPOREAL_BEAST_LAIR_MAX_Z)

public fun CoordGrid.corporealBeastLairWildernessLevel(): Int {
    if (!isInCorporealBeastLair()) return -1
    if (z in CORPOREAL_BEAST_ENTRANCE_MIN_Z..CORPOREAL_BEAST_ENTRANCE_MAX_Z) {
        return CORPOREAL_BEAST_LAIR_MIN_WILDERNESS_LEVEL
    }
    val levelIncrease =
        (x - CORPOREAL_BEAST_LAIR_ENTRY_X).coerceAtLeast(0) / WILDERNESS_LEVEL_TILE_WIDTH
    return (CORPOREAL_BEAST_LAIR_MIN_WILDERNESS_LEVEL + levelIncrease).coerceAtMost(
        CORPOREAL_BEAST_LAIR_MAX_WILDERNESS_LEVEL
    )
}

public fun CoordGrid.isInEdgevilleDungeon(): Boolean {
    if (level != 0) return false
    return (x in 3072..3135 && z in 9792..9860) ||
        (x in 3085..3144 && z in 9861..9919) ||
        (x in 3145..3175 && z in 9861..9920) ||
        (x in 3076..3144 && z in 9920..10015)
}

public fun CoordGrid.edgevilleDungeonWildernessLevel(): Int =
    if (isInEdgevilleDungeon()) EDGEVILLE_DUNGEON_WILDERNESS_LEVEL else -1

private fun CoordGrid.isInWildernessSlayerCave(): Boolean =
    level in 0..3 &&
        x in SLAYER_CAVE_MIN_X..SLAYER_CAVE_MAX_X &&
        z in SLAYER_CAVE_MIN_Z..SLAYER_CAVE_MAX_Z

private const val SURFACE_MIN_X = 2944
private const val SURFACE_MAX_X = 3391
private const val SURFACE_MIN_Z = 3520
private const val SURFACE_MAX_Z = 3967

private const val SLAYER_CAVE_MIN_X = 3328
private const val SLAYER_CAVE_MAX_X = 3455
private const val SLAYER_CAVE_MIN_Z = 10048
private const val SLAYER_CAVE_MAX_Z = 10175
public const val EDGEVILLE_DUNGEON_WILDERNESS_LEVEL: Int = 30

public const val CORPOREAL_BEAST_LAIR_MIN_WILDERNESS_LEVEL: Int = 20
public const val CORPOREAL_BEAST_LAIR_MAX_WILDERNESS_LEVEL: Int = 23

private const val CORPOREAL_BEAST_LAIR_LEVEL = 2
private const val CORPOREAL_BEAST_LAIR_ENTRY_X = 2964
private const val WILDERNESS_LEVEL_TILE_WIDTH = 8
private const val CORPOREAL_BEAST_LAIR_MIN_X = 2944
private const val CORPOREAL_BEAST_LAIR_MAX_X = 3007
private const val CORPOREAL_BEAST_ENTRANCE_MIN_Z = 4224
private const val CORPOREAL_BEAST_ENTRANCE_MAX_Z = 4287
private const val CORPOREAL_BEAST_LAIR_MIN_Z = 4352
private const val CORPOREAL_BEAST_LAIR_MAX_Z = 4415
