package org.rsmod.content.bosses.doom

import dev.openrune.ServerCacheManager
import dev.openrune.types.NpcMode
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Answers
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.runtime.*
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.game.process.player.PlayerQueueProcessor
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.events.interact.AiPlayerEvents
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.hit.DeferredPlayerHit
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RouteFactory
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Actual Doom registration, native AI event, interpreter and launch; spatial repos record output.
 */
@OptIn(InternalApi::class)
class DoomNativeDslTest {
    @Test
    fun `all standard styles and delve tiers retain native launch delay damage and seeded commit`() {
        for (delve in 1..9) for (style in DoomAttackStyle.entries) {
            if (delve == 1 && style == DoomAttackStyle.MELEE) continue
            Fixture(delve, standardSeed(delve, style)).use { f ->
                val before = f.run.attackFlowState
                val expected = DoomAttackFlow.next(delve, before, adjacent = false)
                val stateDuringRoll = mutableListOf<DoomAttackFlowState>()
                f.random.beforeRoll = { stateDuringRoll += f.run.attackFlowState }
                f.attack()

                assertEquals(expected.nextState, f.run.attackFlowState, "delve=$delve style=$style")
                assertTrue(stateDuringRoll.isNotEmpty())
                assertTrue(
                    stateDuringRoll.all { it == before },
                    "Commit must follow native damage RNG",
                )
                assertEquals(100 + DoomRules.attackRate(delve), f.npc.actionDelay)
                assertEquals(listOf(100), f.registry.of(f.npc).recentAbilityCommitTicks())
                val (delay, hit) = f.hits().single()
                assertEquals(DoomRules.standardProjectileTravelTicks(delve), delay)
                assertEquals(DoomRules.standardMaxHit(delve), hit.damage)
                assertEquals(
                    when (style) {
                        DoomAttackStyle.MELEE -> HitType.Melee
                        DoomAttackStyle.RANGED -> HitType.Ranged
                        DoomAttackStyle.MAGIC -> HitType.Magic
                    },
                    hit.type,
                )
                assertEquals(1, f.projectiles().size)
                assertNull(f.run.preparedAttack)
                f.attack()
                assertEquals(1, f.hits().size, "Same-cycle AI cannot launch twice")
            }
        }
    }

    @Test
    fun `tongue substitution and next distant attack use their own native rates without consuming seed`() {
        for (delve in listOf(1, 4, 8, 9)) {
            Fixture(delve, standardSeed(delve, DoomAttackStyle.RANGED)).use { f ->
                val before = f.run.attackFlowState
                f.player.coords = f.npc.coords.translateX(5)
                f.attack()
                assertEquals(before, f.run.attackFlowState)
                assertEquals(100 + DoomRules.tongueAttackRate(delve), f.npc.actionDelay)
                assertEquals(HitType.Melee, f.hits().single().second.type)
                assertTrue(f.projectiles().isEmpty())
                f.at(f.npc.actionDelay - 1)
                f.attack()
                assertEquals(1, f.registry.of(f.npc).recentAbilityCommitTicks().size)
                f.at(f.npc.actionDelay)
                f.player.coords = f.npc.coords.translateX(8)
                val secondTick = f.clock.cycle
                f.attack()
                assertEquals(
                    listOf(100, secondTick),
                    f.registry.of(f.npc).recentAbilityCommitTicks(),
                )
                assertEquals(secondTick + DoomRules.attackRate(delve), f.npc.actionDelay)
                assertEquals(
                    DoomAttackFlow.next(delve, before, adjacent = false).nextState,
                    f.run.attackFlowState,
                )
                assertEquals(1, f.projectiles().size)
            }
        }
    }

