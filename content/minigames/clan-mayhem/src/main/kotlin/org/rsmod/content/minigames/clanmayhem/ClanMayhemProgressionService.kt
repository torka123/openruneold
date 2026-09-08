package org.rsmod.content.minigames.clanmayhem

import jakarta.inject.Singleton
import java.util.UUID
import org.rsmod.api.attr.AttributeKey
import org.rsmod.game.entity.Player

internal val MAYHEM_PERSONAL_STATS: AttributeKey<MutableList<Int>> =
    AttributeKey(persistenceKey = "clan_mayhem_personal_stats_v1")
internal val MAYHEM_LAST_STATS_MATCH: AttributeKey<MutableList<Int>> =
    AttributeKey(persistenceKey = "clan_mayhem_last_stats_match_v1")

internal data class ClanMayhemPersonalProgress(
    val games: Int = 0,
    val wins: Int = 0,
    val secondPlaces: Int = 0,
    val thirdPlaces: Int = 0,
    val kills: Int = 0,
    val deaths: Int = 0,
    val assists: Int = 0,
    val enemyDamage: Int = 0,
    val bossDamage: Int = 0,
    val bossObjectives: Int = 0,
    val cratesOpened: Int = 0,
    val keysOpened: Int = 0,
    val pointsEarned: Int = 0,
    val currentWinStreak: Int = 0,
    val bestWinStreak: Int = 0,
    val trioWins: Int = 0,
    val fiveManWins: Int = 0,
    val welfareWins: Int = 0,
    val midWins: Int = 0,
    val maxWins: Int = 0,
)

/** Persistent, idempotent per-account statistics recorded only after a committed result. */
@Singleton
internal class ClanMayhemProgressionService {
    fun stats(player: Player): ClanMayhemPersonalProgress =
        ClanMayhemPersonalProgressCodec.decode(player.attr[MAYHEM_PERSONAL_STATS])
            ?: ClanMayhemPersonalProgress()

    fun record(
        player: Player,
        matchId: UUID,
        placement: Int,
        participant: ClanMayhemParticipant,
        team: ClanMayhemTeam,
        format: ClanMayhemFormat,
        pointsAwarded: Int,
    ): ClanMayhemPersonalProgress {
        if (decodeMatchId(player.attr[MAYHEM_LAST_STATS_MATCH]) == matchId) return stats(player)
        val previous = stats(player)
        val won = placement == 1
        val streak = if (won) previous.currentWinStreak + 1 else 0
        val score = participant.score
        val updated =
            previous.copy(
                games = previous.games + 1,
                wins = previous.wins + if (won) 1 else 0,
                secondPlaces = previous.secondPlaces + if (placement == 2) 1 else 0,
                thirdPlaces = previous.thirdPlaces + if (placement == 3) 1 else 0,
                kills = previous.kills + score.kills,
                deaths = previous.deaths + score.deaths,
                assists = previous.assists + score.assists,
                enemyDamage = previous.enemyDamage + score.enemyDamage,
                bossDamage = previous.bossDamage + score.bossDamage,
                bossObjectives = previous.bossObjectives + if (team.score.bossObjective) 1 else 0,
                cratesOpened = previous.cratesOpened + score.cratesClaimed,
                keysOpened = previous.keysOpened + score.keysOpened,
                pointsEarned = previous.pointsEarned + pointsAwarded,
                currentWinStreak = streak,
                bestWinStreak = maxOf(previous.bestWinStreak, streak),
                trioWins =
                    previous.trioWins +
                        if (won && format.teamSize == ClanMayhemTeamSize.TRIO) 1 else 0,
                fiveManWins =
                    previous.fiveManWins +
                        if (won && format.teamSize == ClanMayhemTeamSize.FIVE) 1 else 0,
                welfareWins =
                    previous.welfareWins +
                        if (won && format.kitTier == ClanMayhemKitTier.WELFARE) 1 else 0,
                midWins =
                    previous.midWins + if (won && format.kitTier == ClanMayhemKitTier.MID) 1 else 0,
                maxWins =
                    previous.maxWins + if (won && format.kitTier == ClanMayhemKitTier.MAX) 1 else 0,
            )
        player.attr[MAYHEM_PERSONAL_STATS] = ClanMayhemPersonalProgressCodec.encode(updated)
        player.attr[MAYHEM_LAST_STATS_MATCH] = encodeMatchId(matchId)
        return updated
    }

    fun summary(player: Player): String {
        val stats = stats(player)
        return "Clan Mayhem: games=${stats.games}, wins=${stats.wins}, 2nd=${stats.secondPlaces}, " +
            "3rd=${stats.thirdPlaces}, kills=${stats.kills}, deaths=${stats.deaths}, " +
            "damage=${stats.enemyDamage}, boss objectives=${stats.bossObjectives}, points earned=${stats.pointsEarned}."
    }
}

internal object ClanMayhemPersonalProgressCodec {
    private const val MAGIC = 0x434D_5301

    fun encode(value: ClanMayhemPersonalProgress): MutableList<Int> =
        mutableListOf(
            MAGIC,
            value.games,
            value.wins,
            value.secondPlaces,
            value.thirdPlaces,
            value.kills,
            value.deaths,
            value.assists,
            value.enemyDamage,
            value.bossDamage,
            value.bossObjectives,
            value.cratesOpened,
            value.keysOpened,
            value.pointsEarned,
            value.currentWinStreak,
            value.bestWinStreak,
            value.trioWins,
            value.fiveManWins,
            value.welfareWins,
            value.midWins,
            value.maxWins,
        )

    fun decode(values: List<Int>?): ClanMayhemPersonalProgress? =
        runCatching {
                require(values != null && values.size == 21 && values[0] == MAGIC)
                require(values.drop(1).all { it >= 0 })
                ClanMayhemPersonalProgress(
                    games = values[1],
                    wins = values[2],
                    secondPlaces = values[3],
                    thirdPlaces = values[4],
                    kills = values[5],
                    deaths = values[6],
                    assists = values[7],
                    enemyDamage = values[8],
                    bossDamage = values[9],
                    bossObjectives = values[10],
                    cratesOpened = values[11],
                    keysOpened = values[12],
                    pointsEarned = values[13],
                    currentWinStreak = values[14],
                    bestWinStreak = values[15],
                    trioWins = values[16],
                    fiveManWins = values[17],
                    welfareWins = values[18],
                    midWins = values[19],
                    maxWins = values[20],
                )
            }
            .getOrNull()
}

private fun encodeMatchId(value: UUID): MutableList<Int> =
    mutableListOf(
        (value.mostSignificantBits ushr 32).toInt(),
        value.mostSignificantBits.toInt(),
        (value.leastSignificantBits ushr 32).toInt(),
        value.leastSignificantBits.toInt(),
    )

private fun decodeMatchId(values: List<Int>?): UUID? {
    if (values?.size != 4) return null
    fun join(high: Int, low: Int) = (high.toLong() shl 32) or (low.toLong() and 0xffff_ffffL)
    return UUID(join(values[0], values[1]), join(values[2], values[3]))
}
