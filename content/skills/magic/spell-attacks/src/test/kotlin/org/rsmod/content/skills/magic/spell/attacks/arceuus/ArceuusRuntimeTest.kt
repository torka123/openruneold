package org.rsmod.content.skills.magic.spell.attacks.arceuus

import dev.openrune.ServerCacheManager
import com.google.inject.Guice
import org.rsmod.api.spells.MagicSpellModule
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.spells.runes.combo.*
import org.rsmod.api.spells.runes.compact.*
import org.rsmod.api.spells.runes.fake.*
import org.rsmod.api.spells.runes.staves.*
import org.rsmod.api.spells.runes.subs.*
import org.rsmod.api.spells.runes.unlimited.*
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.inv.InvObj
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.game.process.npc.NpcMovementProcessor
import org.rsmod.api.game.process.npc.NpcPostTickProcess
import org.rsmod.api.game.process.npc.NpcFaceSquareProcessor
import org.rsmod.api.route.StepFactory
import org.rsmod.api.utils.logging.GameExceptionHandler
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.coroutine.GameCoroutine
import dev.openrune.types.NpcMode
import kotlin.test.*
import net.rsprot.protocol.game.outgoing.zone.payload.MapProjAnimV2
import org.junit.jupiter.api.BeforeAll
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.commons.CombatCorruption
import org.rsmod.api.combat.commons.magic.ArceuusSpellState
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.specials.energy.SpecialAttackEnergy
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.Hit
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Executes real summon, projectile, hit and expiry paths with a manual clock and seeded RNG. */
@OptIn(org.rsmod.annotations.InternalApi::class)
class ArceuusRuntimeTest {

