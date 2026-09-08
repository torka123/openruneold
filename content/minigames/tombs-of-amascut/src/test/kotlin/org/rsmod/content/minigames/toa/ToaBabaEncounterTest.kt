package org.rsmod.content.minigames.toa

import java.util.UUID
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid

class ToaBabaEncounterTest {
    @Test
    fun `revision 240 assets remain centralized and stable`() {
        assertEquals(11778, BabaAssets.BABA)
        assertEquals(11781, BabaAssets.BABOON)
        assertEquals(11782, BabaAssets.BOULDER)
        assertEquals(11783, BabaAssets.WEAK_BOULDER)
        assertEquals(11788, BabaAssets.SARCOPHAGUS)
        assertEquals(45752, BabaAssets.CLOSED_SARCOPHAGUS_LOC)
        assertEquals(45753, BabaAssets.OPEN_SARCOPHAGUS_LOC)
        assertEquals(9737, BabaAssets.WALK)
        assertEquals(9743, BabaAssets.MELEE_ATTACK)
        assertEquals(9518, BabaAssets.BOULDER_ROLL)
    }

    @Test
    fun `melee reach is separated from the room-wide chase leash`() {
        assertEquals(1, BabaTuning.MELEE_ATTACK_RANGE.value)
        assertEquals(64, BabaTuning.ROOM_CHASE_RANGE.value)
    }

    @Test
    fun `Ba-Ba scaling delegates to the shared raid formula through eight players`() {
        val expected = listOf(1.0, 1.9, 2.8, 3.4, 4.0, 4.6, 5.2, 5.8)
        val actual = (1..8).map(BabaScaling::partyHitpointsFactor)
        expected.zip(actual).forEach { (expectedFactor, actualFactor) ->
            assertEquals(expectedFactor, actualFactor, 1e-7)
        }
        (1..8).forEach { partySize ->
            assertEquals(
                ToaRules.hitpoints(380, partySize, 225, 3),
                BabaScaling.hitpoints(partySize, 225, 3),
            )
        }
    }

    @Test
    fun `party size produces one through four boss baboons`() {
        assertEquals(listOf(1, 1, 2, 2, 3, 3, 4, 4), (1..8).map(BabaScaling::baboonCount))
    }

    @Test
    fun `only one discharge baboon peels off after attacking players first`() {
        assertFalse(
            BabaBaboonDischargePolicy.runnerDue(
                now = 107,
                nextRunnerAt = 108,
                currentRunner = null,
                hasLiveBaboon = true,
                hasClosedSarcophagus = true,
            )
        )
        assertTrue(
            BabaBaboonDischargePolicy.runnerDue(
                now = 108,
                nextRunnerAt = 108,
                currentRunner = null,
                hasLiveBaboon = true,
                hasClosedSarcophagus = true,
            )
        )
        assertFalse(
            BabaBaboonDischargePolicy.runnerDue(
                now = 108,
                nextRunnerAt = 108,
                currentRunner = 42,
                hasLiveBaboon = true,
                hasClosedSarcophagus = true,
            )
        )
        assertEquals(8, BabaBaboonDischargePolicy.ATTACK_RANGE)
        assertEquals(64, BabaBaboonDischargePolicy.CHASE_RANGE)
    }

    @Test
    fun `solo rolling boulder hitpoints follow path brackets`() {
        assertEquals(listOf(25, 25, 27, 27, 31, 31), (0..5).map(BabaScaling::soloBoulderHitpoints))
        assertEquals(
            listOf(25, 36, 47, 55, 62, 70, 77, 85),
            (1..8).map { BabaScaling.boulderHitpoints(it, 0) },
        )
        assertEquals(44, BabaScaling.boulderHitpoints(2, 4))
        assertEquals(10, BabaScaling.rubbleHitpoints(1))
        assertEquals(80, BabaScaling.rubbleHitpoints(8))
    }

