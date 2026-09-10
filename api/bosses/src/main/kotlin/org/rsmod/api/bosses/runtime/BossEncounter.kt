package org.rsmod.api.bosses.runtime

import java.util.IdentityHashMap
import org.rsmod.api.bosses.spec.*
import org.rsmod.api.random.GameRandom
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitScope
import org.rsmod.game.queue.WorldQueueList

data class BossAttackTrace(
    val ability: String,
    val commitTick: Int,
    var expectedRate: Int,
    var style: HitType? = null,
    var expectedImpactTick: Int? = null,
    var actualImpactTick: Int? = null,
)

class BossEncounter(val npc: Npc, val spec: BossSpec, private val random: GameRandom) {
    var currentPhaseName: String = spec.phases.keys.firstOrNull() ?: ""
    var phaseEnteredTick: Int = -1
    var lastAbilityTick: Int = 0
    var lastAbilityName: String? = null
    var invulnerable: Boolean = false
    var damageScale: Double = 1.0
    var lethalHandled: Boolean = false
    var phaseAbilityCount: Int = 0
        private set

    /** Official upstream override; native attacks and ability readiness use the same cadence. */
    var attackRateOverride: Int? = null
        set(value) {
            require(value == null || value > 0) { "Attack rate must be positive." }
            field = value
            attackTraces.lastOrNull()?.expectedRate = expectedAttackRate()
        }

    internal val firedTriggers = mutableSetOf<Int>()
    internal val firedPhaseEntries = mutableSetOf<String>()

    private class SelectionState {
        val cooldowns = mutableMapOf<String, Int>()
        var rotationCursor = 0
    }

    private val phaseSelection = SelectionState()
    private val branchSelections = IdentityHashMap<Effect.Choose, SelectionState>()
    private val cooldowns
        get() = phaseSelection.cooldowns

    private val forcedTickLastFired = mutableMapOf<String, Int>()
    private var rotationCursor: Int
        get() = phaseSelection.rotationCursor
        set(value) {
            phaseSelection.rotationCursor = value
        }

    private var basicAttackCount = 0
    var lastRotationIndex: Int = -1
        private set

    private var forceAttackThreshold = -1
    private val externalMarks = mutableSetOf<String>()
    private val ownedNpcs = mutableSetOf<Npc>()
    private val attackTraces = ArrayDeque<BossAttackTrace>()
    private var forcedAbility: String? = null
    private val cleanupActions = mutableListOf<() -> Unit>()
    private val encounterState = mutableMapOf<String, Any>()
    private var phaseClock: (() -> Unit)? = null
    private var cancelPhaseDeadline: (() -> Unit)? = null
    internal val hasPhaseClock: Boolean
        get() = phaseClock != null

    internal val effectScope = HitScope()
    var isEnded: Boolean = false
        private set

    init {
        npc.movementLocked = currentPhase?.lockMovement == true
    }

    val currentPhase: PhaseSpec?
        get() = spec.phases[currentPhaseName]

    /** Returns true exactly once for an encounter-scoped external mechanic marker. */
    fun fireOnce(marker: String): Boolean = externalMarks.add(marker)

    fun own(npc: Npc) {
        ownedNpcs += npc
    }

    /** Detaches a retired summon without draining other live resources from this encounter. */
    fun disown(npc: Npc) {
        ownedNpcs.remove(npc)
    }

    fun drainOwnedNpcs(): Set<Npc> = ownedNpcs.toSet().also { ownedNpcs.clear() }

    fun owned(): List<Npc> = ownedNpcs.toList()

    /** Registers encounter-owned world state (locs, hazards, etc.) for deterministic cleanup. */
    fun onCleanup(action: () -> Unit): () -> Unit {
        check(!isEnded) { "Encounter has ended." }
        cleanupActions += action
        return {
            cleanupActions.remove(action)
            Unit
        }
    }

