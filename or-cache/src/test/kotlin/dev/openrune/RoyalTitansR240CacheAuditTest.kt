package dev.openrune

import dev.openrune.cache.MAPS
import dev.openrune.filesystem.Cache
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.map.square.MapSquareGrid

class RoyalTitansR240CacheAuditTest {
    @Test
    fun `LIVE JS5 supplies phase two body models and stock corpse definitions`() {
        val live = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            val codec = dev.openrune.definition.codec.NPCCodec(240)
            for ((npc, model) in
                mapOf(12596 to 55951, 14147 to 55938, 14148 to 55952, 14149 to 55937)) {
                val definition = codec.loadData(npc, requireNotNull(live.data(2, 9, npc)))
                assertEquals(listOf(model), definition.models?.toList(), "Stock NPC $npc models")
            }
            for ((model, bytes) in mapOf(55952 to 34064, 55940 to 39475)) {
                assertEquals(
                    bytes,
                    requireNotNull(live.data(7, model, 0)).size,
                    "Phase-two model $model",
                )
            }
        } finally {
            live.close()
        }
    }

    @Test
    fun `native Titan combat consumers receive canonical stats bonuses immunities and styles`() {
        ServerCacheManager.init(240).close()
        val common =
            mapOf(
                "attackrate" to 7,
                "attack_melee" to 150,
                "melee_strength" to 0,
                "npc_attack_type" to "category.attacktype_crush".asRSCM(),
                "attack_magic" to 100,
                "npc_magic_damage_bonus" to 100,
                "attack_ranged" to 100,
                "ranged_strength" to 100,
                "defence_stab" to 12,
                "defence_slash" to 12,
                "defence_crush" to 0,
                "defence_magic" to 700,
                "defence_ranged" to 700,
                "defence_light" to 700,
                "defence_standard" to 700,
                "defence_heavy" to 700,
                "poison_immunity" to 1,
                "venom_immunity" to 2,
                "cannon_immunity" to 1,
                "burn_immunity" to 0,
                "freeze_resistance" to 100,
                "slayer_experience" to 735,
                "npc_com_xp_multiplier" to 1225,
                "elemental_weakness_percent" to 50,
            )
        for ((id, attackAnim, weakness) in
            listOf(Triple(12596, 11974, 1), Triple(14147, 11973, 3))) {
            val npc = requireNotNull(ServerCacheManager.getNpc(id))
            assertEquals(
                listOf(350, 3, 600, 300, 250, 100, 100, 150),
                listOf(
                    npc.combatLevel,
                    npc.size,
                    npc.hitpoints,
                    npc.attack,
                    npc.strength,
                    npc.defence,
                    npc.magic,
                    npc.ranged,
                ),
                "NPC $id base stats",
            )
            (common + mapOf("attack_anim" to attackAnim, "elemental_weakness_type" to weakness))
                .forEach { (name, expected) ->
                    assertEquals(
                        expected,
                        npc.paramsRaw?.get("param.$name".asRSCM()),
                        "NPC $id $name",
                    )
                }
        }
        for ((id, category, weakness) in
            listOf(Triple(14150, "slash", 1), Triple(14151, "stab", 3))) {
            val npc = requireNotNull(ServerCacheManager.getNpc(id))
            assertEquals(1, npc.size)
            assertEquals(40, npc.hitpoints)
            assertEquals(dev.openrune.util.BlockWalk.None, npc.blockWalk)
            assertEquals(64, npc.maxRange) // Native leash covers this single-map-square instance.
            assertEquals(1, npc.attackRange) // Leash is not melee reach.
            assertEquals(dev.openrune.types.MoveRestrict.Normal, npc.moveRestrict)
            val levels = if (id == 14150) listOf(35, 35, 20, 20, 20) else List(5) { 30 }
            assertEquals(
                levels,
                listOf(npc.attack, npc.strength, npc.defence, npc.magic, npc.ranged),
            )
            mapOf(
                    "attackrate" to 4,
                    "npc_attack_type" to "category.attacktype_$category".asRSCM(),
                    "elemental_weakness_type" to weakness,
                    "elemental_weakness_percent" to 100,
                )
                .forEach { (name, expected) ->
                    assertEquals(
                        expected,
                        npc.paramsRaw?.get("param.$name".asRSCM()),
                        "NPC $id $name",
                    )
                }
        }
    }

    @Test
    fun `revision 240 contains the exact Royal Titans entrance placement`() {
        val cache = Cache.load(Path.of(".data/cache/SERVER"))
        try {
            val matches =
                cache
                    .archives(MAPS)
                    .asSequence()
                    .filter { it <= 25286 }
                    .mapNotNull { groupId ->
                        cache.data(MAPS, groupId, 0) ?: return@mapNotNull null
                        val locData = cache.data(MAPS, groupId, 1) ?: return@mapNotNull null
                        val squareX = groupId shr 8
                        val squareZ = groupId and 0xFF
                        val decoded = MapLocListDecoder.decode(InlineByteBuf(locData))
                        val placements = mutableListOf<LocPlacement>()
                        val iterator = decoded.spawns.longIterator()
                        while (iterator.hasNext()) {
                            val loc = MapLocDefinition(iterator.nextLong())
                            if (loc.id == ROYAL_TITANS_ENTRANCE) {
                                placements +=
                                    LocPlacement(
                                        x = squareX * MapSquareGrid.LENGTH + loc.localX,
                                        z = squareZ * MapSquareGrid.LENGTH + loc.localZ,
                                        level = loc.level,
                                        shape = loc.shape,
                                        angle = loc.angle,
                                    )
                            }
                        }
                        placements.takeIf(List<LocPlacement>::isNotEmpty)
                    }
                    .flatten()
                    .toList()
            assertEquals(listOf(LocPlacement(2948, 9573, 0, shape = 10, angle = 2)), matches)
        } finally {
            cache.close()
        }
    }

    private data class LocPlacement(
        val x: Int,
        val z: Int,
        val level: Int,
        val shape: Int,
        val angle: Int,
    )

    private companion object {
        const val ROYAL_TITANS_ENTRANCE = 55986
    }
}
