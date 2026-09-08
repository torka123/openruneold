package dev.openrune

import dev.openrune.filesystem.Cache
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import org.junit.jupiter.api.Test

/** Locates client scripts which reference the revision-240 Clan Outfit/Motif interface. */
class ClanMotifScriptAuditTest {
    @Test
    fun `locate motif scripts and clan setting reads`() {
        val cache = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            (0..MAX_SCRIPT_ID).forEach { scriptId ->
                val data = cache.data(CLIENT_SCRIPT_INDEX, scriptId) ?: return@forEach
                val instructions = decode(data) ?: return@forEach
                val motifReferences =
                    instructions.withIndex().filter { it.value.first == ICONST && it.value.second in MOTIF_COMPONENTS }
                if (motifReferences.isNotEmpty()) {
                    val settingReads =
                        instructions.withIndex().filter { it.value.first == CLANSETTING_GET }
                    println(
                        "CLAN_MOTIF_SCRIPT script=$scriptId refs=" +
                            motifReferences.joinToString { "${it.index}:${it.value.second}" } +
                            " settings=" + settingReads.joinToString { "${it.index}:${it.value.second}" },
                    )
                }
            }
        } finally {
            cache.close()
        }
    }

    private fun decode(data: ByteArray): List<Pair<Int, Int>>? {
        val trailerLength = uShort(data, data.size - 2)
        val end = data.size - 2 - trailerLength - 16
        if (end !in 0 until data.size) return null
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        readString(buffer)
        return buildList {
            while (buffer.position() < end) {
                val opcode = buffer.short.toInt() and 0xFFFF
                val operand =
                    when {
                        opcode == SCONST -> { readString(buffer); Int.MIN_VALUE }
                        opcode in BYTE_OPERANDS || opcode >= 100 -> { buffer.get(); Int.MIN_VALUE }
                        else -> buffer.int
                    }
                add(opcode to operand)
            }
        }
    }

    private fun readString(buffer: ByteBuffer) {
        while (buffer.hasRemaining() && (buffer.get().toInt() and 0xFF) != 0) Unit
    }

    private fun uShort(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private companion object {
        const val CLIENT_SCRIPT_INDEX = 12
        const val MAX_SCRIPT_ID = 10_000
        const val ICONST = 0
        const val SCONST = 3
        const val CLANSETTING_GET = 74
        val BYTE_OPERANDS = setOf(9, 21, 38, 39)
        val MOTIF_COMPONENTS = 45481984..45482001
    }
}
