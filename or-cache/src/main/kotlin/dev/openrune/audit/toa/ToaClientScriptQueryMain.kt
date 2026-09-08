package dev.openrune.audit.toa

import dev.openrune.filesystem.Cache
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path

private const val CLIENT_SCRIPT_INDEX = 12

private val byteOperandOpcodes = setOf(9, 21, 38, 39)

private val opcodeNames =
    mapOf(
        0 to "iconst",
        3 to "sconst",
        6 to "jump",
        7 to "if_icmpne",
        8 to "if_icmpeq",
        9 to "push_null",
        21 to "return",
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
        60 to "switch",
    )

public fun main(args: Array<String>) {
    require(args.size >= 2) { "Usage: <cache> <client-script-ids...>" }
    val cache = Cache.load(Path.of(args[0]))
    try {
        args.drop(1).map(String::toInt).forEach { id ->
            val data = cache.data(CLIENT_SCRIPT_INDEX, id)
            if (data == null) {
                println("CLIENTSCRIPT\t$id\tmissing")
                return@forEach
            }
            val trailerLength = uShort(data, data.size - 2)
            // Modern scripts add two more local/argument counters to the historical footer.
            val headerOffset = data.size - 2 - trailerLength - 16
            require(headerOffset in 0 until data.size) {
                "Invalid client-script footer for $id (size=${data.size}, trailer=$trailerLength)"
            }
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
                "CLIENTSCRIPT\t$id\tinstructions=$instructionCount" +
                    "\tlocalInts=$localInts\tlocalStrings=$localStrings\tlocalLongs=$localLongs" +
                    "\tintArgs=$intArgs\tstringArgs=$stringArgs\tlongArgs=$longArgs"
            )
            decodeInstructions(data, headerOffset, instructionCount).forEachIndexed {
                index,
                instruction ->
                println("\t${index.toString().padStart(4, '0')}\t$instruction")
            }
        }
    } finally {
        cache.close()
    }
}

private fun decodeInstructions(
    data: ByteArray,
    endOffset: Int,
    instructionCount: Int,
): List<String> {
    val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
    readNullTerminatedString(buffer)
    return buildList(instructionCount) {
        while (buffer.position() < endOffset) {
            val opcode = buffer.short.toInt() and 0xFFFF
            val name = opcodeNames[opcode] ?: "opcode_$opcode"
            val operand =
                when {
                    opcode == 3 -> " " + readNullTerminatedString(buffer)
                    opcode in byteOperandOpcodes || opcode >= 100 ->
                        " ${buffer.get().toInt() and 0xFF}"
                    else -> " ${buffer.int}"
                }
            add(name + operand)
        }
    }
}

private fun readNullTerminatedString(buffer: ByteBuffer): String {
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
