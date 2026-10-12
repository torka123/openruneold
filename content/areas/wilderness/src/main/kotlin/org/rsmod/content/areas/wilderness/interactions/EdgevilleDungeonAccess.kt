package org.rsmod.content.areas.wilderness.interactions

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.events.interact.LocCoordEvents
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.loc.LocLayerConstants

internal data class EdgevilleShortcutRoute(
    val approach: CoordGrid,
    val destination: CoordGrid,
    val direction: Int,
)

internal object EdgevilleDungeonShortcutRules {
    const val MONKEY_BARS_LEVEL: Int = 15
    const val MONKEY_BARS_XP: Double = 20.0
    const val PIPE_LEVEL: Int = 51
    const val MONKEY_BARS_OBJECT: Int = 23566
    const val PIPE_OBJECT: Int = 16511
    val MONKEY_BAR_SOURCES: Set<CoordGrid> =
        setOf(
            CoordGrid(3119, 9964),
            CoordGrid(3120, 9964),
            CoordGrid(3119, 9969),
            CoordGrid(3120, 9969),
        )
    val PIPE_SOURCES: Set<CoordGrid> = setOf(CoordGrid(3150, 9906), CoordGrid(3153, 9906))

    fun monkeyBars(source: CoordGrid, player: CoordGrid): EdgevilleShortcutRoute? {
        if (source !in MONKEY_BAR_SOURCES || player.level != source.level) return null
        val x = source.x
        return if (player.z <= 9966) {
            EdgevilleShortcutRoute(
                CoordGrid(x, 9963, source.level),
                CoordGrid(x, 9970, source.level),
                constants.em_face_north,
            )
        } else {
            EdgevilleShortcutRoute(
                CoordGrid(x, 9970, source.level),
                CoordGrid(x, 9963, source.level),
                constants.em_face_south,
            )
        }
    }

    fun pipe(source: CoordGrid, player: CoordGrid): EdgevilleShortcutRoute? {
        if (source !in PIPE_SOURCES || player.level != source.level) return null
        return if (player.x <= 3151) {
            EdgevilleShortcutRoute(
                CoordGrid(3149, 9906, source.level),
                CoordGrid(3154, 9906, source.level),
                constants.em_face_east,
            )
        } else {
            EdgevilleShortcutRoute(
                CoordGrid(3154, 9906, source.level),
                CoordGrid(3149, 9906, source.level),
                constants.em_face_west,
            )
        }
    }
}

