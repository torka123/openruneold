package dev.openrune

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CollectionLogR240CacheAuditTest {
    @Test
    fun `dump revision 240 collection log protocol`() {
        ServerCacheManager.init(240)

        val interfaceId = "interface.collection".asRSCM(RSCMType.INTERFACE)
        assertEquals(621, interfaceId)
        val type = requireNotNull(ServerCacheManager.getInterface(interfaceId))
        println("COLLECTION_INTERFACE id=$interfaceId components=${type.components.size}")
        type.components.toSortedMap().forEach { (child, component) ->
            println(
                "COLLECTION_COMPONENT child=$child internal=${component.internalName} " +
                    "type=${component.javaClass.simpleName} ops=${component.op} " +
                    "onLoad=${component.onLoad?.toList()}"
            )
        }

        val categories = listOf(471, 472, 473, 474, 475)
        categories.forEach { structId ->
            val struct = requireNotNull(ServerCacheManager.getStruct(structId))
            println("COLLECTION_CATEGORY struct=$structId params=${struct.params}")
            val sectionEnum = struct.params?.get(683) as? Int ?: return@forEach
            val sections = requireNotNull(ServerCacheManager.getEnum(sectionEnum))
            println("COLLECTION_SECTIONS enum=$sectionEnum values=${sections.values}")
            sections.values.toSortedMap().forEach { (order, sectionValue) ->
                val sectionStructId = sectionValue as Int
                val section = requireNotNull(ServerCacheManager.getStruct(sectionStructId))
                val itemEnumId = section.params?.get(690) as? Int
                val items = itemEnumId?.let { requireNotNull(ServerCacheManager.getEnum(it)) }
                println(
                    "COLLECTION_SECTION order=$order struct=$sectionStructId " +
                        "params=${section.params} items=${items?.values}"
                )
            }
        }

        listOf(
                "varp.collection_count_other_max",
                "varp.collection_count_other",
                "varp.collection_count_minigames_max",
                "varp.collection_count_minigames",
                "varp.collection_count_clues_max",
                "varp.collection_count_clues",
                "varp.collection_count_raids_max",
                "varp.collection_count_raids",
                "varp.collection_count_bosses_max",
                "varp.collection_count_bosses",
                "varp.collection_count_max",
                "varp.collection_count",
            )
            .forEach { name ->
                println("COLLECTION_VAR name=$name id=${name.asRSCM(RSCMType.VARP)}")
            }
    }
}
