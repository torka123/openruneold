package org.rsmod.content.bosses.vorkath

import com.google.inject.AbstractModule
import com.google.inject.Guice
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Answers
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.runtime.*
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.commons.npc.combatPlayDefendAnim
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.config.refs.params
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.instances.*
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.events.NpcQueueEvents
import org.rsmod.api.npc.events.interact.AiPlayerEvents
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.player.events.PlayerMovementEvent
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.api.player.hook.PlayerTeleportHooks
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.StepFactory
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
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.PlayerQueueList
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.region.Region
import org.rsmod.game.region.zone.RegionZoneCopy
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Real revision-240 entities, hit queues, and NPC registry; scene output and sessions are recorded.
 */
@OptIn(InternalApi::class)
class VorkathEncounterManagerTest {
    @AfterEach
    fun releaseRecordedFixtures() {
        framework().clearInlineMocks()
    }

    @Test
    fun nativeSpawnRequiresCrumbleIdentityException() {
        val spawn = assertNotNull(ServerCacheManager.getNpc(8063))
        val undead = spawn.param(params.undead)
        println(
            "VORKATH_CRUMBLE_CONTRACT id=${spawn.id} internalName=${spawn.internalName} undead=$undead"
        )
        assertTrue(spawn.isType("npc.vorkath_spawn"))
        assertEquals("npc.vorkath_spawn", spawn.internalName)
        assertEquals(0, undead, "The native spawn needs its explicit Crumble Undead exception")
    }

    @Test
    fun spawnRejectsStuckLandingTilesAndClipsAlongTheCapturedBossEdge() {
        val f = VorkathFixture()
        f.player.coords = CoordGrid(2272, 4061)
        f.launchSpawn()
        f.run.pendingSpawnTile = CoordGrid(2271, 4069)
        f.advance(4)
        val spawn = assertNotNull(f.run.spawn)
        f.collision.add(2272, 4061, 0, CollisionFlag.BLOCK_NPCS)
        assertFalse(f.effects.spawns.spawnCanReach(f.run, spawn.coords))
        f.npcs.del(spawn, Int.MAX_VALUE)
        f.player.coords = CoordGrid(2264, 4068)
        var tile = CoordGrid(2272, 4069)
        for (x in 2271 downTo 2268) {
            tile = f.effects.spawns.nextSpawnStep(f.run, tile)
            assertEquals(CoordGrid(x, 4069), tile, "rsprox-3279 ticks13746..13749")
        }
        assertTrue(f.effects.spawns.spawnCanReach(f.run, CoordGrid(2272, 4069)))
    }

    @Test
    fun spawnFinalStepOverlapsFrozenPlayerButStillRespectsMapObjects() {
        val f = VorkathFixture()
        f.launchSpawn()
        f.advance(4)
        val spawn = assertNotNull(f.run.spawn)
        val target = f.player.coords
        spawn.coords = target.translate(1, 0)
        f.collision.add(target.x, target.z, target.level, CollisionFlag.BLOCK_NPCS)
        f.advance()
        val steps = StepFactory(f.collision)
        val strategy = assertNotNull(spawn.collisionStrategy)
        assertEquals(target, steps.validated(spawn, spawn.coords, target, strategy))
        f.collision.add(target.x, target.z, target.level, CollisionFlag.LOC)
        assertEquals(CoordGrid.NULL, f.effects.spawns.nextSpawnStep(f.run, spawn.coords))
        assertNotEquals(target, steps.validated(spawn, spawn.coords, target, strategy))
    }

    @Test
    fun incomingAttacksCannotReplaceVorkathCastAnimationsWithGenericDefend() {
        val f = VorkathFixture()
        assertTrue(f.debug.debugReset(f.player))
        assertTrue(f.manager.poke(f.player, f.run.boss))
        f.advance(7)
        val active = f.run.boss
        assertEquals(VorkathState.ACTIVE, f.run.state)
        assertTrue(active.suppressCombatDefendAnimation)
        active.anim(VorkathAssets.ACID_ANIM)
        active.combatPlayDefendAnim()
        assertEquals(7957, active.pendingSequence?.id)
    }

    @Test
    fun wakeUsesMappedArenaBoundsInsteadOfTheAllocationId() {
        val f = VorkathFixture()
        assertEquals(setOf(0), f.session.regionIds)
        assertTrue(f.debug.debugReset(f.player))
        assertTrue(f.manager.poke(f.player, f.run.boss))
        assertEquals(VorkathState.AWAKENING, f.run.state)
    }

    @Test
    fun wakeRejectsCoordinatesOutsideMappedArenaAndWrongPlane() {
        val f = VorkathFixture()
        assertTrue(f.debug.debugReset(f.player))
        for (tile in
            listOf(
                CoordGrid(2239, 4057),
                CoordGrid(2304, 4057),
                CoordGrid(2272, 4031),
                CoordGrid(2272, 4096),
                CoordGrid(2272, 4057, 1),
            )) {
            f.player.coords = tile
            assertFalse(f.manager.poke(f.player, f.run.boss), "$tile must not wake this instance")
            assertEquals(VorkathState.SLEEPING, f.run.state)
        }
    }

