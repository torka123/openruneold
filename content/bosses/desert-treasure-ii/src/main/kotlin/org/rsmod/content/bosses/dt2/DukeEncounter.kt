package org.rsmod.content.bosses.dt2

import dev.openrune.ServerCacheManager
import dev.openrune.types.NpcMode
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import org.rsmod.api.bosses.runtime.EncounterRegistry
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.magic.ArceuusSpellState
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.npc.access.StandardNpcAccessLauncher
import org.rsmod.api.npc.events.interact.AiPlayerEvents
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.queueImpactHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.content.bosses.dt2.Dt2EncounterManager.Run
import org.rsmod.content.skills.mining.scripts.Mining
import org.rsmod.content.skills.mining.scripts.Mining.Companion.pickaxeAnim
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.hit.HitValidity
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Duke dispatches its native combat owner at StartCycle; pursuit never drives this encounter. */
internal class DukeEncounter
@Inject
constructor(
    private val clock: MapClock,
    private val npcs: NpcRepository,
    private val instances: InstanceManager,
    private val world: WorldRepository,
    private val locs: LocRepository,
    private val collision: CollisionFlagMap,
    private val accuracy: AccuracyFormulae,
    private val bossEncounters: EncounterRegistry,
    private val accessLauncher: StandardNpcAccessLauncher,
    private val eventBus: EventBus,
) {
    fun configureNpc(npc: Npc, awakened: Boolean) {
        npc.respawns = false
        npc.mode = NpcMode.None
        npc.ignoreCombatInteractions = true
        npc.setHunt(0)
        npc.baseAttackLvl = if (awakened) 435 else 300
        npc.attackLvl = npc.baseAttackLvl
        npc.baseStrengthLvl = if (awakened) 500 else 345
        npc.strengthLvl = npc.baseStrengthLvl
        npc.baseDefenceLvl = if (awakened) 316 else 275
        npc.defenceLvl = npc.baseDefenceLvl
        npc.baseMagicLvl = if (awakened) 465 else 310
        npc.magicLvl = npc.baseMagicLvl
        npc.baseMagicDefenceLvl = npc.baseMagicLvl
        npc.magicDefenceLvl = npc.baseMagicDefenceLvl
        npc.combatImmunities.statDrain = awakened
        npc.combatImmunities.slayerTaskBonuses = awakened
    }

    fun prepare(run: Run, preserveVats: Boolean = false) {
        val vats = if (preserveVats) run.duke.preparation.vats.toMap() else emptyMap()
        clear(run)
        run.duke = DukeState(clock.cycle)
        run.boss.actionDelay = Int.MAX_VALUE
        run.duke.preparation.vats.putAll(vats)
        run.boss.mode = NpcMode.None
        run.boss.ignoreCombatInteractions = true
        run.boss.setHunt(0)
        run.boss.movementLocked = true
        run.boss.transmog(
            requireNotNull(ServerCacheManager.getNpc(DukeRules.ASLEEP)),
            Int.MAX_VALUE,
        )
        run.boss.anim(if (preserveVats) DukeRules.RESET else DukeRules.SLEEP)
        // These are the dynamic object changes in 1703:172266-172274 and 3285:303541-303547.
        scene(run, 47560, 3034, 6434, 1)
        scene(run, 47561, 3044, 6434, 1)
        scene(run, 47522, 3033, 6448, 2)
        scene(run, 47522, 3043, 6448, 0)
        scene(run, 47524, 3048, 6453, 0)
        scene(run, 47528, 3030, 6453, 0)
        for (x in listOf(3034, 3043)) refreshVat(run, x to 6438)
        eyes(run, dead = false)
        scene(run, 47565, 3039, 6433, 3, shape = 0)
    }

    fun tick(run: Run) {
        if (!present(run)) return
        run.boss.mode = NpcMode.None
        if (valid(run)) run.boss.ignoreCombatInteractions = true
        val state = run.duke
        if (valid(run)) pulsePools(run)
        state.timeline.pulse(clock.cycle, run.generation)
        if (!valid(run)) return
        if (run.state == Dt2State.PREPARING) {
            prepHazards(run)
            return
        }
        if (run.state == Dt2State.STARTING && clock.cycle >= run.startReadyCycle) {
            run.state = Dt2State.ACTIVE
            run.killStartCycle = clock.cycle
            run.boss.actionDelay = clock.cycle
        }
        if (run.state !in setOf(Dt2State.ACTIVE, Dt2State.ENRAGED)) return
        run.enraged = DukeRules.enraged(run.boss.hitpoints, awakened(run))
        run.state = if (run.enraged) Dt2State.ENRAGED else Dt2State.ACTIVE
        syncCombatPhase(run)
        if (clock.cycle >= run.boss.actionDelay && !run.boss.isBusy) {
            // This is the engine's native AI event route, invoked before player movement.
            // None mode and hunt=0 prevent a second pursuit-driven opportunity later in the cycle.
            accessLauncher.launch(run.boss) {
                eventBus.publish(this, AiPlayerEvents.Ap2(run.player, run.boss))
            }
        }
        if (awakened(run) && run.boss.hitpoints <= 543 && clock.cycle >= state.nextOrb) orb(run)
    }

    private fun syncCombatPhase(run: Run) {
        val encounter = bossEncounters.of(run.boss)
        if (encounter.currentPhaseName != DUKE_INACTIVE) return
        encounter.transitionTo(dukeInitialPhase(awakened(run)), clock.cycle)
        encounter.lastAbilityTick =
            maxOf(clock.cycle, run.boss.actionDelay) - encounter.expectedAttackRate()
    }

    fun prepareOpportunity(run: Run): Boolean {
        if (
            !valid(run) ||
                run.boss.hitpoints <= 0 ||
                run.boss.isBusy ||
                run.state !in setOf(Dt2State.ACTIVE, Dt2State.ENRAGED) ||
                clock.cycle < run.boss.actionDelay
        )
            return false
        val encounter = bossEncounters.of(run.boss)
        if (clock.cycle - encounter.lastAbilityTick < encounter.expectedAttackRate()) return false
        return true
    }

    fun launchAction(run: Run, action: DukeAction) {
        when (action) {
            DukeAction.STANDARD -> standard(run)
            DukeAction.GAZE -> gaze(run)
            DukeAction.GAS -> gas(run)
        }
        if (action == DukeAction.STANDARD) run.attackCounter++
    }

    private fun standard(run: Run) {
        val (x, z) = source(run, run.player.coords)
        if (DukeRules.meleeRange(x, z)) {
            run.boss.anim(DukeRules.MELEE)
            run.boss.spotanim(2439)
            DukeRules.icicleTiles.zip(DukeRules.icicleGraphics).forEach { (tile, graphic) ->
                graphic(run, graphic, tile)
            }
            world.soundArea(coord(run, 3039, 6452), 2707, radius = 10)
            val hit =
                run.rng.nextInt(10000) <
                    accuracy.getMeleeHitChance(run.boss, run.player, MeleeAttackType.Crush)
            if (DukeRules.icicleArea(x, z)) {
                val damage = if (hit) run.rng.nextInt(12) else 0
                impact(
                    run,
                    DukeRules.icicleDamage(damage, prayer(run, false)),
                    HitType.Melee,
                    avoidable = false,
                    ward = !prayer(run, false),
                )
            }
            schedule(run, DukeRules.SLAM_TICKS) {
                val tile = source(run, run.player.coords)
                if (DukeRules.slamArea(tile.first, tile.second)) {
                    val rolled = run.rng.nextInt(if (awakened(run)) 82 else 57)
                    impact(
                        run,
                        DukeRules.slamDamage(rolled, prayer(run, false)),
                        HitType.Melee,
                        ward = true,
                    )
                }
            }
        } else {
            run.boss.anim(DukeRules.MAGIC)
            projectile(run, 2434, run.player.coords, 50, 120, 350, 100, track = true)
            schedule(run, DukeRules.MAGIC_HIT_TICKS) {
                val rolled = run.rng.nextInt(if (awakened(run)) 72 else 49)
                run.player.spotanim(2435)
                world.soundArea(run.player.coords, 7197)
                impact(
                    run,
                    DukeRules.magicDamage(rolled, prayer(run, true)),
                    HitType.Magic,
                    ward = true,
                )
            }
        }
    }

    private fun gaze(run: Run) {
        run.boss.anim(DukeRules.GAZE)
        run.player.mes("<col=a53fff>Duke Sucellus turns his gaze upon you...")
        run.boss.actionDelay = clock.cycle + DukeRules.GAZE_TICKS
        schedule(run, DukeRules.GAZE_CHECK_TICKS) {
            val (x, z) = source(run, run.player.coords)
            if (DukeRules.pillarProtected(x, z))
                run.player.mes("<col=229628>You manage to avoid Duke Sucellus' gaze.")
            else {
                CombatEffects.freeze(run.player, 5)
                impact(run, run.rng.nextInt(if (awakened(run)) 122 else 102))
            }
        }
    }

    private fun gas(run: Run) {
        val count = DukeRules.gasCount(run.boss.hitpoints, awakened(run))
        val offsets = DukeRules.gasOffsets(count)
        run.boss.actionDelay =
            clock.cycle +
                if (count == 1) DukeRules.rate(run.boss.hitpoints, awakened(run))
                else offsets.last() + if (run.enraged) 3 else 4
        offsets.forEach { offset ->
            if (offset == 0) spit(run) else schedule(run, offset) { spit(run) }
        }
        if (awakened(run) && run.boss.hitpoints <= 1103 && clock.cycle >= run.duke.nextBile) {
            // Never overlap leaks. Further post-gas selection rules need a longer current capture.
            val delay = run.boss.actionDelay - clock.cycle
            schedule(run, delay) { bile(run) }
        }
    }

    private fun spit(run: Run) {
        run.boss.anim(DukeRules.SPIT)
        val (x, z) = source(run, run.player.coords)
        val target = DukeRules.nearestVent(x, z)
        projectile(run, 2436, coord(run, target.first, target.second), 20, 90, 350, 0)
        schedule(run, 2) { vent(run, target, preparation = false, delay = 30) }
    }

    private fun vent(run: Run, centre: Pair<Int, Int>, preparation: Boolean, delay: Int = 0) {
        val npc = helper(run, DukeRules.VENT, centre.first - 1, centre.second - 1)
        npc.spotanim(2431, delay = delay)
        // 3285:314120/314209: spawn, then idle two ticks later; idle repeats every two ticks.
        for (offset in 2..10 step 2) schedule(run, offset) {
            npc.spotanim(2432)
            world.soundArea(coord(run, centre.first, centre.second), 3052, radius = 3)
            val (x, z) = source(run, run.player.coords)
            if (DukeRules.ventArea(x, z, centre)) {
                val maxHit = if (!preparation && awakened(run)) 27 else 12
                val faceProtected = faceProtection(run.player)
                impact(run, run.rng.nextInt((if (faceProtected) maxHit / 2 else maxHit) + 1))
                if (preparation) run.player.statSub("stat.prayer", 4, 0)
            }
        }
        schedule(run, 12) { npc.spotanim(2433) }
        schedule(run, 14) { remove(run, npc) }
    }

    private fun prepHazards(run: Run) {
        val elapsed = clock.cycle - run.duke.prepStart
        if (elapsed >= run.duke.nextExtremities) {
            run.duke.nextExtremities += 15
            DukeRules.extremityRows.forEachIndexed { index, row ->
                val spawn = {
                    for (west in listOf(true, false)) {
                        val id =
                            (if (west) DukeRules.westExtremities else DukeRules.eastExtremities)[
                                index]
                        val npc = helper(run, id, if (west) 3028 else 3046, row)
                        npc.faceSquare(coord(run, if (west) 3040 else 3037, row + 2))
                        val eye =
                            locInfo(
                                run,
                                47544,
                                if (west) 3028 else 3049,
                                row + 1,
                                if (west) 3 else 1,
                            )
                        world.locAnim(eye, 10187 + (id - 12199))
                        for (tick in 1..3) schedule(run, tick) {
                            val (x, z) = source(run, run.player.coords)
                            if (DukeRules.coneArea(x, z, row, west) && !run.player.isBound) {
                                CombatEffects.freeze(run.player, 5)
                                run.player.statSub("stat.prayer", 0, 25)
                                impact(run, run.rng.nextInt(71))
                            }
                        }
                        schedule(run, 4) { remove(run, npc) }
                    }
                }
                if (index == 0) spawn() else schedule(run, index * 2, spawn)
            }
        }
        if (elapsed >= run.duke.nextVents) {
            DukeRules.prepVents(run.duke.ventStep++).forEach { vent(run, it, preparation = true) }
            run.duke.nextVents += 15
        }
        // Ice is random in OSRS, but only in the two captured corridors, never the main room.
        if (elapsed >= run.duke.nextIce) {
            run.duke.nextIce += 6
            val candidates = DukeRules.iceTiles.toMutableList()
            repeat(3) {
                val tile = candidates.removeAt(run.rng.nextInt(candidates.size))
                graphic(run, 1447, tile)
                schedule(run, 4) {
                    graphic(run, 538, tile)
                    if (source(run, run.player.coords) == tile) {
                        CombatEffects.freeze(run.player, 2)
                        impact(run, run.rng.nextInt(19))
                    }
                }
            }
        }
    }

    private fun bile(run: Run) {
        if (!awakened(run)) return
        run.boss.actionDelay = clock.cycle + DukeRules.rate(run.boss.hitpoints, true)
        run.duke.nextBile = clock.cycle + DukeRules.BILE_TICKS
        run.boss.anim(DukeRules.SPIT)
        run.player.mes(
            "Duke Sucellus covers you in bile. It slowly drips off your body and onto the floor."
        )
        projectile(run, 1354, run.player.coords, 32, 51, 130, 160, track = true, angle = 0)
        for (offset in 1 until 1 + DukeRules.BILE_TICKS) schedule(run, offset) {
            val tile = run.player.coords
            pool(run, tile)
            // StartCycle still contains the preceding movement's intermediate running tile.
            val previous = run.player.previousCoords
            val middle = run.player.lastProcessedCoord
            if (
                offset > 1 &&
                    middle != previous &&
                    middle != tile &&
                    middle.level == tile.level &&
                    middle.chebyshevDistance(tile) == 1 &&
                    middle.chebyshevDistance(previous) == 1
            )
                pool(run, middle)
        }
    }

    private fun pool(run: Run, tile: CoordGrid) {
        // Standing on a pool does not continually renew its lifetime (555:317662-317978).
        if (tile in run.duke.pools) return
        run.duke.poisonTiles[tile] = clock.cycle + DukeRules.POOL_TICKS
        val pool = LocInfo(2, tile, LocEntity(30032, 10, run.rng.nextInt(4)))
        locs.add(pool, Int.MAX_VALUE)
        run.duke.pools[tile] = pool
    }

    private fun pulsePools(run: Run) {
        val expired = run.duke.poisonTiles.filterValues { it <= clock.cycle }.keys
        expired.forEach { tile ->
            run.duke.poisonTiles.remove(tile)
            run.duke.pools.remove(tile)?.let { locs.del(it, Int.MAX_VALUE) }
        }
        // Test existing pools before this tick's new drip; a moving player can leave it safely.
        if (run.duke.poisonTiles.containsKey(run.player.coords)) {
            impact(run, 9 + run.rng.nextInt(4))
            CombatEffects.poison(run.player, 2)
        }
    }

    private fun orb(run: Run) {
        run.duke.nextOrb = clock.cycle + 17
        val east = run.duke.orbEast
        run.duke.orbEast = !east
        val start = if (east) 3044 else 3034
        graphic(run, 382, start to 6451)
        schedule(run, 3) {
            val npc = helper(run, DukeRules.ORB, start, 6451)
            graphic(run, 379, start to 6451)
            world.soundArea(npc.coords, 6545, radius = 4)
            for (step in 1..10) schedule(run, step) {
                if (npc in run.helpers) {
                    npc.teleport(collision, coord(run, start + if (east) -step else step, 6451))
                    if (npc.coords == run.player.coords) {
                        impact(run, run.rng.nextInt(28))
                        // Hitpoints are handled by the hit; drain the remaining released skills.
                        val stats =
                            ServerCacheManager.getStats()
                                .values
                                .filterNot { it.unreleased || it.isType("stat.hitpoints") }
                                .map { it.internalName }
                        CombatEffects.statDrain(run.player, stats, 8)
                        remove(run, npc)
                    }
                }
            }
            schedule(run, 11) { remove(run, npc) }
        }
    }

    suspend fun gather(run: Run, access: ProtectedAccess, locId: Int, tile: CoordGrid) {
        val generation = run.generation
        val player = run.player
        val salt = locId == 47522
        while (
            valid(run) &&
                generation == run.generation &&
                run.state == Dt2State.PREPARING &&
                run.duke.scene[tile]?.id == locId
        ) {
            val item =
                if (salt) Dt2Assets.SALAX_SALT
                else if (locId == 47524) Dt2Assets.MUSCA_MUSHROOM else Dt2Assets.ARDER_MUSHROOM
            val delay =
                if (salt) {
                    val pickaxe = Mining.findPickaxe(player)
                    if (pickaxe == null) {
                        player.mes("You need a pickaxe you can use to mine the salt.")
                        return
                    }
                    access.anim(
                        requireNotNull(ServerCacheManager.getItem(pickaxe.id)).pickaxeAnim.id
                    )
                    Mining.pickaxeActionDelay(
                        pickaxe,
                        DefaultGameRandom(run.rng.nextInt(Int.MAX_VALUE).toLong()),
                    )
                } else {
                    access.anim(2282)
                    3
                }
            access.delay(delay.coerceAtLeast(1))
            if (!valid(run) || generation != run.generation || run.state != Dt2State.PREPARING)
                return
            val count = if (salt) DukeRules.saltYield(player.stat("stat.mining")) else 1
            if (!player.invAdd(player.inv, item, count).success) return
            if (!salt) {
                val picked = (run.duke.preparation.mushroomsPicked[locId] ?: 0) + 1
                run.duke.preparation.mushroomsPicked[locId] = picked
                if (picked >= 8) {
                    val (x, z) = source(run, tile)
                    scene(run, locId + 1, x, z, 0)
                    access.resetAnim()
                    return
                }
            }
        }
    }

    fun inspectVat(run: Run, tile: CoordGrid, empty: Boolean) {
        if (!valid(run) || run.state != Dt2State.PREPARING) return
        val source = source(run, tile)
        if (source !in setOf(3034 to 6438, 3043 to 6438)) return
        if (empty) {
            run.duke.preparation.vats.remove(source)
            refreshVat(run, source)
        } else {
            val vat = run.duke.preparation.vats[source]
            run.player.mes(
                if (vat == null) "The vat is empty."
                else if (clock.cycle < vat.ready) "The poison is still fermenting."
                else "The vat contains ${vat.doses} doses of poison."
            )
        }
    }

    private fun refreshVat(run: Run, tile: Pair<Int, Int>) {
        val vat = run.duke.preparation.vats[tile]
        val id = if (vat == null) 47536 else if (clock.cycle < vat.ready) 47537 else 47539
        scene(run, id, tile.first, tile.second, if (tile.first == 3034) 3 else 1)
        if (vat != null && clock.cycle < vat.ready)
            schedule(run, vat.ready - clock.cycle) { refreshVat(run, tile) }
    }

    fun beginDeath(run: Run) {
        val generation = run.generation
        run.boss.transmog(requireNotNull(ServerCacheManager.getNpc(12192)), Int.MAX_VALUE)
        run.duke.timeline.at(clock.cycle + 2, generation) {
            if (present(run) && run.generation == generation) eyes(run, dead = true)
        }
        run.duke.timeline.at(clock.cycle + 4, generation) {
            if (present(run) && run.generation == generation) {
                scene(run, 47567, 3034, 6434, 1)
                scene(run, 47568, 3044, 6434, 1)
                scene(run, 47523, 3033, 6448, 2)
                scene(run, 47523, 3043, 6448, 0)
                scene(run, 47532, 3048, 6453, 0)
                scene(run, 47534, 3030, 6453, 0)
                for (x in listOf(3034, 3043)) scene(run, 47537, x, 6438, if (x == 3034) 3 else 1)
                scene(run, 47566, 3039, 6433, 3, shape = 0)
            }
        }
    }

    fun showCorpse(run: Run) {
        helper(run, 12192, 3036, 6452).anim(10183)
    }

    private fun eyes(run: Run, dead: Boolean) {
        for (west in listOf(true, false)) DukeRules.extremityRows.forEachIndexed { index, row ->
            val id = (if (west) DukeRules.westExtremities else DukeRules.eastExtremities)[index]
            scene(
                run,
                (if (dead) 47550 else 47544) + id - 12199,
                if (west) 3028 else 3049,
                row + 1,
                if (west) 3 else 1,
            )
        }
    }

    fun ingredient(run: Run, player: Player, locId: Int, tile: CoordGrid) {
        if (!valid(run) || run.state != Dt2State.PREPARING) return
        val source = source(run, tile)
        if (run.duke.scene[tile]?.id != locId) return
        val item =
            when (locId) {
                47560 -> Dt2Assets.PESTLE_AND_MORTAR
                47561 -> Dt2Assets.IRON_PICKAXE
                else -> null
            }
        if (item != null) {
            if (player.invAdd(player.inv, item, 1).success) player.anim(832)
            return
        }
        if (locId !in 47536..47543 || source !in setOf(3034 to 6438, 3043 to 6438)) return
        val vat = run.duke.preparation.vats[source]
        if (vat != null) {
            if (clock.cycle < vat.ready) {
                player.mes("The poison is still fermenting.")
                return
            }
            if (player.invAdd(player.inv, Dt2Assets.ARDER_MUSCA_POISON, vat.doses).success) {
                run.duke.preparation.vats.remove(source)
                scene(run, 47536, source.first, source.second, if (source.first == 3034) 3 else 1)
            }
            return
        }
        val items = listOf(Dt2Assets.ARDER_POWDER, Dt2Assets.MUSCA_POWDER, Dt2Assets.SALAX_SALT)
        val doses =
            items.minOf {
                player.inv.count(requireNotNull(ServerCacheManager.getItem(it)).internalName) / 6
            }
        if (doses == 0) {
            player.mes("You need six arder powder, six musca powder and six salax salt per poison.")
            return
        }
        val result =
            player.invTransaction(player.inv) {
                val inventory = select(player.inv)
                items.forEach { id ->
                    delete {
                        from = inventory
                        obj = id
                        strictCount = doses * 6
                    }
                }
            }
        if (result.success) {
            run.duke.preparation.vats[source] =
                DukeVat(doses, clock.cycle + DukeRules.FERMENT_TICKS)
            scene(run, 47537, source.first, source.second, if (source.first == 3034) 3 else 1)
            schedule(run, DukeRules.FERMENT_TICKS) { refreshVat(run, source) }
        }
    }

    fun grind(run: Run, musca: Boolean): Boolean {
        if (!valid(run) || run.state != Dt2State.PREPARING) return false
        val player = run.player
        if (
            player.inv.count(
                requireNotNull(ServerCacheManager.getItem(Dt2Assets.PESTLE_AND_MORTAR)).internalName
            ) == 0
        )
            return false
        val result =
            player.invTransaction(player.inv) {
                val inventory = select(player.inv)
                delete {
                    from = inventory
                    obj = if (musca) Dt2Assets.MUSCA_MUSHROOM else Dt2Assets.ARDER_MUSHROOM
                    strictCount = 1
                }
                insert {
                    into = inventory
                    obj = if (musca) Dt2Assets.MUSCA_POWDER else Dt2Assets.ARDER_POWDER
                    strictCount = DukeRules.powderYield(player.stat("stat.herblore"))
                }
            }
        return result.success
    }

    fun feed(run: Run, item: Int) {
        if (!valid(run) || run.state != Dt2State.PREPARING) return
        val powder = item in listOf(Dt2Assets.ARDER_POWDER, Dt2Assets.MUSCA_POWDER)
        if (!powder && item != Dt2Assets.ARDER_MUSCA_POISON) return
        if (!run.player.invDel(run.player.inv, item, 1).success) return
        if (!powder) run.poisonDoses++
        if (!run.duke.preparation.feed(awakened(run), powder)) return
        clear(run, keepScene = true)
        run.boss.ignoreCombatInteractions = true
        run.boss.transmog(
            requireNotNull(
                ServerCacheManager.getNpc(
                    if (awakened(run)) DukeRules.AWAKENED else DukeRules.NORMAL
                )
            ),
            Int.MAX_VALUE,
        )
        run.boss.anim(DukeRules.WAKE)
        run.state = Dt2State.STARTING
        run.startReadyCycle = clock.cycle + DukeRules.WAKE_TICKS
        run.boss.actionDelay = run.startReadyCycle
        run.duke.preparation.mushroomsPicked.clear()
    }

    fun clear(run: Run, keepScene: Boolean = false) {
        run.duke.timeline.clear()
        bossEncounters.remove(run.boss)
        run.boss.ignoreCombatInteractions = false
        run.duke.pools.values.forEach { locs.del(it, Int.MAX_VALUE) }
        run.duke.pools.clear()
        run.duke.poisonTiles.clear()
        run.helpers.toList().forEach { remove(run, it) }
        if (!keepScene) {
            run.duke.scene.values.forEach { locs.del(it, Int.MAX_VALUE) }
            run.duke.scene.clear()
        }
    }

    private fun present(run: Run): Boolean {
        val (x, z) = source(run, run.player.coords)
        return run.player.isSlotAssigned &&
            !run.player.pendingLogout &&
            !run.player.loggingOut &&
            run.player.hitpoints > 0 &&
            run.player.coords.level == run.boss.coords.level &&
            x in 3008..3071 &&
            z in 6400..6463 &&
            run.state != Dt2State.INVALIDATED &&
            instances.sessionForPlayer(run.player)?.id == run.session.id
    }

    private fun valid(run: Run) =
        present(run) && run.state !in setOf(Dt2State.DYING, Dt2State.RESPAWNING)

    private fun awakened(run: Run) = run.difficulty == Dt2Difficulty.AWAKENED

    private fun prayer(run: Run, magic: Boolean) =
        run.player.vars[
                if (magic) "varbit.prayer_protectfrommagic"
                else "varbit.prayer_protectfrommelee"] == 1

    private fun faceProtection(player: Player) =
        player.worn.any { item ->
            item != null &&
                (item.id == 1506 ||
                    ServerCacheManager.getItem(item.id)?.paramOrNull(BaseParams.slayer_helm) == 1)
        }

    private fun coord(run: Run, x: Int, z: Int) = run.boss.coords.translate(x - 3036, z - 6452)

    private fun source(run: Run, tile: CoordGrid) =
        (tile.x - run.boss.coords.x + 3036) to (tile.z - run.boss.coords.z + 6452)

    private fun schedule(run: Run, delay: Int, action: () -> Unit) {
        val generation = run.generation
        run.duke.timeline.at(clock.cycle + delay, generation) {
            if (valid(run) && generation == run.generation) action()
        }
    }

    private fun graphic(run: Run, id: Int, source: Pair<Int, Int>) =
        world.spotanimMap(SpotanimType(id), coord(run, source.first, source.second))

    private fun impact(
        run: Run,
        amount: Int,
        type: HitType = HitType.Typeless,
        avoidable: Boolean = true,
        ward: Boolean = false,
    ) {
        val generation = run.generation
        val timeline = run.duke.timeline
        val epoch = timeline.epoch
        run.player.queueImpactHit(
            run.boss,
            1,
            type,
            amount,
            modifier =
                PlayerHitModifier { target ->
                    validity = HitValidity {
                        valid(run) && generation == run.generation && timeline.epoch == epoch
                    }
                    if (!validity!!.isValid()) damage = 0
                    else {
                        if (ward && ArceuusSpellState.hasWard(target, clock.cycle))
                            damage -= damage / 10
                        with(NpcBypassProtectionPrayerPlayerHitModifier.withoutWard) {
                            modify(target)
                        }
                        if (avoidable) run.duke.perfect = false
                    }
                },
        )
    }

    private fun helper(run: Run, id: Int, x: Int, z: Int): Npc {
        val npc = Npc(requireNotNull(ServerCacheManager.getNpc(id)), coord(run, x, z))
        npc.mode = NpcMode.None
        npc.movementLocked = true
        npc.ignoreCombatInteractions = true
        npc.setHunt(0)
        npc.assignSpawnOwner(run.player, clock.cycle)
        npcs.add(npc, Int.MAX_VALUE)
        npc.respawns = false
        instances.attachNpc(run.session.id, npc)
        run.helpers.add(npc)
        return npc
    }

    private fun remove(run: Run, npc: Npc) {
        run.helpers.remove(npc)
        instances.detachNpc(run.session.id, npc)
        if (npc.isSlotAssigned) npcs.del(npc, Int.MAX_VALUE)
    }

    private fun projectile(
        run: Run,
        id: Int,
        target: CoordGrid,
        start: Int,
        end: Int,
        height: Int,
        endHeight: Int,
        track: Boolean = false,
        angle: Int = 5,
    ) {
        world.projAnim(
            ProjAnim(
                id,
                height,
                endHeight,
                start,
                end,
                angle,
                32,
                0,
                if (track) -(run.player.slotId + 1) else 0,
                coord(run, 3039, 6453),
                target,
            )
        )
    }

    private fun locInfo(run: Run, id: Int, x: Int, z: Int, angle: Int, shape: Int = 10) =
        LocInfo(if (shape == 0) 0 else 2, coord(run, x, z), LocEntity(id, shape, angle))

    private fun scene(run: Run, id: Int, x: Int, z: Int, angle: Int, shape: Int = 10) {
        val loc = locInfo(run, id, x, z, angle, shape)
        locs.add(loc, Int.MAX_VALUE)
        run.duke.scene[loc.coords] = loc
    }
}

internal class DukeState(val prepStart: Int) {
    val timeline = DukeTimeline()
    val preparation = DukePreparation()
    val scene = mutableMapOf<CoordGrid, LocInfo>()
    val poisonTiles = mutableMapOf<CoordGrid, Int>()
    val pools = mutableMapOf<CoordGrid, LocInfo>()
    var nextExtremities = 2
    var nextVents = 15
    var nextIce = 4
    var ventStep = 0
    var nextOrb = 0
    var nextBile = 0
    var orbEast = true
    var perfect = true
}
