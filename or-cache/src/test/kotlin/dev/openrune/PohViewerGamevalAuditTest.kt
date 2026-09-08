package dev.openrune

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.baseVar
import dev.openrune.types.varp.bits
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PohViewerGamevalAuditTest {
    @Test
    fun `revision 240 house viewer gamevals match the live cache`() {
        ServerCacheManager.init(240)
        assertEquals(370, "interface.poh_options".asRSCM(RSCMType.INTERFACE))
        assertEquals((370 shl 16) or 1, "component.poh_options:viewer".asRSCM(RSCMType.COMPONENT))
        val viewerButton = requireNotNull(ServerCacheManager.getInterface(370)?.components?.get(1))
        assertEquals("viewer", viewerButton.internalName)
        assertEquals("Viewer", viewerButton.op.firstOrNull())

        assertEquals(422, "interface.poh_viewer".asRSCM(RSCMType.INTERFACE))
        val viewer = requireNotNull(ServerCacheManager.getInterface(422))
        val requiredComponents =
            (5..6) + (7..43) + listOf(46, 49, 50, 51, 63, 64, 65, 66, 67, 68, 69)
        requiredComponents.forEach { child ->
            requireNotNull(viewer.components[child]) {
                "Missing revision-240 poh_viewer component $child"
            }
        }
        val expected =
            mapOf(
                5329 to (1..6),
                5330 to (7..14),
                5331 to (15..16),
                5332 to (17..17),
                5333 to (18..23),
                5334 to (24..27),
                5335 to (28..31),
            )
        expected.forEach { (id, expectedBits) ->
            val varbit = requireNotNull(ServerCacheManager.getVarbit(id))
            assertEquals(780, varbit.baseVar.id, "House Viewer varbit $id base var")
            assertEquals(expectedBits, varbit.bits, "House Viewer varbit $id bit range")
        }
    }
}
