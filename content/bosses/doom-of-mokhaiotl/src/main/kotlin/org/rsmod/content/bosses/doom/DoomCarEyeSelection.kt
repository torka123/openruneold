package org.rsmod.content.bosses.doom

import org.rsmod.map.CoordGrid

/** Pure target selection for the yellow eye which telegraphs one burrowed Doom zoom. */
internal object DoomCarEyeSelection {
    private const val TARGET_OFFSET = 4

    fun select(
        bossSouthWest: CoordGrid,
        playerTile: CoordGrid,
        minimum: CoordGrid,
        maximum: CoordGrid,
    ): CoordGrid {
        require(
            setOf(bossSouthWest.level, playerTile.level, minimum.level, maximum.level).size == 1
        ) {
            "Doom car eye selection cannot cross levels."
        }
        require(minimum.x <= maximum.x && minimum.z <= maximum.z) {
            "Invalid Doom car eye bounds: $minimum -> $maximum"
        }

        val bossCentre = DoomCarGeometry.centre(bossSouthWest)
        var deltaX = playerTile.x.compareTo(bossCentre.x)
        val deltaZ = playerTile.z.compareTo(bossCentre.z)
        if (deltaX == 0 && deltaZ == 0) deltaX = 1

        val preferred =
            clamp(
                playerTile.translate(deltaX * TARGET_OFFSET, deltaZ * TARGET_OFFSET),
                minimum,
                maximum,
            )
        if (preferred != bossCentre) return preferred

        val directions =
            listOf(
                    -deltaX to -deltaZ,
                    1 to 0,
                    0 to 1,
                    -1 to 0,
                    0 to -1,
                    1 to 1,
                    -1 to 1,
                    -1 to -1,
                    1 to -1,
                )
                .distinct()
        return directions
            .asSequence()
            .map { (x, z) ->
                clamp(bossCentre.translate(x * TARGET_OFFSET, z * TARGET_OFFSET), minimum, maximum)
            }
            .firstOrNull { it != bossCentre }
            ?: error("Doom car eye bounds contain no non-zero target around $bossCentre")
    }

    private fun clamp(tile: CoordGrid, minimum: CoordGrid, maximum: CoordGrid): CoordGrid =
        CoordGrid(
            tile.x.coerceIn(minimum.x, maximum.x),
            tile.z.coerceIn(minimum.z, maximum.z),
            minimum.level,
        )
}
