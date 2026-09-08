package org.rsmod.content.drops.tables.monsters

import dtx.core.RollResult
import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.attr.BossRewardAttributes
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.rsPlayerGuaranteedTable
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.content.drops.brimstoneKeyRoll
import org.rsmod.content.drops.clueScrollTransformObj
import org.rsmod.content.drops.tables.shared.SharedDropTables
import org.rsmod.game.entity.Player

private const val CHARGED_ICE = "obj.muspah_pet_morph"
internal const val MUSPAH_PET_DENOMINATOR = 2500

private val muspahSupplyObjs =
    setOf(
        "obj.shark",
        "obj.summer_pie",
        "obj.3doseancientbrew",
        "obj.3doserangerspotion",
        "obj.3dose2restore",
        "obj.3doseprayerrestore",
    )

private fun RollResult<DropRollItem>.items(): List<DropRollItem> =
    when (this) {
        is RollResult.Single -> listOf(result)
        is RollResult.ListOf -> results
        RollResult.Nothing -> emptyList()
    }

internal fun Player.isMuspahChargedIceEligible(): Boolean {
    val ticks = attr[BossRewardAttributes.phantomMuspahLastKillTicks] ?: Int.MAX_VALUE
    return ticks < 300 &&
        attr[BossRewardAttributes.phantomMuspahChargedIceUsed] != true &&
        invMap.values.none { inventory -> CHARGED_ICE in inventory }
}

internal val muspahUniqueRoll =
    rsPlayerWeightedTable(total = 100) {
        name("Phantom Muspah unique roll")
        60 weight "obj.ancient_essence" count 540..599
        23 weight
            DropRollItem(
                obj = "obj.ancient_essence",
                count = 885..995,
                countChoices = (885..995 step 5).toList(),
            )
        10 weight
            DropRollItem(
                obj = "obj.ancient_essence",
                count = 1970..2060,
                countChoices = (1970..2060 step 10).toList(),
            )
        4 weight "obj.frozen_cache" count 1
        2 weight "obj.ancient_icon" count 1
        1 weight "obj.venator_shard" count 1
    }

internal val muspahSupplyRoll =
    rsPlayerWeightedTable(total = 9) {
        name("Phantom Muspah supply replacement")
        2 weight
            rsPlayerGuaranteedTable {
                add(
                    rsPlayerWeightedTable(total = 2) {
                        1 weight "obj.shark" count 4..6
                        1 weight "obj.summer_pie" count 4..6
                    }
                )
                add(
                    rsPlayerWeightedTable(total = 2) {
                        1 weight "obj.3doseancientbrew" count 1..2
                        1 weight "obj.3doserangerspotion" count 1..3
                    }
                )
                add(
                    rsPlayerWeightedTable(total = 2) {
                        1 weight "obj.3dose2restore" count 2..3
                        1 weight "obj.3doseprayerrestore" count 2..3
                    }
                )
            }
        7 weight DropRollItem(obj = "", count = 1, isNothing = true)
    }

