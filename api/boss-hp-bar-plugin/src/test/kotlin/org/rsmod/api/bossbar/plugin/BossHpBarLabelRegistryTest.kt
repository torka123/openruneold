package org.rsmod.api.bossbar.plugin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BossHpBarLabelRegistryTest {
    @Test
    fun `labels are scoped to both player and target identity`() {
        val registry = BossHpBarLabelRegistry<Any>()
        val doom = Any()
        val otherBoss = Any()

        registry.put(1L, doom, "Doom of Mokhaiotl (Delve 8)")

        assertEquals("Doom of Mokhaiotl (Delve 8)", registry.get(1L, doom))
        assertNull(registry.get(2L, doom))
        assertNull(registry.get(1L, otherBoss))
    }

    @Test
    fun `a new target replaces the player's stale override`() {
        val registry = BossHpBarLabelRegistry<Any>()
        val first = Any()
        val second = Any()

        registry.put(1L, first, "First")
        registry.put(1L, second, "Second")

        assertNull(registry.get(1L, first))
        assertEquals("Second", registry.get(1L, second))
    }

    @Test
    fun `player and npc cleanup remove retained overrides`() {
        val registry = BossHpBarLabelRegistry<Any>()
        val first = Any()
        val second = Any()
        registry.put(1L, first, "First")
        registry.put(2L, second, "Second")

        registry.removeTarget(first)
        registry.removePlayer(2L)

        assertNull(registry.get(1L, first))
        assertNull(registry.get(2L, second))
    }
}
