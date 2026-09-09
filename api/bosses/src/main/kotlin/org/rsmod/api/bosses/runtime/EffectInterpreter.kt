package org.rsmod.api.bosses.runtime

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ProjAnimType
import dev.openrune.types.aconverted.SpotanimType
import org.rsmod.api.bosses.spec.*
import org.rsmod.api.bosses.spec.HitType as BossHitType
import org.rsmod.api.combat.NpcAttackOverride
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.DragonfireProtection
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.config.refs.params
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.NpcPartialProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.prayerLvl
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitAttribution
import org.rsmod.game.hit.HitContext
import org.rsmod.game.hit.HitImpactHandler
import org.rsmod.game.hit.HitType
import org.rsmod.game.hit.HitValidity
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid

class EffectInterpreter(
    private val npc: Npc,
    private val target: Player,
    private val spec: BossSpec,
    private val encounter: BossEncounter,
    private val deps: BossDeps,
) {

    suspend fun run(access: StandardNpcAccess, effect: Effect) {
        if (!deps.encounterRegistry.isCurrent(npc, encounter)) return
        val unregister = encounter.onCleanup { npc.cancelActiveCoroutine() }
        try {
            execute(access, effect)
        } finally {
            unregister()
        }
    }

    private suspend fun execute(access: StandardNpcAccess, effect: Effect) {
        when (effect) {
            is Effect.SetAttackRate -> encounter.attackRateOverride = effect.ticks
            is Effect.NpcAttack -> {
                val supplied =
                    effect.parameters?.let {
                        deps.extensionRegistry.attackParameters(
                            it,
                            npc,
                            target,
                            effect.parameterData,
                        )
                    } ?: NpcAttackOverride()
                val encounterValidity = validity(target)
                deps.npcCombat.attack(
                    access,
                    target,
                    effect.attack,
                    supplied.copy(
                        attackAnimation = supplied.attackAnimation ?: effect.animation,
                        maxHit = supplied.maxHit ?: effect.maxHit,
                        forceAccurate =
                            supplied.forceAccurate || effect.accuracy == AccuracyMode.Always,
                        projectile = supplied.projectile ?: effect.projectile,
                        impactDelay = supplied.impactDelay ?: effect.impactDelay,
                        attackRate = encounter.expectedAttackRate(),
                        hitModifier =
                            if (effect.protectionPrayer == ProtectionPrayer.Standard)
                                supplied.hitModifier
                            else protectionModifier(effect.protectionPrayer),
                        onImpact = { source, impact ->
                            supplied.onImpact?.invoke(source, impact)
                            effect.impact?.let { target.spotanim(it) }
                        },
                        hitValidity =
                            HitValidity {
                                encounterValidity.isValid() &&
                                    supplied.hitValidity?.isValid() != false
                            },
                        hitContext =
                            HitContext.compose(
                                supplied.hitContext ?: HitContext.EMPTY,
                                HitContext.of(HitAttribution(), encounter.effectScope),
                            ),
                    ),
                )
            }
            is Effect.Anim -> access.anim(effect.seq, effect.delay)
            is Effect.NpcAttackAnim -> {
                val animation = npc.visType.paramOrNull(params.attack_anim)
                if (animation != null) {
                    access.anim(RSCM.getReverseMapping(RSCMType.SEQ, animation.id))
                }
            }
            is Effect.Say -> access.say(effect.text)
            is Effect.Sound -> {
                deps.worldRepo.soundArea(npc.coords, effect.synth, radius = effect.radius)
            }
            is Effect.Spotanim -> access.spotanim(effect.spot, effect.delay, effect.height)
            is Effect.MapSpotanim -> {
                val coord = resolveTile(effect.at)
                val spot = SpotanimType(effect.spot.asRSCM(RSCMType.SPOTANIM))
                deps.worldRepo.spotanimMap(spot, coord, effect.height, effect.delay)
            }
            is Effect.Broadcast -> {
                val radius = effect.radius
                for (player in eligiblePlayers()) {
                    if (player.coords.chebyshevDistance(npc.coords) <= radius) {
                        player.mes(effect.text)
                    }
                }
            }
            is Effect.Delay -> access.delay(effect.ticks)
            is Effect.NoOp -> {}

            is Effect.Hit -> applyHit(effect)
            is Effect.Projectile -> fireProjectile(access, effect)
            is Effect.TileAoE -> applyTileAoE(effect)
            is Effect.Debris -> applyDebris(effect)
            is Effect.Summon -> summon(effect)
            is Effect.Poison -> applyPoison(effect)
            is Effect.Freeze -> applyFreeze(effect)
            is Effect.StatDrain -> applyStatDrain(effect)
            is Effect.Transmog -> {
                val npcType = ServerCacheManager.getNpc(effect.to.asRSCM(RSCMType.NPC))
                if (npcType != null) {
                    access.changeType(npcType, effect.durationTicks)
                }
            }

            is Effect.Run -> {
                val ability = spec.abilities[effect.ability] ?: return
                run(access, ability)
            }
            is Effect.TransitionTo -> {
                encounter.transitionTo(effect.phase, deps.mapClock.cycle)
            }
            is Effect.External -> {
                deps.extensionRegistry.invoke(effect.handler, access, npc, target, effect.params)
            }

            is Effect.Sequence -> {
                for (e in effect.effects) run(access, e)
            }
            is Effect.Parallel -> {
                access.parallel(effect.effects.map { child -> { run(this, child) } })
            }
            is Effect.Choose -> {
                val abilityName =
                    encounter.selectBranch(effect, deps.mapClock.cycle, target) ?: return
                val branch = effect.branches[abilityName] ?: return
                run(access, branch)
            }
            is Effect.Repeat -> {
                repeat(effect.times) { index ->
                    run(access, effect.effect)
                    if (index < effect.times - 1 && effect.gap > 0) access.delay(effect.gap)
                }
            }
            is Effect.Whenever -> {
                if (encounter.evaluate(effect.condition, target)) {
                    run(access, effect.then)
                } else {
                    run(access, effect.otherwise)
                }
            }
            is Effect.OnEach -> {
                val targets = resolveMulti(effect.targets)
                for (t in targets) {
                    val subInterpreter = EffectInterpreter(npc, t, spec, encounter, deps)
                    subInterpreter.run(access, effect.effect)
                }
            }
        }
    }

    private fun applyHit(hit: Effect.Hit) {
        val targets =
            when (val t = hit.target) {
                is TargetExpr.Single -> listOfNotNull(resolveSingle(t))
                is TargetExpr.Multi -> resolveMulti(t)
                else -> listOf(target)
            }
        val delay = hit.delay.coerceAtLeast(1)
        val expectedImpactTick = deps.mapClock.cycle + delay
        encounter.recordScheduledImpact(hit.type, expectedImpactTick)
        for (t in targets) {
            var damage = evaluateDamage(hit.damage)
            dragonfireType(hit.type)?.let { dfType ->
                val cap = DragonfireProtection.resolveMaxHit(t, dfType, damageMax(hit.damage))
                damage = if (cap <= 0) 0 else deps.random.of(cap + 1)
            }
            val accurate = rollAccuracy(t, hit)
            if (!accurate) damage = 0
            t.queueHit(
                npc,
                delay,
                hit.type.toEngine(),
                damage,
                modifier = hitModifier(hit),
                impactHandler = impactHandler(t, hit, accurate, expectedImpactTick),
                validity = validity(t),
                context = HitContext.of(HitAttribution(), encounter.effectScope),
            )
        }
    }

    private fun fireProjectile(access: StandardNpcAccess, proj: Effect.Projectile) {
        val t =
            resolveSingle(proj.target as? TargetExpr.Single ?: TargetExpr.CurrentTarget) ?: return
        val spotId = proj.spotanim.asRSCM(RSCMType.SPOTANIM)

        val type =
            if (proj.travel != null) {
                ServerCacheManager.getProjectile(proj.travel.asRSCM(RSCMType.PROJANIM))
                    ?: error("Projectile not found: ${proj.travel}")
            } else {
                val cfg = proj.config ?: ProjectileConfig()
                ProjAnimType(
                    startHeight = cfg.startHeight,
                    endHeight = cfg.endHeight,
                    delay = cfg.startDelay,
                    angle = cfg.angle,
                    lengthAdjustment = cfg.travelTime,
                    progress = cfg.progress,
                    stepMultiplier = cfg.stepMultiplier,
                )
            }

        val projAnim = ProjAnim.fromNpcToPlayer(npc, t, spotId, type)
        proj.launch?.let { access.spotanim(it) }
        deps.worldRepo.projAnim(projAnim)

        proj.hit?.let { hit ->
            var damage = evaluateDamage(hit.damage)
            dragonfireType(hit.type)?.let { dfType ->
                val cap = DragonfireProtection.resolveMaxHit(t, dfType, damageMax(hit.damage))
                damage = if (cap <= 0) 0 else deps.random.of(cap + 1)
            }
            val accurate = rollAccuracy(t, hit)
            if (!accurate) damage = 0
            val landedHit =
                if (proj.impact != null && hit.spotanim == null) hit.copy(spotanim = proj.impact)
                else hit
            val expectedImpactTick = deps.mapClock.cycle + projAnim.serverCycles + hit.delay
            encounter.recordScheduledImpact(hit.type, expectedImpactTick)
            t.queueHit(
                npc,
                projAnim.serverCycles + hit.delay,
                hit.type.toEngine(),
                damage,
                modifier = hitModifier(hit),
                impactHandler = impactHandler(t, landedHit, accurate, expectedImpactTick),
                validity = validity(t),
                context = HitContext.of(HitAttribution(), encounter.effectScope),
            )
        }
        if (proj.hit == null && proj.impact != null) {
            encounter.schedule(deps.worldQueues, projAnim.serverCycles) {
                if (validity(t).isValid()) t.spotanim(proj.impact)
            }
        }
    }

    private fun validity(player: Player): HitValidity = HitValidity {
        deps.encounterRegistry.isCurrent(npc, encounter) && eligible(player)
    }

    private fun impactHandler(
        player: Player,
        hit: Effect.Hit,
        accurate: Boolean,
        expectedImpactTick: Int,
    ): HitImpactHandler {
        return HitImpactHandler {
            if (!deps.encounterRegistry.isCurrent(npc, encounter)) return@HitImpactHandler
            if (!player.isSlotAssigned || player.hitpoints <= 0) return@HitImpactHandler
            if (
                player.coords.level != npc.coords.level ||
                    player.coords.chebyshevDistance(npc.coords) > MAX_PENDING_ATTACK_DISTANCE
            )
                return@HitImpactHandler
            encounter.recordActualImpact(expectedImpactTick, deps.mapClock.cycle)
            hit.spotanim?.let { player.spotanim(it, height = hit.spotanimHeight) }
            if (!accurate) return@HitImpactHandler
            for (effect in hit.impactEffects) {
                val (chance, outOf) = impactOdds(player, effect)
                if (deps.random.of(outOf) >= chance) continue
                when (effect) {
                    is ImpactEffect.Poison -> CombatEffects.poison(player, effect.damage)
                    is ImpactEffect.Freeze -> CombatEffects.freeze(player, effect.ticks)
                    is ImpactEffect.StatDrain ->
                        CombatEffects.statDrain(player, effect.stats, effect.amount)
                }
            }
        }
    }

    private fun impactOdds(player: Player, effect: ImpactEffect): Pair<Int, Int> =
        when (effect.protection) {
            ImpactProtection.None -> effect.chance to effect.outOf
            ImpactProtection.KingBlackDragon ->
                if (DragonfireProtection.hasKbdSecondaryEffectProtection(player)) 1 to 8
                else effect.chance to effect.outOf
        }

    private fun applyTileAoE(aoe: Effect.TileAoE) {
        val center = resolveTile(aoe.center)
        val radius = aoe.radius
        val targets = eligiblePlayers().filter { it.coords.chebyshevDistance(center) <= radius }
        for (t in targets) {
            val damage = evaluateDamage(aoe.damage)
            t.queueHit(
                npc,
                0,
                aoe.type.toEngine(),
                damage,
                validity = validity(t),
                context = HitContext.of(HitAttribution(), encounter.effectScope),
            )
        }
    }

    private fun applyDebris(effect: Effect.Debris) {
        val center = resolveTile(effect.center)
        val telegraphSpot = SpotanimType(effect.telegraph.asRSCM(RSCMType.SPOTANIM))

        val playersInRange =
            eligiblePlayers().filter { it.coords.chebyshevDistance(center) <= effect.targetRadius }
        val total = effect.count.first + deps.random.of(effect.count.last - effect.count.first + 1)
        val scatterCount = (total - playersInRange.size).coerceAtLeast(0)
        val scatterDiameter = effect.scatterRadius * 2 + 1
        val tiles =
            playersInRange.map { it.coords } +
                List(scatterCount) {
                    center.translate(
                        deps.random.of(scatterDiameter) - effect.scatterRadius,
                        deps.random.of(scatterDiameter) - effect.scatterRadius,
                    )
                }
        val tileSet = tiles.toSet()

        tiles.forEach { deps.worldRepo.spotanimMap(telegraphSpot, it) }

        encounter.schedule(deps.worldQueues, effect.windup) {
            if (!deps.encounterRegistry.isCurrent(npc, encounter)) return@schedule
            effect.impact?.let { impact ->
                val impactSpot = SpotanimType(impact.asRSCM(RSCMType.SPOTANIM))
                tileSet.forEach { deps.worldRepo.spotanimMap(impactSpot, it) }
            }
            val landing =
                eligiblePlayers().filter {
                    it.coords.chebyshevDistance(center) <= effect.targetRadius
                }
            for (player in landing) {
                if (player.hitpoints > 0 && player.coords in tileSet) {
                    player.queueHit(
                        npc,
                        1,
                        effect.type.toEngine(),
                        evaluateDamage(effect.damage),
                        validity = validity(player),
                        context = HitContext.of(HitAttribution(), encounter.effectScope),
                    )
                }
            }
        }
    }

    private fun summon(summon: Effect.Summon) {
        val npcTypeId = summon.npc.asRSCM(RSCMType.NPC)
        val npcType = ServerCacheManager.getNpc(npcTypeId) ?: return
        val center = resolveTile(summon.centeredOn)
        val radius = summon.radius
        val mode = summon.mode ?: npcType.defaultMode

        // Only consider tiles that are walkable
        val spawnTiles =
            buildList {
                    for (dx in -radius..radius) {
                        for (dz in -radius..radius) {
                            val origin = center.translate(dx, dz)
                            if (canStand(origin, npcType.size)) {
                                add(origin)
                            }
                        }
                    }
                }
                .toMutableList()

        repeat(summon.count) {
            val spawnCoord =
                if (spawnTiles.isNotEmpty()) {
                    spawnTiles.removeAt(deps.random.of(spawnTiles.size))
                } else {
                    center
                }
            val spawned = Npc(npcType, spawnCoord)
            spawned.mode = mode
            spawned.spawnOwner = npc.spawnOwner
            spawned.suppressRewards = npc.suppressRewards
            spawned.suppressDrops = npc.suppressDrops
            deps.npcRepo.add(spawned, 100)
            encounter.own(spawned)
        }
    }

    /** Whether an npc of [size] can stand at [origin]. */
    private fun canStand(origin: CoordGrid, size: Int): Boolean {
        for (dx in 0 until size) {
            for (dz in 0 until size) {
                if (deps.collision.isWalkBlocked(origin.translate(dx, dz))) {
                    return false
                }
            }
        }
        return true
    }

    private fun applyPoison(effect: Effect.Poison) {
        if (deps.random.of(effect.outOf) < effect.chance) {
            CombatEffects.poison(target, effect.damage)
        }
    }

    private fun applyFreeze(effect: Effect.Freeze) {
        if (deps.random.of(effect.outOf) < effect.chance) {
            CombatEffects.freeze(target, effect.ticks)
        }
    }

    private fun applyStatDrain(effect: Effect.StatDrain) {
        for (entry in effect.entries) {
            if (deps.random.of(entry.outOf) < entry.chance) {
                CombatEffects.statDrain(target, listOf(entry.stat), entry.amount)
            }
        }
    }

    private fun resolveSingle(expr: TargetExpr.Single): Player? {
        return when (expr) {
            is TargetExpr.CurrentTarget -> target.takeIf(::eligible)
            is TargetExpr.Self -> null
            is TargetExpr.CurrentTargetTile -> null
            is TargetExpr.HighestDamageDealer ->
                eligiblePlayers().maxByOrNull { npc.damageContributions.damageBy(it) }
            is TargetExpr.LowestPrayer -> eligiblePlayers().minByOrNull { it.prayerLvl }
            is TargetExpr.RandomNearby -> deps.random.pickOrNull(eligiblePlayers())
        }
    }

    private fun resolveTile(expr: TargetExpr): org.rsmod.map.CoordGrid {
        return when (expr) {
            is TargetExpr.CurrentTarget -> target.coords
            is TargetExpr.CurrentTargetTile -> target.coords
            is TargetExpr.Self -> npc.coords
            else -> npc.coords
        }
    }

    private fun rollAccuracy(player: Player, hit: Effect.Hit): Boolean {
        if (hit.accuracy == AccuracyMode.Always) return true
        return when (hit.type) {
            BossHitType.Melee ->
                deps.accuracy.rollMeleeAccuracy(
                    npc = npc,
                    target = player,
                    attackType = hit.meleeType ?: MeleeAttackType.Crush,
                    random = deps.random,
                )
            BossHitType.Ranged -> deps.accuracy.rollRangedAccuracy(npc, player, deps.random)
            BossHitType.Magic -> deps.accuracy.rollMagicAccuracy(npc, player, deps.random)
            BossHitType.Dragonfire,
            BossHitType.DragonfireMetal,
            BossHitType.WyvernIce,
            BossHitType.KbdDragonfire,
            BossHitType.KbdSpecialDragonfire,
            BossHitType.Typeless -> true
        }
    }

    private fun hitModifier(hit: Effect.Hit): PlayerHitModifier =
        protectionModifier(hit.protectionPrayer)

    private fun protectionModifier(prayer: ProtectionPrayer): PlayerHitModifier =
        when (prayer) {
            ProtectionPrayer.Standard -> StandardPlayerHitModifier
            ProtectionPrayer.Bypass -> NpcBypassProtectionPrayerPlayerHitModifier
            is ProtectionPrayer.Reduce ->
                NpcPartialProtectionPrayerPlayerHitModifier(prayer.percent)
        }

    private fun resolveMulti(expr: TargetExpr): List<Player> {
        return when (expr) {
            is TargetExpr.AllInRadius -> {
                val center = resolveTile(expr.of)
                eligiblePlayers().filter { it.coords.chebyshevDistance(center) <= expr.radius }
            }
            is TargetExpr.TopN ->
                when (expr.by) {
                    TargetExpr.HighestDamageDealer ->
                        eligiblePlayers().sortedByDescending {
                            npc.damageContributions.damageBy(it)
                        }
                    TargetExpr.LowestPrayer -> eligiblePlayers().sortedBy { it.prayerLvl }
                    TargetExpr.RandomNearby ->
                        buildList {
                            val remaining = eligiblePlayers().toMutableList()
                            repeat(minOf(expr.n.coerceAtLeast(0), remaining.size)) {
                                add(remaining.removeAt(deps.random.of(remaining.size)))
                            }
                        }
                    else -> listOfNotNull(resolveSingle(expr.by))
                }.take(expr.n.coerceAtLeast(0))
            is TargetExpr.Single -> listOfNotNull(resolveSingle(expr))
            else -> listOf(target)
        }
    }

    private fun eligiblePlayers(): List<Player> {
        val owner = npc.spawnOwner
        if (owner != PlayerUid.NULL) {
            return owner.resolve(deps.playerList)?.takeIf(::eligible)?.let(::listOf) ?: emptyList()
        }
        return deps.playerList.filter(::eligible)
    }

    private fun eligible(player: Player): Boolean =
        player.isSlotAssigned &&
            player.hitpoints > 0 &&
            player.coords.level == npc.coords.level &&
            npc.isWithinDistance(player, MAX_PENDING_ATTACK_DISTANCE) &&
            (npc.spawnOwner == PlayerUid.NULL || npc.spawnOwner == player.uid)

    private fun damageMax(expr: DamageExpr): Int =
        when (expr) {
            is DamageExpr.Roll -> if (expr.range.isEmpty()) 0 else expr.range.last
            is DamageExpr.Fixed -> expr.value
            else -> evaluateDamage(expr)
        }

    private fun dragonfireType(t: BossHitType): DragonfireProtection.DragonfireType? =
        when (t) {
            BossHitType.Dragonfire -> DragonfireProtection.DragonfireType.Chromatic
            BossHitType.DragonfireMetal -> DragonfireProtection.DragonfireType.Metal
            BossHitType.WyvernIce -> DragonfireProtection.DragonfireType.WyvernIce
            BossHitType.KbdDragonfire -> DragonfireProtection.DragonfireType.KbdStandard
            BossHitType.KbdSpecialDragonfire -> DragonfireProtection.DragonfireType.KbdSpecial
            else -> null
        }

    private fun evaluateDamage(expr: DamageExpr): Int {
        return when (expr) {
            is DamageExpr.Fixed -> expr.value
            is DamageExpr.Roll ->
                if (expr.range.isEmpty()) 0
                else {
                    expr.range.first + deps.random.of(expr.range.last - expr.range.first + 1)
                }
            is DamageExpr.Accuracy -> evaluateDamage(expr.on)
            is DamageExpr.PercentOfTargetHp -> (target.hitpoints * expr.fraction).toInt()
            is DamageExpr.Min -> minOf(evaluateDamage(expr.a), evaluateDamage(expr.b))
            is DamageExpr.Max -> maxOf(evaluateDamage(expr.a), evaluateDamage(expr.b))
        }
    }

    private fun BossHitType.toEngine(): HitType =
        when (this) {
            BossHitType.Melee -> HitType.Melee
            BossHitType.Ranged -> HitType.Ranged
            BossHitType.Magic -> HitType.Magic
            BossHitType.Dragonfire -> HitType.Magic
            BossHitType.DragonfireMetal -> HitType.Magic
            BossHitType.WyvernIce -> HitType.Magic
            BossHitType.KbdDragonfire -> HitType.Magic
            BossHitType.KbdSpecialDragonfire -> HitType.Magic
            BossHitType.Typeless -> HitType.Typeless
        }
}

private const val MAX_PENDING_ATTACK_DISTANCE = 32
