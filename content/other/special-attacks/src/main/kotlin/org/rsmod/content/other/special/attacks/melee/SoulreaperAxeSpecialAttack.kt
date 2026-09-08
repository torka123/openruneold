package org.rsmod.content.other.special.attacks.melee

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.config.constants
import org.rsmod.api.mechanics.toxins.SoulreaperStackDecay
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.MeleeSpecialAttack
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/**
 * Behead consumes the axe's Soul Stacks rather than ordinary special-attack energy. The cache
 * marks its energy requirement as specialised, so this map owns the stack validation and debit.
 */
class SoulreaperAxeSpecialAttack : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val behead = Behead(manager)
        registerMelee("obj.soulreaper", behead)
        registerMelee("obj.soulreaper_axe_orn", behead)
    }

    private class Behead(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean = behead(target, attack)

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean = behead(target, attack)

        private fun ProtectedAccess.behead(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            val stacks = vars[SOUL_STACKS].coerceIn(0, MAX_SOUL_STACKS)
            if (stacks == 0) {
                mes("You need at least one Soul Stack to use this special attack.")
                manager.stopCombat(this)
                return false
            }

            // Behead has one human animation. The normal and ornamented axes differ only in the
            // revision-240 combat graphic; the vfx_human_* sequences belong to those spotanims
            // and must never be played directly on the player's model.
            val ornamented = player.righthand?.id == ORNAMENTED_SOULREAPER_ID
            anim(BEHEAD_SEQUENCE)
            spotanim(
                spot = if (ornamented) ORNAMENTED_BEHEAD_SPOTANIM else BEHEAD_SPOTANIM,
                slot = constants.spotanim_slot_combat,
                height = 96,
            )

            // The normal strength bonus is removed first. Behead applies its own damage and
            // accuracy bonus from the consumed count, rather than doubling the active stacks.
            SoulreaperStackDecay.clear(player)
            VarPlayerIntMapSetter.set(player, SOUL_STACKS, 0)
            statHeal("stat.hitpoints", constant = stacks * HEAL_PER_STACK, percent = 0)

            val multiplier = 1.0 + (stacks * BONUS_PER_STACK)
            val damage =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = multiplier,
                    maxHitMultiplier = multiplier,
                )
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            manager.continueCombat(this, target)
            return true
        }
    }

    private companion object {
        const val SOUL_STACKS = "varp.soulreaper_stacks"
        const val MAX_SOUL_STACKS = 5
        val ORNAMENTED_SOULREAPER_ID = "obj.soulreaper_axe_orn".asRSCM(RSCMType.OBJ)
        const val BEHEAD_SEQUENCE = "seq.ancient_axe_special"
        const val BEHEAD_SPOTANIM = "spotanim.ancient_axe_special_spotanim"
        const val ORNAMENTED_BEHEAD_SPOTANIM = "spotanim.ancient_axe_special_spotanim_orn"
        const val HEAL_PER_STACK = 8
        const val BONUS_PER_STACK = 0.06
    }
}
