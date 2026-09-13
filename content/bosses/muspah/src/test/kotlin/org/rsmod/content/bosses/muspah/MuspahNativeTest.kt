package org.rsmod.content.bosses.muspah

import com.google.inject.AbstractModule
import com.google.inject.Guice
import dev.openrune.ParamMap
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.toml.rsconfig.decodeRuneScape
import dev.openrune.toml.rsconfig.decodeRuneScapeBlocks
import dev.openrune.toml.rsconfig.rsconfig
import dev.openrune.toml.tomlMapper
import dev.openrune.types.NpcServerType
import java.io.File
import kotlin.reflect.typeOf
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.runtime.*
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.commons.CombatCorruption
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.death.NpcDeathDropHook
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.death.plugin.NpcDeathScript
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.access.StandardNpcAccessContextFactory
import org.rsmod.api.npc.access.StandardNpcAccessLauncher
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.events.NpcQueueEvents
import org.rsmod.api.npc.events.interact.AiPlayerEvents
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.events.PlayerMovementEvent
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.api.player.hook.PlayerTeleportHooks
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryResult
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.repo.EntityLifecycleProcess
import org.rsmod.api.repo.NpcRevealProcessor
import org.rsmod.api.repo.controller.ControllerRepository
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.content.generic.killcount.KillcountNpcKillHook
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
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

/** Characterizes the existing draft through native registration, not independent OSRS proof. */
@OptIn(InternalApi::class)
class MuspahNativeTest {
    @Test
    fun `natural crevice and both exits use registered protected instance interactions`() {
        for (exit in listOf(MuspahAssets.EXIT_ID, MuspahAssets.ESCAPE_ID)) {
            val f = MuspahFixture()
            f.locOp(MuspahAssets.ENTRANCE_ID)
            f.until(105)
            assertNotNull(f.instances.sessionForPlayer(f.player))
            assertEquals(1, f.npcList.count())
            f.locOp(exit)
            f.until(110)
            assertNull(f.instances.sessionForPlayer(f.player))
            assertTrue(f.npcList.none())
            assertEquals(MUSPAH_OUTSIDE, f.player.coords)
        }
    }

    @Test
    fun `two native rooms retain independent owners clocks and cleanup`() {
        val f = MuspahFixture()
        f.start()
        val second = f.newPlayer(2)
        f.players[2] = second
        f.start(second)
        val one = assertNotNull(f.instances.muspahState(f.player))
        val two = assertNotNull(f.instances.muspahState(second))
        assertNotEquals(one.session.id, two.session.id)
        assertNotEquals(one.boss.coords, two.boss.coords)
        assertFalse(f.encounters.canAttack(one.boss, second))
        f.until(104)
        f.damage(213)
        f.until(106)
        assertEquals(MuspahState.LIGHTNING_CLOUDS, one.state)
        assertEquals(MuspahState.RANGED, two.state)
        f.leave()
        assertEquals(listOf(two.boss), f.npcList.toList())
        assertTrue(one.encounter.isEnded)
        assertFalse(two.encounter.isEnded)
        assertSame(two, f.instances.muspahState(second))
        f.until(150)
        assertEquals(MuspahState.RANGED, two.state)
        assertTrue(f.activeLocs.isEmpty())
        assertEquals(0, f.deps.worldQueues.size)
        f.leave(second)
        assertTrue(f.npcList.none())
    }

    @Test
    fun `lightning cloud does not hit on its birth tick`() {
        val f = MuspahFixture()
        f.start()
        f.until(104)
        f.damage(213)
        val run = assertNotNull(f.instances.muspahState(f.player))
        f.until(106)
        assertEquals(f.player.coords, run.clouds.single().tile)
        assertTrue(f.hits().isEmpty())
        f.player.coords = f.local(40, 34)
        f.until(107)
        assertEquals(f.player.coords, run.clouds.single().tile)
        assertEquals(20, f.hits().single().second.damage)
    }

