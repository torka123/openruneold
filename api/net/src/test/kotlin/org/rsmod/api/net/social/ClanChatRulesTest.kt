package org.rsmod.api.net.social

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClanChatRulesTest {
    @Test
    fun `longest current chat prefixes win and aliases strip their prefix`() {
        assertEquals(ChatRoute.GuestClan("guest"), ChatPrefixRouter.route("///guest"))
        assertEquals(ChatRoute.GuestClan("guest"), ChatPrefixRouter.route("/gc guest"))
        assertEquals(ChatRoute.OwnClan("clan"), ChatPrefixRouter.route("//clan"))
        assertEquals(ChatRoute.OwnClan("clan"), ChatPrefixRouter.route("/c clan"))
        assertEquals(ChatRoute.FriendChat("friends"), ChatPrefixRouter.route("/friends"))
        assertEquals(ChatRoute.FriendChat("friends"), ChatPrefixRouter.route("/f friends"))
        assertEquals(ChatRoute.Public("hello"), ChatPrefixRouter.route("hello"))
        assertEquals(ChatRoute.FriendChat("g guest"), ChatPrefixRouter.route("/g guest"))
    }

    @Test
    fun `empty prefixed messages are rejected safely`() {
        assertIs<ChatRoute.Empty>(ChatPrefixRouter.route(""))
        assertIs<ChatRoute.Empty>(ChatPrefixRouter.route("///   "))
        assertIs<ChatRoute.Empty>(ChatPrefixRouter.route("//"))
        assertIs<ChatRoute.Empty>(ChatPrefixRouter.route("/f"))
        assertIs<ChatRoute.Empty>(ChatPrefixRouter.route("/gc"))
    }

    @Test
    fun `social message text rejects blank controls and caps the protocol payload`() {
        assertNull(SocialChatText.normalize(" \u0000\n\t "))
        assertEquals("hello", SocialChatText.normalize("  he\u0000llo  "))
        assertEquals(SocialChatText.MAX_LENGTH, SocialChatText.normalize("x".repeat(200))?.length)
    }

    @Test
    fun `full friends channel only displaces a strictly lower ranked non owner`() {
        val ranks = linkedMapOf(1 to 7, 10 to -1, 11 to -1, 12 to 3)
        assertEquals(10, FriendChatCapacity.displacementCandidate(ranks, 1, 1))
        assertNull(FriendChatCapacity.displacementCandidate(ranks, 1, -1))
        assertEquals(500, FriendChatCapacity.MAX_MEMBERS)
    }

    @Test
    fun `friends chat temporary kicks retain their independent semantics`() {
        assertTrue(FriendChatKickRules.isActive(61_000L, 1_000L))
        assertFalse(FriendChatKickRules.isActive(1_000L, 1_000L))
        assertFalse(FriendChatKickRules.isActive(null, 1_000L))
        assertTrue(FriendChatKickRules.clearWhenEmpty(0))
        assertFalse(FriendChatKickRules.clearWhenEmpty(1))
    }

    @Test
    fun `fixed clan roles and explicit hierarchy cannot be spoofed`() {
        assertTrue(ClanRanks.isFixed(ClanRanks.GUEST))
        assertTrue(ClanRanks.isFixed(ClanRanks.ADMINISTRATOR))
        assertTrue(ClanRanks.isFixed(ClanRanks.DEPUTY_OWNER))
        assertTrue(ClanRanks.isFixed(ClanRanks.OWNER))
        assertTrue(ClanRanks.canActOn(ClanRanks.OWNER, ClanRanks.DEPUTY_OWNER))
        assertTrue(ClanRanks.canActOn(ClanRanks.DEPUTY_OWNER, 124))
        assertTrue(ClanRanks.canActOn(101, ClanRanks.ADMINISTRATOR))
        assertFalse(ClanRanks.canActOn(ClanRanks.ADMINISTRATOR, 101))
        assertFalse(ClanRanks.canActOn(ClanRanks.OWNER, ClanRanks.OWNER))
        assertFalse(ClanRanks.canActOn(99, ClanRanks.MEMBER))
        assertFalse(ClanRanks.atLeast(14, ClanRanks.MEMBER))
    }

    @Test
    fun `modern social channel wire kinds remain distinct`() {
        assertEquals(listOf(0, 1, 2), SocialChannelKind.entries.map { it.wireId })
    }

    @Test
    fun `clan speaking and guest permissions enforce configured thresholds`() {
        assertTrue(ClanPermissions.canMemberSpeak(40, muted = false, minimumRank = 40))
        assertFalse(ClanPermissions.canMemberSpeak(30, muted = false, minimumRank = 40))
        assertFalse(ClanPermissions.canMemberSpeak(126, muted = true, minimumRank = 0))
        assertTrue(ClanPermissions.canGuestListen(allowGuests = true))
        assertFalse(ClanPermissions.canGuestListen(allowGuests = false))
        assertTrue(ClanPermissions.canGuestSpeak(allowGuests = true, guestsCanTalk = true))
        assertFalse(ClanPermissions.canGuestSpeak(allowGuests = true, guestsCanTalk = false))
    }

    @Test
    fun `clan moderation protects hierarchy and owner role`() {
        assertTrue(ClanPermissions.canKickGuest(100, minimumRank = 100))
        assertFalse(ClanPermissions.canBanGuest(99, minimumRank = 100))
        assertTrue(ClanPermissions.canExpelMember(125, 100, minimumRank = 125))
        assertFalse(ClanPermissions.canExpelMember(100, 100, minimumRank = 100))
        assertTrue(ClanPermissions.canMuteMember(126, 125))
        assertFalse(ClanPermissions.canMuteMember(100, 125))
        assertTrue(ClanPermissions.canEditPermissions(125, minimumRank = 125))
        assertTrue(ClanRanks.validAssignable(99))
        assertTrue(ClanRanks.validAssignable(124))
        assertTrue(ClanRanks.validAssignable(ClanRanks.ADMINISTRATOR))
        assertFalse(ClanRanks.validAssignable(14))
        assertFalse(ClanRanks.validAssignable(ClanRanks.DEPUTY_OWNER))
        assertFalse(ClanRanks.validAssignable(ClanRanks.OWNER))
    }
}
