package org.rsmod.content.bosses.grotesqueguardians

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
import dev.openrune.types.ProjAnimType
import java.io.File
import kotlin.reflect.typeOf
import kotlin.test.*
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

/** Executes the pre-migration registration; these are behavioral oracles, not new OSRS evidence. */
@OptIn(InternalApi::class)
class GrotesqueGuardiansNativeTest {
    @Test
    fun `native entry owns two dormant guardians and bell arms both after two ticks`() {
        val f = GuardiansFixture()
        assertEquals(CoordGrid(6433, 6423), f.player.coords)
        assertEquals(CoordGrid(6437, 6429), f.dawn.coords)
        assertEquals(CoordGrid(6425, 6429), f.dusk.coords)
        assertEquals(listOf(450, 450), listOf(f.dawn.hitpoints, f.dusk.hitpoints))
        val session = assertNotNull(f.instances.sessionForPlayer(f.player))
        assertEquals(setOf(f.dawn, f.dusk), f.instances.npcsForInstance(session.id).toSet())
        assertEquals(f.player.uid, f.dawn.spawnOwner)
        f.attack(100, f.dawn)
        assertTrue(f.hits().isEmpty())
        f.ring()
        assertEquals(7774, f.dawn.pendingSequence.id)
        assertEquals(7790, f.dusk.pendingSequence.id)
        f.attack(101, f.dawn)
        assertTrue(f.hits().isEmpty())
        f.attack(102, f.dawn)
        assertEquals(2, f.hits().size)
        assertEquals(108, f.dawn.actionDelay)
    }

    @Test
    fun `Dawn has independent ranged components one tick apart and fourth stone ball`() {
        val f = GuardiansFixture()
        f.ring()
        f.attack(102, f.dawn)
        val expected =
            ProjAnim.fromNpcToPlayer(
                f.dawn,
                f.player,
                1444,
                ProjAnimType(
                    startHeight = 90,
                    endHeight = 40,
                    delay = 0,
                    angle = 32,
                    lengthAdjustment = 0,
                    progress = 0,
                    stepMultiplier = 5,
                ),
            )
        assertEquals(listOf(expected, expected), f.projectiles())
        assertEquals(listOf(9, 9), f.hits().map { it.second.damage })
        assertEquals(
            listOf(
                expected.serverCycles.coerceAtLeast(1),
                expected.serverCycles.coerceAtLeast(1) + 1,
            ),
            f.hits().map { it.first },
        )
        f.attack(108, f.dawn)
        f.attack(114, f.dawn)
        assertEquals(6, f.projectiles().size)
        f.attack(120, f.dawn)
        assertEquals(7771, f.dawn.pendingSequence.id)
        assertEquals(6, f.projectiles().size)
        f.idleUntil(122)
        assertEquals(6, f.hits().size)
        f.idleUntil(123)
        assertEquals(15, f.hits().last().second.damage)
        assertEquals(HitType.Typeless, f.hits().last().second.type)
        assertEquals(133, f.player.actionDelay)
    }

    @Test
    fun `Dawn nearby second attack is melee and normal prayer snapshots eighty percent`() {
        val f = GuardiansFixture()
        f.ring()
        f.player.coords = f.dawn.coords.translate(-1, 0)
        VarPlayerIntMapSetter.set(f.player, "varbit.prayer_protectfrommissiles", 1)
        f.attack(102, f.dawn)
        VarPlayerIntMapSetter.set(f.player, "varbit.prayer_protectfrommissiles", 0)
        assertEquals(listOf(1, 1), f.hits().map { it.second.damage })
        VarPlayerIntMapSetter.set(f.player, "varbit.prayer_protectfrommelee", 1)
        f.attack(108, f.dawn)
        assertEquals(7769, f.dawn.pendingSequence.id)
        assertEquals(3, f.hits().last().second.damage)
        assertEquals(HitType.Melee, f.hits().last().second.type)
    }

