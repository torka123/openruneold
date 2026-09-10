package org.rsmod.content.bosses.muspah

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
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
import org.rsmod.plugin.scripts.ScriptContext

internal class MuspahWorld
@Inject
constructor(
    registry: BossInstanceRegistry,
    private val encounters: MuspahEncounterManager,
    private val protectedAccess: ProtectedAccessLauncher,
) :
    InstanceScript(
        registry,
        MUSPAH_INSTANCE_KEY,
        muspahSpec(
            requireNotNull(ServerCacheManager.getNpc(MuspahAssets.RANGED.asRSCM(RSCMType.NPC)))
        ),
    ) {
    override fun ScriptContext.configure() {
        encounters.validateAssets()
        onInstancePlayerJoin {
            manager.sessionForId(instanceId)?.let { encounters.onJoin(player, it) }
        }
        onEvent<InstanceLeaveRequestEvent>(instanceEventId(MUSPAH_INSTANCE_KEY)) {
            if (manager.sessionForPlayer(player)?.id == instanceId) {
                result =
                    if (protectedAccess.launch(player) { leaveLair() }) RaidLeaveResult.Left
                    else RaidLeaveResult.Busy
            }
        }
        onOpLoc1(requireLoc(MuspahAssets.ENTRANCE_ID)) {
            arriveDelay()
            startLair()
        }
        for (exit in listOf(MuspahAssets.EXIT_ID, MuspahAssets.ESCAPE_ID)) {
            onOpLoc1(requireLoc(exit)) {
                arriveDelay()
                leaveLair()
            }
        }
        onCommand("muspah") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Teleport outside the real Phantom Muspah crevice"
            invalidArgs = "Use as ::muspah"
            cheat {
                if (args.isEmpty())
                    protectedAccess.launch(player) {
                        val session = manager.sessionForPlayer(player)
                        if (session != null && session.key != key) {
                            mes(
                                "Leave your current instance before travelling to Ghorrock Dungeon."
                            )
                            return@launch
                        }
                        if (session != null) leaveLair() else telejump(MUSPAH_OUTSIDE)
                        mes("You arrive immediately outside the Phantom Muspah crevice.")
                    }
            }
        }
        onCommand("muspahdebug") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Show the current private Phantom Muspah encounter state"
            invalidArgs = "Use as ::muspahdebug"
            cheat { if (args.isEmpty()) player.mes(encounters.debugStatus(player)) }
        }
    }

    internal suspend fun ProtectedAccess.startLair() {
        if (MUSPAH_REQUIRE_SECRETS_OF_NORTH) {
            mes("You must complete Secrets of the North before entering this crevice.")
            return
        }
        if (player.hitpoints <= 0) return
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
    }

    internal suspend fun ProtectedAccess.leaveLair() {
        if (manager.sessionForPlayer(player)?.key != key) {
            mes("You are not inside a Phantom Muspah encounter.")
            return
        }
        defaultLeaveFlow()
        if (manager.sessionForPlayer(player)?.key != key)
            mes("You escape from the Phantom Muspah's lair.")
    }

    private fun requireLoc(id: Int) =
        requireNotNull(ServerCacheManager.getObject(id)) {
            "Missing Phantom Muspah location definition: $id"
        }
}
