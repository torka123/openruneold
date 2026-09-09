package org.rsmod.content.drops.tables.monsters

import dev.openrune.ServerCacheManager
import dtx.core.Single
import dtx.impl.chance.ChanceRollable
import dtx.rs.RSTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

class VorkathDropTableTest {
    @Test
    fun `default reward multiplier is exactly one`() {
        assertEquals(1.0, VORKATH_DROP_RATE_MULTIPLIER)
    }

    @Test
    fun `shark lure quantity choices are even from 70 through 110`() {
        assertEquals(70, vorkathSharkLureCounts.first())
        assertEquals(110, vorkathSharkLureCounts.last())
        assertEquals(21, vorkathSharkLureCounts.size)
        assertTrue(vorkathSharkLureCounts.all { it in 70..110 && it % 2 == 0 })
    }

    @Test
    fun `post quest Vorkath is the only registered form`() {
        assertEquals(listOf("npc.vorkath"), vorkathDropTable.npcs)
    }

    @Test
    fun `head conditions share the native collection kill counter at the fiftieth kill boundary`() {
        synchronized(ServerCacheManager) {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
        fun headAt(tableIndex: Int): DropRollItem {
            val table = vorkathDropTable.tableEntries.elementAt(tableIndex) as RSTable<*, *>
            return table.tableEntries
                .mapNotNull { entry ->
                    val rollable = (entry as? ChanceRollable<*, *>)?.rollable
                    (rollable as? Single<*, *>)?.result as? DropRollItem
                }
                .single { it.obj == "obj.vorkath_head" }
        }
        val guaranteed = headAt(0)
        val tertiary = headAt(4)
        val player = Player()
        for (kills in listOf(0, 1, 49, 50, 51, 100)) {
            VarPlayerIntMapSetter.set(player, "varp.total_vorkath_kills", kills)
            assertEquals(kills == 50, guaranteed.condition(player), "Guaranteed head at $kills KC")
            assertEquals(kills != 50, tertiary.condition(player), "Tertiary head at $kills KC")
        }
    }
}
