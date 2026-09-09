package org.rsmod.api.bosses.runtime

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.validation.SpecValidator
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.heal
import org.rsmod.api.npc.owner.hasSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcHit
import org.rsmod.api.script.onResolveNpcHit
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.plugin.scripts.ScriptContext

object BossCombat {

    fun register(
        ctx: ScriptContext,
        spec: BossSpec,
        deps: BossDeps,
        ownerSlug: String = spec.npcTypes.first().removePrefix("npc.").replace('_', '-'),
        onLethal: ((Npc) -> Unit)? = null,
        beforeModifyHit: (NpcHitEvents.Modify.() -> Unit)? = null,
        onModifyHit: (NpcHitEvents.Modify.() -> Unit)? = null,
        onResolveHit: (NpcHitEvents.Resolve.() -> Unit)? = null,
        onImpactHit: (NpcHitEvents.Impact.() -> Unit)? = null,
        onAbilityCommitted: (suspend (Npc, Player, BossEncounter) -> Unit)? = null,
    ) {
        val errors = SpecValidator.validate(spec)
        if (errors.isNotEmpty()) {
            val msg = errors.joinToString("\n") { "  - ${it.message}" }
            error("Boss spec validation failed for '${spec.npcTypes.joinToString()}':\n$msg")
        }

        val npcTypes =
            spec.npcTypes.map { name ->
                val npcId = name.asRSCM(RSCMType.NPC)
                ServerCacheManager.getNpc(npcId) ?: error("Boss NPC type not found: $name")
            }

        for (npcType in npcTypes) {
            deps.encounterRegistry.register(npcType.id, spec, ownerSlug)
        }

        with(ctx) {
            for (npcType in npcTypes) {
                onAiOpPlayer2(npcType) { runCombatTick(it.target, spec, deps, onAbilityCommitted) }
                onAiApPlayer2(npcType) { runCombatTick(it.target, spec, deps, onAbilityCommitted) }
                if (onResolveHit != null) onResolveNpcHit(npcType, onResolveHit)
                if (onImpactHit != null) onNpcHit(npcType, onImpactHit)
                onModifyNpcHit(npcType) {
                    val encounter = deps.encounterRegistry.of(npc)
                    beforeModifyHit?.invoke(this)
                    hit.damage =
                        if (encounter.invulnerable) 0
                        else (hit.damage * encounter.damageScale).toInt()
                    onModifyHit?.invoke(this)
                    if (
                        onLethal != null &&
                            !encounter.lethalHandled &&
                            npc.hitpoints - hit.damage <= 0
                    ) {
                        encounter.lethalHandled = true
                        onLethal(npc)
                    }
                }
            }

            val bossIds = npcTypes.map { it.id }.toSet()
            // Ensures phases and other states are reset
            onEvent<NpcStateEvents.Respawn> {
                if (npc.type.id in bossIds || npc.visType.id in bossIds) resetBoss(npc, deps)
            }
            onEvent<NpcStateEvents.Delete> {
                if (npc.type.id in bossIds || npc.visType.id in bossIds) clearEncounter(npc, deps)
            }
        }
    }

    /**
     * Clears the boss's encounter and any scripted-control overrides left on the npc from the
     * previous fight, so a respawned boss starts clean in its first phase. The fresh
     * [BossEncounter] is created lazily on the next combat tick.
     */
    private fun resetBoss(npc: Npc, deps: BossDeps) {
        clearEncounter(npc, deps)
        npc.movementLocked = false
        npc.apRangeOverride = null
        npc.apRequiresLineOfSight = true
        npc.moveRestrict = npc.type.moveRestrict
        npc.ignoreCombatInteractions = false
        npc.clearFacingLock()
        npc.clearIdleAnim()
        npc.resetTransmog()
    }

    fun resetEncounter(npc: Npc, deps: BossDeps) {
        resetBoss(npc, deps)
        npc.heal(npc.baseHitpointsLvl)
    }

