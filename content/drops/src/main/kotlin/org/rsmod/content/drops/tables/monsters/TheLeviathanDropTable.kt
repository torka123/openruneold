package org.rsmod.content.drops.tables.monsters

import dtx.core.ArgMap
import dtx.core.RollResult
import dtx.core.Rollable
import dtx.core.RollableHooks
import dtx.rs.RSDropTable
import dtx.rs.RSTable
import dtx.rs.brimstoneRarityDenominator
import dtx.rs.npcs
import kotlin.random.Random
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.KillRollContext
import org.rsmod.api.droptable.NpcKillRewardContext
import org.rsmod.api.droptable.NpcKillRewardContexts
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.content.drops.clueScrollTransformObj
import org.rsmod.content.drops.shouldDropBrimstoneKey
import org.rsmod.game.entity.Player

public const val LEVIATHAN_UNIQUE_DENOMINATOR: Int = 96
public const val LEVIATHAN_AWAKENED_UNIQUE_NUMERATOR: Int = 3
public const val LEVIATHAN_ORB_DENOMINATOR: Int = 53
public const val LEVIATHAN_TABLET_DENOMINATOR: Int = 25
public const val LEVIATHAN_QUARTZ_DENOMINATOR: Int = 200
public const val LEVIATHAN_QUARTZ_ROLL_BOUND: Int = 20_000
public const val LEVIATHAN_QUARTZ_BASE_NUMERATOR: Int = 100
public const val LEVIATHAN_QUARTZ_BEST_NUMERATOR: Int = 400
public const val LEVIATHAN_QUARTZ_BEST_KILLCOUNT: Int = 300
public const val LEVIATHAN_SUPPLY_DENOMINATOR: Int = 5
public const val LEVIATHAN_PET_DENOMINATOR: Int = 2_500
public const val LEVIATHAN_CLUE_DENOMINATOR: Int = 40

public object LeviathanRewardAttributes {
    public val ELIGIBLE_KILLS: AttributeKey<Int> =
        AttributeKey(persistenceKey = "leviathan_reward_eligible_kills_v1")
    public val VESTIGE_PROGRESS: AttributeKey<Int> =
        AttributeKey(persistenceKey = "leviathan_venator_vestige_progress_v1")
    public val TABLET_RECEIVED: AttributeKey<Boolean> =
        AttributeKey(persistenceKey = "leviathan_scarred_tablet_received_v1")
    public val QUARTZ_RECEIVED: AttributeKey<Boolean> =
        AttributeKey(persistenceKey = "leviathan_smoke_quartz_received_v1")
}

public data class LeviathanRewardState(
    val eligibleKills: Int = 0,
    val vestigeProgress: Int = 0,
    val tabletReceived: Boolean = false,
    val quartzReceived: Boolean = false,
)

public fun Player.leviathanRewardState(): LeviathanRewardState =
    LeviathanRewardState(
        eligibleKills = (attr[LeviathanRewardAttributes.ELIGIBLE_KILLS] ?: 0).coerceAtLeast(0),
        vestigeProgress = (attr[LeviathanRewardAttributes.VESTIGE_PROGRESS] ?: 0).coerceIn(0, 2),
        tabletReceived = attr[LeviathanRewardAttributes.TABLET_RECEIVED] ?: false,
        quartzReceived = attr[LeviathanRewardAttributes.QUARTZ_RECEIVED] ?: false,
    )

private fun Player.saveLeviathanRewardState(state: LeviathanRewardState) {
    attr[LeviathanRewardAttributes.ELIGIBLE_KILLS] = state.eligibleKills.coerceAtLeast(0)
    attr[LeviathanRewardAttributes.VESTIGE_PROGRESS] = state.vestigeProgress.coerceIn(0, 2)
    attr[LeviathanRewardAttributes.TABLET_RECEIVED] = state.tabletReceived
    attr[LeviathanRewardAttributes.QUARTZ_RECEIVED] = state.quartzReceived
}

public enum class LeviathanRewardBranch {
    Ineligible,
    Virtus,
    VestigeProgress,
    Vestige,
    ChromiumIngot,
    LeviathansLure,
    AwakenersOrb,
    ScarredTablet,
    SmokeQuartz,
    Supplies,
    Standard,
    StandardNothing,
}

public data class LeviathanRewardItem(
    val obj: String,
    val count: IntRange,
    val clueBase: Boolean = false,
) {
    constructor(
        obj: String,
        count: Int,
        clueBase: Boolean = false,
    ) : this(obj, count..count, clueBase)
}

public data class LeviathanRewardResult(
    val branch: LeviathanRewardBranch,
    val items: List<LeviathanRewardItem>,
    val state: LeviathanRewardState,
)

public fun interface LeviathanRewardRandom {
    public fun nextInt(bound: Int): Int
}

public data class LeviathanStandardReward(
    val weight: Int,
    val obj: String?,
    val normalQuantity: Int,
    val perfectQuantity: Int,
)

