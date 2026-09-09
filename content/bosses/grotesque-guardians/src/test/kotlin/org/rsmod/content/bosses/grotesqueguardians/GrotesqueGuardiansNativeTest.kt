package org.rsmod.content.bosses.grotesqueguardians

import dev.openrune.ServerCacheManager
import dev.openrune.types.ProjAnimType
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossExtensionRegistry
import org.rsmod.api.bosses.runtime.EncounterRegistry
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.death.NpcDeathDropHook
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.death.plugin.NpcDeathScript
import org.rsmod.api.game.process.world.WorldQueueListProcess
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
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.region.Region
import org.rsmod.game.region.zone.RegionZoneCopy
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Executes the pre-migration registration; these are behavioral oracles, not new OSRS evidence. */
@OptIn(InternalApi::class)
class GrotesqueGuardiansNativeTest {
    @Test
    fun `native entry owns two dormant guardians and bell arms both after two ticks`() {
        val f = GuardiansFixture()
        assertEquals(CoordGrid(6433, 6423), f.player.coords)
        assertEquals(CoordGrid(6437, 6429), f.dawn.coords)
        assertEquals(CoordGrid(6425, 6429), f.dusk.coords)
        assertEquals(listOf(450, 450), listOf(f.dawn.hitpoints, f.dusk.hitpoints))
        val session = assertNotNull(f.instances.sessionForPlayer(f.player))
        assertEquals(setOf(f.dawn, f.dusk), f.instances.npcsForInstance(session.id).toSet())
        assertEquals(f.player.uid, f.dawn.spawnOwner)
        f.attack(100, f.dawn)
        assertTrue(f.hits().isEmpty())
        f.ring()
        assertEquals(7774, f.dawn.pendingSequence.id)
        assertEquals(7790, f.dusk.pendingSequence.id)
        f.attack(101, f.dawn)
        assertTrue(f.hits().isEmpty())
        f.attack(102, f.dawn)
        assertEquals(2, f.hits().size)
        assertEquals(108, f.dawn.actionDelay)
    }

    @Test
    fun `Dawn has independent ranged components one tick apart and fourth stone ball`() {
        val f = GuardiansFixture()
        f.ring()
        f.attack(102, f.dawn)
        val expected =
            ProjAnim.fromNpcToPlayer(
                f.dawn,
                f.player,
                1444,
                ProjAnimType(
                    startHeight = 90,
                    endHeight = 40,
                    delay = 0,
                    angle = 32,
                    lengthAdjustment = 0,
                    progress = 0,
                    stepMultiplier = 5,
                ),
            )
        assertEquals(listOf(expected, expected), f.projectiles())
        assertEquals(listOf(9, 9), f.hits().map { it.second.damage })
        assertEquals(
            listOf(
                expected.serverCycles.coerceAtLeast(1),
                expected.serverCycles.coerceAtLeast(1) + 1,
            ),
            f.hits().map { it.first },
        )
        f.attack(108, f.dawn)
        f.attack(114, f.dawn)
        assertEquals(6, f.projectiles().size)
        f.attack(120, f.dawn)
        assertEquals(7771, f.dawn.pendingSequence.id)
        assertEquals(6, f.projectiles().size)
        f.idleUntil(122)
        assertEquals(6, f.hits().size)
        f.idleUntil(123)
        assertEquals(15, f.hits().last().second.damage)
        assertEquals(HitType.Typeless, f.hits().last().second.type)
        assertEquals(133, f.player.actionDelay)
    }

    @Test
    fun `Dawn nearby second attack is melee and normal prayer snapshots eighty percent`() {
        val f = GuardiansFixture()
        f.ring()
        f.player.coords = f.dawn.coords.translate(-1, 0)
        VarPlayerIntMapSetter.set(f.player, "varbit.prayer_protectfrommissiles", 1)
        f.attack(102, f.dawn)
        VarPlayerIntMapSetter.set(f.player, "varbit.prayer_protectfrommissiles", 0)
        assertEquals(listOf(1, 1), f.hits().map { it.second.damage })
        VarPlayerIntMapSetter.set(f.player, "varbit.prayer_protectfrommelee", 1)
        f.attack(108, f.dawn)
        assertEquals(7769, f.dawn.pendingSequence.id)
        assertEquals(3, f.hits().last().second.damage)
        assertEquals(HitType.Melee, f.hits().last().second.type)
    }

