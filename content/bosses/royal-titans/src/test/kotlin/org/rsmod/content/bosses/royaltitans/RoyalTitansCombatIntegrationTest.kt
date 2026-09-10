package org.rsmod.content.bosses.royaltitans

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import java.sql.SQLException
import kotlin.test.*
import net.rsprot.protocol.game.outgoing.sound.SynthSound
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.mockito.Mockito.mock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.death.plugin.NpcDeathScript
import org.rsmod.api.droptable.DropTableRegistry
import org.rsmod.api.game.process.npc.NpcFaceSquareProcessor
import org.rsmod.api.game.process.npc.NpcMovementProcessor
import org.rsmod.api.game.process.npc.NpcPostTickProcess
import org.rsmod.api.game.process.npc.NpcQueueProcessor
import org.rsmod.api.game.process.npc.NpcRegenProcessor
import org.rsmod.api.game.process.npc.mode.NpcModeProcessor
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.npc.access.StandardNpcAccessContextFactory
import org.rsmod.api.npc.access.StandardNpcAccessLauncher
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.hit.NpcDamageContributor
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.StepFactory
import org.rsmod.api.utils.logging.GameExceptionHandler
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.client.NoopClient
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.HitAttribution
import org.rsmod.game.hit.HitContext
import org.rsmod.game.hit.HitType
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.game.seq.EntitySeq
import org.rsmod.game.spot.EntitySpotanim
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Real instances, cache NPCs, hit queues and production scripts. HUD/drop delivery are not tested.
 */
class RoyalTitansCombatIntegrationTest {
    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    @Test
    fun `protected entry and intro join use native instance access and enforce the duo limit`() {
        val f = RoyalTitansTestWorld()
        val owner = f.newPlayer()
        assertTrue(f.start(owner))
        val partner = f.newPlayer(2)
        // Joining before Eldric's second-tick spawn must use his pending 600 HP.
        assertTrue(f.join(partner, owner.displayName))
        assertFalse(
            f.join(f.newPlayer(3), owner.displayName),
            "A private instance has at most two players",
        )
        f.tick(2)
        assertEquals(600, f.npc(partner, 12596).hitpoints)
        assertEquals(600, f.npc(partner, 14147).hitpoints)
    }

    @Test
    fun `late join boundary checks each Titan independently`() {
        for ((brandaHp, eldricHp) in listOf(449 to 600, 600 to 449, 450 to 450, 451 to 600)) {
            val f = RoyalTitansTestWorld()
            val owner = f.enter()
            f.tick(2)
            f.npc(owner, 12596).hitpoints = brandaHp
            f.npc(owner, 14147).hitpoints = eldricHp
            assertEquals(
                brandaHp >= 450 && eldricHp >= 450,
                f.join(f.newPlayer(2), owner.displayName),
            )
            framework().clearInlineMocks()
        }
        // Equality follows the currently isolated rule; captures do not resolve 450 vs 451.
    }

