package org.rsmod.content.bosses.scurrius

import jakarta.inject.Inject
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceEnterTransition
import org.rsmod.api.instances.InstanceScript
import org.rsmod.api.instances.enterLocObjects
import org.rsmod.api.instances.exitLocObjects
import org.rsmod.api.instances.withInstanceEnterTransition
import org.rsmod.api.instances.withInstanceLeaveTransition
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

class ScurriusInstance @Inject constructor(
    registry: BossInstanceRegistry,
    private val players: PlayerList,
) : InstanceScript(registry) {

    override fun settingsRow(): String = "dbrow.instance_scurrius"

    override fun area(): InstanceArea = PRIVATE_AREA

    override fun ScriptContext.configure() {
        val row = settingsRowData()
        onEnterPrelude { _, enter ->
            anim("seq.human_reachforladder")
            delay(1)
            withInstanceEnterTransition(InstanceEnterTransition(), enter)
        }

        onEnterObject { enterPersistentPublicRoom() }

        row.enterLocObjects().forEach { loc ->
            onOpLoc2(loc) { defaultInstanceEntry() }
        }
        row.exitLocObjects().forEach { loc ->
            onOpLoc2(loc) { quickEscapeScurrius() }
            onOpLoc3(loc) { peekPublicRoom() }
        }

        onExitObject { crossLeaveScurrius() }
    }

    private suspend fun ProtectedAccess.enterPersistentPublicRoom() {
        anim("seq.human_reachforladder")
        delay(1)
        telejump(PUBLIC_ENTRY)
    }

    private suspend fun ProtectedAccess.crossLeaveScurrius() {
        anim("seq.human_ledge_off_right")
        delay(1)
        val session = manager.sessionForPlayer(player)
        if (session != null && session.key == key) {
            withInstanceLeaveTransition { defaultLeaveFlow() }
        } else {
            telejump(PUBLIC_EXIT)
        }
    }

    private suspend fun ProtectedAccess.quickEscapeScurrius() {
        val session = manager.sessionForPlayer(player)
        if (session != null && session.key == key) {
            defaultLeaveFlow()
        } else {
            telejump(PUBLIC_EXIT)
        }
    }

    private fun ProtectedAccess.peekPublicRoom() {
        val count =
            players.count { candidate ->
                candidate.isSlotAssigned &&
                    candidate.coords.level == PUBLIC_CENTRE.level &&
                    candidate.coords.chebyshevDistance(PUBLIC_CENTRE) <= PUBLIC_ROOM_RADIUS
            }
        if (count == 0) {
            mes("The public Scurrius room is currently empty.")
        } else {
            mes(
                "There ${if (count == 1) "is" else "are"} $count " +
                    "player${if (count == 1) "" else "s"} in the public Scurrius room.",
            )
        }
    }

    private companion object {
        private val PUBLIC_ENTRY = CoordGrid(3290, 9868)
        private val PUBLIC_EXIT = CoordGrid(3281, 9870)
        private val PUBLIC_CENTRE = CoordGrid(3299, 9867)
        private const val PUBLIC_ROOM_RADIUS = 18
        private val PRIVATE_AREA = InstanceArea.Companion.copyRegions(
            centerRegionId = 13210,
            npcSpawns =
                listOf(
                    org.rsmod.api.instances.InstanceNpc(
                        "npc.rat_boss_instance",
                        CoordGrid(3299, 9867),
                    ),
                ),
        )
    }
}
