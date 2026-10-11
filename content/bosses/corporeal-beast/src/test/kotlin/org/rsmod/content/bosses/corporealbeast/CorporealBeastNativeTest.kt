package org.rsmod.content.bosses.corporealbeast

import dev.openrune.ServerCacheManager
import dev.openrune.types.NpcMode
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.runtime.*
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.events.interact.AiPlayerEvents
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.player.hit.DeferredPlayerHit
import org.rsmod.api.player.hit.modify
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Frozen baseline assertions run through registered events, real NvPCombat and player hit queues.
 */
@OptIn(InternalApi::class)
class CorporealBeastNativeTest {
    @AfterEach
    fun releaseFixtures() {
        framework().clearInlineMocks()
    }

    @Test
    fun `ordinary magic retains geometry damage prayer timing and four tick cadence`() {
        val f = CorpFixture()
        repeat(13) { f.attackTick() }
        assertEquals(listOf(100, 104, 108, 112), f.launches)
        val expected =
            ProjAnim.fromNpcToPlayer(f.npc, f.player, 316, CorporealBeastAssets.TRACKING_PROJECTILE)
        assertEquals(List(4) { expected }, f.projectiles())
        assertEquals(List(4) { 65 }, f.hits().map { it.second.damage })
        assertEquals(List(4) { expected.serverCycles }, f.hits().map { it.first })
        assertTrue(f.hits().all { it.second.type == HitType.Magic })
        assertEquals(1679, f.npc.pendingSequence.id)
    }

    @Test
    fun `occasional short cooldown launches on third tick`() {
        val f = CorpFixture(short = true)
        repeat(10) { f.attackTick() }
        assertEquals(listOf(100, 103, 106, 109), f.launches)
    }

    @Test
    fun `both melee animations and crush maximum use native attacks on the five tile footprint`() {
        for (side in 0..1) {
            val f = CorpFixture(melee = true, side = side)
            f.player.coords = f.npc.coords.translate(5, 2)
            f.attackTick()
            assertEquals(1682 + side, f.npc.pendingSequence.id)
            val hit = f.hits().single().second
            assertEquals(HitType.Melee, hit.type)
            assertEquals(33, hit.damage)
            assertEquals(1, f.hits().single().first)
        }
    }

    @Test
    fun `drain is a distinct native magic projectile`() {
        val f = CorpFixture(magic = 1)
        f.attackTick()
        assertEquals(314, f.projectiles().single().spotanim)
        assertEquals(1680, f.npc.pendingSequence.id)
        assertEquals(65, f.hits().single().second.damage)
    }

    @Test
    fun `area attack snapshots its tile instead of tracking a player`() {
        val f = CorpFixture(magic = 2)
        val tile = f.player.coords
        f.attackTick()
        val projectile = f.projectiles().single()
        assertEquals(315, projectile.spotanim)
        assertEquals(tile, projectile.endCoord)
        assertEquals(0, projectile.targetIndex)
        assertEquals(1681, f.npc.pendingSequence.id)
        assertTrue(f.hits().isEmpty())
        assertEquals(104, f.npc.actionDelay)
    }

    @Test
    fun `other lair and different plane cannot be targeted`() {
        for (tile in listOf(CoordGrid(2993, 4382, 2), CoordGrid(2993, 4254, 1))) {
            val f = CorpFixture()
            f.player.coords = tile
            f.attackTick()
            assertTrue(f.projectiles().isEmpty())
            assertTrue(f.hits().isEmpty())
        }
    }

    @Test
    fun `protection prayer is sampled on impact for melee and magic`() {
        for (melee in listOf(false, true)) {
            val f = CorpFixture(melee = melee)
            if (melee) f.player.coords = f.npc.coords.translate(5, 2)
            f.attackTick()
            val hit = f.deferredHits().single()
            VarPlayerIntMapSetter.set(
                f.player,
                if (melee) "varbit.prayer_protectfrommelee" else "varbit.prayer_protectfrommagic",
                1,
            )
            hit.modifier.modify(hit.builder, f.player)
            assertEquals(if (melee) 0 else 43, hit.builder.damage)
        }
    }

