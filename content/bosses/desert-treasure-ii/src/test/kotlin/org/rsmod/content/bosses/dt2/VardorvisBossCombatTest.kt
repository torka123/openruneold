package org.rsmod.content.bosses.dt2

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.npc.NpcStateEvents

/**
 * Real manager, production AI registration, native NvPCombat and queued hits; scene IO is mocked.
 */
@OptIn(InternalApi::class)
class VardorvisBossCombatTest {
    @Test
    fun `native melee keeps five ticks dynamic max prayer and accepted-launch callbacks`() {
        val f = Dt2NativeCombatFixture()
        f.run.vardorvisEnragePending = true
        VarPlayerIntMapSetter.set(f.player, "varbit.prayer_protectfrommelee", 1)
        f.attack()
        assertEquals(1, f.run.attackCounter)
        assertTrue(f.run.enraged)
        assertFalse(f.run.vardorvisEnragePending)
        assertEquals(105, f.boss.actionDelay)
        assertEquals(1, f.hits().single().first)
        assertEquals(8, f.hits().single().second.damage)
        assertEquals(listOf(33), f.rollBounds)
        repeat(4) {
            f.advance(1)
            f.attack()
        }
        assertEquals(1, f.run.attackCounter)
        f.advance(1)
        f.boss.hitpoints = 1
        f.attack()
        assertEquals(2, f.run.attackCounter)
        assertEquals(listOf(33, 44), f.rollBounds)
        assertEquals(10, f.hits().last().second.damage)
        f.advance(5)
        f.attack()
        assertEquals(3, f.run.attackCounter)
        assertEquals(1, f.run.vardorvisAxeSets.size)
        assertEquals(Dt2Rules.vardorvisAxeCount(1, false), f.run.helpers.size)
    }

    @Test
    fun `native action-delay extensions retain precedence over DSL cadence`() {
        val f = Dt2NativeCombatFixture()
        f.boss.actionDelay = 110
        repeat(10) {
            f.attack()
            f.advance(1)
        }
        assertEquals(0, f.run.attackCounter)
        assertTrue(f.rollBounds.isEmpty())
        f.attack()
        assertEquals(1, f.run.attackCounter)
        assertEquals(115, f.boss.actionDelay)
        f.boss.actionDelay = 117
        f.advance(5)
        f.attack()
        assertEquals(1, f.run.attackCounter)
        f.advance(2)
        f.attack()
        assertEquals(2, f.run.attackCounter)
        assertEquals(122, f.boss.actionDelay)
    }

    @Test
    fun `miss still counts accepted melee and actual impact damage alone heals`() {
        val miss = Dt2NativeCombatFixture(accurate = false)
        miss.attack()
        assertEquals(1, miss.run.attackCounter)
        assertEquals(0, miss.hits().single().second.damage)
        assertTrue(miss.rollBounds.isEmpty())
        framework().clearInlineMocks()

        val f = Dt2NativeCombatFixture()
        f.boss.hitpoints = 650
        f.attack()
        val hit = f.hits().single().second
        assertTrue(hit.damage > 3)
        hit.dispatchImpact(f.player, 3)
        assertEquals(651, f.boss.hitpoints, "Only actual damage 3 heals by floor(3/2)")
        f.run.state = Dt2State.DYING
        hit.dispatchImpact(f.player, 20)
        assertEquals(651, f.boss.hitpoints)
    }

    @Test
    fun `already fired melee retains death lifetime and retires on deletion or departure`() {
        for (ending in listOf("death", "delete", "logout", "scope")) {
            try {
                val f = Dt2NativeCombatFixture()
                f.attack()
                val hit = f.hits().single().second
                when (ending) {
                    "death" -> {
                        f.boss.hitpoints = 0
                        f.run.state = Dt2State.DYING
                    }
                    "delete" -> {
                        f.events.publish(NpcStateEvents.Delete(f.boss))
                        f.run.state = Dt2State.INVALIDATED
                    }
                    "logout" -> f.player.pendingLogout = true
                    else -> {
                        f.player.combatHitScope!!.close()
                        f.run.state = Dt2State.INVALIDATED
                    }
                }
                assertEquals(ending == "death", hit.isValidNow(), ending)
                f.advance(5)
                f.attack()
                assertEquals(1, f.run.attackCounter, ending)
            } finally {
                framework().clearInlineMocks()
            }
        }
    }

    @AfterTest
    fun releaseMocks() {
        framework().clearInlineMocks()
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}
