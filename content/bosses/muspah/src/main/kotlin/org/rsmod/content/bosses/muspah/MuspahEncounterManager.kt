package org.rsmod.content.bosses.muspah

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ProjAnimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.max
import org.rsmod.api.attr.BossRewardAttributes
import org.rsmod.api.bosses.runtime.BossPhaseClock
import org.rsmod.api.bosses.runtime.EncounterRegistry
import org.rsmod.api.combat.commons.CombatCorruption
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.random.GameRandom
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder
import org.rsmod.routefinder.collision.CollisionFlagMap

@Singleton
internal class MuspahEncounterManager
@Inject
constructor(
    private val clock: MapClock,
    private val instances: InstanceManager,
    private val interactions: AiPlayerInteractions,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
    private val bossEncounters: EncounterRegistry,
    private val corruption: CombatCorruption,
    private val effects: MuspahArenaEffects,
    private val phaseClock: BossPhaseClock,
) {

    fun validateAssets() {
        MuspahAssets.npcForms.forEach(::requireNpc)
        MuspahAssets.sequences.forEach { internal ->
            requireNotNull(ServerCacheManager.getAnim(internal.asRSCM(RSCMType.SEQ))) {
                "Missing Phantom Muspah sequence: $internal"
            }
        }
        MuspahAssets.spotanims.forEach { it.asRSCM(RSCMType.SPOTANIM) }
        projectileType(MuspahAssets.PROJECTILE_CONTROLLER)
        listOf(MuspahAssets.SPIKE_PRE, MuspahAssets.SPIKE, MuspahAssets.SPIKE_FOLLOW).forEach {
            internal ->
            requireNotNull(ServerCacheManager.getObject(internal.asRSCM(RSCMType.LOC))) {
                "Missing Phantom Muspah location: $internal"
            }
        }
        listOf(
                MuspahAssets.ENTRANCE_ID,
                MuspahAssets.EXIT_ID,
                MuspahAssets.ESCAPE_ID,
                MuspahAssets.SCOREBOARD_ID,
            )
            .forEach { id ->
                requireNotNull(ServerCacheManager.getObject(id)) {
                    "Missing Phantom Muspah world location: $id"
                }
            }
        requireItem("obj.xbows_crossbow_bolts_mithril_tipped_sapphire_enchanted")
        requireItem("obj.dragon_bolts_enchanted_sapphire")
        requireItem("obj.ancient_goblin_mace")
    }

    fun onJoin(player: Player, session: InstanceSession) {
        session.state(MUSPAH_ARENA_STATE) {
            val boss = instances.npcsForInstance(session.id).single { it.type.id == 12077 }
            val run = MuspahArenaState(player, session, boss, bossEncounters.of(boss))
            prepareRound(run)
            player.lootDropDuration = MUSPAH_DROP_DURATION
            session.onCleanup {
                run.encounter.transitionTo("invalidated", clock.cycle)
                effects.cleanupHazards(run)
                bossEncounters.remove(boss)
                corruption.clear(player)
                player.lootDropDuration = null
                prepare(player)
            }
            player.mes("The Phantom Muspah begins to emerge from the darkness.")
            run
        }
    }

    fun onNativeRespawn(npc: Npc) {
        val run = instances.muspahState(npc) ?: return
        if (run.boss === npc && run.state == MuspahState.RESPAWNING) run.pendingRespawn = true
    }

    fun tick(player: Player) {
        val run = instances.muspahState(player) ?: return
        if (!player.isSlotAssigned || player.pendingLogout || player.loggingOut) return
        if (run.pendingRespawn) {
            run.pendingRespawn = false
            run.encounter = bossEncounters.of(run.boss)
            prepareRound(run)
            player.mes("A new Phantom Muspah begins to emerge.")
        }
        corruption.tick(player)
        when (run.state) {
            MuspahState.STARTING,
            MuspahState.TRANSFORMING,
            MuspahState.SHOCKWAVE -> Unit
            MuspahState.LIGHTNING_CLOUDS -> effects.tickClouds(run)
            MuspahState.HOMING_SPIKES -> {
                effects.moveHoming(run)
                keepAggressive(run)
            }
            MuspahState.RANGED,
            MuspahState.MELEE,
            MuspahState.PRAYER_SHIELD,
            MuspahState.FINAL_STAND -> keepAggressive(run)
            MuspahState.RESPAWNING -> Unit
            MuspahState.DYING,
            MuspahState.INVALIDATED -> Unit
        }
    }

    fun combatRun(npc: Npc, target: Player): MuspahArenaState? =
        instances.muspahState(target)?.takeIf { it.boss === npc && isCombatState(it.state) }

    fun canAttack(npc: Npc, target: Player): Boolean =
        combatRun(npc, target) != null && validHit(npc, target)

    fun validHit(npc: Npc, target: Player): Boolean =
        instances.muspahState(target)?.let {
            it.boss === npc &&
                instances.sessionForPlayer(target)?.id == it.session.id &&
                target.isSlotAssigned &&
                target.hitpoints > 0 &&
                !target.pendingLogout &&
                !target.loggingOut &&
                it.state != MuspahState.INVALIDATED
        } == true

    fun modifyNpcHit(player: Player, npc: Npc, hit: HitBuilder) {
        val run = instances.muspahState(player)
        if (run == null || run.boss !== npc || !npc.isSpawnOwnedBy(player)) {
            hit.damage = 0
            return
        }
        if (!hit.isFromPlayer || hit.damage < 0) {
            hit.damage = 0
            return
        }
        if (run.state == MuspahState.PRAYER_SHIELD) {
            modifyShieldHit(run, hit)
            return
        }
        if (!isDamageableState(run.state)) {
            hit.damage = 0
            return
        }
        if (run.state == MuspahState.SHOCKWAVE && hit.damage >= npc.hitpoints) {
            if (run.shieldSkipPending) {
                run.shieldSkipPending = false
                hit.damage = 0
            } else {
                run.shieldSkipPending = true
                hit.damage = (npc.hitpoints - 1).coerceAtLeast(0)
            }
        }
        val damage = hit.damage.coerceAtLeast(0)
        val afterHitpoints = (npc.hitpoints - damage).coerceAtLeast(0)
        if (damage > 0 && run.state != MuspahState.LIGHTNING_CLOUDS) {
            run.eligibleDamage += damage
            if (
                MuspahRules.shouldCountPositiveAttack(
                    damage,
                    clock.cycle,
                    run.lastPositiveAttackCycle,
                )
            ) {
                run.positiveAttacks++
                run.lastPositiveAttackCycle = clock.cycle
            }
        }
        if (
            run.state != MuspahState.SHOCKWAVE &&
                run.state != MuspahState.FINAL_STAND &&
                MuspahRules.crossedShieldThreshold(afterHitpoints)
        ) {
            beginShieldTransition(run)
            return
        }
        if (canStartThresholdSpecial(run)) {
            if (MuspahRules.crossedFirstSpecial(afterHitpoints, run.firstSpecialFired)) {
                run.firstSpecialFired = true
                beginSpecial(run, MuspahRules.firstSpecial(run.initialForm))
                return
            }
            if (MuspahRules.crossedSecondSpecial(afterHitpoints, run.secondSpecialFired)) {
                run.secondSpecialFired = true
                beginSpecial(run, MuspahRules.secondSpecial(run.initialForm))
                return
            }
        }
        if (
            run.state in setOf(MuspahState.RANGED, MuspahState.MELEE) &&
                MuspahRules.canNormalSwap(
                    run.currentForm,
                    run.firstSwapComplete,
                    run.positiveAttacks,
                    run.eligibleDamage,
                )
        ) {
            beginTransformation(run)
        }
    }

    private fun modifyShieldHit(run: MuspahArenaState, hit: HitBuilder) {
        val incoming = hit.damage.coerceAtLeast(0)
        var drain = 0
        if (incoming > 0 && run.player.vars["varbit.prayer_smite"] == 1) {
            drain += MuspahRules.smiteDrain(incoming)
            run.shieldDrainSources += "Smite"
        }
        if (incoming > 0 && isSapphireBolt(hit) && random.of(100) < 25) {
            drain += MuspahRules.sapphireDrain()
            run.shieldDrainSources += "Sapphire bolts"
            run.player.mes("Your enchanted sapphire bolt tears into the prayer shield.")
        }
        if (incoming > 0 && hit.isRighthandObj(requireItem("obj.ancient_goblin_mace"))) {
            drain += incoming
            run.shieldDrainSources += "Ancient mace"
        }
        hit.damage = 0
        if (drain > 0) drainShield(run, drain)
        else run.player.mes("The Phantom Muspah's prayer shield mitigates regular damage.")
    }

    private fun isSapphireBolt(hit: HitBuilder): Boolean =
        hit.isSecondaryObj(
            requireItem("obj.xbows_crossbow_bolts_mithril_tipped_sapphire_enchanted")
        ) || hit.isSecondaryObj(requireItem("obj.dragon_bolts_enchanted_sapphire"))

    fun beginBossDeath(npc: Npc): MuspahArenaState? {
        val run = instances.muspahState(npc)?.takeIf { it.boss === npc } ?: return null
        if (run.state in setOf(MuspahState.DYING, MuspahState.RESPAWNING)) return null
        run.encounter.transitionTo("dying", clock.cycle)
        run.encounter.runCleanupActions()
        effects.cleanupHazards(run)
        corruption.clear(run.player)
        run.player.combatClearQueue()
        run.killTicks = (clock.cycle - run.killStartCycle).coerceAtLeast(0)
        run.player.attr[BossRewardAttributes.phantomMuspahLastKillTicks] = run.killTicks
        return run
    }

    fun finishBossDeath(run: MuspahArenaState) {
        if (run.state != MuspahState.DYING) return
        if (instances.muspahState(run.player) !== run) return
        val oldPb = run.player.attr[MUSPAH_PERSONAL_BEST_TICKS]
        val personalBest = oldPb == null || run.killTicks < oldPb
        if (personalBest) run.player.attr[MUSPAH_PERSONAL_BEST_TICKS] = run.killTicks
        val suffix = if (personalBest) " <col=ff0000>New personal best!</col>" else ""
        run.player.mes(
            "<col=ff9900>Phantom Muspah kill ${run.player.vars["varp.total_muspah_kills"]}: " +
                "${MuspahRules.formatTicks(run.killTicks)}.$suffix</col>"
        )
        run.encounter.transitionTo("respawning", clock.cycle)
    }

    fun isActive(player: Player): Boolean = (instances.muspahState(player) != null)

    fun isEncounterNpc(npc: Npc): Boolean = (instances.muspahState(npc)?.boss === npc)

    fun isOwnedBy(player: Player, npc: Npc): Boolean =
        instances.muspahState(player)?.let { it.boss === npc && npc.isSpawnOwnedBy(player) } == true

    fun attackDenial(player: Player, npc: Npc): String? {
        val run =
            instances.muspahState(player)
                ?: return "You do not have an active Phantom Muspah encounter."
        if (run.boss !== npc) return "That creature belongs to another encounter."
        return when (run.state) {
            MuspahState.STARTING -> "Wait for the Phantom Muspah to finish emerging."
            MuspahState.TRANSFORMING -> "The Phantom Muspah is changing form."
            MuspahState.DYING,
            MuspahState.RESPAWNING,
            MuspahState.INVALIDATED -> "The Phantom Muspah cannot be attacked right now."
            else -> null
        }
    }

    fun debugStatus(player: Player): String {
        val run = instances.muspahState(player) ?: return "Muspah: no active encounter."
        return "Muspah instance=${run.session.id} state=${run.state} form=${run.currentForm} " +
            "hp=${run.boss.hitpoints} shield=${run.shieldPoints} " +
            "hits=${run.positiveAttacks} damage=${run.eligibleDamage} " +
            "spikes=${run.staticSpikes.size}+${run.movingSpikes.size} " +
            "clouds=${run.clouds.size}"
    }

    /** Called by declared phase entry, not the player post-tick hook. */
    fun ordinaryArrival(npc: Npc, target: Player) {
        val run = instances.muspahState(target)?.takeIf { it.boss === npc } ?: return
        if (run.transformTarget != null) {
            completeTransformation(run)
        } else if (run.currentForm == MuspahForm.TELEPORT) {
            // The draft processes the last cloud step on the return tick before clearing it.
            effects.tickClouds(run)
            run.clouds.clear()
            npc.transmog(requireNpc(MuspahAssets.RANGED), Int.MAX_VALUE)
            npc.anim(MuspahAssets.TELEPORT_APPEAR)
            run.currentForm = MuspahForm.RANGED
            npc.actionDelay = clock.cycle + 3
        } else if (run.killStartCycle == 0) {
            run.killStartCycle = clock.cycle
        } else {
            effects.hardenMovingSpikes(run)
        }
        npc.apRangeOverride = if (run.currentForm == MuspahForm.MELEE) 1 else 10
        npc.apRequiresLineOfSight = true
        npc.apPlayer2(target, interactions)
    }

    private fun prepareRound(run: MuspahArenaState) {
        effects.cleanupHazards(run)
        val initial = MuspahRules.startingForm(random.of(2))
        val boss = run.boss
        boss.mode = null
        // Keep the opening form's native base/current stats; transmog alone is visual.
        boss.copyStats(requireNpc(internalFor(initial)))
        boss.baseHitpointsLvl = MUSPAH_MAX_HITPOINTS
        boss.hitpoints = MUSPAH_MAX_HITPOINTS
        boss.transmog(requireNpc(internalFor(initial)), Int.MAX_VALUE)
        boss.assignSpawnOwner(run.player, clock.cycle)
        boss.movementLocked = true
        boss.apRangeOverride = if (initial == MuspahForm.MELEE) 1 else 10
        boss.apRequiresLineOfSight = true
        boss.actionDelay = 0
        boss.showAllOps()
        run.currentForm = initial
        run.initialForm = initial
        run.transformTarget = null
        run.firstSwapComplete = false
        run.positiveAttacks = 0
        run.eligibleDamage = 0
        run.lastPositiveAttackCycle = Int.MIN_VALUE
        run.firstSpecialFired = false
        run.secondSpecialFired = false
        run.shieldPoints = 0
        run.stationaryMeleeAttacks = 0
        run.shieldSkipPending = false
        run.killStartCycle = 0
        run.killTicks = 0
        run.lastCloudHitCycle = Int.MIN_VALUE
        run.shieldDrainSources.clear()
        run.encounter.onCleanup { effects.cleanupHazards(run) }
        run.encounter.transitionTo("spawn-${initial.name}", clock.cycle)
        phaseClock.bind(boss, run.player)
        boss.anim(MuspahAssets.SPAWN)
        prepare(run.player)
    }

    private fun beginTransformation(run: MuspahArenaState) {
        if (run.state == MuspahState.TRANSFORMING) return
        run.transformTarget =
            if (run.currentForm == MuspahForm.RANGED) MuspahForm.MELEE else MuspahForm.RANGED
        run.encounter.transitionTo("swap-${run.transformTarget!!.name}", clock.cycle)
        run.boss.actionDelay = max(run.boss.actionDelay, clock.cycle + 5)
        run.boss.anim(MuspahAssets.ATTACK_SUMMON)
        effects.spawnCrack(run, run.player.coords)
    }

    private fun completeTransformation(run: MuspahArenaState) {
        val target = run.transformTarget ?: return
        run.boss.transmog(requireNpc(internalFor(target)), Int.MAX_VALUE)
        run.boss.anim(
            if (target == MuspahForm.MELEE) MuspahAssets.TRANSFORM_APPEAR_1
            else MuspahAssets.TRANSFORM_APPEAR_2
        )
        run.currentForm = target
        run.firstSwapComplete = true
        run.positiveAttacks = 0
        run.eligibleDamage = 0
        run.lastPositiveAttackCycle = Int.MIN_VALUE
        run.stationaryMeleeAttacks = 0
        run.transformTarget = null
    }

    private fun beginSpecial(run: MuspahArenaState, special: MuspahSpecial) {
        when (special) {
            MuspahSpecial.LIGHTNING_CLOUDS -> beginLightning(run)
            MuspahSpecial.HOMING_SPIKES -> beginHoming(run)
        }
    }

    private fun beginLightning(run: MuspahArenaState) {
        effects.hardenMovingSpikes(run)
        run.encounter.transitionTo("lightning", clock.cycle)
        run.currentForm = MuspahForm.TELEPORT
        run.clouds.clear()
        run.boss.transmog(requireNpc(MuspahAssets.TELEPORT), Int.MAX_VALUE)
        run.boss.anim(MuspahAssets.TELEPORT_DISAPPEAR)
        run.boss.movementLocked = true
        run.boss.actionDelay = clock.cycle + 40
        effects.lightningRoute(run)
    }

    private fun beginHoming(run: MuspahArenaState) {
        run.encounter.transitionTo("homing-${run.currentForm.name}", clock.cycle)
        run.boss.anim(MuspahAssets.ATTACK_SUMMON)
        if (!effects.startHoming(run)) {
            run.encounter.transitionTo(run.currentForm.name, clock.cycle)
            run.boss.apPlayer2(run.player, interactions)
        }
    }

    private fun beginShieldTransition(run: MuspahArenaState) {
        if (run.state == MuspahState.SHOCKWAVE || run.state == MuspahState.PRAYER_SHIELD) return
        if (run.state == MuspahState.HOMING_SPIKES) effects.hardenMovingSpikes(run)
        run.clouds.clear()
        val lock = if (run.boss.movementLocked) "locked" else "mobile"
        run.encounter.transitionTo("shockwave-$lock", clock.cycle)
        run.currentForm = MuspahForm.TELEPORT
        run.boss.transmog(requireNpc(MuspahAssets.TELEPORT), Int.MAX_VALUE)
        run.boss.anim(MuspahAssets.TELEPORT_DISAPPEAR)
        run.boss.teleport(collision, effects.arenaCenter(run.session))
        run.boss.anim(MuspahAssets.ATTACK_EXPLOSION)
        run.boss.actionDelay = clock.cycle + 7
    }

    fun shockwave(npc: Npc, target: Player) {
        val run = instances.muspahState(target)?.takeIf { it.boss === npc } ?: return
        effects.shockwave(run)
    }

    fun shieldArrival(npc: Npc, target: Player) {
        val run = instances.muspahState(target)?.takeIf { it.boss === npc } ?: return
        if (run.shieldSkipPending) {
            run.boss.hitpoints = 1
            beginFinal(run)
            return
        }
        run.boss.transmog(requireNpc(MuspahAssets.SHIELD), Int.MAX_VALUE)
        run.boss.anim(MuspahAssets.TRANSFORM_APPEAR_2)
        run.boss.baseDefenceLvl = 200
        run.boss.defenceLvl = 200
        run.currentForm = MuspahForm.SHIELD
        run.shieldPoints = MUSPAH_SHIELD_POINTS
        run.boss.apRangeOverride = 10
        run.boss.movementLocked = true
        run.boss.apPlayer2(run.player, interactions)
    }

    private fun drainShield(run: MuspahArenaState, amount: Int) {
        run.shieldPoints = MuspahRules.drainShield(run.shieldPoints, amount)
        run.player.mes("The Phantom Muspah has ${run.shieldPoints} prayer shield points remaining.")
        if (run.shieldPoints == 0 && run.state == MuspahState.PRAYER_SHIELD) beginFinal(run)
    }

    private fun beginFinal(run: MuspahArenaState) {
        run.boss.transmog(requireNpc(MuspahAssets.FINAL), Int.MAX_VALUE)
        run.boss.anim(MuspahAssets.TRANSFORM_APPEAR_1)
        run.currentForm = MuspahForm.FINAL
        run.encounter.transitionTo("FINAL", clock.cycle)
        run.shieldPoints = 0
        run.boss.actionDelay = clock.cycle + 3
        run.boss.apRangeOverride = 10
        run.boss.movementLocked = true
        effects.spawnCrack(run, run.player.coords)
        run.boss.apPlayer2(run.player, interactions)
    }

    fun onRangedAttack(run: MuspahArenaState) {
        if (run.state == MuspahState.PRAYER_SHIELD) {
            drainShield(run, MuspahRules.passiveShieldDrain())
            // Preserve the draft's extra expansion when passive drain breaks the shield.
            // This is retained draft behavior, not packet-certified.
            if (run.state == MuspahState.FINAL_STAND) finalSpikes(run.boss, run.player)
        }
    }

    fun finalSpikes(npc: Npc, target: Player) {
        val run = instances.muspahState(target)?.takeIf { it.boss === npc } ?: return
        if (run.state != MuspahState.FINAL_STAND) return
        effects.spawnCrack(run, run.player.coords)
        effects.spreadFinalSpikes(run)
    }

    private fun keepAggressive(run: MuspahArenaState) {
        if (run.boss.isSlotAssigned && run.boss.mode == null) {
            run.boss.apPlayer2(run.player, interactions)
        }
    }

    private fun canStartThresholdSpecial(run: MuspahArenaState): Boolean =
        run.state in setOf(MuspahState.RANGED, MuspahState.MELEE)

    private fun isCombatState(state: MuspahState): Boolean =
        state in
            setOf(
                MuspahState.RANGED,
                MuspahState.MELEE,
                MuspahState.HOMING_SPIKES,
                MuspahState.PRAYER_SHIELD,
                MuspahState.FINAL_STAND,
            )

    private fun isDamageableState(state: MuspahState): Boolean =
        state in
            setOf(
                MuspahState.RANGED,
                MuspahState.MELEE,
                MuspahState.HOMING_SPIKES,
                MuspahState.LIGHTNING_CLOUDS,
                MuspahState.SHOCKWAVE,
                MuspahState.FINAL_STAND,
            )

    private fun internalFor(form: MuspahForm): String =
        when (form) {
            MuspahForm.RANGED -> MuspahAssets.RANGED
            MuspahForm.MELEE -> MuspahAssets.MELEE
            MuspahForm.SHIELD -> MuspahAssets.SHIELD
            MuspahForm.FINAL -> MuspahAssets.FINAL
            MuspahForm.TELEPORT -> MuspahAssets.TELEPORT
        }

    private fun projectileType(internal: String): ProjAnimType =
        requireNotNull(ServerCacheManager.getProjectile(internal.asRSCM(RSCMType.PROJANIM))) {
            "Missing Phantom Muspah projectile controller: $internal"
        }

    private fun requireNpc(internal: String) =
        requireNotNull(ServerCacheManager.getNpc(internal.asRSCM(RSCMType.NPC))) {
            "Missing Phantom Muspah npc definition: $internal"
        }

    private fun requireItem(internal: String) =
        requireNotNull(ServerCacheManager.getItem(internal.asRSCM(RSCMType.OBJ))) {
            "Missing Phantom Muspah item interaction: $internal"
        }

    private fun prepare(player: Player) {
        player.combatClearQueue()
        player.clearInteraction()
        player.resetAnim()
    }
}
