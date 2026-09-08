package org.rsmod.content.skills.magic.spell.attacks.arceuus

import com.google.inject.Guice
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfButtonOp
import kotlin.test.*
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.commons.CombatCorruption
import org.rsmod.api.combat.commons.magic.ArceuusSpellState
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.protect.ProtectedAccessLauncher.Companion.withProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.specials.energy.SpecialAttackEnergy
import org.rsmod.api.spells.MagicSpellModule
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.spells.runes.combo.*
import org.rsmod.api.spells.runes.compact.*
import org.rsmod.api.spells.runes.fake.*
import org.rsmod.api.spells.runes.staves.*
import org.rsmod.api.spells.runes.subs.*
import org.rsmod.api.spells.runes.unlimited.*
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.client.NoopClient
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.inv.InvObj
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.stat.PlayerSkillXPTable
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@OptIn(InternalApi::class)
class ArceuusUtilityCastRuntimeTest {
    @Test fun `all ten utility clicks consume runes grant cast XP and apply their effect`() {
        for (definition in ArceuusUtilitySpell.entries) {
            val f = UtilityCastFixture()
            val spell = f.spell(definition.spellObj)
            f.supply(spell)
            when (definition) {
                ArceuusUtilitySpell.VileVigour -> f.player.runEnergy = 750
                ArceuusUtilitySpell.Degrime -> f.player.inv[20] = InvObj("obj.unidentified_guam", 1)
                ArceuusUtilitySpell.DemonicOffering -> f.player.inv[20] = InvObj("obj.infernal_ashes", 1)
                ArceuusUtilitySpell.SinisterOffering -> f.player.inv[20] = InvObj("obj.dragon_bones", 1)
                else -> Unit
            }
            f.player.statMap.setCurrentLevel("stat.prayer", 50)
            val magicBefore = f.player.statMap.getFineXP("stat.magic")
            f.click(spell)
            f.tick(6)
            assertEquals((spell.castXp * 10).toInt(),
                f.player.statMap.getFineXP("stat.magic") - magicBefore, definition.name)
            spell.objReqs.forEach { assertEquals(0, f.player.inv.count(it.obj.internalName), definition.name) }
            when (definition) {
                ArceuusUtilitySpell.MarkOfDarkness -> assertTrue(ArceuusSpellState.isMarked(f.player, f.clock.cycle))
                ArceuusUtilitySpell.LesserCorruption, ArceuusUtilitySpell.GreaterCorruption ->
                    assertTrue(f.state.onCooldown(f.player, ArceuusCooldown.Corruption))
                ArceuusUtilitySpell.ShadowVeil -> assertTrue(ArceuusSpellState.hasShadowVeil(f.player, f.clock.cycle))
                ArceuusUtilitySpell.VileVigour -> {
                    assertEquals(1000, f.player.runEnergy)
                    assertEquals(25, f.player.stat("stat.prayer"))
                }
                ArceuusUtilitySpell.Degrime -> assertEquals(1, f.player.inv.count("obj.guam_leaf"))
                ArceuusUtilitySpell.DeathCharge -> {
                    assertTrue(f.state.hasDeathCharge(f.player))
                    f.state.onNpcKill(f.player)
                    assertEquals(150, f.specials.getSpecialEnergy(f.player))
                }
                ArceuusUtilitySpell.WardOfArceuus -> assertTrue(ArceuusSpellState.hasWard(f.player, f.clock.cycle))
                ArceuusUtilitySpell.DemonicOffering -> {
                    assertEquals(0, f.player.inv.count("obj.infernal_ashes"))
                    assertEquals(52, f.player.stat("stat.prayer"))
                }
                ArceuusUtilitySpell.SinisterOffering -> {
                    assertEquals(0, f.player.inv.count("obj.dragon_bones"))
                    assertEquals(51, f.player.stat("stat.prayer"))
                }
            }
            f.state.clear(f.player)
        }
    }

    @Test fun `every resurrection click enforces book and Prayer then summons correct thrall`() {
        for (definition in ArceuusThralls.definitions) {
            val f = UtilityCastFixture()
            val spell = f.spell(definition.spellObj)
            f.supply(spell)
            f.click(spell)
            assertFalse(f.npcList.any(), definition.displayName)
            spell.objReqs.forEach { assertEquals(reqCount(spell, it.obj.id), f.player.inv.count(it.obj.internalName)) }
            f.player.inv[20] = InvObj(assertNotNull(ServerCacheManager.getItem(25818)), 1)
            f.player.statMap.setCurrentLevel("stat.prayer", (definition.prayerDrain - 1).toByte())
            f.click(spell)
            assertFalse(f.npcList.any(), definition.displayName)
            f.player.statMap.setCurrentLevel("stat.prayer", 99)
            f.click(spell)
            assertEquals(definition.npcId, f.npcList.single().id)
            assertEquals(ArceuusThralls.definitions.indexOf(definition), f.player.vars["varbit.arceuus_resurrection_used"])
            assertEquals(0, f.player.vars["varbit.arceuus_resurrection_active"])
            assertEquals(99 - definition.prayerDrain, f.player.stat("stat.prayer"))
            spell.objReqs.forEach { assertEquals(0, f.player.inv.count(it.obj.internalName)) }
            f.tick(4)
            assertEquals(0, f.player.vars["varbit.arceuus_resurrection_active"])
            f.tick()
            assertEquals(1, f.player.vars["varbit.arceuus_resurrection_active"])
            f.tick(11)
            assertTrue(f.state.onCooldown(f.player, ArceuusCooldown.Thrall))
            f.tick()
            assertFalse(f.state.onCooldown(f.player, ArceuusCooldown.Thrall))
            f.tick(84)
            assertTrue(f.npcList.single().isSlotAssigned)
            f.tick()
            assertFalse(f.npcList.any())
            assertEquals(1, f.player.vars["varbit.arceuus_resurrection_active"])
            f.tick()
            assertEquals(0, f.player.vars["varbit.arceuus_resurrection_active"])
            f.state.clear(f.player)
        }
    }

