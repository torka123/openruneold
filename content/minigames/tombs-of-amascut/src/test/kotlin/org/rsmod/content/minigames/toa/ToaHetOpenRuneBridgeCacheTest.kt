package org.rsmod.content.minigames.toa

import dev.openrune.ParamReferences.param
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.SequenceServerType
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.baseVar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.rsmod.api.config.refs.params

class ToaHetOpenRuneBridgeCacheTest {
    @Test
    fun `revision 240 het interaction and item mappings stay explicit`() {
        ToaCacheTestSupport.initRevision240()

        val mirror = assertNotNull(ServerCacheManager.getObject(45455))
        assertEquals("Pick-up", mirror.actions.getOpOrNull(0))
        assertEquals("Rotate-clockwise", mirror.actions.getOpOrNull(1))
        assertEquals("Rotate-anticlockwise", mirror.actions.getOpOrNull(2))
        assertEquals("Push", mirror.actions.getOpOrNull(3))
        assertNull(mirror.actions.getOpOrNull(4))
        assertEquals(
            "Clean",
            assertNotNull(ServerCacheManager.getObject(45457)).actions.getOpOrNull(0),
        )
        assertEquals(
            "Break",
            assertNotNull(ServerCacheManager.getObject(45462)).actions.getOpOrNull(0),
        )
        assertEquals(
            "Break",
            assertNotNull(ServerCacheManager.getObject(45464)).actions.getOpOrNull(0),
        )
        assertEquals(
            "Destroy",
            assertNotNull(ServerCacheManager.getNpc(11706)).actions.getOpOrNull(0),
        )
        assertEquals(
            "Destroy",
            assertNotNull(ServerCacheManager.getNpc(11707)).actions.getOpOrNull(0),
        )
        assertNotNull(ServerCacheManager.getObject(HetLiveAssets.EMPTY_LOC))
        assertNotNull(ServerCacheManager.getVarp(HetLiveAssets.SEAL_STATE_VARP))

        val statueParent = assertNotNull(ServerCacheManager.getObject(45468))
        assertEquals(
            listOf(
                45470,
                45471,
                45472,
                45473,
                45474,
                45475,
                45476,
                45477,
                45478,
                45480,
                45479,
                45482,
                45482,
                45481,
                45483,
                45483,
                55808,
                45484,
                45484,
                45484,
                55809,
                55809,
                55809,
                45470,
            ),
            assertNotNull(statueParent.transforms).toList(),
        )
        ToaLocs.hetPickaxeStatues.forEach { id ->
            val statue = assertNotNull(ServerCacheManager.getObject(id))
            assertEquals("Take-pickaxe", statue.actions.getOpOrNull(0))
            assertEquals("Deposit-pickaxe", statue.actions.getOpOrNull(1))
        }
        val storageVarbit =
            assertNotNull(ServerCacheManager.getVarbit(HetPickaxeStorageRules.STORAGE_VARBIT))
        assertEquals(HetPickaxeStorageRules.STORAGE_VARP, storageVarbit.baseVar.id)
        assertEquals(VarpLifetime.Perm, storageVarbit.baseVar.scope)
        HetPickaxeStorageRules.entries.forEach { entry ->
            assertEquals(
                entry.internalName,
                assertNotNull(ServerCacheManager.getItem(RSCM.getRSCM(entry.internalName)))
                    .internalName,
            )
        }

        val mirrorItem = assertNotNull(ServerCacheManager.getItem(HetLiveAssets.MIRROR_ITEM))
        val mirrorType = RSCM.getReverseMapping(RSCMType.OBJ, HetLiveAssets.MIRROR_ITEM)
        assertEquals("obj.toa_het_mirror", mirrorType)
        assertEquals(mirrorItem.id, mirrorType.asRSCM(RSCMType.OBJ))
        assertEquals("Place", mirrorItem.interfaceOptions[0])

        val runePickaxe =
            assertNotNull(ServerCacheManager.getItem("obj.rune_pickaxe".asRSCM(RSCMType.OBJ)))
        assertNotNull(runePickaxe.paramOrNull(params.skill_anim))
        assertNotNull(runePickaxe.paramOrNull(param<SequenceServerType>("skill_wall_anim")))
    }
}
