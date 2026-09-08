package dev.openrune

import org.junit.jupiter.api.Test

class ClanComponentAuditTest {
    @Test
    fun `dump revision 240 clan interface components`() {
        ServerCacheManager.init(240)
        val clanInterfaceIds = (689..706) + 783
        clanInterfaceIds.forEach { interfaceId ->
            val type = requireNotNull(ServerCacheManager.getInterface(interfaceId))
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
                        }.getOrElse { "${field.name}=<unavailable>" }
                    }
                println(
                    "CLAN_COMPONENT interface=$interfaceId child=$child " +
                        "packed=${component.packed} " +
                        "class=${component.javaClass.simpleName} $fields",
                )
            }
        }
    }
}
