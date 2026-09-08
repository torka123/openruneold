package org.rsmod.content.minigames.toa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToaScabarasIntegrationTest {
    @Test
    fun primaryObjectOperationsUseExplicitRevision240Bindings() {
        val expected = buildMap {
            put(
                ScabarasRevision240RuntimeLayout.sequenceCache.buttonLoc,
                ScabarasLiveObjectAction.PUSH,
            )
            put(
                ScabarasRevision240RuntimeLayout.ADDITION_TABLET_LOC,
                ScabarasLiveObjectAction.INSPECT,
            )
            ScabarasRevision240RuntimeLayout.matchingTiles.forEach { tile ->
                put(tile.hiddenButtonLoc, ScabarasLiveObjectAction.ACTIVATE)
            }
        }

        assertEquals(expected, ScabarasWorldBindings.primaryActions)
        assertEquals(11, ScabarasWorldBindings.primaryActions.size)
    }

    @Test
    fun pressurePlateUsesItsVerifiedFifthOperation() {
        assertEquals(
            mapOf(
                ScabarasRevision240RuntimeLayout.lightsCache.tileOffLoc to
                    ScabarasLiveObjectAction.FLIP
            ),
            ScabarasWorldBindings.fifthActions,
        )
        assertTrue(
            ScabarasWorldBindings.primaryActions.keys
                .intersect(ScabarasWorldBindings.fifthActions.keys)
                .isEmpty()
        )
    }
}
