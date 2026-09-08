package org.rsmod.content.interfaces.gameframe.script

import kotlin.test.Test
import kotlin.test.assertEquals

class SideChannelTabTest {
    @Test
    fun `revision 240 side-channel values are mapped explicitly`() {
        assertEquals(SideChannelTab.FriendChat, SideChannelTab.fromClientValue(0))
        assertEquals(SideChannelTab.OwnClan, SideChannelTab.fromClientValue(1))
        assertEquals(SideChannelTab.GuestClan, SideChannelTab.fromClientValue(2))
        assertEquals(SideChannelTab.Grouping, SideChannelTab.fromClientValue(3))
    }

    @Test
    fun `unknown or stale side-channel values safely restore friends chat`() {
        assertEquals(SideChannelTab.FriendChat, SideChannelTab.fromClientValue(-1))
        assertEquals(SideChannelTab.FriendChat, SideChannelTab.fromClientValue(7))
    }
}
