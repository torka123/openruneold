package org.rsmod.content.bosses.vorkath

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.combat.NpcAttackOverride
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitValidity
import org.rsmod.game.map.collision.get
import org.rsmod.map.CoordGrid

@Singleton
internal class VorkathEncounterManager
@Inject
constructor(
    private val clock: MapClock,
    private val instances: InstanceManager,
    private val npcs: NpcRepository,
    private val interactions: AiPlayerInteractions,
    private val random: GameRandom,
    private val deps: BossDeps,
    private val bossBars: BossHpBarScript,
    private val effects: VorkathArenaEffects,
) {
    private val log = InlineLogger()
    internal val stateKey
        get() = VORKATH_ARENA_STATE

    private fun runFor(player: Player): VorkathArenaState? =
        instances
            .sessionForPlayer(player)
            ?.takeIf { it.key == VORKATH_INSTANCE_KEY }
            ?.stateOrNull(stateKey)

    private fun runFor(npc: Npc): VorkathArenaState? =
        instances.instanceForNpc(npc)?.let(instances::sessionForId)?.stateOrNull(stateKey)

    fun validateAssets() {
        listOf(
                VorkathAssets.SLEEPING,
                VorkathAssets.SLEEPING_NOOP,
                VorkathAssets.ACTIVE,
                VorkathAssets.SPAWN,
                VorkathAssets.TORFINN,
                VorkathAssets.TORFINN_COLLECT,
                VorkathAssets.TORFINN_RELLEKKA,
                VorkathAssets.TORFINN_COLLECT_RELLEKKA,
            )
            .forEach(::requireNpc)
        listOf(
                VorkathAssets.WAKE_ANIM,
                VorkathAssets.ICE_WALL_JUMP_ANIM,
                VorkathAssets.MELEE_ANIM,
                VorkathAssets.RANGED_ANIM,
                VorkathAssets.RANGED_UP_ANIM,
                VorkathAssets.ACID_ANIM,
                VorkathAssets.SPAWN_DEATH_ANIM,
                VorkathAssets.SPAWN_ATTACK_ANIM,
            )
            .forEach { internal ->
                val id = internal.asRSCM(RSCMType.SEQ)
                requireNotNull(ServerCacheManager.getAnim(id)) {
                    "Missing Vorkath sequence: $internal"
                }
            }
        listOf(
                VorkathAssets.RANGED_TRAVEL,
                VorkathAssets.MAGIC_TRAVEL,
                VorkathAssets.DRAGONFIRE_TRAVEL,
                VorkathAssets.VENOM_DRAGONFIRE_TRAVEL,
                VorkathAssets.PRAYER_DRAGONFIRE_TRAVEL,
                VorkathAssets.RANGED_IMPACT,
                VorkathAssets.MAGIC_IMPACT,
                VorkathAssets.FIREBALL_TRAVEL,
                VorkathAssets.RAPID_FIRE_TRAVEL,
                VorkathAssets.ACID_TRAVEL,
                VorkathAssets.SPAWN_TRAVEL,
            )
            .forEach { it.asRSCM(RSCMType.SPOTANIM) }
        VORKATH_CRATER_ENTRANCE_LOC_IDS.forEach {
            requireNotNull(ServerCacheManager.getObject(it)) { "Missing Vorkath entrance loc: $it" }
        }
        effects.acidPoolType()
    }

    fun onJoin(player: Player, session: InstanceSession) {
        if (session.stateOrNull(stateKey) != null) return
        val sleeping =
            instances.npcsForInstance(session.id).single {
                it.type.internalName == VorkathAssets.SLEEPING
            }
        sleeping.assignSpawnOwner(player, clock.cycle)
        sleeping.mode = null
        sleeping.movementLocked = true
        val run =
            session.state(stateKey) {
                VorkathArenaState(
                    player,
                    session,
                    session.id.value,
                    sleeping,
                    firstSpecial = VorkathRules.firstSpecial(random.of(2)),
                    combat = deps.encounterRegistry.of(sleeping),
                    clock = clock,
                )
            }
        ownResources(run)
        player.lootDropDuration?.let { player.attr[VORKATH_PREVIOUS_DROP_DURATION] = it }
        player.lootDropDuration = VORKATH_DROP_DURATION
        player.mes("Vorkath is sleeping. Poke him when you are ready.")
        log.info { "[Vorkath] player=${player.displayName} instance=${session.id}" }
    }

    /** Only arena resources: native InstanceManager owns membership and entity retirement. */
    internal fun ownResources(run: VorkathArenaState) {
        run.session.onCleanup { releaseResources(run) }
    }

    fun onLeave(player: Player, session: InstanceSession) {
        session.stateOrNull(stateKey)?.takeIf { it.player === player }?.let(::releaseResources)
    }

    private fun releaseResources(run: VorkathArenaState) {
        if (run.resourcesReleased) return
        run.resourcesReleased = true
        run.state = VorkathState.ENDED
        effects.clearMechanics(run)
        deps.encounterRegistry.remove(run.boss)
        bossBars.onClose(run.player, run.boss, instant = true)
        // Logout defers map destruction, but account state must be restored before saving.
        run.player.lootDropDuration = run.player.attr[VORKATH_PREVIOUS_DROP_DURATION]
        run.player.attr.remove(VORKATH_PREVIOUS_DROP_DURATION)
        prepare(run.player)
    }

    fun poke(player: Player, npc: Npc): Boolean {
        val run = runFor(player) ?: return false
        if (!effects.inArena(run) || !player.isSlotAssigned || player.hitpoints <= 0) return false
        if (run.state != VorkathState.SLEEPING || run.boss !== npc || !npc.isSpawnOwnedBy(player))
            return false
        npc.mode = null
        npc.movementLocked = true
        run.state = VorkathState.AWAKENING
        run.wakeCycle = clock.cycle + 7
        run.combat.scheduleAt(deps.worldQueues, clock, run.wakeCycle) {
            if (
                runFor(player) === run &&
                    effects.inArena(run) &&
                    run.state == VorkathState.AWAKENING &&
                    player.hitpoints > 0 &&
                    npc.isSlotAssigned
            )
                activate(run)
        }
        npc.transmog(requireNpc(VorkathAssets.SLEEPING_NOOP), Int.MAX_VALUE)
        npc.anim(VorkathAssets.WAKE_ANIM)
        player.mes("Vorkath awakens.")
        return true
    }

    fun tickAll() {
        instances
            .sessionsForKey(VORKATH_INSTANCE_KEY)
            .toList()
            .mapNotNull { it.stateOrNull(stateKey) }
            .forEach { tick(it.player) }
    }

    internal fun auditRun(player: Player): VorkathArenaState? = runFor(player)

    fun tick(player: Player) {
        val run = runFor(player) ?: return
        // Membership cleanup belongs to InstanceLifecycleScript. Guard effects during the
        // interval before its logout/death/teleport hook retires the native session.
        if (!player.isSlotAssigned || player.pendingLogout || player.loggingOut) return
        if (player.hitpoints <= 0) {
            // Keep membership until the death hook chooses the existing Vorkath reclaim behavior.
            if (run.state != VorkathState.ENDED) {
                run.state = VorkathState.ENDED
                effects.clearMechanics(run)
                bossBars.onClose(player, run.boss, instant = true)
            }
            return
        }
        if (!effects.inArena(run)) return
        if (run.state in VORKATH_COMBAT_STATES && !run.boss.isSlotAssigned) {
            instances.terminate(run.session)
            return
        }
        if (run.state in VORKATH_COMBAT_STATES && run.boss.hitpoints <= 0) {
            // The death queue runs later in the tick. Do not launch pending effects before it.
            effects.clearMechanics(run)
            return
        }
        if (run.state in VORKATH_COMBAT_STATES) bossBars.onUpdate(player, run.boss)
        if (run.state == VorkathState.ACTIVE) keepAggressive(run)
    }

    /** Delegate unique arena deadlines to the shared, encounter-cancelled world queue. */
    fun attackParameters(
        npc: Npc,
        target: Player,
        attack: VorkathStandardAttack,
    ): NpcAttackOverride {
        val run = requireNotNull(runFor(npc))
        return VorkathAttackParameters.resolve(npc, target, attack)
            .copy(hitValidity = effects.hitValidity(run))
    }

    fun fireball(npc: Npc) {
        runFor(npc)?.let(effects::launchFireball)
    }

    fun special(npc: Npc) {
        runFor(npc)?.let(effects::beginSpecial)
    }

    fun isEncounterNpc(npc: Npc): Boolean = runFor(npc)?.owns(npc) == true

    fun isOwnedBy(player: Player, npc: Npc): Boolean =
        runFor(player)?.let { it.owns(npc) && npc.isSpawnOwnedBy(player) } == true

    fun attackDenial(player: Player, npc: Npc): String? {
        val run = runFor(player) ?: return "You do not have an active Vorkath encounter."
        if (!run.owns(npc)) return "That creature belongs to another encounter."
        if (
            npc === run.boss &&
                run.state != VorkathState.ACTIVE &&
                run.state != VorkathState.ACID_SPECIAL
        ) {
            return if (run.state == VorkathState.SLEEPING || run.state == VorkathState.AWAKENING) {
                "Poke Vorkath to wake him first."
            } else {
                "Vorkath is immune during this phase."
            }
        }
        return null
    }

    fun modifyNpcHit(player: Player, npc: Npc, hit: HitBuilder) {
        val run = runFor(player)
        if (run == null || !run.owns(npc) || !npc.isSpawnOwnedBy(player)) {
            hit.damage = 0
            return
        }
        if (npc === run.boss) {
            val existingValidity = hit.validity
            val epoch = run.hitEpoch
            hit.validity = HitValidity {
                existingValidity?.isValid() != false &&
                    run.hitEpoch == epoch &&
                    effects.validTarget(run) &&
                    (run.state == VorkathState.ACTIVE || run.state == VorkathState.ACID_SPECIAL)
            }
            hit.damage =
                when (run.state) {
                    VorkathState.ACTIVE -> hit.damage
                    VorkathState.ACID_SPECIAL -> VorkathRules.acidDamage(hit.damage)
                    else -> 0
                }
        }
    }

    fun beginBossDeath(npc: Npc): VorkathArenaState? {
        val run = runFor(npc)?.takeIf { it.boss === npc } ?: return null
        if (
            run.state == VorkathState.DYING ||
                run.state == VorkathState.LOOTABLE ||
                run.state == VorkathState.RESETTING ||
                run.state == VorkathState.ENDED
        )
            return null
        run.state = VorkathState.DYING
        run.player.combatClearQueue()
        effects.clearMechanics(run)
        if (run.rewardEligible) {
            val newKillcount = run.player.vars["varp.total_vorkath_kills"] + 1
            VarPlayerIntMapSetter.set(run.player, "varp.total_vorkath_kills", newKillcount)
            run.killcount = newKillcount
            run.killTicks = (clock.cycle - run.startCycle).coerceAtLeast(0)
        }
        return run
    }

    fun finishBossDeath(npc: Npc) {
        val run = runFor(npc)?.takeIf { it.boss === npc } ?: return
        if (runFor(run.player) !== run) return
        if (run.rewardEligible) {
            val oldPb = run.player.attr[VORKATH_PERSONAL_BEST_TICKS]
            val personalBest = oldPb == null || run.killTicks < oldPb
            if (personalBest) run.player.attr[VORKATH_PERSONAL_BEST_TICKS] = run.killTicks
            val suffix = if (personalBest) " <col=ff0000>New personal best!</col>" else ""
            run.player.mes(
                "<col=ff9900>Vorkath kill ${run.killcount}: ${VorkathRules.formatTicks(run.killTicks)}.$suffix</col>"
            )
        } else {
            run.player.mes(
                "Developer Vorkath kill complete; no loot, killcount, or record awarded."
            )
        }
        run.state = VorkathState.LOOTABLE
        run.boss = npc
        respawn(run)
    }

    fun beginSpawnDeath(npc: Npc) {
        val run = runFor(npc)?.takeIf { it.spawn === npc } ?: return
        if (
            run.state != VorkathState.ZOMBIFIED_SPAWN_SPECIAL ||
                run.spawnDeathCycle != Int.MAX_VALUE
        )
            return
        npc.movementLocked = true
        npc.mode = null
        npc.hideAllOps()
        // Anchor to the lethal hit, independently of death-queue processing order.
        run.spawnDeathCycle = clock.cycle + 3
    }

    fun canFinishDeath(run: VorkathArenaState, npc: Npc): Boolean =
        runFor(run.player) === run &&
            run.boss === npc &&
            run.state == VorkathState.DYING &&
            effects.inArena(run) &&
            run.player.hitpoints > 0

    fun isActive(player: Player): Boolean = runFor(player)?.state != null

    fun wallTile(player: Player, localX: Int, inside: Boolean): CoordGrid? {
        val session = instances.sessionForPlayer(player) ?: return null
        if (session.key != VORKATH_INSTANCE_KEY) return null
        return wallTile(session, localX, inside)
    }

    internal fun wallTile(session: InstanceSession, localX: Int, inside: Boolean): CoordGrid? {
        if (localX !in VORKATH_WALL_MIN_LOCAL_X..VORKATH_WALL_MAX_LOCAL_X) return null
        val localZ = if (inside) VORKATH_WALL_INSIDE_LOCAL_Z else VORKATH_WALL_OUTSIDE_LOCAL_Z
        return instances.localCoord(session, RegionLocal(0, 35, 63, localX, localZ))
    }

    private fun activate(run: VorkathArenaState) {
        val sleeping = run.boss
        // Retire the old footprint before flagging the active NPC on the same tiles.
        if (sleeping.isSlotAssigned) npcs.del(sleeping, Int.MAX_VALUE)
        val active =
            effects.spawnNpc(
                run.player,
                run.session,
                VorkathAssets.ACTIVE,
                effects.bossCoord(run.session),
            )
        active.baseHitpointsLvl = VORKATH_MAX_HITPOINTS
        active.hitpoints = VORKATH_MAX_HITPOINTS
        active.movementLocked = true
        // Captures never replace Vorkath's casts with the generic 7954 defend sequence.
        active.suppressCombatDefendAnimation = true
        active.apRangeOverride = 32
        active.apRequiresLineOfSight = false
        run.boss = active
        run.combat = deps.encounterRegistry.of(active)
        run.state = VorkathState.ACTIVE
        active.actionDelay = clock.cycle + 1
        run.startCycle = clock.cycle
        active.apPlayer2(run.player, interactions)
        bossBars.onOpen(run.player, active)
    }

    internal fun respawn(run: VorkathArenaState) {
        effects.clearMechanics(run)
        bossBars.onClose(run.player, run.boss, instant = true)
        if (run.boss.isSlotAssigned) npcs.del(run.boss, Int.MAX_VALUE)
        val sleeping =
            effects.spawnNpc(
                run.player,
                run.session,
                VorkathAssets.SLEEPING,
                effects.bossCoord(run.session),
            )
        sleeping.movementLocked = true
        run.boss = sleeping
        run.combat = deps.encounterRegistry.of(sleeping)
        run.state = VorkathState.SLEEPING
        run.nextSpecial = VorkathRules.firstSpecial(random.of(2))
        run.shotsFired = 0
        run.wakeCycle = Int.MAX_VALUE
        run.player.mes("Vorkath settles back into a deep sleep.")
    }

    private fun keepAggressive(run: VorkathArenaState) {
        if (run.boss.isSlotAssigned && run.boss.mode == null) {
            run.boss.apPlayer2(run.player, interactions)
        }
    }

    private fun requireNpc(internal: String) =
        requireNotNull(ServerCacheManager.getNpc(internal.asRSCM(RSCMType.NPC))) {
            "Missing Vorkath npc definition: $internal"
        }

    private fun prepare(player: Player) {
        player.combatClearQueue()
        player.clearInteraction()
        player.resetAnim()
    }
}
