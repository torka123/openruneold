package org.rsmod.content.skills.sailing

import org.rsmod.game.entity.Player
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.region.Region
import org.rsmod.map.CoordGrid

class Boat(val type: BoatType, val entity: WorldEntity, val region: Region) {
    var dockId: Int? = null

    var targetAngle: Int = entity.angle
    var speed: Int = 0
    var targetSpeed: Int = 0
    var helmsman: Player? = null
    var moveMode: Int = SailingMoveModes.STOPPED

    internal val deckLocs: MutableMap<String, LocInfo> = HashMap()

    val boardDest: CoordGrid
        get() =
            CoordGrid(
                region.southWest.x + type.boardDestDx,
                region.southWest.z + type.boardDestDz,
                type.deckLevel,
            )

    internal fun depart() {
        dockId = null
    }

    internal fun relinquishHelm(player: Player, stopBoat: Boolean = true): Boolean {
        if (helmsman !== player) {
            return false
        }
        helmsman = null
        if (stopBoat) {
            moveMode = SailingMoveModes.STOPPED
            targetSpeed = 0
        }
        return true
    }

    internal fun primarySailLoc(): LocInfo? = deckLocs[type.primarySailLoc]

    override fun toString(): String = "Boat(type=${type.key}, entity=$entity)"
}
