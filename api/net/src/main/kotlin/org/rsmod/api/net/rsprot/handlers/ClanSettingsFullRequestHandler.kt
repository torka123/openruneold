package org.rsmod.api.net.rsprot.handlers

import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.clan.ClanSettingsFullRequest
import org.rsmod.api.net.social.ClanChatService
import org.rsmod.game.entity.Player

public class ClanSettingsFullRequestHandler
@Inject
constructor(private val clans: ClanChatService) : MessageHandler<ClanSettingsFullRequest> {
    override fun handle(player: Player, message: ClanSettingsFullRequest) {
        clans.refreshClient(player, message.clanId)
    }
}
