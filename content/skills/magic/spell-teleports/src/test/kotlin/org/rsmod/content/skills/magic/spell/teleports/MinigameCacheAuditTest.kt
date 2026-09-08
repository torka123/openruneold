package org.rsmod.content.skills.magic.spell.teleports

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MinigameCacheAuditTest {
    @Test fun `native minigame controls map to the actual revision 240 interface`() {
        if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        for (id in 13272..13292) {
            assertNotNull(ServerCacheManager.getDbrow(id))
        }
        val type = assertNotNull(ServerCacheManager.getInterface(951))
        for (index in 1..21) {
            val packed = "component.minigames:minigame_$index".asRSCM(RSCMType.COMPONENT)
            assertEquals(951, packed ushr 16)
            assertEquals(26 - index, packed and 0xFFFF)
            assertNotNull(type.components[packed and 0xFFFF])
        }
        assertEquals(888, MinigameTeleport.timer.asRSCM(RSCMType.VARP))
    }
}
