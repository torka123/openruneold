package org.rsmod.content.minigames.clanmayhem

import java.util.UUID

internal class ClanMayhemTimeline(private val matchId: UUID) {
    private val fired = mutableSetOf<ClanMayhemTimelineEvent>()

    fun due(activeMatchId: UUID, elapsedTicks: Int): List<ClanMayhemTimelineEvent> {
        if (activeMatchId != matchId || elapsedTicks < 0) return emptyList()
        return ClanMayhemTimelineEvent.entries.filter { event ->
            elapsedTicks >= event.atTick && fired.add(event)
        }
    }

    fun hasFired(event: ClanMayhemTimelineEvent): Boolean = event in fired
}