    @Test
    fun `declared phases progress out of melee reach without post tick phase polling`() {
        val f = MuspahFixture(melee = true)
        f.start()
        f.player.coords = f.local(42, 45)
        f.until(104, postTick = false)
        val run = assertNotNull(f.instances.muspahState(f.player))
        assertEquals(MuspahState.MELEE, run.state)
        assertEquals(1, f.boss.apRangeOverride)
        f.damage(213)
        f.until(137, postTick = false)
        assertEquals(MuspahState.HOMING_SPIKES, run.state)
        f.until(138, postTick = false)
        assertEquals(MuspahState.MELEE, run.state)
        assertTrue(run.movingSpikes.isEmpty())
        assertEquals(1, f.boss.apRangeOverride)
    }

    @Test
    fun `shield interruption prevents all remaining lightning movement and old phase return`() {
        val f = MuspahFixture()
        f.start()
        f.until(104)
        f.damage(213)
        f.until(106)
        f.damage(511)
        val run = assertNotNull(f.instances.muspahState(f.player))
        val center = f.boss.coords
        f.until(110)
        assertEquals(MuspahState.SHOCKWAVE, run.state)
        assertEquals(80, f.hits().last().second.damage)
        f.until(111)
        assertEquals(MuspahState.PRAYER_SHIELD, run.state)
        f.until(150)
        assertEquals(center, f.boss.coords)
        assertEquals(MuspahState.PRAYER_SHIELD, run.state)
        assertEquals(0, f.deps.worldQueues.size)
        assertTrue(run.clouds.isEmpty())
    }

    @Test
    fun `same tick lethal shockwave races retain the characterized skip policy`() {
        for (extraHits in listOf(1, 2)) {
            val f = MuspahFixture()
            f.start()
            f.until(104)
            f.boss.hitpoints = 128
            f.damage(2)
            repeat(extraHits) { f.damage(500) }
            assertEquals(1, f.boss.hitpoints)
            f.until(109)
            val run = assertNotNull(f.instances.muspahState(f.player))
            assertEquals(
                if (extraHits == 1) MuspahState.FINAL_STAND else MuspahState.PRAYER_SHIELD,
                run.state,
            )
        }
    }

    @Test
    fun `passive shield break retains the draft spike expansion without counting a final shot`() {
        val f = MuspahFixture()
        f.start()
        f.until(104)
        f.boss.hitpoints = 128
        f.damage(2)
        f.until(111)
        val run = assertNotNull(f.instances.muspahState(f.player))
        run.shieldPoints = 2
        f.attack()
        assertEquals(MuspahState.FINAL_STAND, run.state)
        assertEquals(1, f.projectiles.size)
        assertEquals(2, run.warningSpikes.size)
    }

    @Test
    fun `final spikes accompany every fourth committed shot without replacing normal combat`() {
        val f = MuspahFixture()
        f.start()
        f.until(104)
        f.boss.hitpoints = 128
        f.damage(2)
        f.until(109)
        VarPlayerIntMapSetter.set(f.player, "varbit.prayer_smite", 1)
        repeat(3) { f.damage(100) }
        val run = assertNotNull(f.instances.muspahState(f.player))
        repeat(8) { index ->
            f.until(112 + 5 * index)
            f.player.coords = f.local(40, 27 + index)
            assertTrue(run.warningSpikes.isEmpty())
            f.attack()
            assertEquals(index + 1, f.projectiles.size)
            assertEquals((index + 1) % 4 == 0, run.warningSpikes.isNotEmpty())
            val warnings = run.warningSpikes.toList()
            repeat(3) { f.attack() }
            assertEquals(index + 1, f.projectiles.size)
            assertEquals(warnings, run.warningSpikes)
            assertEquals(117 + 5 * index, f.boss.actionDelay)
        }
    }

