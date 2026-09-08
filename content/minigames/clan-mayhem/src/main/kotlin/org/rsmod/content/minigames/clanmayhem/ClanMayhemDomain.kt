package org.rsmod.content.minigames.clanmayhem

import java.util.UUID

internal enum class ClanMayhemState {
    IDLE,
    REGISTRATION,
    ROSTER_LOCKED,
    PREPARING,
    ACTIVE,
    FINAL_CIRCLE,
    FINISHED,
    CLEANUP,
    CANCELLED,
}

internal enum class ClanMayhemParticipantState {
    REGISTERED,
    ARENA,
    RESPAWN_BOX,
    REWARD_ROOM,
    RESTORED,
}

internal data class ClanMayhemClanIdentity(
    val clanId: UUID,
    val displayName: String,
    val memberSinceEpochMillis: Long,
    val canManageRoster: Boolean,
)

internal data class ClanMayhemPersonalScore(
    var kills: Int = 0,
    var deaths: Int = 0,
    var assists: Int = 0,
    var enemyDamage: Int = 0,
    var bossDamage: Int = 0,
    var cratesClaimed: Int = 0,
    var keysOpened: Int = 0,
    var activityActions: Int = 0,
)

internal data class ClanMayhemTeamScore(
    var pvpKills: Int = 0,
    var bossObjective: Boolean = false,
    var enemyDamage: Int = 0,
    var deaths: Int = 0,
    var cratesClaimed: Int = 0,
    var keysOpened: Int = 0,
    var finalKillTick: Int? = null,
) {
    val matchPoints: Int
        get() =
            pvpKills * ClanMayhemConfig.pvpKillScore +
                if (bossObjective) ClanMayhemConfig.bossObjectiveScore else 0
}

internal data class ClanMayhemParticipant(
    val accountId: Int,
    val characterId: Int,
    val playerUid: Long,
    val joinedAtTick: Int,
    var ready: Boolean = false,
    var state: ClanMayhemParticipantState = ClanMayhemParticipantState.REGISTERED,
    var respawnReadyAtTick: Int? = null,
    var spawnProtectionUntilTick: Int = 0,
    var returnProtectionUntilTick: Int = 0,
    var pendingCratePackages: Int = 0,
    var connected: Boolean = true,
    var finalChestEligible: Boolean = false,
    var finalChestClaimed: Boolean = false,
    val score: ClanMayhemPersonalScore = ClanMayhemPersonalScore(),
)

internal data class ClanMayhemTeam(
    val matchTeamId: UUID,
    val clanId: UUID,
    val clanName: String,
    val roster: LinkedHashMap<Int, ClanMayhemParticipant> = linkedMapOf(),
    val score: ClanMayhemTeamScore = ClanMayhemTeamScore(),
) {
    fun hasExactRoster(format: ClanMayhemFormat): Boolean =
        roster.size == format.teamSize.players && roster.values.all { it.ready }
}

internal data class ClanMayhemAuditEvent(
    val matchId: UUID,
    val tick: Int,
    val type: String,
    val fields: Map<String, String>,
)

internal data class ClanMayhemFinalStanding(
    val placement: Int,
    val teamId: UUID,
    val clanId: UUID,
    val clanName: String,
    val score: ClanMayhemTeamScore,
)

internal sealed interface ClanMayhemWinnerResolution {
    data class Winner(val team: ClanMayhemTeam) : ClanMayhemWinnerResolution

    data class GenuineTie(val teams: List<ClanMayhemTeam>) : ClanMayhemWinnerResolution
}

internal class ClanMayhemResultGate {
    private var committed: Boolean = false

    fun commitOnce(action: () -> Unit): Boolean {
        if (committed) return false
        committed = true
        action()
        return true
    }

    fun isCommitted(): Boolean = committed
}
