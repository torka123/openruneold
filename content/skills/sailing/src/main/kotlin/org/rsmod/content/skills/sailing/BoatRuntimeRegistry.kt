package org.rsmod.content.skills.sailing

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.content.skills.sailing.ownership.SailingOwnershipValidator

@JvmInline
value class SailingBoatId(val value: Long) {
    init {
        require(value > 0) { "A durable Sailing boat id must be positive." }
    }
}

/** Single checked encoder for durable `(character_id, ownership_slot)` runtime identities. */
internal object SailingBoatIds {
    private const val SLOT_BITS = 3

    fun owned(characterId: Long, ownershipSlot: Int): SailingBoatId {
        require(characterId > 0) { "A Sailing character id must be positive." }
        require(ownershipSlot in 0 until SailingOwnershipValidator.MAX_OWNED_BOATS) {
            "Invalid Sailing ownership slot $ownershipSlot."
        }
        return SailingBoatId((characterId shl SLOT_BITS) or (ownershipSlot + 1).toLong())
    }

    fun debug(characterId: Long): SailingBoatId {
        require(characterId > 0) { "A Sailing character id must be positive." }
        return SailingBoatId(Long.MAX_VALUE - characterId)
    }
}

data class BoatRuntimeLeaseSnapshot(
    val boatId: SailingBoatId,
    val ownerId: Long,
    val entitySlot: Int,
    val playerCapacity: Int,
    val occupantIds: Set<Long>,
    val helmsmanId: Long?,
)

sealed interface BoatDeployResult {
    data class Success(val lease: BoatRuntimeLeaseSnapshot) : BoatDeployResult

    data object InvalidLease : BoatDeployResult

    data class BoatAlreadyLive(val lease: BoatRuntimeLeaseSnapshot) : BoatDeployResult

    data class OwnerAlreadyHasBoat(val lease: BoatRuntimeLeaseSnapshot) : BoatDeployResult

    data class EntitySlotInUse(val lease: BoatRuntimeLeaseSnapshot) : BoatDeployResult
}

sealed interface BoatBoardResult {
    data class Success(val lease: BoatRuntimeLeaseSnapshot) : BoatBoardResult

    data class AlreadyAboard(val lease: BoatRuntimeLeaseSnapshot) : BoatBoardResult

    data object InvalidPlayer : BoatBoardResult

    data object MissingBoat : BoatBoardResult

    data class NotOwner(val lease: BoatRuntimeLeaseSnapshot) : BoatBoardResult

    data class AboardOtherBoat(val boatId: SailingBoatId) : BoatBoardResult

    data class Full(val lease: BoatRuntimeLeaseSnapshot) : BoatBoardResult
}

sealed interface BoatLeaveResult {
    data class Success(val boatId: SailingBoatId) : BoatLeaveResult

    data object NotAboard : BoatLeaveResult
}

sealed interface BoatHelmClaimResult {
    data class Success(val lease: BoatRuntimeLeaseSnapshot) : BoatHelmClaimResult

    data class AlreadyHelmsman(val lease: BoatRuntimeLeaseSnapshot) : BoatHelmClaimResult

    data class Busy(val helmsmanId: Long) : BoatHelmClaimResult

    data object InvalidPlayer : BoatHelmClaimResult

    data object MissingBoat : BoatHelmClaimResult

    data object NotAboard : BoatHelmClaimResult

    data object NotOwner : BoatHelmClaimResult
}

sealed interface BoatHelmReleaseResult {
    data class Success(val boatId: SailingBoatId) : BoatHelmReleaseResult

    data object InvalidPlayer : BoatHelmReleaseResult

    data object NotHelmsman : BoatHelmReleaseResult
}

sealed interface BoatRuntimeDespawnResult {
    data class Success(val lease: BoatRuntimeLeaseSnapshot) : BoatRuntimeDespawnResult

    data object MissingBoat : BoatRuntimeDespawnResult
}

/** Runtime-only indexes. Durable ownership remains database/account state, never an entity slot. */
@Singleton
class BoatRuntimeRegistry @Inject constructor() {
    private class MutableLease(
        val boatId: SailingBoatId,
        val ownerId: Long,
        val entitySlot: Int,
        val playerCapacity: Int,
    ) {
        val occupants = LinkedHashSet<Long>()
        var helmsmanId: Long? = null

        fun snapshot(): BoatRuntimeLeaseSnapshot =
            BoatRuntimeLeaseSnapshot(
                boatId = boatId,
                ownerId = ownerId,
                entitySlot = entitySlot,
                playerCapacity = playerCapacity,
                occupantIds = occupants.toSet(),
                helmsmanId = helmsmanId,
            )
    }

    private val byBoatId = HashMap<SailingBoatId, MutableLease>()
    private val boatIdByOwner = HashMap<Long, SailingBoatId>()
    private val boatIdByEntitySlot = HashMap<Int, SailingBoatId>()
    private val boatIdByOccupant = HashMap<Long, SailingBoatId>()

    fun deploy(
        boatId: SailingBoatId,
        ownerId: Long,
        entitySlot: Int,
        playerCapacity: Int,
    ): BoatDeployResult {
        if (ownerId <= 0 || entitySlot <= 0 || playerCapacity <= 0) {
            return BoatDeployResult.InvalidLease
        }
        byBoatId[boatId]?.let {
            return BoatDeployResult.BoatAlreadyLive(it.snapshot())
        }
        boatIdByOwner[ownerId]?.let { id ->
            return BoatDeployResult.OwnerAlreadyHasBoat(checkNotNull(byBoatId[id]).snapshot())
        }
        boatIdByEntitySlot[entitySlot]?.let { id ->
            return BoatDeployResult.EntitySlotInUse(checkNotNull(byBoatId[id]).snapshot())
        }
        val lease = MutableLease(boatId, ownerId, entitySlot, playerCapacity)
        byBoatId[boatId] = lease
        boatIdByOwner[ownerId] = boatId
        boatIdByEntitySlot[entitySlot] = boatId
        return BoatDeployResult.Success(lease.snapshot())
    }

