package org.rsmod.content.skills.sailing

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.output.InteractionModes
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.baseSailingLvl
import org.rsmod.api.registry.worldentity.WorldEntityRegistry
import org.rsmod.api.registry.worldentity.WorldEntityRegistryResult
import org.rsmod.api.registry.worldentity.isSuccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.content.skills.sailing.ownership.SailingOwnershipState
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.loc.LocAngle
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@Singleton
class BoatManager
@Inject
constructor(
    private val regionRepo: RegionRepository,
    private val locRepo: LocRepository,
    private val worldEntityRegistry: WorldEntityRegistry,
    private val playerList: PlayerList,
    private val collision: CollisionFlagMap,
    private val boatDefinitions: SailingBoatDefinitionRegistry,
    private val runtimeBoats: BoatRuntimeRegistry,
    private val docks: SailingDockRegistry,
) {
    private val logger = InlineLogger()

    private val boats = HashMap<Int, Boat>()

    val all: Collection<Boat>
        get() = boats.values

    fun spawn(type: BoatType, level: Int, fineX: Int, fineZ: Int, angle: Int = 0): Boat? {
        val verifiedType = boatDefinitions[type].runtimeType
        val template =
            RegionTemplate.create {
                copyAllLevels(verifiedType.templateZoneX, verifiedType.templateZoneZ) {
                    zoneWidth = verifiedType.sizeZonesX
                    zoneLength = verifiedType.sizeZonesZ
                }
            }
        val region = regionRepo.add(template)
        if (region == null) {
            logger.error { "Could not allocate a deck region for boat type `${verifiedType.key}`." }
            return null
        }
        regionRepo.protect(region)
        val swZone = region.southWestZone
        val entity =
            WorldEntity(
                id = verifiedType.worldEntityType,
                sizeX = verifiedType.sizeZonesX,
                sizeZ = verifiedType.sizeZonesZ,
                southWestZoneX = swZone.x,
                southWestZoneZ = swZone.z,
                fineX = fineX,
                fineZ = fineZ,
                activeLevel = verifiedType.deckLevel,
                projectedLevel = level,
                angle = angle,
            )
        entity.region = region
        val result = worldEntityRegistry.add(entity)
        if (!result.isSuccess()) {
            regionRepo.unprotect(region)
            logger.error { "Could not register boat world entity `${verifiedType.key}`: $result" }
            return null
        }
        val boat = Boat(verifiedType, entity, region)
        furnish(boat)
        boats[entity.slotId] = boat
        return boat
    }

    private fun furnish(boat: Boat) {
        val southWest = boat.region.southWest
        for (deckLoc in boat.type.deckLocs) {
            val loc =
                locRepo.add(
                    CoordGrid(southWest.x + deckLoc.dx, southWest.z + deckLoc.dz, deckLoc.level),
                    deckLoc.loc,
                    Int.MAX_VALUE,
                    LocAngle.West,
                    deckLoc.shape,
                )
            boat.deckLocs[deckLoc.loc] = loc
        }
    }

    fun spawnAtDock(type: BoatType, dockId: Int): Boat? {
        val dock = docks.byDockId(dockId)
        if (dock == null) {
            logger.error { "Unknown Sailing dock id `$dockId`." }
            return null
        }
        val geometry = dock.operational as? SailingDockOperationalGeometry.Verified
        if (geometry == null) {
            logger.error {
                "Sailing dock $dockId `${dock.definition.displayName}` has no capture-verified travel geometry."
            }
            return null
        }
        if (mooredAt(dockId) != null) {
            logger.error { "A Sailing boat is already moored at `${dock.definition.displayName}`." }
            return null
        }
        val boat =
            spawn(
                type = type,
                level = geometry.seaSpawn.level,
                fineX = WorldEntity.tileToFine(geometry.seaSpawn.x),
                fineZ = WorldEntity.tileToFine(geometry.seaSpawn.z),
                angle = geometry.boatRotation,
            )
        boat?.dockId = dockId
        return boat
    }

    fun spawnOwnedAtDock(owner: Player, ownershipSlot: Int, dockId: Int): OwnedBoatSpawnResult {
        val dock = docks.byDockId(dockId) ?: return OwnedBoatSpawnResult.InvalidDockId(dockId)
        val ownerId =
            SailingPlayerIdentity.idOrNull(owner) ?: return OwnedBoatSpawnResult.InvalidOwner
        val sailingLevel = owner.baseSailingLvl
        if (sailingLevel < dock.definition.levelRequired) {
            return OwnedBoatSpawnResult.DockLevelLocked(dockId, dock.definition.levelRequired)
        }
        if (dock.operational !is SailingDockOperationalGeometry.Verified) {
            return OwnedBoatSpawnResult.DockGeometryUnavailable(dockId)
        }
        val unlockedSlots = SailingBoatSlotPolicy.maxOwnedBoats(sailingLevel)
        if (ownershipSlot !in 0 until unlockedSlots) {
            return OwnedBoatSpawnResult.OwnershipSlotLocked(ownershipSlot, unlockedSlots)
        }
        val ownedBoat =
            SailingOwnershipState.snapshot(owner).boats.singleOrNull { it.slot == ownershipSlot }
                ?: return OwnedBoatSpawnResult.MissingOwnershipSlot(ownershipSlot)
        val definition =
            boatDefinitions.byCacheRow(ownedBoat.boatTypeKey)
                ?: return OwnedBoatSpawnResult.InvalidOwnershipType(ownedBoat.boatTypeKey)
        if (sailingLevel < definition.levelRequired) {
            return OwnedBoatSpawnResult.BoatTypeLevelLocked(
                definition.runtimeType,
                definition.levelRequired,
            )
        }
        val boat =
            spawnAtDock(definition.runtimeType, dockId)
                ?: return OwnedBoatSpawnResult.WorldSpawnFailed
        return deployOwnedBoat(SailingBoatIds.owned(ownerId, ownershipSlot), ownerId, boat)
    }

    internal fun spawnOwned(
        boatId: SailingBoatId,
        ownerId: Long,
        type: BoatType,
        level: Int,
        fineX: Int,
        fineZ: Int,
        angle: Int = 0,
    ): OwnedBoatSpawnResult {
        val boat =
            spawn(type = type, level = level, fineX = fineX, fineZ = fineZ, angle = angle)
                ?: return OwnedBoatSpawnResult.WorldSpawnFailed
        return deployOwnedBoat(boatId, ownerId, boat)
    }

    private fun deployOwnedBoat(
        boatId: SailingBoatId,
        ownerId: Long,
        boat: Boat,
    ): OwnedBoatSpawnResult {
        val type = boat.type
        val definition = boatDefinitions[type]
        return when (
            val deploy =
                runtimeBoats.deploy(
                    boatId = boatId,
                    ownerId = ownerId,
                    entitySlot = boat.entity.slotId,
                    playerCapacity = definition.playerCapacity,
                )
        ) {
            is BoatDeployResult.Success -> OwnedBoatSpawnResult.Success(boat, deploy.lease)
            else -> {
                val rollback = despawn(boat)
                check(rollback.isSuccess()) {
                    "Failed to roll back rejected Sailing boat deployment: $rollback"
                }
                OwnedBoatSpawnResult.LeaseRejected(deploy)
            }
        }
    }

    fun mooredAt(dockId: Int): Boat? = boats.values.firstOrNull { it.dockId == dockId }

    fun despawn(boat: Boat): WorldEntityRegistryResult.Delete {
        val slot = boat.entity.slotId
        val result = worldEntityRegistry.del(boat.entity)
        return completeBoatDespawn(result) {
            evacuate(boat)
            boat.entity.region = null
            regionRepo.unprotect(boat.region)
            if (slot != WorldEntity.INVALID_SLOT) {
                boats.remove(slot)
            }
            val lease = runtimeBoats.boatForEntitySlot(slot)
            if (lease != null) {
                val runtimeResult = runtimeBoats.despawn(lease.boatId)
                check(runtimeResult is BoatRuntimeDespawnResult.Success) {
                    "Failed to release Sailing runtime lease for entity slot $slot."
                }
            }
        }
    }

    fun boatAt(coords: CoordGrid): Boat? = boats.values.firstOrNull { it.entity.contains(coords) }

    fun boatOf(player: Player): Boat? = boatAt(player.coords)

    fun boatForEntitySlot(entitySlot: Int): Boat? = boats[entitySlot]

    fun releaseHelm(player: Player, updateClient: Boolean = true, stopBoat: Boolean = true) {
        val boat = boats.values.firstOrNull { it.helmsman === player }
        if (boat != null && boat.relinquishHelm(player, stopBoat) && updateClient) {
            InteractionModes.resetInteractionMode(player, boat.entity.slotId)
            InteractionModes.setInteractionMode(
                player,
                InteractionModes.WORLD_DEFAULT,
                InteractionModes.TILE_MODE_WALK,
                InteractionModes.ENTITY_MODE_ALL,
            )
        }
        player.helmLockedIn = 0
        SailingPlayerIdentity.idOrNull(player)?.let(runtimeBoats::releaseHelm)
    }

    fun releaseSeparatedHelm(player: Player) {
        val boat = boats.values.firstOrNull { it.helmsman === player } ?: return
        if (!boat.entity.contains(player.coords)) {
            releaseHelm(player)
        }
    }

    private fun evacuate(boat: Boat) {
        for (player in playerList) {
            if (!boat.entity.contains(player.coords)) {
                continue
            }
            releaseHelm(player)
            PathingEntityCommon.telejump(player, collision, player.lastKnownNormalCoord)
            player.aboardPlayerBoat = 0
            player.mes("You are returned to shore.")
        }
    }
}

