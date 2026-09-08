package org.rsmod.content.bosses.dt2

import dev.openrune.ServerCacheManager
import dev.openrune.types.NpcMode
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.magic.ArceuusSpellState
import org.rsmod.api.instances.*
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.hit.DeferredPlayerHit
import org.rsmod.api.player.hit.modify
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.protect.ProtectedAccessLauncher.Companion.withProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.*
import org.rsmod.game.hit.Hit
import org.rsmod.game.inv.InvObj
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Real cache, NPC registry, inventory transactions and hit queues; scene/session IO and accuracy are mocked. */
class DukeRuntimeTest {
    @Test fun `both mushroom patches finish picking and stop safely when depleted`() {
        for (awakened in listOf(false, true)) {
            val f = DukeFixture(awakened, offsetX = 2048, offsetZ = 1024)
            val context = mock(ProtectedAccessContext::class.java)
            f.player.currentMapClock = f.clock.cycle
            f.player.processedMapClock = f.clock.cycle
            val patches = listOf(
                Triple(47524, f.tile(3048, 6453), Dt2Assets.MUSCA_MUSHROOM),
                Triple(47528, f.tile(3030, 6453), Dt2Assets.ARDER_MUSHROOM),
            )
            for ((locId, tile, item) in patches) {
                var completed = false
                fun pick() {
                    completed = false
                    assertTrue(withProtectedAccess(f.player, context) {
                        f.encounter.gather(f.run, this, locId, tile)
                        completed = true
                    })
                }
                pick()
                assertFalse(completed)
                repeat(24) { tick ->
                    f.tick()
                    f.player.currentMapClock = f.clock.cycle
                    f.player.processedMapClock = f.clock.cycle
                    f.player.advanceActiveCoroutine()
                    assertEquals((tick + 1) / 3, f.count(item))
                }
                assertTrue(completed, "awakened=$awakened, patch=$locId")
                assertFalse(f.player.isAccessProtected)
                assertEquals(EntitySeq.ZERO, f.player.pendingSequence)
                assertEquals(locId + 1, f.run.duke.scene[tile]?.id)
                assertEquals(8, f.run.duke.preparation.mushroomsPicked[locId])
                pick() // A stale click on the depleted patch must finish without another harvest.
                assertTrue(completed)
                assertEquals(8, f.count(item))
            }
        }
    }

    @Test fun `normal and awakened poisons wake once and first attack is five ticks later`() {
        for (awakened in listOf(false, true)) {
            val f = DukeFixture(awakened)
            f.give(Dt2Assets.ARDER_MUSCA_POISON, 5)
            repeat(if (awakened) 2 else 1) { f.encounter.feed(f.run, Dt2Assets.ARDER_MUSCA_POISON) }
            assertEquals(Dt2State.PREPARING, f.run.state)
            f.encounter.feed(f.run, Dt2Assets.ARDER_MUSCA_POISON)
            assertEquals(Dt2State.STARTING, f.run.state)
            assertEquals(if (awakened) 12195 else 12191, f.boss.visType.id)
            assertEquals(12191, f.boss.type.id, "Sleeping visual must retain the loot/combat type")
            assertEquals(if (awakened) 465 else 310, f.boss.magicDefenceLvl)
            assertEquals(f.boss.magicLvl, f.boss.baseMagicDefenceLvl)
            assertEquals(if (awakened) 316 else 275, f.boss.defenceLvl)
            assertEquals(awakened, f.boss.combatImmunities.statDrain)
            val remaining = f.count(Dt2Assets.ARDER_MUSCA_POISON)
            f.encounter.feed(f.run, Dt2Assets.ARDER_MUSCA_POISON)
            assertEquals(remaining, f.count(Dt2Assets.ARDER_MUSCA_POISON))
            f.tick(4)
            assertEquals(0, f.run.attackCounter)
            f.tick()
            assertEquals(1, f.run.attackCounter)
            assertEquals(NpcMode.None, f.boss.mode)
            assertTrue(f.boss.movementLocked)
        }
    }

