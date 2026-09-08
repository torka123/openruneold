package dev.openrune

import dev.openrune.filesystem.Cache
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import org.junit.jupiter.api.Test

/** Extracts the CLANSETTING_GET (opcode 74) ids used by revision-240 clan scripts. */
class ClanSettingIdAuditTest {
    @Test
    fun `dump confirmed clan setting ids`() {
        val cache = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            (4200..4500).forEach { scriptId ->
                val data = cache.data(CLIENT_SCRIPT_INDEX, scriptId) ?: return@forEach
                val trailerLength = uShort(data, data.size - 2)
                val headerOffset = data.size - 2 - trailerLength - 16
                if (headerOffset !in 0 until data.size) return@forEach
                val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
                readString(buffer)
                var index = 0
                while (buffer.position() < headerOffset) {
                    val opcode = buffer.short.toInt() and 0xFFFF
                    val operand =
                        when {
                            opcode == 3 -> {
                                readString(buffer)
                                null
                            }
                            opcode in BYTE_OPERANDS || opcode >= 100 -> {
                                buffer.get()
                                null
                            }
                            else -> buffer.int
                        }
                    if (opcode == CLANSETTING_GET) {
                        println("CLAN_SETTING script=$scriptId instruction=$index id=$operand")
                    }
                    index++
                }
            }
        } finally {
            cache.close()
        }
    }

    private fun readString(buffer: ByteBuffer): String {
        val out = StringBuilder()
        while (buffer.hasRemaining()) {
            val value = buffer.get().toInt() and 0xFF
            if (value == 0) break
            out.append(value.toChar())
        }
        return out.toString()
    }

    private fun uShort(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private companion object {
        const val CLIENT_SCRIPT_INDEX = 12
        const val CLANSETTING_GET = 74
        val BYTE_OPERANDS = setOf(9, 21, 38, 39)
    }
}