    @Test
    fun `both kill orders persist once using the last defeated Titan spawn clock`() {
        for (firstId in listOf(12596, 14147)) {
            val f = RoyalTitansTestWorld()
            val player = f.enter()
            val brandaSpawn = f.clock.cycle
            f.tick(6)
            val first = f.npc(player, firstId)
            val last = f.npc(player, if (firstId == 12596) 14147 else 12596)
            f.hit(player, first, 600)
            f.tick(2)
            assertTrue(f.completions.isEmpty(), "The first Titan is not a completed encounter")
            f.hit(player, last, 600)
            f.tick(2)
            val completion = f.completions.single()
            val lastSpawn = brandaSpawn + if (firstId == 12596) 2 else 0
            assertEquals(f.clock.cycle - lastSpawn - 1, completion.elapsedTicks)
            assertEquals(listOf(1), completion.participants.map { it.accountId })
            f.manager.finishTitanDeath(first)
            f.manager.finishTitanDeath(last)
            f.tick(3)
            assertEquals(1, f.completions.size)
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `failed completion survives both players leaving and retries once without resurrecting the run`() {
        val f = RoyalTitansTestWorld()
        val owner = f.enter()
        val partner = f.newPlayer(2)
        assertTrue(f.join(partner, owner.displayName))
        f.tick(6)
        val branda = f.npc(owner, 12596)
        val eldric = f.npc(owner, 14147)
        f.failCompletions = true
        f.hit(owner, branda, 600)
        f.hit(partner, eldric, 600)
        f.tick(2)
        assertTrue(f.completions.isEmpty())
        val pending = f.completionAttempts.single()
        assertEquals(2, pending.participants.size)
        assertEquals(0, owner.vars["varp.total_royal_titan_kills"])
        assertTrue(f.escape(owner))
        assertTrue(f.escape(partner))
        assertFalse(branda.isSlotAssigned || eldric.isSlotAssigned)
        val reconnected = f.newPlayer(1)
        f.failCompletions = false
        f.tick(9)
        assertTrue(f.completions.isEmpty(), "Respect the scheduled retry backoff")
        f.tick()
        assertEquals(listOf(pending), f.completions)
        assertEquals(listOf(pending, pending), f.completionAttempts)
        assertEquals(1, reconnected.vars["varp.total_royal_titan_kills"])
        assertEquals(1, partner.vars["varp.total_royal_titan_kills"])
        assertFalse(f.manager.isActive(reconnected) || f.manager.isActive(partner))
        assertFalse(f.manager.isEncounterNpc(branda) || f.manager.isEncounterNpc(eldric))
        f.tick(30)
        assertEquals(1, f.completions.size)
        assertEquals(2, f.completionAttempts.size)
    }

    @Test
    fun `unclaimed corpses survive and the first claim starts the captured reset sequence`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        f.tick(6)
        val branda = f.npc(player, 12596)
        val eldric = f.npc(player, 14147)
        f.hit(player, branda, 600)
        f.hit(player, eldric, 600)
        f.tick(2)
        f.tick(100)
        assertEquals(14148, branda.visType.id)
        assertTrue(branda.isSlotAssigned && eldric.isSlotAssigned)
        assertEquals(1, f.completions.size)
        f.manager.claim(player, branda, ClaimChoice.PAGES)
        val drops = f.objRegistry.count()
        assertTrue(drops > 0)
        f.tick(5)
        f.manager.claim(player, eldric, ClaimChoice.PAGES)
        assertEquals(drops, f.objRegistry.count(), "Only one personal reward across both corpses")
        f.tick(15)
        assertEquals(11990, f.lastAnimation(branda))
        f.tick()
        assertEquals(11992, f.lastAnimation(branda))
        assertEquals(11987, f.lastAnimation(eldric))
        f.tick(5)
        assertFalse(branda.isSlotAssigned || eldric.isSlotAssigned)
        f.tick(3)
        assertEquals(1, f.activeNpcs(player).size)
        assertEquals(11966, f.npc(player, 12596).pendingSequence.id)
        f.tick(2)
        assertEquals(2, f.activeNpcs(player).size)
        assertEquals(11963, f.npc(player, 14147).pendingSequence.id)
        assertEquals(0, f.manager.contribution(player))
    }

    @Test
    fun `initial spawn packets retain captured positions sequence and two tick separation`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        val branda = f.npc(player, RoyalTitansAssets.BRANDA_ACTIVE)
        assertEquals(f.tile(player, 26, 30), branda.coords)
        assertEquals(f.tile(player, 32, 30), branda.pendingFaceSquare)
        assertEquals(11966, branda.pendingSequence.id)
        assertFalse(branda.respawns)
        assertEquals(1, f.activeNpcs(player).size)
        f.tick()
        assertEquals(1, f.activeNpcs(player).size)
        f.tick()
        val eldric = f.npc(player, RoyalTitansAssets.ELDRIC_ACTIVE)
        assertEquals(f.tile(player, 34, 30), eldric.coords)
        assertEquals(f.tile(player, 28, 30), eldric.pendingFaceSquare)
        assertEquals(11963, eldric.pendingSequence.id)
        assertFalse(eldric.respawns)
    }

