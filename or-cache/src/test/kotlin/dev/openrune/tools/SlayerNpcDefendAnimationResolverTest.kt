package dev.openrune.tools

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SlayerNpcDefendAnimationResolverTest {

    @Test
    fun `prefers the generic reaction from the same movement family`() {
        val generic =
            SlayerNpcDefendAnimationResolver.candidateScore(
                standName = "seq.skeleton_update_ready",
                npcName = "Skeleton",
                candidateName = "seq.skeleton_update_defend",
            )
        val unrelatedVariant =
            SlayerNpcDefendAnimationResolver.candidateScore(
                standName = "seq.skeleton_update_ready",
                npcName = "Skeleton",
                candidateName = "seq.skeleton_update_defend_transparent",
            )

        assertTrue(generic > unrelatedVariant)
    }

    @Test
    fun `prefers a monster-specific variant when its name identifies it`() {
        val kingBlackDragon =
            SlayerNpcDefendAnimationResolver.candidateScore(
                standName = "seq.dragon_ready",
                npcName = "King Black Dragon",
                candidateName = "seq.dragon_block_kbd",
            )
        val genericDragon =
            SlayerNpcDefendAnimationResolver.candidateScore(
                standName = "seq.dragon_ready",
                npcName = "King Black Dragon",
                candidateName = "seq.dragon_block",
            )

        assertTrue(kingBlackDragon > genericDragon)
    }

    @Test
    fun `uses the unarmed block as the generic human reaction`() {
        val unarmed =
            SlayerNpcDefendAnimationResolver.candidateScore(
                standName = "seq.human_ready",
                npcName = "Chaos druid",
                candidateName = "seq.human_unarmedblock",
            )
        val sword =
            SlayerNpcDefendAnimationResolver.candidateScore(
                standName = "seq.human_ready",
                npcName = "Chaos druid",
                candidateName = "seq.human_sword_block",
            )

        assertTrue(unarmed > sword)
    }
}
