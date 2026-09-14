package org.rsmod.content.minigames.toa

import dev.openrune.ServerCacheManager
import java.util.UUID
import kotlin.random.Random
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.bosses.runtime.EncounterRegistry
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NoopNpcInfo
import org.rsmod.map.CoordGrid

/** Checkpoint ddb47abbd selection oracles; native branches must preserve the shared RNG stream. */
@OptIn(InternalApi::class)
class ToaSpecialChoicesTest {
    @Test
    fun `BaBa native branches match every previous and rubble gate across seeds and intervening geometry draws`() {
        for (seed in seeds) {
            val source = BabaEncounter(UUID(0, seed), seed).apply { reset() }
            val oracle = Oracle(seed xor source.generation.toLong())
            val f = Fixture(BabaAssets.BABA, toaBabaBossSpec(), source::nextInt)
            var tick = 100
            for (previous in listOf(null) + BabaSpecial.entries) {
                for (allow in listOf(false, true)) {
                    f.encounter.babaSpecialHistory().previous = previous
                    val context = "seed=$seed previous=$previous rubble=$allow"
                    val expected = oracle.baba(previous, allow)
                    assertEquals(expected, f.encounter.selectBabaSpecial(tick++, allow), context)
                    assertEquals(expected, f.encounter.babaSpecialHistory().previous, context)
                    assertEquals(oracle.selectionBounds, f.selectionBounds, context)
                    for (bound in listOf(3, 5, 31, Int.MAX_VALUE)) {
                        assertEquals(
                            oracle.random.nextInt(bound),
                            source.nextInt(bound),
                            "$context next=$bound",
                        )
                    }
                    f.assertOrdinaryUnchanged()
                }
            }
            f.assertDefaultRandomUntouched()
        }
    }

    @Test
    fun `BaBa singleton draws and forced rockfall throw chain leave the next logical selection intact`() {
        val source = BabaEncounter(UUID(0, 42), 42).apply { reset() }
        val oracle = Oracle(42L xor source.generation.toLong())
        val f = Fixture(BabaAssets.BABA, toaBabaBossSpec(), source::nextInt)
        var previous: BabaSpecial? = BabaSpecial.SHOCKWAVE
        f.encounter.babaSpecialHistory().previous = previous
        repeat(128) { index ->
            val allow = index % 3 == 0
            val expected = oracle.baba(previous, allow)
            assertEquals(expected, f.encounter.selectBabaSpecial(100 + index, allow))
            previous = expected
            val boundsBeforeForced = f.selectionBounds.toList()
            // Surrogate geometry draws for forced Rockfall and its chained Throw: their payloads
            // bypass the normal selection call and cannot rewrite its native logical history.
            for (bound in listOf(17, 31)) {
                assertEquals(oracle.random.nextInt(bound), source.nextInt(bound))
            }
            assertEquals(previous, f.encounter.babaSpecialHistory().previous)
            assertEquals(boundsBeforeForced, f.selectionBounds)
            f.encounter.transitionTo(f.encounter.currentPhaseName, 100 + index)
            assertEquals(
                previous,
                f.encounter.babaSpecialHistory().previous,
                "Phase entry cannot rewrite logical history",
            )
        }
        assertTrue(1 in f.selectionBounds, "A singleton must still consume the retained nextInt(1)")
        assertEquals(oracle.selectionBounds, f.selectionBounds)
        assertEquals(oracle.random.nextInt(Int.MAX_VALUE), source.nextInt(Int.MAX_VALUE))
    }

