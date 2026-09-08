package org.rsmod.content.minigames.toa

import org.rsmod.api.player.stat.statSub
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitImpact
import org.rsmod.game.hit.HitImpactHandler
import org.rsmod.game.hit.HitType

internal object ToaWipeRules {
    /** A disconnect by itself is never a team wipe; every eligible participant must have died. */
    fun isTrueWipe(participants: Collection<ToaParticipant>, completionPending: Boolean): Boolean {
        if (completionPending) return false
        val eligible = participants.filter(ToaParticipant::rewardEligible)
        return eligible.isNotEmpty() && eligible.all(ToaParticipant::spectating)
    }
}

internal object ToaPrayerInvocationRules {
    fun hasMatchingProtectionPrayer(player: Player, type: HitType): Boolean =
        when (type) {
            HitType.Typeless -> false
            HitType.Melee -> player.vars["varbit.prayer_protectfrommelee"] == 1
            HitType.Ranged -> player.vars["varbit.prayer_protectfrommissiles"] == 1
            HitType.Magic -> player.vars["varbit.prayer_protectfrommagic"] == 1
        }

    fun deadlyImpactHandler(
        player: Player,
        type: HitType,
        invocations: Set<ToaInvocation>,
    ): HitImpactHandler? {
        if (ToaInvocation.DEADLY_PRAYERS !in invocations || type == HitType.Typeless) return null
        val protectedAtCalculation = hasMatchingProtectionPrayer(player, type)
        return HitImpactHandler { impact ->
            val drain =
                ToaRules.deadlyPrayerDrain(
                    landedDamage = impact.damage,
                    matchingProtectionPrayer = protectedAtCalculation,
                    invocations = invocations,
                )
            if (drain > 0) player.statSub("stat.prayer", constant = drain, percent = 0)
        }
    }

    fun compose(first: HitImpactHandler?, second: HitImpactHandler?): HitImpactHandler? =
        when {
            first == null -> second
            second == null -> first
            else ->
                HitImpactHandler { impact ->
                    first.onImpact(impact)
                    second.onImpact(impact)
                }
        }

    fun standardCombatImpact(impact: HitImpact, invocations: Set<ToaInvocation>) {
        val player = impact.target as? Player ?: return
        val drain =
            ToaRules.deadlyPrayerDrain(
                landedDamage = impact.damage,
                matchingProtectionPrayer = hasMatchingProtectionPrayer(player, impact.hit.type),
                invocations = invocations,
            )
        if (drain > 0) player.statSub("stat.prayer", constant = drain, percent = 0)
    }
}

internal object ToaNpcStatScaling {
    private val scalableRoles =
        setOf(
            ToaNpcRole.BOSS,
            ToaNpcRole.CHALLENGE,
            ToaNpcRole.WARDEN_PHASE,
            ToaNpcRole.OBELISK,
            ToaNpcRole.ADD,
        )

    fun apply(npc: Npc, raid: ToaRaidSession, role: ToaNpcRole) {
        if (role !in scalableRoles) return
        val pathLevel = raid.currentPath?.let { raid.pathLevels[it] } ?: 0
        npc.baseAttackLvl = ToaRules.combatStat(npc.baseAttackLvl, raid.raidLevel, pathLevel)
        npc.attackLvl = npc.baseAttackLvl
        npc.baseStrengthLvl = ToaRules.combatStat(npc.baseStrengthLvl, raid.raidLevel, pathLevel)
        npc.strengthLvl = npc.baseStrengthLvl
        npc.baseDefenceLvl = ToaRules.combatStat(npc.baseDefenceLvl, raid.raidLevel, pathLevel)
        npc.defenceLvl = npc.baseDefenceLvl
        npc.baseRangedLvl = ToaRules.combatStat(npc.baseRangedLvl, raid.raidLevel, pathLevel)
        npc.rangedLvl = npc.baseRangedLvl
        npc.baseMagicLvl = ToaRules.combatStat(npc.baseMagicLvl, raid.raidLevel, pathLevel)
        npc.magicLvl = npc.baseMagicLvl
    }
}
