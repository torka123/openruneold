package dev.openrune

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ClanHallInterfaceContractTest {
    @Test
    fun `native coffer controls retain their revision 240 contract`() {
        val main = requireNotNull(ServerCacheManager.getInterface(696))
        val side = requireNotNull(ServerCacheManager.getInterface(697))
        assertEquals(4341, main.components.getValue(0).onLoad?.first())
        assertEquals(4347, side.components.getValue(0).onLoad?.first())
        assertEquals("items", main.components.getValue(4).internalName)
        assertEquals("items", side.components.getValue(2).internalName)
        assertEquals(listOf("a", "b", "c", "d", "x", "all"), (7..12).map { main.components.getValue(it).internalName })
        assertEquals(listOf("a", "b", "c", "d", "x", "all"), (5..10).map { side.components.getValue(it).internalName })
    }

    @Test
    fun `native clan party drop controls and inventories retain their revision 240 contract`() {
        val main = requireNotNull(ServerCacheManager.getInterface(783))
        assertEquals(3607, main.components.getValue(0).onLoad?.first())
        assertEquals("occupiedslots", main.components.getValue(3).internalName)
        assertEquals("chest_container", main.components.getValue(5).internalName)
        assertEquals("chest_items", main.components.getValue(11).internalName)
        assertEquals("offer_items", main.components.getValue(16).internalName)
        assertEquals("confirm", main.components.getValue(17).internalName)
        assertEquals("capacity", main.components.getValue(18).internalName)
        assertEquals("216", main.components.getValue(18).text)

        val chest = requireNotNull(ServerCacheManager.getInventory(916))
        val offer = requireNotNull(ServerCacheManager.getInventory(92))
        val backpack = requireNotNull(ServerCacheManager.getInventory(93))
        println("CLAN_PARTY_INV chest=$chest offer=$offer backpack=$backpack")
        assertEquals(216, chest.size)
        assertEquals(8, offer.size)
        assertEquals(28, backpack.size)

        val sideInterfaces =
            (0..1200).filter { id ->
                ServerCacheManager.getInterface(id)?.components?.values?.any { component ->
                    component.onLoad?.contains(3617) == true
                } == true
            }
        println("CLAN_PARTY_SIDE interfaces=$sideInterfaces")
        (775..790).forEach { id ->
            println("CLAN_PARTY_NEARBY id=$id name=${RSCM.getReverseMapping(RSCMType.INTERFACE, id)}")
        }
        requireNotNull(ServerCacheManager.getInterface(266)).components.toSortedMap().forEach { (child, component) ->
            println(
                "CLAN_PARTY_SIDE_COMPONENT child=$child internal=${component.internalName} onLoad=${component.onLoad?.contentDeepToString()}"
            )
        }
        assertTrue(sideInterfaces.isNotEmpty(), "Missing partydrop_side_init interface")
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun initCache() {
            ServerCacheManager.init(240)
        }
    }
}
