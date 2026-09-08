package dev.openrune

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class PerilousMoonR240CacheAuditTest {
    @Test
    fun `revision 240 resolves every active Perilous Moon npc semantically`() {
        ServerCacheManager.init(240)

        val npcs =
            mapOf(
                "npc.pmoon_boss_blood_moon" to Triple(13016, 5, 500),
                "npc.pmoon_boss_blue_moon" to Triple(13017, 5, 500),
                "npc.pmoon_boss_eclipse_moon" to Triple(13018, 5, 500),
                "npc.pmoon_boss_threat_circle" to Triple(13015, 2, 1),
                "npc.pmoon_boss_jaguar" to Triple(13021, 2, 100),
                "npc.pmoon_boss_icicle_1" to Triple(13022, 2, 100),
                "npc.pmoon_boss_icicle_2" to Triple(13023, 2, 100),
                "npc.pmoon_boss_icicle_3" to Triple(13024, 2, 100),
                "npc.pmoon_boss_winter_storm" to Triple(13027, 1, 1),
                "npc.pmoon_boss_eclipse_moon_shield" to Triple(13020, 2, 1),
                "npc.pmoon_boss_eclipse_clone" to Triple(13019, 5, 500),
            )
        npcs.forEach { (name, expected) ->
            val id = name.asRSCM(RSCMType.NPC)
            val type = requireNotNull(ServerCacheManager.getNpc(id)) { name }
            assertNotNull(type)
            assertEquals(name, type.internalName)
            assertEquals(expected.first, id, name)
            assertEquals(expected.second, type.size, name)
            assertEquals(expected.third, type.hitpoints, name)
        }
    }
}
