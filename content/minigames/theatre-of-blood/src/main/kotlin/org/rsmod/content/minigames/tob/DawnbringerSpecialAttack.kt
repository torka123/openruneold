package org.rsmod.content.minigames.tob

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.config.constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.MagicSpecialAttack
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitImpactHandler

/** Native Theatre-of-Blood Dawnbringer special, Pulsate. */
internal class DawnbringerSpecialAttack
@Inject
constructor(private val raids: TobRaidManager, private val random: GameRandom) : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        registerMagic(TobItems.DAWNBRINGER, PULSATE_ENERGY, Pulsate(manager, raids, random))
    }

    private class Pulsate(
        private val manager: SpecialAttackManager,
        private val raids: TobRaidManager,
        private val random: GameRandom,
    ) : MagicSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Staff,
        ): Boolean {
            if (!raids.canUseDawnbringer(player, target)) {
                manager.stopCombat(this)
                mes("Pulsate can only be used against Verzik during her first phase.")
                return false
            }
            anim("seq.human_casting")
            spotanim(
                reverse(RSCMType.SPOTANIM, TobAssets.DAWNBRINGER_SPECIAL_CAST),
                height = 96,
                slot = constants.spotanim_slot_combat,
            )
            val projectile =
                manager.spawnProjectile(
                    source = this,
                    target = target,
                    spotanim = reverse(RSCMType.SPOTANIM, TobAssets.DAWNBRINGER_SPECIAL_PROJECTILE),
                    projanim = "projanim.magic_spell",
                )
            val damage = random.of(75..150)
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMagicHit(
                source = this,
                target = target,
                damage = damage,
                clientDelay = projectile.clientCycles,
                hitDelay = projectile.serverCycles,
                impactHandler =
                    HitImpactHandler {
                        target.spotanim(
                            reverse(RSCMType.SPOTANIM, TobAssets.DAWNBRINGER_SPECIAL_IMPACT),
                            height = 96,
                            slot = constants.spotanim_slot_combat,
                        )
                    },
            )
            manager.continueCombat(this, target)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Staff,
        ): Boolean {
            manager.stopCombat(this)
            mes("The Dawnbringer's Pulsate attack is only effective against Verzik.")
            return false
        }
    }

    private companion object {
        const val PULSATE_ENERGY: Int = 350

        fun reverse(type: RSCMType, id: Int): String =
            requireNotNull(RSCM.getReverseMapping(type, id)) { "Missing Dawnbringer $type $id" }
    }
}