    @Test
    fun `all paired thresholds lightning and hammer transition preserve HP and form timing`() {
        val f = GuardiansFixture()
        f.ring()
        f.damage(f.dusk, 100, HitType.Melee)
        assertEquals(450, f.dusk.hitpoints)
        f.damage(f.dawn, 500, HitType.Ranged)
        assertEquals(247, f.dawn.hitpoints)
        assertEquals(7853, f.dawn.visType.id)
        assertEquals(7882, f.dusk.visType.id)
        f.damage(f.dusk, 500, HitType.Melee)
        assertEquals(247, f.dusk.hitpoints)
        assertEquals(7855, f.dusk.visType.id)
        f.idleUntil(111)
        assertEquals(7855, f.dusk.visType.id)
        f.idleUntil(112)
        assertEquals(7854, f.dusk.visType.id)
        assertEquals(7884, f.dawn.visType.id)
        assertEquals(3, f.locAdds())
        f.damage(f.dawn, 500, HitType.Ranged)
        assertEquals(9, f.dawn.hitpoints)
        assertTrue(f.encounters.useHammer(f.player, f.dawn, "obj.slayer_rock_hammer"))
        assertEquals(7885, f.dawn.visType.id)
        f.idleUntil(116)
        assertEquals(7854, f.dusk.visType.id)
        f.idleUntil(117)
        assertEquals(7888, f.dusk.visType.id)
        assertEquals(247, f.dusk.hitpoints)
        assertEquals(119, f.dusk.actionDelay)
    }

    @Test
    fun `Dusk phase two alternates melee animations around every third explosion`() {
        val f = GuardiansFixture()
        f.ring()
        f.damage(f.dawn, 500, HitType.Ranged)
        val sequences =
            listOf(102, 108, 114, 120, 126, 132).map { tick ->
                f.attack(tick, f.dusk)
                f.dusk.pendingSequence.id
            }
        assertEquals(listOf(7787, 7785, 7802, 7785, 7787, 7802), sequences)
        assertEquals(listOf(15, 15, 15, 15), f.hits().map { it.second.damage })
    }

    @Test
    fun `Dusk final fixed first and nested second rolls preserve prayer and missed components`() {
        for (prayer in listOf(false, true)) {
            val f = GuardiansFixture()
            f.finalPhase()
            if (prayer) VarPlayerIntMapSetter.set(f.player, "varbit.prayer_protectfrommissiles", 1)
            f.attack(119, f.dusk)
            assertEquals(7799, f.dusk.pendingSequence.id)
            val before = f.rolls.size
            f.attack(125, f.dusk)
            assertEquals(7801, f.dusk.pendingSequence.id)
            assertEquals(
                listOf(if (prayer) 4 else 9, 15),
                f.hits().takeLast(2).map { it.second.damage },
            )
            assertEquals(listOf(16, 16), f.rolls.drop(before))
            assertEquals(1, f.hits().takeLast(2).let { it[1].first - it[0].first })
        }
        val f = GuardiansFixture(accurate = false)
        f.finalPhase()
        f.attack(119, f.dusk)
        val before = f.rolls.size
        f.attack(125, f.dusk)
        assertEquals(listOf(0, 0), f.hits().takeLast(2).map { it.second.damage })
        assertEquals(listOf(1), f.rolls.drop(before))
    }

    @Test
    fun `threshold transition waits for native impact and queued damage respects the active phase`() {
        val f = GuardiansFixture()
        f.ring()
        val first = f.calculate(f.dawn, 500, HitType.Ranged)
        val second = f.calculate(f.dawn, 500, HitType.Ranged)
        assertEquals(7852, f.dawn.visType.id, "Calculating a projectile must not transform Dawn")
        assertEquals(450, f.dawn.hitpoints)
        f.apply(f.dawn, first)
        assertEquals(247, f.dawn.hitpoints)
        assertEquals(7853, f.dawn.visType.id)
        f.apply(f.dawn, second)
        assertEquals(247, f.dawn.hitpoints, "A second queued hit cannot bypass the new phase gate")
        val duskFirst = f.calculate(f.dusk, 500, HitType.Melee)
        val duskSecond = f.calculate(f.dusk, 500, HitType.Melee)
        assertEquals(7882, f.dusk.visType.id)
        f.apply(f.dusk, duskFirst)
        assertEquals(247, f.dusk.hitpoints)
        f.apply(f.dusk, duskSecond)
        // The inherited rules explicitly allow Dusk damage during lightning. Do not silently
        // introduce transition immunity as part of an architecture migration.
        assertEquals(44, f.dusk.hitpoints)
        assertEquals(7855, f.dusk.visType.id)
    }

