package org.rsmod.api.net.social

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.rsprot.protocol.game.outgoing.clan.ClanSettingsFull
import net.rsprot.protocol.game.outgoing.clan.VarClan

class ClanClientSettingsTest {
    @Test
    fun `revision 240 settings contain explicit titles motif and permission ids`() {
        val broadcastRanks =
            ClanBroadcastKey.entries.associateWith { key ->
                if (key == ClanBroadcastKey.Membership) ClanRanks.ADMINISTRATOR
                else ClanRanks.MEMBER
            }
        val broadcasts =
            ClanBroadcastSettings(
                minimumRanks = broadcastRanks,
                post99XpInterval = 25_000_000,
                collectionInterval = ClanCollectionBroadcastInterval.EveryTwentyFiveSlots,
                minimumLootValue = 12_345,
                minimumPlayerKillValue = 23_456,
                minimumLootKeyValue = 34_567,
                minimumCombatAchievementTier = ClanCombatAchievementTier.Master,
                mainGameWorldsOnly = true,
                eventStartingNotificationsEnabled = false,
                levelEveryTenMinimum = 40,
                levelEveryFiveMinimum = 70,
                levelEveryOneMinimum = 90,
            )
        val clan =
            ClanSnapshot(
                clanId = 1,
                displayName = "Test",
                ownerCharacterId = 1,
                creationRuneDay = 1,
                homeWorld = 1,
                allowGuests = true,
                guestsCanTalk = true,
                talkRank = ClanRanks.GUEST,
                kickRank = ClanRanks.ADMINISTRATOR,
                banRank = ClanRanks.ADMINISTRATOR,
                expelRank = ClanRanks.DEPUTY_OWNER,
                assignRank = ClanRanks.ADMINISTRATOR,
                editPermissionsRank = ClanRanks.DEPUTY_OWNER,
                stateVersion = 1,
                members = emptyList(),
                bans = emptyList(),
                titles = listOf(ClanRankTitle(0, 17, "Example"), ClanRankTitle(3, 9, "Other")),
                interests = setOf(ClanInterest.BossHunting, ClanInterest.Raiding),
                motif =
                    ClanMotif(
                        ClanMotifColour.Purple,
                        ClanMotifColour.Purple,
                        ClanMotifIcon.Skull,
                        ClanMotifIcon.UltimateIron,
                    ),
                guestRequirements =
                    ClanGuestRequirements(
                        minimumCombatLevel = 12,
                        minimumTotalLevel = 2_345,
                        minimumQuestPoints = 321,
                        ironRequirement = ClanGuestIronRequirement.UltimateIronOnly,
                    ),
                broadcastSettings = broadcasts,
            )
        val settings =
            ClanClientSettings.encode(clan).filterIsInstance<ClanSettingsFull.IntClanSetting>()
        val byId = settings.associate { it.id to it.value }

        assertEquals(17, byId.getValue(1) and 0x3FF)
        assertEquals(9, byId.getValue(2) and 0x3FF)
        assertEquals(ClanMotifCodec.encode(clan.motif), byId.getValue(11))
        assertEquals(clan.permissionSet[ClanPermissionKey.PermissionAccess] * 2, byId.getValue(12))
        assertEquals(clan.permissionSet[ClanPermissionKey.EditPublicProfile] * 2, byId.getValue(13))
        assertEquals(clan.permissionSet[ClanPermissionKey.EditRankTitles] * 2, byId.getValue(16))
        assertEquals(clan.permissionSet[ClanPermissionKey.Recruitment] * 2, byId.getValue(17))
        assertEquals(clan.permissionSet[ClanPermissionKey.BanGuests] * 2, byId.getValue(18))
        assertEquals(clan.permissionSet[ClanPermissionKey.OrganiseEvents] * 2, byId.getValue(21))
        assertEquals(clan.permissionSet[ClanPermissionKey.InitiateClanWars] * 2, byId.getValue(22))
        assertEquals(clan.permissionSet[ClanPermissionKey.EditMotif] * 2, byId.getValue(25))
        assertEquals(clan.permissionSet[ClanPermissionKey.HallPullLever] * 2, byId.getValue(95))
        assertEquals(
            12 or
                (2_345 shl 7) or
                (321 shl 19) or
                (ClanGuestIronRequirement.UltimateIronOnly.storageId shl 30),
            byId.getValue(26),
        )
        assertEquals(ClanRanks.ADMINISTRATOR * 2, byId.getValue(27))
        assertEquals(12_345, byId.getValue(31))
        assertEquals(ClanCombatAchievementTier.Master.storageId, byId.getValue(101))
        assertEquals(23_456, byId.getValue(65))
        assertEquals(40, byId.getValue(66))
        assertEquals(70, byId.getValue(67))
        assertEquals(90, byId.getValue(68))
        assertEquals(25, byId.getValue(69))
        assertEquals(34_567, byId.getValue(98))
        assertEquals(0, byId.getValue(110))
        assertEquals(25, byId.getValue(111))
        assertEquals(1, byId.getValue(112))
        assertTrue(
            listOf(27, 28, 29, 30, 32, 58, 59, 60, 61, 62, 63, 64, 97, 100, 105, 106, 107, 108, 109)
                .all(byId::containsKey)
        )
        assertTrue((1..5).all(byId::containsKey))
        assertTrue(listOf(102, 103, 104).all(byId::containsKey))
    }

    @Test
    fun `revision 240 coffer uses varclan 3 and clamps to protocol int`() {
        val packet = ClanClientVars.encodeCofferBalance(Long.MAX_VALUE)

        assertEquals(ClanClientVars.COFFER_BALANCE_ID, packet.id)
        assertEquals(Int.MAX_VALUE, (packet.value as VarClan.VarClanIntData).value)
    }
}
