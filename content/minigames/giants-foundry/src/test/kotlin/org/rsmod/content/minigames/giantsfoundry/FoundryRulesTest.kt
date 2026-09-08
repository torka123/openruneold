package org.rsmod.content.minigames.giantsfoundry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.map.CoordGrid

class FoundryRulesTest {
    @Test
    fun workshopBoundsOnlyMatchTheFoundryMapSquare() {
        assertTrue(FoundryWorkshop.contains(CoordGrid(3369, 11486, 0)))
        assertFalse(FoundryWorkshop.contains(CoordGrid(3369, 11486, 1)))
        assertFalse(FoundryWorkshop.contains(CoordGrid(3369, 11455, 0)))
    }

    @Test
    fun allThirtyThreeMouldsAndTwelveLegalCommissionsArePresent() {
        assertEquals(33, FoundryMoulds.all.size)
        assertEquals(18, FoundryMoulds.all.count { it.default })
        assertEquals(12, FoundryRules.commissionPairs.size)
        assertFalse(FoundryRules.isValidCommission(FoundryDescriptor.HEAVY, FoundryDescriptor.HEAVY))
        assertFalse(FoundryRules.isValidCommission(FoundryDescriptor.LIGHT, FoundryDescriptor.LIGHT))
        assertEquals(
            FoundryDescriptor.BROAD to FoundryDescriptor.HEAVY,
            FoundryRules.commissionForRoll(0),
        )
        assertEquals(FoundryRules.commissionForRoll(0), FoundryRules.commissionForRoll(12))
    }

    @Test
    fun defaultAndUnlockedMouldScoresMatchDocumentedExtremes() {
        val commission = FoundryCommission(1, FoundryDescriptor.BROAD, FoundryDescriptor.HEAVY, 0)
        val defaults =
            listOf(
                FoundryMoulds.byKey.getValue("medusa_ricasso"),
                FoundryMoulds.byKey.getValue("medusa_blade"),
                FoundryMoulds.byKey.getValue("saw_tip"),
            )
        assertEquals(44, FoundryRules.mouldScore(commission, defaults))
        val best =
            listOf(
                FoundryMoulds.byKey.getValue("defender_base"),
                FoundryMoulds.byKey.getValue("fleur_de_blade"),
                FoundryMoulds.byKey.getValue("defenders_tip"),
            )
        assertEquals(58, FoundryRules.mouldScore(commission, best))
        assertEquals(69, FoundryRules.MAX_MOULD_SCORE)
    }

    @Test
    fun documentedMetalScoreAndSectionVectorsMatchExactly() {
        val vectors =
            listOf(
                mapOf(FoundryMetal.BRONZE to 28) to (10 to 3),
                mapOf(FoundryMetal.IRON to 28) to (20 to 4),
                mapOf(FoundryMetal.STEEL to 19, FoundryMetal.MITHRIL to 9) to (59 to 4),
                mapOf(FoundryMetal.MITHRIL to 18, FoundryMetal.ADAMANT to 10) to (89 to 5),
                mapOf(FoundryMetal.ADAMANT to 19, FoundryMetal.RUNE to 9) to (118 to 6),
                mapOf(FoundryMetal.ADAMANT to 14, FoundryMetal.RUNE to 14) to (130 to 7),
            )
        for ((amounts, expected) in vectors) {
            val score = FoundryRules.metalScore(FoundryCrucible(amounts))
            assertEquals(expected.first, score)
            assertEquals(expected.second, FoundryRules.sectionCount(score))
        }
    }

    @Test
    fun nativeHeatWindowsMatchTheHudAtHighMetalScores() {
        val preform = FoundryPreform(1, 118, 0, 118, 118, 6)
        assertEquals(75..257, FoundryRules.heatRange(preform, FoundryHeatBand.LOW))
        assertEquals(408..590, FoundryRules.heatRange(preform, FoundryHeatBand.MEDIUM))
        assertEquals(741..923, FoundryRules.heatRange(preform, FoundryHeatBand.HIGH))
        assertTrue(FoundryRules.inRequiredHeat(preform.copy(heat = 250), FoundryTool.POLISHING_WHEEL))
        assertFalse(FoundryRules.inRequiredHeat(preform.copy(heat = 258), FoundryTool.POLISHING_WHEEL))
    }
    @Test
    fun higherTierWinsAQuantityTieWhenChoosingTopTwoMetals() {
        val withThirdMetal =
            FoundryCrucible(
                mapOf(FoundryMetal.STEEL to 9, FoundryMetal.MITHRIL to 9, FoundryMetal.RUNE to 10),
            )
        val expectedTopTwo = FoundryCrucible(mapOf(FoundryMetal.RUNE to 10, FoundryMetal.MITHRIL to 9, FoundryMetal.BRONZE to 9))
        assertEquals(FoundryRules.metalScore(expectedTopTwo), FoundryRules.metalScore(withThirdMetal))
    }

