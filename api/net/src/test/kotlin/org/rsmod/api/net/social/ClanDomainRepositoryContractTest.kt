package org.rsmod.api.net.social

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClanDomainRepositoryContractTest {
    @Test
    fun `repository exposes every advanced clan query and mutation`() {
        val methods = ClanDomainRepository::class.java.methods.mapTo(HashSet()) { it.name }
        assertTrue(
            methods.containsAll(
                setOf(
                    "renameClan",
                    "getPermissions",
                    "updatePermissions",
                    "getMotif",
                    "updateMotif",
                    "getRankTitles",
                    "updateRankTitles",
                    "listEvents",
                    "updateRankTitle",
                    "createEvent",
                    "updateEvent",
                    "deleteEvent",
                    "getHallSettings",
                    "updateHallSettings",
                    "getCoffer",
                    "depositCoffer",
                    "withdrawCoffer",
                    "listPartyChest",
                    "addPartyChestItem",
                    "removePartyChestItem",
                    "pullPartyChestLever",
                    "getDomainSettings",
                    "updateDomainSettings",
                    "withdrawApplication",
                    "getBroadcastSettings",
                    "updateBroadcastSettings",
                    "getMemberNotificationPreferences",
                    "updateMemberNotificationPreferences",
                    "listPendingRecruiterNotifications",
                    "markRecruiterNotificationsDelivered",
                    "declineInvite",
                    "computeSuccessor",
                    "requestSuccessorOwnership",
                    "finalizeSuccessorOwnership",
                )
            )
        )
    }

    @Test
    fun `service exposes the UI safe post commit facade`() {
        val methods = ClanDomainService::class.java.methods.mapTo(HashSet()) { it.name }
        assertTrue(
            methods.containsAll(
                setOf(
                    "renameClan",
                    "permissions",
                    "updatePermissions",
                    "motif",
                    "updateMotif",
                    "rankTitles",
                    "updateRankTitles",
                    "events",
                    "updateRankTitle",
                    "createEvent",
                    "updateEvent",
                    "deleteEvent",
                    "hallSettings",
                    "updateHallSettings",
                    "coffer",
                    "depositCoffer",
                    "withdrawCoffer",
                    "partyChest",
                    "addPartyChestItem",
                    "removePartyChestItem",
                    "pullPartyChestLever",
                    "domainSettings",
                    "updateDomainSettings",
                    "withdrawApplication",
                    "broadcastSettings",
                    "updateBroadcastSettings",
                    "notificationPreferences",
                    "updateNotificationPreferences",
                    "pendingRecruiterNotifications",
                    "markRecruiterNotificationsDelivered",
                    "declineInvite",
                    "successor",
                    "requestSuccessorOwnership",
                    "finalizeSuccessorOwnership",
                )
            )
        )
    }

    @Test
    fun `application submission persists recruiter notices only for opted in authorized ranks`() {
        val source =
            Files.readString(
                Path.of("src/main/kotlin/org/rsmod/api/net/social/ClanManagementRepository.kt")
            )
        assertTrue(source.contains("INSERT INTO clan_recruiter_notifications"))
        assertTrue(source.contains("p.recruiter_notifications_enabled"))
        assertTrue(source.contains("m.rank >= permission.minimum_rank"))
        assertTrue(source.contains("ClanPermissionKey.Recruitment.storageKey"))
        assertTrue(source.contains("if (!alreadyPending)"))
    }

    @Test
    fun `clan inserts require five founders and the test command cannot bypass creation`() {
        val migration =
            Files.readString(
                Path.of("src/main/resources/db/migration/V35__clan_founder_insert_guard.sql")
            )
        val service =
            Files.readString(Path.of("src/main/kotlin/org/rsmod/api/net/social/ClanChatService.kt"))
        assertTrue(migration.contains("CREATE CONSTRAINT TRIGGER clans_exact_founder_count"))
        assertTrue(migration.contains("AFTER INSERT ON clans DEFERRABLE INITIALLY DEFERRED"))
        assertTrue(migration.contains("PERFORM assert_clan_founder_count(NEW.id)"))
        assertFalse(service.contains("createOrRefreshTestClan("))
        assertFalse(
            Files.readString(
                    Path.of("src/main/kotlin/org/rsmod/api/net/social/ClanChatRepository.kt")
                )
                .contains("createTestClan(")
        )
        assertFalse(
            Files.exists(
                Path.of(
                    "../../content/other/commands/src/main/kotlin/org/rsmod/content/other/commands/SocialChatAdminCommands.kt"
                )
            )
        )
    }

    @Test
    fun `canonical permission writes update every live compatibility column atomically`() {
        val source =
            Files.readString(
                Path.of("src/main/kotlin/org/rsmod/api/net/social/ClanDomainRepository.kt")
            )
        assertTrue(source.contains("UPDATE clans SET talk_rank = ?, kick_rank = ?"))
        assertTrue(source.contains("permissions[ClanPermissionKey.TalkInChat]"))
        assertTrue(source.contains("permissions[ClanPermissionKey.Recruitment]"))
        assertTrue(source.contains("permissions[ClanPermissionKey.EditPublicProfile]"))
        assertTrue(source.contains("permissions[ClanPermissionKey.PermissionAccess]"))
    }

    @Test
    fun `login snapshot reports persisted recruiter notifications without consuming them`() {
        val source =
            Files.readString(Path.of("src/main/kotlin/org/rsmod/api/net/social/ClanChatService.kt"))
        assertTrue(source.contains("domain.listPendingRecruiterNotifications"))
        assertTrue(source.contains("pending recruiter application notification(s)"))
        assertFalse(source.contains("markRecruiterNotificationsDelivered(connection"))
    }

    @Test
    fun `sole owner deletion is delayed atomic and cannot delete a non-empty clan`() {
        val source =
            Files.readString(
                Path.of("src/main/kotlin/org/rsmod/api/net/social/ClanDomainRepository.kt")
            )
        assertTrue(source.contains("OWNER_RESIGNATION_DELAY_DAYS"))
        assertTrue(source.contains("memberCount(connection, actor.clanId) != 1"))
        assertTrue(source.contains("DELETE FROM clans WHERE id = ? AND owner_character_id = ?"))
        assertTrue(source.contains("ClanOwnershipFinalization(actor.clanId, deleted = true"))
    }

    @Test
    fun `clan names use the osrs twelve character normalized namespace and cooldown`() {
        assertEquals(
            ClanCanonicalName("Rune Clan", "rune clan"),
            ClanNameRules.canonicalize("  Rune__Clan  "),
        )
        assertEquals(ClanCanonicalName("123", "123"), ClanNameRules.canonicalize("123"))
        assertNull(ClanNameRules.canonicalize(""))
        assertNull(ClanNameRules.canonicalize("Rune-Clan"))
        assertNull(ClanNameRules.canonicalize("ThirteenChars"))
        assertEquals(Duration.ofDays(7), ClanNameRules.CHANGE_COOLDOWN)
    }

    @Test
    fun `coffer and motif values obey their canonical bounds`() {
        assertTrue(ClanCofferRules.canApply(0, 1))
        assertTrue(ClanCofferRules.canApply(1, -1))
        assertFalse(ClanCofferRules.canApply(0, -1))
        assertFalse(ClanCofferRules.canApply(ClanDomainLimits.MAX_COFFER_COINS, 1))

        val motif =
            ClanMotif(
                ClanMotifColour.Red,
                ClanMotifColour.Red,
                ClanMotifIcon.Swords,
                ClanMotifIcon.DragonMedHelm,
            )
        assertEquals(motif, ClanMotifCodec.decode(ClanMotifCodec.encode(motif)))
    }

    @Test
    fun `successor selection is rank activity and tenure deterministic`() {
        val now = 200L * ClanOwnershipRules.DAY_MILLIS
        val candidates =
            listOf(
                ClanSuccessionCandidate(3, ClanRanks.ADMINISTRATOR, 10, now),
                ClanSuccessionCandidate(2, ClanRanks.DEPUTY_OWNER, 20, now),
                ClanSuccessionCandidate(1, ClanRanks.DEPUTY_OWNER, 10, now),
                ClanSuccessionCandidate(
                    4,
                    ClanRanks.DEPUTY_OWNER,
                    1,
                    now - 91L * ClanOwnershipRules.DAY_MILLIS,
                ),
            )
        assertEquals(1, ClanOwnershipRules.selectSuccessor(candidates, now)?.characterId)
    }

    @Test
    fun `mutation envelope preserves refresh identity separately from payload`() {
        val mutation = ClanDomainMutation(clanId = 42L, value = "saved")
        assertEquals(42L, mutation.clanId)
        assertEquals("saved", mutation.value)
    }

    @Test
    fun `legacy management authority is locked and application profile is reloaded`() {
        val source =
            Path.of("src/main/kotlin/org/rsmod/api/net/social/ClanManagementRepository.kt")
                .readText()

        assertTrue(
            source.contains("val clanId = clans.findOwnClanId(connection, actorId) ?: return null")
        )
        assertTrue(source.contains("clans.lockClan(connection, clanId)"))
        assertTrue(source.contains("clans.loadClan(connection, selectedProfile.clanId)"))
    }

    @Test
    fun `legacy ownership APIs and automatic clerk finalizer are unreachable`() {
        val service =
            Path.of("src/main/kotlin/org/rsmod/api/net/social/ClanManagementService.kt").readText()
        val repository =
            Path.of("src/main/kotlin/org/rsmod/api/net/social/ClanManagementRepository.kt")
                .readText()
        val clerk =
            Path.of(
                    "../../content/interfaces/gameframe/src/main/kotlin/org/rsmod/content/interfaces/gameframe/script/ClanHubClerkScript.kt"
                )
                .readText()

        assertFalse(service.contains("requestOwnershipTransfer("))
        assertFalse(service.contains("finalizeOwnershipTransfer("))
        assertFalse(repository.contains("requestOwnershipTransfer("))
        assertFalse(repository.contains("finalizeOwnershipTransfer("))
        assertFalse(clerk.contains("management.finalizeOwnershipTransfer("))
        assertFalse(service.contains("permissions: ClanPermissionSettings"))
        assertFalse(repository.contains("permissions: ClanPermissionSettings"))
        assertFalse(
            Path.of("src/main/kotlin/org/rsmod/api/net/social/ClanManagementModels.kt")
                .readText()
                .contains("class ClanPermissionSettings")
        )
    }

    @Test
    fun `clan snapshot reads last seen fields from membership rows`() {
        val source =
            Path.of("src/main/kotlin/org/rsmod/api/net/social/ClanChatRepository.kt").readText()

        assertTrue(source.contains("m.last_seen_at, m.last_seen_visible"))
        assertFalse(source.contains("c.last_seen_at, c.last_seen_visible"))
    }

    @Test
    fun `clan creation side modal registers modal button handlers`() {
        val source =
            Path.of(
                    "../../content/interfaces/gameframe/src/main/kotlin/org/rsmod/content/interfaces/gameframe/script/ClanCreationNativeUi.kt"
                )
                .readText()

        assertEquals(6, Regex("""onIfModalButton\(ClanCreationUi\.""").findAll(source).count())
        assertFalse(source.contains("onIfOverlayButton(ClanCreationUi."))
        assertTrue(source.contains("access.player.enableCreationEvents()"))
        assertTrue(
            source.indexOf("setClanCreationClientState(access.player, true)") <
                source.indexOf("access.ifOpenSide(ClanCreationUi.INTERFACE)")
        )
        assertTrue(
            source.contains(
                "onIfClose(ClanCreationUi.INTERFACE) { setClanCreationClientState(player, false) }"
            )
        )

        val clientState =
            Path.of("src/main/kotlin/org/rsmod/api/net/social/ClanClientState.kt").readText()
        assertTrue(clientState.contains("OWNER_CREATION_STATE_VARBIT = 13_058"))

        val clerk =
            Path.of(
                    "../../content/interfaces/gameframe/src/main/kotlin/org/rsmod/content/interfaces/gameframe/script/ClanHubClerkScript.kt"
                )
                .readText()
        assertTrue(clerk.contains("management.ownProfile(player.characterId)"))
        assertTrue(
            clerk.indexOf("access.ifClose()") <
                clerk.indexOf("creationUi.open(access)")
        )
        assertFalse(clerk.contains("ifCloseSub(CLAN_MENU_INTERFACE)"))
    }

    @Test
    fun `nested clan dialogue buttons resume the waiting coroutine before modal lookup`() {
        val source =
            Path.of(
                    "src/main/kotlin/org/rsmod/api/net/rsprot/handlers/ResumePauseButtonHandler.kt"
                )
                .readText()
        val awaitedBranch =
            source.indexOf(
                "player.activeCoroutine?.isAwaiting(ResumePauseButtonInput::class) == true"
            )
        val modalBranch = source.indexOf("if (modal != null)")

        assertTrue(awaitedBranch >= 0)
        assertTrue(modalBranch > awaitedBranch)
        assertTrue(
            source.substring(awaitedBranch, modalBranch).contains(
                "player.resumeActiveCoroutine(input)"
            )
        )
    }

    @Test
    fun `event mutations lock and reject while applications are invited`() {
        val source =
            Path.of("src/main/kotlin/org/rsmod/api/net/social/ClanDomainRepository.kt").readText()

        assertEquals(
            3,
            Regex.escape("eventEditingRestriction(connection, actor.value.clanId)")
                .toRegex()
                .findAll(source)
                .count(),
        )
        assertTrue(
            source.contains(
                "SELECT recruitment_open, applications_open FROM clans WHERE id = ? FOR UPDATE"
            )
        )
        assertTrue(source.contains("Stop inviting applications before entering Event Edit Mode."))
    }
}
