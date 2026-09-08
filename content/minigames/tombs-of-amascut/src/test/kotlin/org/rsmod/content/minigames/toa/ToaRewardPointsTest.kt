package org.rsmod.content.minigames.toa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.game.entity.player.PlayerUid

class ToaRewardPointsTest {
    @Test
    fun `published starting total and room caps are exact`() {
        assertEquals(5_000, ToaRewardPointRules.STARTING_POINTS)
        assertEquals(64_000, ToaRewardPointRules.TOTAL_CAP)
        assertEquals(10_000, ToaRewardPointRules.roomCap(ToaRewardPointRoom.ZEBAK))
        assertEquals(60_000, ToaRewardPointRules.roomCap(ToaRewardPointRoom.WARDENS))
        (ToaRewardPointRoom.entries - setOf(ToaRewardPointRoom.ZEBAK, ToaRewardPointRoom.WARDENS))
            .forEach { assertEquals(20_000, ToaRewardPointRules.roomCap(it), it.name) }
    }

    @Test
    fun `points accrue only during active paths and Wardens`() {
        assertTrue(ToaRewardPointRules.canAccrue(ToaRaidState.ACTIVE))
        assertTrue(ToaRewardPointRules.canAccrue(ToaRaidState.WARDENS))
        setOf(
                ToaRaidState.FORMING,
                ToaRaidState.REWARDS,
                ToaRaidState.ABANDONED,
                ToaRaidState.COMPLETE,
            )
            .forEach { assertFalse(ToaRewardPointRules.canAccrue(it), it.name) }
    }

    @Test
    fun `damage multipliers use integer ratios and floor fractional points`() {
        fun points(damage: Int, kind: ToaRewardPointTargetKind): Int =
            ToaRewardPointRules.pointsForDamage(damage, ToaRewardPointTarget(kind))

        assertEquals(0, points(100, ToaRewardPointTargetKind.INELIGIBLE))
        assertEquals(5, points(5, ToaRewardPointTargetKind.ORDINARY))
        assertEquals(6, points(5, ToaRewardPointTargetKind.APMEKEN_BABOON))
        assertEquals(10, points(5, ToaRewardPointTargetKind.BABA))
        assertEquals(6, points(4, ToaRewardPointTargetKind.ZEBAK))
        assertEquals(2, points(5, ToaRewardPointTargetKind.KEPHRI_SCARAB))
        assertEquals(10, points(4, ToaRewardPointTargetKind.HET_SEAL))
        assertEquals(6, points(4, ToaRewardPointTargetKind.WARDEN_OBELISK))
        assertEquals(8, points(4, ToaRewardPointTargetKind.WARDEN_PHASE_TWO))
        assertEquals(10, points(4, ToaRewardPointTargetKind.WARDEN_PHASE_THREE))
        assertEquals(0, points(0, ToaRewardPointTargetKind.BABA))
        assertEquals(0, points(-1, ToaRewardPointTargetKind.BABA))
    }

    @Test
    fun `revision 240 path target ranges have their exact classifications`() {
        (11709..11718).forEach {
            assertEquals(
                ToaRewardPointTargetKind.APMEKEN_BABOON,
                ToaRewardPointTargets.fromNpc(it).kind,
                "Apmeken NPC $it",
            )
        }
        (11778..11780).forEach {
            assertEquals(
                ToaRewardPointTargetKind.BABA,
                ToaRewardPointTargets.fromNpc(it).kind,
                "Ba-Ba NPC $it",
            )
        }
        assertEquals(ToaRewardPointTargetKind.ORDINARY, ToaRewardPointTargets.fromNpc(11781).kind)
        (11782..11788).forEach {
            assertFalse(ToaRewardPointTargets.fromNpc(it).isEligible, "Ba-Ba hazard $it")
        }

        setOf(11730, 11732).forEach {
            assertEquals(ToaRewardPointTargetKind.ZEBAK, ToaRewardPointTargets.fromNpc(it).kind)
        }
        setOf(11735, 11736).forEach {
            assertEquals(ToaRewardPointTargetKind.ORDINARY, ToaRewardPointTargets.fromNpc(it).kind)
        }
        ((11730..11744).toSet() - setOf(11730, 11732, 11735, 11736)).forEach {
            assertFalse(ToaRewardPointTargets.fromNpc(it).isEligible, "Zebak hazard $it")
        }

        (11724..11726).forEach {
            assertEquals(
                ToaRewardPointTargetKind.KEPHRI_SCARAB,
                ToaRewardPointTargets.fromNpc(it).kind,
                "Kephri combat scarab $it",
            )
        }
        setOf(11723, 11727, 11728, 11729).forEach {
            assertEquals(
                ToaRewardPointTargetKind.ORDINARY,
                ToaRewardPointTargets.fromNpc(it).kind,
                "Kephri swarm or egg $it",
            )
        }
        (11719..11721).forEach {
            assertEquals(ToaRewardPointTargetKind.ORDINARY, ToaRewardPointTargets.fromNpc(it).kind)
        }
        assertFalse(ToaRewardPointTargets.fromNpc(11722).isEligible)
        assertEquals(ToaRewardPointTargetKind.HET_SEAL, ToaRewardPointTargets.fromNpc(11706).kind)
        assertEquals(ToaRewardPointTargetKind.HET_SEAL, ToaRewardPointTargets.fromNpc(11707).kind)
    }

