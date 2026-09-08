package dev.openrune

import dev.openrune.filesystem.Cache
import java.lang.reflect.Modifier
import java.nio.file.Path
import org.junit.jupiter.api.Test

class ConstructionPlantClientCacheAuditTest {
    @Test
    fun `dump revision 240 construction plant client locs`() {
        val cache = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            val provider = OsrsCacheProvider(cache, 240)
            provider.init()
            val ids =
                (5128..5139).toList() +
                    (31970..31985).toList() +
                    (56120..56135).toList() +
                    listOf(
                        4521,
                        4524,
                        13497,
                        13498,
                        13499,
                        13500,
                        13501,
                        13502,
                        13503,
                        13505,
                        15326,
                        15327,
                        15328,
                        15329,
                        15364,
                        15365,
                        15366,
                        15367,
                        15377,
                        15378,
                        15379,
                        15380,
                        15381,
                        15382,
                        15387,
                        15388,
                        15390,
                        15391,
                    )
            ids.forEach { id ->
                val value = provider.objects[id]
                println("CONSTRUCTION_CLIENT_LOC $id = ${reflect(value)}")
            }
            val vorkathHead = reflect(provider.objects[56128])
            check("name=Vorkath head" in vorkathHead) {
                "Revision 240 is missing the mounted Vorkath head object."
            }
            check(
                "0=Talk-to" in vorkathHead &&
                    "1=Trophies" in vorkathHead &&
                    "4=Remove" in vorkathHead
            ) {
                "Mounted Vorkath head has unexpected construction interactions: $vorkathHead"
            }
            println("CONSTRUCTION_CLIENT_NPC 8026 = ${reflect(provider.npcs[8026])}")
        } finally {
            cache.close()
        }
    }

    private fun reflect(value: Any?): String {
        if (value == null) return "null"
        val fields = linkedMapOf<String, Any?>()
        var type: Class<*>? = value.javaClass
        while (type != null && type != Any::class.java) {
            type.declaredFields.forEach { field ->
                if (!Modifier.isStatic(field.modifiers) && !field.isSynthetic) {
                    field.trySetAccessible()
                    fields.putIfAbsent(field.name, runCatching { field.get(value) }.getOrNull())
                }
            }
            type = type.superclass
        }
        return fields.entries.joinToString("; ") { (name, fieldValue) ->
            val rendered =
                when (fieldValue) {
                    is IntArray -> fieldValue.joinToString(prefix = "[", postfix = "]")
                    is Array<*> -> fieldValue.joinToString(prefix = "[", postfix = "]")
                    else -> fieldValue.toString()
                }
            "$name=$rendered"
        }
    }
}
