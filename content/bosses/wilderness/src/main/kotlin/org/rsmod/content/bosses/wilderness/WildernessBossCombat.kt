package org.rsmod.content.bosses.wilderness

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ProjAnimType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.bosses.spec.*
import org.rsmod.api.bosses.validation.BossAssetValidator
import org.rsmod.api.combat.NpcAttackOverride
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.magic.MagicSpellChecks
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.heal
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.feet
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.NpcPartialProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.worn.WornUnequipOp
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.walkTo
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onEvent
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.script.onNpcHit
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitImpactHandler
import org.rsmod.game.hit.HitType as EngineHitType
import org.rsmod.game.inv.isType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.StepValidator

/** Combat ownership for Wilderness bosses that were missing from the production boss runtime. */
public class WildernessBossCombat
@Inject
constructor(
    private val deps: BossDeps,
    private val wornBonuses: WornBonuses,
    private val unequipOp: WornUnequipOp,
    private val locRepo: LocRepository,
    private val routeFactory: RouteFactory,
    private val aiPlayerInteractions: AiPlayerInteractions,
    private val nvpCombat: NvPCombat,
    private val instances: InstanceManager,
) : PluginScript() {
    private val stepValidator = StepValidator(deps.collision)
    private val rayCastValidator = RayCastValidator(deps.collision)
    private val logger = InlineLogger()
    private val traceBearBosses =
        System.getProperty("openrune.bearBossTrace")?.toBooleanStrictOrNull() == true
    private val traceSpiderBosses =
        System.getProperty("openrune.spiderBossTrace")?.toBooleanStrictOrNull() == true
    private val traceUndeadBosses =
        System.getProperty("openrune.undeadBossTrace")?.toBooleanStrictOrNull() == true

    override fun ScriptContext.startup() {
        registerChaosElementalMechanics()
        registerMaledictusMechanics()
        registerBearMechanics()
        registerSpiderMechanics()
        registerUndeadMechanics()
        registerHoundMechanics(this)
        validateRuntimeAssets()

        BossCombat.register(this, CHAOS_ELEMENTAL_SPEC, deps, ownerSlug = "chaos-elemental")
        BossCombat.register(this, MALEDICTUS_SPEC, deps, ownerSlug = "revenant-maledictus")
        BossCombat.register(
            this,
            CALLISTO_SPEC,
            deps,
            ownerSlug = "callisto",
            onLethal = ::finishBearFight,
            beforeModifyHit = { prepareBearIncomingHit(npc) },
            onModifyHit = { markBearThresholds(npc, hit.damage) },
        )
        BossCombat.register(
            this,
            ARTIO_SPEC,
            deps,
            ownerSlug = "artio",
            onLethal = ::finishBearFight,
            beforeModifyHit = { prepareBearIncomingHit(npc) },
            onModifyHit = { markBearThresholds(npc, hit.damage) },
        )
        BossCombat.register(
            this,
            VENENATIS_SPEC,
            deps,
            ownerSlug = "venenatis",
            onLethal = ::finishSpiderFight,
            onAbilityCommitted = ::afterSpiderAttack,
        )
        BossCombat.register(
            this,
            SPINDEL_SPEC,
            deps,
            ownerSlug = "spindel",
            onLethal = ::finishSpiderFight,
            onAbilityCommitted = ::afterSpiderAttack,
        )
        registerUndeadBoss(this, VETION_SPEC, VETION_PROFILE)
        registerUndeadBoss(this, CALVARION_SPEC, CALVARION_PROFILE)
        BossCombat.register(
            this,
            SPIDERLING_SPEC,
            deps,
            ownerSlug = "wilderness-spiderlings",
            onModifyHit = { hit.damage = spiderlingForcedDamage(npc.hitpoints) },
        )
        registerSpiderLifecycle()
        registerBearLifecycle()
        registerUndeadLifecycle()
    }

    private fun validateRuntimeAssets() {
        val missing = linkedSetOf<String>()
        WILDERNESS_BOSS_SPECS.forEach { spec ->
            BossAssetValidator.validate(spec, deps.extensionRegistry).missing.forEach {
                missing += "${spec.npcTypes.first()}: $it"
            }
        }
        WILDERNESS_RUNTIME_SEQUENCE_KEYS.forEach { key ->
            validateAsset(key, RSCMType.SEQ, { ServerCacheManager.getAnim(it) != null }, missing)
        }
        WILDERNESS_RUNTIME_SPOTANIM_KEYS.forEach { key ->
            validateAsset(key, RSCMType.SPOTANIM, { true }, missing)
        }
        WILDERNESS_RUNTIME_LOC_KEYS.forEach { key ->
            validateAsset(key, RSCMType.LOC, { ServerCacheManager.getObject(it) != null }, missing)
        }
        WILDERNESS_RUNTIME_NPC_KEYS.forEach { key ->
            validateAsset(key, RSCMType.NPC, { ServerCacheManager.getNpc(it) != null }, missing)
        }
        check(missing.isEmpty()) {
            "Wilderness boss combat references missing revision-240 assets:\n" +
                missing.joinToString("\n") { "  - $it" }
        }
    }

    private fun validateAsset(
        key: String,
        type: RSCMType,
        present: (Int) -> Boolean,
        missing: MutableSet<String>,
    ) {
        val id =
            runCatching { key.asRSCM(type) }
                .getOrElse {
                    missing += "$key (no ${type.prefix} RSCM mapping)"
                    return
                }
        if (!present(id)) missing += "$key ($id absent from active server cache)"
    }

    private fun registerUndeadBoss(
        context: ScriptContext,
        spec: BossSpec,
        profile: UndeadBossProfile,
    ) {
        val secondForm =
            ServerCacheManager.getNpc(profile.secondForm.asRSCM(RSCMType.NPC))
                ?: error("Missing npc type: ${profile.secondForm}")
        BossCombat.register(
            context,
            spec,
            deps,
            ownerSlug = profile.slug,
            onLethal = ::finishUndeadFight,
            beforeModifyHit = {
                val encounter = deps.encounterRegistry.of(npc)
                val state = undeadState(npc, encounter)
                prepareUndeadIncomingHit(npc, encounter, state, profile)
            },
            onModifyHit = {
                val encounter = deps.encounterRegistry.of(npc)
                val state = undeadState(npc, encounter)
                modifyUndeadHit(npc, hit, encounter, state, profile, secondForm)
            },
            onAbilityCommitted = ::afterUndeadAttack,
        )
    }

    private fun registerChaosElementalMechanics() {
        deps.extensionRegistry.register(CHAOS_TELEPORT) { access, npc, target, _ ->
            access.anim(CHAOS_ELEMENTAL_ATTACK_ANIM)
            access.spotanim(CHAOS_CONFUSION_CAST)
            deps.worldRepo.projAnimSourced(
                npc,
                target,
                CHAOS_CONFUSION_TRAVEL_TYPE,
                CHAOS_ELEMENTAL_PROJECTILE,
            )
            val encounter = deps.encounterRegistry.of(npc)
            deps.worldQueues.add(CHAOS_SPECIAL_DELAY_TICKS) {
                if (!deps.encounterRegistry.isCurrent(npc, encounter)) return@add
                if (!target.isSlotAssigned || target.hitpoints <= 0) return@add
                if (
                    target.coords.level != npc.coords.level ||
                        target.coords.chebyshevDistance(npc.coords) > 32
                )
                    return@add
                val originalDistance = target.coords.chebyshevDistance(npc.coords)
                repeat(32) {
                    val tile =
                        target.coords.translate(deps.random.of(11) - 5, deps.random.of(11) - 5)
                    val movementDistance = tile.chebyshevDistance(target.coords)
                    val distanceFromElemental = tile.chebyshevDistance(npc.coords)
                    if (
                        movementDistance > 0 &&
                            distanceFromElemental > originalDistance &&
                            !deps.collision.isWalkBlocked(tile)
                    ) {
                        PathingEntityCommon.teleport(target, deps.collision, tile)
                        target.spotanim(CHAOS_CONFUSION_IMPACT)
                        return@add
                    }
                }
            }
        }
        deps.extensionRegistry.register(CHAOS_DISARM) { access, npc, target, _ ->
            access.anim(CHAOS_ELEMENTAL_ATTACK_ANIM)
            access.spotanim(CHAOS_MADNESS_CAST)
            deps.worldRepo.projAnimSourced(
                npc,
                target,
                CHAOS_MADNESS_TRAVEL_TYPE,
                CHAOS_ELEMENTAL_PROJECTILE,
            )
            val encounter = deps.encounterRegistry.of(npc)
            deps.worldQueues.add(CHAOS_SPECIAL_DELAY_TICKS) {
                if (!deps.encounterRegistry.isCurrent(npc, encounter)) return@add
                if (!target.isSlotAssigned || target.hitpoints <= 0) return@add
                if (
                    target.coords.level != npc.coords.level ||
                        target.coords.chebyshevDistance(npc.coords) > 32
                )
                    return@add
                target.spotanim(CHAOS_MADNESS_IMPACT)
                repeat(4) {
                    if (target.inv.isFull()) return@repeat
                    val occupied = target.worn.mapSlots { _, obj -> obj != null }.toList()
                    val slot = deps.random.pickOrNull(occupied) ?: return@repeat
                    unequipOp.unequip(target, slot, target.worn, target.inv)
                }
            }
        }
    }

    private fun registerMaledictusMechanics() {
        deps.extensionRegistry.register(MALEDICTUS_ICE) { access, npc, _, _ ->
            access.anim("seq.npc_revenant_superior_attack_stun")
            val encounter = deps.encounterRegistry.of(npc)
            val tiles = nearbyPlayers(npc, 16).associateWith { it.coords }
            deps.worldQueues.add(3) {
                if (!deps.encounterRegistry.isCurrent(npc, encounter)) return@add
                tiles.forEach { (player, tile) ->
                    deps.worldRepo.spotanimMap(
                        SpotanimType(
                            "spotanim.revenant_superior_special_2_impact".asRSCM(RSCMType.SPOTANIM)
                        ),
                        tile,
                    )
                    if (player.isSlotAssigned && player.hitpoints > 0 && player.coords == tile) {
                        player.queueHit(
                            npc,
                            1,
                            EngineHitType.Magic,
                            deps.random.of(31),
                            modifier = NpcPartialProtectionPrayerPlayerHitModifier(50),
                            impactHandler =
                                HitImpactHandler {
                                    if (it.damage >= 0) CombatEffects.freeze(player, 10)
                                },
                        )
                    }
                }
            }
        }
        deps.extensionRegistry.register(MALEDICTUS_BLOOD) { access, npc, _, _ ->
            access.anim("seq.npc_revenant_superior_attack_heal")
            val encounter = deps.encounterRegistry.of(npc)
            val tiles = nearbyPlayers(npc, 16).associateWith { it.coords }
            deps.worldQueues.add(3) {
                if (!deps.encounterRegistry.isCurrent(npc, encounter)) return@add
                tiles.forEach { (player, tile) ->
                    if (player.isSlotAssigned && player.hitpoints > 0 && player.coords == tile) {
                        player.queueHit(
                            npc,
                            1,
                            EngineHitType.Magic,
                            deps.random.of(31),
                            modifier = NpcPartialProtectionPrayerPlayerHitModifier(50),
                            impactHandler = HitImpactHandler { npc.heal(it.damage / 2) },
                        )
                    }
                }
            }
        }
        deps.extensionRegistry.register(MALEDICTUS_AIR) { access, npc, target, _ ->
            access.anim("seq.npc_revenant_superior_attack_dot")
            val encounter = deps.encounterRegistry.of(npc)
            val center = target.coords
            deps.worldQueues.add(2) {
                if (!deps.encounterRegistry.isCurrent(npc, encounter)) return@add
                eligiblePlayers(npc)
                    .filter { it.coords.chebyshevDistance(center) <= 2 }
                    .forEach { player ->
                        val max =
                            (25 - player.coords.chebyshevDistance(center) * 5).coerceAtLeast(5)
                        player.queueHit(
                            npc,
                            1,
                            EngineHitType.Typeless,
                            deps.random.of(max + 1),
                            modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                        )
                    }
            }
        }
    }

    private fun registerBearMechanics() {
        deps.extensionRegistry.register(BEAR_ATTACK) { _, npc, target, _ ->
            performBearAttack(npc, target)
        }
    }

    private fun performBearAttack(npc: Npc, primary: Player) {
        val encounter = deps.encounterRegistry.of(npc)
        val profile = bearProfile(npc) ?: return
        val state = bearState(npc, encounter)
        recoverBearLifecycleIfNeeded(npc, encounter, profile, state)
        if (
            !state.lifecycle.allowsAttacks ||
                !state.active ||
                !primary.isValidTarget() ||
                !isBearArenaTile(npc, profile, primary.coords)
        ) {
            return
        }
        npc.facePlayer(primary)

        if (deps.mapClock.cycle < state.roaringUntil) return
        state.attackCounter++
        if (state.pendingRoars.isNotEmpty()) {
            traceBearEvent(
                npc,
                encounter,
                profile,
                state,
                "attack-selected",
                primary,
                "attack=roar reason=pending-threshold",
            )
            performBearRoar(npc, encounter, profile, state, state.pendingRoars.removeFirst())
            return
        }
        if (state.magicCountdown <= 0) {
            state.magicCountdown = nextBearMagicCountdown()
            traceBearEvent(
                npc,
                encounter,
                profile,
                state,
                "attack-selected",
                primary,
                "attack=magic-orb reason=special-counter-ready",
            )
            performBearMagicOrb(npc, primary, encounter, profile, state)
            if (state.lowTrapPhase) deployBearTraps(npc, encounter, profile, state)
            return
        }

        state.magicCountdown--
        if (npc.isWithinDistance(primary, 1)) {
            performBearMelee(npc, primary, encounter, profile, state)
        } else {
            performBearRanged(npc, primary, encounter, profile, state)
        }
        if (state.lowTrapPhase) deployBearTraps(npc, encounter, profile, state)
    }

    private fun performBearMelee(
        npc: Npc,
        primary: Player,
        encounter: BossEncounter,
        profile: BearProfile,
        state: BearRuntimeState,
    ) {
        traceBearEvent(
            npc,
            encounter,
            profile,
            state,
            "attack-selected",
            primary,
            "attack=melee reason=within-melee-reach animId=${BEAR_MELEE_ANIM.asRSCM(RSCMType.SEQ)}",
        )
        npc.anim(BEAR_MELEE_ANIM)
        val targets =
            if (profile.multiTarget) {
                bearPlayers(npc, profile).filter { npc.isWithinDistance(it, 1) }
            } else {
                listOf(primary)
            }
        targets.forEach { player ->
            val accurate =
                deps.accuracy.rollMeleeAccuracy(
                    npc = npc,
                    target = player,
                    attackType = MeleeAttackType.Crush,
                    random = deps.random,
                )
            val damage =
                if (!accurate) 0
                else profile.meleeMin + deps.random.of(profile.meleeMax - profile.meleeMin + 1)
            deps.worldQueues.add(BEAR_MELEE_IMPACT_DELAY) {
                if (!isCurrentBearAttack(npc, encounter, state, player, profile)) return@add
                traceBearEvent(
                    npc,
                    encounter,
                    profile,
                    state,
                    "impact",
                    player,
                    "attack=melee prayerAtImpact=${player.protectingAgainst(HitType.Melee)} damage=$damage",
                )
                player.queueHit(
                    npc,
                    BEAR_HIT_QUEUE_DELAY,
                    EngineHitType.Melee,
                    damage,
                    modifier = NpcPartialProtectionPrayerPlayerHitModifier(50),
                )
            }
        }
    }

    private fun performBearRanged(
        npc: Npc,
        primary: Player,
        encounter: BossEncounter,
        profile: BearProfile,
        state: BearRuntimeState,
    ) {
        traceBearEvent(
            npc,
            encounter,
            profile,
            state,
            "attack-selected",
            primary,
            "attack=ranged reason=outside-melee-reach animId=${BEAR_RANGED_ANIM.asRSCM(RSCMType.SEQ)}",
        )
        npc.anim(BEAR_RANGED_ANIM)
        npc.spotanim(BEAR_RANGED_CAST)
        val targets = if (profile.multiTarget) bearPlayers(npc, profile) else listOf(primary)
        targets.forEach { player ->
            val accurate = deps.accuracy.rollRangedAccuracy(npc, player, deps.random)
            val damage = if (accurate) deps.random.of(profile.rangedMax + 1) else 0
            val projectile =
                deps.worldRepo.projAnimSourced(
                    npc,
                    player,
                    SpotanimType(BEAR_RANGED_PROJECTILE.asRSCM(RSCMType.SPOTANIM)),
                    BEAR_RANGED_PROJECTILE_TYPE,
                )
            val impactDelay = projectile.serverCycles.coerceAtLeast(BEAR_HIT_QUEUE_DELAY)
            val expectedImpact = deps.mapClock.cycle + impactDelay
            encounter.recordScheduledImpact(HitType.Ranged, expectedImpact)
            traceBearEvent(
                npc,
                encounter,
                profile,
                state,
                "projectile-launch",
                player,
                "attack=ranged projectileId=${BEAR_RANGED_PROJECTILE.asRSCM(RSCMType.SPOTANIM)} predictedImpact=$expectedImpact",
            )
            deps.worldQueues.add(impactDelay) {
                if (!isCurrentBearAttack(npc, encounter, state, player, profile)) return@add
                encounter.recordActualImpact(expectedImpact, deps.mapClock.cycle)
                traceBearEvent(
                    npc,
                    encounter,
                    profile,
                    state,
                    "impact",
                    player,
                    "attack=ranged prayerAtImpact=${player.protectingAgainst(HitType.Ranged)} damage=$damage",
                )
                player.spotanim(BEAR_RANGED_IMPACT)
                player.queueHit(
                    npc,
                    BEAR_HIT_QUEUE_DELAY,
                    EngineHitType.Ranged,
                    damage,
                    modifier = StandardPlayerHitModifier,
                )
            }
        }
    }

    private fun performBearMagicOrb(
        npc: Npc,
        primary: Player,
        encounter: BossEncounter,
        profile: BearProfile,
        state: BearRuntimeState,
    ) {
        npc.anim(BEAR_MAGIC_ANIM)
        val projectileKey = if (profile.multiTarget) BEAR_MAGIC_ORB_LARGE else BEAR_MAGIC_ORB_SMALL
        val projectile =
            deps.worldRepo.projAnimSourced(
                npc,
                primary,
                SpotanimType(projectileKey.asRSCM(RSCMType.SPOTANIM)),
                BEAR_MAGIC_PROJECTILE_TYPE,
            )
        val impactDelay = projectile.serverCycles.coerceAtLeast(BEAR_HIT_QUEUE_DELAY)
        val expectedImpact = deps.mapClock.cycle + impactDelay
        encounter.recordScheduledImpact(HitType.Magic, expectedImpact)
        traceBearEvent(
            npc,
            encounter,
            profile,
            state,
            "projectile-launch",
            primary,
            "attack=magic-orb animId=${BEAR_MAGIC_ANIM.asRSCM(RSCMType.SEQ)} projectileId=${projectileKey.asRSCM(RSCMType.SPOTANIM)} predictedImpact=$expectedImpact",
        )
        deps.worldQueues.add(impactDelay) {
            if (!isCurrentBearAttack(npc, encounter, state, primary, profile)) return@add
            encounter.recordActualImpact(expectedImpact, deps.mapClock.cycle)
            val impactTile = primary.coords
            val victims =
                if (profile.multiTarget) {
                    bearPlayers(npc, profile).filter {
                        it.coords.chebyshevDistance(impactTile) <= 1
                    }
                } else {
                    listOf(primary)
                }
            victims.forEach { player ->
                val protected = player.protectingAgainst(HitType.Magic)
                traceBearEvent(
                    npc,
                    encounter,
                    profile,
                    state,
                    "impact",
                    player,
                    "attack=magic-orb prayerAtImpact=$protected splashCentre=$impactTile",
                )
                if (!protected) knockBack(npc, player, encounter, profile, state)
            }
        }
    }

    private fun knockBack(
        npc: Npc,
        player: Player,
        encounter: BossEncounter,
        profile: BearProfile,
        state: BearRuntimeState,
    ) {
        if (!isCurrentBearAttack(npc, encounter, state, player, profile)) return
        val center = npc.coords.translate(npc.size / 2, npc.size / 2)
        var dx = (player.coords.x - center.x).coerceIn(-1, 1)
        var dz = (player.coords.z - center.z).coerceIn(-1, 1)
        if (dx == 0 && dz == 0) {
            dx = 1
        }
        var destination = player.coords
        var successfulSteps = 0
        for (step in 0 until BEAR_KNOCKBACK_DISTANCE) {
            val next = destination.translate(dx, dz)
            val canTravel =
                isBearArenaTile(npc, profile, next) &&
                    next !in state.traps &&
                    !occupiesBearTile(npc, next) &&
                    deps.collision.isZoneValid(next) &&
                    !deps.collision.isWalkBlocked(next) &&
                    stepValidator.canTravel(
                        level = destination.level,
                        x = destination.x,
                        z = destination.z,
                        offsetX = dx,
                        offsetZ = dz,
                        size = 1,
                    )
            if (!canTravel) break
            destination = next
            successfulSteps++
        }
        player.routeDestination.clear()
        if (destination != player.coords) {
            PathingEntityCommon.teleport(player, deps.collision, destination)
        }
        val damage = bearKnockbackDamage(successfulSteps)
        traceBearEvent(
            npc,
            encounter,
            profile,
            state,
            "knockback",
            player,
            "direction=$dx,$dz successfulTiles=$successfulSteps destination=$destination damage=$damage",
        )
        player.queueHit(
            npc,
            BEAR_HIT_QUEUE_DELAY,
            EngineHitType.Magic,
            damage,
            modifier = NpcBypassProtectionPrayerPlayerHitModifier,
        )
    }

    private fun performBearRoar(
        npc: Npc,
        encounter: BossEncounter,
        profile: BearProfile,
        state: BearRuntimeState,
        threshold: Int,
    ) {
        state.roaringUntil = deps.mapClock.cycle + BEAR_ROAR_LOCK_TICKS
        traceBearEvent(
            npc,
            encounter,
            profile,
            state,
            "threshold-roar",
            detail =
                "threshold=$threshold animId=${BEAR_ENRAGE_ANIM.asRSCM(RSCMType.SEQ)} " +
                    "spotanimId=${(if (profile.multiTarget) BEAR_ENRAGE_LARGE else BEAR_ENRAGE_SMALL).asRSCM(RSCMType.SPOTANIM)}",
        )
        state.bindGeneration++
        CombatEffects.breakBind(npc)
        state.freezeImmuneUntil = deps.mapClock.cycle + BEAR_ROAR_LOCK_TICKS
        npc.routeDestination.clear()
        npc.movementLocked = true
        npc.ignoreCombatInteractions = true
        npc.anim(BEAR_ENRAGE_ANIM)
        npc.spotanim(if (profile.multiTarget) BEAR_ENRAGE_LARGE else BEAR_ENRAGE_SMALL)
        if (threshold == profile.thresholds.last()) state.lowTrapPhase = true
        deps.worldQueues.add(BEAR_TRAP_SPAWN_DELAY) {
            if (!isCurrentBear(npc, encounter, state)) return@add
            deployBearTraps(npc, encounter, profile, state)
        }
        deps.worldQueues.add(BEAR_ROAR_LOCK_TICKS) {
            if (!isCurrentBear(npc, encounter, state)) return@add
            if (state.pendingRoars.isNotEmpty()) {
                performBearRoar(npc, encounter, profile, state, state.pendingRoars.removeFirst())
                return@add
            }
            state.roaringUntil = Int.MIN_VALUE
            npc.movementLocked = false
            npc.ignoreCombatInteractions = false
            encounter.lastAbilityTick = deps.mapClock.cycle
        }
    }

    private fun deployBearTraps(
        npc: Npc,
        encounter: BossEncounter,
        profile: BearProfile,
        state: BearRuntimeState,
    ) {
        if (!isCurrentBear(npc, encounter, state)) return
        if (state.traps.isNotEmpty()) {
            traceBearEvent(
                npc,
                encounter,
                profile,
                state,
                "traps-removed",
                detail = "reason=redeploy tiles=${state.traps.keys.sortedBy { it.packed }}",
            )
        }
        state.traps.clear()
        val created = mutableListOf<CoordGrid>()
        val candidates =
            buildList {
                    for (radius in BEAR_TRAP_MIN_RADIUS..BEAR_TRAP_MAX_RADIUS) {
                        for (dx in -radius..radius) {
                            for (dz in -radius..radius) {
                                if (
                                    kotlin.math.abs(dx) != radius && kotlin.math.abs(dz) != radius
                                ) {
                                    continue
                                }
                                val tile = npc.coords.translate(dx, dz)
                                if (
                                    isBearTrapTileValid(npc, profile, tile) &&
                                        bearPlayers(npc, profile).none { it.coords == tile }
                                ) {
                                    add(tile)
                                }
                            }
                        }
                    }
                }
                .distinct()
                .toMutableList()
        repeat(profile.trapCount) {
            if (candidates.isEmpty()) return@repeat
            val tile = candidates.removeAt(deps.random.of(candidates.size))
            state.traps[tile] = deps.mapClock.cycle + BEAR_TRAP_LIFETIME_TICKS
            created += tile
            deps.worldRepo.spotanimMap(
                SpotanimType(BEAR_TRAP_SPOTANIM.asRSCM(RSCMType.SPOTANIM)),
                tile,
            )
        }
        if (created.isNotEmpty()) {
            traceBearEvent(
                npc,
                encounter,
                profile,
                state,
                "traps-created",
                detail =
                    "spotanimId=${BEAR_TRAP_SPOTANIM.asRSCM(RSCMType.SPOTANIM)} " +
                        "lifetime=$BEAR_TRAP_LIFETIME_TICKS tiles=$created",
            )
        }
        if (state.traps.isNotEmpty() && !state.trapPulseScheduled) {
            state.trapPulseScheduled = true
            processBearTraps(npc, encounter, profile, state)
        }
    }

    private fun processBearTraps(
        npc: Npc,
        encounter: BossEncounter,
        profile: BearProfile,
        state: BearRuntimeState,
    ) {
        deps.worldQueues.add(1) {
            if (!isCurrentBear(npc, encounter, state)) {
                state.trapPulseScheduled = false
                return@add
            }
            val tick = deps.mapClock.cycle
            val expired = state.traps.filterValues { it <= tick }.keys.toList()
            expired.forEach(state.traps::remove)
            if (expired.isNotEmpty()) {
                traceBearEvent(
                    npc,
                    encounter,
                    profile,
                    state,
                    "traps-removed",
                    detail = "reason=expired tiles=$expired",
                )
            }
            bearPlayers(npc, profile).forEach { player ->
                val triggerTile =
                    when {
                        player.coords in state.traps -> player.coords
                        player.lastProcessedCoord in state.traps -> player.lastProcessedCoord
                        else -> null
                    }
                if (triggerTile != null) {
                    state.traps.remove(triggerTile)
                    traceBearEvent(
                        npc,
                        encounter,
                        profile,
                        state,
                        "trap-triggered",
                        player,
                        "tile=$triggerTile maxHit=$BEAR_TRAP_MAX_HIT bindTicks=$BEAR_TRAP_BIND_TICKS",
                    )
                    if (triggerTile != player.coords) {
                        PathingEntityCommon.teleport(player, deps.collision, triggerTile)
                    }
                    player.queueHit(
                        npc,
                        BEAR_HIT_QUEUE_DELAY,
                        EngineHitType.Typeless,
                        deps.random.of(BEAR_TRAP_MAX_HIT + 1),
                        modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                        impactHandler =
                            HitImpactHandler { CombatEffects.bind(player, BEAR_TRAP_BIND_TICKS) },
                    )
                }
            }
            if (state.traps.isEmpty()) {
                state.trapPulseScheduled = false
            } else {
                processBearTraps(npc, encounter, profile, state)
            }
        }
    }

    private fun prepareBearIncomingHit(npc: Npc) {
        val encounter = deps.encounterRegistry.of(npc)
        val profile = bearProfile(npc) ?: return
        val state = bearState(npc, encounter)
        recoverBearLifecycleIfNeeded(npc, encounter, profile, state)
        if (
            !shouldUnlockBearDamage(
                lifecycle = state.lifecycle,
                active = state.active,
                slotAssigned = npc.isSlotAssigned,
                hitpoints = npc.hitpoints,
            )
        ) {
            return
        }
        val recoveredLock = encounter.invulnerable || npc.ignoreCombatInteractions
        encounter.invulnerable = false
        npc.ignoreCombatInteractions = false
        npc.movementLocked = false
        if (recoveredLock) {
            traceBearEvent(npc, encounter, profile, state, "damage-gate-recovered")
        }
    }

    private fun markBearThresholds(npc: Npc, incomingDamage: Int) {
        val encounter = deps.encounterRegistry.of(npc)
        val profile = bearProfile(npc) ?: return
        val state = bearState(npc, encounter)
        recoverBearLifecycleIfNeeded(npc, encounter, profile, state)
        if (incomingDamage <= 0) return
        if (!state.active) return
        val after = (npc.hitpoints - incomingDamage).coerceAtLeast(0)
        if (after <= 0) return
        bearThresholdsCrossed(npc.hitpoints, after, profile.thresholds, state.firedThresholds)
            .forEach { threshold ->
                state.firedThresholds += threshold
                state.pendingRoars += threshold
            }
        if (state.pendingRoars.isNotEmpty()) {
            traceBearEvent(
                npc,
                encounter,
                profile,
                state,
                "threshold-crossed",
                detail = "queued=${state.pendingRoars.toList()}",
            )
        }
        if (deps.mapClock.cycle >= state.roaringUntil && state.pendingRoars.isNotEmpty()) {
            performBearRoar(npc, encounter, profile, state, state.pendingRoars.removeFirst())
        }
    }

    private fun ScriptContext.registerBearLifecycle() {
        val types =
            BEAR_PROFILES.associateBy { profile ->
                requireNotNull(
                        ServerCacheManager.getNpc(
                            "npc.${profile.internalName}".asRSCM(RSCMType.NPC)
                        )
                    )
                    .id
            }
        val bossIds = types.keys
        onEvent<NpcStateEvents.Create> { if (npc.type.id in bossIds) beginBearSpawn(npc) }
        onEvent<NpcStateEvents.Respawn> { if (npc.type.id in bossIds) beginBearSpawn(npc) }
        onEvent<NpcStateEvents.Delete> { if (npc.type.id in bossIds) CombatEffects.breakBind(npc) }
        types.forEach { (id, _) ->
            val type = requireNotNull(ServerCacheManager.getNpc(id))
            onNpcHit(type) { applyBearFreezeFromHit(npc, hit) }
        }
    }

    private fun beginBearSpawn(npc: Npc) {
        val profile =
            bearProfile(npc)
                ?: run {
                    logger.error {
                        "[BearBoss] refusing to lock unrecognized bear npcId=${npc.type.id} " +
                            "internalName=${npc.type.internalName} tile=${npc.coords}"
                    }
                    return
                }
        val encounter = deps.encounterRegistry.of(npc)
        val state = bearState(npc, encounter)
        state.lifecycle = BearLifecycleState.SPAWNING
        state.active = true
        state.spawnUnlockTick = deps.mapClock.cycle + BEAR_SPAWN_LOCK_TICKS
        state.pendingRoars.clear()
        state.firedThresholds.clear()
        state.traps.clear()
        state.trapPulseScheduled = false
        state.lowTrapPhase = false
        state.firstFreezeAvailable = true
        state.freezeImmuneUntil = Int.MIN_VALUE
        state.roaringUntil = Int.MIN_VALUE
        state.bindGeneration++
        state.attackCounter = 0
        state.magicCountdown = nextBearMagicCountdown()
        CombatEffects.breakBind(npc)
        encounter.invulnerable = true
        encounter.lastAbilityTick = deps.mapClock.cycle
        // The respawn grace blocks damage and movement, but it must not clear Artio's hunt target.
        // `ignoreCombatInteractions` makes NpcModeProcessor erase the interaction permanently when
        // a stale encounter causes the delayed unlock to miss.
        npc.ignoreCombatInteractions = !state.lifecycle.allowsTargetAcquisition
        npc.movementLocked = true
        npc.anim(BEAR_SPAWN_ANIM)
        traceBearEvent(
            npc,
            encounter,
            profile,
            state,
            "spawn",
            detail =
                "animId=${BEAR_SPAWN_ANIM.asRSCM(RSCMType.SEQ)} targetableTick=${deps.mapClock.cycle + BEAR_SPAWN_LOCK_TICKS}",
        )
        deps.worldQueues.add(BEAR_SPAWN_LOCK_TICKS) { completeBearSpawn(npc, encounter) }
    }

    /**
     * Resolves the encounter again when the grace period expires. BossCombat's respawn cleanup and
     * this plugin's respawn hook are separate listeners, so retaining the encounter captured by
     * [beginBearSpawn] can leave the bear locked forever when cleanup replaces that encounter.
     */
    private fun completeBearSpawn(npc: Npc, scheduledEncounter: BossEncounter) {
        if (!npc.isSlotAssigned || npc.hitpoints <= 0) return
        val profile = bearProfile(npc) ?: return
        val encounter = deps.encounterRegistry.of(npc)
        val state = bearState(npc, encounter)
        if (state.lifecycle == BearLifecycleState.DEAD) {
            // Respawn cleanup can mark the newly visible NPC's reused state as dead.
            state.lifecycle = BearLifecycleState.UNINITIALIZED
            state.active = false
        }
        recoverBearLifecycleIfNeeded(npc, encounter, profile, state)
        if (state.lifecycle == BearLifecycleState.SPAWNING) {
            activateBearCombat(npc, encounter, state)
        }
        if (state.lifecycle == BearLifecycleState.ACTIVE && state.active) {
            encounter.invulnerable = false
            npc.ignoreCombatInteractions = false
            npc.movementLocked = false
        }
        if (encounter !== scheduledEncounter) {
            logger.warn {
                "[BearBoss] repaired stale spawn encounter boss=${profile.internalName} " +
                    "npcId=${npc.type.id} tile=${npc.coords}"
            }
        }
    }

    private fun finishBearFight(npc: Npc) {
        val encounter = deps.encounterRegistry.existing(npc) ?: return
        val state = bearState(npc, encounter)
        bearProfile(npc)?.let { profile ->
            traceBearEvent(
                npc,
                encounter,
                profile,
                state,
                "death-cleanup",
                detail = "activeTraps=${state.traps.keys}",
            )
        }
        state.lifecycle = BearLifecycleState.DEAD
        state.active = false
        state.roaringUntil = Int.MIN_VALUE
        state.bindGeneration++
        state.pendingRoars.clear()
        state.traps.clear()
        state.trapPulseScheduled = false
        CombatEffects.breakBind(npc)
        encounter.invulnerable = true
        encounter.runCleanupActions()
        npc.ignoreCombatInteractions = true
        npc.movementLocked = true
    }

    private fun applyBearFreezeFromHit(npc: Npc, hit: org.rsmod.game.hit.Hit) {
        val spell = hit.secondaryType() ?: return
        if (!MagicSpellChecks.isIceSpell(spell) && !MagicSpellChecks.isBindSpell(spell)) return
        if (!hit.isFromPlayer || npc.isBound) return
        val source = runCatching { hit.resolvePlayerSource(deps.playerList) }.getOrNull() ?: return
        val encounter = deps.encounterRegistry.existing(npc) ?: return
        val profile = bearProfile(npc) ?: return
        val state = bearState(npc, encounter)
        if (
            !state.active ||
                !isBearArenaTile(npc, profile, source.coords) ||
                deps.mapClock.cycle < state.freezeImmuneUntil
        ) {
            return
        }
        val guaranteed = state.firstFreezeAvailable && wornBonuses.offensiveMagicBonus(source) > 0
        if (!guaranteed && hit.damage <= 0) return
        val duration = bearFreezeTicks(spell.name)
        if (duration <= 0 || !CombatEffects.bind(npc, duration)) return
        state.firstFreezeAvailable = false
        val generation = ++state.bindGeneration
        deps.worldQueues.add(duration) {
            if (!isCurrentBear(npc, encounter, state) || state.bindGeneration != generation) {
                return@add
            }
            state.freezeImmuneUntil = deps.mapClock.cycle + BEAR_NATURAL_FREEZE_IMMUNITY_TICKS
        }
    }

    private fun traceBearEvent(
        npc: Npc,
        encounter: BossEncounter,
        profile: BearProfile,
        state: BearRuntimeState,
        event: String,
        target: Player? = null,
        detail: String = "",
    ) {
        if (!traceBearBosses) return
        val distance = target?.let { npc.coords.chebyshevDistance(it.coords) }
        val meleeReach = target?.let { npc.isWithinDistance(it, 1) }
        val pathReach =
            target?.let {
                runCatching {
                        rayCastValidator.hasLineOfWalk(
                            npc.coords,
                            it.coords,
                            srcWidth = npc.size,
                            srcLength = npc.size,
                        )
                    }
                    .getOrNull()
            }
        logger.info {
            "[BearBoss] tick=${deps.mapClock.cycle} event=$event boss=${profile.internalName} " +
                "npcId=${npc.type.id} state=active:${state.active},bound:${npc.isBound}," +
                "roaring:${deps.mapClock.cycle < state.roaringUntil},lowTraps:${state.lowTrapPhase} " +
                "bossTile=${npc.coords} hp=${npc.hitpoints} target=${target?.uid ?: "none"} " +
                "targetTile=${target?.coords ?: "none"} distance=${distance ?: "none"} " +
                "meleeReach=${meleeReach ?: "none"} pathReach=${pathReach ?: "none"} " +
                "attackCounter=${state.attackCounter} specialCounter=${state.magicCountdown} " +
                "thresholds=${state.firedThresholds.sorted()} nextEligibleTick=" +
                "${encounter.lastAbilityTick + BEAR_ATTACK_RATE_TICKS} $detail"
        }
    }

    private fun recoverBearLifecycleIfNeeded(
        npc: Npc,
        encounter: BossEncounter,
        profile: BearProfile,
        state: BearRuntimeState,
    ) {
        if (
            !shouldRecoverBearLifecycle(
                lifecycle = state.lifecycle,
                active = state.active,
                slotAssigned = npc.isSlotAssigned,
                hitpoints = npc.hitpoints,
                currentTick = deps.mapClock.cycle,
                spawnUnlockTick = state.spawnUnlockTick,
            )
        ) {
            return
        }
        state.active = true
        state.pendingRoars.clear()
        state.firedThresholds.clear()
        state.traps.clear()
        state.trapPulseScheduled = false
        state.lowTrapPhase = false
        state.firstFreezeAvailable = true
        state.freezeImmuneUntil = Int.MIN_VALUE
        state.roaringUntil = Int.MIN_VALUE
        state.bindGeneration++
        state.attackCounter = 0
        state.magicCountdown = nextBearMagicCountdown()
        CombatEffects.breakBind(npc)
        encounter.lethalHandled = false
        encounter.transitionTo("combat", deps.mapClock.cycle)
        activateBearCombat(npc, encounter, state)
        traceBearEvent(npc, encounter, profile, state, "spawn-recovered")
    }

    private fun activateBearCombat(npc: Npc, encounter: BossEncounter, state: BearRuntimeState) {
        state.lifecycle = BearLifecycleState.ACTIVE
        state.spawnUnlockTick = Int.MIN_VALUE
        encounter.invulnerable = false
        npc.ignoreCombatInteractions = false
        npc.movementLocked = false
        encounter.lastAbilityTick = deps.mapClock.cycle
    }

    private fun bearState(npc: Npc, encounter: BossEncounter): BearRuntimeState =
        encounter.state(BEAR_STATE_KEY) {
            BearRuntimeState().also { state ->
                encounter.onCleanup {
                    state.lifecycle = BearLifecycleState.DEAD
                    state.active = false
                    state.pendingRoars.clear()
                    state.traps.clear()
                    state.trapPulseScheduled = false
                    CombatEffects.breakBind(npc)
                }
            }
        }

    private fun bearProfile(npc: Npc): BearProfile? =
        BEAR_PROFILES.firstOrNull {
            it.internalName == normalizeNpcInternalName(npc.type.internalName)
        }

    private fun bearPlayers(npc: Npc, profile: BearProfile): List<Player> =
        eligiblePlayers(npc).filter { it.isValidTarget() && isBearArenaTile(npc, profile, it.coords) }

    private fun isCurrentBear(
        npc: Npc,
        encounter: BossEncounter,
        state: BearRuntimeState,
    ): Boolean =
        state.active && deps.encounterRegistry.isCurrent(npc, encounter) && npc.isSlotAssigned

    private fun isCurrentBearAttack(
        npc: Npc,
        encounter: BossEncounter,
        state: BearRuntimeState,
        player: Player,
        profile: BearProfile,
    ): Boolean =
        isCurrentBear(npc, encounter, state) &&
            player.isSlotAssigned &&
            player.hitpoints > 0 &&
            isBearArenaTile(npc, profile, player.coords)

    private fun isBearArenaTile(npc: Npc, profile: BearProfile, tile: CoordGrid): Boolean =
        isMayhemEventArenaTile(npc, tile) || profile.room.contains(tile)

    private fun isBearTrapTileValid(npc: Npc, profile: BearProfile, tile: CoordGrid): Boolean {
        val localX = tile.x and 63
        val localZ = tile.z and 63
        return isBearArenaTile(npc, profile, tile) &&
            localX in 2..61 &&
            localZ in 2..61 &&
            !occupiesBearTile(npc, tile) &&
            deps.collision.isZoneValid(tile) &&
            !deps.collision.isWalkBlocked(tile)
    }

    private fun occupiesBearTile(npc: Npc, tile: CoordGrid): Boolean =
        tile.level == npc.coords.level &&
            tile.x in npc.coords.x until npc.coords.x + npc.size &&
            tile.z in npc.coords.z until npc.coords.z + npc.size

    private fun nextBearMagicCountdown(): Int =
        BEAR_MAGIC_MIN_NORMAL_ATTACKS +
            deps.random.of(BEAR_MAGIC_MAX_NORMAL_ATTACKS - BEAR_MAGIC_MIN_NORMAL_ATTACKS + 1)

    private fun registerSpiderMechanics() {
        deps.extensionRegistry.register(SPIDER_ATTACK) { _, npc, target, params ->
            val profile = SpiderAttackProfile.decode(params as? String) ?: return@register
            performSpiderAttack(npc, target, profile)
        }
        deps.extensionRegistry.register(SPIDERLING_PRAYER_DRAIN) { _, _, target, _ ->
            if (target.isSlotAssigned && target.hitpoints > 0) {
                target.statSub("stat.prayer", constant = 1, percent = 0)
            }
        }
    }

    private fun performSpiderAttack(npc: Npc, primary: Player, profile: SpiderAttackProfile) {
        val encounter = deps.encounterRegistry.of(npc)
        val distantStyle = spiderDistantStyle(encounter.lastRotationIndex)
        val targets =
            if (profile.multiTarget) spiderPlayers(npc)
            else listOf(primary).filter { it.isValidTarget() && isSpiderArenaTile(npc, it.coords) }
        if (targets.isEmpty()) return

        val primaryStyle = if (npc.isWithinDistance(primary, 1)) HitType.Melee else distantStyle
        npc.anim(spiderAttackAnim(primaryStyle))
        targets.forEach { player ->
            val style = if (npc.isWithinDistance(player, 1)) HitType.Melee else distantStyle
            queueSpiderAttack(npc, player, style, profile, encounter)
        }
    }

    private fun queueSpiderAttack(
        npc: Npc,
        player: Player,
        style: HitType,
        profile: SpiderAttackProfile,
        encounter: BossEncounter,
    ) {
        val accurate =
            when (style) {
                HitType.Melee ->
                    deps.accuracy.rollMeleeAccuracy(
                        npc = npc,
                        target = player,
                        attackType = MeleeAttackType.Stab,
                        random = deps.random,
                    )
                HitType.Ranged -> deps.accuracy.rollRangedAccuracy(npc, player, deps.random)
                HitType.Magic -> deps.accuracy.rollMagicAccuracy(npc, player, deps.random)
                else -> true
            }
        val maximum =
            when (style) {
                HitType.Melee -> profile.meleeMax
                HitType.Ranged -> profile.rangedMax
                HitType.Magic -> profile.magicMax
                else -> 0
            }
        val spiderlings = aliveSpiderlings(encounter)
        val empoweredMaximum = spiderlingEmpoweredMaximum(spiderlings)
        val protected = player.protectingAgainst(style)
        val damage =
            if (!accurate) {
                0
            } else {
                val base = deps.random.of(maximum + 1)
                val empowered =
                    if (empoweredMaximum == 0) 0 else deps.random.of(empoweredMaximum + 1)
                if (protected) empowered else base + empowered
            }

        if (style == HitType.Melee) {
            player.queueHit(
                npc,
                1,
                EngineHitType.Melee,
                damage,
                modifier = NpcBypassProtectionPrayerPlayerHitModifier,
            )
            return
        }

        val projectileSpot =
            if (style == HitType.Ranged) SPIDER_RANGED_PROJECTILE else SPIDER_MAGIC_PROJECTILE
        val projectileType =
            if (style == HitType.Magic) SPIDER_MAGIC_PROJECTILE_TYPE
            else SPIDER_RANGED_PROJECTILE_TYPE
        val impact = if (style == HitType.Ranged) SPIDER_RANGED_IMPACT else SPIDER_MAGIC_IMPACT
        val projectile =
            deps.worldRepo.projAnimSourced(
                npc,
                player,
                SpotanimType(projectileSpot.asRSCM(RSCMType.SPOTANIM)),
                projectileType,
            )
        val expectedImpact = deps.mapClock.cycle + projectile.serverCycles
        encounter.recordScheduledImpact(style, expectedImpact)
        player.queueHit(
            npc,
            projectile.serverCycles,
            if (style == HitType.Ranged) EngineHitType.Ranged else EngineHitType.Magic,
            damage,
            modifier = NpcBypassProtectionPrayerPlayerHitModifier,
            impactHandler =
                HitImpactHandler {
                    if (!deps.encounterRegistry.isCurrent(npc, encounter)) return@HitImpactHandler
                    if (!player.isSlotAssigned || player.hitpoints <= 0) return@HitImpactHandler
                    encounter.recordActualImpact(expectedImpact, deps.mapClock.cycle)
                    player.spotanim(impact)
                },
        )
    }

    private suspend fun afterSpiderAttack(npc: Npc, target: Player, encounter: BossEncounter) {
        val index = encounter.lastRotationIndex
        if (index !in 0 until SPIDER_ROTATION_LENGTH) return
        val playerCount = spiderPlayers(npc).size.coerceAtLeast(1)
        val state = spiderState(encounter)
        val crowded = npc.type.internalName == VENENATIS_INTERNAL && playerCount > CROWD_THRESHOLD
        val special = spiderSpecialAt(index, crowded, state.lastSpecial)
        if (special != null) state.lastSpecial = special
        when (special) {
            SpiderSpecial.Spiderlings -> summonSpiderlings(npc, target, encounter, playerCount)
            SpiderSpecial.Web -> launchWeb(npc, target, encounter)
            null -> Unit
        }
        if (spiderShouldMove(index, crowded)) moveSpider(npc, target, encounter)
        if (traceSpiderBosses) {
            logger.info {
                "${npc.type.internalName} opportunity=$index style=${spiderDistantStyle(index)} " +
                    "special=${special ?: "none"} move=${spiderShouldMove(index, crowded)} " +
                    "players=$playerCount adds=${aliveSpiderlings(encounter)}"
            }
        }
    }

    private fun summonSpiderlings(
        npc: Npc,
        target: Player,
        encounter: BossEncounter,
        playerCount: Int,
    ) {
        val spindel = npc.type.internalName == SPINDEL_INTERNAL
        val npcName = if (spindel) SPINDEL_SPIDERLING else VENENATIS_SPIDERLING
        val cap = if (spindel) SPINDEL_SPIDERLING_CAP else VENENATIS_SPIDERLING_CAP
        val alive = aliveSpiderlings(encounter)
        val requested =
            if (spindel) SPINDEL_SPIDERLINGS_PER_SPECIAL else venenatisSpiderlingCount(playerCount)
        val victims = spiderPlayers(npc).ifEmpty { listOf(target).filter { it.isValidTarget() } }
        if (victims.isEmpty()) return
        val spawned = spawnOwned(npc, npcName, (cap - alive).coerceIn(0, requested))
        spawned.forEachIndexed { index, spiderling ->
            val victim = victims.getOrNull(index % victims.size) ?: return@forEachIndexed
            spiderling.opPlayer2(victim, aiPlayerInteractions)
        }
    }

    private fun launchWeb(npc: Npc, target: Player, encounter: BossEncounter) {
        val center = target.coords
        npc.anim(SPIDER_MAGIC_ANIM)
        val projectile =
            deps.worldRepo.projAnimSourced(
                npc,
                center,
                SpotanimType(SPIDER_WEB_PROJECTILE.asRSCM(RSCMType.SPOTANIM)),
                SPIDER_WEB_PROJECTILE_TYPE,
            )
        deps.worldQueues.add(projectile.serverCycles) {
            if (!deps.encounterRegistry.isCurrent(npc, encounter)) return@add
            deps.worldRepo.spotanimMap(
                SpotanimType(SPIDER_WEB_IMPACT.asRSCM(RSCMType.SPOTANIM)),
                center,
            )
            createWebField(npc, encounter, center)
        }
    }

    private fun createWebField(npc: Npc, encounter: BossEncounter, center: CoordGrid) {
        val tiles = buildSet {
            for (dx in -WEB_X_RADIUS..WEB_X_RADIUS) {
                for (dz in -WEB_Z_RADIUS..WEB_Z_RADIUS) {
                    val tile = center.translate(dx, dz)
                    if (
                        isSpiderArenaTile(npc, tile) &&
                            deps.collision.isZoneValid(tile) &&
                            !deps.collision.isWalkBlocked(tile)
                    )
                        add(tile)
                }
            }
        }
        if (tiles.isEmpty()) return
        val state = spiderState(encounter)
        val id = state.nextWebId++
        val web =
            ActiveSpiderWeb(
                id = id,
                tiles = tiles,
                expiresAt = deps.mapClock.cycle + WEB_DURATION_TICKS,
            )
        state.activeWebs[id] = web
        tiles.forEach { tile ->
            val references = state.tileReferences.merge(tile, 1, Int::plus) ?: 1
            if (references == 1) {
                state.webLocs[tile] =
                    locRepo.add(
                        tile,
                        SPIDER_WEB_FLOOR,
                        Int.MAX_VALUE,
                        LocAngle.North,
                        LocShape.GroundDecor,
                    )
            }
        }
        processWebField(npc, encounter, state, id)
    }

    private fun processWebField(
        npc: Npc,
        encounter: BossEncounter,
        state: SpiderRuntimeState,
        webId: Int,
    ) {
        deps.worldQueues.add(WEB_PROC_INTERVAL) {
            if (!deps.encounterRegistry.isCurrent(npc, encounter)) return@add
            val web = state.activeWebs[webId] ?: return@add
            if (deps.mapClock.cycle >= web.expiresAt) {
                removeWeb(state, webId)
                return@add
            }
            spiderPlayers(npc)
                .filter { it.coords in web.tiles }
                .forEach { player ->
                    if (
                        player.feet.isType(ARANEA_BOOTS) && web.bootsProtectionUsed.add(player.uid)
                    ) {
                        return@forEach
                    }
                    player.queueHit(
                        npc,
                        1,
                        EngineHitType.Typeless,
                        WEB_DAMAGE,
                        modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                    )
                    player.statSub("stat.prayer", constant = WEB_PRAYER_DRAIN, percent = 0)
                    player.runEnergy = (player.runEnergy - WEB_RUN_DRAIN).coerceAtLeast(0)
                }
            processWebField(npc, encounter, state, webId)
        }
    }

    private fun spiderState(encounter: BossEncounter): SpiderRuntimeState =
        encounter.state(SPIDER_STATE_KEY) {
            SpiderRuntimeState().also { state ->
                encounter.onCleanup {
                    state.webLocs.values.toList().forEach { locRepo.del(it, Int.MAX_VALUE) }
                    state.activeWebs.clear()
                    state.tileReferences.clear()
                    state.webLocs.clear()
                }
            }
        }

    private fun removeWeb(state: SpiderRuntimeState, webId: Int) {
        val web = state.activeWebs.remove(webId) ?: return
        web.tiles.forEach { tile ->
            val remaining = (state.tileReferences[tile] ?: 1) - 1
            if (remaining <= 0) {
                state.tileReferences.remove(tile)
                state.webLocs.remove(tile)?.let { locRepo.del(it, Int.MAX_VALUE) }
            } else {
                state.tileReferences[tile] = remaining
            }
        }
    }

    private fun moveSpider(npc: Npc, preferred: Player, encounter: BossEncounter) {
        val candidates =
            spiderMovementTiles(npc)
                .filter { it != npc.coords && canStand(it, npc.size) }
                .sortedByDescending { it.chebyshevDistance(preferred.coords) }
        if (candidates.isEmpty()) return
        val bestDistance = candidates.first().chebyshevDistance(preferred.coords)
        val best =
            candidates.takeWhile { it.chebyshevDistance(preferred.coords) >= bestDistance - 1 }
        val destination = best[deps.random.of(best.size)]
        npc.resetFaceEntity()
        npc.ignoreCombatInteractions = true
        npc.walkTo(routeFactory, destination, passThroughEntities = true) {
            if (!deps.encounterRegistry.isCurrent(npc, encounter)) return@walkTo
            npc.ignoreCombatInteractions = false
            val target =
                preferred.takeIf { it.isValidTarget() && isSpiderArenaTile(npc, it.coords) }
                    ?: spiderPlayers(npc).firstOrNull()
                    ?: return@walkTo
            npc.apRangeOverride = SPIDER_ATTACK_RANGE
            npc.apRequiresLineOfSight = true
            npc.apPlayer2(target, aiPlayerInteractions)
        }
    }

    private fun ScriptContext.registerSpiderLifecycle() {
        val bossIds =
            SPIDER_BOSS_INTERNALS.mapTo(hashSetOf()) { name ->
                requireNotNull(ServerCacheManager.getNpc("npc.$name".asRSCM(RSCMType.NPC))).id
            }
        onEvent<NpcStateEvents.Create> { if (npc.type.id in bossIds) beginSpiderSpawn(npc) }
        onEvent<NpcStateEvents.Respawn> { if (npc.type.id in bossIds) beginSpiderSpawn(npc) }
    }

    private fun beginSpiderSpawn(npc: Npc) {
        val encounter = deps.encounterRegistry.of(npc)
        encounter.invulnerable = true
        encounter.lastAbilityTick = deps.mapClock.cycle
        npc.ignoreCombatInteractions = true
        npc.movementLocked = true
        npc.anim(SPIDER_SPAWN_ANIM)
        deps.worldQueues.add(SPIDER_SPAWN_LOCK_TICKS) {
            if (!deps.encounterRegistry.isCurrent(npc, encounter)) return@add
            encounter.invulnerable = false
            npc.ignoreCombatInteractions = false
            npc.movementLocked = false
            encounter.lastAbilityTick = deps.mapClock.cycle
        }
    }

    private fun finishSpiderFight(npc: Npc) {
        val encounter = deps.encounterRegistry.existing(npc) ?: return
        encounter.invulnerable = true
        encounter.drainOwnedNpcs().forEach { owned ->
            if (owned.isSlotAssigned) deps.npcRepo.del(owned, Int.MAX_VALUE)
        }
        encounter.runCleanupActions()
        npc.ignoreCombatInteractions = true
        npc.movementLocked = true
    }

    private fun aliveSpiderlings(encounter: BossEncounter): Int =
        encounter.owned().count {
            it.isSlotAssigned && it.hitpoints > 0 && it.type.internalName in SPIDERLING_INTERNALS
        }

    private fun Player.protectingAgainst(style: HitType): Boolean =
        when (style) {
            HitType.Melee -> vars["varbit.prayer_protectfrommelee"] == 1
            HitType.Ranged -> vars["varbit.prayer_protectfrommissiles"] == 1
            HitType.Magic -> vars["varbit.prayer_protectfrommagic"] == 1
            else -> false
        }

    private fun spiderPlayers(npc: Npc): List<Player> =
        eligiblePlayers(npc).filter {
            it.isValidTarget() &&
                it.coords.level == npc.coords.level &&
                isSpiderArenaTile(npc, it.coords)
        }

    private fun isSpiderArenaTile(npc: Npc, tile: CoordGrid): Boolean {
        val bounds =
            if (npc.type.internalName == VENENATIS_INTERNAL) VENENATIS_ARENA else SPINDEL_ARENA
        return isMayhemEventArenaTile(npc, tile) ||
            tile.level == bounds.level &&
            tile.x in bounds.minX..bounds.maxX &&
            tile.z in bounds.minZ..bounds.maxZ
    }

    private fun canStand(origin: CoordGrid, size: Int): Boolean =
        (0 until size).all { dx ->
            (0 until size).all { dz ->
                val tile = origin.translate(dx, dz)
                deps.collision.isZoneValid(tile) && !deps.collision.isWalkBlocked(tile)
            }
        }

    private fun spiderMovementTiles(npc: Npc): List<CoordGrid> =
        if (npc.type.internalName == VENENATIS_INTERNAL) VENENATIS_MOVEMENT_TILES
        else SPINDEL_MOVEMENT_TILES

    private fun registerUndeadMechanics() {
        deps.extensionRegistry.register(UNDEAD_LIGHTNING) { access, npc, target, params ->
            val maximum = (params as? Int) ?: return@register
            performUndeadLightning(access, npc, target, maximum)
        }
        deps.extensionRegistry.register(UNDEAD_BASH) { access, npc, target, params ->
            val maximum = (params as? Int) ?: return@register
            val profile = undeadProfile(npc) ?: return@register
            if (!npc.isWithinDistance(target, 1)) {
                performUndeadLightning(access, npc, target, profile.lightningMax)
                return@register
            }
            val encounter = deps.encounterRegistry.of(npc)
            val state = undeadState(npc, encounter)
            recoverUndeadLifecycleIfNeeded(npc, encounter, state, profile)
            if (!state.lifecycle.allowsAttacks) return@register
            val generation = state.generation
            access.anim(if (npc.isEnragedUndead()) VETION_MELEE_ANIM_2 else VETION_MELEE_ANIM_1)
            val warning = SpotanimType(VETION_MELEE_IMPACT.asRSCM(RSCMType.SPOTANIM))
            val tiles = undeadBashTiles(npc.coords, npc.visType.size)
            tiles.forEach { deps.worldRepo.spotanimMap(warning, it) }
            deps.worldQueues.add(BASH_WINDUP_TICKS) {
                if (!deps.encounterRegistry.isCurrent(npc, encounter)) return@add
                if (state.generation != generation || !state.lifecycle.allowsAttacks) return@add
                undeadPlayers(npc, profile, target)
                    .filter { it.isSlotAssigned && it.hitpoints > 0 && it.coords in tiles }
                    .forEach { player ->
                        player.queueHit(
                            npc,
                            1,
                            EngineHitType.Typeless,
                            deps.random.of(maximum + 1),
                            modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                            impactHandler =
                                HitImpactHandler {
                                    if (
                                        deps.encounterRegistry.isCurrent(npc, encounter) &&
                                            player.isSlotAssigned
                                    ) {
                                        player.actionDelay =
                                            maxOf(
                                                player.actionDelay,
                                                player.currentMapClock + BASH_ATTACK_LOCK_TICKS,
                                            )
                                    }
                                },
                        )
                    }
            }
        }
    }

    private fun performUndeadLightning(
        access: StandardNpcAccess,
        npc: Npc,
        target: Player,
        maximum: Int,
    ) {
        val profile = undeadProfile(npc) ?: return
        val enraged = npc.isEnragedUndead()
        access.anim(if (enraged) VETION_MAGIC_ANIM_2 else VETION_MAGIC_ANIM_1)
        val encounter = deps.encounterRegistry.of(npc)
        val state = undeadState(npc, encounter)
        recoverUndeadLifecycleIfNeeded(npc, encounter, state, profile)
        if (!state.lifecycle.allowsAttacks) return
        val generation = state.generation
        val players = undeadPlayers(npc, profile, target)
        val desiredCount = undeadLightningStrikeCount(players.size)
        val tiles =
            players.take(MAX_TARGETED_LIGHTNING).mapNotNullTo(linkedSetOf()) { player ->
                randomTargetedLightningTile(npc, player.coords, profile)
            }
        var attempts = 0
        while (tiles.size < desiredCount && attempts++ < LIGHTNING_TILE_ATTEMPTS) {
            randomArenaLightningTile(npc, profile)?.let(tiles::add)
        }
        val warning =
            SpotanimType(
                (if (enraged) VETION_MAGIC_CAST_2 else VETION_MAGIC_CAST_1).asRSCM(
                    RSCMType.SPOTANIM
                )
            )
        val impact =
            SpotanimType(
                (if (enraged) VETION_MAGIC_IMPACT_2 else VETION_MAGIC_IMPACT_1).asRSCM(
                    RSCMType.SPOTANIM
                )
            )
        tiles.forEach { deps.worldRepo.spotanimMap(warning, it) }
        deps.worldQueues.add(LIGHTNING_WINDUP_TICKS) {
            if (!deps.encounterRegistry.isCurrent(npc, encounter)) return@add
            if (state.generation != generation || !state.lifecycle.allowsAttacks) return@add
            tiles.forEach { deps.worldRepo.spotanimMap(impact, it) }
            applyUndeadLightningDamage(npc, profile, tiles, maximum, target)
        }
    }

    private fun registerHoundMechanics(context: ScriptContext) {
        with(context) {
            HOUND_COMBAT_PROFILES.forEach { profile ->
                val type =
                    requireNotNull(
                        ServerCacheManager.getNpc(profile.internalName.asRSCM(RSCMType.NPC))
                    )
                onAiOpPlayer2(type) { attackUndeadHound(it.target, profile) }
                onAiApPlayer2(type) { attackUndeadHound(it.target, profile) }
                onNpcHit(type) { if (npc.hitpoints > 0) npc.anim(profile.defendAnimation) }
            }
        }
    }

    private fun StandardNpcAccess.attackUndeadHound(target: Player, profile: HoundCombatProfile) {
        if (!target.isValidTarget()) {
            resetMode()
            return
        }
        nvpCombat.attack(
            this,
            target,
            CombatAttack.NpcMelee(MeleeAttackType.Crush),
            NpcAttackOverride(
                attackAnimation = profile.attackAnimation,
                maxHit = profile.maxHit,
                attackRate = HOUND_ATTACK_RATE,
            ),
        )
    }

    private fun modifyUndeadHit(
        npc: Npc,
        hit: org.rsmod.game.hit.HitBuilder,
        encounter: BossEncounter,
        state: UndeadRuntimeState,
        profile: UndeadBossProfile,
        secondForm: dev.openrune.types.NpcServerType,
    ) {
        if (hit.damage <= 0) return
        when (state.lifecycle) {
            UndeadLifecycleState.FORM_ONE_ACTIVE -> {
                val threshold = undeadHoundThreshold(npc.baseHitpointsLvl)
                if (!state.normalHoundsTriggered && npc.hitpoints - hit.damage <= threshold) {
                    hit.damage = undeadDamageToThreshold(npc.hitpoints, hit.damage, threshold)
                    state.normalHoundsTriggered = true
                    beginUndeadHoundWave(npc, encounter, state, profile, enraged = false)
                    return
                }
                if (npc.hitpoints - hit.damage <= 0) {
                    hit.damage = (npc.hitpoints - 1).coerceAtLeast(0)
                    beginUndeadTransform(npc, encounter, state, profile, secondForm)
                }
            }
            UndeadLifecycleState.FORM_TWO_ACTIVE -> {
                val threshold = undeadHoundThreshold(npc.baseHitpointsLvl)
                if (!state.enragedHoundsTriggered && npc.hitpoints - hit.damage <= threshold) {
                    hit.damage = undeadDamageToThreshold(npc.hitpoints, hit.damage, threshold)
                    state.enragedHoundsTriggered = true
                    beginUndeadHoundWave(npc, encounter, state, profile, enraged = true)
                }
            }
            UndeadLifecycleState.FORM_ONE_HOUNDS,
            UndeadLifecycleState.FORM_TWO_HOUNDS,
            UndeadLifecycleState.SPAWNING,
            UndeadLifecycleState.TRANSFORMING,
            UndeadLifecycleState.FINAL_DEATH,
            UndeadLifecycleState.RESPAWN_PENDING,
            UndeadLifecycleState.RESETTING,
            UndeadLifecycleState.DESPAWNED -> hit.damage = 0
        }
    }

    private fun beginUndeadHoundWave(
        npc: Npc,
        encounter: BossEncounter,
        state: UndeadRuntimeState,
        profile: UndeadBossProfile,
        enraged: Boolean,
    ) {
        if (!state.active || state.activeHounds.any { it.isSlotAssigned && it.hitpoints > 0 })
            return
        state.lifecycle =
            if (enraged) UndeadLifecycleState.FORM_TWO_HOUNDS
            else UndeadLifecycleState.FORM_ONE_HOUNDS
        encounter.invulnerable = true
        npc.scriptedDamageGate = true
        npc.anim(if (enraged) VETION_SHIELD_ANIM_2 else VETION_SHIELD_ANIM_1)
        npc.say(if (enraged) "Bahh! Go, Dogs!!" else "Kill, my pets!")
        val houndName = if (enraged) profile.enragedHound else profile.normalHound
        val hounds = spawnUndeadHounds(npc, profile, houndName, HOUNDS_PER_WAVE)
        state.activeHounds = hounds
        traceUndeadEvent(npc, encounter, state, profile, "hounds-spawned", "count=${hounds.size}")
        if (hounds.isEmpty()) {
            state.lifecycle =
                if (enraged) UndeadLifecycleState.FORM_TWO_ACTIVE
                else UndeadLifecycleState.FORM_ONE_ACTIVE
            encounter.invulnerable = false
            npc.scriptedDamageGate = false
            return
        }
        monitorUndeadHounds(npc, encounter, state, profile, hounds, enraged)
    }

    private fun monitorUndeadHounds(
        npc: Npc,
        encounter: BossEncounter,
        state: UndeadRuntimeState,
        profile: UndeadBossProfile,
        wave: List<Npc>,
        enraged: Boolean,
    ) {
        deps.worldQueues.add(1) {
            if (!isCurrentUndead(npc, encounter, state) || state.activeHounds !== wave) return@add
            val alive = wave.filter { it.isSlotAssigned && it.hitpoints > 0 }
            if (alive.isEmpty()) {
                state.activeHounds = emptyList()
                state.lifecycle =
                    if (npc.isEnragedUndead()) UndeadLifecycleState.FORM_TWO_ACTIVE
                    else UndeadLifecycleState.FORM_ONE_ACTIVE
                encounter.invulnerable = false
                npc.scriptedDamageGate = false
                traceUndeadEvent(npc, encounter, state, profile, "hounds-cleared")
                return@add
            }
            val targets = undeadPlayers(npc, profile)
            if (targets.isNotEmpty() && deps.mapClock.cycle % HOUND_RETARGET_TICKS == 0) {
                alive.forEachIndexed { index, hound ->
                    hound.opPlayer2(targets[index % targets.size], aiPlayerInteractions)
                }
            }
            monitorUndeadHounds(npc, encounter, state, profile, wave, enraged)
        }
    }

    private fun spawnUndeadHounds(
        owner: Npc,
        profile: UndeadBossProfile,
        npcName: String,
        count: Int,
    ): List<Npc> {
        val type = ServerCacheManager.getNpc(npcName.asRSCM(RSCMType.NPC)) ?: return emptyList()
        val encounter = deps.encounterRegistry.of(owner)
        val tiles = undeadHoundSpawnTiles(owner, profile, type.size).toMutableList()
        val targets = undeadPlayers(owner, profile)
        return List(count.coerceAtMost(tiles.size)) { index ->
            Npc(type, tiles.removeAt(0)).also { hound ->
                hound.mode = type.defaultMode
                hound.spawnOwner = owner.spawnOwner
                hound.suppressRewards = owner.suppressRewards
                deps.npcRepo.add(hound, Int.MAX_VALUE)
                hound.respawns = false
                encounter.own(hound)
                targets.getOrNull(index % targets.size.coerceAtLeast(1))?.let { target ->
                    hound.opPlayer2(target, aiPlayerInteractions)
                }
            }
        }
    }

    private fun undeadHoundSpawnTiles(
        owner: Npc,
        profile: UndeadBossProfile,
        houndSize: Int,
    ): List<CoordGrid> {
        val bossSize = owner.size
        val aligned = (bossSize - houndSize).coerceAtLeast(0) / 2
        val west = owner.coords.translate(-houndSize, aligned)
        val east = owner.coords.translate(bossSize, aligned)
        val south = owner.coords.translate(aligned, -houndSize)
        val north = owner.coords.translate(aligned, bossSize)
        val preferred =
            listOf(
                west,
                east,
                south,
                north,
                owner.coords.translate(-houndSize, -houndSize),
                owner.coords.translate(bossSize, bossSize),
                owner.coords.translate(-houndSize, bossSize),
                owner.coords.translate(bossSize, -houndSize),
            )
        val fallback = buildList {
            for (radius in 2..8) {
                for (dx in -radius..radius) {
                    for (dz in -radius..radius) {
                        if (kotlin.math.abs(dx) == radius || kotlin.math.abs(dz) == radius) {
                            add(owner.coords.translate(dx, dz))
                        }
                    }
                }
            }
        }
        return (preferred + fallback).distinct().filter { origin ->
            val tiles =
                (0 until houndSize).flatMap { dx ->
                    (0 until houndSize).map { dz -> origin.translate(dx, dz) }
                }
            tiles.all { isUndeadHazardTile(owner, profile, it) } &&
                canStand(origin, houndSize) &&
                !footprintsOverlap(owner.coords, bossSize, origin, houndSize)
        }
    }

    private fun ScriptContext.registerUndeadLifecycle() {
        val types =
            UNDEAD_BOSS_PROFILES.flatMap { listOf(it.firstForm, it.secondForm) }
                .associate { name ->
                    val type = requireNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)))
                    type.id to type
                }
        val ids = types.keys
        onEvent<NpcStateEvents.Create> {
            if (npc.type.id in ids) undeadProfile(npc)?.let { beginUndeadSpawn(npc, it) }
        }
        onEvent<NpcStateEvents.Respawn> {
            if (npc.type.id in ids) undeadProfile(npc)?.let { beginUndeadSpawn(npc, it) }
        }
        onEvent<NpcStateEvents.Delete> {
            if (npc.type.id in ids) {
                deps.encounterRegistry.existing(npc)?.let { encounter ->
                    undeadState(npc, encounter).apply {
                        active = false
                        lifecycle = UndeadLifecycleState.DESPAWNED
                        generation++
                        activeHounds = emptyList()
                    }
                }
            }
        }
        types.values.forEach { type ->
            onNpcHit(type) {
                val encounter = deps.encounterRegistry.existing(npc) ?: return@onNpcHit
                val state = undeadState(npc, encounter)
                if (npc.hitpoints <= 0 || !state.lifecycle.allowsDefendAnimation) return@onNpcHit
                npc.anim(
                    when (state.lifecycle) {
                        UndeadLifecycleState.FORM_ONE_HOUNDS -> VETION_SHIELD_ANIM_1
                        UndeadLifecycleState.FORM_TWO_HOUNDS -> VETION_SHIELD_ANIM_2
                        else ->
                            if (npc.isEnragedUndead()) VETION_PARRY_ANIM_2 else VETION_PARRY_ANIM_1
                    }
                )
            }
        }
    }

    private fun beginUndeadSpawn(npc: Npc, profile: UndeadBossProfile) {
        val encounter = deps.encounterRegistry.of(npc)
        val state = undeadState(npc, encounter)
        state.lifecycle = UndeadLifecycleState.RESETTING
        state.active = true
        state.spawnUnlockTick = deps.mapClock.cycle + UNDEAD_SPAWN_LOCK_TICKS
        state.generation++
        state.normalHoundsTriggered = false
        state.enragedHoundsTriggered = false
        state.activeHounds = emptyList()
        npc.resetTransmog()
        npc.copyStats(npc.type)
        encounter.lethalHandled = false
        encounter.invulnerable = true
        encounter.transitionTo("normal", deps.mapClock.cycle)
        encounter.lastAbilityTick = deps.mapClock.cycle
        npc.scriptedDamageGate = true
        npc.ignoreCombatInteractions = true
        npc.movementLocked = true
        npc.anim(VETION_SPAWN_ANIM)
        state.lifecycle = UndeadLifecycleState.SPAWNING
        val generation = state.generation
        traceUndeadEvent(npc, encounter, state, profile, "spawn")
        deps.worldQueues.add(UNDEAD_SPAWN_LOCK_TICKS) {
            if (!isCurrentUndead(npc, encounter, state) || state.generation != generation)
                return@add
            state.lifecycle = UndeadLifecycleState.FORM_ONE_ACTIVE
            state.spawnUnlockTick = Int.MIN_VALUE
            encounter.invulnerable = false
            npc.scriptedDamageGate = false
            npc.ignoreCombatInteractions = false
            npc.movementLocked = false
            encounter.lastAbilityTick = deps.mapClock.cycle
            traceUndeadEvent(npc, encounter, state, profile, "spawn-complete")
        }
    }

    private fun recoverUndeadLifecycleIfNeeded(
        npc: Npc,
        encounter: BossEncounter,
        state: UndeadRuntimeState,
        profile: UndeadBossProfile,
    ) {
        if (
            !shouldRecoverUndeadLifecycle(
                lifecycle = state.lifecycle,
                active = state.active,
                slotAssigned = npc.isSlotAssigned,
                hitpoints = npc.hitpoints,
                currentTick = deps.mapClock.cycle,
                spawnUnlockTick = state.spawnUnlockTick,
            )
        ) {
            return
        }
        val enraged = npc.isEnragedUndead()
        state.active = true
        state.generation++
        state.normalHoundsTriggered = false
        state.enragedHoundsTriggered = false
        state.activeHounds = emptyList()
        state.spawnUnlockTick = Int.MIN_VALUE
        state.lifecycle =
            if (enraged) UndeadLifecycleState.FORM_TWO_ACTIVE
            else UndeadLifecycleState.FORM_ONE_ACTIVE
        encounter.lethalHandled = false
        encounter.invulnerable = false
        encounter.transitionTo(if (enraged) "enraged" else "normal", deps.mapClock.cycle)
        encounter.lastAbilityTick = deps.mapClock.cycle
        npc.scriptedDamageGate = false
        npc.ignoreCombatInteractions = false
        npc.movementLocked = false
        traceUndeadEvent(npc, encounter, state, profile, "spawn-recovered")
    }

    private fun prepareUndeadIncomingHit(
        npc: Npc,
        encounter: BossEncounter,
        state: UndeadRuntimeState,
        profile: UndeadBossProfile,
    ) {
        recoverUndeadLifecycleIfNeeded(npc, encounter, state, profile)
        val livingHounds = state.activeHounds.count { it.isSlotAssigned && it.hitpoints > 0 }
        if (
            !shouldUnlockUndeadDamage(
                lifecycle = state.lifecycle,
                active = state.active,
                slotAssigned = npc.isSlotAssigned,
                hitpoints = npc.hitpoints,
                livingHounds = livingHounds,
            )
        ) {
            return
        }
        val recoveredLock =
            encounter.invulnerable || npc.scriptedDamageGate || npc.ignoreCombatInteractions
        encounter.invulnerable = false
        npc.scriptedDamageGate = false
        npc.ignoreCombatInteractions = false
        npc.movementLocked = false
        if (recoveredLock) {
            traceUndeadEvent(npc, encounter, state, profile, "damage-gate-recovered")
        }
    }

    private fun beginUndeadTransform(
        npc: Npc,
        encounter: BossEncounter,
        state: UndeadRuntimeState,
        profile: UndeadBossProfile,
        secondForm: dev.openrune.types.NpcServerType,
    ) {
        if (state.lifecycle != UndeadLifecycleState.FORM_ONE_ACTIVE) return
        state.lifecycle = UndeadLifecycleState.TRANSFORMING
        state.generation++
        val generation = state.generation
        encounter.invulnerable = true
        npc.scriptedDamageGate = true
        npc.ignoreCombatInteractions = true
        npc.movementLocked = true
        npc.anim(VETION_ENRAGE_ANIM_1)
        npc.say("Do it again!!")
        val strikes = undeadTransformationStrikeTiles(npc.coords, npc.size)
        val warning = SpotanimType(VETION_MAGIC_CAST_2.asRSCM(RSCMType.SPOTANIM))
        val impact = SpotanimType(VETION_MAGIC_IMPACT_2.asRSCM(RSCMType.SPOTANIM))
        strikes.forEach { deps.worldRepo.spotanimMap(warning, it) }
        traceUndeadEvent(npc, encounter, state, profile, "transform-start", "tiles=$strikes")
        deps.worldQueues.add(UNDEAD_TRANSFORM_TICKS) {
            if (!isCurrentUndead(npc, encounter, state) || state.generation != generation)
                return@add
            strikes.forEach { deps.worldRepo.spotanimMap(impact, it) }
            applyUndeadLightningDamage(npc, profile, strikes, profile.lightningMax)
            npc.transmog(secondForm, Int.MAX_VALUE)
            npc.copyStats(secondForm)
            encounter.transitionTo("enraged", deps.mapClock.cycle)
            encounter.lastAbilityTick = deps.mapClock.cycle
            encounter.invulnerable = false
            npc.scriptedDamageGate = false
            npc.ignoreCombatInteractions = false
            npc.movementLocked = false
            state.lifecycle = UndeadLifecycleState.FORM_TWO_ACTIVE
            traceUndeadEvent(npc, encounter, state, profile, "transform-complete")
            if (profile.enragedTimeoutTicks != null) {
                scheduleUndeadReversion(npc, encounter, state, profile, generation)
            }
        }
    }

    private fun scheduleUndeadReversion(
        npc: Npc,
        encounter: BossEncounter,
        state: UndeadRuntimeState,
        profile: UndeadBossProfile,
        generation: Int,
    ) {
        deps.worldQueues.add(requireNotNull(profile.enragedTimeoutTicks)) {
            if (!isCurrentUndead(npc, encounter, state) || state.generation != generation)
                return@add
            if (
                state.lifecycle != UndeadLifecycleState.FORM_TWO_ACTIVE &&
                    state.lifecycle != UndeadLifecycleState.FORM_TWO_HOUNDS
            )
                return@add
            val hp = npc.hitpoints
            state.generation++
            npc.resetTransmog()
            npc.copyStats(npc.type)
            npc.hitpoints = hp.coerceIn(1, npc.baseHitpointsLvl)
            encounter.transitionTo("normal", deps.mapClock.cycle)
            encounter.lastAbilityTick = deps.mapClock.cycle
            state.lifecycle =
                if (state.activeHounds.any { it.isSlotAssigned && it.hitpoints > 0 }) {
                    npc.scriptedDamageGate = true
                    UndeadLifecycleState.FORM_ONE_HOUNDS
                } else {
                    encounter.invulnerable = false
                    npc.scriptedDamageGate = false
                    UndeadLifecycleState.FORM_ONE_ACTIVE
                }
            traceUndeadEvent(npc, encounter, state, profile, "enraged-timeout", "hp=$hp")
        }
    }

    private fun finishUndeadFight(npc: Npc) {
        val encounter = deps.encounterRegistry.existing(npc) ?: return
        val profile = undeadProfile(npc) ?: return
        val state = undeadState(npc, encounter)
        if (state.lifecycle == UndeadLifecycleState.FINAL_DEATH) return
        state.lifecycle = UndeadLifecycleState.FINAL_DEATH
        state.active = false
        state.generation++
        encounter.invulnerable = true
        npc.scriptedDamageGate = true
        state.activeHounds = emptyList()
        encounter.drainOwnedNpcs().forEach { hound ->
            if (hound.isSlotAssigned) deps.npcRepo.del(hound, Int.MAX_VALUE)
        }
        npc.ignoreCombatInteractions = true
        npc.movementLocked = true
        traceUndeadEvent(npc, encounter, state, profile, "final-death")
        deps.worldQueues.add(UNDEAD_DEATH_ANIM_TICKS) {
            if (deps.encounterRegistry.isCurrent(npc, encounter)) {
                state.lifecycle = UndeadLifecycleState.RESPAWN_PENDING
            }
        }
    }

    private suspend fun afterUndeadAttack(npc: Npc, target: Player, encounter: BossEncounter) {
        val profile = undeadProfile(npc) ?: return
        val state = undeadState(npc, encounter)
        traceUndeadEvent(
            npc,
            encounter,
            state,
            profile,
            "attack-selected",
            "target=${target.uid} targetTile=${target.coords} delta=" +
                "${target.coords.x - npc.coords.x},${target.coords.z - npc.coords.z} " +
                "distance=${target.coords.chebyshevDistance(npc.coords)} " +
                "bashEligible=${npc.isWithinDistance(target, 1)} selected=${encounter.lastAbilityName}",
        )
    }

    private fun applyUndeadLightningDamage(
        npc: Npc,
        profile: UndeadBossProfile,
        strikeCenters: Collection<CoordGrid>,
        maximum: Int,
        primaryTarget: Player? = null,
    ) {
        undeadPlayers(npc, profile, primaryTarget).forEach { player ->
            val distance =
                strikeCenters
                    .asSequence()
                    .filter { it.level == player.coords.level }
                    .minOfOrNull { player.coords.chebyshevDistance(it) } ?: return@forEach
            if (distance > LIGHTNING_AOE_RADIUS) return@forEach
            val damageMax = undeadLightningDamageMaximum(maximum, distance)
            player.queueHit(
                npc,
                1,
                EngineHitType.Typeless,
                deps.random.of(damageMax + 1),
                modifier = NpcBypassProtectionPrayerPlayerHitModifier,
            )
        }
    }

    private fun randomTargetedLightningTile(
        npc: Npc,
        playerTile: CoordGrid,
        profile: UndeadBossProfile,
    ): CoordGrid? {
        repeat(9) {
            val tile = playerTile.translate(deps.random.of(3) - 1, deps.random.of(3) - 1)
            if (isUndeadHazardTile(npc, profile, tile)) return tile
        }
        return playerTile.takeIf { isUndeadHazardTile(npc, profile, it) }
    }

    private fun randomArenaLightningTile(npc: Npc, profile: UndeadBossProfile): CoordGrid? {
        repeat(LIGHTNING_TILE_ATTEMPTS) {
            val tile =
                npc.coords.translate(
                    deps.random.of(LIGHTNING_SCATTER_DIAMETER) - LIGHTNING_SCATTER_RADIUS,
                    deps.random.of(LIGHTNING_SCATTER_DIAMETER) - LIGHTNING_SCATTER_RADIUS,
                )
            if (isUndeadHazardTile(npc, profile, tile)) return tile
        }
        return null
    }

    private fun isUndeadHazardTile(npc: Npc, profile: UndeadBossProfile, tile: CoordGrid): Boolean =
        (isMayhemEventArenaTile(npc, tile) || profile.room.contains(tile)) &&
            deps.collision.isZoneValid(tile) &&
            !deps.collision.isWalkBlocked(tile)

    private fun undeadPlayers(
        npc: Npc,
        profile: UndeadBossProfile,
        primaryTarget: Player? = null,
    ): List<Player> {
        val candidates = buildList {
            if (primaryTarget != null) add(primaryTarget)
            eligiblePlayers(npc).filterTo(this) { it !== primaryTarget }
        }
        return candidates.filter { player ->
            val validTarget = player.isValidTarget() && player.hitpoints > 0
            if (!validTarget) return@filter false
            val normalArena = profile.room.contains(player.coords)
            val mayhemArena = isMayhemEventArenaTile(npc, player.coords)
            val directCombatTarget =
                player === primaryTarget &&
                    undeadDirectTargetEligible(
                        targetValid = validTarget,
                        sameLevel = player.coords.level == npc.coords.level,
                        distance = player.coords.chebyshevDistance(npc.coords),
                    )
            normalArena || mayhemArena || directCombatTarget
        }
    }

    private fun undeadState(npc: Npc, encounter: BossEncounter): UndeadRuntimeState =
        encounter.state(UNDEAD_STATE_KEY) {
            UndeadRuntimeState().also { state ->
                encounter.onCleanup {
                    state.active = false
                    state.lifecycle = UndeadLifecycleState.DESPAWNED
                    state.generation++
                    state.activeHounds = emptyList()
                }
            }
        }

    private fun undeadProfile(npc: Npc): UndeadBossProfile? =
        UNDEAD_BOSS_PROFILES.firstOrNull {
            it.firstForm.removePrefix("npc.") == npc.type.internalName
        }

    private fun isCurrentUndead(
        npc: Npc,
        encounter: BossEncounter,
        state: UndeadRuntimeState,
    ): Boolean =
        state.active && npc.isSlotAssigned && deps.encounterRegistry.isCurrent(npc, encounter)

    private fun traceUndeadEvent(
        npc: Npc,
        encounter: BossEncounter,
        state: UndeadRuntimeState,
        profile: UndeadBossProfile,
        event: String,
        detail: String = "",
    ) {
        if (!traceUndeadBosses) return
        logger.info {
            "[UndeadBoss] tick=${deps.mapClock.cycle} event=$event boss=${profile.slug} " +
                "npcId=${npc.visType.id} state=${state.lifecycle} phase=${encounter.currentPhaseName} " +
                "hp=${npc.hitpoints}/${npc.baseHitpointsLvl} cooldown=" +
                "${(encounter.lastAbilityTick + encounter.expectedAttackRate() - deps.mapClock.cycle).coerceAtLeast(0)} " +
                "participants=${undeadPlayers(npc, profile).size} hounds=" +
                "${state.activeHounds.count { it.isSlotAssigned && it.hitpoints > 0 }} $detail"
        }
    }

    private fun spawnOwned(
        owner: Npc,
        npcName: String,
        count: Int,
        target: Player? = null,
    ): List<Npc> {
        if (count <= 0) return emptyList()
        val type = ServerCacheManager.getNpc(npcName.asRSCM(RSCMType.NPC)) ?: return emptyList()
        val encounter = deps.encounterRegistry.of(owner)
        val spawnTiles =
            buildList {
                    for (radius in 2..6) {
                        for (dx in -radius..radius) {
                            for (dz in -radius..radius) {
                                if (kotlin.math.abs(dx) != radius && kotlin.math.abs(dz) != radius)
                                    continue
                                val tile = owner.coords.translate(dx, dz)
                                val inRoom =
                                    owner.type.internalName !in SPIDER_BOSS_INTERNALS ||
                                        isSpiderArenaTile(owner, tile)
                                if (inRoom && canStand(tile, type.size)) add(tile)
                            }
                        }
                    }
                }
                .distinct()
                .toMutableList()
        return List(count) {
            val spawn =
                if (spawnTiles.isEmpty()) owner.coords
                else spawnTiles.removeAt(deps.random.of(spawnTiles.size))
            Npc(type, spawn).also {
                it.mode = type.defaultMode
                it.spawnOwner = owner.spawnOwner
                it.suppressRewards = owner.suppressRewards
                deps.npcRepo.add(it, Int.MAX_VALUE)
                it.respawns = false
                encounter.own(it)
                if (target != null && target.isValidTarget()) {
                    it.opPlayer2(target, aiPlayerInteractions)
                }
            }
        }
    }

    private fun nearbyPlayers(npc: Npc, radius: Int): List<Player> =
        eligiblePlayers(npc).filter {
            it.coords.chebyshevDistance(npc.coords) <= radius && it.hitpoints > 0
        }

    private fun eligiblePlayers(npc: Npc): List<Player> {
        val owner = npc.spawnOwner
        if (owner != PlayerUid.NULL) {
            return owner.resolve(deps.playerList)?.let(::listOf) ?: emptyList()
        }
        return deps.playerList.toList()
    }

    private fun Npc.isEnragedUndead(): Boolean =
        visType.internalName == "vetion_2" || visType.internalName == "vetion_2_single"

    private fun isMayhemEventArenaTile(npc: Npc, tile: CoordGrid): Boolean {
        val session =
            instances.instanceForNpc(npc)?.let(instances::sessionForId)
                ?: instances.sessionForRegion(
                    ((npc.coords.x shr 6) shl 8) or (npc.coords.z shr 6)
                )
                ?: return false
        return session.key == CLAN_MAYHEM_INSTANCE_KEY &&
            RegionRegistry.inWorkingArea(npc.coords) &&
            RegionRegistry.inWorkingArea(tile) &&
            tile.level == npc.coords.level &&
            tile.chebyshevDistance(npc.coords) <= MAYHEM_EVENT_BOSS_RADIUS
    }
}

