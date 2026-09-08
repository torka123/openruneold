package dev.openrune

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FoundryR240RewardShopAuditTest {
    @Test
    fun `revision 240 reward shop mappings are cache authoritative`() {
        ServerCacheManager.init(240)

        val interfaceId = "interface.giants_foundry_reward_shop".asRSCM(RSCMType.INTERFACE)
        assertEquals(753, interfaceId)
        val type = requireNotNull(ServerCacheManager.getInterface(interfaceId))
        type.components.toSortedMap().forEach { (child, component) ->
            println(
                "FOUNDRY_COMPONENT child=$child internal=${component.internalName} " +
                    "type=${component.javaClass.simpleName} ops=${component.op?.toList()} " +
                    "onOp=${component.onOp?.toList()} onLoad=${component.onLoad?.toList()}"
            )
        }

        val entries = requireNotNull(ServerCacheManager.getEnum(4379))
        println("FOUNDRY_REWARD_ENUM values=${entries.values}")
        entries.values.toSortedMap().forEach { (selectionId, rawStructId) ->
            val structId = rawStructId as Int
            val params = requireNotNull(ServerCacheManager.getStruct(structId)?.params)
            println("FOUNDRY_REWARD selection=$selectionId struct=$structId params=$params")
        }

        assertEquals(
            9,
            entries.values.values
                .map { it as Int }
                .count { structId ->
                    val params = requireNotNull(ServerCacheManager.getStruct(structId)?.params)
                    params[1636] is Int
                },
        )
    }
}
