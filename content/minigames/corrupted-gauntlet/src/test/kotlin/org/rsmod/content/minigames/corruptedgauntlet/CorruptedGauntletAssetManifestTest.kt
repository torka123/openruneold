package org.rsmod.content.minigames.corruptedgauntlet

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CorruptedGauntletAssetManifestTest {
    @Test
    fun `revision 240 manifest contains the complete native encounter contract`() {
        assertEquals(setOf(7_768), CorruptedGauntletAssetManifest.mapRegions)
        assertEquals((9_035..9_048).toSet(), CorruptedGauntletAssetManifest.npcIds)
        assertTrue(
            CorruptedGauntletConfig.Loc.SINGING_BOWL in CorruptedGauntletAssetManifest.locIds
        )
        assertTrue(
            CorruptedGauntletConfig.Loc.BOSS_BARRIER in CorruptedGauntletAssetManifest.locIds
        )
        assertTrue(CorruptedGauntletConfig.Loc.SAFE_FLOOR in CorruptedGauntletAssetManifest.locIds)
        assertTrue(
            CorruptedGauntletConfig.Loc.ENABLED_ENTRANCE in CorruptedGauntletAssetManifest.locIds
        )
        assertEquals((637..641).toSet(), CorruptedGauntletAssetManifest.interfaceIds)
        assertEquals(
            (2_908..2_917).toSet() + 2_919,
            CorruptedGauntletAssetManifest.clientScriptIds.toSet(),
        )
        assertEquals(56, CorruptedGauntletAssetManifest.varbitIds.size)
    }

    @Test
    fun `run item manifest contains tools resources equipment and recovery items only`() {
        val runItems = CorruptedGauntletConfig.Obj.runOnly
        assertTrue(CorruptedGauntletConfig.Obj.SCEPTRE in runItems)
        assertTrue(CorruptedGauntletConfig.Obj.PERFECTED_BOW in runItems)
        assertTrue(CorruptedGauntletConfig.Obj.POTION_FOUR_DOSE in runItems)
        assertTrue(CorruptedGauntletConfig.Obj.ESCAPE_CRYSTAL in runItems)
        assertFalse(CorruptedGauntletUniqueReward.ENHANCED_CRYSTAL_WEAPON_SEED.itemId in runItems)
        assertFalse(CorruptedGauntletUniqueReward.YOUNGLLEF.itemId in runItems)
    }

    @Test
    fun `reference hunllef projectile and sequence values remain explicit`() {
        assertEquals(8_418, CorruptedGauntletConfig.Sequence.HUNLLEF_TORNADO_CAST)
        assertEquals(8_419, CorruptedGauntletConfig.Sequence.HUNLLEF_ATTACK)
        assertEquals(1_708, CorruptedGauntletConfig.Projectile.HUNLLEF_MAGIC)
        assertEquals(1_712, CorruptedGauntletConfig.Projectile.HUNLLEF_RANGED)
        assertEquals(1_714, CorruptedGauntletConfig.Projectile.PRAYER_DISABLE)
        assertEquals(214, CorruptedGauntletConfig.Projectile.REFERENCE_START_HEIGHT)
        assertEquals(124, CorruptedGauntletConfig.Projectile.REFERENCE_END_HEIGHT)
        assertEquals(19, CorruptedGauntletConfig.Projectile.REFERENCE_START_TIME)
        assertEquals(69, CorruptedGauntletConfig.Projectile.REFERENCE_END_TIME)
    }

    @Test
    fun `scoreboard formats durable completion times in server ticks`() {
        assertEquals("-", CorruptedGauntletScoreboard.formatTicks(null))
        assertEquals("0:00", CorruptedGauntletScoreboard.formatTicks(0))
        assertEquals("7:30", CorruptedGauntletScoreboard.formatTicks(750))
        assertEquals("12:34", CorruptedGauntletScoreboard.formatTicks(1_257))
    }
}