    @Test
    fun `drain callback uses resolved damage and native stat subtraction`() {
        val f = CorpFixture(magic = 1)
        f.npc.hitpoints = 1500
        f.player.statMap.setBaseLevel("stat.magic", 99)
        f.player.statMap.setCurrentLevel("stat.magic", 99)
        f.attackTick()
        val pending = f.deferredHits().single()
        val hit = pending.builder.build()
        hit.dispatchImpact(f.player, 9)
        assertEquals(1504, f.npc.hitpoints)
        assertEquals(97, f.player.statMap.getCurrentLevel("stat.magic"))
    }

    @Test
    fun `core ownership survives only until core death or native boss destruction`() {
        val f = CorpFixture()
        f.spawnCore()
        val encounter = f.registry.of(f.npc)
        val first = assertNotNull(f.effects.state(f.npc).core)
        assertEquals(25, first.hitpoints)
        assertEquals(f.npc.coords.translate(2, 2), first.coords)
        assertEquals(listOf(first), encounter.owned())
        f.spawnCore()
        assertEquals(listOf(first), encounter.owned())
        f.npcs.del(first, Int.MAX_VALUE)
        assertNull(f.effects.state(f.npc).core)
        assertTrue(encounter.owned().isEmpty())
        f.spawnCore()
        val second = assertNotNull(f.effects.state(f.npc).core)
        f.npcs.del(f.npc, Int.MAX_VALUE)
        assertFalse(second.isSlotAssigned)
        assertTrue(encounter.isEnded)
        assertEquals(0, f.npcList.count())
    }

    @Test
    fun `core jump uses native queue and reset cancels its pending landing`() {
        val f = CorpFixture()
        f.spawnCore()
        val state = f.effects.state(f.npc)
        val core = assertNotNull(state.core)
        f.clock.cycle += 2
        f.lateTick()
        assertTrue(state.coreJumping)
        val jump = f.projectiles().single()
        assertEquals(319, jump.spotanim)
        assertEquals(f.player.coords, jump.endCoord)
        assertTrue(f.queues.isNotEmpty)
        f.registry.remove(f.npc)
        assertFalse(f.queues.isNotEmpty)
        assertFalse(core.isSlotAssigned)
        repeat(5) { f.advanceWorld() }
        assertEquals(1, f.projectiles().size)
    }

    @Test
    fun `adjacent core siphons every two ticks and uses actual damage for healing`() {
        val f = CorpFixture()
        f.npc.hitpoints = 1500
        f.spawnCore()
        val core = assertNotNull(f.effects.state(f.npc).core)
        f.player.coords = core.coords.translate(1, 0)
        f.clock.cycle += 2
        f.lateTick()
        val hit = f.rawHits().single()
        assertEquals(13, hit.damage)
        hit.dispatchImpact(f.player, 7)
        assertEquals(1507, f.npc.hitpoints)
        f.clock.tick()
        f.lateTick()
        assertEquals(1, f.rawHits().size)
        f.clock.tick()
        f.lateTick()
        assertEquals(2, f.rawHits().size)
        f.npcs.del(core, Int.MAX_VALUE)
        assertFalse(hit.isValidNow())
    }

    @Test
    fun `split attack emits six native projectiles and retirement unlinks all callbacks`() {
        val f = CorpFixture(magic = 2)
        f.attackTick()
        val main = f.projectiles().single()
        repeat(main.serverCycles) { f.advanceWorld() }
        assertEquals(7, f.projectiles().size)
        assertEquals(6, f.projectiles().drop(1).map { it.endCoord }.distinct().size)
        assertTrue(
            f.projectiles().drop(1).all { it.startCoord == main.endCoord && it.spotanim == 1836 }
        )
        assertTrue(f.rawHits().isNotEmpty())
        val hit = f.rawHits().first()
        assertTrue(hit.isValidNow())
        f.registry.remove(f.npc)
        assertFalse(f.queues.isNotEmpty)
        assertFalse(hit.isValidNow())
    }

    @Test
    fun `underfoot stomp preserves the seven tick late-cycle cadence`() {
        val f = CorpFixture()
        f.effects.state(f.npc)
        f.player.coords = f.npc.coords.translate(4, 4)
        repeat(15) {
            f.lateTick()
            f.clock.tick()
        }
        assertEquals(2, f.rawHits().size)
        assertTrue(f.rawHits().all { it.type == HitType.Typeless && it.damage == 51 })
        assertEquals(1686, f.npc.pendingSequence.id)
    }