    @Test
    fun `integer threshold crossing queues each phase exactly once`() {
        assertEquals(listOf(1), BabaThresholds.crossed(300, 200, 300))
        assertEquals(listOf(2), BabaThresholds.crossed(200, 100, 300))
        assertEquals(listOf(1, 2), BabaThresholds.crossed(300, 0, 300))
        assertTrue(BabaThresholds.crossed(200, 200, 300).isEmpty())

        val state = BabaEncounter(UUID(0, 1), 42)
        state.startIntro(1)
        state.startNormalCombat()
        assertEquals(listOf(1, 2), state.queueCrossedThresholds(300, 0, 300))
        assertTrue(state.queueCrossedThresholds(300, 0, 300).isEmpty())
    }

    @Test
    fun `special cycle is deterministic excludes rock throw and does not repeat`() {
        val first = BabaEncounter(UUID(1, 1), 91)
        val second = BabaEncounter(UUID(1, 1), 91)
        val firstSequence = List(40) { first.nextSpecial(allowRockfall = true) }
        val secondSequence = List(40) { second.nextSpecial(allowRockfall = true) }
        assertEquals(firstSequence, secondSequence)
        assertTrue(firstSequence.none { it == BabaSpecial.ROCK_THROW })
        assertTrue(firstSequence.zipWithNext().none { (left, right) -> left == right })
        assertTrue(BabaSpecial.BABOON_DISCHARGE in firstSequence)
        assertTrue(
            List(20) { first.nextSpecial(allowRockfall = false) }
                .none { it == BabaSpecial.ROCKFALL }
        )
        assertEquals(2, BabaTuning.SPECIAL_AFTER_AUTOS.value)
    }

    @Test
    fun `a cracked boulder receives the attacking players guaranteed maximum hit`() {
        val runtime = BabaRollingBoulder(1, 2, true, CoordGrid(37, 31, 0))
        assertEquals(25, runtime.damageForValidRangedHit(40, 25))
        assertEquals(1, runtime.validRangedHits)
    }

    @Test
    fun `weak lane starts in middle three and stays within two lanes`() {
        val state = BabaEncounter(UUID(2, 2), 7)
        state.state = BabaState.ROLLING_STONES_PREP
        state.beginRollingSet(boulderdash = false, now = 0)
        assertTrue(state.weakLane in 1..3)
        repeat(30) {
            val previous = state.weakLane
            state.beginRollingSet(boulderdash = false, now = it + 1)
            assertTrue(abs(state.weakLane - previous) <= 2)
        }
    }

    @Test
    fun `adjacent rolling stones cover their cache footprint without false lane gaps`() {
        val boulder = CoordGrid(40, 31, 0)
        assertTrue(BabaRollingCollision.touches(boulder, CoordGrid.NULL, boulder, 3))
        assertTrue(BabaRollingCollision.touches(CoordGrid(40, 32, 0), CoordGrid.NULL, boulder, 3))
        assertTrue(BabaRollingCollision.touches(CoordGrid(40, 33, 0), CoordGrid.NULL, boulder, 3))
        assertTrue(BabaRollingCollision.touches(CoordGrid(39, 31, 0), boulder, boulder, 3))
        assertFalse(BabaRollingCollision.touches(CoordGrid(40, 30, 0), CoordGrid.NULL, boulder, 3))
        assertFalse(BabaRollingCollision.touches(CoordGrid(40, 34, 0), CoordGrid.NULL, boulder, 3))
    }

    @Test
    fun `rolling path always advances from the live actor tile and never self steps`() {
        val endX = 22
        assertEquals(CoordGrid(36, 31, 0), BabaRollingPath.next(CoordGrid(37, 31, 0), endX))
        assertEquals(CoordGrid(36, 31, 0), BabaRollingPath.next(CoordGrid(37, 31, 0), endX))
        assertEquals(null, BabaRollingPath.next(CoordGrid(endX, 31, 0), endX))
        assertEquals(null, BabaRollingPath.next(CoordGrid(endX - 1, 31, 0), endX))
    }

