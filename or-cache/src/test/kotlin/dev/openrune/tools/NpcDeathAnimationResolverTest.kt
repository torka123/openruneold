package dev.openrune.tools

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NpcDeathAnimationResolverTest {

    @Test
    fun `same animation family beats an unrelated death`() {
        val related =
            NpcDeathAnimationResolver.candidateScore(
                standName = "seq.demon_ready",
                npcName = "Greater demon",
                candidateName = "seq.demon_death",
            )
        val unrelated =
            NpcDeathAnimationResolver.candidateScore(
                standName = "seq.demon_ready",
                npcName = "Greater demon",
                candidateName = "seq.human_death",
            )

        assertTrue(related > unrelated)
    }

    @Test
    fun `greater demon variant beats the generic demon death`() {
        val greater =
            NpcDeathAnimationResolver.candidateScore(
                standName = "seq.demon_ready_greater",
                npcName = "Greater demon",
                candidateName = "seq.demon_death_greater",
            )
        val generic =
            NpcDeathAnimationResolver.candidateScore(
                standName = "seq.demon_ready_greater",
                npcName = "Greater demon",
                candidateName = "seq.demon_death",
            )

        assertTrue(greater > generic)
    }

    @Test
    fun `npc variant death beats another compatible family variant`() {
        val wyrm =
            NpcDeathAnimationResolver.candidateScore(
                standName = "seq.wyrm_walk",
                npcName = "Wyrm",
                candidateName = "seq.wyrm_death",
            )
        val dragon =
            NpcDeathAnimationResolver.candidateScore(
                standName = "seq.wyrm_walk",
                npcName = "Wyrm",
                candidateName = "seq.dragon_death",
            )

        assertTrue(wyrm > dragon)
    }
}