    @Test
    fun `all paired thresholds lightning and hammer transition preserve HP and form timing`() {
        val f = GuardiansFixture()
        f.ring()
        f.damage(f.dusk, 100, HitType.Melee)
        assertEquals(450, f.dusk.hitpoints)
        f.damage(f.dawn, 500, HitType.Ranged)
        assertEquals(247, f.dawn.hitpoints)
        assertEquals(7853, f.dawn.visType.id)
        assertEquals(7882, f.dusk.visType.id)
        f.damage(f.dusk, 500, HitType.Melee)
        assertEquals(247, f.dusk.hitpoints)
        assertEquals(7855, f.dusk.visType.id)
        f.idleUntil(111)
        assertEquals(7855, f.dusk.visType.id)
        f.idleUntil(112)
        assertEquals(7854, f.dusk.visType.id)
        assertEquals(7884, f.dawn.visType.id)
        assertEquals(3, f.locAdds())
        f.damage(f.dawn, 500, HitType.Ranged)
        assertEquals(9, f.dawn.hitpoints)
        assertTrue(f.encounters.useHammer(f.player, f.dawn, "obj.slayer_rock_hammer"))
        assertEquals(7885, f.dawn.visType.id)
        f.idleUntil(116)
        assertEquals(7854, f.dusk.visType.id)
        f.idleUntil(117)
        assertEquals(7888, f.dusk.visType.id)
        assertEquals(247, f.dusk.hitpoints)
        assertEquals(119, f.dusk.actionDelay)
    }

    @Test
    fun `Dusk phase two alternates melee animations around every third explosion`() {
        val f = GuardiansFixture()
        f.ring()
        f.damage(f.dawn, 500, HitType.Ranged)
        val sequences =
            listOf(102, 108, 114, 120, 126, 132).map { tick ->
                f.attack(tick, f.dusk)
                f.dusk.pendingSequence.id
            }
        assertEquals(listOf(7787, 7785, 7802, 7785, 7787, 7802), sequences)
        assertEquals(listOf(15, 15, 15, 15), f.hits().map { it.second.damage })
    }

