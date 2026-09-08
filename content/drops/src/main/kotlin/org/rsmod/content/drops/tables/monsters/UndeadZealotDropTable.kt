package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val undeadZealotDropTable: RSDropTable<Player, DropRollItem> =
    RSDropTable(
        tableIdentifier = "Undead Zealot Drops",
        npcs = npcs("npc.shades_undead_zealot_1", "npc.shades_undead_zealot_2"),
        tertiaries =
            rsPlayerTertiaryTable {
                1 outOf
                    5000 weight
                    "obj.champions_challenge_zombie" count
                    1 condition
                    { player ->
                        // Drops Need Manual: Zombie champion scrolls are only dropped if Champions
                        // guild is unlocked and player hasn't already defeated the Zombie champion.
                        true
                    }
            },
    )