private const val CHAOS_TELEPORT = "chaos-elemental.teleport"
private const val CHAOS_DISARM = "chaos-elemental.disarm"
private const val CHAOS_ELEMENTAL_ATTACK_ANIM = "seq.chaoselemental_attack"
private const val CHAOS_DISCORD_CAST = "spotanim.chaoselemental_spotanim_discord_casting"
private const val CHAOS_DISCORD_TRAVEL = "spotanim.chaoselemental_spotanim_discord_travel"
private const val CHAOS_DISCORD_IMPACT = "spotanim.chaoselemental_spotanim_discord_impact"
private const val CHAOS_CONFUSION_CAST = "spotanim.chaoselemental_spotanim_confusion_casting"
private const val CHAOS_CONFUSION_TRAVEL = "spotanim.chaoselemental_spotanim_confusion_travel"
private const val CHAOS_CONFUSION_IMPACT = "spotanim.chaoselemental_spotanim_confusion_impact"
private const val CHAOS_MADNESS_CAST = "spotanim.chaoselemental_spotanim_madness_casting"
private const val CHAOS_MADNESS_TRAVEL = "spotanim.chaoselemental_spotanim_madness_travel"
private const val CHAOS_MADNESS_IMPACT = "spotanim.chaoselemental_spotanim_madness_impact"
internal const val CHAOS_ELEMENTAL_MAX_HIT = 28
internal const val CHAOS_SPECIAL_DELAY_TICKS = 2

