package org.rsmod.content.bosses.dt2

import java.nio.file.Files
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeviathanGlobalRecordsTest {
    @Test
    fun `duplicate events do not increment global totals twice`() {
        val evidence = evidence(eventId = "run-1:kill")
        val first =
            LeviathanGlobalRecords.recordKill(
                LeviathanGlobalRecordState(),
                accountId = 41,
                displayName = "First Player",
                evidence = evidence,
            )
        val duplicate =
            LeviathanGlobalRecords.recordKill(
                first.state,
                accountId = 41,
                displayName = "First Player",
                evidence = evidence,
            )

        assertTrue(first.changed)
        assertFalse(duplicate.changed)
        assertEquals(1, duplicate.state.totalKills)

        val death =
            LeviathanGlobalRecords.recordDeath(
                duplicate.state,
                accountId = 41,
                displayName = "First Player",
                eventId = "run-2:death",
                rewardEligible = true,
            )
        val duplicateDeath =
            LeviathanGlobalRecords.recordDeath(
                death.state,
                accountId = 41,
                displayName = "First Player",
                eventId = "run-2:death",
                rewardEligible = true,
            )
        assertFalse(duplicateDeath.changed)
        assertEquals(1, duplicateDeath.state.deaths)
    }

    @Test
    fun `normal and awakened records stay separate and ties use stable account ordering`() {
        val first =
            LeviathanGlobalRecords.recordKill(
                    LeviathanGlobalRecordState(),
                    accountId = 50,
                    displayName = "Later Account",
                    evidence = evidence(eventId = "normal-1", elapsedTicks = 100),
                )
                .state
        val tie =
            LeviathanGlobalRecords.recordKill(
                    first,
                    accountId = 12,
                    displayName = "Earlier Account",
                    evidence = evidence(eventId = "normal-2", elapsedTicks = 100),
                )
                .state
        val awakened =
            LeviathanGlobalRecords.recordKill(
                    tie,
                    accountId = 77,
                    displayName = "Awakened Player",
                    evidence = evidence(eventId = "awakened-1", elapsedTicks = 160, awakened = true),
                )
                .state

        assertEquals(2, awakened.normalKills)
        assertEquals(1, awakened.awakenedKills)
        assertEquals(3, awakened.totalKills)
        assertEquals(12, awakened.normalBest.accountId)
        assertEquals("Earlier Account", awakened.normalBest.displayName)
        assertEquals(100, awakened.normalBest.ticks)
        assertEquals(77, awakened.awakenedBest.accountId)
        assertEquals(160, awakened.awakenedBest.ticks)
    }

    @Test
    fun `record holder name refreshes on a later eligible event`() {
        val first =
            LeviathanGlobalRecords.recordKill(
                    LeviathanGlobalRecordState(),
                    accountId = 9,
                    displayName = "Old Name",
                    evidence = evidence(eventId = "kill-1"),
                )
                .state
        val renamed =
            LeviathanGlobalRecords.recordDeath(
                    first,
                    accountId = 9,
                    displayName = "Current Name",
                    eventId = "death-1",
                    rewardEligible = true,
                )
                .state

        assertEquals("Current Name", renamed.normalBest.displayName)

        val refreshed =
            LeviathanGlobalRecords.refreshDisplayName(
                renamed,
                accountId = 9,
                displayName = "Newest Name",
            )
        assertTrue(refreshed.changed)
        assertEquals("Newest Name", refreshed.state.normalBest.displayName)
    }

    @Test
    fun `ineligible administrator results never mutate global records`() {
        val state = LeviathanGlobalRecordState(normalKills = 4, deaths = 2)
        val kill =
            LeviathanGlobalRecords.recordKill(
                state,
                accountId = 1,
                displayName = "Admin",
                evidence = evidence(rewardEligible = false),
            )
        val death =
            LeviathanGlobalRecords.recordDeath(
                state,
                accountId = 1,
                displayName = "Admin",
                eventId = "debug-death",
                rewardEligible = false,
            )

        assertFalse(kill.changed)
        assertFalse(death.changed)
        assertEquals(state, kill.state)
        assertEquals(state, death.state)
    }

    @Test
    fun `atomic store survives service reconstruction and rejects malformed state`() {
        val directory = Files.createTempDirectory("leviathan-global-records")
        try {
            val path = directory.resolve("records.txt")
            val first = LeviathanGlobalRecordService(LeviathanGlobalRecordStore(path))
            first.recordKill(
                accountId = 44,
                displayName = "Persistent Player",
                evidence = evidence(eventId = "persistent-kill", elapsedTicks = 123),
            )

            val restarted = LeviathanGlobalRecordService(LeviathanGlobalRecordStore(path))
            assertEquals(1, restarted.snapshot().normalKills)
            assertEquals(123, restarted.snapshot().normalBest.ticks)
            assertFalse(Files.exists(path.resolveSibling("records.txt.tmp")))

            Files.writeString(path, "version=1\nmissing=everything\n")
            assertFailsWith<IllegalStateException> {
                LeviathanGlobalRecordService(LeviathanGlobalRecordStore(path))
            }
        } finally {
            Files.walk(directory).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private fun evidence(
        eventId: String = "kill-1",
        elapsedTicks: Int = 120,
        awakened: Boolean = false,
        rewardEligible: Boolean = true,
    ) =
        LeviathanKillEvidence(
            eventId = eventId,
            rewardEligible = rewardEligible,
            elapsedTicks = elapsedTicks,
            killCountAfterKill = 1,
            awakened = awakened,
            stunCount = 1,
            perfectKill = true,
            onSlayerTask = false,
            onlyMithrilAmmunition = false,
            neverAbove25Hitpoints = false,
        )
}