    @Test
    fun wakeRejectsAnotherSessionEvenWithMatchingMappedCoordinates() {
        val f = VorkathFixture()
        assertTrue(f.debug.debugReset(f.player))
        val oldRun = f.run
        f.instances.terminate(f.session)
        assertFalse(f.manager.poke(f.player, oldRun.boss))
        assertEquals(VorkathState.ENDED, oldRun.state)
    }

    @Test
    fun acidPoolArrivalAndBarrageUseTheSameEncounterClock() {
        val f = VorkathFixture()
        assertTrue(f.debug.debugSpecial(f.player, VorkathSpecial.ACID))
        val cast = f.clock.cycle
        val poolCount = f.run.pendingAcidPools.size
        assertTrue(poolCount in 56..57)
        assertTrue(f.run.pendingAcidPools.all { it.impactCycle == cast + 3 })
        assertTrue(f.player.coords in f.run.pendingAcidPools.map { it.tile })
        assertTrue(
            f.run.acidSafeLane.intersect(f.run.pendingAcidPools.map { it.tile }.toSet()).isEmpty()
        )
        f.player.coords = f.run.acidSafeLane.first()
        f.advance(2)
        assertTrue(f.run.acidTiles.isEmpty())
        assertEquals(0, f.run.shotsFired)
        f.advance()
        assertEquals(poolCount, f.run.acidTiles.size)
        assertEquals(poolCount, f.run.acidVisuals.size)
        assertEquals(0, f.run.shotsFired)
        f.advance()
        assertEquals(1, f.run.shotsFired)
        f.advance(24)
        assertEquals(25, f.run.shotsFired)
        assertEquals(25, f.projectiles().count { it.spotanim == 1482 })
        assertTrue(f.run.acidTiles.isEmpty())
        assertTrue(f.run.acidVisuals.isEmpty())
        f.advance(5)
        assertEquals(VorkathState.ACTIVE, f.run.state)
        assertEquals(25, f.projectiles().count { it.spotanim == 1482 })
        assertEquals(cast + 33, f.run.boss.actionDelay)
    }

    @Test
    fun spawnArrivesFourTicksAfterLaunchThenReleasesFreezeOnNativeDeathResolution() {
        val f = VorkathFixture()
        val launch = f.launchSpawn()
        assertTrue(f.player.frozen)
        assertTrue(f.player.isBound)
        assertEquals(VorkathState.ZOMBIFIED_SPAWN_SPECIAL, f.run.state)
        assertNotNull(f.manager.attackDenial(f.player, f.run.boss))
        val tile = assertNotNull(f.run.pendingSpawnTile)
        assertEquals(8, tile.chebyshevDistance(f.player.coords))
        f.advance(3)
        assertNull(f.run.spawn)
        f.advance()
        val spawn = assertNotNull(f.run.spawn)
        assertEquals(launch + 4, f.clock.cycle)
        assertEquals(38, spawn.hitpoints)
        assertEquals(tile, spawn.coords)
        spawn.hitpoints = 0
        f.manager.beginSpawnDeath(spawn)
        f.advance(2)
        assertTrue(f.player.frozen)
        f.advance()
        assertFalse(f.player.frozen)
        assertFalse(f.player.isBound)
        assertEquals(VorkathState.ACTIVE, f.run.state)
        assertNull(f.manager.attackDenial(f.player, f.run.boss))
        assertNull(f.run.spawn)
        assertTrue(spawn in f.run.retiringSpawns)
        assertTrue(f.playerHits().isEmpty())
        f.advance(2)
        assertFalse(spawn.isSlotAssigned)
        assertTrue(f.run.retiringSpawns.isEmpty())
    }

    @Test
    fun fullHealthSpawnExplosionDealsSixtyAndCombatResumesOnItsDamageTick() {
        val f = VorkathFixture()
        f.launchSpawn()
        f.advance(4)
        val spawn = assertNotNull(f.run.spawn)
        spawn.coords = f.player.coords
        f.advance()
        assertEquals(f.clock.cycle + 1, f.run.spawnExplosionCycle)
        assertFalse(f.player.frozen)
        f.advance()
        val hit = f.playerHits().single { it.damage == 60 }
        assertEquals(60, hit.damage)
        assertEquals(1, f.playerQueues().single { it.args === hit }.remainingCycles)
        assertTrue(hit.isValidNow())
        assertEquals(VorkathState.ACTIVE, f.run.state)
        assertEquals(f.clock.cycle, f.run.boss.actionDelay)
        assertNull(f.run.spawn)
        assertFalse(spawn.isSlotAssigned)
    }