    @Test
    fun `source KC metadata uses the native kill hook once across two completions`() {
        val f = MuspahFixture()
        f.start()
        val blocks =
            tomlMapper { rsconfig { enableConstantProvider() } }
                .decodeRuneScapeBlocks(File(".data/raw-cache/server/npcs.toml").toPath())
        val mapper = tomlMapper {}
        val overlay =
            assertNotNull(
                blocks
                    .asSequence()
                    .filter { it.name == "npc" }
                    .map {
                        mapper.decodeRuneScape(typeOf<List<NpcServerType>>(), it.map.properties)
                            as NpcServerType
                    }
                    .firstOrNull { it.id == 12077 }
            )
        val definition = f.boss.type
        val previous = definition.paramMap
        try {
            definition.paramMap =
                ParamMap(definition.paramsRaw.orEmpty() + overlay.paramsRaw.orEmpty())
            repeat(2) { round ->
                f.until(f.clock.cycle + 4)
                f.damage(1)
                f.boss.hitpoints = 0
                f.killQueue()
                f.until(f.clock.cycle + 15)
                assertEquals(round + 1, f.kills.size)
                assertEquals(round + 1, f.player.vars["varp.total_muspah_kills"])
                val state = assertNotNull(f.instances.muspahState(f.player))
                f.encounters.finishBossDeath(state)
                assertEquals(round + 1, f.player.vars["varp.total_muspah_kills"])
                f.until(f.clock.cycle + 60)
                assertEquals(1, f.npcList.count())
            }
        } finally {
            definition.paramMap = previous
        }
    }

    @Test
    fun `crack forms on tick two and its queued spike hit is retired on departure`() {
        val f = MuspahFixture()
        f.start()
        f.until(104)
        val tile = f.player.coords
        f.damage(100)
        f.until(105)
        assertEquals(MuspahAssets.SPIKE_PRE.asRSCM(RSCMType.LOC), f.activeLocs[tile]?.id)
        f.until(106)
        assertEquals(MuspahAssets.SPIKE.asRSCM(RSCMType.LOC), f.activeLocs[tile]?.id)
        val hit = f.hits().single().second
        assertTrue(hit.context.isValid())
        val run = assertNotNull(f.instances.muspahState(f.player))
        assertTrue(run.warningSpikes.isEmpty())
        f.leave()
        assertFalse(hit.context.isValid())
        f.until(120)
        assertTrue(f.activeLocs.isEmpty())
    }

    @Test
    fun `death cancels a warning before formation and native repeat resets round fields`() {
        val f = MuspahFixture()
        f.start()
        f.until(104)
        val run = assertNotNull(f.instances.muspahState(f.player))
        f.boss.hitpoints = 128
        f.damage(2)
        f.until(109)
        VarPlayerIntMapSetter.set(f.player, "varbit.prayer_smite", 1)
        repeat(3) { f.damage(100) }
        assertTrue(run.warningSpikes.isNotEmpty())
        run.lastCloudHitCycle = 109
        run.stationaryMeleeAttacks = 9
        f.damage(200)
        f.killQueue()
        f.until(130)
        assertTrue(f.activeLocs.isEmpty())
        assertTrue(run.warningSpikes.isEmpty())
        f.until(190)
        assertSame(run, f.instances.muspahState(f.player))
        assertEquals(Int.MIN_VALUE, run.lastCloudHitCycle)
        assertEquals(0, run.stationaryMeleeAttacks)
        assertEquals(0, run.encounter.phaseAbilityCount)
        assertTrue(run.shieldDrainSources.isEmpty())
        assertFalse(run.shieldSkipPending)
        assertEquals(850, f.boss.hitpoints)
        assertEquals(listOf(f.boss), f.instances.npcsForInstance(run.session.id))
        f.leave()
        f.start()
        assertNotSame(run, f.instances.muspahState(f.player))
        assertTrue(f.activeLocs.isEmpty())
        assertEquals(1, f.npcList.count())
    }

    @Test
    fun `native departure immediately retires encounter membership and hazards`() {
        for (reason in listOf("teleport", "logout", "disconnect", "death")) {
            val f = MuspahFixture(melee = true)
            f.start()
            f.until(104)
            f.damage(213)
            assertTrue(f.activeLocs.isNotEmpty())
            f.end(reason)
            f.instances.tickReclaim(f.clock.cycle + 1)
            assertFalse(f.encounters.isActive(f.player), reason)
            assertTrue(f.activeLocs.isEmpty(), reason)
            assertTrue(f.npcList.none(), reason)
        }
    }

