package org.rsmod.content.skills.magic.spell.attacks.standard

import dev.openrune.ServerCacheManager
import dev.openrune.filesystem.Cache
import dev.openrune.gamevals.GameValProvider
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.util.WeaponCategory
import dev.openrune.util.Wearpos
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.worn.EquipmentChecks
import org.rsmod.api.spells.autocast.AutocastSpells
import org.rsmod.api.spells.autocast.AutocastWeapons
import org.rsmod.api.spells.runes.unlimited.UnlimitedRuneRepository
import org.rsmod.game.inv.InvObj

class TwinflameR240ContractTest {
    private var cache: Cache? = null

    @AfterTest
    fun closeCache() {
        cache?.close()
    }

    @Test
    fun `Twinflame item and spell effects match revision 240`() {
        val root =
            generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                .first { Files.exists(it.resolve(".data/gamevals-binary/gamevals.dat")) }
        val mappings =
            GameValProvider.loadIsolated("$root${File.separator}", autoAssignIds = false).mappings
        val objects = assertNotNull(mappings["obj"])
        val npcs = assertNotNull(mappings["npc"])
        val sequences = assertNotNull(mappings["seq"])
        val spotanims = assertNotNull(mappings["spotanim"])
        val projectiles = assertNotNull(mappings["projanim"])

        assertEquals(TWINFLAME_STAFF_ID, objects["obj.twinflame_staff"])
        assertEquals(FIRE_WALL_ID, npcs["npc.rs_loc_attack_fire_hitbox"])
        assertEquals(ICE_WALL_ID, npcs["npc.rs_loc_attack_ice_hitbox"])
        assertNotNull(sequences["seq.human_caststrike_staff"])
        assertNotNull(sequences["seq.human_staff_pummel"])
        assertNotNull(projectiles["projanim.magic_spell"])

        val effectStems =
            listOf(
                "windstrike",
                "waterstrike",
                "earthstrike",
                "firestrike",
                "windbolt",
                "waterbolt",
                "earthbolt",
                "firebolt",
                "windblast",
                "waterblast",
                "earthblast",
                "fireblast",
                "windwave",
                "waterwave",
                "earthwave",
                "firewave",
                "windsurge",
                "watersurge",
                "earthsurge",
                "firesurge",
            )
        for (stem in effectStems) {
            assertNotNull(spotanims["spotanim.${stem}_casting"], "$stem casting")
            assertNotNull(spotanims["spotanim.${stem}_travel"], "$stem travel")
            assertNotNull(spotanims["spotanim.${stem}_impact"], "$stem impact")
        }

        System.setProperty("user.dir", root.toString())
        GameValProvider.load("$root${File.separator}")
        cache = ServerCacheManager.init(root.resolve(".data/cache/SERVER"), 240)
        val staff = assertNotNull(ServerCacheManager.getItem(TWINFLAME_STAFF_ID))
        val fireWall = assertNotNull(ServerCacheManager.getNpc(FIRE_WALL_ID))
        val iceWall = assertNotNull(ServerCacheManager.getNpc(ICE_WALL_ID))

        assertEquals("npc.rs_loc_attack_fire_hitbox", fireWall.internalName)
        assertEquals("npc.rs_loc_attack_ice_hitbox", iceWall.internalName)

        assertEquals("obj.twinflame_staff", staff.internalName)
        assertEquals(WeaponCategory.Staff, staff.resolvedWeaponCategory)
        assertEquals(7, staff.param(params.attack_stab))
        assertEquals(-1, staff.param(params.attack_slash))
        assertEquals(28, staff.param(params.attack_crush))
        assertEquals(12, staff.param(params.attack_magic))
        assertEquals(2, staff.param(params.defence_stab))
        assertEquals(3, staff.param(params.defence_slash))
        assertEquals(1, staff.param(params.defence_crush))
        assertEquals(12, staff.param(params.defence_magic))
        assertEquals(22, staff.param(params.melee_strength))
        assertEquals(0, staff.param(params.magic_damage))
        assertEquals(Wearpos.RightHand.slot, staff.wearpos1)
        assertFalse(staff.wearpos2 == Wearpos.LeftHand.slot)
        assertFalse(staff.wearpos3 == Wearpos.LeftHand.slot)
        assertEquals(6, staff.param(params.attackrate))

        val autocast = AutocastWeapons(AutocastSpells())
        assertTrue(autocast.canAutocastSpellbook(staff, Spellbook.Standard))
        assertNotNull(autocast.getVarBits(staff))
        assertFalse(autocast.canAutocastSpellbook(staff, Spellbook.Ancients))
        assertFalse(autocast.canAutocastSpellbook(staff, Spellbook.Arceuus))

        val unlimitedRunes = UnlimitedRuneRepository()
        val equippedStaff = InvObj("obj.twinflame_staff")
        val fireRune = assertNotNull(ServerCacheManager.getItem(objects.getValue("obj.firerune")))
        val waterRune = assertNotNull(ServerCacheManager.getItem(objects.getValue("obj.waterrune")))
        assertTrue(unlimitedRunes.isHighPrioritySource(fireRune, equippedStaff, lefthand = null))
        assertTrue(unlimitedRunes.isHighPrioritySource(waterRune, equippedStaff, lefthand = null))

        val magicId = "stat.magic".asRSCM(RSCMType.STAT)
        val requirements =
            listOfNotNull(
                staff.paramOrNull(BaseParams.statreq1_skill)?.id?.let {
                    it to staff.param(BaseParams.statreq1_level)
                },
                staff.paramOrNull(BaseParams.statreq2_skill)?.id?.let {
                    it to staff.param(BaseParams.statreq2_level)
                },
            )
        assertEquals(60, requirements.toMap()[magicId])
        assertEquals(6, EquipmentChecks.TWINFLAME_SPELL_ATTACK_RATE)

        val castSequence = sequences["seq.human_caststrike_staff"]
        val projectile = projectiles["projanim.magic_spell"]
        val effects =
            effectStems.joinToString(separator = ";") { stem ->
                val casting = spotanims["spotanim.${stem}_casting"]
                val travel = spotanims["spotanim.${stem}_travel"]
                val impact = spotanims["spotanim.${stem}_impact"]
                "$stem:$casting/$travel/$impact"
            }
        println(
            "TWINFLAME_R240 item=${staff.id} cast=$castSequence projectile=$projectile effects=$effects"
        )
    }

    private companion object {
        const val TWINFLAME_STAFF_ID = 30634
        const val FIRE_WALL_ID = 14152
        const val ICE_WALL_ID = 14153
    }
}
