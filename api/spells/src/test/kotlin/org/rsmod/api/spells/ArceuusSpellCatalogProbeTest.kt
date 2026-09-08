package org.rsmod.api.spells

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.gamevals.GameValProvider
import java.io.File
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.BaseParams

class ArceuusSpellCatalogProbeTest {
    @Test
    fun `revision 240 exposes the Arceuus spell catalog`() {
        val root = projectRoot()
        System.setProperty("user.dir", root.toString())
        GameValProvider.load("$root${File.separator}")
        ServerCacheManager.init(root.resolve(".data/cache/SERVER"), 240)
        val registry = MagicSpellRegistry().also { it.init() }
        val spells =
            registry
                .allSpells()
                .filter { it.obj.param(BaseParams.spell_spellbook) == ARCEUUS_SPELLBOOK }
                .sortedWith(compareBy({ it.levelReq }, { it.obj.id }))

        assertEquals(45, spells.size, "Revision 240 must expose the complete Arceuus spellbook.")
        val tokens = spells.mapTo(mutableSetOf()) { RSCM.getReverseMapping(RSCMType.OBJ, it.obj.id) }
        assertEquals(EXPECTED_SPELL_OBJECTS, tokens)
        assertTrue(spells.all { it.component.packed != 0 })
    }

    private companion object {
        const val ARCEUUS_SPELLBOOK: Int = 3
        val EXPECTED_SPELL_OBJECTS =
            setOf(
                "obj.deadman_level99_lamp",
                "obj.arceuus_minigame_teleport",
                "obj.br_mithril_platebody",
                "obj.easter_helper",
                "obj.br_mithril_platelegs",
                "obj.23_teleport_battlefront",
                "obj.br_greendhide_body",
                "obj.poh_guide_guildtrophy",
                "obj.dmm_ref_hat",
                "obj.osb8_monkey_donie",
                "obj.osb8_monkey_bob",
                "obj.osb8_monkey_cow",
                "obj.br_greendhide_chaps",
                "obj.cert_chompy_bird_obj",
                "obj.br_mithril_scimitar",
                "obj.placeholder_skillpethunter_grey",
                "obj.br_moonclan_body",
                "obj.placeholder_snakepet_orange",
                "obj.dmm_ref_torso",
                "obj.osb8_bamboo",
                "obj.osb8_monkey_unicorn",
                "obj.osb8_monkey_sheep",
                "obj.br_bass",
                "obj.br_moonclan_legs",
                "obj.br_willow_bow",
                "obj.zanarischoir_dummy",
                "obj.br_xeric_body",
                "obj.placeholder_snakepet_blue",
                "obj.placeholder_skillpethunter_black",
                "obj.br_xeric_legs",
                "obj.evil_bob_amulet_of_manspeak",
                "obj.placeholder_skillcape_ardy_hood_firecape",
                "obj.osb8_monkey_trap",
                "obj.osb8_monkey_aereck",
                "obj.osb8_unicorn_horn",
                "obj.br_swordfish",
                "obj.dmm_ref_legs",
                "obj.placeholder_vetion_pet2",
                "obj.br_adamant_scimitar",
                "obj.br_air_staff",
                "obj.placeholder_skillpethunter_gold",
                "obj.skillcape_ardy_hood_firecape",
                "obj.magictraining_guardianstatue",
                "obj.br_dragon_helm",
                "obj.poh_temp_limestonebrick",
            )

        fun projectRoot(): Path {
            var current = Path.of("").toAbsolutePath()
            repeat(8) {
                if (current.resolve(".data/cache/SERVER").toFile().isDirectory) return current
                current = current.parent ?: return@repeat
            }
            error("Unable to locate the OpenRune project root from ${Path.of("").toAbsolutePath()}")
        }
    }
}
