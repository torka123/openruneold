package org.rsmod.content.minigames.fightcaves

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.map.CoordGrid

class FightCaveWavesTest {
    @Test
    fun `shortened cave uses the exact seven requested rosters`() {
        assertEquals((57..63).toList(), FIGHT_CAVE_WAVES.map { it.osrsNumber })
        assertEquals((1..7).toList(), FIGHT_CAVE_WAVES.map { it.displayNumber })
        assertEquals(
            listOf(
                mapOf(
                    FightCaveMonster.KetZek to 1,
                    FightCaveMonster.YtMejKot to 1,
                    FightCaveMonster.TokXil to 1,
                    FightCaveMonster.TzKek to 1,
                    FightCaveMonster.TzKih to 1,
                ),
                mapOf(
                    FightCaveMonster.KetZek to 1,
                    FightCaveMonster.YtMejKot to 1,
                    FightCaveMonster.TokXil to 1,
                    FightCaveMonster.TzKek to 1,
                    FightCaveMonster.TzKih to 2,
                ),
                mapOf(
                    FightCaveMonster.KetZek to 1,
                    FightCaveMonster.YtMejKot to 1,
                    FightCaveMonster.TokXil to 1,
                    FightCaveMonster.TzKek to 2,
                ),
                mapOf(
                    FightCaveMonster.KetZek to 1,
                    FightCaveMonster.YtMejKot to 1,
                    FightCaveMonster.TokXil to 2,
                ),
                mapOf(FightCaveMonster.KetZek to 1, FightCaveMonster.YtMejKot to 2),
                mapOf(FightCaveMonster.KetZek to 1, FightCaveMonster.KetZekOrange to 1),
                mapOf(FightCaveMonster.TzTokJad to 1),
            ),
            FIGHT_CAVE_WAVES.map { wave -> wave.spawns.groupingBy { it.monster }.eachCount() },
        )
    }

    @Test
    fun `revision 240 ids are explicit and unique`() {
        assertEquals(
            listOf(3116, 3118, 3120, 3121, 3123, 3125, 3126, 3127, 3128),
            FightCaveMonster.entries.map { it.cacheId },
        )
        assertEquals(
            FightCaveMonster.entries.size,
            FightCaveMonster.entries.map { it.cacheId }.toSet().size,
        )
    }

    @Test
    fun `every wave fits separate player-centered compass positions`() {
        assertEquals(8, FightCaveSpawnPoint.entries.size)
        assertTrue(FIGHT_CAVE_WAVES.maxOf { it.spawns.size } <= FightCaveSpawnPoint.entries.size)
        assertEquals(
            listOf(1, 2, 1, 3, 4, 5, 5, 5, 1),
            FightCaveMonster.entries.map { it.footprintSize },
        )
        val player = CoordGrid(2402, 5094)
        for (wave in FIGHT_CAVE_WAVES) {
            val occupied = mutableListOf<Pair<CoordGrid, Int>>()
            for (spawn in wave.spawns) {
                val candidates =
                    surroundingSpawnCandidates(
                        player,
                        spawn.monster.footprintSize,
                        spawn.point,
                    )
                val actual =
                    candidates.firstOrNull { candidate ->
                        occupied.none { (other, otherSize) ->
                            rectanglesOverlap(
                                candidate,
                                spawn.monster.footprintSize,
                                other,
                                otherSize,
                            )
                        }
                    }
                assertNotNull(actual, "Wave ${wave.displayNumber} ran out of compass positions")
                occupied += requireNotNull(actual) to spawn.monster.footprintSize
            }
            assertEquals(wave.spawns.size, occupied.size)
        }
    }

    @Test
    fun `spawn rings surround the player without overlapping every npc footprint`() {
        val player = CoordGrid(100, 200, 2)
        val size = 5
        val expectedPrimary =
            mapOf(
                FightCaveSpawnPoint.North to CoordGrid(98, 206, 2),
                FightCaveSpawnPoint.NorthEast to CoordGrid(106, 206, 2),
                FightCaveSpawnPoint.East to CoordGrid(106, 198, 2),
                FightCaveSpawnPoint.SouthEast to CoordGrid(106, 190, 2),
                FightCaveSpawnPoint.South to CoordGrid(98, 190, 2),
                FightCaveSpawnPoint.SouthWest to CoordGrid(90, 190, 2),
                FightCaveSpawnPoint.West to CoordGrid(90, 198, 2),
                FightCaveSpawnPoint.NorthWest to CoordGrid(90, 206, 2),
            )

        for (point in FightCaveSpawnPoint.entries) {
            val candidates = surroundingSpawnCandidates(player, size, point)
            assertEquals(24, candidates.size)
            assertEquals(expectedPrimary.getValue(point), candidates.first())
            assertTrue(candidates.all { it.level == player.level })
            assertTrue(
                candidates.none { candidate -> rectanglesOverlap(candidate, size, player, 1) }
            )
        }
    }

