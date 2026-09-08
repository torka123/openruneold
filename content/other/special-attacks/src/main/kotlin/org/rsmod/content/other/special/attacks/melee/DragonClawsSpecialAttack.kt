package org.rsmod.content.other.special.attacks.melee

import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.config.constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.MeleeSpecialAttack
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/**
 * Slice and Dice makes up to four normal-accuracy rolls against Slash defence. The first roll to
 * succeed determines the special's total damage range and how it is split between its four hits.
 */
class DragonClawsSpecialAttack : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val sliceAndDice = SliceAndDice(manager)
        registerMelee("obj.dragon_claws", sliceAndDice)
        registerMelee("obj.br_dragon_claws", sliceAndDice)
        registerMelee("obj.dragon_claws_ornament", sliceAndDice)
        registerMelee("obj.bh_dragon_claws_corrupted", sliceAndDice)
        registerMelee("obj.deadman_blighted_dragon_claws", sliceAndDice)
    }

    private class SliceAndDice(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            sliceAndDice(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            sliceAndDice(target, attack)
            return true
        }

        private fun ProtectedAccess.sliceAndDice(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ) {
            anim("seq.human_dragon_claws_spec")
            spotanim(
                spot = "spotanim.dragon_claws_spot",
                slot = constants.spotanim_slot_combat,
                height = 96,
            )

            val hits =
                DragonClawDamage.roll(
                    maxHit =
                        manager.calculateMeleeMaxHit(
                            source = this,
                            target = target,
                            attackType = attack.type,
                            attackStyle = attack.style,
                            multiplier = 1.0,
                        ),
                    rollAccuracy = {
                        manager.rollMeleeAccuracy(
                            source = this,
                            target = target,
                            attackType = attack.type,
                            attackStyle = attack.style,
                            blockType = MeleeAttackType.Slash,
                            multiplier = 1.0,
                        )
                    },
                    rollInclusive = { range -> random.of(range) },
                    rollExclusive = { maximum -> random.of(maximum) },
                )

            manager.giveCombatXp(this, target, attack, hits.sum())
            hits.forEach { hit -> manager.queueMeleeHit(this, target, hit) }
            manager.continueCombat(this, target)
        }
    }
}

/**
 * OSRS Dragon claws distribution, expressed separately so the order and boundaries remain
 * inspectable. Fractions are truncated, just as the game's damage calculations do.
 */
internal object DragonClawDamage {
    /**
     * [rollAccuracy] is evaluated until the first success; each remaining hit is then guaranteed.
     */
    fun roll(
        maxHit: Int,
        rollAccuracy: () -> Boolean,
        rollInclusive: (IntRange) -> Int,
        rollExclusive: (Int) -> Int,
    ): IntArray {
        if (maxHit <= 0) {
            return IntArray(4)
        }

        for (accuracyRoll in 0 until 4) {
            if (!rollAccuracy()) {
                continue
            }

            val total = rollInclusive(totalDamageRange(maxHit, accuracyRoll))
            return split(total, accuracyRoll)
        }

        return misses(rollExclusive)
    }

    private fun totalDamageRange(maxHit: Int, accuracyRoll: Int): IntRange {
        val low = maxHit * (4 - accuracyRoll) / 4
        val high = maxHit + low - 1
        return low..high
    }

    private fun split(total: Int, accuracyRoll: Int): IntArray =
        when (accuracyRoll) {
            0 ->
                intArrayOf(
                    total / 2,
                    total / 4,
                    total / 8,
                    (total / 8) + 1,
                )
            1 ->
                intArrayOf(
                    0,
                    total / 2,
                    total / 4,
                    (total / 4) + 1,
                )
            2 ->
                intArrayOf(
                    0,
                    0,
                    total / 2,
                    (total / 2) + 1,
                )
            3 -> intArrayOf(0, 0, 0, total + 1)
            else -> error("Expected an accuracy roll in [0, 3], got $accuracyRoll")
        }

    /**
     * When all four accuracy rolls fail, OSRS has a two-in-three chance to deal two one-damage
     * hits. The pair is placed in one of the four possible paired hit-splat layouts.
     */
    private fun misses(rollExclusive: (Int) -> Int): IntArray {
        if (rollExclusive(3) == 0) {
            return IntArray(4)
        }
        return when (rollExclusive(4)) {
            0 -> intArrayOf(1, 1, 0, 0)
            1 -> intArrayOf(0, 0, 1, 1)
            2 -> intArrayOf(1, 0, 1, 0)
            3 -> intArrayOf(0, 1, 0, 1)
            else -> error("Random source returned a value outside [0, 3]")
        }
    }
}
