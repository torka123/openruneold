package org.rsmod.content.bosses.doom

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DoomCaptureVisualContractTest {
    @Test
    fun `rock break assets match decoded revision captures`() {
        assertEquals(3384, DoomAssets.GFX_RANGED_ROCK)
        assertEquals(3385, DoomAssets.GFX_MAGIC_ROCK)
        assertEquals(3386, DoomAssets.GFX_RANGED_ROCK_IMPACT)
        assertEquals(3387, DoomAssets.GFX_MAGIC_ROCK_IMPACT)
        assertEquals(2380, DoomAssets.GFX_DEBRIS_SHADOW)
        assertEquals(3388..3395, DoomAssets.GFX_DEBRIS_START..DoomAssets.GFX_DEBRIS_END)
        assertEquals(3404, DoomAssets.GFX_DEBRIS_IMPACT)
        assertEquals(10331, DoomAssets.SOUND_ROCK_BREAK)
        assertEquals(10297, DoomAssets.SOUND_DEBRIS_IMPACT)
        assertEquals(57286, DoomAssets.ROCK)
        assertEquals(7, DoomRules.ROCK_EXPLOSION_TICKS)
        assertEquals(2, DoomRules.ROCK_SHADOW_TICKS)
    }
}
