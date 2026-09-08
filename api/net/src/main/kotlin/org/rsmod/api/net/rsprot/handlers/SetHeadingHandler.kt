package org.rsmod.api.net.rsprot.handlers

import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.misc.user.SetHeading
import org.rsmod.api.player.events.SailingEvent
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player

class SetHeadingHandler @Inject constructor(private val eventBus: EventBus) :
    MessageHandler<SetHeading> {
    override fun handle(player: Player, message: SetHeading) {
        if (message.heading !in VALID_HEADINGS) {
            return
        }
        eventBus.publish(SailingEvent.SetHeading(player, message.heading))
    }

    private companion object {
        private val VALID_HEADINGS: IntRange = 0..15
    }
}
