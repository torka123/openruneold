package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val brandaTheFireQueenDropTable: RSDropTable<Player, DropRollItem> =
    RSDropTable(
        tableIdentifier = "Branda the Fire Queen Drops",
        npcs = npcs("npc.rt_fire_queen"),
        separateRolls =
            royalTitansUniques(
                "obj.twinflame_piece_2",
                "obj.mystic_vigour_prayer_scroll",
                "varbit.prayer_mystic_vigour_unlocked",
            ),
        mainRolls = 2,
        mainRollsFor = { _, unique -> royalTitansMainRolls(unique) },
        mainTable =
            rsPlayerWeightedTable(total = 55) {
                name("Branda the Fire Queen Drops")
                2 weight "obj.cert_mystic_fire_staff" count 4
                2 weight "obj.cert_fire_battlestaff" count 6
                2 weight "obj.rune_plateskirt" count 2
                2 weight "obj.rune_scimitar" count 2
                2 weight "obj.rune_pickaxe" count 2
                2 weight "obj.firerune" count 1500..3000
                2 weight "obj.chaosrune" count 200..400
                2 weight "obj.deathrune" count 150..300
                2 weight "obj.naturerune" count 100..150
                2 weight "obj.lawrune" count 100..150
                2 weight "obj.soulrune" count 100..150
                2 weight "obj.bloodrune" count 100..150
                2 weight "obj.rune_arrow" count 100..300
                2 weight "obj.cert_coal" count 280..320
                2 weight "obj.cert_gold_ore" count 60..100
                2 weight "obj.cert_fire_orb" count 20..40
                2 weight "obj.cert_unidentified_avantoe" count 6..8
                2 weight "obj.cert_unidentified_cadantine" count 6..8
                2 weight "obj.cert_unidentified_dwarf_weed" count 6..8
                2 weight "obj.cert_unidentified_irit" count 6..8
                2 weight "obj.cert_unidentified_kwuarm" count 6..8
                2 weight "obj.cert_unidentified_lantadyme" count 6..8
                2 weight "obj.cert_unidentified_ranarr" count 6..8
                1 weight "obj.maple_seed" count 4..6
                1 weight "obj.palm_tree_seed" count 2
                1 weight "obj.yew_seed" count 2
                2 weight "obj.coins" count 10000..30000
                2 weight "obj.4doseprayerrestore" count 2..4
                2 weight "obj.desiccated_page" count 6..24
            },
        tertiaries = royalTitansTertiaryTable(),
    )
