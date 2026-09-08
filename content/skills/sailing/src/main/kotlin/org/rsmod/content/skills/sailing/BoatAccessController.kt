package org.rsmod.content.skills.sailing

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

enum class BoatMembershipReconcileResult {
    ABOARD,
    RELEASED,
    NOT_ABOARD,
    INVALID_PLAYER,
}

/**
 * Bridges stable character identity and runtime boat leases to the temporary client Sailing vars.
 * Friend/passenger access remains disabled until its exact policy and UI operations are captured.
 */
@Singleton
class BoatAccessController @Inject constructor(private val runtimeBoats: BoatRuntimeRegistry) {
    fun boardOwner(player: Player, boat: Boat): BoatBoardResult {
        val playerId =
            SailingPlayerIdentity.idOrNull(player) ?: return BoatBoardResult.InvalidPlayer
        val lease =
            runtimeBoats.boatForEntitySlot(boat.entity.slotId) ?: return BoatBoardResult.MissingBoat
        val result = runtimeBoats.boardOwner(lease.boatId, playerId)
        if (result is BoatBoardResult.Success || result is BoatBoardResult.AlreadyAboard) {
            player.aboardPlayerBoat = 1
        }
        return result
    }

    fun leave(player: Player): BoatLeaveResult {
        val playerId = SailingPlayerIdentity.idOrNull(player)
        player.aboardPlayerBoat = 0
        return if (playerId == null) BoatLeaveResult.NotAboard else runtimeBoats.leave(playerId)
    }

    fun claimHelm(player: Player, boat: Boat): BoatHelmClaimResult {
        val playerId =
            SailingPlayerIdentity.idOrNull(player) ?: return BoatHelmClaimResult.InvalidPlayer
        return runtimeBoats.claimHelm(boat.entity.slotId, playerId)
    }

    fun releaseHelm(player: Player): BoatHelmReleaseResult =
        SailingPlayerIdentity.idOrNull(player)?.let(runtimeBoats::releaseHelm)
            ?: BoatHelmReleaseResult.InvalidPlayer

    fun canHelm(player: Player, boat: Boat): Boolean {
        val playerId = SailingPlayerIdentity.idOrNull(player) ?: return false
        return runtimeBoats.canHelm(boat.entity.slotId, playerId)
    }

    fun beforeTeleport(
        player: Player,
        destination: CoordGrid,
        boats: BoatManager,
    ): BoatMembershipReconcileResult = reconcileAt(player, destination, boats)

    fun reconcile(player: Player, boats: BoatManager): BoatMembershipReconcileResult {
        return reconcileAt(player, player.coords, boats)
    }

    private fun reconcileAt(
        player: Player,
        destination: CoordGrid,
        boats: BoatManager,
    ): BoatMembershipReconcileResult {
        val playerId = SailingPlayerIdentity.idOrNull(player)
        if (playerId == null) {
            player.aboardPlayerBoat = 0
            return BoatMembershipReconcileResult.INVALID_PLAYER
        }
        val lease = runtimeBoats.boatForPlayer(playerId)
        if (lease == null) {
            player.aboardPlayerBoat = 0
            return BoatMembershipReconcileResult.NOT_ABOARD
        }
        val boat = boats.boatForEntitySlot(lease.entitySlot)
        if (boat == null || !boat.entity.contains(destination)) {
            boats.releaseHelm(player)
            check(runtimeBoats.leave(playerId) is BoatLeaveResult.Success)
            player.aboardPlayerBoat = 0
            return BoatMembershipReconcileResult.RELEASED
        }
        player.aboardPlayerBoat = 1
        return BoatMembershipReconcileResult.ABOARD
    }
}