public val leviathanStandardRewards: List<LeviathanStandardReward> =
    listOf(
        LeviathanStandardReward(1, "obj.cosmic_soul_catalyst", 1_500, 2_250),
        LeviathanStandardReward(1, "obj.cert_iron_ore", 57, 85),
        LeviathanStandardReward(1, "obj.cert_silver_ore", 57, 85),
        LeviathanStandardReward(8, "obj.cert_coal", 195, 292),
        LeviathanStandardReward(8, "obj.cert_gold_ore", 67, 101),
        LeviathanStandardReward(1, "obj.cert_adamantite_ore", 57, 85),
        LeviathanStandardReward(2, "obj.cert_runite_ore", 27, 40),
        LeviathanStandardReward(1, "obj.cert_sapphire", 25, 38),
        LeviathanStandardReward(1, "obj.cert_emerald", 25, 38),
        LeviathanStandardReward(1, "obj.cert_ruby", 25, 38),
        LeviathanStandardReward(5, "obj.cert_uncut_ruby", 37, 56),
        LeviathanStandardReward(5, "obj.cert_uncut_diamond", 37, 56),
        LeviathanStandardReward(8, "obj.dragon_javelin_head", 54, 81),
        LeviathanStandardReward(2, "obj.dragon_bolts_unfeathered", 150, 225),
        LeviathanStandardReward(1, "obj.xbows_bolt_tips_onyx", 90, 135),
        LeviathanStandardReward(1, "obj.cert_raw_mantaray", 180, 270),
        LeviathanStandardReward(8, "obj.anglerfish", 4, 6),
        LeviathanStandardReward(1, "obj.bronze_arrow", 63, 94),
        LeviathanStandardReward(1, "obj.mithril_arrow", 63, 94),
        LeviathanStandardReward(1, "obj.adamant_arrow", 63, 94),
        LeviathanStandardReward(8, "obj.rune_arrow", 54, 81),
        LeviathanStandardReward(1, "obj.bodyrune", 180, 270),
        LeviathanStandardReward(1, "obj.earthrune", 180, 270),
        LeviathanStandardReward(8, "obj.smokerune", 300, 450),
        LeviathanStandardReward(2, "obj.soulrune", 600, 900),
        LeviathanStandardReward(22, null, 0, 0),
    )

public object LeviathanRewardEngine {
    private val virtus = listOf("obj.virtus_mask", "obj.virtus_top", "obj.virtus_legs")
    private val clues =
        listOf(
            "obj.trail_clue_easy_simple001",
            "obj.trail_medium_emote_exp1",
            "obj.trail_clue_hard_map001",
            "obj.trail_elite_emote_exp1",
        )

    /**
     * First-drop protection is expressed as a numerator over 20,000: 100/20,000 (1/200) at zero KC
     * and 400/20,000 (1/50) at 300 KC. Public evidence confirms those endpoints but not Jagex's
     * private interpolation implementation, so the endpoint-linear policy is isolated here for
     * direct replacement if a capture establishes a different curve.
     */
    public fun quartzNumerator(eligibleKills: Int, received: Boolean): Int {
        if (received) return LEVIATHAN_QUARTZ_BASE_NUMERATOR
        val progress = eligibleKills.coerceIn(0, LEVIATHAN_QUARTZ_BEST_KILLCOUNT)
        return (LEVIATHAN_QUARTZ_BASE_NUMERATOR + progress).coerceAtMost(
            LEVIATHAN_QUARTZ_BEST_NUMERATOR
        )
    }

