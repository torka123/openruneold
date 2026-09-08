package dev.openrune

import dev.openrune.cache.filestore.definition.InterfaceType
import org.junit.jupiter.api.Test

class ClanInterfaceAuditTest {
    @Test
    fun `dump revision 240 clan interface index`() {
        ServerCacheManager.init(240)
        val interfacesField = ServerCacheManager::class.java.getDeclaredField("interfaces")
        interfacesField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val interfaces = interfacesField.get(ServerCacheManager) as Map<Int, InterfaceType>
        val clanInterfaces =
            interfaces.values
                .filter { type ->
                    type.internalName.contains("clan", ignoreCase = true) ||
                        type.components.values.any { component ->
                            component.javaClass.declaredFields.any { field ->
                                field.name == "text" &&
                                    runCatching {
                                        field.isAccessible = true
                                        field
                                            .get(component)
                                            ?.toString()
                                            ?.contains("clan", true) == true
                                    }.getOrDefault(false)
                            }
                        }
                }
                .sortedBy(InterfaceType::id)
        clanInterfaces.forEach {
            println(
                "CLAN_INTERFACE id=${it.id} name=${it.internalName} " +
                    "components=${it.components.size}",
            )
        }
    }
}
