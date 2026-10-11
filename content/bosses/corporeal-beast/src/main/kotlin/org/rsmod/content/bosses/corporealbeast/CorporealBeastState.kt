package org.rsmod.content.bosses.corporealbeast

import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.HitValidity

internal const val CORP_STATE = "corp.room-effects"

/** Specialized room/minion state, owned by a native BossEncounter, never by a global run map. */
internal data class CorpState(
    var lastCombatTick: Int,
    var nextStompTick: Int,
    var nextEmptyHealTick: Int,
    var nextStatRestoreTick: Int,
    var nextCoreTick: Int,
    var core: Npc? = null,
    var coreJumping: Boolean = false,
) {
    val coreAlive
        get() = core?.let { it.isSlotAssigned && it.hitpoints > 0 } == true
}

internal fun corpRoomPlayers(players: PlayerList, npc: Npc): List<Player> =
    players.filter {
        it.isSlotAssigned &&
            it.hitpoints > 0 &&
            CorporealBeastRules.roomMatches(npc.spawnCoords, it.coords)
    }

internal fun corpHitValidity(
    deps: BossDeps,
    encounter: BossEncounter,
    target: Player,
): HitValidity {
    val uid = target.uid
    return HitValidity {
        deps.encounterRegistry.isCurrent(encounter.npc, encounter) &&
            target.isSlotAssigned &&
            target.uid == uid &&
            !target.pendingLogout &&
            !target.loggingOut &&
            target.hitpoints > 0 &&
            CorporealBeastRules.roomMatches(encounter.npc.spawnCoords, target.coords)
    }
}
