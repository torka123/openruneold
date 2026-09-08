package org.rsmod.content.bosses.doom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.rsmod.map.CoordGrid

class DoomMechanicsRulesTest {
    @Test
    fun `delve profiles expose the exact mechanic gates`() {
        val one = DoomRules.profile(1)
        assertEquals(525, one.hitpoints)
        assertEquals(1, one.rockProjectiles)
        assertFalse(one.meleeProjectileEnabled)
        assertFalse(one.acidEnabled)
        assertFalse(one.shieldEnabled)
        assertFalse(one.carEnabled)

        val two = DoomRules.profile(2)
        assertTrue(two.meleeProjectileEnabled)
        assertEquals(2, two.rockProjectiles)
        assertEquals(
            listOf(DoomAttackStyle.RANGED, DoomAttackStyle.MAGIC),
            DoomRockFollowupRules.alternatingStyles(DoomAttackStyle.RANGED, two.rockProjectiles),
        )

        val three = DoomRules.profile(3)
        assertTrue(three.acidEnabled)
        assertTrue(three.shieldEnabled)
        assertEquals(500, DoomRules.SHIELD_HITPOINTS)
        assertEquals(5, three.shieldFirstLarvaDelayTicks)
        assertEquals(7..9, three.shieldLarvaIntervalRangeTicks)

        val four = DoomRules.profile(4)
        assertEquals(3, four.rockProjectiles)
        assertEquals(
            listOf(DoomAttackStyle.MAGIC, DoomAttackStyle.RANGED, DoomAttackStyle.MAGIC),
            DoomRockFollowupRules.alternatingStyles(DoomAttackStyle.MAGIC, four.rockProjectiles),
        )

        val five = DoomRules.profile(5)
        assertTrue(five.carEnabled)
        assertEquals(2, five.carZoomCount)
        assertFalse(five.carProjectilesEnabled)
        assertFalse(five.carSlamEnabled)
        assertEquals(25, five.carRockCount)

        val six = DoomRules.profile(6)
        assertEquals(3, six.carZoomCount)
        assertTrue(six.carProjectilesEnabled)
        assertTrue(six.carSlamEnabled)
        assertEquals(23, six.carRockCount)
        assertEquals(5..7, six.shieldLarvaIntervalRangeTicks)

        val seven = DoomRules.profile(7)
        assertEquals(4, seven.attackRateTicks)
        assertEquals(4, seven.shockwaveSlams)

        val eight = DoomRules.profile(8)
        assertEquals(675, eight.hitpoints)
        assertEquals(2, eight.rockSets)
        assertEquals(3, eight.rockProjectiles)
        assertEquals(3, eight.outsideLarvaCount)
        assertEquals(4..4, eight.acidTilesPerHit)
        assertEquals(8, eight.carTilesPerTick)
        assertEquals(16, eight.carRockCount)
        assertEquals(4..6, eight.shieldLarvaIntervalRangeTicks)
        assertEquals(12, DoomRules.profile(9).carRockCount)
        assertEquals(12, DoomRules.profile(20).carRockCount)
    }

    @Test
    fun `prompt start and standard projectile tiers are deterministic`() {
        assertTrue(DoomActivationRules.shouldSpawn(promptAdvanced = true, bossPresent = false))
        assertFalse(DoomActivationRules.shouldSpawn(promptAdvanced = false, bossPresent = false))
        assertFalse(DoomActivationRules.shouldSpawn(promptAdvanced = true, bossPresent = true))
        assertEquals(103, DoomActivationRules.spawnCycle(100))
        assertEquals(203, DoomCombatTimeline.firstShockwaveCycle(103))

        assertEquals(106, DoomCombatTimeline.standardProjectileImpactCycle(100, 1))
        assertEquals(105, DoomCombatTimeline.standardProjectileImpactCycle(100, 2))
        assertEquals(104, DoomCombatTimeline.standardProjectileImpactCycle(100, 3))
        assertEquals(104, DoomCombatTimeline.standardProjectileImpactCycle(100, 5))
        assertEquals(103, DoomCombatTimeline.standardProjectileImpactCycle(100, 6))
        assertEquals(103, DoomCombatTimeline.standardProjectileImpactCycle(100, 8))
    }