    @Test
    fun `native and encounter rejection cannot consume streams or commit abilities`() {
        val f = Fixture(4, standardSeed(4, DoomAttackStyle.RANGED))
        val before = f.run.attackFlowState
        f.npc.actionDelay = 101
        f.attack()
        f.npc.actionDelay = 0
        f.run.state = DoomState.MELEE_CHARGE
        f.attack()
        f.run.state = DoomState.ACTIVE
        f.run.rockVolleysRemaining = 1
        f.attack()
        f.run.rockVolleysRemaining = 0
        f.player.pendingLogout = true
        f.attack()
        f.player.pendingLogout = false
        f.npc.mode = NpcMode.None
        f.attack()
        assertEquals(before, f.run.attackFlowState)
        assertNull(f.registry.existing(f.npc))
        assertTrue(f.hits().isEmpty())
        assertTrue(f.projectiles().isEmpty())
        f.npc.mode = NpcMode.ApPlayer2
        f.run.nextAttackNotBeforeCycle = 105
        f.attack()
        assertEquals(105, f.npc.actionDelay)
        assertNull(f.registry.existing(f.npc))
        f.at(105)
        f.attack()
        assertEquals(1, f.registry.of(f.npc).recentAbilityCommitTicks().size)
    }

    @Test
    fun `forced and seeded rock abilities keep their spatial handler and commit rules`() {
        for (delve in listOf(1, 4, 8)) {
            Fixture(delve, standardSeed(delve, DoomAttackStyle.RANGED)).use { forced ->
                val before = forced.run.attackFlowState
                forced.run.shockwavePreparing = true
                forced.run.forceRockBeforeShockwave = true
                forced.run.preShockwaveAttacksRemaining = 2
                forced.player.coords = forced.npc.coords.translateX(5)
                forced.attack()
                assertEquals(before, forced.run.attackFlowState)
                assertEquals(1, forced.run.fallingRocks.size)
                assertEquals(DoomRules.rockSets(delve) - 1, forced.run.rockVolleysRemaining)
                assertEquals(1, forced.run.preShockwaveAttacksRemaining)
                assertFalse(forced.run.forceRockBeforeShockwave)
                assertEquals(100 + DoomRules.attackRate(delve), forced.npc.actionDelay)
                assertTrue(forced.hits().isEmpty())
            }

            for (charge in listOf(false, true)) {
                val seed =
                    (1L..10000L).first {
                        val turn =
                            DoomAttackFlow.next(delve, DoomAttackFlow.initial(delve, it), false)
                        turn.primary == DoomPrimaryAttack.ROCKS && turn.startMeleeCharge == charge
                    }
                Fixture(delve, seed).use { free ->
                    val expected = DoomAttackFlow.next(delve, free.run.attackFlowState, false)
                    free.attack()
                    assertEquals(expected.nextState, free.run.attackFlowState)
                    assertEquals(
                        if (charge) 102 else Int.MAX_VALUE,
                        free.run.pendingMeleeChargeCycle,
                    )
                    assertEquals(1, free.run.fallingRocks.size)
                }
            }
        }
    }

    @Test
    fun `standard deferred hit applies protection prayer at actual native impact`() {
        for (style in DoomAttackStyle.entries) {
            val prayer =
                when (style) {
                    DoomAttackStyle.MELEE -> "varbit.prayer_protectfrommelee"
                    DoomAttackStyle.RANGED -> "varbit.prayer_protectfrommissiles"
                    DoomAttackStyle.MAGIC -> "varbit.prayer_protectfrommagic"
                }
            Fixture(8, standardSeed(8, style)).use { protected ->
                protected.attack()
                val deferred = protected.deferredHits().single().second
                assertSame(StandardPlayerHitModifier, deferred.modifier)
                assertEquals(65, deferred.builder.damage)
                VarPlayerIntMapSetter.set(protected.player, prayer, 1)
                protected.processPlayerHits(3)
                assertEquals(99, protected.player.hitpoints)
                assertTrue(protected.deferredHits().isEmpty())
            }

            Fixture(8, standardSeed(8, style)).use { exposed ->
                VarPlayerIntMapSetter.set(exposed.player, prayer, 1)
                exposed.attack()
                assertEquals(65, exposed.deferredHits().single().second.builder.damage)
                VarPlayerIntMapSetter.set(exposed.player, prayer, 0)
                exposed.processPlayerHits(3)
                assertEquals(34, exposed.player.hitpoints)
                assertTrue(exposed.deferredHits().isEmpty())
            }
        }
    }