    @Test
    fun `native exits cancel pending hits callbacks and paired instance resources`() {
        for (ending in listOf("exit", "teleport", "logout", "disconnect", "death")) {
            val f = GuardiansFixture()
            f.ring()
            f.attack(102, f.dawn)
            f.encounters.launchSpecial(f.dawn, f.player, "stone")
            val session = assertNotNull(f.instances.sessionForPlayer(f.player))
            val state = assertNotNull(session.stateOrNull(GUARDIANS_ARENA))
            val pending = f.hits().map { it.second }
            assertEquals(2, pending.size)
            assertEquals(1, f.queues.size)
            f.end(ending)
            assertTrue(pending.all { !it.isValidNow() }, ending)
            f.instances.tickReclaim(f.clock.cycle + 1)
            assertTrue(pending.all { !it.isQueueArgumentValid() }, ending)
            assertTrue(session.isEnded, ending)
            assertTrue(f.instances.npcsForInstance(session.id).isEmpty(), ending)
            assertNull(f.instances.sessionForPlayer(f.player), ending)
            assertEquals(0, f.queues.size, ending)
            assertTrue(state.spheres.isEmpty())
            assertTrue(state.lightning.isEmpty())
            assertFalse(f.dawn.isSlotAssigned)
            assertFalse(f.dusk.isSlotAssigned)
            assertNull(f.deps.encounterRegistry.existing(f.dawn))
            assertNull(f.deps.encounterRegistry.existing(f.dusk))
            val output = mockingDetails(f.world).invocations.size
            f.idleUntil(130)
            assertEquals(output, mockingDetails(f.world).invocations.size)
        }
    }

    @Test
    fun `two native kills retain Dawn corpse then replace the pair once after seventeen ticks`() {
        val f = GuardiansFixture()
        val session = assertNotNull(f.instances.sessionForPlayer(f.player))
        repeat(2) { completion ->
            f.finalPhase()
            val old = assertNotNull(session.stateOrNull(GUARDIANS_ARENA))
            f.damage(f.dusk, 500, HitType.Melee)
            f.killQueue()
            var remaining = 30
            while (!f.dusk.hidden && remaining-- > 0) f.idleUntil(f.clock.cycle + 1)
            assertTrue(remaining > 0, "Native death sequence must complete")
            assertEquals(GrotesquePhase.RESPAWNING, old.phase)
            assertFalse(f.dawn.hidden)
            assertEquals(7885, f.dawn.visType.id)
            assertEquals(completion + 1, f.kills.size)
            // Baseline definition defect: native KC reads the immutable spawn definition (7851),
            // while the active cache only supplies this varp on the death definition. Keep this
            // discrepancy visible; it is not proof of working KC or an approved zero-KC rule.
            assertEquals(0, f.player.vars["varp.total_gargboss_kills"])
            assertEquals(f.player.coords, f.kills.last().dropCoords)
            val scheduled = f.queues.size
            f.encounters.finishDuskDeath(f.dusk)
            assertEquals(
                scheduled,
                f.queues.size,
                "Duplicate completion must not schedule another reset",
            )
            val resetTick = f.clock.cycle + 17
            f.idleUntil(resetTick - 1)
            assertEquals(7885, f.dawn.visType.id)
            assertSame(old, session.stateOrNull(GUARDIANS_ARENA))
            f.idleUntil(resetTick)
            assertFalse(old.dawn.isSlotAssigned)
            assertFalse(old.dusk.isSlotAssigned)
            assertTrue(old.dawnEncounter.isEnded)
            assertTrue(old.duskEncounter.isEnded)
            val fresh = assertNotNull(session.stateOrNull(GUARDIANS_ARENA))
            assertNotSame(old, fresh)
            assertEquals(GrotesquePhase.DORMANT, fresh.phase)
            assertEquals(listOf(450, 450), listOf(fresh.dawn.hitpoints, fresh.dusk.hitpoints))
            assertEquals(2, f.npcList.count())
            assertEquals(
                setOf(fresh.dawn, fresh.dusk),
                f.instances.npcsForInstance(session.id).toSet(),
            )
            assertEquals(0, f.queues.size)
            f.dawn = fresh.dawn
            f.dusk = fresh.dusk
        }
    }

    @Test
    fun `real roof operation uses protected entry exit and fresh instance identity without commands`() {
        val f = GuardiansFixture()
        val first = assertNotNull(f.instances.sessionForPlayer(f.player))
        f.roofOp()
        assertEquals(GROTESQUE_PUBLIC_EXIT, f.player.coords)
        assertTrue(first.isEnded)
        f.roofOp()
        val second = assertNotNull(f.instances.sessionForPlayer(f.player))
        assertNotEquals(first.id, second.id)
        assertEquals(
            GrotesquePhase.DORMANT,
            assertNotNull(second.stateOrNull(GUARDIANS_ARENA)).phase,
        )
        assertEquals(2, f.instances.npcsForInstance(second.id).size)
        assertEquals(2, f.npcList.count())
    }