    @Test
    fun `summon and elemental spawn use captured slot2 while deaths use slot0`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        f.tick(6)
        val branda = f.npc(player, 12596)
        f.hit(player, branda, 100)
        f.tick(2)
        assertTrue(EntitySpotanim(3213, 0, 0, 2).packed in branda.pendingSpotanims)
        val elementals = mutableListOf<Npc>()
        repeat(4) {
            f.tick()
            val born = f.activeNpcs(player).filter { it.id in 14150..14151 && it !in elementals }
            for (npc in born) {
                assertEquals(if (npc.id == 14150) 11956 else 11954, npc.pendingSequence.id)
                assertTrue(
                    EntitySpotanim(if (npc.id == 14150) 3204 else 3202, 0, 0, 2).packed in
                        npc.pendingSpotanims
                )
            }
            elementals += born
        }
        assertEquals(6, elementals.size)
        for (npc in elementals) {
            f.hit(player, npc, 40)
        }
        f.tick(2)
        for (npc in elementals) {
            val fire = npc.id == 14150
            assertTrue(npc.isSlotAssigned)
            assertEquals(if (fire) 11957 else 11951, npc.pendingSequence.id)
            assertEquals(
                listOf(EntitySpotanim(if (fire) 3205 else 3203, 0, 0, 0).packed),
                npc.pendingSpotanims.toLongArray().toList(),
            )
        }
    }

    @Test
    fun `Blast execution sends one captured synth sequence to each duo viewer`() {
        val f = RoyalTitansTestWorld()
        val owner = f.enter()
        val partner = f.newPlayer(2)
        assertTrue(f.join(partner, owner.displayName))
        val outputs =
            listOf(owner, partner).map { player ->
                mutableListOf<Any>().also { messages ->
                    player.client =
                        object : Client<Any, Any> by NoopClient {
                            override fun write(message: Any) {
                                messages += message
                            }
                        }
                }
            }
        f.tick(6)
        val branda = f.npc(owner, 12596)
        f.hit(owner, branda, 400)
        f.tick()
        var ticks = 0
        while (f.lastAnimation(branda) != 11982 && ticks++ < 40) f.tick()
        assertEquals(11982, f.lastAnimation(branda))
        val expected = listOf(28, 88, 148).map { SynthSound(10155, 1, it) }
        outputs.forEach { assertEquals(expected, it.filterIsInstance<SynthSound>()) }
        f.tick(6)
        outputs.forEach { assertEquals(expected, it.filterIsInstance<SynthSound>()) }
    }

    @Test
    fun `queued crossing hit resolves from 221 to 194 without a transition cap`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        f.tick(6)
        val branda = f.npc(player, RoyalTitansAssets.BRANDA_ACTIVE)
        branda.hitpoints = 221
        f.hit(player, branda, 27)
        assertEquals(221, branda.hitpoints)
        assertEquals(0, f.manager.contribution(player))
        f.tick()
        assertEquals(194, branda.hitpoints)
        assertEquals(27, f.manager.contribution(player))
        assertEquals(11984, branda.pendingSequence.id)
    }

    @Test
    fun `native HP capping records only resolved Titan damage`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        f.tick(6)
        val branda = f.npc(player, RoyalTitansAssets.BRANDA_ACTIVE)
        f.hit(player, branda, 1000)
        f.tick()
        assertEquals(0, branda.hitpoints)
        assertEquals(600, f.manager.contribution(player))
        assertEquals(600, branda.damageContributions.damageBy(player))
    }

    @Test
    fun `transition uses actual teleport offsets rather than the shared stage counter`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        f.tick(6)
        val branda = f.npc(player, 12596)
        val eldric = f.npc(player, 14147)
        branda.hitpoints = 221
        f.hit(player, branda, 27)
        f.tick()
        val brandaBefore = branda.coords
        // Eldric may complete his already-issued retreat before his own teleport cast.
        val eldricBefore = f.tile(player, 36, 30)
        f.tick(2)
        assertEquals(brandaBefore, branda.coords)
        assertEquals(eldricBefore, eldric.coords)
        f.tick()
        assertEquals(f.tile(player, 30, 35), branda.coords)
        assertEquals(11966, f.lastAnimation(branda))
        assertEquals(f.tile(player, 31, 31), branda.pendingFaceSquare)
        assertEquals(eldricBefore, eldric.coords)
        f.tick()
        assertEquals(11983, f.lastAnimation(eldric))
        f.tick(2)
        assertEquals(eldricBefore, eldric.coords)
        f.tick()
        assertEquals(f.tile(player, 30, 25), eldric.coords)
        assertEquals(11963, f.lastAnimation(eldric))
    }

    @Test
    fun `lethal races refund once on final impact and exclude elemental damage from contribution`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        val elemental = f.summon(player).first()
        val initialContribution = f.manager.contribution(player)
        player.actionDelay = f.clock.cycle + 8
        val initialDelay = player.actionDelay
        f.hit(player, elemental, 30)
        f.hit(player, elemental, 30)
        f.hit(player, elemental, 30)
        assertEquals(initialDelay, player.actionDelay, "No refund when a hit is merely queued")
        f.tick()
        assertEquals(initialDelay - 1, player.actionDelay)
        assertEquals(40, elemental.damageContributions.damageBy(player))
        assertEquals(initialContribution, f.manager.contribution(player))
        f.tick()
        assertEquals(initialDelay - 1, player.actionDelay, "Death queue must not refund twice")
    }

    @Test
    fun `three same tick kills each reduce the remaining delay by one`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        val pack = f.summon(player).filter { it.id == RoyalTitansAssets.FIRE_ELEMENTAL }
        assertEquals(3, pack.size)
        player.actionDelay = f.clock.cycle + 8
        val initialDelay = player.actionDelay
        pack.forEach { f.hit(player, it, 40) }
        f.tick()
        assertEquals(initialDelay - 3, player.actionDelay)
    }

    @Test
    fun `refund uses remaining delay at impact and cannot create a negative delay`() {
        for (remaining in listOf(0, 1, 2, 7)) {
            val f = RoyalTitansTestWorld()
            val player = f.enter()
            val elemental = f.summon(player).first()
            player.actionDelay = f.clock.cycle + 1 + remaining
            f.hit(player, elemental, 40)
            f.tick()
            assertEquals(f.clock.cycle + (remaining - 1).coerceAtLeast(0), player.actionDelay)
        }
    }

    @Test
    fun `five wall segments form after four ticks and the middle spell removes three`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        f.tick(9)
        assertTrue(f.walls(player, 14152).isEmpty())
        f.tick(2)
        assertTrue(f.walls(player, 14152).isEmpty())
        f.tick()
        val walls = f.walls(player, 14152).sortedBy { it.coords.x }
        assertEquals(5, walls.size)
        walls.forEach { assertTrue(f.blockedByLoc(it.coords)) }
        val spell =
            assertNotNull(ServerCacheManager.getItem("obj.47_water_blast".asRSCM(RSCMType.OBJ)))
        f.spellHit(player, walls[2], spell)
        assertEquals(5, f.walls(player, 14152).size, "Walls remain until impact")
        f.tick()
        assertEquals(listOf(walls[0], walls[4]), f.walls(player, 14152).sortedBy { it.coords.x })
        assertTrue(walls.slice(1..3).none { it.isSlotAssigned })
        assertTrue(walls.slice(1..3).none { f.blockedByLoc(it.coords) })
        assertTrue(f.blockedByLoc(walls[0].coords))
        assertTrue(f.blockedByLoc(walls[4].coords))
    }

    @Test
    fun `a player on a forming wall omits only their segment`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        f.tick(9)
        player.coords = f.tile(player, 31, 34)
        f.tick(4)
        val walls = f.walls(player, 14152)
        assertEquals(4, walls.size)
        assertFalse(walls.any { it.coords == player.coords })
        assertFalse(f.blockedByLoc(player.coords))
    }

    @Test
    fun `invalid wall spell leaves every segment and its collision intact`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        f.tick(13)
        val walls = f.walls(player, 14152)
        f.spellHit(
            player,
            walls[2],
            assertNotNull(ServerCacheManager.getItem("obj.23_water_bolt".asRSCM(RSCMType.OBJ))),
        )
        assertEquals(walls, f.walls(player, 14152))
        walls.forEach { assertTrue(f.blockedByLoc(it.coords)) }
    }

    @Test
    fun `a later magic hit without spell metadata still earns its lethal refund`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        val elemental = f.summon(player).first()
        player.actionDelay = f.clock.cycle + 9
        val initialDelay = player.actionDelay
        f.hit(player, elemental, 30)
        f.tick()
        assertEquals(initialDelay, player.actionDelay)
        f.hit(player, elemental, 10)
        f.tick()
        assertEquals(initialDelay - 1, player.actionDelay)
        // This verifies native second-hit processing, not the fork's complete Twinflame cast.
    }

    @Test
    fun `elemental death presentation precedes repository deletion`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        val elemental = f.summon(player).first { it.id == RoyalTitansAssets.FIRE_ELEMENTAL }
        f.hit(player, elemental, 40)
        f.tick(2)
        assertTrue(elemental.isSlotAssigned)
        assertEquals(11957, f.lastAnimation(elemental))
        f.tick(3)
        assertFalse(elemental.isSlotAssigned)
    }

    @Test
    fun `either Titan transforms in place and cannot be claimed before both deaths`() {
        for ((active, corpse, inactive) in
            listOf(Triple(12596, 14148, 11990), Triple(14147, 14149, 11985))) {
            val f = RoyalTitansTestWorld()
            val player = f.enter()
            f.tick(6)
            val titan = f.npc(player, active)
            val originalUid = titan.uid
            val originalSlot = titan.slotId
            f.hit(player, titan, 600)
            f.tick(2)
            assertTrue(titan.isSlotAssigned)
            assertEquals(originalUid, titan.uid)
            assertEquals(originalSlot, titan.slotId)
            assertEquals(corpse, titan.visType.id)
            assertEquals(inactive, f.lastAnimation(titan))
            assertEquals(600, f.manager.contribution(player))
            assertFalse(f.activeNpcs(player).any { it !== titan && it.visType.id == corpse })
            f.manager.claim(player, titan, ClaimChoice.PAGES)
            assertEquals(0, f.objRegistry.count())
        }
    }

    @Test
    fun `pending and newly queued hits cannot delete or reward a transformed corpse`() {
        for ((active, corpse) in listOf(12596 to 14148, 14147 to 14149)) {
            val f = RoyalTitansTestWorld()
            val player = f.enter()
            f.tick(6)
            val titan = f.npc(player, active)
            val uid = titan.uid
            f.hit(player, titan, 600)
            f.hit(player, titan, 10, delay = 3)
            f.tick(2)
            assertEquals(corpse, titan.visType.id)
            assertFalse("queue.hit" in titan.queueList)
            f.hit(player, titan, 10)
            f.tick(6)
            assertTrue(titan.isSlotAssigned)
            assertEquals(uid, titan.uid)
            assertEquals(corpse, titan.visType.id)
            assertEquals(600, f.manager.contribution(player))
            assertEquals(0, f.objRegistry.count())
        }
    }

    @Test
    fun `inactive cache stats remain interactable without restoring the defeated Titan`() {
        val f = RoyalTitansTestWorld()
        val player = f.enter()
        f.tick(6)
        val titan = f.npc(player, 12596)
        f.hit(player, titan, 600)
        f.tick(2)
        val inactiveHp = assertNotNull(ServerCacheManager.getNpc(14148)).hitpoints
        assertEquals(inactiveHp, titan.baseHitpointsLvl)
        repeat(3) {
            titan.regenClock = 0
            NpcRegenProcessor().process(titan)
            assertEquals(inactiveHp, titan.hitpoints)
            assertEquals(14148, titan.visType.id)
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun cache() {
            synchronized(ServerCacheManager) {
                if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
            }
        }
    }
}

