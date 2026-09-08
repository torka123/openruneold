package org.rsmod.content.minigames.pestcontrol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PestControlRulesTest {
    @Test
    fun noviceIsTheOnlyEnabledLanderAndStartsWithOnePlayer() {
        assertEquals(40, PestControlLander.minimumCombat)
        assertEquals(200, PestControlLander.portalHitpoints)
        assertEquals("loc.pest_lander_gangplank", PestControlLander.gangplank)
        assertEquals("loc.pest_lander_ladder", PestControlLander.ladder)
        assertEquals(1, PestControlTuning.minimumPlayers)
        assertEquals(25, PestControlTuning.queueWaitTicks)
        assertEquals(
            setOf("loc.pest_lander_gangplank_2", "loc.pest_lander_gangplank_3"),
            DisabledPestControlLanders.gangplanks,
        )
        assertEquals(
            setOf("loc.pest_lander_ladder_2", "loc.pest_lander_ladder_3"),
            DisabledPestControlLanders.ladders,
        )
        assertEquals(
            setOf("npc.pest_squire_lander_2", "npc.pest_squire_lander_3"),
            DisabledPestControlLanders.squires,
        )
    }

    @Test
    fun osrsRewardKnightsAndEquipmentPricesAreConfigured() {
        assertEquals(
            setOf(
                "npc.pest_voidknight_1",
                "npc.pest_voidknight_2",
                "npc.pest_voidknight_3",
                "npc.pest_voidknight_4",
            ),
            PestControlRewardNpcs.voidKnights,
        )
        assertEquals(
            mapOf(
                "obj.pest_void_knight_top" to 250,
                "obj.pest_void_knight_robes" to 250,
                "obj.pest_void_knight_gloves" to 150,
                "obj.pest_void_knight_mace" to 250,
                "obj.game_pest_melee_helm" to 200,
                "obj.game_pest_archer_helm" to 200,
                "obj.game_pest_mage_helm" to 200,
                "obj.pest_seal_8" to 10,
            ),
            PestControlRewardService.SHOP.associate { it.internal to it.cost },
        )
    }

    @Test
    fun revision240ConfirmSelectionIdsMapToTheExactShopRewards() {
        assertEquals(
            mapOf(
                24 to "obj.pest_void_knight_mace",
                25 to "obj.pest_void_knight_top",
                26 to "obj.pest_void_knight_robes",
                27 to "obj.pest_void_knight_gloves",
                28 to "obj.game_pest_mage_helm",
                29 to "obj.game_pest_archer_helm",
                30 to "obj.game_pest_melee_helm",
                31 to "obj.pest_seal_8",
            ),
            PestControlRewardService.SHOP_BY_SELECTION.mapValues { it.value.internal },
        )
        assertFalse(PestControlRewardService.SHOP_BY_SELECTION.containsKey(23))
        assertFalse(PestControlRewardService.SHOP_BY_SELECTION.containsKey(32))
    }

    @Test
    fun onePlayerQueueLaunchesAfterFifteenSecondCountdown() {
        val queue = PestControlQueue<String>()

        assertEquals(QueueJoinResult.Joined, queue.join("solo", 100))
        assertEquals(15, queue.secondsRemaining(100))
        assertFalse(queue.ready(124))
        assertTrue(queue.ready(125))
        assertEquals(listOf("solo"), queue.snapshot())
    }

    @Test
    fun queueRejectsDuplicatesCapsAndResetsItsCountdownWhenEmpty() {
        val queue = PestControlQueue<String>(minimumPlayers = 1, maximumPlayers = 2, waitTicks = 5)

        assertEquals(QueueJoinResult.Joined, queue.join("a", 10))
        assertEquals(QueueJoinResult.Duplicate, queue.join("a", 10))
        assertEquals(QueueJoinResult.Joined, queue.join("b", 10))
        assertEquals(QueueJoinResult.Full, queue.join("c", 10))
        assertTrue(queue.leave("a"))
        assertTrue(queue.leave("b"))
        assertEquals(QueueJoinResult.Joined, queue.join("c", 20))
        assertFalse(queue.ready(24))
        assertTrue(queue.ready(25))
    }

    @Test
    fun arenaContainsExactlyFourActiveRevision240PortalDefinitions() {
        assertEquals(
            listOf(
                "npc.pest_portal_1_active",
                "npc.pest_portal_2_active",
                "npc.pest_portal_3_active",
                "npc.pest_portal_4_active",
            ),
            PortalColor.entries.map { it.activeNpc },
        )
        assertEquals(4, PortalColor.entries.size)
        assertEquals(4, PortalColor.entries.map { it.sourceCoord }.distinct().size)
    }

    @Test
    fun matchOverlayKeepsOnlyPortalIconsAndTheirHealthValuesVisible() {
        val healthComponents = PortalColor.entries.map { it.healthComponent }
        val visibleComponents = PestControlOverlay.portalIcons + healthComponents

        assertEquals(4, PestControlOverlay.portalIcons.size)
        assertEquals(
            listOf(
                "component.pest_status_overlay:pest_status_porttxt1",
                "component.pest_status_overlay:pest_status_porttxt2",
                "component.pest_status_overlay:pest_status_porttxt3",
                "component.pest_status_overlay:pest_status_porttxt4",
            ),
            healthComponents,
        )
        assertEquals(8, visibleComponents.distinct().size)
        assertTrue(PestControlOverlay.hiddenComponents.none { it in visibleComponents })
    }

    @Test
    fun arenaGateDetectionRequiresAnOpenActionInAnyCacheSlot() {
        assertTrue(
            PestControlRules.isArenaGate(
                displayName = "Gate",
                internalName = "loc.pest_gate_closed",
                actions = listOf("Open", null, null, null, null),
            )
        )
        assertFalse(
            PestControlRules.isArenaGate(
                displayName = "Wall",
                internalName = "loc.pest_wall",
                actions = listOf("Open", null, null, null, null),
            )
        )
        assertFalse(
            PestControlRules.isArenaGate(
                displayName = "Gate",
                internalName = "loc.pest_gate_closed",
                actions = listOf("Close", null, null, null, null),
            )
        )
    }

    @Test
    fun allPortalsCanBeAttackedImmediatelyAndNeverUseShieldState() {
        assertTrue(PestControlRules.portalCanTakeDamage(destroyed = false, running = true))
        assertFalse(PestControlRules.portalCanTakeDamage(destroyed = true, running = true))
        assertFalse(PestControlRules.portalCanTakeDamage(destroyed = false, running = false))
    }

    @Test
    fun uniquePortalDeathsFinishOnlyOnTheFourthPortal() {
        val tracker = PestControlPortalTracker()

        val first = tracker.markDestroyed(PortalColor.Purple)
        val second = tracker.markDestroyed(PortalColor.Blue)
        val third = tracker.markDestroyed(PortalColor.Yellow)
        val fourth = tracker.markDestroyed(PortalColor.Red)

        assertEquals(1, first.destroyedCount)
        assertFalse(first.complete)
        assertEquals(2, second.destroyedCount)
        assertFalse(second.complete)
        assertEquals(3, third.destroyedCount)
        assertFalse(third.complete)
        assertEquals(4, fourth.destroyedCount)
        assertTrue(fourth.complete)
    }

    @Test
    fun duplicateFinalDeathCannotAdvanceOrCompleteTwice() {
        val tracker = PestControlPortalTracker()
        PortalColor.entries.forEach { tracker.markDestroyed(it) }

        val duplicate = tracker.markDestroyed(PortalColor.Red)

        assertFalse(duplicate.newlyDestroyed)
        assertEquals(4, duplicate.destroyedCount)
        assertTrue(duplicate.complete)
    }

    @Test
    fun simultaneousInstancesNeverSharePortalProgress() {
        val first = PestControlPortalTracker()
        val second = PestControlPortalTracker()

        PortalColor.entries.forEach { first.markDestroyed(it) }
        second.markDestroyed(PortalColor.Purple)

        assertEquals(4, first.destroyedCount)
        assertEquals(1, second.destroyedCount)
    }

    @Test
    fun portalVictoryBeatsTheSafetyTimeoutAndTimeoutNeverWinsEarly() {
        assertEquals(PestControlResult.Won, PestControlResultArbiter.decide(4, true))
        assertEquals(PestControlResult.Won, PestControlResultArbiter.decide(4, false))
        assertEquals(PestControlResult.Lost, PestControlResultArbiter.decide(3, true))
        assertNull(PestControlResultArbiter.decide(3, false))
    }

    @Test
    fun aWinAddsExactlyFivePersistentPointsWithoutChangingTheExistingCap() {
        assertEquals(5, PC_REWARD_POINTS)
        assertEquals(5, PestControlRules.cappedPoints(0))
        assertEquals(105, PestControlRules.cappedPoints(100))
        assertEquals(4_000, PestControlRules.cappedPoints(3_999))
        assertEquals(4_000, PC_POINT_CAP)
        assertEquals("pest_control_commendation_points", PEST_CONTROL_POINTS.persistenceKey)
        assertEquals("varp.if1", PestControlRewardShop.pointsVar)
        assertEquals("component.pest_rewardshop:confirm", PestControlRewardShop.confirmComponent)
        assertEquals(1_500, PC_ADMIN_TEST_POINTS)
        assertEquals(0, PestControlRules.normalizePoints(-1))
        assertEquals(1_500, PestControlRules.normalizePoints(1_500))
        assertEquals(4_000, PestControlRules.normalizePoints(5_000))
    }

    @Test
    fun threeConsecutiveGamesEachBeginAtZeroAndFinishAtFour() {
        repeat(3) {
            val tracker = PestControlPortalTracker()
            assertEquals(0, tracker.destroyedCount)
            PortalColor.entries.forEach { tracker.markDestroyed(it) }
            assertEquals(4, tracker.destroyedCount)
        }
    }

    @Test
    fun arenaEntryBelongsToTheCopiedSourceRegion() {
        assertEquals(
            PC_SOURCE_REGION,
            (PestControlCoords.playerEntry.mx shl 8) or PestControlCoords.playerEntry.mz,
        )
    }

    @Test
    fun stateFlowContainsOnlyPortalGameLifecycleStates() {
        assertEquals(
            listOf("STARTING", "RUNNING", "WON", "LOST", "REWARDING", "CLEANING_UP", "CLOSED"),
            PestControlState.entries.map { it.name },
        )
    }
}
