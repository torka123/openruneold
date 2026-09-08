package org.rsmod.api.net.rsprot.handlers

import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.friendchat.FriendChatKick
import org.rsmod.api.net.social.FriendChatService
import org.rsmod.game.entity.Player

public class FriendChatKickHandler @Inject constructor(private val friendChat: FriendChatService) :
    MessageHandler<FriendChatKick> {
    override fun handle(player: Player, message: FriendChatKick) {
        friendChat.requestKick(player, message.name)
    }
}
