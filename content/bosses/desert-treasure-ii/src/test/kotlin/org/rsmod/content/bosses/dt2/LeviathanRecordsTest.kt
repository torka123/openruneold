package org.rsmod.content.bosses.dt2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeviathanRecordsTest {
    @Test
    fun `codec preserves account records and stable task ids`() {
        val state =
            LeviathanRecordState(
                deaths = 7,
                normalPersonalBestTicks = 111,
                awakenedPersonalBestTicks = 222,
                lastKillTicks = 333,
                awakenedCompleted = true,
                perfectKillsThisVisit = 4,
                completedTasks =
                    setOf(LeviathanCombatTask.ADEPT, LeviathanCombatTask.UNCONVENTIONAL),
                recentKillEventIds = listOf("run:1", "run|2"),
                recentDeathEventIds = listOf("death/1"),
            )

        assertEquals(state, LeviathanRecordCodec.decode(LeviathanRecordCodec.encode(state)))
        assertEquals("dt2_leviathan_records_v1", LeviathanRecordAttributes.STATE.persistenceKey)
        assertFailsWith<IllegalArgumentException> { LeviathanRecordCodec.decode("bad") }
    }

    @Test
    fun `admin debug kills and deaths never mutate persistent records`() {
        val state = LeviathanRecordState(perfectKillsThisVisit = 3)

        val kill = LeviathanRecords.recordKill(state, evidence(rewardEligible = false))
        val death = LeviathanRecords.recordDeath(state, "debug:death", rewardEligible = false)

        assertFalse(kill.changed)
        assertFalse(death.changed)
        assertEquals(state, kill.state)
        assertEquals(state, death.state)
    }

    @Test
    fun `kill and death event ids make mutations idempotent`() {
        val first = LeviathanRecords.recordKill(LeviathanRecordState(), evidence()).state
        val duplicate = LeviathanRecords.recordKill(first, evidence())
        val death = LeviathanRecords.recordDeath(first, "death:1", rewardEligible = true).state
        val duplicateDeath = LeviathanRecords.recordDeath(death, "death:1", rewardEligible = true)

        assertFalse(duplicate.changed)
        assertEquals(first, duplicate.state)
        assertFalse(duplicateDeath.changed)
        assertEquals(1, duplicateDeath.state.deaths)
    }

    @Test
    fun `personal bests and awakened completion remain separate by mode`() {
        val normal =
            LeviathanRecords.recordKill(
                    LeviathanRecordState(),
                    evidence(eventId = "normal:1", elapsedTicks = 150),
                )
                .state
        val slowerNormal =
            LeviathanRecords.recordKill(
                    normal,
                    evidence(eventId = "normal:2", elapsedTicks = 170, killCountAfterKill = 2),
                )
                .state
        val awakened =
            LeviathanRecords.recordKill(
                    slowerNormal,
                    evidence(
                        eventId = "awakened:1",
                        elapsedTicks = 220,
                        killCountAfterKill = 3,
                        awakened = true,
                    ),
                )
                .state

        assertEquals(150, awakened.normalPersonalBestTicks)
        assertEquals(220, awakened.awakenedPersonalBestTicks)
        assertEquals(220, awakened.lastKillTicks)
        assertTrue(awakened.awakenedCompleted)
        assertTrue(LeviathanCombatTask.SLEEPER in awakened.completedTasks)
    }

    @Test
    fun `all nine combat tasks complete only from their exact evidence`() {
        val fifthPerfect =
            LeviathanRecords.recordKill(
                current =
                    LeviathanRecordState(
                        perfectKillsThisVisit = 4,
                        completedTasks =
                            setOf(
                                LeviathanCombatTask.ADEPT,
                                LeviathanCombatTask.SPEED_TRIALIST,
                                LeviathanCombatTask.SPEED_CHASER,
                            ),
                    ),
                evidence =
                    evidence(
                        eventId = "master:50",
                        elapsedTicks = 116,
                        killCountAfterKill = 50,
                        awakened = true,
                        stunCount = 1,
                        perfectKill = true,
                        onlyMithrilAmmunition = true,
                        neverAbove25Hitpoints = true,
                    ),
            )

        assertEquals(LeviathanCombatTask.entries.toSet(), fifthPerfect.state.completedTasks)
        assertEquals(
            setOf(
                LeviathanCombatTask.SERPENTINE_SOLO,
                LeviathanCombatTask.MASTER,
                LeviathanCombatTask.PERFECT_LEVIATHAN,
                LeviathanCombatTask.SPEED_RUNNER,
                LeviathanCombatTask.SLEEPER,
                LeviathanCombatTask.UNCONVENTIONAL,
            ),
            fifthPerfect.newlyCompletedTasks,
        )
    }

    @Test
    fun `speed thresholds are strict at the first nonqualifying tick`() {
        assertTrue(LeviathanRecords.underSeconds(183, 110))
        assertFalse(LeviathanRecords.underSeconds(184, 110))
        assertTrue(LeviathanRecords.underSeconds(141, 85))
        assertFalse(LeviathanRecords.underSeconds(142, 85))
        assertTrue(LeviathanRecords.underSeconds(116, 70))
        assertFalse(LeviathanRecords.underSeconds(117, 70))
    }

    @Test
    fun `slayer tasks block speed tasks and imperfect kills do not reset trip progress`() {
        val state = LeviathanRecordState(perfectKillsThisVisit = 2)
        val result =
            LeviathanRecords.recordKill(
                state,
                evidence(
                    elapsedTicks = 100,
                    perfectKill = false,
                    onSlayerTask = true,
                    stunCount = 2,
                ),
            )

        assertEquals(2, result.state.perfectKillsThisVisit)
        assertFalse(LeviathanCombatTask.SPEED_TRIALIST in result.state.completedTasks)
        assertFalse(LeviathanCombatTask.SERPENTINE_SOLO in result.state.completedTasks)
    }

    @Test
    fun `leaving resets only the same-visit perfect counter`() {
        val state =
            LeviathanRecordState(
                deaths = 2,
                normalPersonalBestTicks = 100,
                perfectKillsThisVisit = 4,
                completedTasks = setOf(LeviathanCombatTask.ADEPT),
            )

        val ended = LeviathanRecords.endVisit(state)

        assertTrue(ended.changed)
        assertEquals(0, ended.state.perfectKillsThisVisit)
        assertEquals(2, ended.state.deaths)
        assertEquals(100, ended.state.normalPersonalBestTicks)
        assertEquals(setOf(LeviathanCombatTask.ADEPT), ended.state.completedTasks)
    }

    private fun evidence(
        eventId: String = "kill:1",
        rewardEligible: Boolean = true,
        elapsedTicks: Int = 150,
        killCountAfterKill: Int = 1,
        awakened: Boolean = false,
        stunCount: Int = 0,
        perfectKill: Boolean = true,
        onSlayerTask: Boolean = false,
        onlyMithrilAmmunition: Boolean = false,
        neverAbove25Hitpoints: Boolean = false,
    ) =
        LeviathanKillEvidence(
            eventId = eventId,
            rewardEligible = rewardEligible,
            elapsedTicks = elapsedTicks,
            killCountAfterKill = killCountAfterKill,
            awakened = awakened,
            stunCount = stunCount,
            perfectKill = perfectKill,
            onSlayerTask = onSlayerTask,
            onlyMithrilAmmunition = onlyMithrilAmmunition,
            neverAbove25Hitpoints = neverAbove25Hitpoints,
        )
}