    @Test fun `grinding and fermentation use atomic inventory exchanges and preserve spare poison`() {
        val f = DukeFixture()
        f.give(233, 1)
        f.give(Dt2Assets.MUSCA_MUSHROOM, 2)
        assertTrue(f.encounter.grind(f.run, true))
        assertEquals(6, f.count(Dt2Assets.MUSCA_POWDER))
        assertEquals(1, f.count(Dt2Assets.MUSCA_MUSHROOM))
        f.give(Dt2Assets.ARDER_POWDER, 6)
        f.give(Dt2Assets.SALAX_SALT, 6)
        val tile = f.tile(3034, 6438)
        f.encounter.ingredient(f.run, f.player, 47536, tile)
        assertEquals(0, f.count(Dt2Assets.MUSCA_POWDER))
        assertEquals(1, f.run.duke.preparation.vats.size)
        f.encounter.ingredient(f.run, f.player, 47537, tile)
        assertEquals(0, f.count(Dt2Assets.ARDER_MUSCA_POISON))
        f.tick(6)
        assertEquals(47539, f.run.duke.scene[tile]?.id)
        f.encounter.ingredient(f.run, f.player, 47539, tile)
        assertEquals(1, f.count(Dt2Assets.ARDER_MUSCA_POISON))
        assertTrue(f.run.duke.preparation.vats.isEmpty())
        f.encounter.prepare(f.run, preserveVats = true)
        assertEquals(1, f.count(Dt2Assets.ARDER_MUSCA_POISON))
    }

    @Test fun `invalid locations and insufficient ingredients cannot create poison`() {
        val f = DukeFixture()
        f.give(Dt2Assets.ARDER_POWDER, 6)
        f.give(Dt2Assets.MUSCA_POWDER, 6)
        f.give(Dt2Assets.SALAX_SALT, 5)
        f.encounter.ingredient(f.run, f.player, 47536, f.tile(3034, 6438))
        assertTrue(f.run.duke.preparation.vats.isEmpty())
        assertEquals(6, f.count(Dt2Assets.MUSCA_POWDER))
        f.give(Dt2Assets.SALAX_SALT, 1)
        f.encounter.ingredient(f.run, f.player, 47536, f.tile(3035, 6438))
        assertTrue(f.run.duke.preparation.vats.isEmpty())
    }

    @Test fun `full inventory retains a ready vat and clearing it cancels its old callback`() {
        val f = DukeFixture()
        val tile = f.tile(3034, 6438)
        f.run.duke.preparation.vats[3034 to 6438] = DukeVat(3, f.clock.cycle)
        f.encounter.prepare(f.run, preserveVats = true)
        for (i in 0 until f.player.inv.size) f.player.inv[i] = InvObj(requireNotNull(ServerCacheManager.getItem(4151)), 1)
        f.encounter.ingredient(f.run, f.player, 47539, tile)
        assertEquals(3, f.run.duke.preparation.vats[3034 to 6438]?.doses)
        f.encounter.inspectVat(f.run, tile, empty = true)
        assertTrue(f.run.duke.preparation.vats.isEmpty())
        assertEquals(47536, f.run.duke.scene[tile]?.id)
    }

    @Test fun `translated preparation spawns middle south north cones and wake removes every helper`() {
        val f = DukeFixture(offsetX = 2048, offsetZ = 1024)
        f.tick(2)
        assertEquals(setOf(12199, 12201), f.run.helpers.map { it.id }.toSet())
        assertTrue(f.run.helpers.all { it.coords.z == f.tile(3028, 6444).z })
        f.tick(2)
        assertTrue(f.run.helpers.any { it.id == 12200 && it.coords.z == f.tile(3028, 6440).z })
        f.tick(2)
        assertTrue(f.run.helpers.any { it.coords.z == f.tile(3028, 6448).z })
        val helpers = f.run.helpers.toList()
        f.wake()
        assertTrue(f.run.helpers.isEmpty())
        assertTrue(helpers.all { !it.isSlotAssigned })
    }