    @Test fun `Mark and Ward send native buff timers matching effect expiry with boosted Magic`() {
        for (definition in listOf(ArceuusUtilitySpell.MarkOfDarkness, ArceuusUtilitySpell.WardOfArceuus)) {
            val f = UtilityCastFixture()
            val messages = mutableListOf<Any>()
            f.player.client = object : Client<Any, Any> by NoopClient {
                override fun write(message: Any) { messages += message }
            }
            f.player.statMap.setBaseLevel("stat.magic", 90)
            f.player.statMap.setCurrentLevel("stat.magic", 120)
            val mark = definition == ArceuusUtilitySpell.MarkOfDarkness
            val preference = if (mark) "varbit.buff_mark_of_darkness_disabled" else "varbit.buff_ward_of_arceuus_disabled"
            VarPlayerIntMapSetter.set(f.player, preference, 1)
            val key = if (mark) 3120 else 3126
            val duration = if (mark) 270 else 90
            val spell = f.spell(definition.spellObj)
            f.supply(spell)
            f.click(spell)
            assertTrue(messages.contains(RunClientScript(5926, listOf(key, if (mark) duration else 100))))
            f.tick(duration - 1)
            assertTrue(if (mark) ArceuusSpellState.isMarked(f.player, f.clock.cycle) else ArceuusSpellState.hasWard(f.player, f.clock.cycle))
            f.tick()
            assertFalse(if (mark) ArceuusSpellState.isMarked(f.player, f.clock.cycle) else ArceuusSpellState.hasWard(f.player, f.clock.cycle))
            assertEquals(RunClientScript(5926, listOf(key, 0)), messages.filterIsInstance<RunClientScript>().last())
            assertEquals(1, f.player.vars[preference])
            f.state.clear(f.player)
        }
    }

    private fun reqCount(spell: MagicSpell, id: Int) = spell.objReqs.filter { it.obj.id == id }.sumOf { it.count }

    companion object {
        @JvmStatic @BeforeAll fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }
    }
}

@OptIn(InternalApi::class)
private class UtilityCastFixture {
    val clock = MapClock(100)
    val events = EventBus()
    val context = mock(ProtectedAccessContext::class.java)
    val npcList = NpcList()
    val players = PlayerList()
    val collision = CollisionFlagMap()
    val world = WorldRepository(ZoneUpdateMap())
    val specials = SpecialAttackEnergy()
    val corruption = CombatCorruption(clock)
    val state = ArceuusUtilityState(clock, corruption, specials, DefaultGameRandom(42L),
        NpcRepository(clock, NpcRegistry(npcList, collision, events), npcList),
        world, NpcHitModifier {}, AiPlayerInteractions(events, players))
    val registry = Guice.createInjector(MagicSpellModule()).getInstance(MagicSpellRegistry::class.java)
    val player = Player().apply {
        uuid = 1L; observerUUID = 1L; slotId = 1; assignUid()
        coords = CoordGrid(3200, 3200, 0)
        currentMapClock = clock.cycle; processedMapClock = clock.cycle
        xpRate = 1.0; globalXpRate = 1.0
        for (stat in listOf("magic", "hitpoints", "prayer", "herblore")) {
            statMap.setXP("stat.$stat", PlayerSkillXPTable.getXPFromLevel(99))
            statMap.setBaseLevel("stat.$stat", 99)
            statMap.setCurrentLevel("stat.$stat", 99)
        }
        inv = invMap.getOrPut("inv.inv")
        worn = invMap.getOrPut("inv.worn")
    }
    init {
        collision.allocateIfAbsent(3200, 3200, 0)
        players[1] = player
        `when`(context.collision).thenReturn(collision)
        VarPlayerIntMapSetter.set(player, "varbit.spellbook", 3)
        val fakes = FakeRuneRepository(); val combos = ComboRuneRepository(); val compact = CompactRuneRepository()
        val unlimited = UnlimitedRuneRepository(); val staves = StaffSubstituteRepository(); val subs = RuneSubstituteRepository()
        val scriptContext = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        listOf(FakeRuneScript(fakes), ComboRuneScript(combos), CompactRuneScript(compact),
            UnlimitedRuneScript(unlimited), StaffSubstituteScript(staves), RuneSubstituteScript(subs))
            .forEach { with(it) { scriptContext.startup() } }
        val runes = MagicRuneManager(fakes, combos, compact, unlimited, staves, subs)
        with(ArceuusUtilitySpellScript(registry, runes, state, corruption, clock, events,
            mock(ProtectedAccessLauncher::class.java), world)) { scriptContext.startup() }
    }
    fun spell(obj: String) = assertNotNull(registry.getObjSpell(
        assertNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)))))
    fun supply(spell: MagicSpell) = spell.objReqs.forEachIndexed { index, req -> player.inv[index] = InvObj(req.obj, req.count) }
    fun click(spell: MagicSpell) {
        assertTrue(withProtectedAccess(player, context) {
            assertTrue(events.publish(this, IfOverlayButton(spell.component, -1, null, IfButtonOp.Op1)), spell.name)
        })
    }
    fun tick(count: Int = 1) = repeat(count) {
        clock.tick()
        player.currentMapClock = clock.cycle
        player.processedMapClock = clock.cycle
        player.advanceActiveCoroutine()
        state.tick(player)
    }
}
