package org.rsmod.content.bosses.grotesqueguardians

import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.hit.HitValidity
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.loc.LocLayerConstants

/** Spatial specials only. Existing BossEncounter scopes own every native delayed callback. */
internal class GuardiansArenaEffects
@Inject
constructor(
    private val clock: MapClock,
    private val instances: InstanceManager,
    private val random: GameRandom,
    private val deps: BossDeps,
    private val worldQueues: WorldQueueList,
    private val world: WorldRepository,
) {
    fun launch(run: GuardiansArenaState, handler: String) {
        when (handler) {
            "stone" -> launchStoneBall(run)
            "explosion" -> launchBlindingExplosion(run)
            "debris" -> if (clock.cycle >= run.nextDebrisCycle) launchDebris(run)
            "prison" -> launchPrison(run)
            else -> error("Unknown Guardians arena handler: $handler")
        }
    }

    fun clear(run: GuardiansArenaState) {
        clearSpheres(run)
        run.lightning.clear()
    }

    fun showLightning(run: GuardiansArenaState, tile: CoordGrid) {
        world.spotanimMapPrivate(run.player, SpotanimType(1416), tile)
    }

    private fun isCurrent(run: GuardiansArenaState): Boolean =
        instances.guardiansState(run.player) === run &&
            run.player.isSlotAssigned &&
            run.player.hitpoints > 0 &&
            deps.encounterRegistry.isCurrent(run.dawn, run.dawnEncounter) &&
            deps.encounterRegistry.isCurrent(run.dusk, run.duskEncounter) &&
            run.phase !in
                setOf(
                    GrotesquePhase.DORMANT,
                    GrotesquePhase.INVALIDATED,
                    GrotesquePhase.DYING,
                    GrotesquePhase.RESPAWNING,
                )

    private fun launchStoneBall(run: GuardiansArenaState) {
        run.dawn.anim(GrotesqueAssets.DAWN_SPHERE_ANIMATION)
        val tile = run.player.coords
        world.spotanimMapPrivate(run.player, SpotanimType(GrotesqueAssets.DEBRIS_SHADOW), tile)
        val phase = run.phase
        val phaseTick = run.duskEncounter.phaseEnteredTick
        run.dawnEncounter.schedule(worldQueues, 3) {
            if (
                !(isCurrent(run) &&
                    run.phase == phase &&
                    run.duskEncounter.phaseEnteredTick == phaseTick)
            )
                return@schedule
            world.spotanimMapPrivate(run.player, SpotanimType(GrotesqueAssets.DEBRIS_IMPACT), tile)
            if (
                GrotesqueGuardiansRules.inDebrisArea(
                    run.player.coords.x,
                    run.player.coords.z,
                    tile.x,
                    tile.z,
                )
            ) {
                run.player.queueHit(
                    source = run.dawn,
                    delay = 1,
                    type = HitType.Typeless,
                    damage = random.of(16),
                    modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                    validity = HitValidity { isCurrent(run) },
                )
                if (
                    GrotesqueGuardiansRules.directlyUnderDebris(
                        run.player.coords.x,
                        run.player.coords.z,
                        tile.x,
                        tile.z,
                    )
                ) {
                    run.player.actionDelay =
                        maxOf(
                            run.player.actionDelay,
                            clock.cycle + GrotesqueGuardiansRules.DEBRIS_STUN_TICKS,
                        )
                    run.player.mes("You have been trapped in stone!")
                }
            }
        }
    }

    private fun launchBlindingExplosion(run: GuardiansArenaState) {
        run.dusk.anim(GrotesqueAssets.DUSK_EXPLOSION_ANIMATION)
        val phase = run.phase
        val phaseTick = run.duskEncounter.phaseEnteredTick
        run.duskEncounter.schedule(worldQueues, 2) {
            if (
                !(isCurrent(run) &&
                    run.phase == phase &&
                    run.duskEncounter.phaseEnteredTick == phaseTick) ||
                    distanceToFootprint(run.dusk, run.player) > 1
            )
                return@schedule
            run.player.queueHit(
                source = run.dusk,
                delay = 1,
                type = HitType.Typeless,
                damage = 20 + random.of(14),
                modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                validity = HitValidity { isCurrent(run) },
            )
        }
    }

    private fun launchDebris(run: GuardiansArenaState) {
        run.nextDebrisCycle = clock.cycle + 25
        val phase = run.phase
        val phaseTick = run.duskEncounter.phaseEnteredTick
        repeat(3) { wave ->
            run.duskEncounter.schedule(worldQueues, wave) {
                if (
                    !(isCurrent(run) &&
                        run.phase == phase &&
                        run.duskEncounter.phaseEnteredTick == phaseTick)
                )
                    return@schedule
                repeat(3) {
                    val tile = randomArenaTile(run)
                    world.spotanimMapPrivate(
                        run.player,
                        SpotanimType(GrotesqueAssets.DEBRIS_SHADOW),
                        tile,
                    )
                    run.duskEncounter.schedule(worldQueues, 3) {
                        if (
                            !(isCurrent(run) &&
                                run.phase == phase &&
                                run.duskEncounter.phaseEnteredTick == phaseTick)
                        )
                            return@schedule
                        world.spotanimMapPrivate(
                            run.player,
                            SpotanimType(GrotesqueAssets.DEBRIS_IMPACT),
                            tile,
                        )
                        if (
                            GrotesqueGuardiansRules.inDebrisArea(
                                run.player.coords.x,
                                run.player.coords.z,
                                tile.x,
                                tile.z,
                            )
                        ) {
                            run.player.queueHit(
                                source = run.dawn,
                                delay = 1,
                                type = HitType.Typeless,
                                damage = 15 + random.of(8),
                                modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                                validity = HitValidity { isCurrent(run) },
                            )
                            if (
                                GrotesqueGuardiansRules.directlyUnderDebris(
                                    run.player.coords.x,
                                    run.player.coords.z,
                                    tile.x,
                                    tile.z,
                                )
                            ) {
                                run.player.actionDelay =
                                    maxOf(
                                        run.player.actionDelay,
                                        clock.cycle + GrotesqueGuardiansRules.DEBRIS_STUN_TICKS,
                                    )
                            }
                        }
                    }
                }
            }
        }
    }

    fun spawnSpheres(run: GuardiansArenaState) {
        clearSpheres(run)
        GrotesqueAssets.ENERGY_SPHERES.forEach { id ->
            val tile = randomArenaTile(run)
            val loc =
                LocInfo(
                    LocLayerConstants.of(LocShape.CentrepieceStraight.id),
                    tile,
                    LocEntity(id, LocShape.CentrepieceStraight.id, LocAngle.North.id),
                )
            world.locAddPrivate(run.player, loc)
            run.spheres += EnergySphere(loc, clock.cycle)
        }
    }

    fun absorbSphere(player: Player, coords: CoordGrid): Boolean {
        val run = instances.guardiansState(player) ?: return false
        val sphere = run.spheres.firstOrNull { it.loc.coords == coords } ?: return false
        world.locDelPrivate(player, sphere.loc)
        run.spheres.remove(sphere)
        player.mes("You absorb the energy sphere.")
        return true
    }

    private fun launchPrison(run: GuardiansArenaState) {
        run.dusk.anim(GrotesqueAssets.DUSK_PRISON_ANIMATION)
        val centre = run.player.coords
        val safe = CoordGrid(centre.x + 2, centre.z, centre.level)
        val flames = linkedSetOf<CoordGrid>()
        for (dx in -2..2) {
            for (dz in -2..2) {
                if (kotlin.math.abs(dx) != 2 && kotlin.math.abs(dz) != 2) continue
                val tile = CoordGrid(centre.x + dx, centre.z + dz, centre.level)
                if (tile != safe) flames += tile
            }
        }
        flames.forEach {
            world.spotanimMapPrivate(run.player, SpotanimType(GrotesqueAssets.PRISON_FLAME), it)
        }
        val phase = run.phase
        val phaseTick = run.duskEncounter.phaseEnteredTick
        run.duskEncounter.schedule(worldQueues, GrotesqueGuardiansRules.PRISON_ESCAPE_TICKS) {
            if (
                !(isCurrent(run) &&
                    run.phase == phase &&
                    run.duskEncounter.phaseEnteredTick == phaseTick)
            )
                return@schedule
            if (run.player.coords.chebyshevDistance(centre) <= 1) {
                val damage = 45 + random.of(21)
                run.player.queueHit(
                    source = run.dusk,
                    delay = 1,
                    type = HitType.Typeless,
                    damage = damage,
                    modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                    validity = HitValidity { isCurrent(run) },
                )
                run.dusk.hitpoints =
                    GrotesqueGuardiansRules.healAfterPrisonDamage(run.dusk.hitpoints, damage)
            }
        }
    }

    fun tickLightning(run: GuardiansArenaState) {
        val tile = run.player.coords
        if (
            run.lightning.any {
                clock.cycle in it.activeCycle..it.endCycle &&
                    GrotesqueGuardiansRules.inLightningArea(tile.x, tile.z, it.tile.x, it.tile.z)
            }
        ) {
            run.player.queueHit(
                source = run.dusk,
                delay = 1,
                type = HitType.Typeless,
                damage =
                    random.of(
                        GrotesqueGuardiansRules.LIGHTNING_MIN_HIT..GrotesqueGuardiansRules
                                .LIGHTNING_MAX_HIT
                    ),
                modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                validity = HitValidity { isCurrent(run) },
            )
        }
    }

    fun tickSpheres(run: GuardiansArenaState) {
        val returning =
            run.spheres.filter {
                GrotesqueGuardiansRules.sphereState(clock.cycle - it.spawnCycle) ==
                    SphereState.RETURNING
            }
        returning.forEach { sphere ->
            world.locDelPrivate(run.player, sphere.loc)
            run.spheres.remove(sphere)
            run.dawn.hitpoints =
                (run.dawn.hitpoints + GrotesqueGuardiansRules.SPHERE_HEAL).coerceAtMost(
                    GrotesqueGuardiansRules.HITPOINTS
                )
        }
    }

    fun clearSpheres(run: GuardiansArenaState) {
        run.spheres.toList().forEach { world.locDelPrivate(run.player, it.loc) }
        run.spheres.clear()
    }

    private fun localCoord(session: InstanceSession, x: Int, z: Int): CoordGrid =
        requireNotNull(
            instances.localCoord(
                session,
                RegionLocal(
                    GROTESQUE_SOURCE_LEVEL,
                    GROTESQUE_SOURCE_MAP_X,
                    GROTESQUE_SOURCE_MAP_Z,
                    x,
                    z,
                ),
            )
        )

    fun randomArenaTile(run: GuardiansArenaState): CoordGrid =
        localCoord(
            run.session,
            random.of(GROTESQUE_ARENA_MIN_X..GROTESQUE_ARENA_MAX_X),
            random.of(GROTESQUE_ARENA_MIN_Z..GROTESQUE_ARENA_MAX_Z),
        )

    private fun distanceToFootprint(npc: Npc, player: Player): Int =
        if (npc.coords.level == player.coords.level) npc.distanceTo(player) else Int.MAX_VALUE
}
