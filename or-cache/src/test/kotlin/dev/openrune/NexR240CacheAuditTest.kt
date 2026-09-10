package dev.openrune

import dev.openrune.definition.codec.NPCCodec
import dev.openrune.filesystem.Cache
import dev.openrune.rscm.RSCM.asRSCM
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/** Actual SERVER/LIVE decoding. Asset identity is not proof of attack timing or gameplay parity. */
class NexR240CacheAuditTest {
    @Test
    fun `native definitions distinguish Nex forms guards encounter reaver and pet`() {
        for (id in 11278..11282) {
            val npc = requireNotNull(ServerCacheManager.getNpc(id))
            assertEquals("Nex", npc.name)
            assertEquals(
                listOf(3, 3400, 315, 200, 260, 230, 350),
                listOf(
                    npc.size,
                    npc.hitpoints,
                    npc.attack,
                    npc.strength,
                    npc.defence,
                    npc.magic,
                    npc.ranged,
                ),
            )
            // Death-form operations are suppressed by runtime masks, not by this cache option.
            assertEquals(if (id == 11279) null else "Attack", npc.actions.getOpOrNull(1), "NPC $id")
        }
        for ((offset, name) in listOf("Fumus", "Umbra", "Cruor", "Glacies").withIndex()) {
            val npc = requireNotNull(ServerCacheManager.getNpc(11283 + offset))
            assertEquals(name, npc.name)
            assertEquals(
                listOf(1, 500, 200, 200),
                listOf(npc.size, npc.hitpoints, npc.magic, npc.defence),
            )
            assertEquals(5, npc.paramsRaw?.get("param.attackrate".asRSCM()))
            assertEquals(25, npc.paramsRaw?.get("param.npc_magic_damage_bonus".asRSCM()))
            assertEquals(836, npc.paramsRaw?.get("param.death_anim".asRSCM()))
            // SERVER attack_anim5247 is deliberately NOT certified: captures show1979 instead.
        }
        val reaver = requireNotNull(ServerCacheManager.getNpc(11294))
        assertEquals("Blood Reaver", reaver.name)
        assertEquals(
            listOf(2, 150, 190, 100),
            listOf(reaver.size, reaver.hitpoints, reaver.magic, reaver.defence),
        )
        assertEquals(5, reaver.paramsRaw?.get("param.attackrate".asRSCM()))
        assertEquals(9194, reaver.paramsRaw?.get("param.attack_anim".asRSCM()))
        assertEquals(9192, reaver.paramsRaw?.get("param.death_anim".asRSCM()))
        assertTrue(requireNotNull(ServerCacheManager.getNpc(11277)).isFollower)
        assertFalse(requireNotNull(ServerCacheManager.getNpc(11276)).isFollower)
    }

    @Test
    fun `LIVE minion locomotion and models remain independent of server attack overrides`() {
        val live = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            val codec = NPCCodec(240)
            for (id in (11278..11286).toList() + listOf(11277, 11294)) {
                val npc = codec.loadData(id, requireNotNull(live.data(2, 9, id)))
                assertTrue(npc.models?.isNotEmpty() == true, "NPC $id models")
                for (model in npc.models.orEmpty()) assertTrue(
                    requireNotNull(live.data(7, model)).isNotEmpty()
                )
                if (id in 11283..11286) {
                    assertEquals(808, npc.standAnim)
                    assertEquals(819, npc.walkAnim)
                }
                if (id == 11294) {
                    assertEquals(9195, npc.standAnim)
                    assertEquals(9193, npc.walkAnim)
                    assertEquals(listOf(43217), npc.models?.toList())
                }
            }
        } finally {
            live.close()
        }
    }

    @Test
    fun `Nex and captured minion presentation assets resolve to real linked models and sequences`() {
        val linkedSequences =
            listOf(
                1981,
                1987,
                7615,
                1969,
                1966,
                1969,
                1967,
                1958,
                1965,
                1960,
                1980,
                664,
                9197,
                9198,
                9199,
                9200,
                9201,
                9201,
                8991,
                9191,
                9202,
            )
        val spots =
            linkedSequences.mapIndexed { index, seq -> (1997 + index) to seq }.toMap() +
                mapOf(368 to 1964, 372 to 1968, 390 to 1986)
        val live = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            for (id in (9177..9203).toList() + listOf(1979, 836)) {
                assertNotNull(ServerCacheManager.getAnim(id), "Native sequence $id")
                assertTrue(requireNotNull(live.data(2, 12, id)).isNotEmpty(), "LIVE sequence $id")
            }
            for ((id, seq) in spots) {
                val spot = requireNotNull(ServerCacheManager.getSpotAnimation(id))
                assertEquals(seq, spot.animationId, "Spotanim $id sequence")
                assertNotNull(ServerCacheManager.getAnim(seq))
                assertTrue(
                    requireNotNull(live.data(7, spot.modelId)).isNotEmpty(),
                    "Spotanim $id model",
                )
            }
            // Explicit packet sounds; no assertion about normal-mode cadence or delay.
            for (id in listOf(183, 180, 178, 175, 106, 102, 171, 110, 4497, 5303)) assertTrue(
                requireNotNull(live.data(4, id)).isNotEmpty(),
                "Sound $id",
            )
        } finally {
            live.close()
        }
    }

    @Test
    fun `temporary geometry altar statistics components and key identities exist in revision 240`() {
        for (id in (42937..42944).toList() + listOf(42965, 42967, 42968)) assertNotNull(
            ServerCacheManager.getObject(id)
        )
        assertEquals(0, requireNotNull(ServerCacheManager.getObject(42942)).blockWalk)
        for (id in listOf(42943, 42944)) assertEquals(
            1,
            requireNotNull(ServerCacheManager.getObject(id)).blockWalk,
        )
        assertNull(requireNotNull(ServerCacheManager.getObject(42943)).actions.getOpOrNull(0))
        assertEquals(
            "Attack",
            requireNotNull(ServerCacheManager.getObject(42944)).actions.getOpOrNull(0),
        )
        val altar = requireNotNull(ServerCacheManager.getObject(42965))
        assertEquals(3, altar.width)
        assertEquals(3, altar.length)
        assertEquals("Pray", altar.actions.getOpOrNull(0))
        assertEquals("Teleport", altar.actions.getOpOrNull(1))
        assertEquals("Frozen key", requireNotNull(ServerCacheManager.getItem(26356)).name)
        assertEquals("Ecumenical key shard", requireNotNull(ServerCacheManager.getItem(26388)).name)
        for (id in listOf(13180, 13183, 13184, 13191) + (13652..13662)) assertNotNull(
            ServerCacheManager.getVarbit(id)
        )
        assertNotNull(ServerCacheManager.getVarp(3269))
        val live = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            for (component in listOf(2, 6, 9, 11, 13, 15, 17, 19)) assertTrue(
                requireNotNull(live.data(3, 732, component)).isNotEmpty(),
                "Statistics732:$component",
            )
        } finally {
            live.close()
        }
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            ServerCacheManager.init(240).close()
        }
    }
}
