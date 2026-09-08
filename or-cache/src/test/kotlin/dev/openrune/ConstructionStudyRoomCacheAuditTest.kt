package dev.openrune

import dev.openrune.cache.MAPS
import dev.openrune.filesystem.Cache
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.util.InlineByteBuf
import java.lang.reflect.Modifier
import java.nio.file.Path
import org.junit.jupiter.api.Test

class ConstructionStudyRoomCacheAuditTest {
    @Test
    fun `dump revision 240 study and workshop room hotspots`() {
        val cache = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            val provider = OsrsCacheProvider(cache, 240)
            provider.init()
            val squareX = 29
            val squareZ = 110
            val locData = requireNotNull(cache.data(MAPS, (squareX shl 8) or squareZ, 1))
            val decoded = MapLocListDecoder.decode(InlineByteBuf(locData))
            val iterator = decoded.spawns.longIterator()
            while (iterator.hasNext()) {
                val loc = MapLocDefinition(iterator.nextLong())
                if (loc.level != 0 || loc.localZ !in 40..47) continue
                val room =
                    when (loc.localX) {
                        in 32..39 -> "STUDY"
                        in 0..7 -> "WORKSHOP"
                        else -> continue
                    }
                println("${room}_ROOM_LOC $loc; ${reflect(provider.objects[loc.id])}")
            }
            val furnitureItems =
                (8334..8356).toList() + (8375..8379).toList() + listOf(20644, 20645, 20646)
            furnitureItems.forEach { id ->
                println("CONSTRUCTION_FURNITURE_ITEM $id = ${reflect(provider.items[id])}")
            }
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