    public fun roll(
        state: LeviathanRewardState,
        context: NpcKillRewardContext,
        random: LeviathanRewardRandom,
        brimstoneDenominator: Int? = null,
    ): LeviathanRewardResult {
        if (!context.rewardEligible) {
            return LeviathanRewardResult(LeviathanRewardBranch.Ineligible, emptyList(), state)
        }
        val currentKill = state.eligibleKills + 1
        var nextState = state.copy(eligibleKills = currentKill)
        val main = mutableListOf<LeviathanRewardItem>()
        val branch: LeviathanRewardBranch
        val uniqueNumerator = if (context.awakened) LEVIATHAN_AWAKENED_UNIQUE_NUMERATOR else 1
        if (random.checked(LEVIATHAN_UNIQUE_DENOMINATOR) < uniqueNumerator) {
            when (random.checked(8)) {
                0 -> {
                    branch = LeviathanRewardBranch.Virtus
                    main += LeviathanRewardItem(virtus[random.checked(virtus.size)], 1)
                }
                in 1..3 -> {
                    if (nextState.vestigeProgress == 2) {
                        branch = LeviathanRewardBranch.Vestige
                        main += LeviathanRewardItem("obj.venator_vestige", 1)
                        nextState = nextState.copy(vestigeProgress = 0)
                    } else {
                        branch = LeviathanRewardBranch.VestigeProgress
                        val progress = nextState.vestigeProgress + 1
                        main += LeviathanRewardItem("obj.gold_ring", 1)
                        nextState = nextState.copy(vestigeProgress = progress)
                    }
                }
                in 4..6 -> {
                    branch = LeviathanRewardBranch.ChromiumIngot
                    main += LeviathanRewardItem("obj.chromium_ingot", 1)
                }
                else -> {
                    branch = LeviathanRewardBranch.LeviathansLure
                    main += LeviathanRewardItem("obj.soulreaper_axe_lure", 1)
                }
            }
        } else if (random.oneIn(LEVIATHAN_ORB_DENOMINATOR)) {
            branch = LeviathanRewardBranch.AwakenersOrb
            main += LeviathanRewardItem("obj.dt2_awakeners_orb", 1)
        } else if (!nextState.tabletReceived && random.oneIn(LEVIATHAN_TABLET_DENOMINATOR)) {
            branch = LeviathanRewardBranch.ScarredTablet
            main += LeviathanRewardItem("obj.leviathan_tablet", 1)
            nextState = nextState.copy(tabletReceived = true)
        } else if (
            random.checked(LEVIATHAN_QUARTZ_ROLL_BOUND) <
                quartzNumerator(state.eligibleKills, nextState.quartzReceived)
        ) {
            branch = LeviathanRewardBranch.SmokeQuartz
            main += LeviathanRewardItem("obj.smoke_quartz", 1)
            nextState = nextState.copy(quartzReceived = true)
        } else if (random.oneIn(LEVIATHAN_SUPPLY_DENOMINATOR)) {
            branch = LeviathanRewardBranch.Supplies
            main += LeviathanRewardItem("obj.3doseprayerrestore", 1)
            main += LeviathanRewardItem("obj.2doserangerspotion", 1)
            main += LeviathanRewardItem("obj.seaturtle", 3..4)
        } else {
            val standard = selectStandard(random)
            if (standard.obj == null) {
                branch = LeviathanRewardBranch.StandardNothing
            } else {
                branch = LeviathanRewardBranch.Standard
                val quantity =
                    if (context.perfectKill) standard.perfectQuantity else standard.normalQuantity
                main += LeviathanRewardItem(standard.obj, quantity)
            }
        }
        if (brimstoneDenominator != null && random.oneIn(brimstoneDenominator)) {
            main += LeviathanRewardItem("obj.konar_key", 1)
        }
        if (random.oneIn(LEVIATHAN_PET_DENOMINATOR)) {
            main += LeviathanRewardItem("obj.leviathanpet", 1)
        }
        if (random.oneIn(LEVIATHAN_CLUE_DENOMINATOR)) {
            main += LeviathanRewardItem(clues[random.checked(clues.size)], 1, clueBase = true)
        }
        return LeviathanRewardResult(branch, main, nextState)
    }

    private fun selectStandard(random: LeviathanRewardRandom): LeviathanStandardReward {
        val total = leviathanStandardRewards.sumOf(LeviathanStandardReward::weight)
        var roll = random.checked(total)
        for (reward in leviathanStandardRewards) {
            if (roll < reward.weight) return reward
            roll -= reward.weight
        }
        error("Leviathan standard table has no entry for roll")
    }

    private fun LeviathanRewardRandom.checked(bound: Int): Int {
        require(bound > 0)
        return nextInt(bound).also { require(it in 0 until bound) }
    }

    private fun LeviathanRewardRandom.oneIn(denominator: Int): Boolean = checked(denominator) == 0
}

internal object LeviathanSequentialRoll :
    RSTable<Player, DropRollItem>, RollableHooks<Player, DropRollItem> by RollableHooks.Default() {
    override val tableIdentifier: String = "The Leviathan sequential reward chain"
    override val tableEntries: Collection<Rollable<Player, DropRollItem>> = emptyList()

    override fun selectResult(target: Player, otherArgs: ArgMap): RollResult<DropRollItem> {
        val npc = otherArgs[KillRollContext.npc] ?: return RollResult.Nothing()
        val context = NpcKillRewardContexts[npc] ?: return RollResult.Nothing()
        if (!context.rewardEligible) return RollResult.Nothing()
        val areaChecker = otherArgs[KillRollContext.areaChecker]
        val brimstoneDenominator =
            if (areaChecker != null && target.shouldDropBrimstoneKey(npc, areaChecker)) {
                brimstoneRarityDenominator(npc.type.combatLevel, false)
            } else {
                null
            }
        val result =
            LeviathanRewardEngine.roll(
                state = target.leviathanRewardState(),
                context = context,
                random = LeviathanRewardRandom(Random::nextInt),
                brimstoneDenominator = brimstoneDenominator,
            )
        target.saveLeviathanRewardState(result.state)
        val drops =
            result.items.map { reward ->
                DropRollItem(
                    obj = reward.obj,
                    count = reward.count,
                    transformObj =
                        if (reward.clueBase) {
                            { player -> player.clueScrollTransformObj(reward.obj) }
                        } else {
                            { null }
                        },
                )
            }
        return when (drops.size) {
            0 -> RollResult.Nothing()
            1 -> RollResult.Single(drops.first())
            else -> RollResult.ListOf(drops)
        }
    }
}

@field:RegisterDropTable
@JvmField
public val theLeviathanDropTable: RSDropTable<Player, DropRollItem> =
    RSDropTable(
        tableIdentifier = "The Leviathan Drops",
        npcs = npcs("npc.leviathan"),
        mainTable = LeviathanSequentialRoll,
    )