    @Test
    fun `two rooms isolate guardian ownership arena resources and cleanup`() {
        val f = GuardiansFixture()
        val first = assertNotNull(f.instances.sessionForPlayer(f.player))
        val partner =
            Player().apply {
                slotId = 2
                uuid = 2L
                assignUid()
                coords = GROTESQUE_PUBLIC_EXIT
                statMap.setBaseLevel("stat.hitpoints", 99)
                statMap.setCurrentLevel("stat.hitpoints", 99)
            }
        f.players[2] = partner
        f.enter(partner)
        val second = assertNotNull(f.instances.sessionForPlayer(partner))
        val secondState = assertNotNull(second.stateOrNull(GUARDIANS_ARENA))
        assertFalse(f.encounters.isParticipant(f.player, secondState.dawn))
        assertFalse(f.encounters.canAttack(f.dawn, partner))
        f.ring()
        f.encounters.launchSpecial(f.dawn, f.player, "stone")
        assertTrue(f.encounters.ringBell(partner))
        f.encounters.launchSpecial(secondState.dawn, partner, "stone")
        assertEquals(2, f.queues.size)
        f.leave()
        assertTrue(first.isEnded)
        assertFalse(second.isEnded)
        assertEquals(1, f.queues.size)
        assertSame(second, f.instances.sessionForPlayer(partner))
        assertEquals(2, f.npcList.count())
        f.instances.terminate(second)
        assertEquals(0, f.queues.size)
        assertEquals(0, f.npcList.count())
    }

    @Test
    fun `sphere absorption and timed return retain exact native locations and healing`() {
        val f = GuardiansFixture()
        f.ring()
        f.damage(f.dawn, 500, HitType.Ranged)
        f.damage(f.dusk, 500, HitType.Melee)
        f.idleUntil(112)
        val state = assertNotNull(f.instances.guardiansState(f.player))
        val spheres = state.spheres.toList()
        assertEquals(listOf(31678, 31679, 31680), spheres.map { it.loc.id })
        assertTrue(f.encounters.absorbSphere(f.player, spheres.first().loc.coords))
        assertFalse(f.encounters.absorbSphere(f.player, spheres.first().loc.coords))
        f.dawn.hitpoints = 100
        f.idleUntil(147)
        assertEquals(100, f.dawn.hitpoints)
        f.idleUntil(148)
        assertEquals(280, f.dawn.hitpoints)
        assertTrue(state.spheres.isEmpty())
        assertEquals(
            3,
            mockingDetails(f.world).invocations.count { it.method.name == "locDelPrivate" },
        )
    }

    @Test
    fun `prison snapshots fifteen perimeter flames and escape avoids damage and healing`() {
        for (escape in listOf(false, true)) {
            val f = GuardiansFixture()
            f.finalPhase()
            val previousHits = f.hits().size
            f.attack(119, f.dusk)
            val flames =
                mockingDetails(f.world)
                    .invocations
                    .filter {
                        it.method.name.startsWith("spotanimMapPrivate") &&
                            (it.arguments[1] as dev.openrune.types.aconverted.SpotanimType).id ==
                                1434
                    }
                    .map { CoordGrid(it.arguments[2] as Int) }
            assertEquals(15, flames.size)
            assertFalse(f.player.coords.translate(2, 0) in flames)
            if (escape) f.player.coords = f.player.coords.translate(2, 0)
            f.idleUntil(123)
            assertEquals(247, f.dusk.hitpoints)
            f.idleUntil(124)
            assertEquals(if (escape) 247 else 312, f.dusk.hitpoints)
            assertEquals(
                if (escape) emptyList() else listOf(65),
                f.hits()
                    .drop(previousHits)
                    .filter { it.second.type == HitType.Typeless }
                    .map { it.second.damage },
            )
        }
    }

