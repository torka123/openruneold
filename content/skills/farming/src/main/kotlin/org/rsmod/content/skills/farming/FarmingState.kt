package org.rsmod.content.skills.farming

import org.rsmod.api.attr.AttributeKey
import org.rsmod.game.entity.Player

internal val FARMING_STATE = AttributeKey<MutableMap<String, Any>>(persistenceKey = "farming_patches_v1")

internal data class FarmingPatchState(
    val cropKey: String? = null,
    val plantedAt: Long = 0L,
    val compost: Int = 0,
    val weeds: Int = 3,
    val diseased: Boolean = false,
    val dead: Boolean = false,
    val resurrected: Boolean = false,
    val harvestsLeft: Int = 0,
    val healthChecked: Boolean = false,
)

internal fun Player.loadFarmingPatch(key: String): FarmingPatchState {
    val root = attr[FARMING_STATE] ?: return FarmingPatchState()
    val raw = root[key] as? Map<*, *> ?: return FarmingPatchState()
    return FarmingPatchState(
        cropKey = raw["crop"] as? String,
        plantedAt = (raw["plantedAt"] as? Number)?.toLong() ?: 0L,
        compost = (raw["compost"] as? Number)?.toInt()?.coerceIn(0, 3) ?: 0,
        weeds = (raw["weeds"] as? Number)?.toInt()?.coerceIn(0, 3) ?: 3,
        diseased = raw["diseased"] as? Boolean ?: false,
        dead = raw["dead"] as? Boolean ?: false,
        resurrected = raw["resurrected"] as? Boolean ?: false,
        harvestsLeft = (raw["harvestsLeft"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
        healthChecked = raw["healthChecked"] as? Boolean ?: false,
    )
}

internal fun Player.saveFarmingPatch(key: String, state: FarmingPatchState) {
    val root = (attr[FARMING_STATE] ?: mutableMapOf()).toMutableMap()
    if (state.cropKey == null && state.compost == 0 && state.weeds == 3) {
        root.remove(key)
    } else {
        root[key] = mutableMapOf<String, Any>(
            "crop" to (state.cropKey ?: ""),
            "plantedAt" to state.plantedAt,
            "compost" to state.compost,
            "weeds" to state.weeds,
            "diseased" to state.diseased,
            "dead" to state.dead,
            "resurrected" to state.resurrected,
            "harvestsLeft" to state.harvestsLeft,
            "healthChecked" to state.healthChecked,
        )
    }
    attr[FARMING_STATE] = root
}