@field:RegisterDropTable
@JvmField
public val phantomMuspahDropTable: RSDropTable<Player, DropRollItem> =
    RSDropTable(
        tableIdentifier = "Phantom Muspah Drops",
        npcs =
            npcs(
                "npc.muspah",
                "npc.muspah_final",
                "npc.muspah_melee",
                "npc.muspah_soulsplit",
                "npc.muspah_teleport",
            ),
        preRoll = muspahUniqueRoll,
        exclusivePreRollWhen = { _, result ->
            result.items().any { it.obj == "obj.venator_shard" }
        },
        separateRolls = rsPlayerGuaranteedTable { add(muspahSupplyRoll) },
        mainTable =
            rsPlayerWeightedTable(total = 235) {
                name("Phantom Muspah secondary table")
                10 weight "obj.cert_rune_kiteshield" count 3
                5 weight "obj.dragon_plateskirt" count 1
                5 weight "obj.cert_rune_platelegs" count 3
                5 weight "obj.black_dragonhide_body" count 1
                4 weight "obj.dragon_platelegs" count 2
                1 weight "obj.rune_sword" count 1
                10 weight "obj.lawrune" count 146
                10 weight "obj.soulrune" count 380
                10 weight "obj.deathrune" count 428
                15 weight "obj.smokerune" count 314
                5 weight "obj.chaosrune" count 480
                5 weight "obj.firerune" count 1964
                5 weight "obj.mcannonball" count 670
                3 weight "obj.cert_unidentified_toadflax" count 40
                5 weight "obj.yew_seed" count 2
                5 weight "obj.torstol_seed" count 2
                5 weight "obj.palm_tree_seed" count 2
                5 weight "obj.ranarr_seed" count 3
                4 weight "obj.snapdragon_seed" count 5
                3 weight "obj.ranarr_seed" count 5
                2 weight "obj.spirit_tree_seed" count 1
                10 weight "obj.cert_adamantite_ore" count 22
                10 weight "obj.cert_gold_ore" count 180
                10 weight "obj.cert_plank_teak" count 22
                15 weight "obj.cert_molten_glass" count 89
                5 weight "obj.cert_blankrune_high" count 2314
                5 weight "obj.cert_coal" count 163
                3 weight "obj.cert_runite_ore" count 18
                2 weight "obj.cert_silver_ore" count 101
                15 weight "obj.cert_water_orb" count 21
                10 weight "obj.dragon_bolts_unfeathered" count 89
                3 weight "obj.cert_limpwurt_root" count 21
                10 weight
                    rsPlayerWeightedTable(total = 8) {
                        3 weight "obj.cert_raw_shark" count 28
                        3 weight "obj.shark_lure" count 56
                        2 weight "obj.cert_mantaray" count 28
                    }
                5 weight
                    rsPlayerWeightedTable(total = 16) {
                        5 weight "obj.cert_unidentified_kwuarm" count 6
                        4 weight "obj.cert_unidentified_dwarf_weed" count 6
                        4 weight "obj.cert_unidentified_cadantine" count 6
                        3 weight "obj.cert_unidentified_lantadyme" count 6
                    }
                5 weight
                    rsPlayerWeightedTable(total = 250) {
                        30 weight "obj.ranarr_seed" count 3
                        28 weight "obj.snapdragon_seed" count 3
                        22 weight "obj.torstol_seed" count 3
                        21 weight "obj.watermelon_seed" count 45
                        20 weight "obj.willow_seed" count 3
                        18 weight "obj.mahogany_seed" count 3
                        18 weight "obj.maple_seed" count 3
                        18 weight "obj.teak_seed" count 3
                        18 weight "obj.yew_seed" count 3
                        14 weight "obj.papaya_tree_seed" count 3
                        11 weight "obj.magic_tree_seed" count 3
                        10 weight "obj.palm_tree_seed" count 3
                        8 weight "obj.spirit_tree_seed" count 3
                        6 weight "obj.dragonfruit_tree_seed" count 3
                        4 weight "obj.celastrus_tree_seed" count 3
                        4 weight "obj.redwood_tree_seed" count 3
                    }
                5 weight SharedDropTables.rareDrop
            },
        mainRolls = 2,
        mainRollsFor = { _, supplyResult ->
            if (supplyResult.items().any { it.obj in muspahSupplyObjs }) 1 else 2
        },
        tertiaries =
            rsPlayerTertiaryTable {
                onBuilder { brimstoneKeyRoll() }
                1 outOf
                    1 weight
                    CHARGED_ICE count
                    1 condition
                    { player ->
                        player.isMuspahChargedIceEligible()
                    }
                1 outOf MUSPAH_PET_DENOMINATOR weight "obj.muspahpet" count 1
                1 outOf
                    30 weight
                    "obj.trail_clue_hard_map001" count
                    1 transformObj
                    { player ->
                        player.clueScrollTransformObj("obj.trail_clue_hard_map001")
                    }
                1 outOf
                    45 weight
                    "obj.trail_elite_emote_exp1" count
                    1 transformObj
                    { player ->
                        player.clueScrollTransformObj("obj.trail_elite_emote_exp1")
                    }
            },
    )
