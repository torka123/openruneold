package org.rsmod.api.net.rsprot.handlers

import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.friendchat.FriendChatSetRank
import org.rsmod.api.net.social.FriendChatService
import org.rsmod.game.entity.Player

public class FriendChatSetRankHandler
@Inject
constructor(private val friendChat: FriendChatService) : MessageHandler<FriendChatSetRank> {
    override fun handle(player: Player, message: FriendChatSetRank) {
        friendChat.requestSetRank(player, message.name, message.rank)
    }
}