    @Test
    fun walkingOntoThePlayerStartsContactDuringMovementWithoutAnExtraManagerTick() {
        val f = VorkathFixture()
        f.launchSpawn()
        f.advance(5)
        val spawn = assertNotNull(f.run.spawn)
        assertTrue(f.player.frozen)
        val contactCycle = f.clock.cycle
        // The engine advances MapClock before the NPC post-tick arrival callback.
        f.clock.tick()
        f.completeMovement(spawn, f.player.coords)
        assertFalse(f.player.frozen)
        assertEquals(contactCycle + 1, f.run.spawnExplosionCycle)
        assertTrue(f.playerHits().isEmpty())
        f.manager.tick(f.player)
        f.processWorld()
        assertEquals(60, f.playerHits().single().damage)
        assertEquals(VorkathState.ACTIVE, f.run.state)
        assertFalse(spawn.isSlotAssigned)
    }

    @Test
    fun aSpawnArrivalCallbackFromBeforeResetCannotStartAnExplosion() {
        val f = VorkathFixture()
        f.launchSpawn()
        f.advance(5)
        val spawn = assertNotNull(f.run.spawn)
        assertTrue(f.debug.debugReset(f.player))
        f.completeMovement(spawn, f.player.coords)
        assertFalse(f.player.frozen)
        assertEquals(Int.MAX_VALUE, f.run.spawnExplosionCycle)
        assertEquals(VorkathState.SLEEPING, f.run.state)
        assertTrue(f.playerHits().isEmpty())
    }

    @Test
    fun lethalSpawnHitAfterContactCancelsThePendingExplosion() {
        val f = VorkathFixture()
        f.launchSpawn()
        f.advance(4)
        val spawn = assertNotNull(f.run.spawn)
        spawn.coords = f.player.coords
        f.advance()
        spawn.hitpoints = 0
        f.manager.beginSpawnDeath(spawn)
        f.advance(3)
        assertTrue(f.playerHits().isEmpty())
        assertFalse(f.player.frozen)
        assertEquals(VorkathState.ACTIVE, f.run.state)
        assertNull(f.run.spawn)
    }

    @Test
    fun incomingDamageReductionAndImmunityFollowOnlyTheCurrentPhase() {
        val f = VorkathFixture()
        for (state in VorkathState.entries) {
            f.run.state = state
            val hit = f.incomingHit(41)
            f.manager.modifyNpcHit(f.player, f.run.boss, hit)
            val expected =
                when (state) {
                    VorkathState.ACTIVE -> 41
                    VorkathState.ACID_SPECIAL -> 20
                    else -> 0
                }
            assertEquals(expected, hit.damage, state.name)
            assertEquals(expected > 0, hit.validity?.isValid(), state.name)
        }
    }

    @Test
    fun fireballSnapshotsAtProjectileLaunchAndResetCancelsItsImpact() {
        val f = VorkathFixture()
        assertTrue(f.debug.debugAttack(f.player, VorkathStandardAttack.FIREBALL))
        f.dispatchCombat()
        assertTrue(f.projectiles().isEmpty())
        val launchTile = f.player.coords.translate(1, 0)
        f.player.coords = launchTile
        f.advance()
        val projectile = f.projectiles().single { it.spotanim == 1481 }
        assertEquals(launchTile, projectile.endCoord)
        assertEquals(0, projectile.targetIndex)
        assertEquals(1, f.run.pendingHits.size)
        assertEquals(launchTile, f.run.pendingHits.single().tile)
        assertTrue(f.debug.debugReset(f.player))
        assertTrue(f.run.pendingFireballs.isEmpty())
        assertTrue(f.run.pendingHits.isEmpty())
        assertEquals(VorkathState.SLEEPING, f.run.state)
        f.advance(8)
        assertTrue(f.playerHits().isEmpty())
        assertEquals(1, f.projectiles().count { it.spotanim == 1481 })
    }

    @Test
    fun fireballImpactQueuesForThisPlayerCycleAndResetInvalidatesTheQueuedHit() {
        val f = VorkathFixture()
        assertTrue(f.debug.debugAttack(f.player, VorkathStandardAttack.FIREBALL))
        f.dispatchCombat()
        f.advance()
        val impactCycle = f.run.pendingHits.single().impactCycle
        f.advance(impactCycle - f.clock.cycle - 1)
        assertTrue(f.playerHits().isEmpty())
        f.advance()
        val hit = f.playerHits().single()
        assertEquals(1, f.playerQueues().single { it.args === hit }.remainingCycles)
        assertTrue(hit.isValidNow())
        assertTrue(f.run.pendingHits.isEmpty())
        assertTrue(f.debug.debugReset(f.player))
        assertFalse(hit.isValidNow())
    }

    @Test
    fun abortRemovesEncounterEffectsAndInvalidatesAlreadyQueuedHits() {
        val f = VorkathFixture()
        val oldHit = f.seedMechanics()
        val spawn = assertNotNull(f.run.spawn)
        val boss = f.run.boss
        f.leave()
        assertNull(f.manager.auditRun(f.player))
        f.assertClean(oldHit)
        assertFalse(spawn.isSlotAssigned)
        assertFalse(boss.isSlotAssigned)
        verify(f.bars).onClose(f.player, boss, instant = true)
        assertNull(f.instances.sessionForPlayer(f.player))
        verify(f.locs).del(f.poolVisual, Int.MAX_VALUE)
    }

