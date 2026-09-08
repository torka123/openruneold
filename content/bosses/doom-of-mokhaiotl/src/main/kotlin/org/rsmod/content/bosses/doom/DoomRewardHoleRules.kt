package org.rsmod.content.bosses.doom

import kotlin.math.max
import org.rsmod.map.CoordGrid

/** Orders safe in-arena burrow-hole candidates from beneath Doom toward the arena centre. */
internal object DoomRewardHoleRules {
    fun candidates(
        bossSouthWest: CoordGrid,
        bossSize: Int,
        arenaSouthWest: CoordGrid,
        arenaNorthEast: CoordGrid,
    ): List<CoordGrid> {
        require(bossSize > 0)
        require(arenaSouthWest.level == arenaNorthEast.level)
        val level = arenaSouthWest.level
        val bossCentre =
            CoordGrid(
                (bossSouthWest.x + bossSize / 2).coerceIn(arenaSouthWest.x, arenaNorthEast.x),
                (bossSouthWest.z + bossSize / 2).coerceIn(arenaSouthWest.z, arenaNorthEast.z),
                level,
            )
        val arenaCentre =
            CoordGrid(
                (arenaSouthWest.x + arenaNorthEast.x) / 2,
                (arenaSouthWest.z + arenaNorthEast.z) / 2,
                level,
            )
        val radius = max(arenaNorthEast.x - arenaSouthWest.x, arenaNorthEast.z - arenaSouthWest.z)
        return buildList {
                add(bossCentre)
                for (distance in 1..radius) {
                    for (x in (bossCentre.x - distance)..(bossCentre.x + distance)) {
                        add(CoordGrid(x, bossCentre.z - distance, level))
                        add(CoordGrid(x, bossCentre.z + distance, level))
                    }
                    for (z in (bossCentre.z - distance + 1) until (bossCentre.z + distance)) {
                        add(CoordGrid(bossCentre.x - distance, z, level))
                        add(CoordGrid(bossCentre.x + distance, z, level))
                    }
                }
            }
            .asSequence()
            .filter {
                it.x in arenaSouthWest.x..arenaNorthEast.x &&
                    it.z in arenaSouthWest.z..arenaNorthEast.z
            }
            .distinct()
            .sortedWith(
                compareBy<CoordGrid>(
                    {
                        max(
                            kotlin.math.abs(it.x - bossCentre.x),
                            kotlin.math.abs(it.z - bossCentre.z),
                        )
                    },
                    {
                        max(
                            kotlin.math.abs(it.x - arenaCentre.x),
                            kotlin.math.abs(it.z - arenaCentre.z),
                        )
                    },
                )
            )
            .toList()
    }
}
