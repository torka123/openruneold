package org.rsmod.api.net.social

import jakarta.inject.Inject
import java.time.Instant
import org.rsmod.api.db.DatabaseConnection

public enum class ClanModerationAction {
    KickGuest,
    BanGuest,
    MuteMember,
    UnmuteMember,
    ExpelMember,
}

public sealed interface ClanModerationResult {
    public data class Success(
        val clanId: Long,
        val target: SocialIdentity,
        val action: ClanModerationAction,
    ) : ClanModerationResult

    public data class Denied(val message: String) : ClanModerationResult
}

public class ClanModerationRepository
@Inject
constructor(private val clans: ClanChatRepository, private val identities: FriendChatRepository) {
    public fun kickOrExpel(
        connection: DatabaseConnection,
        actorId: Int,
        targetName: String,
        expiresAt: Instant,
        expectedGuest: Boolean,
    ): ClanModerationResult {
        val (clan, actor) =
            actorClan(connection, actorId)
                ?: return ClanModerationResult.Denied("You are not a member of a clan.")
        val target =
            identities.resolveIdentity(connection, targetName)
                ?: return ClanModerationResult.Denied("That player does not exist.")
        val targetMember = clan.member(target.characterId)
        if (targetMember == null) {
            if (!expectedGuest) {
                return ClanModerationResult.Denied("That player is not a member of your clan.")
            }
            if (!isGuestPresent(connection, clan.clanId, target.characterId)) {
                return ClanModerationResult.Denied("That player is not in this clan chat.")
            }
            if (!ClanPermissions.canKickGuest(actor.rank, clan.kickRank)) {
                return ClanModerationResult.Denied(
                    "You do not have permission to kick clan guests."
                )
            }
            connection
                .prepareStatement(
                    """
                INSERT INTO clan_guest_kicks (clan_id, character_id, expires_at)
                VALUES (?, ?, ?)
                ON CONFLICT (clan_id, character_id) DO UPDATE
                SET expires_at = EXCLUDED.expires_at
                """
                        .trimIndent()
                )
                .use { statement ->
                    statement.setLong(1, clan.clanId)
                    statement.setInt(2, target.characterId)
                    statement.setTimestamp(3, java.sql.Timestamp.from(expiresAt))
                    statement.executeUpdate()
                }
            clans.setGuestAffinity(connection, target.characterId, null)
            return ClanModerationResult.Success(clan.clanId, target, ClanModerationAction.KickGuest)
        }
        if (expectedGuest) {
            return ClanModerationResult.Denied(
                "Clan members must be expelled, not kicked as guests."
            )
        }
        if (!ClanPermissions.canExpelMember(actor.rank, targetMember.rank, clan.expelRank)) {
            return ClanModerationResult.Denied(
                "You do not have permission to expel that clan member."
            )
        }
        connection
            .prepareStatement("DELETE FROM clan_members WHERE clan_id = ? AND character_id = ?")
            .use { statement ->
                statement.setLong(1, clan.clanId)
                statement.setInt(2, target.characterId)
                statement.executeUpdate()
            }
        bumpVersion(connection, clan.clanId)
        return ClanModerationResult.Success(clan.clanId, target, ClanModerationAction.ExpelMember)
    }

    public fun kickGuest(
        connection: DatabaseConnection,
        actorId: Int,
        targetName: String,
        expiresAt: Instant,
    ): ClanModerationResult {
        val (clan, actor) =
            actorClan(connection, actorId)
                ?: return ClanModerationResult.Denied("You are not a member of a clan.")
        if (!ClanPermissions.canKickGuest(actor.rank, clan.kickRank)) {
            return ClanModerationResult.Denied("You do not have permission to kick clan guests.")
        }
        val target =
            identities.resolveIdentity(connection, targetName)
                ?: return ClanModerationResult.Denied("That player does not exist.")
        if (clan.member(target.characterId) != null) {
            return ClanModerationResult.Denied(
                "Clan members must be expelled, not kicked as guests."
            )
        }
        if (!isGuestPresent(connection, clan.clanId, target.characterId)) {
            return ClanModerationResult.Denied("That player is not in this clan chat.")
        }
        connection
            .prepareStatement(
                """
            INSERT INTO clan_guest_kicks (clan_id, character_id, expires_at)
            VALUES (?, ?, ?)
            ON CONFLICT (clan_id, character_id) DO UPDATE SET expires_at = EXCLUDED.expires_at
            """
                    .trimIndent()
            )
            .use { statement ->
                statement.setLong(1, clan.clanId)
                statement.setInt(2, target.characterId)
                statement.setTimestamp(3, java.sql.Timestamp.from(expiresAt))
                statement.executeUpdate()
            }
        clans.setGuestAffinity(connection, target.characterId, null)
        return ClanModerationResult.Success(clan.clanId, target, ClanModerationAction.KickGuest)
    }

    public fun banGuest(
        connection: DatabaseConnection,
        actorId: Int,
        targetName: String,
    ): ClanModerationResult {
        val (clan, actor) =
            actorClan(connection, actorId)
                ?: return ClanModerationResult.Denied("You are not a member of a clan.")
        if (!ClanPermissions.canBanGuest(actor.rank, clan.banRank)) {
            return ClanModerationResult.Denied("You do not have permission to ban clan guests.")
        }
        val target =
            identities.resolveIdentity(connection, targetName)
                ?: return ClanModerationResult.Denied("That player does not exist.")
        if (
            target.characterId == clan.ownerCharacterId || clan.member(target.characterId) != null
        ) {
            return ClanModerationResult.Denied(
                "A clan member cannot be added to the guest ban list."
            )
        }
        if (!isGuestPresent(connection, clan.clanId, target.characterId)) {
            return ClanModerationResult.Denied("That player is not in this clan chat.")
        }
        val banCount =
            connection.prepareStatement("SELECT COUNT(*) FROM clan_bans WHERE clan_id = ?").use {
                statement ->
                statement.setLong(1, clan.clanId)
                statement.executeQuery().use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
            }
        if (
            banCount >= ClanLimits.MAX_BANS &&
                clan.bans.none { it.characterId == target.characterId }
        ) {
            return ClanModerationResult.Denied("A clan may have at most 100 banned guests.")
        }
        connection
            .prepareStatement(
                """
            INSERT INTO clan_bans (clan_id, character_id, actor_character_id)
            VALUES (?, ?, ?)
            ON CONFLICT (clan_id, character_id) DO NOTHING
            """
                    .trimIndent()
            )
            .use { statement ->
                statement.setLong(1, clan.clanId)
                statement.setInt(2, target.characterId)
                statement.setInt(3, actorId)
                statement.executeUpdate()
            }
        clans.setGuestAffinity(connection, target.characterId, null)
        bumpVersion(connection, clan.clanId)
        return ClanModerationResult.Success(clan.clanId, target, ClanModerationAction.BanGuest)
    }

    public fun setMuted(
        connection: DatabaseConnection,
        actorId: Int,
        targetName: String,
        muted: Boolean,
    ): ClanModerationResult {
        val (clan, actor) =
            actorClan(connection, actorId)
                ?: return ClanModerationResult.Denied("You are not a member of a clan.")
        val target =
            identities.resolveIdentity(connection, targetName)
                ?: return ClanModerationResult.Denied("That player does not exist.")
        val member =
            clan.member(target.characterId)
                ?: return ClanModerationResult.Denied("That player is not a member of your clan.")
        if (!ClanPermissions.canMuteMember(actor.rank, member.rank)) {
            return ClanModerationResult.Denied("You do not have permission to mute that member.")
        }
        connection
            .prepareStatement(
                "UPDATE clan_members SET muted = ? WHERE clan_id = ? AND character_id = ?"
            )
            .use { statement ->
                statement.setBoolean(1, muted)
                statement.setLong(2, clan.clanId)
                statement.setInt(3, target.characterId)
                statement.executeUpdate()
            }
        bumpVersion(connection, clan.clanId)
        return ClanModerationResult.Success(
            clan.clanId,
            target,
            if (muted) {
                ClanModerationAction.MuteMember
            } else {
                ClanModerationAction.UnmuteMember
            },
        )
    }

    private fun actorClan(
        connection: DatabaseConnection,
        actorId: Int,
    ): Pair<ClanSnapshot, ClanMemberSnapshot>? {
        val clanId = clans.findOwnClanId(connection, actorId) ?: return null
        clans.lockClan(connection, clanId)
        val clan = clans.loadClan(connection, clanId) ?: return null
        val member = clan.member(actorId) ?: return null
        return clan to member
    }

    private fun isGuestPresent(
        connection: DatabaseConnection,
        clanId: Long,
        characterId: Int,
    ): Boolean =
        connection
            .prepareStatement(
                "SELECT 1 FROM social_chat_presence WHERE guest_clan_id = ? AND character_id = ?"
            )
            .use { statement ->
                statement.setLong(1, clanId)
                statement.setInt(2, characterId)
                statement.executeQuery().use { it.next() }
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
}
