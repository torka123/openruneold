package org.rsmod.content.drops.tables.monsters

import dev.openrune.ServerCacheManager
import dev.openrune.gamevals.GameValProvider
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.mockito.Mockito.mock
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.droptable.DropTableRegistry
import org.rsmod.content.drops.toml.ContentDropTableTomlResolver
import org.rsmod.content.quest.manager.QuestRequirementResolver
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
class GrotesqueGuardiansDropTableTest {
    @Test
    fun `final dusk registers the wiki two roll reward table`() {
        assertEquals("Grotesque Guardians Drops", grotesqueGuardiansDropTable.tableIdentifier)
        assertEquals(
            listOf("npc.gargboss_dusk_phase1_defensive", "npc.gargboss_dusk_phase4"),
            grotesqueGuardiansDropTable.npcs,
        )
        assertEquals(2, GROTESQUE_MAIN_ROLLS)
        assertEquals(142.0, grotesqueCommonTable.maxRoll)
        assertEquals(8.0, grotesqueSharkTable.maxRoll)
        assertEquals(3000.0, grotesquePerRollTable.maxRoll)
        assertTrue(grotesqueCommonTable.tableEntries.isNotEmpty())
    }

    @Test
    fun `per roll weights encode current unique rates`() {
        assertEquals(GROTESQUE_PER_ROLL_TOTAL / 250, 12)
        assertEquals(GROTESQUE_PER_ROLL_TOTAL / 500, 6)
        assertEquals(GROTESQUE_PER_ROLL_TOTAL / 750, 4)
        assertEquals(GROTESQUE_PER_ROLL_TOTAL / 1000, 3)
        assertEquals(2969, GROTESQUE_PER_ROLL_TOTAL - 12 - 6 - 6 - 4 - 3)
    }

    @Test
    fun `collection log rewards use revision 240 gamevals`() {
        val root = projectRoot()
        System.setProperty("user.dir", root.toString())
        GameValProvider.load("$root${File.separator}")
        val cache = ServerCacheManager.init(root.resolve(".data/cache/SERVER"), 240)
        try {
            val mappings =
                mapOf(
                    "obj.granite_dust" to 21726,
                    "obj.tourmaline_core" to 21730,
                    "obj.granite_gloves" to 21736,
                    "obj.granite_ring" to 21739,
                    "obj.granite_hammer" to 21742,
                    "obj.jar_of_stone" to 21745,
                    "obj.dawnpet" to 21748,
                )
            mappings.forEach { (gameval, id) ->
                assertEquals(gameval, RSCM.getReverseMapping(RSCMType.OBJ, id))
            }
            assertEquals("npc.gargboss_dusk_phase4", RSCM.getReverseMapping(RSCMType.NPC, 7888))
        } finally {
            cache.close()
        }
    }

    @Test
    fun `native registry resolves transformed Dusk using original actor identity without awarding Dawn`() {
        if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        val registry =
            DropTableRegistry(
                ContentDropTableTomlResolver(
                    mock(AreaChecker::class.java),
                    mock(QuestRequirementResolver::class.java),
                )
            )
        val dusk = Npc(assertNotNull(ServerCacheManager.getNpc(7851)), CoordGrid(1689, 4573))
        dusk.transmog(assertNotNull(ServerCacheManager.getNpc(7888)), Int.MAX_VALUE)
        assertEquals(7851, dusk.type.id)
        assertSame(grotesqueGuardiansDropTable, registry.forNpc(dusk))
        val dawn = Npc(assertNotNull(ServerCacheManager.getNpc(7852)), CoordGrid(1701, 4573))
        assertNull(registry.forNpc(dawn))
    }

    private fun projectRoot(): Path =
        generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve(".data/gamevals-binary/gamevals.dat")) }
}
