package org.rsmod.content.bosses.alchemicalhydra

import dev.openrune.ServerCacheManager
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.instances.*
import org.rsmod.api.instances.events.InstanceLeaveRequestEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.script.*
import org.rsmod.plugin.scripts.ScriptContext

internal class AlchemicalHydraWorld
@Inject
constructor(
    registry: BossInstanceRegistry,
    private val encounters: AlchemicalHydraEncounterManager,
    private val protectedAccess: ProtectedAccessLauncher,
) :
    InstanceScript(
        registry,
        HYDRA_INSTANCE_KEY,
        hydraSpec(requireNotNull(ServerCacheManager.getNpc(AlchemicalHydraAssets.GREEN))),
    ) {
    override fun ScriptContext.configure() {
        encounters.validateAssets()
        onInstancePlayerJoin {
            manager.sessionForId(instanceId)?.let { encounters.onJoin(player, it) }
        }
        onInstancePlayerLeave {
            manager.sessionForId(instanceId)?.let { encounters.onLeave(player, it) }
        }
        onEvent<InstanceLeaveRequestEvent>(instanceEventId(HYDRA_INSTANCE_KEY)) {
            if (manager.sessionForPlayer(player)?.id == instanceId) {
                result =
                    if (protectedAccess.launch(player) { leaveHydra() }) RaidLeaveResult.Left
                    else RaidLeaveResult.Busy
            }
        }
        AlchemicalHydraAssets.doors.forEach { id ->
            val door = requireNotNull(ServerCacheManager.getObject(id)) { "Missing Hydra door $id" }
            onOpLoc1(door) {
                arriveDelay()
                encounters.crossDoor(this)
            }
            onOpLoc2(door) {
                arriveDelay()
                encounters.crossDoor(this)
            }
        }
        onCommand("hydra") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Create and enter a private Alchemical Hydra encounter"
            invalidArgs = "Use as ::hydra"
            cheat {
                if (args.isEmpty())
                    protectedAccess.launch(
                        player,
                        "Finish what you are doing before entering the Alchemical Hydra's lair.",
                    ) {
                        enterHydra()
                    }
            }
        }
        onCommand("leavehydra") {
            desc = "Leave your private Alchemical Hydra encounter"
            invalidArgs = "Use as ::leavehydra"
            cheat { if (args.isEmpty()) protectedAccess.launch(player) { leaveHydra() } }
        }
        onCommand("hydrastatus") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Inspect the active Alchemical Hydra state"
            invalidArgs = "Use as ::hydrastatus"
            cheat { if (args.isEmpty()) player.mes(encounters.status(player)) }
        }
    }

    internal suspend fun ProtectedAccess.enterHydra() {
        if (player.hitpoints <= 0) {
            mes("Finish what you are doing before entering the Alchemical Hydra's lair.")
            return
        }
        if (manager.sessionForPlayer(player) != null) {
            mes("You already have an active instance.")
            return
        }
        player.combatClearQueue()
        player.clearInteraction()
        player.resetAnim()
        completeInstanceEntry(
            manager.create(player, key, buildSpec(), InstanceAccess.Private, worldClock.cycle)
        )
        val session = manager.sessionForPlayer(player)?.takeIf { it.key == key } ?: return
        val destination = requireNotNull(manager.localCoord(session, HYDRA_OBSERVATION_DESTINATION))
        anim(AlchemicalHydraAssets.ENTRY_ANIM)
        exactMove(player.coords, destination, 0, HYDRA_ENTRY_CLIENT_CYCLES, HYDRA_ENTRY_ANGLE)
        mes("You climb over the rocks into the observation room.")
        mes("Cross the alchemical doors to begin the fight.")
    }

    internal suspend fun ProtectedAccess.leaveHydra() {
        if (manager.sessionForPlayer(player)?.key != key) {
            mes("You are not inside an Alchemical Hydra encounter.")
            return
        }
        defaultLeaveFlow()
        if (manager.sessionForPlayer(player)?.key == key) return
        player.combatClearQueue()
        player.clearInteraction()
        player.resetAnim()
        mes("You leave the Alchemical Hydra's lair.")
    }
}
