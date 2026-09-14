package org.rsmod.content.bosses.dt2

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.runtime.EncounterRegistry
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NoopNpcInfo
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid

/** Native owners/selectors against the retained Whisperer sequence and dispatch contracts. */
@OptIn(InternalApi::class)
class WhispererCombatPhasesTest {
    @Test
    fun `forced special output advances the scheduled slot rather than replacing its predecessor`() {
        for (seed in listOf(0L, 1L)) {
            val f = Fixture(seed)
            val combatOracle = DefaultGameRandom(57L)
            repeat(24) { slot ->
                f.encounter.transitionTo(whispererDistancePhase(0, false), f.clock.cycle)
                val ordinary = assertNotNull(f.encounter.currentPhase).selector
                assertEquals(
                    Dt2AttackStyle.MAGIC.name,
                    f.encounter.selectAbility(ordinary, f.clock.cycle, f.player),
                )
                val requested =
                    when (slot % 4) {
                        0 -> WhispererSpecial.SOUL_SIPHON.name.lowercase()
                        1 -> WhispererSpecial.CORRUPTED_SEEDS.name
                        2 -> "invalid"
                        else -> null
                    }
                val expected =
                    when (slot % 4) {
                        0 -> WhispererSpecial.SOUL_SIPHON
                        1 -> WhispererSpecial.CORRUPTED_SEEDS
                        else -> Dt2Rules.whispererSpecial(slot, seed.toInt())
                    }
                val selected = f.phases.selectSpecial(requested, f.player)
                assertEquals(expected, selected, "seed=$seed slot=$slot")
                assertEquals(slot + 1, f.phases.startedSpecials)
                assertEquals(
                    Dt2AttackStyle.RANGED.name,
                    f.encounter.selectAbility(ordinary, f.clock.cycle, f.player),
                )
                f.phases.enterSpecial(selected)
                assertEquals(selected, f.phases.activeSpecial)
                f.clock.tick()
            }
            assertEquals(combatOracle.of(Int.MAX_VALUE), f.combatRandom.of(Int.MAX_VALUE))
        }
    }

    @Test
    fun `special completion waits for owned dispatch and final-tick success wins over timeout`() {
        for (special in WhispererSpecial.entries) for (succeed in listOf(false, true)) {
            val f = Fixture()
            var success = false
            val completed = mutableListOf<Boolean>()
            f.phases.enterSpecial(special)
            val deadline = f.phases.armSpecialCompletion({ success }) { completed += it }
            assertEquals(100 + WHISPERER_SPECIAL_DURATIONS.getValue(special), deadline)
            f.clock.cycle = deadline - 1
            f.phases.completeSpecial()
            assertTrue(completed.isEmpty())
            f.clock.tick()
            f.process.process()
            assertTrue(
                completed.isEmpty(),
                "The normal world stage cannot complete a LateCycle special",
            )
            // The caller performs the seed/pillar/soul update before this dispatch.
            success = succeed
            f.phases.completeSpecial()
            f.phases.completeSpecial()
            assertEquals(listOf(succeed), completed)
            assertEquals(0, f.phases.completionOwner.size)
        }
    }

    @Test
    fun `early success and replacing the same special cancel the obsolete completion`() {
        val f = Fixture()
        var old = false
        var current = false
        f.phases.enterSpecial(WhispererSpecial.SOUL_SIPHON)
        f.phases.armSpecialCompletion({ false }) { old = true }
        f.clock.cycle = 105
        f.phases.enterSpecial(WhispererSpecial.SOUL_SIPHON)
        f.phases.armSpecialCompletion({ true }) { current = it }
        f.phases.completeSpecial()
        assertTrue(current)
        f.clock.cycle = 125
        f.phases.completeSpecial()
        assertFalse(old)
        assertEquals(0, f.phases.completionOwner.size)
    }

    @Test
    fun `declared secondary recipes retain every stage and visual melee before enrage`() {
        for (stage in 0..5) for (turn in 0..7) for (enraged in listOf(false, true)) {
            val oracle = Dt2Rules.whispererVolley(turn, stage, enraged)
            val shots = whispererFollowups(oracle.styles.first(), stage, enraged)
            assertEquals(oracle.styles.drop(1), shots.map { it.style })
            assertEquals((1..shots.size).toList(), shots.map { it.afterTicks })
            assertTrue(shots.all { it.projectile })
        }
        for (enraged in listOf(false, true)) {
            assertEquals(
                listOf(WhispererFollowup(Dt2AttackStyle.MELEE, 1, projectile = false)),
                whispererFollowups(Dt2AttackStyle.MELEE, 3, enraged),
            )
        }
    }