    @Test
    fun `shield larva schedule separates first delay from delve interval windows`() {
        assertEquals(105, DoomCombatTimeline.firstShieldLarvaSpawnCycle(100))
        assertEquals(207, DoomCombatTimeline.nextShieldLarvaSpawnCycle(200, 3, 7))
        assertEquals(209, DoomCombatTimeline.nextShieldLarvaSpawnCycle(200, 4, 9))
        assertEquals(205, DoomCombatTimeline.nextShieldLarvaSpawnCycle(200, 5, 5))
        assertEquals(207, DoomCombatTimeline.nextShieldLarvaSpawnCycle(200, 6, 7))
        assertEquals(204, DoomCombatTimeline.nextShieldLarvaSpawnCycle(200, 7, 4))
        assertEquals(206, DoomCombatTimeline.nextShieldLarvaSpawnCycle(200, 8, 6))
        assertFailsWith<IllegalArgumentException> {
            DoomCombatTimeline.nextShieldLarvaSpawnCycle(200, 3, 6)
        }
        assertFailsWith<IllegalArgumentException> { DoomRules.shieldLarvaIntervalRange(2) }
    }

    @Test
    fun `damage ranges distinguish normal rolls from explicitly guaranteed hits`() {
        assertEquals(0..47, DoomAccuracyRules.damageRange(maxHit = 47, guaranteedAccuracy = false))
        assertEquals(0..47, DoomAccuracyRules.damageRange(maxHit = 47, guaranteedAccuracy = true))
        assertEquals(0..0, DoomAccuracyRules.damageRange(maxHit = 0, guaranteedAccuracy = true))
        assertFailsWith<IllegalArgumentException> {
            DoomAccuracyRules.damageRange(maxHit = -1, guaranteedAccuracy = true)
        }
    }

    @Test
    fun `larva placement retains shield stream and constrains outside pairs`() {
        val southWest = DoomLarvaSpawnDirection(-1, -1)
        val west = DoomLarvaSpawnDirection(-1, 0)
        val northWest = DoomLarvaSpawnDirection(-1, 1)

        assertEquals(west, DoomLarvaPlacementRules.selectShieldStream(null, setOf(west, northWest)))
        assertEquals(
            northWest,
            DoomLarvaPlacementRules.selectShieldStream(
                current = northWest,
                available = setOf(west, northWest),
            ),
        )
        assertEquals(
            west,
            DoomLarvaPlacementRules.selectShieldStream(
                current = southWest,
                available = setOf(west, northWest),
            ),
        )

        val boss = CoordGrid(100, 100, 0)
        val player = CoordGrid(96, 100, 0)
        assertTrue(
            DoomLarvaPlacementRules.isBeyondPlayerOnBossSide(boss, player, CoordGrid(92, 101, 0))
        )
        assertFalse(
            DoomLarvaPlacementRules.isBeyondPlayerOnBossSide(boss, player, CoordGrid(104, 100, 0))
        )
        assertFalse(
            DoomLarvaPlacementRules.isBeyondPlayerOnBossSide(boss, player, CoordGrid(98, 100, 0))
        )
        assertTrue(
            DoomLarvaPlacementRules.isAdjacentPair(CoordGrid(92, 100, 0), CoordGrid(92, 101, 0))
        )
        assertFalse(
            DoomLarvaPlacementRules.isAdjacentPair(CoordGrid(92, 100, 0), CoordGrid(92, 102, 0))
        )

        val open =
            listOf(
                CoordGrid(90, 100, 0),
                CoordGrid(90, 101, 0),
                CoordGrid(90, 102, 0),
                CoordGrid(105, 105, 0),
            )
        val preferred = open.take(3).toSet()
        val adjacent: (CoordGrid) -> List<CoordGrid> = { tile ->
            listOf(
                tile.translate(-1, 0),
                tile.translate(1, 0),
                tile.translate(0, -1),
                tile.translate(0, 1),
            )
        }
        assertEquals(open.take(3), DoomLarvaPlacementRules.selectWave(3, open, preferred, adjacent))
    }

