package org.rsmod.api.mechanics.toxins

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.IdentityHashMap
import org.rsmod.api.config.refs.done.hitmark_groups
import org.rsmod.api.config.refs.params
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitAttribution
import org.rsmod.game.hit.HitContext
import org.rsmod.game.hit.HitOrigin
import org.rsmod.game.hit.HitType
import org.rsmod.game.queue.WorldQueueList

/**
 * The shared Old School "Burn" status used by effects such as Burning claws and Flames of Ralos.
 *
 * Each stack independently queues one typeless point of damage every four game cycles, ten times.
 * Stacks are intentionally kept only in memory while active: a pending tick verifies that its
 * target is still a valid living entity before it queues damage, which prevents an effect from
 * surviving a death, despawn, or logout.
 */
@Singleton
public class BurnEffectService
@Inject
constructor(
    private val worldQueues: WorldQueueList,
    private val npcHitModifier: StandardNpcHitModifier,
) {
    private val active: IdentityHashMap<PathingEntity, MutableList<BurnStack>> = IdentityHashMap()

    /**
     * Applies one standard Burn stack, if [target] is burnable and has fewer than [maxStacks]
     * active stacks. A standard stack deals [DAMAGE_PER_STACK] damage over [DURATION_CYCLES].
     *
     * @return true only when a new stack was applied.
     */
    public fun apply(source: Player, target: PathingEntity, maxStacks: Int = MAX_STACKS): Boolean =
        applyStack(source, target, DAMAGE_PER_STACK, maxStacks)

    /** Applies an NPC-sourced Burn with an explicit remaining-damage value. */
    public fun apply(
        source: Npc,
        target: Player,
        damage: Int,
        maxStacks: Int = MAX_STACKS,
    ): Boolean = applyStack(source, target, damage, maxStacks)

    private fun applyStack(
        source: PathingEntity,
        target: PathingEntity,
        damage: Int,
        maxStacks: Int,
    ): Boolean {
        require(maxStacks > 0) { "maxStacks must be positive." }
        require(damage > 0) { "damage must be positive." }
        prune(target)
        if (!target.isBurnable()) {
            return false
        }

        val context =
            HitContext.capture(source, target)
                .withAttribution(HitAttribution(HitOrigin.Burn, (source as? Player)?.uuid))
        if (!context.isValid()) return false
        val stacks = active.getOrPut(target) { mutableListOf() }
        if (stacks.size >= maxStacks) {
            return false
        }
        val stack = BurnStack(source, target, damage, context)
        stacks += stack
        arm(stack)
        return true
    }

    /** Returns the number of currently active Burn stacks on [target]. */
    public fun stackCount(target: PathingEntity): Int {
        prune(target)
        return active[target]?.size ?: 0
    }

    /** Returns whether [target] currently has any active Burn stacks. */
    public fun isBurned(target: PathingEntity): Boolean = stackCount(target) > 0

    /**
     * Removes every active Burn stack from [target] and returns its remaining future damage.
     *
     * Queues already emitted by a burn tick are intentionally not retroactively cancelled; this
     * mirrors consuming only the damage that the effect would deal from this point forward.
     */
    public fun consumeRemainingDamage(target: PathingEntity): Int {
        prune(target)
        val stacks = active.remove(target) ?: return 0
        return stacks.sumOf { it.remainingDamage }
    }

    /** Cancels active stacks emitted by [source], without touching burns from another encounter. */
    public fun clearSource(source: PathingEntity): Unit {
        val entries = active.entries.iterator()
        while (entries.hasNext()) {
            val entry = entries.next()
            entry.value.removeAll { it.source === source }
            if (entry.value.isEmpty()) entries.remove()
        }
    }

    private fun arm(stack: BurnStack) {
        worldQueues.add(TICK_INTERVAL) { tick(stack) }
    }

    private fun prune(target: PathingEntity) {
        val stacks = active[target] ?: return
        stacks.removeAll { !it.context.isValid() }
        if (stacks.isEmpty()) active.remove(target)
    }

    private fun tick(stack: BurnStack) {
        if (!isActive(stack) || !stack.context.isValid() || !stack.target.isBurnable()) {
            remove(stack)
            return
        }

        queueBurnDamage(stack)
        stack.remainingDamage--
        if (stack.remainingDamage <= 0) {
            remove(stack)
        } else {
            arm(stack)
        }
    }

    /**
     * Burn damage goes through the normal hit queues rather than mutating hitpoints directly, so
     * hitmarks, damage attribution, boss hit processing, deaths, and final-damage hooks all remain
     * consistent with other combat damage. Typeless damage naturally bypasses protection prayers.
     */
    private fun queueBurnDamage(stack: BurnStack) {
        when (val target = stack.target) {
            is Npc -> {
                val source = stack.source as? Player ?: return
                target.queueHit(
                    source = source,
                    delay = 1,
                    type = HitType.Typeless,
                    damage = 1,
                    hitmark = hitmark_groups.burn,
                    modifier = npcHitModifier,
                    context = stack.context,
                )
            }

            is Player ->
                when (val source = stack.source) {
                    is Player ->
                        target.queueHit(
                            source,
                            1,
                            HitType.Typeless,
                            1,
                            hitmark_groups.burn,
                            context = stack.context,
                        )
                    is Npc ->
                        target.queueHit(
                            source,
                            1,
                            HitType.Typeless,
                            1,
                            hitmark_groups.burn,
                            context = stack.context,
                        )
                }
        }
    }

    private fun isActive(stack: BurnStack): Boolean = active[stack.target]?.contains(stack) == true

    private fun remove(stack: BurnStack) {
        val stacks = active[stack.target] ?: return
        stacks.remove(stack)
        if (stacks.isEmpty()) {
            active.remove(stack.target)
        }
    }

    private fun PathingEntity.isBurnable(): Boolean =
        when (this) {
            is Npc -> isValidTarget() && visType.param(params.burn_immunity) == 0
            is Player -> isValidTarget()
        }

    private class BurnStack(
        val source: PathingEntity,
        val target: PathingEntity,
        var remainingDamage: Int,
        val context: HitContext,
    )

    public companion object {
        public const val MAX_STACKS: Int = 5
        public const val DAMAGE_PER_STACK: Int = 10
        public const val TICK_INTERVAL: Int = 4
        public const val DURATION_CYCLES: Int = DAMAGE_PER_STACK * TICK_INTERVAL
    }
}
