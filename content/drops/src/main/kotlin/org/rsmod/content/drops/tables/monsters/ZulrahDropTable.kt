package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.rsPlayerGuaranteedTable
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.content.drops.brimstoneKeyRoll
import org.rsmod.content.drops.clueScrollTransformObj
import org.rsmod.content.drops.tables.shared.SharedDropTables
import org.rsmod.game.entity.Player

internal val zulrahMutagenOrFlax =
    rsPlayerWeightedTable(total = 2632) {
        name("Zulrah flax and mutagen sub-table")
        5 weight "obj.cyan_mutagen" count 1
        5 weight "obj.red_mutagen" count 1
        2622 weight "obj.cert_flax" count 1000
    }

internal val zulrahSharkTable =
    rsPlayerWeightedTable(total = 8) {
        name("Zulrah shark drop table")
        3 weight "obj.cert_raw_shark" count 35
        3 weight "obj.shark_lure" count 70
        2 weight "obj.cert_mantaray" count 35
    }

internal val zulrahCommonTable =
    rsPlayerWeightedTable(total = 249) {
        name("Zulrah common table")
        10 weight zulrahMutagenOrFlax
        10 weight "obj.cert_battlestaff" count 10
        2 weight "obj.dragon_med_helm" count 1
        2 weight "obj.dragon_halberd" count 1
        12 weight "obj.deathrune" count 300
        12 weight "obj.lawrune" count 200
        12 weight "obj.chaosrune" count 500
        2 weight "obj.cert_snapdragon" count 10
        2 weight "obj.cert_dwarf_weed" count 30
        2 weight "obj.cert_toadflax" count 25
        2 weight "obj.cert_torstol" count 10
        6 weight "obj.palm_tree_seed" count 1
        6 weight "obj.papaya_tree_seed" count 3
        6 weight "obj.calquat_tree_seed" count 2
        4 weight "obj.magic_tree_seed" count 1
        2 weight "obj.toadflax_seed" count 2
        2 weight "obj.snapdragon_seed" count 1
        2 weight "obj.dwarf_weed_seed" count 2
        2 weight "obj.torstol_seed" count 1
        1 weight "obj.spirit_tree_seed" count 1
        11 weight "obj.cert_village_snake_skin" count 35
        11 weight "obj.cert_runite_ore" count 2
        10 weight "obj.cert_blankrune_high" count 1500
        10 weight "obj.cert_yew_logs" count 35
        8 weight "obj.cert_adamantite_bar" count 20
        8 weight "obj.cert_coal" count 200
        8 weight "obj.cert_dragon_bones" count 12
        8 weight "obj.cert_mahogany_logs" count 50
        12 weight zulrahSharkTable
        15 weight "obj.teleportscroll_zulandra" count 4
        9 weight "obj.cert_antidote++4" count 10
        8 weight "obj.xbows_bolt_tips_dragonstone" count 12
        6 weight "obj.cert_grapes" count 250
        6 weight "obj.cert_coconut" count 20
        5 weight "obj.swamp_tar" count 1000
        5 weight "obj.snakeboss_scale" count 500
        10 weight SharedDropTables.rareDrop
    }

internal val zulrahPerRollTable =
    rsPlayerWeightedTable(total = 1024) {
        name("Zulrah independent primary roll")
        1 weight "obj.blowpipe_fang" count 1
        1 weight "obj.magic_fang" count 1
        1 weight "obj.serpentine_visage" count 1
        1 weight "obj.uncut_onyx" count 1
        1020 weight zulrahCommonTable
    }

@field:RegisterDropTable
@JvmField
public val zulrahDropTable: RSDropTable<Player, DropRollItem> =
    RSDropTable(
        tableIdentifier = "Zulrah Drops",
        npcs =
            npcs(
                "npc.snakeboss_boss_ranged",
                "npc.snakeboss_boss_melee",
                "npc.snakeboss_boss_magic",
            ),
        guaranteed = rsPlayerGuaranteedTable { "obj.snakeboss_scale" count 100..299 },
        mainTable = zulrahPerRollTable,
        mainRolls = 2,
        tertiaries =
            rsPlayerTertiaryTable {
                onBuilder { brimstoneKeyRoll() }
                1 outOf 3000 weight "obj.jar_of_swamp" count 1
                1 outOf 4000 weight "obj.snakepet" count 1
                1 outOf
                    71 weight
                    "obj.trail_elite_emote_exp1" count
                    1 transformObj
                    { player ->
                        player.clueScrollTransformObj("obj.trail_elite_emote_exp1")
                    }
            },
    )
