package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.rsPlayerGuaranteedTable
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val ranalphDevereDropTable: RSDropTable<Player, DropRollItem> =
    RSDropTable(
        tableIdentifier = "Ranalph Devere Drops",
        npcs = npcs("npc.ranalph_devere"),
        guaranteed =
            rsPlayerGuaranteedTable {
                "obj.heartcrystal_sectionc" count
                    1 condition
                    { player ->
                        // Drops Need Manual: Only if unowned during the Legends' Quest.
                        true
                    }
            },
    )
