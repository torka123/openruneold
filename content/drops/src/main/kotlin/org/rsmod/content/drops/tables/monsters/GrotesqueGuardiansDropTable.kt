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
import org.rsmod.game.entity.Player

internal const val GROTESQUE_COMMON_TOTAL = 142
internal const val GROTESQUE_SHARK_TOTAL = 8
internal const val GROTESQUE_PER_ROLL_TOTAL = 3000
internal const val GROTESQUE_MAIN_ROLLS = 2

internal val grotesqueSharkTable =
    rsPlayerWeightedTable(total = GROTESQUE_SHARK_TOTAL) {
        name("Grotesque Guardians shark table")
        3 weight "obj.cert_raw_shark" count 20
        3 weight "obj.shark_lure" count 40
        2 weight "obj.cert_raw_seaturtle" count 20
    }

internal val grotesquePotionTable = rsPlayerGuaranteedTable {
    "obj.2dose1magic" count 1
    "obj.2doserangerspotion" count 1
    "obj.2dose2combat" count 1
}

internal val grotesqueCommonTable =
    rsPlayerWeightedTable(total = GROTESQUE_COMMON_TOTAL) {
        name("Grotesque Guardians common table")
        6 weight "obj.rune_pickaxe" count 1
        5 weight "obj.rune_full_helm" count 1
        5 weight "obj.rune_platelegs" count 1
        4 weight "obj.rune_2h_sword" count 1
        3 weight "obj.rune_battleaxe" count 1
        1 weight "obj.dragon_longsword" count 1
        1 weight "obj.adamant_boots" count 1
        1 weight "obj.dragon_med_helm" count 1
        10 weight "obj.potato_mushroom+onion" count 4..6
        8 weight "obj.4dosepotionofsaradomin" count 2
        6 weight grotesquePotionTable
        4 weight "obj.4doseprayerrestore" count 1..2
        7 weight "obj.cert_gold_ore" count 40..50
        6 weight "obj.cert_adamantite_bar" count 25..40
        6 weight "obj.cert_gold_bar" count 35..50
        6 weight "obj.cert_mithril_bar" count 35..45
        5 weight "obj.cert_coal" count 180..250
        4 weight "obj.cert_runite_ore" count 3..6
        3 weight "obj.cert_runite_bar" count 3..5
        4 weight grotesqueSharkTable
        10 weight "obj.coins" count 10000..20000
        8 weight "obj.chaosrune" count 100..150
        5 weight "obj.coins" count 25000
        5 weight "obj.crystal_key" count 1
        5 weight "obj.deathrune" count 90..130
        5 weight "obj.dragon_dart_tip" count 15..25
        3 weight "obj.xbows_bolt_tips_diamond" count 100..150
        2 weight "obj.xbows_bolt_tips_dragonstone" count 20..40
        2 weight "obj.xbows_bolt_tips_onyx" count 10..15
        1 weight "obj.dragon_arrowheads" count 50..150
        1 weight "obj.soulrune" count 20..40
    }

internal val grotesquePerRollTable =
    rsPlayerWeightedTable(total = GROTESQUE_PER_ROLL_TOTAL) {
        name("Grotesque Guardians independent primary roll")
        12 weight "obj.granite_maul" count 1
        6 weight "obj.granite_gloves" count 1
        6 weight "obj.granite_ring" count 1
        4 weight "obj.granite_hammer" count 1
        3 weight "obj.tourmaline_core" count 1
        2969 weight grotesqueCommonTable
    }

@field:RegisterDropTable
@JvmField
public val grotesqueGuardiansDropTable: RSDropTable<Player, DropRollItem> =
    RSDropTable(
        tableIdentifier = "Grotesque Guardians Drops",
        npcs = npcs("npc.gargboss_dusk_phase4"),
        guaranteed = rsPlayerGuaranteedTable { "obj.granite_dust" count 50..100 },
        mainTable = grotesquePerRollTable,
        mainRolls = GROTESQUE_MAIN_ROLLS,
        tertiaries =
            rsPlayerTertiaryTable {
                onBuilder { brimstoneKeyRoll(konarTaskBonus = true) }
                1 outOf 3000 weight "obj.dawnpet" count 1
                1 outOf 5000 weight "obj.jar_of_stone" count 1
                1 outOf
                    230 weight
                    "obj.trail_elite_emote_exp1" count
                    1 transformObj
                    { player ->
                        player.clueScrollTransformObj("obj.trail_elite_emote_exp1")
                    }
            },
    )
