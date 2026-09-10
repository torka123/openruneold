package org.rsmod.content.bosses.royaltitans

import dev.openrune.ServerCacheManager
import dev.openrune.types.NpcMode
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.route.RouteFactory
import org.rsmod.content.bosses.royaltitans.RoyalTitansDefinitions.anim
import org.rsmod.content.bosses.royaltitans.RoyalTitansDefinitions.spot
import org.rsmod.content.bosses.royaltitans.RoyalTitansRun as Run
import org.rsmod.content.bosses.royaltitans.RoyalTitansRun.ElementalState
import org.rsmod.content.bosses.royaltitans.RoyalTitansRun.PendingSummon
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Pack geometry, native travel and custom death/splash. Ordinary melee is in the elemental DSL. */
internal class RoyalTitansElementals
@Inject
constructor(
    private val clock: MapClock,
    collision: CollisionFlagMap,
    private val world: WorldRepository,
    private val random: GameRandom,
    private val players: PlayerList,
    private val interactions: AiPlayerInteractions,
    private val npcHitModifier: StandardNpcHitModifier,
    private val arena: RoyalTitansArena,
) {
    private val routes = RouteFactory(collision)
    private val bounds = BoundValidator(collision)

    fun scheduleSummon(run: Run, titan: Npc, element: RoyalElement) {
        titan.anim(
            anim(
                if (element == RoyalElement.FIRE) RoyalTitansAssets.BRANDA_SUMMON
                else RoyalTitansAssets.ELDRIC_SUMMON
            )
        )
        if (element == RoyalElement.FIRE)
            titan.spotanim(spot(RoyalTitansAssets.ELEMENTAL_SUMMON_GFX), slot = 2)
        run.pendingSummons +=
            PendingSummon(clock.cycle + RoyalTitansRules.SUMMON_DELAY_TICKS, element, titan)
    }

    fun processPendingSummons(run: Run) {
        val iterator = run.pendingSummons.iterator()
        while (iterator.hasNext()) {
            val summon = iterator.next()
            if (clock.cycle < summon.spawnCycle) continue
            iterator.remove()
            if (summon.titan !== run.branda && summon.titan !== run.eldric) continue
            if (summon.titan.hitpoints <= 0) continue
            val arenaPhase =
                run.stage in
                    setOf(
                        RoyalTitansStage.ARENA,
                        RoyalTitansStage.FINAL_BRANDA,
                        RoyalTitansStage.FINAL_ELDRIC,
                    )
            val (packX, baseZ) =
                RoyalTitansUnverifiedMovementSelection.packStart(summon.element, arenaPhase) {
                    random.of(it)
                }
            val packTarget = arena.targetFor(run, summon.titan)
            for (offset in 0..2) {
                val npc =
                    arena.spawnNpc(
                        run,
                        if (summon.element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_ELEMENTAL
                        else RoyalTitansAssets.ICE_ELEMENTAL,
                        arena.local(run, packX, baseZ + offset),
                        hitpoints = RoyalTitansRules.ELEMENTAL_HITPOINTS,
                    )
                npc.anim(
                    anim(
                        if (summon.element == RoyalElement.FIRE)
                            RoyalTitansAssets.FIRE_ELEMENTAL_SPAWN
                        else RoyalTitansAssets.ICE_ELEMENTAL_SPAWN
                    )
                )
                npc.spotanim(
                    spot(
                        if (summon.element == RoyalElement.FIRE)
                            RoyalTitansAssets.FIRE_ELEMENTAL_SPAWN_GFX
                        else RoyalTitansAssets.ICE_ELEMENTAL_SPAWN_GFX
                    ),
                    slot = 2,
                )
                run.elementals[npc] =
                    ElementalState(
                        summon.element,
                        clock.cycle + RoyalTitansRules.ELEMENTAL_LIFETIME_TICKS,
                        packTarget?.uid,
                    )
                packTarget?.let { npc.apPlayer2(it, interactions) }
            }
            arena.online(run).forEach {
                it.mes(
                    if (summon.element == RoyalElement.FIRE) "Branda summons fire elementals!"
                    else "Eldric summons ice elementals!"
                )
            }
            world.soundArea(
                arena.local(run, packX, baseZ),
                if (summon.element == RoyalElement.FIRE)
                    RoyalTitansAssets.FIRE_ELEMENTAL_SPAWN_SOUND
                else RoyalTitansAssets.ICE_ELEMENTAL_SPAWN_SOUND,
            )
        }
    }

    fun processElementals(run: Run) {
        run.elementals.toMap().forEach { (npc, state) ->
            if (!npc.isSlotAssigned || npc.hitpoints <= 0) return@forEach
            if (clock.cycle >= state.explodeCycle) {
                state.exploding = true
                npc.anim(
                    anim(
                        if (state.element == RoyalElement.FIRE)
                            RoyalTitansAssets.FIRE_ELEMENTAL_EXPLODE
                        else RoyalTitansAssets.ICE_ELEMENTAL_EXPLODE
                    )
                )
                world.spotanimMap(
                    SpotanimType(
                        if (state.element == RoyalElement.FIRE)
                            RoyalTitansAssets.FIRE_ELEMENTAL_EXPLOSION_GFX
                        else RoyalTitansAssets.ICE_ELEMENTAL_EXPLOSION_GFX
                    ),
                    npc.coords,
                )
                arena
                    .online(run)
                    .filter { arena.distance(it.coords, npc.coords) <= 1 }
                    .forEach {
                        arena.hit(
                            run,
                            it,
                            8 + random.of(3),
                            HitType.Typeless,
                            bypassPrayer = true,
                            source = npc,
                            mechanic = "royal_titans.elemental_explosion",
                        )
                    }
                npc.queueDeath()
            } else {
                val retained =
                    state.target?.resolve(players)?.takeIf {
                        arena.isPresent(run, it) && it.hitpoints > 0
                    }
                val target = retained ?: arena.targetFor(run, npc)
                state.target = target?.uid
                if (target == null) {
                    npc.clearInteraction()
                    npc.resetMovement()
                    npc.mode = NpcMode.None
                } else if (
                    bounds.touches(npc.avatar, target.avatar) ||
                        bounds.collides(npc.avatar, target.avatar)
                ) {
                    if (
                        (npc.interaction as? org.rsmod.game.interact.InteractionPlayer)?.target !=
                            target
                    ) {
                        npc.apPlayer2(target, interactions)
                    }
                    state.routeTarget = null
                } else if (
                    state.routeTarget != target.coords ||
                        npc.routeDestination.isEmpty() ||
                        npc.mode != NpcMode.None ||
                        (!npc.movementLocked && !npc.isBound && npc.coords == npc.previousCoords)
                ) {
                    // Keep native attack/reach processing when adjacent. For travel feed the
                    // shared pathfinder's full route to native movement, including wall detours.
                    npc.clearInteraction()
                    npc.mode = NpcMode.None
                    npc.facePlayer(target)
                    val route = routes.create(npc.avatar, target.avatar)
                    state.routeTarget = target.coords
                    npc.walk(route.map { CoordGrid(it.x, it.z, it.level) })
                }
            }
        }
    }

    fun onElementalKilled(run: Run, player: Player, npc: Npc) {
        if (!run.creditedElementalKills.add(npc) || !arena.isPresent(run, player)) return
        player.actionDelay =
            clock.cycle +
                RoyalTitansRules.elementalKillAttackDelay(player.actionDelay - clock.cycle)
    }

    fun splashElementals(
        run: Run,
        player: Player,
        primary: Npc,
        element: RoyalElement,
        damage: Int,
    ) {
        if (damage <= 0) return
        run.elementals.entries
            .filter {
                it.key !== primary &&
                    it.value.element == element &&
                    arena.distance(it.key.coords, primary.coords) <= 1
            }
            .forEach { (npc, _) ->
                val splash = damage.coerceAtMost(npc.hitpoints)
                npc.queueHit(
                    source = player,
                    delay = RoyalTitansRules.ELEMENTAL_SPLASH_HIT_DELAY_TICKS,
                    type = HitType.Magic,
                    damage = splash,
                    modifier = npcHitModifier,
                )
            }
    }

    fun clearElementals(run: Run) {
        run.pendingSummons.clear()
        run.elementals.keys.toList().forEach { arena.removeNpc(run, it) }
        run.elementals.clear()
    }

    fun finishElementalDeath(run: Run, npc: Npc) {
        val state = run.elementals.remove(npc) ?: return
        npc.clearInteraction()
        npc.resetMovement()
        npc.movementLocked = true
        npc.hideAllOps()
        if (state.exploding) {
            run.pendingRemovals[npc] =
                clock.cycle + if (state.element == RoyalElement.FIRE) 2 else 1
            return
        }
        npc.anim(
            anim(
                if (state.element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_ELEMENTAL_DEATH
                else RoyalTitansAssets.ICE_ELEMENTAL_DEATH
            )
        )
        npc.spotanim(
            spot(
                if (state.element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_ELEMENTAL_DEATH_GFX
                else RoyalTitansAssets.ICE_ELEMENTAL_DEATH_GFX
            )
        )
        val sequence =
            ServerCacheManager.getAnim(
                if (state.element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_ELEMENTAL_DEATH
                else RoyalTitansAssets.ICE_ELEMENTAL_DEATH
            )
        run.pendingRemovals[npc] = clock.cycle + (sequence?.tickDuration ?: 1).coerceAtLeast(1)
    }
}
