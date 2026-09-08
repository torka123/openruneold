package org.rsmod.content.minigames.toa

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.map.CoordGrid

class ToaCrondisEncounterTest {
    @Test
    fun `revision 240 Crondis assets remain centralized and stable`() {
        assertEquals((11700..11705).toSet(), CrondisAssets.npcIds)
        assertEquals(27295, CrondisAssets.WATER_CONTAINER)
        assertEquals(45397, CrondisAssets.CONTINUE_LOC)
        assertEquals(setOf(45397, 45398, 45399, 45415, 45416), CrondisAssets.locIds)
        assertEquals(setOf(9562, 9563, 9565), CrondisAssets.sequenceIds)
        assertEquals(2129, CrondisAssets.CLOUD_TRAIL)
    }

    @Test
    fun `current OSRS palm starts at one hundred seventy-five water then adds one hundred twenty-five per player`() {
        assertEquals(
            listOf(175, 300, 425, 550, 675, 800, 925, 1050),
            (1..8).map(CrondisTuning::requiredWater),
        )
        assertEquals(175, CrondisTuning.requiredWater(0))
    }

    @Test
    fun `palm stages advance at quarters and cap overflow atomically`() {
        val encounter = CrondisEncounter(UUID(0, 1))
        encounter.state = CrondisState.ACTIVE
        encounter.originalPartySize = 1
        assertEquals(43, encounter.addWater(43))
        assertEquals(0, encounter.stage)
        assertEquals(1, encounter.addWater(1))
        assertEquals(1, encounter.stage)
        assertEquals(44, encounter.addWater(44))
        assertEquals(2, encounter.stage)
        assertEquals(44, encounter.addWater(44))
        assertEquals(3, encounter.stage)
        assertEquals(43, encounter.addWater(500))
        assertEquals(4, encounter.stage)
        assertEquals(CrondisState.COMPLETE, encounter.state)
        assertEquals(0, encounter.addWater(1))
    }

    @Test
    fun `deposit empties overflow instead of retaining raid water`() {
        val encounter = CrondisEncounter(UUID(0, 3))
        encounter.state = CrondisState.ACTIVE
        encounter.originalPartySize = 1
        encounter.water = 100
        val container = CrondisWaterContainer(100)
        assertEquals(75, encounter.depositWater(container))
        assertEquals(175, encounter.water)
        assertEquals(0, container.units)
        assertEquals(CrondisState.COMPLETE, encounter.state)
    }

    @Test
    fun `normal spill halves repeatedly with floor remaining and one unit minimum`() {
        val container = CrondisWaterContainer(100)
        assertEquals(50, container.loseToTrap(CrondisSpillOutcome.NORMAL))
        assertEquals(50, container.units)
        assertEquals(25, container.loseToTrap(CrondisSpillOutcome.NORMAL))
        assertEquals(25, container.units)
        assertEquals(13, container.loseToTrap(CrondisSpillOutcome.NORMAL))
        assertEquals(12, container.units)
        assertEquals(6, container.loseToTrap(CrondisSpillOutcome.NORMAL))
        assertEquals(6, container.units)
        container.units = 1
        assertEquals(0, container.loseToTrap(CrondisSpillOutcome.NORMAL))
        assertEquals(1, container.units)
    }

    @Test
    fun `rare spill retains eighty-four percent and empty containers remain empty`() {
        val container = CrondisWaterContainer(100)
        assertEquals(16, container.loseToTrap(CrondisSpillOutcome.RARE))
        assertEquals(84, container.units)
        container.units = 25
        assertEquals(4, container.loseToTrap(CrondisSpillOutcome.RARE))
        assertEquals(21, container.units)
        container.units = 0
        assertEquals(0, container.loseToTrap(CrondisSpillOutcome.RARE))
        assertEquals(0, container.units)
    }

