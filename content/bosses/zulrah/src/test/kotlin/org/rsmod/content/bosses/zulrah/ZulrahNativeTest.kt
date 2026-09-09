package org.rsmod.content.bosses.zulrah

import dev.openrune.ServerCacheManager
import kotlin.random.Random
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.death.NpcDeathDropHook
import org.rsmod.api.death.plugin.NpcDeathScript
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.events.interact.AiPlayerEvents
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
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
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.region.Region
import org.rsmod.game.region.zone.RegionZoneCopy
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Native characterization of the retained draft; not a complete OSRS/client parity claim. */
@OptIn(InternalApi::class)
class ZulrahNativeTest {
    @Test
    fun `players outside the shrine are not handled by Zulrah death storage`() {
        val f = ZulrahFixture()
        assertFalse(f.encounters.isActive(f.player))
        f.start(1)
        assertTrue(f.encounters.isActive(f.player))
        f.encounters.abort(f.player, "fixture exit", teleport = false)
        assertFalse(f.encounters.isActive(f.player))
    }

    @Test
    fun `native entry owns one actor and opening cloud packets preserve captured cadence`() {
        val f = ZulrahFixture()
        f.start(1)
        assertEquals(f.local(2268, 3068), f.player.coords)
        assertEquals(f.local(2266, 3073), f.boss.coords)
        assertEquals(2042, f.boss.type.id)
        assertEquals(500, f.boss.hitpoints)
        assertEquals(5071, f.boss.pendingSequence.id)
        assertEquals(f.player.uid, f.boss.spawnOwner)
        val session = assertNotNull(f.instances.sessionForPlayer(f.player))
        assertEquals(listOf(f.boss), f.instances.npcsForInstance(session.id))
        f.until(124)
        assertEquals(listOf(111, 111, 114, 114, 117, 117, 120, 120), f.projectiles.map { it.first })
        val primary = f.projectiles.first().second
        val secondary = f.projectiles[1].second
        assertEquals(listOf(1045, 370, 20, 40, 90, 0), primary.profile())
        assertEquals(listOf(1045, 370, 20, 45, 120, 10), secondary.profile())
        assertEquals(f.local(2268, 3068), primary.endCoord)
        assertEquals(f.local(2271, 3068), secondary.endCoord)
        assertEquals(listOf(114, 115, 117, 118, 120, 121, 123, 124), f.locAdds.map { it.first })
        assertEquals(5072, f.boss.pendingSequence.id)
        f.until(127)
        assertEquals(2043, f.boss.visType.id)
        assertEquals(5073, f.boss.pendingSequence.id)
    }

    @Test
    fun `magma tail alternation and seven tick cadence remain unchanged`() {
        val f = ZulrahFixture()
        f.start(1, 2)
        f.until(103)
        assertEquals(5806, f.boss.pendingSequence.id)
        f.until(110)
        assertEquals(5807, f.boss.pendingSequence.id)
        f.until(117)
        assertEquals(5072, f.boss.pendingSequence.id)
        assertTrue(f.projectiles.isEmpty())
    }

    @Test
    fun `green ordinary attacks use native projectiles damage and three tick lock`() {
        val f = ZulrahFixture()
        f.start(1, 4)
        f.until(103)
        f.attack()
        assertEquals(1, f.projectiles.size)
        assertEquals(listOf(1044, 370, 50, 40, 65, 0), f.projectiles.single().second.profile())
        assertEquals(5069, f.boss.pendingSequence.id)
        val hit = f.hits().single()
        assertEquals(3, hit.first)
        assertEquals(41, hit.second.damage)
        f.attack()
        assertEquals(1, f.projectiles.size)
        f.until(106)
        f.attack()
        assertEquals(listOf(103, 106), f.projectiles.map { it.first })
    }

    @Test
    fun `prayer is snapshotted at launch and already fired standard hits survive boss death`() {
        val f = ZulrahFixture()
        f.start(1, 4)
        VarPlayerIntMapSetter.set(f.player, "varbit.prayer_protectfrommissiles", 1)
        f.until(103)
        val hit = f.hits().single().second
        assertEquals(0, hit.damage)
        VarPlayerIntMapSetter.set(f.player, "varbit.prayer_protectfrommissiles", 0)
        f.boss.hitpoints = 0
        f.encounters.beginBossDeath(f.boss)
        assertTrue(hit.isValidNow())
        assertEquals(0, hit.damage)
    }

