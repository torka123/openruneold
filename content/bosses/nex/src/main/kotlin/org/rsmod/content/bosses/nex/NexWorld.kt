package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import org.rsmod.api.player.events.interact.LocCoordEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class NexWorld
@Inject
constructor(
    private val encounters: NexEncounterManager,
    private val protectedAccess: ProtectedAccessLauncher,
) : PluginScript() {
    override fun ScriptContext.startup() {
        encounters.startup()

        val passKey = LocCoordEvents.key(BARRIER_CONTROLLER, NEX_BARRIER)
        onProtectedEvent<LocCoordEvents.Op1>(passKey) {
            arriveDelay()
            encounters.enter(this)
        }
        val peekKey = LocCoordEvents.key(BARRIER_CONTROLLER, NEX_BARRIER)
        onProtectedEvent<LocCoordEvents.Op3>(peekKey) {
            arriveDelay()
            encounters.peek(player)
        }
        check(eventBus.contains(LocCoordEvents.Op1::class.java, passKey)) {
            "Nex barrier pass handler was not registered at $NEX_BARRIER"
        }
        check(eventBus.contains(LocCoordEvents.Op3::class.java, peekKey)) {
            "Nex barrier peek handler was not registered at $NEX_BARRIER"
        }

        val altarPrayKey = LocCoordEvents.key(ZAROS_ALTAR, NEX_ALTAR)
        onProtectedEvent<LocCoordEvents.Op1>(altarPrayKey) {
            arriveDelay()
            encounters.prayAtAltar(player)
        }
        val altarExitKey = LocCoordEvents.key(ZAROS_ALTAR, NEX_ALTAR)
        onProtectedEvent<LocCoordEvents.Op2>(altarExitKey) {
            arriveDelay()
            if (!encounters.isParticipant(player)) player.mes("You are not inside Nex's chamber.")
            else encounters.exit(this)
        }

        onOpLoc1(requireLoc(ICE_PRISON_LOC)) {
            arriveDelay()
            if (!encounters.breakIce(player))
                player.mes("That icicle is no longer trapping anyone.")
        }
        onOpLoc1(requireLoc(ICE_PRISON_ALT_LOC)) {
            arriveDelay()
            if (!encounters.breakIce(player))
                player.mes("That icicle is no longer trapping anyone.")
        }

        onCommand("nex") {
            desc = "Teleport to the safe Ancient Prison bank room"
            invalidArgs = "Use as ::nex"
            cheat {
                if (args.isEmpty())
                    protectedAccess.launch(
                        player,
                        "Finish what you are doing before travelling to the Ancient Prison.",
                    ) {
                        encounters.teleportBank(this)
                    }
            }
        }
    }

    private fun requireLoc(id: Int) =
        requireNotNull(ServerCacheManager.getObject(id)) { "Missing revision-240 Nex location $id" }

    private companion object {
        const val BARRIER_CONTROLLER = 42967
        const val ZAROS_ALTAR = 42965
        const val ICE_PRISON_LOC = 42943
        const val ICE_PRISON_ALT_LOC = 42944
    }
}
