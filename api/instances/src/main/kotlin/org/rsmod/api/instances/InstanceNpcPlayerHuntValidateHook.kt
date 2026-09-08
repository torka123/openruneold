package org.rsmod.api.instances

import jakarta.inject.Inject
import org.rsmod.api.game.process.npc.hunt.NpcPlayerHuntValidateHook
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/** Prevents copied-map NPCs and players in different encounters from targeting one another. */
public class InstanceNpcPlayerHuntValidateHook
@Inject
constructor(private val instances: InstanceManager) : NpcPlayerHuntValidateHook {
    override fun canHunt(npc: Npc, player: Player): Boolean {
        return sameHuntInstance(
            npcInstance = instances.instanceForNpc(npc),
            playerInstance = instances.sessionForPlayer(player)?.id,
        )
    }
}

internal fun sameHuntInstance(npcInstance: InstanceId?, playerInstance: InstanceId?): Boolean =
    npcInstance == playerInstance