    @Test
    fun `seeded blue choice and jad style ordering execute native registration`() {
        val blue = ZulrahFixture()
        blue.start(1, 3)
        for (tick in listOf(103, 106, 109, 112)) {
            blue.until(tick)
        }
        val seed = Random(0)
        assertEquals(
            List(4) { if (seed.nextInt(3) < 2) 1046 else 1044 },
            blue.projectiles.map { it.second.spotanim },
        )
        for ((rotation, phase, first, count) in
            listOf(listOf(1, 9, 1044, 10), listOf(3, 10, 1046, 10), listOf(4, 11, 1046, 8))) {
            val jad = ZulrahFixture()
            jad.start(rotation, phase)
            repeat(count) { index -> jad.until(103 + index * 3) }
            assertEquals(
                List(count) { if (it % 2 == 0) first else if (first == 1044) 1046 else 1044 },
                jad.projectiles.map { it.second.spotanim },
            )
        }
    }

    @Test
    fun `damage reroll is applied through the native modifier and actor UID survives dives`() {
        val f = ZulrahFixture()
        f.start(1)
        val uid = f.boss.uid
        for (damage in listOf(0, 1, 50, 51, 100)) {
            val result = f.calculate(damage)
            if (damage <= 50) assertEquals(damage, result.damage)
            else assertTrue(result.damage in 45..50)
        }
        f.until(127)
        assertEquals(uid, f.boss.uid)
        assertEquals(1, f.npcList.count())
    }

    @Test
    fun `native registration retains one HP snakeling types and four tick attacks`() {
        val f = ZulrahFixture()
        f.start(4, 2)
        f.until(107)
        val snake = f.npcList.first { it.type.id in 2045..2046 }
        assertEquals(1, snake.hitpoints)
        val projectileCount = f.projectiles.size
        f.attack(snake)
        assertEquals(projectileCount, f.projectiles.size)
        f.until(110)
        f.attack(snake)
        assertEquals(1741, snake.pendingSequence.id)
        assertEquals(114, snake.actionDelay)
        assertEquals(15, f.hits().last().second.damage)
    }

    @Test
    fun `all four complete programs execute their actions forms coordinates and intermission ticks`() {
        for (rotation in ZulrahRotations.all) {
            val f = ZulrahFixture()
            f.start(rotation.number)
            val expected = mutableListOf<SurfaceSample>()
            var tick = 111
            for (phase in rotation.phases) {
                val npcId =
                    when (phase.form) {
                        ZulrahForm.SERPENTINE -> 2042
                        ZulrahForm.MAGMA -> 2043
                        ZulrahForm.TANZANITE -> 2044
                    }
                val position = f.local(phase.position.x, phase.position.z)
                for ((actionIndex, action) in phase.actions.withIndex()) {
                    if (action is ZulrahAction.Delay) {
                        tick += action.ticks
                        continue
                    }
                    val count =
                        when (action) {
                            is ZulrahAction.StandardAttack -> action.count
                            is ZulrahAction.JadAttack -> action.count
                            is ZulrahAction.TailSwipe -> action.count
                            else -> 1
                        }
                    repeat(count) { component ->
                        val animation =
                            if (action is ZulrahAction.TailSwipe) {
                                if (component % 2 == 0) 5806 else 5807
                            } else 5069
                        expected += SurfaceSample(tick, npcId, animation, position)
                        tick +=
                            if (action is ZulrahAction.TailSwipe) 7
                            else if (
                                actionIndex == phase.actions.lastIndex && component == count - 1
                            )
                                4
                            else 3
                    }
                }
                expected += SurfaceSample(tick, npcId, 5072, position)
                if (phase != rotation.phases.last()) {
                    val next = rotation.phases[phase.number]
                    val nextId =
                        when (next.form) {
                            ZulrahForm.SERPENTINE -> 2042
                            ZulrahForm.MAGMA -> 2043
                            ZulrahForm.TANZANITE -> 2044
                        }
                    expected +=
                        SurfaceSample(
                            tick + 3,
                            nextId,
                            5073,
                            f.local(next.position.x, next.position.z),
                        )
                }
                tick += 6
            }
            f.until(tick - 6)
            assertEquals(
                expected,
                f.samples,
                "Rotation ${rotation.number}: ${f.encounters.debugStatus(f.player)}",
            )
        }
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }

    @org.junit.jupiter.api.AfterEach
    fun releaseMocks() {
        framework().clearInlineMocks()
    }

    private fun ProjAnim.profile() =
        listOf(spotanim, startHeight, endHeight, startTime, endTime, angle)
}

private data class SurfaceSample(
    val tick: Int,
    val npc: Int,
    val animation: Int,
    val tile: CoordGrid,
)