    @Test fun `all 45 Arceuus spells accept questless players and retain level rune and book checks`() {
        val fakes = FakeRuneRepository()
        val combos = ComboRuneRepository()
        val compact = CompactRuneRepository()
        val unlimited = UnlimitedRuneRepository()
        val staves = StaffSubstituteRepository()
        val subs = RuneSubstituteRepository()
        val scripts: List<PluginScript> = listOf(FakeRuneScript(fakes), ComboRuneScript(combos),
            CompactRuneScript(compact), UnlimitedRuneScript(unlimited), StaffSubstituteScript(staves),
            RuneSubstituteScript(subs))
        val context = ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache())
        with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { context.startup() }
        scripts.forEach { with(it) { context.startup() } }
        val runes = MagicRuneManager(fakes, combos, compact, unlimited, staves, subs)
        val registry = Guice.createInjector(MagicSpellModule()).getInstance(MagicSpellRegistry::class.java)
        val spells = registry.allSpells().filter { it.spellbook == Spellbook.Arceuus }
        assertEquals(45, spells.size)
        for (spell in spells) {
            val f = Fixture()
            spell.objReqs.forEachIndexed { index, req -> f.player.inv[index] = InvObj(req.obj, req.count) }
            assertTrue(runes.canCastSpell(f.player, spell, Spellbook.Arceuus), spell.name)
            assertFalse(runes.canCastSpell(f.player, spell, Spellbook.Standard), spell.name)
            if (spell.levelReq > 1) {
                f.player.statMap.setCurrentLevel("stat.magic", 1)
                assertFalse(runes.canCastSpell(f.player, spell, Spellbook.Arceuus), spell.name)
                f.player.statMap.setCurrentLevel("stat.magic", 99)
            }
            if (spell.objReqs.isNotEmpty()) {
                assertFalse(runes.attemptCast(f.player, spell, Spellbook.Arceuus).isFailure(), spell.name)
                assertFalse(runes.canCastSpell(f.player, spell, Spellbook.Arceuus), spell.name)
            }
        }
    }

    @Test fun `all nine thralls spawn attack and expire without mapping errors`() {
        for (definition in ArceuusThralls.definitions) {
            val f = Fixture()
            f.state.summon(f.player, definition, 9)
            val thrall = f.thrall()
            assertEquals(NpcMode.None, thrall.mode)
            assertFalse(thrall.respawns)
            assertTrue(thrall.ignoreCombatInteractions)
            f.state.recordTarget(f.player, f.target, 0)
            f.tick(5)
            assertEquals(1, f.hits().size, definition.displayName)
            assertTrue(f.hits().single().damage in 0..definition.maxHit)
            assertTrue(f.hits().single().isValidNow())
            f.tick(7)
            assertFalse(thrall.isSlotAssigned)
            assertEquals(1, f.player.vars["varbit.arceuus_resurrection_active"])
            f.tick()
            assertEquals(0, f.player.vars["varbit.arceuus_resurrection_active"])
        }
    }

    @Test fun `skeletal thralls emit captured projectile fields at one two and three tile distances`() {
        for (definition in ArceuusThralls.definitions.filter { it.npcId in 10881..10883 }) {
            for ((distance, endFrame) in listOf(1 to 72, 2 to 77, 3 to 82)) {
                val f = Fixture()
                f.state.summon(f.player, definition, 99)
                f.target.coords = f.thrall().coords.translate(distance, 0)
                f.state.recordTarget(f.player, f.target)
                f.tick(4)
                val shot = f.zoneUpdates.updatedZones.values.flatMap { it.toList() }
                    .filterIsInstance<MapProjAnimV2>().single()
                assertEquals(1906, shot.id)
                assertEquals(100, shot.startHeight)
                assertEquals(124, shot.endHeight)
                assertEquals(57, shot.startTime)
                assertEquals(endFrame, shot.endTime)
                assertEquals(15, shot.angle)
                assertEquals(32, shot.progress)
                assertEquals(0, shot.sourceIndex)
                assertEquals(f.target.slotId + 1, shot.targetIndex)
                assertEquals(1, f.hits().size)
                f.state.clear(f.player)
            }
        }
    }

    @Test fun `death teleport and logout invalidate the summon and its launched hits`() {
        for (transition in 0..3) {
            val f = Fixture()
            f.state.summon(f.player, ArceuusThralls.definitions.first(), 99)
            f.state.recordTarget(f.player, f.target, 1)
            f.tick(5)
            val thrall = f.thrall()
            val hit = f.hits().single()
            when (transition) {
                0 -> f.player.statMap.setCurrentLevel("stat.hitpoints", 0)
                1 -> f.player.coords = CoordGrid(3200, 3200, 1)
                2 -> f.player.coords = CoordGrid(3000, 3000, 0)
                3 -> f.state.clear(f.player)
            }
            f.tick()
            assertFalse(thrall.isSlotAssigned, "transition=$transition")
            assertFalse(hit.isValidNow(), "transition=$transition")
            val before = f.target.hitpoints
            f.process(hit.copy(hitmark = hit.hitmark.copy(damage = 3)))
            assertEquals(before, f.target.hitpoints, "Cancelled thrall hit reached the actual NPC damage processor")
        }
    }

    @Test fun `all nine thralls follow and chase through real movement without despawning`() {
        for (definition in ArceuusThralls.definitions) {
            for (chasing in listOf(false, true)) {
                val f = Fixture()
                f.state.summon(f.player, definition, 99)
                val thrall = f.thrall()
                val start = thrall.coords
                if (chasing) {
                    f.target.removeBlockWalkCollision(f.collision, f.target.coords)
                    f.target.coords = start.translate(0, 10)
                    f.target.addBlockWalkCollision(f.collision, f.target.coords)
                    f.state.recordTarget(f.player, f.target, 1)
                } else {
                    f.player.coords = start.translate(0, 10)
                }
                repeat(10) {
                    f.tick()
                    thrall.currentMapClock = f.clock.cycle
                    thrall.processedMapClock = f.clock.cycle
                    thrall.previousCoords = thrall.coords
                    val before = thrall.coords
                    f.movement.process(thrall)
                    assertTrue(thrall.coords.chebyshevDistance(before) <= 1,
                        "${definition.displayName} chasing=$chasing moved ${thrall.coords.chebyshevDistance(before)} tiles")
                    f.postTick.process()
                    assertTrue(thrall.isSlotAssigned, definition.displayName)
                }
                assertTrue(thrall.coords.z > start.z, "Thrall never followed or chased")
            }
        }
    }

    @Test fun `new summon cannot validate a hit from an old summon`() {
        val f = Fixture()
        f.state.summon(f.player, ArceuusThralls.definitions.first(), 99)
        f.state.recordTarget(f.player, f.target, 1)
        f.tick(5)
        val hit = f.hits().single()
        f.state.summon(f.player, ArceuusThralls.definitions.last(), 99)
        assertFalse(hit.isValidNow())
    }

    @Test fun `thrall cannot launch between floors even if owner and new target share a floor`() {
        val f = Fixture()
        f.state.summon(f.player, ArceuusThralls.definitions.first(), 99)
        f.player.coords = CoordGrid(3200, 3200, 1)
        f.target.coords = CoordGrid(3202, 3200, 1)
        f.state.recordTarget(f.player, f.target, 1)
        f.tick(5)
        assertTrue(f.hits().isEmpty())
    }

    @Test fun `missed melee ranged and spell launches acquire a thrall target before impact`() {
        for (style in 0..2) {
            val f = Fixture()
            val manager = org.rsmod.api.combat.manager.PlayerAttackManager(
                DefaultGameRandom(42L), f.events, f.world,
                org.mockito.Mockito.mock(org.rsmod.api.combat.formulas.AccuracyFormulae::class.java),
                org.mockito.Mockito.mock(org.rsmod.api.combat.formulas.MaxHitFormulae::class.java),
                NpcHitModifier {},
                org.mockito.Mockito.mock(org.rsmod.api.player.interact.NpcInteractions::class.java),
                org.mockito.Mockito.mock(org.rsmod.api.player.interact.NpcTInteractions::class.java),
                org.mockito.Mockito.mock(org.rsmod.api.player.interact.PlayerInteractions::class.java),
                org.mockito.Mockito.mock(org.rsmod.api.player.interact.PlayerTInteractions::class.java),
                emptySet(), emptySet(), emptySet(), emptySet(),
                setOf(org.rsmod.api.npc.hit.NpcAttackLaunchHook { player, npc -> f.state.recordTarget(player, npc) }),
            )
            f.state.summon(f.player, ArceuusThralls.definitions.first(), 99)
            when (style) {
                0 -> manager.queueMeleeHit(f.player, f.target, damage = 0, delay = 20)
                1 -> manager.queueRangedHit(f.player, f.target, null, 0, 120, 20)
                2 -> manager.queueSplashHit(f.player, f.target, null, 120, 20)
            }
            f.tick(5)
            assertEquals(2, f.hits().size, "Owner miss and thrall attack must both be queued: style=$style")

            val other = Npc(assertNotNull(ServerCacheManager.getNpc(82)), f.target.coords.translate(0, 1))
            f.npcs.add(other, Int.MAX_VALUE)
            // A delayed secondary magic hit must not retarget the thrall away from the chosen foe.
            manager.queueMagicHit(f.player, other, null, 1, 0, 1, notifyAttackLaunch = false)
            f.state.onNpcDamage(f.player, other, 1)
            f.tick(4)
            assertEquals(3, f.hits().size, "A delayed impact stole the thrall target: style=$style")
            f.state.clear(f.player)
        }
    }

    @Test fun `corruption drains three escalating Prayer hits and Ward cures and prevents it`() {
        for (greater in listOf(false, true)) {
            for (marked in listOf(false, true)) {
                val f = Fixture()
                val corruption = CombatCorruption(f.clock)
                val before = f.player.statMap.getCurrentLevel("stat.prayer").toInt()
                val interval = if (marked) 5 else 10
                assertTrue(corruption.apply(f.player, strong = greater, accelerated = marked))
                assertFalse(corruption.apply(f.player, strong = greater, accelerated = marked))
                var drained = 0
                repeat(3) { stage ->
                    repeat(interval - 1) { f.clock.tick(); corruption.tick(f.player) }
                    assertEquals(before - drained, f.player.statMap.getCurrentLevel("stat.prayer").toInt())
                    f.clock.tick(); corruption.tick(f.player)
                    drained += (stage + 1) * if (greater) 2 else 1
                    assertEquals(before - drained, f.player.statMap.getCurrentLevel("stat.prayer").toInt())
                }
                assertFalse(corruption.isActive(f.player))
                assertTrue(corruption.apply(f.player, strong = greater))
                corruption.clear(f.player)
                ArceuusSpellState.ward(f.player, f.clock.cycle + 99)
                assertFalse(corruption.apply(f.player, strong = greater))
                repeat(30) { f.clock.tick(); corruption.tick(f.player) }
                assertEquals(before - drained, f.player.statMap.getCurrentLevel("stat.prayer").toInt())
                f.state.clear(f.player)
            }
        }
    }


    @Test fun `death charge restores special energy once and expires safely`() {
        val f = Fixture()
        f.state.armDeathCharge(f.player)
        f.state.onNpcKill(f.player)
        f.state.onNpcKill(f.player)
        assertEquals(150, f.specials.getSpecialEnergy(f.player))
        f.state.armDeathCharge(f.player)
        f.tick(100)
        assertFalse(f.state.hasDeathCharge(f.player))
        f.state.onNpcKill(f.player)
        assertEquals(150, f.specials.getSpecialEnergy(f.player))
    }

    @Test fun `native cooldown and active flags expire without changing buff preferences`() {
        val f = Fixture()
        for (cooldown in ArceuusCooldown.entries) {
            f.state.setCooldown(f.player, cooldown, 2)
            assertEquals(1, f.player.vars[cooldown.varbit], cooldown.name)
        }
        f.state.summon(f.player, ArceuusThralls.definitions.first(), 6)
        assertEquals(0, f.player.vars["varbit.arceuus_resurrection_active"])
        f.tick(2)
        for (cooldown in ArceuusCooldown.entries) {
            assertFalse(f.state.onCooldown(f.player, cooldown))
            assertEquals(0, f.player.vars[cooldown.varbit], cooldown.name)
        }
        assertEquals(0, f.player.vars["varbit.arceuus_resurrection_active"])
        f.tick(3)
        assertEquals(1, f.player.vars["varbit.arceuus_resurrection_active"])
        f.tick(5)
        assertEquals(0, f.player.vars["varbit.arceuus_resurrection_active"])
        f.state.clear(f.player)
    }

    @Test fun `Death Charge upgrade consumes exactly two eligible kills and honours range PvP and exclusions`() {
        val f = Fixture()
        VarPlayerIntMapSetter.set(f.player, "varbit.death_charge_scroll_used", 1)
        f.state.armDeathCharge(f.player)
        assertEquals(2, f.player.vars["varbit.arceuus_death_charge_active"])
        f.target.hitpoints = 0
        f.player.coords = f.target.coords.translate(17, 0)
        f.state.onNpcDamage(f.player, f.target, 1)
        assertEquals(0, f.specials.getSpecialEnergy(f.player))
        f.player.coords = f.target.coords.translate(1, 0)
        VarPlayerIntMapSetter.set(f.player, "varp.lastcombat_pvp", f.clock.cycle)
        f.state.onNpcDamage(f.player, f.target, 1)
        assertEquals(0, f.specials.getSpecialEnergy(f.player))
        f.tick(11)
        val lung = Npc(assertNotNull(ServerCacheManager.getNpc(5914)), f.player.coords).apply { hitpoints = 0 }
        f.state.onNpcDamage(f.player, lung, 1)
        assertEquals(2, f.player.vars["varbit.arceuus_death_charge_active"])
        f.state.onNpcDamage(f.player, f.target, 1)
        assertEquals(150, f.specials.getSpecialEnergy(f.player))
        assertEquals(1, f.player.vars["varbit.arceuus_death_charge_active"])
        f.state.onNpcDamage(f.player, f.target, 1)
        assertEquals(300, f.specials.getSpecialEnergy(f.player))
        assertEquals(0, f.player.vars["varbit.arceuus_death_charge_active"])
        f.state.onNpcDamage(f.player, f.target, 1)
        assertEquals(300, f.specials.getSpecialEnergy(f.player))
        f.state.clear(f.player)
    }

    @Test fun `buff casting and expiry preserve the players buff display settings`() {
        val f = Fixture()
        for (disabled in 0..1) {
            VarPlayerIntMapSetter.set(f.player, "varbit.buff_mark_of_darkness_disabled", disabled)
            VarPlayerIntMapSetter.set(f.player, "varbit.buff_ward_of_arceuus_disabled", disabled)
            ArceuusSpellState.mark(f.player, f.clock.cycle + 2)
            ArceuusSpellState.ward(f.player, f.clock.cycle + 2)
            assertTrue(ArceuusSpellState.isMarked(f.player, f.clock.cycle))
            assertTrue(ArceuusSpellState.hasWard(f.player, f.clock.cycle))
            assertEquals(disabled, f.player.vars["varbit.buff_mark_of_darkness_disabled"])
            assertEquals(disabled, f.player.vars["varbit.buff_ward_of_arceuus_disabled"])
            f.tick(2)
            assertFalse(ArceuusSpellState.hasWard(f.player, f.clock.cycle))
            assertEquals(disabled, f.player.vars["varbit.buff_ward_of_arceuus_disabled"])
        }
    }

    @Test fun `both unnoted variants of each ensouled head are accepted`() {
        assertEquals(46, ArceuusReanimations.byHead.size)
        for (definition in ArceuusReanimations.definitions) {
            assertEquals(2, definition.headIds.size)
            for (id in definition.headIds) {
                val item = assertNotNull(ServerCacheManager.getItem(id))
                assertEquals(0, item.certtemplate, item.name)
                val npc = assertNotNull(ServerCacheManager.getNpc(definition.npcId))
                assertEquals(npc.name.removePrefix("Reanimated ").lowercase(),
                    item.name.removePrefix("Ensouled ").removeSuffix(" head").lowercase())
            }
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() {
            synchronized(ServerCacheManager) {
                if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
            }
        }
    }
}

