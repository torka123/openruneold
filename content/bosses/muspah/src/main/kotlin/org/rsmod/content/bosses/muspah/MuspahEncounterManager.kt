package org.rsmod.content.bosses.muspah

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ProjAnimType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import org.rsmod.api.attr.BossRewardAttributes
import org.rsmod.api.combat.NpcAttackOverride
import org.rsmod.api.combat.NpcProjectile
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.CombatCorruption
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.heal
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.NpcCalculatedProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.NpcPartialProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.LineValidator
import org.rsmod.routefinder.collision.CollisionFlagMap

@Singleton
internal class MuspahEncounterManager
@Inject
constructor(
    private val clock: MapClock,
    private val instances: InstanceManager,
    private val npcs: NpcRepository,
    private val locs: LocRepository,
    private val interactions: AiPlayerInteractions,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
    private val combat: NvPCombat,
    private val corruption: CombatCorruption,
    private val world: WorldRepository,
) {
    private val log = InlineLogger()
    private val lineValidator = LineValidator(collision)
    private val runs = mutableMapOf<PlayerUid, Run>()
    private var generation = 1L

    fun validateAssets() {
        MuspahAssets.npcForms.forEach(::requireNpc)
        MuspahAssets.sequences.forEach { internal ->
            requireNotNull(ServerCacheManager.getAnim(internal.asRSCM(RSCMType.SEQ))) {
                "Missing Phantom Muspah sequence: $internal"
            }
        }
        MuspahAssets.spotanims.forEach { it.asRSCM(RSCMType.SPOTANIM) }
        listOf(
                MuspahAssets.SPIKE_PRE,
                MuspahAssets.SPIKE,
                MuspahAssets.SPIKE_FOLLOW,
            )
            .forEach { internal ->
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

    fun teleportOutside(player: Player): Boolean {
        val current = instances.sessionForPlayer(player)
        if (current != null && current.key != MUSPAH_INSTANCE_KEY) {
            player.mes("Leave your current instance before travelling to Ghorrock Dungeon.")
            return false
        }
        runs[player.uid]?.let { abort(player, "staff travel command", teleport = false) }
        if (current != null) instances.leave(player, current, clock.cycle)
        prepare(player)
        PathingEntityCommon.telejump(player, collision, MUSPAH_OUTSIDE)
        player.mes("You arrive immediately outside the Phantom Muspah crevice.")
        return true
    }

    fun enter(player: Player): Boolean {
        if (MUSPAH_REQUIRE_SECRETS_OF_NORTH) {
            player.mes("You must complete Secrets of the North before entering this crevice.")
            return false
        }
        if (player.hitpoints <= 0 || player.isAccessProtected) {
            player.mes("Finish what you are doing before entering the Muspah's lair.")
            return false
        }
        if (runs.containsKey(player.uid) || instances.sessionForPlayer(player) != null) {
            player.mes("You already have an active instance.")
            return false
        }
        val rangedType = requireNpc(MuspahAssets.RANGED)
        val result =
            instances.create(
                owner = player,
                key = MUSPAH_INSTANCE_KEY,
                spec = muspahSpec(rangedType),
                access = InstanceAccess.Private,
                currentTick = clock.cycle,
            )
        if (result !is InstanceManager.Result.Created) {
            player.mes(
                (result as? InstanceManager.Result.Failed)?.reason
                    ?: "The Phantom Muspah arena is unavailable."
            )
            return false
        }
        prepare(player)
        PathingEntityCommon.telejump(player, collision, result.enter)
        instances.finalizeEntry(player, result.session, clock.cycle)
        player.lootDropDuration = MUSPAH_DROP_DURATION
        val initial = MuspahRules.startingForm(random.of(2))
        val boss = spawnBoss(player, result.session, initial)
        val run =
            Run(
                player = player,
                session = result.session,
                generation = generation++,
                boss = boss,
                state = MuspahState.STARTING,
                currentForm = initial,
                initialForm = initial,
                startReadyCycle = clock.cycle + 4,
            )
        runs[player.uid] = run
        boss.anim(MuspahAssets.SPAWN)
        player.mes("The Phantom Muspah begins to emerge from the darkness.")
        log.info {
            "[Muspah] player=${player.displayName} generation=${run.generation} " +
                "instance=${result.session.id} initial=$initial"
        }
        return true
    }

    fun tick(player: Player) {
        val run = runs[player.uid] ?: return
        if (!player.isSlotAssigned || player.pendingLogout || player.loggingOut) return
        if (instances.sessionForPlayer(player)?.id != run.session.id) {
            abort(player, "left instance without the crevice", teleport = false)
            return
        }
        corruption.tick(player)
        when (run.state) {
            MuspahState.STARTING -> if (clock.cycle >= run.startReadyCycle) activate(run)
            MuspahState.TRANSFORMING -> tickTransformation(run)
            MuspahState.LIGHTNING_CLOUDS -> tickLightning(run)
            MuspahState.HOMING_SPIKES -> tickHoming(run)
            MuspahState.SHOCKWAVE -> tickShockwave(run)
            MuspahState.RANGED,
            MuspahState.MELEE,
            MuspahState.PRAYER_SHIELD,
            MuspahState.FINAL_STAND -> keepAggressive(run)
            MuspahState.RESPAWNING -> if (clock.cycle >= run.respawnCycle) respawn(run)
            MuspahState.DYING,
            MuspahState.INVALIDATED -> Unit
        }
    }

    fun attack(access: StandardNpcAccess, target: Player) {
        val run = runs[target.uid] ?: return
        if (run.boss !== access.npc || !isCombatState(run.state)) return
        when (run.currentForm) {
            MuspahForm.MELEE -> attackMelee(access, run, target)
            MuspahForm.RANGED -> attackRanged(access, run, target, reinforced = false)
            MuspahForm.SHIELD -> attackRanged(access, run, target, reinforced = true)
            MuspahForm.FINAL -> attackRanged(access, run, target, reinforced = true)
            MuspahForm.TELEPORT -> Unit
        }
    }

    fun modifyNpcHit(player: Player, npc: Npc, hit: HitBuilder) {
        val run = runs[player.uid]
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

    private fun modifyShieldHit(run: Run, hit: HitBuilder) {
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

    fun beginBossDeath(npc: Npc): Run? {
        val run = runs.values.firstOrNull { it.boss === npc } ?: return null
        if (run.state in setOf(MuspahState.DYING, MuspahState.RESPAWNING)) return null
        run.state = MuspahState.DYING
        cleanupHazards(run)
        corruption.clear(run.player)
        run.player.combatClearQueue()
        val killcount = run.player.vars["varp.total_muspah_kills"] + 1
        VarPlayerIntMapSetter.set(run.player, "varp.total_muspah_kills", killcount)
        run.killcount = killcount
        run.killTicks = (clock.cycle - run.killStartCycle).coerceAtLeast(0)
        run.player.attr[BossRewardAttributes.phantomMuspahLastKillTicks] = run.killTicks
        return run
    }

    fun finishBossDeath(npc: Npc) {
        val run = runs.values.firstOrNull { it.boss === npc } ?: return
        if (runs[run.player.uid] !== run) return
        val oldPb = run.player.attr[MUSPAH_PERSONAL_BEST_TICKS]
        val personalBest = oldPb == null || run.killTicks < oldPb
        if (personalBest) run.player.attr[MUSPAH_PERSONAL_BEST_TICKS] = run.killTicks
        val suffix = if (personalBest) " <col=ff0000>New personal best!</col>" else ""
        run.player.mes(
            "<col=ff9900>Phantom Muspah kill ${run.killcount}: " +
                "${MuspahRules.formatTicks(run.killTicks)}.$suffix</col>"
        )
        run.state = MuspahState.RESPAWNING
        run.respawnCycle = clock.cycle + MUSPAH_RESPAWN_TICKS
    }

    fun isActive(player: Player): Boolean = runs.containsKey(player.uid)

    fun isEncounterNpc(npc: Npc): Boolean = runs.values.any { it.boss === npc }

    fun isOwnedBy(player: Player, npc: Npc): Boolean =
        runs[player.uid]?.let { it.boss === npc && npc.isSpawnOwnedBy(player) } == true

    fun attackDenial(player: Player, npc: Npc): String? {
        val run = runs[player.uid] ?: return "You do not have an active Phantom Muspah encounter."
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

    fun escape(player: Player): Boolean {
        if (!runs.containsKey(player.uid)) return false
        abort(player, "left through the crevice", teleport = true)
        player.mes("You escape from the Phantom Muspah's lair.")
        return true
    }

    fun abort(player: Player, reason: String, teleport: Boolean, logout: Boolean = false) {
        val run = runs.remove(player.uid) ?: return
        run.state = MuspahState.INVALIDATED
        cleanupHazards(run)
        corruption.clear(player)
        if (run.boss.isSlotAssigned) npcs.del(run.boss, Int.MAX_VALUE)
        player.lootDropDuration = null
        if (logout) {
            instances.handleLogout(player, clock.cycle)
        } else {
            val exit = instances.leave(player, run.session, clock.cycle)
            if (teleport && player.isSlotAssigned) {
                PathingEntityCommon.telejump(player, collision, exit)
            }
        }
        prepare(player)
        log.info {
            "[Muspah] player=${player.displayName} generation=${run.generation} abort=$reason"
        }
    }

    fun debugStatus(player: Player): String {
        val run = runs[player.uid] ?: return "Muspah: no active encounter."
        return "Muspah generation=${run.generation} state=${run.state} form=${run.currentForm} " +
            "hp=${run.boss.hitpoints} shield=${run.shieldPoints} " +
            "hits=${run.positiveAttacks} damage=${run.eligibleDamage} " +
            "spikes=${run.staticSpikes.size}+${run.movingSpikes.size} " +
            "clouds=${run.clouds.size}"
    }

    private fun activate(run: Run) {
        run.state = stateFor(run.currentForm)
        run.killStartCycle = clock.cycle
        run.boss.movementLocked = run.currentForm != MuspahForm.MELEE
        run.boss.apRangeOverride = if (run.currentForm == MuspahForm.MELEE) 1 else 10
        run.boss.apRequiresLineOfSight = true
        run.boss.apPlayer2(run.player, interactions)
    }

    private fun respawn(run: Run) {
        cleanupHazards(run)
        val initial = MuspahRules.startingForm(random.of(2))
        val boss = spawnBoss(run.player, run.session, initial)
        run.boss = boss
        run.state = MuspahState.STARTING
        run.currentForm = initial
        run.initialForm = initial
        run.firstSwapComplete = false
        run.positiveAttacks = 0
        run.eligibleDamage = 0
        run.lastPositiveAttackCycle = Int.MIN_VALUE
        run.firstSpecialFired = false
        run.secondSpecialFired = false
        run.shieldPoints = 0
        run.attackCounter = 0
        run.stationaryMeleeAttacks = 0
        run.startReadyCycle = clock.cycle + 4
        run.shieldSkipPending = false
        run.shieldDrainSources.clear()
        boss.anim(MuspahAssets.SPAWN)
        run.player.mes("A new Phantom Muspah begins to emerge.")
    }

    private fun beginTransformation(run: Run) {
        if (run.state == MuspahState.TRANSFORMING) return
        run.transformTarget =
            if (run.currentForm == MuspahForm.RANGED) MuspahForm.MELEE else MuspahForm.RANGED
        run.state = MuspahState.TRANSFORMING
        run.transformCycle = clock.cycle + 4
        run.boss.actionDelay = max(run.boss.actionDelay, run.transformCycle + 1)
        run.boss.anim(MuspahAssets.ATTACK_SUMMON)
        spawnCrack(run, run.player.coords)
    }

    private fun tickTransformation(run: Run) {
        if (clock.cycle < run.transformCycle) return
        val target = run.transformTarget ?: return
        run.boss.transmog(requireNpc(internalFor(target)), Int.MAX_VALUE)
        run.boss.anim(
            if (target == MuspahForm.MELEE) MuspahAssets.TRANSFORM_APPEAR_1
            else MuspahAssets.TRANSFORM_APPEAR_2
        )
        run.currentForm = target
        run.state = stateFor(target)
        run.firstSwapComplete = true
        run.positiveAttacks = 0
        run.eligibleDamage = 0
        run.lastPositiveAttackCycle = Int.MIN_VALUE
        run.stationaryMeleeAttacks = 0
        run.transformTarget = null
        run.boss.movementLocked = target != MuspahForm.MELEE
        run.boss.apRangeOverride = if (target == MuspahForm.MELEE) 1 else 10
        run.boss.apPlayer2(run.player, interactions)
    }

    private fun beginSpecial(run: Run, special: MuspahSpecial) {
        when (special) {
            MuspahSpecial.LIGHTNING_CLOUDS -> beginLightning(run)
            MuspahSpecial.HOMING_SPIKES -> beginHoming(run)
        }
    }

    private fun beginLightning(run: Run) {
        hardenMovingSpikes(run)
        run.state = MuspahState.LIGHTNING_CLOUDS
        run.currentForm = MuspahForm.TELEPORT
        run.lightningIndex = 0
        run.nextLightningCycle = clock.cycle + 2
        run.clouds.clear()
        run.boss.transmog(requireNpc(MuspahAssets.TELEPORT), Int.MAX_VALUE)
        run.boss.anim(MuspahAssets.TELEPORT_DISAPPEAR)
        run.boss.movementLocked = true
        run.boss.actionDelay = clock.cycle + 40
    }

    private fun tickLightning(run: Run) {
        tickClouds(run)
        if (clock.cycle < run.nextLightningCycle) return
        if (run.lightningIndex >= lightningRoute.size) {
            run.clouds.clear()
            run.boss.transmog(requireNpc(MuspahAssets.RANGED), Int.MAX_VALUE)
            run.boss.anim(MuspahAssets.TELEPORT_APPEAR)
            run.currentForm = MuspahForm.RANGED
            run.state = MuspahState.RANGED
            run.boss.actionDelay = clock.cycle + 3
            run.boss.apRangeOverride = 10
            run.boss.apPlayer2(run.player, interactions)
            return
        }
        val local = lightningRoute[run.lightningIndex++]
        val destination = requireLocal(run.session, local.first, local.second)
        run.boss.anim(MuspahAssets.TELEPORT_DISAPPEAR)
        run.boss.teleport(collision, destination)
        run.boss.anim(MuspahAssets.TELEPORT_APPEAR)
        spawnLightningCloud(run, destination)
        run.nextLightningCycle = clock.cycle + 3
    }

    private fun spawnLightningCloud(run: Run, source: CoordGrid) {
        val center = arenaCenter(run.session)
        val dx = (center.x - source.x).coerceIn(-1, 1)
        val dz = (center.z - source.z).coerceIn(-1, 1)
        run.clouds +=
            LightningCloud(
                tile = source,
                deltaX = dx,
                deltaZ = dz,
                endCycle = clock.cycle + 18,
                nextMoveCycle = clock.cycle,
            )
    }

    private fun tickClouds(run: Run) {
        val iterator = run.clouds.iterator()
        while (iterator.hasNext()) {
            val cloud = iterator.next()
            if (clock.cycle >= cloud.endCycle || !inArena(run.session, cloud.tile)) {
                iterator.remove()
                continue
            }
            if (clock.cycle >= cloud.nextMoveCycle) {
                cloud.tile = cloud.tile.translate(cloud.deltaX, cloud.deltaZ)
                cloud.nextMoveCycle++
                world.spotanimMap(spot(MuspahAssets.LIGHTNING), cloud.tile)
            }
            if (cloud.tile == run.player.coords && run.lastCloudHitCycle != clock.cycle) {
                run.lastCloudHitCycle = clock.cycle
                run.player.queueHit(
                    source = run.boss,
                    delay = 1,
                    type = HitType.Typeless,
                    damage = random.of(0..20),
                    modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                )
            }
        }
    }

    private fun beginHoming(run: Run) {
        run.resumeState = stateFor(run.currentForm)
        run.state = MuspahState.HOMING_SPIKES
        run.homingEndCycle = MuspahRules.homingEndCycle(clock.cycle)
        run.boss.anim(MuspahAssets.ATTACK_SUMMON)
        val desiredSeedCount = 4
        for (offset in homingSeedOffsets) {
            if (run.staticSpikes.size >= desiredSeedCount) break
            spawnStaticSpike(run, run.player.coords.translate(offset.first, offset.second))
        }
        val available = run.staticSpikes.values.toMutableList()
        if (available.isEmpty()) {
            finishHoming(run, resume = true)
            return
        }
        val amount = random.of(1..4).coerceAtMost(available.size)
        repeat(amount) {
            val selected = available.removeAt(random.of(available.size))
            run.staticSpikes.remove(selected.coords)
            locs.del(selected, Int.MAX_VALUE)
            val moving =
                locs.add(
                    selected.coords,
                    MuspahAssets.SPIKE_FOLLOW,
                    Int.MAX_VALUE,
                    selected.angle,
                    selected.shape,
                )
            run.movingSpikes += MovingSpike(moving, clock.cycle + 2)
        }
    }

    private fun tickHoming(run: Run) {
        if (MuspahRules.homingExpired(clock.cycle, run.homingEndCycle)) {
            finishHoming(run, resume = true)
            return
        }
        for (moving in run.movingSpikes) {
            if (clock.cycle < moving.nextMoveCycle) continue
            moving.nextMoveCycle += 2
            val next = homingNextTile(run, moving.loc.coords)
            if (next == moving.loc.coords) continue
            locs.del(moving.loc, Int.MAX_VALUE)
            moving.loc =
                locs.add(
                    next,
                    MuspahAssets.SPIKE_FOLLOW,
                    Int.MAX_VALUE,
                    moving.loc.angle,
                    moving.loc.shape,
                )
            if (next == run.player.coords) spikeHit(run)
        }
        keepAggressive(run)
    }

    private fun homingNextTile(run: Run, from: CoordGrid): CoordGrid {
        val primary =
            MuspahRules.nextOrthogonalStep(
                    from.x,
                    from.z,
                    run.player.coords.x,
                    run.player.coords.z,
                )
                .let { CoordGrid(it.first, it.second, from.level) }
        if (validSpikeTile(run, primary)) return primary
        val dx = (run.player.coords.x - from.x).coerceIn(-1, 1)
        val dz = (run.player.coords.z - from.z).coerceIn(-1, 1)
        val alternate =
            if (primary.x != from.x) from.translate(0, dz) else from.translate(dx, 0)
        return alternate.takeIf { validSpikeTile(run, it) } ?: from
    }

    private fun finishHoming(run: Run, resume: Boolean) {
        hardenMovingSpikes(run)
        if (resume && run.state == MuspahState.HOMING_SPIKES) {
            run.state = run.resumeState
            run.boss.apPlayer2(run.player, interactions)
        }
    }

    private fun hardenMovingSpikes(run: Run) {
        for (moving in run.movingSpikes) {
            val tile = moving.loc.coords
            locs.del(moving.loc, Int.MAX_VALUE)
            spawnStaticSpike(run, tile)
        }
        run.movingSpikes.clear()
    }

    private fun beginShieldTransition(run: Run) {
        if (run.state == MuspahState.SHOCKWAVE || run.state == MuspahState.PRAYER_SHIELD) return
        if (run.state == MuspahState.HOMING_SPIKES) finishHoming(run, resume = false)
        run.clouds.clear()
        run.state = MuspahState.SHOCKWAVE
        run.currentForm = MuspahForm.TELEPORT
        run.shockwaveCycle = clock.cycle + 4
        run.shieldApplyCycle = clock.cycle + 5
        run.shockwaveFired = false
        run.boss.transmog(requireNpc(MuspahAssets.TELEPORT), Int.MAX_VALUE)
        run.boss.anim(MuspahAssets.TELEPORT_DISAPPEAR)
        run.boss.teleport(collision, arenaCenter(run.session))
        run.boss.anim(MuspahAssets.ATTACK_EXPLOSION)
        run.boss.actionDelay = run.shieldApplyCycle + 2
    }

    private fun tickShockwave(run: Run) {
        if (!run.shockwaveFired && clock.cycle >= run.shockwaveCycle) {
            run.shockwaveFired = true
            world.spotanimMap(spot(MuspahAssets.SHOCKWAVE), arenaCenter(run.session))
            val exposed =
                lineValidator.hasLineOfSight(
                    level = run.boss.level,
                    srcX = run.boss.coords.x,
                    srcZ = run.boss.coords.z,
                    destX = run.player.coords.x,
                    destZ = run.player.coords.z,
                    srcWidth = run.boss.size,
                    srcLength = run.boss.size,
                )
            if (exposed) {
                run.player.queueHit(
                    source = run.boss,
                    delay = 1,
                    type = HitType.Typeless,
                    damage = random.of(0..80),
                    modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                )
                corruption.apply(run.player, strong = true)
            }
        }
        if (clock.cycle < run.shieldApplyCycle) return
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
        run.state = MuspahState.PRAYER_SHIELD
        run.shieldPoints = MUSPAH_SHIELD_POINTS
        run.attackCounter = 0
        run.boss.apRangeOverride = 10
        run.boss.movementLocked = true
        run.boss.apPlayer2(run.player, interactions)
    }

    private fun drainShield(run: Run, amount: Int) {
        run.shieldPoints = MuspahRules.drainShield(run.shieldPoints, amount)
        run.player.mes("The Phantom Muspah has ${run.shieldPoints} prayer shield points remaining.")
        if (run.shieldPoints == 0 && run.state == MuspahState.PRAYER_SHIELD) beginFinal(run)
    }

    private fun beginFinal(run: Run) {
        run.boss.transmog(requireNpc(MuspahAssets.FINAL), Int.MAX_VALUE)
        run.boss.anim(MuspahAssets.TRANSFORM_APPEAR_1)
        run.currentForm = MuspahForm.FINAL
        run.state = MuspahState.FINAL_STAND
        run.shieldPoints = 0
        run.attackCounter = 0
        run.boss.actionDelay = clock.cycle + 3
        run.boss.apRangeOverride = 10
        run.boss.movementLocked = true
        spawnCrack(run, run.player.coords)
        run.boss.apPlayer2(run.player, interactions)
    }

    private fun attackMelee(access: StandardNpcAccess, run: Run, target: Player) {
        val moved = run.boss.coords != run.boss.previousCoords
        val protected = target.vars["varbit.prayer_protectfrommelee"] == 1
        if (moved) run.stationaryMeleeAttacks = 0 else run.stationaryMeleeAttacks++
        val maximum =
            if (MuspahRules.meleeStepBackBlocks(moved, protected)) 0
            else MuspahRules.meleeMaximum(run.stationaryMeleeAttacks)
        combat.attack(
            access,
            target,
            CombatAttack.NpcMelee(MeleeAttackType.Crush),
            NpcAttackOverride(
                attackAnimation = MuspahAssets.ATTACK_MELEE,
                maxHit = maximum,
                attackRate = MUSPAH_MELEE_ATTACK_RATE,
                hitModifier = NpcPartialProtectionPrayerPlayerHitModifier(50),
                onAttack = { run.attackCounter++ },
            ),
        )
    }

    private fun attackRanged(
        access: StandardNpcAccess,
        run: Run,
        target: Player,
        reinforced: Boolean,
    ) {
        val magic = random.of(if (reinforced) 3 else 5) == 0
        val attack = if (magic) MuspahAttack.CHARGED_MAGIC else MuspahAttack.RANGED
        val protectedAtCalculation =
            if (magic) target.vars["varbit.prayer_protectfrommagic"] == 1
            else target.vars["varbit.prayer_protectfrommissiles"] == 1
        val maxHit =
            when {
                magic && reinforced -> 79
                magic -> 72
                reinforced -> 72
                else -> 61
            }
        val projectile =
            NpcProjectile(
                if (magic) MuspahAssets.MAGIC_TRAVEL else MuspahAssets.RANGED_TRAVEL,
                projectileType("projanim.magic"),
            )
        val override =
            NpcAttackOverride(
                attackAnimation =
                    if (magic) MuspahAssets.ATTACK_MAGIC else MuspahAssets.ATTACK_RANGED,
                maxHit = maxHit,
                projectile = projectile,
                impactDelay = if (magic) 3 else null,
                attackRate =
                    when (run.currentForm) {
                        MuspahForm.SHIELD -> MUSPAH_SHIELD_ATTACK_RATE
                        MuspahForm.FINAL -> MUSPAH_FINAL_ATTACK_RATE
                        else -> MUSPAH_RANGED_ATTACK_RATE
                    },
                hitModifier =
                    NpcCalculatedProtectionPrayerPlayerHitModifier(
                        protectedAtCalculation = protectedAtCalculation,
                        reductionPercent = if (magic) 100 else 90,
                    ),
                onAttack = {
                    run.attackCounter++
                    if (run.state == MuspahState.PRAYER_SHIELD) {
                        drainShield(run, MuspahRules.passiveShieldDrain())
                    }
                    if (
                        run.state == MuspahState.FINAL_STAND &&
                            run.attackCounter % MUSPAH_FINAL_SPIKE_ATTACKS == 0
                    ) {
                        spawnCrack(run, run.player.coords)
                        spreadFinalSpikes(run)
                    }
                },
                onImpact = { _, impact ->
                    target.spotanim(
                        if (magic) MuspahAssets.MAGIC_IMPACT else MuspahAssets.RANGED_IMPACT
                    )
                    if (magic && !protectedAtCalculation && impact.damage > 0) {
                        corruption.apply(target)
                    }
                    if (run.state == MuspahState.PRAYER_SHIELD && impact.damage > 0) {
                        run.boss.heal(MuspahRules.soulSplitHeal(impact.damage))
                    }
                },
            )
        combat.attack(
            access,
            target,
            if (attack == MuspahAttack.RANGED) {
                CombatAttack.NpcRanged(RangedAttackType.Standard)
            } else {
                CombatAttack.NpcMagic(maxHit)
            },
            override,
        )
    }

    private fun spawnCrack(run: Run, tile: CoordGrid) {
        if (!validSpikeTile(run, tile)) return
        world.spotanimMap(spot(MuspahAssets.SPIKE_WARNING), tile)
        val warning =
            locs.add(
                tile,
                MuspahAssets.SPIKE_PRE,
                2,
                LocAngle.North,
                LocShape.CentrepieceStraight,
                onDespawn = {
                    if (runs[run.player.uid] === run && run.state != MuspahState.INVALIDATED) {
                        spawnStaticSpike(run, tile)
                        if (run.player.coords == tile) spikeHit(run)
                    }
                },
            )
        run.warningSpikes += warning
    }

    private fun spawnStaticSpike(run: Run, tile: CoordGrid) {
        if (!validSpikeTile(run, tile)) return
        if (run.staticSpikes.containsKey(tile)) return
        val loc =
            locs.add(
                tile,
                MuspahAssets.SPIKE,
                Int.MAX_VALUE,
                LocAngle.North,
                LocShape.CentrepieceStraight,
            )
        run.staticSpikes[tile] = loc
    }

    private fun spreadFinalSpikes(run: Run) {
        val existing = run.staticSpikes.keys.toList()
        for (tile in existing.take(6)) {
            val candidates =
                listOf(
                    tile.translateX(1),
                    tile.translateX(-1),
                    tile.translateZ(1),
                    tile.translateZ(-1),
                )
            val target = candidates.firstOrNull { validSpikeTile(run, it) }
            if (target != null) spawnCrack(run, target)
        }
    }

    private fun spikeHit(run: Run) {
        val damage = random.of(0..20)
        run.player.queueHit(
            source = run.boss,
            delay = 1,
            type = HitType.Typeless,
            damage = damage,
            modifier = NpcBypassProtectionPrayerPlayerHitModifier,
            impactHandler =
                org.rsmod.game.hit.HitImpactHandler { impact ->
                    run.boss.heal(MuspahRules.spikeHeal(impact.damage))
                },
        )
        val destination =
            adjacentTiles(run.player.coords).firstOrNull {
                inArena(run.session, it) &&
                    it !in run.staticSpikes &&
                    run.movingSpikes.none { moving -> moving.loc.coords == it }
            }
        if (destination != null) PathingEntityCommon.telejump(run.player, collision, destination)
    }

    private fun cleanupHazards(run: Run) {
        run.warningSpikes.forEach { locs.del(it, Int.MAX_VALUE) }
        run.warningSpikes.clear()
        run.staticSpikes.values.forEach { locs.del(it, Int.MAX_VALUE) }
        run.staticSpikes.clear()
        run.movingSpikes.forEach { locs.del(it.loc, Int.MAX_VALUE) }
        run.movingSpikes.clear()
        run.clouds.clear()
    }

    private fun keepAggressive(run: Run) {
        if (run.boss.isSlotAssigned && run.boss.mode == null) {
            run.boss.apPlayer2(run.player, interactions)
        }
    }

    private fun canStartThresholdSpecial(run: Run): Boolean =
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

    private fun stateFor(form: MuspahForm): MuspahState =
        when (form) {
            MuspahForm.RANGED -> MuspahState.RANGED
            MuspahForm.MELEE -> MuspahState.MELEE
            MuspahForm.SHIELD -> MuspahState.PRAYER_SHIELD
            MuspahForm.FINAL -> MuspahState.FINAL_STAND
            MuspahForm.TELEPORT -> MuspahState.LIGHTNING_CLOUDS
        }

    private fun internalFor(form: MuspahForm): String =
        when (form) {
            MuspahForm.RANGED -> MuspahAssets.RANGED
            MuspahForm.MELEE -> MuspahAssets.MELEE
            MuspahForm.SHIELD -> MuspahAssets.SHIELD
            MuspahForm.FINAL -> MuspahAssets.FINAL
            MuspahForm.TELEPORT -> MuspahAssets.TELEPORT
        }

    private fun spawnBoss(
        player: Player,
        session: InstanceSession,
        form: MuspahForm,
    ): Npc {
        val boss = Npc(requireNpc(internalFor(form)), bossSpawn(session))
        boss.mode = null
        boss.baseHitpointsLvl = MUSPAH_MAX_HITPOINTS
        boss.hitpoints = MUSPAH_MAX_HITPOINTS
        boss.assignSpawnOwner(player, clock.cycle)
        boss.movementLocked = true
        boss.apRangeOverride = if (form == MuspahForm.MELEE) 1 else 10
        boss.apRequiresLineOfSight = true
        npcs.add(boss, MUSPAH_NPC_LIFETIME)
        instances.attachNpc(session.id, boss)
        return boss
    }

    private fun bossSpawn(session: InstanceSession): CoordGrid = requireLocal(session, 31, 34)

    private fun arenaCenter(session: InstanceSession): CoordGrid = requireLocal(session, 31, 34)

    private fun requireLocal(session: InstanceSession, x: Int, z: Int): CoordGrid =
        requireNotNull(instances.localCoord(session, RegionLocal(0, 44, 66, x, z)))

    private fun inArena(session: InstanceSession, tile: CoordGrid): Boolean {
        val southWest = requireLocal(session, 17, 19)
        val northEast = requireLocal(session, 43, 49)
        return tile.level == southWest.level &&
            tile.x in southWest.x..northEast.x &&
            tile.z in southWest.z..northEast.z
    }

    private fun validSpikeTile(run: Run, tile: CoordGrid): Boolean =
        inArena(run.session, tile) &&
            tile !in run.staticSpikes &&
            run.movingSpikes.none { it.loc.coords == tile } &&
            !bossOccupies(run, tile)

    private fun bossOccupies(run: Run, tile: CoordGrid): Boolean =
        tile.x in run.boss.coords.x until (run.boss.coords.x + run.boss.size) &&
            tile.z in run.boss.coords.z until (run.boss.coords.z + run.boss.size)

    private fun adjacentTiles(tile: CoordGrid): List<CoordGrid> =
        listOf(
            tile.translate(1, 1),
            tile.translate(-1, 1),
            tile.translate(1, -1),
            tile.translate(-1, -1),
            tile.translateX(1),
            tile.translateX(-1),
            tile.translateZ(1),
            tile.translateZ(-1),
        )

    private fun projectileType(internal: String): ProjAnimType =
        requireNotNull(ServerCacheManager.getProjectile(internal.asRSCM(RSCMType.PROJANIM))) {
            "Missing Phantom Muspah projectile controller: $internal"
        }

    private fun spot(internal: String): SpotanimType =
        SpotanimType(internal.asRSCM(RSCMType.SPOTANIM))

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

    internal data class Run(
        val player: Player,
        val session: InstanceSession,
        val generation: Long,
        var boss: Npc,
        var state: MuspahState,
        var currentForm: MuspahForm,
        var initialForm: MuspahForm,
        var resumeState: MuspahState = MuspahState.RANGED,
        var transformTarget: MuspahForm? = null,
        var firstSwapComplete: Boolean = false,
        var positiveAttacks: Int = 0,
        var eligibleDamage: Int = 0,
        var lastPositiveAttackCycle: Int = Int.MIN_VALUE,
        var firstSpecialFired: Boolean = false,
        var secondSpecialFired: Boolean = false,
        var shieldPoints: Int = 0,
        var attackCounter: Int = 0,
        var stationaryMeleeAttacks: Int = 0,
        var startReadyCycle: Int = Int.MAX_VALUE,
        var transformCycle: Int = Int.MAX_VALUE,
        var homingEndCycle: Int = Int.MAX_VALUE,
        var lightningIndex: Int = 0,
        var nextLightningCycle: Int = Int.MAX_VALUE,
        var shockwaveCycle: Int = Int.MAX_VALUE,
        var shieldApplyCycle: Int = Int.MAX_VALUE,
        var shockwaveFired: Boolean = false,
        var shieldSkipPending: Boolean = false,
        var respawnCycle: Int = Int.MAX_VALUE,
        var killStartCycle: Int = 0,
        var killTicks: Int = 0,
        var killcount: Int = 0,
        var lastCloudHitCycle: Int = Int.MIN_VALUE,
        val warningSpikes: MutableList<LocInfo> = mutableListOf(),
        val staticSpikes: MutableMap<CoordGrid, LocInfo> = linkedMapOf(),
        val movingSpikes: MutableList<MovingSpike> = mutableListOf(),
        val clouds: MutableList<LightningCloud> = mutableListOf(),
        val shieldDrainSources: MutableSet<String> = linkedSetOf(),
    )

    private companion object {
        val lightningRoute =
            listOf(
                41 to 34,
                23 to 43,
                31 to 22,
                39 to 43,
                20 to 34,
                39 to 25,
                31 to 46,
                23 to 25,
                41 to 34,
                23 to 43,
                31 to 22,
                39 to 43,
            )
        val homingSeedOffsets = listOf(4 to 0, -4 to 0, 0 to 4, 0 to -4)
    }
}
