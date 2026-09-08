package org.rsmod.content.minigames.clanmayhem

import java.util.UUID
import kotlin.math.ceil
import kotlin.random.Random

internal object ClanMayhemEntryRules {
    /** A Mayhem snapshot may never contain transferable items. */
    fun canEnter(inventoryEmpty: Boolean, equipmentEmpty: Boolean): Boolean =
        inventoryEmpty && equipmentEmpty
}

internal object ClanMayhemRosterRules {
    fun completeTeams(
        teams: Collection<ClanMayhemTeam>,
        format: ClanMayhemFormat,
    ): List<ClanMayhemTeam> = teams.filter { it.hasExactRoster(format) }

    fun canStart(
        teams: Collection<ClanMayhemTeam>,
        format: ClanMayhemFormat,
        minimumClans: Int = ClanMayhemConfig.minimumClans,
        debugOverride: Boolean = false,
    ): Boolean {
        val eligible =
            if (debugOverride) {
                teams.count {
                    it.roster.isNotEmpty() && it.roster.values.all(ClanMayhemParticipant::ready)
                }
            } else {
                completeTeams(teams, format).size
            }
        return eligible >= if (debugOverride) 1 else minimumClans
    }

    fun uniqueClanIds(teams: Collection<ClanMayhemTeam>): Boolean =
        teams.map(ClanMayhemTeam::clanId).distinct().size == teams.size

    fun uniqueAccounts(teams: Collection<ClanMayhemTeam>): Boolean {
        val accounts = teams.flatMap { it.roster.keys }
        return accounts.distinct().size == accounts.size
    }
}

internal object ClanMayhemCombatRules {
    fun canAttack(
        attackerTeamId: UUID?,
        targetTeamId: UUID?,
        attackerState: ClanMayhemParticipantState?,
        targetState: ClanMayhemParticipantState?,
        attackerProtected: Boolean,
        targetProtected: Boolean,
        matchActive: Boolean,
    ): Boolean =
        matchActive &&
            attackerTeamId != null &&
            targetTeamId != null &&
            attackerTeamId != targetTeamId &&
            attackerState == ClanMayhemParticipantState.ARENA &&
            targetState == ClanMayhemParticipantState.ARENA &&
            !attackerProtected &&
            !targetProtected
}

internal object ClanMayhemStandings {
    private val comparator =
        compareByDescending<ClanMayhemTeam> { it.score.matchPoints }
            .thenByDescending { it.score.pvpKills }
            .thenByDescending { it.score.bossObjective }
            .thenByDescending { it.score.enemyDamage }
            .thenBy { it.score.deaths }
            .thenByDescending { it.score.cratesClaimed }
            .thenByDescending { it.score.keysOpened }
            .thenComparator { left, right ->
                val l = left.score.finalKillTick
                val r = right.score.finalKillTick
                when {
                    l == null && r == null -> 0
                    l == null -> 1
                    r == null -> -1
                    else -> l.compareTo(r)
                }
            }

    fun ordered(teams: Collection<ClanMayhemTeam>): List<ClanMayhemTeam> =
        teams.sortedWith(comparator)

    fun resolve(teams: Collection<ClanMayhemTeam>): ClanMayhemWinnerResolution {
        require(teams.isNotEmpty())
        val ordered = ordered(teams)
        val first = ordered.first()
        val tied = ordered.takeWhile { comparator.compare(first, it) == 0 }
        return if (tied.size == 1) ClanMayhemWinnerResolution.Winner(first)
        else ClanMayhemWinnerResolution.GenuineTie(tied)
    }

    fun toFinalStandings(teams: Collection<ClanMayhemTeam>): List<ClanMayhemFinalStanding> =
        ordered(teams).mapIndexed { index, team ->
            ClanMayhemFinalStanding(
                index + 1,
                team.matchTeamId,
                team.clanId,
                team.clanName,
                team.score.copy(),
            )
        }
}

internal enum class ClanMayhemBoss(val npcInternal: String, val displayName: String) {
    VETION("npc.vetion", "Vet'ion"),
    VENENATIS("npc.venenatis", "Venenatis"),
    CALLISTO("npc.callisto", "Callisto"),
}

internal data class ClanMayhemBossRoll(
    val boss: ClanMayhemBoss,
    val node: MayhemNamedNode,
    val bossRoll: Int,
    val locationRoll: Int,
)