    @Test
    fun `source NPC overlay enables one native killcount without mutating the active cache files`() {
        val f = GuardiansFixture()
        val blocks =
            tomlMapper { rsconfig { enableConstantProvider() } }
                .decodeRuneScapeBlocks(File(".data/raw-cache/server/npcs.toml").toPath())
        val mapper = tomlMapper {}
        val overlay =
            blocks
                .asSequence()
                .filter { it.name == "npc" }
                .map {
                    mapper.decodeRuneScape(typeOf<List<NpcServerType>>(), it.map.properties)
                        as NpcServerType
                }
                .firstOrNull { it.id == 7851 }
        assertNotNull(
            overlay,
            "The native spawn identity ${f.dusk.type.internalName} needs the same KC parameter as its death form",
        )
        val definition = f.dusk.type
        val previous = definition.paramMap
        try {
            // Exercise the compiler's base-plus-source parameter merge entirely in memory.
            // The live SERVER/LIVE cache files and all unrelated definition fields stay unchanged.
            definition.paramMap =
                ParamMap(definition.paramsRaw.orEmpty() + overlay.paramsRaw.orEmpty())
            f.finalPhase()
            f.damage(f.dusk, 500, HitType.Melee)
            f.killQueue()
            var guard = 30
            while (!f.dusk.hidden && guard-- > 0) f.idleUntil(f.clock.cycle + 1)
            assertTrue(guard > 0)
            assertEquals(1, f.kills.size)
            assertEquals(1, f.player.vars["varp.total_gargboss_kills"])
        } finally {
            definition.paramMap = previous
        }
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }

    @org.junit.jupiter.api.AfterEach
    fun releaseMocks() {
        framework().clearInlineMocks()
    }
}

