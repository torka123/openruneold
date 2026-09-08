package org.rsmod.content.skills.magic.spell.attacks.arceuus

import com.google.inject.Guice
import dev.openrune.ServerCacheManager
import dev.openrune.types.NpcMode
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.magic.DarkLureState
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.config.refs.params
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.protect.ProtectedAccessLauncher.Companion.withProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.game.process.npc.NpcMovementProcessor
import org.rsmod.api.route.StepFactory
import org.rsmod.api.spells.MagicSpellModule
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.spells.attack.*
import org.rsmod.api.spells.runes.combo.*
import org.rsmod.api.spells.runes.compact.*
import org.rsmod.api.spells.runes.fake.*
import org.rsmod.api.spells.runes.staves.*
import org.rsmod.api.spells.runes.subs.*
import org.rsmod.api.spells.runes.unlimited.*
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitImpact
import org.rsmod.game.hit.HitImpactHandler
import org.rsmod.game.inv.InvObj
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.stat.PlayerSkillXPTable
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@OptIn(InternalApi::class)
class ArceuusCombatCastRuntimeTest {
    @Test fun `all seven target spells execute registered handlers and consume actual runes`() {
        for (definition in ArceuusCombatRules.definitions) {
            val f = CombatCastFixture(definition)
            val before = f.player.statMap.getFineXP("stat.magic")
            f.cast()
            assertEquals((f.spell.castXp * 10).toInt(),
                f.player.statMap.getFineXP("stat.magic") - before, f.spell.name)
            f.spell.objReqs.forEach { assertEquals(0, f.player.inv.count(it.obj.internalName), f.spell.name) }
            assertEquals(definition.maxHit, f.queuedDamage, f.spell.name)
            if (definition.effect == ArceuusCombatEffect.DarkLure) {
                assertTrue(f.handler.canTargetNonCombatNpc(f.target))
                assertTrue(f.target.isValidTarget(), "Impling must reach the engine spell dispatch")
                f.impact()
                assertTrue(DarkLureState.preventsRetreat(f.target))
            }
            f.close()
        }
    }

    @Test fun `Dark Lure blocks competing owners follows for ten ticks then restores wandering`() {
        val f = CombatCastFixture(lure())
        val originalMode = f.target.mode
        f.cast(); f.impact()
        assertEquals(NpcMode.None, f.target.mode)
        val other = f.otherPlayer()
        assertNotNull(DarkLureState.rejection(other, f.target, 100))
        f.player.coords = CoordGrid(3205, 3200, 0)
        DarkLureState.tick(f.player, 101)
        val before = f.target.coords
        f.movement.process(f.target)
        assertEquals(1, maxOf(kotlin.math.abs(f.target.coords.x - before.x), kotlin.math.abs(f.target.coords.z - before.z)), "Lured NPC must take one legal walking step")
        for (cycle in 102..109) DarkLureState.tick(f.player, cycle)
        assertNotNull(DarkLureState.rejection(other, f.target, 109))
        DarkLureState.tick(f.player, 110)
        assertNull(DarkLureState.rejection(other, f.target, 110))
        assertEquals(originalMode, f.target.mode)
        assertNotNull(DarkLureState.rejection(f.player, f.target, 116))
        assertNull(DarkLureState.rejection(f.player, f.target, 117))
        f.close()
    }

    @Test fun `Dark Lure splash still consumes recharge but never changes the target`() {
        val f = CombatCastFixture(lure())
        f.splash = true
        val mode = f.target.mode
        f.cast()
        assertNull(f.impactHandler)
        assertFalse(DarkLureState.preventsRetreat(f.target))
        assertEquals(mode, f.target.mode)
        assertNotNull(DarkLureState.rejection(f.player, f.target, 116))
        assertNull(DarkLureState.rejection(f.player, f.target, 117))
        f.close()
    }

    @Test fun `lure cleanup releases an impling on owner logout death plane change or target removal`() {
        for (reason in 0..3) {
            val f = CombatCastFixture(lure())
            val mode = f.target.mode
            f.cast(); f.impact()
            when (reason) {
                0 -> f.player.pendingLogout = true
                1 -> f.player.statMap.setCurrentLevel("stat.hitpoints", 0)
                2 -> f.player.coords = CoordGrid(3200, 3200, 1)
                3 -> f.npcs.del(f.target, Int.MAX_VALUE)
            }
            DarkLureState.tick(f.otherPlayer(), 101)
            assertFalse(DarkLureState.preventsRetreat(f.target))
            if (reason != 3) assertEquals(mode, f.target.mode)
            f.close()
        }
    }

