package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import dev.openrune.types.NpcMode
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossExtensionRegistry
import org.rsmod.api.bosses.runtime.EncounterRegistry
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.death.NpcDeathDropHook
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.game.process.npc.NpcFaceSquareProcessor
import org.rsmod.api.game.process.npc.NpcPostTickProcess
import org.rsmod.api.game.process.npc.NpcQueueProcessor
import org.rsmod.api.game.process.player.PlayerQueueProcessor
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.npc.access.StandardNpcAccessContextFactory
import org.rsmod.api.npc.access.StandardNpcAccessLauncher
import org.rsmod.api.npc.events.interact.AiPlayerEvents
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.hit.DeferredPlayerHit
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.api.player.hook.PlayerTeleportHook
import org.rsmod.api.player.hook.PlayerTeleportHooks
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.EntityLifecycleProcess
import org.rsmod.api.repo.controller.ControllerRepository
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.utils.logging.GameExceptionHandler
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Production registration/native combat characterization, with captured attack animation
 * identities: guards1979 at3284:9773-78 and808:6376-77/7617-19/9837-38; Reaver9194 at808:6666-69.
 * Fumus390 at3284:9773-80 and Glacies368 at808:9837-38/9854-56 supply the two projectile fixtures
 * below. These Grid Master samples do not certify general normal timing, damage, prayer or other
 * minion projectiles. Player hits are inspected on the native queue; this suite does not claim
 * player impact processing or complete bodyguard-death parity. A delegating native protocol records
 * sequences emitted by accepted attacks without replacing the combat handler.
 */
