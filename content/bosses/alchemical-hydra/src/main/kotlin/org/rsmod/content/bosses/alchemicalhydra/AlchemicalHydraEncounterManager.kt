package org.rsmod.content.bosses.alchemicalhydra

import dev.openrune.ServerCacheManager
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.abs
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.RaidLeaveResult
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.NpcCalculatedProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitImpactHandler
import org.rsmod.game.hit.HitType
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@Singleton
internal class AlchemicalHydraEncounterManager
@Inject
constructor(
    private val clock: MapClock,
    private val instances: InstanceManager,
    private val npcs: NpcRepository,
    private val interactions: AiPlayerInteractions,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
    private val accuracy: AccuracyFormulae,
    private val world: WorldRepository,
) {
    private val runs = mutableMapOf<PlayerUid, Run>()
    private var generation = 1L

    fun validateAssets() {
        AlchemicalHydraAssets.allForms.forEach { id ->
            requireNotNull(ServerCacheManager.getNpc(id)) { "Missing revision-240 Hydra NPC $id" }
        }
        require(
            ServerCacheManager.getNpc(AlchemicalHydraAssets.GREEN)?.hitpoints == HYDRA_MAX_HITPOINTS
        ) {
            "Revision-240 Hydra must have $HYDRA_MAX_HITPOINTS hitpoints."
        }
        AlchemicalHydraAssets.animations.forEach { id ->
            requireNotNull(ServerCacheManager.getAnim(id)) {
                "Missing revision-240 Hydra animation $id"
            }
        }
        (AlchemicalHydraAssets.doors + HydraVent.entries.map(HydraVent::locId)).forEach { id ->
            requireNotNull(ServerCacheManager.getObject(id)) {
                "Missing revision-240 Hydra location $id"
            }
        }
    }

    fun enter(player: Player): Boolean {
        if (player.hitpoints <= 0 || player.isAccessProtected) {
            player.mes("Finish what you are doing before entering the Alchemical Hydra's lair.")
            return false
        }
        if (runs.containsKey(player.uid) || instances.sessionForPlayer(player) != null) {
            player.mes("You already have an active instance.")
            return false
        }
        val green = requireNpc(AlchemicalHydraAssets.GREEN)
        val result =
            instances.create(
                owner = player,
                key = HYDRA_INSTANCE_KEY,
                spec = hydraSpec(green),
                access = InstanceAccess.Private,
                currentTick = clock.cycle,
            )
        if (result !is InstanceManager.Result.Created) {
            player.mes(
                (result as? InstanceManager.Result.Failed)?.reason
                    ?: "The Alchemical Hydra's lair is unavailable."
            )
            return false
        }
        prepare(player)
        PathingEntityCommon.telejump(player, collision, result.enter)
        instances.finalizeEntry(player, result.session, clock.cycle)
        val destination = localCoord(result.session, HYDRA_OBSERVATION_DESTINATION)
        player.anim(AlchemicalHydraAssets.ENTRY_ANIM)
        PathingEntityCommon.exactMove(
            player,
            result.enter,
            destination,
            delay1 = 0,
            delay2 = HYDRA_ENTRY_CLIENT_CYCLES,
            dir = HYDRA_ENTRY_ANGLE,
            collision = collision,
        )
        val boss = spawnBoss(player, result.session)
        runs[player.uid] =
            Run(
                player = player,
                session = result.session,
                boss = boss,
                generation = generation++,
                initialStyle =
                    if (random.of(2) == 0) HydraAttackStyle.MAGIC else HydraAttackStyle.RANGED,
            )
        player.mes("You climb over the rocks into the observation room.")
        player.mes("Cross the alchemical doors to begin the fight.")
        return true
    }

    fun crossDoor(player: Player): Boolean {
        val run = runs[player.uid] ?: return false
        return when (run.state) {
            HydraState.OBSERVATION -> {
                PathingEntityCommon.telejump(
                    player,
                    collision,
                    localCoord(run.session, HYDRA_CHAMBER_ENTRY),
                )
                activate(run)
                true
            }
            HydraState.RESPAWNING -> {
                player.mes("The chamber is open while the Alchemical Hydra reforms.")
                true
            }
            HydraState.ACTIVE,
            HydraState.TRANSITION -> {
                player.mes("The alchemical doors are locked during the fight.")
                true
            }
            HydraState.DYING,
            HydraState.INVALIDATED -> false
        }
    }

    fun attack(access: StandardNpcAccess, target: Player) {
        val run = runs[target.uid] ?: return
        if (run.boss !== access.npc || !run.boss.isSpawnOwnedBy(target)) return
        if (run.state != HydraState.ACTIVE || access.actionDelay > clock.cycle) return
        if (AlchemicalHydraRules.specialDue(run.basicAttacks, run.specialsLaunched)) {
            when (AlchemicalHydraRules.special(run.phase)) {
                HydraSpecial.POISON -> launchPoison(run)
                HydraSpecial.LIGHTNING -> launchLightning(run)
                HydraSpecial.FLAME -> launchFlame(run)
            }
            run.specialsLaunched++
            access.actionDelay = run.specialEndCycle
            return
        }
        launchBasic(run)
        access.actionDelay = clock.cycle + AlchemicalHydraRules.attackRate(run.phase)
    }

    private fun launchBasic(run: Run) {
        val npc = run.boss
        val target = run.player
        val style = run.style
        val protectedAtLaunch =
            target.vars[
                    if (style == HydraAttackStyle.MAGIC) "varbit.prayer_protectfrommagic"
                    else "varbit.prayer_protectfrommissiles"] == 1
        npc.facePlayer(target)
        npc.anim(basicAnimation(run.phase, style))
        val weakened = run.weakened || run.phase == HydraPhase.FINAL
        val maxHit =
            AlchemicalHydraRules.empoweredDamage(
                AlchemicalHydraRules.basicMaxHit(run.phase, weakened),
                run.wrongVentStacks,
            )
        repeat(AlchemicalHydraRules.basicHitCount(run.phase, weakened)) {
            val projectile =
                ProjAnim.fromNpcToPlayer(
                    npc,
                    target,
                    if (style == HydraAttackStyle.MAGIC) AlchemicalHydraAssets.MAGIC_PROJECTILE
                    else AlchemicalHydraAssets.RANGED_PROJECTILE,
                    AlchemicalHydraAssets.BASIC_PROJECTILE,
                )
            world.projAnim(projectile)
            val accurate =
                if (style == HydraAttackStyle.MAGIC) accuracy.rollMagicAccuracy(npc, target, random)
                else accuracy.rollRangedAccuracy(npc, target, random)
            target.queueHit(
                source = npc,
                delay = projectile.serverCycles.coerceAtLeast(1),
                type = if (style == HydraAttackStyle.MAGIC) HitType.Magic else HitType.Ranged,
                damage = if (accurate) random.of(maxHit + 1) else 0,
                modifier =
                    NpcCalculatedProtectionPrayerPlayerHitModifier(
                        protectedAtCalculation = protectedAtLaunch,
                        reductionPercent = 100,
                    ),
            )
        }
        run.lastBasicStyle = style
        run.basicAttacks++
        run.attacksWithStyle++
        val next = AlchemicalHydraRules.nextStyleAfterAttack(run.phase, style, run.attacksWithStyle)
        if (next != style) {
            run.style = next
            run.attacksWithStyle = 0
        }
    }

    private fun launchPoison(run: Run) {
        run.boss.facePlayer(run.player)
        run.boss.anim(
            if (run.phase == HydraPhase.FINAL) AlchemicalHydraAssets.FINAL_MAGIC_OR_POISON_ANIM
            else AlchemicalHydraAssets.GREEN_POISON_ANIM
        )
        val centers =
            AlchemicalHydraRules.poisonCenters(
                run.player.coords.x,
                run.player.coords.z,
                five = run.phase == HydraPhase.FINAL,
            )
        centers.forEach { (x, z) ->
            val tile = legalTile(run, x, z)
            val projectile =
                ProjAnim.fromNpcToCoord(
                    run.boss,
                    tile,
                    AlchemicalHydraAssets.POISON_PROJECTILE,
                    AlchemicalHydraAssets.SPECIAL_PROJECTILE,
                )
            world.projAnim(projectile)
            run.pendingPoison += PendingPoison(tile, clock.cycle + projectile.serverCycles)
        }
        run.specialEndCycle = clock.cycle + AlchemicalHydraRules.NORMAL_ATTACK_RATE
    }

    private fun launchLightning(run: Run) {
        run.boss.facePlayer(run.player)
        run.boss.anim(AlchemicalHydraAssets.BLUE_LIGHTNING_ANIM)
        val center = localCoord(run.session, HYDRA_BOSS_SPAWN).translate(3, 3)
        world.projAnim(
            ProjAnim.fromNpcToCoord(
                run.boss,
                center,
                AlchemicalHydraAssets.LIGHTNING_CENTER,
                AlchemicalHydraAssets.SPECIAL_PROJECTILE,
            )
        )
        run.pendingLightningCycle = clock.cycle + 2
        run.specialEndCycle = clock.cycle + AlchemicalHydraRules.LIGHTNING_LIFETIME
    }

    private fun launchFlame(run: Run) {
        run.boss.clearInteraction()
        run.boss.movementLocked = true
        run.boss.teleport(collision, localCoord(run.session, HYDRA_BOSS_SPAWN))
        run.boss.facePlayer(run.player)
        run.boss.anim(AlchemicalHydraAssets.RED_FLAME_ANIM)
        val player = run.player.coords
        val bossCenter = run.boss.coords.translate(3, 3)
        val vertical = abs(player.z - bossCenter.z) >= abs(player.x - bossCenter.x)
        val walls = linkedSetOf<CoordGrid>()
        for (offset in -2..2) {
            if (vertical) {
                walls += legalTile(run, player.x - 2, player.z + offset)
                walls += legalTile(run, player.x + 2, player.z + offset)
            } else {
                walls += legalTile(run, player.x + offset, player.z - 2)
                walls += legalTile(run, player.x + offset, player.z + 2)
            }
        }
        walls.forEach { tile ->
            world.projAnim(
                ProjAnim.fromNpcToCoord(
                    run.boss,
                    tile,
                    AlchemicalHydraAssets.FLAME_PROJECTILE,
                    AlchemicalHydraAssets.SPECIAL_PROJECTILE,
                )
            )
            world.spotanimMapPrivate(
                run.player,
                SpotanimType(AlchemicalHydraAssets.FLAME_TILE),
                tile,
            )
        }
        run.flameTiles.clear()
        run.flameTiles += walls
        run.flameEndCycle = clock.cycle + AlchemicalHydraRules.FLAME_LIFETIME
        run.trackingFlame = bossCenter
        run.specialEndCycle = run.flameEndCycle
    }

    fun modifyNpcHit(player: Player, npc: Npc, hit: HitBuilder) {
        val run = runs[player.uid]
        if (
            run == null ||
                run.boss !== npc ||
                !npc.isSpawnOwnedBy(player) ||
                run.state != HydraState.ACTIVE
        ) {
            hit.damage = 0
            return
        }
        val weakened = run.weakened || run.phase == HydraPhase.FINAL
        hit.damage = AlchemicalHydraRules.incomingDamage(hit.damage, weakened)
        hit.damage = AlchemicalHydraRules.capAtThreshold(run.phase, npc.hitpoints, hit.damage)
        val previous = hit.impactHandler
        hit.impactHandler = HitImpactHandler { impact ->
            previous?.onImpact(impact)
            if (
                impact.damage > 0 &&
                    runs[player.uid] === run &&
                    run.state == HydraState.ACTIVE &&
                    AlchemicalHydraRules.reachesThreshold(run.phase, npc.hitpoints)
            ) {
                beginTransition(run)
            }
        }
    }

    fun tickAll() {
        runs.values.toList().forEach { run ->
            val actual = instances.sessionForPlayer(run.player)?.id?.value
            if (AlchemicalHydraRules.shouldDiscardRun(run.session.id.value, actual)) {
                membershipEnded(run.player, run.session.id)
                return@forEach
            }
            processPoison(run)
            processLightning(run)
            processFlame(run)
            when (run.state) {
                HydraState.OBSERVATION -> {
                    if (inChamber(run.player.coords)) activate(run)
                }
                HydraState.ACTIVE -> {
                    processVent(run)
                    keepAggressive(run)
                }
                HydraState.TRANSITION -> {
                    if (clock.cycle >= run.stateCycle) finishTransition(run)
                }
                HydraState.RESPAWNING -> {
                    if (clock.cycle >= run.stateCycle) respawn(run)
                }
                HydraState.DYING,
                HydraState.INVALIDATED -> Unit
            }
        }
    }

    private fun activate(run: Run) {
        if (run.state != HydraState.OBSERVATION) return
        run.state = HydraState.ACTIVE
        run.nextVentCycle = clock.cycle + AlchemicalHydraRules.VENT_INITIAL_DELAY
        run.boss.movementLocked = false
        run.boss.showAllOps()
        run.boss.actionDelay = clock.cycle + 1
        keepAggressive(run)
        run.player.mes("The alchemical doors lock behind you. The Hydra attacks!")
    }

    private fun processVent(run: Run) {
        if (clock.cycle < run.nextVentCycle) return
        run.nextVentCycle = clock.cycle + AlchemicalHydraRules.VENT_INTERVAL
        val touched =
            HydraVent.entries.firstOrNull { vent ->
                val tile = localCoord(run.session, vent.coord)
                AlchemicalHydraRules.ventTouchesBoss(
                    run.boss.coords.x,
                    run.boss.coords.z,
                    run.boss.visType.size,
                    tile.x,
                    tile.z,
                )
            } ?: return
        if (touched == AlchemicalHydraRules.requiredVent(run.phase)) {
            if (!run.weakened)
                run.player.mes("The correct chemical neutralises the Hydra's defence.")
            run.weakened = true
        } else {
            run.wrongVentStacks = AlchemicalHydraRules.applyWrongVentStack(run.wrongVentStacks)
            run.player.mes("The wrong chemical empowers the Hydra.")
        }
    }

    private fun beginTransition(run: Run) {
        val transition = AlchemicalHydraRules.transitionNpc(run.phase) ?: return
        run.state = HydraState.TRANSITION
        run.stateCycle = clock.cycle + AlchemicalHydraRules.TRANSITION_TICKS
        run.boss.clearInteraction()
        run.boss.movementLocked = true
        run.boss.transmog(requireNpc(transition), Int.MAX_VALUE)
        run.boss.anim(transitionAnimation(run.phase))
        run.lightningCurrents.clear()
        run.pendingLightningCycle = Int.MAX_VALUE
        run.trackingFlame = null
        run.player.combatClearQueue()
    }

    private fun finishTransition(run: Run) {
        val previousPhase = run.phase
        val next = AlchemicalHydraRules.nextPhase(previousPhase) ?: return
        val hp = run.boss.hitpoints
        val type = requireNpc(AlchemicalHydraRules.activeNpc(next))
        run.boss.transmog(type, Int.MAX_VALUE)
        run.boss.copyStats(type)
        run.boss.baseHitpointsLvl = HYDRA_MAX_HITPOINTS
        run.boss.hitpoints = hp
        run.boss.anim(emergeAnimation(next))
        run.phase = next
        run.state = HydraState.ACTIVE
        run.weakened = next == HydraPhase.FINAL
        run.wrongVentStacks = 0
        run.basicAttacks = 0
        run.specialsLaunched = 0
        run.attacksWithStyle = 0
        if (next == HydraPhase.FINAL) run.style = run.lastBasicStyle.opposite()
        run.nextVentCycle = clock.cycle + AlchemicalHydraRules.VENT_INITIAL_DELAY
        run.boss.movementLocked = false
        run.boss.actionDelay = clock.cycle + 1
        keepAggressive(run)
    }

    private fun processPoison(run: Run) {
        val landed = run.pendingPoison.filter { it.impactCycle <= clock.cycle }
        run.pendingPoison.removeAll(landed.toSet())
        landed.forEach { poison ->
            world.spotanimMapPrivate(
                run.player,
                SpotanimType(AlchemicalHydraAssets.POISON_SPLASH),
                poison.tile,
            )
            val direct = run.player.coords == poison.tile
            world.spotanimMapPrivate(
                run.player,
                SpotanimType(AlchemicalHydraAssets.POISON_POOL),
                poison.tile,
            )
            if (
                AlchemicalHydraRules.isPoisonSplash(
                    run.player.coords.x,
                    run.player.coords.z,
                    poison.tile.x,
                    poison.tile.z,
                )
            ) {
                queueTypeless(run, random.of(8))
                PlayerPoison.tryPoison(
                    run.player,
                    severity = PlayerPoison.severityForInitialDamage(if (direct) 6 else 4),
                )
            }
            run.poisonPools +=
                PoisonPool(poison.tile, clock.cycle + AlchemicalHydraRules.POISON_POOL_LIFETIME)
        }
        run.poisonPools.removeAll { it.endCycle <= clock.cycle }
        if (
            run.player.coords in run.poisonPools.map(PoisonPool::tile) &&
                run.lastPoolDamageCycle != clock.cycle
        ) {
            run.lastPoolDamageCycle = clock.cycle
            world.spotanimMapPrivate(
                run.player,
                SpotanimType(AlchemicalHydraAssets.POISON_POOL),
                run.player.coords,
            )
            queueTypeless(run, random.of(13))
        }
    }

    private fun processLightning(run: Run) {
        if (run.pendingLightningCycle <= clock.cycle && run.lightningCurrents.isEmpty()) {
            run.pendingLightningCycle = Int.MAX_VALUE
            listOf(28 to 23, 28 to 32, 18 to 23, 17 to 32).forEach { (x, z) ->
                run.lightningCurrents +=
                    LightningCurrent(
                        localCoord(
                            run.session,
                            RegionLocal(0, HYDRA_SOURCE_MAP_X, HYDRA_SOURCE_MAP_Z, x, z),
                        ),
                        clock.cycle + AlchemicalHydraRules.LIGHTNING_LIFETIME,
                    )
            }
        }
        if (run.lightningCurrents.isEmpty()) return
        val iterator = run.lightningCurrents.iterator()
        var struck = false
        while (iterator.hasNext()) {
            val current = iterator.next()
            if (current.endCycle <= clock.cycle) {
                iterator.remove()
                continue
            }
            val (x, z) =
                AlchemicalHydraRules.stepToward(
                    current.tile.x,
                    current.tile.z,
                    run.player.coords.x,
                    run.player.coords.z,
                )
            current.tile = legalTile(run, x, z)
            world.spotanimMapPrivate(
                run.player,
                SpotanimType(AlchemicalHydraAssets.LIGHTNING_CURRENT),
                current.tile,
            )
            if (current.tile == run.player.coords) struck = true
        }
        if (struck) {
            queueTypeless(run, random.of(23))
            run.player.applyShoveStun(stunCycles = 5, postStunImmunityCycles = 1)
            run.lightningCurrents.clear()
        }
    }

    private fun processFlame(run: Run) {
        run.flameTiles.removeAll { clock.cycle >= run.flameEndCycle }
        val tracking = run.trackingFlame
        if (tracking != null && clock.cycle < run.flameEndCycle) {
            val (x, z) =
                AlchemicalHydraRules.stepToward(
                    tracking.x,
                    tracking.z,
                    run.player.coords.x,
                    run.player.coords.z,
                )
            val next = legalTile(run, x, z)
            run.trackingFlame = next
            run.flameTiles += next
            world.spotanimMapPrivate(
                run.player,
                SpotanimType(AlchemicalHydraAssets.FLAME_TILE),
                next,
            )
        } else if (tracking != null) {
            run.trackingFlame = null
            run.boss.movementLocked = false
        }
        if (
            run.player.coords in run.flameTiles &&
                run.fireBurnsRemaining == 0 &&
                run.lastFireContactCycle != clock.cycle
        ) {
            run.lastFireContactCycle = clock.cycle
            queueTypeless(run, random.of(26))
            run.fireBurnsRemaining = AlchemicalHydraRules.FLAME_BURNS
            run.nextBurnCycle = clock.cycle + 2
        }
        if (run.fireBurnsRemaining > 0 && clock.cycle >= run.nextBurnCycle) {
            queueTypeless(run, 5)
            run.fireBurnsRemaining--
            run.nextBurnCycle = clock.cycle + 2
        }
    }

    private fun queueTypeless(run: Run, damage: Int) {
        if (!run.player.isSlotAssigned || run.player.hitpoints <= 0 || !run.boss.isSlotAssigned)
            return
        run.player.queueHit(
            source = run.boss,
            delay = 1,
            type = HitType.Typeless,
            damage = AlchemicalHydraRules.empoweredDamage(damage, run.wrongVentStacks),
            modifier = NpcBypassProtectionPrayerPlayerHitModifier,
        )
    }

    fun beginBossDeath(npc: Npc): CoordGrid? {
        val run = runs.values.firstOrNull { it.boss === npc } ?: return null
        if (run.phase != HydraPhase.FINAL || run.state != HydraState.ACTIVE) return null
        run.state = HydraState.DYING
        clearHazards(run)
        run.player.combatClearQueue()
        npc.anim(AlchemicalHydraAssets.DEATH_ANIM)
        return run.player.coords
    }

    fun finishBossDeath(npc: Npc) {
        val run = runs.values.firstOrNull { it.boss === npc } ?: return
        if (run.state != HydraState.DYING) return
        val kills = run.player.vars["varp.total_hydraboss_kills"] + 1
        VarPlayerIntMapSetter.set(run.player, "varp.total_hydraboss_kills", kills)
        run.state = HydraState.RESPAWNING
        run.stateCycle = clock.cycle + HYDRA_RESPAWN_TICKS
        run.player.mes("Alchemical Hydra kill $kills. Its drops land at your feet.")
        run.player.mes("The Alchemical Hydra will reform in about 26 seconds.")
    }

    private fun respawn(run: Run) {
        if (runs[run.player.uid] !== run || !run.player.isSlotAssigned) return
        if (instances.sessionForPlayer(run.player)?.id != run.session.id) {
            abort(run.player, "left before respawn", teleport = false)
            return
        }
        clearHazards(run)
        run.boss = spawnBoss(run.player, run.session)
        run.phase = HydraPhase.GREEN
        run.state = HydraState.OBSERVATION
        run.style = if (random.of(2) == 0) HydraAttackStyle.MAGIC else HydraAttackStyle.RANGED
        run.initialStyle = run.style
        run.lastBasicStyle = run.style
        run.basicAttacks = 0
        run.specialsLaunched = 0
        run.attacksWithStyle = 0
        run.weakened = false
        run.wrongVentStacks = 0
        run.player.combatClearQueue()
        run.player.mes("The Alchemical Hydra has reformed. Cross the doors to fight again.")
    }

    fun isEncounterNpc(npc: Npc): Boolean = runs.values.any { it.boss === npc }

    fun isParticipant(player: Player, npc: Npc): Boolean =
        runs[player.uid]?.let { it.boss === npc && npc.isSpawnOwnedBy(player) } == true

    fun attackDenial(player: Player, npc: Npc): String? {
        val run = runs[player.uid] ?: return "You do not have an active Hydra encounter."
        if (run.boss !== npc) return "That Hydra belongs to another encounter."
        return if (run.state == HydraState.ACTIVE) null
        else
            "The Alchemical Hydra cannot be attacked from the observation room or during a transition."
    }

    fun leave(player: Player): Boolean {
        val run = runs.remove(player.uid) ?: return false
        dispose(run)
        val exit = instances.leave(player, run.session, clock.cycle)
        if (player.isSlotAssigned) PathingEntityCommon.telejump(player, collision, exit)
        prepare(player)
        player.mes("You leave the Alchemical Hydra's lair.")
        return true
    }

    fun leaveFromLogoutTab(player: Player, expected: InstanceId): RaidLeaveResult {
        val run = runs[player.uid] ?: return RaidLeaveResult.Unhandled
        if (run.session.id != expected) return RaidLeaveResult.Unhandled
        return if (leave(player)) RaidLeaveResult.Left else RaidLeaveResult.Failed
    }

    fun membershipEnded(player: Player, instanceId: InstanceId) {
        val run = runs[player.uid] ?: return
        if (run.session.id != instanceId) return
        runs.remove(player.uid)
        dispose(run)
    }

    fun externalTeleport(player: Player, instanceId: InstanceId) {
        membershipEnded(player, instanceId)
    }

    fun logout(player: Player) {
        val run = runs[player.uid] ?: return
        if (instances.sessionForPlayer(player)?.id == run.session.id) {
            instances.handleLogout(player, clock.cycle)
        } else {
            membershipEnded(player, run.session.id)
        }
    }

    fun abort(player: Player, reason: String, teleport: Boolean) {
        val run = runs.remove(player.uid) ?: return
        dispose(run)
        val exit = instances.leave(player, run.session, clock.cycle)
        if (teleport && player.isSlotAssigned) PathingEntityCommon.telejump(player, collision, exit)
        prepare(player)
        player.mes("The Alchemical Hydra encounter ended: $reason.")
    }

    fun status(player: Player): String {
        val run = runs[player.uid] ?: return "Hydra: no active encounter."
        return "Hydra phase=${run.phase} state=${run.state} hp=${run.boss.hitpoints}/$HYDRA_MAX_HITPOINTS " +
            "style=${run.style} basics=${run.basicAttacks} specials=${run.specialsLaunched} " +
            "weakened=${run.weakened} wrongVent=${run.wrongVentStacks}"
    }

    private fun spawnBoss(player: Player, session: InstanceSession): Npc {
        val type = requireNpc(AlchemicalHydraAssets.GREEN)
        val npc = Npc(type, localCoord(session, HYDRA_BOSS_SPAWN))
        npc.mode = null
        npc.respawns = false
        npc.movementLocked = true
        npc.apRangeOverride = 5
        npc.maxOpRangeOverride = 64
        npc.apRequiresLineOfSight = true
        npc.baseHitpointsLvl = HYDRA_MAX_HITPOINTS
        npc.hitpoints = HYDRA_MAX_HITPOINTS
        npc.hideAllOps()
        npc.assignSpawnOwner(player, clock.cycle)
        npcs.add(npc, HYDRA_NPC_LIFETIME)
        instances.attachNpc(session.id, npc)
        return npc
    }

    private fun keepAggressive(run: Run) {
        if (
            run.state == HydraState.ACTIVE &&
                run.boss.isSlotAssigned &&
                run.boss.hitpoints > 0 &&
                run.boss.mode == null
        ) {
            run.boss.apPlayer2(run.player, interactions)
        }
    }

    private fun dispose(run: Run) {
        run.state = HydraState.INVALIDATED
        clearHazards(run)
        run.boss.clearInteraction()
        if (run.boss.isSlotAssigned) npcs.del(run.boss, Int.MAX_VALUE)
    }

    private fun clearHazards(run: Run) {
        run.pendingPoison.clear()
        run.poisonPools.clear()
        run.lightningCurrents.clear()
        run.pendingLightningCycle = Int.MAX_VALUE
        run.flameTiles.clear()
        run.trackingFlame = null
        run.fireBurnsRemaining = 0
    }

    private fun inChamber(tile: CoordGrid): Boolean =
        AlchemicalHydraRules.inChamber(tile.x and 63, tile.z and 63)

    private fun legalTile(run: Run, x: Int, z: Int): CoordGrid {
        val southWest =
            localCoord(
                run.session,
                RegionLocal(
                    0,
                    HYDRA_SOURCE_MAP_X,
                    HYDRA_SOURCE_MAP_Z,
                    AlchemicalHydraRules.CHAMBER_MIN_X,
                    AlchemicalHydraRules.CHAMBER_MIN_Z,
                ),
            )
        val northEast =
            localCoord(
                run.session,
                RegionLocal(
                    0,
                    HYDRA_SOURCE_MAP_X,
                    HYDRA_SOURCE_MAP_Z,
                    AlchemicalHydraRules.CHAMBER_MAX_X,
                    AlchemicalHydraRules.CHAMBER_MAX_Z,
                ),
            )
        return CoordGrid(
            x.coerceIn(minOf(southWest.x, northEast.x), maxOf(southWest.x, northEast.x)),
            z.coerceIn(minOf(southWest.z, northEast.z), maxOf(southWest.z, northEast.z)),
            southWest.level,
        )
    }

    private fun localCoord(session: InstanceSession, coord: RegionLocal): CoordGrid =
        requireNotNull(instances.localCoord(session, coord)) {
            "Hydra local coordinate is unavailable: $coord"
        }

    private fun requireNpc(id: Int) =
        requireNotNull(ServerCacheManager.getNpc(id)) { "Missing revision-240 Hydra NPC $id" }

    private fun basicAnimation(phase: HydraPhase, style: HydraAttackStyle): Int =
        when (phase) {
            HydraPhase.GREEN ->
                if (style == HydraAttackStyle.MAGIC) AlchemicalHydraAssets.GREEN_MAGIC_ANIM
                else AlchemicalHydraAssets.GREEN_RANGE_ANIM
            HydraPhase.BLUE ->
                if (style == HydraAttackStyle.MAGIC) AlchemicalHydraAssets.BLUE_MAGIC_ANIM
                else AlchemicalHydraAssets.BLUE_RANGE_ANIM
            HydraPhase.RED ->
                if (style == HydraAttackStyle.MAGIC) AlchemicalHydraAssets.RED_MAGIC_ANIM
                else AlchemicalHydraAssets.RED_RANGE_ANIM
            HydraPhase.FINAL ->
                if (style == HydraAttackStyle.MAGIC)
                    AlchemicalHydraAssets.FINAL_MAGIC_OR_POISON_ANIM
                else AlchemicalHydraAssets.FINAL_RANGE_ANIM
        }

    private fun transitionAnimation(phase: HydraPhase): Int =
        when (phase) {
            HydraPhase.GREEN -> AlchemicalHydraAssets.GREEN_TRANSITION_ANIM
            HydraPhase.BLUE -> AlchemicalHydraAssets.BLUE_TRANSITION_ANIM
            HydraPhase.RED -> AlchemicalHydraAssets.RED_TRANSITION_ANIM
            HydraPhase.FINAL -> AlchemicalHydraAssets.DEATH_ANIM
        }

    private fun emergeAnimation(phase: HydraPhase): Int =
        when (phase) {
            HydraPhase.BLUE -> AlchemicalHydraAssets.BLUE_EMERGE_ANIM
            HydraPhase.RED -> AlchemicalHydraAssets.RED_EMERGE_ANIM
            HydraPhase.FINAL -> AlchemicalHydraAssets.FINAL_EMERGE_ANIM
            HydraPhase.GREEN -> AlchemicalHydraAssets.GREEN_RANGE_ANIM
        }

    private fun prepare(player: Player) {
        player.combatClearQueue()
        player.clearInteraction()
        player.resetAnim()
    }

    private data class PendingPoison(val tile: CoordGrid, val impactCycle: Int)

    private data class PoisonPool(val tile: CoordGrid, val endCycle: Int)

    private data class LightningCurrent(var tile: CoordGrid, val endCycle: Int)

    private data class Run(
        val player: Player,
        val session: InstanceSession,
        var boss: Npc,
        val generation: Long,
        var initialStyle: HydraAttackStyle,
        var state: HydraState = HydraState.OBSERVATION,
        var phase: HydraPhase = HydraPhase.GREEN,
        var style: HydraAttackStyle = initialStyle,
        var lastBasicStyle: HydraAttackStyle = initialStyle,
        var basicAttacks: Int = 0,
        var specialsLaunched: Int = 0,
        var attacksWithStyle: Int = 0,
        var weakened: Boolean = false,
        var wrongVentStacks: Int = 0,
        var stateCycle: Int = 0,
        var specialEndCycle: Int = 0,
        var nextVentCycle: Int = 0,
        val pendingPoison: MutableList<PendingPoison> = mutableListOf(),
        val poisonPools: MutableList<PoisonPool> = mutableListOf(),
        var lastPoolDamageCycle: Int = -1,
        var pendingLightningCycle: Int = Int.MAX_VALUE,
        val lightningCurrents: MutableList<LightningCurrent> = mutableListOf(),
        val flameTiles: MutableSet<CoordGrid> = linkedSetOf(),
        var trackingFlame: CoordGrid? = null,
        var flameEndCycle: Int = 0,
        var fireBurnsRemaining: Int = 0,
        var nextBurnCycle: Int = 0,
        var lastFireContactCycle: Int = -1,
    )
}