private val CHAOS_CONFUSION_TRAVEL_TYPE by lazy {
    SpotanimType(CHAOS_CONFUSION_TRAVEL.asRSCM(RSCMType.SPOTANIM))
}
private val CHAOS_MADNESS_TRAVEL_TYPE by lazy {
    SpotanimType(CHAOS_MADNESS_TRAVEL.asRSCM(RSCMType.SPOTANIM))
}
private val CHAOS_ELEMENTAL_PROJECTILE_CONFIG = ProjectileConfig()
private val CHAOS_ELEMENTAL_PROJECTILE =
    ProjAnimType(
        startHeight = CHAOS_ELEMENTAL_PROJECTILE_CONFIG.startHeight,
        endHeight = CHAOS_ELEMENTAL_PROJECTILE_CONFIG.endHeight,
        delay = CHAOS_ELEMENTAL_PROJECTILE_CONFIG.startDelay,
        angle = CHAOS_ELEMENTAL_PROJECTILE_CONFIG.angle,
        lengthAdjustment = CHAOS_ELEMENTAL_PROJECTILE_CONFIG.travelTime,
        progress = CHAOS_ELEMENTAL_PROJECTILE_CONFIG.progress,
        stepMultiplier = CHAOS_ELEMENTAL_PROJECTILE_CONFIG.stepMultiplier,
    )
