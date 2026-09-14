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
import org.rsmod.api.game.process.npc.NpcQueueProcessor
import org.rsmod.api.game.process.player.PlayerQueueProcessor
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceAttributes
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.events.InstancePlayerLeaveEvent
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.access.StandardNpcAccessContextFactory
import org.rsmod.api.npc.access.StandardNpcAccessLauncher
import org.rsmod.api.npc.events.interact.AiPlayerEvents
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.events.PlayerMovementEvent
import org.rsmod.api.player.hit.DeferredPlayerHit
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.righthand
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
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitAttribution
import org.rsmod.game.hit.HitContext
import org.rsmod.game.hit.HitOrigin
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.InvObj
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.region.Region
import org.rsmod.game.region.zone.RegionZoneCopy
import org.rsmod.game.seq.EntitySeq
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
        f.run.attacks.beginRockVolleys(2, tied = true, eligible = { false }, launch = {})
        f.attack()
        f.run.attacks.cancelRockVolleys()
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
                forced.run.phases.shockwavePreparing = true
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
                        f.run.attacks.cancelHits()
                        f.run.state = DoomState.ACTIVE
                    }
                    val adjacent = step % 8 == 2
                    val forced = step % 9 == 5
                    val suppressed = step % 4 == 3 || forced
                    f.player.coords = f.npc.coords.translateX(if (adjacent) 5 else 8)
                    f.run.phases.shockwavePreparing = suppressed
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
                    f.run.attacks.cancelRockVolleys()
                    f.run.phases.cancelMeleeCharge()
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
            f.scheduleStyleHit(
                DoomEncounterManager.PendingStyleHit(
                    impactCycle = 100 + DoomRules.standardProjectileTravelTicks(4),
                    style = DoomAttackStyle.MAGIC,
                    maxHit = 1,
                )
            )
            f.attack()
            f.assertTurn(expected)
            val decision = assertNotNull(f.registry.of(f.npc).decisionResult(DOOM_STYLE_STREAM))
            assertEquals(DoomAttackStyle.RANGED.name, decision.rolledChoice)
            assertEquals(DoomAttackStyle.MAGIC.name, decision.choice)
            assertEquals(HitType.Magic, f.hits().single().second.type)
            f.run.attacks.cancelHits()
            f.at(f.npc.actionDelay)
            val following = DoomAttackFlow.next(4, f.oracleState, adjacent = false)
            f.attack()
            f.assertTurn(following)
        }
    }

    @Test
    fun `native combat phase identity retains accepted streams and attack lifetime`() {
        for (delve in 1..9) {
            Fixture(delve, standardSeed(delve, DoomAttackStyle.RANGED)).use { f ->
                f.attack()
                val encounter = f.registry.of(f.npc)
                val counts = f.decisionCounts()
                val results =
                    listOf(DOOM_PRIMARY_STREAM, DOOM_STYLE_STREAM, DOOM_CHARGE_STREAM)
                        .map(encounter::decisionResult)
                val trace = encounter.recentAbilityCommitTicks()
                val actionDelay = f.npc.actionDelay
                val lastAbility = encounter.lastAbilityTick
                val hit = f.hits().single().second
                for (state in DOOM_COMBAT_STATES) {
                    f.run.state = state
                    assertEquals(doomPhase(delve, state), encounter.currentPhaseName)
                    assertEquals(state, f.run.state)
                    assertEquals(counts, f.decisionCounts())
                    assertEquals(
                        results,
                        listOf(DOOM_PRIMARY_STREAM, DOOM_STYLE_STREAM, DOOM_CHARGE_STREAM)
                            .map(encounter::decisionResult),
                    )
                    assertEquals(trace, encounter.recentAbilityCommitTicks())
                    assertEquals(actionDelay, f.npc.actionDelay)
                    assertEquals(lastAbility, encounter.lastAbilityTick)
                    assertTrue(hit.isValidNow())
                }
                // The native phase is authoritative even when entered through its public API.
                encounter.transitionTo(doomPhase(delve, DoomState.MELEE_CHARGE), f.clock.cycle)
                assertEquals(DoomState.MELEE_CHARGE, f.run.state)
            }
        }
    }

    @Test
    fun `shield readiness precedes input but activation follows queued damage and LateCycle larvae`() {
        Fixture(4, 42).use { f ->
            f.beginShield()
            val epoch = f.run.shieldEpoch
            assertEquals(1, f.worldQueues.size)
            f.worldPreInput()
            f.lateCycle()
            assertEquals(101, f.clock.cycle)
            assertEquals(DoomState.SHIELD_TRANSITION, f.run.state)
            assertEquals(600, f.npc.hitpoints)

            f.worldPreInput()
            assertTrue(f.worldQueues.isEmpty, "Native deadline has marked readiness")
            assertEquals(DoomState.SHIELD_TRANSITION, f.run.state)
            assertEquals(DoomAssets.BOSS, f.npc.visType.id)
            assertEquals(Int.MAX_VALUE, f.run.beamFireCycle)
            val incoming = f.queueBossHit(37)
            assertTrue("queue.hit" in f.npc.queueList)
            f.processBossHits()
            val damagedHealth = 600 - incoming.damage
            assertEquals(damagedHealth, f.npc.hitpoints)
            assertEquals(damagedHealth, f.run.normalHitpoints)
            assertFalse("queue.hit" in f.npc.queueList)
            val larva = f.arrivingLarva(activeCycle = 102)
            val expectedHealth =
                (damagedHealth + DoomRules.larvaHeal(0, false)).coerceAtMost(f.npc.baseHitpointsLvl)
            f.lateCycle()

            assertEquals(102, f.clock.cycle)
            assertEquals(DoomState.SHIELDED, f.run.state)
            assertEquals(doomPhase(4, DoomState.SHIELDED), f.registry.of(f.npc).currentPhaseName)
            assertEquals(DoomAssets.SHIELDED, f.npc.visType.id)
            assertEquals(DoomRules.SHIELD_HITPOINTS, f.npc.hitpoints)
            assertEquals(expectedHealth, f.run.normalHitpoints)
            assertEquals(1, f.run.demonicCharge)
            assertFalse(larva.isSlotAssigned)
            assertTrue(f.hits().isEmpty(), "Arriving larva keeps transition hazard grace")
            assertEquals(epoch, f.run.shieldEpoch)
            assertEquals(
                DoomCombatTimeline.firstShieldLarvaSpawnCycle(102),
                f.run.nextShieldLarvaCycle,
            )
            assertEquals(
                DoomCombatTimeline.beamFireCycle(102, DoomState.SHIELDED),
                f.run.beamFireCycle,
            )
            assertEquals(listOf(0L, 0L, 0L), f.decisionCounts())
            f.manager.tick(f.player)
            assertEquals(expectedHealth, f.run.normalHitpoints)
            assertEquals(epoch, f.run.shieldEpoch)
        }
    }

    @Test
    fun `stale shield readiness cannot activate a replacement phase epoch`() {
        for (readyBeforeExit in listOf(false, true)) {
            Fixture(4, 42).use { f ->
                f.beginShield()
                f.worldPreInput()
                f.lateCycle()
                if (readyBeforeExit) f.worldPreInput()
                f.run.state = DoomState.ACTIVE
                f.beginShield()
                val epoch = f.run.shieldEpoch
                f.worldPreInput()
                f.lateCycle()
                assertEquals(102, f.clock.cycle)
                assertEquals(DoomState.SHIELD_TRANSITION, f.run.state)
                assertEquals(DoomAssets.BOSS, f.npc.visType.id)
                f.worldPreInput()
                f.lateCycle()
                assertEquals(103, f.clock.cycle)
                assertEquals(DoomState.SHIELDED, f.run.state)
                assertEquals(epoch, f.run.shieldEpoch)
                assertEquals(
                    2,
                    f.worldQueues.size,
                    "Shield activation owns beam and larva deadlines",
                )
            }
        }
    }

    @Test
    fun `shield deadline retires on native death deletion and session departure`() {
        for (departure in listOf(false, true)) {
            Fixture(4, 42).use { f ->
                f.beginShield()
                val encounter = f.registry.of(f.npc)
                if (departure) {
                    f.events.publish(SessionStateEvent.Logout(f.player))
                    assertEquals(DoomState.INVALIDATED, f.run.state)
                } else {
                    f.npc.hitpoints = 0
                    assertSame(f.run, f.manager.beginNpcDeath(f.npc))
                    f.npcs.del(f.npc, Int.MAX_VALUE)
                    assertEquals(DoomState.DYING, f.run.state)
                }
                assertTrue(encounter.isEnded)
                assertTrue(f.worldQueues.isEmpty)
                f.at(110)
                f.worldPreInput()
                f.manager.tick(f.player)
                assertFalse(f.npc.isSlotAssigned)
                assertEquals(DoomAssets.BOSS, f.npc.visType.id)
                assertFailsWith<IllegalStateException> { f.run.state = DoomState.ACTIVE }
            }
        }
    }

    @Test
    fun `native hidden startup retains its reveal deadline and session outcome`() {
        Fixture(4, 42, active = false).use { f ->
            f.run.bossRevealCycle = 103
            f.run.startCycle = 103
            f.npcs.hide(f.npc, 3)
            assertEquals(DOOM_INACTIVE_PHASE, f.registry.of(f.npc).currentPhaseName)
            repeat(2) {
                f.worldPreInput()
                f.lateCycle()
                assertEquals(DoomState.STARTING, f.run.state)
                assertFalse(f.npc.isVisible)
                f.attack()
                assertTrue(f.registry.of(f.npc).recentAbilityCommitTicks().isEmpty())
            }
            f.worldPreInput()
            f.lateCycle()
            assertEquals(103, f.clock.cycle)
            assertEquals(DoomState.ACTIVE, f.run.state)
            assertEquals(doomPhase(4), f.registry.of(f.npc).currentPhaseName)
            assertEquals(103, f.npc.lifecycleRevealCycle)
            // This is NpcRepository's existing native reveal processing, independent of DSL clocks.
            f.npcs.javaClass.declaredMethods
                .single { it.name.startsWith("processReveal") }
                .let {
                    it.isAccessible = true
                    it.invoke(f.npcs, f.npc)
                }
            assertTrue(f.npc.isVisible)
            assertEquals(1, f.worldQueues.size, "Activation arms the existing 100-tick readiness")
            assertEquals(listOf(0L, 0L, 0L), f.decisionCounts())
        }
    }

    @Test
    fun `declared shockwave graph preserves every delve packet pulse and final shield order`() {
        for (delve in 1..9) {
            Fixture(delve, 42).use { f ->
                f.callRun("beginShockwavePreparation")
                val shield = f.protectingEarthShield()
                val start = DoomCombatTimeline.shockwaveStartCycle(100)
                val slams = DoomCombatTimeline.shockwaveSlamCycles(start, delve)
                val pulses = DoomCombatTimeline.shockwavePulseCycles(start, delve)
                val recover = pulses.last()
                val packets = mutableListOf<Pair<Int, Int>>()
                val pulseRolls = mutableListOf<Int>()
                f.random.beforeRoll = {
                    pulseRolls += f.clock.cycle
                    assertEquals(DoomState.SHOCKWAVE, f.run.state)
                    assertTrue(shield.isSlotAssigned, "Final pulse must precede recovery cleanup")
                    assertSame(shield, f.run.shield)
                }
                while (f.clock.cycle < recover) {
                    f.npc.pendingSequence = EntitySeq.NULL
                    f.worldPreInput()
                    f.lateCycle()
                    val sequence = f.npc.pendingSequence.id
                    if (
                        sequence in
                            listOf(
                                DoomAssets.ANIM_SHOCKWAVE_PREPARE,
                                DoomAssets.ANIM_SHOCKWAVE_CHARGE,
                                DoomAssets.ANIM_SHOCKWAVE_SLAM,
                                DoomAssets.ANIM_SHOCKWAVE_RECOVER,
                            )
                    )
                        packets += f.clock.cycle to sequence
                    if (f.clock.cycle in slams) {
                        assertEquals(f.clock.cycle + 2, f.run.activeShockwaves.single().pulseCycle)
                        assertEquals(
                            DoomGeometry.centre(f.npc.coords, f.npc.size),
                            f.run.activeShockwaves.single().origin,
                        )
                    }
                }
                assertEquals(
                    listOf(
                        start to DoomAssets.ANIM_SHOCKWAVE_PREPARE,
                        (start + 2) to DoomAssets.ANIM_SHOCKWAVE_CHARGE,
                    ) +
                        slams.map { it to DoomAssets.ANIM_SHOCKWAVE_SLAM } +
                        (recover to DoomAssets.ANIM_SHOCKWAVE_RECOVER),
                    packets,
                )
                val tiles = DoomShockwaveGeometry.candidateTiles(f.npc.coords).size
                assertEquals(
                    pulses.associateWith { tiles },
                    pulseRolls.groupingBy { it }.eachCount(),
                )
                assertEquals(DoomState.ACTIVE, f.run.state)
                assertTrue(f.run.activeShockwaves.isEmpty())
                assertFalse(shield.isSlotAssigned)
                assertNull(f.run.shield)
                assertTrue(
                    f.hits().isEmpty(),
                    "The retained shield covers every pulse, including the final one",
                )
                assertEquals(DoomRules.POST_SHOCKWAVE_ATTACKS, f.run.postShockwaveAttacksRemaining)
                assertTrue(f.run.forceRockAfterShockwave)
                assertEquals(1, f.worldQueues.size, "Recovery arms one next readiness deadline")
                assertEquals(listOf(0L, 0L, 0L), f.decisionCounts())
            }
        }
    }

    @Test
    fun `hundred tick shockwave readiness survives charge and shield until active priority`() {
        for (shielded in listOf(false, true)) {
            Fixture(4, 42).use { f ->
                f.callRun("activate")
                assertEquals(1, f.worldQueues.size)
                f.at(198)
                if (shielded) f.beginShield() else f.callRun("beginMeleeCharge")
                f.worldPreInput()
                f.lateCycle()
                assertFalse(f.run.phases.shockwaveDue)
                f.worldPreInput()
                assertTrue(f.run.phases.shockwaveDue)
                f.lateCycle()
                assertEquals(200, f.clock.cycle)
                assertEquals(
                    if (shielded) DoomState.SHIELDED else DoomState.MELEE_CHARGE,
                    f.run.state,
                )
                assertFalse(f.run.shockwavePreparing)
                if (shielded) f.finishShield()
                else {
                    f.queueBossHit(1, HitType.Melee)
                    f.processBossHits()
                }
                assertEquals(DoomState.ACTIVE, f.run.state)
                f.manager.tick(f.player)
                assertTrue(f.run.shockwavePreparing)
                assertFalse(f.run.phases.shockwaveDue)
                assertEquals(1, f.worldQueues.size)
                assertEquals(DoomRules.preShockwaveAttacks(4), f.run.preShockwaveAttacksRemaining)
            }
        }
    }

    @Test
    fun `car emergence replaces the outstanding readiness deadline with six ticks`() {
        Fixture(5, 42).use { f ->
            f.callRun("activate")
            f.at(110)
            f.run.state = DoomState.BURROWING
            f.registry
                .of(f.npc)
                .transitionTo(
                    doomCarPhase(5, DoomCarPacket.RECOVERY, DoomRules.carZooms(5)),
                    f.clock.cycle,
                )
            f.callRun("completeZoom")
            assertEquals(116, f.run.nextAttackNotBeforeCycle)
            assertEquals(1, f.worldQueues.size, "The original 100-tick queue was cancelled")
            f.at(114)
            f.worldPreInput()
            f.lateCycle()
            assertFalse(f.run.shockwavePreparing)
            assertFalse(f.run.phases.shockwaveDue)
            f.worldPreInput()
            assertTrue(f.run.phases.shockwaveDue)
            f.lateCycle()
            assertEquals(116, f.clock.cycle)
            assertTrue(f.run.shockwavePreparing)
            f.run.phases.shockwavePreparing = false
            assertTrue(f.worldQueues.isEmpty)
            f.at(200)
            f.worldPreInput()
            assertFalse(
                f.run.phases.shockwaveDue,
                "The cancelled original deadline cannot reassert readiness",
            )
        }
    }

    @Test
    fun `warning retains accepted non tongue budgets and ordinary opportunities until LateCycle deadline`() {
        Fixture(4, standardSeed(4, DoomAttackStyle.RANGED)).use { f ->
            f.callRun("beginShockwavePreparation")
            f.run.forceRockBeforeShockwave = false
            val initial = f.run.preShockwaveAttacksRemaining
            f.player.hidden = true
            f.attack()
            assertEquals(initial, f.run.preShockwaveAttacksRemaining)
            assertEquals(listOf(0L, 0L, 0L), f.decisionCounts())
            f.player.hidden = false
            f.attack()
            assertEquals(initial - 1, f.run.preShockwaveAttacksRemaining)
            assertEquals(listOf(1L, 1L, 0L), f.decisionCounts())
            f.at(f.npc.actionDelay)
            f.player.coords = f.npc.coords.translateX(5)
            f.attack()
            assertEquals(initial - 1, f.run.preShockwaveAttacksRemaining)
            val counts = f.decisionCounts()
            f.at(114)
            f.worldPreInput()
            assertTrue(f.run.shockwavePreparing, "Readiness does not change the pre-input phase")
            val committed = f.registry.of(f.npc).recentAbilityCommitTicks().size
            f.attack()
            assertEquals(committed + 1, f.registry.of(f.npc).recentAbilityCommitTicks().size)
            assertEquals(initial - 1, f.run.preShockwaveAttacksRemaining)
            f.lateCycle()
            assertEquals(115, f.clock.cycle)
            assertEquals(DoomState.SHOCKWAVE, f.run.state)
            f.attack()
            assertEquals(committed + 1, f.registry.of(f.npc).recentAbilityCommitTicks().size)
            assertEquals(counts, f.decisionCounts())
        }
    }

    @Test
    fun `already armed melee charge retains the original warning deadline after native interruption`() {
        val seed =
            (1L..10000L).first {
                DoomAttackFlow.next(4, DoomAttackFlow.initial(4, it), adjacent = false)
                    .startMeleeCharge
            }
        Fixture(4, seed).use { f ->
            f.attack()
            assertEquals(102, f.run.pendingMeleeChargeCycle)
            val counts = f.decisionCounts()
            f.run.fallingRocks.clear()
            f.run.attacks.cancelRockVolleys()
            f.callRun("beginShockwavePreparation")
            repeat(2) {
                f.worldPreInput()
                f.lateCycle()
            }
            assertEquals(DoomState.MELEE_CHARGE, f.run.state)
            assertTrue(f.run.shockwavePreparing)
            assertEquals(
                doomShockwaveWarningPhase(4, DoomState.MELEE_CHARGE),
                f.registry.of(f.npc).currentPhaseName,
            )
            f.queueBossHit(1, HitType.Melee)
            f.processBossHits()
            assertEquals(DoomState.ACTIVE, f.run.state)
            assertTrue(f.run.shockwavePreparing)
            f.at(114)
            f.npc.pendingSequence = EntitySeq.NULL
            f.worldPreInput()
            f.lateCycle()
            assertEquals(115, f.clock.cycle)
            assertEquals(DoomState.SHOCKWAVE, f.run.state)
            assertEquals(DoomAssets.ANIM_SHOCKWAVE_PREPARE, f.npc.pendingSequence.id)
            assertEquals(counts, f.decisionCounts())
        }
    }

    @Test
    fun `native death and departure cancel both shockwave readiness and declared edge queues`() {
        for (warning in listOf(false, true)) for (departure in listOf(false, true)) {
            Fixture(4, 42).use { f ->
                if (warning) f.callRun("beginShockwavePreparation") else f.callRun("activate")
                assertEquals(1, f.worldQueues.size)
                if (departure) f.events.publish(SessionStateEvent.Logout(f.player))
                else {
                    f.npc.hitpoints = 0
                    assertNotNull(f.manager.beginNpcDeath(f.npc))
                    assertTrue(
                        f.worldQueues.isEmpty,
                        "Death invalidates deadlines before native NPC deletion",
                    )
                    f.npcs.del(f.npc, Int.MAX_VALUE)
                }
                assertTrue(f.worldQueues.isEmpty)
                f.at(300)
                f.worldPreInput()
                f.manager.tick(f.player)
                assertFalse(f.npc.isSlotAssigned)
                assertTrue(f.run.activeShockwaves.isEmpty())
                assertFalse(f.run.phases.shockwaveDue)
            }
        }
    }

    @Test
    fun `native beam deadlines preserve thirteen seventeen and twenty ticks and exact queued completion`() {
        for (state in listOf(DoomState.MELEE_CHARGE, DoomState.SHIELDED, DoomState.BURROWING)) {
            Fixture(if (state == DoomState.BURROWING) 5 else 4, 42).use { f ->
                f.startCharge(state)
                val duration = DoomRules.chargeTicks(state)
                val fire = f.clock.cycle + duration
                assertEquals(fire, f.run.beamFireCycle)
                assertEquals(1, f.worldQueues.size)
                f.at(fire - 2)
                f.worldPreInput()
                f.lateCycle()
                assertTrue(f.projectiles().isEmpty())
                assertEquals(duration - 1, f.run.chargeProgress)
                f.worldPreInput()
                assertTrue(f.worldQueues.isEmpty, "The native queue marks readiness before input")
                assertNull(f.run.pendingBeamHit)
                f.lateCycle()
                assertEquals(fire, f.clock.cycle)
                assertEquals(5, f.projectiles().size)
                val pending = assertNotNull(f.run.pendingBeamHit)
                assertEquals(state, f.run.pendingBeamState)
                assertEquals(Int.MAX_VALUE, f.run.beamFireCycle)
                assertEquals(duration, f.run.chargeProgress)
                assertTrue(DoomBeamQueue.containsIdentity(f.player.queueList, pending))

                f.at(f.run.pendingBeamImpactCycle)
                f.callRun("processPendingBeam")
                assertSame(
                    pending,
                    f.run.pendingBeamHit,
                    "Elapsed time cannot finish an unprocessed native hit",
                )
                f.processCurrentPlayerHits()
                assertFalse(DoomBeamQueue.containsIdentity(f.player.queueList, pending))
                assertEquals(99 - DoomRules.beamMaxHit(f.delve), f.player.hitpoints)
                f.manager.tick(f.player)
                assertNull(f.run.pendingBeamHit)
                assertEquals(
                    if (state == DoomState.BURROWING) DoomState.BURROWING else DoomState.ACTIVE,
                    f.run.state,
                )
                assertEquals(0, f.run.chargeProgress)
                if (state == DoomState.BURROWING) {
                    assertEquals(f.clock.cycle + 20, f.run.beamFireCycle)
                    assertEquals(1, f.worldQueues.size)
                }
                assertEquals(listOf(0L, 0L, 0L), f.decisionCounts())
            }
        }
    }

    @Test
    fun `ready melee beam is interrupted only by positive native player melee impact`() {
        for (kind in listOf("melee", "zero", "ranged", "recoil")) {
            Fixture(4, 42).use { f ->
                f.startCharge(DoomState.MELEE_CHARGE)
                val fire = f.run.beamFireCycle
                f.at(fire - 1)
                f.worldPreInput()
                assertTrue(f.worldQueues.isEmpty, "$kind native beam deadline")
                val queued =
                    when (kind) {
                        "melee" -> f.queueBossHit(1, HitType.Melee)
                        "zero" -> f.queueBossHit(0, HitType.Melee)
                        "ranged" -> f.queueBossHit(1, HitType.Ranged)
                        else -> f.queueRecoil()
                    }
                f.processBossHits()
                f.lateCycle()
                if (kind == "melee") {
                    assertTrue(queued.damage > 0, "$kind native damage")
                    assertEquals(DoomState.ACTIVE, f.run.state, "$kind interruption")
                    // The retained positive-impact hook sprays acid before cancelling the beam.
                    assertTrue(f.projectiles().isNotEmpty(), "$kind retained acid spray")
                    assertTrue(
                        f.projectiles().all { it.spotanim == DoomAssets.GFX_ACID_PROJECTILE },
                        "$kind emits acid only, with no beam segments",
                    )
                    assertNull(f.run.pendingBeamHit, "$kind pending beam")
                    assertEquals(Int.MAX_VALUE, f.run.beamFireCycle, "$kind retired deadline")
                    assertEquals(
                        (fire - 1) + DoomRules.meleePunishDelay(4),
                        f.run.nextAttackNotBeforeCycle,
                        "$kind punishment delay",
                    )
                } else {
                    assertEquals(0, queued.damage, "$kind native damage")
                    assertEquals(DoomState.MELEE_CHARGE, f.run.state, "$kind retains charge")
                    assertEquals(
                        listOf(
                            DoomAssets.GFX_BEAM_HEAD,
                            DoomAssets.GFX_BEAM_MIDDLE,
                            DoomAssets.GFX_BEAM_MIDDLE,
                            DoomAssets.GFX_BEAM_MIDDLE,
                            DoomAssets.GFX_BEAM_END,
                        ),
                        f.projectiles().map { it.spotanim },
                        "$kind releases all five beam segments",
                    )
                    assertNotNull(f.run.pendingBeamHit, "$kind pending beam")
                }
            }
        }
    }

    @Test
    fun `ready shield beam rearm uses the queued demonbane weapon snapshot`() {
        for (demonbane in listOf(false, true)) {
            Fixture(4, 42).use { f ->
                f.startCharge(DoomState.SHIELDED)
                val fire = f.run.beamFireCycle
                val phase = f.registry.of(f.npc).currentPhaseName
                f.at(fire - 1)
                f.worldPreInput()
                if (demonbane) f.player.righthand = InvObj("obj.arclight")
                val queued = f.queueBossHit(7, HitType.Melee)
                f.player.righthand = null
                f.processBossHits()
                assertEquals(phase, f.registry.of(f.npc).currentPhaseName)
                f.lateCycle()
                if (demonbane) {
                    assertEquals(7, queued.damage)
                    assertEquals(DoomRules.SHIELD_HITPOINTS - 7, f.run.shieldHitpoints)
                    assertEquals(fire - 1 + 17, f.run.beamFireCycle)
                    assertTrue(f.projectiles().isEmpty())
                    assertEquals(1, f.worldQueues.size)
                } else {
                    assertEquals(0, queued.damage)
                    assertEquals(DoomRules.SHIELD_HITPOINTS, f.run.shieldHitpoints)
                    assertEquals(5, f.projectiles().size)
                    assertNotNull(f.run.pendingBeamHit)
                }
            }
        }
    }

    @Test
    fun `car beam reset preserves exact movement and refuses rearm while its hit is pending`() {
        Fixture(5, 42).use { f ->
            f.startCharge(DoomState.BURROWING)
            f.npc.movementLocked = false
            PathingEntityCommon.exactMove(
                f.npc,
                f.npc.coords,
                f.npc.coords.translateX(1),
                0,
                30,
                0,
                f.collision,
            )
            val movement = f.npc.pendingExactMove
            val location = f.npc.coords
            val phase = f.registry.of(f.npc).currentPhaseName
            f.at(f.run.beamFireCycle - 1)
            f.worldPreInput()
            f.queueBossHit(1)
            f.processBossHits()
            val fire = f.clock.cycle + 20
            assertEquals(fire, f.run.beamFireCycle)
            assertEquals(movement, f.npc.pendingExactMove)
            assertEquals(location, f.npc.coords)
            assertFalse(f.npc.movementLocked)
            assertEquals(phase, f.registry.of(f.npc).currentPhaseName)
            f.lateCycle()
            assertTrue(f.projectiles().isEmpty())
            f.at(fire - 1)
            f.worldPreInput()
            f.lateCycle()
            val pending = assertNotNull(f.run.pendingBeamHit)
            assertEquals(5, f.projectiles().size)
            f.queueBossHit(1)
            f.processBossHits()
            assertSame(pending, f.run.pendingBeamHit)
            assertEquals(Int.MAX_VALUE, f.run.beamFireCycle)
            assertEquals(movement, f.npc.pendingExactMove)
            assertFalse(f.npc.movementLocked)
        }
    }

    @Test
    fun `overdue warning resumes once after native melee interruption cancels the fired beam`() {
        val seed =
            (1L..10000L).first {
                DoomAttackFlow.next(4, DoomAttackFlow.initial(4, it), adjacent = false)
                    .startMeleeCharge
            }
        Fixture(4, seed).use { f ->
            f.attack()
            f.run.fallingRocks.clear()
            f.run.attacks.cancelRockVolleys()
            f.callRun("beginShockwavePreparation")
            repeat(2) {
                f.worldPreInput()
                f.lateCycle()
            }
            assertEquals(102, f.clock.cycle)
            assertEquals(115, f.run.beamFireCycle)
            f.at(114)
            f.worldPreInput()
            f.lateCycle()
            assertEquals(DoomState.MELEE_CHARGE, f.run.state)
            assertTrue(f.run.shockwavePreparing)
            val beam = assertNotNull(f.run.pendingBeamHit)
            assertTrue(DoomBeamQueue.containsIdentity(f.player.queueList, beam))
            f.queueBossHit(1, HitType.Melee)
            f.processBossHits()
            assertEquals(DoomState.ACTIVE, f.run.state)
            assertFalse(DoomBeamQueue.containsIdentity(f.player.queueList, beam))
            assertNull(f.run.pendingBeamHit)
            f.worldPreInput()
            f.npc.pendingSequence = EntitySeq.NULL
            f.lateCycle()
            assertEquals(116, f.clock.cycle)
            assertEquals(DoomShockwavePacket.PREPARE, f.run.phases.shockwavePacket)
            assertEquals(DoomAssets.ANIM_SHOCKWAVE_PREPARE, f.npc.pendingSequence.id)
            val entered = f.registry.of(f.npc).phaseEnteredTick
            f.worldPreInput()
            f.lateCycle()
            assertEquals(DoomShockwavePacket.PREPARE, f.run.phases.shockwavePacket)
            assertEquals(entered, f.registry.of(f.npc).phaseEnteredTick)
            assertEquals(99, f.player.hitpoints)
        }
    }

    @Test
    fun `native death and departure retire pending melee starts and beam deadlines`() {
        val seed =
            (1L..10000L).first {
                DoomAttackFlow.next(4, DoomAttackFlow.initial(4, it), adjacent = false)
                    .startMeleeCharge
            }
        for (started in listOf(false, true)) for (departure in listOf(false, true)) {
            Fixture(4, seed).use { f ->
                f.attack()
                f.run.fallingRocks.clear()
                f.run.attacks.cancelRockVolleys()
                if (started)
                    repeat(2) {
                        f.worldPreInput()
                        f.lateCycle()
                    }
                assertEquals(1, f.worldQueues.size)
                val projectiles = f.projectiles().size
                if (departure) f.events.publish(SessionStateEvent.Logout(f.player))
                else {
                    f.npc.hitpoints = 0
                    assertNotNull(f.manager.beginNpcDeath(f.npc))
                }
                assertTrue(f.worldQueues.isEmpty)
                assertEquals(Int.MAX_VALUE, f.run.pendingMeleeChargeCycle)
                assertEquals(Int.MAX_VALUE, f.run.beamFireCycle)
                f.at(300)
                f.worldPreInput()
                f.manager.tick(f.player)
                assertEquals(projectiles, f.projectiles().size)
                assertNull(f.run.pendingBeamHit)
            }
        }
    }

    @Test
    fun `native car graph preserves all zooms packet cadence exact moves and final emergence`() {
        val segmentCounts = mutableSetOf<Int>()
        for (delve in 5..9) for (start in listOf(100, 101)) {
            for (target in listOf(CoordGrid(1304, 9502), CoordGrid(1318, 9516))) {
                Fixture(delve, 42).use { f ->
                    f.at(start)
                    PathingEntityCommon.telejump(f.npc, f.collision, CoordGrid(1302, 9500))
                    f.player.coords = target
                    f.callRun("beginBurrow")
                    val schedules = mutableListOf<DoomCarZoomSchedule>()
                    var shots = 0L
                    var moves = 0
                    var lastSchedule: DoomCarZoomSchedule? = null
                    while (f.run.state == DoomState.BURROWING && f.clock.cycle < start + 150) {
                        val beforeSchedule = f.run.phases.carSchedule
                        val beforePlan = f.run.carPlan
                        val beforePacket = f.run.phases.carPacket
                        val zoom = f.run.phases.carZoom
                        val beforeProjectiles = f.projectiles().size
                        val beforePhase = f.registry.of(f.npc).currentPhaseName
                        // Native post-tick clears per-frame presentation before the next cycle.
                        f.npc.pendingExactMove = null
                        f.npc.pendingSequence = EntitySeq.NULL
                        f.worldPreInput()
                        assertEquals(beforePhase, f.registry.of(f.npc).currentPhaseName)
                        assertEquals(beforeProjectiles, f.projectiles().size)
                        assertNull(f.npc.pendingExactMove)
                        f.attack()
                        f.lateCycle()
                        val cycle = f.clock.cycle
                        val label =
                            "delve=$delve start=$start target=$target zoom=$zoom tick=$cycle"
                        val schedule = f.run.phases.carSchedule
                        if (schedule != null && schedule !== lastSchedule) {
                            lastSchedule = schedule
                            schedules += schedule
                            val plan = assertNotNull(f.run.carPlan, label)
                            segmentCounts += plan.segments.size
                            assertEquals(
                                DoomCarTimeline.schedule(cycle, delve, plan.segments.size),
                                schedule,
                                label,
                            )
                            assertEquals(DoomCarPacket.DASH_WAIT, f.run.phases.carPacket, label)
                        }
                        if (cycle == start + 5) {
                            assertEquals(DoomAssets.BURROWED, f.npc.visType.id, label)
                            // Independent beam readiness/impact is covered by the charge matrix.
                            f.run.phases.stopBeamCharge()
                        }
                        when (cycle - start) {
                            in 1..4 ->
                                assertEquals(
                                    DoomCarPacket.TRANSFORM_WAIT,
                                    f.run.phases.carPacket,
                                    label,
                                )
                            5 ->
                                assertEquals(DoomCarPacket.ROCK_WAIT, f.run.phases.carPacket, label)
                            6,
                            7 -> assertEquals(DoomCarPacket.EYE_WAIT, f.run.phases.carPacket, label)
                            8 ->
                                assertEquals(DoomCarPacket.DASH_WAIT, f.run.phases.carPacket, label)
                        }
                        if (cycle == start + 6)
                            assertEquals(DoomRules.carRockCount(delve), f.run.rocks.size, label)
                        val expectedShots =
                            beforeSchedule?.projectileCycles?.count { it == cycle } ?: 0
                        assertEquals(expectedShots, f.projectiles().size - beforeProjectiles, label)
                        shots += expectedShots
                        assertEquals(listOf(0L, shots, 0L), f.decisionCounts(), label)
                        val segmentIndex = beforeSchedule?.exactMoveCycles?.indexOf(cycle) ?: -1
                        if (segmentIndex >= 0) {
                            val plan = assertNotNull(beforePlan, label)
                            val segment = plan.segments[segmentIndex]
                            assertNotNull(f.npc.pendingExactMove, label)
                            assertEquals(segment.endSouthWest, f.npc.coords, label)
                            assertEquals(
                                segmentIndex == plan.segments.lastIndex,
                                f.npc.movementLocked,
                                label,
                            )
                            moves++
                        } else {
                            assertNull(f.npc.pendingExactMove, label)
                        }
                        if (beforeSchedule != null) {
                            val complete =
                                beforeSchedule.nextEyeCycle
                                    ?: beforeSchedule.trampleImpactCycles.last()
                            val expectedPhase =
                                when {
                                    cycle >= complete ->
                                        if (zoom < DoomRules.carZooms(delve))
                                            DoomCarPacket.DASH_WAIT
                                        else null
                                    cycle < beforeSchedule.dashStartCycle -> DoomCarPacket.DASH_WAIT
                                    cycle < beforeSchedule.exactMoveCycles.last() ->
                                        DoomCarPacket.DASHING
                                    beforeSchedule.slamPacketCycle == null -> DoomCarPacket.RECOVERY
                                    cycle < beforeSchedule.slamPacketCycle ->
                                        DoomCarPacket.PROJECTILES
                                    cycle < assertNotNull(beforeSchedule.waveCycle) ->
                                        DoomCarPacket.SLAM_WAIT
                                    else -> DoomCarPacket.RECOVERY
                                }
                            assertEquals(
                                expectedPhase,
                                f.run.phases.carPacket,
                                "$label from=$beforePacket",
                            )
                        }
                    }
                    assertEquals(DoomState.ACTIVE, f.run.state)
                    assertEquals(DoomRules.carZooms(delve), schedules.size)
                    assertEquals(schedules.sumOf { it.exactMoveCycles.size }, moves)
                    assertEquals(schedules.sumOf { it.projectileCycles.size }.toLong(), shots)
                    assertEquals(DoomAssets.BOSS, f.npc.visType.id)
                    assertEquals(CoordGrid(1309, 9507), f.npc.coords)
                    assertEquals(f.clock.cycle + 6, f.run.nextAttackNotBeforeCycle)
                    assertEquals(1, f.worldQueues.size, "Only post-car shockwave readiness remains")
                    assertNull(f.run.phases.carSchedule)
                    assertNull(f.run.pendingCarTrample)
                }
            }
        }
        assertTrue(
            segmentCounts.containsAll(listOf(1, 2, 3, 4)),
            "Observed exact-move sizes: $segmentCounts",
        )
    }

    @Test
    fun `native car overlap resolves trample then beam then style before final exact move`() {
        Fixture(6, 42).use { f ->
            PathingEntityCommon.telejump(f.npc, f.collision, CoordGrid(1302, 9500))
            f.player.coords = CoordGrid(1304, 9502)
            f.callRun("beginBurrow")
            while (f.clock.cycle < 109) f.carTick()
            val reset = f.queueBossHit(1)
            f.processBossHits()
            assertEquals(1, reset.damage)
            assertEquals(129, f.run.beamFireCycle)
            while (f.clock.cycle < 122) f.carTick()
            f.player.coords = CoordGrid(1318, 9516)
            f.carTick()
            assertEquals(2, f.run.phases.carZoom)
            val schedule = assertNotNull(f.run.phases.carSchedule)
            val plan = assertNotNull(f.run.carPlan)
            assertEquals(listOf(126, 127, 128, 129), schedule.exactMoveCycles)
            assertEquals(listOf(127, 129), schedule.projectileCycles)
            while (f.clock.cycle < 128) f.carTick()
            assertFalse(f.run.trampledThisZoom)
            val arriving = assertNotNull(f.run.pendingCarTrample)
            assertEquals(129, arriving.impactCycle)
            val beforeShots = f.decisionCounts()[1]
            val observed = mutableListOf<Int>()
            f.onProjectile = { projectile ->
                observed += projectile.spotanim
                assertTrue(f.run.trampledThisZoom, "Arrival is resolved before every projectile")
                assertNull(f.run.pendingCarTrample, "Previous segment snapshot was consumed")
                assertEquals(
                    plan.segments[2].endSouthWest,
                    f.npc.coords,
                    "Final exact move follows projectiles",
                )
                assertEquals(DoomCarPacket.DASHING, f.run.phases.carPacket)
                assertFalse(f.npc.movementLocked)
                if (observed.size <= 5) assertNull(f.run.pendingBeamHit)
                else
                    assertNotNull(
                        f.run.pendingBeamHit,
                        "Beam hit queues before the style projectile",
                    )
            }
            f.carTick()
            assertEquals(129, f.clock.cycle)
            assertEquals(
                listOf(
                    DoomAssets.GFX_BEAM_HEAD,
                    DoomAssets.GFX_BEAM_MIDDLE,
                    DoomAssets.GFX_BEAM_MIDDLE,
                    DoomAssets.GFX_BEAM_MIDDLE,
                    DoomAssets.GFX_BEAM_END,
                ),
                observed.take(5),
            )
            assertEquals(6, observed.size)
            assertEquals(beforeShots + 1, f.decisionCounts()[1])
            assertEquals(plan.targetSouthWest, f.npc.coords)
            assertNotNull(f.npc.pendingExactMove)
            assertTrue(f.npc.movementLocked)
            assertEquals(DoomCarPacket.PROJECTILES, f.run.phases.carPacket)
            assertEquals(plan.segments.last(), assertNotNull(f.run.pendingCarTrample).segment)
            assertEquals(130, f.run.pendingCarTrample?.impactCycle)
        }
    }

    @Test
    fun `native car trample samples arrival and final segment shoves once before recovery`() {
        for (arrival in listOf("leaves", "enters-edge", "enters-centre")) {
            Fixture(5, 42).use { f ->
                val entersAtArrival = arrival != "leaves"
                PathingEntityCommon.telejump(f.npc, f.collision, CoordGrid(1302, 9500))
                f.player.coords = CoordGrid(1304, 9502)
                f.callRun("beginBurrow")
                while (f.clock.cycle < 111) {
                    f.carTick()
                    if (f.clock.cycle == 105) f.run.phases.stopBeamCharge()
                }
                val pending = assertNotNull(f.run.pendingCarTrample)
                assertTrue(pending.finalSegment)
                assertEquals(112, pending.impactCycle)
                val inside =
                    if (arrival == "enters-centre")
                        DoomCarGeometry.centre(pending.segment.endSouthWest)
                    else pending.segment.endSouthWest.translate(4, 2)
                val outside = CoordGrid(1318, 9516)
                // Visibility of the exact-move packet must not decide arrival-time collision.
                f.player.coords = if (entersAtArrival) outside else inside
                f.worldPreInput()
                f.player.coords = if (entersAtArrival) inside else outside
                val beforeHits = f.hits().size
                f.npc.pendingExactMove = null
                f.lateCycle()
                // The retained successful shove calls native combatClearQueue, which also
                // clears the final trample just queued. A collision-denied shove retains it.
                assertEquals(
                    if (arrival == "enters-centre") 1 else 0,
                    f.hits().size - beforeHits,
                    arrival,
                )
                if (entersAtArrival) {
                    if (arrival == "enters-centre") {
                        val hit = f.hits().last().second
                        assertEquals(DoomRules.trampleDamage(5), hit.damage)
                        assertEquals(HitType.Typeless, hit.type)
                        // Retained native NPC collision blocks a route through Doom's footprint.
                        assertEquals(
                            inside,
                            f.player.coords,
                            "Centre shove remains collision-gated",
                        )
                    } else {
                        assertTrue(
                            f.player.coords !in
                                DoomCarGeometry.footprint(pending.segment.endSouthWest),
                            arrival,
                        )
                    }
                } else assertEquals(outside, f.player.coords)
                assertTrue(f.run.bossHasRepositioned)
                assertEquals(2, f.run.phases.carZoom)
                assertEquals(DoomCarPacket.DASH_WAIT, f.run.phases.carPacket)
                assertNull(f.run.pendingCarTrample)
                val after = f.player.coords
                val hits = f.hits().size
                f.manager.tick(f.player)
                assertEquals(after, f.player.coords)
                assertEquals(hits, f.hits().size, "The consumed arrival cannot hit or shove twice")
            }
        }
    }

    @Test
    fun `native car death deletion and departure retire queued and already ready zoom work`() {
        for (packet in DoomCarPacket.entries) for (exit in listOf("death", "delete", "leave")) {
            Fixture(6, 42).use { f ->
                PathingEntityCommon.telejump(f.npc, f.collision, CoordGrid(1302, 9500))
                f.player.coords = CoordGrid(1318, 9516)
                f.callRun("beginBurrow")
                while (f.run.phases.carPacket != packet && f.clock.cycle < 140) {
                    f.carTick()
                    if (f.clock.cycle == 105) f.run.phases.stopBeamCharge()
                }
                val label = "$packet $exit"
                assertEquals(packet, f.run.phases.carPacket, label)
                assertFalse(f.worldQueues.isEmpty, label)
                // Make every outstanding native deadline ready, without delivering LateCycle.
                f.at(300)
                f.worldPreInput()
                val projectiles = f.projectiles().size
                when (exit) {
                    "death" -> {
                        f.npc.hitpoints = 0
                        assertSame(f.run, f.manager.beginNpcDeath(f.npc), label)
                    }
                    "delete" -> f.npcs.del(f.npc, Int.MAX_VALUE)
                    else -> f.events.publish(SessionStateEvent.Logout(f.player))
                }
                assertTrue(f.worldQueues.isEmpty, label)
                assertNull(f.run.phases.carSchedule, label)
                assertFalse(f.run.phases.consumeCarPacketReady(), label)
                assertFalse(f.run.phases.consumeCarTrampleReady(), label)
                assertEquals(0, f.run.phases.consumeCarProjectiles(), label)
                f.npc.pendingExactMove = null
                f.lateCycle()
                assertEquals(projectiles, f.projectiles().size, label)
                assertNull(f.npc.pendingExactMove, label)
            }
        }
    }

    @Test
    fun `replacement car zoom cancels old ready phase shot and arrival edges`() {
        Fixture(6, 42).use { f ->
            PathingEntityCommon.telejump(f.npc, f.collision, CoordGrid(1302, 9500))
            f.player.coords = CoordGrid(1318, 9516)
            f.callRun("beginBurrow")
            while (f.clock.cycle < 112) {
                f.carTick()
                if (f.clock.cycle == 105) f.run.phases.stopBeamCharge()
            }
            assertNotNull(f.run.pendingCarTrample)
            val oldSchedule = assertNotNull(f.run.phases.carSchedule)
            f.worldPreInput()
            // Start the next approved zoom at this LateCycle boundary while old flags are ready.
            f.callRun("completeZoom")
            val replacement = assertNotNull(f.run.phases.carSchedule)
            assertNotSame(oldSchedule, replacement)
            assertEquals(2, f.run.phases.carZoom)
            val shots = f.projectiles().size
            val draws = f.decisionCounts()
            f.npc.pendingExactMove = null
            f.manager.tick(f.player)
            assertEquals(shots, f.projectiles().size)
            assertEquals(draws, f.decisionCounts())
            assertNull(f.npc.pendingExactMove)
            assertNull(f.run.pendingCarTrample)
            assertEquals(DoomCarPacket.DASH_WAIT, f.run.phases.carPacket)
            while (f.clock.cycle < replacement.dashStartCycle) f.carTick()
            assertNotNull(f.npc.pendingExactMove)
            assertEquals(replacement.dashStartCycle, f.clock.cycle)
        }
    }

    @Test
    fun `native secondary rock volleys retain phase deferral target sampling and tied output`() {
        for (delve in 1..9) for (defer in listOf(false, true)) {
            Fixture(delve, 42).use { f ->
                assertTrue(
                    f.registry
                        .of(f.npc)
                        .forceNextAbility(doomAbility(delve, DoomPrimaryAttack.ROCKS))
                )
                f.attack()
                val first = f.projectiles().single()
                assertEquals(1, f.run.fallingRocks.size)
                assertEquals(DoomRules.rockSets(delve) - 1, f.run.rockVolleysRemaining)
                if (defer) {
                    f.run.state = DoomState.SHIELD_TRANSITION
                    repeat(2) { f.carTick() }
                    assertEquals(1, f.projectiles().size, "delve=$delve deferred")
                    f.run.state = DoomState.MELEE_CHARGE
                }
                f.player.coords = CoordGrid(1305, 9505)
                val target =
                    DoomRockTargeting.splitTile(
                        f.player.coords,
                        DoomGeometry.centre(f.npc.coords, f.npc.size),
                    )
                var rolls = 0
                f.random.beforeRoll = { rolls++ }
                f.worldPreInput()
                assertEquals(1, f.projectiles().size, "Owned volleys do not run before input")
                f.lateCycle()
                assertEquals(DoomRules.rockSets(delve), f.projectiles().size)
                assertEquals(0, f.run.rockVolleysRemaining)
                assertEquals(
                    0,
                    rolls,
                    "Secondary volleys retain the first volley style without a fresh draw",
                )
                if (delve >= 8) {
                    val second = f.projectiles().last()
                    assertEquals(first.spotanim, second.spotanim)
                    assertEquals(target, f.run.fallingRocks.last().tile)
                    assertEquals(1, f.run.fallingRocks.last().volleyIndex)
                    assertEquals(f.clock.cycle + 9, f.run.fallingRocks.last().landCycle)
                }
                val count = f.projectiles().size
                f.manager.tick(f.player)
                assertEquals(count, f.projectiles().size, "Each queued volley launches once")
                assertEquals(listOf(0L, 0L, 0L), f.decisionCounts())
            }
        }
    }

    @Test
    fun `native shield larva queue preserves first spawn recurrence and spawn before beam`() {
        for (delve in 3..9) {
            Fixture(delve, 42, nativeRoutes = true).use { f ->
                f.beginShield()
                repeat(2) { f.carTick() }
                assertEquals(107, f.run.nextShieldLarvaCycle)
                f.run.phases.stopBeamCharge()
                while (f.clock.cycle < 106) f.carTick()
                assertTrue(f.run.larvae.isEmpty())
                f.worldPreInput()
                assertTrue(
                    f.run.larvae.isEmpty(),
                    "Owned spawning stays at the phase's LateCycle position",
                )
                val rngLarvaCounts = mutableListOf<Int>()
                f.random.beforeRoll = { rngLarvaCounts += f.run.larvae.size }
                f.lateCycle()
                assertEquals(listOf(107), f.run.larvae.values.map { it.spawnCycle })
                assertTrue(rngLarvaCounts.dropLast(1).all { it == 0 })
                assertEquals(1, rngLarvaCounts.last(), "Interval is drawn after spawning")
                val next = 107 + DoomRules.shieldLarvaIntervalRange(delve).last
                assertEquals(next, f.run.nextShieldLarvaCycle)
                f.random.beforeRoll = {}
                while (f.clock.cycle < next - 1) f.carTick()
                f.carTick()
                assertEquals(1, f.run.larvae.values.count { it.spawnCycle == next })
                assertEquals(
                    next + DoomRules.shieldLarvaIntervalRange(delve).last,
                    f.run.nextShieldLarvaCycle,
                )
            }
        }
        Fixture(4, 42).use { f ->
            f.beginShield()
            repeat(2) { f.carTick() }
            // The first spawn is overdue when the existing beam deadline becomes ready.
            f.at(f.run.beamFireCycle - 1)
            f.worldPreInput()
            assertTrue(f.run.larvae.isEmpty())
            f.onProjectile = {
                assertEquals(1, f.run.larvae.size, "Larva precedes every beam segment")
            }
            f.lateCycle()
            assertEquals(5, f.projectiles().size)
            val spawn = f.run.larvae.values.single().spawnCycle
            val next = f.run.nextShieldLarvaCycle
            assertNotNull(f.run.pendingBeamHit)
            f.at(next)
            f.callRun("processCharge")
            assertEquals(
                listOf(spawn),
                f.run.larvae.values.map { it.spawnCycle },
                "Pending native beam pauses the owner",
            )
            assertEquals(next, f.run.nextShieldLarvaCycle)
            f.callRun("cancelPendingBeam")
            f.callRun("processCharge")
            assertEquals(1, f.run.larvae.values.count { it.spawnCycle == next })
            assertEquals(next + 9, f.run.nextShieldLarvaCycle)
        }
    }

    @Test
    fun `native scheduled style hits retain accuracy at impact minus one and prayer at impact`() {
        for (style in DoomAttackStyle.entries) for (protectAtImpact in listOf(false, true)) {
            Fixture(4, 42).use { f ->
                val prayer =
                    when (style) {
                        DoomAttackStyle.MELEE -> "varbit.prayer_protectfrommelee"
                        DoomAttackStyle.RANGED -> "varbit.prayer_protectfrommissiles"
                        DoomAttackStyle.MAGIC -> "varbit.prayer_protectfrommagic"
                    }
                VarPlayerIntMapSetter.set(f.player, prayer, if (protectAtImpact) 0 else 1)
                val accuracyTicks = mutableListOf<Int>()
                val damageTicks = mutableListOf<Int>()
                f.onAccuracy = { accuracyTicks += f.clock.cycle }
                f.random.beforeRoll = { damageTicks += f.clock.cycle }
                f.scheduleStyleHit(DoomEncounterManager.PendingStyleHit(104, style, 17))
                f.run.state = DoomState.MELEE_CHARGE
                repeat(2) { f.carTick() }
                assertTrue(accuracyTicks.isEmpty())
                assertTrue(f.deferredHits().isEmpty())
                f.worldPreInput()
                assertTrue(accuracyTicks.isEmpty())
                f.lateCycle()
                assertEquals(listOf(103), accuracyTicks)
                assertEquals(listOf(103), damageTicks)
                assertTrue(f.run.pendingHits.isEmpty())
                assertEquals(17, f.deferredHits().single().second.builder.damage)
                VarPlayerIntMapSetter.set(f.player, prayer, if (protectAtImpact) 1 else 0)
                f.processPlayerHits(1)
                assertEquals(if (protectAtImpact) 99 else 82, f.player.hitpoints)
                assertTrue(f.deferredHits().isEmpty())
            }
        }
    }

    @Test
    fun `native style dispatch retains equal hit identities insertion order and guaranteed accuracy`() {
        Fixture(4, 42).use { f ->
            val first = DoomEncounterManager.PendingStyleHit(103, DoomAttackStyle.MAGIC, 11)
            val duplicate = first.copy()
            val guaranteed =
                DoomEncounterManager.PendingStyleHit(103, DoomAttackStyle.RANGED, 12, true)
            f.scheduleStyleHit(first)
            f.scheduleStyleHit(duplicate)
            f.scheduleStyleHit(guaranteed)
            val pendingDuringAccuracy = mutableListOf<List<DoomEncounterManager.PendingStyleHit>>()
            f.onAccuracy = { pendingDuringAccuracy += f.run.pendingHits.toList() }
            f.at(102)
            f.worldPreInput()
            assertTrue(f.hits().isEmpty())
            f.callRun("processPendingHits")
            assertEquals(2, pendingDuringAccuracy.size)
            assertSame(first, pendingDuringAccuracy[0][0])
            assertSame(duplicate, pendingDuringAccuracy[1][0])
            assertEquals(listOf(11, 11, 12), f.hits().map { it.second.damage })
            assertTrue(f.run.pendingHits.isEmpty())
            f.callRun("processPendingHits")
            assertEquals(3, f.hits().size)
        }
    }

    @Test
    fun `native attack owners cancel rock larva and style work on death deletion and departure`() {
        for (kind in listOf("rock", "larva", "style")) for (exit in
            listOf("death", "delete", "leave")) {
            Fixture(8, 42).use { f ->
                when (kind) {
                    "rock" -> {
                        f.registry
                            .of(f.npc)
                            .forceNextAbility(doomAbility(8, DoomPrimaryAttack.ROCKS))
                        f.attack()
                    }
                    "larva" -> {
                        f.beginShield()
                        repeat(2) { f.carTick() }
                        f.run.phases.stopBeamCharge()
                    }
                    else ->
                        f.scheduleStyleHit(
                            DoomEncounterManager.PendingStyleHit(120, DoomAttackStyle.MAGIC, 12)
                        )
                }
                assertFalse(f.worldQueues.isEmpty, "$kind $exit")
                f.at(300)
                f.worldPreInput()
                val projectiles = f.projectiles().size
                var rolls = 0
                f.random.beforeRoll = { rolls++ }
                when (exit) {
                    "death" -> {
                        // A depleted shield's death path normally starts car. Finish that phase
                        // before recording actual boss death, while retaining the old owner's work.
                        if (kind == "larva") f.run.state = DoomState.ACTIVE
                        f.npc.hitpoints = 0
                        assertSame(f.run, f.manager.beginNpcDeath(f.npc))
                    }
                    "delete" -> f.npcs.del(f.npc, Int.MAX_VALUE)
                    else -> f.events.publish(SessionStateEvent.Logout(f.player))
                }
                assertTrue(f.worldQueues.isEmpty, "$kind $exit")
                assertEquals(0, f.run.rockVolleysRemaining)
                assertTrue(f.run.pendingHits.isEmpty())
                assertEquals(Int.MAX_VALUE, f.run.nextShieldLarvaCycle)
                f.run.attacks.processRockVolleys()
                f.run.attacks.processShieldLarvae()
                f.run.attacks.processHits()
                assertEquals(projectiles, f.projectiles().size)
                assertEquals(0, rolls)
                assertTrue(f.run.larvae.isEmpty())
            }
        }
    }

    private class Fixture(
        val delve: Int,
        val seed: Long,
        active: Boolean = true,
        private val nativeRoutes: Boolean = false,
    ) : AutoCloseable {
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
        var onProjectile: (ProjAnim) -> Unit = {}
        val world =
            mock(WorldRepository::class.java) { call ->
                if (call.method.name == "projAnim") onProjectile(call.arguments[0] as ProjAnim)
                Answers.RETURNS_DEFAULTS.answer(call)
            }
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
        var onAccuracy: () -> Unit = {}
        val accuracy =
            mock(AccuracyFormulae::class.java) { call ->
                if (call.method.name.startsWith("roll")) {
                    onAccuracy()
                    true
                } else Answers.RETURNS_DEFAULTS.answer(call)
            }
        val worldQueues = WorldQueueList()
        private val worldQueueProcess = WorldQueueListProcess(worldQueues)
        private val npcAccess =
            StandardNpcAccessLauncher(
                StandardNpcAccessContextFactory(
                    random,
                    StandardNpcHitModifier(events),
                    StandardNpcHitProcessor(players, events, emptySet()),
                )
            )
        private val npcQueues = NpcQueueProcessor(events, npcAccess)
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
                registry,
                BossExtensionRegistry(),
                NvPCombat(accuracy, mock(MaxHitFormulae::class.java), world),
            )
        private val routes =
            if (nativeRoutes) RouteFactory(collision) else mock(RouteFactory::class.java)
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
                routes,
                worldQueues,
                worldQueueProcess,
            )
        val run =
            DoomEncounterManager.Run(
                player = player,
                session = session,
                generation = seed,
                delve = delve,
                boss = npc,
                startCycle = 100,
                bossRevealCycle = 100,
                startTile = player.coords,
                startingPrayerMask = 0,
                runId = "native-dsl-test",
                chainStartCycle = 100,
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
            if (active) run.state = DoomState.ACTIVE
            with(PlayerHitScript()) { scripts.startup() }
            with(NpcHitScript()) { scripts.startup() }
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

        fun worldPreInput() = worldQueueProcess.process()

        fun lateCycle() {
            at(clock.cycle + 1)
            manager.tick(player)
        }

        fun carTick() {
            npc.pendingSequence = EntitySeq.NULL
            npc.pendingExactMove = null
            worldPreInput()
            lateCycle()
        }

        fun queueBossHit(damage: Int, type: HitType = HitType.Ranged): Hit {
            var queued: Hit? = null
            npcAccess.launch(npc) {
                queued = queueHit(player, delay = 1, type = type, damage = damage)
            }
            return assertNotNull(queued)
        }

        fun processBossHits() = npcQueues.process(npc)

        fun processCurrentPlayerHits() {
            player.currentMapClock = clock.cycle
            player.processedMapClock = clock.cycle
            playerQueues.process(player)
        }

        fun queueRecoil(): Hit =
            npc.queueHit(
                source = player,
                delay = 1,
                type = HitType.Typeless,
                damage = 5,
                modifier = StandardNpcHitModifier(events),
                context = HitContext.of(HitAttribution(HitOrigin.Recoil, player.uuid)),
            )

        fun startCharge(state: DoomState) {
            when (state) {
                DoomState.MELEE_CHARGE -> callRun("beginMeleeCharge")
                DoomState.SHIELDED -> {
                    beginShield()
                    repeat(2) {
                        worldPreInput()
                        lateCycle()
                    }
                    // This fixture isolates the beam clock from the separately tested larva
                    // cadence.
                    run.attacks.cancelShieldLarvae()
                }
                DoomState.BURROWING -> {
                    run.state = state
                    callRun("transformBurrow")
                    run.phases.cancelCarPacket()
                }
                else -> error("No charge in $state")
            }
        }

        fun beginShield() {
            run.shieldAttacksRemaining = 0
            manager.tick(player)
            assertEquals(DoomState.SHIELD_TRANSITION, run.state)
        }

        fun callRun(name: String) {
            manager.javaClass.getDeclaredMethod(name, DoomEncounterManager.Run::class.java).let {
                it.isAccessible = true
                it.invoke(manager, run)
            }
        }

        fun scheduleStyleHit(pending: DoomEncounterManager.PendingStyleHit) {
            manager.javaClass
                .getDeclaredMethod(
                    "scheduleStyleHit",
                    DoomEncounterManager.Run::class.java,
                    DoomEncounterManager.PendingStyleHit::class.java,
                )
                .let {
                    it.isAccessible = true
                    it.invoke(manager, run, pending)
                }
        }

        fun finishShield() {
            manager.javaClass
                .getDeclaredMethod(
                    "finishShieldPhase",
                    DoomEncounterManager.Run::class.java,
                    Boolean::class.javaPrimitiveType,
                )
                .let {
                    it.isAccessible = true
                    it.invoke(manager, run, false)
                }
        }

        fun protectingEarthShield(): Npc {
            val shield =
                Npc(
                    assertNotNull(ServerCacheManager.getNpc(DoomAssets.EARTH_SHIELD)),
                    player.coords,
                )
            npcs.add(shield, 1000)
            instances.attachNpc(session.id, shield)
            run.shield = shield
            run.shieldDestination = null
            return shield
        }

        fun arrivingLarva(activeCycle: Int): Npc {
            val larva =
                Npc(assertNotNull(ServerCacheManager.getNpc(DoomAssets.LARVA_NEUTRAL)), npc.coords)
            npcs.add(larva, 1000)
            instances.attachNpc(session.id, larva)
            run.larvae[larva] =
                DoomEncounterManager.Larva(
                    style = null,
                    giant = false,
                    protectedStyles = emptySet(),
                    shieldPhase = false,
                    spawnCycle = clock.cycle,
                    movementCycle = clock.cycle,
                    activeCycle = activeCycle,
                    crawlStarted = true,
                    lastProgressTile = larva.coords,
                    lastProgressCycle = clock.cycle,
                    lastRouteCycle = clock.cycle,
                )
            return larva
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
            if (nativeRoutes) {
                // Native routing caches its collision map on the JUnit thread. Release that
                // fixture-owned cache alongside inline mocks instead of retaining arena maps.
                val cache = RouteFactory::class.java.getDeclaredField("threadLocalRouteFinding")
                cache.isAccessible = true
                (cache.get(routes) as ThreadLocal<*>).remove()
            }
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
