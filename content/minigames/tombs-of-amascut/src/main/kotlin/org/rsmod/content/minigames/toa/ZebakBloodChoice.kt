package org.rsmod.content.minigames.toa

import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.Selector
import org.rsmod.api.bosses.spec.WeightedRef

internal enum class ZebakBloodChoice {
    BARRAGE,
    CLOUDS,
}

// Stable branch identity follows the retained 0=barrage/1=clouds order on Zebak's shared RNG.
private val bloodChoice =
    Effect.Choose(
        Selector.WeightedRandom(
            ZebakBloodChoice.entries.map { WeightedRef(it.name) },
            noRepeatBias = 1.0,
        ),
        ZebakBloodChoice.entries.associate { it.name to Effect.NoOp },
    )

/** Independent blood opportunities do not consume or replace an ordinary ability opportunity. */
internal fun BossEncounter.selectZebakBlood(tick: Int): ZebakBloodChoice =
    ZebakBloodChoice.valueOf(checkNotNull(selectBranch(bloodChoice, tick, null)))
