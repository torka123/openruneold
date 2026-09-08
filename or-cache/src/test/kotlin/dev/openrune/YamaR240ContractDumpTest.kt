package dev.openrune

import dev.openrune.filesystem.Cache
import java.lang.reflect.Modifier
import java.nio.file.Path
import org.junit.jupiter.api.Test

class YamaR240ContractDumpTest {
    @Test
    fun `dump remaining yama contract definitions`() {
        val cache = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            val provider = OsrsCacheProvider(cache, 240)
            provider.init()
            provider.items
                .filterKeys { it in 30840..30900 }
                .toSortedMap()
                .forEach { (id, item) ->
                    println("YAMA_R240_ITEM\t$id\t${item.name}\t${item.interfaceOptions}")
                }
            listOf(7668, 14183, 14184, 14185).forEach { id ->
                val npc = provider.npcs[id] ?: return@forEach
                val fields = linkedMapOf<String, Any?>()
                var type: Class<*>? = npc.javaClass
                while (type != null && type != Any::class.java) {
                    type.declaredFields.forEach { field ->
                        if (
                            !Modifier.isStatic(field.modifiers) &&
                                !field.isSynthetic &&
                                field.trySetAccessible()
                        ) {
                            fields.putIfAbsent(
                                field.name,
                                runCatching { field.get(npc) }.getOrNull(),
                            )
                        }
                    }
                    type = type.superclass
                }
                println("YAMA_R240_VOICE\t$id\t$fields")
            }
        } finally {
            cache.close()
        }
    }
}