@OptIn(InternalApi::class)
private class GuardiansFixture(accurate: Boolean = true) {
    val clock = MapClock(100)
    val events = EventBus()
    val collision = CollisionFlagMap()
    val player =
        Player().apply {
            slotId = 1
            uuid = 1L
            assignUid()
            coords = GROTESQUE_PUBLIC_EXIT
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
                    val region = Region(base, base.translate(64, 64), ++allocations, allocations)
                    for (level in 0..3) for (x in 0..7) for (z in 0..7) {
                        val tile = CoordGrid(base.x + x * 8, base.z + z * 8, level)
                        region.registerZone(
                            RegionZoneCopy(ZoneKey(208 + x, 568 + z, level), 0, null),
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
        InstanceManager(regions, npcs, players, events, InstanceAreaResolver(), clock, collision)
    val rolls = mutableListOf<Int>()
    private var arenaDraw = 0
    val random =
        object : GameRandom {
            override fun of(maxExclusive: Int): Int {
                rolls += maxExclusive
                return maxExclusive - 1
            }

            override fun of(minInclusive: Int, maxInclusive: Int): Int {
                if (minInclusive == 26 && maxInclusive == 40) return 26 + (arenaDraw++ / 2) % 15
                if (minInclusive == 24 && maxInclusive == 38)
                    return 24 + (arenaDraw++ / 2 / 15) % 15
                rolls += maxInclusive - minInclusive + 1
                return maxInclusive
            }

            override fun randomDouble() = 0.5
        }
    val world = mock(WorldRepository::class.java)
    val queues = WorldQueueList()
    val accuracy =
        mock(AccuracyFormulae::class.java) { call ->
            if (call.method.name.startsWith("roll")) accurate else RETURNS_DEFAULTS.answer(call)
        }
    val deps =
        BossDeps(
            random,
            accuracy,
            world,
            npcs,
            players,
            clock,
            queues,
            collision,
            EncounterRegistry(random),
            BossExtensionRegistry(),
            NvPCombat(accuracy, mock(MaxHitFormulae::class.java), world),
        )
    val encounters =
        GrotesqueGuardiansEncounterManager(
            clock,
            instances,
            AiPlayerInteractions(events, players),
            deps,
            queues,
            GuardiansArenaEffects(clock, instances, random, deps, queues, world),
        )
    val kills = mutableListOf<NpcDeathKillContext>()
    val death =
        NpcDeath(
            npcs,
            players,
            mock(ObjRepository::class.java),
            setOf(NpcDeathDropHook { true }),
            setOf(KillcountNpcKillHook(), NpcDeathKillHook { kills += it }),
        )
    var dawn: Npc
    var dusk: Npc
    val instanceScript =
        GrotesqueGuardiansWorld(
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
        collision.allocateIfAbsent(player.coords.x, player.coords.z, player.coords.level)
        val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        with(NpcDeathScript(death)) { context.startup() }
        with(
            GrotesqueGuardiansCombat(
                encounters,
                death,
                players,
                deps,
                GrotesqueGuardiansAttackParameters(instances),
                GuardiansDamageRules(instances, deps),
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
        with(GrotesqueGuardiansLifecycle(encounters)) { context.startup() }
        val plugin =
            injector.getInstance(
                Class.forName("org.rsmod.api.instances.hook.InstanceLifecycleScript")
                    .asSubclass(PluginScript::class.java)
            )
        with(plugin) { context.startup() }
        lifecycle = plugin as PlayerPostTickHook
        enter(player)
        dawn = npcList.single { it.type.id == 7852 }
        dusk = npcList.single { it.type.id == 7851 }
    }

    fun enter(who: Player) {
        who.launch {
            with(instanceScript) {
                ProtectedAccess(who, this@launch, accessContext).enterGuardians()
            }
        }
    }

    fun leave() {
        player.launch {
            with(instanceScript) {
                ProtectedAccess(player, this@launch, accessContext).leaveGuardians()
            }
        }
    }

    fun ring() {
        assertTrue(encounters.ringBell(player))
    }

    fun finalPhase() {
        val start = clock.cycle
        ring()
        damage(dawn, 500, HitType.Ranged)
        damage(dusk, 500, HitType.Melee)
        idleUntil(start + 12)
        damage(dawn, 500, HitType.Ranged)
        assertTrue(encounters.useHammer(player, dawn, "obj.slayer_rock_hammer"))
        idleUntil(start + 17)
    }

    fun idleUntil(tick: Int) {
        while (clock.cycle < tick) {
            clearFrameSequences()
            clock.cycle++
            WorldQueueListProcess(queues).process()
            npcList.toList().forEach {
                if (!it.isSlotAssigned) return@forEach
                it.currentMapClock = clock.cycle
                it.processedMapClock = clock.cycle
                if (it.canProcess && it.activeCoroutine != null) it.advanceActiveCoroutine()
                NpcRevealProcessor(npcs).process(it)
            }
            encounters.tickAll()
        }
    }

    fun end(ending: String) {
        when (ending) {
            "exit" -> leave()
            "teleport" -> {
                val previous = player.coords
                player.coords = GROTESQUE_PUBLIC_EXIT
                events.publish(PlayerMovementEvent.CoordsMovedEvent(player, previous))
            }
            "logout" -> events.publish(SessionStateEvent.Logout(player))
            "disconnect" -> {
                player.clientDisconnected.set(true)
                lifecycle.onPostTick(player)
            }
            "death" -> instances.handleDeath(player, clock.cycle)
            else -> error(ending)
        }
    }

    fun killQueue() {
        dusk.launch {
            events.publish(
                StandardNpcAccess(
                    dusk,
                    this,
                    StandardNpcAccessContext(
                        { random },
                        { StandardNpcHitModifier(events) },
                        { StandardNpcHitProcessor(players, events, emptySet()) },
                    ),
                ),
                NpcQueueEvents.Type(dusk, null, "queue.death".asRSCM(RSCMType.QUEUE)),
            )
        }
    }

    fun roofOp() {
        val type = assertNotNull(ServerCacheManager.getObject(GrotesqueAssets.ROOF_ENTRANCE))
        val loc = BoundLocInfo(LocInfo(2, player.coords, LocEntity(type.id, 10, 0)), type)
        // Movement-arrival processing is outside this interaction fixture.
        player.previousCoords = player.coords
        player.launch {
            events.publish(
                ProtectedAccess(player, this, accessContext),
                LocEvents.Op1(loc, loc, type),
            )
        }
    }

    fun attack(tick: Int, npc: Npc) {
        if (tick > clock.cycle) clearFrameSequences()
        clock.cycle = tick
        npc.currentMapClock = tick
        npc.processedMapClock = tick
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
                AiPlayerEvents.Ap2(player, npc),
            )
        }
    }

    // Client frame boundary: NpcPostTickProcess clears these fields after emitting each update.
    private fun clearFrameSequences() {
        npcList.forEach { it.pendingSequence = EntitySeq.NULL }
    }

    fun damage(npc: Npc, amount: Int, type: HitType) = apply(npc, calculate(npc, amount, type))

    fun calculate(npc: Npc, amount: Int, type: HitType): Hit {
        val builder =
            HitBuilder(
                type,
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
        events.publish(NpcHitEvents.Modify(npc, builder))
        return builder.build()
    }

    fun apply(npc: Npc, hit: Hit) {
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

    fun projectiles() =
        mockingDetails(world)
            .invocations
            .filter { it.method.name == "projAnim" }
            .map { it.arguments[0] as ProjAnim }

    fun locAdds() = mockingDetails(world).invocations.count { it.method.name == "locAddPrivate" }

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
