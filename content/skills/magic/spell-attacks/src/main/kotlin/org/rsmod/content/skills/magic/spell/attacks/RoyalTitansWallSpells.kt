package org.rsmod.content.skills.magic.spell.attacks

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.hit.HitType

/** Wall-specific content composed with native spells, rune transactions and NPC hit queues. */
public class RoyalTitansWallSpells
@Inject
constructor(
    private val runes: MagicRuneManager,
    private val world: WorldRepository,
    private val modifier: StandardNpcHitModifier,
) {
    public fun begin(access: ProtectedAccess, target: Npc, attack: CombatAttack.Spell): Boolean {
        if (!accepts(target.visType.id, attack.spell.obj.internalName)) {
            access.mes(rejection(target.visType.id))
            access.clearPendingAction()
            return false
        }
        // Wall casts consume native requirements but grant neither cast nor damage XP (2283
        // wall-only windows at1339/1345/1447/1449). Do not use SpellAttackManager.attemptCast,
        // which deliberately awards cast XP for combat spells.
        if (runes.attemptCast(access.player, attack.spell).isFailure()) {
            access.clearPendingAction()
            return false
        }
        return true
    }

    public fun finish(
        access: ProtectedAccess,
        target: Npc,
        attack: CombatAttack.Spell,
        serverDelay: Int,
        clientDelay: Int,
        impact: String,
        impactHeight: Int,
        castSound: String,
        hitSound: String,
    ) {
        access.soundSynth(castSound)
        access.soundSynth(hitSound, delay = clientDelay)
        world.spotanimMap(
            SpotanimType(impact.asRSCM(RSCMType.SPOTANIM)),
            target.coords,
            height = impactHeight,
            delay = clientDelay,
        )
        target.queueHit(
            source = access.player,
            delay = serverDelay,
            type = HitType.Magic,
            damage = 0,
            modifier = modifier,
            sourceSecondary = attack.spell.obj,
        )
        // The wall's native queue handler consumes this hit at projectile impact. No attack delay,
        // retaliation, damage formula, freeze, area-target selection or Twinflame echo is created.
        access.clearPendingAction()
    }

    public companion object {
        public const val FIRE: Int = 14152
        public const val ICE: Int = 14153

        public fun isWall(target: PathingEntity): Boolean =
            target is Npc && target.visType.id in FIRE..ICE

        public fun accepts(targetId: Int, spell: String): Boolean =
            when (targetId) {
                FIRE -> spell in WaterSpells || spell in IceSpells
                ICE -> spell in FireSpells
                else -> false
            }

        public fun rejection(targetId: Int): String =
            when (targetId) {
                FIRE ->
                    "You'll need to use a water or ice spell that is strong enough to put out the fire."
                ICE -> "You'll need to use a fire spell to melt the ice."
                else -> error("Not a Royal Titans wall: $targetId")
            }

        private val WaterSpells =
            setOf("obj.47_water_blast", "obj.65_water_wave", "obj.85_water_surge")
        private val FireSpells = setOf("obj.59_fire_blast", "obj.75_fire_wave", "obj.95_fire_surge")
        private val IceSpells =
            setOf("obj.58_ice_rush", "obj.70_ice_burst", "obj.82_ice_blitz", "obj.94_ice_barrage")
    }
}
