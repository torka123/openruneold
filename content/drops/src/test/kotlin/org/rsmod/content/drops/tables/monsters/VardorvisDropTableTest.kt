package org.rsmod.content.drops.tables.monsters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.rsmod.api.collectionlog.CollectionLogCatalogRepository

class VardorvisDropTableTest {
    @Test
    fun `repeatable vardorvis is registered for the drop pipeline`() {
        assertTrue("npc.vardorvis" in vardorvisDropTable.npcs)
        assertEquals("Vardorvis Drops", vardorvisDropTable.tableIdentifier)
    }

    @Test
    fun `collection log catalog contains the ten canonical vardorvis uniques in order`() {
        val section = CollectionLogCatalogRepository().sections.single { it.name == "Vardorvis" }
        assertEquals(
            listOf(
                "Butch",
                "Executioner's axe head",
                "Virtus mask",
                "Virtus robe top",
                "Virtus robe bottom",
                "Ultor vestige",
                "Blood quartz",
                "Strangled tablet",
                "Chromium ingot",
                "Awakener's orb",
            ),
            section.slots.map { it.name },
        )
    }
}
