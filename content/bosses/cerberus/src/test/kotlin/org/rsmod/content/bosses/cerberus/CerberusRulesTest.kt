package org.rsmod.content.bosses.cerberus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.api.instances.InstanceArea
import org.rsmod.map.CoordGrid

class CerberusRulesTest {
    @Test
    fun `western room uses region 4883 and the verified source tiles`() {
        val area = CERBERUS_AREA as InstanceArea.CopyRegions
        assertEquals(listOf(4883), area.regionIds)
        assertEquals(CERBERUS_ENTRY_LOCAL_X, area.enterCoord.localX)
        assertEquals(CERBERUS_ENTRY_LOCAL_Z, area.enterCoord.localZ)
        assertEquals(CERBERUS_PUBLIC_EXIT, area.exitCoord)
        assertEquals(CoordGrid(1240, 1226, 0), sourceEntry())
        assertEquals(CoordGrid(1238, 1250, 0), sourceBoss())
    }

    @Test
    fun `entry is safely south of Cerberus`() {
        val distance =
            CerberusRules.chebyshevDistance(
                CERBERUS_ENTRY_LOCAL_X,
                CERBERUS_ENTRY_LOCAL_Z,
                CERBERUS_BOSS_LOCAL_X,
                CERBERUS_BOSS_LOCAL_Z,
            )
        assertEquals(24, distance)
        assertTrue(distance > CerberusRules.ATTACK_RANGE)
    }

    @Test
    fun `core combat cadence and special priorities are deterministic`() {
        assertEquals(600, CerberusRules.HITPOINTS)
        assertEquals(6, CerberusRules.ATTACK_RATE_TICKS)
        assertEquals(23, CerberusRules.BASIC_MAX_HIT)
        assertTrue(CerberusRules.isTripleTurn(1))
        assertTrue(CerberusRules.isTripleTurn(11))
        assertFalse(CerberusRules.isTripleTurn(0))
        assertFalse(CerberusRules.isTripleTurn(9))
        assertFalse(CerberusRules.isLavaTurn(5, 200))
        assertTrue(CerberusRules.isLavaTurn(5, 199))
        assertTrue(CerberusRules.isSoulTurn(7, 399))
        assertFalse(CerberusRules.isSoulTurn(7, 400))
        assertEquals(2, CerberusRules.TRIPLE_COMPONENT_GAP_TICKS)
    }

    @Test
    fun `triple order is magic ranged melee and ordinary distance excludes melee`() {
        assertEquals(
            listOf(
                CerberusAttackStyle.MAGIC,
                CerberusAttackStyle.RANGED,
                CerberusAttackStyle.MELEE,
            ),
            CerberusRules.TRIPLE_SEQUENCE,
        )
        assertEquals(
            listOf(CerberusAttackStyle.MAGIC, CerberusAttackStyle.RANGED),
            CerberusRules.ordinaryStyles(inMeleeRange = false),
        )
        assertEquals(
            setOf(CerberusAttackStyle.MAGIC, CerberusAttackStyle.RANGED, CerberusAttackStyle.MELEE),
            CerberusRules.ordinaryStyles(inMeleeRange = true).toSet(),
        )
    }

    @Test
    fun `attack schedule is one based and priority is triple souls then lava`() {
        val expected =
            mapOf(
                1 to CerberusAttack.TRIPLE,
                5 to CerberusAttack.LAVA,
                7 to CerberusAttack.SOULS,
                10 to CerberusAttack.LAVA,
                11 to CerberusAttack.TRIPLE,
                14 to CerberusAttack.SOULS,
                15 to CerberusAttack.LAVA,
                20 to CerberusAttack.LAVA,
                21 to CerberusAttack.TRIPLE,
                25 to CerberusAttack.LAVA,
                28 to CerberusAttack.SOULS,
                30 to CerberusAttack.LAVA,
                31 to CerberusAttack.TRIPLE,
                35 to CerberusAttack.SOULS,
            )
        for (turn in 1..35) {
            assertEquals(
                expected[turn] ?: CerberusAttack.BASIC,
                CerberusRules.chooseAttack(turn, 150, false),
                "turn $turn",
            )
        }
    }

    @Test
    fun `skip roll affects eligible souls and lava but never the triple attack`() {
        assertEquals(CerberusAttack.TRIPLE, CerberusRules.chooseAttack(1, 150, true))
        assertEquals(CerberusAttack.BASIC, CerberusRules.chooseAttack(7, 150, true))
        assertEquals(CerberusAttack.BASIC, CerberusRules.chooseAttack(5, 150, true))
        assertEquals(CerberusAttack.BASIC, CerberusRules.chooseAttack(2, 150, true))
    }

