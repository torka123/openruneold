package org.rsmod.content.bosses.doom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DoomEncounterRuntimeRulesTest {
    @Test
    fun `shield depletion finishes the shield phase instead of killing the encounter`() {
        assertEquals(
            DoomBossDeathQueueAction.FINISH_SHIELD,
            DoomEncounterRuntimeRules.bossDeathQueueAction(DoomState.SHIELDED, hitpoints = 0),
        )
        assertEquals(
            DoomBossDeathQueueAction.IGNORE,
            DoomEncounterRuntimeRules.bossDeathQueueAction(DoomState.ACTIVE, hitpoints = 1),
        )
        assertEquals(
            DoomBossDeathQueueAction.ENCOUNTER_DEATH,
            DoomEncounterRuntimeRules.bossDeathQueueAction(DoomState.ACTIVE, hitpoints = 0),
        )
    }

    @Test
    fun `completed shield beam exits to the next captured phase`() {
        assertTrue(
            DoomEncounterRuntimeRules.shouldFinishShieldPhaseAfterBeam(
                firingState = DoomState.SHIELDED,
                currentState = DoomState.SHIELDED,
                bossAssigned = true,
            )
        )
        assertFalse(
            DoomEncounterRuntimeRules.shouldFinishShieldPhaseAfterBeam(
                firingState = DoomState.SHIELDED,
                currentState = DoomState.DYING,
                bossAssigned = true,
            )
        )
        assertFalse(
            DoomEncounterRuntimeRules.shouldFinishShieldPhaseAfterBeam(
                firingState = DoomState.SHIELDED,
                currentState = DoomState.SHIELDED,
                bossAssigned = false,
            )
        )
        assertFalse(
            DoomEncounterRuntimeRules.shouldFinishShieldPhaseAfterBeam(
                firingState = DoomState.MELEE_CHARGE,
                currentState = DoomState.SHIELDED,
                bossAssigned = true,
            )
        )
    }

    @Test
    fun `delayed style hits require accuracy unless explicitly marked guaranteed`() {
        assertNull(
            DoomEncounterRuntimeRules.delayedStyleDamageRange(
                maxHit = 65,
                guaranteedAccuracy = false,
                accurate = false,
            )
        )
        assertEquals(
            0..65,
            DoomEncounterRuntimeRules.delayedStyleDamageRange(
                maxHit = 65,
                guaranteedAccuracy = false,
                accurate = true,
            ),
        )
        assertEquals(
            0..65,
            DoomEncounterRuntimeRules.delayedStyleDamageRange(
                maxHit = 65,
                guaranteedAccuracy = true,
                accurate = false,
            ),
        )
    }

    @Test
    fun `acid accepts its sentinel once and never damages during startup`() {
        assertFalse(
            DoomEncounterRuntimeRules.acidHitAllowed(
                state = DoomState.STARTING,
                currentCycle = 100,
                lastHitCycle = Int.MIN_VALUE,
            )
        )
        assertTrue(
            DoomEncounterRuntimeRules.acidHitAllowed(
                state = DoomState.ACTIVE,
                currentCycle = 100,
                lastHitCycle = Int.MIN_VALUE,
            )
        )
        assertFalse(
            DoomEncounterRuntimeRules.acidHitAllowed(
                state = DoomState.ACTIVE,
                currentCycle = 100,
                lastHitCycle = 100,
            )
        )
        assertTrue(
            DoomEncounterRuntimeRules.acidHitAllowed(
                state = DoomState.ACTIVE,
                currentCycle = 101,
                lastHitCycle = 100,
            )
        )
    }

    @Test
    fun `acid exists and damages only during normal or melee-charge combat`() {
        assertTrue(DoomEncounterRuntimeRules.acidPhaseAllowed(DoomState.ACTIVE))
        assertTrue(DoomEncounterRuntimeRules.acidPhaseAllowed(DoomState.MELEE_CHARGE))

        listOf(
                DoomState.STARTING,
                DoomState.SHIELD_TRANSITION,
                DoomState.SHIELDED,
                DoomState.SHOCKWAVE,
                DoomState.BURROWING,
                DoomState.DYING,
                DoomState.DEFEATED,
                DoomState.INVALIDATED,
            )
            .forEach { state ->
                assertFalse(
                    DoomEncounterRuntimeRules.acidPhaseAllowed(state),
                    "Acid must be inactive during $state.",
                )
                assertFalse(
                    DoomEncounterRuntimeRules.acidHitAllowed(
                        state = state,
                        currentCycle = 101,
                        lastHitCycle = 100,
                    ),
                    "Existing acid must not damage during $state.",
                )
            }
    }

    @Test
    fun `burrow beam restarts only while the live car phase remains active`() {
        assertTrue(
            DoomEncounterRuntimeRules.shouldRestartBurrowCharge(
                firingState = DoomState.BURROWING,
                currentState = DoomState.BURROWING,
                bossAssigned = true,
            )
        )
        assertFalse(
            DoomEncounterRuntimeRules.shouldRestartBurrowCharge(
                firingState = DoomState.BURROWING,
                currentState = DoomState.DYING,
                bossAssigned = true,
            )
        )
        assertFalse(
            DoomEncounterRuntimeRules.shouldRestartBurrowCharge(
                firingState = DoomState.BURROWING,
                currentState = DoomState.BURROWING,
                bossAssigned = false,
            )
        )
    }

    @Test
    fun `moving shield remains for the endpoint protection tick`() {
        assertFalse(
            DoomEncounterRuntimeRules.shouldDespawnMovingShield(
                currentCycle = 200,
                endpointCycle = 200,
            )
        )
        assertFalse(
            DoomEncounterRuntimeRules.shouldDespawnMovingShield(
                currentCycle = 201,
                endpointCycle = 200,
            )
        )
        assertTrue(
            DoomEncounterRuntimeRules.shouldDespawnMovingShield(
                currentCycle = 202,
                endpointCycle = 200,
            )
        )
        assertFalse(
            DoomEncounterRuntimeRules.shouldDespawnMovingShield(
                currentCycle = 201,
                endpointCycle = Int.MAX_VALUE,
            )
        )
    }
}
