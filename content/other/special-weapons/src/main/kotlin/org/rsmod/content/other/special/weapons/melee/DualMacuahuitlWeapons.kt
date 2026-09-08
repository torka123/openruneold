package org.rsmod.content.other.special.weapons.melee

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.player.hat
import org.rsmod.api.player.legs
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.torso
import org.rsmod.api.player.worn.EquipmentChecks
import org.rsmod.api.random.GameRandom
import org.rsmod.api.weapons.MeleeWeapon
import org.rsmod.api.weapons.WeaponAttackManager
import org.rsmod.api.weapons.WeaponMap
import org.rsmod.api.weapons.WeaponRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

class DualMacuahuitlWeapons
@Inject
constructor(
    private val random: GameRandom,
) : WeaponMap {
    override fun WeaponRepository.register(manager: WeaponAttackManager) {
        register("obj.dual_macuahuitl", DualMacuahuitl(manager, random))
        register("obj.br_dual_macuahuitl", DualMacuahuitl(manager, random))
    }

    private class DualMacuahuitl(
        private val manager: WeaponAttackManager,
        private val random: GameRandom,
    ) : MeleeWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            manager.playWeaponFx(this, attack)
            val totalDamage = rollAndQueueHits(target, attack)
            manager.giveCombatXp(this, target, attack, totalDamage)
            manager.continueCombat(this, target)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            manager.playWeaponFx(this, attack)
            val totalDamage = rollAndQueueHits(target, attack)
            manager.giveCombatXp(this, target, attack, totalDamage)
            manager.continueCombat(this, target)
            return true
        }

        private fun ProtectedAccess.rollAndQueueHits(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Int {
            val firstAccuracy =
                manager.rollMeleeAccuracy(
                    source = this,
                    target = target,
                    attackType = attack.type,
                    attackStyle = attack.style,
                    blockType = attack.type,
                    multiplier = 1.0,
                )
            val firstDamage =
                if (firstAccuracy) {
                    manager.rollMeleeMaxHit(
                        source = this,
                        target = target,
                        attackType = attack.type,
                        attackStyle = attack.style,
                        multiplier = 0.5,
                        roundUp = false,
                    )
                } else {
                    0
                }

            val secondAccuracy =
                firstAccuracy &&
                    manager.rollMeleeAccuracy(
                        source = this,
                        target = target,
                        attackType = attack.type,
                        attackStyle = attack.style,
                        blockType = attack.type,
                        multiplier = 1.0,
                    )
            val secondDamage =
                if (secondAccuracy) {
                    manager.rollMeleeMaxHit(
                        source = this,
                        target = target,
                        attackType = attack.type,
                        attackStyle = attack.style,
                        multiplier = 0.5,
                        roundUp = true,
                    )
                } else {
                    0
                }

            manager.queueMeleeHit(this, target, firstDamage, delay = 1)
            manager.queueMeleeHit(this, target, secondDamage, delay = 2)
            applyBloodragerDelay(firstAccuracy, secondAccuracy)
            return firstDamage + secondDamage
        }

        private fun ProtectedAccess.applyBloodragerDelay(
            firstAccurate: Boolean,
            secondAccurate: Boolean,
        ) {
            if (
                !EquipmentChecks.isBloodMoonSet(
                    player.hat,
                    player.torso,
                    player.legs,
                    player.righthand,
                )
            ) {
                return
            }

            // Each successful hitsplat rolls independently. With two successful hits this is
            // 1 - (2/3 * 2/3) = 5/9, matching the live-game Bloodrager behavior.
            val firstProc = firstAccurate && random.randomBoolean(BLOODRAGER_DENOMINATOR)
            val secondProc = secondAccurate && random.randomBoolean(BLOODRAGER_DENOMINATOR)
            if (firstProc || secondProc) {
                manager.setNextAttackDelay(this, BLOODRAGER_ATTACK_DELAY)
            }
        }
    }

    private companion object {
        const val BLOODRAGER_DENOMINATOR: Int = 3
        const val BLOODRAGER_ATTACK_DELAY: Int = 3
    }
}