    @Test
    fun `native launched standard survives retained source death but retires on deletion and departure`() {
        Fixture(8, standardSeed(8, DoomAttackStyle.MAGIC)).use { f ->
            f.attack()
            val hit = f.hits().single().second
            assertTrue(hit.isValidNow())
            f.npc.hitpoints = 0
            assertTrue(
                hit.isValidNow(),
                "Ordinary fired hits retain the native source-death policy",
            )
            f.npc.hitpoints = 600
            f.events.publish(NpcStateEvents.Delete(f.npc))
            assertFalse(hit.isValidNow())
            assertNull(f.registry.existing(f.npc))
            f.processPlayerHits(3)
            assertEquals(99, f.player.hitpoints)
        }

        Fixture(8, standardSeed(8, DoomAttackStyle.MAGIC)).use { dead ->
            dead.attack()
            dead.npc.hitpoints = 0
            dead.processPlayerHits(3)
            assertEquals(34, dead.player.hitpoints)
        }

        Fixture(8, standardSeed(8, DoomAttackStyle.MAGIC)).use { departed ->
            departed.attack()
            val pending = departed.hits().single().second
            departed.player.coords = departed.player.coords.translateZ(100)
            assertFalse(pending.isValidNow())
            departed.processPlayerHits(3)
            assertEquals(99, departed.player.hitpoints)
        }
    }

