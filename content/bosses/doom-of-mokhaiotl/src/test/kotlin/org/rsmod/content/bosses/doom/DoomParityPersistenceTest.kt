package org.rsmod.content.bosses.doom

import java.nio.file.Files
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.map.CoordGrid

class DoomParityPersistenceTest {
    @Test
    fun `shared five by five geometry drives footprint contact and intersection`() {
        val southWest = CoordGrid(100, 200, 0)
        val occupied = DoomGeometry.occupied(southWest)
        val contact = DoomGeometry.contactTiles(southWest)

        assertEquals(25, occupied.size)
        assertEquals(CoordGrid(102, 202, 0), DoomGeometry.centre(southWest))
        assertEquals(24, contact.size)
        assertTrue(DoomGeometry.contains(southWest, CoordGrid(104, 204, 0)))
        assertFalse(DoomGeometry.contains(southWest, CoordGrid(105, 204, 0)))
        assertEquals(0, DoomGeometry.distanceToFootprint(southWest, CoordGrid(102, 202, 0)))
        assertEquals(1, DoomGeometry.distanceToFootprint(southWest, CoordGrid(105, 202, 0)))
        assertTrue(DoomGeometry.intersects(setOf(CoordGrid(104, 204, 0)), southWest))
        assertFalse(DoomGeometry.intersects(setOf(CoordGrid(105, 205, 0)), southWest))
    }

    @Test
    fun `shockwave field uses captured footprint-relative client delays`() {
        val southWest = CoordGrid(100, 200, 0)
        val candidates = DoomShockwaveGeometry.candidateTiles(southWest)

        assertEquals(613, candidates.size)
        assertEquals(-2, DoomShockwaveGeometry.clientDelay(southWest, southWest.translate(2, 2)))
        assertEquals(1, DoomShockwaveGeometry.clientDelay(southWest, southWest.translate(5, 0)))
        assertEquals(10, DoomShockwaveGeometry.clientDelay(southWest, southWest.translate(14, 14)))
    }

    @Test
    fun `pending rewards round trip run and result ids without duplication`() {
        val state =
            DoomPendingRewardState(
                runId = "run-1",
                resultIds = setOf("run-1:2", "run-1:1"),
                rewards = listOf(DoomReward(31113, 1), DoomReward(31130, 1)),
                uniqueRisk = true,
                claiming = true,
            )

        assertEquals(state, DoomPendingRewardCodec.decode(DoomPendingRewardCodec.encode(state)))
        assertEquals(listOf(DoomReward(995, 25)), DoomPendingRewardCodec.decode("995:25").rewards)
    }

    @Test
    fun `loot table retains its exact weighted common contract`() {
        assertEquals(DoomLootTable.TOTAL_COMMON_WEIGHT, DoomLootTable.common.sumOf { it.weight })
        assertEquals(24, DoomLootTable.common.size)
        assertEquals(
            1,
            DoomLootTable.common.single { it.internal == "obj.varlamore_key_half_1" }.weight,
        )
        assertEquals(7, DoomLootTable.common.single { it.internal == "obj.demon_tear" }.weight)
        assertEquals(
            1..2,
            DoomLootTable.common.single { it.internal == "obj.dom_teleport_item" }.count,
        )
    }

    @Test
    fun `personal scoreboard codec preserves idempotency keys and delve bands`() {
        val bands =
            List(DoomScoreboardCodec.BAND_COUNT) { index -> DoomDelveStat(index, index * 10) }
        val state =
            DoomPersonalStats(
                deepestDelve = 11,
                deaths = 4,
                bestFullRunTicks = 500,
                deepDelves = 3,
                delveStats = bands,
                completionIds = setOf("r:1", "r:2"),
                deathIds = setOf("r:death"),
            )

        assertEquals(state, DoomScoreboardCodec.decode(DoomScoreboardCodec.encode(state)))
        assertFailsWith<IllegalArgumentException> { DoomScoreboardCodec.decode("bad") }
    }

    @Test
    fun `global scoreboard survives a new store instance and rejects malformed state`() {
        val directory = Files.createTempDirectory("doom-scoreboard")
        try {
            val path = directory.resolve("scoreboard.txt")
            val state =
                DoomGlobalStats(
                    deepestDelve = 12,
                    deepestPlayer = "Doom Tester",
                    deaths = 9,
                    bestFullRunTicks = 777,
                    bestFullRunPlayer = "Fast Tester",
                    deepDelves = 4,
                    bands =
                        List(DoomScoreboardCodec.BAND_COUNT) { index ->
                            DoomGlobalBand(index.toLong(), index * 100, "Player $index")
                        },
                    completionIds = setOf("run-a:1", "run-b:8"),
                    deathIds = setOf("run-c:death"),
                )

            DoomGlobalStatsStore(path).save(state)
            assertEquals(state, DoomGlobalStatsStore(path).load())
            assertFalse(Files.exists(path.resolveSibling("scoreboard.txt.tmp")))

            Files.writeString(path, "version=1\nmissing=everything\n")
            assertFailsWith<IllegalStateException> { DoomGlobalStatsStore(path).load() }
        } finally {
            Files.walk(directory).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    @Test
    fun `scoreboard time formatting uses elapsed ticks and nearest seconds`() {
        assertEquals("--:--", DoomStatsTime.format(0))
        assertEquals("0:01", DoomStatsTime.format(1))
        assertEquals("0:01", DoomStatsTime.format(2))
        assertEquals("1:00", DoomStatsTime.format(100))
        assertEquals("0:01.2", DoomStatsTime.format(2, precise = true))
    }

    @Test
    fun `delve client levels and phase counts cover every band`() {
        assertEquals(
            listOf(558, 608, 658, 708, 758, 808, 858, 908),
            (1..8).map(DoomRules::combatLevel),
        )
        assertEquals(listOf(1, 1, 2, 2, 3, 3, 4, 5), (1..8).map(DoomRules::shockwaveSlams))
        assertEquals(7, DoomRules.ACID_MAX_HIT)
    }
}