@OptIn(InternalApi::class)
class NexMinionCombatCharacterizationTest {
    @Test
    fun `each called guard retains native launch prayer damage projectile and five tick gate`() {
        for (guardId in 11283..11286) for (protected in listOf(false, true)) {
            val f = NexMinionTestWorld()
            f.reachGuard(guardId)
            val guard = f.npc(guardId)
            val sequences = recordSequences(f, guard)
            f.protectMagic(protected)
            val start = f.clock.cycle
            f.attack(guard)
            val (delay, hit) = f.hits().single()
            val projectile = f.projectiles().last()
            assertEquals(if (protected) 10 else 20, hit.damage)
            assertEquals(HitType.Magic, hit.type)
            assertEquals(1979, guard.pendingSequence.id)
            assertEquals(listOf(Sequence(start, 1979, 0)), sequences)
            assertEquals(
                when (guardId) {
                    11283 -> 390
                    11286 -> 368
                    else -> 1999
                },
                projectile.spotanim,
            )
            assertEquals(projectile.serverCycles, delay)
            assertEquals(start + 5, guard.actionDelay)
            val rolls = f.accuracyRolls
            f.attack(guard)
            f.tick(4)
            f.attack(guard)
            assertEquals(1, f.hits().size)
            assertEquals(rolls, f.accuracyRolls, "Cooldown rejection must not roll accuracy")
            assertEquals(
                listOf(Sequence(start, 1979, 0)),
                sequences,
                "Cooldown rejection and native post-tick must not replay the cast",
            )
            f.tick()
            f.attack(guard)
            assertEquals(2, f.hits().size)
            assertEquals(start + 10, guard.actionDelay)
            assertEquals(listOf(Sequence(start, 1979, 0), Sequence(start + 5, 1979, 0)), sequences)
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `Fumus and Glacies native projectiles match captured fields without changing queued delay`() {
        val captures =
            listOf(
                CapturedProjectile(
                    11283,
                    CoordGrid(2913, 5215),
                    CoordGrid(2915, 5212),
                    390,
                    76,
                    124,
                    3,
                ),
                CapturedProjectile(
                    11286,
                    CoordGrid(2913, 5191),
                    CoordGrid(2919, 5192),
                    368,
                    106,
                    0,
                    4,
                ),
            )
        for (capture in captures) {
            val f = NexMinionTestWorld()
            f.reachGuard(capture.guardId)
            val guard = f.npc(capture.guardId)
            f.player.coords = capture.target
            assertEquals(capture.source, guard.coords)
            val beforeProjectiles = f.projectiles().size
            val start = f.clock.cycle
            f.attack(guard)
            assertEquals(beforeProjectiles + 1, f.projectiles().size)
            val projectile = f.projectiles().last()
            assertEquals(capture.spotanim, projectile.spotanim)
            assertEquals(capture.source, projectile.startCoord)
            assertEquals(guard.slotId + 1, projectile.sourceIndex)
            assertEquals(capture.target, projectile.endCoord)
            assertEquals(-(f.player.slotId + 1), projectile.targetIndex)
            assertEquals(51, projectile.startTime)
            assertEquals(capture.endTime, projectile.endTime)
            assertEquals(16, projectile.angle)
            assertEquals(64, projectile.progress)
            assertEquals(172, projectile.startHeight)
            assertEquals(capture.endHeight, projectile.endHeight)
            val (delay, hit) = f.hits().single()
            assertEquals(capture.hitDelay, delay, "Retain the existing magic_spell queued delay")
            assertEquals(capture.hitDelay, projectile.serverCycles)
            assertEquals(20, hit.damage)
            assertEquals(HitType.Magic, hit.type)
            assertEquals(1979, guard.pendingSequence.id)
            assertEquals(start + 5, guard.actionDelay)
            val rolls = f.accuracyRolls

            f.attack(guard)
            f.tick(4)
            f.attack(guard)
            assertEquals(
                beforeProjectiles + 1,
                f.projectiles().size,
                "Cooldown cannot emit another projectile",
            )
            assertEquals(1, f.hits().size)
            assertEquals(rolls, f.accuracyRolls)
            f.tick()
            f.attack(guard)
            assertEquals(beforeProjectiles + 2, f.projectiles().size)
            assertEquals(projectile, f.projectiles().last())
            assertEquals(2, f.hits().size)
            assertEquals(start + 10, guard.actionDelay)
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `inactive guard outsider and inaccurate native magic do not inflict damage`() {
        val f = NexMinionTestWorld()
        val fumus = f.npc(11283)
        val sequences = recordSequences(f, fumus)
        f.attack(fumus)
        assertTrue(f.hits().isEmpty())
        assertEquals(0, f.accuracyRolls)
        assertTrue(sequences.isEmpty(), "An inactive guard must not emit an attack animation")
        f.reachGuard(11283)
        f.accurate = false
        val start = f.clock.cycle
        f.attack(fumus)
        assertEquals(0, f.hits().single().second.damage)
        assertEquals(1, f.accuracyRolls)
        assertEquals(listOf(Sequence(start, 1979, 0)), sequences)
        f.tick(5)
        f.player.coords = NEX_BANK_TELEPORT
        f.attack(fumus)
        assertEquals(1, f.hits().size)
        assertEquals(1, f.accuracyRolls)
        assertEquals(listOf(Sequence(start, 1979, 0)), sequences)
    }

    @Test
    fun `siphon reaver uses native magic with retained eighteen maximum and five tick rate`() {
        for (protected in listOf(false, true)) {
            val f = NexMinionTestWorld()
            val reaver = f.summonReaver()
            val sequences = recordSequences(f, reaver)
            f.protectMagic(protected)
            val start = f.clock.cycle
            val beforeProjectiles = f.projectiles().size
            f.attack(reaver)
            val (delay, hit) = f.hits().single()
            assertEquals(2, delay, "Preserve the native no-projectile magic hit delay")
            assertEquals(if (protected) 9 else 18, hit.damage)
            assertEquals(HitType.Magic, hit.type)
            assertEquals(9194, reaver.pendingSequence.id)
            assertEquals(listOf(Sequence(start, 9194, 0)), sequences)
            assertEquals(start + 5, reaver.actionDelay)
            assertEquals(beforeProjectiles, f.projectiles().size)
            f.attack(reaver)
            f.tick(4)
            f.attack(reaver)
            assertEquals(1, f.hits().size)
            assertEquals(
                listOf(Sequence(start, 9194, 0)),
                sequences,
                "Cooldown rejection must not replay the Reaver attack",
            )
            f.tick()
            f.attack(reaver)
            assertEquals(2, f.hits().size)
            assertEquals(start + 10, reaver.actionDelay)
            assertEquals(listOf(Sequence(start, 9194, 0), Sequence(start + 5, 9194, 0)), sequences)
            framework().clearInlineMocks()
        }
    }

    private fun recordSequences(f: NexMinionTestWorld, npc: Npc): List<Sequence> {
        val sequences = mutableListOf<Sequence>()
        val protocol = npc.infoProtocol
        npc.infoProtocol =
            object : NpcInfoProtocol by protocol {
                override fun setSequence(seq: Int, delay: Int) {
                    sequences += Sequence(f.clock.cycle, seq, delay)
                    protocol.setSequence(seq, delay)
                }
            }
        return sequences
    }

    private data class Sequence(val cycle: Int, val id: Int, val delay: Int)

    private data class CapturedProjectile(
        val guardId: Int,
        val source: CoordGrid,
        val target: CoordGrid,
        val spotanim: Int,
        val endTime: Int,
        val endHeight: Int,
        val hitDelay: Int,
    )

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
            assertNotNull(ServerCacheManager.getAnim(1979), "Native bodyguard cast asset")
            assertNotNull(ServerCacheManager.getAnim(9194), "Native Reaver attack asset")
        }
    }
}

@OptIn(InternalApi::class)
internal class NexMinionTestWorld(
    duo: Boolean = false,
    nativeDeaths: Boolean = false,
    enterRoom: Boolean = true,
    private val nativeLocs: Boolean = false,
    nativeWorld: Boolean = false,
    deathObjects: ObjRepository? = null,
    deathDropHooks: Set<NpcDeathDropHook> = emptySet(),
    deathKillHooks: Set<NpcDeathKillHook> = emptySet(),
    nativeCollision: CollisionFlagMap? = null,
) {
    val clock = MapClock().apply { cycle = 100 }
    val events = EventBus()
    val commands = CheatCommandMap()
    val players = PlayerList()
    val npcs = NpcList()
    val collision = nativeCollision ?: CollisionFlagMap()
    val locUpdates = ZoneUpdateMap()
    private val locStorage = LocZoneStorage()
    var rejectLocAddAt: CoordGrid? = null
    var rejectLocDeleteAt: CoordGrid? = null
    val locs =
        if (nativeLocs)
            mock(
                LocRepository::class.java,
                withSettings()
                    .spiedInstance(
                        LocRepository(
                            clock,
                            LocRegistry(
                                locStorage,
                                LocRegistryNormal(locUpdates, collision, locStorage),
                                mock(LocRegistryRegion::class.java),
                            ),
                            mock(RegionRegistry::class.java),
                        )
                    )
                    .defaultAnswer { call ->
                        val loc = call.arguments.firstOrNull() as? LocInfo
                        when {
                            call.method.name == "add" &&
                                loc?.coords == rejectLocAddAt &&
                                loc != null -> false
                            call.method.name == "del" &&
                                loc?.coords == rejectLocDeleteAt &&
                                loc != null -> false
                            else -> call.callRealMethod()
                        }
                    },
            )
        else mock(LocRepository::class.java)
    private val registry = NpcRegistry(npcs, collision, events)
    val repository = NpcRepository(clock, registry, npcs)
    val instanceRegions =
        mock(RegionRepository::class.java) { call ->
            error("Nex fixed public fixture called RegionRepository.${call.method.name}")
        }
    val instances =
        InstanceManager(
            instanceRegions,
            repository,
            players,
            events,
            InstanceAreaResolver(),
            clock,
            collision,
            npcs,
        )
    private val entityLifecycle =
        EntityLifecycleProcess(
            repository,
            locs,
            mock(ObjRepository::class.java),
            mock(ControllerRepository::class.java),
        )
    private val postTick =
        NpcPostTickProcess(
            npcs,
            registry,
            NpcFaceSquareProcessor(),
            GameExceptionHandler { error, message ->
                throw AssertionError(message().toString(), error)
            },
        )
    val worldUpdates = ZoneUpdateMap()
    val world =
        if (nativeWorld) spy(WorldRepository(worldUpdates)) else mock(WorldRepository::class.java)
    val worldQueues = WorldQueueList()
    val selectionBounds = mutableListOf<Int>()
    val selectionRolls = ArrayDeque<Int>()
    val random =
        object : GameRandom {
            override fun of(maxExclusive: Int): Int {
                selectionBounds += maxExclusive
                return if (selectionRolls.isEmpty()) maxExclusive - 1
                else selectionRolls.removeFirst().also { require(it in 0 until maxExclusive) }
            }

            override fun of(minInclusive: Int, maxInclusive: Int) = maxInclusive

            override fun randomDouble() = 0.999
        }
    var accurate = true
    var accuracyRolls = 0
    private val accuracy =
        mock(AccuracyFormulae::class.java) { call ->
            if (call.method.name.startsWith("roll")) {
                accuracyRolls++
                accurate
            } else RETURNS_DEFAULTS.answer(call)
        }
    val combat = NvPCombat(accuracy, mock(MaxHitFormulae::class.java), world)
    val encounters = EncounterRegistry(random)
    val deps =
        BossDeps(
            random,
            accuracy,
            world,
            repository,
            players,
            clock,
            worldQueues,
            collision,
            encounters,
            BossExtensionRegistry(),
            combat,
        )
    val rewardService = mock(NexRewardService::class.java)
    val manager =
        NexEncounterManager(
            clock,
            npcs,
            players,
            locs,
            AiPlayerInteractions(events, players),
            collision,
            random,
            encounters,
            worldQueues,
            world,
            rewardService,
            instances,
        )
    val lifecycle = NexLifecycle(manager)
    val player =
        Player().apply {
            slotId = 1
            uuid = 1L
            observerUUID = 1L
            assignUid()
            coords = NEX_BANK_RETURN
            currentMapClock = clock.cycle
            processedMapClock = clock.cycle
            for (stat in
                listOf("hitpoints", "prayer", "attack", "strength", "defence", "ranged", "magic")) {
                statMap.setBaseLevel("stat.$stat", 99)
                statMap.setCurrentLevel("stat.$stat", 99)
            }
            inv = invMap.getOrPut("inv.inv")
            worn = invMap.getOrPut("inv.worn")
        }
    private val modifier = StandardNpcHitModifier(events)
    val partner =
        if (duo)
            Player().apply {
                slotId = 2
                uuid = 2L
                observerUUID = 2L
                assignUid()
                coords = NEX_BANK_RETURN
                currentMapClock = clock.cycle
                processedMapClock = clock.cycle
                for (stat in
                    listOf(
                        "hitpoints",
                        "prayer",
                        "attack",
                        "strength",
                        "defence",
                        "ranged",
                        "magic",
                    )) {
                    statMap.setBaseLevel("stat.$stat", 99)
                    statMap.setCurrentLevel("stat.$stat", 99)
                }
                inv = invMap.getOrPut("inv.inv")
                worn = invMap.getOrPut("inv.worn")
            }
        else null
    private val launcher =
        StandardNpcAccessLauncher(
            StandardNpcAccessContextFactory(
                random,
                modifier,
                StandardNpcHitProcessor(players, events, emptySet()),
            )
        )
    private val queues = NpcQueueProcessor(events, launcher)
    var beforeTeleport: (Player, CoordGrid, TeleportType) -> Unit = { _, _, _ -> }
    var teleportDenial: String? = null
    val validatedTeleports = mutableListOf<TeleportType>()
    private val teleportValidator =
        PlayerTeleportValidator(
            setOf(
                PlayerTeleportValidateHook { _, type, _ ->
                    validatedTeleports += type
                    teleportDenial
                }
            )
        )
    private val areaChecker = mock(AreaChecker::class.java)
    private val teleportHooks =
        PlayerTeleportHooks(
            setOf(PlayerTeleportHook { target, dest, type -> beforeTeleport(target, dest, type) })
        )
    private val playerContext =
        mock(ProtectedAccessContext::class.java) { call ->
            when (call.method.name) {
                "getRandom" -> random
                "getEventBus" -> events
                "getNpcList" -> npcs
                "getPlayerList" -> players
                "getCollision" -> collision
                "getTeleportHooks" -> teleportHooks
                "getTeleportValidator" -> teleportValidator
                "getAreaChecker" -> areaChecker
                else -> RETURNS_DEFAULTS.answer(call)
            }
        }
    val playerLauncher =
        ProtectedAccessLauncher(mock(ProtectedAccessContextFactory::class.java) { playerContext })
    private val playerQueues = PlayerQueueProcessor(events, playerLauncher)
    val nativeInstanceLifecycle: PlayerPostTickHook

    init {
        players[1] = player
        for (x in 360..368) for (z in 647..653) collision.allocateIfAbsent(x * 8, z * 8, 0)
        val context = ScriptContext(events, commands, EngineQueueCache())
        nativeInstanceLifecycle = installNexNativeInstanceScripts(context, instances, clock)
        with(NpcHitScript()) { context.startup() }
        with(PlayerHitScript()) { context.startup() }
        val death =
            if (nativeDeaths)
                NpcDeath(
                    repository,
                    players,
                    deathObjects ?: mock(ObjRepository::class.java),
                    deathDropHooks,
                    deathKillHooks,
                )
            else mock(NpcDeath::class.java)
        with(NexCombat(manager, death, deps)) { context.startup() }
        with(lifecycle) { context.startup() }
        with(NexWorld(manager, playerLauncher)) { context.startup() }
        tick()
        if (enterRoom) {
            assertTrue(enter(player))
            partner?.let {
                players[2] = it
                assertTrue(enter(it))
            }
            tick(5)
        }
    }

    fun npc(id: Int): Npc = npcs.first { it.type.id == id }

    fun session(): InstanceSession =
        requireNotNull(instances.sessionForId(requireNotNull(instances.instanceForNpc(npc(11278)))))

    fun passBarrier(target: Player = player, coords: CoordGrid = NEX_BARRIER): Boolean {
        val type = requireNotNull(ServerCacheManager.getObject(42967))
        val loc = BoundLocInfo(LocInfo(1, coords, LocEntity(42967, 10, 0)), type)
        val trigger =
            LocInteractions(BoundValidator(collision), events)
                .opTrigger(target, loc, InteractionOp.Op1) ?: return false
        return playerLauncher.launch(target) { check(events.publish(this, trigger)) }
    }

    /** Dispatch the real registered location handler, retaining the selected native loc value. */
    fun clickIce(loc: LocInfo, target: Player = player): Boolean {
        val type = requireNotNull(ServerCacheManager.getObject(loc.id))
        val bound = BoundLocInfo(loc, type)
        val trigger =
            LocInteractions(BoundValidator(collision), events)
                .opTrigger(target, bound, InteractionOp.Op1) ?: return false
        return playerLauncher.launch(target) { check(events.publish(this, trigger)) }
    }

    /** Recast fault setup through the registered production handler, not a cadence assertion. */
    fun registeredSpecial(target: Player = player) {
        val boss = npc(11278)
        launcher.launch(boss) {
            deps.extensionRegistry.invoke(NEX_SPECIAL, this, boss, target, null)
        }
    }

    fun processLocDurations() {
        check(nativeLocs)
        entityLifecycle.process()
    }

    fun travelBank(target: Player = player): Boolean {
        var travelled = false
        playerLauncher.launch(target) { travelled = manager.teleportBank(this) }
        return travelled
    }

    fun enter(target: Player = player): Boolean {
        var entered = false
        playerLauncher.launch(target) { entered = manager.enter(this) }
        return entered
    }

    fun exit(target: Player = player): Boolean {
        var exited = false
        playerLauncher.launch(target) { exited = manager.exit(this) }
        return exited
    }

    fun altarExit(target: Player = player): Boolean {
        return altar(target, InteractionOp.Op2)
    }

    fun altarPray(target: Player = player): Boolean = altar(target, InteractionOp.Op1)

    private fun altar(target: Player, op: InteractionOp): Boolean {
        val type = requireNotNull(ServerCacheManager.getObject(42965))
        val loc = BoundLocInfo(LocInfo(1, NEX_ALTAR, LocEntity(42965, 10, 0)), type)
        val trigger =
            requireNotNull(
                LocInteractions(BoundValidator(collision), events).opTrigger(target, loc, op)
            )
        return playerLauncher.launch(target) { check(events.publish(this, trigger)) }
    }

    fun protectMagic(enabled: Boolean) =
        VarPlayerIntMapSetter.set(player, "varbit.prayer_protectfrommagic", if (enabled) 1 else 0)

    fun attack(npc: Npc, target: Player = player) {
        npc.mode = NpcMode.ApPlayer2
        launcher.launch(npc) { events.publish(this, AiPlayerEvents.Ap2(target, npc)) }
    }

    fun damage(npc: Npc, amount: Int, type: HitType = HitType.Magic) =
        npc.queueHit(player, 1, type, amount, modifier)

    fun reachGuard(id: Int) {
        val thresholds = listOf(2720, 2040, 1360, 680)
        for (guardId in 11283..id) {
            val boss = npc(11278)
            boss.hitpoints = thresholds[guardId - 11283] + 1
            boss.queueHit(player, 1, HitType.Magic, 20, modifier)
            tick()
            if (guardId != id) {
                // Setup-only phase handoff through the production hook, not a death timing proof.
                manager.handleDeath(npc(guardId))
                tick(2)
            }
        }
    }

    fun summonReaver(): Npc {
        reachGuard(11284)
        manager.handleDeath(npc(11284))
        tick(2)
        repeat(6) {
            attack(npc(11278))
            tick(4)
        }
        player.queueList.clear()
        return npc(11294)
    }

    /** Setup through existing phase hooks; this is not a bodyguard death-timing assertion. */
    fun reachPhase(phase: NexState) {
        val guard =
            when (phase) {
                NexState.SMOKE -> return
                NexState.SHADOW -> 11283
                NexState.BLOOD -> 11284
                NexState.ICE -> 11285
                NexState.ZAROS -> 11286
                else -> error("Not an elemental/final phase: $phase")
            }
        reachGuard(guard)
        manager.handleDeath(npc(guard))
        tick(2)
    }

    fun tick(count: Int = 1) =
        repeat(count) {
            // Finish the preceding frame, including sequence priority/reset and arrival actions.
            postTick.process()
            if (nativeLocs) entityLifecycle.process()
            WorldQueueListProcess(worldQueues).process()
            clock.tick()
            events.publish(GameLifecycle.LateCycle)
            players.forEach {
                it.currentMapClock = clock.cycle
                it.processedMapClock = clock.cycle
                if (it.isNotDelayed) it.advanceActiveCoroutine()
            }
            npcs.toList().forEach {
                it.currentMapClock = clock.cycle
                it.processedMapClock = clock.cycle
                it.advanceActiveCoroutine()
                if (it.isSlotAssigned) queues.process(it)
            }
            players.forEach {
                nativeInstanceLifecycle.onPostTick(it)
                lifecycle.onPostTick(it)
            }
            events.publish(GameLifecycle.EndCycle)
        }

    fun projectiles(): List<ProjAnim> =
        mockingDetails(world)
            .invocations
            .filter { it.method.name == "projAnim" }
            .map { it.arguments[0] as ProjAnim }

    /** Exercise the real player queue and impact processor without the room post-tick cleanup. */
    fun processPlayerHits(count: Int = 12) =
        repeat(count) {
            clock.tick()
            for (target in listOfNotNull(player, partner)) {
                target.currentMapClock = clock.cycle
                target.processedMapClock = clock.cycle
                playerQueues.process(target)
            }
        }

    fun hits(target: Player = player): List<Pair<Int, Hit>> = queued(target)

    fun deferredHits(target: Player = player): List<Pair<Int, DeferredPlayerHit>> = queued(target)

    private inline fun <reified T : Any> queued(target: Player): List<Pair<Int, T>> {
        val iterator = target.queueList.iterator() ?: return emptyList()
        return try {
            buildList {
                while (iterator.hasNext()) {
                    val queue = iterator.next()
                    (queue.args as? T)?.let { add(queue.remainingCycles to it) }
                }
            }
        } finally {
            iterator.cleanUp()
        }
    }
}

/** Uses the same test-only JVM constructor route as the Royal Titans native instance fixture. */
internal fun installNexNativeInstanceScripts(
    context: ScriptContext,
    instances: InstanceManager,
    clock: MapClock,
): PlayerPostTickHook {
    val lifecycle =
        Class.forName("org.rsmod.api.instances.hook.InstanceLifecycleScript")
            .getConstructor(InstanceManager::class.java, MapClock::class.java)
            .newInstance(instances, clock) as PluginScript
    with(lifecycle) { context.startup() }
    val ticks =
        Class.forName("org.rsmod.api.instances.hook.InstanceTickScript")
            .getConstructor(MapClock::class.java, InstanceManager::class.java)
            .newInstance(clock, instances) as PluginScript
    with(ticks) { context.startup() }
    return lifecycle as PlayerPostTickHook
}
