package org.rsmod.content.bosses.doom

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.combat.manager.PlayerNpcMeleeAttackDelayOverrideHook
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

@Singleton
internal class DoomMeleeAttackDelayHook
@Inject
constructor(private val encounters: DoomEncounterManager) : PlayerNpcMeleeAttackDelayOverrideHook {
    override fun bypass(source: Player, target: Npc): Boolean =
        encounters.consumeMeleeChargeAttackDelayBypass(source, target)
}
