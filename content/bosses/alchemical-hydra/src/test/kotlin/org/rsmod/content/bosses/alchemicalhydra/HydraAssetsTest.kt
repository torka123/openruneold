package org.rsmod.content.bosses.alchemicalhydra

import dev.openrune.ServerCacheManager
import dev.openrune.gamevals.GameValProvider
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import java.nio.file.Paths
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.SAME_THREAD)
class HydraAssetsTest {
    @Test
    fun `revision 241 cache contains Hydra forms animations locations and instance row`() {
        val originalDirectory = System.getProperty("user.dir")
        val repositoryDirectory = Paths.get(originalDirectory).parent.parent.parent
        System.setProperty("user.dir", repositoryDirectory.toString())
        try {
            GameValProvider.load(repositoryDirectory.toString() + java.io.File.separator)
            ServerCacheManager.init(repositoryDirectory.resolve(".data/cache/SERVER"), 241).close()

            HydraAssets.formIds.forEach { assertNotNull(ServerCacheManager.getNpc(it), "NPC $it") }
            HydraAssets.animationIds.forEach { assertNotNull(ServerCacheManager.getAnim(it), "SEQ $it") }
            (HydraAssets.doorIds + HydraAssets.ventIds).forEach {
                assertNotNull(ServerCacheManager.getObject(it), "LOC $it")
            }
            HydraAssets.spotanimIds.forEach {
                assertNotNull(RSCM.getReverseMapping(RSCMType.SPOTANIM, it), "SPOTANIM $it")
            }
            assertNotNull(ServerCacheManager.getDbrow(RSCM.getRSCM("dbrow.instance_alchemical_hydra")))
            assertEquals(63226, RSCM.getRSCM("dbrow.instance_alchemical_hydra"))
        } finally {
            System.setProperty("user.dir", originalDirectory)
        }
    }
}