    @Test
    fun leavingTheRegionAndLosingTheSessionRunTheSameCleanup() {
        for (loseSession in listOf(false, true)) {
            val f = VorkathFixture()
            val oldHit = f.seedMechanics()
            if (loseSession) f.instances.terminate(f.session)
            else {
                val before = f.player.coords
                f.player.coords = CoordGrid(3200, 3200)
                f.events.publish(PlayerMovementEvent.CoordsMovedEvent(f.player, before))
            }
            assertNull(f.manager.auditRun(f.player))
            f.assertClean(oldHit)
        }
    }

    @Test
    fun liveAuditDisconnectExercisesNativePostTickWithoutRequestingAnAccountSave() {
        val f = VorkathFixture()
        val oldHit = f.seedMechanics()
        VorkathLiveAudit.requestAuditDisconnect(f.player)
        assertTrue(f.player.clientDisconnected.get())
        assertFalse(f.player.pendingLogout)
        f.lifecycle.onPostTick(f.player)
        assertTrue(f.player.forceDisconnect)
        assertNull(f.manager.auditRun(f.player))
        VorkathLiveAudit.finishAuditDisconnect(f.player, f.instances)
        assertFalse(f.player.clientDisconnected.get())
        assertFalse(f.player.forceDisconnect)
        assertFalse(f.player.pendingLogout)
        assertFalse(f.player.loggingOut)
        f.instances.tickReclaim(f.clock.cycle + 1)
        f.assertClean(oldHit)
    }

    @Test
    fun liveAuditLogoutWaitsForNativeDeferredReclaimWithoutPublishingAnAccountEvent() {
        val f = VorkathFixture()
        val oldHit = f.seedMechanics()
        VorkathLiveAudit.beginAuditLogout(f.player, f.instances, f.clock.cycle)
        assertNull(f.manager.auditRun(f.player))
        assertNull(f.instances.sessionForPlayer(f.player))
        assertNotNull(f.instances.sessionForId(f.session.id), "Native reclaim is deferred")
        assertTrue(f.run.boss.isSlotAssigned)
        assertFalse(oldHit.validity?.isValid() == true)
        assertFalse(f.player.pendingLogout)
        assertFalse(f.player.loggingOut)
        f.instances.tickReclaim(f.clock.cycle + 1)
        assertNull(f.instances.sessionForId(f.session.id))
        f.assertClean(oldHit)
    }

    @Test
    fun logoutAndDisconnectClearEffectsAndRouteToTheCorrectInstanceCleanup() {
        for (loggingOut in listOf(false, true)) {
            val f = VorkathFixture()
            val oldHit = f.seedMechanics()
            if (loggingOut) f.events.publish(SessionStateEvent.Logout(f.player))
            else {
                f.player.clientDisconnected.set(true)
                f.lifecycle.onPostTick(f.player)
            }
            assertNull(f.manager.auditRun(f.player))
            assertNull(
                f.player.lootDropDuration,
                "Restore before the native logout save, not deferred map destruction",
            )
            f.instances.tickReclaim(f.clock.cycle + 1)
            f.assertClean(oldHit)
            assertEquals(VORKATH_OUTSIDE, f.player.coords)
        }
    }

    @Test
    fun playerDeathClearsHazardsButRetainsMembershipForTheExistingDeathStorageHook() {
        val f = VorkathFixture()
        val oldHit = f.seedMechanics()
        f.player.statMap.setCurrentLevel("stat.hitpoints", 0)
        f.manager.tick(f.player)
        assertSame(f.run, f.manager.auditRun(f.player))
        assertEquals(VorkathState.ENDED, f.run.state)
        f.assertClean(oldHit)
        verify(f.bars).onClose(f.player, f.run.boss, instant = true)
        f.instances.handleDeath(f.player, f.clock.cycle)
        assertNull(f.manager.auditRun(f.player))
    }

    @Test
    fun bossDeathClearsSpecialEffectsAndRejectsDuplicateDeathAndStaleDamage() {
        val f = VorkathFixture()
        val oldHit = f.seedMechanics()
        f.run.rewardEligible = false
        assertSame(f.run, f.manager.beginBossDeath(f.run.boss))
        assertEquals(VorkathState.DYING, f.run.state)
        f.assertClean(oldHit)
        assertNull(f.manager.beginBossDeath(f.run.boss))
        assertTrue(f.manager.canFinishDeath(f.run, f.run.boss))
        f.leave()
        assertFalse(f.manager.canFinishDeath(f.run, f.run.boss))
    }