    @Test
    fun `real entrance exit countdown and arena coordinates remain fixed`() {
        assertEquals("loc.tzhaar_fightcave_wall_entrance", FIGHT_CAVE_ENTRANCE_LOC)
        assertEquals("loc.tzhaar_fightcave_wall_exit", FIGHT_CAVE_EXIT_LOC)
        assertEquals(CoordGrid(2368, 5056), FIGHT_CAVE_TEMPLATE_MIN)
        assertEquals(CoordGrid(2431, 5183), FIGHT_CAVE_TEMPLATE_MAX)
        assertEquals(CoordGrid(2438, 5168), FIGHT_CAVE_OUTSIDE)
        assertEquals(CoordGrid(2413, 5117), FIGHT_CAVE_TEMPLATE_ENTRY)
        assertEquals(CoordGrid(2437, 5166), FIGHT_CAVE_ENTRANCE_OBJECT)
        assertTrue(isFightCaveEntrance(CoordGrid(2437, 5166)))
        assertFalse(isFightCaveEntrance(CoordGrid(2437, 5165)))
        assertEquals(17, FIGHT_CAVE_COUNTDOWN_TICKS)
        assertEquals(17, FIGHT_CAVE_INTERMISSION_TICKS)
    }

    @Test
    fun `wave 6 orange Ket-Zek point is the unmodified Jad point`() {
        val orange =
            FIGHT_CAVE_WAVES[5].spawns.single { it.monster == FightCaveMonster.KetZekOrange }
        val jad = FIGHT_CAVE_WAVES[6].spawns.single()
        assertEquals(orange.point, jad.point)
    }

    @Test
    fun `parent split children are registered on deterministic adjacent footprints`() {
        val parent = CoordGrid(100, 200, 2)
        assertEquals(listOf(parent, CoordGrid(101, 200, 2)), splitChildCoords(parent))
    }

    @Test
    fun `healer threshold only fires on a live crossing`() {
        assertTrue(crossedHalfHealth(previous = 126, current = 125, maximum = 250))
        assertFalse(crossedHalfHealth(previous = 125, current = 124, maximum = 250))
        assertFalse(crossedHalfHealth(previous = 126, current = 0, maximum = 250))
    }

    @Test
    fun `state machine rejects stale and duplicate transitions`() {
        val first = FightCaveProgress(generation = 41)
        val second = FightCaveProgress(generation = 42)
        assertNotEquals(first.generation, second.generation)
        assertTrue(first.beginWave())
        assertFalse(first.beginWave())
        repeat(6) {
            if (first.state == FightCaveRunState.ACTIVE_WAVE) assertTrue(first.finishWave())
            if (first.state == FightCaveRunState.BETWEEN_WAVES) assertTrue(first.advanceWave())
        }
        assertEquals(FightCaveRunState.JAD_PHASE, first.state)
        assertTrue(first.complete())
        assertFalse(first.complete())
        assertFalse(first.abort())
    }

    @Test
    fun `two progress objects never share wave state`() {
        val one = FightCaveProgress(generation = 1)
        val two = FightCaveProgress(generation = 2)
        one.forceWave(5)
        assertEquals(5, one.waveIndex)
        assertEquals(0, two.waveIndex)
        assertEquals(FightCaveRunState.COUNTDOWN, two.state)
    }
}

private fun rectanglesOverlap(
    first: CoordGrid,
    firstSize: Int,
    second: CoordGrid,
    secondSize: Int,
): Boolean =
    first.level == second.level &&
        first.x <= second.x + secondSize - 1 &&
        first.x + firstSize - 1 >= second.x &&
        first.z <= second.z + secondSize - 1 &&
        first.z + firstSize - 1 >= second.z
