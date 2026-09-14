package dev.openrune

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Installation audit only. Native gameplay tests separately prove exactly-once kill credit. */
class BossMigrationKcCacheAuditTest {
    @Test
    fun `installed revision 240 spawn identities carry the native killcount metadata`() {
        ServerCacheManager.init(240).close()
        val key = "param.killcount_varp".asRSCM(RSCMType.PARAM)
        val notify = "param.killcount_notify".asRSCM(RSCMType.PARAM)
        for ((name, variable) in
            listOf(
                "npc.gargboss_dusk_phase1_defensive" to "varp.total_gargboss_kills",
                "npc.snakeboss_boss_ranged" to "varp.total_snakeboss_kills",
                "npc.muspah" to "varp.total_muspah_kills",
                "npc.nex" to "varp.total_nex_kills",
            )) {
            val type = requireNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)))
            assertEquals(variable.asRSCM(RSCMType.VARP), type.paramsRaw?.get(key), name)
            if (!name.contains("dusk")) assertEquals(0, type.paramsRaw?.get(notify), name)
            println(
                "INSTALLED_NATIVE_KC npc=${type.id} name=$name varp=${type.paramsRaw?.get(key)} notify=${type.paramsRaw?.get(notify)}"
            )
        }
    }
}