    @Test
    fun eligibleDeathUpdatesTheNativeCollectionCounterExactlyOnce() {
        val f = VorkathFixture()
        org.rsmod.api.player.vars.VarPlayerIntMapSetter.set(
            f.player,
            "varp.total_vorkath_kills",
            49,
        )
        f.run.rewardEligible = true
        assertSame(f.run, f.manager.beginBossDeath(f.run.boss))
        assertEquals(50, f.player.vars["varp.total_vorkath_kills"])
        assertEquals(50, f.run.killcount)
        assertNull(f.manager.beginBossDeath(f.run.boss))
        assertEquals(50, f.player.vars["varp.total_vorkath_kills"])
    }

    @Test
    fun lethalBossDamageCancelsDueEffectsBeforeTheLaterDeathQueueRuns() {
        val f = VorkathFixture()
        val oldHit = f.seedMechanics()
        f.run.rewardEligible = false
        f.run.boss.hitpoints = 0
        f.advance()
        assertSame(f.run, f.manager.auditRun(f.player))
        f.assertClean(oldHit)
        assertTrue(f.projectiles().isEmpty())
        assertTrue(f.playerHits().isEmpty())
        assertSame(f.run, f.manager.beginBossDeath(f.run.boss))
        assertEquals(VorkathState.DYING, f.run.state)
    }

    @Test
    fun removedBossAbortsInsteadOfLaunchingPendingEffects() {
        val f = VorkathFixture()
        val oldHit = f.seedMechanics()
        f.npcs.del(f.run.boss, Int.MAX_VALUE)
        f.advance()
        assertNull(f.manager.auditRun(f.player))
        f.assertClean(oldHit)
        assertTrue(f.projectiles().isEmpty())
        assertTrue(f.playerHits().isEmpty())
    }

    @Test
    fun resetAndReawakeningStartAFreshBossWithoutOldSpecialState() {
        val f = VorkathFixture()
        val oldHit = f.seedMechanics()
        assertTrue(f.debug.debugReset(f.player))
        f.assertClean(oldHit)
        assertEquals(8059, f.run.boss.id)
        assertEquals(VorkathState.SLEEPING, f.run.state)
        assertEquals(0, f.run.standardAttacks)
        assertTrue(f.debug.debugWake(f.player))
        f.advance(7)
        assertEquals(8061, f.run.boss.id)
        assertEquals(750, f.run.boss.hitpoints)
        assertEquals(VorkathState.ACTIVE, f.run.state)
        assertEquals(0, f.run.standardAttacks)
        assertEquals(0, f.run.shotsFired)
        assertTrue(f.run.pendingHits.isEmpty())
        assertFalse(oldHit.validity?.isValid() == true)
    }

    @Test
    fun nativeDslAlternatesSpecialsAndMatchesTheOldOrdinaryCadence() {
        val f = VorkathFixture()
        f.run.nextSpecial = VorkathSpecial.ACID
        val oracle =
            VorkathTimeline(VorkathSpecial.ACID).also {
                it.wake(93)
                it.activate(100)
            }
        val expected = mutableListOf<Int>()
        repeat(31) {
            f.advance()
            f.dispatchCombat()
            if (oracle.attackDue(f.clock.cycle)) {
                expected += f.clock.cycle
                if (oracle.standardAttacks == 6) oracle.beginSpecial(f.clock.cycle)
                else oracle.standardLaunched(f.clock.cycle)
            }
        }
        assertEquals(listOf(101, 106, 111, 116, 121, 126, 131), expected)
        assertEquals(expected, f.run.combat.recentAbilityCommitTicks())
        assertEquals(VorkathState.ACID_SPECIAL, f.run.state)
        repeat(33) {
            f.advance()
            f.dispatchCombat()
        }
        assertEquals(
            (135..159).toList(),
            f.projectileTicks.filter { it.second.spotanim == 1482 }.map { it.first },
        )
        assertEquals(VorkathState.ACTIVE, f.run.state)
        assertEquals(164, f.run.combat.lastAbilityTick)
        assertEquals(1, f.run.standardAttacks)
        repeat(30) {
            f.advance()
            f.dispatchCombat()
        }
        assertEquals(VorkathState.ZOMBIFIED_SPAWN_SPECIAL, f.run.state)
        assertEquals(
            listOf(164, 169, 174, 179, 184, 189, 194),
            f.run.combat.recentAbilityCommitTicks(7),
        )
        assertEquals(VorkathSpecial.ACID, f.run.nextSpecial)
        f.leave()
        assertEquals(0, f.pendingWorldEffects)
        assertTrue(f.instances.npcsForInstance(f.session.id).isEmpty())
    }

    @Test
    fun nativeTeardownCancelsAwakeningAndEverySpecialWithoutAStaleCallback() {
        for (phase in listOf("wake", "acid", "spawn")) {
            val f = VorkathFixture()
            when (phase) {
                "wake" -> {
                    f.debug.debugReset(f.player)
                    f.manager.poke(f.player, f.run.boss)
                }
                "acid" -> f.debug.debugSpecial(f.player, VorkathSpecial.ACID)
                "spawn" -> f.debug.debugSpecial(f.player, VorkathSpecial.ZOMBIFIED_SPAWN)
            }
            assertTrue(f.pendingWorldEffects > 0)
            val projectileCount = f.projectiles().size
            f.leave()
            assertEquals(0, f.pendingWorldEffects)
            f.advance(70)
            assertEquals(projectileCount, f.projectiles().size)
            assertTrue(f.instances.npcsForInstance(f.session.id).isEmpty())
            assertNull(f.session.stateOrNull(f.manager.stateKey))
            framework().clearInlineMocks()
        }
    }

