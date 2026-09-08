package org.rsmod.content.minigames.pestcontrol

import org.rsmod.api.instances.InstanceSession
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.map.CoordGrid

internal class PestControlMatch(
    val session: InstanceSession,
    val startedAt: Int,
    val instanceEntry: CoordGrid,
) {
    var state: PestControlState = PestControlState.STARTING
    var result: PestControlResult? = null
    var resultTick: Int? = null
    var cleanupStarted = false

    val portals = linkedMapOf<PortalColor, PestControlPortal>()
    val participants = linkedMapOf<PlayerUid, PestControlParticipant>()
    val portalTracker = PestControlPortalTracker()

    val destroyedPortalCount: Int
        get() = portalTracker.destroyedCount

    val isRunning: Boolean
        get() = state == PestControlState.RUNNING

    fun remainingTicks(currentTick: Int): Int =
        (PC_MATCH_TICKS - (currentTick - startedAt)).coerceAtLeast(0)

    fun owns(npc: Npc): Boolean = portals.values.any { it.npc === npc }

    fun participant(uid: PlayerUid): PestControlParticipant? = participants[uid]

    fun portalFor(npc: Npc): PestControlPortal? = portals.values.firstOrNull { it.npc === npc }

    fun mapSourceCoord(source: CoordGrid): CoordGrid =
        instanceEntry.translate(
            source.x - PestControlCoords.playerEntry.x,
            source.z - PestControlCoords.playerEntry.z,
            source.level - PestControlCoords.playerEntry.level,
        )
}

internal class PestControlPortal(val color: PortalColor, val npc: Npc, val maximumHitpoints: Int) {
    val hitpoints: Int
        get() = npc.hitpoints.coerceAtLeast(0)
}

internal class PestControlParticipant(val uid: PlayerUid) {
    var rewarded = false
        private set

    var pendingDeathRespawn = false
    var lastUi: PestControlUiSnapshot? = null

    fun claimReward(): Boolean {
        if (rewarded) return false
        rewarded = true
        return true
    }
}

internal data class PestControlUiSnapshot(
    val secondsRemaining: Int,
    val portalHitpoints: List<Int>,
    val destroyed: List<Boolean>,
)
