package org.rsmod.content.bosses.alchemicalhydra

import com.google.inject.AbstractModule
import com.google.inject.Guice
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.runtime.*
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.death.NpcDeathDropHook
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.death.plugin.NpcDeathScript
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.events.NpcQueueEvents
import org.rsmod.api.npc.events.interact.AiPlayerEvents
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.events.PlayerMovementEvent
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.api.player.hook.PlayerTeleportHooks
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.NpcRevealProcessor
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.content.generic.killcount.KillcountNpcKillHook
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.region.Region
import org.rsmod.game.region.zone.RegionZoneCopy
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
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

    @Test
    fun `native damage resolution caps all transitions and restores the next form after four ticks`() {
        val f = HydraFixture()
        f.activate()
        f.attackAt(101)
        f.damage(160)
        assertEquals(1060, f.npc.hitpoints)
        for ((index, threshold) in listOf(825, 550, 275).withIndex()) {
            val start = 101 + index * 5
            f.clock.cycle = start
            f.npc.hitpoints = threshold + 15
            f.damage(1000)
            assertEquals(threshold, f.npc.hitpoints)
            assertEquals(listOf(8616, 8617, 8618)[index], f.npc.visType.id)
            val before = f.hits().size
            f.attackAt(start + 3)
            f.encounters.tickAll()
            assertEquals(before, f.hits().size)
            f.clock.cycle = start + 4
            f.encounters.tickAll()
            assertEquals(listOf(8619, 8620, 8621)[index], f.npc.visType.id)
            assertEquals(threshold, f.npc.hitpoints)
            assertEquals(1100, f.npc.baseHitpointsLvl)
            f.attackAt(start + 5)
            assertEquals(listOf(2, 1, 1)[index], f.hits().size - before)
            assertEquals(listOf(26, 52, 55)[index], f.hits().last().second.damage)
        }
        assertEquals(HitType.Ranged, f.hits().last().second.type)
        assertEquals(120, f.npc.actionDelay)
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }

    @Test
    fun `observation room stays safe at the actual mapped template offset`() {
        val f = HydraFixture()
        repeat(12) { f.idleTick() }
        assertTrue(f.encounters.status(f.player).contains("state=OBSERVATION"))
        assertNotNull(f.encounters.attackDenial(f.player, f.npc))
        assertTrue(f.npc.movementLocked)
        assertTrue(f.hits().isEmpty())
    }

    @Test
    fun `simultaneous queued crossing hits cannot skip a gated phase`() {
        val f = HydraFixture()
        f.activate()
        f.npc.hitpoints = 840
        val first = f.buildDamage(1000)
        val second = f.buildDamage(1000)
        f.processDamage(first)
        assertEquals(825, f.npc.hitpoints)
        f.processDamage(second)
        assertEquals(825, f.npc.hitpoints)
        repeat(4) { f.idleTick() }
        assertEquals(8619, f.npc.visType.id)
        assertEquals(825, f.npc.hitpoints)
    }

    @Test
    fun `native exits invalidate every queued volley and detach the owned actor`() {
        for (ending in listOf("exit", "teleport", "logout", "disconnect", "death")) {
            val f = HydraFixture()
            f.activate()
            f.attackAt(101)
            val session = assertNotNull(f.instances.sessionForPlayer(f.player))
            val pending = f.hits().map { it.second }
            assertEquals(2, pending.size)
            when (ending) {
                "exit" -> f.leave()
                "teleport" -> {
                    val previous = f.player.coords
                    f.player.coords = HYDRA_PUBLIC_EXIT
                    f.events.publish(PlayerMovementEvent.CoordsMovedEvent(f.player, previous))
                }
                "logout" -> f.events.publish(SessionStateEvent.Logout(f.player))
                "disconnect" -> {
                    f.player.clientDisconnected.set(true)
                    f.lifecycle.onPostTick(f.player)
                }
                "death" -> f.instances.handleDeath(f.player, f.clock.cycle)
            }
            assertTrue(pending.all { !it.isQueueArgumentValid() }, ending)
            f.instances.tickReclaim(f.clock.cycle + 1)
            assertTrue(session.isEnded, ending)
            assertFalse(f.npc.isSlotAssigned, ending)
            assertTrue(f.instances.npcsForInstance(session.id).isEmpty(), ending)
            assertNull(f.instances.sessionForPlayer(f.player), ending)
            assertNull(f.deps.encounterRegistry.existing(f.npc), ending)
        }
    }

    @Test
    fun `two complete kills use one native actor and the 43 tick reform cadence`() {
        val f = HydraFixture()
        val session = assertNotNull(f.instances.sessionForPlayer(f.player))
        repeat(2) { completion ->
            f.activate()
            f.attackAt(f.clock.cycle + 1)
            for (threshold in listOf(825, 550, 275)) {
                f.npc.hitpoints = threshold + 1
                f.damage(4)
                repeat(4) { f.idleTick() }
            }
            assertEquals(8621, f.npc.visType.id)
            val encounter = f.deps.encounterRegistry.of(f.npc)
            f.damage(f.npc.hitpoints)
            f.killQueue()
            var guard = 30
            while (f.npc.activeCoroutine != null && guard-- > 0) f.idleTick()
            assertTrue(guard > 0)
            assertTrue(f.npc.hidden)
            assertTrue(encounter.isEnded)
            assertEquals(completion + 1, f.player.vars["varp.total_hydraboss_kills"])
            assertEquals(completion + 1, f.kills.size)
            assertEquals(f.player.coords, f.kills.last().dropCoords)
            assertEquals(f.clock.cycle + 43, f.npc.lifecycleRespawnCycle)
            repeat(42) { f.idleTick() }
            assertTrue(f.npc.hidden)
            f.idleTick()
            assertFalse(f.npc.hidden)
            assertEquals(8615, f.npc.visType.id)
            assertEquals(1100, f.npc.hitpoints)
            assertTrue(f.encounters.status(f.player).contains("state=OBSERVATION"))
            assertEquals(listOf(f.npc), f.instances.npcsForInstance(session.id))
            assertEquals(1, f.npcList.count())
            assertNotSame(encounter, f.deps.encounterRegistry.of(f.npc))
        }
    }

    @Test
    fun `cache default differs from the characterized encounter reform delay`() {
        // Read-only cache evidence, not gameplay parity: native death needs an explicit override.
        assertEquals(50, assertNotNull(ServerCacheManager.getNpc(8615)).respawnRate)
        assertEquals(43, HYDRA_RESPAWN_TICKS)
    }

    @Test
    fun `two native instances isolate attacks ownership cleanup and reentry`() {
        val f = HydraFixture()
        f.activate()
        f.attackAt(101)
        val first = assertNotNull(f.instances.sessionForPlayer(f.player))
        val partner =
            Player().apply {
                slotId = 2
                uuid = 2L
                assignUid()
                coords = HYDRA_PUBLIC_EXIT
                statMap.setBaseLevel("stat.hitpoints", 99)
                statMap.setCurrentLevel("stat.hitpoints", 99)
                inv = invMap.getOrPut("inv.inv")
                worn = invMap.getOrPut("inv.worn")
            }
        f.players[2] = partner
        val other = f.enter(partner)
        val second = assertNotNull(f.instances.sessionForPlayer(partner))
        assertNotEquals(first.id, second.id)
        assertNotEquals(other.coords, f.npc.coords)
        assertNotSame(f.instances.hydraState(f.player), f.instances.hydraState(partner))
        f.activate(partner)
        assertFalse(f.encounters.canAttack(other, f.player))
        assertFalse(f.encounters.canAttack(f.npc, partner))
        f.attackAt(102, other, partner)
        f.leave(partner)
        f.instances.tickReclaim(103)
        assertTrue(second.isEnded)
        assertFalse(other.isSlotAssigned)
        assertTrue(f.hits().all { it.second.isQueueArgumentValid() })
        assertEquals(listOf(f.npc), f.instances.npcsForInstance(first.id))
        val replacement = f.enter(partner)
        assertNotSame(other, replacement)
        assertNotEquals(second.id, f.instances.sessionForPlayer(partner)?.id)
        assertEquals(2, f.npcList.count())
        assertNotNull(f.encounters.attackDenial(partner, replacement))
    }

    @Test
    fun `poison lands at projectile impact and expires then cannot survive teardown`() {
        val f = HydraFixture()
        f.activate()
        listOf(101, 107, 113, 119).forEach { f.attackAt(it) }
        val run = assertNotNull(f.instances.hydraState(f.player))
        assertEquals(
            listOf(
                f.player.coords,
                f.player.coords.translate(2, 0),
                f.player.coords.translate(0, 2),
            ),
            run.pendingPoison.map { it.tile },
        )
        val impact = run.pendingPoison.minOf { it.impactCycle }
        val expiry = run.pendingPoison.maxOf { it.impactCycle } + 20
        while (f.clock.cycle < impact - 1) f.idleTick()
        assertTrue(run.poisonPools.isEmpty())
        f.idleTick()
        assertTrue(run.poisonPools.isNotEmpty())
        assertTrue(f.hits().any { it.second.type == HitType.Typeless && it.second.damage == 7 })
        assertTrue(f.hits().any { it.second.type == HitType.Typeless && it.second.damage == 12 })
        while (f.clock.cycle < expiry) f.idleTick()
        assertTrue(run.poisonPools.isEmpty())
        f.encounters.launchSpecial(f.npc, f.player, HydraSpecial.POISON)
        assertTrue(run.pendingPoison.isNotEmpty())
        val before = f.projectiles().size
        val incoming = f.buildDamage(40)
        f.leave()
        f.instances.tickReclaim(f.clock.cycle + 1)
        repeat(30) { f.idleTick() }
        assertTrue(run.pendingPoison.isEmpty())
        assertTrue(run.poisonPools.isEmpty())
        assertEquals(before, f.projectiles().size)
        assertFalse(incoming.isValidNow())
        assertTrue(f.hits().all { !it.second.isQueueArgumentValid() })
    }

    @Test
    fun `lightning and flame retain native late-cycle movement and teardown resources`() {
        val f = HydraFixture()
        f.activate()
        f.npc.hitpoints = 826
        f.damage(4)
        repeat(4) { f.idleTick() }
        val run = assertNotNull(f.instances.hydraState(f.player))
        val launch = f.clock.cycle
        f.encounters.launchSpecial(f.npc, f.player, HydraSpecial.LIGHTNING)
        f.idleTick()
        assertTrue(run.lightningCurrents.isEmpty())
        f.idleTick()
        val origin = f.instances.hydraCoord(run.session, HYDRA_CHAMBER_ENTRY)
        assertEquals(
            listOf(14 to 4, 14 to 13, 4 to 4, 3 to 13).map { (x, z) -> origin.translate(x, z) },
            run.lightningCurrents.map { it.tile },
        )
        assertTrue(run.lightningCurrents.all { it.endCycle == launch + 2 + 18 })
        f.npc.hitpoints = 551
        f.damage(4)
        assertTrue(run.lightningCurrents.isEmpty())
        repeat(4) { f.idleTick() }
        f.player.coords = origin.translate(8, 4)
        f.encounters.launchSpecial(f.npc, f.player, HydraSpecial.FLAME)
        assertEquals(10, run.flameTiles.size)
        assertTrue(f.npc.movementLocked)
        val tracker = assertNotNull(run.trackingFlame)
        f.idleTick()
        assertEquals(tracker.translate(-1, -1), run.trackingFlame)
        val lastTile = assertNotNull(run.trackingFlame)
        f.player.coords = lastTile
        f.idleTick()
        assertEquals(5, run.fireBurnsRemaining)
        assertTrue(f.hits().any { it.second.type == HitType.Typeless && it.second.damage == 25 })
        repeat(10) { f.idleTick() }
        assertEquals(0, run.fireBurnsRemaining)
        assertEquals(
            5,
            f.hits().count { it.second.type == HitType.Typeless && it.second.damage == 5 },
        )
        f.leave()
        assertTrue(run.flameTiles.isEmpty())
        assertNull(run.trackingFlame)
        assertTrue(f.hits().all { !it.second.isQueueArgumentValid() })
    }

    @org.junit.jupiter.api.AfterEach
    fun releaseNativeFixtures() {
        framework().clearInlineMocks()
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
        InstanceManager(
            regions,
            npcs,
            players,
            events,
            InstanceAreaResolver(),
            clock,
            collision,
            npcList,
        )
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
    val deps =
        BossDeps(
            random,
            accuracy,
            world,
            npcs,
            players,
            clock,
            WorldQueueList(),
            collision,
            EncounterRegistry(random),
            BossExtensionRegistry(),
            NvPCombat(accuracy, mock(MaxHitFormulae::class.java), world),
        )
    val encounters =
        AlchemicalHydraEncounterManager(
            clock,
            instances,
            AiPlayerInteractions(events, players),
            random,
            deps,
            AlchemicalHydraHazards(clock, instances, collision, random, deps, world),
            AlchemicalHydraTransforms(clock),
        )
    val npc: Npc
    val kills = mutableListOf<NpcDeathKillContext>()
    val death =
        NpcDeath(
            npcs,
            players,
            mock(ObjRepository::class.java),
            setOf(NpcDeathDropHook { true }),
            setOf(KillcountNpcKillHook(), NpcDeathKillHook { kills += it }),
        )
    val instanceScript =
        AlchemicalHydraWorld(
            BossInstanceRegistry(),
            encounters,
            mock(ProtectedAccessLauncher::class.java),
        )
    val accessContext =
        mock(ProtectedAccessContext::class.java) { call ->
            when (call.method.name) {
                "getCollision" -> collision
                "getEventBus" -> events
                "getTeleportHooks" -> PlayerTeleportHooks.EMPTY
                "getTeleportValidator" -> PlayerTeleportValidator(emptySet())
                "getAreaChecker" -> mock(call.method.returnType)
                else -> RETURNS_DEFAULTS.answer(call)
            }
        }
    val lifecycle: PlayerPostTickHook

    init {
        collision.allocateIfAbsent(player.coords.x, player.coords.z, 0)
        val combat =
            AlchemicalHydraCombat(
                encounters,
                death,
                players,
                deps,
                AlchemicalHydraAttackParameters(instances),
                AlchemicalHydraDamageRules(instances, deps, AlchemicalHydraTransforms(clock)),
            )
        val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        with(NpcDeathScript(death)) { context.startup() }
        with(combat) { context.startup() }
        val injector =
            Guice.createInjector(
                object : AbstractModule() {
                    override fun configure() {
                        bind(InstanceManager::class.java).toInstance(instances)
                        bind(MapClock::class.java).toInstance(clock)
                    }
                }
            )
        injector.injectMembers(instanceScript)
        with(instanceScript) { context.startup() }
        with(AlchemicalHydraLifecycle(encounters)) { context.startup() }
        val plugin =
            injector.getInstance(
                Class.forName("org.rsmod.api.instances.hook.InstanceLifecycleScript")
                    .asSubclass(PluginScript::class.java)
            )
        with(plugin) { context.startup() }
        lifecycle = plugin as PlayerPostTickHook
        npc = enter(player)
    }

    fun enter(who: Player): Npc {
        who.launch {
            with(instanceScript) { ProtectedAccess(who, this@launch, accessContext).enterHydra() }
        }
        return instances.npcsForInstance(assertNotNull(instances.sessionForPlayer(who)).id).single()
    }

    fun activate(who: Player = player) {
        who.launch { assertTrue(encounters.crossDoor(ProtectedAccess(who, this, accessContext))) }
    }

    fun leave(who: Player = player) {
        who.launch {
            with(instanceScript) { ProtectedAccess(who, this@launch, accessContext).leaveHydra() }
        }
    }

    fun idleTick() {
        clock.cycle++
        npc.currentMapClock = clock.cycle
        npc.processedMapClock = clock.cycle
        if (npc.activeCoroutine != null) npc.advanceActiveCoroutine()
        NpcRevealProcessor(npcs).process(npc)
        encounters.tickAll()
    }

    fun killQueue() {
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
                NpcQueueEvents.Type(npc, null, "queue.death".asRSCM(RSCMType.QUEUE)),
            )
        }
    }

    fun damage(amount: Int) {
        processDamage(buildDamage(amount))
    }

    fun buildDamage(amount: Int): Hit {
        val builder =
            HitBuilder(
                type = HitType.Typeless,
                damage = amount,
                sourceUid = player.uid.packed,
                sourceSlot = player.slotId,
                isFromNpc = false,
                isFromPlayer = true,
                clientDelay = 0,
                righthandType = null,
                secondaryType = null,
                targetHitmark = 0,
                sourceHitmark = 0,
                publicHitmark = null,
                zeroDamageHitmarkLit = null,
                zeroDamageHitmarkTint = null,
                maxDamageHitmarkLit = null,
                targetMaxDamageThreshold = Int.MAX_VALUE,
                sourceMaxDamageThreshold = Int.MAX_VALUE,
            )
        events.publish(NpcHitEvents.Modify(npc, builder))
        return builder.build()
    }

    fun processDamage(hit: Hit) {
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
                .processQueuedHit(hit)
        }
    }

    fun attackAt(tick: Int, actor: Npc = npc, target: Player = player) {
        clock.cycle = tick
        actor.currentMapClock = tick
        actor.processedMapClock = tick
        actor.launch {
            events.publish(
                StandardNpcAccess(
                    actor,
                    this,
                    StandardNpcAccessContext(
                        { random },
                        { StandardNpcHitModifier(events) },
                        { StandardNpcHitProcessor(players, events, emptySet()) },
                    ),
                ),
                AiPlayerEvents.Ap2(target, actor),
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
