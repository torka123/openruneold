package org.rsmod.content.bosses.corporealbeast

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType

internal object CorporealBeastValidation {
    fun validateAssets() {
        val boss = requireNpc(CorporealBeastAssets.BOSS_NPC_ID)
        val core = requireNpc(CorporealBeastAssets.DARK_CORE_NPC_ID)
        require(boss.hitpoints == CorporealBeastRules.HITPOINTS) {
            "Revision-240 Corporeal Beast must have ${CorporealBeastRules.HITPOINTS} hitpoints."
        }
        require(core.hitpoints == CorporealBeastRules.DARK_CORE_HITPOINTS) {
            "Revision-240 Dark Core must have ${CorporealBeastRules.DARK_CORE_HITPOINTS} hitpoints."
        }
        listOf(
                CorporealBeastAssets.MAGIC_ANIMATION,
                CorporealBeastAssets.DRAIN_ANIMATION,
                CorporealBeastAssets.AREA_MAGIC_ANIMATION,
                CorporealBeastAssets.MELEE_LEFT_ANIMATION,
                CorporealBeastAssets.MELEE_RIGHT_ANIMATION,
                CorporealBeastAssets.STOMP_ANIMATION,
            )
            .forEach { id ->
                requireNotNull(ServerCacheManager.getAnim(id)) { "Missing animation $id" }
            }
        listOf(
                CorporealBeastAssets.DRAIN_PROJECTILE_SPOTANIM,
                CorporealBeastAssets.AREA_PROJECTILE_SPOTANIM,
                CorporealBeastAssets.MAGIC_PROJECTILE_SPOTANIM,
                CorporealBeastAssets.DARK_CORE_JUMP_SPOTANIM,
                CorporealBeastAssets.AREA_IMPACT_SPOTANIM,
            )
            .forEach(::spotanimInternal)
    }

    private fun spotanimInternal(id: Int): String = RSCM.getReverseMapping(RSCMType.SPOTANIM, id)

    private fun requireNpc(id: Int) =
        requireNotNull(ServerCacheManager.getNpc(id)) { "Missing revision-240 NPC definition: $id" }
}