    @Test
    fun twoConsecutiveCompletionsRetainOnlyTheCurrentSleepingBoss() {
        val f = VorkathFixture()
        repeat(2) {
            val boss = f.run.boss
            f.run.rewardEligible = false
            f.debug.debugSpecial(f.player, VorkathSpecial.ACID)
            boss.hitpoints = 0
            assertSame(f.run, f.manager.beginBossDeath(boss))
            assertNull(f.manager.beginBossDeath(boss))
            f.manager.finishBossDeath(boss)
            assertEquals(VorkathState.SLEEPING, f.run.state)
            assertEquals(listOf(f.run.boss), f.instances.npcsForInstance(f.session.id))
            assertEquals(0, f.pendingWorldEffects)
            assertTrue(f.run.pendingHits.isEmpty())
            assertFalse(boss.isSlotAssigned)
            f.manager.poke(f.player, f.run.boss)
            f.advance(7)
        }
    }

    @Test
    fun nativeNpcDeathQueueKeepsTheCorpseForSixTicksThenResets() {
        val f = VorkathFixture()
        f.run.rewardEligible = false
        val boss = f.run.boss
        f.killNative()
        assertEquals(VorkathState.DYING, f.run.state)
        repeat(5) {
            f.advance()
            f.resumeNpc(boss)
            assertTrue(boss.isSlotAssigned)
            assertSame(boss, f.run.boss)
        }
        f.advance()
        f.resumeNpc(boss)
        assertFalse(boss.isSlotAssigned)
        assertEquals(VorkathState.SLEEPING, f.run.state)
        assertEquals(listOf(f.run.boss), f.instances.npcsForInstance(f.session.id))
    }

    @Test
    fun oneInstanceLeavingCannotRetireAnotherPlayersBossOrFreeze() {
        val f = VorkathFixture()
        val second =
            Player().apply {
                uuid = 2L
                observerUUID = 2L
                slotId = 2
                assignUid()
                coords = VORKATH_OUTSIDE
                statMap.setBaseLevel("stat.hitpoints", 99)
                statMap.setCurrentLevel("stat.hitpoints", 99)
                inv = invMap.getOrPut("inv.inv")
                worn = invMap.getOrPut("inv.worn")
            }
        f.players[2] = second
        f.enter(second)
        val other = assertNotNull(f.manager.auditRun(second))
        assertNotEquals(f.session.id, other.session.id)
        assertFalse(f.manager.isOwnedBy(f.player, other.boss))
        assertNotNull(f.manager.attackDenial(f.player, other.boss))
        assertFalse(f.manager.poke(f.player, other.boss))
        second.coords = other.boss.coords.translate(3, -5)
        assertTrue(f.manager.poke(second, other.boss))
        f.advance(7)
        f.debug.debugSpecial(second, VorkathSpecial.ZOMBIFIED_SPAWN)
        f.advance(3)
        assertTrue(second.frozen)
        f.leave()
        assertSame(other.session, f.instances.sessionForPlayer(second))
        assertTrue(other.boss.isSlotAssigned)
        assertTrue(second.frozen)
        f.instances.terminate(other.session)
        assertFalse(second.frozen)
        assertEquals(0, f.pendingWorldEffects)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}

@OptIn(InternalApi::class)
private class VorkathFixture {
    val clock = MapClock(93)
    val collision = CollisionFlagMap()
    private val npcList = NpcList()
    val events = EventBus()
    val players = PlayerList()
    val npcs = NpcRepository(clock, NpcRegistry(npcList, collision, events), npcList)
    private var allocation = 0
    val regions =
        mock(RegionRepository::class.java) { call ->
            when (call.method.name) {
                "add" -> {
                    val id = allocation++
                    val base = CoordGrid(2240 + id * 64, 4032)
                    Region(base, base.translate(64, 64), id, id).also { region ->
                        for (x in 0..7) for (z in 0..7) {
                            val tile = base.translate(x * 8, z * 8)
                            region.registerZone(
                                RegionZoneCopy(ZoneKey(280 + x, 504 + z, 0), 0, null),
                                ZoneKey.from(tile),
                            )
                            collision.allocateIfAbsent(tile.x, tile.z, 0)
                        }
                    }
                }
                "isValid" -> true
                else -> Answers.RETURNS_DEFAULTS.answer(call)
            }
        }
    val instances =
        InstanceManager(regions, npcs, players, events, InstanceAreaResolver(), clock, collision)
    val bars = mock(BossHpBarScript::class.java)
    val projectileTicks = mutableListOf<Pair<Int, ProjAnim>>()
    val world =
        mock(WorldRepository::class.java) { call ->
            if (call.method.name == "projAnim")
                projectileTicks += clock.cycle to call.getArgument<ProjAnim>(0)
            Answers.RETURNS_DEFAULTS.answer(call)
        }
    val locs =
        mock(LocRepository::class.java) { call ->
            if (call.method.returnType == LocInfo::class.java) {
                LocInfo(2, CoordGrid(call.getArgument<Int>(0)), LocEntity(32000, 10, 0))
            } else Answers.RETURNS_DEFAULTS.answer(call)
        }
    val player =
        Player().apply {
            uuid = 1L
            observerUUID = 1L
            slotId = 1
            assignUid()
            coords = CoordGrid(2272, 4057)
            for (name in
                listOf("hitpoints", "prayer", "attack", "strength", "defence", "ranged", "magic")) {
                statMap.setBaseLevel("stat.$name", 99)
                statMap.setCurrentLevel("stat.$name", 99)
            }
            inv = invMap.getOrPut("inv.inv")
            worn = invMap.getOrPut("inv.worn")
        }
    lateinit var session: InstanceSession
    lateinit var run: VorkathArenaState
    private val boss
        get() = run.boss

