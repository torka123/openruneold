package dev.openrune.audit.toa

import dev.openrune.OsrsCacheProvider
import dev.openrune.filesystem.Cache
import java.lang.reflect.Modifier
import java.nio.file.Path

public fun main(args: Array<String>) {
    require(args.size >= 2) { "Usage: <cache> <revision> [tokens...]" }
    val cache = Cache.load(Path.of(args[0]))
    try {
        val provider = OsrsCacheProvider(cache, args[1].toInt())
        provider.init()
        val tokens = args.drop(2).map(::normalize)
        val groups =
            linkedMapOf(
                "NPC" to provider.npcs,
                "LOC" to provider.objects,
                "OBJ" to provider.items,
            )
        for ((kind, values) in groups) {
            for ((id, value) in values) {
                val name = name(value) ?: continue
                val normalized = normalize(name)
                if (tokens.isNotEmpty() && tokens.none(normalized::contains)) continue
                val details =
                    reflected(value)
                        .filterKeys {
                            val key = it.lowercase()
                            key.contains("option") || key.contains("action") ||
                                key.contains("anim") || key.contains("transform") ||
                                key == "width" || key == "length" || key == "size"
                        }
                        .entries
                        .joinToString("; ") { "${it.key}=${render(it.value)}" }
                println("$kind\t$id\t$name\t$details")
            }
        }
    } finally {
        cache.close()
    }
}

private fun name(value: Any): String? =
    runCatching { value.javaClass.getMethod("getName").invoke(value) as? String }.getOrNull()

private fun normalize(value: String): String =
    value.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

private fun reflected(value: Any): Map<String, Any?> {
    val out = linkedMapOf<String, Any?>()
    var type: Class<*>? = value.javaClass
    while (type != null && type != Any::class.java) {
        for (field in type.declaredFields) {
            if (Modifier.isStatic(field.modifiers) || field.isSynthetic) continue
            out.putIfAbsent(field.name, runCatching { field.trySetAccessible(); field.get(value) }.getOrNull())
        }
        type = type.superclass
    }
    return out
}

private fun render(value: Any?): String =
    when (value) {
        null -> "null"
        is IntArray -> value.joinToString(prefix = "[", postfix = "]")
        is Array<*> -> value.joinToString(prefix = "[", postfix = "]")
        else -> value.toString()
    }
