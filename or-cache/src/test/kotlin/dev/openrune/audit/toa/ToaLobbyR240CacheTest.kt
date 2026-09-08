package dev.openrune.audit.toa

import dev.openrune.ServerCacheManager
import dev.openrune.filesystem.Cache
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class ToaLobbyR240CacheTest {
    @Test
    fun `revision 240 lobby components and cache crosswalks are exact`() {
        ServerCacheManager.init(240)

        val expectedComponents =
            mapOf(
                772 to (17..65).toSet(),
                774 to setOf(4, 6, 7, 8, 9, 32, 48, 52, 62, 63, 64, 69, 70, 71, 72, 83, 84),
                776 to setOf(4, 5),
            )
        expectedComponents.forEach { (interfaceId, children) ->
            val type = requireNotNull(ServerCacheManager.getInterface(interfaceId))
            children.forEach { child ->
                assertNotNull(type.components[child], "$interfaceId:$child")
            }
        }
        assertNotNull(ServerCacheManager.getInterface(777))
        assertNotNull(ServerCacheManager.getVarp(3603))
        assertEquals(
            6594,
            ServerCacheManager.getInterface(772)?.components?.get(0)?.onLoad?.first(),
        )
        assertEquals(
            6615,
            ServerCacheManager.getInterface(774)?.components?.get(0)?.onLoad?.first(),
        )
        assertEquals(
            6609,
            ServerCacheManager.getInterface(776)?.components?.get(0)?.onLoad?.first(),
        )

        val partyRows = requireNotNull(ServerCacheManager.getEnum(4793))
        assertEquals(
            (0..44).associateWith { slot -> (772 shl 16) or (17 + slot) },
            partyRows.values,
        )
        assertEquals(
            mapOf(((772 shl 16) or 63) to 0, ((772 shl 16) or 64) to 1, ((772 shl 16) or 65) to 2),
            requireNotNull(ServerCacheManager.getEnum(4794)).values,
        )
        assertEquals(
            mapOf(
                ((774 shl 16) or 4) to 0,
                ((774 shl 16) or 62) to 1,
                ((774 shl 16) or 64) to 2,
                ((774 shl 16) or 72) to 3,
                ((774 shl 16) or 63) to 4,
                ((774 shl 16) or 69) to 5,
                ((774 shl 16) or 83) to 6,
                ((774 shl 16) or 84) to 7,
                ((774 shl 16) or 6) to 8,
                ((774 shl 16) or 7) to 9,
                ((774 shl 16) or 8) to 10,
                ((774 shl 16) or 9) to 11,
            ),
            requireNotNull(ServerCacheManager.getEnum(4792)).values,
        )

        val structIds =
            listOf(
                417,
                418,
                419,
                420,
                421,
                422,
                423,
                424,
                425,
                426,
                427,
                428,
                429,
                430,
                431,
                432,
                433,
                434,
                435,
                436,
                437,
                438,
                439,
                440,
                441,
                442,
                444,
                445,
                447,
                448,
                449,
                542,
                543,
                603,
                752,
                949,
                1275,
                1276,
                1278,
                1688,
                2874,
                2933,
                2934,
                2971,
                5892,
                5893,
            )
        val invocationEnum = requireNotNull(ServerCacheManager.getEnum(4664))
        assertEquals(
            structIds.withIndex().associate { it.index to it.value },
            invocationEnum.values,
        )
        val names =
            listOf(
                "Try Again",
                "Persistence",
                "Softcore Run",
                "Hardcore Run",
                "Walk for It",
                "Jog for It",
                "Run for It",
                "Sprint for It",
                "Need Some Help?",
                "Need Less Help?",
                "No Help Needed",
                "Walk the Path",
                "Pathseeker",
                "Pathfinder",
                "Pathmaster",
                "Quiet Prayers",
                "Deadly Prayers",
                "On a Diet",
                "Dehydration",
                "Overly Draining",
                "Lively Larvae",
                "More Overlords",
                "Blowing Mud",
                "Medic!",
                "Aerial Assault",
                "Not Just a Head",
                "Arterial Spray",
                "Blood Thinners",
                "Upset Stomach",
                "Double Trouble",
                "Keep Back",
                "Stay Vigilant",
                "Feeling Special?",
                "Mind the Gap!",
                "Gotta Have Faith",
                "Jungle Japes",
                "Shaking Things Up",
                "Boulderdash",
                "Ancient Haste",
                "Acceleration",
                "Penetration",
                "Overclocked",
                "Overclocked 2",
                "Insanity",
                "Blazing Tombs I",
                "Blazing Tombs II",
            )
        val raidLevels =
            listOf(
                5,
                10,
                15,
                25,
                10,
                15,
                20,
                25,
                15,
                25,
                40,
                50,
                15,
                40,
                50,
                20,
                20,
                15,
                30,
                15,
                5,
                15,
                10,
                15,
                10,
                15,
                10,
                5,
                15,
                20,
                10,
                15,
                20,
                10,
                10,
                5,
                10,
                10,
                10,
                10,
                10,
                10,
                10,
                50,
                200,
                200,
            )
        structIds.forEachIndexed { row, structId ->
            val struct = requireNotNull(ServerCacheManager.getStruct(structId))
            val params = requireNotNull(struct.params)
            assertEquals(row + 1, params[1159], "invocation id row $row")
            assertEquals(names[row], params[1160], "invocation name row $row")
            assertEquals(raidLevels[row], params[1162], "invocation level row $row")
        }
    }

    @Test
    fun `revision 240 lobby client scripts retain exact signatures`() {
        val expected =
            mapOf(
                1558 to Signature(4, 1, 0),
                6594 to Signature(2, 0, 0),
                6597 to Signature(0, 0, 0),
                6601 to Signature(1, 1, 0),
                6609 to Signature(2, 0, 0),
                6615 to Signature(30, 0, 0),
                6722 to Signature(1, 1, 0),
                6727 to Signature(0, 1, 0),
                6729 to Signature(8, 0, 0),
                6749 to Signature(7, 0, 0),
                6750 to Signature(8, 0, 0),
                6751 to Signature(8, 0, 0),
            )
        val cache = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            expected.forEach { (scriptId, signature) ->
                val data = requireNotNull(cache.data(CLIENT_SCRIPT_INDEX, scriptId))
                assertEquals(signature, signature(data), "client script $scriptId")
            }
        } finally {
            cache.close()
        }
    }

    private fun signature(data: ByteArray): Signature {
        val trailerLength = uShort(data, data.size - 2)
        val headerOffset = data.size - 2 - trailerLength - 16
        val footer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        footer.position(headerOffset)
        footer.int
        repeat(3) { footer.short }
        return Signature(
            intArgs = footer.short.toInt() and 0xFFFF,
            stringArgs = footer.short.toInt() and 0xFFFF,
            longArgs = footer.short.toInt() and 0xFFFF,
        )
    }

    private fun uShort(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private data class Signature(val intArgs: Int, val stringArgs: Int, val longArgs: Int)

    private companion object {
        const val CLIENT_SCRIPT_INDEX = 12
    }
}