    @Test
    fun `special skip rolls are isolated to eligible souls and lava`() {
        assertFalse(CerberusRules.shouldSkipSpecial(null, roll = 0))
        assertFalse(CerberusRules.shouldSkipSpecial(CerberusAttack.TRIPLE, roll = 0))
        assertTrue(CerberusRules.shouldSkipSpecial(CerberusAttack.SOULS, roll = 10))
        assertTrue(CerberusRules.shouldSkipSpecial(CerberusAttack.LAVA, roll = 20))
        assertFalse(CerberusRules.shouldSkipSpecial(CerberusAttack.SOULS, roll = 11))
        assertTrue(
            CerberusRules.shouldSkipSpecial(CerberusAttack.SOULS, roll = 11, forcedSkip = true)
        )
        assertFalse(
            CerberusRules.shouldSkipSpecial(CerberusAttack.LAVA, roll = 20, forcedSkip = false)
        )
    }

    @Test
    fun `souls use OSRS prayer drains and elysian damage`() {
        assertEquals(30, CerberusRules.soulPrayerDrain(hasSpectral = false, hasWard = false))
        assertEquals(15, CerberusRules.soulPrayerDrain(hasSpectral = true, hasWard = false))
        assertEquals(20, CerberusRules.soulPrayerDrain(hasSpectral = false, hasWard = true))
        assertEquals(10, CerberusRules.soulPrayerDrain(hasSpectral = true, hasWard = true))
        assertEquals(30, CerberusRules.soulUnprotectedDamage(hasElysian = false))
        assertEquals(22, CerberusRules.soulUnprotectedDamage(hasElysian = true))
    }

    @Test
    fun `all six soul lane permutations are reachable without duplicate styles`() {
        val permutations = buildSet {
            for (first in 0..2) {
                for (second in 0..1) add(CerberusRules.soulOrder(first, second))
            }
        }
        assertEquals(6, permutations.size)
        permutations.forEach {
            assertEquals(
                setOf(
                    CerberusAttackStyle.RANGED,
                    CerberusAttackStyle.MAGIC,
                    CerberusAttackStyle.MELEE,
                ),
                it.toSet(),
            )
        }
    }

    @Test
    fun `lava protection matrix matches documented maximum hits`() {
        assertEquals(15, CerberusRules.lavaMaximumDamage(false, false, false, false))
        assertEquals(15, CerberusRules.lavaMaximumDamage(false, true, false, false))
        assertEquals(15, CerberusRules.lavaMaximumDamage(false, false, true, false))
        assertEquals(12, CerberusRules.lavaMaximumDamage(false, true, true, false))
        assertEquals(11, CerberusRules.lavaMaximumDamage(false, false, false, true))
        assertEquals(12, CerberusRules.lavaMaximumDamage(true, false, false, false))
        assertEquals(12, CerberusRules.lavaMaximumDamage(true, true, true, false))
        assertEquals(11, CerberusRules.lavaMaximumDamage(true, true, false, true))
    }

    @Test
    fun `melee range is measured from the five by five footprint`() {
        assertEquals(0, CerberusRules.distanceToFootprint(10, 20, 5, 12, 22))
        assertEquals(1, CerberusRules.distanceToFootprint(10, 20, 5, 9, 22))
        assertEquals(1, CerberusRules.distanceToFootprint(10, 20, 5, 15, 25))
        assertEquals(3, CerberusRules.distanceToFootprint(10, 20, 5, 17, 22))
    }

    @Test
    fun `chamber activation excludes the entry corridor`() {
        assertFalse(CerberusRules.inChamber(CERBERUS_ENTRY_LOCAL_X, CERBERUS_ENTRY_LOCAL_Z))
        assertTrue(CerberusRules.inChamber(CERBERUS_BOSS_LOCAL_X, CERBERUS_BOSS_LOCAL_Z))
        assertTrue(CerberusRules.inChamber(CERBERUS_CHAMBER_MIN_X, CERBERUS_CHAMBER_MIN_Z))
        assertFalse(CerberusRules.inChamber(24, 26))
        assertTrue(CerberusRules.inChamber(24, 27))
        assertFalse(CerberusRules.inChamber(CERBERUS_CHAMBER_MIN_X - 1, CERBERUS_CHAMBER_MIN_Z))
    }

