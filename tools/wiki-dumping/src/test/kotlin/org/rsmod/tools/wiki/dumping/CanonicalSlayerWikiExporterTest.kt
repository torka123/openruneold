package org.rsmod.tools.wiki.dumping

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.rsmod.content.slayer.core.CanonicalSlayerRegistry

class CanonicalSlayerWikiExporterTest {
    @Test
    fun `exported rows match the live canonical registry exactly`() {
        val markdown = CanonicalSlayerWikiExporter.render()
        val rows = markdown.lineSequence().filter { it.matches(Regex("""\| \d+ \|.*""")) }.toList()
        assertEquals(CanonicalSlayerRegistry.categories.size, rows.size)
        for (category in CanonicalSlayerRegistry.categories) {
            assertEquals(
                1,
                rows.count { it.startsWith("| ${category.id} | ${category.key} |") },
                category.key,
            )
        }
    }

    @Test
    fun `old unrestricted master pools are not exported`() {
        assertFalse(CanonicalSlayerWikiExporter.render().contains("Konar combinations"))
    }
}