private const val MALEDICTUS_ICE = "revenant-maledictus.ice"
private const val MALEDICTUS_BLOOD = "revenant-maledictus.blood"
private const val MALEDICTUS_AIR = "revenant-maledictus.air"
private const val BEAR_ATTACK = "wilderness-bear.attack"
private const val SPIDER_ATTACK = "wilderness-spider.attack"
private const val SPIDERLING_PRAYER_DRAIN = "wilderness-spiderling.prayer-drain"
private const val UNDEAD_LIGHTNING = "wilderness-undead.lightning"
private const val UNDEAD_BASH = "wilderness-undead.bash"

private fun magicProjectile(
    anim: String,
    graphic: String,
    max: Int,
    prayer: ProtectionPrayer = ProtectionPrayer.Standard,
): Effect =
    Effect.Sequence(
        listOf(
            Effect.Anim(anim),
            Effect.Projectile(
                spotanim = graphic,
                config = ProjectileConfig(),
                hit =
                    Effect.Hit(
                        damage = DamageExpr.Roll(0..max),
                        type = HitType.Magic,
                        protectionPrayer = prayer,
                    ),
            ),
        )
    )

private fun chaosDiscord(type: HitType, meleeType: MeleeAttackType? = null): Effect =
    Effect.Sequence(
        listOf(
            Effect.Anim(CHAOS_ELEMENTAL_ATTACK_ANIM),
            Effect.Spotanim(CHAOS_DISCORD_CAST),
            Effect.Projectile(
                spotanim = CHAOS_DISCORD_TRAVEL,
                config = ProjectileConfig(),
                impact = CHAOS_DISCORD_IMPACT,
                hit =
                    Effect.Hit(
                        damage = DamageExpr.Roll(0..CHAOS_ELEMENTAL_MAX_HIT),
                        type = type,
                        meleeType = meleeType,
                        protectionPrayer = ProtectionPrayer.Standard,
                    ),
            ),
        )
    )

