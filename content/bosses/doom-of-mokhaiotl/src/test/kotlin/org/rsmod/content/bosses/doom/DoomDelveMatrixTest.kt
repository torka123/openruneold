package org.rsmod.content.bosses.doom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DoomDelveMatrixTest {
    @Test
    fun `delves one through eight expose the exact combat and mechanic matrix`() {
        val expected =
            listOf(
                ExpectedDelve(1, 525, 6, 47, 60, 1, 1, 1, 1),
                ExpectedDelve(2, 550, 6, 50, 60, 2, 1, 1, 1, larvaProtection = true),
                ExpectedDelve(
                    3,
                    575,
                    5,
                    50,
                    60,
                    2,
                    1,
                    2,
                    1,
                    acidTiles = 2..3,
                    shield = true,
                    larvaProtection = true,
                ),
                ExpectedDelve(
                    4,
                    600,
                    5,
                    52,
                    80,
                    3,
                    1,
                    2,
                    1,
                    acidTiles = 2..3,
                    shield = true,
                    colouredLarvae = true,
                ),
                ExpectedDelve(
                    5,
                    625,
                    5,
                    53,
                    80,
                    3,
                    1,
                    3,
                    2,
                    acidTiles = 2..3,
                    shield = true,
                    colouredLarvae = true,
                    car = ExpectedCar(zooms = 2, speed = 4, rocks = 25, trample = 10),
                ),
                ExpectedDelve(
                    6,
                    650,
                    5,
                    55,
                    99,
                    3,
                    1,
                    3,
                    2,
                    acidTiles = 2..3,
                    clearAcid = true,
                    shield = true,
                    colouredLarvae = true,
                    car = ExpectedCar(3, 4, 23, 20, projectiles = true, slam = true),
                ),
                ExpectedDelve(
                    7,
                    650,
                    4,
                    57,
                    99,
                    3,
                    1,
                    4,
                    2,
                    acidTiles = 2..3,
                    shield = true,
                    colouredLarvae = true,
                    car = ExpectedCar(3, 6, 23, 30, projectiles = true, slam = true),
                ),
                ExpectedDelve(
                    8,
                    675,
                    4,
                    65,
                    99,
                    3,
                    2,
                    5,
                    3,
                    acidTiles = 4..4,
                    shield = true,
                    colouredLarvae = true,
                    giantLarva = false,
                    car = ExpectedCar(3, 8, 16, 40, projectiles = true, slam = true),
                ),
            )
        expected.forEach { row ->
            val profile = DoomRules.profile(row.delve)
            assertEquals(row.hitpoints, profile.hitpoints, "hitpoints delve ${row.delve}")
            assertEquals(row.attackRate, profile.attackRateTicks, "attack rate delve ${row.delve}")
            assertEquals(
                row.standardMaxHit,
                profile.standardMaxHit,
                "standard max delve ${row.delve}",
            )
            assertEquals(row.beamMaxHit, profile.beamMaxHit, "beam max delve ${row.delve}")
            assertEquals(row.rockProjectiles, profile.rockProjectiles, "rocks delve ${row.delve}")
            assertEquals(row.rockSets, profile.rockSets, "rock sets delve ${row.delve}")
            assertEquals(row.shockwaveSlams, profile.shockwaveSlams, "slams delve ${row.delve}")
            assertEquals(row.outsideLarvae, profile.outsideLarvaCount, "larvae delve ${row.delve}")
            assertEquals(
                row.delve >= 1,
                profile.shockwaveEnabled,
                "shockwave gate delve ${row.delve}",
            )
            assertEquals(
                row.delve >= 1,
                profile.volatileEarthEnabled,
                "volatile earth gate delve ${row.delve}",
            )
            assertEquals(row.shield, profile.shieldEnabled, "shield delve ${row.delve}")
            assertEquals(row.acidTiles != null, profile.acidEnabled, "acid delve ${row.delve}")
            assertEquals(
                row.clearAcid,
                !DoomPersistenceRules.shouldCarryAcidInto(row.delve),
                "descent acid clear delve ${row.delve}",
            )
            row.acidTiles?.let {
                assertEquals(it, profile.acidTilesPerHit, "acid tiles delve ${row.delve}")
            }

            val larva = DoomLarvaRules.profile(row.delve, DoomLarvaContext.OUTSIDE_SHIELD)
            assertEquals(row.giantLarva, larva.giant, "giant larva delve ${row.delve}")
            assertEquals(
                row.larvaProtection,
                DoomLarvaRules.protectionEnabled(row.delve),
                "larva protection delve ${row.delve}",
            )
            val expectedStyles =
                if (row.colouredLarvae) {
                    setOf(DoomAttackStyle.RANGED, DoomAttackStyle.MAGIC)
                } else {
                    setOf(null)
                }
            assertEquals(
                expectedStyles,
                DoomLarvaRules.styleOptions(row.delve, DoomLarvaContext.OUTSIDE_SHIELD).toSet(),
                "larva styles delve ${row.delve}",
            )

            val car = row.car
            assertEquals(car != null, profile.carEnabled, "car gate delve ${row.delve}")
            if (car == null) {
                assertFalse(profile.carProjectilesEnabled, "car projectiles delve ${row.delve}")
                assertFalse(profile.carSlamEnabled, "car slam delve ${row.delve}")
            } else {
                assertEquals(car.zooms, profile.carZoomCount, "car zooms delve ${row.delve}")
                assertEquals(car.speed, profile.carTilesPerTick, "car speed delve ${row.delve}")
                assertEquals(car.rocks, profile.carRockCount, "car rocks delve ${row.delve}")
                assertEquals(car.trample, profile.trampleMaxHit, "trample delve ${row.delve}")
                assertEquals(
                    car.projectiles,
                    profile.carProjectilesEnabled,
                    "car projectiles delve ${row.delve}",
                )
                assertEquals(car.slam, profile.carSlamEnabled, "car slam delve ${row.delve}")
            }
        }
    }

    @Test
    fun `car schedules are exact for every active delve tier`() {
        val delveFive = DoomCarTimeline.schedule(100, delve = 5, exactMoveSegments = 2)
        assertEquals(103, delveFive.dashStartCycle)
        assertEquals(listOf(103, 104), delveFive.exactMoveCycles)
        assertEquals(listOf(104, 105), delveFive.trampleImpactCycles)
        assertTrue(delveFive.projectileCycles.isEmpty())
        assertEquals(null, delveFive.slamPacketCycle)
        assertEquals(null, delveFive.waveCycle)
        assertEquals(null, delveFive.nextEyeCycle)

        for (delve in 6..7) {
            val schedule = DoomCarTimeline.schedule(100, delve, exactMoveSegments = 3)
            assertEquals(103, schedule.dashStartCycle, "dash delve $delve")
            assertEquals(listOf(103, 104, 105), schedule.exactMoveCycles, "moves delve $delve")
            assertEquals(listOf(104, 105, 106), schedule.trampleImpactCycles, "hits delve $delve")
            assertEquals(listOf(103, 105), schedule.projectileCycles, "shots delve $delve")
            assertEquals(106, schedule.slamPacketCycle, "slam delve $delve")
            assertEquals(112, schedule.waveCycle, "wave delve $delve")
            assertEquals(116, schedule.nextEyeCycle, "next eye delve $delve")
        }

        val delveEight = DoomCarTimeline.schedule(100, delve = 8, exactMoveSegments = 2)
        assertEquals(103, delveEight.dashStartCycle)
        assertEquals(listOf(103, 104), delveEight.exactMoveCycles)
        assertEquals(listOf(104, 105), delveEight.trampleImpactCycles)
        assertEquals(listOf(103, 105), delveEight.projectileCycles)
        assertEquals(105, delveEight.slamPacketCycle)
        assertEquals(111, delveEight.waveCycle)
        assertEquals(115, delveEight.nextEyeCycle)
    }

    @Test
    fun `delve eight launches two same-style alternating rock volleys`() {
        assertEquals(2, DoomRules.rockSets(8))
        val firstStyle = DoomAttackStyle.MAGIC
        val volleys =
            List(DoomRules.rockSets(8)) {
                DoomRockFollowupRules.alternatingStyles(firstStyle, DoomRules.rockProjectiles(8))
            }
        val expected = listOf(DoomAttackStyle.MAGIC, DoomAttackStyle.RANGED, DoomAttackStyle.MAGIC)
        assertEquals(listOf(expected, expected), volleys)
    }

    private data class ExpectedDelve(
        val delve: Int,
        val hitpoints: Int,
        val attackRate: Int,
        val standardMaxHit: Int,
        val beamMaxHit: Int,
        val rockProjectiles: Int,
        val rockSets: Int,
        val shockwaveSlams: Int,
        val outsideLarvae: Int,
        val acidTiles: IntRange? = null,
        val clearAcid: Boolean = false,
        val shield: Boolean = false,
        val larvaProtection: Boolean = false,
        val colouredLarvae: Boolean = false,
        val giantLarva: Boolean = false,
        val car: ExpectedCar? = null,
    )

    private data class ExpectedCar(
        val zooms: Int,
        val speed: Int,
        val rocks: Int,
        val trample: Int,
        val projectiles: Boolean = false,
        val slam: Boolean = false,
    )
}