    @Test
    fun `Akkha native branches match all party sizes previous histories and double trouble flags`() {
        for (seed in seeds) {
            val source = AkkhaEncounter(UUID(0, seed), seed).apply { reset() }
            val oracle = Oracle(seed xor source.generation.toLong())
            val f = Fixture(AkkhaAssets.MELEE, toaAkkhaBossSpec(), source::nextInt)
            var tick = 100
            for (partySize in 1..8) {
                for (double in listOf(false, true)) {
                    for (previous in listOf(null) + AkkhaSpecial.entries) {
                        f.encounter.akkhaSpecialHistory().previous = previous
                        val context =
                            "seed=$seed party=$partySize double=$double previous=$previous"
                        val expected = oracle.akkha(partySize, double, previous)
                        val selected = f.encounter.selectAkkhaSpecials(tick++, partySize, double)
                        assertEquals(expected, selected, context)
                        assertEquals(
                            previous,
                            f.encounter.akkhaSpecialHistory().previous,
                            "$context before payloads",
                        )
                        assertEquals(oracle.selectionBounds, f.selectionBounds, context)
                        // Existing payload/extras consume this same RNG after BOTH choices.
                        for (bound in listOf(4, 2, 29, Int.MAX_VALUE)) {
                            assertEquals(
                                oracle.random.nextInt(bound),
                                source.nextInt(bound),
                                "$context next=$bound",
                            )
                        }
                        f.encounter.akkhaSpecialHistory().previous = selected.last()
                        f.assertOrdinaryUnchanged()
                    }
                }
            }
            f.assertDefaultRandomUntouched()
        }
    }

    @Test
    fun `Akkha solo double is fixed without draws and team double finishes both choices before payload RNG`() {
        val source = AkkhaEncounter(UUID(0, 99), 99).apply { reset() }
        val oracle = Oracle(99L xor source.generation.toLong())
        val f = Fixture(AkkhaAssets.MELEE, toaAkkhaBossSpec(), source::nextInt)
        var previous: AkkhaSpecial? = null
        repeat(256) { index ->
            val partySize = if (index % 3 == 0) 1 else 3
            val double = index % 5 != 0
            f.encounter.akkhaSpecialHistory().previous = previous
            val expected = oracle.akkha(partySize, double, previous)
            val drawsBefore = f.selectionBounds.size
            val selected = f.encounter.selectAkkhaSpecials(100 + index, partySize, double)
            assertEquals(expected, selected)
            assertEquals(
                if (partySize == 1 && double) 0 else if (double) 2 else 1,
                f.selectionBounds.size - drawsBefore,
            )
            assertEquals(previous, f.encounter.akkhaSpecialHistory().previous)
            for (special in selected) {
                val payloadBound =
                    when (special) {
                        AkkhaSpecial.MEMORY_BLAST -> 4
                        AkkhaSpecial.TRAILING_ORBS -> 8
                        AkkhaSpecial.DETONATE -> 31
                    }
                assertEquals(oracle.random.nextInt(payloadBound), source.nextInt(payloadBound))
            }
            previous = selected.last()
            f.encounter.akkhaSpecialHistory().previous = previous
            f.encounter.transitionTo(if (index % 2 == 0) "ranged" else "magic", 100 + index)
            assertEquals(previous, f.encounter.akkhaSpecialHistory().previous)
        }
        assertEquals(oracle.selectionBounds, f.selectionBounds)
        assertEquals(oracle.random.nextInt(Int.MAX_VALUE), source.nextInt(Int.MAX_VALUE))
    }

    @Test
    fun `native registry first creation fixes the shared RNG and removal retires history without another draw`() {
        for (baba in listOf(true, false)) {
            val source = Random(42)
            val oracle = Oracle(42)
            val f =
                Fixture(
                    if (baba) BabaAssets.BABA else AkkhaAssets.MELEE,
                    if (baba) toaBabaBossSpec() else toaAkkhaBossSpec(),
                    source::nextInt,
                )
            assertSame(f.encounter, f.registry.of(f.npc, DefaultGameRandom(-1)))
            if (baba) {
                assertEquals(oracle.baba(null, true), f.encounter.selectBabaSpecial(100, true))
            } else {
                val expected = oracle.akkha(3, true, null)
                assertEquals(expected, f.encounter.selectAkkhaSpecials(100, 3, true))
                f.encounter.akkhaSpecialHistory().previous = expected.last()
            }
            assertEquals(oracle.selectionBounds, f.selectionBounds)
            val retired = f.encounter
            f.registry.remove(f.npc)
            assertTrue(retired.isEnded)
            val before = f.selectionBounds.toList()
            if (baba)
                assertFailsWith<IllegalStateException> { retired.selectBabaSpecial(101, true) }
            else
                assertFailsWith<IllegalStateException> { retired.selectAkkhaSpecials(101, 1, true) }
            assertEquals(before, f.selectionBounds)
            val fresh = f.registry.of(f.npc, ToaSpecialSelectionRandom(source::nextInt))
            assertNotSame(retired, fresh)
            if (baba) assertNull(fresh.babaSpecialHistory().previous)
            else assertNull(fresh.akkhaSpecialHistory().previous)
            assertEquals(oracle.random.nextInt(Int.MAX_VALUE), source.nextInt(Int.MAX_VALUE))
        }
    }

