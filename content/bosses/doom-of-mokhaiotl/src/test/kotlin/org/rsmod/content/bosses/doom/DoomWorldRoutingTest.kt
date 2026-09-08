package org.rsmod.content.bosses.doom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DoomWorldRoutingTest {
    @Test
    fun `outside entrance and arena exit route to opposite lifecycle actions`() {
        assertEquals(DoomGapAction.ENTER, DoomGapRouting.actionFor(57289))
        assertEquals(DoomGapAction.QUICK_ESCAPE, DoomGapRouting.actionFor(57290))
    }

    @Test
    fun `unrelated locations are not Doom gap routes`() {
        assertNull(DoomGapRouting.actionFor(DoomAssets.BURROW_HOLE))
    }

    @Test
    fun `first delve moves before transition and opens captured prompt on tick three`() {
        val transition = DoomEntryTransition.FIRST_DELVE

        assertEquals("You jump the gap...", transition.promptText)
        assertEquals(0, transition.createRunCycle)
        assertEquals(3, DoomEntryTransition.INTERACTIVE_PROMPT_CYCLE)
        assertEquals(50, DoomEntryTransition.FADE_CLIENT_DURATION)
    }

    @Test
    fun `deeper delve waits one full cleanup tick before creating the next run`() {
        val transition = DoomEntryTransition.DEEPER_DELVE

        assertEquals("You jump further into the burrow...", transition.promptText)
        assertEquals(2, transition.createRunCycle)
        assertEquals(3, DoomEntryTransition.INTERACTIVE_PROMPT_CYCLE)
        assertEquals(50, DoomEntryTransition.FADE_CLIENT_DURATION)
    }

    @Test
    fun `entry landing uses capture backed first and deeper delve tiles`() {
        assertEquals(23, doomEntryLocalZ(1))
        assertEquals(30, doomEntryLocalZ(2))
        assertEquals(30, doomEntryLocalZ(8))
    }
}