    @Test fun `magic impact waits four ticks and reads protection prayer at impact`() {
        val f = DukeFixture()
        f.player.coords = f.tile(3039, 6447)
        f.wake()
        f.tick(5)
        assertTrue(f.hits.isEmpty())
        f.tick(3)
        assertTrue(f.hits.isEmpty())
        VarPlayerIntMapSetter.set(f.player, "varbit.prayer_protectfrommagic", 1)
        f.tick()
        assertEquals(1, f.hits.size)
        assertTrue(f.hits.single().damage in 0..24)
        assertFalse(f.run.duke.perfect)
    }

    @Test fun `slam checks the tile two ticks later and protected chip allows a perfect kill`() {
        val f = DukeFixture()
        f.player.coords = f.tile(3036, 6451)
        VarPlayerIntMapSetter.set(f.player, "varbit.prayer_protectfrommelee", 1)
        f.wake()
        f.tick(5)
        assertEquals(1, f.hits.size)
        assertTrue(f.hits.single().damage in 0..5)
        f.player.coords = f.tile(3034, 6451)
        f.tick(2)
        assertEquals(1, f.hits.size)
        assertTrue(f.run.duke.perfect)
    }

    @Test fun `gaze behind pillar is safe and returning early is punished`() {
        for (safe in listOf(false, true)) {
            val f = DukeFixture()
            f.player.coords = f.tile(3034, 6451)
            f.wake()
            f.tick(30) // Wake + 5 standards + gaze launch.
            assertEquals(5, f.run.attackCounter)
            if (!safe) f.player.coords = f.tile(3036, 6451)
            f.tick(4)
            assertEquals(if (safe) 0 else 1, f.hits.size)
            f.tick(2)
            assertEquals(6, f.run.attackCounter)
        }
    }

    @Test fun `death logout floor change and session loss cancel pending impacts`() {
        for (transition in 0..4) {
            val f = DukeFixture()
            f.player.coords = f.tile(3039, 6447)
            f.wake(); f.tick(5)
            when (transition) {
                0 -> f.run.state = Dt2State.DYING
                1 -> f.player.pendingLogout = true
                2 -> f.player.coords = CoordGrid(f.player.coords.x, f.player.coords.z, 1)
                3 -> `when`(f.instances.sessionForPlayer(f.player)).thenReturn(null)
                4 -> { f.run.generation++; f.encounter.clear(f.run) }
            }
            f.tick(5)
            assertTrue(f.hits.isEmpty(), "transition=$transition")
        }
    }

    @Test fun `already queued hits cannot survive wake or reset`() {
        val f = DukeFixture()
        f.player.coords = f.tile(3036, 6451)
        f.wake(); f.tick(5)
        val hit = f.hits.single()
        assertTrue(hit.isValidNow())
        f.encounter.clear(f.run)
        assertFalse(hit.isValidNow())
    }

    @Test fun `death disables resources shows corpse and reset restores preparation`() {
        val f = DukeFixture()
        f.wake(); f.tick(5)
        f.run.state = Dt2State.DYING
        f.run.generation++
        f.encounter.clear(f.run)
        f.encounter.beginDeath(f.run)
        f.tick(2)
        assertEquals(47550, f.run.duke.scene[f.tile(3028, 6445)]?.id)
        f.tick(2)
        assertEquals(47566, f.run.duke.scene[f.tile(3039, 6433)]?.id)
        f.run.state = Dt2State.RESPAWNING
        f.encounter.showCorpse(f.run)
        assertEquals(12192, f.run.helpers.single().id)
        f.encounter.prepare(f.run, preserveVats = true)
        f.run.state = Dt2State.PREPARING
        assertTrue(f.run.helpers.isEmpty())
        assertEquals(12167, f.boss.visType.id)
        assertEquals(47565, f.run.duke.scene[f.tile(3039, 6433)]?.id)
        assertEquals(23, DukeRules.RESPAWN_TICKS)
    }

