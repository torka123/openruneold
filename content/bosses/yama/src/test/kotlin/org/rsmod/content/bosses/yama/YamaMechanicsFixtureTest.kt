package org.rsmod.content.bosses.yama

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YamaMechanicsFixtureTest {
    @Test
    fun `capture fixture is checked in and contains both supplied decoded streams`() {
        val resource =
            checkNotNull(javaClass.getResourceAsStream("/yama/rev235-mechanics-fixtures.csv"))
        val lines = resource.bufferedReader().use { it.readLines() }
        assertEquals("capture,event,tick,asset,detail", lines.first())
        assertTrue(lines.any { it.startsWith("rsprox-3386-rev235,") })
        assertTrue(lines.any { it.startsWith("rsprox-3347-rev235,") })
        assertTrue(lines.any { ",shadow_crash_bow_tie," in it && ",3252," in it })
        assertFalse(lines.any { "Downloads" in it || "C:\\Users" in it })
    }

    @Test
    fun `fixture proves moving fronts and three tick crash cadence`() {
        val text =
            checkNotNull(javaClass.getResource("/yama/rev235-mechanics-fixtures.csv")).readText()
        assertTrue("three telegraph ticks" in text)
        assertTrue("running speed" in text)
        assertTrue("three server ticks apart" in text)
    }
}
