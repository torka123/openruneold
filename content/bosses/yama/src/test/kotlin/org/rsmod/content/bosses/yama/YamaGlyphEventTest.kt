package org.rsmod.content.bosses.yama

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.rsmod.map.CoordGrid

class YamaGlyphEventTest {
    @Test
    fun `launched glyph event snapshots source target element and impact cycle`() {
        val source = CoordGrid(1501, 10076, 0)
        val originalTarget = CoordGrid(1498, 10085, 0)
        val event =
            YamaEncounterManager.PendingGlyphAttack(
                accountHash = 42L,
                sourceTile = source,
                targetTile = originalTarget,
                type = YamaGlyph.FIRE,
                impactCycle = YamaCombatTimeline.glyphAttackImpactCycle(310),
                maximum = 46,
                impactGraphic = YamaAssets.GFX_FIREBALL_IMPACT,
            )

        val laterPlayerTile = originalTarget.translate(4, -2)
        assertEquals(source, event.sourceTile)
        assertEquals(originalTarget, event.targetTile)
        assertNotEquals(laterPlayerTile, event.targetTile)
        assertEquals(YamaGlyph.FIRE, event.type)
        assertEquals(316, event.impactCycle)
        assertEquals(3255, event.impactGraphic)
    }
}