    @Test
    fun `empty room recovery and three hundred tick reset restore native stats and detach core`() {
        val f = CorpFixture()
        f.spawnCore()
        val old = f.registry.of(f.npc)
        val core = assertNotNull(f.effects.state(f.npc).core)
        f.player.coords = CoordGrid(2993, 4382, 2)
        f.npc.hitpoints = 1000
        f.npc.attackLvl -= 10
        f.clock.cycle = 107
        f.lateTick()
        assertEquals(1025, f.npc.hitpoints)
        val attack = f.npc.attackLvl
        f.clock.cycle = 120
        f.lateTick()
        assertEquals(attack + 1, f.npc.attackLvl)
        f.clock.cycle = 400
        f.lateTick()
        assertEquals(2000, f.npc.hitpoints)
        assertEquals(f.npc.baseAttackLvl, f.npc.attackLvl)
        assertTrue(old.isEnded)
        assertFalse(core.isSlotAssigned)
        assertNull(f.registry.existing(f.npc))
        assertFalse(f.queues.isNotEmpty)
    }

    @Test
    fun `launches cannot land after external teleport logout death or boss respawn`() {
        for (reason in listOf("teleport", "logout", "death", "respawn")) {
            val f = CorpFixture()
            f.attackTick()
            val hit = f.deferredHits().single().builder
            assertTrue(hit.isValidNow())
            when (reason) {
                "teleport" -> f.player.coords = CoordGrid(2993, 4382, 2)
                "logout" -> f.player.pendingLogout = true
                "death" -> f.player.statMap.setCurrentLevel("stat.hitpoints", 0)
                "respawn" -> f.events.publish(NpcStateEvents.Respawn(f.npc))
            }
            assertFalse(hit.isValidNow(), reason)
        }
    }

    @Test
    fun `both public lairs retain independent cores encounters and reset cycles`() {
        val f = CorpFixture()
        f.spawnCore()
        val first = f.registry.of(f.npc)
        val other = Npc(f.npc.type, CoordGrid(2993, 4382, 2))
        f.npcs.add(other, Int.MAX_VALUE)
        val second = f.registry.of(other)
        f.effects.cores.spawn(other, second, f.effects.state(other))
        val otherCore = assertNotNull(f.effects.state(other).core)
        f.registry.remove(f.npc)
        assertTrue(first.isEnded)
        assertFalse(second.isEnded)
        assertTrue(otherCore.isSlotAssigned)
        assertEquals(listOf(otherCore), second.owned())
        repeat(2) {
            val old = f.registry.of(other)
            f.events.publish(NpcStateEvents.Respawn(other))
            assertTrue(old.isEnded)
            assertNull(f.registry.existing(other))
            val fresh = f.registry.of(other)
            assertNotSame(old, fresh)
            assertNull(f.effects.state(other).core)
            f.effects.cores.spawn(other, fresh, f.effects.state(other))
            assertEquals(1, fresh.owned().size)
        }
        f.npcs.del(other, Int.MAX_VALUE)
        assertEquals(listOf(f.npc), f.npcList.toList())
        assertFalse(f.queues.isNotEmpty)
    }

    @Test
    fun `every weighted boundary preserves melee reach and equally split magic`() {
        for (adjacent in listOf(false, true)) {
            val expected =
                if (adjacent) listOf("melee", "melee", "magic", "drain", "area")
                else listOf("magic", "drain", "area")
            for ((roll, name) in expected.withIndex()) {
                val f = CorpFixture()
                if (adjacent) f.player.coords = f.npc.coords.translate(5, 2)
                val random =
                    object : GameRandom {
                        override fun of(maxExclusive: Int) = roll

                        override fun of(minInclusive: Int, maxInclusive: Int) = minInclusive

                        override fun randomDouble() = 0.0
                    }
                val spec = corporealBeastSpec()
                val encounter = BossEncounter(f.npc, spec, random)
                assertEquals(
                    name,
                    encounter.selectAbility(encounter.currentPhase!!.selector, 100, f.player),
                )
            }
        }
    }