@OptIn(InternalApi::class)
internal class RoyalTitansTestWorld(
    private val hudBeforeInstances: Boolean? = null,
    private val nativeMovement: Boolean = true,
    private val random: GameRandom = DefaultGameRandom(42L),
    private val nativeAttackAi: Boolean = false,
    private val arenaCollision: Boolean = false,
) {
    val clock = MapClock(100)
    val collision = CollisionFlagMap()
    val updates = ZoneUpdateMap()
    val events = EventBus()
    val players = PlayerList()
    val npcList = NpcList()
    val npcRegistry = NpcRegistry(npcList, collision, events)
    private val postTick =
        NpcPostTickProcess(
            npcList,
            npcRegistry,
            NpcFaceSquareProcessor(),
            GameExceptionHandler { error, message ->
                throw AssertionError(message().toString(), error)
            },
        )
    private val animations = mutableMapOf<Npc, Int>()
    val spawnPositions = mutableMapOf<Npc, CoordGrid>()
    val npcs = NpcRepository(clock, npcRegistry, npcList)
    private val locStorage = LocZoneStorage()
    private val locRegistry = LocRegistryNormal(updates, collision, locStorage)
    private val activity = ZonePlayerActivityBitSet()
    private val regions =
        RegionRegistry(
            RegionListSmall(),
            RegionListLarge(),
            locRegistry,
            collision,
            locStorage,
            npcRegistry,
            ControllerRegistry(clock, ControllerList()),
            activity,
        )
    val instances =
        InstanceManager(
            RegionRepository(regions),
            npcs,
            players,
            events,
            InstanceAreaResolver(),
            clock,
            collision,
        )
    val objRegistry = ObjRegistry(updates)
    private val objects = ObjRepository(clock, objRegistry)
    private val movement = NpcMovementProcessor(collision, StepFactory(collision), events)
    private val modes =
        NpcModeProcessor::class.java.constructors.single().let { ctor ->
            ctor.newInstance(
                *ctor.parameterTypes
                    .map {
                        if (
                            nativeAttackAi &&
                                it ==
                                    org.rsmod.api.game.process.npc.mode.AiPlayerModeProcessor::class
                                        .java
                        )
                            org.rsmod.api.game.process.npc.mode.AiPlayerModeProcessor(
                                players,
                                AiPlayerInteractions(events, players),
                            )
                        else mock(it)
                    }
                    .toTypedArray()
            ) as NpcModeProcessor
        }
    val modifier = StandardNpcHitModifier(events)
    val world = WorldRepository(updates)
    val completions = mutableListOf<RoyalTitansCompletion>()
    val completionAttempts = mutableListOf<RoyalTitansCompletion>()
    var failCompletions = false
    var failDeaths = false
    val deathAttempts = mutableListOf<java.util.UUID>()
    val statistics =
        mock(RoyalTitansStatistics::class.java) { call ->
            if (call.method.name == "complete") {
                val completion = call.arguments[0] as RoyalTitansCompletion
                completionAttempts += completion
                if (failCompletions) throw SQLException("Simulated unavailable statistics database")
                completions += completion
                completion.participants.associate {
                    it.accountId to
                        RoyalTitansCompletionReceipt(
                            RoyalTitansStatisticsSnapshot(
                                personalKills = completions.size,
                                globalKills = completions.size,
                                personalBestTicks = completion.elapsedTicks,
                                globalBestTicks = completion.elapsedTicks,
                            ),
                            true,
                        )
                }
            } else if (call.method.name == "death") {
                deathAttempts += call.arguments[0] as java.util.UUID
                if (failDeaths) throw SQLException("Simulated unavailable death database")
                org.mockito.Mockito.RETURNS_DEFAULTS.answer(call)
            } else org.mockito.Mockito.RETURNS_DEFAULTS.answer(call)
        }

    val hud =
        if (hudBeforeInstances == null) mock(BossHpBarScript::class.java)
        else {
            val factory =
                mock(org.rsmod.api.player.protect.ProtectedAccessContextFactory::class.java) {
                    mock(ProtectedAccessContext::class.java)
                }
            BossHpBarScript(
                instances,
                mock(org.rsmod.api.bossbar.plugin.BossHpBarDamageContributor::class.java),
                org.rsmod.api.player.protect.ProtectedAccessLauncher(factory),
                players,
            )
        }

    val damageNotifications = mutableListOf<Pair<Player?, HitAttribution>>()
    private val instanceContribution =
        Class.forName("org.rsmod.api.instances.hook.InstanceNpcDamageContributor")
            .getConstructor(InstanceManager::class.java)
            .newInstance(instances) as NpcDamageContributor
    private val processor =
        StandardNpcHitProcessor(
            players,
            events,
            setOf(
                instanceContribution,
                object : NpcDamageContributor {
                    override fun onPlayerDamageNpc(npc: Npc, source: Player, damage: Int) = Unit

                    override fun onPlayerDamageNpc(
                        npc: Npc,
                        source: Player?,
                        damage: Int,
                        attribution: HitAttribution,
                    ) {
                        damageNotifications += source to attribution
                    }
                },
            ),
        )
    private val queues =
        NpcQueueProcessor(
            events,
            StandardNpcAccessLauncher(StandardNpcAccessContextFactory(random, modifier, processor)),
        )
    val accuracy = mock(org.rsmod.api.combat.formulas.AccuracyFormulae::class.java)
    val npcCombat =
        org.rsmod.api.combat.NvPCombat(
            accuracy,
            mock(org.rsmod.api.combat.formulas.MaxHitFormulae::class.java),
            world,
        )
    val bossDeps =
        org.rsmod.api.bosses.runtime.BossDeps(
            random,
            accuracy,
            world,
            npcs,
            players,
            clock,
            org.rsmod.game.queue.WorldQueueList(),
            collision,
            org.rsmod.api.bosses.runtime.EncounterRegistry(random),
            org.rsmod.api.bosses.runtime.BossExtensionRegistry(),
            npcCombat,
        )
    val manager =
        RoyalTitansEncounterManager(
            clock,
            instances,
            npcs,
            LocRepository(
                clock,
                LocRegistry(
                    locStorage,
                    locRegistry,
                    LocRegistryRegion(updates, collision, locStorage, regions),
                ),
                regions,
            ),
            objects,
            world,
            collision,
            random,
            AiPlayerInteractions(events, players),
            players,
            mock(DropTableRegistry::class.java),
            hud,
            modifier,
            statistics,
        )

    var denyTeleport = false
    private val protectedContext =
        mock(ProtectedAccessContext::class.java) { call ->
            when (call.method.name) {
                "getCollision" -> collision
                "getEventBus" -> events
                "getTeleportHooks" -> org.rsmod.api.player.hook.PlayerTeleportHooks.EMPTY
                "getTeleportValidator" ->
                    org.rsmod.api.player.hook.PlayerTeleportValidator(
                        setOf(
                            org.rsmod.api.player.hook.PlayerTeleportValidateHook { _, _, _ ->
                                if (denyTeleport) "Test teleport denied" else null
                            }
                        )
                    )
                "getAreaChecker" -> mock(call.method.returnType)
                else -> org.mockito.Mockito.RETURNS_DEFAULTS.answer(call)
            }
        }
    private val protectedLauncher =
        org.rsmod.api.player.protect.ProtectedAccessLauncher(
            mock(org.rsmod.api.player.protect.ProtectedAccessContextFactory::class.java) { call ->
                if (call.method.name == "create") protectedContext
                else org.mockito.Mockito.RETURNS_DEFAULTS.answer(call)
            }
        )
    val instanceScript =
        RoyalTitansWorld(
            org.rsmod.api.instances.BossInstanceRegistry(),
            manager,
            RoyalTitansScoreboard(statistics),
            players,
            protectedLauncher,
        )
    lateinit var nativeLifecycle: org.rsmod.api.player.hook.PlayerPostTickHook
        private set

    private val nativeInteractions =
        org.rsmod.api.game.process.npc.NpcInteractionProcessor::class
            .java
            .constructors
            .single()
            .let { ctor ->
                ctor.newInstance(
                    *ctor.parameterTypes
                        .map { type ->
                            when (type) {
                                GameRandom::class.java -> random
                                EventBus::class.java -> events
                                org.rsmod.api.route.BoundValidator::class.java ->
                                    org.rsmod.api.route.BoundValidator(collision)
                                org.rsmod.api.route.RayCastValidator::class.java ->
                                    org.rsmod.api.route.RayCastValidator(collision)
                                AiPlayerInteractions::class.java ->
                                    AiPlayerInteractions(events, players)
                                StandardNpcAccessLauncher::class.java ->
                                    StandardNpcAccessLauncher(
                                        StandardNpcAccessContextFactory(random, modifier, processor)
                                    )
                                NpcMovementProcessor::class.java -> movement
                                Set::class.java -> emptySet<Any>()
                                else -> mock(type)
                            }
                        }
                        .toTypedArray()
                ) as org.rsmod.api.game.process.npc.NpcInteractionProcessor
            }

    init {
        // Most combat tests use synthetic terrain with the two blocked Titan platforms.
        // Movement/damage regressions can instead decode the complete real source map.
        for (x in 0..7) for (z in 0..7) collision.allocateIfAbsent(2880 + x * 8, 9536 + z * 8, 0)
        if (arenaCollision) loadArenaCollision()
        else {
            for (x in (24..28) + (34..38)) for (z in 25..37) collision.add(
                2880 + x,
                9536 + z,
                0,
                CollisionFlag.BLOCK_WALK,
            )
        }
        val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        com.google.inject.Guice.createInjector(
                object : com.google.inject.AbstractModule() {
                    override fun configure() {
                        bind(InstanceManager::class.java).toInstance(instances)
                        bind(MapClock::class.java).toInstance(clock)
                    }
                }
            )
            .injectMembers(instanceScript)
        with(instanceScript) { context.startup() }
        with(NpcHitScript()) { context.startup() }
        with(PlayerHitScript()) { context.startup() }
        val retaliation =
            Class.forName("org.rsmod.api.combat.scripts.NpcRetaliateScript")
                .getConstructor(AiPlayerInteractions::class.java)
                .newInstance(AiPlayerInteractions(events, players)) as PluginScript
        with(retaliation) { context.startup() }
        if (hudBeforeInstances == true) with(hud) { context.startup() }
        val instanceLifecycle =
            Class.forName("org.rsmod.api.instances.hook.InstanceLifecycleScript")
                .getConstructor(InstanceManager::class.java, MapClock::class.java)
                .newInstance(instances, clock) as PluginScript
        with(instanceLifecycle) { context.startup() }
        nativeLifecycle = instanceLifecycle as org.rsmod.api.player.hook.PlayerPostTickHook
        if (hudBeforeInstances == false) with(hud) { context.startup() }
        val death = NpcDeath(npcs, players, objects, emptySet(), emptySet())
        with(NpcDeathScript(death)) { context.startup() }
        with(RoyalTitansCombat(manager, death, players, bossDeps)) { context.startup() }
        with(RoyalTitansLifecycle(manager)) { context.startup() }
    }

    fun enter(): Player =
        newPlayer().also { player ->
            assertTrue(start(player))
            activity.flag(ZoneKey.from(player.coords))
        }

    fun start(player: Player): Boolean {
        val launched = protectedLauncher.launch(player) { with(instanceScript) { enterArena() } }
        return launched && instances.sessionForPlayer(player)?.key == ROYAL_TITANS_INSTANCE_KEY
    }

    fun join(player: Player, name: String): Boolean {
        val launched = protectedLauncher.launch(player) { with(instanceScript) { joinArena(name) } }
        return launched && instances.sessionForPlayer(player)?.key == ROYAL_TITANS_INSTANCE_KEY
    }

    fun escape(player: Player): Boolean {
        val session = instances.sessionForPlayer(player) ?: return false
        val launched = protectedLauncher.launch(player) { with(instanceScript) { leaveArena() } }
        return launched && instances.sessionForPlayer(player) !== session
    }

    fun attack(npc: Npc, target: Player) {
        npc.apPlayer2(target, AiPlayerInteractions(events, players))
        StandardNpcAccessLauncher(StandardNpcAccessContextFactory(random, modifier, processor))
            .launch(npc) {
                events.publish(
                    this,
                    org.rsmod.api.npc.events.interact.AiPlayerEvents.Ap2(target, npc),
                )
            }
    }

    private fun loadArenaCollision() {
        val cache = dev.openrune.filesystem.Cache.load(java.nio.file.Path.of(".data/cache/SERVER"))
        try {
            val square = org.rsmod.map.square.MapSquareKey(45, 149)
            val tiles =
                dev.openrune.map.tile.MapTileDecoder.decode(
                    dev.openrune.map.util.InlineByteBuf(
                        assertNotNull(cache.data(dev.openrune.cache.MAPS, 11669, 0))
                    )
                )
            val locs =
                dev.openrune.map.loc.MapLocListDecoder.decode(
                    dev.openrune.map.util.InlineByteBuf(
                        assertNotNull(cache.data(dev.openrune.cache.MAPS, 11669, 1))
                    )
                )
            val builder = dev.openrune.map.GameMapBuilder()
            dev.openrune.map.GameMapDecoder.putMaps(collision, square, tiles)
            dev.openrune.map.GameMapDecoder.putLocs(builder, collision, square, tiles, locs)
            for ((key, zone) in builder.zoneBuilders) locStorage.mapLocs[key] = zone.build()
        } finally {
            cache.close()
        }
    }

    fun newPlayer(id: Int = 1): Player {
        val player =
            Player().apply {
                uuid = id.toLong()
                accountHash = id.toLong()
                accountId = id
                characterId = id
                username = "royaltest$id"
                displayName = if (id == 1) "Royal Test" else "Royal Test $id"
                observerUUID = id.toLong()
                slotId = id
                assignUid()
                coords = ROYAL_TITANS_OUTSIDE
                currentMapClock = clock.cycle
                processedMapClock = clock.cycle
                for (stat in
                    listOf(
                        "hitpoints",
                        "attack",
                        "strength",
                        "defence",
                        "ranged",
                        "magic",
                        "prayer",
                    )) {
                    statMap.setBaseLevel("stat.$stat", 99)
                    statMap.setCurrentLevel("stat.$stat", 99)
                }
                inv = invMap.getOrPut("inv.inv")
                worn = invMap.getOrPut("inv.worn")
            }
        players[id] = player
        return player
    }

    fun activeNpcs(player: Player): List<Npc> =
        instances.npcsForInstance(assertNotNull(instances.sessionForPlayer(player)).id).filter {
            it.isSlotAssigned
        }

    fun npc(player: Player, id: Int): Npc = activeNpcs(player).first { it.visType.id == id }

    fun tile(player: Player, x: Int, z: Int): CoordGrid =
        assertNotNull(
            instances.localCoord(
                assertNotNull(instances.sessionForPlayer(player)),
                RegionLocal(0, 45, 149, x, z),
            )
        )

    fun hit(player: Player, npc: Npc, damage: Int, delay: Int = 1, context: HitContext? = null) =
        npc.queueHit(
            source = player,
            delay = delay,
            type = HitType.Magic,
            damage = damage,
            modifier = modifier,
            context = context,
        )

    fun spellHit(player: Player, npc: Npc, spell: dev.openrune.types.ItemServerType) =
        npc.queueHit(
            source = player,
            delay = 1,
            type = HitType.Magic,
            damage = 0,
            modifier = modifier,
            sourceSecondary = spell,
        )

    fun walls(player: Player, id: Int): List<Npc> = activeNpcs(player).filter { it.id == id }

    fun blockedByLoc(tile: CoordGrid): Boolean =
        collision[tile.x, tile.z, tile.level] and CollisionFlag.LOC != 0

    fun lastAnimation(npc: Npc): Int? = animations[npc]

    fun summon(player: Player): List<Npc> {
        tick(6)
        hit(player, npc(player, RoyalTitansAssets.BRANDA_ACTIVE), 100)
        tick(6)
        return activeNpcs(player).filter { it.id in 14150..14151 }.also { assertEquals(6, it.size) }
    }

    fun tick(count: Int = 1) =
        repeat(count) {
            // Finish the preceding tick through the native post-tick pipeline. Otherwise a previous
            // spawn's pending priority masks later actions in this fixture but not in the server.
            postTick.process()
            clock.tick()
            players.forEach {
                it.currentMapClock = clock.cycle
                it.processedMapClock = clock.cycle
            }
            npcList
                .toList()
                .filter { it.isSlotAssigned }
                .forEach { npc ->
                    npc.currentMapClock = clock.cycle
                    npc.processedMapClock = clock.cycle
                    npc.previousCoords = npc.coords
                    if (npc.canProcess) npc.advanceActiveCoroutine()
                    if (npc.isSlotAssigned && npc.canProcess) queues.process(npc)
                    if (nativeMovement && npc.isSlotAssigned && npc.canProcess) {
                        modes.process(npc)
                        if (nativeAttackAi) nativeInteractions.process(npc)
                        else movement.process(npc)
                    }
                }
            manager.tickAll()
            npcList
                .filter { it.isSlotAssigned }
                .forEach { spawnPositions.putIfAbsent(it, it.coords) }
            npcList
                .filter { it.isSlotAssigned && it.pendingSequence != EntitySeq.NULL }
                .forEach { animations[it] = it.pendingSequence.id }
        }
}
