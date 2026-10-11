package org.rsmod.content.bosses.yama

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YamaJudgeRulesTest {
    @Test
    fun `Judge melee routing uses one tile range around the full occupied rectangle`() {
        assertEquals(1, YamaJudgeRules.MELEE_AP_RANGE)
    }

    @Test
    fun `blocked Judge styles display zero and do not consume shared health`() {
        val blocked = YamaJudgeRules.initialBlockedStyles()
        for (style in listOf(YamaAttackStyle.MELEE, YamaAttackStyle.RANGED)) {
            val result =
                YamaJudgeRules.resolvePlayerHit(
                    style = style,
                    rolledDamage = 37,
                    currentMaxHit = 42,
                    blockedStyles = blocked,
                    alreadyAcceptedThisTick = false,
                    sharedHitpoints = 400,
                )
            assertEquals(0, result.damage)
            assertFalse(result.accepted)
            assertEquals(400, YamaJudgeRules.remainingHitpoints(400, result.damage))
            assertEquals(blocked, result.nextBlockedStyles)
        }
    }

    @Test
    fun `accepted hit displays forced damage and changes shared health by exactly that value`() {
        val result =
            YamaJudgeRules.resolvePlayerHit(
                style = YamaAttackStyle.MAGIC,
                rolledDamage = 7,
                currentMaxHit = 42,
                blockedStyles = YamaJudgeRules.initialBlockedStyles(),
                alreadyAcceptedThisTick = false,
                sharedHitpoints = 400,
            )
        assertTrue(result.accepted)
        assertEquals(42, result.damage)
        assertEquals(358, YamaJudgeRules.remainingHitpoints(400, result.damage))
        assertEquals(setOf(YamaAttackStyle.RANGED, YamaAttackStyle.MAGIC), result.nextBlockedStyles)
    }

    @Test
    fun `special damage above current maximum stays visible and same tick followups are zero`() {
        val first =
            YamaJudgeRules.resolvePlayerHit(
                style = YamaAttackStyle.MAGIC,
                rolledDamage = 60,
                currentMaxHit = 42,
                blockedStyles = YamaJudgeRules.initialBlockedStyles(),
                alreadyAcceptedThisTick = false,
                sharedHitpoints = 400,
            )
        val followup =
            YamaJudgeRules.resolvePlayerHit(
                style = YamaAttackStyle.MAGIC,
                rolledDamage = 60,
                currentMaxHit = 42,
                blockedStyles = YamaJudgeRules.initialBlockedStyles(),
                alreadyAcceptedThisTick = true,
                sharedHitpoints = 400,
            )
        assertEquals(60, first.damage)
        assertTrue(first.accepted)
        assertEquals(0, followup.damage)
        assertFalse(followup.accepted)
    }

    @Test
    fun `duo participants each retain an independent same tick allowance`() {
        val west =
            YamaJudgeRules.resolvePlayerHit(
                YamaAttackStyle.MAGIC,
                1,
                42,
                YamaJudgeRules.initialBlockedStyles(),
                alreadyAcceptedThisTick = false,
                sharedHitpoints = 400,
            )
        val afterWest = YamaJudgeRules.remainingHitpoints(400, west.damage)
        val east =
            YamaJudgeRules.resolvePlayerHit(
                YamaAttackStyle.MAGIC,
                1,
                42,
                YamaJudgeRules.initialBlockedStyles(),
                alreadyAcceptedThisTick = false,
                sharedHitpoints = afterWest,
            )
        assertEquals(42, west.damage)
        assertEquals(42, east.damage)
        assertEquals(316, YamaJudgeRules.remainingHitpoints(afterWest, east.damage))
    }

    @Test
    fun `delayed hit on a later tick remains eligible`() {
        val delayed =
            YamaJudgeRules.resolvePlayerHit(
                YamaAttackStyle.MAGIC,
                0,
                42,
                YamaJudgeRules.initialBlockedStyles(),
                alreadyAcceptedThisTick = false,
                sharedHitpoints = 20,
            )
        assertTrue(delayed.accepted)
        assertEquals(20, delayed.damage)
        assertEquals(0, YamaJudgeRules.remainingHitpoints(20, delayed.damage))
    }

    @Test
    fun `full occupied Judge rectangle is used from every edge and diagonal`() {
        val southWestX = 6
        val southWestZ = 50
        val size = YamaJudgeRules.JUDGE_SIZE
        val adjacent =
            listOf(
                5 to 50,
                5 to 51,
                5 to 52,
                6 to 49,
                7 to 49,
                8 to 49,
                9 to 50,
                9 to 51,
                9 to 52,
                6 to 53,
                7 to 53,
                8 to 53,
                5 to 49,
                9 to 49,
                5 to 53,
                9 to 53,
            )
        adjacent.forEach { (x, z) ->
            assertEquals(
                1,
                YamaJudgeRules.distanceToOccupiedRectangle(southWestX, southWestZ, size, x, z),
            )
        }
        assertTrue(YamaJudgeRules.occupiesTile(southWestX, southWestZ, size, 8, 52))
        assertEquals(
            0,
            YamaJudgeRules.distanceToOccupiedRectangle(southWestX, southWestZ, size, 7, 51),
        )
        assertEquals(
            3,
            YamaJudgeRules.distanceToOccupiedRectangle(southWestX, southWestZ, size, 11, 55),
        )
    }

    @Test
    fun `east Judge southwest anchor mirrors the complete cache-sized footprint`() {
        val westSouthWest = 6
        val eastSouthWest = YamaJudgeRules.mirrorSouthWestX(westSouthWest)
        assertEquals(50, eastSouthWest)
        val westTiles = (westSouthWest until westSouthWest + YamaJudgeRules.JUDGE_SIZE).toList()
        val mirroredTiles = westTiles.map(YamaJudgeRules::mirrorLocalX).sorted()
        assertEquals(
            (eastSouthWest until eastSouthWest + YamaJudgeRules.JUDGE_SIZE).toList(),
            mirroredTiles,
        )
    }

    @Test
    fun `east Judge coordinates exactly mirror west coordinates`() {
        val west = listOf(5, 6, 7, 8, 9)
        assertEquals(listOf(53, 52, 51, 50, 49), west.map(YamaJudgeRules::mirrorLocalX))
        west.forEach { x ->
            assertEquals(x, YamaJudgeRules.mirrorLocalX(YamaJudgeRules.mirrorLocalX(x)))
        }
    }
}
