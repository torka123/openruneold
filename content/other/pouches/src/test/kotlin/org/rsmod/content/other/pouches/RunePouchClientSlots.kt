package org.rsmod.content.other.pouches

import dev.openrune.filesystem.Cache
import java.nio.ByteBuffer
import java.nio.file.Path

/**
 * Executes the regular/divine slot-visibility predicate from the actual revision-240 client cache.
 * This intentionally supports only script 7869's slots 1..4, not a general client or UI renderer.
 * Its caller, draw-slot script 667, hides rune models when this predicate returns zero.
 */
internal object RunePouchClientSlots {
    private data class Instruction(val opcode: Int, val operand: Int)

    private val instructions: List<Instruction>
    private val switches: List<Map<Int, Int>>

    init {
        val cache = Cache.load(Path.of(".data/cache/LIVE"))
        val bytes =
            try {
                requireNotNull(cache.data(12, 7869))
            } finally {
                cache.close()
            }
        val buffer = ByteBuffer.wrap(bytes)
        val trailerSize = buffer.getShort(bytes.size - 2).toInt() and 0xFFFF
        val codeEnd = bytes.size - 2 - trailerSize - 16
        buffer.position(codeEnd)
        val count = buffer.int
        repeat(6) { buffer.short } // int/string/long locals and arguments in revision 240
        switches =
            List(buffer.get().toInt() and 0xFF) {
                buildMap { repeat(buffer.short.toInt() and 0xFFFF) { put(buffer.int, buffer.int) } }
            }
        check(buffer.position() == bytes.size - 2)
        buffer.position(0)
        while (buffer.get().toInt() != 0) {
            /* optional debug name */
        }
        instructions = buildList {
            while (buffer.position() < codeEnd) {
                val opcode = buffer.short.toInt() and 0xFFFF
                check(opcode != 3) { "Unexpected string operand in slot predicate" }
                val operand =
                    if (opcode >= 100 || opcode in listOf(21, 38, 39)) {
                        buffer.get().toInt() and 0xFF
                    } else buffer.int
                add(Instruction(opcode, operand))
            }
        }
        check(buffer.position() == codeEnd && instructions.size == count)
    }

    fun enabled(slot: Int, varp: (Int) -> Int): Boolean {
        require(slot in 1..4)
        val stack = ArrayDeque<Int>()
        var pc = 0
        repeat(100) {
            val (opcode, operand) = instructions[pc++]
            when (opcode) {
                0 -> stack.addLast(operand)
                1 -> stack.addLast(varp(operand))
                6 -> pc += operand
                21 -> return stack.removeLast() == 1
                32 -> {
                    val right = stack.removeLast()
                    if (stack.removeLast() >= right) pc += operand
                }
                33 -> {
                    check(operand == 0)
                    stack.addLast(slot)
                }
                60 -> pc += switches[operand][stack.removeLast()] ?: 0
                else -> error("Unexpected opcode $opcode at ${pc - 1} for pouch slot $slot")
            }
        }
        error("Pouch slot predicate did not terminate")
    }
}