    @Test
    fun `revision 240 puzzle cosmetics are fail closed`() {
        assertEquals(ToaRewardPointTargetKind.ORDINARY, ToaRewardPointTargets.fromNpc(11697).kind)
        assertEquals(ToaRewardPointTargetKind.ORDINARY, ToaRewardPointTargets.fromNpc(11698).kind)
        assertFalse(ToaRewardPointTargets.fromNpc(11699).isEligible)
        (11700..11704).forEach { assertFalse(ToaRewardPointTargets.fromNpc(it).isEligible) }
        assertEquals(ToaRewardPointTargetKind.ORDINARY, ToaRewardPointTargets.fromNpc(11705).kind)

        (11790..11795).forEach {
            assertEquals(ToaRewardPointTargetKind.ORDINARY, ToaRewardPointTargets.fromNpc(it).kind)
        }
        setOf(11797, 11798).forEach {
            assertEquals(ToaRewardPointTargetKind.ORDINARY, ToaRewardPointTargets.fromNpc(it).kind)
        }
        setOf(11789, 11796, 11799, 11800, 11801, 11802, 11803, 11804, 11805).forEach {
            assertFalse(ToaRewardPointTargets.fromNpc(it).isEligible, "Akkha cosmetic $it")
        }

        assertFalse(ToaRewardPointTargets.fromNpc(99_999).isEligible)
        assertEquals(
            ToaRewardPointTargetKind.ORDINARY,
            ToaRewardPointTargets.fromNpc(
                    99_999,
                    ToaRewardPointNpcContext(ordinaryDamageableTarget = true),
                )
                .kind,
        )
    }

    @Test
    fun `warden crosswalk classifies obelisks cores siphons and inactive forms`() {
        (11750..11752).forEach {
            assertEquals(
                ToaRewardPointTargetKind.WARDEN_OBELISK,
                ToaRewardPointTargets.fromNpc(it).kind,
            )
        }
        setOf(11770, 11771).forEach {
            assertFalse(ToaRewardPointTargets.fromNpc(it).isEligible, "Warden core $it")
        }

        val p2 =
            ToaRewardPointNpcContext(
                wardenPhase = ToaRewardPointWardenPhase.PHASE_TWO,
                phaseTwoDownsCompleted = 0,
            )
        setOf(11753, 11754, 11756, 11757).forEach {
            assertEquals(
                ToaRewardPointTargetKind.WARDEN_PHASE_TWO,
                ToaRewardPointTargets.fromNpc(it, p2).kind,
            )
            assertFalse(ToaRewardPointTargets.fromNpc(it).isEligible)
        }

        val p3 = ToaRewardPointNpcContext(wardenPhase = ToaRewardPointWardenPhase.PHASE_THREE)
        setOf(11761, 11762).forEach {
            assertEquals(
                ToaRewardPointTargetKind.WARDEN_PHASE_THREE,
                ToaRewardPointTargets.fromNpc(it, p3).kind,
            )
            assertFalse(ToaRewardPointTargets.fromNpc(it).isEligible)
        }

        setOf(11772, 11773).forEach {
            assertEquals(ToaRewardPointTargetKind.ORDINARY, ToaRewardPointTargets.fromNpc(it).kind)
            assertEquals(
                1,
                ToaRewardPointRules.pointsForDamage(1, ToaRewardPointTargets.fromNpc(it)),
            )
        }

        val eligibleSpecials =
            setOf(11750, 11751, 11752, 11753, 11754, 11756, 11757, 11761, 11762, 11772, 11773)
        ((11746..11777).toSet() - eligibleSpecials).forEach {
            assertFalse(ToaRewardPointTargets.fromNpc(it).isEligible, "Warden actor $it")
        }
    }

