package dev.openrune

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ZulrahR240CacheAuditTest {
    @Test
    fun `revision 240 resolves every active Zulrah combat asset semantically`() {
        ServerCacheManager.init(240)

        val npcs =
            mapOf(
                "npc.snakeboss_boss_ranged" to 2042,
                "npc.snakeboss_boss_melee" to 2043,
                "npc.snakeboss_boss_magic" to 2044,
                "npc.snakeboss_minion_melee" to 2045,
                "npc.snakeboss_minion_magic" to 2046,
            )
        npcs.forEach { (name, expected) ->
            val id = name.asRSCM(RSCMType.NPC)
            assertEquals(expected, id, name)
            val type = requireNotNull(ServerCacheManager.getNpc(id)) { name }
            assertEquals(name, type.internalName)
            println(
                "ZULRAH_NPC semantic=$name id=$id display=${type.name} size=${type.size} " +
                    "combat=${type.combatLevel} stats=${type.attack}/${type.strength}/${type.defence}/" +
                    "${type.ranged}/${type.magic}/${type.hitpoints} actions=${type.actions} params=${type.paramsRaw}"
            )
        }

        val locs = mapOf("loc.snakeboss_poisoncloud" to 11700, "loc.snakeboss_exit" to 11701)
        locs.forEach { (name, expected) ->
            val id = name.asRSCM(RSCMType.LOC)
            assertEquals(expected, id, name)
            val type = requireNotNull(ServerCacheManager.getObject(id)) { name }
            assertEquals(name, type.internalName)
            println(
                "ZULRAH_LOC semantic=$name id=$id display=${type.name} size=${type.width}x${type.length} " +
                    "blockWalk=${type.blockWalk} blockRange=${type.blockRange} actions=${type.actions}"
            )
        }

        val sequences =
            mapOf(
                "seq.snakeboss_attack_acidx1" to 5069,
                "seq.snakeboss_attack_acidx3" to 5068,
                "seq.snakeboss_spawn" to 5071,
                "seq.snakeboss_sinkfast" to 5072,
                "seq.snakeboss_emergefast" to 5073,
                "seq.snakeboss_death" to 5804,
                "seq.snakeboss_attack_tail_left" to 5806,
                "seq.snakeboss_attack_tail_right" to 5807,
                "seq.snakeboss_defend" to 5808,
                "seq.snakeboss_pet_attack" to 1741,
                "seq.snakeboss_pet_death" to 2408,
            )
        sequences.forEach { (name, expected) ->
            val id = name.asRSCM(RSCMType.SEQ)
            assertEquals(expected, id, name)
            val type = requireNotNull(ServerCacheManager.getAnim(id)) { name }
            assertEquals(name, type.internalName)
            println(
                "ZULRAH_SEQ semantic=$name id=$id priority=${type.priority} " +
                    "tickDuration=${type.tickDuration} totalDelay=${type.totalDelay}"
            )
        }

        val graphics =
            mapOf(
                "spotanim.snakeboss_orb" to 1044,
                "spotanim.snakeboss_double_orb" to 1045,
                "spotanim.snakeboss_fireball" to 1046,
                "spotanim.snakeboss_egg" to 1047,
                "spotanim.snakeboss_minion_spell" to 1230,
            )
        graphics.forEach { (name, expected) ->
            val id = name.asRSCM(RSCMType.SPOTANIM)
            assertEquals(expected, id, name)
            // Spot animations are client definitions; the semantic gameval is the server contract.
            // Its resolved ID is checked against the captured revision-234 ID above.
            println("ZULRAH_SPOTANIM semantic=$name id=$id")
        }
    }

    @Test
    fun `revision 240 resolves captured Zulrah sounds`() {
        ServerCacheManager.init(240)

        val synths =
            mapOf(
                "synth.firewave_cast_and_fire" to 162,
                "synth.firewave_hit" to 163,
                "synth.waterwave_cast_and_fire" to 213,
                "synth.zulrah_ranged_impact" to 224,
                "synth.zulrah_snakeling_send" to 788,
                "synth.zulrah_cloud_send" to 790,
                "synth.zulrah_snakeling_impact" to 794,
                "synth.zulrah_cloud_dissipate_a" to 795,
                "synth.zulrah_cloud_dissipate_b" to 796,
                "synth.zulrah_hazard_spawn" to 1930,
            )
        synths.forEach { (name, expected) ->
            assertEquals(expected, name.asRSCM(RSCMType.SYNTH), name)
        }
    }

    @Test
    fun `revision 240 resolves captured Zulrah projectile controllers`() {
        ServerCacheManager.init(240)

        val expected =
            mapOf(
                "projanim.zulrah_attack" to listOf(18, 370, 50, 40, 0, 15, 0, 5),
                "projanim.zulrah_cloud_primary" to listOf(19, 370, 20, 40, 0, 50, 0, 0),
                "projanim.zulrah_cloud_secondary" to listOf(20, 370, 20, 45, 10, 75, 0, 0),
                "projanim.zulrah_snakeling_egg" to listOf(21, 370, 20, 40, 0, 80, 0, 0),
            )
        expected.forEach { (name, values) ->
            val id = name.asRSCM(RSCMType.PROJANIM)
            assertEquals(values[0], id, name)
            val type = requireNotNull(ServerCacheManager.getProjectile(id)) { name }
            assertEquals(
                values,
                listOf(
                    id,
                    type.startHeight,
                    type.endHeight,
                    type.delay,
                    type.angle,
                    type.lengthAdjustment,
                    type.progress,
                    type.stepMultiplier,
                ),
                name,
            )
        }
    }
}
