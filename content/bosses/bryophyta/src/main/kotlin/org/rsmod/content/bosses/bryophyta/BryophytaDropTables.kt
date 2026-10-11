package org.rsmod.content.bosses.bryophyta

import dtx.rs.RSDropTable
import dtx.rs.locs
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.nothing
import org.rsmod.api.droptable.rsPlayerGuaranteedTable
import org.rsmod.api.droptable.rsPlayerPrerollTable
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.content.drops.clueScrollTransformObj
import org.rsmod.content.drops.tables.shared.SharedDropTables
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val bryophytaFloorDropTable: RSDropTable<Player, DropRollItem> =
    RSDropTable(
        tableIdentifier = "Bryophyta floor drops",
        npcs = npcs(BRYOPHYTA_NPC),
        preRoll = rsPlayerPrerollTable {
            1 outOf 16 weight BRYOPHYTA_KEY count 1
        },
        mainTable = rsPlayerWeightedTable(total = 128) {
            name("Bryophyta floor drops")
            5 weight "obj.black_sq_shield" count 1
            2 weight "obj.magic_staff" count 1
            2 weight "obj.steel_med_helm" count 1
            2 weight "obj.mithril_sword" count 1
            2 weight "obj.mithril_spear" count 1
            1 weight "obj.steel_kiteshield" count 1
            4 weight "obj.lawrune" count 3
            3 weight "obj.airrune" count 18
            3 weight "obj.earthrune" count 27
            3 weight "obj.chaosrune" count 7
            3 weight "obj.naturerune" count 6
            2 weight "obj.cosmicrune" count 3
            2 weight "obj.iron_arrow" count 15
            1 weight "obj.steel_arrow" count 30
            1 weight "obj.deathrune" count 3
            1 weight "obj.bloodrune" count 1
            19 weight "obj.coins" count 37
            11 weight "obj.coins" count 2
            10 weight "obj.coins" count 119
            2 weight "obj.coins" count 300
            6 weight "obj.steel_bar" count 1
            1 weight "obj.coal" count 1
            1 weight "obj.spinach_roll" count 1
            5 weight SharedDropTables.herb
            4 weight SharedDropTables.gem
            32 weight nothing()
        },
        tertiaries = rsPlayerTertiaryTable {
            1 outOf 24 weight "obj.arceuus_corpse_giant" count 1
            1 outOf 400 weight "obj.dorgesh_construction_bone" count 1
            1 outOf 5000 weight "obj.champions_challenge_giant" count 1
            1 outOf 5013 weight "obj.dorgesh_construction_bone_curved" count 1
            1 outOf 45 weight "obj.trail_clue_beginner" count 1 transformObj { player ->
                player.clueScrollTransformObj("obj.trail_clue_beginner")
            }
        },
    )

@field:RegisterDropTable
@JvmField
public val bryophytaChestDropTable: RSDropTable<Player, DropRollItem> =
    RSDropTable(
        tableIdentifier = "Bryophyta's chest",
        locs = locs(BRYOPHYTA_CHEST_CLOSED),
        guaranteed = rsPlayerGuaranteedTable {
            "obj.trail_clue_beginner" count 1 transformObj { player ->
                player.clueScrollTransformObj("obj.trail_clue_beginner")
            }
        },
        mainTable = rsPlayerWeightedTable(total = 118) {
            name("Bryophyta's chest")
            6 weight "obj.rune_longsword" count 1
            6 weight "obj.rune_med_helm" count 2
            6 weight "obj.rune_chainbody" count 1
            6 weight "obj.rune_plateskirt" count 1
            6 weight "obj.rune_platelegs" count 1
            6 weight "obj.rune_sq_shield" count 1
            5 weight "obj.rune_sword" count 2
            3 weight "obj.cert_adamant_platebody" count 5
            3 weight "obj.cert_battlestaff" count 3
            1 weight "obj.adamant_kiteshield" count 3
            8 weight "obj.naturerune" count 100
            6 weight "obj.cosmicrune" count 100
            6 weight "obj.lawrune" count 100
            5 weight "obj.deathrune" count 100
            6 weight "obj.chaosrune" count 200
            5 weight "obj.chaosrune" count 100
            1 weight "obj.bloodrune" count 100
            1 weight "obj.adamant_arrow" count 100
            5 weight SharedDropTables.herb
            6 weight SharedDropTables.seed
            6 weight "obj.runite_bar" count 2
            4 weight rsPlayerGuaranteedTable {
                "obj.cert_uncut_ruby" count 5
                "obj.cert_uncut_diamond" count 5
            }
            3 weight "obj.cert_steel_bar" count 25
            5 weight "obj.coins" count 10000
            2 weight "obj.coins" count 8000
            1 weight "obj.gb_moss_essence" count 1
        },
        tertiaries = rsPlayerTertiaryTable {
            1 outOf 16 weight BRYOPHYTA_KEY count 1
        },
    )
