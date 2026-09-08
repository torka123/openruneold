package dev.openrune.tools

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SlayerMasterMovementAnimationResolverTest {
    @Test
    fun `every affected Slayer master visual is covered`() {
        assertEquals(
            setOf(
                "npc.slayer_master_7",
                "npc.slayer_master_mortimer_vis",
                "npc.slayer_master_9_active",
                "npc.porcine_spria",
            ),
            SlayerMasterMovementAnimationResolver.targetNames,
        )
    }

    @Test
    fun `matching movement family outranks unrelated human movement`() {
        val staff =
            SlayerMasterMovementAnimationResolver.movementScore(
                "seq.human_staffready",
                "Krystilia",
                "seq.human_staffwalk",
            )
        val unrelated =
            SlayerMasterMovementAnimationResolver.movementScore(
                "seq.human_staffready",
                "Krystilia",
                "seq.barrows_repeating_crossbow_walk",
            )
        assertTrue(staff > unrelated)
    }

    @Test
    fun `Spria crossbow walk matches her ready family`() {
        val matching =
            SlayerMasterMovementAnimationResolver.movementScore(
                "seq.barrows_repeating_crossbow_ready",
                "Spria",
                "seq.barrows_repeating_crossbow_walk",
            )
        val unrelated =
            SlayerMasterMovementAnimationResolver.movementScore(
                "seq.barrows_repeating_crossbow_ready",
                "Spria",
                "seq.human_walk",
            )
        assertTrue(matching > unrelated)
    }
}