    @Test
    fun `phase two awards points for exactly the first three downs`() {
        (0..2).forEach { down ->
            val target = ToaRewardPointTargets.wardenPhaseTwo(down)
            assertTrue(target.isEligible, "down index $down")
            assertEquals(20, ToaRewardPointRules.pointsForDamage(10, target))
        }
        (3..5).forEach { down ->
            val target = ToaRewardPointTargets.wardenPhaseTwo(down)
            assertFalse(target.isEligible, "down index $down")
            assertEquals(0, ToaRewardPointRules.pointsForDamage(10, target))
        }
    }

    @Test
    fun `damage accrues in the current room without changing settled total`() {
        val ledger = ToaRewardPointsLedger(listOf(101L, 202L))
        val result =
            ledger.awardDamage(
                101L,
                ToaRewardPointRoom.APMEKEN,
                ToaRewardPointTargets.fromNpc(11709),
                actualDamage = 5,
            )

        assertEquals(6, result.scaledPoints)
        assertEquals(6, result.awardedPoints)
        assertEquals(5_000, result.totalAfter)
        assertEquals(6, result.roomAfter)
        assertEquals(5_000, ledger.totalPoints(101L))
        assertEquals(6, ledger.roomPoints(101L, ToaRewardPointRoom.APMEKEN))
        assertEquals(5_000, ledger.totalPoints(202L))
    }

    @Test
    fun `room and total caps are enforced on the actual awarded amount`() {
        val ordinary = ToaRewardPointsLedger(listOf(1L))
        val ordinaryAward =
            ordinary.awardDamage(
                1L,
                ToaRewardPointRoom.AKKHA,
                ToaRewardPointTargets.ordinary(),
                50_000,
            )
        assertEquals(20_000, ordinaryAward.awardedPoints)
        assertEquals(5_000, ordinary.totalPoints(1L))
        assertEquals(20_000, ordinary.roomPoints(1L, ToaRewardPointRoom.AKKHA))
        assertEquals(
            0,
            ordinary
                .awardDamage(1L, ToaRewardPointRoom.AKKHA, ToaRewardPointTargets.ordinary(), 1)
                .awardedPoints,
        )
        ordinary.settleRoom(ToaRewardPointRoom.AKKHA, generation = 1)
        assertEquals(25_300, ordinary.totalPoints(1L))

        val zebak = ToaRewardPointsLedger(listOf(2L))
        assertEquals(
            10_000,
            zebak
                .awardDamage(
                    2L,
                    ToaRewardPointRoom.ZEBAK,
                    ToaRewardPointTargets.fromNpc(11730),
                    20_000,
                )
                .awardedPoints,
        )
        assertEquals(10_000, zebak.roomPoints(2L, ToaRewardPointRoom.ZEBAK))

        val wardens = ToaRewardPointsLedger(listOf(3L))
        assertEquals(
            60_000,
            wardens
                .awardDamage(
                    3L,
                    ToaRewardPointRoom.WARDENS,
                    ToaRewardPointTarget(ToaRewardPointTargetKind.WARDEN_PHASE_THREE),
                    30_000,
                )
                .awardedPoints,
        )
        assertEquals(5_000, wardens.totalPoints(3L))
        assertEquals(60_000, wardens.roomPoints(3L, ToaRewardPointRoom.WARDENS))
        wardens.settleRoom(ToaRewardPointRoom.WARDENS, generation = 1)
        assertEquals(64_000, wardens.totalPoints(3L))
    }