    @Test
    fun `native death and repeat retain one actor instead of spawning a duplicate`() {
        val f = MuspahFixture()
        f.start()
        f.until(104)
        f.boss.hitpoints = 128
        f.damage(2)
        f.until(109)
        VarPlayerIntMapSetter.set(f.player, "varbit.prayer_smite", 1)
        repeat(3) { f.damage(100) }
        val original = f.boss
        f.damage(200)
        f.killQueue()
        f.until(130)
        assertEquals(1, f.kills.size)
        assertFalse(original.isVisible)
        f.until(190)
        assertEquals(listOf(original), f.npcList.toList())
        assertEquals(850, original.hitpoints)
        assertEquals(1, f.kills.size)
    }

    @Test
    fun `first incoming hit cannot relock an activated melee form before it reaches the player`() {
        val f = MuspahFixture(melee = true)
        f.start()
        f.until(104)
        assertFalse(f.boss.movementLocked)
        f.damage(1)
        assertFalse(f.boss.movementLocked)
        assertEquals(849, f.boss.hitpoints)
    }

    @Test
    fun `every native weighted selection boundary preserves ordinary and reinforced styles`() {
        for (reinforced in listOf(false, true)) {
            for (roll in 0 until if (reinforced) 3 else 5) {
                val f = MuspahFixture()
                f.start()
                f.until(104)
                if (reinforced) {
                    f.boss.hitpoints = 128
                    f.damage(2)
                    f.until(111)
                }
                f.selectionRoll = roll
                f.attack()
                val hit = f.hits().last().second
                assertEquals(if (roll == 0) HitType.Magic else HitType.Ranged, hit.type)
                assertEquals(
                    if (roll == 0) {
                        if (reinforced) 79 else 72
                    } else {
                        if (reinforced) 72 else 61
                    },
                    hit.damage,
                )
                assertEquals(1, f.projectiles.size)
            }
        }
    }

    @Test
    fun `native approach and operate registrations share one attack cooldown`() {
        val f = MuspahFixture()
        f.start()
        f.until(104)
        f.attack(operate = true)
        f.attack()
        assertEquals(1, f.projectiles.size)
        assertEquals(1, f.registry.of(f.boss).recentAttackTraces().size)
        assertEquals(110, f.boss.actionDelay)
    }

    @Test
    fun `ordinary projectile is invalidated after leaving its encounter`() {
        val f = MuspahFixture()
        f.start()
        f.until(104)
        f.attack()
        val hit = f.hits().single().second
        assertTrue(hit.isValidNow())
        f.leave()
        assertFalse(hit.isValidNow())
    }

    @Test
    fun `cooldown blocked ranged attempts do not consume style selection randomness`() {
        val f = MuspahFixture()
        f.start()
        f.until(104)
        f.attack()
        val draws = f.bounds.toList()
        for (tick in 105..109) {
            f.until(tick)
            f.attack()
        }
        assertEquals(1, f.projectiles.size)
        assertEquals(draws, f.bounds)
    }

    @Test
    fun `stationary melee maximum advances once per committed attack not every attempt`() {
        val f = MuspahFixture(melee = true)
        f.start()
        f.until(104)
        f.boss.previousCoords = f.boss.coords
        f.attack()
        assertEquals(14, f.hits().last().second.damage)
        for (tick in 105..109) {
            f.until(tick)
            f.attack()
        }
        assertEquals(2, f.hits().size)
        assertEquals(20, f.hits().last().second.damage)
    }

