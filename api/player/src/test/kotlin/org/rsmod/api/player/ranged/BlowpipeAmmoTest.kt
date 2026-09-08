package org.rsmod.api.player.ranged

import dev.openrune.ServerCacheManager
import dev.openrune.gamevals.GameValProvider
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

@ResourceLock("server-cache")
class BlowpipeAmmoTest {
    @Test
    fun `toxic state remains configurable when only one supply is depleted`() {
        val scalesDepleted =
            assertNotNull(
                BlowpipeAmmo.resolveConsumption(
                    currentDarts = 100,
                    currentScales = 1,
                    darts = 1,
                    scales = 1,
                    usesScales = true,
                )
            )
        assertEquals(99, scalesDepleted.dartsLeft)
        assertEquals(0, scalesDepleted.scalesLeft)
        assertFalse(scalesDepleted.becameEmpty)
        assertFalse(scalesDepleted.canFire)

        val dartsDepleted =
            assertNotNull(
                BlowpipeAmmo.resolveConsumption(
                    currentDarts = 1,
                    currentScales = 100,
                    darts = 1,
                    scales = 1,
                    usesScales = true,
                )
            )
        assertEquals(0, dartsDepleted.dartsLeft)
        assertEquals(99, dartsDepleted.scalesLeft)
        assertFalse(dartsDepleted.becameEmpty)
        assertFalse(dartsDepleted.canFire)

        val fullyEmpty =
            assertNotNull(BlowpipeAmmo.resolveConsumption(1, 1, 1, 1, usesScales = true))
        assertTrue(fullyEmpty.becameEmpty)
        assertFalse(fullyEmpty.canFire)
    }

    @Test
    fun `dragon darts scales unload and uncharge preserve exact packed contents`() {
        val root = Path.of("").toAbsolutePath()
        GameValProvider.load("$root${File.separator}")
        val cache = ServerCacheManager.init(root.resolve(".data/cache/SERVER"), 240)
        try {
            val invType =
                assertNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM(RSCMType.INV)))
            val inventory = Inventory(invType, arrayOfNulls(invType.size))
            inventory[0] = InvObj("obj.toxic_blowpipe")
            val dragonDart =
                assertNotNull(ServerCacheManager.getItem("obj.dragon_dart".asRSCM(RSCMType.OBJ)))

            assertEquals(500, BlowpipeAmmo.storeDarts(inventory, 0, dragonDart, 500))
            assertEquals(500, BlowpipeAmmo.darts(inventory[0]))
            assertEquals(
                "obj.dragon_dart",
                BlowpipeAmmo.storedDart(inventory[0])?.type?.internalName,
            )
            assertEquals("obj.toxic_blowpipe_loaded".asRSCM(RSCMType.OBJ), inventory[0]?.id)

            assertEquals(750, BlowpipeAmmo.storeScales(inventory, 0, 750))
            assertEquals(750, BlowpipeAmmo.scales(inventory[0]))

            val unloaded = assertNotNull(BlowpipeAmmo.unloadDarts(inventory, 0))
            assertEquals(500, unloaded.count)
            assertEquals(dragonDart.id, unloaded.dart?.type?.id)
            assertEquals(0, BlowpipeAmmo.darts(inventory[0]))
            assertEquals(750, BlowpipeAmmo.scales(inventory[0]))
            assertEquals("obj.toxic_blowpipe_loaded".asRSCM(RSCMType.OBJ), inventory[0]?.id)

            val uncharged = assertNotNull(BlowpipeAmmo.uncharge(inventory, 0))
            assertEquals(0, uncharged.dartCount)
            assertEquals(750, uncharged.scaleCount)
            assertEquals("obj.toxic_blowpipe".asRSCM(RSCMType.OBJ), inventory[0]?.id)
        } finally {
            cache.close()
        }
    }
}
