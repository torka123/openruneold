package org.rsmod.content.drops.tables.monsters

import dtx.core.RollResult
import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.config.constants
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.dropRollable
import org.rsmod.api.droptable.rsPlayerGuaranteedTable
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.content.drops.brimstoneKeyRoll
import org.rsmod.content.drops.clueScrollTransformObj
import org.rsmod.content.drops.tables.shared.SharedDropTables
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val alchemicalHydraDropTable: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Alchemical Hydra Drops",
    npcs = npcs("npc.hydraboss_4"),
    guaranteed = rsPlayerGuaranteedTable { "obj.hydra_bones" count 2 },
    separateRolls = AlchemicalHydraUniqueRoll,
    mainRolls = 2,
    mainRollsFor = { _, unique -> if (unique is RollResult.Nothing) 2 else 1 },
    mainTable = rsPlayerWeightedTable(total = 110) {
        name("Alchemical Hydra Drops")
        8 weight dropRollable(DropRollItem("obj.mystic_fire_staff", 1, bonusDrops = listOf(
            DropRollItem("obj.mystic_water_staff", 1),
        )))
        8 weight "obj.mystic_water_staff" count 1
        5 weight "obj.cert_battlestaff" count 8..12
        5 weight "obj.black_dragonhide_body" count 1
        3 weight "obj.dragon_longsword" count 1
        3 weight "obj.dragon_med_helm" count 1
        3 weight dropRollable(DropRollItem("obj.rune_platebody", 1, bonusDrops = listOf(
            DropRollItem("obj.rune_platelegs", 1, condition = { player -> player.appearance.bodyType == constants.bodytype_a }),
            DropRollItem("obj.rune_plateskirt", 1, condition = { player -> player.appearance.bodyType == constants.bodytype_b }),
        )))
        2 weight "obj.dragon_battleaxe" count 1
        2 weight "obj.rune_platelegs" count 1
        2 weight "obj.rune_plateskirt" count 1
        1 weight dropRollable(DropRollItem("obj.mystic_robe_top_light", 1, bonusDrops = listOf(
            DropRollItem("obj.mystic_robe_bottom_light", 1),
        )))
        1 weight "obj.mystic_robe_bottom_light" count 1
        6 weight "obj.chaosrune" count 150..300
        6 weight "obj.deathrune" count 150..300
        6 weight "obj.bloodrune" count 150..300
        6 weight "obj.astralrune" count 150..300
        2 weight "obj.xbows_crossbow_bolts_runite_tipped_dragonstone_enchanted" count 100..120
        1 weight "obj.xbows_crossbow_bolts_runite_tipped_onyx_enchanted" count 35..50
        1 weight "obj.coins" count 5550..25550
        10 weight "obj.coins" count 40000..60000
        7 weight "obj.shark" count 2..4
        7 weight dropRollable(DropRollItem("obj.3doserangerspotion", 1, bonusDrops = listOf(
            DropRollItem("obj.3dose2restore", 2),
        )))
        7 weight "obj.3dose2restore" count 2
        6 weight "obj.cert_dragon_bones" count 30
        1 weight "obj.crystal_key" count 1

        1 weight SharedDropTables.rareDrop
    },
    tertiaries = rsPlayerTertiaryTable {
        onBuilder { brimstoneKeyRoll(konarTaskBonus = true) }
        1 outOf 256 weight "obj.poh_alchemical_hydra_head" count 1
        1 outOf 2000 weight "obj.jar_of_chemicals" count 1
        1 outOf 3000 weight "obj.hydrapet" count 1
        1 outOf 95 weight "obj.trail_clue_hard_map001" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_clue_hard_map001")
        }
        1 outOf 243 weight "obj.trail_elite_emote_exp1" count 1 transformObj { player ->
             player.clueScrollTransformObj("obj.trail_elite_emote_exp1")
        }
    },
)

// Unknown wiki drop rates (text rarity — need data collection):
