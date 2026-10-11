package org.rsmod.content.bosses.grotesqueguardians

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
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.game.map.Direction
import org.rsmod.plugin.scripts.ScriptContext

internal class GrotesqueGuardiansWorld
@Inject
constructor(
    registry: BossInstanceRegistry,
    private val encounters: GrotesqueGuardiansEncounterManager,
    private val protectedAccess: ProtectedAccessLauncher,
) :
    InstanceScript(
        registry,
        GROTESQUE_INSTANCE_KEY,
        grotesqueSpec(
            requireNotNull(ServerCacheManager.getNpc(GrotesqueAssets.DAWN_PHASE_ONE)),
            requireNotNull(ServerCacheManager.getNpc(GrotesqueAssets.DUSK_PHASE_ONE)),
        ),
    ) {
    override fun ScriptContext.configure() {
        encounters.validateAssets()
        onInstancePlayerJoin {
            manager.sessionForId(instanceId)?.let { encounters.onJoin(player, it) }
        }
        onEvent<InstanceLeaveRequestEvent>(instanceEventId(GROTESQUE_INSTANCE_KEY)) {
            if (manager.sessionForPlayer(player)?.id == instanceId) {
                result =
                    if (protectedAccess.launch(player) { leaveGuardians() }) RaidLeaveResult.Left
                    else RaidLeaveResult.Busy
            }
        }

        onOpLoc1(requireLoc(GrotesqueAssets.ROOF_ENTRANCE)) {
            arriveDelay()
            if (manager.sessionForPlayer(player)?.key == key) leaveGuardians() else enterGuardians()
        }
        onOpLoc1(requireLoc(GrotesqueAssets.CLOISTER_BELL)) {
            arriveDelay()
            encounters.ringBell(player)
        }
        GrotesqueAssets.ENERGY_SPHERES.forEach { id ->
            onOpLoc1(requireLoc(id)) {
                arriveDelay()
                encounters.absorbSphere(player, it.loc.coords)
            }
        }
        listOf(GrotesqueAssets.DAWN_PHASE_ONE, GrotesqueAssets.DAWN_PHASE_THREE).forEach { id ->
            onOpNpcU(requireNpc(id)) {
                arriveDelay()
                encounters.useHammer(player, it.npc, it.objType.internalName)
            }
        }

        onCommand("grotesque") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Create and enter a private Grotesque Guardians encounter"
            invalidArgs = "Use as ::grotesque"
            cheat { if (args.isEmpty()) protectedAccess.launch(player) { enterGuardians() } }
        }
        onCommand("gg") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Create and enter a private Grotesque Guardians encounter"
            invalidArgs = "Use as ::gg"
            cheat { if (args.isEmpty()) protectedAccess.launch(player) { enterGuardians() } }
        }
        onCommand("leavegg") {
            desc = "Leave your current Grotesque Guardians encounter"
            invalidArgs = "Use as ::leavegg"
            cheat { if (args.isEmpty()) protectedAccess.launch(player) { leaveGuardians() } }
        }
    }

    internal suspend fun ProtectedAccess.enterGuardians() {
        if (player.hitpoints <= 0) {
            mes("Finish what you are doing before entering the rooftop.")
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
        if (manager.sessionForPlayer(player)?.key != key) return
        player.faceDirection(Direction.North)
        mes("Ring the Cloister Bell to awaken the Grotesque Guardians.")
    }

    internal suspend fun ProtectedAccess.leaveGuardians() {
        if (manager.sessionForPlayer(player)?.key != key) {
            mes("You are not inside a Grotesque Guardians encounter.")
            return
        }
        defaultLeaveFlow()
        if (manager.sessionForPlayer(player)?.key == key) return
        player.combatClearQueue()
        player.clearInteraction()
        player.resetAnim()
        mes("You leave the Slayer Tower roof.")
    }

    private fun requireNpc(id: Int) =
        requireNotNull(ServerCacheManager.getNpc(id)) { "Missing revision-240 Guardian NPC $id" }

    private fun requireLoc(id: Int) =
        requireNotNull(ServerCacheManager.getObject(id)) { "Missing revision-240 Guardian loc $id" }
}