    @Test
    fun `Dusk final fixed first and nested second rolls preserve prayer and missed components`() {
        for (prayer in listOf(false, true)) {
            val f = GuardiansFixture()
            f.finalPhase()
            if (prayer) VarPlayerIntMapSetter.set(f.player, "varbit.prayer_protectfrommissiles", 1)
            f.attack(119, f.dusk)
            assertEquals(7799, f.dusk.pendingSequence.id)
            val before = f.rolls.size
            f.attack(125, f.dusk)
            assertEquals(7801, f.dusk.pendingSequence.id)
            assertEquals(
                listOf(if (prayer) 4 else 9, 15),
                f.hits().takeLast(2).map { it.second.damage },
            )
            assertEquals(listOf(16, 16), f.rolls.drop(before))
            assertEquals(1, f.hits().takeLast(2).let { it[1].first - it[0].first })
        }
        val f = GuardiansFixture(accurate = false)
        f.finalPhase()
        f.attack(119, f.dusk)
        val before = f.rolls.size
        f.attack(125, f.dusk)
        assertEquals(listOf(0, 0), f.hits().takeLast(2).map { it.second.damage })
        assertEquals(listOf(1), f.rolls.drop(before))
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
}

@OptIn(InternalApi::class)
private class GuardiansFixture(accurate: Boolean = true) {
    val clock = MapClock(100)
    val events = EventBus()
    val collision = CollisionFlagMap()
    val player =
        Player().apply {
            slotId = 1
            uuid = 1L
            assignUid()
            coords = GROTESQUE_PUBLIC_EXIT
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
                    val region = Region(base, base.translate(64, 64), ++allocations, allocations)
                    for (level in 0..3) for (x in 0..7) for (z in 0..7) {
                        val tile = CoordGrid(base.x + x * 8, base.z + z * 8, level)
                        region.registerZone(
                            RegionZoneCopy(ZoneKey(208 + x, 568 + z, level), 0, null),
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
    val rolls = mutableListOf<Int>()
    private var arenaDraw = 0
    val random =
        object : GameRandom {
            override fun of(maxExclusive: Int): Int {
                rolls += maxExclusive
                return maxExclusive - 1
            }

            override fun of(minInclusive: Int, maxInclusive: Int): Int {
                if (minInclusive == 26 && maxInclusive == 40) return 26 + (arenaDraw++ / 2) % 15
                if (minInclusive == 24 && maxInclusive == 38)
                    return 24 + (arenaDraw++ / 2 / 15) % 15
                rolls += maxInclusive - minInclusive + 1
                return maxInclusive
            }

            override fun randomDouble() = 0.5
        }
    val world = mock(WorldRepository::class.java)
    val queues = WorldQueueList()
    val accuracy =
        mock(AccuracyFormulae::class.java) { call ->
            if (call.method.name.startsWith("roll")) accurate else RETURNS_DEFAULTS.answer(call)
        }
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
            EncounterRegistry(random),
            BossExtensionRegistry(),
            NvPCombat(accuracy, mock(MaxHitFormulae::class.java), world),
        )
    val encounters =
        GrotesqueGuardiansEncounterManager(
            clock,
            instances,
            npcs,
            AiPlayerInteractions(events, players),
            collision,
            random,
            deps,
            queues,
            world,
        )
    val death =
        NpcDeath(
            npcs,
            players,
            mock(ObjRepository::class.java),
            setOf(NpcDeathDropHook { true }),
            emptySet<NpcDeathKillHook>(),
        )
    val dawn: Npc
    val dusk: Npc

    init {
        collision.allocateIfAbsent(player.coords.x, player.coords.z, player.coords.level)
        val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        with(NpcDeathScript(death)) { context.startup() }
        with(
            GrotesqueGuardiansCombat(
                encounters,
                death,
                players,
                deps,
                GrotesqueGuardiansAttackParameters(),
            )
        ) {
            context.startup()
        }
        with(GrotesqueGuardiansWorld(encounters)) { context.startup() }
        assertTrue(encounters.enter(player))
        dawn = npcList.single { it.type.id == 7852 }
        dusk = npcList.single { it.type.id == 7851 }
    }

    fun ring() {
        assertTrue(encounters.ringBell(player))
    }

    fun finalPhase() {
        ring()
        damage(dawn, 500, HitType.Ranged)
        damage(dusk, 500, HitType.Melee)
        idleUntil(112)
        damage(dawn, 500, HitType.Ranged)
        assertTrue(encounters.useHammer(player, dawn, "obj.slayer_rock_hammer"))
        idleUntil(117)
    }

    fun idleUntil(tick: Int) {
        while (clock.cycle < tick) {
            clearFrameSequences()
            clock.cycle++
            WorldQueueListProcess(queues).process()
            encounters.tickAll()
        }
    }

    fun attack(tick: Int, npc: Npc) {
        if (tick > clock.cycle) clearFrameSequences()
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

    // Client frame boundary: NpcPostTickProcess clears these fields after emitting each update.
    private fun clearFrameSequences() {
        npcList.forEach { it.pendingSequence = EntitySeq.NULL }
    }

    fun damage(npc: Npc, amount: Int, type: HitType) {
        val builder =
            HitBuilder(
                type,
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
        events.publish(NpcHitEvents.Modify(npc, builder))
        npc.currentMapClock = clock.cycle
        npc.processedMapClock = clock.cycle
        npc.launch {
            StandardNpcAccess(
                    npc,
                    this,
                    StandardNpcAccessContext(
                        { random },
                        { StandardNpcHitModifier(events) },
                        { StandardNpcHitProcessor(players, events, emptySet()) },
                    ),
                )
                .processQueuedHit(builder.build())
        }
    }

    fun projectiles() =
        mockingDetails(world)
            .invocations
            .filter { it.method.name == "projAnim" }
            .map { it.arguments[0] as ProjAnim }

    fun locAdds() = mockingDetails(world).invocations.count { it.method.name == "locAddPrivate" }

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
