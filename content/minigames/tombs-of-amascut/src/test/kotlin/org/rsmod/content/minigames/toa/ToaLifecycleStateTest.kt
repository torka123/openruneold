package org.rsmod.content.minigames.toa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.rsmod.game.entity.player.PlayerUid

class ToaLifecycleStateTest {
    @Test
    fun `roster rebinds transient uid without changing account identity or order`() {
        val first = ToaParticipant(PlayerUid(1), accountHash = 101L)
        val second = ToaParticipant(PlayerUid(2), accountHash = 202L)
        val roster = ToaRaidRoster()
        roster.add(first)
        roster.add(second)

        val oldUid = roster.rebind(accountHash = 101L, newUid = PlayerUid(9))

        assertEquals(PlayerUid(1), oldUid)
        assertSame(first, roster.byAccountHash(101L))
        assertEquals(PlayerUid(9), first.uid)
        assertEquals(listOf(101L, 202L), roster.values.map(ToaParticipant::accountHash))
    }

    @Test
    fun `roster rejects a uid already bound to a different account`() {
        val roster = ToaRaidRoster()
        roster.add(ToaParticipant(PlayerUid(1), accountHash = 101L))
        roster.add(ToaParticipant(PlayerUid(2), accountHash = 202L))

        assertFailsWith<IllegalArgumentException> {
            roster.rebind(accountHash = 101L, newUid = PlayerUid(2))
        }
        assertEquals(PlayerUid(1), roster.byAccountHash(101L)?.uid)
    }

    @Test
    fun `raid advances only when an eligible connected participant exists`() {
        val disconnected = ToaParticipant(PlayerUid(1), accountHash = 101L, connected = false)
        val retired =
            ToaParticipant(
                PlayerUid(2),
                accountHash = 202L,
                connected = true,
                rewardEligible = false,
            )

        assertFalse(ToaRaidPauseRules.shouldAdvance(listOf(disconnected, retired)))
        disconnected.connected = true
        assertTrue(ToaRaidPauseRules.shouldAdvance(listOf(disconnected, retired)))
    }

    @Test
    fun `completion credit is granted once and never to a retired participant`() {
        val eligible = ToaParticipant(PlayerUid(1), accountHash = 101L)
        val retired = ToaParticipant(PlayerUid(2), accountHash = 202L, rewardEligible = false)

        assertTrue(ToaCompletionCredit.claim(eligible))
        assertFalse(ToaCompletionCredit.claim(eligible))
        assertFalse(ToaCompletionCredit.claim(retired))
        assertTrue(eligible.completionRecorded)
        assertFalse(retired.completionRecorded)
    }

    @Test
    fun `developer start validates expected party size and level zero`() {
        assertTrue(ToaDevStartRules.validExpectedPartySize(1))
        assertTrue(ToaDevStartRules.validExpectedPartySize(8))
        assertFalse(ToaDevStartRules.validExpectedPartySize(0))
        assertFalse(ToaDevStartRules.validExpectedPartySize(9))
        assertTrue(ToaDevStartRules.matchesPartySize(4, 4))
        assertFalse(ToaDevStartRules.matchesPartySize(4, 3))
        assertTrue(ToaDevStartRules.isLevelZero(0))
        assertFalse(ToaDevStartRules.isLevelZero(1))
    }

    @Test
    fun `developer seed override is account stable and consume once`() {
        val overrides = ToaDevSeedOverrides()
        overrides.arm(accountHash = 101L, seed = 987654321L)

        assertEquals(987654321L, overrides.peek(101L))
        assertEquals(987654321L, overrides.consume(101L))
        assertNull(overrides.peek(101L))
        assertNull(overrides.consume(101L))
    }

    @Test
    fun `developer routes distinguish every challenge from its guardian`() {
        assertEquals(
            ToaDevRoomRoute.Challenge(ToaPath.SCABARAS),
            ToaDevRoomRoutes.parse("scabaras"),
        )
        assertEquals(ToaDevRoomRoute.Boss(ToaPath.SCABARAS), ToaDevRoomRoutes.parse("kephri"))
        assertEquals(ToaDevRoomRoute.Challenge(ToaPath.APMEKEN), ToaDevRoomRoutes.parse("apmeken"))
        assertEquals(ToaDevRoomRoute.Boss(ToaPath.APMEKEN), ToaDevRoomRoutes.parse("baba"))
        assertEquals(ToaDevRoomRoute.Challenge(ToaPath.CRONDIS), ToaDevRoomRoutes.parse("crondis"))
        assertEquals(ToaDevRoomRoute.Boss(ToaPath.CRONDIS), ToaDevRoomRoutes.parse("zebak"))
        assertEquals(ToaDevRoomRoute.Challenge(ToaPath.HET), ToaDevRoomRoutes.parse("het"))
        assertEquals(ToaDevRoomRoute.Boss(ToaPath.HET), ToaDevRoomRoutes.parse("akkha"))
        assertEquals(ToaDevRoomRoute.Wardens, ToaDevRoomRoutes.parse("wardens"))
        assertEquals(ToaDevRoomRoute.Tumeken, ToaDevRoomRoutes.parse("tumeken"))
        assertEquals(ToaDevRoomRoute.Reward, ToaDevRoomRoutes.parse("reward"))
        assertNull(ToaDevRoomRoutes.parse("unknown"))
    }

    @Test
    fun `developer status exposes locked seed size level paths and room`() {
        val status =
            ToaDevStatusSnapshot(
                    seed = 44L,
                    lockedPartySize = 3,
                    raidLevel = 0,
                    pathLevels = TOA_ACTIVE_PATHS.associateWith { 0 },
                    room = ToaRoom.HET,
                    completedPaths = setOf(ToaPath.SCABARAS),
                    wardenStage = ToaWardenStage.OBELISK,
                )
                .render()

        assertTrue("seed=44" in status)
        assertTrue("party=3" in status)
        assertTrue("level=0" in status)
        assertTrue("scabaras=0" in status)
        assertTrue("room=het" in status)
        assertTrue("completed=[scabaras]" in status)
        assertTrue("wardens=obelisk" in status)
    }
}
