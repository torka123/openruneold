package org.rsmod.content.interfaces.gameframe.script

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import org.rsmod.api.net.social.FriendChatPermission
import org.rsmod.api.net.social.FriendChatPermissionField
import org.rsmod.api.net.social.FriendChatService
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

public class FriendChatScript
@Inject
constructor(private val tabs: SideChannelTabs, private val friendChat: FriendChatService) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onIfOpen(SIDE_CHANNELS) {
            tabs.enable(player)
            tabs.restore(player)
        }
        onIfOpen(FRIEND_CHAT) {
            player.enableFriendChatControls()
            friendChat.refreshClient(player)
        }
        onIfOverlayButton(SIDE_FRIEND_CHAT_TAB) { tabs.openFriendChat(player) }
        onIfOverlayButton(FRIEND_CHAT_SETUP) { friendChat.openSettings(player) }

        onIfModalButton(SETTINGS_NAME) { event ->
            when (event.op) {
                IfButtonOp.Op1 -> {
                    val name = stringDialog("Enter a name for your Friends Chat:")
                    friendChat.requestChannelName(player, name)
                }
                IfButtonOp.Op2 -> friendChat.requestChannelEnabled(player, false)
                else -> Unit
            }
        }
        onIfModalButton(SETTINGS_JOIN) { event ->
            selectPermission(event.op, FriendChatPermissionField.Join)
        }
        onIfModalButton(SETTINGS_TALK) { event ->
            selectPermission(event.op, FriendChatPermissionField.Talk)
        }
        onIfModalButton(SETTINGS_KICK) { event ->
            selectPermission(event.op, FriendChatPermissionField.Kick)
        }
    }

    private fun org.rsmod.api.player.protect.ProtectedAccess.selectPermission(
        op: IfButtonOp,
        field: FriendChatPermissionField,
    ) {
        val permission = FriendChatPermission.fromWire(op.slot - 2) ?: return
        friendChat.requestPermission(player, field, permission)
    }

    private fun Player.enableFriendChatControls() {
        ifSetEvents(FRIEND_CHAT_SETUP, -1..-1, IfEvent.Op1)
        ifSetEvents(FRIEND_CHAT_JOIN_LEAVE, -1..-1, IfEvent.Op1)
        ifSetEvents(SIDE_FRIEND_CHAT_TAB, -1..-1, IfEvent.Op1)
    }

    private companion object {
        private const val SIDE_CHANNELS = "interface.side_channels"
        private const val SIDE_FRIEND_CHAT_TAB = "component.side_channels:tab_0"
        private const val FRIEND_CHAT = "interface.chatchannel_current"
        private const val FRIEND_CHAT_SETUP = "component.chatchannel_current:setup"
        private const val FRIEND_CHAT_JOIN_LEAVE = "component.chatchannel_current:joinleave"
        private const val SETTINGS_NAME = "component.chatchannel_setup:opt_prefix"
        private const val SETTINGS_JOIN = "component.chatchannel_setup:opt_enter"
        private const val SETTINGS_TALK = "component.chatchannel_setup:opt_talk"
        private const val SETTINGS_KICK = "component.chatchannel_setup:opt_kick"
    }
}