internal val CHAOS_ELEMENTAL_SPEC: BossSpec =
    boss("npc.chaoselemental") {
        stats(attackRate = 4, aggressionRadius = 8)
        val magic = ability("discord_magic", chaosDiscord(HitType.Magic))
        val ranged = ability("discord_ranged", chaosDiscord(HitType.Ranged))
        val melee = ability("discord_melee", chaosDiscord(HitType.Melee, MeleeAttackType.Crush))
        val teleport = ability("confusion") { include(external(CHAOS_TELEPORT)) }
        val disarm = ability("madness") { include(external(CHAOS_DISARM)) }
        phase("combat") {
            weightedSelectorRandom(0.0) {
                +random(magic, 3)
                +random(ranged, 2)
                +random(melee, 1)
                +random(teleport, 1, cooldown = 8)
                +random(disarm, 1, cooldown = 8)
            }
        }
    }

internal val MALEDICTUS_SPEC: BossSpec =
    boss("npc.wild_cave_superior") {
        stats(attackRate = 5, aggressionRadius = 16)
        val ranged =
            ability(
                "ranged",
                magicProjectile(
                        "seq.npc_revenant_superior_attack_melee",
                        "spotanim.revenant_superior_standard_attack",
                        30,
                    )
                    .let { effect ->
                        (effect as Effect.Sequence).copy(
                            effects =
                                effect.effects.map {
                                    if (it is Effect.Projectile)
                                        it.copy(
                                            hit =
                                                it.hit?.copy(
                                                    type = HitType.Ranged,
                                                    protectionPrayer = ProtectionPrayer.Reduce(50),
                                                )
                                        )
                                    else it
                                }
                        )
                    },
            )
        val ice = ability("ice") { include(external(MALEDICTUS_ICE)) }
        val blood = ability("blood") { include(external(MALEDICTUS_BLOOD)) }
        val air = ability("air") { include(external(MALEDICTUS_AIR)) }
        phase("combat") {
            rotationSelector {
                +then(ranged)
                +then(ranged)
                +then(ranged)
                +then(ice)
                +then(ranged)
                +then(ranged)
                +then(ranged)
                +then(blood)
                +then(ranged)
                +then(ranged)
                +then(ranged)
                +then(air)
            }
        }
    }


