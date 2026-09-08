package org.rsmod.content.bosses.doom

import kotlin.test.Test
import kotlin.test.assertEquals

class DoomCaptureReplayTest {
    @Test
    fun `recorded charge windows replay exactly`() {
        assertEquals(13, DoomRules.chargeTicks(DoomState.MELEE_CHARGE))
        assertEquals(17, DoomRules.chargeTicks(DoomState.SHIELDED))
        assertEquals(20, DoomRules.chargeTicks(DoomState.BURROWING))
        assertEquals(2, DoomRules.SHIELD_TRANSITION_TICKS)
        assertEquals(2, DoomRules.LARVA_SPAWN_TICKS)
    }

    @Test
    fun `recorded rock split replays for delves one through deep nine`() {
        val expectedArrivals =
            mapOf(
                1 to listOf(3),
                2 to listOf(3, 4),
                3 to listOf(3, 4),
                4 to listOf(3, 4, 5),
                5 to listOf(3, 4, 5),
                6 to listOf(3, 4, 5),
                7 to listOf(1, 2, 3),
                8 to listOf(1, 2, 3),
                9 to listOf(1, 2, 3),
            )
        expectedArrivals.forEach { (delve, arrivals) ->
            assertEquals(arrivals, DoomRules.rockFollowupTravelTicks(delve), "delve $delve")
        }
        assertEquals(8, DoomRules.ROCK_DEBRIS_COUNT)
        assertEquals(7, DoomRules.ROCK_EXPLOSION_TICKS)
        assertEquals(2, DoomRules.ROCK_SHADOW_TICKS)
    }

    @Test
    fun `captured attack and beam profiles cover delves one through deep nine`() {
        val expectedRates = listOf(6, 6, 5, 5, 5, 5, 4, 4, 4)
        val expectedBeams = listOf(60, 60, 60, 80, 80, 99, 99, 99, 99)
        for (delve in 1..9) {
            assertEquals(expectedRates[delve - 1], DoomRules.attackRate(delve), "rate $delve")
            assertEquals(expectedBeams[delve - 1], DoomRules.beamMaxHit(delve), "beam $delve")
        }
        val expectedActiveZooms = mapOf(5 to 2, 6 to 3, 7 to 3, 8 to 3, 9 to 3)
        expectedActiveZooms.forEach { (delve, zooms) ->
            assertEquals(zooms, DoomRules.carZooms(delve), "zooms $delve")
        }
        assertEquals(500, DoomRules.SHIELD_HITPOINTS)
        assertEquals(3429, DoomRules.acidSplashGraphic(0, 1))
        assertEquals(3430, DoomRules.acidSplashGraphic(1, 0))
        assertEquals(3431, DoomRules.acidSplashGraphic(0, -1))
        assertEquals(3432, DoomRules.acidSplashGraphic(-1, 0))
    }

    @Test
    fun `deep nine run 26 car packets replay on their captured cycles`() {
        val schedule =
            DoomCarTimeline.schedule(eyeSpawnCycle = 4146, delve = 9, exactMoveSegments = 2)

        assertEquals(60, schedule.persistentEyeClientDelay)
        assertEquals(4149, schedule.dashStartCycle)
        assertEquals(listOf(4149, 4150), schedule.exactMoveCycles)
        assertEquals(listOf(4150, 4151), schedule.trampleImpactCycles)
        assertEquals(listOf(4149, 4151), schedule.projectileCycles)
        assertEquals(4151, schedule.slamPacketCycle)
        assertEquals(120, schedule.slamClientDelay)
        assertEquals(4157, schedule.waveCycle)
        assertEquals(4161, schedule.nextEyeCycle)
    }

    @Test
    fun `delve eight run 8 starts its attack on the first exact move`() {
        val schedule =
            DoomCarTimeline.schedule(eyeSpawnCycle = 3294, delve = 8, exactMoveSegments = 1)

        assertEquals(3297, schedule.dashStartCycle)
        assertEquals(listOf(3297), schedule.exactMoveCycles)
        assertEquals(listOf(3298), schedule.trampleImpactCycles)
        assertEquals(listOf(3297), schedule.projectileCycles)
        assertEquals(3298, schedule.slamPacketCycle)
    }

    @Test
    fun `delve six car projectiles follow captured global cadence and travel duration`() {
        val evenThree = DoomCarTimeline.schedule(1623, delve = 6, exactMoveSegments = 3)
        assertEquals(1626, evenThree.dashStartCycle)
        assertEquals(1629, evenThree.slamPacketCycle)
        assertEquals(listOf(1627, 1629), evenThree.projectileCycles)

        val evenFour = DoomCarTimeline.schedule(1639, delve = 6, exactMoveSegments = 4)
        assertEquals(1642, evenFour.dashStartCycle)
        assertEquals(1646, evenFour.slamPacketCycle)
        assertEquals(listOf(1643, 1645), evenFour.projectileCycles)

        val oddFour = DoomCarTimeline.schedule(1656, delve = 6, exactMoveSegments = 4)
        assertEquals(1659, oddFour.dashStartCycle)
        assertEquals(1663, oddFour.slamPacketCycle)
        assertEquals(listOf(1659, 1661, 1663), oddFour.projectileCycles)
    }

    @Test
    fun `delve seven uses its captured six tile exact move speed`() {
        assertEquals(6, DoomRules.carTilesPerTick(7))
        assertEquals(2, DoomRules.carTravelTicks(7, distance = 10))
        assertEquals(3, DoomRules.carTravelTicks(7, distance = 14))
    }

    @Test
    fun `car projectiles use normal boss accuracy rolls`() {
        assertEquals(false, DoomCarAttackRules.PROJECTILES_GUARANTEED_ACCURACY)
    }
}
