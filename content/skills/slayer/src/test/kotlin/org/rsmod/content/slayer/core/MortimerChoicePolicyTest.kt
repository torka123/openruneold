package org.rsmod.content.slayer.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.content.slayer.SlayerTaskChoiceInterface

class MortimerChoicePolicyTest {
    @Test
    fun `new players receive three choices`() {
        assertEquals(3, MortimerModifiers.choiceCount(tasksCompleted = 0))
    }

    @Test
    fun `players below the former requirement receive three choices`() {
        assertEquals(3, MortimerModifiers.choiceCount(tasksCompleted = 49))
    }

    @Test
    fun `experienced players continue to receive three choices`() {
        assertEquals(3, MortimerModifiers.choiceCount(tasksCompleted = 50))
    }

    @Test
    fun `legacy two choice offers are incomplete`() {
        assertEquals(false, MortimerModifiers.hasCompleteChoiceSet(offerCount = 2))
    }

    @Test
    fun `three saved offers are complete`() {
        assertEquals(true, MortimerModifiers.hasCompleteChoiceSet(offerCount = 3))
    }

    @Test
    fun `first dynamic row resolves to first offer`() {
        assertEquals(0, SlayerTaskChoiceInterface.resolveContentSlot(comsub = 0, offerCount = 3))
        assertEquals(0, SlayerTaskChoiceInterface.resolveContentSlot(comsub = 9, offerCount = 3))
    }

    @Test
    fun `second dynamic row resolves to second offer`() {
        assertEquals(1, SlayerTaskChoiceInterface.resolveContentSlot(comsub = 10, offerCount = 3))
        assertEquals(1, SlayerTaskChoiceInterface.resolveContentSlot(comsub = 19, offerCount = 3))
    }

    @Test
    fun `third dynamic row resolves to third offer`() {
        assertEquals(2, SlayerTaskChoiceInterface.resolveContentSlot(comsub = 20, offerCount = 3))
        assertEquals(2, SlayerTaskChoiceInterface.resolveContentSlot(comsub = 29, offerCount = 3))
    }

    @Test
    fun `dynamic children outside available offers are rejected`() {
        assertEquals(
            null,
            SlayerTaskChoiceInterface.resolveContentSlot(comsub = -1, offerCount = 3),
        )
        assertEquals(
            null,
            SlayerTaskChoiceInterface.resolveContentSlot(comsub = 30, offerCount = 3),
        )
        assertEquals(
            null,
            SlayerTaskChoiceInterface.resolveContentSlot(comsub = 20, offerCount = 2),
        )
    }
}