private const val MAYHEM_EVENT_BOSS_RADIUS = 64
private const val CLAN_MAYHEM_INSTANCE_KEY = "clan-mayhem"

private fun bearSpec(npc: String): BossSpec =
    boss(npc) {
        stats(attackRate = BEAR_ATTACK_RATE_TICKS, aggressionRadius = 12)
        val attack = ability("attack") { include(external(BEAR_ATTACK)) }
        phase("combat") { rotationSelector { +then(attack) } }
    }

internal val CALLISTO_SPEC = bearSpec("npc.callisto")
internal val ARTIO_SPEC = bearSpec("npc.callisto_singles")

private fun spiderSpec(
    npc: String,
    meleeMax: Int,
    rangedMax: Int,
    magicMax: Int,
    aggressionRadius: Int,
    multiTarget: Boolean,
): BossSpec =
    boss(npc) {
        stats(attackRate = 4, aggressionRadius = aggressionRadius)
        val attackParams = "$multiTarget|$meleeMax|$rangedMax|$magicMax"
        val melee = ability("melee") { include(external(SPIDER_ATTACK, attackParams)) }
        val ranged = ability("ranged") { include(external(SPIDER_ATTACK, attackParams)) }
        val magic = ability("magic") { include(external(SPIDER_ATTACK, attackParams)) }
        phase("combat") {
            meleeOverrideRotationSelector(melee) {
                repeat(8) { +then(ranged) }
                repeat(8) { +then(magic) }
            }
        }
    }

