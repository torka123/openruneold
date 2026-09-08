package dev.openrune

import dev.openrune.filesystem.Cache
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import org.junit.jupiter.api.Test

/** Read-only revision-240 evidence for the native clan-members interface and its scripts. */
class ClanMemberClientScriptAuditTest {
    @Test
    fun `dump revision 240 member interface and client scripts`() {
        ServerCacheManager.init(240)
        val type = requireNotNull(ServerCacheManager.getInterface(MEMBERS_INTERFACE))
        type.components.toSortedMap().forEach { (child, component) ->
            val fields =
                component.javaClass.declaredFields.associate { field ->
                    field.isAccessible = true
                    field.name to printable(field.get(component))
                }
            println(
                "CLAN_MEMBER_COMPONENT child=$child packed=${component.packed} " +
                    "name=${component.internalName} " +
                    ACTION_FIELDS.joinToString(" ") { "$it=${fields[it]}" },
            )
        }

        val cache = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            MEMBER_SCRIPT_IDS.forEach { scriptId -> dumpScript(cache, scriptId) }
        } finally {
            cache.close()
        }
    }

    private fun dumpScript(cache: Cache, scriptId: Int) {
        val data = requireNotNull(cache.data(CLIENT_SCRIPT_INDEX, scriptId))
        val trailerLength = uShort(data, data.size - 2)
        val headerOffset = data.size - 2 - trailerLength - FIXED_FOOTER_BYTES
        require(headerOffset in 0 until data.size)
        val footer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        footer.position(headerOffset)
        val instructionCount = footer.int
        val localInts = footer.short.toInt() and 0xFFFF
        val localStrings = footer.short.toInt() and 0xFFFF
        val localLongs = footer.short.toInt() and 0xFFFF
        val intArgs = footer.short.toInt() and 0xFFFF
        val stringArgs = footer.short.toInt() and 0xFFFF
        val longArgs = footer.short.toInt() and 0xFFFF
        println(
            "CLAN_MEMBER_SCRIPT id=$scriptId instructions=$instructionCount " +
                "locals=$localInts/$localStrings/$localLongs args=$intArgs/$stringArgs/$longArgs",
        )
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        readString(buffer)
        var instruction = 0
        while (buffer.position() < headerOffset) {
            val opcode = buffer.short.toInt() and 0xFFFF
            val operand =
                when {
                    opcode == SCONST -> readString(buffer)
                    opcode in BYTE_OPERANDS || opcode >= 100 ->
                        (buffer.get().toInt() and 0xFF).toString()
                    else -> buffer.int.toString()
                }
            println(
                "  ${instruction.toString().padStart(4, '0')} " +
                    "${OPCODE_NAMES[opcode] ?: "opcode_$opcode"} $operand",
            )
            instruction++
        }
    }

    private fun printable(value: Any?): String? =
        when (value) {
            null -> null
            is Array<*> -> value.contentDeepToString()
            is IntArray -> value.contentToString()
            is LongArray -> value.contentToString()
            else -> value.toString()
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

    private companion object {
        const val MEMBERS_INTERFACE = 693
        const val CLIENT_SCRIPT_INDEX = 12
        const val FIXED_FOOTER_BYTES = 16
        const val SCONST = 3
        val BYTE_OPERANDS = setOf(9, 21, 38, 39)
        val MEMBER_SCRIPT_IDS = (4223..4254).toList()
        val ACTION_FIELDS =
            listOf(
                "text",
                "opBase",
                "op",
                "onLoad",
                "onOp",
                "onClick",
                "onMouseOver",
                "onMouseLeave",
                "onVarTransmit",
                "onVarcTransmit",
                "onVarcStrTransmit",
            )
        val OPCODE_NAMES =
            mapOf(
                0 to "iconst",
                1 to "get_varp",
                2 to "set_varp",
                3 to "sconst",
                6 to "jump",
                7 to "if_icmpne",
                8 to "if_icmpeq",
                9 to "push_null",
                10 to "if_icmpgt",
                21 to "return",
                25 to "get_varbit",
                27 to "set_varbit",
                31 to "if_icmple",
                32 to "if_icmpge",
                33 to "iload",
                34 to "istore",
                35 to "oload",
                36 to "ostore",
                37 to "join_string",
                38 to "pop_int",
                39 to "pop_object",
                40 to "invoke",
                42 to "get_varc_int",
                43 to "set_varc_int",
                47 to "get_varc_string_old",
                48 to "set_varc_string_old",
                49 to "get_varc_string",
                50 to "set_varc_string",
                60 to "switch",
                74 to "clansetting_get",
                75 to "clanvar_get",
            )
    }
}
