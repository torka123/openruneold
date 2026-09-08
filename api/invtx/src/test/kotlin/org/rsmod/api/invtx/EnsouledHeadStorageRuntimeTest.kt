@file:OptIn(dev.openrune.types.util.UncheckedType::class)

package org.rsmod.api.invtx

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.obj.EnsouledHeadProvenance
import org.rsmod.map.CoordGrid

class EnsouledHeadStorageRuntimeTest {
    @Test fun `all head types lose origin on bank or economic offer insertion and retain that state after return`() {
        for (container in listOf("inv.bank", "inv.tradeoffer")) {
            for (id in EnsouledHeadProvenance.freshIds) {
                val held = Inventory.create("inv.inv")
                val stored = Inventory.create(container)
                held[0] = fresh(id)
                val result = transactions.transaction(true) {
                    transfer(select(held), 0, 1, select(stored))
                }
                assertTrue(result.success, "$container $id $result")
                assertNull(held[0])
                val head = assertNotNull(stored.firstOrNull { it != null })
                assertEquals(id + 1, head.id)
                assertEquals(1, head.count)
                assertEquals(0, head.vars)
                assertFalse(EnsouledHeadProvenance.canReanimateAt(head.id, head.vars, origin))
                val returned = transactions.transaction(true) {
                    transfer(select(stored), stored.indexOfFirst { it != null }, 1, select(held))
                }
                assertTrue(returned.success)
                assertEquals(head, held.firstOrNull { it != null })
            }
        }
    }

    @Test fun `partial quantity merges into existing bank stack and preserves untransferred origin`() {
        val held = Inventory.create("inv.inv")
        val bank = Inventory.create("inv.bank")
        held[0] = fresh(13447)
        held[1] = fresh(13447, origin.translate(4, 0))
        bank[0] = InvObj(13448, 7)
        val remaining = held[1]
        val result = transactions.transaction(true) {
            transfer(select(held), 0, 1, select(bank))
        }
        assertTrue(result.success)
        assertEquals(InvObj(13448, 8), bank[0])
        assertEquals(1, bank.occupiedSpace())
        assertNull(held[0])
        assertEquals(remaining, held[1])
    }

    @Test fun `full destination rolls back without stripping fresh eligibility`() {
        for (container in listOf("inv.bank", "inv.tradeoffer")) {
            val held = Inventory.create("inv.inv")
            val stored = Inventory.create(container)
            held[0] = fresh(13447)
            for (slot in stored.indices) stored[slot] = InvObj(995, 1)
            val previous = held[0]
            val result = transactions.transaction(true) {
                transfer(select(held), 0, 1, select(stored))
            }
            assertFalse(result.success)
            assertEquals(previous, held[0])
            assertTrue(EnsouledHeadProvenance.canReanimateAt(previous!!.id, previous.vars, origin))
            assertTrue(stored.all { it?.id == 995 })
        }
    }

    @Test fun `deposit all merges different fresh origins and ordinary items without affecting source on uncommitted preview`() {
        val held = Inventory.create("inv.inv")
        val bank = Inventory.create("inv.bank")
        held[0] = fresh(13447)
        held[1] = fresh(13447, origin.translate(2, 0))
        held[2] = InvObj(13447, 1) // A legacy fresh head still normalizes without proven origin.
        held[3] = InvObj(995, 100)
        val preview = transactions.transaction(false) { moveAll(select(held), select(bank)) }
        assertTrue(preview.success)
        assertEquals(4, held.occupiedSpace())
        assertTrue(bank.isEmpty())
        preview.commitAll()
        assertTrue(held.isEmpty())
        assertEquals(3, bank.filterNotNull { it.id == 13448 }.sumOf { it.count })
        assertEquals(100, bank.filterNotNull { it.id == 995 }.sumOf { it.count })
        assertTrue(bank.filterNotNull { it.id == 13448 }.all { it.vars == 0 })
    }

    @Test fun `ordinary inventory transfer preserves fresh origin`() {
        val held = Inventory.create("inv.inv")
        val other = Inventory.create("inv.inv")
        held[0] = fresh(13447)
        val original = held[0]
        val result = transactions.transaction(true) { transfer(select(held), 0, 1, select(other)) }
        assertTrue(result.success)
        assertEquals(original, other[0])
    }

    companion object {
        private val origin = CoordGrid(3200, 3200, 0)
        private lateinit var transactions: InvTransactions
        private fun fresh(id: Int, coords: CoordGrid = origin) =
            EnsouledHeadProvenance.fromMonsterDrop(InvObj(id, 1), coords)

        @JvmStatic @BeforeAll fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
            transactions = InvTransactions.from()
        }
    }
}
