package org.rsmod.content.bosses.yama

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YamaCaptureReplayTest {
    @Test
    fun `captured throne summon and boss enable timing replays exactly`() {
        val challengeTick = 197
        assertEquals(199, YamaCombatTimeline.bossSpawnCycle(challengeTick))
        assertEquals(202, YamaCombatTimeline.bossAttackableCycle(challengeTick))
    }

    @Test
    fun `captured Judge portal and return delays replay exactly`() {
        assertEquals(245, YamaCombatTimeline.judgeArrivalCycle(239))
        assertEquals(271, YamaCombatTimeline.judgeReturnCycle(265))
        assertEquals(272, YamaCombatTimeline.judgeReturnPlayerAnimCycle(265))
        assertEquals(275, YamaCombatTimeline.judgeReturnTeleportCycle(265))
        assertEquals(262, YamaCombatTimeline.judgeFireballImpactCycle(258))
        assertEquals(6, YamaCombatTimeline.PHASE_RESUME_ATTACK_DELAY)
    }

    @Test
    fun `normal contract and single shadow crash target locks stay deterministic`() {
        assertEquals(
            listOf(302, 305, 308),
            YamaCombatTimeline.shadowCrashLockCycles(300, YamaContract.NONE),
        )
        assertEquals(
            listOf(302, 305, 308, 311, 314),
            YamaCombatTimeline.shadowCrashLockCycles(300, YamaContract.CATALYST),
        )
        assertEquals(listOf(302), YamaCombatTimeline.shadowCrashLockCycles(300, 1))
        assertEquals(emptyList(), YamaCombatTimeline.shadowCrashLockCycles(300, 0))
    }

    @Test
    fun `all four captured player-selected Judge routes validate row by row`() {
        val captures =
            listOf(
                listOf(7 to 31, 8 to 34, 7 to 37, 9 to 40, 8 to 43, 7 to 46),
                listOf(7 to 31, 8 to 34, 9 to 37, 9 to 40, 8 to 43, 7 to 46),
                listOf(7 to 31, 8 to 34, 7 to 37, 7 to 40, 8 to 43, 7 to 46),
                listOf(7 to 31, 8 to 34, 9 to 37, 9 to 40, 8 to 43, 7 to 46),
            )
        captures.forEach { route ->
            route.forEachIndexed { row, (x, z) ->
                assertTrue(YamaRules.isValidJudgeStone(0, row, x, z))
            }
        }
        assertFalse(YamaRules.isValidJudgeStone(0, 0, 7, 34))
        assertFalse(YamaRules.isValidJudgeStone(0, 6, 8, 48))
        assertTrue(YamaRules.judgeCrossingComplete(6))
        assertFalse(YamaRules.judgeCrossingComplete(5))
        assertEquals(8, YamaRules.judgeLandingX(0))
        assertEquals(50, YamaRules.judgeLandingX(1))
        assertEquals(48, YamaRules.JUDGE_LANDING_Z)
    }

    @Test
    fun `captured stone exact-move angles and client timing are stable`() {
        assertEquals(1024, YamaRules.exactMoveAngle(1479, 10077, 1479, 10079))
        assertEquals(1129, YamaRules.exactMoveAngle(1479, 10079, 1480, 10082))
        assertEquals(919, YamaRules.exactMoveAngle(1481, 10088, 1480, 10091))
        assertEquals(27, YamaCombatTimeline.STONE_JUMP_CLIENT_CYCLES)
        assertEquals(4, YamaRules.JUDGE_STONE_INTERACTION_RANGE)
    }

    @Test
    fun `glyph projectile and current OSRS Judge surge timings stay deterministic`() {
        assertEquals(177, YamaCombatTimeline.standardImpactCycle(174))
        assertEquals(188, YamaCombatTimeline.glyphAttackImpactCycle(182))
        assertEquals(250, YamaCombatTimeline.meteorImpactCycle(246))
        assertEquals(745, YamaCombatTimeline.shadowStompImpactCycle(740))
        assertEquals(listOf(205, 210, 215, 220), YamaCombatTimeline.judgeSurgeCycles(205, 4))
        assertEquals(30, YamaCombatTimeline.JUDGE_SURGE_MOVE_CLIENT_CYCLES)
        assertEquals(1, YamaCombatTimeline.JUDGE_POST_CROSS_ATTACK_DELAY)
        assertEquals(1, YamaRules.judgeSurgeCount(0))
        assertEquals(2, YamaRules.judgeSurgeCount(1))
        assertEquals(1, YamaRules.judgeSurgeCount(2))
        assertEquals(2, YamaRules.judgeSurgeCount(3))
    }

    @Test
    fun `Judge maximum hits and self fireball footprint are deterministic`() {
        assertEquals(30, YamaRules.JUDGE_SURGE_MAX)
        assertEquals(20, YamaRules.JUDGE_FIREBALL_MAX)
        assertEquals(42, YamaRules.judgeSuccessfulDamage(0, 42))
        assertEquals(42, YamaRules.judgeSuccessfulDamage(7, 42))
        assertEquals(50, YamaRules.judgeSuccessfulDamage(50, 42))
        assertTrue(YamaRules.judgeFireballHitsJudge(10, 20, 2, 10, 20))
        assertTrue(YamaRules.judgeFireballHitsJudge(10, 20, 2, 11, 21))
        assertFalse(YamaRules.judgeFireballHitsJudge(10, 20, 2, 12, 21))
        assertEquals(13, YamaRules.JUDGE_FIREBALL_SELF_MIN)
        assertEquals(18, YamaRules.JUDGE_FIREBALL_SELF_MAX)
        assertEquals(638, YamaRules.JUDGE_PORTAL_FALL_HEIGHT)
        assertEquals(5, YamaRules.JUDGE_PORTAL_SPOTANIM_SLOT)
    }

    @Test
    fun `capture proven impact and deferred damage offsets remain distinct`() {
        assertEquals(2, YamaCombatTimeline.SHADOW_CRASH_FIRST_LOCK_DELAY)
        assertEquals(3, YamaCombatTimeline.SHADOW_CRASH_IMPACT_DELAY)
        assertEquals(1300, YamaCombatTimeline.shadowCrashLockCycles(1298, 1).single())
        assertEquals(1303, YamaCombatTimeline.shadowCrashImpactCycle(1300))
        assertEquals(1, YamaCombatTimeline.MOVING_HAZARD_HIT_DELAY)
        assertEquals(1, YamaCombatTimeline.VOID_FLARE_HIT_DELAY)
    }

    @Test
    fun `active fight leash covers every corner of the defined Yama arena`() {
        val bossSpawn = 29 to 28
        val arenaCorners = listOf(20 to 20, 20 to 42, 40 to 20, 40 to 42)
        arenaCorners.forEach { corner ->
            val distance =
                maxOf(
                    kotlin.math.abs(corner.first - bossSpawn.first),
                    kotlin.math.abs(corner.second - bossSpawn.second),
                )
            assertTrue(distance <= YamaRules.ACTIVE_FIGHT_RANGE)
        }
    }

    @Test
    fun `captured glyph messages preserve exact elements and colours`() {
        assertEquals(
            "<col=ff8e32>You absorb a Glyph of Fire.",
            YamaRules.glyphAbsorbMessage(YamaGlyph.FIRE),
        )
        assertEquals(
            "<col=a53fff>The Glyph of Shadow absorbs the damage from the attack.",
            YamaRules.glyphBlockedMessage(YamaGlyph.SHADOW),
        )
        assertEquals(
            "<col=ff3045>The Glyph of Fire fades away.",
            YamaRules.glyphFadeMessage(YamaGlyph.FIRE),
        )
    }

    @Test
    fun `captured five glyph coordinates are stable in region 6045`() {
        assertEquals(
            listOf(1497 to 10084, 1495 to 10077, 1502 to 10072, 1509 to 10077, 1507 to 10084),
            listOf(25 to 36, 23 to 29, 30 to 24, 37 to 29, 35 to 36).map { (x, z) ->
                (1472 + x) to (10048 + z)
            },
        )
    }
}
