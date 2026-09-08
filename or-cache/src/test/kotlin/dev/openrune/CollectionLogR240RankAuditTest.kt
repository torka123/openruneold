package dev.openrune

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CollectionLogR240RankAuditTest {
    @Test
    fun `revision 240 collection ranks and rewards are cache authoritative`() {
        ServerCacheManager.init(240)
        val structs = listOf(1714, 1715, 1716, 1717, 1718, 1719, 1740, 1741, 1742)
        assertEquals(
            structs.withIndex().associate { (index, structId) -> index to structId },
            requireNotNull(ServerCacheManager.getEnum(3336)).values,
        )

        val labels =
            listOf(
                "I: <col=ffffff>Bronze",
                "II: <col=ffffff>Iron",
                "III: <col=ffffff>Steel",
                "IV: <col=ffffff>Black",
                "V: <col=ffffff>Mithril",
                "VI: <col=ffffff>Adamant",
                "VII: <col=ffffff>Rune",
                "VIII: <col=ffffff>Dragon",
                "IX: <col=ffffff>Gilded",
            )
        val thresholds = listOf(100, 300, 500, 700, 900, 1000, 1100, 1200, null)
        val staves = listOf(30597, 30599, 30601, 30603, 30605, 30607, 30609, 30611, 30613)
        val colors =
            listOf(5382161, 3880757, 11772573, 0, 3491734, 1399064, 2069173, 9176857, 14931984)

        structs.forEachIndexed { index, structId ->
            val params =
                requireNotNull(requireNotNull(ServerCacheManager.getStruct(structId)).params)
            assertEquals(index, params[2230])
            assertEquals(labels[index], params[2231])
            assertEquals(thresholds[index], params[2232])
            assertEquals(if (index == 8) 1 else 0, params[2233])
            assertEquals(staves[index], params[2234])
            assertEquals(colors[index], params[2235])
        }

        val books = listOf(30579, 30581, 30583, 30585, 30587, 30589, 30591, 30593, 30595)
        val names =
            listOf(
                "bronze",
                "iron",
                "steel",
                "black",
                "mithril",
                "adamant",
                "rune",
                "dragon",
                "gilded",
            )
        names.forEachIndexed { index, name ->
            assertEquals(books[index], "obj.collection_log_$name".asRSCM(RSCMType.OBJ))
            assertEquals(staves[index], "obj.collection_staff_$name".asRSCM(RSCMType.OBJ))
        }
    }
}
