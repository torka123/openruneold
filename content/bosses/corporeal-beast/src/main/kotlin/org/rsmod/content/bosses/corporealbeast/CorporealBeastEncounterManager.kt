package org.rsmod.content.bosses.corporealbeast

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ProjAnimType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.combat.NpcAttackOverride
import org.rsmod.api.combat.NpcProjectile
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.heal
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.NpcPartialProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitImpactHandler
import org.rsmod.game.hit.HitType
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Public-lair encounter state for the two revision-240 Corporeal Beast spawns. */
@Singleton
internal class CorporealBeastEncounterManager
@Inject
constructor(
    private val clock: MapClock,
    private val combat: NvPCombat,
    private val accuracy: AccuracyFormulae,
    private val random: GameRandom,
    private val players: PlayerList,
    private val npcs: NpcRepository,
    private val world: WorldRepository,
    private val worldQueues: WorldQueueList,
    private val collision: CollisionFlagMap,
) {
    private val states = mutableMapOf<Npc, CorpState>()
    private val coreOwners = mutableMapOf<Npc, Npc>()

    fun validateAssets() {
        val boss = requireNpc(CorporealBeastAssets.BOSS_NPC_ID)
        val core = requireNpc(CorporealBeastAssets.DARK_CORE_NPC_ID)
        require(boss.hitpoints == CorporealBeastRules.HITPOINTS) {
            "Revision-240 Corporeal Beast must have ${CorporealBeastRules.HITPOINTS} hitpoints."
        }
        require(core.hitpoints == CorporealBeastRules.DARK_CORE_HITPOINTS) {
            "Revision-240 Dark Core must have ${CorporealBeastRules.DARK_CORE_HITPOINTS} hitpoints."
        }
        listOf(
                CorporealBeastAssets.MAGIC_ANIMATION,
                CorporealBeastAssets.DRAIN_ANIMATION,
                CorporealBeastAssets.AREA_MAGIC_ANIMATION,
                CorporealBeastAssets.MELEE_LEFT_ANIMATION,
                CorporealBeastAssets.MELEE_RIGHT_ANIMATION,
                CorporealBeastAssets.STOMP_ANIMATION,
            )
            .forEach { id ->
                requireNotNull(ServerCacheManager.getAnim(id)) { "Missing animation $id" }
            }
        listOf(
                CorporealBeastAssets.DRAIN_PROJECTILE_SPOTANIM,
                CorporealBeastAssets.AREA_PROJECTILE_SPOTANIM,
                CorporealBeastAssets.MAGIC_PROJECTILE_SPOTANIM,
                CorporealBeastAssets.DARK_CORE_JUMP_SPOTANIM,
                CorporealBeastAssets.AREA_IMPACT_SPOTANIM,
            )
            .forEach(::spotanimInternal)
    }

    fun attack(access: StandardNpcAccess, target: Player) {
        val npc = access.npc
        if (npc.hitpoints <= 0 || !target.isSlotAssigned || target.hitpoints <= 0) return
        if (!CorporealBeastRules.roomMatches(npc.spawnCoords, target.coords)) return
        if (access.actionDelay > clock.cycle) return

        val state = state(npc)
        state.lastCombatTick = clock.cycle
        val inMeleeReach =
            CorporealBeastRules.distanceToFootprint(
                npc.coords.x,
                npc.coords.z,
                npc.visType.size,
                target.coords.x,
                target.coords.z,
            ) <= 1
        val attack = CorporealBeastRules.attack(inMeleeReach, random.of(100), random.of(3))
        val attackRate = CorporealBeastRules.attackRate(random.of(Int.MAX_VALUE))
        when (attack) {
            CorporealBeastAttack.Melee -> launchMelee(access, target, attackRate)
            CorporealBeastAttack.Magic -> launchMagic(access, target, attackRate, drain = false)
            CorporealBeastAttack.Drain -> launchMagic(access, target, attackRate, drain = true)
            CorporealBeastAttack.AreaMagic -> launchArea(access, target, state, attackRate)
        }
        if (
            CorporealBeastRules.coreSpawnFromAttack(
                npc.hitpoints,
                random.of(Int.MAX_VALUE),
                coreAlive(state),
            )
        ) {
            spawnCore(npc, state)
        }
    }

    private fun launchMelee(access: StandardNpcAccess, target: Player, attackRate: Int) {
        val animation =
            if (random.of(2) == 0) CorporealBeastAssets.MELEE_LEFT_ANIMATION
            else CorporealBeastAssets.MELEE_RIGHT_ANIMATION
        combat.attack(
            access,
            target,
            CombatAttack.NpcMelee(MeleeAttackType.Crush),
            NpcAttackOverride(
                attackAnimation = animationInternal(animation),
                maxHit = CorporealBeastRules.MELEE_MAX_HIT,
                attackRate = attackRate,
                applyModifierOnImpact = true,
                hitModifier = NpcPartialProtectionPrayerPlayerHitModifier(100),
            ),
        )
    }

    private fun launchMagic(
        access: StandardNpcAccess,
        target: Player,
        attackRate: Int,
        drain: Boolean,
    ) {
        val animation =
            if (drain) CorporealBeastAssets.DRAIN_ANIMATION
            else CorporealBeastAssets.MAGIC_ANIMATION
        val projectile =
            if (drain) CorporealBeastAssets.DRAIN_PROJECTILE_SPOTANIM
            else CorporealBeastAssets.MAGIC_PROJECTILE_SPOTANIM
        combat.attack(
            access,
            target,
            CombatAttack.NpcMagic(CorporealBeastRules.MAGIC_MAX_HIT),
            NpcAttackOverride(
                attackAnimation = animationInternal(animation),
                maxHit = CorporealBeastRules.MAGIC_MAX_HIT,
                projectile =
                    NpcProjectile(
                        spotanimInternal(projectile),
                        CorporealBeastAssets.TRACKING_PROJECTILE,
                    ),
                attackRate = attackRate,
                applyModifierOnImpact = true,
                hitModifier = NpcPartialProtectionPrayerPlayerHitModifier(33),
                onImpact =
                    if (drain) {
                        { npc, impact ->
                            if (impact.damage > 0) {
                                val stat = if (random.of(2) == 0) "stat.magic" else "stat.prayer"
                                target.statSub(stat, constant = random.of(1..2), percent = 0)
                                npc.heal(impact.damage / 2, showHitsplat = false)
                            }
                        }
                    } else {
                        null
                    },
            ),
        )
    }

    private fun launchArea(
        access: StandardNpcAccess,
        target: Player,
        state: CorpState,
        attackRate: Int,
    ) {
        val npc = access.npc
        access.actionDelay = clock.cycle + attackRate
        npc.facePlayer(target)
        npc.anim(CorporealBeastAssets.AREA_MAGIC_ANIMATION)
        val generation = state.generation
        val center = target.coords
        val main =
            ProjAnim.fromNpcToCoord(
                npc,
                center,
                CorporealBeastAssets.AREA_PROJECTILE_SPOTANIM,
                CorporealBeastAssets.AREA_MAIN_PROJECTILE,
            )
        world.projAnim(main)
        worldQueues.add(main.serverCycles.coerceAtLeast(1)) {
            if (!isCurrent(npc, state, generation)) return@add
            impactArea(npc, center, split = false)
            val candidates = CorporealBeastRules.splitCandidates(center)
            val start = random.of(candidates.size)
            val destinations =
                (candidates.drop(start) + candidates.take(start)).take(
                    CorporealBeastRules.AREA_SPLIT_COUNT
                )
            destinations.forEach { destination ->
                val split =
                    coordProjectile(
                        center,
                        destination,
                        CorporealBeastAssets.AREA_IMPACT_SPOTANIM,
                        CorporealBeastAssets.AREA_SPLIT_PROJECTILE,
                    )
                world.projAnim(split)
                worldQueues.add(split.serverCycles.coerceAtLeast(1)) {
                    if (isCurrent(npc, state, generation)) {
                        impactArea(npc, destination, split = true)
                    }
                }
            }
        }
    }

    private fun impactArea(npc: Npc, center: CoordGrid, split: Boolean) {
        world.spotanimMap(SpotanimType(CorporealBeastAssets.AREA_IMPACT_SPOTANIM), center)
        roomPlayers(npc).forEach { target ->
            val distance = target.coords.chebyshevDistance(center)
            if (distance > 1) return@forEach
            val maximum = CorporealBeastRules.areaMaximum(direct = distance == 0, split = split)
            val accurate = accuracy.rollMagicAccuracy(npc, target, random)
            val damage = if (accurate) random.of(maximum + 1) else 0
            target.queueHit(
                source = npc,
                delay = 1,
                type = HitType.Magic,
                damage = damage,
                modifier = NpcPartialProtectionPrayerPlayerHitModifier(33),
            )
        }
    }

    fun onBossImpact(npc: Npc, damage: Int) {
        if (npc.hitpoints <= 0) return
        val state = state(npc)
        state.lastCombatTick = clock.cycle
        if (
            CorporealBeastRules.coreSpawnFromHit(damage, random.of(Int.MAX_VALUE), coreAlive(state))
        ) {
            spawnCore(npc, state)
        }
    }

    fun idleCore(access: StandardNpcAccess) {
        access.npc.clearInteraction()
        access.actionDelay = clock.cycle + CorporealBeastRules.DARK_CORE_ATTACK_RATE_TICKS
    }

    fun coreDied(core: Npc) {
        val boss = coreOwners.remove(core) ?: return
        states[boss]?.takeIf { it.core === core }?.core = null
    }

    fun cleanup(npc: Npc) {
        val state = states.remove(npc) ?: return
        state.generation++
        removeCore(state)
    }

    fun npcDeleted(npc: Npc) {
        if (npc.id == CorporealBeastAssets.BOSS_NPC_ID) cleanup(npc) else coreDied(npc)
    }

    fun npcRespawned(npc: Npc) {
        if (npc.id != CorporealBeastAssets.BOSS_NPC_ID) return
        cleanup(npc)
        npc.copyStats(npc.type)
    }

    fun tickAll() {
        states.entries.toList().forEach { (npc, state) ->
            if (!npc.isSlotAssigned || npc.hitpoints <= 0) {
                cleanup(npc)
                return@forEach
            }
            processStomp(npc, state)
            processCore(npc, state)
            processEmptyRoomHealing(npc, state)
            processStatRestore(npc, state)
            if (CorporealBeastRules.shouldReset(clock.cycle, state.lastCombatTick)) reset(npc)
        }
    }

    private fun processStomp(npc: Npc, state: CorpState) {
        if (clock.cycle < state.nextStompTick) return
        val targets =
            roomPlayers(npc).filter { player ->
                CorporealBeastRules.isUnderFootprint(
                    npc.coords.x,
                    npc.coords.z,
                    npc.visType.size,
                    player.coords.x,
                    player.coords.z,
                )
            }
        if (targets.isEmpty()) return
        state.nextStompTick = clock.cycle + CorporealBeastRules.STOMP_INTERVAL_TICKS
        npc.anim(CorporealBeastAssets.STOMP_ANIMATION)
        targets.forEach { player ->
            player.queueHit(
                source = npc,
                delay = 1,
                type = HitType.Typeless,
                damage =
                    random.of(CorporealBeastRules.STOMP_MIN_HIT..CorporealBeastRules.STOMP_MAX_HIT),
                modifier = NpcBypassProtectionPrayerPlayerHitModifier,
            )
        }
    }

    private fun processCore(npc: Npc, state: CorpState) {
        val core = state.core ?: return
        if (!core.isSlotAssigned || core.hitpoints <= 0) {
            coreDied(core)
            return
        }
        if (clock.cycle < state.nextCoreTick || state.coreJumping) return
        state.nextCoreTick = clock.cycle + CorporealBeastRules.DARK_CORE_ATTACK_RATE_TICKS
        val roomPlayers = roomPlayers(npc)
        val adjacent =
            roomPlayers
                .filter { it.coords.chebyshevDistance(core.coords) <= 1 }
                .minByOrNull { it.slotId }
        if (adjacent != null) {
            adjacent.queueHit(
                source = core,
                delay = 1,
                type = HitType.Typeless,
                damage =
                    random.of(
                        CorporealBeastRules.DARK_CORE_MIN_HIT..CorporealBeastRules.DARK_CORE_MAX_HIT
                    ),
                modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                impactHandler =
                    HitImpactHandler { impact -> npc.heal(impact.damage, showHitsplat = false) },
            )
            return
        }
        val target =
            roomPlayers.minWithOrNull(
                compareBy<Player> { it.coords.chebyshevDistance(core.coords) }.thenBy { it.slotId }
            ) ?: return
        val generation = state.generation
        state.coreJumping = true
        val destination = target.coords
        val projectile =
            ProjAnim.fromNpcToCoord(
                core,
                destination,
                CorporealBeastAssets.DARK_CORE_JUMP_SPOTANIM,
                CorporealBeastAssets.DARK_CORE_JUMP_PROJECTILE,
            )
        world.projAnim(projectile)
        worldQueues.add(projectile.serverCycles.coerceAtLeast(1)) {
            if (isCurrent(npc, state, generation) && core.isSlotAssigned) {
                PathingEntityCommon.telejump(core, collision, destination)
                state.coreJumping = false
            }
        }
    }

    private fun processEmptyRoomHealing(npc: Npc, state: CorpState) {
        if (roomPlayers(npc).isNotEmpty() || clock.cycle < state.nextEmptyHealTick) return
        state.nextEmptyHealTick = clock.cycle + CorporealBeastRules.EMPTY_ROOM_HEAL_INTERVAL_TICKS
        npc.heal(CorporealBeastRules.EMPTY_ROOM_HEAL, showHitsplat = false)
    }

    private fun processStatRestore(npc: Npc, state: CorpState) {
        if (clock.cycle < state.nextStatRestoreTick) return
        state.nextStatRestoreTick = clock.cycle + CorporealBeastRules.STAT_RESTORE_INTERVAL_TICKS
        npc.attackLvl = stepToward(npc.attackLvl, npc.baseAttackLvl)
        npc.strengthLvl = stepToward(npc.strengthLvl, npc.baseStrengthLvl)
        npc.defenceLvl = stepToward(npc.defenceLvl, npc.baseDefenceLvl)
        npc.rangedLvl = stepToward(npc.rangedLvl, npc.baseRangedLvl)
        npc.magicLvl = stepToward(npc.magicLvl, npc.baseMagicLvl)
        npc.magicDefenceLvl = stepToward(npc.magicDefenceLvl, npc.baseMagicDefenceLvl)
    }

    private fun reset(npc: Npc) {
        cleanup(npc)
        npc.clearInteraction()
        PathingEntityCommon.telejump(npc, collision, npc.spawnCoords)
        npc.copyStats(npc.type)
        npc.defaultMode()
    }

    private fun spawnCore(npc: Npc, state: CorpState) {
        if (coreAlive(state)) return
        val type = requireNpc(CorporealBeastAssets.DARK_CORE_NPC_ID)
        val center = npc.coords.translate(npc.visType.size / 2, npc.visType.size / 2)
        val core = Npc(type, center)
        core.baseHitpointsLvl = CorporealBeastRules.DARK_CORE_HITPOINTS
        core.hitpoints = CorporealBeastRules.DARK_CORE_HITPOINTS
        core.mode = null
        core.movementLocked = true
        core.respawns = false
        core.suppressRewards = true
        npcs.add(core, DARK_CORE_LIFETIME_TICKS)
        state.core = core
        state.nextCoreTick = clock.cycle + CorporealBeastRules.DARK_CORE_ATTACK_RATE_TICKS
        coreOwners[core] = npc
    }

    private fun removeCore(state: CorpState) {
        val core = state.core ?: return
        state.core = null
        coreOwners.remove(core)
        core.clearInteraction()
        if (core.isSlotAssigned) npcs.del(core, Int.MAX_VALUE)
    }

    private fun state(npc: Npc): CorpState =
        states.getOrPut(npc) {
            CorpState(
                lastCombatTick = clock.cycle,
                nextStompTick = clock.cycle + CorporealBeastRules.STOMP_INTERVAL_TICKS,
                nextEmptyHealTick =
                    clock.cycle + CorporealBeastRules.EMPTY_ROOM_HEAL_INTERVAL_TICKS,
                nextStatRestoreTick = clock.cycle + CorporealBeastRules.STAT_RESTORE_INTERVAL_TICKS,
                nextCoreTick = clock.cycle + CorporealBeastRules.DARK_CORE_ATTACK_RATE_TICKS,
            )
        }

    private fun roomPlayers(npc: Npc): List<Player> =
        players.filter { player ->
            player.isSlotAssigned &&
                player.hitpoints > 0 &&
                CorporealBeastRules.roomMatches(npc.spawnCoords, player.coords)
        }

    private fun coreAlive(state: CorpState): Boolean =
        state.core?.let { it.isSlotAssigned && it.hitpoints > 0 } == true

    private fun isCurrent(npc: Npc, state: CorpState, generation: Int): Boolean =
        states[npc] === state &&
            state.generation == generation &&
            npc.isSlotAssigned &&
            npc.hitpoints > 0

    private fun coordProjectile(
        source: CoordGrid,
        target: CoordGrid,
        spotanim: Int,
        type: ProjAnimType,
    ): ProjAnim =
        ProjAnim(
            spotanim = spotanim,
            startHeight = type.startHeight,
            endHeight = type.endHeight,
            startTime = type.delay,
            endTime = ProjAnim.calculateEndTime(type, source.chebyshevDistance(target)),
            angle = type.angle,
            progress = type.progress,
            sourceIndex = 0,
            targetIndex = 0,
            startCoord = source,
            endCoord = target,
        )

    private fun stepToward(current: Int, base: Int): Int =
        when {
            current < base -> current + 1
            current > base -> current - 1
            else -> current
        }

    private fun animationInternal(id: Int): String = RSCM.getReverseMapping(RSCMType.SEQ, id)

    private fun spotanimInternal(id: Int): String = RSCM.getReverseMapping(RSCMType.SPOTANIM, id)

    private fun requireNpc(id: Int) =
        requireNotNull(ServerCacheManager.getNpc(id)) { "Missing revision-240 NPC definition: $id" }

    private data class CorpState(
        var lastCombatTick: Int,
        var nextStompTick: Int,
        var nextEmptyHealTick: Int,
        var nextStatRestoreTick: Int,
        var nextCoreTick: Int,
        var generation: Int = 0,
        var core: Npc? = null,
        var coreJumping: Boolean = false,
    )

    private companion object {
        const val DARK_CORE_LIFETIME_TICKS = 3_600
    }
}
