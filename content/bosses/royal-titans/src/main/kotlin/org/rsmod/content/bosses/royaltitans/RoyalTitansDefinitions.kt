package org.rsmod.content.bosses.royaltitans

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType

/** Encounter definition identities, not a second cache registry. */
internal object RoyalTitansDefinitions {
    fun requireNpc(id: Int) =
        requireNotNull(ServerCacheManager.getNpc(id)) {
            "Missing revision-240 Royal Titans NPC $id"
        }

    fun requireLoc(id: Int) =
        requireNotNull(ServerCacheManager.getObject(id)) {
            "Missing revision-240 Royal Titans location $id"
        }

    fun anim(id: Int) =
        requireNotNull(RSCM.getReverseMapping(RSCMType.SEQ, id)) {
            "Missing Royal Titans sequence $id"
        }

    fun spot(id: Int) =
        requireNotNull(RSCM.getReverseMapping(RSCMType.SPOTANIM, id)) {
            "Missing Royal Titans spotanim $id"
        }
}
