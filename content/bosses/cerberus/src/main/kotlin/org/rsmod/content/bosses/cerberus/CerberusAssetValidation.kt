package org.rsmod.content.bosses.cerberus

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType

internal object CerberusAssetValidation {
    fun validateAssets() {
        val boss = requireNpc(CerberusAssets.ACTIVE_NPC_ID)
        require(boss.id == 5862) { "Revision-240 Cerberus attacking id changed: ${boss.id}" }
        require(boss.size == 5) { "Cerberus must occupy a 5x5 footprint." }
        require(boss.hitpoints == CerberusRules.HITPOINTS) {
            "Cerberus must have ${CerberusRules.HITPOINTS} hitpoints."
        }
        listOf(
                CerberusAssets.SITTING_NPC_ID,
                CerberusAssets.RESETTING_NPC_ID,
                CerberusAssets.RANGED_SOUL_NPC_ID,
                CerberusAssets.MAGIC_SOUL_NPC_ID,
                CerberusAssets.MELEE_SOUL_NPC_ID,
            )
            .forEach { id -> requireNotNull(ServerCacheManager.getNpc(id)) { "Missing npc $id" } }
        requireNotNull(ServerCacheManager.getObject(CerberusAssets.FIRE_WALL_LOC_ID)) {
            "Missing Cerberus fire-wall object ${CerberusAssets.FIRE_WALL_LOC_ID}"
        }
        listOf(
                CerberusAssets.IDLE_ANIMATION,
                CerberusAssets.SITTING_ANIMATION,
                CerberusAssets.STAND_ANIMATION,
                CerberusAssets.SIT_ANIMATION,
                CerberusAssets.WALK_ANIMATION,
                CerberusAssets.DEFEND_ANIMATION,
                CerberusAssets.RANGED_ANIMATION,
                CerberusAssets.MELEE_ANIMATION,
                CerberusAssets.MAGIC_ANIMATION,
                CerberusAssets.TRIPLE_SPRAY_ANIMATION,
                CerberusAssets.HOWL_ANIMATION,
                CerberusAssets.DEATH_ANIMATION,
                CerberusAssets.LAVA_ANIMATION,
                CerberusAssets.SOUL_READY_ANIMATION,
                CerberusAssets.SOUL_FLY_ANIMATION,
                CerberusAssets.MELEE_SOUL_ATTACK_ANIMATION,
                CerberusAssets.RANGED_SOUL_ATTACK_ANIMATION,
                CerberusAssets.MAGIC_SOUL_ATTACK_ANIMATION,
                CerberusAssets.SOUL_DEFEND_ANIMATION,
            )
            .forEach { id -> requireNotNull(ServerCacheManager.getAnim(id)) { "Missing anim $id" } }
        listOf(
                CerberusAssets.MAGIC_CAST_SPOTANIM,
                CerberusAssets.MAGIC_PROJECTILE_SPOTANIM,
                CerberusAssets.MAGIC_IMPACT_SPOTANIM,
                CerberusAssets.RANGED_IMPACT_SPOTANIM,
                CerberusAssets.RANGED_PROJECTILE_SPOTANIM,
                CerberusAssets.LAVA_LIQUID_SPOTANIM,
                CerberusAssets.LAVA_FLAME_SPOTANIM,
                CerberusAssets.RANGED_SOUL_PROJECTILE_SPOTANIM,
                CerberusAssets.MAGIC_SOUL_PROJECTILE_SPOTANIM,
                CerberusAssets.MELEE_SOUL_PROJECTILE_SPOTANIM,
            )
            .forEach(::resolvedSpotanim)
    }

    private fun requireNpc(id: Int) =
        requireNotNull(ServerCacheManager.getNpc(id)) { "Missing Cerberus NPC definition: $id" }

    private fun resolvedSpotanim(id: Int): Int {
        CerberusSpotanimValidation.requireLoaded(id)
        val internal =
            requireNotNull(RSCM.getReverseMapping(RSCMType.SPOTANIM, id)) {
                "Missing Cerberus spotanim mapping: $id"
            }
        return internal.asRSCM(RSCMType.SPOTANIM)
    }
}