    @Test
    fun `larva wave selector backtracks and preserves an exact deterministic count`() {
        val start = CoordGrid(10, 10, 0)
        val deadEnd = CoordGrid(11, 10, 0)
        val branch = CoordGrid(10, 11, 0)
        val finish = CoordGrid(10, 12, 0)
        val candidates = listOf(start, deadEnd, branch, finish)
        val adjacency =
            mapOf(
                start to listOf(deadEnd, branch),
                deadEnd to listOf(start),
                branch to listOf(start, finish),
                finish to listOf(branch),
            )
        val selected =
            DoomLarvaPlacementRules.selectWave(
                count = 3,
                orderedCandidates = candidates,
                preferredCandidates = candidates.toSet(),
                adjacent = { adjacency[it].orEmpty() },
            )

        assertEquals(listOf(start, branch, finish), selected)
        repeat(20) {
            assertEquals(
                selected,
                DoomLarvaPlacementRules.selectWave(
                    3,
                    candidates,
                    candidates.toSet(),
                    { adjacency[it].orEmpty() },
                ),
            )
        }
    }

    @Test
    fun `larva wave selector uses safe fragmented fallback and never returns partial waves`() {
        val isolated = listOf(CoordGrid(1, 1, 0), CoordGrid(5, 5, 0), CoordGrid(9, 9, 0))
        val noNeighbours: (CoordGrid) -> List<CoordGrid> = { emptyList() }

        assertEquals(
            isolated,
            DoomLarvaPlacementRules.selectWave(3, isolated, emptySet(), noNeighbours),
        )
        assertTrue(
            DoomLarvaPlacementRules.selectWave(3, isolated.take(2), emptySet(), noNeighbours)
                .isEmpty()
        )
    }

    @Test
    fun `shield threshold cannot be skipped and only standard attacks advance it`() {
        val maxHitpoints = DoomRules.hitpoints(3)
        assertEquals(431, DoomShieldRules.thresholdHitpoints(maxHitpoints))
        assertFalse(
            DoomShieldRules.shouldArm(
                delve = 3,
                alreadyTriggered = false,
                currentHitpoints = 440,
                incomingDamage = 8,
            )
        )
        assertTrue(
            DoomShieldRules.shouldArm(
                delve = 3,
                alreadyTriggered = false,
                currentHitpoints = 440,
                incomingDamage = 9,
            )
        )
        assertTrue(
            DoomShieldRules.shouldArm(
                delve = 3,
                alreadyTriggered = false,
                currentHitpoints = 575,
                incomingDamage = 300,
            )
        )
        assertFalse(
            DoomShieldRules.shouldArm(
                delve = 2,
                alreadyTriggered = false,
                currentHitpoints = 400,
                incomingDamage = 100,
            )
        )

        assertFalse(DoomShieldRules.shouldArmAfterImpact(3, false, 432))
        assertTrue(DoomShieldRules.shouldArmAfterImpact(3, false, 431))
        assertFalse(DoomShieldRules.shouldArmAfterImpact(3, true, 400))
        assertFalse(DoomShieldRules.shouldArmAfterImpact(2, false, 1))
        assertFalse(DoomShieldRules.shouldArmAfterImpact(3, false, 0))

        var remaining = DoomShieldRules.initialStandardAttacksRemaining()
        remaining = DoomShieldRules.attacksRemainingAfter(remaining, DoomPrimaryAttack.ROCKS)
        assertEquals(2, remaining)
        remaining = DoomShieldRules.attacksRemainingAfter(remaining, DoomPrimaryAttack.TONGUE)
        assertEquals(2, remaining)
        remaining = DoomShieldRules.attacksRemainingAfter(remaining, DoomPrimaryAttack.STANDARD)
        assertEquals(1, remaining)
        remaining = DoomShieldRules.attacksRemainingAfter(remaining, DoomPrimaryAttack.STANDARD)
        assertEquals(0, remaining)

        assertEquals(375, DoomShieldRules.normalHitpointsAfterTransitionHit(400, 25))
        assertEquals(0, DoomShieldRules.normalHitpointsAfterTransitionHit(20, 25))
        assertEquals(
            DoomLarvaHealPool.STORED_NORMAL,
            DoomLarvaHealingRules.pool(DoomState.SHIELDED),
        )
        assertEquals(
            DoomLarvaHealPool.ACTIVE_BOSS,
            DoomLarvaHealingRules.pool(DoomState.SHIELD_TRANSITION),
        )
    }

