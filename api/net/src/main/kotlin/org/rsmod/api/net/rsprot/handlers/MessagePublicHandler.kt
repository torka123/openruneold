package org.rsmod.api.net.rsprot.handlers

import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.messaging.MessagePublic
import org.rsmod.api.net.social.ChatPrefixRouter
import org.rsmod.api.net.social.ChatRoute
import org.rsmod.api.net.social.ClanChatService
import org.rsmod.api.net.social.FriendChatService
import org.rsmod.api.social.isChatMuted
import org.rsmod.api.social.writeSocialMessage
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PublicMessage

class MessagePublicHandler
@Inject
constructor(private val friendChat: FriendChatService, private val clanChat: ClanChatService) :
    MessageHandler<MessagePublic> {
    override fun handle(player: Player, message: MessagePublic) {
        when (message.type) {
            2 -> friendChat.sendMessage(player, message.message)
            3 -> clanChat.sendOwnMessage(player, message.message)
            4 -> clanChat.sendGuestMessage(player, message.message)
            0 -> {
                when (val route = ChatPrefixRouter.route(message.message)) {
                    is ChatRoute.FriendChat -> friendChat.sendMessage(player, route.message)
                    is ChatRoute.OwnClan -> clanChat.sendOwnMessage(player, route.message)
                    is ChatRoute.GuestClan -> clanChat.sendGuestMessage(player, route.message)
                    is ChatRoute.Public -> sendPublic(player, message, route.message)
                    ChatRoute.Empty -> Unit
                }
            }
            1 -> sendPublic(player, message, message.message)
        }
    }

    private fun sendPublic(player: Player, message: MessagePublic, text: String) {
        if (player.isChatMuted()) {
            player.writeSocialMessage("You are muted and cannot send messages.")
            return
        }
        val publicMessage =
            PublicMessage(
                text = text,
                colour = message.colour,
                effect = message.effect,
                clanType = if (message.clanType == -1) null else message.clanType,
                modIcon = player.modLevel.clientCode,
                autoTyper = message.type == 1,
                pattern = message.pattern?.asByteArray(),
            )
        player.publicMessage = publicMessage
    }
}