    @Test
    fun `crocodile waves and successful base damage follow OSRS rules`() {
        assertEquals(listOf(1, 1, 2, 2, 3, 3, 4, 4), (1..8).map(CrondisTuning::crocodilesPerWave))
        assertEquals(18, CrondisTuning.crocodileDamage(0))
        assertEquals(21, CrondisTuning.crocodileDamage(1))
        assertEquals(33, CrondisTuning.crocodileDamage(5))
        assertEquals(36, CrondisTuning.crocodileDamage(99))
    }

    @Test
    fun `all four revision 240 corridors expose forty alternating emitter rows`() {
        assertEquals(
            CrondisCorridor.entries.toSet(),
            CrondisLayout.paths.map { it.corridor }.toSet(),
        )
        assertEquals(40, CrondisLayout.paths.sumOf { it.emitters.size })

        val southWest = CrondisLayout.path(CrondisCorridor.SOUTH_WEST)
        assertEquals(CoordGrid(21, 10, 0), southWest.emitters[0].statueLocal)
        assertEquals(CoordGrid(23, 10, 0), southWest.emitters[0].spearLocal)
        assertEquals((23..25).map { CoordGrid(it, 10, 0) }, southWest.emitters[0].dangerousLocals)
        assertEquals(CoordGrid(28, 11, 0), southWest.emitters[1].statueLocal)
        assertEquals(CoordGrid(25, 11, 0), southWest.emitters[1].spearLocal)
        assertEquals((25..27).map { CoordGrid(it, 11, 0) }, southWest.emitters[1].dangerousLocals)

        val northEast = CrondisLayout.path(CrondisCorridor.NORTH_EAST)
        assertEquals(CoordGrid(35, 45, 0), northEast.emitters.first().statueLocal)
        assertEquals(CoordGrid(42, 54, 0), northEast.emitters.last().statueLocal)
    }

    @Test
    fun `all four revision 240 waterfalls are represented`() {
        assertEquals(
            listOf(
                CoordGrid(22, 2, 0),
                CoordGrid(36, 2, 0),
                CoordGrid(22, 58, 0),
                CoordGrid(36, 58, 0),
            ),
            CrondisLayout.waterfallLocals,
        )
    }

    @Test
    fun `each path keeps its donor backed stagger matrix instead of a shared timer`() {
        assertEquals(
            listOf(
                listOf(0, 2, 4, 6, 8, 0, 2, 4, 6, 8),
                listOf(0, 3, 6, 9, 2, 0, 3, 6, 9, 2),
                listOf(2, 1, 1, 0, 0, 2, 2, 1, 1, 0),
                listOf(0, 1, 2, 3, 4, 4, 3, 2, 1, 0),
            ),
            CrondisLayout.paths.map { it.spikeDelays },
        )
    }

    @Test
    fun `spike warning extension dangerous retraction and cooldown occupy exact ticks`() {
        val emitter = CrondisLayout.path(CrondisCorridor.NORTH_WEST).emitters.first()
        val phases = (100..110).map { CrondisObstacleCycle.spikePhase(100, it, emitter) }
        assertEquals(
            listOf(
                CrondisSpikePhase.WARNING,
                CrondisSpikePhase.PREPARING,
                CrondisSpikePhase.PREPARING,
                CrondisSpikePhase.EXTENDING,
                CrondisSpikePhase.DANGEROUS,
                CrondisSpikePhase.DANGEROUS,
                CrondisSpikePhase.DANGEROUS,
                CrondisSpikePhase.RETRACTING,
                CrondisSpikePhase.COOLDOWN,
                CrondisSpikePhase.COOLDOWN,
                CrondisSpikePhase.WARNING,
            ),
            phases,
        )
    }

