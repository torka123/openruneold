package org.rsmod.content.drops.tables.monsters

import dev.openrune.ServerCacheManager
import dtx.core.ArgMap
import dtx.core.RollRandom
import dtx.core.RollResult
import dtx.core.with
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.mockito.Mockito.mock
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.DropTableRegistry
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.drops.toml.ContentDropTableTomlResolver
import org.rsmod.content.quest.manager.QuestRequirementResolver
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

class RoyalTitansDropTableTest {
    init {
        if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
    }

    @Test
    fun `both tables perform two independent standard rolls including duplicate items`() {
        for (table in tables) {
            val random = ScriptedRollRandom(ints = listOf(0, 0))
            val result = table.roll(Player(), random.args()).items()
            assertEquals(2, result.size)
            assertEquals(result[0], result[1])
            assertEquals(
                listOf(if (table === brandaTheFireQueenDropTable) 55 else 56).repeatTwice(),
                random.bounds,
            )
            assertEquals(6, random.chanceCalls)
        }
    }

    @Test
    fun `every unique replaces standards without suppressing a clue`() {
        for (table in tables) {
            val uniqueIds =
                if (table === brandaTheFireQueenDropTable)
                    listOf(
                        "obj.twinflame_piece_2",
                        "obj.mystic_vigour_prayer_scroll",
                        "obj.giantsoul_amulet_uncharged",
                    )
                else
                    listOf(
                        "obj.twinflame_piece_1",
                        "obj.deadeye_prayer_scroll",
                        "obj.giantsoul_amulet_uncharged",
                    )
            for (selected in 0..2) {
                val chances =
                    MutableList(6) { 99.0 }
                        .apply {
                            this[selected] = 0.0
                            this[4] = 0.0
                        }
                val random = ScriptedRollRandom(chances = chances)
                val result = table.roll(Player(), random.args()).items()
                assertEquals(
                    setOf(uniqueIds[selected], "obj.trail_clue_hard_map001"),
                    result.map { it.obj }.toSet(),
                )
                assertTrue(random.bounds.isEmpty(), "A unique must skip both standard selections")
            }
        }
    }

    @Test
    fun `learned scroll is excluded before selection and does not consume standard rolls`() {
        for ((table, unlock) in
            tables.zip(
                listOf("varbit.prayer_mystic_vigour_unlocked", "varbit.prayer_deadeye_unlocked")
            )) {
            val player = Player()
            VarPlayerIntMapSetter.set(player, unlock, 1)
            val random = ScriptedRollRandom(ints = listOf(0, 0))
            val result = table.roll(player, random.args()).items()
            assertEquals(2, result.size)
            assertEquals(5, random.chanceCalls, "Learned scroll must not even roll")
            val allUniques =
                table.roll(player, ScriptedRollRandom(chances = List(5) { 0.0 }).args()).items()
            assertFalse(allUniques.any { "prayer_scroll" in it.obj })
        }
    }

    @Test
    fun `unclaimed CA qualification does not improve hard or elite clue chances`() {
        for (state in 0..2) {
            val player = Player()
            VarPlayerIntMapSetter.set(player, "varbit.ca_tier_status_hard", state)
            VarPlayerIntMapSetter.set(player, "varbit.ca_tier_status_elite", state)
            val random = ScriptedRollRandom(chances = listOf(99.0, 4.1, 1.025))
            val result = royalTitansTertiaryTable().roll(player, random.args()).items()
            assertEquals(if (state == 2) 2 else 0, result.size)
            assertEquals(
                3,
                random.chanceCalls,
                "One pet, one eligible hard rate, one eligible elite rate",
            )
        }
    }

    @Test
    fun `sacrifice doubles only pet rate and still rolls both clue tiers`() {
        val normal =
            royalTitansTertiaryTable()
                .roll(Player(), ScriptedRollRandom(chances = listOf(0.05, 0.0, 0.0)).args())
                .items()
        val sacrifice =
            royalTitansTertiaryTable(true)
                .roll(Player(), ScriptedRollRandom(chances = listOf(0.05, 0.0, 0.0)).args())
                .items()
        assertEquals(
            listOf("obj.trail_clue_hard_map001", "obj.trail_elite_emote_exp1"),
            normal.map { it.obj },
        )
        assertEquals(listOf("obj.rtbrandapet") + normal.map { it.obj }, sacrifice.map { it.obj })
    }

    @Test
    fun `all standard weight slots resolve and retain the existing quantities`() {
        for ((table, denominator) in tables.zip(listOf(55, 56))) {
            val counts = mutableMapOf<String, Int>()
            for (index in 0 until denominator) {
                val result =
                    table
                        .roll(Player(), ScriptedRollRandom(ints = listOf(index, index)).args())
                        .items()
                assertEquals(2, result.size)
                assertEquals(result[0], result[1])
                assertTrue(result[0].count.first > 0)
                counts.merge(result[0].obj, 1, Int::plus)
            }
            assertEquals(denominator, counts.values.sum())
            assertEquals(1, counts["obj.maple_seed"])
            assertEquals(1, counts["obj.palm_tree_seed"])
            assertEquals(1, counts["obj.yew_seed"])
            assertEquals(if (denominator == 55) 2 else 1, counts["obj.rune_pickaxe"])
            assertEquals(2, counts["obj.desiccated_page"])
        }
    }

    @Test
    fun `transformed corpse resolves the original registered table without proxy construction`() {
        val registry =
            DropTableRegistry(
                ContentDropTableTomlResolver(
                    mock(AreaChecker::class.java),
                    mock(QuestRequirementResolver::class.java),
                )
            )
        for ((ids, expected) in
            listOf(
                (12596 to 14148) to brandaTheFireQueenDropTable,
                (14147 to 14149) to eldricTheIceKingDropTable,
            )) {
            val npc =
                Npc(assertNotNull(ServerCacheManager.getNpc(ids.first)), CoordGrid(2910, 9571))
            npc.transmog(assertNotNull(ServerCacheManager.getNpc(ids.second)), Int.MAX_VALUE)
            assertEquals(ids.first, npc.id)
            assertEquals(ids.second, npc.visType.id)
            assertSame(expected, registry.forNpc(npc))
        }
    }

    private val tables
        get() = listOf(brandaTheFireQueenDropTable, eldricTheIceKingDropTable)

    private fun List<Int>.repeatTwice() = this + this

    private fun RollResult<DropRollItem>.items(): List<DropRollItem> =
        when (this) {
            RollResult.Nothing -> emptyList()
            is RollResult.Single -> listOf(result)
            is RollResult.ListOf -> results
        }

    private class ScriptedRollRandom(
        ints: List<Int> = emptyList(),
        chances: List<Double> = emptyList(),
    ) : RollRandom {
        private val integers = ArrayDeque(ints)
        private val percentages = ArrayDeque(chances)
        val bounds = mutableListOf<Int>()
        var chanceCalls = 0

        override fun nextInt(bound: Int): Int {
            bounds += bound
            return integers.removeFirst().also { require(it in 0 until bound) }
        }

        override fun nextDouble(bound: Double): Double {
            chanceCalls++
            return (percentages.removeFirstOrNull() ?: 99.0).also { require(it >= 0 && it < bound) }
        }

        fun args() = ArgMap(RollRandom.Context with this)
    }
}
