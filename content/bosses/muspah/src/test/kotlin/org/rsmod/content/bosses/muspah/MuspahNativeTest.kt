package org.rsmod.content.bosses.muspah

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.commons.CombatCorruption
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.events.interact.AiPlayerEvents
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryResult
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.region.Region
import org.rsmod.game.region.zone.RegionZoneCopy
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Characterizes the existing draft through native registration, not independent OSRS proof. */
@OptIn(InternalApi::class)
class MuspahNativeTest {
    @Test
    fun `ordinary projectile is invalidated after leaving its encounter`() {
        val f = MuspahFixture()
        f.start()
        f.until(104)
        f.attack()
        val hit = f.hits().single().second
        assertTrue(hit.isValidNow())
        f.encounters.escape(f.player)
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
        assertTrue(f.encounters.escape(f.player))
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
    val player =
        Player().apply {
            slotId = 1
            uuid = 1L
            assignUid()
            coords = MUSPAH_OUTSIDE
            statMap.setBaseLevel("stat.hitpoints", 99)
            statMap.setCurrentLevel("stat.hitpoints", 99)
            statMap.setBaseLevel("stat.prayer", 99)
            statMap.setCurrentLevel("stat.prayer", 99)
            inv = invMap.getOrPut("inv.inv")
            worn = invMap.getOrPut("inv.worn")
        }
    val players = PlayerList().apply { this[1] = player }
    val npcList = NpcList()
    val npcs = NpcRepository(clock, NpcRegistry(npcList, collision, events), npcList)
    private val regions =
        mock(RegionRepository::class.java) { call ->
            when (call.method.name) {
                "add" ->
                    Region(CoordGrid(6400, 6400), CoordGrid(6464, 6464), 1, 1).apply {
                        for (x in 0..7) for (z in 0..7) {
                            registerZone(
                                RegionZoneCopy(ZoneKey(352 + x, 528 + z, 0), 0, null),
                                ZoneKey(800 + x, 800 + z, 0),
                            )
                            collision.allocateIfAbsent(6400 + x * 8, 6400 + z * 8, 0)
                        }
                    }
                "isValid" -> true
                else -> RETURNS_DEFAULTS.answer(call)
            }
        }
    val instances =
        InstanceManager(regions, npcs, players, events, InstanceAreaResolver(), clock, collision)
    val bounds = mutableListOf<Int>()
    val random =
        object : GameRandom {
            override fun of(maxExclusive: Int): Int {
                bounds += maxExclusive
                return when (maxExclusive) {
                    2 -> if (melee) 1 else 0
                    3,
                    5 -> if (magic) 0 else maxExclusive - 1
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
    val encounters =
        MuspahEncounterManager(
            clock,
            instances,
            npcs,
            locs,
            AiPlayerInteractions(events, players),
            collision,
            random,
            NvPCombat(
                mock(AccuracyFormulae::class.java) { call ->
                    if (call.method.name.startsWith("roll")) true else RETURNS_DEFAULTS.answer(call)
                },
                mock(MaxHitFormulae::class.java),
                world,
            ),
            corruption,
            world,
        )
    val boss
        get() = npcList.first()

    init {
        collision.allocateIfAbsent(player.coords.x, player.coords.z, 0)
        val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        val death = NpcDeath(npcs, players, mock(ObjRepository::class.java), emptySet(), emptySet())
        with(MuspahCombat(encounters, death, players)) { context.startup() }
    }

    fun start() {
        assertTrue(encounters.enter(player))
    }

    fun local(x: Int, z: Int) = CoordGrid(6400 + x, 6400 + z)

    fun seq(name: String) = name.asRSCM(RSCMType.SEQ)

    fun spot(name: String) = name.asRSCM(RSCMType.SPOTANIM)

    fun until(tick: Int) {
        while (clock.cycle < tick) {
            npcList.forEach { it.pendingSequence = EntitySeq.NULL }
            clock.cycle++
            encounters.tick(player)
        }
    }

    fun attack() {
        boss.currentMapClock = clock.cycle
        boss.processedMapClock = clock.cycle
        boss.launch {
            events.publish(
                StandardNpcAccess(boss, this, accessContext()),
                AiPlayerEvents.Ap2(player, boss),
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