    @Test
    fun `room settlement resets scores and awards one locked-party-size MVP bonus`() {
        val ledger = ToaRewardPointsLedger(listOf(30L, 10L, 20L))
        ledger.awardDamage(30L, ToaRewardPointRoom.BABA, ToaRewardPointTargets.ordinary(), 20)
        ledger.awardDamage(10L, ToaRewardPointRoom.BABA, ToaRewardPointTargets.ordinary(), 40)
        ledger.awardDamage(20L, ToaRewardPointRoom.BABA, ToaRewardPointTargets.ordinary(), 10)

        val settlement = ledger.settleRoom(ToaRewardPointRoom.BABA, generation = 44)

        assertTrue(settlement.applied)
        assertEquals(10L, settlement.mvpAccountHash)
        assertEquals(900, settlement.mvpBonusAwarded)
        assertEquals(mapOf(30L to 20, 10L to 40, 20L to 10), settlement.roomScores)
        assertEquals(5_940, ledger.totalPoints(10L))
        listOf(30L, 10L, 20L).forEach {
            assertEquals(0, ledger.roomPoints(it, ToaRewardPointRoom.BABA))
        }

        val duplicate = ledger.settleRoom(ToaRewardPointRoom.BABA, generation = 44)
        assertFalse(duplicate.applied)
        assertEquals(0, duplicate.mvpBonusAwarded)
        assertEquals(5_940, ledger.totalPoints(10L))
        assertEquals(
            0,
            ledger
                .awardDamage(10L, ToaRewardPointRoom.BABA, ToaRewardPointTargets.ordinary(), 100)
                .awardedPoints,
        )
    }

    @Test
    fun `MVP ties use locked roster order and still select exactly one member`() {
        val ledger = ToaRewardPointsLedger(listOf(300L, 100L, 200L))
        val settlement = ledger.settleRoom(ToaRewardPointRoom.CRONDIS, generation = 1)

        assertTrue(settlement.applied)
        assertEquals(300L, settlement.mvpAccountHash)
        assertEquals(900, settlement.mvpBonusAwarded)
        assertEquals(5_900, ledger.totalPoints(300L))
        assertEquals(5_000, ledger.totalPoints(100L))
        assertEquals(5_000, ledger.totalPoints(200L))
    }

    @Test
    fun `death reduces settled total while pending room score survives until settlement`() {
        val ledger = ToaRewardPointsLedger(listOf(101L, 202L, 303L, 404L))
        ledger.awardDamage(101L, ToaRewardPointRoom.BABA, ToaRewardPointTargets.ordinary(), 500)
        assertEquals(5_000, ledger.totalPoints(101L))
        assertEquals(500, ledger.roomPoints(101L, ToaRewardPointRoom.BABA))

        val first = ledger.applyDeath(101L, generation = 10)
        assertTrue(first.applied)
        assertEquals(1_000, first.deductedPoints)
        assertEquals(4_000, first.totalAfter)
        assertEquals(500, ledger.roomPoints(101L, ToaRewardPointRoom.BABA))

        assertFalse(ledger.applyDeath(101L, generation = 10).applied)
        assertFalse(ledger.applyDeath(101L, generation = 9).applied)
        assertEquals(4_000, ledger.totalPoints(101L))

        val settlement = ledger.settleRoom(ToaRewardPointRoom.BABA, generation = 11)
        assertEquals(101L, settlement.mvpAccountHash)
        assertEquals(1_200, settlement.mvpBonusAwarded)
        assertEquals(5_700, ledger.totalPoints(101L))
        assertEquals(0, ledger.roomPoints(101L, ToaRewardPointRoom.BABA))
    }

    @Test
    fun `minimum death deduction clamps at zero`() {
        val ledger = ToaRewardPointsLedger(listOf(1L))
        repeat(5) { index -> ledger.applyDeath(1L, generation = index.toLong()) }
        assertEquals(0, ledger.totalPoints(1L))
        val atZero = ledger.applyDeath(1L, generation = 6)
        assertTrue(atZero.applied)
        assertEquals(0, atZero.deductedPoints)
        assertEquals(0, atZero.totalAfter)
    }

