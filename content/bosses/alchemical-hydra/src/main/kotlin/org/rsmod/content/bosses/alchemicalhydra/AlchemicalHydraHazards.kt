package org.rsmod.content.bosses.alchemicalhydra

import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.abs
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.MapClock
import org.rsmod.game.hit.HitType
import org.rsmod.game.hit.HitValidity
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Only arena hazards: no ordinary attack selection, membership, respawning or phase engine. */
@Singleton
internal class AlchemicalHydraHazards
@Inject
constructor(
    private val clock: MapClock,
    private val instances: InstanceManager,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
    private val deps: BossDeps,
    private val world: WorldRepository,
) {
    fun launch(run: HydraArenaState, special: HydraSpecial) {
        when (special) {
            HydraSpecial.POISON -> launchPoison(run)
            HydraSpecial.LIGHTNING -> launchLightning(run)
            HydraSpecial.FLAME -> launchFlame(run)
        }
    }

    /** Preserve the characterized LateCycle snapshot order; these are not ordinary attacks. */
    fun process(run: HydraArenaState) {
        processPoison(run)
        processLightning(run)
        processFlame(run)
    }

    private fun launchPoison(run: HydraArenaState) {
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
            val tile = instances.hydraTile(run, x, z)
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
    }

    private fun launchLightning(run: HydraArenaState) {
        run.boss.facePlayer(run.player)
        run.boss.anim(AlchemicalHydraAssets.BLUE_LIGHTNING_ANIM)
        val center = instances.hydraCoord(run.session, HYDRA_BOSS_SPAWN).translate(3, 3)
        world.projAnim(
            ProjAnim.fromNpcToCoord(
                run.boss,
                center,
                AlchemicalHydraAssets.LIGHTNING_CENTER,
                AlchemicalHydraAssets.SPECIAL_PROJECTILE,
            )
        )
        run.pendingLightningCycle = clock.cycle + 2
    }

    private fun launchFlame(run: HydraArenaState) {
        run.boss.clearInteraction()
        run.boss.movementLocked = true
        run.boss.teleport(collision, instances.hydraCoord(run.session, HYDRA_BOSS_SPAWN))
        run.boss.facePlayer(run.player)
        run.boss.anim(AlchemicalHydraAssets.RED_FLAME_ANIM)
        val player = run.player.coords
        val bossCenter = run.boss.coords.translate(3, 3)
        val vertical = abs(player.z - bossCenter.z) >= abs(player.x - bossCenter.x)
        val walls = linkedSetOf<CoordGrid>()
        for (offset in -2..2) {
            if (vertical) {
                walls += instances.hydraTile(run, player.x - 2, player.z + offset)
                walls += instances.hydraTile(run, player.x + 2, player.z + offset)
            } else {
                walls += instances.hydraTile(run, player.x + offset, player.z - 2)
                walls += instances.hydraTile(run, player.x + offset, player.z + 2)
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
    }

    private fun processPoison(run: HydraArenaState) {
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

    private fun processLightning(run: HydraArenaState) {
        if (run.pendingLightningCycle <= clock.cycle && run.lightningCurrents.isEmpty()) {
            run.pendingLightningCycle = Int.MAX_VALUE
            listOf(28 to 23, 28 to 32, 18 to 23, 17 to 32).forEach { (x, z) ->
                run.lightningCurrents +=
                    LightningCurrent(
                        instances.hydraCoord(
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
            current.tile = instances.hydraTile(run, x, z)
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

    private fun processFlame(run: HydraArenaState) {
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
            val next = instances.hydraTile(run, x, z)
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

    private fun queueTypeless(run: HydraArenaState, damage: Int) {
        if (!run.player.isSlotAssigned || run.player.hitpoints <= 0 || !run.boss.isSlotAssigned)
            return
        val encounter = run.encounter
        run.player.queueHit(
            source = run.boss,
            delay = 1,
            type = HitType.Typeless,
            damage = AlchemicalHydraRules.empoweredDamage(damage, run.wrongVentStacks),
            modifier = NpcBypassProtectionPrayerPlayerHitModifier,
            validity =
                HitValidity {
                    instances.hydraState(run.player) === run &&
                        deps.encounterRegistry.isCurrent(run.boss, encounter) &&
                        run.player.isSlotAssigned &&
                        run.player.hitpoints > 0
                },
        )
    }

    fun processVent(run: HydraArenaState) {
        if (clock.cycle < run.nextVentCycle) return
        run.nextVentCycle = clock.cycle + AlchemicalHydraRules.VENT_INTERVAL
        val touched =
            HydraVent.entries.firstOrNull { vent ->
                val tile = instances.hydraCoord(run.session, vent.coord)
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

    fun clear(run: HydraArenaState) {
        run.pendingPoison.clear()
        run.poisonPools.clear()
        run.lightningCurrents.clear()
        run.pendingLightningCycle = Int.MAX_VALUE
        run.flameTiles.clear()
        run.trackingFlame = null
        run.fireBurnsRemaining = 0
    }
}
