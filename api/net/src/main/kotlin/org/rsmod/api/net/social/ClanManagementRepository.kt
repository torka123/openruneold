package org.rsmod.api.net.social

import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Instant
import org.rsmod.api.db.DatabaseConnection

public class ClanManagementRepository
@Inject
constructor(private val clans: ClanChatRepository, private val identities: FriendChatRepository) {
    public fun canFoundClan(
        connection: DatabaseConnection,
        characterId: Int,
        requestedName: String? = null,
    ): ClanManagementResult<Unit> {
        val runeDay = currentRuneDay()
        if (identities.resolveIdentityById(connection, characterId) == null) {
            return ClanManagementResult.Denied("That player does not exist.")
        }
        if (clans.findOwnClanId(connection, characterId) != null) {
            return ClanManagementResult.Denied("A ranked clan member cannot found another clan.")
        }
        if (createdClanOnDay(connection, characterId, runeDay)) {
            return ClanManagementResult.Denied(
                "That player has already helped create a clan today."
            )
        }
        if (requestedName != null) {
            val display = SocialNameNormalizer.channelName(requestedName)
            if (display.isBlank()) {
                return ClanManagementResult.Denied("Clan names must contain letters or numbers.")
            }
            if (clans.findClanByName(connection, display) != null) {
                return ClanManagementResult.Denied("That clan name is already in use.")
            }
        }
        return ClanManagementResult.Success(Unit)
    }

    public fun createClan(
        connection: DatabaseConnection,
        request: ClanCreationRequest,
    ): ClanManagementResult<ClanSnapshot> {
        ClanCreationRules.validate(request)?.let {
            return ClanManagementResult.Denied(it)
        }
        val orderedFounders =
            request.founders.sortedWith(
                compareByDescending<ClanFounder> { it.characterId == request.ownerCharacterId }
                    .thenBy { SocialNameNormalizer.normalize(it.displayName) }
            )
        val ids = orderedFounders.map { it.characterId }
        if (lockCharacters(connection, ids) != ids.size) {
            return ClanManagementResult.Denied("Every founder must have a valid character.")
        }
        val runeDay = currentRuneDay()
        for (founder in orderedFounders) {
            when (val eligibility = canFoundClan(connection, founder.characterId)) {
                is ClanManagementResult.Denied -> return eligibility
                is ClanManagementResult.Success -> Unit
            }
        }
        val display = SocialNameNormalizer.channelName(request.requestedName)
        val normalized = SocialNameNormalizer.normalize(display)
        val clanId =
            connection
                .prepareStatement(
                    """
                    INSERT INTO clans
                        (normalized_name, display_name, owner_character_id, creation_rune_day, home_world)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT DO NOTHING
                    RETURNING id
                    """
                        .trimIndent()
                )
                .use { statement ->
                    statement.setString(1, normalized)
                    statement.setString(2, display)
                    statement.setInt(3, request.ownerCharacterId)
                    statement.setInt(4, runeDay)
                    statement.setInt(5, request.homeWorld)
                    statement.executeQuery().use { rows ->
                        if (rows.next()) rows.getLong(1) else null
                    }
                } ?: return ClanManagementResult.Denied("That clan name is already in use.")
        connection
            .prepareStatement(
                """
                INSERT INTO clan_members
                    (clan_id, character_id, rank, title_slot, joined_rune_day)
                VALUES (?, ?, ?, 0, ?)
                """
                    .trimIndent()
            )
            .use { statement ->
                for (founder in orderedFounders) {
                    statement.setLong(1, clanId)
                    statement.setInt(2, founder.characterId)
                    statement.setInt(
                        3,
                        if (founder.characterId == request.ownerCharacterId) ClanRanks.OWNER
                        else ClanRanks.MEMBER,
                    )
                    statement.setInt(4, runeDay)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        connection
            .prepareStatement(
                """
                INSERT INTO clan_founders
                    (clan_id, character_id, display_name_at_creation, founder_order)
                VALUES (?, ?, ?, ?)
                """
                    .trimIndent()
            )
            .use { statement ->
                orderedFounders.forEachIndexed { index, founder ->
                    statement.setLong(1, clanId)
                    statement.setInt(2, founder.characterId)
                    statement.setString(3, founder.displayName.take(12))
                    statement.setInt(4, index)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        connection
            .prepareStatement(
                "INSERT INTO clan_creation_history (character_id, creation_rune_day, clan_id) VALUES (?, ?, ?)"
            )
            .use { statement ->
                for (founder in orderedFounders) {
                    statement.setInt(1, founder.characterId)
                    statement.setInt(2, runeDay)
                    statement.setLong(3, clanId)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        val clan = checkNotNull(clans.loadClan(connection, clanId))
        return ClanManagementResult.Success(clan)
    }

    public fun searchDirectory(
        connection: DatabaseConnection,
        query: String,
    ): List<ClanDirectoryEntry> {
        val needle = "%${query.trim().take(30).replace('%', ' ')}%"
        val entries = ArrayList<ClanDirectoryEntry>()
        connection
            .prepareStatement(
                """
                SELECT c.id, c.display_name, owner.display_name AS owner_name,
                       COUNT(m.character_id) AS member_count, c.recruitment_open, c.description
                FROM clans c
                JOIN account_characters owner ON owner.id = c.owner_character_id
                LEFT JOIN clan_members m ON m.clan_id = c.id
                WHERE c.display_name ILIKE ? OR owner.display_name ILIKE ?
                GROUP BY c.id, owner.display_name
                ORDER BY c.recruitment_open DESC, LOWER(c.display_name)
                LIMIT 20
                """
                    .trimIndent()
            )
            .use { statement ->
                statement.setString(1, needle)
                statement.setString(2, needle)
                statement.executeQuery().use { rows ->
                    while (rows.next()) {
                        entries +=
                            ClanDirectoryEntry(
                                clanId = rows.getLong("id"),
                                displayName = rows.getString("display_name"),
                                ownerDisplayName = rows.getString("owner_name"),
                                memberCount = rows.getInt("member_count"),
                                recruitmentOpen = rows.getBoolean("recruitment_open"),
                                description = rows.getString("description") ?: "",
                            )
                    }
                }
            }
        return entries
    }

    public fun loadProfile(connection: DatabaseConnection, requestedName: String): ClanProfile? {
        val clan = clans.findClanByName(connection, requestedName) ?: return null
        return loadProfile(connection, clan)
    }

    public fun loadOwnProfile(connection: DatabaseConnection, characterId: Int): ClanProfile? {
        val clanId = clans.findOwnClanId(connection, characterId) ?: return null
        val clan = clans.loadClan(connection, clanId) ?: return null
        return loadProfile(connection, clan)
    }

    public fun submitApplication(
        connection: DatabaseConnection,
        characterId: Int,
        clanName: String,
        message: String,
        combatLevel: Int,
        totalLevel: Int,
        questPoints: Int,
        isIron: Boolean,
        isUltimateIron: Boolean,
        isHardcoreIron: Boolean,
        skillLevels: List<Int>,
    ): ClanManagementResult<ClanProfile> {
        if (skillLevels.size != 24 || skillLevels.any { it !in 0..255 }) {
            return ClanManagementResult.Denied("The applicant skill snapshot is not valid.")
        }
        if (clans.findOwnClanId(connection, characterId) != null) {
            return ClanManagementResult.Denied("You may only be a ranked member of one clan.")
        }
        val selectedProfile =
            loadProfile(connection, clanName)
                ?: return ClanManagementResult.Denied("That clan does not exist.")
        clans.lockClan(connection, selectedProfile.clanId)
        val profile =
            clans.loadClan(connection, selectedProfile.clanId)?.let { loadProfile(connection, it) }
                ?: return ClanManagementResult.Denied("That clan no longer exists.")
        if (!profile.recruitmentOpen || !profile.applicationsOpen) {
            return ClanManagementResult.Denied("That clan is not accepting applications.")
        }
        if (profile.memberCount >= ClanLimits.MAX_MEMBERS) {
            return ClanManagementResult.Denied("That clan already has 500 members.")
        }
        if (combatLevel < profile.minimumCombatLevel || totalLevel < profile.minimumTotalLevel) {
            return ClanManagementResult.Denied(
                "You do not meet that clan's recruitment requirements."
            )
        }
        if (isBanned(connection, profile.clanId, characterId)) {
            return ClanManagementResult.Denied("You are banned from that clan.")
        }
        val alreadyPending =
            connection
                .prepareStatement(
                    "SELECT 1 FROM clan_applications WHERE clan_id = ? AND character_id = ?"
                )
                .use { statement ->
                    statement.setLong(1, profile.clanId)
                    statement.setInt(2, characterId)
                    statement.executeQuery().use { it.next() }
                }
        connection
            .prepareStatement(
                """
                INSERT INTO clan_applications
                    (clan_id, character_id, message, combat_level, total_level, quest_points,
                     is_iron, is_ultimate_iron, is_hardcore_iron, skill_levels)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (clan_id, character_id) DO UPDATE
                SET message = EXCLUDED.message, combat_level = EXCLUDED.combat_level,
                    total_level = EXCLUDED.total_level, quest_points = EXCLUDED.quest_points,
                    is_iron = EXCLUDED.is_iron, is_ultimate_iron = EXCLUDED.is_ultimate_iron,
                    is_hardcore_iron = EXCLUDED.is_hardcore_iron,
                    skill_levels = EXCLUDED.skill_levels, created_at = CURRENT_TIMESTAMP
                """
                    .trimIndent()
            )
            .use { statement ->
                statement.setLong(1, profile.clanId)
                statement.setInt(2, characterId)
                statement.setString(3, SocialChatText.normalize(message).orEmpty())
                statement.setInt(4, combatLevel)
                statement.setInt(5, totalLevel)
                statement.setInt(6, questPoints)
                statement.setBoolean(7, isIron)
                statement.setBoolean(8, isUltimateIron)
                statement.setBoolean(9, isHardcoreIron)
                statement.setString(10, skillLevels.joinToString(","))
                statement.executeUpdate()
            }
        if (!alreadyPending) {
            notifyRecruiters(connection, profile.clanId, characterId)
        }
        bumpVersion(connection, profile.clanId)
        val updatedClan = checkNotNull(clans.loadClan(connection, profile.clanId))
        return ClanManagementResult.Success(loadProfile(connection, updatedClan))
    }

    public fun listApplications(
        connection: DatabaseConnection,
        actorId: Int,
    ): ClanManagementResult<List<ClanApplication>> {
        val actor =
            actorClan(connection, actorId)
                ?: return ClanManagementResult.Denied("You are not a member of a clan.")
        if (actor.rank < actor.applicationReviewRank) {
            return ClanManagementResult.Denied("You do not have permission to review applications.")
        }
        val applications = ArrayList<ClanApplication>()
        connection
            .prepareStatement(
                """
                SELECT c.id, c.display_name, a.message, a.combat_level, a.total_level,
                       a.quest_points, a.is_iron, a.is_ultimate_iron, a.is_hardcore_iron,
                       a.skill_levels
                FROM clan_applications a
                JOIN account_characters c ON c.id = a.character_id
                WHERE a.clan_id = ?
                ORDER BY a.created_at, LOWER(c.display_name)
                """
                    .trimIndent()
            )
            .use { statement ->
                statement.setLong(1, actor.clanId)
                statement.executeQuery().use { rows ->
                    while (rows.next()) {
                        applications +=
                            ClanApplication(
                                rows.getInt("id"),
                                rows.getString("display_name"),
                                rows.getString("message") ?: "",
                                rows.getNullableInt("combat_level"),
                                rows.getNullableInt("total_level"),
                                rows.getNullableInt("quest_points"),
                                rows.getNullableBoolean("is_iron"),
                                rows.getNullableBoolean("is_ultimate_iron"),
                                rows.getNullableBoolean("is_hardcore_iron"),
                                applicantSkillLevels(rows.getString("skill_levels")),
                            )
                    }
                }
            }
        return ClanManagementResult.Success(applications)
    }

    public fun reviewApplication(
        connection: DatabaseConnection,
        actorId: Int,
        targetName: String,
        approve: Boolean,
    ): ClanManagementResult<ClanSnapshot> {
        val actor =
            actorClan(connection, actorId)
                ?: return ClanManagementResult.Denied("You are not a member of a clan.")
        clans.lockClan(connection, actor.clanId)
        if (actor.rank < actor.applicationReviewRank) {
            return ClanManagementResult.Denied("You do not have permission to review applications.")
        }
        val target =
            identities.resolveIdentity(connection, targetName)
                ?: return ClanManagementResult.Denied("That player does not exist.")
        val applicationExists =
            connection
                .prepareStatement(
                    "SELECT 1 FROM clan_applications WHERE clan_id = ? AND character_id = ?"
                )
                .use { statement ->
                    statement.setLong(1, actor.clanId)
                    statement.setInt(2, target.characterId)
                    statement.executeQuery().use { it.next() }
                }
        if (!applicationExists) {
            return ClanManagementResult.Denied("That player has not applied to your clan.")
        }
        if (approve) {
            if (clans.findOwnClanId(connection, target.characterId) != null) {
                return ClanManagementResult.Denied("That player is already a ranked clan member.")
            }
            if (memberCount(connection, actor.clanId) >= ClanLimits.MAX_MEMBERS) {
                return ClanManagementResult.Denied("Your clan already has 500 members.")
            }
            connection
                .prepareStatement(
                    "INSERT INTO clan_members (clan_id, character_id, rank, title_slot, joined_rune_day) VALUES (?, ?, 0, 0, ?)"
                )
                .use { statement ->
                    statement.setLong(1, actor.clanId)
                    statement.setInt(2, target.characterId)
                    statement.setInt(3, currentRuneDay())
                    statement.executeUpdate()
                }
        }
        connection
            .prepareStatement(
                "DELETE FROM clan_applications WHERE clan_id = ? AND character_id = ?"
            )
            .use { statement ->
                statement.setLong(1, actor.clanId)
                statement.setInt(2, target.characterId)
                statement.executeUpdate()
            }
        bumpVersion(connection, actor.clanId)
        return ClanManagementResult.Success(checkNotNull(clans.loadClan(connection, actor.clanId)))
    }

    public fun inviteMember(
        connection: DatabaseConnection,
        actorId: Int,
        targetName: String,
        expiresAt: Instant,
    ): ClanManagementResult<ClanInvite> {
        val actor =
            actorClan(connection, actorId)
                ?: return ClanManagementResult.Denied("You are not a member of a clan.")
        if (actor.rank < actor.inviteRank) {
            return ClanManagementResult.Denied("You do not have permission to invite members.")
        }
        val target =
            identities.resolveIdentity(connection, targetName)
                ?: return ClanManagementResult.Denied("That player does not exist.")
        if (clans.findOwnClanId(connection, target.characterId) != null) {
            return ClanManagementResult.Denied("That player is already a ranked clan member.")
        }
        if (isBanned(connection, actor.clanId, target.characterId)) {
            return ClanManagementResult.Denied("That player is banned from your clan.")
        }
        if (memberCount(connection, actor.clanId) >= ClanLimits.MAX_MEMBERS) {
            return ClanManagementResult.Denied("Your clan already has 500 members.")
        }
        connection
            .prepareStatement(
                """
            INSERT INTO clan_invites (clan_id, character_id, actor_character_id, expires_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (clan_id, character_id) DO UPDATE
            SET actor_character_id = EXCLUDED.actor_character_id,
                expires_at = EXCLUDED.expires_at,
                created_at = CURRENT_TIMESTAMP
            """
                    .trimIndent()
            )
            .use { statement ->
                statement.setLong(1, actor.clanId)
                statement.setInt(2, target.characterId)
                statement.setInt(3, actorId)
                statement.setTimestamp(4, Timestamp.from(expiresAt))
                statement.executeUpdate()
            }
        return ClanManagementResult.Success(
            ClanInvite(actor.clanId, actor.clanName, actor.displayName)
        )
    }

    public fun pendingInvites(connection: DatabaseConnection, characterId: Int): List<ClanInvite> {
        clearExpiredInvites(connection)
        val invites = ArrayList<ClanInvite>()
        connection
            .prepareStatement(
                """
            SELECT c.id, c.display_name, actor.display_name AS inviter_name
            FROM clan_invites i
            JOIN clans c ON c.id = i.clan_id
            JOIN account_characters actor ON actor.id = i.actor_character_id
            WHERE i.character_id = ?
            ORDER BY i.created_at DESC
            """
                    .trimIndent()
            )
            .use { statement ->
                statement.setInt(1, characterId)
                statement.executeQuery().use { rows ->
                    while (rows.next()) {
                        invites +=
                            ClanInvite(
                                rows.getLong("id"),
                                rows.getString("display_name"),
                                rows.getString("inviter_name"),
                            )
                    }
                }
            }
        return invites
    }

    public fun acceptInvite(
        connection: DatabaseConnection,
        characterId: Int,
        clanName: String,
    ): ClanManagementResult<ClanSnapshot> {
        clearExpiredInvites(connection)
        if (clans.findOwnClanId(connection, characterId) != null) {
            return ClanManagementResult.Denied("You may only be a ranked member of one clan.")
        }
        val clan =
            clans.findClanByName(connection, clanName)
                ?: return ClanManagementResult.Denied("That clan does not exist.")
        clans.lockClan(connection, clan.clanId)
        val invited =
            connection
                .prepareStatement(
                    "SELECT 1 FROM clan_invites WHERE clan_id = ? AND character_id = ? AND expires_at > CURRENT_TIMESTAMP"
                )
                .use { statement ->
                    statement.setLong(1, clan.clanId)
                    statement.setInt(2, characterId)
                    statement.executeQuery().use { it.next() }
                }
        if (!invited) return ClanManagementResult.Denied("You do not have an active invitation.")
        if (isBanned(connection, clan.clanId, characterId)) {
            return ClanManagementResult.Denied("You are banned from that clan.")
        }
        if (memberCount(connection, clan.clanId) >= ClanLimits.MAX_MEMBERS) {
            return ClanManagementResult.Denied("That clan already has 500 members.")
        }
        connection
            .prepareStatement(
                "INSERT INTO clan_members (clan_id, character_id, rank, title_slot, joined_rune_day) VALUES (?, ?, 0, 0, ?)"
            )
            .use { statement ->
                statement.setLong(1, clan.clanId)
                statement.setInt(2, characterId)
                statement.setInt(3, currentRuneDay())
                statement.executeUpdate()
            }
        connection.prepareStatement("DELETE FROM clan_invites WHERE character_id = ?").use {
            it.setInt(1, characterId)
            it.executeUpdate()
        }
        connection.prepareStatement("DELETE FROM clan_applications WHERE character_id = ?").use {
            it.setInt(1, characterId)
            it.executeUpdate()
        }
        bumpVersion(connection, clan.clanId)
        return ClanManagementResult.Success(checkNotNull(clans.loadClan(connection, clan.clanId)))
    }

    public fun setMemberRank(
        connection: DatabaseConnection,
        actorId: Int,
        targetName: String,
        newRank: Int,
    ): ClanManagementResult<ClanSnapshot> {
        val actor =
            actorClan(connection, actorId)
                ?: return ClanManagementResult.Denied("You are not a member of a clan.")
        clans.lockClan(connection, actor.clanId)
        if (actor.rank < actor.assignRank) {
            return ClanManagementResult.Denied("You do not have permission to assign ranks.")
        }
        val target =
            identities.resolveIdentity(connection, targetName)
                ?: return ClanManagementResult.Denied("That player does not exist.")
        val snapshot = clans.loadClan(connection, actor.clanId)!!
        val targetMember =
            snapshot.member(target.characterId)
                ?: return ClanManagementResult.Denied("That player is not a member of your clan.")
        val rankAllowed =
            ClanRanks.validAssignable(newRank) ||
                (newRank == ClanRanks.DEPUTY_OWNER && actor.rank == ClanRanks.OWNER)
        if (
            !rankAllowed ||
                !ClanRanks.canActOn(actor.rank, targetMember.rank) ||
                newRank >= actor.rank
        ) {
            return ClanManagementResult.Denied("You cannot assign that rank to this member.")
        }
        connection
            .prepareStatement(
                "UPDATE clan_members SET rank = ?, title_slot = ? WHERE clan_id = ? AND character_id = ?"
            )
            .use { statement ->
                statement.setInt(1, newRank)
                statement.setInt(2, newRank.coerceIn(0, 14))
                statement.setLong(3, actor.clanId)
                statement.setInt(4, target.characterId)
                statement.executeUpdate()
            }
        bumpVersion(connection, actor.clanId)
        return ClanManagementResult.Success(checkNotNull(clans.loadClan(connection, actor.clanId)))
    }

    public fun updateProfile(
        connection: DatabaseConnection,
        actorId: Int,
        description: String,
        recruitmentOpen: Boolean,
        applicationsOpen: Boolean,
        allowGuests: Boolean,
        guestsCanTalk: Boolean,
        minimumCombatLevel: Int,
        minimumTotalLevel: Int,
    ): ClanManagementResult<ClanProfile> {
        val actor =
            actorClan(connection, actorId)
                ?: return ClanManagementResult.Denied("You are not a member of a clan.")
        if (actor.rank < actor.settingsChangeRank) {
            return ClanManagementResult.Denied(
                "You do not have permission to change clan settings."
            )
        }
        if (minimumCombatLevel !in 0..ClanLimits.MAX_RECRUITMENT_COMBAT_LEVEL) {
            return ClanManagementResult.Denied(
                "The minimum combat level must be between 0 and 126."
            )
        }
        if (minimumTotalLevel !in 0..ClanLimits.MAX_RECRUITMENT_TOTAL_LEVEL) {
            return ClanManagementResult.Denied(
                "The minimum total level must be between 0 and 2500."
            )
        }
        connection
            .prepareStatement(
                """
            UPDATE clans SET description = ?, recruitment_open = ?, applications_open = ?,
                allow_guests = ?, guests_can_talk = ?, minimum_combat_level = ?,
                minimum_total_level = ?,
                state_version = state_version + 1, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """
                    .trimIndent()
            )
            .use { statement ->
                statement.setString(1, description.trim().take(ClanLimits.MAX_DESCRIPTION_LENGTH))
                statement.setBoolean(2, recruitmentOpen)
                statement.setBoolean(3, applicationsOpen)
                statement.setBoolean(4, allowGuests)
                statement.setBoolean(5, allowGuests && guestsCanTalk)
                statement.setInt(6, minimumCombatLevel)
                statement.setInt(7, minimumTotalLevel)
                statement.setLong(8, actor.clanId)
                statement.executeUpdate()
            }
        return ClanManagementResult.Success(checkNotNull(loadOwnProfile(connection, actorId)))
    }

    public fun leaveClan(
        connection: DatabaseConnection,
        characterId: Int,
    ): ClanManagementResult<Long> {
        val actor =
            actorClan(connection, characterId)
                ?: return ClanManagementResult.Denied("You are not a member of a clan.")
        if (actor.rank == ClanRanks.OWNER) {
            return ClanManagementResult.Denied("Transfer ownership before leaving your clan.")
        }
        connection
            .prepareStatement("DELETE FROM clan_members WHERE clan_id = ? AND character_id = ?")
            .use { statement ->
                statement.setLong(1, actor.clanId)
                statement.setInt(2, characterId)
                statement.executeUpdate()
            }
        bumpVersion(connection, actor.clanId)
        return ClanManagementResult.Success(actor.clanId)
    }

    public fun unban(
        connection: DatabaseConnection,
        actorId: Int,
        targetName: String,
    ): ClanManagementResult<ClanSnapshot> {
        val actor =
            actorClan(connection, actorId)
                ?: return ClanManagementResult.Denied("You are not a member of a clan.")
        if (actor.rank < actor.banRank) {
            return ClanManagementResult.Denied("You do not have permission to change the ban list.")
        }
        val target =
            identities.resolveIdentity(connection, targetName)
                ?: return ClanManagementResult.Denied("That player does not exist.")
        connection
            .prepareStatement("DELETE FROM clan_bans WHERE clan_id = ? AND character_id = ?")
            .use { statement ->
                statement.setLong(1, actor.clanId)
                statement.setInt(2, target.characterId)
                statement.executeUpdate()
            }
        bumpVersion(connection, actor.clanId)
        return ClanManagementResult.Success(checkNotNull(clans.loadClan(connection, actor.clanId)))
    }

    private fun loadProfile(connection: DatabaseConnection, clan: ClanSnapshot): ClanProfile =
        connection
            .prepareStatement(
                """
            SELECT owner.display_name AS owner_name, c.description, c.recruitment_open,
                   c.applications_open, c.minimum_combat_level, c.minimum_total_level,
                   (SELECT COUNT(*) FROM clan_members m WHERE m.clan_id = c.id) AS member_count
            FROM clans c
            JOIN account_characters owner ON owner.id = c.owner_character_id
            WHERE c.id = ?
            """
                    .trimIndent()
            )
            .use { statement ->
                statement.setLong(1, clan.clanId)
                statement.executeQuery().use { rows ->
                    check(rows.next())
                    ClanProfile(
                        clanId = clan.clanId,
                        displayName = clan.displayName,
                        ownerDisplayName = rows.getString("owner_name"),
                        memberCount = rows.getInt("member_count"),
                        recruitmentOpen = rows.getBoolean("recruitment_open"),
                        applicationsOpen = rows.getBoolean("applications_open"),
                        minimumCombatLevel = rows.getInt("minimum_combat_level"),
                        minimumTotalLevel = rows.getInt("minimum_total_level"),
                        description = rows.getString("description") ?: "",
                        members = clan.members,
                        creationRuneDay = clan.creationRuneDay,
                        homeWorld = clan.homeWorld,
                        allowGuests = clan.allowGuests,
                        guestsCanTalk = clan.guestsCanTalk,
                        interests = clan.interests,
                        permissionSet = clan.permissionSet,
                        motif = clan.motif,
                        guestRequirements = clan.guestRequirements,
                        hallSettings = clan.hallSettings,
                        cofferBalance = clan.cofferBalance,
                        broadcastSettings = clan.broadcastSettings,
                    )
                }
            }

    private fun actorClan(connection: DatabaseConnection, actorId: Int): ActorClan? {
        val clanId = clans.findOwnClanId(connection, actorId) ?: return null
        clans.lockClan(connection, clanId)
        return connection
            .prepareStatement(
                """
            SELECT c.display_name, m.rank, c.invite_rank, c.application_review_rank,
                   c.settings_change_rank, c.assign_rank, c.ban_rank
            FROM clans c JOIN clan_members m ON m.clan_id = c.id
            WHERE c.id = ? AND m.character_id = ?
            """
                    .trimIndent()
            )
            .use { statement ->
                statement.setLong(1, clanId)
                statement.setInt(2, actorId)
                statement.executeQuery().use { rows ->
                    if (!rows.next()) return@use null
                    ActorClan(
                        clanId,
                        rows.getString("display_name"),
                        identities.resolveIdentityById(connection, actorId)?.displayName.orEmpty(),
                        rows.getInt("rank"),
                        rows.getInt("invite_rank"),
                        rows.getInt("application_review_rank"),
                        rows.getInt("settings_change_rank"),
                        rows.getInt("assign_rank"),
                        rows.getInt("ban_rank"),
                    )
                }
            }
    }

    private fun applicantSkillLevels(value: String?): List<Int?> {
        val stored = value?.split(',').orEmpty()
        return List(24) { index -> stored.getOrNull(index)?.toIntOrNull()?.takeIf { it in 0..255 } }
    }

    private fun java.sql.ResultSet.getNullableBoolean(column: String): Boolean? {
        val value = getBoolean(column)
        return if (wasNull()) null else value
    }

    private fun java.sql.ResultSet.getNullableInt(column: String): Int? {
        val value = getInt(column)
        return if (wasNull()) null else value
    }

    private fun lockCharacters(connection: DatabaseConnection, ids: List<Int>): Int {
        val placeholders = ids.joinToString(",") { "?" }
        return connection
            .prepareStatement(
                "SELECT id FROM account_characters WHERE id IN ($placeholders) ORDER BY id FOR UPDATE"
            )
            .use { statement ->
                ids.forEachIndexed { index, id -> statement.setInt(index + 1, id) }
                statement.executeQuery().use { rows ->
                    var count = 0
                    while (rows.next()) count++
                    count
                }
            }
    }

    private fun memberCount(connection: DatabaseConnection, clanId: Long): Int =
        connection.prepareStatement("SELECT COUNT(*) FROM clan_members WHERE clan_id = ?").use {
            statement ->
            statement.setLong(1, clanId)
            statement.executeQuery().use { rows ->
                rows.next()
                rows.getInt(1)
            }
        }

    private fun isBanned(connection: DatabaseConnection, clanId: Long, characterId: Int): Boolean =
        connection
            .prepareStatement("SELECT 1 FROM clan_bans WHERE clan_id = ? AND character_id = ?")
            .use { statement ->
                statement.setLong(1, clanId)
                statement.setInt(2, characterId)
                statement.executeQuery().use { it.next() }
            }

    private fun createdClanOnDay(
        connection: DatabaseConnection,
        characterId: Int,
        runeDay: Int,
    ): Boolean =
        connection
            .prepareStatement(
                "SELECT 1 FROM clan_creation_history WHERE character_id = ? AND creation_rune_day = ?"
            )
            .use { statement ->
                statement.setInt(1, characterId)
                statement.setInt(2, runeDay)
                statement.executeQuery().use { it.next() }
            }

    private fun clearExpiredInvites(connection: DatabaseConnection) {
        connection
            .prepareStatement("DELETE FROM clan_invites WHERE expires_at <= CURRENT_TIMESTAMP")
            .use { it.executeUpdate() }
    }

    private fun bumpVersion(connection: DatabaseConnection, clanId: Long) {
        connection
            .prepareStatement(
                "UPDATE clans SET state_version = state_version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?"
            )
            .use { statement ->
                statement.setLong(1, clanId)
                statement.executeUpdate()
            }
    }

    private fun notifyRecruiters(connection: DatabaseConnection, clanId: Long, applicantId: Int) {
        connection
            .prepareStatement(
                """
            INSERT INTO clan_recruiter_notifications
                (clan_id, recipient_character_id, applicant_character_id, notification_kind)
            SELECT m.clan_id, m.character_id, ?, ?
            FROM clan_members m
            JOIN clan_member_notification_preferences p
              ON p.clan_id = m.clan_id AND p.character_id = m.character_id
            JOIN clan_permission_settings permission
              ON permission.clan_id = m.clan_id AND permission.permission_key = ?
            WHERE m.clan_id = ? AND m.rank >= permission.minimum_rank
              AND p.recruiter_notifications_enabled
            """
                    .trimIndent()
            )
            .use {
                it.setInt(1, applicantId)
                it.setInt(2, ClanRecruiterNotificationKind.ApplicationSubmitted.storageId)
                it.setString(3, ClanPermissionKey.Recruitment.storageKey)
                it.setLong(4, clanId)
                it.executeUpdate()
            }
    }

    private fun currentRuneDay(): Int = (Instant.now().epochSecond / 86_400L).toInt()

    private data class ActorClan(
        val clanId: Long,
        val clanName: String,
        val displayName: String,
        val rank: Int,
        val inviteRank: Int,
        val applicationReviewRank: Int,
        val settingsChangeRank: Int,
        val assignRank: Int,
        val banRank: Int,
    )
}

private fun FriendChatRepository.resolveIdentityById(
    connection: DatabaseConnection,
    characterId: Int,
): SocialIdentity? =
    connection
        .prepareStatement(
            "SELECT id, display_name, previous_display_name FROM account_characters WHERE id = ?"
        )
        .use { statement ->
            statement.setInt(1, characterId)
            statement.executeQuery().use { rows ->
                if (!rows.next()) null
                else
                    SocialIdentity(
                        rows.getInt("id"),
                        rows.getString("display_name") ?: "",
                        rows.getString("previous_display_name") ?: "",
                    )
            }
        }
