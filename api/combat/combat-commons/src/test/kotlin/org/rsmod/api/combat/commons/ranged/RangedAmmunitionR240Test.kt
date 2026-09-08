package org.rsmod.api.combat.commons.ranged

import dev.openrune.ServerCacheManager
import dev.openrune.filesystem.Cache
import dev.openrune.gamevals.GameValProvider
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class RangedAmmunitionR240Test {
    private var cache: Cache? = null

    @AfterTest
    fun closeCache() {
        cache?.close()
    }

    @Test
    fun `eclipse atlatl accepts only atlatl darts in revision 240`() {
        val root =
            generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                .first { Files.exists(it.resolve(".data/gamevals-binary/gamevals.dat")) }
        System.setProperty("user.dir", root.toString())
        GameValProvider.load("$root${File.separator}")
        cache = ServerCacheManager.init(root.resolve(".data/cache/SERVER"), 240)

        val atlatl = assertNotNull(ServerCacheManager.getItem(ECLIPSE_ATLATL))
        val atlatlDart = assertNotNull(ServerCacheManager.getItem(ATLATL_DART))
        val runeArrow = assertNotNull(ServerCacheManager.getItem(RUNE_ARROW))
        val magicShortbow = assertNotNull(ServerCacheManager.getItem(MAGIC_SHORTBOW))

        assertEquals(
            ATLATL_TRAVEL_SPOTANIM,
            assertNotNull(RangedAmmunition.projectileTravel(atlatl, atlatlDart)).id,
        )
        assertIs<RangedAmmunition.Validation.Valid>(
            RangedAmmunition.validateArrows(atlatl, atlatlDart)
        )
        assertIs<RangedAmmunition.Validation.Invalid.IncorrectAmmo>(
            RangedAmmunition.validateArrows(atlatl, runeArrow)
        )
        assertIs<RangedAmmunition.Validation.Invalid.IncorrectAmmo>(
            RangedAmmunition.validateArrows(magicShortbow, atlatlDart)
        )
    }

    private companion object {
        const val ATLATL_DART = 28991
        const val ECLIPSE_ATLATL = 29000
        const val RUNE_ARROW = 892
        const val MAGIC_SHORTBOW = 861
        const val ATLATL_TRAVEL_SPOTANIM = 2795
    }
}
