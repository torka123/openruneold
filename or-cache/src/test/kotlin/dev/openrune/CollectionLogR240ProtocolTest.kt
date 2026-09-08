package dev.openrune

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class CollectionLogR240ProtocolTest {
    @Test
    fun `revision 240 collection protocol ids are stable`() {
        ServerCacheManager.init(240)

        assertEquals(621, "interface.collection".asRSCM(RSCMType.INTERFACE))
        assertEquals(908, "interface.collection_overview".asRSCM(RSCMType.INTERFACE))
        assertNotNull(ServerCacheManager.getInventory(620))
        assertEquals(22711, "obj.collection_log".asRSCM(RSCMType.OBJ))
        assertEquals(2945, "varp.collection_general".asRSCM(RSCMType.VARP))

        val collection = requireNotNull(ServerCacheManager.getInterface(621))
        assertEquals("search_results_background", collection.components[84]?.internalName)
        assertEquals("search_results_clickzone", collection.components[86]?.internalName)

        val collector = requireNotNull(ServerCacheManager.getNpc(8491))
        assertEquals("Talk-to", collector.actions.getOpOrNull(0))
        assertEquals("Get-log", collector.actions.getOpOrNull(2))
        assertEquals("Claim-log-rewards", collector.actions.getOpOrNull(3))

        val book = requireNotNull(ServerCacheManager.getItem(22711))
        assertEquals(
            listOf(null, "Wield", "Read", "Collections overview", "Destroy"),
            book.interfaceOptions,
        )
        val bronzeStaff = requireNotNull(ServerCacheManager.getItem(30597))
        assertEquals("Create-book", bronzeStaff.interfaceOptions[2])
    }
}
