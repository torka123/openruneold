@file:OptIn(dev.openrune.types.util.UncheckedType::class)

package org.rsmod.content.bosses.yama

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.rsmod.game.inv.InvObj

class YamaDeathStorageTest {
    @Test
    fun `storage codec preserves item id count and vars`() {
        val items = listOf(InvObj(995, 250_000, 0), InvObj(30750, 1, 42))
        assertEquals(items, YamaStorageCodec.decode(YamaStorageCodec.encode(items)))
    }

    @Test
    fun `storage codec ignores incomplete and empty triples`() {
        assertEquals(emptyList(), YamaStorageCodec.decode(mutableListOf(995, 1)))
        assertEquals(emptyList(), YamaStorageCodec.decode(mutableListOf(995, 0, 0)))
        assertEquals(emptyList(), YamaStorageCodec.decode(mutableListOf(-1, 1, 0)))
    }

    @Test
    fun `storage codec retains valid records around corrupt persisted entries`() {
        assertEquals(
            listOf(InvObj(995, 250_000, 0), InvObj(30750, 1, 42)),
            YamaStorageCodec.decode(mutableListOf(995, 250_000, 0, -1, 1, 0, 30750, 1, 42, 30753)),
        )
    }

    @Test
    fun `reclaim uses inventory coins before the bank`() {
        assertEquals(
            YamaReclaimPayment(inventoryCoins = 75_000, bankCoins = 25_000),
            YamaReclaimRules.payment(fee = 100_000, inventoryCoins = 75_000, bankCoins = 50_000),
        )
        assertEquals(
            YamaReclaimPayment(inventoryCoins = 100_000, bankCoins = 0),
            YamaReclaimRules.payment(fee = 100_000, inventoryCoins = 150_000, bankCoins = 50_000),
        )
    }

    @Test
    fun `reclaim can be funded entirely by bank coins`() {
        assertEquals(
            YamaReclaimPayment(inventoryCoins = 0, bankCoins = 100_000),
            YamaReclaimRules.payment(fee = 100_000, inventoryCoins = 0, bankCoins = 100_000),
        )
    }

    @Test
    fun `reclaim rejects insufficient combined funds`() {
        assertNull(
            YamaReclaimRules.payment(fee = 100_000, inventoryCoins = 25_000, bankCoins = 74_999)
        )
    }

    @Test
    fun `free reclaim requires no coins`() {
        assertEquals(
            YamaReclaimPayment(inventoryCoins = 0, bankCoins = 0),
            YamaReclaimRules.payment(fee = 0, inventoryCoins = 0, bankCoins = 0),
        )
    }
}