    private class Fixture(id: Int, spec: BossSpec, nextInt: (Int) -> Int) {
        val selectionBounds = mutableListOf<Int>()
        private val defaultRandom = DefaultGameRandom(9876)
        val registry = EncounterRegistry(defaultRandom)
        val npc =
            Npc(assertNotNull(ServerCacheManager.getNpc(id)), CoordGrid(3500, 9500)).apply {
                slotId = 1
                assignUid()
                infoProtocol = NoopNpcInfo
                actionDelay = 106
            }
        val encounter: BossEncounter
        private val originalPhase: String

        init {
            registry.register(id, spec, "toa-test-specials")
            encounter =
                registry.of(
                    npc,
                    ToaSpecialSelectionRandom { bound ->
                        selectionBounds += bound
                        nextInt(bound)
                    },
                )
            originalPhase = encounter.currentPhaseName
            encounter.lastAbilityTick = 100
            encounter.lastAbilityName = "ordinary"
            encounter.initializeDecisionSeed(0)
            npc.lockFacing(CoordGrid(3505, 9505), targetWidth = 2, targetLength = 3)
        }

        fun assertOrdinaryUnchanged() {
            assertEquals(originalPhase, encounter.currentPhaseName)
            assertEquals("ordinary", encounter.lastAbilityName)
            assertEquals(100, encounter.lastAbilityTick)
            assertEquals(0, encounter.phaseAbilityCount)
            assertEquals(6, encounter.expectedAttackRate())
            assertEquals(106, npc.actionDelay)
            assertTrue(encounter.recentAttackTraces().isEmpty())
            assertEquals(CoordGrid(3505, 9505), npc.faceLockSquare)
            assertEquals(2, npc.faceLockWidth)
            assertEquals(3, npc.faceLockLength)
        }

        fun assertDefaultRandomUntouched() =
            assertEquals(DefaultGameRandom(9876).of(Int.MAX_VALUE), defaultRandom.of(Int.MAX_VALUE))
    }

    /** Literal checkpoint rules, independent of the new declarative pools and native selection. */
    private class Oracle(seed: Long) {
        val random = Random(seed)
        val selectionBounds = mutableListOf<Int>()

        private fun draw(bound: Int): Int {
            selectionBounds += bound
            return random.nextInt(bound)
        }

        fun baba(previous: BabaSpecial?, allowRockfall: Boolean): BabaSpecial {
            val selectable =
                listOf(BabaSpecial.ROCKFALL, BabaSpecial.SHOCKWAVE, BabaSpecial.BABOON_DISCHARGE)
            val available = if (allowRockfall) selectable else selectable - BabaSpecial.ROCKFALL
            val choices = available.filter { it != previous }.ifEmpty { available }
            return choices[draw(choices.size).coerceIn(choices.indices)]
        }

        fun akkha(party: Int, double: Boolean, previous: AkkhaSpecial?): List<AkkhaSpecial> {
            if (double && party == 1)
                return listOf(AkkhaSpecial.MEMORY_BLAST, AkkhaSpecial.TRAILING_ORBS)
            val pool =
                if (party > 1) AkkhaSpecial.entries
                else listOf(AkkhaSpecial.MEMORY_BLAST, AkkhaSpecial.TRAILING_ORBS)
            val firstPool = pool.filterNot { pool.size > 1 && it == previous }
            val first = firstPool[draw(firstPool.size)]
            if (!double) return listOf(first)
            val secondPool = pool.filterNot { it == first }
            return listOf(first, secondPool[draw(secondPool.size)])
        }
    }

    companion object {
        private val seeds = (-16L..16L).toList() + listOf(Long.MIN_VALUE, Long.MAX_VALUE, 42L)

        @BeforeAll
        @JvmStatic
        fun cache() {
            ToaCacheTestSupport.initRevision240()
        }
    }
}