    @Test
    fun `locked roster rejects duplicates size drift and unknown accounts`() {
        assertFailsWith<IllegalArgumentException> { ToaRewardPointsLedger(emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            ToaRewardPointsLedger(listOf(1L, 1L), lockedPartySize = 2)
        }
        assertFailsWith<IllegalArgumentException> {
            ToaRewardPointsLedger(listOf(1L), lockedPartySize = 2)
        }
        val ledger = ToaRewardPointsLedger(listOf(1L))
        assertFailsWith<IllegalArgumentException> { ledger.totalPoints(2L) }
    }

    @Test
    fun `Crondis water awards two points for each accepted unit`() {
        assertEquals(0, ToaRewardPointRules.crondisWaterPoints(-1))
        assertEquals(0, ToaRewardPointRules.crondisWaterPoints(0))
        assertEquals(150, ToaRewardPointRules.crondisWaterPoints(75))
        assertEquals(200, ToaRewardPointRules.crondisWaterPoints(100))

        val ledger = ToaRewardPointsLedger(listOf(101L))
        val award =
            ledger.awardActionPoints(
                accountHash = 101L,
                room = ToaRewardPointRoom.CRONDIS,
                points = ToaRewardPointRules.crondisWaterPoints(75),
            )
        assertEquals(150, award.awardedPoints)
        assertEquals(5_000, ledger.totalPoints(101L))
        assertEquals(150, ledger.roomPoints(101L, ToaRewardPointRoom.CRONDIS))
    }

    @Test
    fun `raid session keeps participant totals synchronized with locked party scoring`() {
        val accountHashes = linkedSetOf(101L, 202L, 303L)
        val snapshot =
            ToaRaidStartSnapshot.create(
                partySize = accountHashes.size,
                raidLevel = 0,
                invocations = emptySet(),
                pathLevels = TOA_ACTIVE_PATHS.associateWith { 0 },
                seed = 0x504F494E5453L,
                participantAccountHashes = accountHashes,
            )
        val raid = ToaRaidSession(ToaParty(PlayerUid(1)), snapshot, startedAt = 100)
        val first = ToaParticipant(PlayerUid(1), accountHash = 101L)
        val second = ToaParticipant(PlayerUid(2), accountHash = 202L)
        val disconnected =
            ToaParticipant(
                PlayerUid(3),
                accountHash = 303L,
                connected = false,
                rewardEligible = false,
            )
        raid.participants.add(first)
        raid.participants.add(second)
        raid.participants.add(disconnected)

        raid.awardRewardActionPoints(101L, ToaRewardPointRoom.CRONDIS, 200)
        raid.awardRewardDamage(
            accountHash = 101L,
            room = ToaRewardPointRoom.BABA,
            target = ToaRewardPointTarget(ToaRewardPointTargetKind.BABA),
            actualDamage = 40,
        )
        assertEquals(5_000, first.points)
        assertEquals(200, raid.rewardPointLedger.roomPoints(101L, ToaRewardPointRoom.CRONDIS))
        assertEquals(80, raid.rewardPointLedger.roomPoints(101L, ToaRewardPointRoom.BABA))

        val settlement = raid.settleRewardPointRoom(ToaRewardPointRoom.BABA, generation = 7)
        assertEquals(101L, settlement.mvpAccountHash)
        assertEquals(900, settlement.mvpBonusAwarded)
        assertEquals(5_980, first.points)
        assertEquals(5_000, second.points)
        assertEquals(5_000, disconnected.points)

        val death = raid.applyRewardPointDeath(101L, generation = 8)
        assertEquals(1_196, death.deductedPoints)
        assertEquals(4_784, first.points)
        assertFalse(raid.applyRewardPointDeath(101L, generation = 8).applied)
        assertEquals(4_784, first.points)
    }

    @Test
    fun `delayed impact accepts live Scabaras ownership but rejects a stale room`() {
        assertTrue(
            ToaRewardPointImpactGuard.canAward(
                raidState = ToaRaidState.ACTIVE,
                capturedRoom = ToaRewardPointRoom.SCABARAS,
                currentRoom = ToaRewardPointRoom.SCABARAS,
                raidOwnsTarget = false,
                scabarasOwnsTarget = true,
                sameRaid = true,
                sameTarget = true,
                damage = 1,
            )
        )
        assertFalse(
            ToaRewardPointImpactGuard.canAward(
                raidState = ToaRaidState.ACTIVE,
                capturedRoom = ToaRewardPointRoom.SCABARAS,
                currentRoom = ToaRewardPointRoom.KEPHRI,
                raidOwnsTarget = false,
                scabarasOwnsTarget = true,
                sameRaid = true,
                sameTarget = true,
                damage = 100,
            )
        )
        assertFalse(
            ToaRewardPointImpactGuard.canAward(
                raidState = ToaRaidState.ACTIVE,
                capturedRoom = ToaRewardPointRoom.KEPHRI,
                currentRoom = ToaRewardPointRoom.KEPHRI,
                raidOwnsTarget = false,
                scabarasOwnsTarget = false,
                sameRaid = true,
                sameTarget = true,
                damage = 100,
            )
        )
        assertFalse(
            ToaRewardPointImpactGuard.canAward(
                raidState = ToaRaidState.REWARDS,
                capturedRoom = ToaRewardPointRoom.KEPHRI,
                currentRoom = ToaRewardPointRoom.KEPHRI,
                raidOwnsTarget = true,
                scabarasOwnsTarget = false,
                sameRaid = true,
                sameTarget = true,
                damage = 100,
            )
        )
    }
}
