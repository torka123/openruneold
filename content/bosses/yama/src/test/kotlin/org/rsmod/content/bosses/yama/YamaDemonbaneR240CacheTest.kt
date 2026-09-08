package org.rsmod.content.bosses.yama

import dev.openrune.ServerCacheManager
import dev.openrune.filesystem.Cache
import dev.openrune.gamevals.GameValProvider
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.combat.commons.magic.MagicSpellChecks
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.config.refs.params

@Execution(ExecutionMode.SAME_THREAD)
class YamaDemonbaneR240CacheTest {
    private var cache: Cache? = null

    @AfterTest
    fun closeCache() {
        cache?.close()
    }

    @Test
    fun `revision 240 demonbane spell tokens and npc attributes are exact`() {
        val root =
            generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                .first { Files.exists(it.resolve(".data/gamevals-binary/gamevals.dat")) }
        System.setProperty("user.dir", root.toString())
        GameValProvider.load("$root${File.separator}")
        cache = ServerCacheManager.init(root.resolve(".data/cache/SERVER"), 240)

        val matches =
            (0..40_000).mapNotNull { id ->
                val type = ServerCacheManager.getItem(id) ?: return@mapNotNull null
                val spellName = type.paramOrNull(BaseParams.spell_name) ?: return@mapNotNull null
                if (spellName.contains("demonbane", ignoreCase = true)) {
                    id to spellName
                } else null
            }
        assertEquals(
            listOf(
                20398 to "Inferior Demonbane",
                20399 to "Superior Demonbane",
                20400 to "Dark Demonbane",
            ),
            matches,
        )
        matches.forEach { (id, _) ->
            check(MagicSpellChecks.isDemonbaneSpell(checkNotNull(ServerCacheManager.getItem(id))))
        }
        assertEquals(
            1,
            checkNotNull(ServerCacheManager.getNpc(YamaAssets.BOSS)).param(params.demon),
        )
        assertEquals(
            0,
            checkNotNull(ServerCacheManager.getNpc(YamaAssets.JUDGE)).param(params.demon),
        )
    }
}
