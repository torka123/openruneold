package org.rsmod.api.net.rsprot.handlers

import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.clan.ClanChannelKickUser
import org.rsmod.api.net.social.ClanChatService
import org.rsmod.game.entity.Player

public class ClanChannelKickUserHandler @Inject constructor(private val clans: ClanChatService) :
    MessageHandler<ClanChannelKickUser> {
    override fun handle(player: Player, message: ClanChannelKickUser) {
        clans.requestKickOrExpel(player, message.clanId, message.name)
    }
}
