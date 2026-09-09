package org.rsmod.content.skills.agility.rooftop

import jakarta.inject.Inject
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.config.constants
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.script.onApLoc1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.skills.agility.AgilityAnims
import org.rsmod.content.skills.agility.balanceAlong
import org.rsmod.content.skills.agility.climbTo
import org.rsmod.content.skills.agility.fallTo
import org.rsmod.content.skills.agility.leapTo
import org.rsmod.content.skills.agility.seqGlideTicks
import org.rsmod.content.skills.agility.seqTicks
import org.rsmod.content.skills.agility.stepOnto
import org.rsmod.content.skills.agility.successChance
import org.rsmod.content.skills.agility.zipTo
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Handles every obstacle of the rooftop agility courses.
 *
 * Uses native protected loc interactions and movement. Only a full, ordered lap pays the bonus.
 */
class RooftopScript
@Inject
constructor(private val marks: MarksOfGrace, private val routes: RouteFactory) : PluginScript() {
    override fun ScriptContext.startup() {
        for (layout in RooftopCourses.layouts) {
            for ((index, obstacle) in layout.obstacles.withIndex()) {
                for (loc in obstacle.locs) {
                    onOpLoc1(loc) { attempt(layout, index, obstacle) }
                    if (obstacle.apRange > 0) {
                        onApLoc1(loc) {
                            if (isWithinApRange(it.loc, obstacle.apRange)) {
                                attempt(layout, index, obstacle)
                            }
                        }
                    }
                }
            }
        }
        onPlayerLogout { player.courseProgress = 0 }
    }

    internal suspend fun ProtectedAccess.attempt(
        layout: CourseLayout,
        index: Int,
        obstacle: RooftopObstacle,
    ) {
        val course = layout.course
        // Invalidate before any suspension, including arrival and approach.
        val ordered = index == 0 || player.progressOn(course) == index
        player.courseProgress = 0
        arriveDelay()
        if (player.agilityLvl < course.level) {
            mes("You need an Agility level of ${course.level} to use this course.")
            return
        }
        if (!stepOnto(obstacle.start, routes)) return

        val failure = obstacle.failure?.takeIf { rollFailure(course, it) }
        val completed = perform(obstacle.move, failure)
        if (!completed || coords != obstacle.move.destination || player.hitpoints <= 0) {
            player.courseProgress = 0
            return
        }

        statAdvance(AGILITY, obstacle.xp)
        val restored = (player.runEnergy + random.of(10, 20)).coerceAtMost(constants.run_max_energy)
        if (restored != player.runEnergy) {
            player.runEnergy = restored
            UpdateRun.energy(player, restored)
        }
        if (!obstacle.isFinish) {
            if (ordered) player.courseProgress = pack(course, index + 1)
            return
        }

        if (ordered) {
            statAdvance(AGILITY, obstacle.lapBonusXp)
            if (marks.roll(player, random, layout)) {
                mes("A mark of grace has appeared somewhere on the course.")
            }
        }
    }

    private fun ProtectedAccess.rollFailure(
        course: RooftopCourse,
        failure: ObstacleFailure,
    ): Boolean {
        val level = player.agilityLvl
        if (level >= failure.noFailLevel) {
            return false
        }
        val chance = successChance(level, course.level, failure.noFailLevel)
        return random.of(100) >= chance
    }

    /** Runs [move], or the failure sequence when [failure] is set. Returns `false` on a fail. */
    private suspend fun ProtectedAccess.perform(
        move: ObstacleMove,
        failure: ObstacleFailure?,
    ): Boolean {
        when (move) {
            is ObstacleMove.Climb -> {
                val ticks = move.ticks ?: seqTicks(move.seq, fallback = 2)
                climbTo(move.dest, move.seq, ticks)
            }
            is ObstacleMove.Leap -> {
                if (failure != null) {
                    fallTo(failure.landing, move.seq, failure.minDamage, failure.maxDamage)
                    return false
                }
                val ticks = move.ticks ?: seqGlideTicks(move.seq, fallback = 2)
                leapTo(move.dest, move.seq, ticks, glideLevel = move.glideLevel ?: coords.level)
            }
            is ObstacleMove.Zipline -> {
                if (failure != null) {
                    fallTo(
                        failure.landing,
                        AgilityAnims.ZIPLINE_GRAB,
                        failure.minDamage,
                        failure.maxDamage,
                    )
                    return false
                }
                zipTo(move.dest, move.ticks)
            }
            is ObstacleMove.Balance -> {
                val stopAt = if (failure != null) move.path.size / 2 else -1
                val crossed = balanceAlong(move.path, move.style, stopAt)
                if (!crossed && failure != null) {
                    // A cancelled/externally moved player must not be pulled into a scripted fall.
                    if (coords != move.path[stopAt - 1] || player.hitpoints <= 0) return false
                    fallTo(
                        failure.landing,
                        AgilityAnims.BALANCE_STUMBLE,
                        failure.minDamage,
                        failure.maxDamage,
                    )
                    return false
                }
            }
        }
        return true
    }

    private companion object {
        const val AGILITY = "stat.agility"

        /** Packed `course ordinal shl 16 or last completed stage`; not persisted. */
        val COURSE_PROGRESS = AttributeKey<Int>()

        var Player.courseProgress: Int
            get() = attr[COURSE_PROGRESS] ?: 0
            set(value) {
                attr[COURSE_PROGRESS] = value
            }

        fun Player.progressOn(course: RooftopCourse): Int {
            val packed = courseProgress
            return if (packed shr 16 == course.ordinal) packed and 0xFFFF else 0
        }

        fun pack(course: RooftopCourse, stage: Int): Int = (course.ordinal shl 16) or stage
    }
}
