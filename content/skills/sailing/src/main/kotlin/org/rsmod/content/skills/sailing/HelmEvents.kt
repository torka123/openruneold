package org.rsmod.content.skills.sailing

import jakarta.inject.Inject
import org.rsmod.api.player.events.SailingEvent
import org.rsmod.api.player.output.InteractionModes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class HelmEvents
@Inject
constructor(
    private val boats: BoatManager,
    private val access: BoatAccessController,
    private val world: WorldRepository,
    private val clientState: SailingClientState,
) : PluginScript() {
    override fun ScriptContext.startup() {
        if (!SailingReleaseGate.playerGameplayEnabled) {
            return
        }
        for (loc in HELM_IDLE_LOCS) {
            onOpLoc1(loc) { navigate(it.loc) }
        }
        for (loc in HELM_IN_USE_LOCS) {
            onOpLoc1(loc) { stopNavigating(it.loc) }
        }
        onEvent<SailingEvent.SetHeading> { steer(player, heading) }
    }

    private suspend fun ProtectedAccess.navigate(helm: BoundLocInfo) {
        val boat = boats.boatOf(player) ?: return
        if (boat.helmsman != null || player.helmLockedIn != 0) {
            return
        }
        when (access.claimHelm(player, boat)) {
            is BoatHelmClaimResult.Success,
            is BoatHelmClaimResult.AlreadyHelmsman -> Unit
            is BoatHelmClaimResult.Busy,
            BoatHelmClaimResult.InvalidPlayer,
            BoatHelmClaimResult.MissingBoat,
            BoatHelmClaimResult.NotAboard,
            BoatHelmClaimResult.NotOwner -> return
        }
        boat.helmsman = player
        boat.moveMode = SailingMoveModes.HELM_IDLE
        player.helmLockedIn = LOCKEDIN_NAVIGATING
        clientState.helmLocked(player)
        InteractionModes.setInteractionMode(
            player,
            InteractionModes.WORLD_DEFAULT,
            InteractionModes.TILE_MODE_HEADING,
            InteractionModes.ENTITY_MODE_ALL,
        )
        InteractionModes.setInteractionMode(
            player,
            boat.entity.slotId,
            InteractionModes.TILE_MODE_WALK,
            InteractionModes.ENTITY_MODE_ALL,
        )
        delay(1)
        if (boat.helmsman !== player || player.helmLockedIn != LOCKEDIN_NAVIGATING) {
            return
        }
        clientState.helmEntered(player)
        anim(boat.type.helmPlayerGrab)
        world.locAnim(helm, boat.type.helmLocGrab)
        soundSynth(boat.type.helmSynthGrab)
        delay(1)
        if (boat.helmsman !== player || player.helmLockedIn != LOCKEDIN_NAVIGATING) {
            return
        }
        boat.moveMode = SailingMoveModes.FULL
        boat.targetSpeed = boat.type.baseSpeed
        clientState.fullSail(player)
        val sail = boat.primarySailLoc()
        if (sail != null) {
            world.locAnim(sail, boat.type.sailDownToFull)
            delay(1)
            if (boat.helmsman === player && player.helmLockedIn == LOCKEDIN_NAVIGATING) {
                world.locAnim(sail, boat.type.sailFull)
            }
        }
    }

    private suspend fun ProtectedAccess.stopNavigating(helm: BoundLocInfo) {
        val boat = boats.boatOf(player) ?: return
        if (boat.helmsman != player) {
            if (boat.helmsman == null && player.helmLockedIn != 0) {
                access.releaseHelm(player)
                player.helmLockedIn = 0
                clientState.helmReleased(player, boat)
            }
            return
        }
        val lowerSails = player.keepSailingBoatWhenLeavingHelm == 0
        val sail = boat.primarySailLoc()
        resetAnim()
        world.locAnim(helm, boat.type.helmLocRelease)
        if (lowerSails && sail != null) {
            world.locAnim(sail, boat.type.sailFullToDown)
        }
        soundSynth(boat.type.helmSynthRelease)
        delay(1)
        boats.releaseHelm(player, stopBoat = lowerSails)
        clientState.helmReleased(player, boat)
        if (lowerSails && sail != null) {
            delay(1)
            world.locAnim(sail, boat.type.sailDown)
        }
    }

    private fun steer(player: Player, heading: Int) {
        val boat = boats.boatOf(player) ?: return
        if (!access.canHelm(player, boat)) {
            return
        }
        if (boat.helmsman != player || player.helmLockedIn != LOCKEDIN_NAVIGATING) {
            return
        }
        if (SailingHelmControl.applyHeading(boat, heading, player.startBoatWhenSettingHeading)) {
            clientState.syncMotion(player, boat)
        }
    }

    private companion object {
        private const val LOCKEDIN_NAVIGATING = 3

        private val HELM_IDLE_LOCS =
            listOf(
                "loc.sailing_boat_steering_kandarin_1x3_wood_idle",
                "loc.sailing_boat_steering_kandarin_1x3_oak_idle",
                "loc.sailing_boat_steering_kandarin_1x3_teak_idle",
                "loc.sailing_boat_steering_kandarin_1x3_mahogany_idle",
                "loc.sailing_boat_steering_kandarin_1x3_camphor_idle",
                "loc.sailing_boat_steering_kandarin_1x3_ironwood_idle",
                "loc.sailing_boat_steering_kandarin_1x3_rosewood_idle",
                "loc.sailing_boat_steering_kandarin_2x5_wood_idle",
                "loc.sailing_boat_steering_kandarin_2x5_oak_idle",
                "loc.sailing_boat_steering_kandarin_2x5_teak_idle",
                "loc.sailing_boat_steering_kandarin_2x5_mahogany_idle",
                "loc.sailing_boat_steering_kandarin_2x5_camphor_idle",
                "loc.sailing_boat_steering_kandarin_2x5_ironwood_idle",
                "loc.sailing_boat_steering_kandarin_2x5_rosewood_idle",
                "loc.sailing_boat_steering_kandarin_3x8_wood_idle",
                "loc.sailing_boat_steering_kandarin_3x8_oak_idle",
                "loc.sailing_boat_steering_kandarin_3x8_teak_idle",
                "loc.sailing_boat_steering_kandarin_3x8_mahogany_idle",
                "loc.sailing_boat_steering_kandarin_3x8_camphor_idle",
                "loc.sailing_boat_steering_kandarin_3x8_ironwood_idle",
                "loc.sailing_boat_steering_kandarin_3x8_rosewood_idle",
            )

        private val HELM_IN_USE_LOCS =
            listOf(
                "loc.sailing_boat_steering_kandarin_1x3_wood_in_use",
                "loc.sailing_boat_steering_kandarin_1x3_oak_in_use",
                "loc.sailing_boat_steering_kandarin_1x3_teak_in_use",
                "loc.sailing_boat_steering_kandarin_1x3_mahogany_in_use",
                "loc.sailing_boat_steering_kandarin_1x3_camphor_in_use",
                "loc.sailing_boat_steering_kandarin_1x3_ironwood_in_use",
                "loc.sailing_boat_steering_kandarin_1x3_rosewood_in_use",
                "loc.sailing_boat_steering_kandarin_2x5_wood_in_use",
                "loc.sailing_boat_steering_kandarin_2x5_oak_in_use",
                "loc.sailing_boat_steering_kandarin_2x5_teak_in_use",
                "loc.sailing_boat_steering_kandarin_2x5_mahogany_in_use",
                "loc.sailing_boat_steering_kandarin_2x5_camphor_in_use",
                "loc.sailing_boat_steering_kandarin_2x5_ironwood_in_use",
                "loc.sailing_boat_steering_kandarin_2x5_rosewood_in_use",
                "loc.sailing_boat_steering_kandarin_3x8_wood_in_use",
                "loc.sailing_boat_steering_kandarin_3x8_oak_in_use",
                "loc.sailing_boat_steering_kandarin_3x8_teak_in_use",
                "loc.sailing_boat_steering_kandarin_3x8_mahogany_in_use",
                "loc.sailing_boat_steering_kandarin_3x8_camphor_in_use",
                "loc.sailing_boat_steering_kandarin_3x8_ironwood_in_use",
                "loc.sailing_boat_steering_kandarin_3x8_rosewood_in_use",
            )
    }
}

internal object SailingHelmControl {
    fun applyHeading(boat: Boat, heading: Int, startPolicy: Int = START_FIRST_ONLY): Boolean {
        if (heading !in MIN_HEADING..MAX_HEADING) {
            return false
        }
        val start =
            when (startPolicy) {
                START_ALWAYS -> boat.moveMode == SailingMoveModes.STOPPED
                START_NEVER -> false
                else -> boat.moveMode == SailingMoveModes.HELM_IDLE
            }
        if (start) {
            boat.moveMode = SailingMoveModes.FULL
            boat.targetSpeed = boat.type.baseSpeed
        }
        boat.targetAngle = heading * HEADING_TO_ANGLE
        return true
    }

    internal const val START_FIRST_ONLY = 0
    internal const val START_ALWAYS = 1
    internal const val START_NEVER = 2

    private const val MIN_HEADING = 0
    private const val MAX_HEADING = 15
    private const val HEADING_TO_ANGLE = 128
}
