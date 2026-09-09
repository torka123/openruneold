package org.rsmod.content.skills.agility.rooftop

import jakarta.inject.Inject
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.entity.Player

/** Keeps the existing local mark policy while rooftop movement is migrated from PR #216. */
class MarksOfGrace @Inject constructor(private val objects: ObjRepository) {
    private val lastMarkCycle = mutableMapOf<String, Int>()

    fun roll(player: Player, random: GameRandom, layout: CourseLayout): Boolean {
        val identity = player.displayName.lowercase()
        val now = player.currentMapClock
        val last = lastMarkCycle[identity]
        if (last != null && now - last < MARK_COOLDOWN_TICKS) return false
        val course = layout.course
        if (random.of(course.markDenominator) >= course.markNumerator) return false
        if (
            course != RooftopCourse.Canifis &&
                player.agilityLvl >= course.level + 20 &&
                random.of(5) != 0
        )
            return false
        objects.add(
            "obj.grace",
            random.pick(layout.markTiles),
            duration = MARK_DURATION,
            receiver = player,
            reveal = Int.MAX_VALUE,
        )
        lastMarkCycle[identity] = now
        return true
    }

    companion object {
        const val MARK_COOLDOWN_TICKS = 300
        const val MARK_DURATION = 1000
    }
}
