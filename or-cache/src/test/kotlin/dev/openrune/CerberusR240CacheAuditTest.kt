package dev.openrune

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.baseVar
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CerberusR240CacheAuditTest {
    @Test
    fun `revision 240 preserves every Cerberus form soul and selected visual`() {
        ServerCacheManager.init(REVISION)

        val cerberus = requireNotNull(ServerCacheManager.getNpc(5862))
        assertEquals("Cerberus", cerberus.name)
        assertEquals(5, cerberus.size)
        assertEquals(318, cerberus.combatLevel)
        assertEquals(600, cerberus.hitpoints)
        assertEquals(220, cerberus.attack)
        assertEquals(220, cerberus.strength)
        assertEquals(100, cerberus.defence)
        assertEquals(220, cerberus.magic)
        assertEquals(220, cerberus.ranged)
        assertEquals("Attack", cerberus.actions.getOpOrNull(1))
        val expectedParams = mapOf(
            "attack_stab" to 50, "attack_slash" to 0, "attack_crush" to 50,
            "attack_magic" to 0, "attack_ranged" to 50, "attack_melee" to 50,
            "defence_stab" to 50, "defence_slash" to 100, "defence_crush" to 25,
            "defence_magic" to 65, "defence_ranged" to 100,
            "defence_light" to 100, "defence_standard" to 100, "defence_heavy" to 100,
            "attackrate" to 6, "demon" to 1, "poison_immunity" to 1,
            "venom_immunity" to 2, "burn_immunity" to 1,
            "elemental_weakness_type" to 1, "elemental_weakness_percent" to 40,
            "slayer_experience" to 690, "npc_com_xp_multiplier" to 1150,
            "npc_attack_type" to "category.attacktype_stab".asRSCM(),
        )
        for (form in listOf(5862, 5863, 5866)) {
            val type = requireNotNull(ServerCacheManager.getNpc(form))
            expectedParams.forEach { (name, expected) ->
                assertEquals(expected, type.paramsRaw?.get("param.$name".asRSCM()), "NPC $form param $name")
            }
        }
        assertEquals("obj.infernal_ashes".asRSCM(), cerberus.paramsRaw?.get("param.dropped_remains".asRSCM()))
        assertEquals("varp.total_cerberus_kills".asRSCM(), cerberus.paramsRaw?.get("param.killcount_varp".asRSCM()))
        assertEquals(VarpLifetime.Perm,
            requireNotNull(ServerCacheManager.getVarp("varp.total_cerberus_kills".asRSCM())).scope)
        for (key in listOf(
            "ca_task_cerberus_killcount_1_completed",
            "ca_task_cerberus_killcount_2_completed",
            "ca_task_cerberus_mechanical_1_completed",
            "ca_task_cerberus_mechanical_2_completed",
            "ca_task_cerberus_perfection_1_completed",
            "ca_task_cerberus_perfection_2_completed",
            "ca_total_tasks_completed_cerberus",
        )) {
            val bit = requireNotNull(ServerCacheManager.getVarbit("varbit.$key".asRSCM()))
            assertEquals(VarpLifetime.Perm, bit.baseVar.scope, "$key must survive account saves")
        }

        NPCS.forEach { id ->
            val npc = requireNotNull(ServerCacheManager.getNpc(id)) { "Missing npc $id" }
            assertTrue(npc.name.isNotBlank(), "NPC $id has no name")
        }
        ANIMATIONS.forEach { assertNotNull(ServerCacheManager.getAnim(it), "Missing anim $it") }
        assertNotNull(ServerCacheManager.getObject(23105), "Missing Cerberus fire-wall object 23105")
        listOf(5863, 5866).forEach { id ->
            val npc = requireNotNull(ServerCacheManager.getNpc(id))
            assertEquals(5, npc.size, "Cerberus form $id must remain 5x5")
            assertTrue(
                (0..4).none { npc.actions.getOpOrNull(it).equals("Attack", ignoreCase = true) },
                "Inactive Cerberus form $id exposed Attack",
            )
        }
        listOf(5867, 5868, 5869).forEach { id ->
            val soul = requireNotNull(ServerCacheManager.getNpc(id))
            assertEquals(1, soul.size, "Summoned Soul $id must remain 1x1")
            assertTrue(
                (0..4).none { soul.actions.getOpOrNull(it).equals("Attack", ignoreCase = true) },
                "Summoned Soul $id exposed Attack",
            )
        }
        SPOTANIMS.forEach {
            assertNotNull(ServerCacheManager.getSpotAnimation(it), "Missing spotanim $it")
            val internal =
                requireNotNull(RSCM.getReverseMapping(RSCMType.SPOTANIM, it)) {
                    "Missing revision-240 RSCM spotanim mapping $it"
                }
            assertEquals(it, internal.asRSCM(RSCMType.SPOTANIM))
        }
        DROP_ITEMS.forEach {
            assertNotNull(
                ServerCacheManager.getItem(it.asRSCM(RSCMType.OBJ)),
                "Missing drop item mapping $it",
            )
        }
        assertEquals(
            "Unholy symbol",
            requireNotNull(ServerCacheManager.getItem("obj.blessedsnake".asRSCM(RSCMType.OBJ))).name,
        )
        val live = dev.openrune.filesystem.Cache.load(Path.of(".data/cache/LIVE"))
        try {
            SPOTANIMS.forEach { id ->
                val spot = requireNotNull(ServerCacheManager.getSpotAnimation(id))
                assertTrue(live.data(7, spot.modelId)?.isNotEmpty() == true, "Missing model for spotanim $id")
                if (spot.animationId >= 0) assertNotNull(ServerCacheManager.getAnim(spot.animationId), "Missing linked sequence for spotanim $id")
            }
            // Sound presence proves asset availability, not Cerberus-specific timing/usage.
            listOf(162, 163, 911).forEach { id ->
                assertTrue(live.data(4, id)?.isNotEmpty() == true, "Missing synth $id")
            }
        } finally {
            live.close()
        }
        assertNotNull(ServerCacheManager.getAnim(494), "Missing fire-wall animation")
    }

    @Test
    fun `western room dump preserves the verified boss anchor`() {
        val dump =
            Files.readString(Path.of(".data/raw-cache/map/npcs/cerberus.toml"))
                .replace("\r\n", "\n")
        val spawn = "npc = \"npc.cerberus_sitting\"\ncoords = \"0_19_19_22_34\""
        assertTrue(dump.contains(spawn), "Missing western Cerberus spawn:\n$spawn")
        assertEquals(4883, (19 shl 8) or 19)
        assertEquals(1238, (19 shl 6) + 22)
        assertEquals(1250, (19 shl 6) + 34)
    }

    private companion object {
        const val REVISION = 240
        val NPCS = listOf(5862, 5863, 5866, 5867, 5868, 5869)
        val ANIMATIONS =
            listOf(
                4484,
                4485,
                4486,
                4487,
                4488,
                4489,
                4490,
                4491,
                4492,
                4493,
                4494,
                4495,
                4496,
                4497,
                4498,
                4499,
                4500,
                4501,
                4502,
                659,
                8526,
                8527,
                8528,
                8529,
                8530,
                8531,
            )
        val SPOTANIMS = listOf(34, 100) + (1241..1248).toList()
        val DROP_ITEMS =
            listOf(
                "obj.rune_platebody",
                "obj.rune_chainbody",
                "obj.rune_2h_sword",
                "obj.black_dragonhide_body",
                "obj.rune_axe",
                "obj.rune_pickaxe",
                "obj.cert_battlestaff",
                "obj.rune_full_helm",
                "obj.lava_battlestaff",
                "obj.rune_halberd",
                "obj.firerune",
                "obj.soulrune",
                "obj.cert_blankrune_high",
                "obj.bloodrune",
                "obj.mcannonball",
                "obj.xbows_crossbow_bolts_runite_unfeathered",
                "obj.deathrune",
                "obj.cert_coal",
                "obj.4dose2restore",
                "obj.summer_pie",
                "obj.coins",
                "obj.cert_dragon_bones",
                "obj.blessedsnake",
                "obj.cert_wine_of_zamorak",
                "obj.cert_ashes",
                "obj.cert_fire_orb",
                "obj.cert_unidentified_torstol",
                "obj.cert_runite_ore",
                "obj.cert_uncut_diamond",
                "obj.torstol_seed",
                "obj.ranarr_seed",
                "obj.teleportscroll_cerberus",
                "obj.infernal_ashes",
                "obj.primordial_crystal",
                "obj.pegasian_crystal",
                "obj.eternal_crystal",
                "obj.smouldering_stone",
                "obj.arceuus_corpse_hellhound",
                "obj.jar_of_souls",
                "obj.hell_pet",
                "obj.trail_elite_emote_exp1",
                "obj.teleportscroll_cerberus",
                "obj.blessedsnake",
            )
    }
}