    private fun clearEncounter(npc: Npc, deps: BossDeps) {
        val ownedNpcs = deps.encounterRegistry.existing(npc)?.drainOwnedNpcs().orEmpty()
        try {
            // Retire hit/task scopes before deleting owned entities or invoking their hooks.
            deps.encounterRegistry.remove(npc)
        } finally {
            ownedNpcs.forEach { owned ->
                if (owned.isSlotAssigned) {
                    deps.npcRepo.del(owned, Int.MAX_VALUE)
                }
            }
        }
    }

    private suspend fun StandardNpcAccess.runCombatTick(
        target: Player,
        spec: BossSpec,
        deps: BossDeps,
        onAbilityCommitted: (suspend (Npc, Player, BossEncounter) -> Unit)?,
    ) {
        if (npc.hasSpawnOwner && !npc.isSpawnOwnedBy(target)) return
        val encounter = deps.encounterRegistry.of(npc)
        if (encounter.currentPhase == null) return
        val tick = deps.mapClock.cycle
        if (encounter.phaseEnteredTick < 0) encounter.phaseEnteredTick = tick

        checkAutoTransitions(this, target, encounter, tick, spec, deps)
        checkTriggers(this, target, spec, deps, encounter)

        // Native action locks (for example awakening) must not consume an ability opportunity.
        if (npc.actionDelay > tick) return
        val ticksSinceLastAttack = tick - encounter.lastAbilityTick
        val attackRate = encounter.currentPhase?.attackRate ?: spec.stats.attackRate
        if (ticksSinceLastAttack < attackRate) return

        val phase = encounter.currentPhase ?: return
        val abilityName =
            encounter.consumeForcedAbility()
                ?: encounter.selectAbility(phase.selector, tick, target)
                ?: return
        val effect = spec.abilities[abilityName] ?: return

        encounter.recordAbilityCommit(tick, abilityName, attackRate)

        val interpreter = EffectInterpreter(npc, target, spec, encounter, deps)
        interpreter.run(this, effect)
        onAbilityCommitted?.invoke(npc, target, encounter)
    }

    private suspend fun checkTriggers(
        access: StandardNpcAccess,
        target: Player,
        spec: BossSpec,
        deps: BossDeps,
        encounter: BossEncounter,
    ) {
        if (spec.triggers.isEmpty()) return
        for ((index, trigger) in spec.triggers.withIndex()) {
            if (index in encounter.firedTriggers) continue
            if (encounter.evaluate(trigger.condition, target)) {
                encounter.firedTriggers += index
                val interpreter = EffectInterpreter(encounter.npc, target, spec, encounter, deps)
                interpreter.run(access, trigger.effect)
            }
        }
    }

    private suspend fun checkAutoTransitions(
        access: StandardNpcAccess,
        target: Player,
        encounter: BossEncounter,
        tick: Int,
        spec: BossSpec,
        deps: BossDeps,
    ) {
        for ((name, phase) in spec.phases) {
            if (name == encounter.currentPhaseName) continue
            val entryHp = phase.entryHp ?: continue
            // Ensures that phases are only entered once
            if (name in encounter.firedPhaseEntries) continue
            val hpFraction =
                encounter.npc.hitpoints.toDouble() / encounter.npc.baseHitpointsLvl.coerceAtLeast(1)
            if (hpFraction <= entryHp) {
                encounter.firedPhaseEntries += name
                encounter.transitionTo(name, tick)
                phase.entry?.let { runEntry(access, target, it, spec, encounter, deps) }
                return
            }
        }

        val phase = spec.phases[encounter.currentPhaseName] ?: return
        val exitAfter = phase.exitAfter ?: return
        val nextPhase = phase.nextPhase ?: return
        if (tick - encounter.phaseEnteredTick >= exitAfter) {
            val next = spec.phases[nextPhase] ?: return
            encounter.transitionTo(nextPhase, tick)
            next.entry?.let { runEntry(access, target, it, spec, encounter, deps) }
        }
    }

    private suspend fun runEntry(
        access: StandardNpcAccess,
        target: Player,
        abilityName: String,
        spec: BossSpec,
        encounter: BossEncounter,
        deps: BossDeps,
    ) {
        val effect = spec.abilities[abilityName] ?: return
        EffectInterpreter(access.npc, target, spec, encounter, deps).run(access, effect)
    }
}
