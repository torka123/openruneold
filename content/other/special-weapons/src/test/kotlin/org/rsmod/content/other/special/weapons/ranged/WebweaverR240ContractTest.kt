package org.rsmod.content.other.special.weapons.ranged

import dev.openrune.ServerCacheManager
import dev.openrune.filesystem.Cache
import dev.openrune.gamevals.GameValProvider
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.rsmod.api.config.refs.params

class WebweaverR240ContractTest {
    private var cache: Cache? = null

    @AfterTest
    fun closeCache() {
        cache?.close()
    }

    @Test
    fun `charged webweaver exposes safe revision 240 ranged and charge parameters`() {
        val root =
            generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                .first { Files.exists(it.resolve(".data/gamevals-binary/gamevals.dat")) }
        val mappings =
            GameValProvider.loadIsolated("$root${File.separator}", autoAssignIds = false).mappings
        val spotanims = assertNotNull(mappings["spotanim"])
        val sequences = assertNotNull(mappings["seq"])
        assertEquals(426, sequences["seq.human_bow"])
        assertEquals(9964, sequences["seq.human_special01_webweaver"])
        assertEquals(2283, spotanims["spotanim.wild_cave_bow_arrow_launch02"])
        assertEquals(2282, spotanims["spotanim.wild_cave_bow_arrow_travel02"])
        assertEquals(2354, spotanims["spotanim.fx_webweaver01_launch_spotanim"])
        assertEquals(2355, spotanims["spotanim.fx_webweaver01_impact_spotanim"])
        assertNotEquals(
            spotanims["spotanim.wild_cave_bow_arrow_travel"],
            spotanims["spotanim.wild_cave_bow_arrow_travel02"],
        )
        System.setProperty("user.dir", root.toString())
        GameValProvider.load("$root${File.separator}")
        cache = ServerCacheManager.init(root.resolve(".data/cache/SERVER"), 240)

        val chargedId = "obj.wild_cave_webweaver_charged".asRSCM(RSCMType.OBJ)
        val unchargedId = "obj.wild_cave_webweaver_uncharged".asRSCM(RSCMType.OBJ)
        val charged = assertNotNull(ServerCacheManager.getItem(chargedId))
        val uncharged = assertNotNull(ServerCacheManager.getItem(unchargedId))
        val unchargedVariant = charged.paramOrNull(params.uncharged_variant)
        val projectile = assertNotNull(charged.paramOrNull(params.proj_type))
        val attackAnimation = assertNotNull(charged.paramOrNull(params.attack_anim_stance1))
        val travelId = "spotanim.wild_cave_bow_arrow_travel02".asRSCM(RSCMType.SPOTANIM)

        assertNull(unchargedVariant, "The runtime must not use the generic charge reducer here.")
        assertEquals(chargedId, charged.id)
        assertEquals(unchargedId, uncharged.id)
        assertTrue(travelId >= 0)
        assertNotNull(ServerCacheManager.getProjectile(projectile.id))
        assertNotNull(ServerCacheManager.getAnim(attackAnimation.id))
        println(
            "WEBWEAVER_R240 charged=$chargedId uncharged=$unchargedId " +
                "travel=$travelId projectile=${projectile.id} attack=${attackAnimation.id} " +
                "launch=${charged.paramOrNull(params.proj_launch)?.id} " +
                "chargedOptions=${charged.interfaceOptions} unchargedOptions=${uncharged.interfaceOptions}"
        )
    }
}
