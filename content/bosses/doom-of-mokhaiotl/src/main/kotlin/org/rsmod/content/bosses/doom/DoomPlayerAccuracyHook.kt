package org.rsmod.content.bosses.doom

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.combat.manager.PlayerNpcAccuracyOverrideHook
import org.rsmod.api.combat.manager.PlayerNpcAccuracyStyle
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

@Singleton
internal class DoomPlayerAccuracyHook
@Inject
constructor(private val encounters: DoomEncounterManager) : PlayerNpcAccuracyOverrideHook {
    override fun forceAccurate(
        source: Player,
        target: Npc,
        style: PlayerNpcAccuracyStyle,
    ): Boolean =
        when (style) {
            PlayerNpcAccuracyStyle.Melee,
            PlayerNpcAccuracyStyle.Ranged,
            PlayerNpcAccuracyStyle.Magic -> encounters.forcePlayerAccuracy(source, target)
        }
}
