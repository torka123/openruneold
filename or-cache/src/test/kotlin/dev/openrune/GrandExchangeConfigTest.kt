package dev.openrune

import dev.openrune.cache.MAPS
import dev.openrune.filesystem.Cache
import dev.openrune.map.area.MapAreaDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.rsmod.map.square.MapSquareGrid
import org.rsmod.map.util.LocalMapSquareZone

class GrandExchangeConfigTest {
    @Test
    fun `native modern music table includes Trade Parade without removing Lumbridge`() {
        val rows = ServerCacheManager.getRowsForTable("dbtable.music_modern".asRSCM())
        assertTrue(rows.any { it.id == "dbrow.music_modern_lumbridge".asRSCM() })
        val ge = rows.single { it.id == "dbrow.music_modern_grand_exchange".asRSCM() }
        assertEquals("grand_exchange", ge.definedColumns().getValue(0).values!!.single())
        assertEquals(
            "dbrow.music_the_trade_parade".asRSCM(),
            ge.definedColumns().getValue(1).values!!.single(),
        )
    }

    @Test
    fun `GE music polygon covers the plaza on each level without neighboring tiles`() {
        val cache = Cache.load(Path.of(".data/cache/SERVER"))
        try {
            val area =
                MapAreaDecoder.decode(InlineByteBuf(requireNotNull(cache.data(MAPS, 12598, 7))))
            val ge = "area.grand_exchange".asRSCM().toShort()
            fun contains(x: Int, z: Int, level: Int): Boolean {
                val grid = MapSquareGrid(x and 63, z and 63, level).packed.toShort()
                val zone = LocalMapSquareZone((x and 63) / 8, (z and 63) / 8, level).packed.toByte()
                return ge in area.mapSquareAreas ||
                    area.zoneAreas[zone]?.contains(ge) == true ||
                    area.coordAreas[grid]?.contains(ge) == true
            }
            for (level in 0..3) {
                assertTrue(contains(3165, 3487, level))
                assertTrue(contains(3143, 3462, level))
                assertTrue(contains(3187, 3510, level))
                assertFalse(contains(3142, 3487, level))
                assertFalse(contains(3188, 3487, level))
                assertFalse(contains(3165, 3461, level))
                assertFalse(contains(3165, 3511, level))
            }
        } finally {
            cache.close()
        }
    }

    @Test
    fun `GE staff retain stationary configuration`() {
        val names =
            listOf(
                "ge_clerk_1",
                "ge_clerk_2",
                "ge_clerk_3",
                "ge_clerk_4",
                "ge_boss",
                "ge_expert_ores",
                "ge_expert_herbs",
                "ge_expert_runes",
                "ge_expert_logs",
                "ge_expert_combat",
                "banker1_west",
                "banker1_east",
                "banker2_east",
                "deadman_banker_grey_west",
                "bond_james_visible",
                "clan_clerk",
            )
        for (name in names) {
            val npc = requireNotNull(ServerCacheManager.getNpc("npc.$name".asRSCM()))
            assertEquals(0, npc.wanderRange, name)
            assertEquals("NoMove", npc.moveRestrict.name, name)
        }
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}
