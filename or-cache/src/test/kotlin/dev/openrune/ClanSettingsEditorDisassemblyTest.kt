package dev.openrune

import dev.openrune.filesystem.Cache
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import org.junit.jupiter.api.Test

/** Disassembles the revision-240 client scripts behind the clan settings editors. */
class ClanSettingsEditorDisassemblyTest {
    @Test
    fun `dump clan settings editor bytecode`() {
        val cache = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            SCRIPTS.forEach { scriptId ->
                decode(requireNotNull(cache.data(CLIENT_SCRIPT_INDEX, scriptId))).forEachIndexed {
                    index,
                    instruction ->
                    println(
                        "CLAN_EDITOR_BYTECODE script=$scriptId index=$index " +
                            "opcode=${instruction.opcode} int=${instruction.intOperand} " +
                            "string=${instruction.stringOperand}",
                    )
                }
            }
        } finally {
            cache.close()
        }
    }

    private fun decode(data: ByteArray): List<Instruction> {
        val trailerLength = uShort(data, data.size - 2)
        val end = data.size - 2 - trailerLength - FIXED_FOOTER_BYTES
        require(end in 0 until data.size)
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        readString(buffer)
        return buildList {
            while (buffer.position() < end) {
                val opcode = buffer.short.toInt() and 0xFFFF
                when {
                    opcode == SCONST -> add(Instruction(opcode, stringOperand = readString(buffer)))
                    opcode in BYTE_OPERANDS || opcode >= 100 ->
                        add(Instruction(opcode, intOperand = buffer.get().toInt() and 0xFF))
                    else -> add(Instruction(opcode, intOperand = buffer.int))
                }
            }
        }
    }

    private fun readString(buffer: ByteBuffer): String {
        val value = StringBuilder()
        while (buffer.hasRemaining()) {
            val next = buffer.get().toInt() and 0xFF
            if (next == 0) break
            value.append(next.toChar())
        }
        return value.toString()
    }

    private fun uShort(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private data class Instruction(
        val opcode: Int,
        val intOperand: Int = Int.MIN_VALUE,
        val stringOperand: String? = null,
    )

    private companion object {
        const val CLIENT_SCRIPT_INDEX = 12
        const val FIXED_FOOTER_BYTES = 16
        const val SCONST = 3
        val BYTE_OPERANDS = setOf(9, 21, 38, 39)
        val SCRIPTS = listOf(4263, 4264, 4307, 4330, 4431, 4433, 4434, 4457, 4463)
    }
}
