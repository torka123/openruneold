package dev.openrune

import dev.openrune.filesystem.Cache
import dev.openrune.gamevals.GameValProvider
import dev.openrune.util.WeaponCategory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class WeaponBasicAttackR240ContractTest {
    private var cache: Cache? = null

    @AfterEach
    fun closeCache() {
        cache?.close()
    }

    @Test
    fun `weapon categories and aliases match revision 240`() {
        val root =
            generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                .first { Files.exists(it.resolve(".data/gamevals-binary/gamevals.dat")) }
        val mappings =
            GameValProvider.loadIsolated("$root${File.separator}", autoAssignIds = false).mappings
        val objects = requireNotNull(mappings["obj"])
        val expected =
            mapOf(
                "obj.arkan_blade" to (30955 to WeaponCategory.SlashSword),
                "obj.thunder_khopesh" to (30388 to WeaponCategory.SlashSword),
                "obj.deadman_thunder_khopesh" to (33041 to WeaponCategory.SlashSword),
                "obj.noxious_halberd" to (29796 to WeaponCategory.Polearm),
                "obj.br_noxious_halberd" to (33178 to WeaponCategory.Polearm),
            )

        System.setProperty("user.dir", root.toString())
        GameValProvider.load("$root${File.separator}")
        cache = ServerCacheManager.init(root.resolve(".data/cache/SERVER"), 240)

        for ((alias, contract) in expected) {
            val (expectedId, expectedCategory) = contract
            assertEquals(expectedId, objects[alias], alias)
            val item = ServerCacheManager.getItem(expectedId)
            assertNotNull(item, alias)
            assertEquals(expectedCategory, requireNotNull(item).resolvedWeaponCategory, alias)
        }
    }
}
