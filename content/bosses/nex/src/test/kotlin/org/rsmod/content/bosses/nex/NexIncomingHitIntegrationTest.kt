package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import dev.or2.central.account.Rights
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito.framework
import org.rsmod.annotations.InternalApi
import org.rsmod.api.config.refs.params
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.player.cheat.adminOneHit
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.HitAttribution
import org.rsmod.game.hit.HitContext
import org.rsmod.game.hit.HitOrigin
import org.rsmod.game.hit.HitType
import org.rsmod.game.hit.HitValidity

/**
 * Production registration and native queues. Formula values are characterization, not OSRS proof.
 */
@OptIn(InternalApi::class)
class NexIncomingHitIntegrationTest {
    @Test
    fun `encounter binding preserves an existing caller cancellation`() {
        val f = NexMinionTestWorld()
        val boss = f.npc(11278)
        val nativeModifier = StandardNpcHitModifier(f.events)
        boss.queueHit(
            f.player,
            1,
            HitType.Magic,
            20,
            NpcHitModifier { target ->
                validity = HitValidity { false }
                with(nativeModifier) { modify(target) }
            },
        )
        f.tick()
        assertEquals(3400, boss.hitpoints)
        assertTrue(boss.damageContributions.isEmpty)
        assertTrue(f.run.contributions.isEmpty())
    }