internal val VENENATIS_SPEC = spiderSpec("npc.venenatis", 21, 35, 30, 30, multiTarget = true)
internal val SPINDEL_SPEC = spiderSpec("npc.venenatis_singles", 14, 31, 24, 16, multiTarget = false)

internal val SPIDERLING_SPEC: BossSpec =
    boss("npc.venenatis_spiderling", "npc.spindel_spiderling") {
        stats(attackRate = 4, aggressionRadius = 12)
        val melee =
            ability("melee") {
                npcAttackAnim()
                hit {
                    damage(0..3).roll()
                    type(Melee)
                    meleeType = MeleeAttackType.Stab
                }
                include(external(SPIDERLING_PRAYER_DRAIN))
            }
        phase("combat") { weightedSelectorRandom(0.0) { +random(melee, 1) } }
    }

private fun undeadSpec(first: String, second: String, normalMax: Int, bashMax: Int): BossSpec =
    boss(first, second) {
        stats(attackRate = 6, aggressionRadius = 12)
        val lightning = ability("lightning") { include(external(UNDEAD_LIGHTNING, normalMax)) }
        val bash = ability("shield_bash") { include(external(UNDEAD_BASH, bashMax)) }
        phase("normal", attackRate = 6) {
            rotationSelector {
                repeat(4) { +then(lightning) }
                +then(bash)
            }
        }
        phase("enraged", attackRate = 5) {
            rotationSelector {
                repeat(4) { +then(lightning) }
                +then(bash)
            }
        }
    }

internal val VETION_SPEC = undeadSpec("npc.vetion", "npc.vetion_2", 44, 35)
internal val CALVARION_SPEC = undeadSpec("npc.vetion_single", "npc.vetion_2_single", 26, 26)

internal val VETION_PROFILE =
    UndeadBossProfile(
        slug = "vetion",
        firstForm = "npc.vetion",
        secondForm = "npc.vetion_2",
        normalHound = "npc.vetion_hellhound_jnr",
        enragedHound = "npc.vetion_hellhound_snr",
        lightningMax = 44,
        bashMax = 35,
        room = VETION_ROOM,
        enragedTimeoutTicks = VETION_ENRAGED_TIMEOUT_TICKS,
    )

internal val CALVARION_PROFILE =
    UndeadBossProfile(
        slug = "calvarion",
        firstForm = "npc.vetion_single",
        secondForm = "npc.vetion_2_single",
        normalHound = "npc.vetion_hellhound_jnr_singles",
        enragedHound = "npc.vetion_hellhound_snr_singles",
        lightningMax = 26,
        bashMax = 26,
        room = CALVARION_ROOM,
        enragedTimeoutTicks = null,
    )

private val UNDEAD_BOSS_PROFILES = listOf(VETION_PROFILE, CALVARION_PROFILE)

private const val BEAR_STATE_KEY = "wilderness-bear.runtime"
private const val BEAR_MELEE_ANIM = "seq.npc_callisto_attack_melee01"
private const val BEAR_RANGED_ANIM = "seq.npc_callisto_attack_ranged01"
private const val BEAR_MAGIC_ANIM = "seq.npc_callisto_attack_magic01"
private const val BEAR_ENRAGE_ANIM = "seq.npc_callisto_enraged01"
private const val BEAR_SPAWN_ANIM = "seq.npc_callisto_spawn01"
private const val BEAR_RANGED_CAST = "spotanim.fx_callisto_ranged_spotanim"
private const val BEAR_RANGED_PROJECTILE = "spotanim.fx_callisto_ranged_projectile"
private const val BEAR_RANGED_IMPACT = "spotanim.fx_callisto_ranged_impact"
private const val BEAR_MAGIC_ORB_LARGE = "spotanim.fx_ursine01_special"
private const val BEAR_MAGIC_ORB_SMALL = "spotanim.fx_ursine02_special"
private const val BEAR_ENRAGE_LARGE = "spotanim.fx_callisto_enrage"
private const val BEAR_ENRAGE_SMALL = "spotanim.fx_callisto_enrage_small"
private const val BEAR_TRAP_SPOTANIM = "spotanim.fx_callisto_trap"
internal const val BEAR_ATTACK_RATE_TICKS = 4
internal const val BEAR_SPAWN_LOCK_TICKS = 3
internal const val BEAR_MELEE_IMPACT_DELAY = 1
internal const val BEAR_HIT_QUEUE_DELAY = 1
internal const val BEAR_ROAR_LOCK_TICKS = 4
internal const val BEAR_TRAP_SPAWN_DELAY = 1
internal const val BEAR_TRAP_LIFETIME_TICKS = 20
internal const val BEAR_TRAP_BIND_TICKS = 5
internal const val BEAR_TRAP_MAX_HIT = 15
internal const val BEAR_NATURAL_FREEZE_IMMUNITY_TICKS = 5
internal const val BEAR_MAGIC_MIN_NORMAL_ATTACKS = 1
internal const val BEAR_MAGIC_MAX_NORMAL_ATTACKS = 4
internal const val BEAR_KNOCKBACK_DISTANCE = 3
private const val BEAR_TRAP_MIN_RADIUS = 3
private const val BEAR_TRAP_MAX_RADIUS = 12
private val BEAR_RANGED_PROJECTILE_TYPE = ProjectileConfig().toProjectileType()
private val BEAR_MAGIC_PROJECTILE_TYPE = ProjectileConfig().toProjectileType()
private const val VENENATIS_INTERNAL = "venenatis"
private const val SPINDEL_INTERNAL = "venenatis_singles"
private const val VENENATIS_SPIDERLING = "npc.venenatis_spiderling"
private const val SPINDEL_SPIDERLING = "npc.spindel_spiderling"
private val SPIDER_BOSS_INTERNALS = setOf(VENENATIS_INTERNAL, SPINDEL_INTERNAL)
private val SPIDERLING_INTERNALS = setOf("venenatis_spiderling", "spindel_spiderling")
private const val SPIDER_STATE_KEY = "wilderness-spider.runtime"
private const val SPIDER_MELEE_ANIM = "seq.npc_venenatis_melee_01"
private const val SPIDER_RANGED_ANIM = "seq.npc_venenatis_ranged_01"
private const val SPIDER_MAGIC_ANIM = "seq.npc_venenatis_magic_01"
private const val SPIDER_DEATH_ANIM = "seq.npc_venenatis_death_01"
private const val SPIDER_SPAWN_ANIM = "seq.npc_venenatis_spawn_01"
private const val SPIDER_RANGED_PROJECTILE = "spotanim.fx_venenatis_ranged_projectile"
private const val SPIDER_RANGED_IMPACT = "spotanim.fx_venenatis_ranged_impact"
private const val SPIDER_MAGIC_PROJECTILE = "spotanim.fx_venenatis_magic_projectile"
private const val SPIDER_MAGIC_IMPACT = "spotanim.fx_venenatis_magic_impact"
private const val SPIDER_WEB_PROJECTILE = "spotanim.fx_venenatis_web_projectile"
private const val SPIDER_WEB_IMPACT = "spotanim.fx_venenatis_web_impact"
private const val SPIDER_WEB_FLOOR = "loc.venenatis_webbed_floor"
internal const val SPIDER_ROTATION_LENGTH = 16
internal const val CROWD_THRESHOLD = 10
private const val SPIDER_ATTACK_RANGE = 10
private const val SPIDER_SPAWN_LOCK_TICKS = 3
private const val SPINDEL_SPIDERLING_CAP = 10
private const val VENENATIS_SPIDERLING_CAP = 20
private const val SPINDEL_SPIDERLINGS_PER_SPECIAL = 2
private const val SPIDERLING_DAMAGE_STEP = 10
private const val SPIDERLING_DAMAGE_CAP = 30
private const val WEB_X_RADIUS = 3
private const val WEB_Z_RADIUS = 2
private const val WEB_DURATION_TICKS = 20
private const val WEB_PROC_INTERVAL = 2
private const val WEB_DAMAGE = 3
private const val WEB_PRAYER_DRAIN = 3
private const val WEB_RUN_DRAIN = 100
private const val ARANEA_BOOTS = "obj.aranea_boots"
private val SPIDER_RANGED_PROJECTILE_TYPE = ProjectileConfig().toProjectileType()
private val SPIDER_MAGIC_PROJECTILE_TYPE = ProjectileConfig().toProjectileType()
private val SPIDER_WEB_PROJECTILE_TYPE = ProjectileConfig().toProjectileType()
private const val VETION_MAGIC_ANIM_1 = "seq.npc_vetion_attack_magic_01"
private const val VETION_MAGIC_ANIM_2 = "seq.npc_vetion_attack_magic_02"
private const val VETION_MELEE_ANIM_1 = "seq.npc_vetion_attack_melee_01"
private const val VETION_MELEE_ANIM_2 = "seq.npc_vetion_attack_melee_02"
private const val VETION_MAGIC_CAST_1 = "spotanim.fx_vetion_attack_magic_01"
private const val VETION_MAGIC_CAST_2 = "spotanim.fx_vetion_attack_magic_02"
private const val VETION_MAGIC_IMPACT_1 = "spotanim.fx_vetion_attack_magic_impact_01"
private const val VETION_MAGIC_IMPACT_2 = "spotanim.fx_vetion_attack_magic_impact_02"
private const val VETION_MELEE_IMPACT = "spotanim.fx_vetion_attack_melee"
private const val UNDEAD_STATE_KEY = "wilderness-undead.runtime"
private const val VETION_SPAWN_ANIM = 9977
private const val VETION_ENRAGE_ANIM_1 = 9978
private const val VETION_PARRY_ANIM_1 = 9973
private const val VETION_PARRY_ANIM_2 = 9974
private const val VETION_SHIELD_ANIM_1 = 9975
private const val VETION_SHIELD_ANIM_2 = 9976
private const val HOUND_ATTACK_ANIM = "seq.wbr_vention_hellhound_attack"
private const val HOUND_DEFEND_ANIM = "seq.wbr_vention_hellhound_defend"
private const val HOUND_DEATH_ANIM = "seq.wbr_vention_hellhound_death"

private val WILDERNESS_BOSS_SPECS =
    listOf(
        CHAOS_ELEMENTAL_SPEC,
        MALEDICTUS_SPEC,
        CALLISTO_SPEC,
        ARTIO_SPEC,
        VENENATIS_SPEC,
        SPINDEL_SPEC,
        VETION_SPEC,
        CALVARION_SPEC,
        SPIDERLING_SPEC,
    )

internal val WILDERNESS_RUNTIME_SEQUENCE_KEYS =
    setOf(
        "seq.chaoselemental_attack",
        BEAR_MAGIC_ANIM,
        BEAR_MELEE_ANIM,
        BEAR_RANGED_ANIM,
        BEAR_ENRAGE_ANIM,
        BEAR_SPAWN_ANIM,
        "seq.npc_revenant_superior_attack_dot",
        "seq.npc_revenant_superior_attack_heal",
        "seq.npc_revenant_superior_attack_melee",
        "seq.npc_revenant_superior_attack_stun",
        SPIDER_MAGIC_ANIM,
        SPIDER_MELEE_ANIM,
        SPIDER_RANGED_ANIM,
        SPIDER_DEATH_ANIM,
        SPIDER_SPAWN_ANIM,
        VETION_MAGIC_ANIM_1,
        VETION_MAGIC_ANIM_2,
        VETION_MELEE_ANIM_1,
        VETION_MELEE_ANIM_2,
        HOUND_ATTACK_ANIM,
        HOUND_DEFEND_ANIM,
        HOUND_DEATH_ANIM,
    )

internal val WILDERNESS_RUNTIME_SPOTANIM_KEYS =
    setOf(
        CHAOS_DISCORD_CAST,
        CHAOS_DISCORD_TRAVEL,
        CHAOS_DISCORD_IMPACT,
        CHAOS_CONFUSION_CAST,
        CHAOS_CONFUSION_TRAVEL,
        CHAOS_CONFUSION_IMPACT,
        CHAOS_MADNESS_CAST,
        CHAOS_MADNESS_TRAVEL,
        CHAOS_MADNESS_IMPACT,
        "spotanim.revenant_superior_special_2_impact",
        "spotanim.revenant_superior_standard_attack",
        BEAR_RANGED_CAST,
        BEAR_RANGED_PROJECTILE,
        BEAR_RANGED_IMPACT,
        BEAR_MAGIC_ORB_LARGE,
        BEAR_MAGIC_ORB_SMALL,
        BEAR_ENRAGE_LARGE,
        BEAR_ENRAGE_SMALL,
        BEAR_TRAP_SPOTANIM,
        SPIDER_RANGED_PROJECTILE,
        SPIDER_RANGED_IMPACT,
        SPIDER_MAGIC_PROJECTILE,
        SPIDER_MAGIC_IMPACT,
        SPIDER_WEB_PROJECTILE,
        SPIDER_WEB_IMPACT,
        VETION_MAGIC_CAST_1,
        VETION_MAGIC_CAST_2,
        VETION_MAGIC_IMPACT_1,
        VETION_MAGIC_IMPACT_2,
        VETION_MELEE_IMPACT,
    )

