package org.rsmod.content.skills.thieving

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.commons.magic.ArceuusSpellState
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.hit.processor.InstantPlayerHitProcessor
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.protect.ProtectedAccessLauncher.Companion.withProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

class ShadowVeilRuntimeTest {
    @Test fun `Veil saves a failed pickpocket without rolling or consuming the necklace`() {
        val f = ThievingFixture(0)
        f.necklace()
        f.veil()
        f.pickpocket()
        assertFalse(f.player.isShoveStunned)
        assertEquals(0, f.damage)
        assertEquals(1, f.avoidanceRolls)
        assertNull(f.player.attr.toPersistentMap()["dodgy_necklace_charges"])
        assertNotNull(f.player.worn[2])
    }

    @Test fun `necklace rolls after failed Veil and only its successful roll spends a charge`() {
        val saved = ThievingFixture(9999, 0)
        saved.necklace()
        saved.veil()
        saved.pickpocket()
        assertEquals(2, saved.avoidanceRolls)
        assertEquals(9, saved.player.attr.toPersistentMap()["dodgy_necklace_charges"])
        assertEquals(0, saved.damage)
        assertFalse(saved.player.isShoveStunned)

        val failed = ThievingFixture(9999, 9999)
        failed.necklace()
        failed.veil()
        failed.pickpocket()
        assertEquals(2, failed.avoidanceRolls)
        assertEquals(1, failed.damage)
        assertTrue(failed.player.isShoveStunned)
        assertNull(failed.player.attr.toPersistentMap()["dodgy_necklace_charges"])
    }

    @Test fun `necklace works without Veil and its charges survive replacement and player reload`() {
        val original = ThievingFixture(0)
        original.necklace()
        original.pickpocket()
        val loaded = ThievingFixture(0)
        loaded.player.attr.putAllFromPersistence(original.player.attr.toPersistentMap())
        loaded.necklace()
        repeat(9) { loaded.pickpocket() }
        assertEquals(9, loaded.avoidanceRolls)
        assertNull(loaded.player.worn[2])
        assertFalse(loaded.player.isShoveStunned)
        assertEquals(0, loaded.damage)
        loaded.necklace()
        loaded.pickpocket()
        assertNotNull(loaded.player.worn[2])
        assertEquals(9, loaded.player.attr.toPersistentMap()["dodgy_necklace_charges"])
    }

    @Test fun `expired Veil leaves the normal failed pickpocket damage and stun`() {
        val f = ThievingFixture(0)
        ArceuusSpellState.shadowVeil(f.player, 100)
        f.pickpocket()
        assertEquals(0, f.avoidanceRolls)
        assertEquals(1, f.damage)
        assertTrue(f.player.isShoveStunned)
        assertEquals(0, f.player.inv.occupiedSpace())
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
private class ThievingFixture(vararg rolls: Int) {
    val clock = MapClock(100)
    var damage = 0
    var avoidanceRolls = 0
    val context = mock(ProtectedAccessContext::class.java)
    val player = Player().apply {
        uuid = 1L; observerUUID = 1L; slotId = 1; assignUid()
        coords = CoordGrid(3200, 3200, 0)
        currentMapClock = 100; processedMapClock = 100
        for ((stat, level) in listOf("thieving" to 1, "hitpoints" to 99)) {
            statMap.setBaseLevel("stat.$stat", level.toByte())
            statMap.setCurrentLevel("stat.$stat", level.toByte())
        }
        inv = invMap.getOrPut("inv.inv")
        worn = invMap.getOrPut("inv.worn")
        ArceuusSpellState.clear(this)
    }
    val npc = Npc(assertNotNull(ServerCacheManager.getNpcs().values.firstOrNull { it.name == "Man" }),
        CoordGrid(3200, 3201, 0))
    private val random = mock(GameRandom::class.java) { call ->
        when {
            call.method.name == "of" && call.arguments.contentEquals(arrayOf(100)) -> 99
            call.method.name == "of" && call.arguments.contentEquals(arrayOf(10000)) ->
                rolls[(avoidanceRolls++).coerceAtMost(rolls.lastIndex)]
            call.method.name == "of" && call.arguments.firstOrNull() is IntRange -> 1
            else -> RETURNS_DEFAULTS.answer(call)
        }
    }
    private val script = ThievingScript(random, mock(LocRepository::class.java), mock(NpcRepository::class.java), clock)
    init {
        `when`(context.random).thenReturn(random)
        `when`(context.instantHitProcessor).thenReturn(InstantPlayerHitProcessor { hit -> damage += hit.damage })
    }
    fun necklace() { player.worn[2] = InvObj("obj.dodgy_necklace", 1) }
    fun veil() = ArceuusSpellState.shadowVeil(player, clock.cycle + 99)
    fun pickpocket() {
        assertTrue(withProtectedAccess(player, context) { with(script) { pickpocket(npc, ThievingDefinitions.man) } })
        repeat(3) {
            clock.cycle++
            player.currentMapClock = clock.cycle
            player.processedMapClock = clock.cycle
            player.advanceActiveCoroutine()
        }
        assertNull(player.activeCoroutine)
    }
}
