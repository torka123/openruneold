package org.rsmod.content.bosses.kraken

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
import org.rsmod.api.instances.*
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
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
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.hit.Hit
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

/**
 * Same pre-migration assertions, now entered through InstanceScript and its native lifecycle
 * plugin.
 */
@OptIn(InternalApi::class)
class KrakenEncounterCharacterizationTest {
    @Test
    fun `entry spawns exactly five owned dormant actors at their mapped coordinates`() {
        val f = Fixture()
        f.enter()
        assertEquals(CoordGrid(6440, 6438), f.player.coords)
        assertEquals(
            listOf(38 to 50, 35 to 50, 35 to 54, 44 to 50, 44 to 54),
            f.created.map { it.coords.x - 6400 to it.coords.z - 6400 },
        )
        assertEquals(
            listOf(KrakenAssets.BOSS_WHIRLPOOL) + List(4) { KrakenAssets.TENTACLE_WHIRLPOOL },
            f.created.map { it.visType.internalName },
        )
        assertTrue(
            f.created.all { it.spawnOwner == f.player.uid && !it.respawns && it.movementLocked }
        )
        val session = assertNotNull(f.instances.sessionForPlayer(f.player))
        assertEquals(5, f.instances.npcsForInstance(session.id).size)
        assertEquals(setOf(1L), session.occupants)
    }

    @Test
    fun `individual tentacle activation then boss activation preserve identity stats and delay`() {
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
        assertTrue(
            f.created.all {
                it.actionDelay == 101 && it.apRangeOverride == 10 && it.maxOpRangeOverride == 64
            }
        )
        assertFalse(f.encounters.disturb(f.player, boss))
        assertNull(f.encounters.attackDenial(f.player, boss))
    }

