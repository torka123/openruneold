package org.rsmod.api.net.rsprot.handlers

import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.clan.AffinedClanSettingsAddBannedFromChannel
import org.rsmod.api.net.social.ClanChatService
import org.rsmod.game.entity.Player

public class AffinedClanSettingsAddBannedHandler
@Inject
constructor(private val clans: ClanChatService) :
    MessageHandler<AffinedClanSettingsAddBannedFromChannel> {
    override fun handle(player: Player, message: AffinedClanSettingsAddBannedFromChannel) {
        clans.requestBanGuest(player, message.name)
    }
}