    @Test
    fun `both initial forms use one owned actor and four tick readiness`() {
        for (melee in listOf(false, true)) {
            val f = MuspahFixture(melee)
            f.start()
            assertEquals(f.local(41, 34), f.player.coords)
            assertEquals(f.local(31, 34), f.boss.coords)
            assertEquals(850, f.boss.hitpoints)
            assertEquals(f.player.uid, f.boss.spawnOwner)
            assertEquals(f.seq(MuspahAssets.SPAWN), f.boss.pendingSequence.id)
            val session = assertNotNull(f.instances.sessionForPlayer(f.player))
            assertEquals(listOf(f.boss), f.instances.npcsForInstance(session.id))
            f.until(103)
            f.attack()
            assertTrue(f.hits().isEmpty())
            f.until(104)
            f.attack()
            assertEquals(1, f.hits().size)
            assertEquals(104 + if (melee) 5 else 6, f.boss.actionDelay)
            assertEquals(melee, f.boss.apRangeOverride == 1)
        }
    }

    @Test
    fun `normal ranged and charged magic use native projectile and impact delays`() {
        for (magic in listOf(false, true)) {
            val f = MuspahFixture(magic = magic)
            f.start()
            f.until(104)
            f.attack()
            val hit = f.hits().single()
            assertEquals(if (magic) HitType.Magic else HitType.Ranged, hit.second.type)
            assertEquals(if (magic) 72 else 61, hit.second.damage)
            val projectile = f.projectiles.single().second
            assertEquals(f.player.coords, projectile.endCoord)
            assertEquals(
                f.spot(if (magic) MuspahAssets.MAGIC_TRAVEL else MuspahAssets.RANGED_TRAVEL),
                projectile.spotanim,
            )
            if (magic) assertEquals(3, hit.first)
            assertEquals(
                f.seq(if (magic) MuspahAssets.ATTACK_MAGIC else MuspahAssets.ATTACK_RANGED),
                f.boss.pendingSequence.id,
            )
        }
    }

    @Test
    fun `ordinary shots snapshot protection at launch`() {
        val f = MuspahFixture(magic = true)
        f.start()
        f.until(104)
        VarPlayerIntMapSetter.set(f.player, "varbit.prayer_protectfrommagic", 1)
        f.attack()
        val hit = f.hits().single().second
        VarPlayerIntMapSetter.set(f.player, "varbit.prayer_protectfrommagic", 0)
        assertEquals(0, hit.damage)
    }

    @Test
    fun `damage crossing starts four tick transformation and preserves actor identity`() {
        val f = MuspahFixture()
        f.start()
        f.until(104)
        val uid = f.boss.uid
        f.damage(100)
        assertEquals(750, f.boss.hitpoints)
        assertTrue(f.encounters.debugStatus(f.player).contains("state=TRANSFORMING"))
        assertEquals(f.seq(MuspahAssets.ATTACK_SUMMON), f.boss.pendingSequence.id)
        f.until(107)
        assertEquals(MuspahAssets.RANGED.asRSCM(RSCMType.NPC), f.boss.visType.id)
        f.until(108)
        assertEquals(MuspahAssets.MELEE.asRSCM(RSCMType.NPC), f.boss.visType.id)
        assertEquals(uid, f.boss.uid)
        assertEquals(109, f.boss.actionDelay)
        assertEquals(1, f.boss.apRangeOverride)
    }

    @Test
    fun `ranged opening threshold runs all twelve lightning positions then resumes`() {
        val f = MuspahFixture()
        f.start()
        f.until(104)
        f.damage(213)
        assertEquals(637, f.boss.hitpoints)
        assertTrue(f.encounters.debugStatus(f.player).contains("state=LIGHTNING_CLOUDS"))
        val route =
            listOf(
                41 to 34,
                23 to 43,
                31 to 22,
                39 to 43,
                20 to 34,
                39 to 25,
                31 to 46,
                23 to 25,
                41 to 34,
                23 to 43,
                31 to 22,
                39 to 43,
            )
        route.forEachIndexed { i, tile ->
            f.until(106 + i * 3)
            assertEquals(f.local(tile.first, tile.second), f.boss.coords)
            assertEquals(f.seq(MuspahAssets.TELEPORT_APPEAR), f.boss.pendingSequence.id)
        }
        f.until(142)
        assertTrue(f.encounters.debugStatus(f.player).contains("state=RANGED"))
        assertEquals(145, f.boss.actionDelay)
    }

