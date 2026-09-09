package org.rsmod.content.bosses.vorkath

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import dev.openrune.types.ObjectServerType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.combat.NpcAttackOverride
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.InstanceStateKey
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.heal
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.StepFactory
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.hit.HitValidity
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.get
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@Singleton
internal class VorkathEncounterManager
@Inject
constructor(
    private val clock: MapClock,
    private val instances: InstanceManager,
    private val npcs: NpcRepository,
    private val locs: LocRepository,
    private val interactions: AiPlayerInteractions,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
    private val deps: BossDeps,
    private val world: WorldRepository,
    private val npcHitModifier: StandardNpcHitModifier,
    private val bossBars: BossHpBarScript,
) {
    private val log = InlineLogger()
    private val spawnSteps = StepFactory(collision)
    private val COMBAT_STATES =
        setOf(VorkathState.ACTIVE, VorkathState.ACID_SPECIAL, VorkathState.ZOMBIFIED_SPAWN_SPECIAL)
    internal val stateKey = InstanceStateKey<Run>("vorkath-arena")

    private fun runFor(player: Player): Run? =
        instances
            .sessionForPlayer(player)
            ?.takeIf { it.key == VORKATH_INSTANCE_KEY }
            ?.stateOrNull(stateKey)

    private fun runFor(npc: Npc): Run? =
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
        acidPoolType()
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
                Run(
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
    internal fun ownResources(run: Run) {
        run.session.onCleanup {
            run.state = VorkathState.ENDED
            clearMechanics(run)
            deps.encounterRegistry.remove(run.boss)
            bossBars.onClose(run.player, run.boss, instant = true)
            run.player.lootDropDuration = run.player.attr[VORKATH_PREVIOUS_DROP_DURATION]
            run.player.attr.remove(VORKATH_PREVIOUS_DROP_DURATION)
            prepare(run.player)
        }
    }

    fun poke(player: Player, npc: Npc): Boolean {
        val run = runFor(player) ?: return false
        if (!inArena(run) || !player.isSlotAssigned || player.hitpoints <= 0) return false
        if (run.state != VorkathState.SLEEPING || run.boss !== npc || !npc.isSpawnOwnedBy(player))
            return false
        npc.mode = null
        npc.movementLocked = true
        run.state = VorkathState.AWAKENING
        run.wakeCycle = clock.cycle + 7
        run.combat.scheduleAt(deps.worldQueues, clock, run.wakeCycle) {
            if (
                runFor(player) === run &&
                    inArena(run) &&
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

    internal fun auditRun(player: Player): Run? = runFor(player)

    fun tick(player: Player) {
        val run = runFor(player) ?: return
        // Membership cleanup belongs to InstanceLifecycleScript. Guard effects during the
        // interval before its logout/death/teleport hook retires the native session.
        if (!player.isSlotAssigned || player.pendingLogout || player.loggingOut) return
        if (player.hitpoints <= 0) {
            // Keep membership until the death hook chooses the existing Vorkath reclaim behavior.
            if (run.state != VorkathState.ENDED) {
                run.state = VorkathState.ENDED
                clearMechanics(run)
                bossBars.onClose(player, run.boss, instant = true)
            }
            return
        }
        if (!inArena(run)) return
        if (run.state in COMBAT_STATES && !run.boss.isSlotAssigned) {
            instances.terminate(run.session)
            return
        }
        if (run.state in COMBAT_STATES && run.boss.hitpoints <= 0) {
            // The death queue runs later in the tick. Do not launch pending effects before it.
            clearMechanics(run)
            return
        }
        if (run.state in COMBAT_STATES) bossBars.onUpdate(player, run.boss)
        if (run.state == VorkathState.ACTIVE) keepAggressive(run)
    }

    /** Delegate unique arena deadlines to the shared, encounter-cancelled world queue. */
    private fun schedule(run: Run, tick: Int, action: () -> Unit) {
        run.combat.scheduleAt(deps.worldQueues, clock, tick) { if (validTarget(run)) action() }
    }

    private fun scheduleSpecialStep(run: Run) {
        schedule(run, clock.cycle + 1) {
            when (run.state) {
                VorkathState.ACID_SPECIAL -> tickAcid(run)
                VorkathState.ZOMBIFIED_SPAWN_SPECIAL -> tickSpawn(run)
                else -> return@schedule
            }
            if (
                run.state == VorkathState.ACID_SPECIAL ||
                    run.state == VorkathState.ZOMBIFIED_SPAWN_SPECIAL
            )
                scheduleSpecialStep(run)
        }
    }

    fun attackParameters(
        npc: Npc,
        target: Player,
        attack: VorkathStandardAttack,
    ): NpcAttackOverride {
        val run = requireNotNull(runFor(npc))
        return VorkathAttackParameters.resolve(npc, target, attack)
            .copy(hitValidity = hitValidity(run))
    }

    fun fireball(npc: Npc) {
        runFor(npc)?.let(::launchFireball)
    }

    fun special(npc: Npc) {
        runFor(npc)?.let(::beginSpecial)
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
                    validTarget(run) &&
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

    fun beginBossDeath(npc: Npc): Run? {
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
        clearMechanics(run)
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
        run.respawnCycle = clock.cycle
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

    fun canFinishDeath(run: Run, npc: Npc): Boolean =
        runFor(run.player) === run &&
            run.boss === npc &&
            run.state == VorkathState.DYING &&
            inArena(run) &&
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

    fun debugStatus(player: Player): String {
        val run = runFor(player) ?: return "Vorkath: no active encounter."
        return "Vorkath generation=${run.generation} state=${run.state} hp=${run.boss.hitpoints} " +
            "attacks=${run.standardAttacks}/$VORKATH_STANDARD_ATTACKS next=${run.nextSpecial} " +
            "acid=${run.acidTiles.size} lane=${run.acidSafeLane.size} " +
            "shots=${run.shotsFired}/$VORKATH_ACID_SHOTS spawn=${run.spawn?.hitpoints ?: 0} " +
            "rewards=${run.rewardEligible}"
    }

    fun debugWake(player: Player): Boolean {
        val run = runFor(player) ?: return debugMissing(player)
        run.rewardEligible = false
        if (run.state != VorkathState.SLEEPING) {
            player.mes("Vorkath must be sleeping to force the wake-up.")
            return false
        }
        return poke(player, run.boss)
    }

    fun debugSpecial(player: Player, special: VorkathSpecial): Boolean {
        val run = activeDebugRun(player) ?: return false
        run.rewardEligible = false
        clearMechanics(run)
        run.state = VorkathState.ACTIVE
        run.nextSpecial = special
        run.boss.actionDelay = clock.cycle
        beginSpecial(run)
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
        val run = runFor(player) ?: return debugMissing(player)
        run.rewardEligible = false
        run.state = VorkathState.RESETTING
        clearMechanics(run)
        if (run.boss.isSlotAssigned) npcs.del(run.boss, Int.MAX_VALUE)
        respawn(run)
        player.mes("Vorkath encounter reset to sleeping state (no rewards).")
        return true
    }

    fun debugKill(player: Player): Boolean {
        val run = activeDebugRun(player) ?: return false
        run.rewardEligible = false
        clearMechanics(run)
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

    private fun activeDebugRun(player: Player): Run? {
        val run = runFor(player) ?: return debugMissing(player).let { null }
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

    private fun activate(run: Run) {
        val sleeping = run.boss
        // Retire the old footprint before flagging the active NPC on the same tiles.
        if (sleeping.isSlotAssigned) npcs.del(sleeping, Int.MAX_VALUE)
        val active = spawnNpc(run.player, run.session, VorkathAssets.ACTIVE, bossCoord(run.session))
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

    private fun respawn(run: Run) {
        clearMechanics(run)
        bossBars.onClose(run.player, run.boss, instant = true)
        if (run.boss.isSlotAssigned) npcs.del(run.boss, Int.MAX_VALUE)
        val sleeping =
            spawnNpc(run.player, run.session, VorkathAssets.SLEEPING, bossCoord(run.session))
        sleeping.movementLocked = true
        run.boss = sleeping
        run.combat = deps.encounterRegistry.of(sleeping)
        run.state = VorkathState.SLEEPING
        run.nextSpecial = VorkathRules.firstSpecial(random.of(2))
        run.shotsFired = 0
        run.wakeCycle = Int.MAX_VALUE
        run.player.mes("Vorkath settles back into a deep sleep.")
    }

    private fun keepAggressive(run: Run) {
        if (run.boss.isSlotAssigned && run.boss.mode == null) {
            run.boss.apPlayer2(run.player, interactions)
        }
    }

    private fun launchFireball(run: Run) {
        if (!validTarget(run)) return
        run.boss.actionDelay = clock.cycle + VORKATH_ATTACK_RATE
        run.boss.anim(VorkathAssets.RANGED_UP_ANIM, delay = 2)
        val launch = PendingFireball(clock.cycle + 1)
        run.pendingFireballs += launch
        schedule(run, launch.launchCycle) {
            run.pendingFireballs.remove(launch)
            val tile = run.player.coords
            launchTileProjectile(run, tile, VorkathProjectiles.FIREBALL)
            scheduleTileHit(
                run,
                PendingTileHit(
                    clock.cycle + VorkathProjectiles.FIREBALL.impactTicks,
                    tile,
                    0,
                    121,
                    VorkathTileAttack.FIREBALL,
                ),
            )
        }
    }

    private fun beginSpecial(run: Run) {
        if (!validTarget(run)) return
        val special = run.nextSpecial
        run.nextSpecial = VorkathRules.nextSpecial(special)
        run.shotsFired = 0
        run.specialStartCycle = clock.cycle
        run.state =
            if (special == VorkathSpecial.ACID) VorkathState.ACID_SPECIAL
            else VorkathState.ZOMBIFIED_SPAWN_SPECIAL
        when (special) {
            VorkathSpecial.ACID -> beginAcid(run)
            VorkathSpecial.ZOMBIFIED_SPAWN -> beginSpawn(run)
        }
        run.boss.mode = null
        scheduleSpecialStep(run)
    }

    private fun beginAcid(run: Run) {
        run.boss.anim(VorkathAssets.ACID_ANIM)
        clearAcid(run)
        val selected =
            VorkathAcidLayout.select(
                boss = run.boss.coords,
                player = run.player.coords,
                isWalkable = { collision[it] and CollisionFlag.BLOCK_WALK == 0 },
                choose = { it[random.of(it.size)] },
            )
        run.acidSafeLane +=
            VorkathAcidLayout.exitLane(run.boss.coords).filter {
                it !in selected && collision[it] and CollisionFlag.BLOCK_WALK == 0
            }
        selected.forEach { tile ->
            launchTileProjectile(run, tile, VorkathProjectiles.ACID)
            val pool = PendingAcidPool(clock.cycle + 3, tile)
            run.pendingAcidPools += pool
            schedule(run, pool.impactCycle) {
                if (run.pendingAcidPools.remove(pool)) formAcidPool(run, pool)
            }
        }
    }

    private fun tickAcid(run: Run) {
        if (run.player.coords in run.acidTiles) {
            val damage = random.of(1..10)
            run.player.queueHit(
                source = run.boss,
                delay = 1,
                type = HitType.Typeless,
                damage = damage,
                modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                impactHandler =
                    org.rsmod.game.hit.HitImpactHandler { impact ->
                        val heal = (clock.cycle + 1) to impact.damage
                        run.pendingHeals += heal
                        schedule(run, heal.first) {
                            run.pendingHeals.remove(heal)
                            run.boss.heal(heal.second, showHitsplat = true)
                        }
                    },
                validity = hitValidity(run),
            )
        }
        if (
            run.shotsFired < VORKATH_ACID_SHOTS &&
                clock.cycle >= run.specialStartCycle + 4 + run.shotsFired
        ) {
            val tile = run.player.coords
            run.boss.facePlayer(run.player)
            val delay = launchTileProjectile(run, tile, VorkathProjectiles.RAPID_FIRE)
            scheduleTileHit(
                run,
                PendingTileHit(
                    impactCycle = clock.cycle + delay,
                    tile = tile,
                    minimum = 25,
                    maximum = 41,
                    kind = VorkathTileAttack.RAPID_FIRE,
                ),
            )
            run.shotsFired++
            if (run.shotsFired == VORKATH_ACID_SHOTS) clearAcid(run)
        }
        if (clock.cycle >= run.specialStartCycle + 33) finishSpecial(run, recoveryTicks = 0)
    }

    private fun scheduleTileHit(run: Run, hit: PendingTileHit) {
        // Diagnostic metadata only: execution/cancellation belongs to BossEncounter.scheduleAt.
        run.pendingHits += hit
        schedule(run, hit.impactCycle) {
            run.pendingHits.remove(hit)
            val graphic =
                if (hit.kind == VorkathTileAttack.FIREBALL) VorkathAssets.FIREBALL_IMPACT
                else VorkathAssets.RAPID_FIRE_IMPACT
            val height = if (hit.kind == VorkathTileAttack.FIREBALL) 38 else 30
            world.spotanimMap(SpotanimType(graphic), hit.tile, height)
            if (hit.kind == VorkathTileAttack.FIREBALL)
                world.soundArea(hit.tile, VorkathAssets.FIREBALL_IMPACT_SOUND, radius = 12)
            else world.soundArea(hit.tile, VorkathAssets.RAPID_FIRE_IMPACT_SOUND, radius = 12)
            val distance = run.player.coords.chebyshevDistance(hit.tile)
            val maximum =
                when (hit.kind) {
                    VorkathTileAttack.FIREBALL -> VorkathRules.fireballMaximum(distance)
                    VorkathTileAttack.RAPID_FIRE -> if (distance == 0) hit.maximum else 0
                }
            if (maximum <= 0) return@schedule
            run.player.queueHit(
                source = run.boss,
                delay = 1,
                type = HitType.Typeless,
                damage =
                    if (hit.kind == VorkathTileAttack.FIREBALL)
                        VorkathRules.fireballDamage(random.of(0..hit.maximum), distance)
                    else random.of(hit.minimum..maximum),
                modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                validity = hitValidity(run),
            )
        }
    }

    private fun formAcidPool(run: Run, pool: PendingAcidPool) {
        if (pool.tile in run.acidTiles) return
        val visual =
            locs.add(
                pool.tile,
                acidPoolType(),
                Int.MAX_VALUE,
                LocAngle[random.of(4)],
                LocShape.CentrepieceStraight,
            )
        run.acidTiles += pool.tile
        run.acidVisuals += visual
    }

    private fun beginSpawn(run: Run) {
        run.boss.anim(VorkathAssets.RANGED_ANIM)
        val ice = VorkathProjectiles.ICE.build(run.boss, run.player.coords, run.player)
        world.projAnim(ice)
        run.player.spotanim(369, delay = ice.endTime, height = 0)
        run.freezeCycle = clock.cycle + ice.endTime / 30
        run.spawnLaunchCycle = run.freezeCycle
    }

    private fun tickSpawn(run: Run) {
        if (clock.cycle >= run.freezeCycle) {
            run.freezeCycle = Int.MAX_VALUE
            // This bind belongs to the special; PvP freeze immunity must not prevent it.
            run.player.clearQueue("queue.com_retaliate_npc")
            run.player.clearInteraction()
            CombatEffects.breakBind(run.player)
            run.player.applyBind(Int.MAX_VALUE - clock.cycle)
            run.player.frozen = true
            run.ownsFreeze = true
            run.player.mes("You have been frozen!")
        }
        if (clock.cycle >= run.spawnLaunchCycle) {
            run.spawnLaunchCycle = Int.MAX_VALUE
            val candidates =
                arenaTiles(run.session).filter {
                    it.chebyshevDistance(run.player.coords) == VORKATH_SPAWN_DISTANCE &&
                        !bossOccupies(run, it) &&
                        collision[it] and CollisionFlag.BLOCK_WALK == 0 &&
                        spawnCanReach(run, it)
                }
            if (candidates.isEmpty()) {
                log.error {
                    "[Vorkath] no valid spawn location at distance $VORKATH_SPAWN_DISTANCE for ${run.session.id}"
                }
                finishSpecial(run)
                return
            }
            val tile = candidates[random.of(candidates.size)]
            run.boss.anim(VorkathAssets.RANGED_UP_ANIM)
            launchTileProjectile(run, tile, VorkathProjectiles.SPAWN)
            run.pendingSpawnTile = tile
            run.spawnArrivalCycle = clock.cycle + 4
        }
        val pendingTile = run.pendingSpawnTile
        if (pendingTile != null && clock.cycle >= run.spawnArrivalCycle) {
            val spawn = spawnNpc(run.player, run.session, VorkathAssets.SPAWN, pendingTile)
            spawn.baseHitpointsLvl = 38
            spawn.hitpoints = 38
            spawn.mode = null
            run.spawn = spawn
            run.pendingSpawnTile = null
            return // First walk is the tick after arrival.
        }
        val spawn = run.spawn ?: return
        if (!spawn.isSlotAssigned) {
            finishSpecial(run)
            return
        }
        if (spawn.hitpoints <= 0) {
            if (clock.cycle >= run.spawnDeathCycle) {
                spawn.anim(VorkathAssets.SPAWN_DEATH_ANIM)
                run.retiringSpawns += spawn
                run.spawn = null
                run.spawnRetireCycle = clock.cycle + 2
                schedule(run, run.spawnRetireCycle) {
                    run.retiringSpawns.remove(spawn)
                    if (spawn.isSlotAssigned) npcs.del(spawn, Int.MAX_VALUE)
                    run.spawnRetireCycle = Int.MAX_VALUE
                }
                finishSpecial(run)
            }
            return
        }
        if (clock.cycle >= run.spawnExplosionCycle) {
            run.spawnExplosionCycle = Int.MAX_VALUE
            val damage = VorkathRules.zombifiedSpawnDamage(spawn.hitpoints)
            run.player.queueHit(
                source = spawn,
                delay = 1,
                type = HitType.Typeless,
                damage = damage,
                modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                validity = hitValidity(run),
            )
            // Captures remove the NPC on the explosion's damage tick (contact +1).
            if (spawn.isSlotAssigned) npcs.del(spawn, Int.MAX_VALUE)
            run.spawn = null
            finishSpecial(run, recoveryTicks = 0)
        } else if (run.spawnExplosionCycle != Int.MAX_VALUE) {
            return
        } else if (spawn.coords.chebyshevDistance(run.player.coords) == 0) {
            beginSpawnExplosion(run, spawn, contactCycle = clock.cycle)
        } else {
            spawn.facePlayer(run.player)
            // Arrival is processed after movement and player hits, before NPC/world updates.
            // The map clock has advanced by then, so retain this movement tick's cycle.
            val movementCycle = clock.cycle
            val next = nextSpawnStep(run, spawn.coords)
            if (next == CoordGrid.NULL) return
            // Only the final step may overlap the target. Captures retain boss-body clipping.
            spawn.moveRestrict =
                if (next == run.player.coords) MoveRestrict.PassThru else MoveRestrict.Normal
            spawn.walk(next) { beginSpawnExplosion(run, spawn, contactCycle = movementCycle) }
        }
    }

    internal fun nextSpawnStep(run: Run, source: CoordGrid): CoordGrid {
        val target = run.player.coords
        if (source == target) return target
        if (source.chebyshevDistance(target) == 1 && !bossOccupies(run, target)) {
            val diagonal = source.x != target.x && source.z != target.z
            val crossesBossCorner =
                diagonal &&
                    (bossOccupies(run, CoordGrid(source.x, target.z, source.level)) ||
                        bossOccupies(run, CoordGrid(target.x, source.z, source.level)))
            if (!crossesBossCorner && spawnSteps.validated(source, target) == target) return target
        }
        return spawnSteps.validated(source, target, extraFlag = CollisionFlag.BLOCK_NPCS)
    }

    internal fun spawnCanReach(run: Run, source: CoordGrid): Boolean {
        // The ring and edge-clipping are captured; rejecting stuck direct routes is inferred.
        var tile = source
        repeat(VORKATH_SPAWN_DISTANCE * 2) {
            if (tile == run.player.coords) return true
            tile = nextSpawnStep(run, tile)
            if (tile == CoordGrid.NULL) return false
        }
        return tile == run.player.coords
    }

    private fun beginSpawnExplosion(run: Run, spawn: Npc, contactCycle: Int) {
        if (
            !validTarget(run) ||
                run.state != VorkathState.ZOMBIFIED_SPAWN_SPECIAL ||
                run.spawn !== spawn ||
                !spawn.isSlotAssigned ||
                spawn.hitpoints <= 0 ||
                run.spawnExplosionCycle != Int.MAX_VALUE ||
                spawn.coords != run.player.coords
        )
            return
        spawn.anim(VorkathAssets.SPAWN_ATTACK_ANIM)
        world.spotanimMap(
            SpotanimType(VorkathAssets.SPAWN_EXPLOSION),
            spawn.coords,
            height = 30,
            delay = 22,
        )
        releaseFreeze(run)
        spawn.movementLocked = true
        spawn.abortRoute()
        run.spawnExplosionCycle = contactCycle + 1
    }

    private fun finishSpecial(run: Run, recoveryTicks: Int = 1) {
        clearAcid(run)
        clearSpawn(run)
        releaseFreeze(run)
        run.state = VorkathState.ACTIVE
        run.specialStartCycle = Int.MAX_VALUE
        run.boss.actionDelay = clock.cycle + recoveryTicks
        run.boss.apPlayer2(run.player, interactions)
    }

    private fun clearMechanics(run: Run) {
        run.hitEpoch++
        run.combat.runCleanupActions()
        clearAcid(run)
        run.pendingHits.clear()
        run.pendingFireballs.clear()
        run.pendingHeals.clear()
        clearSpawn(run)
        run.retiringSpawns.forEach { if (it.isSlotAssigned) npcs.del(it, Int.MAX_VALUE) }
        run.retiringSpawns.clear()
        run.spawnRetireCycle = Int.MAX_VALUE
        releaseFreeze(run)
        run.boss.clearInteraction()
        run.boss.clearFacingLock()
        run.boss.mode = null
        run.boss.vars["varn.attacking_player"] = PlayerUid.NULL.packed
        run.boss.vars["varn.aggressive_player"] = PlayerUid.NULL.packed
        if (run.player.vars["varp.aggressive_npc"] == run.boss.uid.packed)
            VarPlayerIntMapSetter.set(run.player, "varp.aggressive_npc", NpcUid.NULL.packed)
        run.player.combatClearQueue()
        run.player.clearInteraction()
        run.player.resetSpotanim()
    }

    private fun releaseFreeze(run: Run) {
        run.freezeCycle = Int.MAX_VALUE
        if (!run.ownsFreeze) return
        CombatEffects.breakBind(run.player)
        run.player.resetSpotanim()
        run.ownsFreeze = false
    }

    private fun clearAcid(run: Run) {
        run.acidVisuals.forEach { locs.del(it, Int.MAX_VALUE) }
        run.acidVisuals.clear()
        run.acidTiles.clear()
        run.acidSafeLane.clear()
        run.pendingAcidPools.clear()
    }

    private fun clearSpawn(run: Run) {
        run.pendingSpawnTile = null
        run.spawnLaunchCycle = Int.MAX_VALUE
        run.spawnArrivalCycle = Int.MAX_VALUE
        run.spawnExplosionCycle = Int.MAX_VALUE
        run.spawnDeathCycle = Int.MAX_VALUE
        val spawn = run.spawn ?: return
        run.spawn = null
        if (spawn.isSlotAssigned) npcs.del(spawn, Int.MAX_VALUE)
    }

    private fun inArena(run: Run): Boolean {
        if (instances.sessionForPlayer(run.player) !== run.session) return false
        // Session regionIds are allocation IDs, not packed OSRS map-square coordinates.
        val origin = instances.localCoord(run.session, RegionLocal(0, 35, 63, 0, 0)) ?: return false
        val tile = run.player.coords
        return tile.level == origin.level &&
            tile.x in origin.x until (origin.x + 64) &&
            tile.z in origin.z until (origin.z + 64)
    }

    private fun validTarget(run: Run): Boolean =
        runFor(run.player) === run &&
            inArena(run) &&
            run.player.isSlotAssigned &&
            !run.player.pendingLogout &&
            !run.player.loggingOut &&
            run.player.hitpoints > 0 &&
            run.boss.isSlotAssigned &&
            run.boss.hitpoints > 0 &&
            run.state in COMBAT_STATES

    private fun hitValidity(run: Run): HitValidity {
        val epoch = run.hitEpoch
        return HitValidity { run.hitEpoch == epoch && validTarget(run) }
    }

    private fun arenaTiles(session: InstanceSession): List<CoordGrid> {
        val tiles = ArrayList<CoordGrid>(550)
        // All four bounds are witnessed by the supplied spawn launches.
        for (x in 21..43) {
            for (z in 22..44) {
                instances.localCoord(session, RegionLocal(0, 35, 63, x, z))?.let(tiles::add)
            }
        }
        return tiles
    }

    private fun bossOccupies(run: Run, tile: CoordGrid): Boolean =
        tile.x in run.boss.coords.x until (run.boss.coords.x + run.boss.size) &&
            tile.z in run.boss.coords.z until (run.boss.coords.z + run.boss.size)

    private fun bossCoord(session: InstanceSession): CoordGrid =
        requireNotNull(instances.localCoord(session, RegionLocal(0, 35, 63, 29, 30)))

    private fun spawnNpc(
        player: Player,
        session: InstanceSession,
        internal: String,
        coords: CoordGrid,
    ): Npc {
        val type = requireNpc(internal)
        val npc = Npc(type, coords)
        npc.mode = null
        npc.assignSpawnOwner(player, clock.cycle)
        npcs.add(npc, VORKATH_NPC_LIFETIME)
        instances.attachNpc(session.id, npc)
        return npc
    }

    private fun launchTileProjectile(run: Run, tile: CoordGrid, spec: VorkathProjectile): Int {
        world.projAnim(spec.build(run.boss, tile, facingTile = run.player.coords))
        return spec.impactTicks
    }

    private fun acidPoolType(): ObjectServerType =
        requireNotNull(ServerCacheManager.getObject(VORKATH_ACID_POOL_LOC_ID)) {
            "Missing Vorkath acid-pool loc: $VORKATH_ACID_POOL_LOC_ID"
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

    internal data class Run(
        val player: Player,
        val session: InstanceSession,
        val generation: Long,
        var boss: Npc,
        val initialState: VorkathState = VorkathState.SLEEPING,
        val firstSpecial: VorkathSpecial = VorkathSpecial.ACID,
        var combat: BossEncounter,
        private val clock: MapClock,
        var nextSpecial: VorkathSpecial = firstSpecial,
        var wakeCycle: Int = Int.MAX_VALUE,
        var specialStartCycle: Int = Int.MAX_VALUE,
        var shotsFired: Int = 0,
        var startCycle: Int = 0,
        var respawnCycle: Int = Int.MAX_VALUE,
        var spawnArrivalCycle: Int = Int.MAX_VALUE,
        var freezeCycle: Int = Int.MAX_VALUE,
        var spawnLaunchCycle: Int = Int.MAX_VALUE,
        var spawnExplosionCycle: Int = Int.MAX_VALUE,
        var spawnDeathCycle: Int = Int.MAX_VALUE,
        var spawnRetireCycle: Int = Int.MAX_VALUE,
        var ownsFreeze: Boolean = false,
        var hitEpoch: Long = 0,
        val retiringSpawns: MutableList<Npc> = mutableListOf(),
        val pendingFireballs: MutableList<PendingFireball> = mutableListOf(),
        val pendingHeals: MutableList<Pair<Int, Int>> = mutableListOf(),
        var spawn: Npc? = null,
        var pendingSpawnTile: CoordGrid? = null,
        var killcount: Int = 0,
        var killTicks: Int = 0,
        var rewardEligible: Boolean = true,
        val acidTiles: MutableSet<CoordGrid> = linkedSetOf(),
        val acidSafeLane: MutableSet<CoordGrid> = linkedSetOf(),
        val acidVisuals: MutableList<LocInfo> = mutableListOf(),
        val pendingAcidPools: MutableList<PendingAcidPool> = mutableListOf(),
        val pendingHits: MutableList<PendingTileHit> = mutableListOf(),
    ) {
        var state: VorkathState
            get() = VorkathState.entries.single { phaseName(it) == combat.currentPhaseName }
            set(value) {
                combat.transitionTo(phaseName(value), clock.cycle)
            }

        val standardAttacks: Int
            get() =
                combat.attackTracesAfter(combat.phaseEnteredTick - 1).count {
                    it.ability != "special"
                }

        private fun phaseName(state: VorkathState) =
            when (state) {
                VorkathState.ACID_SPECIAL -> "acid"
                VorkathState.ZOMBIFIED_SPAWN_SPECIAL -> "spawn"
                else -> state.name.lowercase()
            }

        fun owns(npc: Npc): Boolean = npc === boss || npc === spawn || npc in retiringSpawns
    }
}
