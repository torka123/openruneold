package org.rsmod.content.other.special.attacks.melee

import kotlin.math.max
import kotlin.math.min
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.config.constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.MeleeSpecialAttack
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitImpactHandler

/**
 * Effects that must be applied from the final resolved hit rather than from its pre-roll damage.
 *
 * Keeping these together makes the post-impact timing explicit: a capped or fully absorbed hit
 * cannot drain stats, heal the attacker, or freeze a player.
 */
class ImpactMeleeSpecialAttacks : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val bandos = BandosGodsword(manager, "seq.bgs_special_player", BANDOS_STANDARD_SPOTANIM)
        registerMelee("obj.bgs", bandos)
        registerMelee(
            "obj.bgsg",
            BandosGodsword(manager, "seq.bgs_special_ornate_player", BANDOS_ORNATE_SPOTANIM),
        )

        val saradomin =
            SaradominGodsword(manager, "seq.sgs_special_player", SARADOMIN_STANDARD_SPOTANIM)
        registerMelee("obj.sgs", saradomin)
        registerMelee(
            "obj.sgsg",
            SaradominGodsword(manager, "seq.sgs_special_ornate_player", SARADOMIN_ORNATE_SPOTANIM),
        )

        val zamorak =
            ZamorakGodsword(manager, "seq.zgs_special_player", ZAMORAK_STANDARD_SPOTANIM)
        registerMelee("obj.zgs", zamorak)
        registerMelee(
            "obj.zgsg",
            ZamorakGodsword(manager, "seq.zgs_special_ornate_player", ZAMORAK_ORNATE_SPOTANIM),
        )

        val warhammer = DragonWarhammer(manager)
        registerMelee("obj.dragon_warhammer", warhammer)
        registerMelee("obj.br_dragon_warhammer", warhammer)
        registerMelee("obj.dragon_warhammer_ornament", warhammer)
        registerMelee("obj.bh_dragon_warhammer_corrupted", warhammer)
    }

    private class BandosGodsword(
        private val manager: SpecialAttackManager,
        private val sequence: String,
        private val graphic: String,
    ) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            bandosStrike(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            bandosStrike(target, attack)
            return true
        }

        private fun ProtectedAccess.bandosStrike(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ) {
            anim(sequence)
            spotanim(
                spot = graphic,
                slot = constants.spotanim_slot_combat,
                height = 96,
            )
            val damage =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = 2.0,
                    maxHitMultiplier = 1.21,
                    blockType = MeleeAttackType.Slash,
                )
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(
                source = this,
                target = target,
                damage = damage,
                impactHandler = HitImpactHandler { impact ->
                    if (impact.damage > 0) {
                        drainBandosStats(impact.target, impact.damage)
                    }
                },
            )
            manager.continueCombat(this, target)
        }
    }

    private class SaradominGodsword(
        private val manager: SpecialAttackManager,
        private val sequence: String,
        private val graphic: String,
    ) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            healingBlade(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            healingBlade(target, attack)
            return true
        }

        private fun ProtectedAccess.healingBlade(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ) {
            anim(sequence)
            spotanim(
                spot = graphic,
                slot = constants.spotanim_slot_combat,
                height = 96,
            )
            val damage =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = 2.0,
                    maxHitMultiplier = 1.1,
                    blockType = MeleeAttackType.Slash,
                )
            val source = player
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(
                source = this,
                target = target,
                damage = damage,
                impactHandler = HitImpactHandler { impact ->
                    if (impact.damage <= 0) {
                        return@HitImpactHandler
                    }
                    source.statHeal(
                        "stat.hitpoints",
                        constant = max(10, (impact.damage + 1) / 2),
                        percent = 0,
                    )
                    source.statHeal(
                        "stat.prayer",
                        constant = max(5, (impact.damage + 3) / 4),
                        percent = 0,
                    )
                },
            )
            manager.continueCombat(this, target)
        }
    }

    private class ZamorakGodsword(
        private val manager: SpecialAttackManager,
        private val sequence: String,
        private val graphic: String,
    ) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            iceCleaver(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            iceCleaver(target, attack)
            return true
        }

        private fun ProtectedAccess.iceCleaver(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ) {
            anim(sequence)
            spotanim(
                spot = graphic,
                slot = constants.spotanim_slot_combat,
                height = 96,
            )
            val damage =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = 2.0,
                    maxHitMultiplier = 1.1,
                    blockType = MeleeAttackType.Slash,
                )
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(
                source = this,
                target = target,
                damage = damage,
                impactHandler = HitImpactHandler { impact ->
                    if (impact.damage <= 0) return@HitImpactHandler
                    when (val impacted = impact.target) {
                        is Player -> CombatEffects.freeze(impacted, ZGS_FREEZE_TICKS)
                        is Npc -> CombatEffects.bind(impacted, ZGS_FREEZE_TICKS)
                    }
                },
            )
            manager.continueCombat(this, target)
        }
    }

    private class DragonWarhammer(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            smash(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            smash(target, attack)
            return true
        }

        private fun ProtectedAccess.smash(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ) {
            anim("seq.dragon_warhammer_sa_player")
            spotanim(
                spot = "spotanim.dragon_warhammer_sa_spotanim",
                slot = constants.spotanim_slot_combat,
                height = 96,
            )
            val damage =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = 1.0,
                    maxHitMultiplier = 1.5,
                    blockType = MeleeAttackType.Crush,
                )
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(
                source = this,
                target = target,
                damage = damage,
                impactHandler = HitImpactHandler { impact ->
                    if (impact.damage > 0) {
                        reduceDefence(impact.target)
                    }
                },
            )
            manager.continueCombat(this, target)
        }
    }

    internal companion object {
        const val ZGS_FREEZE_TICKS = 33
        const val BANDOS_STANDARD_SPOTANIM = "spotanim.dh_sword_update_bandos_special_spotanim"
        const val BANDOS_ORNATE_SPOTANIM = "spotanim.bandos_special_spotanim_gold"
        const val SARADOMIN_STANDARD_SPOTANIM = "spotanim.dh_sword_update_saradomin_special_spotanim"
        const val SARADOMIN_ORNATE_SPOTANIM = "spotanim.saradomin_special_spotanim_gold"
        const val ZAMORAK_STANDARD_SPOTANIM = "spotanim.dh_sword_update_zamorak_special_spotanim"
        const val ZAMORAK_ORNATE_SPOTANIM = "spotanim.zamorak_special_spotanim_gold"

        val BANDOS_PLAYER_STAT_ORDER =
            listOf(
                "stat.defence",
                "stat.strength",
                "stat.prayer",
                "stat.attack",
                "stat.magic",
                "stat.ranged",
            )
    }
}

