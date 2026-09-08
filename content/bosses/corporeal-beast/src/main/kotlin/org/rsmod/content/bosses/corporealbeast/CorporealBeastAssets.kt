package org.rsmod.content.bosses.corporealbeast

import dev.openrune.types.ProjAnimType

internal object CorporealBeastAssets {
    const val BOSS_NPC_ID = 319
    const val DARK_CORE_NPC_ID = 320

    const val MAGIC_ANIMATION = 1679
    const val DRAIN_ANIMATION = 1680
    const val AREA_MAGIC_ANIMATION = 1681
    const val MELEE_LEFT_ANIMATION = 1682
    const val MELEE_RIGHT_ANIMATION = 1683
    const val STOMP_ANIMATION = 1686

    const val DRAIN_PROJECTILE_SPOTANIM = 314
    const val AREA_PROJECTILE_SPOTANIM = 315
    const val MAGIC_PROJECTILE_SPOTANIM = 316
    const val DARK_CORE_JUMP_SPOTANIM = 319
    const val AREA_IMPACT_SPOTANIM = 1836

    val TRACKING_PROJECTILE =
        ProjAnimType(
            startHeight = 50,
            endHeight = 25,
            delay = 25,
            angle = 15,
            lengthAdjustment = 18,
            progress = 64,
            stepMultiplier = 5,
        )

    val AREA_MAIN_PROJECTILE =
        ProjAnimType(
            startHeight = 50,
            endHeight = 5,
            delay = 25,
            angle = 15,
            lengthAdjustment = 18,
            progress = 64,
            stepMultiplier = 5,
        )

    val AREA_SPLIT_PROJECTILE =
        ProjAnimType(
            startHeight = 5,
            endHeight = 5,
            delay = 0,
            angle = 15,
            lengthAdjustment = 28,
            progress = 64,
            stepMultiplier = 5,
        )

    val DARK_CORE_JUMP_PROJECTILE =
        ProjAnimType(
            startHeight = 0,
            endHeight = 0,
            delay = 0,
            angle = 15,
            lengthAdjustment = 30,
            progress = 0,
            stepMultiplier = 5,
        )
}