    @Test
    fun `melee opening threshold runs homing for thirty four ticks and hardens spikes`() {
        val f = MuspahFixture(melee = true)
        f.start()
        f.until(104)
        f.damage(213)
        assertTrue(f.encounters.debugStatus(f.player).contains("state=HOMING_SPIKES"))
        assertTrue(
            f.activeLocs.values.any { it.id == MuspahAssets.SPIKE_FOLLOW.asRSCM(RSCMType.LOC) }
        )
        f.until(137)
        assertTrue(f.encounters.debugStatus(f.player).contains("state=HOMING_SPIKES"))
        f.until(138)
        assertTrue(f.encounters.debugStatus(f.player).contains("state=MELEE"))
        assertFalse(
            f.activeLocs.values.any { it.id == MuspahAssets.SPIKE_FOLLOW.asRSCM(RSCMType.LOC) }
        )
    }

    @Test
    fun `shield transition is below127 and applies shield five ticks later`() {
        val f = MuspahFixture()
        f.start()
        f.until(104)
        f.boss.hitpoints = 128
        f.damage(2)
        assertEquals(126, f.boss.hitpoints)
        assertTrue(f.encounters.debugStatus(f.player).contains("state=SHOCKWAVE"))
        f.until(108)
        assertTrue(f.encounters.debugStatus(f.player).contains("state=SHOCKWAVE"))
        f.until(109)
        assertTrue(f.encounters.debugStatus(f.player).contains("shield=75"))
        assertEquals(MuspahAssets.SHIELD.asRSCM(RSCMType.NPC), f.boss.visType.id)
        f.until(111)
        f.attack()
        assertTrue(f.encounters.debugStatus(f.player).contains("shield=73"))
        assertEquals(116, f.boss.actionDelay)
        VarPlayerIntMapSetter.set(f.player, "varbit.prayer_smite", 1)
        repeat(3) { f.damage(100) }
        assertEquals(126, f.boss.hitpoints)
        assertTrue(f.encounters.debugStatus(f.player).contains("state=FINAL_STAND"))
        assertEquals(MuspahAssets.FINAL.asRSCM(RSCMType.NPC), f.boss.visType.id)
    }

    @Test
    fun `outside players are not encounter members and explicit exit clears hazards`() {
        val f = MuspahFixture(melee = true)
        assertFalse(f.encounters.isActive(f.player))
        f.start()
        f.until(104)
        f.damage(213)
        assertTrue(f.activeLocs.isNotEmpty())
        f.leave()
        assertFalse(f.encounters.isActive(f.player))
        assertNull(f.instances.sessionForPlayer(f.player))
        assertTrue(f.activeLocs.isEmpty())
        assertTrue(f.npcList.none())
        assertEquals(MUSPAH_OUTSIDE, f.player.coords)
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }

    @org.junit.jupiter.api.AfterEach fun clearMocks() = framework().clearInlineMocks()
}

@OptIn(InternalApi::class)
private class MuspahFixture(melee: Boolean = false, magic: Boolean = false) {
    val clock = MapClock(100)
    val events = EventBus()
    val collision = CollisionFlagMap()

    fun newPlayer(slot: Int) =
        Player().apply {
            slotId = slot
            uuid = slot.toLong()
            assignUid()
            coords = MUSPAH_OUTSIDE
            statMap.setBaseLevel("stat.hitpoints", 99)
            statMap.setCurrentLevel("stat.hitpoints", 99)
            statMap.setBaseLevel("stat.prayer", 99)
            statMap.setCurrentLevel("stat.prayer", 99)
            inv = invMap.getOrPut("inv.inv")
            worn = invMap.getOrPut("inv.worn")
        }