    @Test
    fun `baseline rolling rows retain the captured eight tick cadence`() {
        val state = BabaEncounter(UUID(4, 4), 19)
        state.state = BabaState.ROLLING_STONES_PREP
        state.beginRollingSet(boulderdash = false, now = 100)
        assertEquals(108, state.nextBoulderAt)
        state.beginRollingSet(boulderdash = true, now = 108)
        assertEquals(112, state.nextBoulderAt)
    }

    @Test
    fun `the terminal rolling row disables further casts so the phase can drain`() {
        val state = BabaEncounter(UUID(4, 5), 23)
        state.state = BabaState.ROLLING_STONES_PREP
        state.beginRollingSet(boulderdash = false, now = 100)

        repeat(BabaTuning.BOULDER_MAX_SETS.value - 1) { index ->
            assertTrue(
                state.advanceRollingScheduleAfterCast(
                    boulderdash = false,
                    now = 108 + index * BabaTuning.BOULDER_RELEASE_INTERVAL.value,
                    maximumSets = BabaTuning.BOULDER_MAX_SETS.value,
                )
            )
        }

        assertEquals(BabaTuning.BOULDER_MAX_SETS.value, state.rollingSet)
        assertTrue(state.isRollingCastDue(state.nextBoulderAt))
        assertFalse(
            state.advanceRollingScheduleAfterCast(
                boulderdash = false,
                now = state.nextBoulderAt,
                maximumSets = BabaTuning.BOULDER_MAX_SETS.value,
            )
        )
        assertEquals(-1, state.nextBoulderAt)
        assertFalse(state.isRollingCastDue(Int.MAX_VALUE))
    }

    @Test
    fun `rolling boulder knockback cannot cross the visible floor edge`() {
        assertEquals(4, BabaRollingKnockback.safeDistance(37, 22, 4))
        assertEquals(2, BabaRollingKnockback.safeDistance(24, 22, 4))
        assertEquals(1, BabaRollingKnockback.safeDistance(23, 22, 4))
        assertEquals(0, BabaRollingKnockback.safeDistance(22, 22, 4))
        assertEquals(0, BabaRollingKnockback.safeDistance(20, 22, 4))
    }

    @Test
    fun `rolling stones follow one uninterrupted current OSRS route at row cadence`() {
        val route =
            generateSequence(CoordGrid(37, 31, 0)) { current -> BabaRollingPath.next(current, 22) }
                .drop(1)
                .toList()
        assertEquals((36 downTo 22).toList(), route.map { it.x })
        assertTrue(route.all { it.z == 31 && it.level == 0 })
        assertEquals(8, BabaTuning.BOULDER_RELEASE_INTERVAL.value)
        assertEquals(4, BabaTuning.BOULDERDASH_RELEASE_INTERVAL.value)
        assertEquals(10, BabaTuning.BOULDER_MAX_SETS.value)
        assertEquals(5, BabaArenaLayout.laneLocalZ.size)
    }

    @Test
    fun `rubble sequence has a thirty second cooldown`() {
        val state = BabaEncounter(UUID(3, 3), 11)
        assertTrue(state.rubbleDue(100))
        state.markRubbleSequenceStarted(100)
        assertFalse(state.rubbleDue(149))
        assertTrue(state.rubbleDue(150))
        assertEquals(50, BabaTuning.RUBBLE_COOLDOWN_TICKS.value)
    }

    @Test
    fun `shockwave has directional base and expanded masks`() {
        val boss = CoordGrid(100, 100, 0)
        val targets =
            listOf(
                CoordGrid(102, 120, 0),
                CoordGrid(120, 120, 0),
                CoordGrid(120, 102, 0),
                CoordGrid(120, 80, 0),
                CoordGrid(102, 80, 0),
                CoordGrid(80, 80, 0),
                CoordGrid(80, 102, 0),
                CoordGrid(80, 120, 0),
            )
        val directions = targets.map { BabaShockwaveMask.tiles(boss, it, false).first }.toSet()
        assertEquals(BabaDirection.entries.toSet(), directions)
        assertEquals(13, BabaShockwaveMask.tiles(boss, targets.first(), false).second.size)
        assertEquals(29, BabaShockwaveMask.tiles(boss, targets.first(), true).second.size)
        assertEquals(4, BabaShockwaveMask.mindGapTiles(boss, targets.first()).size)
    }