    @Test fun `awakened bile reserves an attack slot and cleanup removes pools and orb`() {
        val f = DukeFixture(awakened = true)
        f.player.coords = f.tile(3034, 6451)
        f.wake(); f.boss.hitpoints = 500
        f.tick(51) // First gas after eight standards and the first gaze.
        assertEquals(8, f.run.attackCounter)
        f.tick(11) // four gas spits at 0/3/5/7 then bile.
        assertEquals(8, f.run.attackCounter, "Bile must not be overwritten by a standard attack")
        f.tick()
        assertTrue(f.run.duke.pools.isNotEmpty())
        f.encounter.clear(f.run)
        assertTrue(f.run.duke.pools.isEmpty())
        assertTrue(f.run.helpers.isEmpty())
        assertEquals(0, f.run.duke.timeline.size)
    }

    @Test fun `ward is read at magic impact and expires without affecting protected chip`() {
        fun magic(wardExpiry: Int?): Int {
            val f = DukeFixture()
            f.player.coords = f.tile(3039, 6447)
            f.wake(); f.tick(8)
            if (wardExpiry != null) ArceuusSpellState.ward(f.player, wardExpiry)
            f.tick()
            return f.hits.single().damage
        }
        val unwarded = magic(null)
        assertEquals(8, unwarded)
        assertEquals(8, magic(110), "Ward rounds the reduction down, so an eight-damage hit is unchanged")
        assertEquals(unwarded, magic(109))
        fun chip(ward: Boolean): Int {
            val f = DukeFixture()
            f.player.coords = f.tile(3036, 6451)
            VarPlayerIntMapSetter.set(f.player, "varbit.prayer_protectfrommelee", 1)
            if (ward) ArceuusSpellState.ward(f.player, 200)
            f.wake(); f.tick(5)
            return f.hits.single().damage
        }
        assertEquals(chip(false), chip(true))
    }

    @Test fun `moving between bile drips avoids pools but standing still takes damage`() {
        val f = DukeFixture(awakened = true)
        f.wake(); f.boss.hitpoints = 1000
        f.tick(63)
        f.hits.clear()
        f.player.coords = f.tile(3034, 6450)
        f.tick()
        assertTrue(f.hits.isEmpty(), "A newly placed drip must not hit immediately")
        f.tick()
        assertEquals(1, f.hits.size)
        assertTrue(f.hits.single().damage in 9..12)
    }

    @Test fun `running leaves both bile tiles and pools expire nine ticks after creation`() {
        val f = DukeFixture(awakened = true)
        f.wake(); f.boss.hitpoints = 1000
        f.tick(63)
        val first = f.player.coords
        val expiry = f.run.duke.poisonTiles.getValue(first)
        f.tick(2)
        assertEquals(expiry, f.run.duke.poisonTiles[first], "Standing still must not renew a pool")
        f.player.previousCoords = first
        f.player.lastProcessedCoord = f.tile(3034, 6450)
        f.player.coords = f.tile(3034, 6449)
        f.tick()
        assertTrue(f.player.lastProcessedCoord in f.run.duke.pools)
        assertTrue(f.player.coords in f.run.duke.pools)
        f.player.previousCoords = f.player.coords
        f.tick(expiry - f.clock.cycle - 1)
        assertTrue(first in f.run.duke.pools)
        f.tick()
        assertFalse(first in f.run.duke.pools)
    }

    @Test fun `orb moves across the front row and drains combat and gathering skills`() {
        val f = DukeFixture(awakened = true)
        f.wake(); f.boss.hitpoints = 500
        f.tick(8)
        assertTrue(f.run.helpers.any { it.id == DukeRules.ORB })
        f.player.coords = f.tile(3043, 6451)
        f.tick()
        assertEquals(91, f.player.stat("stat.attack"))
        assertEquals(91, f.player.stat("stat.mining"))
        assertEquals(91, f.player.stat("stat.herblore"))
        assertTrue(f.run.helpers.none { it.id == DukeRules.ORB })
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
            val context = ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache())
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { context.startup() }
        }
    }
}

