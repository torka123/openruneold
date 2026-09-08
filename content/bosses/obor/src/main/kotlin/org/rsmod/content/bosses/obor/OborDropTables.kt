package org.rsmod.content.bosses.obor

import dtx.rs.RSDropTable
import dtx.rs.locs
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.ringNothing
import org.rsmod.api.droptable.rsPlayerGuaranteedTable
import org.rsmod.api.droptable.rsPlayerPrerollTable
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.content.drops.clueScrollTransformObj
import org.rsmod.content.drops.tables.shared.SharedDropTables
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val oborFloorDropTable: RSDropTable<Player, DropRollItem> =
    RSDropTable(
        tableIdentifier = "Obor floor drops",
        npcs = npcs(OBOR_NPC),
        preRoll = rsPlayerPrerollTable {
            1 outOf 16 weight OBOR_KEY count 1
        },
        mainTable = rsPlayerWeightedTable(total = 134) {
            name("Obor floor drops")
            4 weight "obj.iron_dagger" count 1
            5 weight "obj.iron_med_helm" count 1
            5 weight "obj.iron_full_helm" count 1
            3 weight "obj.iron_kiteshield" count 1
            2 weight "obj.steel_scimitar" count 1
            2 weight "obj.steel_longsword" count 1
            6 weight "obj.iron_arrow" count 3
            3 weight "obj.firerune" count 15
            3 weight "obj.waterrune" count 7
            3 weight "obj.lawrune" count 2
            2 weight "obj.steel_arrow" count 10
            2 weight "obj.mindrune" count 3
            2 weight "obj.cosmicrune" count 2
            2 weight "obj.naturerune" count 6
            1 weight "obj.chaosrune" count 2
            1 weight "obj.deathrune" count 2
            14 weight "obj.coins" count 38
            10 weight "obj.coins" count 52
            8 weight "obj.coins" count 15
            6 weight "obj.coins" count 8
            2 weight "obj.coins" count 88
            11 weight "obj.limpwurt_root" count 1
            6 weight "obj.beer" count 1
            2 weight "obj.body_talisman" count 1
            1 weight ringNothing()
            7 weight SharedDropTables.herb
            3 weight SharedDropTables.gem
            18 weight SharedDropTables.seed
        },
        tertiaries = rsPlayerTertiaryTable {
            1 outOf 25 weight "obj.arceuus_corpse_giant" count 1
            1 outOf 400 weight "obj.dorgesh_construction_bone" count 1
            1 outOf 5000 weight "obj.champions_challenge_giant" count 1
            1 outOf 5013 weight "obj.dorgesh_construction_bone_curved" count 1
            1 outOf 50 weight "obj.trail_clue_beginner" count 1 transformObj { player ->
                player.clueScrollTransformObj("obj.trail_clue_beginner")
            }
        },
    )

@field:RegisterDropTable
@JvmField
public val oborChestDropTable: RSDropTable<Player, DropRollItem> =
    RSDropTable(
        tableIdentifier = "Obor's chest",
        locs = locs(OBOR_CHEST_CLOSED),
        guaranteed = rsPlayerGuaranteedTable {
            "obj.trail_clue_beginner" count 1 transformObj { player ->
                player.clueScrollTransformObj("obj.trail_clue_beginner")
            }
            "obj.arceuus_corpse_giant" count 1
        },
        mainTable = rsPlayerWeightedTable(total = 118) {
            name("Obor's chest")
            3 weight "obj.rune_kiteshield" count 1
            5 weight "obj.rune_full_helm" count 1
            6 weight "obj.rune_med_helm" count 1
            3 weight "obj.rune_chainbody" count 1
            3 weight "obj.rune_platelegs" count 1
            3 weight "obj.rune_plateskirt" count 1
            4 weight "obj.rune_battleaxe" count 1
            5 weight "obj.rune_longsword" count 1
            3 weight "obj.rune_2h_sword" count 1
            12 weight "obj.lawrune" count 50..99
            12 weight "obj.cosmicrune" count 60..119
            10 weight "obj.chaosrune" count 100..199
            10 weight "obj.deathrune" count 40..79
            7 weight "obj.naturerune" count 40..79
            6 weight "obj.coins" count 10000..15000
            4 weight "obj.coins" count 12000..20000
            8 weight "obj.cert_limpwurt_root" count 20
            8 weight "obj.cert_big_bones" count 50
            5 weight rsPlayerGuaranteedTable {
                "obj.cert_uncut_ruby" count 5
                "obj.cert_uncut_diamond" count 5
            }
            1 weight "obj.hillgiant_boss_club" count 1
        },
        tertiaries = rsPlayerTertiaryTable {
            1 outOf 16 weight OBOR_KEY count 1
        },
    )
