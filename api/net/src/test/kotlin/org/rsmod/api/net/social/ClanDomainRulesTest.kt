package org.rsmod.api.net.social

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClanDomainRulesTest {
    @Test
    fun `rank slots map to the exact revision 240 wire bytes`() {
        val expected =
            listOf(
                0,
                10,
                20,
                30,
                40,
                50,
                60,
                70,
                80,
                90,
                95,
                96,
                97,
                98,
                99,
                101,
                102,
                103,
                104,
                105,
                110,
                115,
                120,
                124,
            )
        assertEquals(expected, ClanRanks.CUSTOM_RANKS)
        assertEquals((0..23).toList(), expected.map { checkNotNull(ClanRanks.titleSlot(it)) })
        assertEquals(expected, (0..23).map { checkNotNull(ClanRanks.rankForTitleSlot(it)) })
        assertEquals(16, ClanRanks.hierarchyIndex(ClanRanks.ADMINISTRATOR))

        assertTrue(ClanRanks.higherThan(101, ClanRanks.ADMINISTRATOR))
        assertFalse(ClanRanks.higherThan(ClanRanks.ADMINISTRATOR, 101))
        assertFalse(ClanRanks.isValidMemberRank(14))
        assertFalse(ClanRanks.isValidMemberRank(106))
    }

    @Test
    fun `rank title ids are the exact controlled revision 240 catalog`() {
        assertEquals(3797, ClanRankTitleCatalog.CACHE_ENUM_ID)
        assertEquals(265, ClanRankTitleCatalog.selectable.size)
        assertEquals("Sheriff", ClanRankTitleCatalog.name(1))
        assertEquals("Dogsbody", ClanRankTitleCatalog.name(22))
        assertEquals("Helper", ClanRankTitleCatalog.name(265))
        assertEquals(22, ClanRankTitleCatalog.id(" dogsBODY "))
        assertNull(ClanRankTitleCatalog.name(-4))
        assertNull(ClanRankTitleCatalog.name(266))
        assertEquals(265, ClanRankTitleCatalog.selectable.values.toSet().size)
    }

    @Test
    fun `clans expose exactly three interest slots`() {
        assertEquals(3, ClanDomainLimits.INTERESTS_PER_CLAN)
    }

    @Test
    fun `canonical permission set is complete and enforces management floors`() {
        assertEquals(19, ClanPermissionKey.entries.size)
        assertNull(ClanPermissionSet.defaults().validationError())
        val belowAdmin =
            ClanPermissionSet.defaults().minimumRanks.toMutableMap().apply {
                this[ClanPermissionKey.EditMotif] = 99
            }
        assertEquals(
            "The edit_motif rank is below its minimum.",
            ClanPermissionSet(belowAdmin).validationError(),
        )
        val missing = ClanPermissionSet.defaults().minimumRanks - ClanPermissionKey.CofferWithdraw
        assertTrue(
            checkNotNull(ClanPermissionSet(missing).validationError()).contains("coffer_withdraw")
        )
    }

    @Test
    fun `paired palette dual symbol motif codec matches revision 240`() {
        assertEquals(8, ClanMotifColour.entries.size)
        assertEquals((0..7).toList(), ClanMotifColour.entries.map { it.storageId })
        assertEquals(
            listOf(
                0x282828 to 0x202020,
                0xD09038 to 0xB57B24,
                0x303068 to 0x2D2860,
                0x981010 to 0x781010,
                0x789008 to 0x607800,
                0xD0C848 to 0xB8B028,
                0x98C8F8 to 0x98A8B8,
                0x9868B0 to 0x805098,
            ),
            ClanMotifColour.entries.map { it.primaryRgb to it.secondaryRgb },
        )
        assertEquals(27, ClanMotifIcon.entries.size)
        assertEquals((0..26).toList(), ClanMotifIcon.entries.map { it.storageId })
        assertEquals(16, ClanInterest.entries.size)

        val motif =
            ClanMotif(
                ClanMotifColour.Purple,
                ClanMotifColour.Purple,
                ClanMotifIcon.Skull,
                ClanMotifIcon.UltimateIron,
            )
        val packed = 25 or (27 shl 10) or (7 shl 20)
        assertEquals(packed, ClanMotifCodec.encode(motif))
        assertEquals(motif, ClanMotifCodec.decode(packed))

        val blankSymbols = ClanMotif(ClanMotifColour.Black, ClanMotifColour.Black, null, null)
        assertEquals(0, ClanMotifCodec.encode(blankSymbols))
        assertEquals(blankSymbols, ClanMotifCodec.decode(0))
        assertEquals(
            1,
            ClanMotifCodec.encode(blankSymbols.copy(primarySymbol = ClanMotifIcon.DragonMedHelm)),
        )
        assertNull(ClanMotifCodec.decode(28))
        assertNull(ClanMotifCodec.decode(1 shl 30))

        val mismatched = motif.copy(secondaryColour = ClanMotifColour.Orange)
        assertTrue(ClanMotifCodec.decode(ClanMotifCodec.encode(mismatched)) != mismatched)
    }

    @Test
    fun `guest requirements include quest points and iron status`() {
        val candidate = ClanGuestCandidate(100, 2_000, 250, true, false, true)
        assertTrue(
            ClanGuestRequirements(90, 1_500, 200, ClanGuestIronRequirement.AnyIron)
                .accepts(candidate)
        )
        assertTrue(
            ClanGuestRequirements(90, 1_500, 200, ClanGuestIronRequirement.HardcoreIronOnly)
                .accepts(candidate)
        )
        assertFalse(
            ClanGuestRequirements(90, 1_500, 200, ClanGuestIronRequirement.UltimateIronOnly)
                .accepts(candidate)
        )
        assertFalse(ClanGuestRequirements(minimumQuestPoints = 251).accepts(candidate))
    }

    @Test
    fun `durable creation draft allows zero to four accepted unique invitees`() {
        val owner = ClanFounder(1, "Owner", ClanLimits.MINIMUM_FOUNDER_PLAYTIME_TICKS)
        val invitations =
            (2..5).map { id ->
                ClanFounderInvitation(
                    id,
                    "Founder$id",
                    id - 1,
                    ClanFounderInvitationState.Accepted,
                    ClanLimits.MINIMUM_FOUNDER_PLAYTIME_TICKS,
                )
            }
        val draft = ClanCreationDraft(owner, "Open Rune", 477, invitations)
        assertNull(ClanCreationDraftRules.validationError(draft))
        assertNull(ClanCreationDraftRules.validationError(draft.copy(invitations = emptyList())))
        assertTrue(
            checkNotNull(
                    ClanCreationDraftRules.validationError(
                        draft.copy(
                            invitations =
                                invitations +
                                    ClanFounderInvitation(
                                        6,
                                        "Founder6",
                                        1,
                                        ClanFounderInvitationState.Accepted,
                                        ClanLimits.MINIMUM_FOUNDER_PLAYTIME_TICKS,
                                    )
                        )
                    )
                )
                .contains("at most four")
        )
        assertTrue(
            checkNotNull(
                    ClanCreationDraftRules.validationError(
                        draft.copy(
                            invitations =
                                invitations.mapIndexed { index, invite ->
                                    if (index == 1) {
                                        invite.copy(founderOrder = invitations.first().founderOrder)
                                    } else {
                                        invite
                                    }
                                }
                        )
                    )
                )
                .contains("position must be unique")
        )
        assertTrue(
            checkNotNull(
                    ClanCreationDraftRules.validationError(
                        draft.copy(
                            invitations =
                                invitations.mapIndexed { index, invite ->
                                    if (index == 0) {
                                        invite.copy(state = ClanFounderInvitationState.Pending)
                                    } else {
                                        invite
                                    }
                                }
                        )
                    )
                )
                .contains("accept")
        )
    }

    @Test
    fun `owner validation and succession use hierarchy rank then tenure`() {
        val members =
            listOf(
                ClanOwnershipMember(1, ClanRanks.OWNER),
                ClanOwnershipMember(2, ClanRanks.DEPUTY_OWNER),
            )
        assertNull(ClanOwnershipRules.validationError(1, members))
        assertEquals(
            "A live clan must have exactly one Owner.",
            ClanOwnershipRules.validationError(1, members + ClanOwnershipMember(3, ClanRanks.OWNER)),
        )
        val now = 200L * ClanOwnershipRules.DAY_MILLIS
        val successor =
            ClanOwnershipRules.selectSuccessor(
                listOf(
                    ClanSuccessionCandidate(2, 124, 50, now - 10),
                    ClanSuccessionCandidate(3, 124, 40, now - 20),
                    ClanSuccessionCandidate(4, ClanRanks.DEPUTY_OWNER, 60, now - 100),
                    ClanSuccessionCandidate(
                        5,
                        ClanRanks.DEPUTY_OWNER,
                        10,
                        now - 91L * ClanOwnershipRules.DAY_MILLIS,
                    ),
                ),
                now,
            )
        assertEquals(4, successor?.characterId)
    }

    @Test
    fun `events retain exact tick duration and enforce thirty day maximum`() {
        val valid =
            ClanEventDraft(
                ClanEventType.Bossing,
                ClanEventSubtype.Mass,
                "royal_titans",
                30,
                23,
                59,
                ClanDomainLimits.MAX_EVENT_DURATION_TICKS,
                ClanRanks.GUEST,
                477,
            )
        assertNull(ClanEventRules.validationError(valid))
        assertTrue(
            checkNotNull(
                    ClanEventRules.validationError(
                        valid.copy(durationTicks = ClanDomainLimits.MAX_EVENT_DURATION_TICKS + 1)
                    )
                )
                .contains("30 days")
        )
        assertTrue(
            checkNotNull(
                    ClanEventRules.validationError(
                        valid.copy(type = ClanEventType.Social, activityKey = "royal_titans")
                    )
                )
                .contains("type")
        )
    }

    @Test
    fun `coffer hall notification and broadcast boundaries are represented`() {
        assertTrue(ClanCofferRules.canApply(49_999_999, 1))
        assertFalse(ClanCofferRules.canApply(49_999_999, 2))
        assertEquals(ClanCofferTransactionType.Withdrawal.storageId, 1)
        assertNull(
            ClanHallSettings(
                    playerCapacity = 2_000,
                    themeKey = "stone_1",
                    identityKey = "clan_home",
                    editStateVersion = 4,
                    editSessionCharacterId = 1,
                    editSessionStartedAtEpochMillis = 100,
                    editSessionExpiresAtEpochMillis = 200,
                )
                .validationError()
        )
        assertTrue(
            checkNotNull(ClanHallSettings(themeKey = "Bad Theme").validationError())
                .contains("theme")
        )
        assertTrue(ClanMemberNotificationPreferences().recruiterNotificationsEnabled)
        assertNull(ClanBroadcastSettings.defaults().validationError())
        assertFalse(
            ClanBroadcastRules.shouldBroadcastLevel(49, ClanLevelBroadcastInterval.EveryLevel)
        )
        assertTrue(
            ClanBroadcastRules.shouldBroadcastCollectionSlot(
                2,
                ClanCollectionBroadcastInterval.EveryFiftySlots,
                isRareItem = true,
                isPet = false,
            )
        )
    }

    @Test
    fun `hall edit lease excludes another editor but permits renewal and stale takeover`() {
        val active =
            ClanHallSettings(
                editStateVersion = 1,
                editSessionCharacterId = 42,
                editSessionStartedAtEpochMillis = 1_000,
                editSessionExpiresAtEpochMillis = 2_000,
            )
        assertEquals(
            "The Clan Hall is currently being edited by another clan member.",
            ClanHallEditSessionRules.acquisitionDenial(active, 7, 1_500),
        )
        assertNull(ClanHallEditSessionRules.acquisitionDenial(active, 42, 1_500))
        assertTrue(ClanHallEditSessionRules.ownsLease(active, 42, 1_500))
        assertFalse(ClanHallEditSessionRules.ownsLease(active, 7, 1_500))
        assertNull(ClanHallEditSessionRules.acquisitionDenial(active, 7, 2_000))
    }

    @Test
    fun `hall edit lease lasts exactly thirty minutes`() {
        assertEquals(1_800_000L, ClanHallEditSessionRules.LEASE_MILLIS)
    }

    @Test
    fun `default hall edit permission denies an ordinary member`() {
        val threshold = ClanPermissionSet.defaults()[ClanPermissionKey.HallEditSettings]
        assertEquals(ClanRanks.ADMINISTRATOR, threshold)
        assertFalse(ClanRanks.atLeast(ClanRanks.MEMBER, threshold))
        assertTrue(ClanRanks.atLeast(ClanRanks.OWNER, threshold))
    }
}