    private fun lure() = ArceuusCombatRules.definitions.single { it.effect == ArceuusCombatEffect.DarkLure }

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
private class CombatCastFixture(val definition: ArceuusCombatDefinition) {
    val clock = MapClock(100)
    val events = EventBus()
    val collision = CollisionFlagMap()
    val list = NpcList()
    val npcs = NpcRepository(clock, NpcRegistry(list, collision, events), list)
    val movement = NpcMovementProcessor(collision, StepFactory(collision), events)
    val player = makePlayer(1)
    val context = mock(ProtectedAccessContext::class.java)
    val spells = Guice.createInjector(MagicSpellModule()).getInstance(MagicSpellRegistry::class.java)
    val spell = assertNotNull(spells.getObjSpell(assertNotNull(ServerCacheManager.getItem(definition.obj.asRSCM(RSCMType.OBJ)))))
    val target = Npc(
        if (definition.effect == ArceuusCombatEffect.DarkLure) assertNotNull(ServerCacheManager.getNpc(1635))
        else ServerCacheManager.getNpcs().values.first { it.param(params.demon) != 0 },
        CoordGrid(3200, 3203, 0))
    var splash = false
    var queuedDamage: Int? = null
    var impactHandler: HitImpactHandler? = null
    val handler: SpellAttack
    init {
        collision.allocateIfAbsent(3200, 3200, 0)
        collision.allocateIfAbsent(3208, 3200, 0)
        npcs.add(target, Int.MAX_VALUE)
        `when`(context.collision).thenReturn(collision)
        VarPlayerIntMapSetter.set(player, "varbit.spellbook", 3)
        val fakes = FakeRuneRepository(); val combos = ComboRuneRepository(); val compact = CompactRuneRepository()
        val unlimited = UnlimitedRuneRepository(); val staves = StaffSubstituteRepository(); val subs = RuneSubstituteRepository()
        val scriptContext = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        listOf(FakeRuneScript(fakes), ComboRuneScript(combos), CompactRuneScript(compact),
            UnlimitedRuneScript(unlimited), StaffSubstituteScript(staves), RuneSubstituteScript(subs))
            .forEach { with(it) { scriptContext.startup() } }
        val runes = MagicRuneManager(fakes, combos, compact, unlimited, staves, subs)
        val attacks = mock(PlayerAttackManager::class.java) { call ->
            when (call.method.name) {
                "rollSpellAccuracy" -> !splash
                "rollSpellMaxHit" -> definition.maxHit
                "spawnProjectile" -> ProjAnim.fromBoundsToNpc(player.bounds(), target, definition.projectile!!, "projanim.magic_spell")
                "queueMagicHit" -> {
                    queuedDamage = call.arguments[3] as Int
                    impactHandler = call.arguments.filterIsInstance<HitImpactHandler>().firstOrNull()
                    mock(Hit::class.java)
                }
                "queueSplashHit" -> mock(Hit::class.java)
                else -> RETURNS_DEFAULTS.answer(call)
            }
        }
        val manager = SpellAttackManager(attacks, runes, DefaultGameRandom(42L))
        val registry = SpellAttackRegistry()
        val repository = SpellAttackRepository(registry)
        with(ArceuusCombatSpells(DefaultGameRandom(42L), clock)) { repository.register(manager) }
        handler = assertNotNull(registry[definition.obj])
        spell.objReqs.forEachIndexed { i, req -> player.inv[i] = InvObj(req.obj, req.count) }
    }
    fun cast() { assertTrue(withProtectedAccess(player, context) { handler.attack(this, target, CombatAttack.Spell(null, spell, false)) }) }
    fun impact() { assertNotNull(impactHandler).onImpact(HitImpact(target, mock(Hit::class.java), queuedDamage ?: 0)) }
    fun otherPlayer() = makePlayer(2)
    fun close() { player.pendingLogout = true; DarkLureState.tick(player, 1000) }
    private fun makePlayer(id: Int) = Player().apply {
        uuid = id.toLong(); observerUUID = uuid; slotId = id; assignUid()
        coords = CoordGrid(3200, 3200, 0)
        currentMapClock = 100; processedMapClock = 100; xpRate = 1.0; globalXpRate = 1.0
        for (stat in listOf("magic", "hitpoints", "prayer")) {
            statMap.setXP("stat.$stat", PlayerSkillXPTable.getXPFromLevel(99))
            statMap.setBaseLevel("stat.$stat", 99)
            statMap.setCurrentLevel("stat.$stat", 99)
        }
        inv = invMap.getOrPut("inv.inv"); worn = invMap.getOrPut("inv.worn")
    }
}
