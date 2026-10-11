package org.rsmod.content.bosses.cerberus

import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.InstanceStateKey
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid

/** Boss-specific state attached to the native InstanceSession, never a parallel membership map. */
internal val CERBERUS_RUN_STATE = InstanceStateKey<CerberusRun>("cerberus-arena")

internal data class CerberusPendingLava(val tile: CoordGrid, val activateCycle: Int)

internal data class CerberusActiveLava(
    val tile: CoordGrid,
    val endCycle: Int,
    var nextDamageCycle: Int,
)

internal data class CerberusRun(
    val player: Player,
    val session: InstanceSession,
    val boss: Npc,
    var encounter: BossEncounter,
    val clock: MapClock,
    var pendingRespawn: Boolean = false,
    var diagnosticCounterOffset: Int = 0,
    var lastPlayerCoord: CoordGrid? = null,
    var resetArrived: Boolean = false,
    var attackAnimationUntilCycle: Int = 0,
    var forcedAttack: CerberusAttack? = null,
    var forcedNormalStyle: CerberusAttackStyle? = null,
    var forcedSkip: Boolean? = null,
    var soulWaveSpawned: Boolean = false,
    var negatedSoulAttacks: Int = 0,
    var tookLavaDamage: Boolean = false,
    var rewardEligible: Boolean = true,
    var tookMeleeDamage: Boolean = false,
    val pendingLava: MutableList<CerberusPendingLava> = mutableListOf(),
    val activeLava: MutableList<CerberusActiveLava> = mutableListOf(),
    val souls: MutableList<Npc> = mutableListOf(),
    val fireWallLocs: MutableList<LocInfo> = mutableListOf(),
) {
    var phase: CerberusPhase
        get() =
            when (encounter.currentPhaseName) {
                "standing",
                "standing-ready" -> CerberusPhase.STANDING
                "resetting",
                "reset-travel",
                "reset-timeout",
                "reset-sit",
                "reset-ready" -> CerberusPhase.RESETTING
                else ->
                    CerberusPhase.valueOf(requireNotNull(encounter.currentPhaseName).uppercase())
            }
        set(value) {
            encounter.transitionTo(value.name.lowercase(), clock.cycle)
        }

    val attackNumber: Int
        get() = encounter.phaseAbilityCount + diagnosticCounterOffset + 1

    fun isCurrent(
        instances: InstanceManager,
        arena: CerberusArena,
        encounter: BossEncounter = this.encounter,
        activeOnly: Boolean = true,
    ): Boolean =
        instances.sessionForPlayer(player)?.stateOrNull(CERBERUS_RUN_STATE) === this &&
            instances.sessionForPlayer(player)?.id == session.id &&
            arena.contains(session, player.coords) &&
            arena.contains(session, boss.coords) &&
            player.coords.level == boss.coords.level &&
            this.encounter === encounter &&
            !encounter.isEnded &&
            (!activeOnly || phase == CerberusPhase.ACTIVE) &&
            boss.isSlotAssigned &&
            boss.hitpoints > 0 &&
            player.isSlotAssigned &&
            player.hitpoints > 0

    fun playAttackAnimation(animation: Int, lockTicks: Int) {
        attackAnimationUntilCycle = maxOf(attackAnimationUntilCycle, clock.cycle + lockTicks)
        boss.anim(animation, priority = CerberusRules.ATTACK_ANIMATION_PRIORITY)
    }
}