    @Test
    fun `cloud warning advances three tiles per tick then each tile is dangerous for two ticks`() {
        val trail = CrondisCloudTrail(0, CrondisCorridor.SOUTH_WEST, lane = 2, startCycle = 10)
        assertEquals(CoordGrid(25, 10, 0), trail.localTile(0))
        assertEquals(CoordGrid(25, 19, 0), trail.localTile(9))
        assertEquals(CrondisCloudPhase.WARNING, CrondisObstacleCycle.cloudPhase(trail, 10, 0))
        assertEquals(CrondisCloudPhase.DANGEROUS, CrondisObstacleCycle.cloudPhase(trail, 11, 0))
        assertEquals(CrondisCloudPhase.DANGEROUS, CrondisObstacleCycle.cloudPhase(trail, 12, 0))
        assertEquals(CrondisCloudPhase.EXPIRED, CrondisObstacleCycle.cloudPhase(trail, 13, 0))
        assertEquals(CrondisCloudPhase.WARNING, CrondisObstacleCycle.cloudPhase(trail, 13, 9))
        assertEquals(CrondisCloudPhase.DANGEROUS, CrondisObstacleCycle.cloudPhase(trail, 15, 9))
        assertFalse(CrondisObstacleCycle.cloudExpired(trail, 15))
        assertTrue(CrondisObstacleCycle.cloudExpired(trail, 16))
    }

    @Test
    fun `north and south cloud paths move toward their waterfalls through separate lanes`() {
        val south = CrondisCloudTrail(0, CrondisCorridor.SOUTH_EAST, 0, 0)
        val north = CrondisCloudTrail(1, CrondisCorridor.NORTH_WEST, 4, 0)
        assertEquals(listOf(10, 11, 12), (0..2).map { south.localTile(it).z })
        assertEquals(listOf(54, 53, 52), (0..2).map { north.localTile(it).z })
        assertEquals(37, south.localTile(0).x)
        assertEquals(27, north.localTile(0).x)
    }

    @Test
    fun `two tile running checks only the occupied destination and permits OSRS tile skipping`() {
        val dangerous = setOf(CoordGrid(21, 10, 0))
        assertFalse(
            CrondisObstacleCycle.occupiesDangerousTile(
                occupied = CoordGrid(22, 10, 0),
                dangerous = dangerous,
            )
        )
        assertTrue(
            CrondisObstacleCycle.occupiesDangerousTile(
                occupied = CoordGrid(21, 10, 0),
                dangerous = dangerous,
            )
        )
        assertFalse(
            CrondisObstacleCycle.occupiesDangerousTile(
                occupied = CoordGrid(22, 11, 0),
                dangerous = dangerous,
            )
        )
    }

    @Test
    fun `cloud lane selection is seeded unique per path and starts after its warning delay`() {
        val first = CrondisEncounter(UUID(0, 10), seed = 1234)
        val second = CrondisEncounter(UUID(0, 11), seed = 1234)
        first.state = CrondisState.ACTIVE
        second.state = CrondisState.ACTIVE
        val firstBatch = first.spawnCloudBatch(50)
        val secondBatch = second.spawnCloudBatch(50)
        assertEquals(
            firstBatch.map { it.corridor to it.lane },
            secondBatch.map { it.corridor to it.lane },
        )
        assertTrue(firstBatch.groupBy { it.corridor }.values.all { it.size in 2..3 })
        assertTrue(
            firstBatch
                .groupBy { it.corridor }
                .values
                .all { group -> group.map { it.lane }.distinct().size == group.size }
        )
        assertTrue(firstBatch.all { it.startCycle == 52 })
    }

    @Test
    fun `pre barrier and completed encounters cannot schedule clouds`() {
        val encounter = CrondisEncounter(UUID(0, 12), seed = 1)
        assertTrue(encounter.spawnCloudBatch(0).isEmpty())
        encounter.state = CrondisState.ACTIVE
        encounter.originalPartySize = 1
        encounter.addWater(175)
        assertEquals(CrondisState.COMPLETE, encounter.state)
        assertTrue(encounter.spawnCloudBatch(1).isEmpty())
    }

