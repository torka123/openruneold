package org.rsmod.content.bosses.scorpia

import jakarta.inject.Inject
import org.rsmod.api.player.events.interact.LocCoordEvents
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/** All three revision-240 Scorpion Pit entrances and all three matching cavern exits. */
public class ScorpiaAccess @Inject constructor(private val collision: CollisionFlagMap) : PluginScript() {
    override fun ScriptContext.startup() {
        SCORPIA_ENTRANCE_LINKS.keys.forEach { source ->
            val key = LocCoordEvents.key(SCORPIA_CAVERN_ID, source)
            onProtectedEvent<LocCoordEvents.Op1>(key) {
                traverse(it.loc.coords, SCORPIA_ENTRANCE_LINKS, "You enter the Scorpion Pit cavern.")
            }
            check(eventBus.contains(LocCoordEvents.Op1::class.java, key)) {
                "Scorpia cavern entrance was not registered at $source"
            }
        }
        SCORPIA_EXIT_LINKS.keys.forEach { source ->
            val key = LocCoordEvents.key(SCORPIA_CREVICE_ID, source)
            onProtectedEvent<LocCoordEvents.Op1>(key) {
                traverse(it.loc.coords, SCORPIA_EXIT_LINKS, "You climb back out of the cavern.")
            }
            check(eventBus.contains(LocCoordEvents.Op1::class.java, key)) {
                "Scorpia cavern exit was not registered at $source"
            }
        }
    }

    private suspend fun ProtectedAccess.traverse(
        source: CoordGrid,
        links: Map<CoordGrid, CoordGrid>,
        message: String,
    ) {
        arriveDelay()
        val destination = links[source] ?: run {
            mes("That cave route is unavailable.")
            return
        }
        // Physical cave traversal is not a spell teleport and must not be rejected by the
        // level-20 Wilderness teleport restriction or an active teleblock.
        val safe = safeDestination(destination)
        if (safe == null) {
            mes("That cave route has no safe arrival tile.")
            return
        }
        telejump(safe, SCORPIA_TRAVERSAL_TYPE)
        if (coords == safe) mes(message)
    }

    private fun safeDestination(preferred: CoordGrid): CoordGrid? =
        buildList {
            add(preferred)
            for (radius in 1..2) {
                for (dx in -radius..radius) for (dz in -radius..radius) {
                    if (kotlin.math.max(kotlin.math.abs(dx), kotlin.math.abs(dz)) == radius) {
                        add(preferred.translate(dx, dz))
                    }
                }
            }
        }.firstOrNull { collision.isZoneValid(it) && !collision.isWalkBlocked(it) }
}

internal val SCORPIA_ENTRANCE_LINKS: Map<CoordGrid, CoordGrid> =
    mapOf(
        CoordGrid(3231, 3936) to CoordGrid(3233, 10332),
        CoordGrid(3231, 3951) to CoordGrid(3232, 10351),
        CoordGrid(3241, 3949) to CoordGrid(3243, 10351),
    )

internal val SCORPIA_EXIT_LINKS: Map<CoordGrid, CoordGrid> =
    mapOf(
        CoordGrid(3233, 10331) to CoordGrid(3232, 3938),
        CoordGrid(3232, 10352) to CoordGrid(3232, 3950),
        CoordGrid(3243, 10352) to CoordGrid(3241, 3948),
    )

internal const val SCORPIA_CAVERN: String = "loc.wilderness_scorpion_entrance"
internal const val SCORPIA_CREVICE: String = "loc.wilderness_scorpion_exit"
internal const val SCORPIA_CAVERN_ID: Int = 26762
internal const val SCORPIA_CREVICE_ID: Int = 26763
internal val SCORPIA_TRAVERSAL_TYPE: TeleportType = TeleportType.Exempt