    /** Default gangplank access is owner-only until friend/passenger policy is captured. */
    fun boardOwner(boatId: SailingBoatId, playerId: Long): BoatBoardResult {
        if (playerId <= 0) {
            return BoatBoardResult.InvalidPlayer
        }
        val lease = byBoatId[boatId] ?: return BoatBoardResult.MissingBoat
        if (lease.ownerId != playerId) {
            return BoatBoardResult.NotOwner(lease.snapshot())
        }
        return board(boatId, playerId)
    }

    fun board(boatId: SailingBoatId, playerId: Long): BoatBoardResult {
        if (playerId <= 0) {
            return BoatBoardResult.InvalidPlayer
        }
        val lease = byBoatId[boatId] ?: return BoatBoardResult.MissingBoat
        val current = boatIdByOccupant[playerId]
        if (current == boatId) {
            return BoatBoardResult.AlreadyAboard(lease.snapshot())
        }
        if (current != null) {
            return BoatBoardResult.AboardOtherBoat(current)
        }
        if (lease.occupants.size >= lease.playerCapacity) {
            return BoatBoardResult.Full(lease.snapshot())
        }
        check(lease.occupants.add(playerId))
        boatIdByOccupant[playerId] = boatId
        return BoatBoardResult.Success(lease.snapshot())
    }

    fun leave(playerId: Long): BoatLeaveResult {
        val boatId = boatIdByOccupant.remove(playerId) ?: return BoatLeaveResult.NotAboard
        val lease = checkNotNull(byBoatId[boatId])
        check(lease.occupants.remove(playerId))
        if (lease.helmsmanId == playerId) {
            lease.helmsmanId = null
        }
        return BoatLeaveResult.Success(boatId)
    }

    fun claimHelm(entitySlot: Int, playerId: Long): BoatHelmClaimResult {
        if (playerId <= 0) {
            return BoatHelmClaimResult.InvalidPlayer
        }
        val boatId = boatIdByEntitySlot[entitySlot] ?: return BoatHelmClaimResult.MissingBoat
        val lease = checkNotNull(byBoatId[boatId])
        if (boatIdByOccupant[playerId] != boatId) {
            return BoatHelmClaimResult.NotAboard
        }
        if (lease.ownerId != playerId) {
            return BoatHelmClaimResult.NotOwner
        }
        val current = lease.helmsmanId
        if (current == playerId) {
            return BoatHelmClaimResult.AlreadyHelmsman(lease.snapshot())
        }
        if (current != null) {
            return BoatHelmClaimResult.Busy(current)
        }
        lease.helmsmanId = playerId
        return BoatHelmClaimResult.Success(lease.snapshot())
    }

    fun releaseHelm(playerId: Long): BoatHelmReleaseResult {
        if (playerId <= 0) {
            return BoatHelmReleaseResult.InvalidPlayer
        }
        val boatId = boatIdByOccupant[playerId] ?: return BoatHelmReleaseResult.NotHelmsman
        val lease = checkNotNull(byBoatId[boatId])
        if (lease.helmsmanId != playerId) {
            return BoatHelmReleaseResult.NotHelmsman
        }
        lease.helmsmanId = null
        return BoatHelmReleaseResult.Success(boatId)
    }

    fun despawn(boatId: SailingBoatId): BoatRuntimeDespawnResult {
        val lease = byBoatId.remove(boatId) ?: return BoatRuntimeDespawnResult.MissingBoat
        val snapshot = lease.snapshot()
        check(boatIdByOwner.remove(lease.ownerId) == boatId)
        check(boatIdByEntitySlot.remove(lease.entitySlot) == boatId)
        for (playerId in lease.occupants) {
            check(boatIdByOccupant.remove(playerId) == boatId)
        }
        lease.occupants.clear()
        return BoatRuntimeDespawnResult.Success(snapshot)
    }

    fun boat(boatId: SailingBoatId): BoatRuntimeLeaseSnapshot? = byBoatId[boatId]?.snapshot()

    fun boatForOwner(ownerId: Long): BoatRuntimeLeaseSnapshot? =
        boatIdByOwner[ownerId]?.let(byBoatId::get)?.snapshot()

    fun boatForEntitySlot(entitySlot: Int): BoatRuntimeLeaseSnapshot? =
        boatIdByEntitySlot[entitySlot]?.let(byBoatId::get)?.snapshot()

    fun boatForPlayer(playerId: Long): BoatRuntimeLeaseSnapshot? =
        boatIdByOccupant[playerId]?.let(byBoatId::get)?.snapshot()

    fun canHelm(entitySlot: Int, playerId: Long): Boolean {
        if (playerId <= 0) {
            return false
        }
        val boatId = boatIdByEntitySlot[entitySlot] ?: return false
        val lease = checkNotNull(byBoatId[boatId])
        return lease.helmsmanId == playerId
    }

    fun count(): Int = byBoatId.size
}

object SailingBoatSlotPolicy {
    fun maxOwnedBoats(baseSailingLevel: Int): Int =
        when {
            baseSailingLevel >= 91 -> 5
            baseSailingLevel >= 78 -> 4
            baseSailingLevel >= 50 -> 3
            baseSailingLevel >= 15 -> 2
            baseSailingLevel >= 1 -> 1
            else -> 0
        }
}