@OptIn(InternalApi::class)
private class Fixture {
    val clock = MapClock(100)
    val events = EventBus()
    val collision = CollisionFlagMap()
    val list = NpcList()
    val players = PlayerList()
    val npcRegistry = NpcRegistry(list, collision, events)
    val npcs = NpcRepository(clock, npcRegistry, list)
    val movement = NpcMovementProcessor(collision, StepFactory(collision), events)
    val postTick = NpcPostTickProcess(list, npcRegistry, NpcFaceSquareProcessor(),
        GameExceptionHandler { error, message -> throw AssertionError(message().toString(), error) })
    val zoneUpdates = ZoneUpdateMap()
    val world = WorldRepository(zoneUpdates)
    val specials = SpecialAttackEnergy()
    val state = ArceuusUtilityState(clock, CombatCorruption(clock), specials, DefaultGameRandom(42L),
        npcs, world, NpcHitModifier {}, AiPlayerInteractions(events, players))
    val player = Player().apply {
        uuid = 1L
        observerUUID = 1L
        slotId = 1
        assignUid()
        coords = CoordGrid(3200, 3200, 0)
        for (stat in listOf("hitpoints", "magic", "prayer")) {
            statMap.setBaseLevel("stat.$stat", 99)
            statMap.setCurrentLevel("stat.$stat", 99)
        }
        inv = invMap.getOrPut("inv.inv")
        worn = invMap.getOrPut("inv.worn")
    }
    val target = Npc(requireNotNull(ServerCacheManager.getNpc(82)), CoordGrid(3202, 3200, 0))
    init {
        collision.allocateIfAbsent(3200, 3200, 0)
        collision.allocateIfAbsent(3200, 3208, 0)
        collision.allocateIfAbsent(3200, 3200, 1)
        players[1] = player
        npcs.add(target, Int.MAX_VALUE)
    }
    fun thrall() = list.first { it.id in 10878..10886 }
    fun tick(count: Int = 1) = repeat(count) { clock.tick(); state.tick(player) }
    fun hits(): List<Hit> = buildList {
        val iterator = target.queueList.iterator() ?: return@buildList
        while (iterator.hasNext()) (iterator.next().args as? Hit)?.let { add(it) }
        iterator.cleanUp()
    }
    fun process(hit: Hit) {
        val processor = StandardNpcHitProcessor(players, events, emptySet())
        val access = StandardNpcAccess(target, GameCoroutine(), StandardNpcAccessContext(
            { DefaultGameRandom(42L) }, { NpcHitModifier {} }, { processor }))
        with(processor) { access.process(hit) }
    }
}