    @Test
    fun `fire wall crossing only triggers through the three tile opening`() {
        assertTrue(CerberusRules.crossedFireWall(24, 27, 24, 26))
        assertTrue(CerberusRules.crossedFireWall(24, 26, 24, 27))
        assertFalse(CerberusRules.crossedFireWall(22, 27, 22, 26))
        assertFalse(CerberusRules.crossedFireWall(24, 29, 24, 28))
        assertEquals(23, CERBERUS_FIRE_WALL_MIN_X)
        assertEquals(25, CERBERUS_FIRE_WALL_MAX_X)
        assertEquals(26, CERBERUS_FIRE_WALL_Z)
    }

    @Test
    fun `lava tiles stay inside the room and outside Cerberus footprint`() {
        assertTrue(CerberusRules.isLavaTileLegal(10, 27))
        assertFalse(CerberusRules.isLavaTileLegal(22, 34))
        assertFalse(CerberusRules.isLavaTileLegal(26, 38))
        assertTrue(CerberusRules.isLavaTileLegal(27, 38))
        assertFalse(CerberusRules.isLavaTileLegal(-1, 27))
        assertFalse(CerberusRules.isLavaTileLegal(10, 26))
    }

    @Test
    fun `projectiles launch from the centre of the five by five boss`() {
        assertEquals(24 to 36, CerberusRules.bossProjectileSource(22, 34, 5))
        assertEquals(10 to 20, CerberusRules.bossProjectileSource(10, 20, 1))
    }

    @Test
    fun `projectiles launch from the selected Cerberus head`() {
        assertEquals(
            25 to 35,
            CerberusRules.bossProjectileSource(22, 34, 5, 24, 20, CerberusHead.LEFT),
        )
        assertEquals(
            24 to 34,
            CerberusRules.bossProjectileSource(22, 34, 5, 24, 20, CerberusHead.CENTRE),
        )
        assertEquals(
            23 to 35,
            CerberusRules.bossProjectileSource(22, 34, 5, 24, 20, CerberusHead.RIGHT),
        )
    }

    @Test
    fun `defend animation cannot replace an active attack animation`() {
        assertFalse(
            CerberusRules.canPlayDefendAnimation(
                CerberusPhase.ACTIVE,
                currentCycle = 102,
                attackAnimationUntilCycle = 106,
                hitpoints = 500,
            )
        )
        assertTrue(
            CerberusRules.canPlayDefendAnimation(
                CerberusPhase.ACTIVE,
                currentCycle = 106,
                attackAnimationUntilCycle = 106,
                hitpoints = 500,
            )
        )
        assertFalse(
            CerberusRules.canPlayDefendAnimation(
                CerberusPhase.RESETTING,
                currentCycle = 106,
                attackAnimationUntilCycle = 0,
                hitpoints = 500,
            )
        )
    }

    @Test
    fun `transitions soul lanes and respawn use tick constants`() {
        assertEquals(4, CerberusRules.STAND_TRANSITION_TICKS)
        assertEquals(4, CerberusRules.RESET_TRANSITION_TICKS)
        assertEquals(15, CerberusRules.RESPAWN_TICKS)
        assertEquals(24, CerberusRules.RESET_WALK_TIMEOUT_TICKS)
        assertEquals(49, CerberusRules.SOUL_SPAWN_LOCAL_Z)
        assertEquals(40, CerberusRules.SOUL_ATTACK_LOCAL_Z)
        assertEquals(9, CerberusRules.SOUL_WALK_DISTANCE)
        assertEquals(1, CerberusRules.SOUL_APPROACH_DELAY_TICKS)
        assertEquals(2, CerberusRules.SOUL_FIRST_ATTACK_DELAY_TICKS)
        assertEquals(2, CerberusRules.SOUL_COMPONENT_GAP_TICKS)
        assertEquals(14, CerberusRules.LAVA_ACTIVE_TICKS)
    }

    @Test
    fun `triple spray and lava use their distinct revision 240 animations`() {
        assertEquals(4493, CerberusAssets.TRIPLE_SPRAY_ANIMATION)
        assertEquals(4501, CerberusAssets.LAVA_ANIMATION)
        assertTrue(CerberusAssets.TRIPLE_SPRAY_ANIMATION != CerberusAssets.LAVA_ANIMATION)
    }

