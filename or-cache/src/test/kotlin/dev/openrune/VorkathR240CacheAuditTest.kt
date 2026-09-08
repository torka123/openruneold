package dev.openrune

import dev.openrune.filesystem.Cache
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Reads the active revision-240 server definitions and native LIVE visual assets; never packs assets. */
class VorkathR240CacheAuditTest {
    @Test
    fun `native Vorkath forms sequences and projectile models exist in revision 240`() {
        ServerCacheManager.init(240)

        assertEquals(8061, "npc.vorkath".asRSCM(RSCMType.NPC))
        val active = requireNotNull(ServerCacheManager.getNpc(8061))
        assertEquals("Vorkath", active.name)
        assertEquals(750, active.hitpoints)
        assertEquals(7, active.size)
        assertEquals("Attack", active.actions.getOpOrNull(1))
        for (id in listOf(8058, 8059, 8061, 8063)) {
            assertNotNull(ServerCacheManager.getNpc(id), "Missing native NPC $id")
        }
        assertEquals("Zombified Spawn", requireNotNull(ServerCacheManager.getNpc(8063)).name)
        assertNotNull(ServerCacheManager.getObject(32000), "Missing native acid pool")

        val sequences = mapOf(
            "seq.ds2_vorkath_idle" to 7948,
            "seq.ds2_vorkath_death" to 7949,
            "seq.ds2_vorkath_spawn" to 7950,
            "seq.ds2_vorkath_attack_melee" to 7951,
            "seq.ds2_vorkath_ranged" to 7952,
            "seq.ds2_vorkath_acid" to 7957,
            "seq.ds2_vorkath_ranged_up" to 7960,
            "seq.ds2_spawn_defend" to 7889,
            "seq.ds2_spawn_attack" to 7890,
            "seq.ds2_spawn_death" to 7891,
        )
        for ((alias, id) in sequences) {
            assertEquals(id, alias.asRSCM(RSCMType.SEQ), alias)
            assertNotNull(ServerCacheManager.getAnim(id), "Missing native sequence $id ($alias)")
        }
        println("Vorkath revision-240 sequence tick durations: " +
            sequences.values.associateWith { requireNotNull(ServerCacheManager.getAnim(it)).tickDuration })

        // Captured travels, impacts, freeze, rapid blast, spawn blast and Crumble Undead.
        val spots = listOf(
            393, 395, 1470, 1471, 1472, 1473,
            1477, 1478, 1479, 1480, 1481, 1482, 1483, 1484,
            369, 1466, 131, 1460, 145, 146, 147,
        )
        val live = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            for (id in spots) {
                val spot = requireNotNull(ServerCacheManager.getSpotAnimation(id)) {
                    "Missing native spotanim $id"
                }
                assertTrue(live.data(7, spot.modelId)?.isNotEmpty() == true,
                    "Missing native model ${spot.modelId} for Vorkath spotanim $id")
                if (spot.animationId >= 0) {
                    assertNotNull(ServerCacheManager.getAnim(spot.animationId),
                        "Missing linked native sequence ${spot.animationId} for spotanim $id")
                }
            }
        } finally {
            live.close()
        }
    }
}
