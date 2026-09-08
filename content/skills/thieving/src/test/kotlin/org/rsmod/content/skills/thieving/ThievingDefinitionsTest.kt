package org.rsmod.content.skills.thieving

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ThievingDefinitionsTest {
    @Test
    fun `current OSRS pickpocket base XP values are preserved`() {
        val expected =
            mapOf(
                ThievingDefinitions.man to 8.0,
                ThievingDefinitions.farmer to 14.5,
                ThievingDefinitions.ham to 22.2,
                ThievingDefinitions.warrior to 26.0,
                ThievingDefinitions.rogue to 36.5,
                ThievingDefinitions.masterFarmer to 43.0,
                ThievingDefinitions.guard to 46.8,
                ThievingDefinitions.bandit to 79.4,
                ThievingDefinitions.knight to 84.3,
                ThievingDefinitions.menaphite to 137.5,
                ThievingDefinitions.paladin to 131.8,
                ThievingDefinitions.gnome to 133.3,
                ThievingDefinitions.hero to 163.3,
                ThievingDefinitions.vyre to 306.9,
                ThievingDefinitions.elf to 353.3,
                ThievingDefinitions.tzhaar to 103.4,
            )
        expected.forEach { (definition, xp) -> assertEquals(xp, definition.baseXp) }
    }

    @Test
    fun `success chance is blocked below requirement and guaranteed at no-fail level`() {
        for (definition in
            listOf(
                ThievingDefinitions.man,
                ThievingDefinitions.masterFarmer,
                ThievingDefinitions.elf,
            )) {
            assertEquals(0, ThievingDefinitions.successChance(definition.level - 1, definition))
            assertTrue(ThievingDefinitions.successChance(definition.level, definition) in 1..99)
            assertEquals(100, ThievingDefinitions.successChance(definition.noFailLevel, definition))
        }
    }

    @Test
    fun `rogue pieces add exactly twenty percent double-loot chance each`() {
        assertEquals(
            listOf(0, 20, 40, 60, 80, 100),
            (0..5).map(ThievingDefinitions::rogueDoubleChance),
        )
        assertEquals(100, ThievingDefinitions.rogueDoubleChance(99))
    }

    @Test
    fun `wall safe chance respects requirement stethoscope and caps`() {
        assertEquals(0, ThievingDefinitions.wallSafeChance(49, false))
        assertEquals(50, ThievingDefinitions.wallSafeChance(50, false))
        assertEquals(60, ThievingDefinitions.wallSafeChance(50, true))
        assertEquals(85, ThievingDefinitions.wallSafeChance(99, false))
        assertEquals(95, ThievingDefinitions.wallSafeChance(99, true))
    }

    @Test
    fun `supported cache names resolve to the correct profile`() {
        assertSame(ThievingDefinitions.man, ThievingDefinitions.pickpocketForName("Woman"))
        assertSame(
            ThievingDefinitions.masterFarmer,
            ThievingDefinitions.pickpocketForName("Martin the Master Gardener"),
        )
        assertSame(
            ThievingDefinitions.menaphite,
            ThievingDefinitions.pickpocketForName("Menaphite Thug"),
        )
        assertSame(ThievingDefinitions.elf, ThievingDefinitions.pickpocketForName("Glorfindel"))
        assertSame(ThievingDefinitions.tzhaar, ThievingDefinitions.pickpocketForName("TzHaar-Hur"))
    }

    @Test
    fun `every stall has valid base XP respawn and unique object mapping`() {
        val flattened = ThievingDefinitions.stalls.flatMap { it.objectIds }
        assertEquals(flattened.size, flattened.distinct().size)
        ThievingDefinitions.stalls.forEach {
            assertTrue(it.baseXp > 0.0)
            assertTrue(it.respawnTicks > 0)
            assertTrue(it.loot.isNotEmpty())
        }
    }

    @Test
    fun `weighted loot selector covers every entry boundary`() {
        val table = listOf(ThievingLoot(1, weight = 2), ThievingLoot(2, weight = 3))
        assertEquals(1, ThievingDefinitions.selectLoot(table, 0).itemId)
        assertEquals(1, ThievingDefinitions.selectLoot(table, 1).itemId)
        assertEquals(2, ThievingDefinitions.selectLoot(table, 2).itemId)
        assertEquals(2, ThievingDefinitions.selectLoot(table, 4).itemId)
    }
}
