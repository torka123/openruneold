package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import dev.openrune.types.NpcMode
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
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
import org.rsmod.api.game.process.npc.NpcFaceSquareProcessor
import org.rsmod.api.game.process.npc.NpcPostTickProcess
import org.rsmod.api.game.process.npc.NpcQueueProcessor
import org.rsmod.api.game.process.player.PlayerQueueProcessor
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.npc.access.StandardNpcAccessContextFactory
import org.rsmod.api.npc.access.StandardNpcAccessLauncher
import org.rsmod.api.npc.events.interact.AiPlayerEvents
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.hit.DeferredPlayerHit
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.utils.logging.GameExceptionHandler
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
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Production registration/native combat characterization, NOT OSRS certification of the draft's
 * generic minion damage, animation or prayer constants. Player hits are inspected on the native
 * queue; this suite does not claim player impact processing or complete bodyguard-death parity.
 */
@OptIn(InternalApi::class)
class NexMinionCombatCharacterizationTest {
    @Test
    fun `each called guard retains native launch prayer damage projectile and five tick gate`() {
        for (guardId in 11283..11286) for (protected in listOf(false, true)) {
            val f = NexMinionTestWorld()
            f.reachGuard(guardId)
            val guard = f.npc(guardId)
            f.protectMagic(protected)
            val start = f.clock.cycle
            f.attack(guard)
            val (delay, hit) = f.hits().single()
            val projectile = f.projectiles().last()
            assertEquals(if (protected) 10 else 20, hit.damage)
            assertEquals(HitType.Magic, hit.type)
            assertEquals(811, guard.pendingSequence.id)
            assertEquals(1999, projectile.spotanim)
            assertEquals(projectile.serverCycles, delay)
            assertEquals(start + 5, guard.actionDelay)
            val rolls = f.accuracyRolls
            f.attack(guard)
            f.tick(4)
            f.attack(guard)
            assertEquals(1, f.hits().size)
            assertEquals(rolls, f.accuracyRolls, "Cooldown rejection must not roll accuracy")
            f.tick()
            f.attack(guard)
            assertEquals(2, f.hits().size)
            assertEquals(start + 10, guard.actionDelay)
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `inactive guard outsider and inaccurate native magic do not inflict damage`() {
        val f = NexMinionTestWorld()
        val fumus = f.npc(11283)
        f.attack(fumus)
        assertTrue(f.hits().isEmpty())
        assertEquals(0, f.accuracyRolls)
        f.reachGuard(11283)
        f.accurate = false
        f.attack(fumus)
        assertEquals(0, f.hits().single().second.damage)
        assertEquals(1, f.accuracyRolls)
        f.tick(5)
        f.player.coords = NEX_BANK_TELEPORT
        f.attack(fumus)
        assertEquals(1, f.hits().size)
        assertEquals(1, f.accuracyRolls)
    }

    @Test
    fun `siphon reaver uses native magic with retained eighteen maximum and five tick rate`() {
        for (protected in listOf(false, true)) {
            val f = NexMinionTestWorld()
            val reaver = f.summonReaver()
            f.protectMagic(protected)
            val start = f.clock.cycle
            val beforeProjectiles = f.projectiles().size
            f.attack(reaver)
            val hit = f.hits().single().second
            assertEquals(if (protected) 9 else 18, hit.damage)
            assertEquals(HitType.Magic, hit.type)
            assertEquals(811, reaver.pendingSequence.id)
            assertEquals(start + 5, reaver.actionDelay)
            assertEquals(beforeProjectiles, f.projectiles().size)
            f.attack(reaver)
            assertEquals(1, f.hits().size)
            framework().clearInlineMocks()
        }
    }

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}

@OptIn(InternalApi::class)
internal class NexMinionTestWorld(duo: Boolean = false) {
    val clock = MapClock().apply { cycle = 100 }
    val events = EventBus()
    val players = PlayerList()
    val npcs = NpcList()
    val collision = CollisionFlagMap()
    private val registry = NpcRegistry(npcs, collision, events)
    val repository = NpcRepository(clock, registry, npcs)
    private val postTick =
        NpcPostTickProcess(
            npcs,
            registry,
            NpcFaceSquareProcessor(),
            GameExceptionHandler { error, message ->
                throw AssertionError(message().toString(), error)
            },
        )
    val world = mock(WorldRepository::class.java)
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
    val manager =
        NexEncounterManager(
            clock,
            repository,
            players,
            mock(LocRepository::class.java),
            AiPlayerInteractions(events, players),
            collision,
            random,
            encounters,
            worldQueues,
            world,
            mock(NexRewardService::class.java),
        )
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
    private val playerContext =
        mock(ProtectedAccessContext::class.java) { call ->
            when (call.method.name) {
                "getRandom" -> random
                "getEventBus" -> events
                "getNpcList" -> npcs
                "getPlayerList" -> players
                "getCollision" -> collision
                else -> RETURNS_DEFAULTS.answer(call)
            }
        }
    private val playerQueues =
        PlayerQueueProcessor(
            events,
            ProtectedAccessLauncher(
                mock(ProtectedAccessContextFactory::class.java) { playerContext }
            ),
        )

    init {
        players[1] = player
        for (x in 360..368) for (z in 647..653) collision.allocateIfAbsent(x * 8, z * 8, 0)
        val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        with(NpcHitScript()) { context.startup() }
        with(PlayerHitScript()) { context.startup() }
        with(NexCombat(manager, mock(NpcDeath::class.java), players, deps)) { context.startup() }
        manager.startup()
        tick()
        assertTrue(manager.enter(player))
        partner?.let {
            players[2] = it
            assertTrue(manager.enter(it))
        }
        tick(5)
    }

    fun npc(id: Int): Npc = npcs.first { it.type.id == id }

    fun protectMagic(enabled: Boolean) =
        VarPlayerIntMapSetter.set(player, "varbit.prayer_protectfrommagic", if (enabled) 1 else 0)

    fun attack(npc: Npc, target: Player = player) {
        npc.mode = NpcMode.ApPlayer2
        launcher.launch(npc) { events.publish(this, AiPlayerEvents.Ap2(target, npc)) }
    }

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
            WorldQueueListProcess(worldQueues).process()
            clock.tick()
            player.currentMapClock = clock.cycle
            player.processedMapClock = clock.cycle
            npcs.toList().forEach {
                it.currentMapClock = clock.cycle
                it.processedMapClock = clock.cycle
                queues.process(it)
            }
            manager.tick(player)
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