    @Test
    fun `boss completion removes tentacles once and preserves personal drop coordinate`() {
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

    @Test
    fun `registered combat preserves four tick magic and ranged attacks with native travel delays`() {
        val f = Fixture()
        f.enter()
        // Approach within each actor's existing ten-tile range before disturbing the boss.
        f.player.coords = f.player.coords.translate(0, 8)
        VarPlayerIntMapSetter.set(f.player, "varbit.prayer_protectfrommagic", 1)
        f.encounters.disturb(f.player, f.created.first())
        val bursts = mutableListOf<Int>()
        repeat(9) {
            val before = f.projectiles().size
            f.tickCombat()
            if (f.projectiles().size > before) bursts += f.clock.cycle
        }
        assertEquals(listOf(101, 105, 109), bursts)
        assertEquals(15, f.projectiles().size)
        val hits = f.queuedHits()
        assertEquals(List(3) { listOf(28, 2, 2, 2, 2) }.flatten(), hits.map { it.second.damage })
        assertEquals(f.projectiles().map { it.serverCycles }, hits.map { it.first })
        assertEquals(
            List(3) { listOf("Magic", "Ranged", "Ranged", "Ranged", "Ranged") }.flatten(),
            hits.map { it.second.type.toString() },
        )
        assertTrue(hits.all { it.second.isValidNow() })
        assertTrue(f.created.all { it.actionDelay == 113 })
    }

    @Test
    fun `normal exit retires actors native encounters and every queued hit before reentry`() {
        val f = Fixture()
        repeat(2) {
            f.enter()
            val session = assertNotNull(f.instances.sessionForPlayer(f.player))
            val actors = f.instances.npcsForInstance(session.id).toList()
            f.player.coords = f.player.coords.translate(0, 8)
            f.encounters.disturb(f.player, actors.first())
            f.tickCombat()
            assertTrue(actors.all { f.registry.existing(it) != null })
            f.leave()
            assertEquals(KRAKEN_PUBLIC_EXIT, f.player.coords)
            assertTrue(session.isEnded)
            assertTrue(f.instances.npcsForInstance(session.id).isEmpty())
            assertNull(f.instances.sessionForPlayer(f.player))
            assertTrue(actors.all { !it.isSlotAssigned && f.registry.existing(it) == null })
            assertTrue(f.queuedHits().all { !it.second.isQueueArgumentValid() })
            val count = f.projectiles().size
            repeat(10) { f.tickCombat() }
            assertEquals(count, f.projectiles().size)
        }
        assertEquals(10, f.created.size)
        assertEquals(10, f.deleted.size)
    }

    @Test
    fun `simultaneous private instances retain distinct owners targets and lifecycle`() {
        val f = Fixture()
        f.enter()
        val second =
            Player().apply {
                slotId = 2
                uuid = 2L
                observerUUID = 2L
                assignUid()
                coords = KRAKEN_PUBLIC_EXIT
                statMap.setBaseLevel("stat.hitpoints", 99)
                statMap.setCurrentLevel("stat.hitpoints", 99)
                inv = invMap.getOrPut("inv.inv")
                worn = invMap.getOrPut("inv.worn")
            }
        f.players[2] = second
        f.enter(second)
        val firstSession = assertNotNull(f.instances.sessionForPlayer(f.player))
        val secondSession = assertNotNull(f.instances.sessionForPlayer(second))
        assertNotEquals(firstSession.id, secondSession.id)
        assertNotEquals(f.player.coords, second.coords)
        val firstBoss = f.created[0]
        val secondBoss = f.created[5]
        assertFalse(f.encounters.disturb(f.player, secondBoss))
        assertNotNull(f.encounters.attackDenial(f.player, secondBoss))
        for ((p, b) in listOf(f.player to firstBoss, second to secondBoss)) {
            p.coords = p.coords.translate(0, 8)
            f.encounters.disturb(p, b)
        }
        f.tickCombat()
        assertEquals(5, f.queuedHits(second).size)
        f.leave()
        assertSame(secondSession, f.instances.sessionForPlayer(second))
        assertTrue(f.queuedHits(second).all { it.second.isValidNow() })
        assertEquals(5, f.instances.npcsForInstance(secondSession.id).size)
    }

    @Test
    fun `native logout disconnect death and external teleport invalidate pending projectiles`() {
        for (ending in listOf("logout", "disconnect", "death", "teleport")) {
            val f = Fixture()
            f.enter()
            val session = assertNotNull(f.instances.sessionForPlayer(f.player))
            f.player.coords = f.player.coords.translate(0, 8)
            f.encounters.disturb(f.player, f.created.first())
            f.tickCombat()
            assertEquals(5, f.queuedHits().size)
            when (ending) {
                "logout" -> f.events.publish(SessionStateEvent.Logout(f.player))
                "disconnect" -> {
                    f.player.clientDisconnected.set(true)
                    f.lifecycle.onPostTick(f.player)
                }
                "death" -> f.instances.handleDeath(f.player, f.clock.cycle)
                "teleport" -> {
                    val previous = f.player.coords
                    f.player.coords = KRAKEN_PUBLIC_EXIT
                    f.events.publish(PlayerMovementEvent.CoordsMovedEvent(f.player, previous))
                }
            }
            f.instances.tickReclaim(f.clock.cycle + 1)
            assertTrue(session.isEnded, ending)
            assertTrue(f.queuedHits().all { !it.second.isQueueArgumentValid() }, ending)
            assertTrue(f.created.all { f.registry.existing(it) == null }, ending)
            assertNull(f.instances.sessionForPlayer(f.player), ending)
        }
    }

    @Test
    fun `registered death queue runs native death and loot hooks at the owners tile`() {
        val f = Fixture()
        f.enter()
        val session = assertNotNull(f.instances.sessionForPlayer(f.player))
        val boss = f.created.first()
        f.encounters.disturb(f.player, boss)
        boss.recordDamage(f.player, 255)
        f.kill(boss)
        repeat(30) { f.tickCombat() }
        assertFalse(boss.isSlotAssigned)
        assertEquals(5, f.deleted.size)
        assertTrue(f.instances.npcsForInstance(session.id).isEmpty())
        val kill = f.kills.single()
        assertSame(f.player, kill.hero)
        assertSame(boss, kill.npc)
        assertEquals(f.player.coords, kill.dropCoords)
        assertNull(f.encounters.beginBossDeath(boss))
        assertEquals(1, f.kills.size)
        // Existing no-natural-respawn policy is retained; the next kill uses a fresh native
        // session.
        f.leave()
        f.enter()
        assertNotEquals(session.id, assertNotNull(f.instances.sessionForPlayer(f.player)).id)
        assertEquals(5, f.created.count { it.isSlotAssigned })
    }

    private class Fixture {
        val clock = MapClock().apply { cycle = 100 }
        val collision = CollisionFlagMap()
        val events = EventBus()
        val players = PlayerList()
        val player =
            Player().apply {
                slotId = 1
                uuid = 1L
                observerUUID = 1L
                assignUid()
                coords = KRAKEN_PUBLIC_EXIT
                statMap.setCurrentLevel("stat.hitpoints", 99)
                statMap.setBaseLevel("stat.hitpoints", 99)
                inv = invMap.getOrPut("inv.inv")
                worn = invMap.getOrPut("inv.worn")
            }
        val created = mutableListOf<Npc>()
        val deleted = mutableListOf<Npc>()
        val npcs =
            mock(NpcRepository::class.java) { call ->
                when {
                    call.method.name == "add" && call.arguments.firstOrNull() is Npc -> {
                        val npc = call.arguments[0] as Npc
                        npc.slotId = created.size + 1
                        npc.assignUid()
                        npc.currentMapClock = clock.cycle
                        npc.processedMapClock = clock.cycle
                        created += npc
                        null
                    }
                    call.method.name == "del" && call.arguments.firstOrNull() is Npc -> {
                        val npc = call.arguments[0] as Npc
                        deleted += npc
                        events.publish(NpcStateEvents.Delete(npc))
                        npc.clearUid()
                        npc.slotId = -1
                        null
                    }
                    else -> RETURNS_DEFAULTS.answer(call)
                }
            }
        var allocations = 0
        val regions =
            mock(RegionRepository::class.java) { call ->
                if (call.method.name == "add") {
                    val base = CoordGrid(6400 + allocations * 64, 6400)
                    val region = Region(base, base.translate(64, 64), ++allocations, allocations)
                    for (x in 0..7) for (z in 0..7) {
                        val tile = base.translate(x * 8, z * 8)
                        region.registerZone(
                            RegionZoneCopy(ZoneKey(280 + x, 1248 + z, 0), 0, null),
                            ZoneKey.from(tile),
                        )
                        collision.allocateIfAbsent(tile.x, tile.z, 0)
                    }
                    region
                } else if (call.method.name == "isValid") true else RETURNS_DEFAULTS.answer(call)
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
                NpcList(),
            )
        val random =
            object : GameRandom {
                override fun of(maxExclusive: Int) = maxExclusive - 1

                override fun of(minInclusive: Int, maxInclusive: Int) = maxInclusive

                override fun randomDouble() = 0.99
            }
        val registry = EncounterRegistry(random)
        val output = mock(WorldRepository::class.java)
        val accuracy =
            mock(AccuracyFormulae::class.java) { call ->
                if (call.method.name.startsWith("roll")) true else RETURNS_DEFAULTS.answer(call)
            }
        val deps =
            BossDeps(
                random,
                accuracy,
                output,
                npcs,
                players,
                clock,
                WorldQueueList(),
                collision,
                registry,
                BossExtensionRegistry(),
                NvPCombat(accuracy, mock(MaxHitFormulae::class.java), output),
            )
        val encounters =
            KrakenWhirlpools(clock, instances, npcs, AiPlayerInteractions(events, players), players)
        val world =
            KrakenWorld(
                BossInstanceRegistry(),
                encounters,
                mock(ProtectedAccessLauncher::class.java),
            )
        val kills = mutableListOf<NpcDeathKillContext>()
        val combat =
            KrakenCombat(
                encounters,
                NpcDeath(
                    npcs,
                    players,
                    mock(ObjRepository::class.java),
                    setOf(NpcDeathDropHook { true }),
                    setOf(NpcDeathKillHook { kills += it }),
                ),
                deps,
            )
        val lifecycle: PlayerPostTickHook
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

        init {
            players[1] = player
            collision.allocateIfAbsent(player.coords.x, player.coords.z, 0)
            val injector =
                Guice.createInjector(
                    object : AbstractModule() {
                        override fun configure() {
                            bind(InstanceManager::class.java).toInstance(instances)
                            bind(MapClock::class.java).toInstance(clock)
                        }
                    }
                )
            injector.injectMembers(world)
            val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(world) { context.startup() }
            with(combat) { context.startup() }
            // Load the actual internal shared plugin through its normal injector, not a copy of its
            // hooks.
            val plugin =
                injector.getInstance(
                    Class.forName("org.rsmod.api.instances.hook.InstanceLifecycleScript")
                        .asSubclass(PluginScript::class.java)
                )
            with(plugin) { context.startup() }
            lifecycle = plugin as PlayerPostTickHook
        }

        fun enter() {
            enter(player)
        }

        fun enter(target: Player) {
            target.launch {
                with(world) { ProtectedAccess(target, this@launch, accessContext).enterKraken() }
            }
            assertNotNull(instances.sessionForPlayer(target))
        }

        fun leave() {
            player.launch {
                with(world) { ProtectedAccess(player, this@launch, accessContext).leaveKraken() }
            }
        }

        fun kill(npc: Npc) {
            npc.hitpoints = 0
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

        fun tickCombat() {
            clock.cycle++
            for (npc in created.filter { it.isSlotAssigned }) {
                npc.currentMapClock = clock.cycle
                npc.processedMapClock = clock.cycle
                if (npc.activeCoroutine != null) npc.advanceActiveCoroutine()
                else if (
                    npc.visType.internalName in setOf(KrakenAssets.BOSS, KrakenAssets.TENTACLE)
                ) {
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
                            AiPlayerEvents.Ap2(assertNotNull(encounters.owner(npc)), npc),
                        )
                    }
                }
            }
        }

        fun projectiles() =
            mockingDetails(output)
                .invocations
                .filter { it.method.name == "projAnim" }
                .map { it.arguments[0] as ProjAnim }

        fun queuedHits(target: Player = player): List<Pair<Int, Hit>> {
            val iterator = target.queueList.iterator() ?: return emptyList()
            return try {
                buildList {
                    while (iterator.hasNext()) {
                        val queue = iterator.next()
                        (queue.args as? Hit)?.let { add(queue.remainingCycles to it) }
                    }
                }
            } finally {
                iterator.cleanUp()
            }
        }
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}
