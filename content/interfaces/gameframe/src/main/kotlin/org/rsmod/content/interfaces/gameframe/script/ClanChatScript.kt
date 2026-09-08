package org.rsmod.content.interfaces.gameframe.script

import dev.openrune.definition.type.widget.IfEvent
import jakarta.inject.Inject
import org.rsmod.api.net.social.ClanChatService
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Revision-240 dark-blue own-clan and cyan guest-clan side-panel wiring. */
public class ClanChatScript
@Inject
constructor(private val tabs: SideChannelTabs, private val clans: ClanChatService) : PluginScript() {
    override fun ScriptContext.startup() {
        onIfOpen(OWN_CLAN_PANEL) { player.enableOwnClanPanel() }
        onIfOpen(GUEST_CLAN_PANEL) { player.enableGuestClanPanel() }
        onIfOverlayButton(SIDE_OWN_CLAN_TAB) { tabs.openOwnClan(player) }
        onIfOverlayButton(SIDE_GUEST_CLAN_TAB) { tabs.openGuestClan(player) }
        onIfOverlayButton(SIDE_GROUPING_TAB) { tabs.rejectUnavailableGrouping(player) }
        onIfOverlayButton(OWN_REFRESH) { clans.refreshClient(player, OWN_CLAN_TYPE) }
        onIfOverlayButton(GUEST_REFRESH) { clans.refreshClient(player, GUEST_CLAN_TYPE) }
        onIfOverlayButton(GUEST_CHAT) { clans.leaveGuest(player) }
    }

    private fun Player.enableOwnClanPanel() {
        ifSetEvents(OWN_REFRESH, -1..-1, IfEvent.Op1)
    }

    private fun Player.enableGuestClanPanel() {
        ifSetEvents(GUEST_REFRESH, -1..-1, IfEvent.Op1)
        ifSetEvents(GUEST_SEARCH, -1..-1, IfEvent.Op1)
        ifSetEvents(GUEST_CHAT, -1..-1, IfEvent.Op1)
    }

    private companion object {
        private const val OWN_CLAN_TYPE = 0
        private const val GUEST_CLAN_TYPE = -1
        private const val SIDE_OWN_CLAN_TAB = "component.side_channels:tab_1"
        private const val SIDE_GUEST_CLAN_TAB = "component.side_channels:tab_2"
        private const val SIDE_GROUPING_TAB = "component.side_channels:tab_3"
        private const val OWN_CLAN_PANEL = "interface.clans_sidepanel"
        private const val GUEST_CLAN_PANEL = "interface.clans_guest_sidepanel"
        private const val OWN_REFRESH = "component.clans_sidepanel:refresh"
        private const val GUEST_REFRESH = "component.clans_guest_sidepanel:refresh"
        private const val GUEST_SEARCH = "component.clans_guest_sidepanel:search"
        private const val GUEST_CHAT = "component.clans_guest_sidepanel:chat"
    }
}