internal object ClanMayhemBossRules {
    fun roll(random: Random): ClanMayhemBossRoll {
        val bossRoll = random.nextInt(ClanMayhemBoss.entries.size)
        val locationRoll = random.nextInt(ClanMayhemCrater.bossNodes.size)
        return ClanMayhemBossRoll(
            boss = ClanMayhemBoss.entries[bossRoll],
            node = ClanMayhemCrater.bossNodes[locationRoll],
            bossRoll = bossRoll,
            locationRoll = locationRoll,
        )
    }

    fun owner(scaledHitpoints: Int, damageByTeam: Map<UUID, Int>): UUID? {
        if (scaledHitpoints <= 0 || damageByTeam.isEmpty()) return null
        val minimum =
            ceil(scaledHitpoints * ClanMayhemConfig.bossOwnershipMinimumPercent / 100.0).toInt()
        return damageByTeam.entries
            .filter { it.value >= minimum }
            .maxWithOrNull(
                compareBy<Map.Entry<UUID, Int>> { it.value }.thenBy { it.key.toString() }
            )
            ?.key
    }
}

internal enum class MayhemSupplyNpcStage {
    NORMAL,
    GHOST,
    DEAD,
    FOG_REMOVED,
}

internal data class MayhemSupplyNpcEncounter(
    val encounterId: UUID,
    var stage: MayhemSupplyNpcStage = MayhemSupplyNpcStage.NORMAL,
) {
    fun killNormal(randomPercentRoll: Int): Boolean {
        if (stage != MayhemSupplyNpcStage.NORMAL) return false
        require(randomPercentRoll in 0..99)
        val ghost = randomPercentRoll < ClanMayhemConfig.ghostChancePercent
        stage = if (ghost) MayhemSupplyNpcStage.GHOST else MayhemSupplyNpcStage.DEAD
        return ghost
    }

    fun killGhost(): Boolean {
        if (stage != MayhemSupplyNpcStage.GHOST) return false
        stage = MayhemSupplyNpcStage.DEAD
        return true
    }

    fun consumeByFog(): Boolean {
        if (stage == MayhemSupplyNpcStage.DEAD || stage == MayhemSupplyNpcStage.FOG_REMOVED)
            return false
        stage = MayhemSupplyNpcStage.FOG_REMOVED
        return true
    }
}

internal enum class MayhemCombatStyle {
    MELEE,
    RANGED,
    MAGIC,
}

internal data class MayhemSupplyStack(val internalName: String, val quantity: Int)

internal data class MayhemEquipmentRoll(
    val style: MayhemCombatStyle,
    val weapon: String,
    val armour: String,
    val supplies: List<MayhemSupplyStack> = emptyList(),
)

internal data class MayhemStyleTable(val weapons: List<String>, val armour: List<String>) {
    init {
        require(weapons.isNotEmpty())
        require(armour.isNotEmpty())
        require(weapons.none { "voidwaker" in it.lowercase() })
        require(armour.none { "voidwaker" in it.lowercase() })
    }
}

internal object ClanMayhemCrateTables {
    private val welfare =
        mapOf(
            MayhemCombatStyle.MELEE to
                MayhemStyleTable(
                    listOf("obj.abyssal_whip", "obj.dragon_dagger_p++"),
                    listOf("obj.rune_platebody", "obj.rune_platelegs"),
                ),
            MayhemCombatStyle.RANGED to
                MayhemStyleTable(
                    listOf("obj.magic_shortbow", "obj.xbows_crossbow_runite"),
                    listOf("obj.black_dragonhide_body", "obj.black_dragonhide_chaps"),
                ),
            MayhemCombatStyle.MAGIC to
                MayhemStyleTable(
                    listOf("obj.staff_of_zaros"),
                    listOf("obj.mystic_robe_top", "obj.mystic_robe_bottom"),
                ),
        )
    private val mid =
        mapOf(
            MayhemCombatStyle.MELEE to
                MayhemStyleTable(
                    listOf("obj.abyssal_whip", "obj.ags"),
                    listOf("obj.barrows_dharok_body_100", "obj.barrows_dharok_legs_100"),
                ),
            MayhemCombatStyle.RANGED to
                MayhemStyleTable(
                    listOf("obj.barrows_karil_weapon_100"),
                    listOf("obj.barrows_karil_body_100", "obj.barrows_karil_legs_100"),
                ),
            MayhemCombatStyle.MAGIC to
                MayhemStyleTable(
                    listOf("obj.staff_of_zaros"),
                    listOf("obj.barrows_ahrim_body_100", "obj.barrows_ahrim_legs_100"),
                ),
        )
    private val max =
        mapOf(
            MayhemCombatStyle.MELEE to
                MayhemStyleTable(
                    listOf("obj.dragon_claws", "obj.ags"),
                    listOf("obj.barrows_dharok_body_100", "obj.dragon_boots"),
                ),
            MayhemCombatStyle.RANGED to
                MayhemStyleTable(
                    listOf("obj.barrows_karil_weapon_100"),
                    listOf("obj.barrows_karil_body_100", "obj.avas_assembler"),
                ),
            MayhemCombatStyle.MAGIC to
                MayhemStyleTable(
                    listOf("obj.staff_of_zaros"),
                    listOf("obj.barrows_ahrim_body_100", "obj.occult_necklace"),
                ),
        )

