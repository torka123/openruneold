package org.rsmod.content.skills.magic.spell.attacks.ancient

import dev.openrune.ServerCacheManager
import dev.openrune.gamevals.GameValProvider
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.rsmod.api.combat.commons.magic.MagicSpellChecks

class AncientSpellRulesTest {
    @Test
    fun `all sixteen stock Ancient combat spells are registered once`() {
        val expected =
            listOf(
                "obj.50_smoke_rush",
                "obj.52_shadow_rush",
                "obj.56_blood_rush",
                "obj.58_ice_rush",
                "obj.62_smoke_burst",
                "obj.64_shadow_burst",
                "obj.68_blood_burst",
                "obj.70_ice_burst",
                "obj.74_smoke_blitz",
                "obj.76_shadow_blitz",
                "obj.80_blood_blitz",
                "obj.82_ice_blitz",
                "obj.86_smoke_barrage",
                "obj.88_shadow_barrage",
                "obj.92_blood_barrage",
                "obj.94_ice_barrage",
            )
        assertEquals(expected, AncientSpellRules.definitions.map { it.obj })
        assertEquals(16, AncientSpellRules.definitions.map { it.obj }.toSet().size)
    }

    @Test
    fun `max hits match current stock Ancient progression`() {
        assertEquals(
            listOf(13, 14, 15, 16, 17, 18, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30),
            AncientSpellRules.definitions.map { it.baseMaxHit },
        )
    }

    @Test
    fun `only burst and barrage tiers are three by three area spells`() {
        for (spell in AncientSpellRules.definitions) {
            val isAreaTier = spell.obj.contains("_burst") || spell.obj.contains("_barrage")
            assertEquals(isAreaTier, spell.area, spell.obj)
        }
        assertEquals(9, AncientSpellRules.MAX_AREA_TARGETS)
    }

    @Test
    fun `element strengths match poison drain heal and freeze rules`() {
        val byObj = AncientSpellRules.definitions.associateBy { it.obj }
        assertEquals(2, byObj.getValue("obj.50_smoke_rush").effectStrength)
        assertEquals(4, byObj.getValue("obj.86_smoke_barrage").effectStrength)
        assertEquals(10, byObj.getValue("obj.52_shadow_rush").effectStrength)
        assertEquals(15, byObj.getValue("obj.88_shadow_barrage").effectStrength)
        assertEquals(25, byObj.getValue("obj.92_blood_barrage").effectStrength)
        assertEquals(8, byObj.getValue("obj.58_ice_rush").effectStrength)
        assertEquals(16, byObj.getValue("obj.70_ice_burst").effectStrength)
        assertEquals(24, byObj.getValue("obj.82_ice_blitz").effectStrength)
        assertEquals(32, byObj.getValue("obj.94_ice_barrage").effectStrength)
    }

    @Test
    fun `secondary effects require at least one final damage`() {
        assertFalse(AncientSpellRules.effectLands(-1))
        assertFalse(AncientSpellRules.effectLands(0))
        assertTrue(AncientSpellRules.effectLands(1))
        assertTrue(AncientSpellRules.effectLands(30))
    }

    @Test
    fun `revision 240 Ice spell objects resolve exact bind durations`() {
        val expected =
            mapOf(
                "obj.58_ice_rush" to 8,
                "obj.70_ice_burst" to 16,
                "obj.82_ice_blitz" to 24,
                "obj.94_ice_barrage" to 32,
            )
        for ((obj, ticks) in expected) {
            val spell = requireNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)))
            assertEquals(ticks, MagicSpellChecks.iceBindTicks(spell), obj)
            assertTrue(MagicSpellChecks.isIceSpell(spell), obj)
        }
    }

    @Test
    fun `blood healing is twenty five percent of actual damage rounded down`() {
        assertEquals(0, AncientSpellRules.bloodHeal(0))
        assertEquals(0, AncientSpellRules.bloodHeal(3))
        assertEquals(1, AncientSpellRules.bloodHeal(4))
        assertEquals(7, AncientSpellRules.bloodHeal(29))
        assertEquals(0, AncientSpellRules.bloodHeal(-1))
    }

    @Test
    fun `shadow drain floors are stable and do not stack below the percentage floor`() {
        assertEquals(90, AncientSpellRules.shadowAttackFloor(100, 10))
        assertEquals(85, AncientSpellRules.shadowAttackFloor(100, 15))
        assertEquals(0, AncientSpellRules.shadowAttackFloor(-1, 15))
    }

    @Test
    fun `single target spells use travel assets where the cache supplies them`() {
        val singles = AncientSpellRules.definitions.filterNot { it.area }
        assertTrue(singles.all { it.travel != null })
        assertTrue(AncientSpellRules.definitions.all { it.impact.isNotBlank() })
        assertTrue(AncientSpellRules.definitions.all { it.castAnim.isNotBlank() })
        assertFalse(
            AncientSpellRules.definitions.any { it.castSound.isBlank() || it.hitSound.isBlank() }
        )
    }

    @Test
    fun `ice burst and blitz use their live impact pair`() {
        val byObj = AncientSpellRules.definitions.associateBy { it.obj }
        assertEquals(
            "spotanim.ice_blitz_impact",
            byObj.getValue("obj.70_ice_burst").impact,
        )
        assertEquals(
            "spotanim.ice_burst_impact",
            byObj.getValue("obj.82_ice_blitz").impact,
        )
    }

    @Test
    fun `all Ancient impacts begin at the target feet`() {
        val byObj = AncientSpellRules.definitions.associateBy { it.obj }
        assertEquals(0, byObj.getValue("obj.62_smoke_burst").impactHeight)
        assertEquals(0, byObj.getValue("obj.68_blood_burst").impactHeight)
        assertEquals(0, byObj.getValue("obj.92_blood_barrage").impactHeight)
        assertEquals(0, byObj.getValue("obj.70_ice_burst").impactHeight)
        assertEquals(0, byObj.getValue("obj.82_ice_blitz").impactHeight)
        assertTrue(AncientSpellRules.definitions.all { it.impactHeight == 0 })
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadRevision240Cache() {
            val root =
                generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                    .first { Files.exists(it.resolve(".data/gamevals-binary/gamevals.dat")) }
            System.setProperty("user.dir", root.toString())
            synchronized(ServerCacheManager) {
                if (ServerCacheManager.objectSize() == 0) {
                    GameValProvider.load("$root${java.io.File.separator}")
                    ServerCacheManager.init(root.resolve(".data/cache/SERVER"), 240)
                }
            }
        }
    }
}