    @Test
    fun `shockwave wins transition priority and retains captured cadence`() {
        assertEquals(
            DoomPhaseTransition.SHOCKWAVE,
            DoomPhasePriority.select(
                shockwaveDue = true,
                shockwavePreparing = false,
                shieldReady = true,
            ),
        )
        assertEquals(
            DoomPhaseTransition.SHOCKWAVE,
            DoomPhasePriority.select(
                shockwaveDue = false,
                shockwavePreparing = true,
                shieldReady = true,
            ),
        )
        assertEquals(
            DoomPhaseTransition.SHIELD,
            DoomPhasePriority.select(
                shockwaveDue = false,
                shockwavePreparing = false,
                shieldReady = true,
            ),
        )
        assertEquals(
            listOf(204, 206, 208, 210, 212),
            DoomCombatTimeline.shockwaveSlamCycles(200, 8),
        )
        assertEquals(
            listOf(206, 208, 210, 212, 214),
            DoomCombatTimeline.shockwavePulseCycles(200, 8),
        )
    }

    @Test
    fun `rock splits at its fixed impact tile but rubble targets the player at detonation`() {
        val projectileImpactTile = CoordGrid(100, 100, 0)
        val playerTileAtDetonation = CoordGrid(104, 103, 0)
        val detonationTile =
            DoomRockTargeting.targetAtDetonation(
                projectileImpactTile = projectileImpactTile,
                playerTileAtDetonation = playerTileAtDetonation,
            )
        assertEquals(playerTileAtDetonation, detonationTile)
        val firstCandidates = (1..20).map { CoordGrid(100 + it, 100, 0) }
        val first = DoomRockTargeting.plan(detonationTile, firstCandidates)
        assertEquals(detonationTile, first.target)
        assertEquals(DoomRules.ROCK_DEBRIS_COUNT, first.tiles.size)
        val followupSource =
            DoomRockTargeting.followupSource(
                debrisTiles = first.tiles,
                rubbleTile = first.target,
                index = 0,
                fallback = CoordGrid(90, 90, 0),
            )
        assertNotEquals(first.target, followupSource)

        val secondCandidates = (1..20).map { CoordGrid(200 + it, 100, 0) }
        val second =
            DoomRockTargeting.plan(
                playerAtDetonation = detonationTile,
                candidates = secondCandidates,
                reserved = first.tiles.toSet(),
            )
        assertNotEquals(detonationTile, second.target)
        assertTrue(first.tiles.toSet().intersect(second.tiles.toSet()).isEmpty())
        assertFailsWith<IllegalArgumentException> {
            DoomRockTargeting.plan(detonationTile, emptyList(), debrisCount = 2)
        }
    }

