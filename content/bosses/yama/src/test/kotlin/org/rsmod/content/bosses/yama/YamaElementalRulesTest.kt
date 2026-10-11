package org.rsmod.content.bosses.yama

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YamaElementalRulesTest {
    @Test
    fun `glyph attacks alternate independently of standard attack style`() {
        assertEquals(YamaGlyph.SHADOW, YamaRules.nextGlyphAttack(YamaGlyph.FIRE))
        assertEquals(YamaGlyph.FIRE, YamaRules.nextGlyphAttack(YamaGlyph.SHADOW))
    }

    @Test
    fun `revision 240 glyph objects preserve their visible element`() {
        assertEquals(56336, YamaAssets.activeGlyph(YamaGlyph.FIRE))
        assertEquals(56339, YamaAssets.deactivatingGlyph(YamaGlyph.FIRE))
        assertEquals(56335, YamaAssets.activeGlyph(YamaGlyph.SHADOW))
        assertEquals(56338, YamaAssets.deactivatingGlyph(YamaGlyph.SHADOW))
    }

    @Test
    fun `only matching elemental protection absorbs an attack`() {
        assertTrue(YamaRules.glyphProtects(YamaGlyph.FIRE, YamaGlyph.FIRE))
        assertFalse(YamaRules.glyphProtects(YamaGlyph.SHADOW, YamaGlyph.FIRE))
        assertTrue(YamaRules.glyphProtects(YamaGlyph.SHADOW, YamaGlyph.SHADOW))
        assertFalse(YamaRules.glyphProtects(YamaGlyph.FIRE, YamaGlyph.SHADOW))
    }

    @Test
    fun `glyph majority seeds the captured opening styles`() {
        assertEquals(
            YamaAttackStyle.RANGED,
            YamaRules.nextAlternatingStandardStyle(YamaRules.phaseOneStyleSeed(true)),
        )
        assertEquals(
            YamaAttackStyle.MAGIC,
            YamaRules.nextAlternatingStandardStyle(YamaRules.phaseOneStyleSeed(false)),
        )
        assertEquals(
            YamaAttackStyle.MAGIC,
            YamaRules.nextAlternatingStandardStyle(YamaRules.phaseThreeStyleSeed(true)),
        )
        assertEquals(
            YamaAttackStyle.RANGED,
            YamaRules.nextAlternatingStandardStyle(YamaRules.phaseThreeStyleSeed(false)),
        )
    }

    @Test
    fun `magic and Scorching bow force maximum damage against a Voidflare`() {
        assertTrue(YamaRules.flareForcesMaximum(YamaAttackStyle.MAGIC, scorchingBow = false))
        assertTrue(YamaRules.flareForcesMaximum(YamaAttackStyle.RANGED, scorchingBow = true))
        assertFalse(YamaRules.flareForcesMaximum(YamaAttackStyle.RANGED, scorchingBow = false))
    }

    @Test
    fun `a fully glyph-protected flare explosion does not heal Yama`() {
        assertFalse(YamaRules.flareShouldHeal(unabsorbedPlayers = 0))
        assertTrue(YamaRules.flareShouldHeal(unabsorbedPlayers = 1))
    }
}
