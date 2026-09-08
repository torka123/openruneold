package org.rsmod.content.slayer.tower

import org.rsmod.map.CoordGrid

internal enum class SlayerTowerDirection(val levelOffset: Int) {
    Up(1),
    Down(-1),
}

internal object SlayerTowerRoutes {
    const val MIN_X = 3400
    const val MAX_X = 3460
    const val MIN_Z = 3525
    const val MAX_Z = 3585
    const val MIN_LEVEL = 0
    const val MAX_LEVEL = 2

    fun contains(coords: CoordGrid): Boolean =
        coords.x in MIN_X..MAX_X &&
            coords.z in MIN_Z..MAX_Z &&
            coords.level in MIN_LEVEL..MAX_LEVEL

    fun directionForAction(action: String?): SlayerTowerDirection? {
        val normalized = action?.lowercase()?.replace('-', ' ')?.trim() ?: return null
        return when {
            normalized.contains("down") || normalized.contains("descend") ->
                SlayerTowerDirection.Down
            normalized.contains("up") || normalized.contains("ascend") ->
                SlayerTowerDirection.Up
            else -> null
        }
    }

    fun isTransportAction(action: String?): Boolean {
        val normalized = action?.lowercase()?.replace('-', ' ')?.trim() ?: return false
        return normalized.contains("climb") ||
            normalized.contains("stairs") ||
            normalized.contains("ascend") ||
            normalized.contains("descend") ||
            normalized == "go up" ||
            normalized == "go down"
    }

    fun defaultDirection(level: Int): SlayerTowerDirection? =
        when (level) {
            MIN_LEVEL -> SlayerTowerDirection.Up
            MAX_LEVEL -> SlayerTowerDirection.Down
            else -> null
        }

    fun destinationLevel(level: Int, direction: SlayerTowerDirection): Int? {
        val destination = level + direction.levelOffset
        return destination.takeIf { it in MIN_LEVEL..MAX_LEVEL }
    }
}
