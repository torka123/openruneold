package org.rsmod.content.bosses.yama

import dev.openrune.ServerCacheManager
import dev.openrune.filesystem.Cache
import dev.openrune.gamevals.GameValProvider
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.SAME_THREAD)
class YamaVoiceR240CacheTest {
    private var cache: Cache? = null

    @AfterTest
    fun closeCache() {
        cache?.close()
    }

    @Test
    fun `voice of yama is fixed to its revision 240 spawn tile`() {
        val root =
            generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                .first { Files.exists(it.resolve(".data/gamevals-binary/gamevals.dat")) }
        System.setProperty("user.dir", root.toString())
        GameValProvider.load("$root${File.separator}")
        cache = ServerCacheManager.init(root.resolve(".data/cache/SERVER"), 240)

        val voice =
            assertNotNull(ServerCacheManager.getNpc("npc.voice_of_yama".asRSCM(RSCMType.NPC)))
        assertEquals(0, voice.wanderRange)
        assertEquals(MoveRestrict.NoMove, voice.moveRestrict)

        val throne = assertNotNull(ServerCacheManager.getNpc(YamaAssets.THRONE_PARENT))
        val occupied = assertNotNull(ServerCacheManager.getNpc(YamaAssets.THRONE_OCCUPIED))
        val empty = assertNotNull(ServerCacheManager.getNpc(YamaAssets.THRONE_EMPTY))
        assertEquals(YamaAssets.VARBIT_THRONE_STATE, throne.multiVarBit)
        assertEquals(
            listOf(YamaAssets.THRONE_OCCUPIED, YamaAssets.THRONE_EMPTY, -1),
            throne.multiNpc.toList(),
        )
        assertEquals("Talk-to", occupied.actions.getOpOrNull(0))
        assertEquals("Challenge", occupied.actions.getOpOrNull(2))
        assertEquals(null, empty.actions.getOpOrNull(0))
        assertEquals(null, empty.actions.getOpOrNull(2))
        YamaAssets.npcIds.forEach { assertNotNull(ServerCacheManager.getNpc(it), "NPC $it") }
        YamaAssets.locIds.forEach {
            assertNotNull(ServerCacheManager.getObject(it), "location $it")
        }
        YamaAssets.sequences.forEach {
            assertNotNull(
                dev.openrune.rscm.RSCM.getReverseMapping(RSCMType.SEQ, it),
                "sequence $it",
            )
        }
        YamaAssets.spotanims.forEach {
            assertNotNull(
                dev.openrune.rscm.RSCM.getReverseMapping(RSCMType.SPOTANIM, it),
                "spotanim $it",
            )
        }
        assertNotNull(
            ServerCacheManager.getHealthBar(
                YamaAssets.VOID_FLARE_CHARGE_HEADBAR.asRSCM(RSCMType.HEADBAR)
            ),
            YamaAssets.VOID_FLARE_CHARGE_HEADBAR,
        )
    }
}