    @Test
    fun `rock throw cover resolves diagonally with stable priority`() {
        val impact = CoordGrid(50, 50, 0)
        val rubble = CoordGrid(51, 51, 0)
        val sarcophagus = CoordGrid(50, 49, 0)
        val baboon = CoordGrid(49, 50, 0)
        assertEquals(
            BabaCoverKind.RUBBLE to rubble,
            BabaCoverResolver.select(impact, listOf(rubble), listOf(sarcophagus), listOf(baboon)),
        )
        assertEquals(
            BabaCoverKind.SARCOPHAGUS to sarcophagus,
            BabaCoverResolver.select(impact, emptyList(), listOf(sarcophagus), listOf(baboon)),
        )
        assertEquals(
            BabaCoverKind.BABOON to baboon,
            BabaCoverResolver.select(impact, emptyList(), emptyList(), listOf(baboon)),
        )
    }

    @Test
    fun `thrown rock only damages a player who remains on its captured impact tile`() {
        val impact = CoordGrid(50, 50, 0)
        assertTrue(BabaRockImpactResolver.playerRemainsOnImpactTile(impact, impact))
        assertFalse(BabaRockImpactResolver.playerRemainsOnImpactTile(CoordGrid(51, 50, 0), impact))
        assertFalse(BabaRockImpactResolver.playerRemainsOnImpactTile(CoordGrid(50, 50, 1), impact))
        assertEquals(
            150,
            ToaProjectileTiming.clientCyclesForServerTicks(
                BabaTuning.ROCK_THROW_WARNING_TICKS.value
            ),
        )
    }

    @Test
    fun `weak boulder accepts ranged only`() {
        assertTrue(babaWeakBoulderCanReceive(HitType.Ranged))
        assertFalse(babaWeakBoulderCanReceive(HitType.Melee))
        assertFalse(babaWeakBoulderCanReceive(HitType.Magic))
    }

    @Test
    fun `encounter reset invalidates old delayed generation and clears hazards`() {
        val state = BabaEncounter(UUID(3, 3), 88)
        val token = state.token()
        state.pendingRubble += BabaPendingRubble(CoordGrid(10, 10, 0), 5, 0)
        state.pendingShockwaves += BabaPendingShockwave(emptySet(), emptySet(), 5, 20)
        state.nextBaboonSarcophagusAt = 10
        state.baboonSarcophagusRunner = 42
        state.reset()
        assertFalse(state.isCurrent(token))
        assertTrue(state.pendingRubble.isEmpty())
        assertTrue(state.pendingShockwaves.isEmpty())
        assertEquals(-1, state.nextBaboonSarcophagusAt)
        assertEquals(null, state.baboonSarcophagusRunner)
        assertEquals(BabaState.WAITING, state.state)
    }

    @Test
    fun `melee from beyond the boulder line ends rolling stones`() {
        val boulderStart = CoordGrid(98, 100, 0)
        val playerPastStart = CoordGrid(99, 102, 0)

        assertTrue(BabaRollingExit.inExitZone(playerPastStart, boulderStart))
        assertTrue(BabaRollingExit.canFinish(playerPastStart, boulderStart, HitType.Melee))
        assertFalse(BabaRollingExit.canFinish(playerPastStart, boulderStart, HitType.Ranged))
        assertFalse(BabaRollingExit.canFinish(playerPastStart, boulderStart, HitType.Magic))
        assertFalse(BabaRollingExit.inExitZone(CoordGrid(98, 102, 0), boulderStart))
        assertFalse(BabaRollingExit.inExitZone(CoordGrid(99, 102, 1), boulderStart))

        // Melee range is enforced by combat itself. The rolling-stone rule only requires that
        // the attack be melee and originate east of the boulder spawn line.
        assertTrue(BabaRollingExit.inExitZone(CoordGrid(99, 140, 0), boulderStart))
    }
}
