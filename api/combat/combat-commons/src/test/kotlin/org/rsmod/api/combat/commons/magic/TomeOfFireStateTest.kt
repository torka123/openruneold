package org.rsmod.api.combat.commons.magic

import dev.openrune.ServerCacheManager
import dev.openrune.gamevals.GameValProvider
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

@ResourceLock("server-cache")
class TomeOfFireStateTest {
    @Test
    fun `pages add twenty charges each and page types cannot be mixed`() = withInventory { inv ->
        inv[0] = InvObj(ItemServerType(TomeOfFireState.EMPTY_ID))

        val added =
            assertIs<TomeOfFireState.AddPagesResult.Success>(
                TomeOfFireState.addPages(inv, 0, TomeOfFireState.PageType.Burnt, 3)
            )
        assertEquals(3, added.pagesAdded)
        assertEquals(60, added.chargesAdded)
        assertEquals(60, TomeOfFireState.charges(inv[0]))
        assertEquals(TomeOfFireState.CHARGED_ID, inv[0]?.id)
        assertEquals(TomeOfFireState.PageType.Burnt, TomeOfFireState.pageType(inv[0]))
        assertIs<TomeOfFireState.AddPagesResult.MixedPageType>(
            TomeOfFireState.addPages(inv, 0, TomeOfFireState.PageType.Searing, 1)
        )
    }

    @Test
    fun `capacity is one thousand pages and partial page occupies a page slot`() =
        withInventory { inv ->
            inv[0] = InvObj(ItemServerType(TomeOfFireState.EMPTY_ID))
            val full =
                assertIs<TomeOfFireState.AddPagesResult.Success>(
                    TomeOfFireState.addPages(
                        inv,
                        0,
                        TomeOfFireState.PageType.Searing,
                        TomeOfFireState.MAX_PAGES + 1,
                    )
                )
            assertEquals(TomeOfFireState.MAX_PAGES, full.pagesAdded)
            assertEquals(TomeOfFireState.MAX_CHARGES, TomeOfFireState.charges(inv[0]))
            assertEquals(0, TomeOfFireState.availablePageCapacity(inv[0]))

            inv[0] =
                InvObj(
                    type = ItemServerType(TomeOfFireState.CHARGED_ID),
                    vars = TomeOfFireState.packed(19, TomeOfFireState.PageType.Burnt),
                )
            assertEquals(1, TomeOfFireState.loadedPages(inv[0]))
            assertEquals(
                TomeOfFireState.MAX_PAGES - 1,
                TomeOfFireState.availablePageCapacity(inv[0]),
            )
        }

    @Test
    fun `removing pages returns only unused whole pages and empties the tome`() =
        withInventory { inv ->
            inv[0] =
                InvObj(
                    type = ItemServerType(TomeOfFireState.CHARGED_ID),
                    vars = TomeOfFireState.packed(59, TomeOfFireState.PageType.Searing),
                )

            val removed = requireNotNull(TomeOfFireState.removeAllPages(inv, 0))
            assertEquals(TomeOfFireState.PageType.Searing, removed.pageType)
            assertEquals(2, removed.pagesReturned)
            assertEquals(19, removed.partialPageChargesLost)
            assertEquals(TomeOfFireState.EMPTY_ID, inv[0]?.id)
            assertEquals(0, TomeOfFireState.charges(inv[0]))
        }

    @Test
    fun `legacy and battle royale charged variants remain active`() {
        val legacy = InvObj(ItemServerType(TomeOfFireState.CHARGED_ID))
        val battleRoyale = InvObj(ItemServerType(TomeOfFireState.BATTLE_ROYALE_ID))

        assertEquals(TomeOfFireState.MAX_CHARGES, TomeOfFireState.charges(legacy))
        assertEquals(TomeOfFireState.MAX_CHARGES, TomeOfFireState.charges(battleRoyale))
        assertTrue(TomeOfFireState.isActive(legacy))
        assertTrue(TomeOfFireState.isActive(battleRoyale))
    }

    private fun withInventory(test: (Inventory) -> Unit) {
        val root =
            generateSequence(Path.of("").toAbsolutePath(), Path::getParent).first {
                Files.isDirectory(it.resolve(".data"))
            }
        val previousUserDir = System.getProperty("user.dir")
        System.setProperty("user.dir", root.toString())
        GameValProvider.load("$root${File.separator}")
        val cache = ServerCacheManager.init(root.resolve(".data/cache/SERVER"), 240)
        try {
            val type =
                requireNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM(RSCMType.INV)))
            test(Inventory(type, arrayOfNulls(type.size)))
        } finally {
            cache.close()
            System.setProperty("user.dir", previousUserDir)
        }
    }
}
