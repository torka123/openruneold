package dev.openrune

import dev.openrune.filesystem.Cache
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import org.junit.jupiter.api.Test

/** Cache evidence for the native revision-240 clan settings editors. */
class ClanSettingsClientScriptAuditTest {
    @Test
    fun `locate scripts which build clan settings editors`() {
        val cache = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            (0..MAX_SCRIPT_ID).forEach { scriptId ->
                val data = cache.data(CLIENT_SCRIPT_INDEX, scriptId) ?: return@forEach
                val instructions = decode(data) ?: return@forEach
                val references =
                    instructions.withIndex().filter { instruction ->
                        instruction.value.opcode == ICONST &&
                            INTERFACE_COMPONENTS.any { instruction.value.intOperand in it }
                    }
                if (references.isNotEmpty()) {
                    println(
                        "CLAN_SETTINGS_SCRIPT script=$scriptId refs=" +
                            references.joinToString { "${it.index}:${it.value.intOperand}" },
                    )
                }
            }
        } finally {
            cache.close()
        }
    }

    @Test
    fun `dump clan settings editor scripts`() {
        val cache = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            EDITOR_SCRIPTS.forEach { scriptId ->
                val instructions = requireNotNull(decode(requireNotNull(cache.data(CLIENT_SCRIPT_INDEX, scriptId))))
                instructions.forEachIndexed { index, instruction ->
                    println(
                        "CLAN_SETTINGS_INSTRUCTION script=$scriptId index=$index " +
                            "opcode=${instruction.opcode} int=${instruction.intOperand} " +
                            "string=${instruction.stringOperand}",
                    )
                }
            }
        } finally {
            cache.close()
        }
    }

    private fun decode(data: ByteArray): List<Instruction>? {
        val trailerLength = uShort(data, data.size - 2)
        val end = data.size - 2 - trailerLength - FIXED_FOOTER_BYTES
        if (end !in 0 until data.size) return null
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
        const val MAX_SCRIPT_ID = 10_000
        const val FIXED_FOOTER_BYTES = 16
        const val ICONST = 0
        const val SCONST = 3
        val BYTE_OPERANDS = setOf(9, 21, 38, 39)
        val INTERFACE_COMPONENTS =
            listOf(
                45285376..45285386, // clans_interests (691)
                45350912..45350923, // clans_hall (692)
                45481984..45482001, // clans_outfit (694)
                45547520..45547534, // clans_ranktitles (695)
                46268416..46268430, // clans_permissions (706)
            )
        val EDITOR_SCRIPTS = listOf(4307, 4330, 4431, 4433, 4434, 4457, 4463)
    }
}
