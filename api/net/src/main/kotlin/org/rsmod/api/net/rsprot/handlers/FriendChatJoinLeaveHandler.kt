package org.rsmod.api.net.rsprot.handlers

import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.friendchat.FriendChatJoinLeave
import org.rsmod.api.net.social.FriendChatService
import org.rsmod.game.entity.Player

public class FriendChatJoinLeaveHandler
@Inject
constructor(private val friendChat: FriendChatService) : MessageHandler<FriendChatJoinLeave> {
    override fun handle(player: Player, message: FriendChatJoinLeave) {
        friendChat.requestJoin(player, message.name)
    }
}
