package org.rsmod.api.net.rsprot

import io.netty.buffer.Unpooled
import io.netty.buffer.UnpooledByteBufAllocator
import kotlin.test.*
import net.rsprot.compression.HuffmanCodec
import net.rsprot.compression.provider.DefaultHuffmanCodecProvider
import net.rsprot.protocol.common.client.OldSchoolClientType
import net.rsprot.protocol.game.outgoing.codec.npcinfo.extendedinfo.writer.NpcAvatarExtendedInfoDesktopWriter
import net.rsprot.protocol.game.outgoing.info.AvatarPriority
import net.rsprot.protocol.game.outgoing.info.filter.ExtendedInfoFilter
import net.rsprot.protocol.game.outgoing.info.npcinfo.NpcAvatar
import net.rsprot.protocol.game.outgoing.info.npcinfo.NpcAvatarExtendedInfo
import net.rsprot.protocol.internal.game.outgoing.info.util.ZoneIndexStorage

class RspNpcBodyModelsTest {
    @Test
    fun `r240 body bytes retain captured model semantics for current and later observers`() {
        for ((model, lowByte) in listOf(55952 to 0x90, 55940 to 0x84)) {
            val f = Fixture()
            try {
                val callerModels = mutableListOf(model)
                f.protocol.setBodyModels(callerModels)
                callerModels[0] = 1 // rsprot must receive a defensive copy.
                f.cycle("precompute")
                val expected = byteArrayOf(0x7e, 1, 0, 0, 0xda.toByte(), lowByte.toByte())
                val original = f.encode(observer = 1)
                assertEquals(10, original.size, "Four mask bytes plus six body bytes")
                assertContentEquals(expected, original.takeLast(6).toByteArray())
                f.cycle("postUpdate")
                assertEquals(0, f.flags())
                assertEquals(NpcAvatarExtendedInfo.BODY_CUSTOMISATION, f.laterFlags())
                assertContentEquals(original, f.encode(observer = 2, extraFlags = f.laterFlags()))
                f.protocol.resetBodyModels()
                f.cycle("precompute")
                val reset = f.encode(observer = 1)
                assertContentEquals(original.take(4).toByteArray() + byteArrayOf(0x7f), reset)
                f.cycle("postUpdate")
                assertEquals(0, f.laterFlags(), "New viewers need no reset after override is gone")
            } finally {
                f.cycle("reset")
            }
        }
    }

    @Test
    fun `protocol pool reset cannot leak body models to a reused avatar`() {
        val f = Fixture()
        f.protocol.setBodyModels(listOf(55940))
        f.cycle("precompute")
        f.cycle("postUpdate")
        assertEquals(NpcAvatarExtendedInfo.BODY_CUSTOMISATION, f.laterFlags())
        f.cycle("reset")
        assertEquals(0, f.flags())
        assertEquals(0, f.laterFlags())
    }

    @Test
    fun `direct protocol calls reject counts that cannot fit the model count byte`() {
        val f = Fixture()
        for (invalid in listOf(emptyList(), listOf(-1), List(256) { 1 })) {
            assertFailsWith<IllegalArgumentException> { f.protocol.setBodyModels(invalid) }
            assertEquals(0, f.flags())
        }
    }

    /**
     * Reflection drives rsprot's internal cycle boundary only in tests; production uses public
     * APIs.
     */
    private class Fixture {
        val info =
            NpcAvatarExtendedInfo(
                1,
                ExtendedInfoFilter { _, _, _, _ -> true },
                listOf(NpcAvatarExtendedInfoDesktopWriter()),
                UnpooledByteBufAllocator.DEFAULT,
                DefaultHuffmanCodecProvider(huffman()),
            )
        private val avatar =
            NpcAvatar::class
                .java
                .constructors
                .single { it.parameterCount == 13 }
                .newInstance(
                    1,
                    12596,
                    0,
                    2910,
                    9571,
                    0,
                    0,
                    AvatarPriority.NORMAL,
                    false,
                    0,
                    15,
                    info,
                    ZoneIndexStorage(16),
                ) as NpcAvatar
        val protocol = RspNpcInfo(avatar)

        private fun huffman(): HuffmanCodec {
            val lengths = Unpooled.wrappedBuffer(ByteArray(256) { 8 })
            return try {
                HuffmanCodec.create(lengths)
            } finally {
                lengths.release()
            }
        }

        fun cycle(name: String): Any? = method(name).invoke(info)

        fun flags(): Int = cycle("getFlags") as Int

        fun laterFlags(): Int = cycle("getLowToHighResChangeExtendedInfoFlags") as Int

        private fun method(prefix: String) =
            NpcAvatarExtendedInfo::class.java.methods.single {
                it.name.startsWith(prefix + "$") || it.name.startsWith(prefix + "-")
            }

        fun encode(observer: Int, extraFlags: Int = 0): ByteArray {
            val buffer = Unpooled.buffer()
            try {
                method("pExtendedInfo")
                    .invoke(info, OldSchoolClientType.DESKTOP, buffer, observer, 0, 1, extraFlags)
                return ByteArray(buffer.readableBytes()).also { buffer.readBytes(it) }
            } finally {
                buffer.release()
            }
        }
    }
}