    private val random = DefaultGameRandom(42L)
    private val queues = WorldQueueList()
    val pendingWorldEffects
        get() = queues.size

    private val registry = EncounterRegistry(random)
    private val accuracy =
        mock(AccuracyFormulae::class.java) { call ->
            if (call.method.name.startsWith("roll")) true else Answers.RETURNS_DEFAULTS.answer(call)
        }
    private val deps =
        BossDeps(
            random,
            accuracy,
            world,
            npcs,
            players,
            clock,
            queues,
            collision,
            registry,
            BossExtensionRegistry(),
            NvPCombat(accuracy, mock(MaxHitFormulae::class.java), world),
        )

    val interactions = mock(AiPlayerInteractions::class.java)
    val effects =
        VorkathArenaEffects(
            clock,
            instances,
            npcs,
            locs,
            interactions,
            collision,
            random,
            deps,
            world,
        )
    val manager =
        VorkathEncounterManager(clock, instances, npcs, interactions, random, deps, bars, effects)
    val debug =
        VorkathDiagnostics(
            manager,
            effects,
            clock,
            interactions,
            mock(StandardNpcHitModifier::class.java),
        )
    val poolVisual = LocInfo(2, player.coords, LocEntity(32000, 10, 0))

    private val arena =
        VorkathWorld(
            BossInstanceRegistry(),
            mock(ProtectedAccessLauncher::class.java),
            manager,
            mock(VorkathDeathStorage::class.java),
            mock(VorkathAccessPolicy::class.java),
            debug,
        )
    val lifecycle: PlayerPostTickHook
    private val accessContext =
        mock(ProtectedAccessContext::class.java) { call ->
            when (call.method.name) {
                "getCollision" -> collision
                "getEventBus" -> events
                "getTeleportHooks" -> PlayerTeleportHooks.EMPTY
                "getTeleportValidator" -> PlayerTeleportValidator(emptySet())
                "getAreaChecker" -> mock(call.method.returnType)
                else -> Answers.RETURNS_DEFAULTS.answer(call)
            }
        }

    init {
        players[1] = player
        val injector =
            Guice.createInjector(
                object : AbstractModule() {
                    override fun configure() {
                        bind(InstanceManager::class.java).toInstance(instances)
                        bind(MapClock::class.java).toInstance(clock)
                    }
                }
            )
        injector.injectMembers(arena)
        val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        with(arena) { context.startup() }
        with(VorkathCombat(manager, mock(NpcDeath::class.java), players, deps)) {
            context.startup()
        }
        val plugin =
            injector.getInstance(
                Class.forName("org.rsmod.api.instances.hook.InstanceLifecycleScript")
                    .asSubclass(PluginScript::class.java)
            )
        with(plugin) { context.startup() }
        lifecycle = plugin as PlayerPostTickHook
        player.launch {
            with(arena) { ProtectedAccess(player, this@launch, accessContext).enterVorkath(32) }
        }
        session = assertNotNull(instances.sessionForPlayer(player))
        run = assertNotNull(manager.auditRun(player))
        player.coords = CoordGrid(2272, 4057)
        assertTrue(manager.poke(player, run.boss))
        advance(7)
        assertEquals(VorkathState.ACTIVE, run.state)
    }

    fun leave() {
        player.launch {
            with(arena) { ProtectedAccess(player, this@launch, accessContext).leaveVorkath(32) }
        }
    }

    fun enter(target: Player) {
        target.launch {
            with(arena) { ProtectedAccess(target, this@launch, accessContext).enterVorkath(32) }
        }
    }

    fun advance(ticks: Int = 1) =
        repeat(ticks) {
            clock.tick()
            player.currentMapClock = clock.cycle
            manager.tick(player)
            processWorld()
        }

