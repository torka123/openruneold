package org.rsmod.content.skills.sailing

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.registry.worldentity.isSuccess
import org.rsmod.api.script.onCommand
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.WorldEntity
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SailingDebugCommands
@Inject
constructor(
    private val boats: BoatManager,
    private val access: BoatAccessController,
    private val protectedAccess: ProtectedAccessLauncher,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onCommand("spawnboat") {
            desc = "Spawn a debug boat world entity at your coords"
            requiredRights = Rights.ADMINISTRATOR
            invalidArgs = "Use as ::spawnboat [raft|skiff|sloop]"
            cheat(::spawnBoat)
        }
        onCommand("moveboat") {
            desc = "Move the last debug boat"
            requiredRights = Rights.ADMINISTRATOR
            invalidArgs =
                "Use as ::moveboat dTilesX dTilesZ [angle] [jump] " +
                    "(ex: ::moveboat 2 0 or ::moveboat 5 5 512 1)"
            cheat(::moveBoat)
        }
        onCommand("setheading") {
            desc = "Set the last debug boat's target heading (0-2047)"
            requiredRights = Rights.ADMINISTRATOR
            invalidArgs = "Use as ::setheading angle (0=S 512=W 1024=N 1536=E)"
            cheat(::setHeading)
        }
        onCommand("setspeed") {
            desc = "Set the last debug boat's target speed in fine units per tick"
            requiredRights = Rights.ADMINISTRATOR
            invalidArgs = "Use as ::setspeed speed (ex: ::setspeed 192, 0 to stop)"
            cheat(::setSpeed)
        }
        onCommand("delboat") {
            desc = "Delete the last debug boat"
            requiredRights = Rights.ADMINISTRATOR
            cheat(::delBoat)
        }
        onCommand("boardboat") {
            desc = "Teleport aboard the last debug boat"
            requiredRights = Rights.ADMINISTRATOR
            cheat(::boardBoat)
        }
        onCommand("exitboat") {
            desc = "Teleport off the last debug boat"
            requiredRights = Rights.ADMINISTRATOR
            cheat(::exitBoat)
        }
    }

    private fun spawnBoat(cheat: Cheat) =
        with(cheat) {
            if (player.attr[LAST_SPAWNED] != null) {
                player.mes("Delete your current debug boat first.")
                return@with
            }
            val key = args.getOrNull(0) ?: BoatTypes.RAFT.key
            val type = BoatTypes.byKey(key)
            if (type == null) {
                player.mes("Unknown boat type: $key (use raft, skiff, or sloop)")
                return@with
            }
            val ownerId = SailingPlayerIdentity.idOrNull(player)
            if (ownerId == null) {
                player.mes("Your character identity is not ready; no boat was spawned.")
                return@with
            }
            when (
                val result =
                    boats.spawnOwned(
                        boatId = SailingPlayerIdentity.debugBoatId(ownerId),
                        ownerId = ownerId,
                        type = type,
                        level = player.level,
                        fineX = WorldEntity.tileToFine(player.x + SPAWN_OFFSET_TILES),
                        fineZ = WorldEntity.tileToFine(player.z),
                    )
            ) {
                is OwnedBoatSpawnResult.Success -> {
                    val boat = result.boat
                    player.attr[LAST_SPAWNED] = boat
                    player.mes(
                        "Spawned ${type.key} world entity: " +
                            "slot=${boat.entity.slotId}, coords=${boat.entity.coords}"
                    )
                }
                is OwnedBoatSpawnResult.LeaseRejected ->
                    player.mes("Could not lease ${type.key}: ${result.reason}")
                OwnedBoatSpawnResult.WorldSpawnFailed ->
                    player.mes("Could not spawn ${type.key} world entity.")
                OwnedBoatSpawnResult.InvalidOwner,
                is OwnedBoatSpawnResult.InvalidDockId,
                is OwnedBoatSpawnResult.DockLevelLocked,
                is OwnedBoatSpawnResult.DockGeometryUnavailable,
                is OwnedBoatSpawnResult.OwnershipSlotLocked,
                is OwnedBoatSpawnResult.MissingOwnershipSlot,
                is OwnedBoatSpawnResult.InvalidOwnershipType,
                is OwnedBoatSpawnResult.BoatTypeLevelLocked ->
                    player.mes("Could not authorize the debug boat: $result")
            }
        }

    private fun moveBoat(cheat: Cheat) =
        with(cheat) {
            val boat = player.attr[LAST_SPAWNED]
            if (boat == null) {
                player.mes("No debug boat spawned.")
                return@with
            }
            val entity = boat.entity
            val dx = args[0].toInt() * WorldEntity.FINE_UNITS_PER_TILE
            val dz = args[1].toInt() * WorldEntity.FINE_UNITS_PER_TILE
            val jump = (args.getOrNull(3)?.toInt() ?: 0) != 0
            entity.updateCoord(
                level = entity.projectedLevel,
                fineX = entity.fineX + dx,
                fineZ = entity.fineZ + dz,
                teleport = jump,
            )
            args.getOrNull(2)?.toInt()?.let(entity::updateAngle)
            boat.targetAngle = entity.angle
            player.mes("Moved boat to ${entity.coords} (angle=${entity.angle}).")
        }

    private fun setHeading(cheat: Cheat) =
        with(cheat) {
            val boat = player.attr[LAST_SPAWNED]
            if (boat == null) {
                player.mes("No debug boat spawned.")
                return@with
            }
            boat.targetAngle = args[0].toInt() and WorldEntity.MAX_ANGLE
            player.mes(
                "Target heading set to ${boat.targetAngle} " +
                    "(current angle=${boat.entity.angle})."
            )
        }

    private fun setSpeed(cheat: Cheat) =
        with(cheat) {
            val boat = player.attr[LAST_SPAWNED]
            if (boat == null) {
                player.mes("No debug boat spawned.")
                return@with
            }
            boat.targetSpeed = args[0].toInt().coerceIn(0, boat.type.speedCap)
            player.mes("Target speed set to ${boat.targetSpeed} (cap=${boat.type.speedCap}).")
        }

    private fun boardBoat(cheat: Cheat) =
        with(cheat) {
            val boat = player.attr[LAST_SPAWNED]
            if (boat == null) {
                player.mes("No debug boat spawned.")
                return@with
            }
            when (access.boardOwner(player, boat)) {
                is BoatBoardResult.Success,
                is BoatBoardResult.AlreadyAboard -> {
                    protectedAccess.launch(player) {
                        try {
                            telejump(boat.boardDest, TeleportType.Exempt)
                        } catch (t: Throwable) {
                            access.leave(player)
                            throw t
                        }
                        player.mes("You board your boat.")
                    }
                }
                is BoatBoardResult.NotOwner -> player.mes("That is not your boat.")
                is BoatBoardResult.Full -> player.mes("There is no room on that boat.")
                is BoatBoardResult.AboardOtherBoat ->
                    player.mes("You must leave your current boat first.")
                BoatBoardResult.InvalidPlayer,
                BoatBoardResult.MissingBoat -> player.mes("You cannot board that boat right now.")
            }
        }

    private fun exitBoat(cheat: Cheat) =
        with(cheat) {
            val dest = player.lastKnownNormalCoord
            protectedAccess.launch(player) {
                boats.releaseHelm(player)
                access.leave(player)
                telejump(dest, TeleportType.Exempt)
                player.mes("You disembark.")
            }
        }

    private fun delBoat(cheat: Cheat) =
        with(cheat) {
            val boat = player.attr[LAST_SPAWNED]
            if (boat == null) {
                player.mes("No debug boat spawned.")
                return@with
            }
            val result = boats.despawn(boat)
            if (result.isSuccess()) {
                player.attr.remove(LAST_SPAWNED)
            }
            player.mes("Deleted boat: $result")
        }

    private companion object {
        private const val SPAWN_OFFSET_TILES = 3
        private val LAST_SPAWNED = AttributeKey<Boat>(temp = true)
    }
}