private fun drainBandosStats(target: PathingEntity, amount: Int) {
    var remaining = amount
    when (target) {
        is Player -> {
            for (stat in ImpactMeleeSpecialAttacks.BANDOS_PLAYER_STAT_ORDER) {
                val drain = min(target.stat(stat), remaining)
                if (drain > 0) {
                    target.statSub(stat, constant = drain, percent = 0)
                    remaining -= drain
                }
                if (remaining == 0) {
                    return
                }
            }
        }
        is Npc -> {
            fun drain(current: Int, apply: (Int) -> Unit) {
                val value = min(current, remaining)
                if (value > 0) {
                    apply(value)
                    remaining -= value
                }
            }

            drain(target.defenceLvl) { target.defenceLvl -= it }
            drain(target.strengthLvl) { target.strengthLvl -= it }
            drain(target.attackLvl) { target.attackLvl -= it }
            drain(target.magicLvl) { target.magicLvl -= it }
            drain(target.rangedLvl) { target.rangedLvl -= it }
        }
    }
}

private fun reduceDefence(target: PathingEntity) {
    when (target) {
        is Player -> {
            val drain = target.stat("stat.defence") * 30 / 100
            if (drain > 0) {
                target.statSub("stat.defence", constant = drain, percent = 0)
            }
        }
        is Npc -> target.defenceLvl = target.defenceLvl * 70 / 100
    }
}
