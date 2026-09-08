package org.rsmod.content.areas.wilderness

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.rsmod.api.config.refs.CanonicalSlayerCategoryMembership

class WildernessNpcAggressionAuditTest {
    @Test
    fun `every canonical Slayer NPC has an explicit proactive or scripted category`() {
        val ids =
            CanonicalSlayerCategoryMembership.categoryIds()
                .flatMap { CanonicalSlayerCategoryMembership.npcIds(it).asIterable() }
                .toSet()
        assertTrue(ids.isNotEmpty())
        ids.forEach { npcId ->
            val behavior =
                WildernessNpcAggressionPolicy.classify(
                    npcId = npcId,
                    internalName = "npc.audit_$npcId",
                    combatLevel = 1,
                    hitpoints = 1,
                )
            assertNotEquals(
                WildernessNpcBehavior.PASSIVE_RETALIATE_ONLY,
                behavior,
                "canonical Slayer npc=$npcId was left passive",
            )
            assertNotEquals(WildernessNpcBehavior.NON_COMBAT, behavior)
        }
    }

    @Test
    fun `scripted bosses are not flattened into ordinary Slayer combatants`() {
        assertEquals(WildernessNpcBehavior.SCRIPTED_BOSS, classify(6609, "npc.callisto"))
        assertEquals(WildernessNpcBehavior.SCRIPTED_BOSS, classify(6610, "npc.venenatis"))
        assertEquals(WildernessNpcBehavior.SCRIPTED_BOSS, classify(319, "npc.corp_beast"))
        assertEquals(WildernessNpcBehavior.SCRIPTED_BOSS, classify(11246, "npc.wild_cave_superior"))
    }

    @Test
    fun `summons and transformed forms remain encounter owned`() {
        assertEquals(
            WildernessNpcBehavior.CONDITIONAL_OR_SUMMON,
            classify(999, "npc.venenatis_spiderling"),
        )
        assertEquals(
            WildernessNpcBehavior.CONDITIONAL_OR_SUMMON,
            classify(999, "npc.vetion_hellhound_snr"),
        )
        assertEquals(
            WildernessNpcBehavior.DORMANT_OR_TRANSFORMATION,
            classify(6612, "npc.vetion_2"),
        )
        assertEquals(
            WildernessNpcBehavior.DORMANT_OR_TRANSFORMATION,
            classify(11994, "npc.vetion_2_single"),
        )
    }

    @Test
    fun `non combat and unrelated combat NPCs are not made proactively aggressive`() {
        assertEquals(
            WildernessNpcBehavior.NON_COMBAT,
            WildernessNpcAggressionPolicy.classify(1, "npc.shop_keeper", 0, 0),
        )
        assertEquals(
            WildernessNpcBehavior.PASSIVE_RETALIATE_ONLY,
            classify(999, "npc.unrelated_fixture"),
        )
    }

    @Test
    fun `location scoping leaves reused IDs outside Wilderness unchanged`() {
        assertFalse(
            WildernessNpcAggressionPolicy.shouldApplyProfile(
                inWilderness = false,
                behavior = WildernessNpcBehavior.AGGRESSIVE,
            )
        )
        assertTrue(
            WildernessNpcAggressionPolicy.shouldApplyProfile(
                inWilderness = true,
                behavior = WildernessNpcBehavior.AGGRESSIVE,
            )
        )
    }

    @Test
    fun `hunt ranges are positive and bounded`() {
        assertEquals(8, WildernessNpcAggressionPolicy.boundedHuntRange(0, 1))
        assertEquals(12, WildernessNpcAggressionPolicy.boundedHuntRange(12, 1))
        assertEquals(32, WildernessNpcAggressionPolicy.boundedHuntRange(100, 50))
    }

    private fun classify(npcId: Int, internalName: String): WildernessNpcBehavior =
        WildernessNpcAggressionPolicy.classify(
            npcId,
            internalName,
            combatLevel = 100,
            hitpoints = 100,
        )
}
