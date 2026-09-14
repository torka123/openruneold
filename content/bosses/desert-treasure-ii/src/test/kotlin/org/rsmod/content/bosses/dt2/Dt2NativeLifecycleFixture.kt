package org.rsmod.content.bosses.dt2

import com.google.inject.AbstractModule
import com.google.inject.Guice
import dev.openrune.ServerCacheManager
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.mockito.Answers
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossExtensionRegistry
import org.rsmod.api.bosses.runtime.EncounterRegistry
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.InstanceStateKey
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.access.StandardNpcAccessContextFactory
import org.rsmod.api.npc.access.StandardNpcAccessLauncher
import org.rsmod.api.npc.events.interact.AiPlayerEvents
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.api.player.hook.PlayerTeleportHooks
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RouteFactory
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.Hit
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.region.Region
import org.rsmod.game.region.zone.RegionZoneCopy
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Real DT2 entry, session ownership, NPC registry, lifecycle scripts and native combat. */
@OptIn(InternalApi::class)
internal class Dt2NativeLifecycleFixture(val bossKind: Dt2Boss = Dt2Boss.VARDORVIS) :
    AutoCloseable {
    val clock = MapClock(100)
    val collision = CollisionFlagMap()
    val events = EventBus()
    val players = PlayerList()
    val npcList = NpcList()
    val npcRegistry = NpcRegistry(npcList, collision, events)
    val npcs = NpcRepository(clock, npcRegistry, npcList)
    var allocations = 0
        private set

    var allocationRefused = false
    var teleportRefused = false
    private val regions =
        mock(RegionRepository::class.java) { call ->
            when {
                call.method.name == "add" -> if (allocationRefused) null else allocateRegion()
                call.method.name.startsWith("isValid") -> true
                else -> Answers.RETURNS_DEFAULTS.answer(call)
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
    val hpBar = mock(BossHpBarScript::class.java)
    private val locs =
        mock(LocRepository::class.java) { call ->
            if (call.method.name.startsWith("findAll")) emptySequence<LocInfo>()
            else Answers.RETURNS_DEFAULTS.answer(call)
        }
    private val world = mock(WorldRepository::class.java)
    private val random = DefaultGameRandom(804L)
    private val accuracy =
        mock(AccuracyFormulae::class.java) { call ->
            if (call.method.name.startsWith("roll")) true else Answers.RETURNS_DEFAULTS.answer(call)
        }
    private val combat = NvPCombat(accuracy, mock(MaxHitFormulae::class.java), world)
    val bossEncounters = EncounterRegistry(random)
    val worldQueues = WorldQueueList()
    val queueProcess = WorldQueueListProcess(worldQueues)
    private val npcAccess =
        StandardNpcAccessLauncher(
            StandardNpcAccessContextFactory(
                random,
                StandardNpcHitModifier(events),
                StandardNpcHitProcessor(players, events, emptySet()),
            )
        )
    val duke =
        DukeEncounter(
            clock,
            npcs,
            instances,
            world,
            locs,
            collision,
            accuracy,
            bossEncounters,
            npcAccess,
            events,
            worldQueues,
            queueProcess,
        )
    val manager =
        Dt2EncounterManager(
            clock,
            instances,
            hpBar,
            npcs,
            locs,
            AiPlayerInteractions(events, players),
            collision,
            combat,
            world,
            events,
            mock(LeviathanRecordService::class.java),
            mock(MusicPlayer::class.java),
            mock(RouteFactory::class.java),
            duke,
            bossEncounters,
            worldQueues,
            queueProcess,
        )
    val storage = LeviathanDeathStorage(manager, mock(MarketPrices::class.java))
    val instanceRegistry = BossInstanceRegistry()
    private val protectedContext =
        mock(ProtectedAccessContext::class.java) { call ->
            when (call.method.name) {
                "getCollision" -> collision
                "getTeleportHooks" -> PlayerTeleportHooks.EMPTY
                "getTeleportValidator" ->
                    PlayerTeleportValidator(
                        setOf(
                            PlayerTeleportValidateHook { _, _, _ ->
                                if (teleportRefused) "Test teleport refusal." else null
                            }
                        )
                    )
                "getEventBus" -> events
                "getAreaChecker" -> mock(call.method.returnType)
                else -> Answers.RETURNS_DEFAULTS.answer(call)
            }
        }
    private val protectedLauncher =
        ProtectedAccessLauncher(
            mock(ProtectedAccessContextFactory::class.java) { call ->
                if (call.method.name == "create") protectedContext
                else Answers.RETURNS_DEFAULTS.answer(call)
            }
        )
    val instanceScript: Dt2InstanceScript =
        when (bossKind) {
            Dt2Boss.DUKE -> DukeInstance(instanceRegistry, manager, protectedLauncher)
            Dt2Boss.WHISPERER -> WhispererInstance(instanceRegistry, manager, protectedLauncher)
            Dt2Boss.VARDORVIS -> VardorvisInstance(instanceRegistry, manager, protectedLauncher)
            Dt2Boss.LEVIATHAN ->
                LeviathanInstance(instanceRegistry, manager, protectedLauncher, storage)
        }
    val nativeLifecycle: PlayerPostTickHook

    init {
        val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        Guice.createInjector(
                object : AbstractModule() {
                    override fun configure() {
                        bind(InstanceManager::class.java).toInstance(instances)
                        bind(MapClock::class.java).toInstance(clock)
                    }
                }
            )
            .injectMembers(instanceScript)
        with(instanceScript) { context.startup() }
        val lifecycle =
            Class.forName("org.rsmod.api.instances.hook.InstanceLifecycleScript")
                .getConstructor(InstanceManager::class.java, MapClock::class.java)
                .newInstance(instances, clock) as PluginScript
        with(lifecycle) { context.startup() }
        nativeLifecycle = lifecycle as PlayerPostTickHook
        with(Dt2Lifecycle(manager)) { context.startup() }
        val deps =
            BossDeps(
                random,
                accuracy,
                world,
                npcs,
                players,
                clock,
                worldQueues,
                collision,
                bossEncounters,
                BossExtensionRegistry(),
                combat,
            )
        val death = NpcDeath(npcs, players, mock(ObjRepository::class.java), emptySet(), emptySet())
        with(Dt2Combat(manager, death, players, deps)) { context.startup() }
    }

    private fun allocateRegion(): Region {
        val base = CoordGrid(6400 + allocations * 64, 6400)
        val region = Region(base, base.translate(64, 64), ++allocations, allocations)
        val sourceZoneX = (bossKind.regionId shr 8) * 8
        val sourceZoneZ = (bossKind.regionId and 255) * 8
        for (level in 0..3) for (x in 0..7) for (z in 0..7) {
            val destination = ZoneKey.fromAbsolute(base.x + x * 8, base.z + z * 8, level)
            region.registerZone(
                RegionZoneCopy(ZoneKey(sourceZoneX + x, sourceZoneZ + z, level), 0, null),
                destination,
            )
            collision.allocateIfAbsent(base.x + x * 8, base.z + z * 8, level)
        }
        return region
    }

    fun player(id: Int = 1): Player =
        Player().apply {
            uuid = id.toLong()
            accountHash = id.toLong()
            accountId = id
            characterId = id
            observerUUID = id.toLong()
            username = "dt2member$id"
            displayName = "DT2 Member $id"
            slotId = id
            assignUid()
            coords = bossKind.exit
            collision.allocateIfAbsent(coords.x, coords.z, coords.level)
            currentMapClock = clock.cycle
            processedMapClock = clock.cycle
            for (stat in
                listOf("hitpoints", "attack", "strength", "defence", "ranged", "magic", "prayer")) {
                statMap.setBaseLevel("stat.$stat", 99)
                statMap.setCurrentLevel("stat.$stat", 99)
            }
            inv = invMap.getOrPut("inv.inv")
            worn = invMap.getOrPut("inv.worn")
            players[id] = this
        }

    fun enter(
        player: Player = player(),
        difficulty: Dt2Difficulty = Dt2Difficulty.NORMAL,
        seed: Long = 42L,
    ): Dt2EncounterManager.Run {
        assertTrue(tryEnter(player, difficulty, seed))
        return run(assertNotNull(instances.sessionForPlayer(player)))
    }

    fun tryEnter(
        player: Player,
        difficulty: Dt2Difficulty = Dt2Difficulty.NORMAL,
        seed: Long = 42L,
    ): Boolean {
        var entered = false
        protectedLauncher.launch(player) {
            with(instanceScript) { entered = enterEncounter(difficulty, seed) }
        }
        return entered
    }

    fun leave(player: Player, expectedLocId: Int? = null): Boolean {
        var handled = false
        protectedLauncher.launch(player) {
            with(instanceScript) { handled = leaveEncounter(expectedLocId) }
        }
        return handled
    }

    @Suppress("UNCHECKED_CAST")
    fun run(session: InstanceSession): Dt2EncounterManager.Run {
        val field = Dt2EncounterManager::class.java.getDeclaredField("RUN_STATE")
        field.isAccessible = true
        val key = field.get(manager) as InstanceStateKey<Dt2EncounterManager.Run>
        return assertNotNull(session.stateOrNull(key))
    }

    fun attachActor(run: Dt2EncounterManager.Run, mechanic: Boolean): Npc =
        Npc(
                assertNotNull(ServerCacheManager.getNpc(Dt2Assets.helperNpcIds.first())),
                run.boss.coords,
            )
            .also {
                it.assignSpawnOwner(run.player, clock.cycle)
                npcs.add(it, 1000)
                instances.attachNpc(run.session.id, it)
                if (mechanic) run.helpers += it
            }

    fun attackVardorvis(run: Dt2EncounterManager.Run): Hit {
        check(run.bossKind == Dt2Boss.VARDORVIS)
        check(manager.attackDenial(run.player, run.boss) == null)
        val boss = run.boss
        run.player.coords = boss.coords.translate(-1, 0)
        boss.currentMapClock = clock.cycle
        boss.processedMapClock = clock.cycle
        boss.launch {
            val access =
                StandardNpcAccess(
                    boss,
                    this,
                    StandardNpcAccessContext(
                        { random },
                        { StandardNpcHitModifier(events) },
                        { StandardNpcHitProcessor(players, events, emptySet()) },
                    ),
                )
            events.publish(access, AiPlayerEvents.Ap2(run.player, boss))
        }
        val iterator = assertNotNull(run.player.queueList.iterator())
        return try {
            val hits =
                buildList<Hit> {
                    while (iterator.hasNext()) {
                        val hit = iterator.next().args as? Hit
                        if (hit != null) add(hit)
                    }
                }
            hits.last()
        } finally {
            iterator.cleanUp()
        }
    }

    fun closeCount(player: Player): Int =
        mockingDetails(hpBar).invocations.count {
            it.method.name == "onClose" && it.arguments.firstOrNull() === player
        }

    override fun close() {
        instances.sessionsForKey(bossKind.key).toList().forEach { instances.terminate(it) }
    }
}
