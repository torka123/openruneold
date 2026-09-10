package org.rsmod.content.drops.tables.monsters

import dtx.core.RollResult
import dtx.core.rollableHooks
import dtx.impl.chance.ChanceRollableImpl
import dtx.rs.RSPreRollTable
import org.rsmod.api.droptable.ChanceRollStyle
import org.rsmod.api.droptable.DropRateBoosts
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RateBoostChanceRollable
import org.rsmod.api.droptable.addRateFirstItem
import org.rsmod.api.droptable.dropRollableWithPlayerCondition
import org.rsmod.api.droptable.rsPlayerPrerollTable
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.content.drops.ClueScrollTier
import org.rsmod.content.drops.clueScrollTransformObj
import org.rsmod.content.drops.eliteClueDropDenominator
import org.rsmod.content.drops.hardClueDropDenominator
import org.rsmod.content.drops.hasCompletedXMarksTheSpot
import org.rsmod.content.drops.ownsClueScroll
import org.rsmod.game.entity.Player

/**
 * Keeps the draft's independent unique checks; their exact OSRS topology remains unresolved.
 * Learned scrolls are excluded before rolling so they cannot consume the two standard rolls.
 */
internal fun royalTitansUniques(crown: String, scroll: String, unlock: String) =
    rsPlayerPrerollTable {
        onBuilder {
            addRateFirstItem(1, 75, ChanceRollStyle.Rolls, DropRollItem(crown, 1))
            val scrollRoll =
                dropRollableWithPlayerCondition(
                    DropRollItem(scroll, 1, condition = { it.vars[unlock] == 0 })
                )
            // The native chance wrapper has its own inclusion hook; inner inclusion is not
            // inherited.
            addEntry(
                ChanceRollableImpl(
                    100.0 / 75,
                    scrollRoll,
                    rollableHooks {
                        shouldInclude { player, args -> scrollRoll.includeInRoll(player, args) }
                    },
                )
            )
            addRateFirstItem(
                1,
                16,
                ChanceRollStyle.Rolls,
                DropRollItem("obj.giantsoul_amulet_uncharged", 1),
            )
        }
    }

internal fun royalTitansMainRolls(unique: RollResult<DropRollItem>): Int =
    when (unique) {
        RollResult.Nothing -> 2
        is RollResult.Single -> if (unique.result.isNothing) 2 else 0
        is RollResult.ListOf -> if (unique.results.any { !it.isNothing }) 0 else 2
    }

/** Shared native table component for Loot, Take pages and Sacrifice; never contribution-scaled. */
public fun royalTitansTertiaryTable(
    sacrifice: Boolean = false
): RSPreRollTable<Player, DropRollItem> = rsPlayerTertiaryTable {
    onBuilder {
        addRateFirstItem(
            1,
            if (sacrifice) 1500 else 3000,
            ChanceRollStyle.Chance,
            DropRollItem("obj.rtbrandapet", 1),
        )
        for ((obj, tier, denominators) in
            listOf(
                Triple("obj.trail_clue_hard_map001", ClueScrollTier.Hard, listOf(25, 23)),
                Triple("obj.trail_elite_emote_exp1", ClueScrollTier.Elite, listOf(100, 95)),
            )) {
            for (denominator in denominators) {
                val drop =
                    DropRollItem(
                        obj,
                        1,
                        condition = { player ->
                            val eligibleRate =
                                if (tier == ClueScrollTier.Hard) player.hardClueDropDenominator(25)
                                else player.eliteClueDropDenominator(100)
                            eligibleRate == denominator &&
                                (player.hasCompletedXMarksTheSpot() || !player.ownsClueScroll(tier))
                        },
                        transformObj = { it.clueScrollTransformObj(obj) },
                    )
                // Preserve the native clue-rate boost wrapper, in addition to claimed CA tiers.
                val rollable = dropRollableWithPlayerCondition(drop)
                addEntry(
                    RateBoostChanceRollable(
                        1,
                        denominator,
                        rollable,
                        DropRateBoosts.clueScrollBoostPercent,
                        rollableHooks {
                            shouldInclude { player, args -> rollable.includeInRoll(player, args) }
                        },
                    )
                )
            }
        }
    }
}
