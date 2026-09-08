package org.rsmod.api.net.social

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClanManagementRulesTest {
    @Test
    fun `creation allows one to five unique eligible founders including owner`() {
        for (founderCount in 1..5) {
            assertNull(ClanCreationRules.validate(validRequest(founderCount)))
        }
        assertEquals(
            "A clan requires between one and five founding players.",
            ClanCreationRules.validate(validRequest(0)),
        )
        assertEquals(
            "A clan requires between one and five founding players.",
            ClanCreationRules.validate(validRequest(6)),
        )
        assertEquals(
            "Every founding player must be unique.",
            ClanCreationRules.validate(
                validRequest()
                    .copy(founders = validFounders().dropLast(1) + validFounders().first())
            ),
        )
        assertEquals(
            "The clan owner must be one of the founders.",
            ClanCreationRules.validate(validRequest().copy(ownerCharacterId = 99)),
        )
    }

    @Test
    fun `every founder needs 25 hours of authoritative playtime`() {
        val founders = validFounders().toMutableList()
        founders[3] = founders[3].copy(playtimeTicks = 149_999)
        assertEquals(
            "Every founder needs at least 25 hours of playtime.",
            ClanCreationRules.validate(validRequest().copy(founders = founders)),
        )
    }

    @Test
    fun `clan name is normalized and bounded`() {
        assertEquals(
            "Clan names must contain letters or numbers.",
            ClanCreationRules.validate(validRequest().copy(requestedName = "!!!")),
        )
        assertEquals(
            "Clan names may have at most 12 characters.",
            ClanCreationRules.validate(validRequest().copy(requestedName = "ThirteenChars!")),
        )
    }

    @Test
    fun `modern clan and friends chat routes stay independent`() {
        assertTrue(ChatPrefixRouter.route("/friends") is ChatRoute.FriendChat)
        assertTrue(ChatPrefixRouter.route("//clan") is ChatRoute.OwnClan)
        assertTrue(ChatPrefixRouter.route("///guest") is ChatRoute.GuestClan)
    }

    @Test
    fun `permissions enforce explicit wire hierarchy and guest settings`() {
        assertTrue(ClanPermissions.canGuestListen(allowGuests = true))
        assertTrue(!ClanPermissions.canGuestListen(allowGuests = false))
        assertTrue(ClanPermissions.canGuestSpeak(allowGuests = true, guestsCanTalk = true))
        assertTrue(!ClanPermissions.canGuestSpeak(allowGuests = true, guestsCanTalk = false))
        assertTrue(
            ClanPermissions.canExpelMember(ClanRanks.DEPUTY_OWNER, 124, ClanRanks.ADMINISTRATOR)
        )
        assertTrue(
            !ClanPermissions.canExpelMember(ClanRanks.ADMINISTRATOR, 101, ClanRanks.ADMINISTRATOR)
        )
    }

    private fun validRequest(
        founderCount: Int = ClanLimits.FOUNDING_MEMBER_COUNT
    ): ClanCreationRequest = ClanCreationRequest("Open Rune", 1, validFounders(founderCount), 1)

    private fun validFounders(count: Int = ClanLimits.FOUNDING_MEMBER_COUNT): List<ClanFounder> =
        (1..count).map { ClanFounder(it, "Founder$it", 150_000) }
}
