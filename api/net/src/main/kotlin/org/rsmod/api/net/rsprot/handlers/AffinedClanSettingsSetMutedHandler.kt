package org.rsmod.api.net.rsprot.handlers

import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.clan.AffinedClanSettingsSetMutedFromChannel
import org.rsmod.api.net.social.ClanChatService
import org.rsmod.game.entity.Player

public class AffinedClanSettingsSetMutedHandler
@Inject
constructor(private val clans: ClanChatService) :
    MessageHandler<AffinedClanSettingsSetMutedFromChannel> {
    override fun handle(player: Player, message: AffinedClanSettingsSetMutedFromChannel) {
        clans.requestSetMuted(player, message.name, message.muted)
    }
}