    fun runCleanupActions() {
        val actions = cleanupActions.toList()
        cleanupActions.clear()
        encounterState.clear()
        branchSelections.clear()
        var failure: Throwable? = null
        actions.forEach { action ->
            try {
                action()
            } catch (error: Throwable) {
                if (failure == null) failure = error else failure!!.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }

    internal fun end() {
        if (isEnded) return
        isEnded = true
        phaseClock = null
        cancelPhaseDeadline?.invoke()
        cancelPhaseDeadline = null
        effectScope.close()
        forcedAbility = null
        externalMarks.clear()
        runCleanupActions()
    }

    /** Owns work on the existing world queue, releasing both callback and cleanup registration. */
    fun schedule(queues: WorldQueueList, ticks: Int, action: () -> Unit) {
        check(!isEnded) { "Encounter has ended." }
        var unregister: () -> Unit = {}
        val cancellation =
            queues.addCancellable(ticks) {
                unregister()
                if (!isEnded) action()
            }
        unregister = onCleanup(cancellation::cancel)
    }

    /** Exact pre-input deadline on the existing world queue, independent of enqueue stage. */
    fun scheduleAt(queues: WorldQueueList, clock: MapClock, tick: Int, action: () -> Unit) {
        check(!isEnded) { "Encounter has ended." }
        var unregister: () -> Unit = {}
        val cancellation =
            queues.addCancellable(0, ready = { clock.cycle >= tick }) {
                unregister()
                if (!isEnded) action()
            }
        unregister = onCleanup(cancellation::cancel)
    }

    /** Stores mechanic state on this encounter instead of in a plugin-global combat singleton. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> state(key: String, create: () -> T): T {
        check(!isEnded) { "Encounter has ended." }
        val existing = encounterState[key]
        if (existing != null) return existing as T
        return create().also { encounterState[key] = it }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> stateOrNull(key: String): T? = encounterState[key] as? T

    fun forceNextAbility(name: String): Boolean {
        if (isEnded) return false
        if (name !in spec.abilities) return false
        forcedAbility = name
        return true
    }

    fun consumeForcedAbility(): String? = forcedAbility.also { forcedAbility = null }

    fun recordAbilityCommit(tick: Int, ability: String, expectedRate: Int) {
        phaseAbilityCount++
        if (
            currentPhase?.forceAbilities?.any { it.attackMin == null && it.ability == ability } ==
                true
        ) {
            forcedTickLastFired[ability] = tick
        }
        lastAbilityTick = tick
        lastAbilityName = ability
        attackTraces +=
            BossAttackTrace(ability = ability, commitTick = tick, expectedRate = expectedRate)
        while (attackTraces.size > 100) {
            attackTraces.removeFirst()
        }
    }

    fun recentAbilityCommitTicks(limit: Int = 20): List<Int> =
        attackTraces.takeLast(limit.coerceAtLeast(0)).map { it.commitTick }

    fun recordScheduledImpact(style: HitType, expectedTick: Int) {
        val trace = attackTraces.lastOrNull() ?: return
        if (trace.expectedImpactTick == null) {
            trace.style = style
            trace.expectedImpactTick = expectedTick
        }
    }

    fun recordActualImpact(expectedTick: Int, actualTick: Int) {
        attackTraces
            .lastOrNull { it.expectedImpactTick == expectedTick && it.actualImpactTick == null }
            ?.actualImpactTick = actualTick
    }

    fun recentAttackTraces(limit: Int = 20): List<BossAttackTrace> =
        attackTraces.takeLast(limit.coerceAtLeast(0)).map { it.copy() }

    fun attackTracesAfter(tick: Int): List<BossAttackTrace> =
        attackTraces.filter { it.commitTick > tick }.map { it.copy() }

    fun expectedAttackRate(): Int =
        attackRateOverride ?: currentPhase?.attackRate ?: spec.stats.attackRate

    fun transitionTo(phaseName: String, tick: Int) {
        cancelPhaseDeadline?.invoke()
        cancelPhaseDeadline = null
        val from = currentPhaseName
        currentPhaseName = phaseName
        phaseEnteredTick = tick
        rotationCursor = 0
        lastRotationIndex = -1
        cooldowns.clear()
        branchSelections.clear()
        forcedTickLastFired.clear()
        basicAttackCount = 0
        phaseAbilityCount = 0
        forceAttackThreshold = -1

        val phase = spec.phases[phaseName]

        val idle = phase?.idleAnim
        if (idle != null) npc.setIdleAnim(idle) else npc.clearIdleAnim()

        npc.movementLocked = phase?.lockMovement == true

        npc.clearFacingLock()
        phaseClock?.invoke()
    }

    /** One native timed-phase owner per encounter; ordinary combat keeps its own readiness. */
    internal fun bindPhaseClock(arm: () -> Unit) {
        check(!isEnded) { "Encounter has ended." }
        if (hasPhaseClock) return
        phaseClock = arm
        arm()
    }

    internal fun ownPhaseDeadline(cancel: () -> Unit) {
        cancelPhaseDeadline = cancel
    }

    fun selectAbility(selector: Selector, tick: Int, target: Player? = null): String? {
        val phase = currentPhase ?: return null

        for (forced in phase.forceAbilities) {
            if (forced.attackMin != null) continue
            val lastFired = forcedTickLastFired[forced.ability] ?: phaseEnteredTick
            if (tick - lastFired >= forced.period) {
                forcedTickLastFired[forced.ability] = tick
                return forced.ability
            }
        }

        val attackForced = phase.forceAbilities.firstOrNull { it.attackMin != null }
        if (attackForced != null) {
            if (forceAttackThreshold < 0) {
                forceAttackThreshold =
                    attackForced.firstAttackCount ?: randomThreshold(attackForced)
            }
            if (basicAttackCount >= forceAttackThreshold) {
                basicAttackCount = 0
                forceAttackThreshold = randomThreshold(attackForced)
                return attackForced.ability
            }
        }

        val selected = select(selector, tick, target, phaseSelection)
        if (selected != null && attackForced != null) {
            basicAttackCount++
        }
        return selected
    }

    /** A nested effect chooses a branch, not another phase attack opportunity. */
    internal fun selectBranch(choice: Effect.Choose, tick: Int, target: Player?): String? =
        select(
            choice.selector,
            tick,
            target,
            branchSelections.getOrPut(choice) { SelectionState() },
        )

    private fun select(
        selector: Selector,
        tick: Int,
        target: Player?,
        state: SelectionState,
    ): String? =
        when (selector) {
            is Selector.WeightedRandom -> selectWeightedRandom(selector, tick, target, state)
            is Selector.Rotation -> selectRotation(selector, state)
            is Selector.MeleeOverrideRotation ->
                selectMeleeOverrideRotation(selector, target, state)
            is Selector.Conditional ->
                selector.branches
                    .firstOrNull { (condition, _) -> evaluate(condition, target) }
                    ?.second ?: selector.fallback
        }

    private fun randomThreshold(forced: ForcedAbility): Int {
        val min = forced.attackMin ?: return Int.MAX_VALUE
        val max = (forced.attackMax ?: min).coerceAtLeast(min)
        return if (max == min) min else random.of(min, max)
    }

    private fun selectWeightedRandom(
        selector: Selector.WeightedRandom,
        tick: Int,
        target: Player? = null,
        state: SelectionState,
    ): String? {
        val available =
            selector.entries.filter { ref ->
                val onCooldown =
                    state.cooldowns[ref.ability]?.let { tick - it < ref.cooldown } ?: false
                !onCooldown && evaluate(ref.requires, target)
            }

        if (available.isEmpty()) return null

        val totalWeight = available.sumOf { it.weight }
        if (totalWeight <= 0) return null

        var roll = random.of(totalWeight)
        for (ref in available) {
            roll -= ref.weight
            if (roll < 0) {
                state.cooldowns[ref.ability] = tick
                if (state === phaseSelection) lastAbilityName = ref.ability
                return ref.ability
            }
        }
        return available.last().ability
    }

    private fun selectRotation(selector: Selector.Rotation, state: SelectionState): String? {
        if (selector.sequence.isEmpty()) return null
        val index = state.rotationCursor % selector.sequence.size
        if (state === phaseSelection) lastRotationIndex = index
        val ability = selector.sequence[index]
        state.rotationCursor++
        return ability
    }

    private fun selectMeleeOverrideRotation(
        selector: Selector.MeleeOverrideRotation,
        target: Player?,
        state: SelectionState,
    ): String? {
        if (selector.sequence.isEmpty()) return null
        val index = state.rotationCursor % selector.sequence.size
        if (state === phaseSelection) lastRotationIndex = index
        state.rotationCursor++
        // Melee is a positional replacement for this attack opportunity. It must not pause the
        // underlying rotation: phase switches, movement, and specials remain tied to the same
        // deterministic opportunity index even when a player steps next to a large boss.
        return if (target != null && npc.isWithinDistance(target, 1)) {
            selector.meleeAbility
        } else {
            selector.sequence[index]
        }
    }

    fun evaluate(condition: Condition, target: Player? = null): Boolean {
        return when (condition) {
            is Condition.Always -> true
            is Condition.OnSpawn -> false
            is Condition.OnDeath -> false
            is Condition.WithinMeleeRange -> {
                target != null &&
                    target.coords.level == npc.coords.level &&
                    npc.isWithinDistance(target, 1)
            }
            is Condition.HpBelow -> {
                val fraction = npc.hitpoints.toDouble() / npc.baseHitpointsLvl.coerceAtLeast(1)
                fraction < condition.fraction
            }
            is Condition.HpExact -> npc.hitpoints == condition.hp
            is Condition.InPhase -> currentPhaseName == condition.phase
            is Condition.AbilityCountModulo ->
                phaseAbilityCount % condition.modulus == condition.remainder
            is Condition.Not -> !evaluate(condition.c, target)
            is Condition.And -> evaluate(condition.a, target) && evaluate(condition.b, target)
            is Condition.Or -> evaluate(condition.a, target) || evaluate(condition.b, target)
            is Condition.EveryNTicks -> false
            is Condition.OnPhaseTick -> false
            is Condition.IncomingHitDamageAtLeast -> false
            is Condition.PlayerEnterRange ->
                target != null &&
                    target.coords.level == npc.coords.level &&
                    npc.isWithinDistance(target, condition.tiles)
            is Condition.TargetPraying ->
                target != null &&
                    when (condition.type) {
                        HitType.Melee -> target.vars["varbit.prayer_protectfrommelee"] == 1
                        HitType.Ranged -> target.vars["varbit.prayer_protectfrommissiles"] == 1
                        HitType.Magic -> target.vars["varbit.prayer_protectfrommagic"] == 1
                        else -> false
                    }
        }
    }
}
