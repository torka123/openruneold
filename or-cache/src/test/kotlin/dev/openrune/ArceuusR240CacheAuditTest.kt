package dev.openrune

import dev.openrune.codec.osrs.impl.ItemServerCodec
import dev.openrune.definition.codec.ItemCodec
import dev.openrune.filesystem.Cache
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import io.netty.buffer.Unpooled
import java.nio.ByteBuffer
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Real revision-240 data, including the quest check executed by the client. */
class ArceuusR240CacheAuditTest {
    private fun <T : Any> assertNotNull(value: T?, message: String = "Missing cache value"): T = requireNotNull(value) { message }
    @Test
    fun `Arceuus spells have no quest locks in either runtime cache`() {
        ServerCacheManager.init(240).close()
        val book = "param.spell_spellbook".asRSCM()
        val quest = "param.spell_questreq_struct".asRSCM()
        val live = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            val spells = ServerCacheManager.getItems().values.filter { it.paramsRaw?.get(book) == 3 }
            assertTrue(spells.size >= 45)
            for (spell in spells) {
                assertEquals(-1, spell.paramsRaw?.get(quest) ?: -1, "Server quest lock on spell ${spell.id}")
                val bytes = assertNotNull(live.data(2, 10, spell.id))
                val client = ItemCodec(240).loadData(spell.id, bytes)
                assertEquals(-1, client.params?.get(quest) ?: -1, "Client quest lock on spell ${spell.id}")
                // A map decoder hides earlier duplicate values. Inspect every raw occurrence too.
                assertTrue(clientQuestValues(bytes, quest).all { it == -1 },
                    "Client still contains a quest requirement on spell ${spell.id}: ${clientQuestValues(bytes, quest)}")
            }
            // Native proc 4128 reads this exact obj param, compares it to -1 and bypasses the
            // quest-completion branch for -1. Keep the decoded instruction evidence in test output.
            val instructions = decode(assertNotNull(live.data(12, 4128)))
            assertEquals(listOf(33 to 0, 0 to quest, 6515 to 0, 34 to 1, 33 to 1, 0 to -1, 7 to 1, 6 to 26), instructions.take(8))
            println("ARCEUUS_NATIVE_QUEST_CHECK script=4128 instructions=$instructions")
        } finally { live.close() }
    }

    @Test
    fun `unlock preserves every other client spell parameter and original opcode`() {
        ServerCacheManager.init(240).close()
        val live = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            val quest = "param.spell_questreq_struct".asRSCM()
            val bytes = assertNotNull(live.data(2, 10, "obj.dmm_ref_legs".asRSCM()))
            val codec = ItemCodec(240)
            val before = codec.loadData(21832, bytes)
            val params = before.params.orEmpty() + (quest to -1)
            val result = removeClientQuestRequirement(bytes, quest)
            assertEquals(bytes.size, result.size)
            assertTrue(clientQuestValues(result, quest).all { it == -1 })
            assertTrue(result.contentEquals(removeClientQuestRequirement(result, quest)))
            assertEquals(params, codec.loadData(21832, result).params)
            assertEquals(before.name, codec.loadData(21832, result).name)
        } finally { live.close() }
    }

    @Test
    fun `unlock replaces original and duplicated quest values without changing other bytes`() {
        val quest = 1189
        val buffer = Unpooled.buffer()
        try {
            buffer.writeByte(2).writeBytes("Test spell\u0000".toByteArray())
            buffer.writeByte(249).writeByte(3)
            buffer.writeByte(0).writeMedium(quest)
            val firstValue = buffer.writerIndex()
            buffer.writeInt(81)
            buffer.writeByte(0).writeMedium(1).writeInt(96) // An unrelated integer requirement.
            buffer.writeByte(1).writeMedium(2).writeBytes("description\u0000".toByteArray())
            buffer.writeByte(249).writeByte(1)
            buffer.writeByte(0).writeMedium(quest)
            val secondValue = buffer.writerIndex()
            buffer.writeInt(-1) // The previous installer appended this while leaving 81 intact.
            buffer.writeByte(0)
            val bytes = ByteArray(buffer.readableBytes()).also { buffer.getBytes(0, it) }
            assertEquals(listOf(81, -1), clientQuestValues(bytes, quest))
            val result = removeClientQuestRequirement(bytes, quest)
            assertEquals(listOf(-1, -1), clientQuestValues(result, quest))
            val expected = bytes.copyOf()
            for (offset in listOf(firstValue, secondValue)) {
                expected.fill(0xFF.toByte(), offset, offset + Int.SIZE_BYTES)
            }
            assertTrue(expected.contentEquals(result), "Only the quest value bytes may change")
            assertTrue(result.contentEquals(removeClientQuestRequirement(result, quest)))
            assertEquals(listOf(81, -1), clientQuestValues(bytes, quest), "Input must not be mutated")
        } finally { buffer.release() }
    }

    @Test
    fun `all Arceuus spell and summon assets exist in the loaded cache`() {
        ServerCacheManager.init(240).close()
        val live = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            val animations = (8970..8981).toList() + listOf(7198, 7163, 3865, 9046, 9047, 9048, 5540, 5512, 5568)
            for (id in animations) {
                assertNotNull(ServerCacheManager.getAnim(id), "Sequence $id")
                assertEquals(id, RSCM.getReverseMapping(RSCMType.SEQ, id).asRSCM())
            }
            val spots = (1851..1885).toList() + (1903..1907).toList() + listOf(1288, 1289, 1290, 1296, 1298)
            for (id in spots) {
                val spot = assertNotNull(ServerCacheManager.getSpotAnimation(id), "Spotanim $id")
                assertTrue(assertNotNull(live.data(7, spot.modelId), "Spotanim $id model").isNotEmpty())
                if (spot.animationId >= 0) assertNotNull(ServerCacheManager.getAnim(spot.animationId))
                assertEquals(id, RSCM.getReverseMapping(RSCMType.SPOTANIM, id).asRSCM())
            }
            val sounds = listOf(5025, 5026, 5027, 5029, 5030, 5031, 5032, 5033, 5034, 5035, 5036, 5037, 5038, 5040, 5042, 5044, 5045, 5046, 5049, 5050, 5051, 5052, 5053, 5054, 5055, 5056, 5059, 5060, 5061, 2700, 918, 211, 212)
            for (id in sounds) {
                assertTrue(assertNotNull(live.data(4, id), "Sound $id").isNotEmpty())
                assertEquals(id, RSCM.getReverseMapping(RSCMType.SYNTH, id).asRSCM())
            }
            for (id in (10878..10886).toList() + (7018..7039).toList()) {
                val npc = assertNotNull(ServerCacheManager.getNpc(id), "NPC $id")
                assertTrue(npc.name.isNotBlank())
                println("ARCEUUS_NPC $id name=${npc.name} hp=${npc.hitpoints}")
            }
            assertEquals("Book of the Dead", assertNotNull(ServerCacheManager.getItem(25818)).name)
            for (id in 13447..13512) {
                val item = ServerCacheManager.getItem(id) ?: continue
                println("ARCEUUS_HEAD $id ${item.name} cert=${item.certtemplate}")
            }
        } finally { live.close() }
    }

    private fun clientQuestValues(bytes: ByteArray, quest: Int): List<Int> {
        val codec = ItemCodec(240)
        val item = codec.createDefinition()
        val buffer = Unpooled.wrappedBuffer(bytes)
        return try {
            buildList {
                while (true) {
                    when (val opcode = buffer.readUnsignedByte().toInt()) {
                        0 -> break
                        249 -> repeat(buffer.readUnsignedByte().toInt()) {
                            val string = buffer.readUnsignedByte().toInt() == 1
                            val key = buffer.readUnsignedMedium()
                            if (string) {
                                while (buffer.readByte() != 0.toByte()) { }
                            } else {
                                val value = buffer.readInt()
                                if (key == quest) add(value)
                            }
                        }
                        else -> with(codec) { item.read(opcode, buffer) }
                    }
                }
            }
        } finally { buffer.release() }
    }

    private fun decode(data: ByteArray): List<Pair<Int, Int>> {
        val tail = ByteBuffer.wrap(data, data.size - 2, 2).short.toInt() and 65535
        val end = data.size - 2 - tail - 16
        val b = ByteBuffer.wrap(data)
        while (b.get() != 0.toByte()) {}
        return buildList {
            while (b.position() < end) {
                val op = b.short.toInt() and 65535
                require(op != 3) { "Unexpected string in native quest check" }
                val arg = if (op in setOf(9, 21, 38, 39) || op >= 100) b.get().toInt() and 255 else b.int
                add(op to arg)
            }
        }
    }
}
