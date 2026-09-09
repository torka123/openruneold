package org.rsmod.content.bosses.alchemicalhydra

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.events.interact.AiPlayerEvents
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.region.Region
import org.rsmod.game.region.zone.RegionZoneCopy
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Executes the existing registration and native NPC/instance/hit pipelines before migration. */
@OptIn(InternalApi::class)
class AlchemicalHydraNativeTest {
    @Test
    fun `entry maps the nine-zone template and owns one observation boss`() {
        val f = HydraFixture()
        assertEquals(CoordGrid(6431, 6428), f.player.coords)
        assertEquals(CoordGrid(6444, 6441), f.npc.coords)
        assertEquals(8615, f.npc.visType.id)
        assertEquals(1100, f.npc.hitpoints)
        assertEquals(f.player.uid, f.npc.spawnOwner)
        assertTrue(f.npc.movementLocked)
        val session = assertNotNull(f.instances.sessionForPlayer(f.player))
        assertEquals(listOf(f.npc), f.instances.npcsForInstance(session.id))
        assertEquals(setOf(1L), session.occupants)
        f.attackAt(100)
        assertTrue(f.hits().isEmpty())
    }

    @Test
    fun `door activates original actor then launches two independently rolled projectiles`() {
        val f = HydraFixture()
        f.activate()
        assertEquals(CoordGrid(6437, 6434), f.player.coords)
        assertFalse(f.npc.movementLocked)
        f.attackAt(100)
        assertTrue(f.hits().isEmpty())
        f.attackAt(101)
        val projectiles = f.projectiles()
        assertEquals(2, projectiles.size)
        assertEquals(listOf(26, 26), f.hits().map { it.second.damage })
        assertTrue(f.hits().all { it.second.type == HitType.Magic })
        assertTrue(f.hits().all { it.first == projectiles.first().serverCycles })
        assertEquals(107, f.npc.actionDelay)
        val expected =
            ProjAnim.fromNpcToPlayer(
                f.npc,
                f.player,
                AlchemicalHydraAssets.MAGIC_PROJECTILE,
                AlchemicalHydraAssets.BASIC_PROJECTILE,
            )
        assertEquals(List(2) { expected }, projectiles)
        assertEquals(2, f.accuracyRolls)
    }

    @Test
    fun `opening ranged style preserves animation projectile and independent misses`() {
        val f = HydraFixture(ranged = true, accuracyResults = listOf(true, false))
        f.activate()
        f.attackAt(101)
        assertEquals(AlchemicalHydraAssets.GREEN_RANGE_ANIM, f.npc.pendingSequence.id)
        assertEquals(listOf(26, 0), f.hits().map { it.second.damage })
        assertTrue(f.hits().all { it.second.type == HitType.Ranged })
        assertEquals(1, f.damageRolls)
        assertEquals(2, f.accuracyRolls)
    }

    @Test
    fun `normal prayer is snapshotted at launch for both hits`() {
        val f = HydraFixture()
        f.activate()
        VarPlayerIntMapSetter.set(f.player, "varbit.prayer_protectfrommagic", 1)
        f.attackAt(101)
        VarPlayerIntMapSetter.set(f.player, "varbit.prayer_protectfrommagic", 0)
        assertEquals(listOf(0, 0), f.hits().map { it.second.damage })
    }

    @Test
    fun `first poison follows three basics and next follows nine more`() {
        val f = HydraFixture()
        f.activate()
        val normals = mutableListOf<Pair<Int, HitType>>()
        val poisons = mutableListOf<Int>()
        for (tick in 101..180) {
            val previousHits = f.hits().size
            val previousProjectiles = f.projectiles().size
            f.attackAt(tick)
            if (f.hits().size > previousHits) normals += tick to f.hits().last().second.type
            else if (f.projectiles().size > previousProjectiles) poisons += tick
        }
        assertEquals(listOf(119, 179), poisons)
        assertEquals(
            listOf(101, 107, 113, 125, 131, 137, 143, 149, 155, 161, 167, 173),
            normals.map { it.first },
        )
        assertEquals(
            List(3) { HitType.Magic } +
                List(3) { HitType.Ranged } +
                List(3) { HitType.Magic } +
                List(3) { HitType.Ranged },
            normals.map { it.second },
        )
        assertEquals(30, f.projectiles().size)
    }

