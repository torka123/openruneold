package org.rsmod.content.bosses.vorkath

import jakarta.inject.Inject
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.output.mes
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType

/** Explicit administrator diagnostics. Every mutation forfeits the run's rewards and records. */
internal class VorkathDiagnostics
@Inject
constructor(
    private val encounters: VorkathEncounterManager,
    private val effects: VorkathArenaEffects,
    private val clock: MapClock,
    private val interactions: AiPlayerInteractions,
    private val npcHitModifier: StandardNpcHitModifier,
) {
    fun debugStatus(player: Player): String {
        val run = encounters.auditRun(player) ?: return "Vorkath: no active encounter."
        return "Vorkath generation=${run.generation} state=${run.state} hp=${run.boss.hitpoints} " +
            "attacks=${run.standardAttacks}/$VORKATH_STANDARD_ATTACKS next=${run.nextSpecial} " +
            "acid=${run.acidTiles.size} lane=${run.acidSafeLane.size} " +
            "shots=${run.shotsFired}/$VORKATH_ACID_SHOTS spawn=${run.spawn?.hitpoints ?: 0} " +
            "rewards=${run.rewardEligible}"
    }

    fun debugWake(player: Player): Boolean {
        val run = encounters.auditRun(player) ?: return debugMissing(player)
        run.rewardEligible = false
        if (run.state != VorkathState.SLEEPING) {
            player.mes("Vorkath must be sleeping to force the wake-up.")
            return false
        }
        return encounters.poke(player, run.boss)
    }

    fun debugSpecial(player: Player, special: VorkathSpecial): Boolean {
        val run = activeDebugRun(player) ?: return false
        run.rewardEligible = false
        effects.clearMechanics(run)
        run.state = VorkathState.ACTIVE
        run.nextSpecial = special
        run.boss.actionDelay = clock.cycle
        effects.beginSpecial(run)
        player.mes("Forced Vorkath special: $special (no rewards).")
        return true
    }

    fun debugAttack(player: Player, attack: VorkathStandardAttack): Boolean {
        val run = activeDebugRun(player) ?: return false
        run.rewardEligible = false
        run.combat.lastAbilityTick = clock.cycle - VORKATH_ATTACK_RATE
        run.combat.forceNextAbility(attack.name)
        run.boss.actionDelay = clock.cycle
        run.boss.apPlayer2(player, interactions)
        player.mes("Forced Vorkath attack: $attack (no rewards).")
        return true
    }

    fun debugReset(player: Player): Boolean {
        val run = encounters.auditRun(player) ?: return debugMissing(player)
        run.rewardEligible = false
        run.state = VorkathState.RESETTING
        effects.clearMechanics(run)
        encounters.respawn(run)
        player.mes("Vorkath encounter reset to sleeping state (no rewards).")
        return true
    }

    fun debugKill(player: Player): Boolean {
        val run = activeDebugRun(player) ?: return false
        run.rewardEligible = false
        effects.clearMechanics(run)
        run.state = VorkathState.ACTIVE
        run.boss.queueHit(
            source = player,
            delay = 1,
            type = HitType.Typeless,
            damage = run.boss.hitpoints.coerceAtLeast(1),
            modifier = npcHitModifier,
            specific = true,
        )
        player.mes("Developer kill queued; this awards no loot, killcount, or record.")
        return true
    }

    private fun activeDebugRun(player: Player): VorkathArenaState? {
        val run = encounters.auditRun(player) ?: return debugMissing(player).let { null }
        if (run.state != VorkathState.ACTIVE) {
            player.mes("Wake Vorkath and wait until he is active before forcing this mechanic.")
            return null
        }
        return run
    }

    private fun debugMissing(player: Player): Boolean {
        player.mes("Enter a Vorkath encounter first with ::vorkath and the ice chunks.")
        return false
    }
}
