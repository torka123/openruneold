package org.rsmod.content.minigames.cox

import kotlin.random.Random

internal object CoxLayoutGenerator {
    private val combat =
        listOf(
            CoxRoom.TEKTON,
            CoxRoom.VASA,
            CoxRoom.VESPULA,
            CoxRoom.VANGUARDS,
            CoxRoom.MUTTADILES,
            CoxRoom.SHAMANS,
            CoxRoom.SKELETAL_MYSTICS,
            CoxRoom.GUARDIANS,
        )
    private val puzzles =
        listOf(CoxRoom.ICE_DEMON, CoxRoom.JEWELLED_CRABS, CoxRoom.TIGHTROPE, CoxRoom.THIEVING)

    /* Challenge Mode has a fixed, complete room order. */
    internal val challengeOrder =
        listOf(
            CoxRoom.START,
            CoxRoom.TEKTON,
            CoxRoom.JEWELLED_CRABS,
            CoxRoom.SCAVENGERS,
            CoxRoom.ICE_DEMON,
            CoxRoom.SHAMANS,
            CoxRoom.RESOURCE_FISHING,
            CoxRoom.VANGUARDS,
            CoxRoom.THIEVING,
            CoxRoom.VESPULA,
            CoxRoom.SKELETAL_MYSTICS,
            CoxRoom.TIGHTROPE,
            CoxRoom.GUARDIANS,
            CoxRoom.VASA,
            CoxRoom.MUTTADILES,
            CoxRoom.RESOURCE_HUNTER,
            CoxRoom.OLM,
        )

    fun generate(seed: Long, difficulty: RaidDifficulty): CoxLayout {
        val rooms =
            when (difficulty) {
                RaidDifficulty.CHALLENGE -> challengeOrder
                RaidDifficulty.NORMAL -> normalOrder(Random(seed))
            }
        val floorBreak = if (difficulty == RaidDifficulty.CHALLENGE) 8 else rooms.size / 2
        val placements =
            rooms.mapIndexed { index, room ->
                val floor = if (index <= floorBreak) 0 else 1
                val floorIndex = if (floor == 0) index else index - floorBreak - 1
                CoxRoomPlacement(
                    room = room,
                    index = index,
                    floor = floor,
                    destinationZoneX = 4,
                    destinationZoneZ = floorIndex * 4,
                )
            }
        return CoxLayout(seed, difficulty, placements)
    }

    private fun normalOrder(random: Random): List<CoxRoom> {
        val selectedCombat = combat.shuffled(random).take(5)
        val selectedPuzzles = puzzles.shuffled(random).take(3)
        val middle = (selectedCombat + selectedPuzzles).shuffled(random).toMutableList()
        middle.add(random.nextInt(middle.size + 1), CoxRoom.SCAVENGERS)
        middle.add(
            (middle.size / 2).coerceAtLeast(1),
            if (random.nextBoolean()) CoxRoom.RESOURCE_FISHING else CoxRoom.RESOURCE_HUNTER,
        )
        return listOf(CoxRoom.START) + middle + CoxRoom.OLM
    }

    fun isConnected(layout: CoxLayout): Boolean =
        layout.placements.zipWithNext().all { (a, b) ->
            b.index == a.index + 1 && (b.floor == a.floor || b.floor == a.floor + 1)
        }
}