    fun table(tier: ClanMayhemKitTier, style: MayhemCombatStyle): MayhemStyleTable =
        when (tier) {
            ClanMayhemKitTier.WELFARE -> welfare
            ClanMayhemKitTier.MID -> mid
            ClanMayhemKitTier.MAX -> max
        }.getValue(style)

    fun refill(style: MayhemCombatStyle): List<MayhemSupplyStack> = buildList {
        add(MayhemSupplyStack("obj.shark", 2))
        add(MayhemSupplyStack("obj.tbwt_cooked_karambwan", 1))
        when (style) {
            MayhemCombatStyle.MELEE -> add(MayhemSupplyStack("obj.shark", 2))
            MayhemCombatStyle.RANGED -> add(MayhemSupplyStack("obj.rune_arrow", 100))
            MayhemCombatStyle.MAGIC -> {
                add(MayhemSupplyStack("obj.deathrune", 100))
                add(MayhemSupplyStack("obj.bloodrune", 50))
            }
        }
    }

    fun roll(tier: ClanMayhemKitTier, random: Random): MayhemEquipmentRoll {

        val style = MayhemCombatStyle.entries[random.nextInt(MayhemCombatStyle.entries.size)]
        val table = table(tier, style)
        return MayhemEquipmentRoll(
            style,
            table.weapons.random(random),
            table.armour.random(random),
            refill(style),
        )
    }
}

internal object ClanMayhemPointRules {
    fun award(
        placement: Int,
        personalKills: Int,
        bossOwningClan: Boolean,
        firstVictoryToday: Boolean,
        active: Boolean,
    ): Int {
        if (!active) return 0
        val placementPoints =
            when (placement) {
                1 -> 25
                2 -> 15
                3 -> 10
                else -> 4
            }
        return placementPoints +
            personalKills.coerceIn(0, ClanMayhemConfig.personalKillPointCap) +
            (if (bossOwningClan) 3 else 0) +
            (if (placement == 1 && firstVictoryToday) 5 else 0)
    }
}

internal data class MayhemRewardItem(
    val internalName: String,
    val unitValue: Int,
    val quantity: IntRange,
)

internal data class MayhemRewardStack(
    val internalName: String,
    val quantity: Int,
    val unitValue: Int,
) {
    val totalValue: Int
        get() = quantity * unitValue
}

internal data class MayhemRewardPackage(val stacks: List<MayhemRewardStack>) {
    val totalValue: Int
        get() = stacks.sumOf(MayhemRewardStack::totalValue)
}

internal class ClanMayhemRewardGenerator(
    private val table: List<MayhemRewardItem>,
    private val fallback: MayhemRewardPackage,
    private val maximumAttempts: Int = 64,
) {
    init {
        require(table.isNotEmpty())
        require(
            fallback.totalValue in
                ClanMayhemConfig.rewardMinimumValue..ClanMayhemConfig.rewardMaximumValue
        )
        require(table.none { forbiddenPermanentReward(it.internalName) })
    }

    fun generate(random: Random): MayhemRewardPackage {
        repeat(maximumAttempts) {
            val stacks = mutableListOf<MayhemRewardStack>()
            repeat(random.nextInt(1, 4)) {
                val item = table.random(random)
                val quantity = random.nextInt(item.quantity.first, item.quantity.last + 1)
                stacks += MayhemRewardStack(item.internalName, quantity, item.unitValue)
            }
            val reward = MayhemRewardPackage(stacks)
            if (
                reward.totalValue in
                    ClanMayhemConfig.rewardMinimumValue..ClanMayhemConfig.rewardMaximumValue
            )
                return reward
        }
        return fallback
    }

    companion object {
        fun forbiddenPermanentReward(name: String): Boolean {
            val key = name.lowercase()
            return "voidwaker" in key ||
                "bloody_key" in key ||
                "bloodier_key" in key ||
                "mayhem_key" in key
        }
    }
}
