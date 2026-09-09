package org.rsmod.content.bosses.corporealbeast

import dev.openrune.ServerCacheManager
import dev.openrune.types.NpcMode
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.events.interact.AiPlayerEvents
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.random.GameRandom
import org.rsmod.api.player.hit.DeferredPlayerHit
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Frozen baseline assertions run through registered events, real NvPCombat and player hit queues. */
@OptIn(InternalApi::class)
class CorporealBeastNativeTest {
    @AfterEach fun releaseFixtures() { framework().clearInlineMocks() }

    @Test fun `ordinary magic retains geometry damage prayer timing and four tick cadence`() {
        val f = CorpFixture()
        repeat(13) { f.attackTick() }
        assertEquals(listOf(100, 104, 108, 112), f.launches)
        val expected = ProjAnim.fromNpcToPlayer(f.npc, f.player, 316, CorporealBeastAssets.TRACKING_PROJECTILE)
        assertEquals(List(4) { expected }, f.projectiles())
        assertEquals(List(4) { 65 }, f.hits().map { it.second.damage })
        assertEquals(List(4) { expected.serverCycles }, f.hits().map { it.first })
        assertTrue(f.hits().all { it.second.type == HitType.Magic })
        assertEquals(1679, f.npc.pendingSequence?.id)
    }

    @Test fun `occasional short cooldown launches on third tick`() {
        val f = CorpFixture(short = true)
        repeat(10) { f.attackTick() }
        assertEquals(listOf(100, 103, 106, 109), f.launches)
    }

    @Test fun `both melee animations and crush maximum use native attacks on the five tile footprint`() {
        for (side in 0..1) {
            val f = CorpFixture(melee = true, side = side)
            f.player.coords = f.npc.coords.translate(5, 2)
            f.attackTick()
            assertEquals(1682 + side, f.npc.pendingSequence?.id)
            val hit = f.hits().single().second
            assertEquals(HitType.Melee, hit.type)
            assertEquals(33, hit.damage)
            assertEquals(1, f.hits().single().first)
        }
    }

    @Test fun `drain is a distinct native magic projectile`() {
        val f = CorpFixture(magic = 1)
        f.attackTick()
        assertEquals(314, f.projectiles().single().spotanim)
        assertEquals(1680, f.npc.pendingSequence?.id)
        assertEquals(65, f.hits().single().second.damage)
    }

    @Test fun `area attack snapshots its tile instead of tracking a player`() {
        val f = CorpFixture(magic = 2)
        val tile = f.player.coords
        f.attackTick()
        val projectile = f.projectiles().single()
        assertEquals(315, projectile.spotanim)
        assertEquals(tile, projectile.endCoord)
        assertEquals(0, projectile.targetIndex)
        assertEquals(1681, f.npc.pendingSequence?.id)
        assertTrue(f.hits().isEmpty())
        assertEquals(104, f.npc.actionDelay)
    }

    @Test fun `other lair and different plane cannot be targeted`() {
        for (tile in listOf(CoordGrid(2993, 4382, 2), CoordGrid(2993, 4254, 1))) {
            val f = CorpFixture()
            f.player.coords = tile
            f.attackTick()
            assertTrue(f.projectiles().isEmpty())
            assertTrue(f.hits().isEmpty())
        }
    }

    companion object {
        @BeforeAll @JvmStatic fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}

@OptIn(InternalApi::class)
private class CorpFixture(
    melee: Boolean = false,
    magic: Int = 0,
    short: Boolean = false,
    side: Int = 0,
) {
    val clock = MapClock(100)
    val events = EventBus()
    val npc = Npc(assertNotNull(ServerCacheManager.getNpc(319)), CoordGrid(2993,4254,2)).apply {
        slotId = 1; assignUid(); hitpoints = 2000; baseHitpointsLvl = 2000
        mode = NpcMode.ApPlayer2
    }
    val player = Player().apply {
        slotId = 1; uuid = 1L; assignUid(); coords = CoordGrid(3000,4255,2)
        statMap.setBaseLevel("stat.hitpoints", 99); statMap.setCurrentLevel("stat.hitpoints", 99)
        inv = invMap.getOrPut("inv.inv"); worn = invMap.getOrPut("inv.worn")
    }
    val players = PlayerList().apply { this[1] = player }
    val random = object : GameRandom {
        override fun of(maxExclusive: Int): Int = when(maxExclusive) {
            100 -> if(melee) 0 else 99
            3 -> magic
            2 -> side
            Int.MAX_VALUE -> if(short) 0 else 1
            else -> maxExclusive - 1
        }
        override fun of(minInclusive: Int, maxInclusive: Int) = maxInclusive
        override fun randomDouble() = 0.5
    }
    val world = mock(WorldRepository::class.java)
    val queues = WorldQueueList()
    val npcs = mock(NpcRepository::class.java)
    val accuracy = mock(AccuracyFormulae::class.java) { call ->
        if(call.method.name.startsWith("roll")) true else RETURNS_DEFAULTS.answer(call)
    }
    val manager = CorporealBeastEncounterManager(clock, NvPCombat(accuracy, mock(MaxHitFormulae::class.java), world), accuracy, random, players, npcs, world, queues, CollisionFlagMap())
    val launches = mutableListOf<Int>()
    init {
        val script = CorporealBeastCombat(manager, mock(NpcDeath::class.java))
        with(script) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
    }
    fun attackTick() {
        val count = hits().size + projectiles().size
        npc.currentMapClock = clock.cycle; npc.processedMapClock = clock.cycle
        npc.launch {
            events.publish(StandardNpcAccess(npc, this, StandardNpcAccessContext(
                { random }, { StandardNpcHitModifier(events) }, { StandardNpcHitProcessor(players, events, emptySet()) }
            )), AiPlayerEvents.Ap2(player, npc))
        }
        if(hits().size + projectiles().size > count) launches += clock.cycle
        clock.tick()
    }
    fun projectiles() = mockingDetails(world).invocations.filter { it.method.name == "projAnim" }.map { it.arguments[0] as ProjAnim }
    data class HitSnapshot(val damage: Int, val type: HitType)
    fun hits(): List<Pair<Int,HitSnapshot>> {
        val iterator = player.queueList.iterator() ?: return emptyList()
        return try { buildList {
            while(iterator.hasNext()) {
                val q = iterator.next()
                val snapshot = when(val arg = q.args) {
                    is Hit -> HitSnapshot(arg.damage, arg.type)
                    is DeferredPlayerHit -> HitSnapshot(arg.builder.damage, arg.builder.type)
                    else -> null
                }
                snapshot?.let { add(q.remainingCycles to it) }
            }
        } } finally { iterator.cleanUp() }
    }
}