    companion object {
        // Baseline cache evidence remains unchanged; no live encounter is started by these tests.
        @BeforeAll
        @JvmStatic
        fun cache() {
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
    val npc =
        Npc(assertNotNull(ServerCacheManager.getNpc(319)), CoordGrid(2993, 4254, 2)).apply {
            slotId = 1
            assignUid()
            hitpoints = 2000
            baseHitpointsLvl = 2000
            mode = NpcMode.ApPlayer2
        }
    val player =
        Player().apply {
            slotId = 1
            uuid = 1L
            assignUid()
            coords = CoordGrid(3000, 4255, 2)
            statMap.setBaseLevel("stat.hitpoints", 99)
            statMap.setCurrentLevel("stat.hitpoints", 99)
            inv = invMap.getOrPut("inv.inv")
            worn = invMap.getOrPut("inv.worn")
        }
    val players = PlayerList().apply { this[1] = player }
    val random =
        object : GameRandom {
            override fun of(maxExclusive: Int): Int =
                when (maxExclusive) {
                    5 -> if (melee) 0 else 2 + magic
                    3 -> magic
                    2 -> side
                    Int.MAX_VALUE -> if (short) 0 else Int.MAX_VALUE - 1
                    else -> maxExclusive - 1
                }

            override fun of(minInclusive: Int, maxInclusive: Int) = maxInclusive

            override fun randomDouble() = 0.5
        }
    val world = mock(WorldRepository::class.java)
    val queues = WorldQueueList()
    val npcList = NpcList()
    val collision = CollisionFlagMap()
    val npcs = NpcRepository(clock, NpcRegistry(npcList, collision, events), npcList)
    val accuracy =
        mock(AccuracyFormulae::class.java) { call ->
            if (call.method.name.startsWith("roll")) true else RETURNS_DEFAULTS.answer(call)
        }
    val registry = EncounterRegistry(random)
    val deps =
        BossDeps(
            random,
            accuracy,
            world,
            npcs,
            players,
            clock,
            queues,
            collision,
            registry,
            BossExtensionRegistry(),
            NvPCombat(accuracy, mock(MaxHitFormulae::class.java), world),
        )
    val effects = CorporealBeastRoomEffects(deps, npcList)
    val launches = mutableListOf<Int>()

    init {
        val script =
            CorporealBeastCombat(
                deps,
                effects,
                CorporealBeastAttackParameters(random),
                mock(NpcDeath::class.java),
            )
        with(script) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        with(CorporealBeastLifecycle(effects, npcList)) {
            ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
        }
        npcs.add(npc, Int.MAX_VALUE)
    }

    fun attackTick() {
        val count = hits().size + projectiles().size
        npc.currentMapClock = clock.cycle
        npc.processedMapClock = clock.cycle
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
        if (hits().size + projectiles().size > count) launches += clock.cycle
        clock.tick()
    }

    fun projectiles() =
        mockingDetails(world)
            .invocations
            .filter { it.method.name == "projAnim" }
            .map { it.arguments[0] as ProjAnim }

    fun lateTick() = events.publish(GameLifecycle.LateCycle)

    fun advanceWorld() {
        WorldQueueListProcess(queues).process()
        clock.tick()
    }

    fun spawnCore() {
        effects.cores.spawn(npc, registry.of(npc), effects.state(npc))
    }

    fun rawHits() = queueArguments().filterIsInstance<Hit>()

    fun deferredHits() = queueArguments().filterIsInstance<DeferredPlayerHit>()

    private fun queueArguments(): List<Any?> {
        val iterator = player.queueList.iterator() ?: return emptyList()
        return try {
            buildList { while (iterator.hasNext()) add(iterator.next().args) }
        } finally {
            iterator.cleanUp()
        }
    }

    data class HitSnapshot(val damage: Int, val type: HitType)

    fun hits(): List<Pair<Int, HitSnapshot>> {
        val iterator = player.queueList.iterator() ?: return emptyList()
        return try {
            buildList {
                while (iterator.hasNext()) {
                    val q = iterator.next()
                    val snapshot =
                        when (val arg = q.args) {
                            is Hit -> HitSnapshot(arg.damage, arg.type)
                            is DeferredPlayerHit ->
                                HitSnapshot(arg.builder.damage, arg.builder.type)
                            else -> null
                        }
                    snapshot?.let { add(q.remainingCycles to it) }
                }
            }
        } finally {
            iterator.cleanUp()
        }
    }
}