internal val WILDERNESS_RUNTIME_LOC_KEYS = setOf(SPIDER_WEB_FLOOR)

internal val WILDERNESS_RUNTIME_NPC_KEYS =
    setOf(
        "npc.venenatis_spiderling",
        "npc.spindel_spiderling",
        "npc.vetion_hellhound_jnr",
        "npc.vetion_hellhound_snr",
        "npc.vetion_hellhound_jnr_singles",
        "npc.vetion_hellhound_snr_singles",
    )

internal const val RANDOM_LIGHTNING_COUNT = 4
internal const val MAX_TARGETED_LIGHTNING = 3
internal const val MAX_LIGHTNING_COUNT = 7
internal const val LIGHTNING_WINDUP_TICKS = 2
internal const val LIGHTNING_AOE_RADIUS = 1
internal const val BASH_WINDUP_TICKS = 2
internal const val BASH_ATTACK_LOCK_TICKS = 8
internal const val BASH_RADIUS = 2
private const val LIGHTNING_SCATTER_RADIUS = 5
private const val LIGHTNING_SCATTER_DIAMETER = LIGHTNING_SCATTER_RADIUS * 2 + 1
private const val LIGHTNING_TILE_ATTEMPTS = 32
internal const val HOUNDS_PER_WAVE = 2
internal const val HOUND_ATTACK_RATE = 4
private const val HOUND_RETARGET_TICKS = 2
internal const val UNDEAD_SPAWN_LOCK_TICKS = 2
internal const val UNDEAD_TRANSFORM_TICKS = 2
internal const val UNDEAD_DIRECT_TARGET_RADIUS = 16
internal const val UNDEAD_DEATH_ANIM_TICKS = 5
internal const val VETION_ENRAGED_TIMEOUT_TICKS = 500

internal fun undeadHoundThreshold(baseHitpoints: Int): Int = baseHitpoints.coerceAtLeast(1) / 2

internal fun undeadDamageToThreshold(current: Int, incoming: Int, threshold: Int): Int =
    incoming.coerceAtLeast(0).coerceAtMost((current - threshold).coerceAtLeast(0))

internal fun undeadLightningStrikeCount(playerCount: Int): Int =
    (RANDOM_LIGHTNING_COUNT + playerCount.coerceAtLeast(0).coerceAtMost(MAX_TARGETED_LIGHTNING))
        .coerceAtMost(MAX_LIGHTNING_COUNT)

internal fun undeadLightningDamageMaximum(maximum: Int, distance: Int): Int =
    if (distance <= 0) maximum.coerceAtLeast(0) else (maximum.coerceAtLeast(0) + 1) / 2

internal fun undeadDirectTargetEligible(
    targetValid: Boolean,
    sameLevel: Boolean,
    distance: Int,
): Boolean =
    targetValid && sameLevel && distance in 0..UNDEAD_DIRECT_TARGET_RADIUS

internal fun undeadBashTiles(origin: CoordGrid, npcSize: Int): Set<CoordGrid> {
    val centerOffset = (npcSize.coerceAtLeast(1) - 1) / 2
    val center = origin.translate(centerOffset, centerOffset)
    return buildSet {
        for (dx in -BASH_RADIUS..BASH_RADIUS) {
            for (dz in -BASH_RADIUS..BASH_RADIUS) add(center.translate(dx, dz))
        }
    }
}

internal fun undeadTransformationStrikeTiles(origin: CoordGrid, npcSize: Int): Set<CoordGrid> {
    val centerOffset = (npcSize.coerceAtLeast(1) - 1) / 2
    val center = origin.translate(centerOffset, centerOffset)
    return buildSet {
        for (dx in listOf(-4, 0, 4)) {
            for (dz in listOf(-4, 0, 4)) {
                if (dx != 0 || dz != 0) add(center.translate(dx, dz))
            }
        }
    }
}

internal fun normalizeNpcInternalName(internalName: String): String =
    internalName.removePrefix("npc.")

internal fun footprintsOverlap(a: CoordGrid, aSize: Int, b: CoordGrid, bSize: Int): Boolean =
    a.level == b.level &&
        a.x < b.x + bSize &&
        a.x + aSize > b.x &&
        a.z < b.z + bSize &&
        a.z + aSize > b.z

internal enum class BearLifecycleState {
    UNINITIALIZED,
    SPAWNING,
    ACTIVE,
    DEAD;

    val allowsTargetAcquisition: Boolean
        get() = this == SPAWNING || this == ACTIVE

    val allowsAttacks: Boolean
        get() = this == ACTIVE
}

internal fun shouldRecoverBearLifecycle(
    lifecycle: BearLifecycleState,
    active: Boolean,
    slotAssigned: Boolean,
    hitpoints: Int,
    currentTick: Int,
    spawnUnlockTick: Int,
): Boolean =
    slotAssigned &&
        hitpoints > 0 &&
        ((lifecycle == BearLifecycleState.UNINITIALIZED && !active) ||
            (lifecycle == BearLifecycleState.SPAWNING && active && currentTick >= spawnUnlockTick))

internal fun shouldUnlockBearDamage(
    lifecycle: BearLifecycleState,
    active: Boolean,
    slotAssigned: Boolean,
    hitpoints: Int,
): Boolean = lifecycle == BearLifecycleState.ACTIVE && active && slotAssigned && hitpoints > 0

internal fun shouldRecoverUndeadLifecycle(
    lifecycle: UndeadLifecycleState,
    active: Boolean,
    slotAssigned: Boolean,
    hitpoints: Int,
    currentTick: Int,
    spawnUnlockTick: Int,
): Boolean =
    slotAssigned &&
        hitpoints > 0 &&
        ((lifecycle == UndeadLifecycleState.DESPAWNED && !active) ||
            (lifecycle == UndeadLifecycleState.SPAWNING &&
                active &&
                currentTick >= spawnUnlockTick))

internal fun shouldUnlockUndeadDamage(
    lifecycle: UndeadLifecycleState,
    active: Boolean,
    slotAssigned: Boolean,
    hitpoints: Int,
    livingHounds: Int,
): Boolean =
    active &&
        slotAssigned &&
        hitpoints > 0 &&
        livingHounds == 0 &&
        (lifecycle == UndeadLifecycleState.FORM_ONE_ACTIVE ||
            lifecycle == UndeadLifecycleState.FORM_TWO_ACTIVE)

internal enum class UndeadLifecycleState {
    DESPAWNED,
    SPAWNING,
    FORM_ONE_ACTIVE,
    FORM_ONE_HOUNDS,
    TRANSFORMING,
    FORM_TWO_ACTIVE,
    FORM_TWO_HOUNDS,
    FINAL_DEATH,
    RESPAWN_PENDING,
    RESETTING;

    val allowsAttacks: Boolean
        get() =
            this == FORM_ONE_ACTIVE ||
                this == FORM_ONE_HOUNDS ||
                this == FORM_TWO_ACTIVE ||
                this == FORM_TWO_HOUNDS

    val allowsDefendAnimation: Boolean
        get() = allowsAttacks
}

internal enum class SpiderSpecial {
    Spiderlings,
    Web,
}

internal fun spiderDistantStyle(opportunity: Int): HitType =
    if (Math.floorMod(opportunity, SPIDER_ROTATION_LENGTH) < 8) HitType.Ranged else HitType.Magic

internal fun spiderShouldMove(opportunity: Int, crowded: Boolean = false): Boolean {
    val index = Math.floorMod(opportunity, SPIDER_ROTATION_LENGTH)
    return if (crowded) index % 2 == 1 else index == 3 || index == 7 || index == 11 || index == 15
}

internal fun spiderSpecialAt(
    opportunity: Int,
    crowded: Boolean = false,
    lastSpecial: SpiderSpecial? = null,
): SpiderSpecial? {
    val index = Math.floorMod(opportunity, SPIDER_ROTATION_LENGTH)
    if (crowded) {
        if (lastSpecial == null) return SpiderSpecial.Spiderlings
        if (!spiderShouldMove(index, crowded = true)) return null
        return if (lastSpecial == SpiderSpecial.Spiderlings) SpiderSpecial.Web
        else SpiderSpecial.Spiderlings
    }
    return when (index) {
        0 -> SpiderSpecial.Spiderlings
        10 -> SpiderSpecial.Web
        else -> null
    }
}

internal fun venenatisSpiderlingCount(playerCount: Int): Int =
    if (playerCount <= CROWD_THRESHOLD) 2 else 3

internal fun spiderlingForcedDamage(currentHitpoints: Int): Int = currentHitpoints.coerceAtLeast(0)

internal fun spiderlingEmpoweredMaximum(aliveSpiderlings: Int): Int =
    (aliveSpiderlings.coerceAtLeast(0) * SPIDERLING_DAMAGE_STEP).coerceAtMost(SPIDERLING_DAMAGE_CAP)

private fun spiderAttackAnim(style: HitType): String =
    when (style) {
        HitType.Melee -> SPIDER_MELEE_ANIM
        HitType.Ranged -> SPIDER_RANGED_ANIM
        HitType.Magic -> SPIDER_MAGIC_ANIM
        else -> SPIDER_RANGED_ANIM
    }

private fun ProjectileConfig.toProjectileType(): ProjAnimType =
    ProjAnimType(
        startHeight = startHeight,
        endHeight = endHeight,
        delay = startDelay,
        angle = angle,
        lengthAdjustment = travelTime,
        progress = progress,
        stepMultiplier = stepMultiplier,
    )

private data class BearProfile(
    val internalName: String,
    val room: WildernessBossRoom,
    val multiTarget: Boolean,
    val meleeMin: Int,
    val meleeMax: Int,
    val rangedMax: Int,
    val thresholds: List<Int>,
    val trapCount: Int,
)

private data class BearRuntimeState(
    var lifecycle: BearLifecycleState = BearLifecycleState.UNINITIALIZED,
    var active: Boolean = false,
    var spawnUnlockTick: Int = Int.MIN_VALUE,
    val pendingRoars: ArrayDeque<Int> = ArrayDeque(),
    val firedThresholds: MutableSet<Int> = hashSetOf(),
    val traps: MutableMap<CoordGrid, Int> = hashMapOf(),
    var trapPulseScheduled: Boolean = false,
    var lowTrapPhase: Boolean = false,
    var firstFreezeAvailable: Boolean = true,
    var freezeImmuneUntil: Int = Int.MIN_VALUE,
    var roaringUntil: Int = Int.MIN_VALUE,
    var bindGeneration: Int = 0,
    var attackCounter: Int = 0,
    var magicCountdown: Int = BEAR_MAGIC_MIN_NORMAL_ATTACKS,
)

private val BEAR_PROFILES =
    listOf(
        BearProfile(
            internalName = "callisto",
            room = CALLISTO_ROOM,
            multiTarget = true,
            meleeMin = 35,
            meleeMax = 55,
            rangedMax = 31,
            thresholds = listOf(666, 333),
            trapCount = 8,
        ),
        BearProfile(
            internalName = "callisto_singles",
            room = ARTIO_ROOM,
            multiTarget = false,
            meleeMin = 15,
            meleeMax = 35,
            rangedMax = 17,
            thresholds = listOf(300, 150),
            trapCount = 4,
        ),
    )

internal fun bearThresholdsCrossed(
    before: Int,
    after: Int,
    thresholds: List<Int>,
    fired: Set<Int> = emptySet(),
): List<Int> =
    thresholds.filter { threshold ->
        threshold !in fired && before > threshold && after <= threshold
    }

internal fun bearKnockbackDamage(successfulSteps: Int): Int =
    5 + (BEAR_KNOCKBACK_DISTANCE - successfulSteps.coerceIn(0, BEAR_KNOCKBACK_DISTANCE)) * 15

internal fun bearFreezeTicks(spellName: String): Int =
    when (spellName.lowercase()) {
        "bind",
        "ice rush" -> 8
        "snare",
        "ice burst" -> 16
        "entangle",
        "ice blitz" -> 24
        "ice barrage" -> 32
        else -> 0
    }

internal data class UndeadBossProfile(
    val slug: String,
    val firstForm: String,
    val secondForm: String,
    val normalHound: String,
    val enragedHound: String,
    val lightningMax: Int,
    val bashMax: Int,
    val room: WildernessBossRoom,
    val enragedTimeoutTicks: Int?,
)

private data class UndeadRuntimeState(
    var lifecycle: UndeadLifecycleState = UndeadLifecycleState.DESPAWNED,
    var active: Boolean = false,
    var generation: Int = 0,
    var spawnUnlockTick: Int = Int.MIN_VALUE,
    var normalHoundsTriggered: Boolean = false,
    var enragedHoundsTriggered: Boolean = false,
    var activeHounds: List<Npc> = emptyList(),
)

private data class HoundCombatProfile(
    val internalName: String,
    val maxHit: Int,
    val attackAnimation: String = HOUND_ATTACK_ANIM,
    val defendAnimation: String = HOUND_DEFEND_ANIM,
)

private val HOUND_COMBAT_PROFILES =
    listOf(
        HoundCombatProfile(internalName = "npc.vetion_hellhound_jnr", maxHit = 26),
        HoundCombatProfile(internalName = "npc.vetion_hellhound_snr", maxHit = 32),
        HoundCombatProfile(internalName = "npc.vetion_hellhound_jnr_singles", maxHit = 16),
        HoundCombatProfile(internalName = "npc.vetion_hellhound_snr_singles", maxHit = 18),
    )

private data class SpiderAttackProfile(
    val multiTarget: Boolean,
    val meleeMax: Int,
    val rangedMax: Int,
    val magicMax: Int,
) {
    companion object {
        fun decode(value: String?): SpiderAttackProfile? {
            val parts = value?.split('|') ?: return null
            if (parts.size != 4) return null
            return SpiderAttackProfile(
                multiTarget = parts[0].toBooleanStrictOrNull() ?: return null,
                meleeMax = parts[1].toIntOrNull() ?: return null,
                rangedMax = parts[2].toIntOrNull() ?: return null,
                magicMax = parts[3].toIntOrNull() ?: return null,
            )
        }
    }
}

private data class SpiderArenaBounds(
    val minX: Int,
    val maxX: Int,
    val minZ: Int,
    val maxZ: Int,
    val level: Int,
)

private data class ActiveSpiderWeb(
    val id: Int,
    val tiles: Set<CoordGrid>,
    val expiresAt: Int,
    val bootsProtectionUsed: MutableSet<PlayerUid> = hashSetOf(),
)

private data class SpiderRuntimeState(
    var nextWebId: Int = 0,
    var lastSpecial: SpiderSpecial? = null,
    val activeWebs: MutableMap<Int, ActiveSpiderWeb> = hashMapOf(),
    val tileReferences: MutableMap<CoordGrid, Int> = hashMapOf(),
    val webLocs: MutableMap<CoordGrid, LocInfo> = hashMapOf(),
)

private val VENENATIS_ARENA = SpiderArenaBounds(3402, 3443, 10181, 10225, 2)
private val SPINDEL_ARENA = SpiderArenaBounds(1600, 1663, 11520, 11583, 2)
private val VENENATIS_MOVEMENT_TILES =
    listOf(3416, 3421, 3427).flatMap { x -> listOf(CoordGrid(x, 10196, 2), CoordGrid(x, 10199, 2)) }
private val SPINDEL_MOVEMENT_TILES =
    listOf(1624, 1630, 1635).flatMap { x -> listOf(CoordGrid(x, 11540, 2), CoordGrid(x, 11546, 2)) }