    @Test
    fun `incoming validity rejects unknown departed replaced or retired ownership`() {
        for (case in 0..3) {
            val f = NexMinionTestWorld(duo = true)
            val boss = f.npc(11278)
            val oldRun = f.run
            boss.queueHit(
                1,
                HitType.Typeless,
                20,
                StandardNpcHitModifier(f.events),
                context =
                    HitContext.of(
                        HitAttribution(HitOrigin.Burn, if (case == 0) 999L else f.player.uuid)
                    ),
            )
            when (case) {
                1 -> f.player.clientDisconnected.set(true)
                2 ->
                    f.players[1] =
                        Player().apply {
                            slotId = 1
                            uuid = f.player.uuid
                            assignUid()
                            coords = f.player.coords
                            statMap.setBaseLevel("stat.hitpoints", 99)
                            statMap.setCurrentLevel("stat.hitpoints", 99)
                        }
                3 -> f.encounters.remove(boss)
            }
            f.tick()
            assertEquals(3400, boss.hitpoints)
            assertTrue(boss.damageContributions.isEmpty)
            assertTrue(oldRun.contributions.isEmpty())
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `deflect turning on or off in flight is sampled with accepted impact damage`() {
        for (enabledAtImpact in listOf(false, true)) {
            val f = NexMinionTestWorld()
            f.reachPhase(NexState.ZAROS)
            val boss = f.npc(11278)
            val before = boss.hitpoints
            f.manager.setZarosOverhead(
                boss,
                if (enabledAtImpact) NexOverhead.NONE else NexOverhead.DEFLECT_MELEE,
            )
            f.player.queueList.clear()
            f.damage(boss, 41, HitType.Melee)
            f.manager.setZarosOverhead(
                boss,
                if (enabledAtImpact) NexOverhead.DEFLECT_MELEE else NexOverhead.NONE,
            )
            f.tick()
            assertEquals(before - if (enabledAtImpact) 20 else 41, boss.hitpoints)
            if (enabledAtImpact) assertEquals(20, f.hits().single().second.damage)
            else assertTrue(f.hits().isEmpty())
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `unowned environmental damage is resolved without fabricated player credit`() {
        val f = NexMinionTestWorld()
        val boss = f.npc(11278)
        boss.queueHit(1, HitType.Typeless, 20, StandardNpcHitModifier(f.events))
        f.tick()
        assertEquals(3380, boss.hitpoints)
        assertTrue(boss.damageContributions.isEmpty)
        assertTrue(f.run.contributions.isEmpty())
    }

    @Test
    fun `siphon uses each immutable input rather than admin expanded damage`() {
        val f = NexMinionTestWorld()
        f.reachPhase(NexState.BLOOD)
        val boss = f.npc(11278)
        boss.hitpoints = 2500
        f.run.siphonUntil = f.clock.cycle + 3
        f.player.modLevel = Rights.ADMINISTRATOR
        f.player.adminOneHit = true
        val before = f.run.contributions.getValue(f.player.uid).damage
        f.damage(boss, 1)
        f.damage(boss, 30)
        assertEquals(2500, boss.hitpoints)
        f.tick()
        assertEquals(2531, boss.hitpoints)
        assertEquals(before, f.run.contributions.getValue(f.player.uid).damage)
    }

    @Test
    fun `siphon absorption publishes a headbar reflecting healed HP without a guessed heal mark`() {
        val f = NexMinionTestWorld()
        f.reachPhase(NexState.BLOOD)
        val boss = f.npc(11278)
        boss.hitpoints = 2000
        val bars = mutableListOf<Headbar>()
        boss.infoProtocol =
            object : NpcInfoProtocol by boss.infoProtocol {
                override fun showHeadbar(headbar: Headbar) {
                    bars += headbar
                }
            }
        f.run.siphonUntil = f.clock.cycle + 3
        f.damage(boss, 700)
        f.tick()
        assertEquals(2700, boss.hitpoints)
        assertEquals(
            2700 * boss.visHeadbar(params.headbar).segments / boss.baseHitpointsLvl,
            bars.last().endFill,
        )
    }

    @Test
    fun `two queued crossings call guard and credit only when damage actually lands`() {
        val f = NexMinionTestWorld()
        val boss = f.npc(11278)
        boss.hitpoints = 2730
        f.damage(boss, 40)
        f.damage(boss, 40)
        assertEquals(2730, boss.hitpoints)
        assertNull(f.run.calledGuard)
        assertTrue(f.run.contributions.isEmpty())
        f.tick()
        assertEquals(2713, boss.hitpoints) // 13 crossing, then 4 against the called phase.
        assertEquals(NexState.FUMUS, f.run.calledGuard)
        assertEquals(17, boss.damageContributions.totalDamage())
        assertEquals(17, f.run.contributions.getValue(f.player.uid).damage)
    }

    @Test
    fun `simultaneous arrivals obey each current called floor without queue-time credit`() {
        for ((guard, floor) in listOf(11283 to 2550, 11284 to 1870, 11285 to 1190, 11286 to 510)) {
            val f = NexMinionTestWorld()
            f.reachGuard(guard)
            val boss = f.npc(11278)
            assertTrue(f.run.bodyguards.values.all { it.vars["varn.flat_armour"] == 0 })
            boss.hitpoints = floor + 5
            val before = f.run.contributions.getValue(f.player.uid).damage
            f.damage(boss, 40)
            f.damage(boss, 40)
            assertEquals(before, f.run.contributions.getValue(f.player.uid).damage)
            f.tick()
            assertEquals(floor, boss.hitpoints)
            assertEquals(before + 5, f.run.contributions.getValue(f.player.uid).damage)
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `lethal bodyguard races credit remaining HP once and use native death`() {
        val f = NexMinionTestWorld(nativeDeaths = true)
        f.reachGuard(11283)
        val guard = f.npc(11283)
        guard.hitpoints = 7
        val before = f.run.contributions.getValue(f.player.uid).damage
        f.damage(guard, 20)
        f.damage(guard, 20)
        assertEquals(before, f.run.contributions.getValue(f.player.uid).damage)
        f.tick(2)
        assertEquals(7, guard.damageContributions.totalDamage())
        assertEquals(before + 7, f.run.contributions.getValue(f.player.uid).damage)
        assertEquals(setOf(NexState.FUMUS), f.run.calledGuardResolved)
        assertEquals(NexState.SHADOW, f.run.phase)
    }

    @Test
    fun `lethal reaver races heal only the accepted hit after impact`() {
        val f = NexMinionTestWorld(nativeDeaths = true)
        val reaver = f.summonReaver()
        assertEquals(0, reaver.vars["varn.flat_armour"])
        reaver.hitpoints = 7
        f.player.statMap.setCurrentLevel("stat.hitpoints", 50)
        val before = f.run.contributions.getValue(f.player.uid).damage
        f.damage(reaver, 40)
        f.damage(reaver, 40)
        assertEquals(50, f.player.hitpoints)
        assertEquals(before, f.run.contributions.getValue(f.player.uid).damage)
        f.tick()
        assertEquals(
            55,
            f.player.hitpoints,
        ) // Retained 3/4 rule; current-rate conflict remains E02.
        assertEquals(7, reaver.damageContributions.totalDamage())
        assertEquals(before + 7, f.run.contributions.getValue(f.player.uid).damage)
    }

    @Test
    fun `siphon beginning in flight absorbs once on arrival and grants no damage credit`() {
        val f = NexMinionTestWorld()
        f.reachPhase(NexState.BLOOD)
        val boss = f.npc(11278)
        boss.hitpoints = 2500
        val before = f.run.contributions.getValue(f.player.uid).damage
        f.damage(boss, 70)
        f.run.siphonUntil = f.clock.cycle + 3
        assertEquals(2500, boss.hitpoints)
        f.tick()
        assertEquals(2570, boss.hitpoints)
        assertEquals(before, f.run.contributions.getValue(f.player.uid).damage)
        assertTrue(f.run.flags.getValue(f.player.uid).bloodSiphonHeal)
        f.damage(boss, 0)
        f.tick()
        assertEquals(2570, boss.hitpoints)
        assertTrue(
            f.run.flags.getValue(f.player.uid).bloodSiphonHeal,
            "Failure flags are monotonic",
        )
    }

    @Test
    fun `siphon ending before arrival accepts damage instead of launch-time healing`() {
        val f = NexMinionTestWorld()
        f.reachPhase(NexState.BLOOD)
        val boss = f.npc(11278)
        boss.hitpoints = 2500
        f.run.siphonUntil = f.clock.cycle + 1
        f.damage(boss, 70)
        assertEquals(2500, boss.hitpoints)
        f.tick()
        assertEquals(2444, boss.hitpoints)
        assertFalse(f.run.flags.getValue(f.player.uid).bloodSiphonHeal)
    }

    @Test
    fun `departure cancels queued incoming damage and every associated side effect`() {
        val f = NexMinionTestWorld(duo = true)
        val boss = f.npc(11278)
        boss.hitpoints = 2730
        f.damage(boss, 40)
        f.player.coords = NEX_BANK_RETURN
        f.tick()
        assertEquals(2730, boss.hitpoints)
        assertNull(f.run.calledGuard)
        assertTrue(boss.damageContributions.isEmpty)
        assertTrue(f.run.contributions.isEmpty())
    }

    @Test
    fun `indirect owned hits retain native provenance and are not discarded as nonplayers`() {
        for (origin in listOf(HitOrigin.Recoil, HitOrigin.Burn, HitOrigin.Thrall)) {
            val f = NexMinionTestWorld()
            val boss = f.npc(11278)
            assertEquals(0, boss.vars["varn.flat_armour"])
            boss.queueHit(
                1,
                HitType.Typeless,
                20,
                StandardNpcHitModifier(f.events),
                context = HitContext.of(HitAttribution(origin, f.player.uuid)),
            )
            assertTrue(f.run.contributions.isEmpty())
            f.tick()
            assertEquals(3380, boss.hitpoints)
            assertEquals(20, boss.damageContributions.damageBy(f.player.uuid!!, origin))
            assertEquals(20, f.run.contributions.getValue(f.player.uid).damage)
            framework().clearInlineMocks()
        }
    }

    @Test
    fun `deflect reflection is queued only after accepted melee impact`() {
        val f = NexMinionTestWorld()
        f.reachPhase(NexState.ZAROS)
        val boss = f.npc(11278)
        f.manager.setZarosOverhead(boss, NexOverhead.DEFLECT_MELEE)
        f.player.queueList.clear()
        f.damage(boss, 40, HitType.Melee)
        assertTrue(f.hits().isEmpty())
        f.tick()
        assertEquals(20, f.hits().single().second.damage)
    }

    private val NexMinionTestWorld.run: NexEncounterManager.Run
        get() =
            NexEncounterManager::class
                .java
                .getDeclaredField("run")
                .apply { isAccessible = true }
                .get(manager) as NexEncounterManager.Run

    @AfterEach fun releaseMocks() = framework().clearInlineMocks()

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}
