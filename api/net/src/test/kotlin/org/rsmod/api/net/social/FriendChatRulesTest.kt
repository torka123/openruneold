package org.rsmod.api.net.social

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FriendChatRulesTest {
    @Test
    fun `names normalize across case spaces and underscores`() {
        assertEquals("zezima one", SocialNameNormalizer.normalize("  Zezima___ONE  "))
        assertEquals("My Chat42", SocialNameNormalizer.channelName("My Chat#42!!!"))
        assertEquals(12, SocialNameNormalizer.channelName("abcdefghijklmnop").length)
    }

    @Test
    fun `permission thresholds include their own rank`() {
        assertTrue(FriendChatPermission.Anyone.allows(-1))
        assertTrue(FriendChatPermission.Recruit.allows(1))
        assertTrue(FriendChatPermission.Recruit.allows(7))
        assertFalse(FriendChatPermission.Recruit.allows(0))
        assertFalse(FriendChatPermission.Owner.allows(6))
        assertTrue(FriendChatPermission.Owner.allows(7))
    }

    @Test
    fun `runtime tracks aliases ranks ignores and logout cleanup`() {
        val runtime = SocialRuntimeService()
        val identity = SocialIdentity(2, "New Name", "Old_Name")
        runtime.install(
            1,
            SocialRuntimeSnapshot(
                friends = listOf(SocialFriend(identity, 4)),
                ignoredCharacterIds = setOf(3),
            ),
        )

        assertEquals(4, runtime.friendRank(1, 2))
        assertEquals(4, runtime.friendRankByName(1, "old name"))
        assertTrue(runtime.isIgnoring(1, 3))

        runtime.updateFriendRank(1, identity, 6)
        runtime.removeIgnore(1, 3)
        assertEquals(6, runtime.friendRankByName(1, "NEW_NAME"))
        assertFalse(runtime.isIgnoring(1, 3))

        runtime.clear(1)
        assertEquals(-1, runtime.friendRank(1, 2))
    }

    @Test
    fun `message limiter blocks burst and recovers after window`() {
        val runtime = SocialRuntimeService()
        repeat(6) { assertTrue(runtime.allowMessage(99, nowMillis = 1_000L)) }
        assertFalse(runtime.allowMessage(99, nowMillis = 1_000L))
        assertTrue(runtime.allowMessage(99, nowMillis = 6_000L))
    }
}
