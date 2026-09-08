package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.content.drops.shouldDropLootingBag
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val skeletonHellhoundVetionDropTable: RSDropTable<Player, DropRollItem> =
    RSDropTable(
        tableIdentifier = "Skeleton Hellhound (Vet'ion) Drops",
        npcs = npcs("npc.vetion_hellhound_jnr"),
        tertiaries =
            rsPlayerTertiaryTable {
                1 outOf
                    3 weight
                    "obj.looting_bag" count
                    1 condition
                    { player ->
                        player.shouldDropLootingBag()
                    }
                1 outOf
                    5000 weight
                    "obj.champions_challenge_skeleton" count
                    1 condition
                    { player ->
                        // Drops Need Manual: Only dropped if access to Champions' Guild is
                        // available, and the Skeleton Champion hasn't been previously killed in the
                        // Champions' Challenge.
                        true
                    }
            },
    )