    @Test
    fun `shield larvae stay small and giant reach adds one persistent charge`() {
        val outside = DoomLarvaRules.profile(8, DoomLarvaContext.OUTSIDE_SHIELD)
        assertFalse(outside.giant)
        assertEquals(2, outside.hitpoints)
        assertEquals(1, outside.demonicChargeGain)

        val shield = DoomLarvaRules.profile(8, DoomLarvaContext.SHIELD)
        assertFalse(shield.giant)
        assertEquals(2, shield.hitpoints)
        assertEquals(1, shield.demonicChargeGain)
        assertEquals(
            listOf<DoomAttackStyle?>(null),
            DoomLarvaRules.styleOptions(1, DoomLarvaContext.OUTSIDE_SHIELD),
        )
        assertFalse(DoomLarvaRules.protectionEnabled(1))
        assertTrue(DoomLarvaRules.protectionEnabled(2))
        assertFalse(DoomLarvaRules.twoProtectionEnabled(3))
        assertTrue(DoomLarvaRules.twoProtectionEnabled(4))
        assertEquals(
            listOf(7),
            DoomLarvaRules.headIconIndices(
                DoomAttackStyle.MAGIC,
                setOf(DoomAttackStyle.MELEE, DoomAttackStyle.RANGED),
            ),
        )
        assertEquals(
            DoomAttackStyle.entries.toSet(),
            DoomLarvaRules.styleOptions(8, DoomLarvaContext.SHIELD).toSet(),
        )
        assertEquals(
            setOf(DoomAttackStyle.RANGED, DoomAttackStyle.MAGIC),
            DoomLarvaRules.styleOptions(8, DoomLarvaContext.OUTSIDE_SHIELD).toSet(),
        )
        assertEquals(0, DoomLarvaRules.headIconIndex(null, DoomAttackStyle.MELEE))
        assertEquals(1, DoomLarvaRules.headIconIndex(null, DoomAttackStyle.RANGED))
        assertEquals(2, DoomLarvaRules.headIconIndex(null, DoomAttackStyle.MAGIC))
        assertEquals(6, DoomLarvaRules.headIconIndex(DoomAttackStyle.MELEE, null))
        assertEquals(7, DoomLarvaRules.headIconIndex(DoomAttackStyle.MAGIC, null))
        assertEquals(8, DoomLarvaRules.headIconIndex(DoomAttackStyle.RANGED, null))
        assertEquals(null, DoomLarvaRules.headIconIndex(null, null))
        assertFalse(
            DoomLarvaRules.shouldDespawn(
                spawnCycle = 100,
                currentCycle = 133,
                bossRepositioned = true,
                bossSurroundedByRocks = false,
            )
        )
        assertTrue(DoomLarvaRules.shouldDespawn(100, 134, true, false))
        assertTrue(DoomLarvaRules.shouldDespawn(100, 134, false, true))
        assertFalse(
            DoomLarvaRules.shouldDespawn(100, 200, false, false),
            "A larva must keep routing when Doom is neither repositioned nor boxed in.",
        )

        assertTrue(DoomLarvaRules.hazardsSuppressed(100, 100))
        assertTrue(DoomLarvaRules.hazardsSuppressed(100, 102))
        assertFalse(DoomLarvaRules.hazardsSuppressed(100, 103))
    }

    @Test
    fun `car zoom count speed and swept footprint are depth aware`() {
        assertEquals(2, DoomRules.carZooms(5))
        assertEquals(3, DoomRules.carZooms(6))
        assertEquals(2, DoomRules.carTravelTicks(delve = 7, distance = 7))
        assertEquals(1, DoomRules.carTravelTicks(delve = 8, distance = 7))
        assertEquals(101, DoomCombatTimeline.carArrivalCycle(100, delve = 8, distance = 7))

        val start = CoordGrid(0, 0, 0)
        val end = CoordGrid(4, 4, 0)
        assertTrue(DoomFootprintGeometry.intersects(start, end, size = 2, CoordGrid(3, 2, 0)))
        assertFalse(DoomFootprintGeometry.intersects(start, end, size = 2, CoordGrid(3, 0, 0)))
    }

    @Test
    fun `slam protection consumes only the nearest line of fire rock`() {
        val origin = CoordGrid(0, 0, 0)
        val player = CoordGrid(6, 0, 0)
        val nearest = CoordGrid(2, 0, 0)
        val farther = CoordGrid(4, 0, 0)
        val unrelated = CoordGrid(2, 1, 0)
        val rocks = listOf(farther, unrelated, nearest)

        assertEquals(nearest, DoomSlamRockRules.blockingRock(origin, player, rocks))
        assertTrue(DoomSlamRockRules.protects(origin, player, rocks))
        assertEquals(setOf(nearest), DoomSlamRockRules.destroyedBySlam(origin, player, rocks))
        assertFalse(DoomSlamRockRules.protects(origin, player, listOf(unrelated)))

        val diagonalPlayer = CoordGrid(6, 6, 0)
        assertTrue(DoomSlamRockRules.protects(origin, diagonalPlayer, listOf(CoordGrid(3, 3, 0))))
        assertFalse(DoomSlamRockRules.protects(origin, diagonalPlayer, listOf(CoordGrid(3, 4, 0))))
    }
}