@OptIn(InternalApi::class)
private class DukeFixture(awakened: Boolean = false, val offsetX: Int = 0, val offsetZ: Int = 0) {
    val clock = MapClock(100)
    val events = EventBus()
    val collision = CollisionFlagMap()
    val npcList = NpcList()
    val npcs = NpcRepository(clock, NpcRegistry(npcList, collision, events), npcList)
    val updates = ZoneUpdateMap()
    val world = WorldRepository(updates)
    val instances = mock(InstanceManager::class.java)
    val locs = mock(LocRepository::class.java)
    val accuracy = mock(AccuracyFormulae::class.java)
    val player = Player().apply {
        uuid = 1L; observerUUID = 1L; slotId = 1; assignUid()
        coords = tile(3034, 6451)
        for (name in listOf("hitpoints", "prayer", "attack", "strength", "defence", "ranged", "magic", "mining", "herblore")) {
            statMap.setBaseLevel("stat.$name", 99)
            statMap.setCurrentLevel("stat.$name", 99)
        }
        inv = invMap.getOrPut("inv.inv")
        worn = invMap.getOrPut("inv.worn")
    }
    val boss = Npc(requireNotNull(ServerCacheManager.getNpc(12191)), tile(3036, 6452))
    val area = Dt2Boss.DUKE.area()
    val placement = (InstanceAreaResolver().resolve(area) as InstanceAreaResolver.Result.Ready).placement
    val session = InstanceSession(InstanceId(1), mutableSetOf(), 1L, Dt2Boss.DUKE.key,
        InstanceSpec(0, 1, 100, 100, area = area, settingsRowId = -1), placement, InstanceAccess.Private)
    val run = Dt2EncounterManager.Run(player, session, 1, Dt2Boss.DUKE,
        if (awakened) Dt2Difficulty.AWAKENED else Dt2Difficulty.NORMAL, boss, Dt2State.PREPARING,
        Dt2SeededRng(42), 42, Int.MAX_VALUE, 100)
    val encounter = DukeEncounter(clock, npcs, instances, world, locs, collision, accuracy)
    val hits = mutableListOf<Hit>()
    init {
        for (x in 3008..3071 step 8) for (z in 6400..6463 step 8) collision.allocateIfAbsent(x + offsetX, z + offsetZ, 0)
        npcs.add(boss, Int.MAX_VALUE)
        encounter.configureNpc(boss, awakened)
        boss.respawns = false
        boss.hitpoints = if (awakened) 1697 else 485
        `when`(instances.sessionForPlayer(player)).thenReturn(session)
        `when`(accuracy.getMeleeHitChance(boss, player, MeleeAttackType.Crush)).thenReturn(10000)
        encounter.prepare(run)
        ArceuusSpellState.clear(player)
    }
    fun tile(x: Int, z: Int) = CoordGrid(x + offsetX, z + offsetZ, 0)
    fun count(id: Int) = player.inv.count(requireNotNull(ServerCacheManager.getItem(id)).internalName)
    fun give(id: Int, count: Int) {
        val slot = (0 until player.inv.size).firstOrNull { player.inv[it]?.id == id }
            ?: (0 until player.inv.size).first { player.inv[it] == null }
        player.inv[slot] = InvObj(requireNotNull(ServerCacheManager.getItem(id)), count + (player.inv[slot]?.count ?: 0))
    }
    fun wake() {
        give(Dt2Assets.ARDER_MUSCA_POISON, 3)
        repeat(3) { encounter.feed(run, Dt2Assets.ARDER_MUSCA_POISON) }
    }
    fun tick(count: Int = 1) = repeat(count) {
        clock.tick()
        encounter.tick(run)
        val iterator = player.queueList.iterator()
        if (iterator != null) {
            while (iterator.hasNext()) {
                val queue = iterator.next()
                val deferred = queue.args as? DeferredPlayerHit ?: continue
                deferred.modifier.modify(deferred.builder, player)
                val hit = deferred.builder.build()
                if (hit.isValidNow()) hits += hit
                iterator.remove()
            }
            iterator.cleanUp()
        }
    }
}