@OptIn(InternalApi::class)
private class ZulrahFixture(accurate: Boolean = true) {
    val clock = MapClock(100)
    val events = EventBus()
    val collision = CollisionFlagMap()
    val player =
        Player().apply {
            slotId = 1
            uuid = 1L
            assignUid()
            coords = ZULRAH_DOCK
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
                    val region = Region(base, base.translate(128, 128), ++allocations, allocations)
                    for (level in 0..1) for (x in 0..2) for (z in 0..2) {
                        val dest = base.translate(48 + x * 8, 48 + z * 8).copy(level = level)
                        region.registerZone(
                            RegionZoneCopy(ZoneKey(282 + x, 382 + z, level), 0, null),
                            ZoneKey.from(dest),
                        )
                        collision.allocateIfAbsent(dest.x, dest.z, level)
                    }
                    region
                }
                "isValid" -> true
                else -> RETURNS_DEFAULTS.answer(call)
            }
        }
    val instances =
        InstanceManager(regions, npcs, players, events, InstanceAreaResolver(), clock, collision)
    val random =
        object : GameRandom {
            override fun of(maxExclusive: Int) = maxExclusive - 1

            override fun of(minInclusive: Int, maxInclusive: Int) = maxInclusive

            override fun randomDouble() = 0.5
        }
    val projectiles = mutableListOf<Pair<Int, ProjAnim>>()
    val locAdds = mutableListOf<Pair<Int, LocInfo>>()
    val world =
        mock(WorldRepository::class.java) { call ->
            when (call.method.name) {
                "projAnim" -> projectiles += clock.cycle to call.arguments[0] as ProjAnim
                "locAddPrivate" -> locAdds += clock.cycle to call.arguments[1] as LocInfo
            }
            RETURNS_DEFAULTS.answer(call)
        }
    val combat =
        NvPCombat(
            mock(AccuracyFormulae::class.java) { call ->
                if (call.method.name.startsWith("roll")) accurate else RETURNS_DEFAULTS.answer(call)
            },
            mock(MaxHitFormulae::class.java),
            world,
        )
    val encounters =
        ZulrahEncounterManager(
            clock,
            instances,
            npcs,
            AiPlayerInteractions(events, players),
            collision,
            random,
            combat,
            world,
        )
    val death =
        NpcDeath(
            npcs,
            players,
            mock(ObjRepository::class.java),
            setOf(NpcDeathDropHook { true }),
            emptySet(),
        )
    val boss
        get() = npcList.first { it.type.id in 2042..2044 }

    val samples = mutableListOf<SurfaceSample>()

    init {
        collision.allocateIfAbsent(player.coords.x, player.coords.z, player.coords.level)
        val ctx = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        with(NpcDeathScript(death)) { ctx.startup() }
        with(ZulrahCombat(encounters, death, players)) { ctx.startup() }
    }

    fun start(rotation: Int, phase: Int = 1) {
        assertTrue(encounters.enterDebug(player, rotation, phase, 0))
    }

    fun local(x: Int, z: Int) = CoordGrid(6448 + x - 2256, 6448 + z - 3056)

    fun until(tick: Int) {
        while (clock.cycle < tick) {
            npcList.forEach { it.pendingSequence = EntitySeq.NULL }
            clock.cycle++
            npcList.toList().forEach {
                it.currentMapClock = clock.cycle
                it.processedMapClock = clock.cycle
                if (it.canProcess && it.activeCoroutine != null) it.advanceActiveCoroutine()
            }
            encounters.tickAll()
            // Invoke the registered native handler at this clock value after the preceding
            // LateCycle state update, as the next GameCycle pre-input/NPC stage would do.
            if (boss.hitpoints > 0 && boss.activeCoroutine == null) attack()
            if (boss.pendingSequence != EntitySeq.NULL) {
                samples +=
                    SurfaceSample(
                        clock.cycle,
                        boss.visType.id,
                        boss.pendingSequence.id,
                        boss.coords,
                    )
            }
        }
    }

    fun attack(npc: Npc = boss) {
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
    }

    fun calculate(amount: Int): Hit {
        val hit =
            HitBuilder(
                HitType.Ranged,
                amount,
                player.uid.packed,
                player.slotId,
                false,
                true,
                0,
                null,
                null,
                0,
                0,
                null,
                null,
                null,
                null,
                Int.MAX_VALUE,
                Int.MAX_VALUE,
            )
        events.publish(NpcHitEvents.Modify(boss, hit))
        return hit.build()
    }

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
