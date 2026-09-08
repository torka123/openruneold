package org.rsmod.content.slayer.core

/** One authoritative Slayer reward-point calculator for every assignment master. */
object SlayerPointRules {

    private val basePointsByMaster =
        mapOf(
            1 to 0, // Turael / Aya
            2 to 6, // Mazchna / Achtryn
            3 to 8, // Vannaka
            4 to 10, // Chaeldar
            5 to 15, // Duradel / Kuradal
            6 to 12, // Nieve / Steve
            7 to 25, // Krystilia
            8 to 18, // Konar
            9 to 0, // Spria
            10 to 0, // Mortimer (his selected modifier awards points instead)
        )

    fun basePoints(masterId: Int): Int = basePointsByMaster[masterId] ?: 0

    fun pointsForCompletedTask(masterId: Int, streak: Int, totalTasksDone: Int): Int {
        val base = basePoints(masterId)
        if (base <= 0 || totalTasksDone <= NO_POINTS_THROUGH_TOTAL_TASK) return 0
        return base * milestoneMultiplier(streak)
    }

    fun milestoneMultiplier(streak: Int): Int {
        val milestone = wrappedMilestone(streak)
        return when {
            milestone > 0 && milestone % 1_000 == 0 -> 50
            milestone > 0 && milestone % 250 == 0 -> 35
            milestone > 0 && milestone % 100 == 0 -> 25
            milestone > 0 && milestone % 50 == 0 -> 15
            milestone > 0 && milestone % 10 == 0 -> 5
            else -> 1
        }
    }

    private fun wrappedMilestone(streak: Int): Int {
        if (streak <= STREAK_MILESTONE_WRAP_THRESHOLD) return streak
        val remainder = streak % MILESTONE_CYCLE
        return if (remainder == 0) MILESTONE_CYCLE else remainder
    }

    private const val NO_POINTS_THROUGH_TOTAL_TASK = 5
    private const val STREAK_MILESTONE_WRAP_THRESHOLD = 16_000
    private const val MILESTONE_CYCLE = 1_000
}
