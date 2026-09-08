package org.rsmod.content.skills.magic.spell.attacks.arceuus

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.magic.DarkLureState
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player

internal class DarkLureLifecycle @Inject constructor(private val clock: MapClock) : PlayerPostTickHook {
    override fun onPostTick(player: Player) = DarkLureState.tick(player, clock.cycle)
}
