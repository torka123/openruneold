package org.rsmod.content.bosses.kraken

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.instances.*
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.region.Region
import org.rsmod.game.region.zone.RegionZoneCopy
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Characterizes production awakening/death using native instance identity, not source strings. */
@OptIn(InternalApi::class)
class KrakenEncounterCharacterizationTest {
    @Test fun `entry spawns exactly five owned dormant actors at their mapped coordinates`() {
        val f = Fixture()
        f.enter()
        assertEquals(CoordGrid(6440, 6438), f.player.coords)
        assertEquals(listOf(38 to 50, 35 to 50, 35 to 54, 44 to 50, 44 to 54),
            f.created.map { it.coords.x - 6400 to it.coords.z - 6400 })
        assertEquals(listOf(KrakenAssets.BOSS_WHIRLPOOL) + List(4) { KrakenAssets.TENTACLE_WHIRLPOOL },
            f.created.map { it.visType.internalName })
        assertTrue(f.created.all { it.spawnOwner == f.player.uid && !it.respawns && it.movementLocked })
        val session = assertNotNull(f.instances.sessionForPlayer(f.player))
        assertEquals(5, f.instances.npcsForInstance(session.id).size)
        assertEquals(setOf(1L), session.occupants)
    }

    @Test fun `individual tentacle activation then boss activation preserve identity stats and delay`() {
        val f = Fixture()
        f.enter()
        val boss = f.created.first()
        val tentacle = f.created[1]
        val bossUid = boss.uid
        assertTrue(f.encounters.disturb(f.player, tentacle))
        assertEquals(KrakenAssets.TENTACLE, tentacle.visType.internalName)
        assertEquals(KrakenAssets.BOSS_WHIRLPOOL, boss.visType.internalName)
        assertTrue(f.encounters.disturb(f.player, boss))
        assertEquals(bossUid, boss.uid)
        assertEquals(255, boss.hitpoints)
        assertEquals(List(4) { 120 }, f.created.drop(1).map { it.hitpoints })
        assertTrue(f.created.all { it.actionDelay == 101 && it.apRangeOverride == 10 && it.maxOpRangeOverride == 64 })
        assertFalse(f.encounters.disturb(f.player, boss))
        assertNull(f.encounters.attackDenial(f.player, boss))
    }

    @Test fun `boss completion removes tentacles once and preserves personal drop coordinate`() {
        val f = Fixture()
        f.enter()
        val boss = f.created.first()
        f.encounters.disturb(f.player, boss)
        assertEquals(f.player.coords, f.encounters.beginBossDeath(boss))
        assertEquals(f.created.drop(1), f.deleted)
        assertNull(f.encounters.beginBossDeath(boss))
        assertEquals(4, f.deleted.size)
        assertNotNull(f.encounters.attackDenial(f.player, boss))
    }

    private class Fixture {
        val clock = MapClock().apply { cycle = 100 }
        val collision = CollisionFlagMap()
        val events = EventBus()
        val players = PlayerList()
        val player = Player().apply {
            slotId = 1; uuid = 1L; observerUUID = 1L; assignUid(); coords = KRAKEN_PUBLIC_EXIT
            statMap.setCurrentLevel("stat.hitpoints", 99)
        }
        val created = mutableListOf<Npc>()
        val deleted = mutableListOf<Npc>()
        val npcs = mock(NpcRepository::class.java) { call ->
            when {
                call.method.name == "add" && call.arguments.firstOrNull() is Npc -> {
                    val npc = call.arguments[0] as Npc
                    npc.slotId = created.size + 1; npc.assignUid(); npc.currentMapClock = 100
                    created += npc
                    null
                }
                call.method.name == "del" && call.arguments.firstOrNull() is Npc -> {
                    deleted += call.arguments[0] as Npc
                    null
                }
                else -> RETURNS_DEFAULTS.answer(call)
            }
        }
        val regions = mock(RegionRepository::class.java) { call ->
            if (call.method.name == "add") {
                val region = Region(CoordGrid(6400, 6400), CoordGrid(6464, 6464), 100, 1)
                for (x in 0..7) for (z in 0..7) {
                    region.registerZone(RegionZoneCopy(ZoneKey(280 + x, 1248 + z, 0), 0, null), ZoneKey(800 + x, 800 + z, 0))
                    collision.allocateIfAbsent(6400 + x * 8, 6400 + z * 8, 0)
                }
                region
            } else RETURNS_DEFAULTS.answer(call)
        }
        val instances = InstanceManager(regions, npcs, players, events, InstanceAreaResolver(), clock, collision)
        val encounters = KrakenEncounterManager(clock, instances, npcs, AiPlayerInteractions(events, players), collision, mock(NvPCombat::class.java))
        init { players[1] = player; collision.allocateIfAbsent(player.coords.x, player.coords.z, 0) }
        fun enter() { assertTrue(encounters.enter(player)) }
    }
    companion object {
        @BeforeAll @JvmStatic fun cache() { if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close() }
    }
}
