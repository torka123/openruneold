package org.rsmod.content.minigames.toa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ToaHetPickaxeStorageTest {
    @Test
    fun `revision 240 values round trip every supported pickaxe`() {
        HetPickaxeStorageRules.entries.forEach { entry ->
            assertEquals(entry.value, HetPickaxeStorageRules.storageValueFor(entry.internalName))
            assertEquals(entry.internalName, HetPickaxeStorageRules.itemFor(entry.value))
        }
    }

    @Test
    fun `unconfigured storage and taken storage both provide the osrs bronze fallback`() {
        assertEquals("obj.bronze_pickaxe", HetPickaxeStorageRules.itemFor(0))
        assertEquals(
            "obj.bronze_pickaxe",
            HetPickaxeStorageRules.itemFor(HetPickaxeStorageRules.FREE_BRONZE_VALUE),
        )
        assertNull(HetPickaxeStorageRules.itemForSwap(0))
        assertNull(HetPickaxeStorageRules.itemForSwap(HetPickaxeStorageRules.FREE_BRONZE_VALUE))
    }

    @Test
    fun `cosmetic and charged variants retain their exact persistent values`() {
        assertEquals(16, HetPickaxeStorageRules.storageValueFor("obj.league_trailblazer_pickaxe"))
        assertEquals(17, HetPickaxeStorageRules.storageValueFor("obj.trailblazer_pickaxe"))
        assertEquals(18, HetPickaxeStorageRules.storageValueFor("obj.trailblazer_pickaxe_empty"))
        assertEquals(
            19,
            HetPickaxeStorageRules.storageValueFor("obj.trailblazer_pickaxe_no_infernal"),
        )
        assertEquals(20, HetPickaxeStorageRules.storageValueFor("obj.trailblazer_reloaded_pickaxe"))
        assertEquals(
            21,
            HetPickaxeStorageRules.storageValueFor("obj.trailblazer_reloaded_pickaxe_empty"),
        )
        assertEquals(
            22,
            HetPickaxeStorageRules.storageValueFor("obj.trailblazer_reloaded_pickaxe_no_infernal"),
        )
    }

    @Test
    fun `invalid storage values and non pickaxes are rejected safely`() {
        assertNull(HetPickaxeStorageRules.itemFor(24))
        assertNull(HetPickaxeStorageRules.itemFor(31))
        assertNull(HetPickaxeStorageRules.storageValueFor("obj.coins"))
    }
}
