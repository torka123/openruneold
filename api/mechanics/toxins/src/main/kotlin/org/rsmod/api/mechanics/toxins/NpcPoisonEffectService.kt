package org.rsmod.api.mechanics.toxins

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.IdentityHashMap
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.done.hitmark_groups
import org.rsmod.api.config.refs.params
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.mechanics.toxins.impl.PlayerVenom
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.isValidTarget
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitAttribution
import org.rsmod.game.hit.HitContext
import org.rsmod.game.hit.HitOrigin
import org.rsmod.game.hit.HitType
import org.rsmod.game.queue.WorldQueueList

/**
 * Applies the standard decreasing poison sequence to NPCs.
 *
 * Player poison is stored in a player varp. NPCs have no matching persistent var, so active poison
 * is represented by a small in-memory state object. Pending ticks verify identity before acting,
 * which means a stronger re-poison, death, or despawn safely invalidates older ticks.
 */
@Singleton
public class NpcPoisonEffectService
@Inject
constructor(
    private val worldQueues: WorldQueueList,
    private val npcHitModifier: StandardNpcHitModifier,
) {
    private val active: IdentityHashMap<Npc, Poison> = IdentityHashMap()
    private val venomActive: IdentityHashMap<Npc, Venom> = IdentityHashMap()

    /**
     * Applies poison that begins with [initialDamage].
     *
     * The timing and decreasing damage sequence intentionally mirror [PlayerPoison]: the initial
     * hit occurs next cycle, then the remaining poison severity ticks every thirty cycles.
     */
    public fun apply(
        source: Player,
        target: Npc,
        initialDamage: Int = DEFAULT_INITIAL_DAMAGE,
    ): Boolean = apply(source, target, initialDamage, immediateFirstHit = true)

    /**
     * Applies poison status now and schedules its first damage for the standard 30-tick interval.
     */
    public fun applyAfterInterval(
        source: Player,
        target: Npc,
        initialDamage: Int = DEFAULT_INITIAL_DAMAGE,
    ): Boolean = apply(source, target, initialDamage, immediateFirstHit = false)

    private fun apply(
        source: Player,
        target: Npc,
        initialDamage: Int,
        immediateFirstHit: Boolean,
    ): Boolean {
        prune(target)
        if (initialDamage <= 0 || !target.isPoisonable() || venomActive.containsKey(target)) {
            return false
        }

        val incomingSeverity = PlayerPoison.severityForInitialDamage(initialDamage)
        val previous = active[target]
        if (previous != null && incomingSeverity <= previous.severity) {
            return false
        }

        val poison =
            Poison(source, target, incomingSeverity, toxinContext(source, target, HitOrigin.Poison))
        if (!poison.context.isValid()) return false
        active[target] = poison
        if (immediateFirstHit) {
            queuePoisonHit(poison, initialDamage)
            poison.severity--
            if (poison.severity <= 0) {
                remove(poison)
                return true
            }
        }
        arm(poison)
        return true
    }

    /**
     * Applies the project's standard escalating venom sequence to an NPC.
     *
     * Revision-240's partial venom-immunity value converts venom to normal poison; full immunity
     * rejects it. This keeps the existing toxin service as the sole owner of NPC toxin state.
     */
    public fun applyVenom(source: Player, target: Npc): Boolean {
        prune(target)
        if (!target.isPoisonable() || venomActive.containsKey(target)) {
            return false
        }
        when (target.visType.param(params.venom_immunity)) {
            constants.npc_venom_full_immunity -> return false
            constants.npc_venom_partial_immunity ->
                return apply(source, target, PlayerVenom.damageForStrikeIndex(0))
        }

        val venom = Venom(source, target, toxinContext(source, target, HitOrigin.Venom))
        if (!venom.context.isValid()) return false
        active.remove(target)
        venomActive[target] = venom
        queueVenomHit(venom, PlayerVenom.damageForStrikeIndex(0))
        arm(venom)
        return true
    }

    /** Returns whether [target] currently has an active poison sequence. */
    public fun isPoisoned(target: Npc): Boolean {
        prune(target)
        return active.containsKey(target)
    }

    /** Returns whether [target] currently has an active venom sequence. */
    public fun isEnvenomed(target: Npc): Boolean {
        prune(target)
        return venomActive.containsKey(target)
    }

    private fun prune(target: Npc) {
        if (active[target]?.context?.isValid() == false) active.remove(target)
        if (venomActive[target]?.context?.isValid() == false) venomActive.remove(target)
    }

    private fun arm(poison: Poison) {
        worldQueues.add(TICK_INTERVAL) { tick(poison) }
    }

    private fun tick(poison: Poison) {
        if (!isActive(poison) || !poison.context.isValid() || !poison.target.isPoisonable()) {
            remove(poison)
            return
        }

        queuePoisonHit(poison = poison, damage = PlayerPoison.damageForSeverity(poison.severity))
        poison.severity--
        if (poison.severity <= 0) {
            remove(poison)
        } else {
            arm(poison)
        }
    }

    private fun arm(venom: Venom) {
        worldQueues.add(TICK_INTERVAL) { tick(venom) }
    }

    private fun tick(venom: Venom) {
        if (!isActive(venom) || !venom.context.isValid() || !venom.target.isPoisonable()) {
            remove(venom)
            return
        }
        queueVenomHit(venom, PlayerVenom.damageForStrikeIndex(venom.strikesCompleted))
        venom.strikesCompleted++
        arm(venom)
    }

    private fun queuePoisonHit(poison: Poison, damage: Int) {
        poison.target.queueHit(
            source = poison.source,
            delay = 1,
            type = HitType.Typeless,
            damage = damage,
            hitmark = hitmark_groups.poison_damage,
            modifier = npcHitModifier,
            context = poison.context,
        )
    }

    private fun queueVenomHit(venom: Venom, damage: Int) {
        venom.target.queueHit(
            source = venom.source,
            delay = 1,
            type = HitType.Typeless,
            damage = damage,
            hitmark = hitmark_groups.venom,
            modifier = npcHitModifier,
            context = venom.context,
        )
    }

    private fun isActive(poison: Poison): Boolean = active[poison.target] === poison

    private fun isActive(venom: Venom): Boolean = venomActive[venom.target] === venom

    private fun remove(poison: Poison) {
        if (isActive(poison)) {
            active.remove(poison.target)
        }
    }

    private fun remove(venom: Venom) {
        if (isActive(venom)) {
            venomActive.remove(venom.target)
        }
    }

    private fun Npc.isPoisonable(): Boolean =
        !combatImmunities.toxins && isValidTarget() && visType.param(params.poison_immunity) == 0

    private fun toxinContext(source: Player, target: Npc, origin: HitOrigin): HitContext =
        HitContext.capture(source, target).withAttribution(HitAttribution(origin, source.uuid))

    private class Poison(
        val source: Player,
        val target: Npc,
        var severity: Int,
        val context: HitContext,
    )

    private class Venom(
        val source: Player,
        val target: Npc,
        val context: HitContext,
        var strikesCompleted: Int = 1,
    )

    public companion object {
        public const val DEFAULT_INITIAL_DAMAGE: Int = 4
        public const val TICK_INTERVAL: Int = PlayerPoison.TICK_INTERVAL
    }
}
