package org.rsmod.content.bosses.royaltitans

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.content.bosses.royaltitans.RoyalTitansDefinitions.anim
import org.rsmod.content.bosses.royaltitans.RoyalTitansDefinitions.requireLoc
import org.rsmod.content.bosses.royaltitans.RoyalTitansDefinitions.spot
import org.rsmod.content.bosses.royaltitans.RoyalTitansRun as Run
import org.rsmod.content.bosses.royaltitans.RoyalTitansRun.AreaAttack
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

/** Paired Blast coverage and directional stomp geometry; ordinary cadence belongs to BossSpec. */
@Singleton
internal class RoyalTitansTileAttacks
@Inject
constructor(
    private val clock: MapClock,
    private val locs: LocRepository,
    private val world: WorldRepository,
    private val random: GameRandom,
    private val arena: RoyalTitansArena,
) {
    fun start(run: Run) {
        if (run.areaAttack != null) return
        val survivor =
            when (run.stage) {
                RoyalTitansStage.FINAL_BRANDA -> RoyalElement.FIRE
                RoyalTitansStage.FINAL_ELDRIC -> RoyalElement.ICE
                else -> null
            }
        val (safeX, safeZ) = RoyalTitansRules.safeTile(survivor) { random.of(it) }
        val safeTile = arena.local(run, safeX, safeZ)
        val safeLoc =
            locs.add(
                safeTile,
                requireLoc(RoyalTitansAssets.SAFE_TILE),
                Int.MAX_VALUE,
                LocAngle.West,
                LocShape.GroundDecor,
            )
        val timer =
            arena.spawnNpc(run, RoyalTitansAssets.AREA_TIMER, safeTile, 1, movementLocked = true)
        val timerHeadbar =
            requireNotNull(
                ServerCacheManager.getHealthBar(
                    RSCM.run { RoyalTitansAssets.AREA_TIMER_HEADBAR.asRSCM(RSCMType.HEADBAR) }
                )
            )
        timer.showHeadbar(
            Headbar.fromNoSource(
                self = timerHeadbar.id,
                public = timerHeadbar.id,
                startFill = 0,
                endFill = timerHeadbar.segments.coerceIn(1, 255),
                startTime = 0,
                endTime = 210,
            )
        )
        run.branda
            ?.takeIf { it.hitpoints > 0 }
            ?.apply {
                anim(RoyalTitansDefinitions.anim(RoyalTitansAssets.BRANDA_AREA_CHARGE))
                spotanim(spot(RoyalTitansAssets.AREA_CHARGE_GFX))
            }
        run.eldric
            ?.takeIf { it.hitpoints > 0 }
            ?.apply { anim(RoyalTitansDefinitions.anim(RoyalTitansAssets.ELDRIC_AREA_CHARGE)) }
        val executeCycle = clock.cycle + RoyalTitansRules.AREA_CHARGE_TICKS
        listOfNotNull(run.branda, run.eldric).forEach {
            it.clearInteraction()
            it.actionDelay = executeCycle + 6
        }
        run.areaAttack = AreaAttack(safeX, safeZ, executeCycle, safeLoc, timer, survivor)
    }

    fun process(run: Run) {
        val attack = run.areaAttack ?: return
        val elapsed = clock.cycle - attack.impactCycle
        if (elapsed < 0) return
        if (elapsed == 0) {
            run.branda
                ?.takeIf { it.hitpoints > 0 }
                ?.apply {
                    anim(RoyalTitansDefinitions.anim(RoyalTitansAssets.BRANDA_AREA_EXECUTE))
                    spotanim(spot(RoyalTitansAssets.AREA_EXECUTE_GFX))
                }
            run.eldric
                ?.takeIf { it.hitpoints > 0 }
                ?.anim(anim(RoyalTitansAssets.ELDRIC_AREA_EXECUTE))
            // 2283 T1576/L24471-24473: one synth per pulse per viewer, not area sounds.
            arena.online(run).forEach { player ->
                for (delay in listOf(28, 88, 148)) {
                    player.soundSynth(RoyalTitansAssets.AREA_PULSE_SOUND, loops = 1, delay = delay)
                }
            }
            return
        }
        if (elapsed == 7) {
            end(run, attack)
            return
        }
        if (elapsed !in setOf(1, 3, 5)) return
        if (elapsed == 1) removeAreaTimer(run, attack)
        for (z in 28..34) {
            for (x in 29..33) {
                if (RoyalTitansRules.isSafeTile(x, z, attack.safeX, attack.safeZ)) continue
                val element = areaElement(run, attack, x, z) ?: continue
                world.spotanimMap(
                    SpotanimType(
                        if (element == RoyalElement.FIRE) RoyalTitansAssets.FIRE_AREA_PROJECTILE
                        else RoyalTitansAssets.ICE_AREA_PROJECTILE
                    ),
                    arena.local(run, x, z),
                )
                // 2283 T1613: fire coverage kills every stacked ice elemental on the tile.
                run.elementals.entries
                    .filter {
                        it.key.coords == arena.local(run, x, z) && it.value.element != element
                    }
                    .map { it.key }
                    .forEach { it.queueDeath() }
            }
        }
        arena
            .online(run)
            .filter {
                arena.inArena(run, it.coords) &&
                    areaElement(
                        run,
                        attack,
                        arena.localX(run, it.coords),
                        arena.localZ(run, it.coords),
                    ) != null
            }
            .forEach {
                arena.hit(
                    run,
                    it,
                    random.of(RoyalTitansRules.AREA_MAX_HIT + 1),
                    HitType.Typeless,
                    bypassPrayer = true,
                    mechanic = "royal_titans.elemental_blast",
                )
            }
    }

    private fun removeAreaTimer(run: Run, attack: AreaAttack) {
        if (!attack.timer.isSlotAssigned) return
        arena.removeNpc(run, attack.timer)
    }

    private fun areaElement(run: Run, attack: AreaAttack, x: Int, z: Int): RoyalElement? {
        if (RoyalTitansRules.isSafeTile(x, z, attack.safeX, attack.safeZ)) return null
        val element =
            attack.survivor
                ?: if (z > attack.safeZ || (z == attack.safeZ && x > attack.safeX))
                    RoyalElement.FIRE
                else RoyalElement.ICE
        return element.takeIf {
            (if (it == RoyalElement.FIRE) run.branda else run.eldric)?.hitpoints?.let { hp ->
                hp > 0
            } == true
        }
    }

    fun end(run: Run, attack: AreaAttack) {
        if (run.areaAttack !== attack) return
        locs.del(attack.safeLoc, Int.MAX_VALUE)
        removeAreaTimer(run, attack)
        run.areaAttack = null
        run.rangedGrace = RoyalTitansRules.AREA_GRACE_TICKS
    }

    fun stomp(
        run: Run,
        titan: Npc,
        element: RoyalElement,
        arenaWide: Boolean =
            run.stage in
                setOf(
                    RoyalTitansStage.ARENA,
                    RoyalTitansStage.FINAL_BRANDA,
                    RoyalTitansStage.FINAL_ELDRIC,
                ),
    ) {
        titan.anim(
            anim(
                if (element == RoyalElement.FIRE) RoyalTitansAssets.BRANDA_STOMP
                else RoyalTitansAssets.ELDRIC_STOMP
            )
        )
        world.soundArea(
            titan.coords,
            if (element == RoyalElement.FIRE) RoyalTitansAssets.BRANDA_STOMP_SOUND
            else RoyalTitansAssets.ELDRIC_STOMP_SOUND,
        )
        val tiles = stompTiles(run, titan, element, arenaWide)
        tiles.forEach { tile ->
            val column =
                if (arenaWide) {
                    if (element == RoyalElement.FIRE) tiles.maxOf { it.z } - tile.z
                    else tile.z - tiles.minOf { it.z }
                } else {
                    if (element == RoyalElement.FIRE) tile.x - tiles.minOf { it.x }
                    else tiles.maxOf { it.x } - tile.x
                }
            world.spotanimMap(
                SpotanimType(RoyalTitansAssets.STOMP_TILE_GFX),
                tile,
                delay = column * 2,
            )
        }
        world.soundArea(
            titan.coords,
            if (element == RoyalElement.FIRE) RoyalTitansAssets.BRANDA_STOMP_IMPACT_SOUND
            else RoyalTitansAssets.ELDRIC_STOMP_IMPACT_SOUND,
            delay = 1,
        )
        val final = run.branda == null || run.eldric == null
        arena
            .online(run)
            .filter { it.coords in tiles }
            .forEach { player ->
                val protected = player.vars["varbit.prayer_protectfrommelee"] == 1
                val roll = random.of(RoyalTitansRules.STOMP_MAX_HIT + 1)
                val damage =
                    if (!final && protected) 0
                    else RoyalTitansRules.finalStompDamage(roll, protected)
                arena.hit(
                    run,
                    player,
                    damage,
                    HitType.Melee,
                    bypassPrayer = true,
                    source = titan,
                    mechanic = "royal_titans.stomp",
                )
            }
    }

    private fun stompTiles(
        run: Run,
        titan: Npc,
        element: RoyalElement,
        arenaWide: Boolean,
    ): List<CoordGrid> =
        if (arenaWide) {
            (29..33).flatMap { x -> (28..34).map { z -> arena.local(run, x, z) } }
        } else {
            val zRange = titan.coords.z..(titan.coords.z + 2)
            val xRange =
                if (element == RoyalElement.FIRE) (titan.coords.x + 3)..(titan.coords.x + 7)
                else (titan.coords.x - 5)..(titan.coords.x - 1)
            xRange.flatMap { x -> zRange.map { z -> CoordGrid(x, z, titan.coords.level) } }
        }
}