    @Test
    fun rewardFormulaMatchesPublishedHandInVectors() {
        assertEquals(FoundryReward(0, 30, 60, 0), FoundryRules.reward(0))
        assertEquals(FoundryReward(133, 13260, 26520, 133), FoundryRules.reward(133))
        assertEquals(FoundryReward(199, 25230, 50460, 199), FoundryRules.reward(199))
    }

    @Test
    fun pourRequiresAFullCrucibleAndServerSideMouldEligibility() {
        val commission = FoundryCommission(17, FoundryDescriptor.BROAD, FoundryDescriptor.HEAVY, 0)
        val assigned = (FoundryReducer.assign(FoundryState(), commission) as FoundryTransition.Accepted).state
        val partial = FoundryReducer.pour(assigned, smithingLevel = 99)
        assertTrue(partial is FoundryTransition.Rejected)

        assertTrue(
            FoundryReducer.selectMould(assigned, MouldSlot.FORTE, "defender_base") is
                FoundryTransition.Rejected,
        )
        var state = (FoundryReducer.addMetal(assigned, FoundryMetal.RUNE, 28) as FoundryTransition.Accepted).state
        assertTrue(FoundryReducer.pour(state, smithingLevel = 99) is FoundryTransition.Rejected)

        state = (FoundryReducer.selectMould(state, MouldSlot.FORTE, "gladius_ricasso") as FoundryTransition.Accepted).state
        state = (FoundryReducer.selectMould(state, MouldSlot.BLADE, "defenders_edge") as FoundryTransition.Accepted).state
        state = (FoundryReducer.selectMould(state, MouldSlot.TIP, "medusas_head") as FoundryTransition.Accepted).state
        val poured = FoundryReducer.pour(state, smithingLevel = 99)
        val preform = (poured as FoundryTransition.Accepted).state.preform
        assertEquals(84, preform?.quality)
        assertEquals(5, preform?.sections)
    }

    @Test
    fun reducerRejectsDuplicateCommissionAndCapacityOverflowWithoutChangingState() {
        val commission = FoundryCommission(1, FoundryDescriptor.BROAD, FoundryDescriptor.HEAVY, 0)
        val assigned = (FoundryReducer.assign(FoundryState(), commission) as FoundryTransition.Accepted).state
        assertTrue(FoundryReducer.assign(assigned, commission) is FoundryTransition.Rejected)
        val full = (FoundryReducer.addMetal(assigned, FoundryMetal.RUNE, 28) as FoundryTransition.Accepted).state
        assertTrue(FoundryReducer.addMetal(full, FoundryMetal.BRONZE, 1) is FoundryTransition.Rejected)
        assertEquals(28, full.crucible.total)
        assertTrue(full.revision > assigned.revision)
    }
    @Test
    fun fullFoundryLoopStartsAtTripHammerAndCanBeHandedIn() {
        val commission = FoundryCommission(1, FoundryDescriptor.BROAD, FoundryDescriptor.HEAVY, 0)
        var state = accepted(FoundryReducer.assign(FoundryState(), commission))
        val defaults = listOf("medusa_ricasso", "medusa_blade", "saw_tip")
        MouldSlot.entries.zip(defaults).forEach { (slot, mould) ->
            state = accepted(FoundryReducer.selectMould(state, slot, mould))
        }
        state = accepted(FoundryReducer.addMetal(state, FoundryMetal.BRONZE, 28))
        state = accepted(FoundryReducer.pour(state, smithingLevel = 99))
        state = accepted(FoundryReducer.pickUpPreform(state))

        val start = requireNotNull(state.preform)
        assertEquals(FoundryTool.TRIP_HAMMER, start.stages.first())
        while (requireNotNull(state.preform).lifecycle != PreformLifecycle.Complete) {
            val preform = requireNotNull(state.preform)
            val current = preform.stages[requireNotNull(FoundryRules.hudStageIndex(preform))]
            val heatRange = FoundryRules.heatRange(preform, current.requiredHeat)
            val heat = heatRange.first + (heatRange.last - heatRange.first) / 2
            state = state.copy(preform = preform.copy(heat = heat))
            state = accepted(FoundryReducer.useTool(state, current, 0))
        }

        assertEquals(start.quality, state.preform?.quality)
        val (settled, reward) = FoundryReducer.settle(state)
        assertEquals(start.quality, (settled as FoundryTransition.Accepted).state.reputation)
        assertEquals(start.quality, reward?.reputation)
    }

