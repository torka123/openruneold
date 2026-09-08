package org.rsmod.content.interfaces.logout.tab

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import java.util.logging.Logger
import org.rsmod.api.instances.InstanceLogoutTabPolicy
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.RaidInstanceType
import org.rsmod.api.instances.RaidLeaveResult
import org.rsmod.api.instances.StandaloneInstanceType
import org.rsmod.api.instances.events.InstanceLeaveRequestEvent
import org.rsmod.api.instances.events.InstancePlayerJoinUnboundEvent
import org.rsmod.api.instances.events.InstancePlayerLeaveUnboundEvent
import org.rsmod.api.instances.events.RaidLeaveRequestEvent
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class LogoutTabScript
@Inject
constructor(
    private val eventBus: EventBus,
    private val protectedAccess: ProtectedAccessLauncher,
    private val instances: InstanceManager,
) : PluginScript() {
    private val log = Logger.getLogger(LogoutTabScript::class.java.name)
    private val leaveRequests = mutableSetOf<PlayerUid>()

    override fun ScriptContext.startup() {
        onIfOverlayButton("component.logout:logout") { player.requestLogout() }

        val instanceLeaveComponent =
            requireNotNull(
                ServerCacheManager.getInterface(InstanceLogoutTabPolicy.INTERFACE_ID)
                    ?.components
                    ?.get(InstanceLogoutTabPolicy.LEAVE_COMPONENT)
            ) {
                "Missing instance logout component ${InstanceLogoutTabPolicy.INTERFACE_ID}:" +
                    InstanceLogoutTabPolicy.LEAVE_COMPONENT
            }
        require(instanceLeaveComponent.op.any { it.equals("Leave Instance", ignoreCase = true) }) {
            "Instance logout component ${InstanceLogoutTabPolicy.INTERFACE_ID}:" +
                "${InstanceLogoutTabPolicy.LEAVE_COMPONENT} has no Leave Instance operation"
        }
        onIfOverlayButton(instanceLeaveComponent) { player.requestInstanceLeave() }

        onEvent<InstancePlayerJoinUnboundEvent> {
            if (InstanceLogoutTabPolicy.interfaceFor(key) != null) player.openInstanceLogoutTab()
        }
        onEvent<InstancePlayerLeaveUnboundEvent> {
            if (InstanceLogoutTabPolicy.interfaceFor(key) != null) player.restoreLogoutTab()
            leaveRequests.remove(player.uid)
        }
    }

    private fun Player.requestLogout() {
        ifClose(eventBus)
        protectedAccess.launch(this) {
            if (isBusy2 && mapClock > preventLogoutUntil) {
                playerWalk(coords)
            }
            logOut()
        }
    }

    private fun Player.requestInstanceLeave() {
        val session = instances.sessionForPlayer(this)
        val raidType = session?.key?.let(RaidInstanceType::fromKey)
        val standaloneType = session?.key?.let(StandaloneInstanceType::fromKey)
        if (session == null || (raidType == null && standaloneType == null)) {
            restoreLogoutTab()
            mes("This instance could not be identified, so you have not been moved.")
            log.warning(
                "Instance logout rejected: player=$uid session=${session?.id} " +
                    "key=${session?.key} coords=$coords"
            )
            return
        }
        ifClose(eventBus)
        if (!leaveRequests.add(uid)) {
            mes("Your instance leave request is already being processed.")
            return
        }
        try {
            val result =
                if (raidType != null) {
                    val request = RaidLeaveRequestEvent(this, session.key, session.id)
                    if (!eventBus.publish(request)) RaidLeaveResult.Unhandled else request.result
                } else {
                    val request = InstanceLeaveRequestEvent(this, session.key, session.id)
                    if (!eventBus.publish(request)) RaidLeaveResult.Unhandled else request.result
                }
            if (result == RaidLeaveResult.Unhandled) {
                mes("The instance leave handler is unavailable. You have not been moved.")
                log.warning(
                    "No instance leave handler: player=$uid instance=" +
                        "${raidType?.displayName ?: standaloneType?.displayName} " +
                        "session=${session.id} coords=$coords"
                )
                return
            }
            when (result) {
                RaidLeaveResult.Left -> Unit
                RaidLeaveResult.InventoryFull,
                RaidLeaveResult.Busy,
                RaidLeaveResult.RewardUnavailable -> Unit
                RaidLeaveResult.Failed -> {
                    mes("The instance could not be left safely. You have not been moved.")
                    log.warning(
                        "Instance leave failed: player=$uid instance=" +
                            "${raidType?.displayName ?: standaloneType?.displayName} " +
                            "session=${session.id} coords=$coords"
                    )
                }
                RaidLeaveResult.Unhandled -> Unit
            }
        } finally {
            leaveRequests.remove(uid)
        }
    }

    private fun Player.openInstanceLogoutTab() {
        ifOpenOverlay(
            InstanceLogoutTabPolicy.INTERFACE_ID,
            "component.toplevel_osrs_stretch:side10",
            eventBus,
        )
    }

    private fun Player.restoreLogoutTab() {
        ifOpenOverlay("interface.logout", "component.toplevel_osrs_stretch:side10", eventBus)
    }
}
