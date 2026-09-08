package dev.openrune

import dev.openrune.filesystem.Cache
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import org.junit.jupiter.api.Test

/** Read-only revision-240 evidence for native social-channel tab selection. */
class ClanChannelClientScriptAuditTest {
    @Test
    fun `dump channel interface and tab scripts`() {
        ServerCacheManager.init(240)
        (680..710).forEach { id ->
            val name = RSCM.getReverseMapping(RSCMType.INTERFACE, id)
            if (name != null) println("CLAN_INTERFACE_RSCM id=$id name=$name")
        }
        val cache = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            listOf(4471, 4474, 4475, 4476, 4477, 4478, 5301).forEach { dump(cache, it) }
        } finally {
            cache.close()
        }
    }

    private fun dump(cache: Cache, scriptId: Int) {
        val data = requireNotNull(cache.data(12, scriptId))
        val trailerLength = uShort(data, data.size - 2)
        val headerOffset = data.size - 2 - trailerLength - 16
        val footer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        footer.position(headerOffset)
        val instructionCount = footer.int
        val localInts = footer.short.toInt() and 0xFFFF
        val localStrings = footer.short.toInt() and 0xFFFF
        val localLongs = footer.short.toInt() and 0xFFFF
        val intArgs = footer.short.toInt() and 0xFFFF
        val stringArgs = footer.short.toInt() and 0xFFFF
        val longArgs = footer.short.toInt() and 0xFFFF
        println("CHANNEL_SCRIPT id=$scriptId instructions=$instructionCount locals=$localInts/$localStrings/$localLongs args=$intArgs/$stringArgs/$longArgs")
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        readString(buffer)
        var index = 0
        while (buffer.position() < headerOffset) {
            val opcode = buffer.short.toInt() and 0xFFFF
            val operand = when {
                opcode == 3 -> readString(buffer)
                opcode in setOf(9, 21, 38, 39) || opcode >= 100 -> (buffer.get().toInt() and 0xFF).toString()
                else -> buffer.int.toString()
            }
            println("  ${index.toString().padStart(4, '0')} opcode_$opcode $operand")
            index++
        }
    }

    private fun readString(buffer: ByteBuffer): String {
        val result = StringBuilder()
        while (buffer.hasRemaining()) {
            val value = buffer.get().toInt() and 0xFF
            if (value == 0) break
            result.append(value.toChar())
        }
        return result.toString()
    }

    private fun uShort(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
}