    fun processWorld() = WorldQueueListProcess(queues).process()

    fun dispatchCombat() {
        val npc = run.boss
        npc.currentMapClock = clock.cycle
        npc.processedMapClock = clock.cycle
        npc.launch {
            val context =
                StandardNpcAccessContext(
                    { random },
                    { StandardNpcHitModifier(events) },
                    { StandardNpcHitProcessor(players, events, emptySet()) },
                )
            events.publish(StandardNpcAccess(npc, this, context), AiPlayerEvents.Ap2(player, npc))
        }
    }

    fun killNative() {
        val npc = run.boss
        npc.hitpoints = 0
        npc.currentMapClock = clock.cycle
        npc.processedMapClock = clock.cycle
        npc.launch {
            val context =
                StandardNpcAccessContext(
                    { random },
                    { StandardNpcHitModifier(events) },
                    { StandardNpcHitProcessor(players, events, emptySet()) },
                )
            events.publish(
                StandardNpcAccess(npc, this, context),
                NpcQueueEvents.Type(npc, null, "queue.death".asRSCM(RSCMType.QUEUE)),
            )
        }
    }

    fun resumeNpc(npc: Npc) {
        npc.currentMapClock = clock.cycle
        npc.processedMapClock = clock.cycle
        npc.advanceActiveCoroutine()
    }

    fun completeMovement(npc: Npc, destination: CoordGrid) {
        npc.coords = destination
        npc.routeDestination.clear()
        npc.processArrivalAction()
    }

    fun launchSpawn(): Int {
        assertTrue(debug.debugSpecial(player, VorkathSpecial.ZOMBIFIED_SPAWN))
        val launch = run.spawnLaunchCycle
        advance(launch - clock.cycle)
        assertEquals(launch + 4, run.spawnArrivalCycle)
        return launch
    }

    fun projectiles(): List<ProjAnim> =
        mockingDetails(world)
            .invocations
            .filter { it.method.name == "projAnim" }
            .map { it.arguments[0] as ProjAnim }

    fun playerHits(): List<Hit> = playerQueues().mapNotNull { it.args as? Hit }

    fun playerQueues(): List<PlayerQueueList.Queue> {
        val queues = mutableListOf<PlayerQueueList.Queue>()
        val iterator = player.queueList.iterator()
        if (iterator != null)
            while (iterator.hasNext()) {
                queues += iterator.next()
            }
        return queues
    }

    fun incomingHit(damage: Int) =
        HitBuilder(
            type = HitType.Typeless,
            damage = damage,
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

    fun seedMechanics(): HitBuilder {
        val hit = incomingHit(40)
        manager.modifyNpcHit(player, boss, hit)
        assertTrue(hit.validity?.isValid() == true)
        run.state = VorkathState.ZOMBIFIED_SPAWN_SPECIAL
        run.acidTiles += player.coords
        run.acidVisuals += poolVisual
        run.acidSafeLane += player.coords.translate(1, 0)
        run.pendingAcidPools += PendingAcidPool(clock.cycle + 3, player.coords)
        run.pendingHits +=
            PendingTileHit(clock.cycle + 5, player.coords, 0, 121, VorkathTileAttack.FIREBALL)
        run.pendingFireballs += PendingFireball(clock.cycle + 1)
        run.pendingHeals += (clock.cycle + 1) to 10
        run.pendingSpawnTile = player.coords.translate(8, 0)
        val spawn =
            Npc(requireNotNull(ServerCacheManager.getNpc(8063)), player.coords.translate(2, 0))
        npcs.add(spawn, Int.MAX_VALUE)
        spawn.assignSpawnOwner(player, clock.cycle)
        run.spawn = spawn
        player.applyBind(100)
        player.frozen = true
        run.ownsFreeze = true
        return hit
    }

    fun assertClean(oldHit: HitBuilder) {
        assertTrue(run.pendingHits.isEmpty())
        assertTrue(run.pendingFireballs.isEmpty())
        assertTrue(run.pendingHeals.isEmpty())
        assertTrue(run.pendingAcidPools.isEmpty())
        assertTrue(run.acidTiles.isEmpty())
        assertTrue(run.acidVisuals.isEmpty())
        assertTrue(run.acidSafeLane.isEmpty())
        assertTrue(run.retiringSpawns.isEmpty())
        assertNull(run.spawn)
        assertNull(run.pendingSpawnTile)
        assertFalse(run.ownsFreeze)
        assertFalse(player.frozen)
        assertFalse(player.isBound)
        assertEquals(Int.MAX_VALUE, run.spawnArrivalCycle)
        assertEquals(Int.MAX_VALUE, run.spawnLaunchCycle)
        assertEquals(Int.MAX_VALUE, run.spawnExplosionCycle)
        assertEquals(Int.MAX_VALUE, run.spawnDeathCycle)
        assertEquals(Int.MAX_VALUE, run.freezeCycle)
        assertFalse(oldHit.validity?.isValid() == true)
    }
}
