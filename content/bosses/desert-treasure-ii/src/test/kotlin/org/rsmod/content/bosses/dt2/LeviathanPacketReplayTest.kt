package org.rsmod.content.bosses.dt2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Ordered replay fixture decoded from rsprox-3285-rev234, not the legacy 555/rev227 label. */
class LeviathanPacketReplayTest {
    private data class Event(val cycle: Int, val kind: String, val id: Int, val value: Int = 0)

    private val replay =
        listOf(
            Event(17350, "player_sequence", 828),
            Event(17351, "inside_entry_x", 0, 23),
            Event(17351, "inside_entry_z", 0, 32),
            Event(17351, "rowboat_transform", 49224),
            Event(17355, "walk_in_x", 0, 27),
            Event(17355, "walk_in_z", 0, 40),
            Event(17356, "boss_sequence", 10291),
            Event(17356, "music", 750),
            Event(17356, "sound", 7037),
            Event(17357, "boss_hud_npc", 1683, 12214),
            Event(17357, "boss_hud_max", 1682, 900),
            Event(17360, "boss_sequence", 10278),
            Event(17360, "cast_graphic", 2484),
            Event(17360, "sound", 7041),
            Event(17361, "boss_sequence", 10281),
            Event(17407, "pathfinder_npc", 12220),
            Event(17407, "pathfinder_sequence", 10296),
            Event(17422, "killcount_varp", 3968, 1),
            Event(17422, "rowboat_transform", 49214),
            Event(17430, "boss_hud_npc", 1683, -1),
            Event(17430, "boss_hud_max", 1682, 0),
            Event(17452, "sound", 7037),
        )

    @Test
    fun `entry spawn HUD and first slow orb preserve capture order`() {
        fun cycle(kind: String, id: Int) = replay.first { it.kind == kind && it.id == id }.cycle
        assertTrue(cycle("player_sequence", 828) < cycle("boss_sequence", 10291))
        assertEquals(1, cycle("boss_hud_npc", 1683) - cycle("boss_sequence", 10291))
        assertEquals(4, cycle("walk_in_z", 0) - cycle("inside_entry_z", 0))
        assertEquals(3, cycle("boss_sequence", 10278) - cycle("boss_hud_npc", 1683))
        assertEquals(1, cycle("boss_sequence", 10281) - cycle("boss_sequence", 10278))
    }

    @Test
    fun `handholds only cross the wall and never teleport the player into the arena`() {
        assertEquals(23, replay.single { it.kind == "inside_entry_x" }.value)
        assertEquals(32, replay.single { it.kind == "inside_entry_z" }.value)
        assertEquals(27, replay.single { it.kind == "walk_in_x" }.value)
        assertEquals(40, replay.single { it.kind == "walk_in_z" }.value)
    }

    @Test
    fun `capture music and confirmed attack sounds use revision 240 ids`() {
        assertEquals(Dt2Assets.LEVIATHAN_MIDI, replay.single { it.kind == "music" }.id)
        assertEquals(
            listOf(17356, 17452),
            replay
                .filter { it.kind == "sound" && it.id == Dt2Assets.LEVIATHAN_APPEARANCE_SOUND }
                .map(Event::cycle),
        )
        assertEquals(Dt2Assets.LEVIATHAN_APPEARANCE_SOUND, replay.first { it.kind == "sound" }.id)
        assertEquals(
            Dt2Assets.LEVIATHAN_RANGED_ATTACK_SOUND,
            replay.single { it.kind == "sound" && it.cycle == 17360 }.id,
        )
    }

    @Test
    fun `Pathfinder death rowboat and HUD cleanup preserve capture order`() {
        val pathfinder = replay.single { it.kind == "pathfinder_npc" }.cycle
        val kill = replay.single { it.kind == "killcount_varp" }.cycle
        val rowboat = replay.last { it.kind == "rowboat_transform" }.cycle
        val hudClear = replay.last { it.kind == "boss_hud_npc" }.cycle
        assertTrue(pathfinder < kill)
        assertEquals(kill, rowboat)
        assertEquals(8, hudClear - kill)
        assertEquals(-1, replay.last { it.kind == "boss_hud_npc" }.value)
        assertEquals(0, replay.last { it.kind == "boss_hud_max" }.value)
    }
}