    @Test
    fun `perfect Crondis requires completion in one hundred ticks without losing water`() {
        val encounter = CrondisEncounter(UUID(0, 13), seed = 1)
        encounter.state = CrondisState.ACTIVE
        encounter.originalPartySize = 1
        encounter.startedAt = 10
        encounter.addWater(175)
        encounter.completedAt = 110
        assertTrue(encounter.perfectAchieved)
        encounter.completedAt = 111
        assertFalse(encounter.perfectAchieved)
        encounter.completedAt = 110
        encounter.perfectEligible = false
        assertFalse(encounter.perfectAchieved)
    }

    @Test
    fun `water containers use donor spawns translated into revision 240 room locals`() {
        assertEquals(
            listOf(CoordGrid(34, 39, 0), CoordGrid(30, 25, 0)),
            CrondisLayout.containerLocals,
        )
    }

    @Test
    fun `crocodiles wait fifty ticks before the first and following waves`() {
        val encounter = CrondisEncounter(UUID(0, 14), seed = 1)
        encounter.state = CrondisState.ACTIVE
        encounter.nextCrocodileSpawnAt = 150
        assertFalse(encounter.crocodileSpawnDue(149))
        assertTrue(encounter.crocodileSpawnDue(150))
        encounter.scheduleNextCrocodileWave(150)
        assertEquals(200, encounter.nextCrocodileSpawnAt)
        assertFalse(encounter.crocodileSpawnDue(199))
        assertTrue(encounter.crocodileSpawnDue(200))
    }

    @Test
    fun `crocodile damage counts only obstacle hits from the previous thirty seconds`() {
        val encounter = CrondisEncounter(UUID(0, 15), seed = 1)
        val uid = PlayerUid(42)
        encounter.recordObstacleHit(uid, 100)
        encounter.recordObstacleHit(uid, 120)
        assertEquals(24, encounter.crocodileDamage(uid, 149))
        assertEquals(21, encounter.crocodileDamage(uid, 150))
        assertEquals(18, encounter.crocodileDamage(uid, 170))
        repeat(10) { encounter.recordObstacleHit(uid, 200 + it) }
        assertEquals(36, encounter.crocodileDamage(uid, 210))
    }

    @Test
    fun `crocodiles use all four revision 240 corner spawns`() {
        assertEquals(
            listOf(
                CoordGrid(21, 37, 0),
                CoordGrid(42, 37, 0),
                CoordGrid(21, 26, 0),
                CoordGrid(42, 26, 0),
            ),
            CrondisLayout.crocodileSpawnLocals,
        )
    }

    @Test
    fun `reset invalidates old Crondis work and removes every obstacle runtime`() {
        val encounter = CrondisEncounter(UUID(0, 2), seed = 99)
        val generation = encounter.generation
        encounter.state = CrondisState.ACTIVE
        encounter.originalPartySize = 4
        encounter.water = 350
        encounter.containersSpawned = true
        encounter.spawnCloudBatch(20)
        encounter.nextCloudBatchAt = 25
        encounter.reset()
        assertEquals(generation + 1, encounter.generation)
        assertEquals(CrondisState.RESET, encounter.state)
        assertEquals(0, encounter.water)
        assertEquals(-1, encounter.nextCloudBatchAt)
        assertEquals(-1, encounter.nextCrocodileSpawnAt)
        assertEquals(0, encounter.crocodileSpawnCursor)
        assertFalse(encounter.containersSpawned)
        assertTrue(encounter.containers.isEmpty())
        assertTrue(encounter.crocodiles.isEmpty())
        assertTrue(encounter.crocodileProvokedPlayers.isEmpty())
        assertTrue(encounter.playerObstacleHitCycles.isEmpty())
        assertTrue(encounter.cloudTrails.isEmpty())
        assertTrue(encounter.playerSpikeReadyAt.isEmpty())
        assertTrue(encounter.playerCloudReadyAt.isEmpty())
    }
}
