package org.rsmod.content.minigames.toa

import dev.openrune.ServerCacheManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ToaInvocationCacheMappingTest {
    @Test
    fun `revision 240 invocation board mappings match the locked catalog`() {
        ToaCacheTestSupport.initRevision240()

        val partyDetails = assertNotNull(ServerCacheManager.getInterface(774))
        listOf(52, 69, 83, 84, 98).forEach { component ->
            assertNotNull(partyDetails.components[component], "interface 774:$component")
        }
        assertNotNull(ServerCacheManager.getVarbit(14541), "invocation preset selection varbit")

        val invocationEnum = assertNotNull(ServerCacheManager.getEnum(4664))
        ToaInvocationCatalog.standard.forEach { definition ->
            val row = definition.ui.row
            val structId = definition.ui.structId
            assertEquals(structId, invocationEnum.values[row], "invocation enum row $row")

            val params = assertNotNull(ServerCacheManager.getStruct(structId)?.params)
            assertEquals(definition.ui.clientInvocationId, params[1159], "client id row $row")
            assertEquals(definition.displayName, params[1160], "name row $row")
            assertEquals(definition.category.cacheParamValue, params[1161], "category row $row")
            assertEquals(definition.raidLevel, params[1162], "raid level row $row")
        }
    }
}
