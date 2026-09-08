package dev.openrune

import org.junit.jupiter.api.Test

class Interface458DumpTest {
    @Test
    fun `dump revision 240 furniture components`() {
        ServerCacheManager.init(240)
        val type = requireNotNull(ServerCacheManager.getInterface(458))
        println("IF458 componentCount=${type.components.size}")
        type.components.toSortedMap().forEach { (child, component) ->
            val fields =
                component.javaClass.declaredFields.joinToString(" | ") { field ->
                    runCatching {
                            field.isAccessible = true
                            "${field.name}=${field.get(component)}"
                        }
                        .getOrElse { "${field.name}=<unavailable>" }
                }
            println(
                "IF458 child=$child packed=${component.packed} class=${component.javaClass.simpleName} $fields"
            )
        }
    }
}
