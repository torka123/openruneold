package org.rsmod.content.minigames.clanmayhem

internal const val MAYHEM_TICKS_PER_MINUTE: Int = 100

internal fun parseMayhemElapsedTicks(value: String): Int? {
    val parts = value.trim().split(':')
    if (parts.size !in 1..2) return null
    val minutes = parts[0].toLongOrNull() ?: return null
    val seconds = if (parts.size == 2) parts[1].toLongOrNull() ?: return null else 0L
    if (minutes < 0L || seconds !in 0L..59L) return null
    val totalSeconds = minutes * 60L + seconds
    val maximumSeconds = ClanMayhemConfig.activeMatchTicks.toLong() * 60L / MAYHEM_TICKS_PER_MINUTE
    if (totalSeconds !in 0L..maximumSeconds) return null
    return (totalSeconds * MAYHEM_TICKS_PER_MINUTE / 60L).toInt()
}

internal fun formatMayhemElapsedTicks(ticks: Int): String {
    val totalSeconds = ticks.coerceAtLeast(0) * 60 / MAYHEM_TICKS_PER_MINUTE
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

internal enum class ClanMayhemKitTier(val presetId: String) {
    WELFARE("lms_med"),
    MID("lms_main"),
    MAX("max_tribrid"),
}

internal enum class ClanMayhemTeamSize(val players: Int, val maxClans: Int, val supplyNpcs: Int) {
    TRIO(players = 3, maxClans = 12, supplyNpcs = 16),
    FIVE(players = 5, maxClans = 8, supplyNpcs = 24),
}

internal data class ClanMayhemFormat(
    val teamSize: ClanMayhemTeamSize,
    val kitTier: ClanMayhemKitTier,
) {
    val id: String = "${teamSize.name}_${kitTier.name}"
}

internal enum class ClanMayhemTimelineEvent(val atTick: Int) {
    DEPLOY(0),
    SPAWN_PROTECTION_EXPIRES(50),
    BOSS_WARNING(250),
    BOSS_SPAWN(300),
    SUPPLY_CRATE_ONE(500),
    FOG_ONE_WARNING(700),
    FOG_ONE_CLOSE(800),
    SUPPLY_CRATE_TWO(1_000),
    FOG_TWO_WARNING(1_200),
    FOG_TWO_CLOSE(1_300),
    SUPPLY_CRATE_THREE(1_500),
    FOG_THREE_WARNING(1_700),
    FOG_THREE_CLOSE(1_800),
    SUPPLY_CRATE_FOUR(2_000),
    FINAL_CIRCLE_WARNING(2_100),
    FINAL_CIRCLE_CLOSE(2_200),
    LETHAL_FOG(2_400),
    MATCH_END(2_500),
}

internal object ClanMayhemConfig {
    const val enabled: Boolean = true
    const val registrationTicks: Int = 10 * MAYHEM_TICKS_PER_MINUTE
    const val rosterLockRemainingTicks: Int = 2 * MAYHEM_TICKS_PER_MINUTE
    const val activeMatchTicks: Int = 25 * MAYHEM_TICKS_PER_MINUTE
    const val minimumClans: Int = 3
    const val spawnProtectionTicks: Int = 50
    const val returnProtectionTicks: Int = 8
    const val respawnPenaltyTicks: Int = MAYHEM_TICKS_PER_MINUTE
    const val bossOwnershipMinimumPercent: Int = 15
    const val ghostChancePercent: Int = 25
    const val ghostHitpointsPercent: Int = 125
    const val ghostAccuracyPercent: Int = 112
    const val ghostDefencePercent: Int = 110
    const val ghostMaxHitBonus: Int = 2
    const val pvpKillScore: Int = 1
    const val bossObjectiveScore: Int = 5
    const val personalKillPointCap: Int = 8
    const val rewardMinimumValue: Int = 100_000
    const val rewardMaximumValue: Int = 220_000
    const val crateCount: Int = 4
    const val craterCenterRegion: Int = (53 shl 8) or 63
    const val craterGridSize: Int = 3

    val formats: List<ClanMayhemFormat> =
        ClanMayhemTeamSize.entries.flatMap { size ->
            ClanMayhemKitTier.entries.map { tier -> ClanMayhemFormat(size, tier) }
        }

    val registrationAnnouncementsRemainingTicks: Set<Int> = setOf(1_000, 500, 200, 100, 50)

    val crateTicks: List<Int> = listOf(500, 1_000, 1_500, 2_000)
}

internal data class MayhemSourceTile(val x: Int, val z: Int, val level: Int = 0) {
    init {
        require(x in 3_328..3_519 && z in 3_968..4_159) {
            "Clan Mayhem tile ($x, $z, $level) is outside the copied 3x3 crater grid."
        }
    }
}

internal data class MayhemNamedNode(val id: String, val name: String, val tile: MayhemSourceTile)

/**
 * Cache-backed crater topology. The center comes from
 * `.data/raw-cache/map/npcs/daimons_crater_bounty_hunter.toml` (map square 53,63). Individual
 * combat nodes remain centralized here so the live collision audit can reject an invalid node
 * without changing event logic.
 */
internal object ClanMayhemCrater {
    val waitingRoom =
        MayhemNamedNode("waiting_room", "Daimon's Crater", MayhemSourceTile(3_427, 4_065))
    val respawnBox = MayhemNamedNode("respawn_box", "Respawn Box", MayhemSourceTile(3_423, 4_061))
    val rewardRoom = MayhemNamedNode("reward_room", "Reward Room", MayhemSourceTile(3_433, 4_071))

    val bossNodes =
        listOf(
            MayhemNamedNode("north", "Northern Ruins", MayhemSourceTile(3_427, 4_112)),
            MayhemNamedNode("east", "Eastern Ruins", MayhemSourceTile(3_474, 4_064)),
            MayhemNamedNode("south", "Southern Ruins", MayhemSourceTile(3_427, 4_016)),
            MayhemNamedNode("west", "Western Ruins", MayhemSourceTile(3_378, 4_064)),
        )

    val deploymentPads =
        listOf(
            MayhemSourceTile(3_401, 4_105),
            MayhemSourceTile(3_453, 4_105),
            MayhemSourceTile(3_474, 4_085),
            MayhemSourceTile(3_474, 4_043),
            MayhemSourceTile(3_453, 4_021),
            MayhemSourceTile(3_401, 4_021),
            MayhemSourceTile(3_380, 4_043),
            MayhemSourceTile(3_380, 4_085),
            MayhemSourceTile(3_414, 4_122),
            MayhemSourceTile(3_440, 4_122),
            MayhemSourceTile(3_414, 4_005),
            MayhemSourceTile(3_440, 4_005),
        )

    val crateNodes = bossNodes

    init {
        require(bossNodes.size == 4)
        require(bossNodes.map { it.id }.distinct().size == 4)
        require(bossNodes.map { it.tile }.distinct().size == 4)
        require(deploymentPads.size >= ClanMayhemTeamSize.TRIO.maxClans)
    }
}