    val player = newPlayer(1)
    val players = PlayerList().apply { this[1] = player }
    val npcList = NpcList()
    val npcs = NpcRepository(clock, NpcRegistry(npcList, collision, events), npcList)
    private var regionNumber = 0
    private val regions =
        mock(RegionRepository::class.java) { call ->
            when (call.method.name) {
                "add" -> {
                    val base = 6400 + 64 * regionNumber++
                    Region(CoordGrid(base, 6400), CoordGrid(base + 64, 6464), 1, 1).apply {
                        for (x in 0..7) for (z in 0..7) {
                            registerZone(
                                RegionZoneCopy(ZoneKey(352 + x, 528 + z, 0), 0, null),
                                ZoneKey(base / 8 + x, 800 + z, 0),
                            )
                            collision.allocateIfAbsent(base + x * 8, 6400 + z * 8, 0)
                        }
                    }
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
    val bounds = mutableListOf<Int>()
    var selectionRoll: Int? = null
    val random =
        object : GameRandom {
            override fun of(maxExclusive: Int): Int {
                bounds += maxExclusive
                return when (maxExclusive) {
                    2 -> if (melee) 1 else 0
                    3,
                    5 -> selectionRoll ?: if (magic) 0 else maxExclusive - 1
                    else -> maxExclusive - 1
                }
            }

            override fun of(minInclusive: Int, maxInclusive: Int) = maxInclusive

            override fun randomDouble() = 0.5
        }
    val projectiles = mutableListOf<Pair<Int, ProjAnim>>()
    val world =
        mock(WorldRepository::class.java) { call ->
            if (call.method.name == "projAnim")
                projectiles += clock.cycle to call.arguments[0] as ProjAnim
            RETURNS_DEFAULTS.answer(call)
        }
    val activeLocs = linkedMapOf<CoordGrid, LocInfo>()
    val locs =
        LocRepository(
            clock,
            mock(LocRegistry::class.java) { call ->
                when (call.method.name) {
                    "add" -> {
                        val loc = call.arguments[0] as LocInfo
                        activeLocs[loc.coords] = loc
                        LocRegistryResult.Add.NormalSpawned
                    }
                    "del" -> {
                        val loc = call.arguments[0] as LocInfo
                        activeLocs.remove(loc.coords)
                        LocRegistryResult.Delete.NormalSpawned
                    }
                    else -> RETURNS_DEFAULTS.answer(call)
                }
            },
            mock(RegionRegistry::class.java),
        )
    val corruption = CombatCorruption(clock)
    val combat =
        NvPCombat(
            mock(AccuracyFormulae::class.java) { call ->
                if (call.method.name.startsWith("roll")) true else RETURNS_DEFAULTS.answer(call)
            },
            mock(MaxHitFormulae::class.java),
            world,
        )
    val registry = EncounterRegistry(random)
    val deps =
        BossDeps(
            random,
            mock(AccuracyFormulae::class.java),
            world,
            npcs,
            players,
            clock,
            WorldQueueList(),
            collision,
            registry,
            BossExtensionRegistry(),
            combat,
        )
    val encounters =
        MuspahEncounterManager(
            clock,
            instances,
            AiPlayerInteractions(events, players),
            collision,
            random,
            registry,
            corruption,
            MuspahArenaEffects(
                clock,
                instances,
                locs,
                collision,
                random,
                world,
                deps.worldQueues,
                corruption,
            ),
            BossPhaseClock(
                deps,
                StandardNpcAccessLauncher(
                    mock(StandardNpcAccessContextFactory::class.java) { call ->
                        if (call.method.name == "create") accessContext()
                        else RETURNS_DEFAULTS.answer(call)
                    }
                ),
            ),
        )
    val boss
        get() = npcList.first()

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
        MuspahWorld(BossInstanceRegistry(), encounters, mock(ProtectedAccessLauncher::class.java))
    val protectedContext =
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
    val nativeLifecycle: PlayerPostTickHook

    init {
        collision.allocateIfAbsent(player.coords.x, player.coords.z, 0)
        val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        with(NpcDeathScript(death)) { context.startup() }
        with(
            MuspahCombat(
                encounters,
                death,
                players,
                deps,
                MuspahAttackParameters(encounters, corruption),
            )
        ) {
            context.startup()
        }
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
        with(MuspahLifecycle(encounters)) { context.startup() }
        val lifecycle =
            injector.getInstance(
                Class.forName("org.rsmod.api.instances.hook.InstanceLifecycleScript")
                    .asSubclass(PluginScript::class.java)
            )
        with(lifecycle) { context.startup() }
        nativeLifecycle = lifecycle as PlayerPostTickHook
    }

    fun start(player: Player = this.player) {
        player.launch {
            with(instanceScript) {
                ProtectedAccess(player, this@launch, protectedContext).startLair()
            }
        }
        assertTrue(encounters.isActive(player))
    }

    fun leave(player: Player = this.player) {
        player.launch {
            with(instanceScript) {
                ProtectedAccess(player, this@launch, protectedContext).leaveLair()
            }
        }
    }

    fun local(x: Int, z: Int) = CoordGrid(6400 + x, 6400 + z)

    fun seq(name: String) = name.asRSCM(RSCMType.SEQ)

    fun spot(name: String) = name.asRSCM(RSCMType.SPOTANIM)

    fun locOp(id: Int) {
        val type = assertNotNull(ServerCacheManager.getObject(id))
        val loc = BoundLocInfo(LocInfo(2, player.coords, LocEntity(type.id, 10, 0)), type)
        player.previousCoords = player.coords
        player.launch {
            events.publish(
                ProtectedAccess(player, this, protectedContext),
                LocEvents.Op1(loc, loc, type),
            )
        }
    }

    fun until(tick: Int, postTick: Boolean = true) {
        while (clock.cycle < tick) {
            npcList.forEach { it.pendingSequence = EntitySeq.NULL }
            clock.cycle++
            players.forEach {
                it.currentMapClock = clock.cycle
                it.processedMapClock = clock.cycle
                if (it.activeCoroutine != null) it.advanceActiveCoroutine()
            }
            EntityLifecycleProcess(
                    npcs,
                    locs,
                    mock(ObjRepository::class.java),
                    mock(ControllerRepository::class.java),
                )
                .process()
            npcList.toList().forEach {
                it.currentMapClock = clock.cycle
                it.processedMapClock = clock.cycle
                if (it.canProcess && it.activeCoroutine != null) it.advanceActiveCoroutine()
                NpcRevealProcessor(npcs).process(it)
            }
            WorldQueueListProcess(deps.worldQueues).process()
            if (postTick) players.forEach { encounters.tick(it) }
        }
    }

    fun end(reason: String) {
        when (reason) {
            "teleport" -> {
                val previous = player.coords
                player.coords = MUSPAH_OUTSIDE
                events.publish(PlayerMovementEvent.CoordsMovedEvent(player, previous))
            }
            "logout" -> events.publish(SessionStateEvent.Logout(player))
            "disconnect" -> {
                player.clientDisconnected.set(true)
                nativeLifecycle.onPostTick(player)
            }
            "death" -> instances.handleDeath(player, clock.cycle)
        }
    }

    fun killQueue() {
        boss.launch {
            events.publish(
                StandardNpcAccess(boss, this, accessContext()),
                NpcQueueEvents.Type(boss, null, "queue.death".asRSCM(RSCMType.QUEUE)),
            )
        }
    }

    fun attack(operate: Boolean = false) {
        boss.currentMapClock = clock.cycle
        boss.processedMapClock = clock.cycle
        boss.launch {
            events.publish(
                StandardNpcAccess(boss, this, accessContext()),
                if (operate) AiPlayerEvents.Op2(player, boss) else AiPlayerEvents.Ap2(player, boss),
            )
        }
    }

    private fun accessContext() =
        StandardNpcAccessContext(
            { random },
            { StandardNpcHitModifier(events) },
            { StandardNpcHitProcessor(players, events, emptySet()) },
        )

    fun damage(amount: Int) {
        val hit =
            HitBuilder(
                HitType.Ranged,
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
        events.publish(NpcHitEvents.Modify(boss, hit))
        boss.launch { StandardNpcAccess(boss, this, accessContext()).processQueuedHit(hit.build()) }
    }

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
