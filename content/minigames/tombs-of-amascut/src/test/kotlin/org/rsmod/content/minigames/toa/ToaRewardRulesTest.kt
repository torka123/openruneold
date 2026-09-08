package org.rsmod.content.minigames.toa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.rsmod.game.entity.player.PlayerUid

class ToaRewardRulesTest {
    @Test
    fun baseUniqueAssignmentUsesTheTwentyFourWeightOsrsTableInTenths() {
        assertEquals(240, ToaRewardRules.totalUniqueWeight)
        assertEquals(
            listOf(70, 70, 30, 20, 20, 20, 10),
            ToaRewardRules.uniques.map(WeightedToaUnique::weight),
        )
        assertEquals(26219, ToaRewardRules.uniqueAtTicket(0).objId)
        assertEquals(26219, ToaRewardRules.uniqueAtTicket(69).objId)
        assertEquals(25975, ToaRewardRules.uniqueAtTicket(70).objId)
        assertEquals(25985, ToaRewardRules.uniqueAtTicket(140).objId)
        assertEquals(27277, ToaRewardRules.uniqueAtTicket(239).objId)
    }

    @Test
    fun uniqueWeightsInterpolateAtEveryFiveRaidLevels() {
        fun weights(level: Int) =
            ToaRewardRules.uniqueTable(level).associate { it.objId to it.weight }

        assertEquals(70, weights(300).getValue(26219))
        assertEquals(69, weights(305).getValue(26219))
        assertEquals(weights(305), weights(309))
        assertEquals(60, weights(350).getValue(26219))
        assertEquals(60, weights(350).getValue(25975))
        assertEquals(40, weights(400).getValue(26219))
        assertEquals(50, weights(400).getValue(25975))
        assertEquals(40, weights(450).getValue(26219))
        assertEquals(40, weights(450).getValue(25975))
        assertEquals(30, weights(500).getValue(26219))
        assertEquals(35, weights(500).getValue(25975))
        assertEquals(weights(500), weights(600))
        assertEquals(220, ToaRewardRules.totalUniqueWeight(350))
        assertEquals(190, ToaRewardRules.totalUniqueWeight(400))
        assertEquals(180, ToaRewardRules.totalUniqueWeight(450))
        assertEquals(165, ToaRewardRules.totalUniqueWeight(500))
        assertEquals(27277, ToaRewardRules.uniqueAtTicket(500, 164).objId)
    }

    @Test
    fun raidLevelZeroRequiresTheAdditionalBypassForEveryTradeableUnique() {
        assertTrue(ToaRewardRules.uniques.all { ToaRewardRules.requiresBypass(0, it) })
        assertFalse(
            ToaRewardRules.requiresBypass(50, ToaRewardRules.uniques.first { it.objId == 26219 })
        )
        assertTrue(
            ToaRewardRules.requiresBypass(149, ToaRewardRules.uniques.first { it.objId == 25985 })
        )
        assertFalse(
            ToaRewardRules.requiresBypass(150, ToaRewardRules.uniques.first { it.objId == 27277 })
        )
    }