internal inline fun completeBoatDespawn(
    result: WorldEntityRegistryResult.Delete,
    cleanup: () -> Unit,
): WorldEntityRegistryResult.Delete {
    if (result.isSuccess()) {
        cleanup()
    }
    return result
}

sealed interface OwnedBoatSpawnResult {
    data object InvalidOwner : OwnedBoatSpawnResult

    data class InvalidDockId(val dockId: Int) : OwnedBoatSpawnResult

    data class DockLevelLocked(val dockId: Int, val levelRequired: Int) : OwnedBoatSpawnResult

    data class DockGeometryUnavailable(val dockId: Int) : OwnedBoatSpawnResult

    data class Success(val boat: Boat, val lease: BoatRuntimeLeaseSnapshot) : OwnedBoatSpawnResult

    data object WorldSpawnFailed : OwnedBoatSpawnResult

    data class LeaseRejected(val reason: BoatDeployResult) : OwnedBoatSpawnResult

    data class OwnershipSlotLocked(val requestedSlot: Int, val unlockedSlots: Int) :
        OwnedBoatSpawnResult

    data class MissingOwnershipSlot(val ownershipSlot: Int) : OwnedBoatSpawnResult

    data class InvalidOwnershipType(val cacheRow: String) : OwnedBoatSpawnResult

    data class BoatTypeLevelLocked(val type: BoatType, val levelRequired: Int) :
        OwnedBoatSpawnResult
}