    @Test
    fun serverUsesTheSameCurrentSectionThatTheNativeHudDisplays() {
        val now = 10_000L
        val stages = listOf(
            FoundryTool.TRIP_HAMMER,
            FoundryTool.GRINDSTONE,
            FoundryTool.POLISHING_WHEEL,
            FoundryTool.TRIP_HAMMER,
        )
        val preform =
            FoundryPreform(
                commissionId = 1,
                metalScore = 20,
                mouldScore = 0,
                maxQuality = 20,
                quality = 20,
                sections = stages.size,
                lifecycle = PreformLifecycle.Processing,
                heat = 100,
                progress = 500,
                stages = stages,
                activeStage = 0,
                lastHeatUpdateMillis = now,
            )

        // At 50% progress on a four-section sword, Easy Giants' Foundry reads
        // the third HUD icon: the polishing wheel. The stored counter is
        // deliberately stale to prove it cannot override the displayed plan.
        val result = FoundryReducer.useTool(FoundryState(preform = preform), FoundryTool.POLISHING_WHEEL, now)
        val updated = accepted(result).preform
        assertEquals(20, updated?.quality)
        assertEquals(510, updated?.progress)
        assertEquals(2, updated?.activeStage)
    }
    @Test
    fun nativeHudSeedsStartAfterTheBuiltInOpeningHammer() {
        val stages = listOf(
            FoundryTool.TRIP_HAMMER,
            FoundryTool.GRINDSTONE,
            FoundryTool.POLISHING_WHEEL,
            FoundryTool.TRIP_HAMMER,
            FoundryTool.GRINDSTONE,
        )

        assertEquals(stages, listOf(FoundryTool.TRIP_HAMMER) + FoundryRules.nativeHudSeeds(stages))
    }

    @Test
    fun rewardShopMappingsAndPricesMatchRevision240() {
        assertEquals((1..24).toList(), FoundryRewardShop.all.map { it.selectionId })
        assertEquals(
            listOf(27_012, 27_014, 27_017, 27_019, 27_023, 27_025, 27_027, 27_029, 27_021),
            FoundryRewardShop.physicalRewards.map { it.itemId },
        )
        assertEquals(
            listOf(2_000, 300, 15, 200, 4_000, 4_000, 3_500, 3_500, 5_000),
            FoundryRewardShop.physicalRewards.map { it.cost },
        )
        FoundryRewardShop.mouldRewards.forEach { reward ->
            assertEquals(reward.cost, FoundryMoulds.byKey.getValue(reward.mouldKey).cost)
        }
    }

    @Test
    fun rewardShopEnforcesBalanceLevelAndDuplicateMouldRules() {
        val insufficient = FoundryReducer.spendReputation(FoundryState(reputation = 14), 15)
        assertTrue(insufficient is FoundryTransition.Rejected)
        val spent = accepted(FoundryReducer.spendReputation(FoundryState(reputation = 300), 15))
        assertEquals(285, spent.reputation)

        val tooLow = FoundryReducer.unlockMould(FoundryState(reputation = 300), "stiletto_forte", 49)
        assertTrue(tooLow is FoundryTransition.Rejected)
        val unlocked = accepted(FoundryReducer.unlockMould(FoundryState(reputation = 300), "stiletto_forte", 50))
        assertEquals(0, unlocked.reputation)
        assertTrue("stiletto_forte" in unlocked.unlockedMoulds)
        assertTrue(FoundryReducer.unlockMould(unlocked, "stiletto_forte", 99) is FoundryTransition.Rejected)
    }

    private fun accepted(transition: FoundryTransition): FoundryState =
        (transition as? FoundryTransition.Accepted)?.state ?: error("Expected accepted transition: $transition")
}
