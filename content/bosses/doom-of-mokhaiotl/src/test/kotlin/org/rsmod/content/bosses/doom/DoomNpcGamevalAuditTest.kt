package org.rsmod.content.bosses.doom

import dev.openrune.ServerCacheManager
import dev.openrune.filesystem.Cache
import dev.openrune.gamevals.GameValProvider
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.config.refs.params

@Execution(ExecutionMode.SAME_THREAD)
class DoomNpcGamevalAuditTest {
    private var cache: Cache? = null

    @AfterTest
    fun closeCache() {
        cache?.close()
    }

    @Test
    fun `revision 240 Doom mappings and demon metadata are exact`() {
        val root =
            generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                .first { Files.exists(it.resolve(".data/gamevals-binary/gamevals.dat")) }
        System.setProperty("user.dir", root.toString())
        GameValProvider.load("$root${File.separator}")
        cache = ServerCacheManager.init(root.resolve(".data/cache/SERVER"), 240)

        val expected =
            listOf(
                14707 to "npc.dom_boss",
                14708 to "npc.dom_boss_shielded",
                14709 to "npc.dom_boss_burrowed",
                14710 to "npc.dom_demonic_energy",
                14711 to "npc.dom_demonic_energy_range",
                14712 to "npc.dom_demonic_energy_mage",
                14713 to "npc.dom_demonic_energy_melee",
                14714 to "npc.dom_shockwave_path_node",
                14715 to "npc.dom_shockwave_shield",
            )
        assertEquals(
            expected,
            expected.map { (id, _) -> id to RSCM.getReverseMapping(RSCMType.NPC, id) },
        )
        (14707..14713).forEach { id ->
            assertEquals(1, checkNotNull(ServerCacheManager.getNpc(id)).param(params.demon))
        }
        (14714..14715).forEach { id ->
            assertEquals(0, checkNotNull(ServerCacheManager.getNpc(id)).param(params.demon))
        }

        DoomIncomingAttackRules.demonbaneWeaponTypes.forEach { key ->
            val weapon = checkNotNull(ServerCacheManager.getItem(key.asRSCM(RSCMType.OBJ))) {
                "Missing revision-240 demonbane item mapping: $key"
            }
            check(DoomIncomingAttackRules.isDemonbaneWeapon(weapon))
        }
    }
}
