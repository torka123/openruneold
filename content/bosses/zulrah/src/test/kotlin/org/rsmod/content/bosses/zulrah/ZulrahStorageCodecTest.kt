@file:OptIn(dev.openrune.types.util.UncheckedType::class)

package org.rsmod.content.bosses.zulrah

import kotlin.test.Test
import kotlin.test.assertEquals
import org.rsmod.game.inv.InvObj

class ZulrahStorageCodecTest {
    @Test
    fun `persistent storage codec round trips ids counts and vars`() {
        val items = listOf(InvObj(4151, 1, 7), InvObj(995, 100_000, 0))
        assertEquals(items, ZulrahStorageCodec.decode(ZulrahStorageCodec.encode(items)))
    }

    @Test
    fun `corrupt or non-positive records are ignored`() {
        assertEquals(emptyList(), ZulrahStorageCodec.decode(listOf(1, 0, 0, 2)))
    }
}
