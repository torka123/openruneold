package dev.openrune

import dev.openrune.cache.filestore.definition.InterfaceType
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.baseVar
import dev.openrune.types.varp.bits
import org.junit.jupiter.api.Test

class PohInterfaceAuditTest {
    @Test
    fun `dump revision 240 poh interfaces`() {
        ServerCacheManager.init(240)
        listOf(2176, 4744, 6269, 14670).forEach { id ->
            val varbit = requireNotNull(ServerCacheManager.getVarbit(id))
            println("VARBIT id=$id base=${varbit.baseVar.id} bits=${varbit.bits}")
        }
        listOf(
                13100,
                13101,
                13094,
                13096,
                13006,
                13007,
                13109,
                13107,
                13016,
                13015,
                13119,
                13118,
                27084,
                27085,
            )
            .forEach { id ->
                val loc = requireNotNull(ServerCacheManager.getObject(id))
                println(
                    "POH_DOOR id=$id name=${loc.name} actions=${loc.actions} " +
                        "size=${loc.width}x${loc.length} blockWalk=${loc.blockWalk} " +
                        "blockRange=${loc.blockRange} nextStage=${loc.paramsRaw?.get(65533)}"
                )
            }
        listOf(
                "npc.poh_servant_multi_dogsbody",
                "npc.poh_servant_multi_waiter_woman",
                "npc.poh_servant_multi_cook_woman",
                "npc.poh_servant_multi_maitre_d_man",
                "npc.poh_servant_multi_demon",
            )
            .forEach { gameval ->
                val id = gameval.asRSCM(RSCMType.NPC)
                val npc = requireNotNull(ServerCacheManager.getNpc(id))
                println(
                    "POH_SERVANT gameval=$gameval id=$id name=${npc.name} actions=${npc.actions}"
                )
            }
        ServerCacheManager.getNpcs()
            .values
            .filter { npc ->
                val name = npc.name?.lowercase().orEmpty()
                name == "rick" ||
                    name == "maid" ||
                    name == "cook" ||
                    name == "butler" ||
                    name == "demon butler"
            }
            .sortedBy { it.id }
            .forEach { npc ->
                println(
                    "POH_SERVANT_CANDIDATE id=${npc.id} internal=${npc.internalName} " +
                        "name=${npc.name} actions=${npc.actions}"
                )
            }
        val interfacesField = ServerCacheManager::class.java.getDeclaredField("interfaces")
        interfacesField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val allInterfaces = interfacesField.get(ServerCacheManager) as Map<Int, InterfaceType>
        allInterfaces.values
            .filter { it.internalName.contains("poh", ignoreCase = true) }
            .sortedBy { it.id }
            .forEach { println("POH_INTERFACE id=${it.id} name=${it.internalName}") }

        listOf("interface.poh_options", "interface.poh_viewer").forEach { name ->
            val id = name.asRSCM(RSCMType.INTERFACE)
            val type = requireNotNull(ServerCacheManager.getInterface(id))
            println("$name id=$id componentCount=${type.components.size}")
            type.components.toSortedMap().forEach { (child, component) ->
                val fields =
                    component.javaClass.declaredFields.joinToString(" | ") { field ->
                        runCatching {
                                field.isAccessible = true
                                val value = field.get(component)
                                val printable =
                                    when (value) {
                                        is Array<*> -> value.contentDeepToString()
                                        is IntArray -> value.contentToString()
                                        is LongArray -> value.contentToString()
                                        else -> value
                                    }
                                "${field.name}=$printable"
                            }
                            .getOrElse { "${field.name}=<unavailable>" }
                    }
                println(
                    "$name child=$child packed=${component.packed} " +
                        "class=${component.javaClass.simpleName} $fields"
                )
            }
        }
    }
}
