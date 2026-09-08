package org.rsmod.content.drops.tables.monsters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.rsmod.api.collectionlog.CollectionLogCatalogRepository
import org.rsmod.api.droptable.NpcKillRewardContext

class TheLeviathanDropTableTest {
    @Test
    fun `only repeatable leviathan is registered`() {
        assertEquals(listOf("npc.leviathan"), theLeviathanDropTable.npcs)
        assertEquals("The Leviathan Drops", theLeviathanDropTable.tableIdentifier)
    }

    @Test
    fun `collection log catalog contains the ten canonical leviathan uniques in order`() {
        val section =
            CollectionLogCatalogRepository().sections.single { it.name == "The Leviathan" }
        assertEquals(
            listOf(
                "Lil'viathan",
                "Leviathan's lure",
                "Virtus mask",
                "Virtus robe top",
                "Virtus robe bottom",
                "Venator vestige",
                "Smoke quartz",
                "Scarred tablet",
                "Chromium ingot",
                "Awakener's orb",
            ),
            section.slots.map { it.name },
        )
    }

    @Test
    fun `standard table has exact one hundred weight and confirmed quantity pairs`() {
        assertEquals(100, leviathanStandardRewards.sumOf(LeviathanStandardReward::weight))
        assertEquals(1_500 to 2_250, quantities("obj.cosmic_soul_catalyst"))
        assertEquals(67 to 101, quantities("obj.cert_gold_ore"))
        assertEquals(27 to 40, quantities("obj.cert_runite_ore"))
        assertEquals(600 to 900, quantities("obj.soulrune"))
        assertEquals(22, leviathanStandardRewards.single { it.obj == null }.weight)
    }

    @Test
    fun `failed unique chain stops at orb before later branches`() {
        val result = roll(LeviathanRewardState(), values = intArrayOf(95, 0))
        assertEquals(LeviathanRewardBranch.AwakenersOrb, result.branch)
        assertEquals(listOf("obj.dt2_awakeners_orb"), result.items.map { it.obj })
    }

    @Test
    fun `vestige progress drops one then two gold rings and third hit drops vestige`() {
        val first = roll(LeviathanRewardState(), values = intArrayOf(0, 1))
        assertEquals(LeviathanRewardBranch.VestigeProgress, first.branch)
        assertEquals(1..1, first.items.single().count)
        assertEquals(1, first.state.vestigeProgress)

        val second = roll(first.state, values = intArrayOf(0, 1))
        assertEquals(1..1, second.items.single().count)
        assertEquals(2, second.state.vestigeProgress)

        val third = roll(second.state, values = intArrayOf(0, 1))
        assertEquals(LeviathanRewardBranch.Vestige, third.branch)
        assertEquals("obj.venator_vestige", third.items.single().obj)
        assertEquals(0, third.state.vestigeProgress)
    }

    @Test
    fun `supply branch returns all three supplies together`() {
        val result =
            roll(
                LeviathanRewardState(tabletReceived = true, quartzReceived = true),
                values = intArrayOf(95, 52, 199, 0),
            )
        assertEquals(LeviathanRewardBranch.Supplies, result.branch)
        assertEquals(
            listOf("obj.3doseprayerrestore", "obj.2doserangerspotion", "obj.seaturtle"),
            result.items.map { it.obj },
        )
        assertEquals(3..4, result.items.last().count)
    }

    @Test
    fun `perfect multiplier changes only exact standard quantity`() {
        val state = LeviathanRewardState(tabletReceived = true, quartzReceived = true)
        val normal = roll(state, values = intArrayOf(95, 52, 199, 4, 1), perfect = false)
        val perfect = roll(state, values = intArrayOf(95, 52, 199, 4, 1), perfect = true)
        assertEquals(LeviathanRewardBranch.Standard, normal.branch)
        assertEquals("obj.cert_iron_ore", normal.items.single().obj)
        assertEquals(57..57, normal.items.single().count)
        assertEquals(85..85, perfect.items.single().count)
    }

    @Test
    fun `quartz first-drop probability scales between documented endpoints then resets`() {
        assertEquals(100, LeviathanRewardEngine.quartzNumerator(0, false))
        assertEquals(250, LeviathanRewardEngine.quartzNumerator(150, false))
        assertEquals(400, LeviathanRewardEngine.quartzNumerator(300, false))
        assertEquals(400, LeviathanRewardEngine.quartzNumerator(5_000, false))
        assertEquals(100, LeviathanRewardEngine.quartzNumerator(5_000, true))
    }

    @Test
    fun `pet and one of four clues are independent of main reward`() {
        val result =
            roll(
                LeviathanRewardState(tabletReceived = true, quartzReceived = true),
                values = intArrayOf(95, 52, 199, 4, 99, 0, 0, 2),
            )
        assertTrue(result.items.any { it.obj == "obj.leviathanpet" })
        assertTrue(result.items.any { it.obj == "obj.trail_clue_hard_map001" && it.clueBase })
    }

    @Test
    fun `ineligible kill neither rolls nor mutates persistent progress`() {
        val state = LeviathanRewardState(eligibleKills = 12, vestigeProgress = 2)
        val random = ScriptedRandom(intArrayOf(0))
        val result =
            LeviathanRewardEngine.roll(
                state,
                NpcKillRewardContext(false, awakened = false, perfectKill = true),
                random,
            )
        assertEquals(LeviathanRewardBranch.Ineligible, result.branch)
        assertEquals(state, result.state)
        assertEquals(0, random.calls)
    }

    @Test
    fun `awakened unique uses three successful slots out of ninety six`() {
        val context = NpcKillRewardContext(true, awakened = true, perfectKill = false)
        for (uniqueRoll in 0..2) {
            val result =
                LeviathanRewardEngine.roll(
                    LeviathanRewardState(),
                    context,
                    ScriptedRandom(intArrayOf(uniqueRoll, 7)),
                )
            assertEquals(LeviathanRewardBranch.LeviathansLure, result.branch)
        }
        val failure =
            LeviathanRewardEngine.roll(
                LeviathanRewardState(tabletReceived = true, quartzReceived = true),
                context,
                ScriptedRandom(intArrayOf(3, 0)),
            )
        assertEquals(LeviathanRewardBranch.AwakenersOrb, failure.branch)
    }

    private fun quantities(obj: String): Pair<Int, Int> =
        leviathanStandardRewards
            .single { it.obj == obj }
            .let { it.normalQuantity to it.perfectQuantity }

    private fun roll(
        state: LeviathanRewardState,
        values: IntArray,
        perfect: Boolean = false,
    ): LeviathanRewardResult =
        LeviathanRewardEngine.roll(
            state = state,
            context =
                NpcKillRewardContext(
                    rewardEligible = true,
                    awakened = false,
                    perfectKill = perfect,
                ),
            random = ScriptedRandom(values),
        )

    private class ScriptedRandom(private val values: IntArray) : LeviathanRewardRandom {
        var calls = 0
            private set

        override fun nextInt(bound: Int): Int {
            val value = if (calls < values.size) values[calls] else bound - 1
            calls++
            require(value in 0 until bound) { "Scripted value $value is outside 0 until $bound" }
            return value
        }
    }
}