    private class Fixture(val delve: Int, seed: Long) : AutoCloseable {
        val clock = MapClock().apply { cycle = 100 }
        val random = MaximumRandom()
        val collision = CollisionFlagMap()
        val events = EventBus()
        val player =
            Player().apply {
                slotId = 1
                uuid = 1L
                assignUid()
                coords = CoordGrid(1308, 9510)
                statMap.setBaseLevel("stat.hitpoints", 99)
                statMap.setCurrentLevel("stat.hitpoints", 99)
                statMap.setCurrentLevel("stat.prayer", 99)
                inv = invMap.getOrPut("inv.inv")
                worn = invMap.getOrPut("inv.worn")
            }
        val players = PlayerList().apply { this[1] = player }
        val npc =
            Npc(assertNotNull(ServerCacheManager.getNpc(DoomAssets.BOSS)), CoordGrid(1300, 9510))
                .apply {
                    slotId = 1
                    assignUid()
                    currentMapClock = 100
                    processedMapClock = 100
                    hitpoints = 600
                    mode = NpcMode.ApPlayer2
                    spawnOwner = player.uid
                }
        val registry = EncounterRegistry(random)
        val npcList = NpcList().apply { this[1] = npc }
        val world = mock(WorldRepository::class.java)
        val npcs = mock(NpcRepository::class.java)
        val accuracy =
            mock(AccuracyFormulae::class.java) { call ->
                if (call.method.name.startsWith("roll")) true
                else Answers.RETURNS_DEFAULTS.answer(call)
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
                registry,
                BossExtensionRegistry(),
                NvPCombat(accuracy, mock(MaxHitFormulae::class.java), world),
            )
        val manager =
            DoomEncounterManager(
                clock,
                mock(InstanceManager::class.java),
                npcs,
                mock(LocRepository::class.java),
                mock(AiPlayerInteractions::class.java),
                collision,
                random,
                registry,
                accuracy,
                mock(WornBonuses::class.java),
                world,
                mock(BossHpBarScript::class.java),
                mock(DoomRewardService::class.java),
                mock(DoomScoreboardService::class.java),
                mock(RouteFactory::class.java),
            )
        val run =
            DoomEncounterManager.Run(
                player = player,
                session = mock(InstanceSession::class.java),
                generation = seed,
                delve = delve,
                boss = npc,
                state = DoomState.ACTIVE,
                stateCycle = 100,
                startCycle = 100,
                bossRevealCycle = 100,
                startTile = player.coords,
                startingPrayerMask = 0,
                runId = "native-dsl-test",
                chainStartCycle = 100,
                nextShockwaveCycle = Int.MAX_VALUE,
            )
        private val playerContext =
            mock(ProtectedAccessContext::class.java) { call ->
                when (call.method.name) {
                    "getRandom" -> random
                    "getEventBus" -> events
                    "getNpcList" -> npcList
                    "getPlayerList" -> players
                    "getCollision" -> collision
                    else -> Answers.RETURNS_DEFAULTS.answer(call)
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
            @Suppress("UNCHECKED_CAST")
            val runs =
                manager.javaClass.getDeclaredField("runs").let {
                    it.isAccessible = true
                    it.get(manager) as MutableMap<PlayerUid, DoomEncounterManager.Run>
                }
            runs[player.uid] = run
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(PlayerHitScript()) { scripts.startup() }
            with(DoomCombat(manager, mock(NpcDeath::class.java), players, npcList, deps)) {
                scripts.startup()
            }
        }

        fun attack() {
            npc.launch {
                val access =
                    StandardNpcAccess(
                        npc,
                        this,
                        StandardNpcAccessContext(
                            { random },
                            { StandardNpcHitModifier(events) },
                            { StandardNpcHitProcessor(players, events, emptySet()) },
                        ),
                    )
                events.publish(access, AiPlayerEvents.Ap2(player, npc))
            }
        }

        fun at(cycle: Int) {
            clock.cycle = cycle
            npc.currentMapClock = cycle
            npc.processedMapClock = cycle
        }

        fun projectiles(): List<ProjAnim> =
            mockingDetails(world)
                .invocations
                .filter { it.method.name == "projAnim" }
                .map { it.arguments[0] as ProjAnim }

        fun processPlayerHits(count: Int) =
            repeat(count) {
                at(clock.cycle + 1)
                player.currentMapClock = clock.cycle
                player.processedMapClock = clock.cycle
                playerQueues.process(player)
            }

        // Standard launches carry DeferredPlayerHit; observing their builder must not apply the
        // modifier early. PlayerQueueProcessor/PlayerHitScript above owns actual impact.
        fun hits(): List<Pair<Int, Hit>> =
            queued().mapNotNull { (delay, payload) ->
                when (payload) {
                    is Hit -> delay to payload
                    is DeferredPlayerHit -> delay to payload.builder.build()
                    else -> null
                }
            }

        fun deferredHits(): List<Pair<Int, DeferredPlayerHit>> =
            queued().mapNotNull { (delay, payload) ->
                (payload as? DeferredPlayerHit)?.let { delay to it }
            }

        private fun queued(): List<Pair<Int, Any?>> {
            val iterator = player.queueList.iterator() ?: return emptyList()
            return try {
                buildList {
                    while (iterator.hasNext()) {
                        val queue = iterator.next()
                        add(queue.remainingCycles to queue.args)
                    }
                }
            } finally {
                iterator.cleanUp()
            }
        }

        override fun close() {
            registry.remove(npc)
            // Inline mock answers retain this fixture and its large native collision map. Release
            // after each matrix case instead of accumulating every case until @AfterEach.
            framework().clearInlineMocks()
        }
    }

    private class MaximumRandom : GameRandom {
        var beforeRoll: () -> Unit = {}

        override fun of(maxExclusive: Int): Int = (maxExclusive - 1).also { beforeRoll() }

        override fun of(minInclusive: Int, maxInclusive: Int): Int =
            maxInclusive.also { beforeRoll() }

        override fun randomDouble(): Double = 0.99.also { beforeRoll() }
    }

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        private fun standardSeed(delve: Int, style: DoomAttackStyle): Long =
            (1L..10000L).first { seed ->
                val turn =
                    DoomAttackFlow.next(
                        delve,
                        DoomAttackFlow.initial(delve, seed),
                        adjacent = false,
                    )
                turn.primary == DoomPrimaryAttack.STANDARD && turn.standardStyle == style
            }

        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}