    @Test
    fun `wrong vent empowers both normal hits and right vent reduces green maximum`() {
        val f = HydraFixture()
        f.activate()
        val session = assertNotNull(f.instances.sessionForPlayer(f.player))
        f.npc.coords = assertNotNull(f.instances.localCoord(session, HydraVent.BLUE.coord))
        f.clock.cycle = 110
        f.encounters.tickAll()
        f.attackAt(110)
        assertEquals(listOf(27, 27), f.hits().takeLast(2).map { it.second.damage })
        f.npc.coords = assertNotNull(f.instances.localCoord(session, HydraVent.RED.coord))
        f.clock.cycle = 118
        f.encounters.tickAll()
        f.attackAt(118)
        assertEquals(listOf(17, 17), f.hits().takeLast(2).map { it.second.damage })
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}

@OptIn(InternalApi::class)
private class HydraFixture(ranged: Boolean = false, accuracyResults: List<Boolean> = listOf(true)) {
    val clock = MapClock(100)
    val events = EventBus()
    val collision = CollisionFlagMap()
    val player =
        Player().apply {
            slotId = 1
            uuid = 1L
            assignUid()
            coords = HYDRA_PUBLIC_EXIT
            statMap.setBaseLevel("stat.hitpoints", 99)
            statMap.setCurrentLevel("stat.hitpoints", 99)
            inv = invMap.getOrPut("inv.inv")
            worn = invMap.getOrPut("inv.worn")
        }
    val players = PlayerList().apply { this[1] = player }
    val npcList = NpcList()
    val npcs = NpcRepository(clock, NpcRegistry(npcList, collision, events), npcList)
    private var allocations = 0
    private val regions =
        mock(RegionRepository::class.java) { call ->
            when (call.method.name) {
                "add" -> {
                    val base = CoordGrid(6400 + allocations * 128, 6400)
                    val region = Region(base, base.translate(72, 72), ++allocations, allocations)
                    for (level in 0..3) for (x in 0..8) for (z in 0..8) {
                        val tile = CoordGrid(base.x + x * 8, base.z + z * 8, level)
                        region.registerZone(
                            RegionZoneCopy(ZoneKey(165 + x, 1278 + z, level), 0, null),
                            ZoneKey.from(tile),
                        )
                        collision.allocateIfAbsent(tile.x, tile.z, level)
                    }
                    region
                }
                "isValid" -> true
                else -> RETURNS_DEFAULTS.answer(call)
            }
        }
    val instances =
        InstanceManager(regions, npcs, players, events, InstanceAreaResolver(), clock, collision)
    var damageRolls = 0
    var accuracyRolls = 0
    val random =
        object : GameRandom {
            override fun of(maxExclusive: Int): Int {
                if (maxExclusive == 2) return if (ranged) 1 else 0
                damageRolls++
                return maxExclusive - 1
            }

            override fun of(minInclusive: Int, maxInclusive: Int): Int {
                damageRolls++
                return maxInclusive
            }

            override fun randomDouble() = 0.5
        }
    val world = mock(WorldRepository::class.java)
    private val accuracy =
        mock(AccuracyFormulae::class.java) { call ->
            if (call.method.name.startsWith("roll")) {
                accuracyResults[(accuracyRolls++).coerceAtMost(accuracyResults.lastIndex)]
            } else RETURNS_DEFAULTS.answer(call)
        }
    val encounters =
        AlchemicalHydraEncounterManager(
            clock,
            instances,
            npcs,
            AiPlayerInteractions(events, players),
            collision,
            random,
            accuracy,
            world,
        )
    val npc: Npc

    init {
        collision.allocateIfAbsent(player.coords.x, player.coords.z, 0)
        val combat = AlchemicalHydraCombat(encounters, mock(NpcDeath::class.java), players)
        with(combat) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        assertTrue(encounters.enter(player))
        npc =
            instances.npcsForInstance(assertNotNull(instances.sessionForPlayer(player)).id).single()
    }

    fun activate() {
        assertTrue(encounters.crossDoor(player))
    }

    fun attackAt(tick: Int) {
        clock.cycle = tick
        npc.currentMapClock = tick
        npc.processedMapClock = tick
        npc.launch {
            events.publish(
                StandardNpcAccess(
                    npc,
                    this,
                    StandardNpcAccessContext(
                        { random },
                        { StandardNpcHitModifier(events) },
                        { StandardNpcHitProcessor(players, events, emptySet()) },
                    ),
                ),
                AiPlayerEvents.Ap2(player, npc),
            )
        }
    }

    fun projectiles() =
        mockingDetails(world)
            .invocations
            .filter { it.method.name == "projAnim" }
            .map { it.arguments[0] as ProjAnim }

    fun hits(): List<Pair<Int, Hit>> {
        val iterator = player.queueList.iterator() ?: return emptyList()
        return try {
            buildList {
                while (iterator.hasNext()) {
                    val q = iterator.next()
                    (q.args as? Hit)?.let { add(q.remainingCycles to it) }
                }
            }
        } finally {
            iterator.cleanUp()
        }
    }
}