    @Test
    fun `secondary launches observe their own deadline state and transient cleanup cancels the rest`() {
        val f = Fixture()
        var prayer = false
        val launches = mutableListOf<Triple<Int, Dt2AttackStyle, Boolean>>()
        f.phases.enqueueFollowups(Dt2AttackStyle.MAGIC, 1, false) {
            launches += Triple(f.clock.cycle, it.style, prayer)
        }
        f.clock.tick()
        prayer = true
        f.process.process()
        assertTrue(launches.isEmpty())
        f.phases.launchDueShots()
        assertEquals(listOf(Triple(101, Dt2AttackStyle.MAGIC, true)), launches)
        assertEquals(1, f.phases.shotOwner.size)
        prayer = false
        f.clock.tick()
        f.phases.launchDueShots()
        assertEquals(Triple(102, Dt2AttackStyle.RANGED, false), launches.last())
        f.phases.enqueueFollowups(Dt2AttackStyle.MAGIC, 0, false) {
            error("Retired volley launched")
        }
        f.phases.clearTransient()
        f.clock.cycle = 110
        f.phases.launchDueShots()
        assertEquals(0, f.phases.shotOwner.size)
    }

    @Test
    fun `independent melee expiry survives a special and runs before a tied secondary shot`() {
        val f = Fixture()
        val order = mutableListOf<String>()
        var mayExpire = true
        assertEquals(
            113,
            f.phases.armMeleeExpiry({ mayExpire }) {
                order += "form"
                f.phases.alignSpecial(WhispererSpecial.SOUL_SIPHON)
            },
        )
        f.phases.enterSpecial(WhispererSpecial.SOUL_SIPHON)
        f.phases.armSpecialCompletion({ false }) { order += "complete" }
        f.phases.clearTransient()
        assertEquals(
            1,
            f.phases.formOwner.size,
            "Special/transient cleanup preserves the original form window",
        )
        f.phases.armSpecialCompletion({ false }) { order += "complete" }
        f.clock.cycle = 112
        f.phases.enqueueFollowups(Dt2AttackStyle.MELEE, 1, false) { order += "shot" }
        f.phases.expireMeleeForm()
        assertTrue(order.isEmpty())
        f.clock.tick()
        f.phases.expireMeleeForm()
        f.phases.launchDueShots()
        assertEquals(listOf("form", "shot"), order)
        assertEquals(100, f.phases.enteredTick, "Form alignment cannot restart the current special")
        f.clock.cycle = 120
        f.phases.completeSpecial()
        assertEquals(listOf("form", "shot", "complete"), order)
        mayExpire = false
        var expired = false
        f.phases.armMeleeExpiry({ mayExpire }) { expired = true }
        f.clock.cycle = 140
        f.phases.expireMeleeForm()
        assertFalse(expired, "The visual melee-first enrage branch must retain its form")
    }

    @Test
    fun `native encounter cleanup retires all owners and detached callbacks cannot fire`() {
        val f = Fixture()
        f.phases.enterSpecial(WhispererSpecial.SCREECH)
        f.phases.armSpecialCompletion({ false }) { error("Retired completion") }
        f.phases.armMeleeExpiry({ true }) { error("Retired form") }
        f.phases.enqueueFollowups(Dt2AttackStyle.MAGIC, 0, false) { error("Retired shot") }
        f.registry.remove(f.npc)
        assertTrue(f.phases.shotOwner.isClosed)
        assertTrue(f.phases.completionOwner.isClosed)
        assertTrue(f.phases.formOwner.isClosed)
        assertEquals(0, f.queues.size)
        f.clock.cycle = 200
        f.phases.expireMeleeForm()
        f.phases.launchDueShots()
        f.phases.completeSpecial()
        assertFailsWith<IllegalStateException> {
            f.phases.enqueueFollowups(Dt2AttackStyle.MELEE, 0, false) {}
        }
        val detached = Fixture()
        detached.phases.enqueueFollowups(Dt2AttackStyle.MAGIC, 0, false) { error("Detached shot") }
        detached.attached = false
        detached.clock.cycle = 103
        detached.phases.launchDueShots()
        assertEquals(0, detached.phases.shotOwner.size)
    }

    private class Fixture(seed: Long = 42L) {
        val clock = MapClock(100)
        val queues = WorldQueueList()
        val process = WorldQueueListProcess(queues)
        val player =
            Player().apply {
                slotId = 1
                uuid = 1
                assignUid()
                coords = CoordGrid(2654, 6368)
            }
        val npc =
            Npc(assertNotNull(ServerCacheManager.getNpc(12203)), CoordGrid(2655, 6368)).apply {
                slotId = 1
                assignUid()
                infoProtocol = NoopNpcInfo
            }
        val session =
            InstanceSession(
                InstanceId(1),
                mutableSetOf(),
                1L,
                Dt2Boss.WHISPERER.key,
                Dt2Boss.WHISPERER.spec(npc.type),
                (InstanceAreaResolver().resolve(Dt2Boss.WHISPERER.area())
                        as InstanceAreaResolver.Result.Ready)
                    .placement,
                InstanceAccess.Private,
            )
        val combatRandom = DefaultGameRandom(57L)
        val registry =
            EncounterRegistry(combatRandom).apply {
                register(npc.type.id, whispererBossSpec(), "dt2-whisperer")
            }
        val encounter = registry.of(npc)
        var attached = true
        val phases =
            WhispererCombatPhases(encounter, clock, queues, process, session, seed) { attached }
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}
