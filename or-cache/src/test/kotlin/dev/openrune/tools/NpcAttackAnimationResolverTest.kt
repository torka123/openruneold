package dev.openrune.tools

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NpcAttackAnimationResolverTest {

    @Test
    fun `prefers attack from the same animation family`() {
        val family =
            NpcAttackAnimationResolver.candidateScore(
                standName = "seq.demon_ready",
                npcName = "Greater demon",
                candidateName = "seq.demon_attack_greater",
            )
        val unrelated =
            NpcAttackAnimationResolver.candidateScore(
                standName = "seq.demon_ready",
                npcName = "Greater demon",
                candidateName = "seq.human_unarmedpunch",
            )
        assertTrue(family > unrelated)
    }

    @Test
    fun `prefers the named npc variant within a shared family`() {
        val greater =
            NpcAttackAnimationResolver.candidateScore(
                standName = "seq.demon_ready",
                npcName = "Greater demon",
                candidateName = "seq.demon_attack_greater",
            )
        val generic =
            NpcAttackAnimationResolver.candidateScore(
                standName = "seq.demon_ready",
                npcName = "Greater demon",
                candidateName = "seq.demon_attack",
            )
        assertTrue(greater > generic)
    }

    @Test
    fun `prefers normal attack over a special variant`() {
        val normal =
            NpcAttackAnimationResolver.candidateScore(
                standName = "seq.cow_ready",
                npcName = "Cow",
                candidateName = "seq.cow_attack",
            )
        val special =
            NpcAttackAnimationResolver.candidateScore(
                standName = "seq.cow_ready",
                npcName = "Cow",
                candidateName = "seq.cow_attack_special",
            )
        assertTrue(normal > special)
    }
}
