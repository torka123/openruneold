package org.rsmod.content.interfaces.gameframe.script

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.net.social.ClanChatService
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.output.mes
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player

/** Owns the selected state and contents of the revision-240 social side-channel tabs. */
@Singleton
public class SideChannelTabs
@Inject
constructor(private val eventBus: EventBus, private val clans: ClanChatService) {
    public fun enable(player: Player) {
        player.ifSetEvents(SIDE_FRIEND_CHAT_TAB, -1..-1, IfEvent.Op1)
        player.ifSetEvents(SIDE_OWN_CLAN_TAB, -1..-1, IfEvent.Op1)
        player.ifSetEvents(SIDE_GUEST_CLAN_TAB, -1..-1, IfEvent.Op1)
        player.ifSetEvents(SIDE_GROUPING_TAB, -1..-1, IfEvent.Op1)
    }

    public fun restore(player: Player) {
        when (selected(player)) {
            SideChannelTab.FriendChat -> openFriendChat(player)
            SideChannelTab.OwnClan -> openOwnClan(player)
            SideChannelTab.GuestClan -> openGuestClan(player)
            SideChannelTab.Grouping -> {
                // Grouping has no server controller in this source tree. A stale value must not
                // reopen a dead stock interface during login or gameframe reconstruction.
                openFriendChat(player)
            }
        }
    }

    public fun openFriendChat(player: Player) {
        select(player, SideChannelTab.FriendChat)
        player.ifOpenOverlay(FRIEND_CHAT, SIDE_CHANNELS_CONTENTS, eventBus)
    }

    public fun openOwnClan(player: Player) {
        select(player, SideChannelTab.OwnClan)
        player.ifOpenOverlay(OWN_CLAN_PANEL, SIDE_CHANNELS_CONTENTS, eventBus)
        clans.refreshClient(player, OWN_CLAN_TYPE)
    }

    public fun openGuestClan(player: Player) {
        select(player, SideChannelTab.GuestClan)
        player.ifOpenOverlay(GUEST_CLAN_PANEL, SIDE_CHANNELS_CONTENTS, eventBus)
        clans.refreshClient(player, GUEST_CLAN_TYPE)
    }

    public fun rejectUnavailableGrouping(player: Player) {
        val previous =
            selected(player).takeUnless { it == SideChannelTab.Grouping }
                ?: SideChannelTab.FriendChat
        // The stock click script has already selected Grouping on the client. Re-sending the
        // authoritative server value restores the prior highlighted tab without replacing it.
        select(player, previous)
        player.mes("The Grouping tab is not available yet.")
    }

    private fun selected(player: Player): SideChannelTab {
        val varbit =
            requireNotNull(ServerCacheManager.getVarbit(SELECTED_TAB_VARBIT)) {
                "Missing revision-240 side-channel selection varbit $SELECTED_TAB_VARBIT"
            }
        return SideChannelTab.fromClientValue(player.vars[varbit])
    }

    private fun select(player: Player, tab: SideChannelTab) {
        val varbit =
            requireNotNull(ServerCacheManager.getVarbit(SELECTED_TAB_VARBIT)) {
                "Missing revision-240 side-channel selection varbit $SELECTED_TAB_VARBIT"
            }
        VarPlayerIntMapSetter.set(player, varbit, tab.clientValue)
    }

    private companion object {
        private const val SELECTED_TAB_VARBIT = 13_071
        private const val OWN_CLAN_TYPE = 0
        private const val GUEST_CLAN_TYPE = -1
        private const val SIDE_CHANNELS_CONTENTS = "component.side_channels:contents"
        private const val SIDE_FRIEND_CHAT_TAB = "component.side_channels:tab_0"
        private const val SIDE_OWN_CLAN_TAB = "component.side_channels:tab_1"
        private const val SIDE_GUEST_CLAN_TAB = "component.side_channels:tab_2"
        private const val SIDE_GROUPING_TAB = "component.side_channels:tab_3"
        private const val FRIEND_CHAT = "interface.chatchannel_current"
        private const val OWN_CLAN_PANEL = "interface.clans_sidepanel"
        private const val GUEST_CLAN_PANEL = "interface.clans_guest_sidepanel"
    }
}

internal enum class SideChannelTab(internal val clientValue: Int) {
    FriendChat(0),
    OwnClan(1),
    GuestClan(2),
    Grouping(3),
    ;

    internal companion object {
        internal fun fromClientValue(value: Int): SideChannelTab =
            entries.firstOrNull { it.clientValue == value } ?: FriendChat
    }
}
