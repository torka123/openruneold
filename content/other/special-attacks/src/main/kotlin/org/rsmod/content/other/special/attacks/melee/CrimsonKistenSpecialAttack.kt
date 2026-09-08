package org.rsmod.content.other.special.attacks.melee

import dev.openrune.CrimsonKisten
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.config.constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.random.GameRandom
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.MeleeSpecialAttack
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/** OSRS Crimson kisten's 50% Brutal Swing special attack. */
public class CrimsonKistenSpecialAttack @Inject constructor(private val random: GameRandom) :
    SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        registerMelee(
            specWeapon = CrimsonKisten.ITEM,
            energyInHundreds = CrimsonKisten.SPECIAL_ENERGY,
            special = BrutalSwing(manager, random),
        )
    }

    private class BrutalSwing(
        private val manager: SpecialAttackManager,
        private val random: GameRandom,
    ) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean = brutalSwing(target, attack)

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean = brutalSwing(target, attack)

        private fun ProtectedAccess.brutalSwing(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            if (player.righthand?.id != CrimsonKisten.ITEM || attack.weapon?.id != CrimsonKisten.ITEM) {
                return false
            }

            player.anim(CrimsonKisten.SPECIAL)
            player.spotanim(
                spot = CrimsonKisten.SPECIAL_GRAPHIC,
                slot = constants.spotanim_slot_combat,
            )

            val successes =
                (1..4).count {
                    manager.rollMeleeAccuracy(
                        source = this,
                        target = target,
                        attackType = MeleeAttackType.Crush,
                        attackStyle = attack.style,
                        blockType = MeleeAttackType.Crush,
                        multiplier = 1.0,
                    )
                }
            val damage =
                when (successes) {
                    0 -> 0
                    else -> {
                        val normalMax =
                            manager.calculateMeleeMaxHit(
                                source = this,
                                target = target,
                                attackType = MeleeAttackType.Crush,
                                attackStyle = attack.style,
                                multiplier = 1.0,
                            )
                        val range = BrutalSwingDamage.range(normalMax, successes)
                        if (range.first == range.last) range.first else random.of(range)
                    }
                }

            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            manager.continueCombat(this, target)
            return true
        }
    }
}

internal object BrutalSwingDamage {
    fun range(normalMax: Int, successfulRolls: Int): IntRange {
        require(successfulRolls in 1..4) { "Expected successful rolls in [1..4]: $successfulRolls" }
        val (minimumPercent, maximumPercent) =
            when (successfulRolls) {
                1 -> 70 to 110
                2 -> 90 to 130
                3 -> 110 to 150
                4 -> 130 to 170
                else -> error("Unreachable")
            }
        val minimum = normalMax.percentOf(minimumPercent)
        val maximum = (normalMax.percentOf(maximumPercent) - 1).coerceAtLeast(minimum)
        return minimum..maximum
    }

    private fun Int.percentOf(percent: Int): Int = (toLong() * percent / 100L).toInt()
}
