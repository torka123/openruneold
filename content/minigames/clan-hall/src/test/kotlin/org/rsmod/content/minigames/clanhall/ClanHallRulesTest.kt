package org.rsmod.content.minigames.clanhall

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.rsmod.api.net.social.ClanHallSettings
import org.rsmod.api.net.social.ClanPermissionKey
import org.rsmod.api.net.social.ClanPermissionSet
import org.rsmod.api.net.social.ClanRanks

class ClanHallRulesTest {
    @Test
    fun `guest can enter an unlocked hall when guest entry is configured`() {
        val permissions = permissionSet(ClanPermissionKey.HallEnter to ClanRanks.GUEST)

        assertNull(
            ClanHallRules.entryDenial(
                context(
                    role = ClanHallRole.Guest,
                    rank = ClanRanks.GUEST,
                    rankedOccupants = 1,
                    permissions = permissions,
                )
            )
        )
    }

    @Test
    fun `guest cannot create an empty hall and may join only after a ranked member`() {
        val permissions = permissionSet(ClanPermissionKey.HallEnter to ClanRanks.GUEST)
        assertEquals(
            "A ranked clan member must be inside before guests can enter the Clan Hall.",
            ClanHallRules.entryDenial(
                context(
                    role = ClanHallRole.Guest,
                    rank = ClanRanks.GUEST,
                    rankedOccupants = 0,
                    permissions = permissions,
                )
            ),
        )
        assertNull(
            ClanHallRules.entryDenial(
                context(
                    role = ClanHallRole.Guest,
                    rank = ClanRanks.GUEST,
                    rankedOccupants = 1,
                    permissions = permissions,
                )
            )
        )
    }

    @Test
    fun `open recruitment or applications blocks hall entry`() {
        val expected = "Close clan recruitment and applications before entering the Clan Hall."
        assertEquals(expected, ClanHallRules.entryDenial(context(recruitmentOpen = true)))
        assertEquals(expected, ClanHallRules.entryDenial(context(applicationsOpen = true)))
    }

    @Test
    fun `locked hall blocks guests but not ranked members`() {
        val settings = ClanHallSettings(locked = true)
        val permissions = permissionSet(ClanPermissionKey.HallEnter to ClanRanks.GUEST)

        assertEquals(
            "This Clan Hall is currently locked to clan members.",
            ClanHallRules.entryDenial(
                context(
                    role = ClanHallRole.Guest,
                    rank = ClanRanks.GUEST,
                    rankedOccupants = 1,
                    settings = settings,
                    permissions = permissions,
                )
            ),
        )
        assertNull(
            ClanHallRules.entryDenial(
                context(
                    role = ClanHallRole.Member,
                    rank = ClanRanks.MEMBER,
                    settings = settings,
                    permissions = permissions,
                )
            )
        )
    }

    @Test
    fun `home world and capacity restrictions are authoritative`() {
        assertEquals(
            "This Clan Hall may only be entered on the clan's home world.",
            ClanHallRules.entryDenial(
                context(currentWorld = 302, settings = ClanHallSettings(homeWorldOnly = true))
            ),
        )
        assertEquals(
            "This Clan Hall is full.",
            ClanHallRules.entryDenial(
                context(occupants = 10, settings = ClanHallSettings(playerCapacity = 10))
            ),
        )
    }

    @Test
    fun `active editor excludes other players but not the editor`() {
        val settings =
            ClanHallSettings(
                editStateVersion = 4,
                editSessionCharacterId = 42,
                editSessionStartedAtEpochMillis = 1_000,
                editSessionExpiresAtEpochMillis = 2_000,
            )

        assertEquals(
            "The Clan Hall is currently being edited.",
            ClanHallRules.entryDenial(context(actorCharacterId = 7, settings = settings)),
        )
        assertNull(ClanHallRules.entryDenial(context(actorCharacterId = 42, settings = settings)))
        assertNull(
            ClanHallRules.entryDenial(
                context(actorCharacterId = 7, nowEpochMillis = 2_000, settings = settings)
            )
        )
    }

    @Test
    fun `hall entry and party controls enforce configured rank thresholds`() {
        val permissions =
            permissionSet(
                ClanPermissionKey.HallEnter to ClanRanks.ADMINISTRATOR,
                ClanPermissionKey.HallPartyChestDeposit to ClanRanks.ADMINISTRATOR,
                ClanPermissionKey.HallPullLever to ClanRanks.DEPUTY_OWNER,
            )

        assertEquals(
            "Your clan rank is not high enough to enter this Clan Hall.",
            ClanHallRules.entryDenial(context(rank = ClanRanks.MEMBER, permissions = permissions)),
        )
        assertEquals(
            "Your clan rank is not high enough to perform that Clan Hall action.",
            ClanHallRules.permissionDenial(
                ClanHallRole.Member,
                ClanRanks.MEMBER,
                permissions,
                ClanPermissionKey.HallPartyChestDeposit,
            ),
        )
        assertNull(
            ClanHallRules.permissionDenial(
                ClanHallRole.Member,
                ClanRanks.DEPUTY_OWNER,
                permissions,
                ClanPermissionKey.HallPullLever,
            )
        )
    }

    private fun context(
        actorCharacterId: Int = 1,
        nowEpochMillis: Long = 1_500,
        role: ClanHallRole = ClanHallRole.Member,
        rank: Int = ClanRanks.MEMBER,
        currentWorld: Int = 301,
        homeWorld: Int = 301,
        occupants: Int = 0,
        rankedOccupants: Int = if (role == ClanHallRole.Member) 1 else 0,
        recruitmentOpen: Boolean = false,
        applicationsOpen: Boolean = false,
        settings: ClanHallSettings = ClanHallSettings(),
        permissions: ClanPermissionSet = ClanPermissionSet.defaults(),
    ) =
        ClanHallEntryContext(
            actorCharacterId = actorCharacterId,
            nowEpochMillis = nowEpochMillis,
            role = role,
            rank = rank,
            currentWorld = currentWorld,
            homeWorld = homeWorld,
            occupants = occupants,
            rankedOccupants = rankedOccupants,
            recruitmentOpen = recruitmentOpen,
            applicationsOpen = applicationsOpen,
            settings = settings,
            permissions = permissions,
        )

    private fun permissionSet(vararg overrides: Pair<ClanPermissionKey, Int>): ClanPermissionSet =
        ClanPermissionSet(ClanPermissionSet.defaults().minimumRanks + overrides)
}