    @Test
    fun `normal magic and ranged attacks retain visible travel profiles`() {
        assertEquals(1241, CerberusAssets.MAGIC_CAST_SPOTANIM)
        assertEquals(1242, CerberusAssets.MAGIC_PROJECTILE_SPOTANIM)
        assertEquals(1243, CerberusAssets.MAGIC_IMPACT_SPOTANIM)
        assertEquals(1244, CerberusAssets.RANGED_IMPACT_SPOTANIM)
        assertEquals(1245, CerberusAssets.RANGED_PROJECTILE_SPOTANIM)
        listOf(CerberusAssets.MAGIC_PROJECTILE, CerberusAssets.RANGED_PROJECTILE).forEach {
            assertEquals(70, it.startHeight)
            assertEquals(20, it.endHeight)
            assertEquals(30, it.delay)
            assertEquals(11, it.angle)
            assertEquals(45, it.lengthAdjustment)
            assertEquals(64, it.progress)
            assertEquals(15, it.stepMultiplier)
        }
    }

    @Test
    fun `lava activation follows the emitted projectile travel time`() {
        assertEquals(104, CerberusRules.projectileImpactCycle(100, 4))
        assertEquals(101, CerberusRules.projectileImpactCycle(100, 0))
    }

    @Test
    fun `specials apply their full recovery before the next attack`() {
        assertEquals(6, CerberusRules.recoveryTicks(CerberusAttack.BASIC))
        assertEquals(10, CerberusRules.recoveryTicks(CerberusAttack.TRIPLE))
        assertEquals(10, CerberusRules.recoveryTicks(CerberusAttack.SOULS))
        assertEquals(8, CerberusRules.recoveryTicks(CerberusAttack.LAVA))
    }

    @Test
    fun `revision 240 soul attacks and projectiles are style specific`() {
        assertEquals(8528, CerberusAssets.MELEE_SOUL_ATTACK_ANIMATION)
        assertEquals(8529, CerberusAssets.MAGIC_SOUL_ATTACK_ANIMATION)
        assertEquals(8530, CerberusAssets.RANGED_SOUL_ATTACK_ANIMATION)
        assertEquals(1248, CerberusAssets.MELEE_SOUL_PROJECTILE_SPOTANIM)
        assertEquals(100, CerberusAssets.MAGIC_SOUL_PROJECTILE_SPOTANIM)
        assertEquals(34, CerberusAssets.RANGED_SOUL_PROJECTILE_SPOTANIM)
    }

    @Test
    fun `only real bone item names trigger the Cerby snack response`() {
        assertTrue(CerberusRules.isBoneItem("obj.bones"))
        assertTrue(CerberusRules.isBoneItem("obj.dragon_bones"))
        assertTrue(CerberusRules.isBoneItem("obj.burnt_bones"))
        assertFalse(CerberusRules.isBoneItem("obj.bonecrusher"))
        assertFalse(CerberusRules.isBoneItem("obj.bone_shards"))
    }

    @Test
    fun `all six Cerberus combat achievements derive from actual kill evidence`() {
        assertEquals(
            CerberusTask.entries.toSet(),
            CerberusRules.completedTasks(
                kills = 150,
                soulWaveSpawned = false,
                negatedSoulAttacks = 6,
                tookLavaDamage = false,
                tookMeleeDamage = false,
            ),
        )
    }

    @Test
    fun `combat achievements fail independently when their evidence is absent`() {
        assertEquals(
            setOf(CerberusTask.VETERAN),
            CerberusRules.completedTasks(
                kills = 75,
                soulWaveSpawned = true,
                negatedSoulAttacks = 5,
                tookLavaDamage = true,
                tookMeleeDamage = true,
            ),
        )
        assertTrue(
            CerberusRules.completedTasks(
                    kills = 74,
                    soulWaveSpawned = true,
                    negatedSoulAttacks = 0,
                    tookLavaDamage = true,
                    tookMeleeDamage = true,
                )
                .isEmpty()
        )
    }

    @Test
    fun `membership cleanup detects teleport out and stale sessions`() {
        assertFalse(CerberusRules.shouldDiscardRun(42L, 42L))
        assertTrue(CerberusRules.shouldDiscardRun(42L, 43L))
        assertTrue(CerberusRules.shouldDiscardRun(42L, null))
    }

    private fun sourceEntry() =
        CoordGrid(
            (CERBERUS_SOURCE_MAP_X shl 6) + CERBERUS_ENTRY_LOCAL_X,
            (CERBERUS_SOURCE_MAP_Z shl 6) + CERBERUS_ENTRY_LOCAL_Z,
            0,
        )

    private fun sourceBoss() =
        CoordGrid(
            (CERBERUS_SOURCE_MAP_X shl 6) + CERBERUS_BOSS_LOCAL_X,
            (CERBERUS_SOURCE_MAP_Z shl 6) + CERBERUS_BOSS_LOCAL_Z,
            0,
        )
}
