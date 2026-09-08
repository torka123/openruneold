package org.rsmod.content.skills.farming

import com.google.inject.Guice
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.events.interact.LocTDefaultEvents
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.protect.ProtectedAccessLauncher.Companion.withProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.spells.MagicSpellModule
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.spells.runes.combo.*
import org.rsmod.api.spells.runes.compact.*
import org.rsmod.api.spells.runes.fake.*
import org.rsmod.api.spells.runes.staves.*
import org.rsmod.api.spells.runes.subs.*
import org.rsmod.api.spells.runes.unlimited.*
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.stat.PlayerSkillXPTable
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
class ResurrectCropsRuntimeTest {
    @Test fun `actual crop cast consumes runes awards XP and persists success or failure exactly once`() {
        for (roll in listOf(0, 99)) {
            val f = CropCastFixture(roll)
            val patch = FarmingData.patches.first()
            f.player.saveFarmingPatch(patch.key, FarmingPatchState(
                cropKey = "guam", plantedAt = System.currentTimeMillis(), weeds = 0, dead = true))
            val magicBefore = f.player.statMap.getFineXP("stat.magic")
            f.cast(patch.baseLocId)
            assertEquals(900, f.player.statMap.getFineXP("stat.magic") - magicBefore)
            f.spell.objReqs.forEach { assertEquals(0, f.player.inv.count(it.obj.internalName)) }
            val saved = f.player.loadFarmingPatch(patch.key)
            if (roll == 0) {
                assertEquals("guam", saved.cropKey)
                assertFalse(saved.dead)
                assertTrue(saved.resurrected)
                // Simulate a later death: the persisted per-crop attempt must still block recasting.
                f.player.saveFarmingPatch(patch.key, saved.copy(dead = true))
            } else {
                assertNull(saved.cropKey)
            }
            f.supply()
            f.player.actionDelay = 0
            f.cast(patch.baseLocId)
            assertEquals(900, f.player.statMap.getFineXP("stat.magic") - magicBefore)
            f.spell.objReqs.forEach { assertEquals(it.count, f.player.inv.count(it.obj.internalName)) }
        }
    }

    @Test fun `invalid living and rune-less crop casts do not consume resources or mark an attempt`() {
        val f = CropCastFixture(0)
        val patch = FarmingData.patches.first()
        f.cast(patch.baseLocId)
        f.spell.objReqs.forEach { assertEquals(it.count, f.player.inv.count(it.obj.internalName)) }
        f.player.saveFarmingPatch(patch.key, FarmingPatchState(
            cropKey = "guam", plantedAt = System.currentTimeMillis(), weeds = 0, dead = true))
        for (slot in f.player.inv.indices) f.player.inv[slot] = null
        f.cast(patch.baseLocId)
        val saved = f.player.loadFarmingPatch(patch.key)
        assertTrue(saved.dead)
        assertFalse(saved.resurrected)
    }

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
private class CropCastFixture(roll: Int) {
    val clock = MapClock(100)
    val events = EventBus()
    val context = mock(ProtectedAccessContext::class.java)
    val registry = Guice.createInjector(MagicSpellModule()).getInstance(MagicSpellRegistry::class.java)
    val spell = assertNotNull(registry.getObjSpell(assertNotNull(
        ServerCacheManager.getItem(ResurrectCropsScript.SPELL_OBJECT.asRSCM(RSCMType.OBJ)))))
    val player = Player().apply {
        uuid = 1L; observerUUID = 1L; slotId = 1; assignUid()
        currentMapClock = clock.cycle; processedMapClock = clock.cycle
        xpRate = 1.0; globalXpRate = 1.0
        for (stat in listOf("magic", "hitpoints", "prayer", "farming")) {
            statMap.setXP("stat.$stat", PlayerSkillXPTable.getXPFromLevel(99))
            statMap.setBaseLevel("stat.$stat", 99)
            statMap.setCurrentLevel("stat.$stat", 99)
        }
        inv = invMap.getOrPut("inv.inv")
        worn = invMap.getOrPut("inv.worn")
    }
    init {
        VarPlayerIntMapSetter.set(player, "varbit.spellbook", 3)
        val fakes = FakeRuneRepository(); val combos = ComboRuneRepository(); val compact = CompactRuneRepository()
        val unlimited = UnlimitedRuneRepository(); val staves = StaffSubstituteRepository(); val subs = RuneSubstituteRepository()
        val scriptContext = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
        listOf(FakeRuneScript(fakes), ComboRuneScript(combos), CompactRuneScript(compact),
            UnlimitedRuneScript(unlimited), StaffSubstituteScript(staves), RuneSubstituteScript(subs))
            .forEach { with(it) { scriptContext.startup() } }
        val runes = MagicRuneManager(fakes, combos, compact, unlimited, staves, subs)
        val random = mock(GameRandom::class.java)
        `when`(context.random).thenReturn(random)
        `when`(random.of(100)).thenReturn(roll)
        with(ResurrectCropsScript(registry, runes, FarmingService(XpModifiers(emptySet())), random, clock)) {
            scriptContext.startup()
        }
        supply()
    }
    fun supply() = spell.objReqs.forEachIndexed { index, req -> player.inv[index] = InvObj(req.obj, req.count) }
    fun cast(id: Int) {
        val loc = mock(BoundLocInfo::class.java)
        `when`(loc.id).thenReturn(id)
        val type = assertNotNull(ServerCacheManager.getObject(id))
        assertTrue(withProtectedAccess(player, context) {
            assertTrue(events.publish(this, LocTDefaultEvents.Ap(loc, loc, type, null, -1, spell.component)))
        })
    }
}