public class EdgevilleDungeonAccess
@Inject
constructor(private val locRepo: LocRepository, private val collision: CollisionFlagMap) :
    PluginScript() {
    override fun ScriptContext.startup() {
        validateOption(TRAPDOOR_CLOSED.objectId, 1, "Open")
        validateOption(TRAPDOOR_OPENED.objectId, 1, "Climb-down")
        validateOption(TRAPDOOR_OPENED.objectId, 2, "Close")
        onProtectedEvent<LocCoordEvents.Op1>(
            LocCoordEvents.key(TRAPDOOR_CLOSED.objectId, TRAPDOOR_CLOSED.coords)
        ) {
            setTrapdoor(opening = true)
        }
        onProtectedEvent<LocCoordEvents.Op2>(
            LocCoordEvents.key(TRAPDOOR_OPENED.objectId, TRAPDOOR_OPENED.coords)
        ) {
            setTrapdoor(opening = false)
        }

        validateOption(EdgevilleDungeonShortcutRules.MONKEY_BARS_OBJECT, 1, "Swing across")
        for (source in EdgevilleDungeonShortcutRules.MONKEY_BAR_SOURCES) {
            onProtectedEvent<LocCoordEvents.Op1>(
                LocCoordEvents.key(EdgevilleDungeonShortcutRules.MONKEY_BARS_OBJECT, source)
            ) {
                crossMonkeyBars(source)
            }
        }

        validateOption(EdgevilleDungeonShortcutRules.PIPE_OBJECT, 1, "Squeeze-through")
        for (source in EdgevilleDungeonShortcutRules.PIPE_SOURCES) {
            onProtectedEvent<LocCoordEvents.Op1>(
                LocCoordEvents.key(EdgevilleDungeonShortcutRules.PIPE_OBJECT, source)
            ) {
                squeezePipe(source)
            }
        }
    }

    private suspend fun ProtectedAccess.setTrapdoor(opening: Boolean) {
        arriveDelay()
        if (!replaceTrapdoor(opening)) {
            mes("The trapdoor is already being used.")
            return
        }
        soundSynth(if (opening) "synth.door_open" else "synth.door_close")
    }

    private suspend fun ProtectedAccess.crossMonkeyBars(source: CoordGrid) {
        arriveDelay()
        if (stat("stat.agility") < EdgevilleDungeonShortcutRules.MONKEY_BARS_LEVEL) {
            mes("You need an Agility level of 15 to cross these monkey bars.")
            return
        }
        val route = EdgevilleDungeonShortcutRules.monkeyBars(source, player.coords)
        if (route == null || !safeDestination(route.destination)) {
            mes("You cannot cross the monkey bars from here.")
            return
        }
        playerWalkWithMinDelay(route.approach)
        if (player.coords != route.approach) {
            mes("You cannot reach that side of the monkey bars.")
            return
        }
        faceSquare(route.destination)
        anim(742)
        delay(1)
        anim(11805)
        exactMove(route.approach, route.destination, 0, 150, route.direction, TeleportType.Exempt)
        delay(5)
        anim(743)
        delay(1)
        if (player.coords == route.destination) {
            statAdvance("stat.agility", EdgevilleDungeonShortcutRules.MONKEY_BARS_XP)
        }
    }

    private suspend fun ProtectedAccess.squeezePipe(source: CoordGrid) {
        arriveDelay()
        if (stat("stat.agility") < EdgevilleDungeonShortcutRules.PIPE_LEVEL) {
            mes("You need an Agility level of 51 to squeeze through this pipe.")
            return
        }
        val route = EdgevilleDungeonShortcutRules.pipe(source, player.coords)
        if (route == null || !safeDestination(route.destination)) {
            mes("You cannot squeeze through the pipe from here.")
            return
        }
        playerWalkWithMinDelay(route.approach)
        if (player.coords != route.approach) {
            mes("You cannot reach that side of the pipe.")
            return
        }
        faceSquare(route.destination)
        anim(749)
        exactMove(route.approach, route.destination, 0, 120, route.direction, TeleportType.Exempt)
        delay(4)
    }

    private fun replaceTrapdoor(opening: Boolean): Boolean {
        val from = if (opening) TRAPDOOR_CLOSED else TRAPDOOR_OPENED
        val into = if (opening) TRAPDOOR_OPENED else TRAPDOOR_CLOSED
        val current =
            locRepo.findAll(from.coords).firstOrNull {
                it.id == from.objectId && it.shape == from.shape && it.angle == from.angle
            } ?: return false
        if (!locRepo.del(current, Int.MAX_VALUE)) return false
        if (!locRepo.add(into.toLocInfo(), Int.MAX_VALUE)) {
            locRepo.add(current, Int.MAX_VALUE)
            return false
        }
        return true
    }

    private fun safeDestination(coords: CoordGrid): Boolean =
        collision.isZoneValid(coords) && !collision.isWalkBlocked(coords)

    private fun validateOption(objectId: Int, option: Int, expected: String) {
        val type = requireNotNull(ServerCacheManager.getObject(objectId))
        require(type.actions.getOpOrNull(option - 1).equals(expected, ignoreCase = true)) {
            "Edgeville object $objectId option $option must be '$expected'."
        }
    }
}

internal data class EdgevilleTrapdoorPlacement(
    val objectId: Int,
    val coords: CoordGrid,
    val shape: LocShape,
    val angle: LocAngle,
)

internal val TRAPDOOR_CLOSED =
    EdgevilleTrapdoorPlacement(1579, CoordGrid(3097, 3468), LocShape.GroundDecor, LocAngle.East)
internal val TRAPDOOR_OPENED =
    EdgevilleTrapdoorPlacement(1581, CoordGrid(3097, 3468), LocShape.GroundDecor, LocAngle.East)

private fun EdgevilleTrapdoorPlacement.toLocInfo(): LocInfo =
    LocInfo(
        layer = LocLayerConstants.of(shape.id),
        coords = coords,
        entity = LocEntity(objectId, shape.id, angle.id),
    )
