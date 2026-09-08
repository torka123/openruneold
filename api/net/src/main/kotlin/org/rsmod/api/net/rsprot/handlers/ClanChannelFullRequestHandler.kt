package org.rsmod.api.net.rsprot.handlers

import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.clan.ClanChannelFullRequest
import org.rsmod.api.net.social.ClanChatService
import org.rsmod.game.entity.Player

public class ClanChannelFullRequestHandler @Inject constructor(private val clans: ClanChatService) :
    MessageHandler<ClanChannelFullRequest> {
    override fun handle(player: Player, message: ClanChannelFullRequest) {
        clans.refreshClient(player, message.clanId)
    }
}
