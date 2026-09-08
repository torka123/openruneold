package dev.openrune

import dev.openrune.filesystem.Cache
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import org.junit.jupiter.api.Test

/** Dumps switch-key to instruction-target mappings for revision-240 clan permission scripts. */
class ClanPermissionSwitchAuditTest {
    @Test
    fun `dump clan permission catalog`() {
        ServerCacheManager.init(240)
        val root = requireNotNull(ServerCacheManager.getEnum(PERMISSION_ROOT_ENUM))
        println("CLAN_PERMISSION_ROOT values=${root.values}")
        for ((categoryKey, categoryValue) in root.values) {
            val categoryEnumId = categoryValue as? Int ?: continue
            val category = ServerCacheManager.getEnum(categoryEnumId)
            println(
                "CLAN_PERMISSION_CATEGORY key=$categoryKey enum=$categoryEnumId " +
                    "values=${category?.values}",
            )
            category?.values?.forEach { (permissionKey, permissionValue) ->
                val recordId = permissionValue as? Int ?: permissionKey as? Int ?: return@forEach
                val record = ServerCacheManager.getStruct(recordId)
                println(
                    "CLAN_PERMISSION_RECORD category=$categoryKey key=$permissionKey " +
                        "value=$permissionValue struct=$recordId params=${record?.params}",
                )
            }
        }
    }

    @Test
    fun `dump clan permission switch tables`() {
        val cache = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            for (scriptId in listOf(4456, 4465)) {
                val data = requireNotNull(cache.data(CLIENT_SCRIPT_INDEX, scriptId))
                val trailerLength = uShort(data, data.size - 2)
                val headerOffset = data.size - 2 - trailerLength - FIXED_FOOTER_BYTES
                val footer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
                footer.position(headerOffset)
                footer.int // instruction count
                repeat(6) { footer.short } // locals and argument counts
                val switchCount = footer.get().toInt() and 0xFF
                repeat(switchCount) { tableIndex ->
                    val entryCount = footer.short.toInt() and 0xFFFF
                    repeat(entryCount) {
                        val key = footer.int
                        val relativeJump = footer.int
                        println(
                            "CLAN_SWITCH script=$scriptId table=$tableIndex key=$key " +
                                "relative=$relativeJump target=${relativeJump + 6}",
                        )
                    }
                }
            }
        } finally {
            cache.close()
        }
    }

    private fun uShort(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private companion object {
        const val CLIENT_SCRIPT_INDEX = 12
        const val FIXED_FOOTER_BYTES = 16
        const val PERMISSION_ROOT_ENUM = 3825
    }
}