    @Test
    fun restrictedUniqueRequiresOneInFiftyBypassOrAwardsTertiaryFallback() {
        val shadow = ToaRewardRules.uniques.first { it.objId == 27277 }

        assertEquals(
            ToaReward(27277, 1, unique = true),
            ToaRewardRules.assignedUniqueReward(
                raidLevel = 0,
                unique = shadow,
                bypassSucceeded = true,
                fallbackObjId = null,
            ),
        )
        assertEquals(
            ToaReward(27279, 1, unique = false),
            ToaRewardRules.assignedUniqueReward(
                raidLevel = 0,
                unique = shadow,
                bypassSucceeded = false,
                fallbackObjId = 27279,
            ),
        )
        assertEquals(
            ToaReward(27277, 1, unique = true),
            ToaRewardRules.assignedUniqueReward(
                raidLevel = 150,
                unique = shadow,
                bypassSucceeded = false,
                fallbackObjId = null,
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            ToaRewardRules.assignedUniqueReward(
                raidLevel = 0,
                unique = shadow,
                bypassSucceeded = false,
                fallbackObjId = 995,
            )
        }
    }

    @Test
    fun startingAndSubthresholdPointsDoNotContributeToLootOrOwnerTickets() {
        assertEquals(0, ToaRules.rewardPoints(5_000))
        assertEquals(2_500, ToaRules.rewardPoints(7_500))
        assertEquals(0, ToaRules.lootEligiblePoints(6_499))
        assertEquals(1_500, ToaRules.lootEligiblePoints(6_500))

        val noContribution = ToaParticipant(PlayerUid(1), accountHash = 101L, points = 5_000)
        val belowThreshold = ToaParticipant(PlayerUid(2), accountHash = 202L, points = 6_499)
        val contributor = ToaParticipant(PlayerUid(3), accountHash = 303L, points = 7_500)
        val participants = listOf(noContribution, belowThreshold, contributor)

        assertEquals(contributor, ToaRules.ownerTicket(participants, 0))
        assertEquals(contributor, ToaRules.ownerTicket(participants, 2_499))
        assertNull(ToaRules.ownerTicket(participants, 2_500))
    }

    @Test
    fun commonTableHasTwentySevenEqualEntriesAndCurrentDivisors() {
        assertEquals(
            listOf(
                995 to 1,
                560 to 20,
                566 to 40,
                444 to 90,
                11232 to 100,
                6332 to 180,
                1623 to 200,
                1621 to 250,
                2357 to 250,
                3138 to 250,
                383 to 250,
                1619 to 300,
                1617 to 400,
                389 to 450,
                6016 to 600,
                1631 to 600,
                1391 to 1_100,
                5935 to 1_100,
                27272 to 1_100,
                5296 to 2_000,
                5295 to 2_500,
                5304 to 3_200,
                5300 to 3_200,
                1149 to 4_000,
                5316 to 6_500,
                26390 to 7_500,
                27293 to 1,
            ),
            ToaRewardRules.commonRewards.map { it.objId to it.divisor },
        )
        assertEquals(27, ToaRewardRules.commonRewards.size)
        assertEquals(
            setOf(
                444,
                6332,
                1623,
                1621,
                2357,
                3138,
                383,
                1619,
                1617,
                389,
                6016,
                1631,
                1391,
                5935,
                27272,
                1149,
            ),
            ToaRewardRules.commonRewards.filter(ToaCommonReward::noted).map { it.objId }.toSet(),
        )
    }

    @Test
    fun commonAmountsUseExactDivisorsAndExpertMultiplierSteps() {
        val dartTips = ToaRewardRules.commonRewards.first { it.objId == 11232 }
        val battlestaff = ToaRewardRules.commonRewards.first { it.objId == 1391 }
        val runeCache = ToaRewardRules.commonRewards.first { it.objId == 27293 }

        assertEquals(15, ToaRewardRules.commonQuantity(1_500, 0, dartTips))
        assertEquals(100, ToaRewardRules.commonQuantity(10_000, 299, dartTips))
        assertEquals(115, ToaRewardRules.commonQuantity(10_000, 300, dartTips))
        assertEquals(116, ToaRewardRules.commonQuantity(10_000, 305, dartTips))
        assertEquals(135, ToaRewardRules.commonQuantity(10_000, 400, dartTips))
        assertEquals(175, ToaRewardRules.commonQuantity(10_000, 600, dartTips))
        assertEquals(1, ToaRewardRules.commonQuantity(1_500, 0, battlestaff))
        assertEquals(1, ToaRewardRules.commonQuantity(64_000, 600, runeCache))
    }

    @Test
    fun commonRollsAreThreeIndependentWithReplacementSelections() {
        val expected = ToaReward(11232, 15, unique = false)
        assertEquals(
            List(ToaRewardRules.COMMON_ROLLS) { expected },
            ToaRewardRules.commonRolls(listOf(4, 4, 4), points = 1_500, raidLevel = 0),
        )
    }

    @Test
    fun lowPointOutcomeIsOneNotedFossilisedDung() {
        assertEquals(
            ToaReward(27216, 1, unique = false, noted = true),
            ToaRewardRules.lowPointReward(),
        )
    }

    @Test
    fun restrictedAssignmentFallbackCatalogUsesRevision240Untradeables() {
        assertEquals(
            setOf(27279, 27283, 27285, 27289, 30893),
            ToaRewardRules.untradeableFallbacks.toSet(),
        )
    }

    @Test
    fun pendingRewardCodecRoundTripsNotedCommonAndUniqueRolls() {
        val common =
            ToaRewardRoll(
                listOf(
                    ToaReward(444, 40, unique = false, noted = true),
                    ToaReward(11232, 25, unique = false),
                ),
                unique = false,
            )
        val unique = ToaRewardRoll(listOf(ToaReward(26219, 1, unique = true)), unique = true)

        assertEquals(common, ToaPendingRewardCodec.decode(ToaPendingRewardCodec.encode(common)))
        assertEquals(unique, ToaPendingRewardCodec.decode(ToaPendingRewardCodec.encode(unique)))
    }

    @Test
    fun pendingRewardCodecStillReadsVersionOnePayloads() {
        val versionOne = listOf(0x544F_4101, 0, 1, 444, 12, 0)
        assertEquals(
            ToaRewardRoll(
                listOf(ToaReward(444, 12, unique = false, noted = false)),
                unique = false,
            ),
            ToaPendingRewardCodec.decode(versionOne),
        )
    }

    @Test
    fun pendingRewardCodecRejectsTruncatedOrContradictoryPayloads() {
        val encoded =
            ToaPendingRewardCodec.encode(
                ToaRewardRoll(listOf(ToaReward(26219, 1, unique = true)), unique = true)
            )
        assertNull(ToaPendingRewardCodec.decode(encoded.dropLast(1)))
        encoded[1] = 0
        assertNull(ToaPendingRewardCodec.decode(encoded))
    }

    @Test
    fun `revision 240 tertiary item and partisan crosswalk is exact`() {
        assertEquals(27279, ToaTertiaryRewardRules.THREAD_OF_ELIDINIS)
        assertEquals(
            listOf(27283, 27285, 27289, 30893),
            ToaTertiaryRewardRules.jewels.map(ToaJewelReward::jewelId),
        )
        assertEquals(
            listOf(27284, 27287, 27291, 30891),
            ToaTertiaryRewardRules.jewels.map(ToaJewelReward::partisanId),
        )
        assertEquals(12073, ToaTertiaryRewardRules.ELITE_CLUE_SCROLL)
        assertEquals(24365, ToaTertiaryRewardRules.ELITE_CLUE_SCROLL_BOX)
        assertEquals(27352, ToaTertiaryRewardRules.TUMEKENS_GUARDIAN)
    }

    @Test
    fun `thread bad luck mitigation and repeat rate use exact ticket spaces`() {
        val fresh = ToaTertiaryProfile(completions = 0)
        assertEquals(150, ToaTertiaryRewardRules.threadTicketBound(fresh))
        assertEquals(15, ToaTertiaryRewardRules.threadWinningTickets(fresh))
        assertTrue(ToaTertiaryRewardRules.threadRolled(fresh, 14))
        assertFalse(ToaTertiaryRewardRules.threadRolled(fresh, 15))

        val mitigated = ToaTertiaryProfile(completions = 15)
        assertEquals(45, ToaTertiaryRewardRules.threadWinningTickets(mitigated))
        assertTrue(ToaTertiaryRewardRules.threadRolled(mitigated, 44))
        assertFalse(ToaTertiaryRewardRules.threadRolled(mitigated, 45))

        val repeat = ToaTertiaryProfile(completions = 500, threadReceived = true)
        assertEquals(50, ToaTertiaryRewardRules.threadTicketBound(repeat))
        assertEquals(1, ToaTertiaryRewardRules.threadWinningTickets(repeat))
        assertTrue(ToaTertiaryRewardRules.threadRolled(repeat, 0))
        assertFalse(ToaTertiaryRewardRules.threadRolled(repeat, 1))
    }

    @Test
    fun `jewel mitigation targets missing jewels and resets after all four`() {
        val first = ToaTertiaryRewardRules.jewels.first()
        val missing = ToaTertiaryProfile(completions = 75, ownedJewels = setOf(first.jewelId))
        assertEquals(900, ToaTertiaryRewardRules.jewelWinningTickets(missing))
        assertTrue(ToaTertiaryRewardRules.jewelRolled(missing, 899))
        assertFalse(ToaTertiaryRewardRules.jewelRolled(missing, 900))
        assertFalse(ToaTertiaryRewardRules.jewelCandidates(missing).contains(first))

        val complete =
            ToaTertiaryProfile(completions = 5_000, ownedJewels = ToaTertiaryRewardRules.jewelIds)
        assertEquals(300, ToaTertiaryRewardRules.jewelWinningTickets(complete))
        assertTrue(ToaTertiaryRewardRules.jewelRolled(complete, 299))
        assertFalse(ToaTertiaryRewardRules.jewelRolled(complete, 300))
        assertEquals(
            ToaTertiaryRewardRules.jewels,
            ToaTertiaryRewardRules.jewelCandidates(complete),
        )
    }

    @Test
    fun `elite clue and guardian formulas use eligible points and scaled raid level`() {
        assertEquals(0, ToaTertiaryRewardRules.clueWinningTickets(1_999))
        assertEquals(1, ToaTertiaryRewardRules.clueWinningTickets(2_000))
        assertEquals(25, ToaTertiaryRewardRules.clueWinningTickets(50_000))
        assertEquals(25, ToaTertiaryRewardRules.clueWinningTickets(Int.MAX_VALUE))
        assertTrue(ToaTertiaryRewardRules.clueRolled(2_000, 0))
        assertFalse(ToaTertiaryRewardRules.clueRolled(2_000, 1))

        assertEquals(400, ToaTertiaryRewardRules.petScaledRaidLevel(400))
        assertEquals(400, ToaTertiaryRewardRules.petScaledRaidLevel(401))
        assertEquals(401, ToaTertiaryRewardRules.petScaledRaidLevel(403))
        assertEquals(450, ToaTertiaryRewardRules.petScaledRaidLevel(550))
        assertEquals(450, ToaTertiaryRewardRules.petScaledRaidLevel(600))
        assertEquals(350_000, ToaTertiaryRewardRules.petPointsPerPercent(0))
        assertEquals(70_000, ToaTertiaryRewardRules.petPointsPerPercent(400))
        assertEquals(35_000, ToaTertiaryRewardRules.petPointsPerPercent(550))
        assertTrue(ToaTertiaryRewardRules.petRolled(100, 0, 99))
        assertFalse(ToaTertiaryRewardRules.petRolled(100, 0, 100))
    }

    @Test
    fun `version three pending codec preserves durable identity pet and tertiaries`() {
        val roll =
            ToaRewardRoll(
                rewards =
                    listOf(
                        ToaReward(ToaTertiaryRewardRules.THREAD_OF_ELIDINIS, 1, false),
                        ToaReward(ToaTertiaryRewardRules.JEWEL_OF_AMASCUT, 1, false),
                    ),
                unique = false,
                pet = true,
                claimId = ToaRewardClaimId(Long.MIN_VALUE + 7, Long.MAX_VALUE - 9, 0x1_0000_002AL),
            )
        assertEquals(roll, ToaPendingRewardCodec.decode(ToaPendingRewardCodec.encode(roll)))
    }

    @Test
    fun `partial party completion preserves existing rolls and fills only missing accounts`() {
        val existingRoll =
            ToaRewardRoll(listOf(ToaReward(444, 10, unique = false, noted = true)), unique = false)
        val generatedUnique =
            ToaRewardRoll(listOf(ToaReward(26219, 1, unique = true)), unique = true)
        val result =
            ToaRewardPlanCompletion.complete(
                participantAccountHashes = listOf(101L, 202L),
                existing = mapOf(101L to existingRoll),
                generated = mapOf(202L to generatedUnique),
            )
        assertEquals(existingRoll, result.getValue(101L))
        assertEquals(generatedUnique, result.getValue(202L))
        assertEquals(listOf(101L, 202L), result.keys.toList())

        assertFailsWith<IllegalArgumentException> {
            ToaRewardPlanCompletion.complete(
                participantAccountHashes = listOf(101L, 202L),
                existing = mapOf(101L to generatedUnique),
                generated = mapOf(202L to generatedUnique),
            )
        }
    }

    @Test
    fun `disconnected entitlement survives restart and crash retry claims exactly once`() {
        val roll =
            ToaRewardRoll(
                rewards = listOf(ToaReward(444, 25, unique = false, noted = true)),
                unique = false,
                claimId = ToaRewardClaimId(11L, 22L, 33L),
            )
        var durablePending: MutableList<Int>? = ToaPendingRewardCodec.encode(roll)
        var durableItems = 0

        fun restartAndClaim(crashAfterDelivery: Boolean): Boolean {
            var runtimePending: ToaRewardRoll? =
                ToaPendingRewardCodec.decode(durablePending) ?: return false
            var runtimeItems = durableItems
            return try {
                ToaCrashSafeClaimCommit.commit(
                    commitMutation = { mutation, rollback ->
                        try {
                            val changed = mutation()
                            if (changed) {
                                durableItems = runtimeItems
                                durablePending = runtimePending?.let(ToaPendingRewardCodec::encode)
                            }
                            changed
                        } catch (cause: Throwable) {
                            rollback()
                            throw cause
                        }
                    },
                    deliver = {
                        runtimeItems +=
                            requireNotNull(runtimePending).rewards.sumOf(ToaReward::count)
                        true
                    },
                    afterDelivery = { if (crashAfterDelivery) error("simulated process crash") },
                    finalizeInMemory = { runtimePending = null },
                    rollback = {
                        runtimeItems = durableItems
                        runtimePending = ToaPendingRewardCodec.decode(durablePending)
                    },
                )
            } catch (_: IllegalStateException) {
                false
            }
        }

        // The reward was persisted for an offline participant before any runtime player exists.
        assertEquals(roll, ToaPendingRewardCodec.decode(durablePending))
        assertFalse(restartAndClaim(crashAfterDelivery = true))
        assertEquals(0, durableItems)
        assertEquals(roll, ToaPendingRewardCodec.decode(durablePending))

        assertTrue(restartAndClaim(crashAfterDelivery = false))
        assertEquals(25, durableItems)
        assertNull(durablePending)

        // A second service/runtime reconstruction sees no pending entitlement and cannot deliver.
        assertFalse(restartAndClaim(crashAfterDelivery = false))
        assertEquals(25, durableItems)
    }

    @Test
    fun `account hash resolves exact positive character identity`() {
        assertEquals(42, ToaRewardAccountIdentity.characterId((7L shl 32) or 42L))
        assertFailsWith<IllegalArgumentException> { ToaRewardAccountIdentity.characterId(0L) }
        assertFailsWith<IllegalArgumentException> {
            ToaRewardAccountIdentity.characterId(0xFFFF_FFFFL)
        }
    }
}
