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
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.game.process.player.PlayerQueueProcessor
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceAttributes
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.events.InstancePlayerLeaveEvent
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.events.interact.AiPlayerEvents
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.events.PlayerMovementEvent
import org.rsmod.api.player.hit.DeferredPlayerHit
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.specials.energy.SpecialAttackEnergyRegeneration
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
                val before = f.oracleState
                val expected = DoomAttackFlow.next(delve, before, adjacent = false)
                val countsBefore = f.decisionCounts()
                val stateDuringRoll = mutableListOf<List<Long>>()
                f.random.beforeRoll = { stateDuringRoll += f.decisionCounts() }
                f.attack()

                f.assertTurn(expected)
                assertTrue(stateDuringRoll.isNotEmpty())
                assertTrue(
                    stateDuringRoll.all { it == countsBefore },
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
                assertNull(f.run.launchedPrimary)
                f.attack()
                assertEquals(1, f.hits().size, "Same-cycle AI cannot launch twice")
            }
        }
    }

    @Test
    fun `tongue substitution and next distant attack use their own native rates without consuming seed`() {
        for (delve in listOf(1, 4, 8, 9)) {
            Fixture(delve, standardSeed(delve, DoomAttackStyle.RANGED)).use { f ->
                val before = f.oracleState
                f.player.coords = f.npc.coords.translateX(5)
                f.attack()
                f.assertTurn(DoomAttackFlow.next(delve, before, adjacent = true))
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
                f.assertTurn(DoomAttackFlow.next(delve, before, adjacent = false))
                assertEquals(1, f.projectiles().size)
            }
        }
    }

    @Test
    fun `native and encounter rejection cannot consume streams or commit abilities`() {
        val f = Fixture(4, standardSeed(4, DoomAttackStyle.RANGED))
        val before = f.decisionCounts()
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
        assertEquals(before, f.decisionCounts())
        assertTrue(f.registry.of(f.npc).recentAbilityCommitTicks().isEmpty())
        assertTrue(f.hits().isEmpty())
        assertTrue(f.projectiles().isEmpty())
        f.npc.mode = NpcMode.ApPlayer2
        f.run.nextAttackNotBeforeCycle = 105
        f.attack()
        assertEquals(105, f.npc.actionDelay)
        assertTrue(f.registry.of(f.npc).recentAbilityCommitTicks().isEmpty())
        f.at(105)
        f.attack()
        assertEquals(1, f.registry.of(f.npc).recentAbilityCommitTicks().size)
    }

    @Test
    fun `forced and seeded rock abilities keep their spatial handler and commit rules`() {
        for (delve in listOf(1, 4, 8)) {
            Fixture(delve, standardSeed(delve, DoomAttackStyle.RANGED)).use { forced ->
                val before = forced.oracleState
                forced.run.shockwavePreparing = true
                forced.run.forceRockBeforeShockwave = true
                forced.run.preShockwaveAttacksRemaining = 2
                forced.player.coords = forced.npc.coords.translateX(5)
                forced.attack()
                forced.assertTurn(
                    DoomAttackFlow.next(delve, before, adjacent = true, forceRock = true)
                )
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
                    val expected = DoomAttackFlow.next(delve, free.oracleState, false)
                    free.attack()
                    free.assertTurn(expected)
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

    @Test
    fun `native logout retires owned state and delayed hits before deferred region reclaim`() {
        Fixture(8, standardSeed(8, DoomAttackStyle.MAGIC)).use { f ->
            assertTrue(assertNotNull(f.instanceSpecs.get(DOOM_INSTANCE_KEY)).destroyWhenEmpty)
            f.attack()
            val hit = f.hits().single().second
            f.run.launchedPrimary = DoomPrimaryAttack.STANDARD
            f.player.lootDropDuration = 100
            SpecialAttackEnergyRegeneration.pause(f.player)
            f.events.publish(SessionStateEvent.Logout(f.player))

            assertTrue(f.run.retired)
            assertEquals(DoomState.INVALIDATED, f.run.state)
            assertNull(f.run.launchedPrimary)
            assertFalse(f.manager.isActive(f.player))
            assertFalse(f.npc.isSlotAssigned)
            assertFalse(hit.isValidNow())
            assertNull(f.player.lootDropDuration)
            assertFalse(SpecialAttackEnergyRegeneration.isPaused(f.player))
            assertEquals(DOOM_LOBBY, f.player.coords)
            assertEquals(DOOM_LOBBY.packed, f.player.attr[InstanceAttributes.LOGIN_EXIT_COORD])
            assertSame(f.session, f.instances.sessionForId(f.session.id))
            f.instances.tickReclaim(101)
            assertTrue(f.session.isEnded)
            assertNull(f.instances.sessionForId(f.session.id))
            verify(f.hpBar, times(1)).onClose(f.player, f.npc, instant = true)
            verify(f.rewards, never()).clear(f.player)
            verify(f.statistics, never()).recordDeath(f.player, "native-dsl-test:death")
            f.processPlayerHits(3)
            assertEquals(99, f.player.hitpoints)
        }
    }

    @Test
    fun `native external teleport and session termination clean state exactly once`() {
        for (terminate in listOf(false, true)) {
            Fixture(8, standardSeed(8, DoomAttackStyle.RANGED)).use { f ->
                f.attack()
                val pending = f.hits().single().second
                if (terminate) assertTrue(f.instances.terminate(f.session))
                else {
                    val before = f.player.coords
                    f.player.coords = DOOM_LOBBY
                    f.events.publish(PlayerMovementEvent.CoordsMovedEvent(f.player, before))
                }
                assertTrue(f.session.isEnded)
                assertTrue(f.run.retired)
                assertFalse(pending.isValidNow())
                assertFalse(f.manager.isActive(f.player))
                assertTrue(f.instances.npcsForInstance(f.session.id).isEmpty())
                f.events.publish(
                    InstancePlayerLeaveEvent(f.player, DOOM_INSTANCE_KEY, f.session.id, 1L)
                )
                assertFalse(f.instances.terminate(f.session))
                verify(f.hpBar, times(1)).onClose(f.player, f.npc, instant = true)
                verify(f.rewards, never()).clear(f.player)
            }
        }
    }

    @Test
    fun `death observation forfeits once whether native teleport detaches before cleanup or afterward`() {
        for (teleportFirst in listOf(false, true)) {
            Fixture(4, standardSeed(4, DoomAttackStyle.RANGED)).use { f ->
                doReturn(listOf(DoomReward(995, 100))).`when`(f.rewards).pending(f.player)
                f.player.statMap.setCurrentLevel("stat.hitpoints", 0)
                val cleanup = DoomDeathCleanup(f.manager)
                assertEquals(1, cleanup.priority)
                if (teleportFirst) {
                    val before = f.player.coords
                    f.player.coords = CoordGrid(3200, 3200)
                    f.events.publish(PlayerMovementEvent.CoordsMovedEvent(f.player, before))
                    cleanup.cleanup(f.player)
                } else {
                    f.player.coords = CoordGrid(3200, 3200)
                    cleanup.cleanup(f.player)
                    assertTrue(
                        f.manager.isActive(f.player),
                        "Native instance death hook owns departure",
                    )
                    val nativeDeath =
                        Class.forName("org.rsmod.api.instances.hook.InstanceDeathCleanupHook")
                            .getDeclaredConstructor(
                                InstanceManager::class.java,
                                MapClock::class.java,
                            )
                            .newInstance(f.instances, f.clock) as PlayerDeathCleanupHook
                    nativeDeath.cleanup(f.player)
                }
                cleanup.cleanup(f.player)
                f.events.publish(
                    InstancePlayerLeaveEvent(f.player, DOOM_INSTANCE_KEY, f.session.id, 1L)
                )
                assertTrue(f.run.deathRecorded)
                assertEquals(DOOM_LOBBY, f.player.coords)
                assertTrue(f.run.retired)
                assertTrue(f.session.isEnded)
                verify(f.rewards, times(1)).clear(f.player)
                verify(f.statistics, times(1)).recordDeath(f.player, "native-dsl-test:death")
            }
        }
    }

    @Test
    fun `session ownership rejects replacement player identity and stale old delve callbacks`() {
        Fixture(4, standardSeed(4, DoomAttackStyle.RANGED)).use { f ->
            val replacement =
                Player().apply {
                    slotId = f.player.slotId
                    uuid = f.player.uuid
                    assignUid()
                }
            assertSame(f.session, f.instances.sessionForPlayer(replacement))
            assertFalse(f.manager.isActive(replacement))
            assertFalse(f.manager.isOwnedBy(replacement, f.npc))
            f.events.publish(
                InstancePlayerLeaveEvent(replacement, DOOM_INSTANCE_KEY, f.session.id, 1L)
            )
            assertFalse(f.run.retired)
            assertTrue(f.manager.leave(f.player))
            assertTrue(f.manager.enter(f.player))
            val current = assertNotNull(f.instances.sessionForPlayer(f.player))
            assertNotEquals(f.session.id, current.id)
            val newBoss = f.instances.npcsForInstance(current.id).single()
            f.events.publish(
                InstancePlayerLeaveEvent(f.player, DOOM_INSTANCE_KEY, f.session.id, 1L)
            )
            f.manager.finishNpcDeath(f.run)
            f.manager.finishAuxiliaryDeath(newBoss, f.run)
            assertTrue(f.manager.isActive(f.player))
            assertTrue(newBoss.isSlotAssigned)
            verify(f.rewards, never())
                .accumulate(f.player, f.delve, "native-dsl-test", "native-dsl-test:${f.delve}")
        }
    }

    @Test
    fun `retained boss death token completes once after native NPC identity detachment`() {
        Fixture(4, standardSeed(4, DoomAttackStyle.RANGED)).use { f ->
            f.npc.hitpoints = 0
            val token = assertNotNull(f.manager.beginNpcDeath(f.npc))
            f.npcs.del(f.npc, Int.MAX_VALUE)
            assertNull(f.instances.instanceForNpc(f.npc))
            f.manager.finishNpcDeath(token)
            assertEquals(DoomState.DEFEATED, token.state)
            assertNotNull(token.hole)
            f.manager.finishNpcDeath(token)
            verify(f.rewards, times(1))
                .accumulate(f.player, 4, "native-dsl-test", "native-dsl-test:4")
            assertEquals(1, f.player.attr[DOOM_KILL_COUNT])
        }
    }

    @Test
    fun `retained auxiliary death token cleans its own actor after native identity detachment`() {
        Fixture(4, standardSeed(4, DoomAttackStyle.RANGED)).use { f ->
            val volatile =
                Npc(
                    assertNotNull(ServerCacheManager.getNpc(DoomAssets.VOLATILE_EARTH)),
                    f.npc.coords.translateX(10),
                )
            f.npcs.add(volatile, 1000)
            f.instances.attachNpc(f.session.id, volatile)
            f.run.volatiles += volatile
            f.run.volatileAttackableCycles[volatile] = 102
            val token = assertNotNull(f.manager.beginAuxiliaryDeath(volatile))
            f.npcs.del(volatile, Int.MAX_VALUE)
            assertNull(f.instances.instanceForNpc(volatile))
            f.manager.finishAuxiliaryDeath(volatile, token)
            assertFalse(volatile in f.run.volatiles)
            assertFalse(volatile in f.run.volatileAttackableCycles)
            f.manager.finishAuxiliaryDeath(volatile, token)
            assertFalse(f.run.retired)
            assertTrue(f.npc.isSlotAssigned)
        }
    }

    @Test
    fun `native decision sequences match retained oracle across delves substitutions and car interleaving`() {
        var repeatedRock = false
        var suppressedRock = false
        var chargedRock = false
        var rejectedStandard = false
        for (delve in 1..9) for (seed in listOf(1L, 42L, 99L, Long.MIN_VALUE)) {
            Fixture(delve, seed).use { f ->
                repeat(24) { step ->
                    if (delve >= 5 && step in setOf(6, 17)) {
                        f.run.state = DoomState.BURROWING
                        val tick = f.clock.cycle
                        val actionDelay = f.npc.actionDelay
                        val trace = f.registry.of(f.npc).recentAbilityCommitTicks()
                        val primaryBefore = f.registry.of(f.npc).decisionResult(DOOM_PRIMARY_STREAM)
                        val expectedStyle = f.carProjectile()
                        assertEquals(expectedStyle, f.run.pendingHits.last().style)
                        assertEquals(tick, f.clock.cycle)
                        assertEquals(actionDelay, f.npc.actionDelay)
                        assertEquals(trace, f.registry.of(f.npc).recentAbilityCommitTicks())
                        assertEquals(
                            primaryBefore,
                            f.registry.of(f.npc).decisionResult(DOOM_PRIMARY_STREAM),
                        )
                        f.run.pendingHits.clear()
                        f.run.state = DoomState.ACTIVE
                    }
                    val adjacent = step % 8 == 2
                    val forced = step % 9 == 5
                    val suppressed = step % 4 == 3 || forced
                    f.player.coords = f.npc.coords.translateX(if (adjacent) 5 else 8)
                    f.run.shockwavePreparing = suppressed
                    f.run.preShockwaveAttacksRemaining = if (suppressed) 10 else 0
                    f.run.forceRockBeforeShockwave = forced
                    val expected =
                        DoomAttackFlow.next(delve, f.oracleState, adjacent, forced, suppressed)
                    if (expected.primary == DoomPrimaryAttack.STANDARD && step % 3 == 0) {
                        val counts = f.decisionCounts()
                        val trace = f.registry.of(f.npc).recentAbilityCommitTicks()
                        val actionDelay = f.npc.actionDelay
                        val mode = f.npc.mode
                        val interaction = f.npc.interaction
                        // Visibility is reversible; logout retires the player's native HitScope.
                        f.player.hidden = true
                        f.attack()
                        assertEquals(counts, f.decisionCounts())
                        assertEquals(trace, f.registry.of(f.npc).recentAbilityCommitTicks())
                        assertEquals(actionDelay, f.npc.actionDelay)
                        assertEquals(mode, f.npc.mode)
                        assertEquals(interaction, f.npc.interaction)
                        f.player.hidden = false
                        rejectedStandard = true
                    }
                    repeat(step % 3) { f.random.of(999) }
                    val previous = f.oracleState.lastFreePrimary
                    f.attack()
                    f.assertTurn(expected)
                    assertEquals(
                        if (expected.spawnLarvae) DoomRules.profile(delve).outsideLarvaCount else 0,
                        f.run.larvae.size,
                        "Larvae must follow accepted free selections only: delve=$delve seed=$seed step=$step",
                    )
                    if (expected.consumesRotation && expected.primary == DoomPrimaryAttack.ROCKS) {
                        repeatedRock = repeatedRock || previous == DoomPrimaryAttack.ROCKS
                        suppressedRock = suppressedRock || suppressed
                        chargedRock = chargedRock || expected.startMeleeCharge
                    }
                    assertEquals(
                        if (expected.startMeleeCharge) f.clock.cycle + 2 else Int.MAX_VALUE,
                        f.run.pendingMeleeChargeCycle,
                    )
                    // Complete the independent spatial mechanics before the next ordinary turn.
                    f.run.larvae.keys.toList().forEach { f.npcs.del(it, Int.MAX_VALUE) }
                    f.run.larvae.clear()
                    f.run.fallingRocks.clear()
                    f.run.rockVolleysRemaining = 0
                    f.run.pendingMeleeChargeCycle = Int.MAX_VALUE
                    f.at(maxOf(f.clock.cycle + 1, f.npc.actionDelay))
                }
            }
        }
        assertTrue(repeatedRock)
        assertTrue(suppressedRock)
        assertTrue(chargedRock)
        assertTrue(rejectedStandard)
    }

    @Test
    fun `coincident impact substitutes declared style after consuming original seeded draw`() {
        Fixture(4, standardSeed(4, DoomAttackStyle.RANGED)).use { f ->
            val expected = DoomAttackFlow.next(4, f.oracleState, adjacent = false)
            f.run.pendingHits +=
                DoomEncounterManager.PendingStyleHit(
                    impactCycle = 100 + DoomRules.standardProjectileTravelTicks(4),
                    style = DoomAttackStyle.MAGIC,
                    maxHit = 1,
                )
            f.attack()
            f.assertTurn(expected)
            val decision = assertNotNull(f.registry.of(f.npc).decisionResult(DOOM_STYLE_STREAM))
            assertEquals(DoomAttackStyle.RANGED.name, decision.rolledChoice)
            assertEquals(DoomAttackStyle.MAGIC.name, decision.choice)
            assertEquals(HitType.Magic, f.hits().single().second.type)
            f.run.pendingHits.clear()
            f.at(f.npc.actionDelay)
            val following = DoomAttackFlow.next(4, f.oracleState, adjacent = false)
            f.attack()
            f.assertTurn(following)
        }
    }

    private class Fixture(val delve: Int, val seed: Long) : AutoCloseable {
        var oracleState = DoomAttackFlow.initial(delve, seed)
        private val oracleCounts = longArrayOf(0, 0, 0)
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
                    currentMapClock = 100
                    processedMapClock = 100
                    hitpoints = 600
                    mode = NpcMode.ApPlayer2
                    spawnOwner = player.uid
                }
        val registry = EncounterRegistry(random)
        val npcList = NpcList()
        val world = mock(WorldRepository::class.java)
        val npcs = NpcRepository(clock, NpcRegistry(npcList, collision, events), npcList)
        private var nextRegion = 0
        private val regions =
            mock(RegionRepository::class.java) { call ->
                when (call.method.name) {
                    "add" -> {
                        val base = 1280 + 64 * nextRegion++
                        // Initial selected delve; the stale-session test subsequently enters delve
                        // 1.
                        val arena = arenaForDelve(if (nextRegion == 1) delve else 1)
                        Region(CoordGrid(base, 9472), CoordGrid(base + 64, 9536), 1, 1).apply {
                            for (x in 0..7) for (z in 0..7) {
                                registerZone(
                                    RegionZoneCopy(
                                        ZoneKey(arena.regionX * 8 + x, arena.regionZ * 8 + z, 0),
                                        0,
                                        null,
                                    ),
                                    ZoneKey(base / 8 + x, 1184 + z, 0),
                                )
                                collision.allocateIfAbsent(base + x * 8, 9472 + z * 8, 0)
                            }
                        }
                    }
                    "isValid" -> true
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
        val session =
            (instances.create(
                    player,
                    DOOM_INSTANCE_KEY,
                    doomSpec(delve, npc.type),
                    InstanceAccess.Private,
                    clock.cycle,
                ) as InstanceManager.Result.Created)
                .session
        val rewards = mock(DoomRewardService::class.java)
        val statistics = mock(DoomScoreboardService::class.java)
        val hpBar = mock(BossHpBarScript::class.java)
        val instanceSpecs = BossInstanceRegistry()
        val locs =
            mock(LocRepository::class.java) { call ->
                if (call.method.name == "add") true else Answers.RETURNS_DEFAULTS.answer(call)
            }
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
                instances,
                npcs,
                locs,
                AiPlayerInteractions(events, players),
                collision,
                random,
                registry,
                accuracy,
                mock(WornBonuses::class.java),
                world,
                hpBar,
                rewards,
                statistics,
                mock(RouteFactory::class.java),
            )
        val run =
            DoomEncounterManager.Run(
                player = player,
                session = session,
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
            collision.allocateIfAbsent(DOOM_LOBBY.x, DOOM_LOBBY.z, DOOM_LOBBY.level)
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(DoomWorld(instanceSpecs, manager, events)) { scripts.startup() }
            val nativeLifecycle =
                Class.forName("org.rsmod.api.instances.hook.InstanceLifecycleScript")
                    .getDeclaredConstructor(InstanceManager::class.java, MapClock::class.java)
                    .newInstance(instances, clock) as PluginScript
            with(nativeLifecycle) { scripts.startup() }
            instances.finalizeEntry(player, session, clock.cycle)
            npcs.add(npc, 10000)
            instances.attachNpc(session.id, npc)
            with(DoomCombat(manager, mock(NpcDeath::class.java), players, npcList, deps)) {
                scripts.startup()
            }
            manager.javaClass
                .getDeclaredMethod("attachRun", DoomEncounterManager.Run::class.java)
                .let {
                    it.isAccessible = true
                    it.invoke(manager, run)
                }
            with(PlayerHitScript()) { scripts.startup() }
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

        fun decisionCounts(): List<Long> =
            listOf(DOOM_PRIMARY_STREAM, DOOM_STYLE_STREAM, DOOM_CHARGE_STREAM).map {
                registry.existing(npc)?.decisionDrawCount(it) ?: 0L
            }

        fun assertTurn(expected: DoomAttackTurn) {
            val encounter = registry.of(npc)
            val next = expected.nextState
            val advanced =
                listOf(
                    next.primaryRandomState != oracleState.primaryRandomState,
                    next.styleRandomState != oracleState.styleRandomState,
                    next.chargeRandomState != oracleState.chargeRandomState,
                )
            advanced.forEachIndexed { index, changed -> if (changed) oracleCounts[index]++ }
            assertEquals(
                oracleCounts.toList(),
                decisionCounts(),
                "delve=$delve seed=$seed turn=${next.scheduledTurn}",
            )
            val primary = encounter.decisionResult(DOOM_PRIMARY_STREAM)
            val style = encounter.decisionResult(DOOM_STYLE_STREAM)
            val charge = encounter.decisionResult(DOOM_CHARGE_STREAM)
            if (expected.consumesRotation) {
                assertEquals(expected.primary.name, assertNotNull(primary).choice)
                assertTrue(primary.drew)
                if (expected.primary == DoomPrimaryAttack.STANDARD) {
                    assertEquals(expected.standardStyle.name, assertNotNull(style).rolledChoice)
                    assertTrue(style.drew)
                    assertNull(charge)
                } else {
                    assertNull(style)
                    assertEquals(
                        if (expected.startMeleeCharge) DOOM_CHARGE else DOOM_NO_CHARGE,
                        assertNotNull(charge).choice,
                    )
                    assertEquals(advanced[2], charge.drew)
                }
            } else {
                assertNull(primary)
                assertNull(style)
                assertNull(charge)
            }
            oracleState = next
        }

        fun carProjectile(): DoomAttackStyle {
            val roll = DoomAttackDecision.chooseStyle(oracleState.styleRandomState, delve)
            manager.javaClass
                .getDeclaredMethod("launchCarProjectile", DoomEncounterManager.Run::class.java)
                .let {
                    it.isAccessible = true
                    it.invoke(manager, run)
                }
            oracleState = oracleState.copy(styleRandomState = roll.nextState)
            oracleCounts[1]++
            assertEquals(oracleCounts.toList(), decisionCounts())
            return roll.value
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
            instances.sessionsForKey(DOOM_INSTANCE_KEY).toList().forEach(instances::terminate)
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
