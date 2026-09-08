package org.rsmod.content.minigames.perilousmoons

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.util.UncheckedType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.abs
import kotlin.math.min
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.npc.heal
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.player.clearInteractionRoute
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.queueImpactHit
import org.rsmod.api.player.output.clearMapFlag
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.route.RayCastValidator
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.InvObj
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@Singleton
internal class PerilousMoonEncounterManager
@Inject
constructor(
    private val clock: MapClock,
    private val npcs: NpcRepository,
    private val collision: CollisionFlagMap,
    private val accuracy: AccuracyFormulae,
    private val random: GameRandom,
    private val npcHitModifier: NpcHitModifier,
    private val rayCast: RayCastValidator,
) {
    private val log = InlineLogger()
    private val runs = mutableMapOf<PlayerUid, Run>()
    private val removedDecorativeShellIds = mutableSetOf<Int>()
    private var generation = 1L

    fun isActive(player: Player): Boolean {
        val state = runs[player.uid]?.state ?: return false
        return state != MoonEncounterState.SUBDUED && state != MoonEncounterState.RESETTING
    }

    fun isEncounterNpc(npc: Npc): Boolean = runs.values.any { it.owns(npc) }

    fun isOwnedBy(player: Player, npc: Npc): Boolean {
        val run = runs[player.uid] ?: return false
        return run.owns(npc) && npc.isSpawnOwnedBy(player)
    }

    fun initializeWorld() {
        PerilousMoon.entries.forEach(::verifyDefinitions)
    }

    /** Runs after static map NPC loading and permanently removes the 1-HP display shells. */
    fun removeDecorativeShells() {
        if (removedDecorativeShellIds.size == PerilousMoon.entries.size) return
        PerilousMoon.entries
            .filterNot { it.decorativeNpcId in removedDecorativeShellIds }
            .forEach(::removeDecorativeShell)
    }

    fun teleportToStatue(player: Player, moon: PerilousMoon): Boolean {
        abort(player, "teleported to ${moon.displayName} statue", teleport = false)
        val destination = moon.statueApproach
        if (!collision.isZoneValid(destination) || collision.isWalkBlocked(destination)) {
            player.mes("The route to the ${moon.displayName} statue is unavailable.")
            return false
        }
        preparePlayer(player)
        resetClientState(player)
        removeDecorativeShells()
        PathingEntityCommon.telejump(player, collision, destination)
        player.mes("Use the ${moon.displayName} statue to enter its chamber.")
        log.info {
            "[PerilousMoon] player=${player.displayName} commandDestination=$destination " +
                "statue=${moon.entryStatueCoords} moon=${moon.name}"
        }
        return true
    }

    fun resetClientState(player: Player) {
        if (runs.containsKey(player.uid)) return
        player.moonBossHidden = MOON_NORMAL_VISIBLE_STATE
        player.moonBossInCombat = 0
        player.moonBossHitpoints = 0
        player.moonBossCurrentFocus = 0
        player.moonBossLastFocus = 0
        player.blueMoonBrazierLeft = 0
        player.blueMoonBrazierRight = 0
        player.blueMoonWearPrevent = 0
    }

    fun begin(player: Player, moon: PerilousMoon): Boolean {
        abort(player, "switching Moon encounter", teleport = false)
        if (!collision.isZoneValid(moon.arrival) || collision.isWalkBlocked(moon.arrival)) {
            player.mes("The ${moon.displayName} arrival tile is unavailable.")
            return false
        }
        preparePlayer(player)
        PathingEntityCommon.telejump(player, collision, moon.arrival)
        player.moonBossHidden = MOON_NORMAL_VISIBLE_STATE
        player.moonBossInCombat = 1
        player.moonBossHitpoints = MOON_MAX_HITPOINTS
        player.setMoonSubduedVarbit(moon, 0)
        removeDecorativeShell(moon)

        val boss = spawnNpc(player, moon.bossNpc, moon.bossSouthWest, MOON_MAX_HITPOINTS)
        boss.movementLocked = true
        boss.vars["varn.flat_armour"] = moon.flatArmour
        boss.anim(moon.spawnAnimation)
        val run =
            Run(
                player = player,
                moon = moon,
                generation = generation++,
                boss = boss,
                state = MoonEncounterState.SPAWNING,
                nextAttackCycle = clock.cycle + 4,
                nextCurseDecayCycle = clock.cycle + MOON_CURSE_DECAY_TICKS,
                nextIdleCycle = clock.cycle + 2,
            )
        runs[player.uid] = run
        spawnGlyph(run)
        player.mes("<col=ff9900>${moon.displayName}: remain inside Eyatlalli's active glyph.</col>")
        player.mes("After it is subdued, this separate boss reforms in 10 seconds.")
        log.info {
            "[PerilousMoon] player=${player.displayName} moon=${moon.name} " +
                "generation=${run.generation} boss=${boss.uid} center=${moon.center} arrival=${moon.arrival}"
        }
        return true
    }

    fun tick(player: Player) {
        val run = runs[player.uid] ?: return
        if (
            !PerilousMoonRules.isInsideRoom(
                run.moon,
                player.coords.x,
                player.coords.z,
                player.coords.level,
            )
        ) {
            abort(player, "left ${run.moon.displayName} chamber", teleport = false)
            return
        }
        if (
            !player.isSlotAssigned ||
                player.pendingLogout ||
                player.loggingOut ||
                player.hitpoints <= 0
        ) {
            abort(player, "player unavailable", teleport = false)
            return
        }
        if (
            !run.boss.isSlotAssigned &&
                run.state != MoonEncounterState.SUBDUED &&
                run.state != MoonEncounterState.RESPAWNING
        ) {
            abort(player, "personal boss disappeared", teleport = false)
            return
        }
        resumeIdleIfDue(run)
        if (run.state != MoonEncounterState.RESPAWNING && clock.cycle >= run.nextCurseDecayCycle) {
            run.curse = PerilousMoonRules.decayCurse(run.curse)
            run.nextCurseDecayCycle = clock.cycle + MOON_CURSE_DECAY_TICKS
        }
        player.moonBossHitpoints =
            if (run.boss.isSlotAssigned) run.boss.hitpoints.coerceIn(0, MOON_MAX_HITPOINTS) else 0
        when (run.state) {
            MoonEncounterState.SPAWNING -> {
                if (clock.cycle >= run.nextAttackCycle) {
                    run.state = MoonEncounterState.STANDARD_COMBAT
                }
            }
            MoonEncounterState.STANDARD_COMBAT -> tickStandard(run)
            MoonEncounterState.SPECIAL_ATTACK_ONE,
            MoonEncounterState.SPECIAL_ATTACK_TWO -> tickSpecial(run)
            MoonEncounterState.SUBDUED,
            MoonEncounterState.RESPAWNING -> respawnIfDue(run)
            MoonEncounterState.RESETTING,
            MoonEncounterState.WAITING -> Unit
        }
    }

    fun attackDenial(player: Player, npc: Npc): String? {
        val run = runs[player.uid] ?: return "You do not have an active Moon encounter."
        if (!run.owns(npc)) return "That target is not part of your encounter."
        if (npc === run.boss) {
            if (run.state != MoonEncounterState.STANDARD_COMBAT) {
                return "The ${run.moon.displayName} cannot be attacked during this special phase."
            }
            if (
                run.moon == PerilousMoon.Blue && PerilousMoonRules.shouldCancelColdAttack(run.curse)
            ) {
                run.curse = PerilousMoonRules.curseAfterCancelledAttack(run.curse)
                run.warnedColdThree = run.curse >= 3
                run.warnedColdNine = run.curse >= 9
                player.actionDelay = 4
                player.anim("seq.human_stunned")
                player.spotanim(MoonAssets.DISARM_SPOTANIM)
                return "Your joints lock up as you go to attack. The cold is too much to bear!"
            }
            return null
        }
        return when (run.helpers[npc]) {
            MoonHelperRole.ICE_BLOCK ->
                when {
                    npc !== run.frozenBlock -> "Your weapon is not inside that block."
                    !PerilousMoonRules.canAttackFrozenBlock(
                        player.worn[Wearpos.RightHand.slot] != null
                    ) -> "You must break the highlighted ice block unarmed."
                    else -> null
                }
            MoonHelperRole.BLOOD_JAGUAR ->
                if (npc === run.highlightedJaguar) null else "That is not your highlighted jaguar."
            MoonHelperRole.ECLIPSE_CLONE -> {
                if (npc === run.activeClone) {
                    player.faceSquare(npc.coords, npc.size, npc.size)
                    if (!run.cloneParried) {
                        run.cloneParried = true
                        counterattack(run)
                        player.mes("You parry the Eclipse clone and counterattack immediately.")
                    }
                    "You parry the attacking clone."
                } else {
                    "Face the attacking clone."
                }
            }
            else -> "You cannot attack that encounter effect."
        }
    }

    fun modifyNpcHit(player: Player, npc: Npc, hit: HitBuilder) {
        val run = runs[player.uid]
        if (run == null || !run.owns(npc) || !npc.isSpawnOwnedBy(player)) {
            hit.damage = 0
            return
        }
        when (run.helpers[npc]) {
            MoonHelperRole.ICE_BLOCK -> {
                if (player.worn[Wearpos.RightHand.slot] != null) hit.damage = 0
            }
            MoonHelperRole.BLOOD_JAGUAR -> {
                val heal = PerilousMoonRules.jaguarPlayerHeal(hit.damage)
                hit.damage = 0
                if (heal > 0) player.statHeal("stat.hitpoints", constant = heal, percent = 0)
            }
            MoonHelperRole.ECLIPSE_CLONE -> {
                if (hit.type == HitType.Melee && !run.cloneParried) {
                    run.cloneParried = true
                    counterattack(run)
                    player.mes("You parry the Eclipse clone and counterattack immediately.")
                }
                hit.damage = 0
            }
            null -> modifyBossHit(run, hit)
            else -> hit.damage = 0
        }
    }

    private fun modifyBossHit(run: Run, hit: HitBuilder) {
        val permitted = run.state == MoonEncounterState.STANDARD_COMBAT || run.mechanicDamage
        if (!permitted) {
            hit.damage = 0
            return
        }
        if (run.moon != PerilousMoon.Eclipse || hit.damage <= 0) return
        val chance = PerilousMoonRules.glancingChance(run.curse)
        if (chance > 0 && random.of(1..100) <= chance) {
            val normalMaximum = run.player.vars["varp.com_maxhit"].coerceAtLeast(hit.damage)
            val maximum =
                PerilousMoonRules.glancingMaximum(
                    normalMaximum,
                    clonePhase = run.state == MoonEncounterState.SPECIAL_ATTACK_TWO,
                )
            hit.damage = min(hit.damage, maximum)
            run.player.mes("Your attack glances off the Eclipse Moon.")
        }
    }

    fun onNpcHit(player: Player, npc: Npc, damage: Int) {
        val run = runs[player.uid] ?: return
        if (!run.owns(npc) || !npc.isSpawnOwnedBy(player)) return
        if (run.helpers[npc] != MoonHelperRole.ICE_BLOCK || damage <= 0) return
        if (npc !== run.frozenBlock) return

        val tile = player.coords
        run.iceBlockHitsAtTile = if (run.iceBlockLastTile == tile) run.iceBlockHitsAtTile + 1 else 1
        run.iceBlockLastTile = tile
        if (run.iceBlockHitsAtTile >= BLUE_FREEZE_SAFE_ATTACKS) {
            run.iceBlockHitsAtTile = 0
            run.icicleWarning = tile
            run.icicleImpactCycle = clock.cycle + BLUE_FREEZE_SPIKE_DELAY
            player.spotanim(MoonAssets.FLOOR_SYMBOL_SPAWN_SPOTANIM)
            player.mes("An ice spike forms under you; move before your next attack.")
        }
        if (npc.hitpoints <= 0) {
            recoverFrozenWeapon(run)
            finishSpecial(run)
        }
    }

    fun beginSubdue(npc: Npc): Run? {
        val run = runs.values.firstOrNull { it.boss === npc } ?: return null
        if (run.state == MoonEncounterState.SUBDUED || run.state == MoonEncounterState.RESETTING)
            return null
        run.state = MoonEncounterState.SUBDUED
        run.nextIdleCycle = null
        run.boss.resetTransmog()
        run.player.combatClearQueue()
        run.player.tempMoveSpeed = null
        clearHelpers(run)
        recoverFrozenWeapon(run)
        run.player.clearInteractionRoute()
        run.player.blueMoonBrazierLeft = 0
        run.player.blueMoonBrazierRight = 0
        val current = run.player.attr.getOrDefault(MOON_SUBDUED_FLAGS, 0)
        run.player.attr[MOON_SUBDUED_FLAGS] = current or run.moon.bit
        run.player.setMoonSubduedVarbit(run.moon, 1)
        run.player.mes("<col=ff9900>You have subdued the ${run.moon.displayName}.</col>")
        return run
    }

    fun finishSubdue(npc: Npc) {
        val run = runs.values.firstOrNull { it.boss === npc } ?: return
        if (runs[run.player.uid] !== run) return
        if (npc.isSlotAssigned) npcs.del(npc, Int.MAX_VALUE)
        preparePlayer(run.player)
        run.player.moonBossHidden = MOON_NORMAL_VISIBLE_STATE
        run.player.moonBossInCombat = 0
        run.player.moonBossHitpoints = 0
        run.state = MoonEncounterState.RESPAWNING
        run.respawnCycle = clock.cycle + MOON_RESPAWN_TICKS
        run.player.mes("<col=ff9900>The ${run.moon.displayName} will reform in 10 seconds.</col>")
        log.info {
            "[PerilousMoon] player=${run.player.displayName} moon=${run.moon.name} " +
                "generation=${run.generation} subdued=true " +
                "respawnCycle=${run.respawnCycle} flags=${run.player.attr.getOrDefault(MOON_SUBDUED_FLAGS, 0)}"
        }
    }

    private fun respawnIfDue(run: Run) {
        val dueCycle = run.respawnCycle ?: return
        if (clock.cycle < dueCycle || runs[run.player.uid] !== run) return

        preparePlayer(run.player)
        clearHelpers(run)
        recoverFrozenWeapon(run)
        val boss =
            spawnNpc(run.player, run.moon.bossNpc, run.moon.bossSouthWest, MOON_MAX_HITPOINTS)
        boss.movementLocked = true
        boss.vars["varn.flat_armour"] = run.moon.flatArmour
        boss.anim(run.moon.spawnAnimation)
        run.boss = boss
        run.state = MoonEncounterState.SPAWNING
        run.respawnCycle = null
        run.glyphIndex = 0
        run.standardAttacks = 0
        run.nextSpecial = 1
        run.curse = 0
        run.auraRamp = 0
        run.nextAttackCycle = clock.cycle + 4
        run.nextCurseDecayCycle = clock.cycle + MOON_CURSE_DECAY_TICKS
        run.nextIdleCycle = clock.cycle + 2
        run.specialStartCycle = null
        run.specialAge = 0
        run.comboSerial++
        run.comboAccuracy.fill(false)
        run.warnedColdThree = false
        run.warnedColdNine = false
        run.highlightedJaguar = null
        run.jaguarSafeGlyph = null
        run.frozenWeapon = null
        run.frozenBlock = null
        run.icicleWarning = null
        run.icicleImpactCycle = null
        run.iceBlockHitsAtTile = 0
        run.iceBlockLastTile = null
        run.braziersLit.fill(false)
        run.walkOnlyUntil = Int.MIN_VALUE
        run.shield = null
        run.shieldRoute = emptyList()
        run.activeClone = null
        run.cloneParried = false
        run.cloneParryDamageQueued = false
        run.cloneAttacks = 0
        run.cloneDeadlineCycle = Int.MIN_VALUE
        run.cloneNextSpawnCycle = Int.MIN_VALUE
        run.mechanicDamage = false
        run.player.moonBossHidden = MOON_NORMAL_VISIBLE_STATE
        run.player.moonBossInCombat = 1
        run.player.moonBossHitpoints = MOON_MAX_HITPOINTS
        run.player.moonBossCurrentFocus = 0
        run.player.moonBossLastFocus = 0
        run.player.setMoonSubduedVarbit(run.moon, 0)
        run.player.blueMoonBrazierLeft = 0
        run.player.blueMoonBrazierRight = 0
        run.player.blueMoonWearPrevent = 0
        spawnGlyph(run)
        run.player.mes("<col=ff9900>The ${run.moon.displayName} reforms.</col>")
        log.info {
            "[PerilousMoon] player=${run.player.displayName} moon=${run.moon.name} " +
                "generation=${run.generation} respawned=${boss.uid}"
        }
    }

    fun lightBrazier(player: Player, left: Boolean): Boolean {
        val run = runs[player.uid] ?: return false
        if (run.moon != PerilousMoon.Blue || run.state != MoonEncounterState.SPECIAL_ATTACK_TWO) {
            player.mes("The brazier does not need lighting right now.")
            return false
        }
        val index = if (left) 0 else 1
        if (run.braziersLit[index]) {
            player.mes("That brazier is already lit.")
            return false
        }
        run.braziersLit[index] = true
        player.anim("seq.human_light_torch")
        if (left) player.blueMoonBrazierLeft = 0 else player.blueMoonBrazierRight = 0
        run.curse = (run.curse - 5).coerceAtLeast(0)
        run.warnedColdThree = run.curse >= 3
        run.warnedColdNine = run.curse >= 9
        player.mes("You relight the brazier and the cold recedes.")
        return true
    }

    fun forceSpecial(player: Player, moon: PerilousMoon, special: Int): Boolean {
        val run = runs[player.uid] ?: return false
        if (run.moon != moon) return false
        if (
            run.state == MoonEncounterState.SUBDUED ||
                run.state == MoonEncounterState.RESPAWNING ||
                run.state == MoonEncounterState.RESETTING
        )
            return false
        clearHelpers(run)
        recoverFrozenWeapon(run)
        run.nextSpecial = special.coerceIn(1, 2)
        beginSpecial(run)
        return true
    }

    fun debugStatus(player: Player): String {
        val run = runs[player.uid] ?: return "Moons: no active encounter."
        return "Moon=${run.moon.displayName} generation=${run.generation} state=${run.state} " +
            "hp=${run.boss.hitpoints}/${run.boss.baseHitpointsLvl} glyph=${run.glyphIndex}/7 " +
            "attacks=${run.standardAttacks}/6 special=${run.nextSpecial} curse=${run.curse} " +
            "helpers=${run.helpers.size} frozen=${run.frozenWeapon?.id ?: 0}"
    }

    fun escape(player: Player): Boolean {
        val run = runs[player.uid]
        if (run == null) {
            player.mes("You are not in an active Moon encounter.")
            return false
        }
        val destination = run.moon.postFight
        abort(player, "used chamber escape stairs", teleport = false)
        if (!collision.isZoneValid(destination) || collision.isWalkBlocked(destination)) {
            player.mes("The chamber escape route is temporarily unavailable.")
            return false
        }
        preparePlayer(player)
        PathingEntityCommon.telejump(player, collision, destination)
        player.mes("You escape the Moon chamber. The unfinished encounter has reset.")
        return true
    }

    fun abort(player: Player, reason: String, teleport: Boolean) {
        val run = runs.remove(player.uid) ?: return
        run.state = MoonEncounterState.RESETTING
        player.combatClearQueue()
        player.tempMoveSpeed = null
        player.clearInteractionRoute()
        clearHelpers(run)
        recoverFrozenWeapon(run)
        player.blueMoonBrazierLeft = 0
        player.blueMoonBrazierRight = 0
        if (run.boss.isSlotAssigned) npcs.del(run.boss, Int.MAX_VALUE)
        player.moonBossHidden = MOON_NORMAL_VISIBLE_STATE
        player.moonBossInCombat = 0
        player.moonBossHitpoints = 0
        if (teleport && collision.isZoneValid(run.moon.arrival)) {
            PathingEntityCommon.telejump(player, collision, run.moon.arrival)
        }
        log.info {
            "[PerilousMoon] player=${player.displayName} moon=${run.moon.name} " +
                "generation=${run.generation} aborted=$reason"
        }
    }

    @OptIn(UncheckedType::class)
    fun recoverOrphanedFrozenWeapon(player: Player) {
        if (runs[player.uid]?.frozenWeapon != null) return
        val id = player.attr.getOrDefault(FROZEN_WEAPON_ID, 0)
        if (id <= 0) return
        val weapon =
            InvObj(
                id = id,
                count = player.attr.getOrDefault(FROZEN_WEAPON_COUNT, 1).coerceAtLeast(1),
                vars = player.attr.getOrDefault(FROZEN_WEAPON_VARS, 0),
            )
        if (restoreExactWeapon(player, weapon)) {
            clearFrozenPersistence(player)
            player.mes("Your weapon was safely restored from Blue Moon's ice.")
        }
    }

    private fun tickStandard(run: Run) {
        tickAura(run)
        val specialCycle = run.specialStartCycle
        if (specialCycle != null && clock.cycle >= specialCycle) {
            run.specialStartCycle = null
            beginSpecial(run)
            return
        }
        if (clock.cycle >= run.nextAttackCycle) startCombo(run)
    }

    private fun tickAura(run: Run) {
        tickGlyphAura(run, run.moon.glyphs[run.glyphIndex])
    }

    private fun tickGlyphAura(run: Run, glyph: CoordGrid) {
        if (PerilousMoonRules.isOnMoonGlyph(glyph, run.player.coords)) {
            run.auraRamp = 0
            return
        }
        val damage = PerilousMoonRules.auraDamage[run.auraRamp.coerceAtMost(4)]
        run.auraRamp = (run.auraRamp + 1).coerceAtMost(4)
        addCurse(run, 1)
        queueMoonHit(run, damage)
    }

    private fun startCombo(run: Run) {
        if (run.boss.hitpoints <= 0 || run.state != MoonEncounterState.STANDARD_COMBAT) return
        run.nextAttackCycle = clock.cycle + MOON_ATTACK_RATE
        run.comboSerial++
        run.comboAccuracy.fill(false)
        run.boss.faceSquare(run.player.coords)
        run.boss.anim(run.moon.standardAnimation)
        run.nextIdleCycle = clock.cycle + 4
        for (index in 0..2) {
            val damage = random.of(0..PerilousMoonRules.comboMaxHits[index])
            run.player.queueImpactHit(
                source = run.boss,
                delay = index + 1,
                type = HitType.Typeless,
                damage = damage,
                modifier = ComboHitModifier(run, run.comboSerial, index),
                impactHandler = { impact -> comboImpact(run, index, impact.damage) },
            )
        }
        run.standardAttacks++
        if (PerilousMoonRules.shouldMoveGlyph(run.standardAttacks)) {
            run.glyphIndex = PerilousMoonRules.nextGlyph(run.glyphIndex)
            spawnGlyph(run)
        }
        if (PerilousMoonRules.shouldStartSpecial(run.standardAttacks)) {
            run.specialStartCycle = clock.cycle + 4
        }
    }

    private inner class ComboHitModifier(
        private val run: Run,
        private val serial: Int,
        private val index: Int,
    ) : PlayerHitModifier {
        override fun HitBuilder.modify(target: Player) {
            val current = runs[target.uid]
            val valid =
                current === run &&
                    run.comboSerial == serial &&
                    run.boss.hitpoints > 0 &&
                    !PerilousMoonRules.thirdHitStepUnderDodges(
                        run.moon,
                        index,
                        standingUnderBoss(run),
                    )
            val chain = index == 0 || run.comboAccuracy[index - 1]
            val accurate =
                valid &&
                    chain &&
                    accuracy.rollMeleeAccuracy(run.boss, target, MeleeAttackType.Slash, random)
            run.comboAccuracy[index] = accurate
            if (!accurate) damage = 0
            val builder = this
            NpcBypassProtectionPrayerPlayerHitModifier.run { builder.modify(target) }
        }
    }

    private fun comboImpact(run: Run, index: Int, damage: Int) {
        if (runs[run.player.uid] !== run || damage <= 0) return
        addCurse(run, PerilousMoonRules.comboCurseAdds[index])
        when (run.moon) {
            PerilousMoon.Blood -> {
                val heal = PerilousMoonRules.bloodHeal(index, damage)
                run.boss.heal(heal, showHitsplat = true)
            }
            PerilousMoon.Blue,
            PerilousMoon.Eclipse -> Unit
        }
    }

    private fun addCurse(run: Run, amount: Int) {
        val previous = run.curse
        run.curse = PerilousMoonRules.addCurse(previous, amount)
        if (run.moon != PerilousMoon.Blue) return
        when (PerilousMoonRules.coldWarning(previous, run.curse)) {
            ColdWarning.MILD ->
                run.player.mes(
                    "The Blue Moon pierces your defence. The cold is starting to get in..."
                )
            ColdWarning.SEVERE ->
                run.player.mes(
                    "The Blue Moon pierces your defence. Your joints are starting to lock up..."
                )
            ColdWarning.NONE -> Unit
        }
        run.warnedColdThree = run.curse >= 3
        run.warnedColdNine = run.curse >= 9
    }

    private fun beginSpecial(run: Run) {
        run.state =
            if (run.nextSpecial == 1) MoonEncounterState.SPECIAL_ATTACK_ONE
            else MoonEncounterState.SPECIAL_ATTACK_TWO
        run.specialAge = 0
        run.auraRamp = 0
        run.nextIdleCycle = null
        clearHelpers(run)
        run.player.tempMoveSpeed = null
        run.boss.vars["varn.flat_armour"] = run.moon.flatArmour
        run.mechanicDamage = false
        run.cloneParryDamageQueued = false
        run.boss.anim(run.moon.disappearAnimation)
        transmogInvisible(run)
        when (run.moon) {
            PerilousMoon.Blood ->
                if (run.nextSpecial == 1) spawnBloodPools(run) else spawnJaguars(run)
            PerilousMoon.Blue ->
                if (run.nextSpecial == 1) beginWeaponFreeze(run) else beginFrostStorm(run)
            PerilousMoon.Eclipse ->
                if (run.nextSpecial == 1) beginMoonShield(run) else beginClones(run)
        }
    }

    private fun tickSpecial(run: Run) {
        run.specialAge++
        when (run.moon) {
            PerilousMoon.Blood ->
                if (run.state == MoonEncounterState.SPECIAL_ATTACK_ONE) tickBloodRain(run)
                else tickJaguars(run)
            PerilousMoon.Blue ->
                if (run.state == MoonEncounterState.SPECIAL_ATTACK_ONE) tickWeaponFreeze(run)
                else tickFrostStorm(run)
            PerilousMoon.Eclipse ->
                if (run.state == MoonEncounterState.SPECIAL_ATTACK_ONE) tickMoonShield(run)
                else tickClones(run)
        }
    }

    private fun spawnBloodPools(run: Run) {
        refreshBloodRainPools(run, pulse = 0)
    }

    private fun refreshBloodRainPools(run: Run, pulse: Int) {
        clearBloodPools(run)
        run.moon.glyphs
            .filterIndexed { index, _ -> (index + pulse) % 2 == 0 }
            .forEach { coords ->
                spawnHelper(
                    run,
                    MoonAssets.GLYPH_NPC,
                    moonGlyphSouthWest(coords),
                    MoonHelperRole.BLOOD_POOL,
                )
            }
    }

    private fun clearBloodPools(run: Run) {
        run.helpers
            .filterValues { it == MoonHelperRole.BLOOD_POOL }
            .keys
            .toList()
            .forEach { deleteHelper(run, it) }
    }

    private fun isStandingInBloodPool(run: Run): Boolean =
        run.helpers.any { (npc, role) ->
            role == MoonHelperRole.BLOOD_POOL &&
                npc.isSlotAssigned &&
                PerilousMoonRules.isOnMoonGlyph(npcCenter(npc), run.player.coords)
        }

    private fun tickBloodRain(run: Run) {
        if (run.specialAge in 2..(BLOOD_RAIN_PULSES * 2) && run.specialAge % 2 == 0) {
            val onPool = isStandingInBloodPool(run)
            if (onPool) {
                queueMoonHit(run, BLOOD_RAIN_MAX_HIT) { damage ->
                    val healing = PerilousMoonRules.bloodRainBossHeal(damage)
                    if (healing > 0) run.boss.heal(healing, showHitsplat = true)
                }
            } else {
                queueMechanicBossHit(run, PerilousMoonRules.bloodRainAvoidanceChip(onPool))
            }
            clearBloodPools(run)
        } else if (run.specialAge in 3 until BLOOD_RAIN_PULSES * 2 && run.specialAge % 2 == 1) {
            refreshBloodRainPools(run, pulse = (run.specialAge - 1) / 2)
        }
        if (run.specialAge > BLOOD_RAIN_PULSES * 2) finishSpecial(run)
    }

    private fun spawnJaguars(run: Run) {
        val selected = random.of(run.moon.glyphs.indices)
        val safeGlyph = run.moon.glyphs[selected]
        val jaguars =
            run.moon.glyphs.map { glyph ->
                val center = PerilousMoonRules.jaguarCenter(run.moon.center, glyph)
                spawnHelper(
                    run,
                    MoonAssets.BLOOD_JAGUAR_NPC,
                    moonGlyphSouthWest(center),
                    MoonHelperRole.BLOOD_JAGUAR,
                    100,
                )
            }
        run.glyphIndex = selected
        run.jaguarSafeGlyph = safeGlyph
        spawnGlyph(run)
        run.highlightedJaguar = jaguars[selected]
        run.highlightedJaguar?.spotanim(MoonAssets.FLOOR_SYMBOL_IDLE_SPOTANIM)
        run.player.mes(
            "Stand on the highlighted moon glyph, attack its jaguar, and step inward as each pool disappears."
        )
    }

    private fun refreshJaguarPools(run: Run) {
        clearBloodPools(run)
        val safe = run.jaguarSafeGlyph ?: return
        listOf(-2 to -2, 0 to -2, 2 to -2, -2 to 0, 2 to 0, -2 to 2, 0 to 2, 2 to 2).forEach {
            (x, z) ->
            spawnHelper(
                run,
                MoonAssets.GLYPH_NPC,
                moonGlyphSouthWest(safe.translate(x, z)),
                MoonHelperRole.BLOOD_POOL,
            )
        }
    }

    private fun tickJaguars(run: Run) {
        val cleaveTick = PerilousMoonRules.isJaguarCleaveTick(run.specialAge)
        val safeGlyph = run.jaguarSafeGlyph
        if (safeGlyph != null && !cleaveTick) {
            tickGlyphAura(run, safeGlyph)
        }

        if (PerilousMoonRules.isJaguarPoolSpawnTick(run.specialAge)) {
            refreshJaguarPools(run)
        }
        if (
            PerilousMoonRules.isJaguarPoolDamageTick(run.specialAge) &&
                isStandingInBloodPool(run)
        ) {
            queueMoonHit(run, BLOOD_RAIN_MAX_HIT) { damage ->
                val healing = PerilousMoonRules.bloodRainBossHeal(damage)
                if (healing > 0) run.boss.heal(healing, showHitsplat = true)
            }
        }

        val jaguar = run.highlightedJaguar
        if (jaguar != null && cleaveTick) {
            clearBloodPools(run)
            jaguar.faceSquare(run.player.coords)
            if (run.player.coords.chebyshevDistance(npcCenter(jaguar)) <= 2) {
                queueMoonHit(run, random.of(1..BLOOD_JAGUAR_MAX_SIPHON)) { damage ->
                    val siphon = PerilousMoonRules.jaguarBossSiphon(damage)
                    val healing = PerilousMoonRules.bloodRainBossHeal(siphon)
                    if (healing > 0) run.boss.heal(healing, showHitsplat = true)
                }
            }
        }

        if (PerilousMoonRules.isJaguarPhaseComplete(run.specialAge)) {
            clearBloodPools(run)
            finishSpecial(run)
        }
    }

    private fun beginWeaponFreeze(run: Run) {
        run.player.blueMoonWearPrevent = 1
        run.iceBlockHitsAtTile = 0
        run.iceBlockLastTile = null
        run.icicleImpactCycle = null
        val slot = Wearpos.RightHand.slot
        val weapon = run.player.worn[slot]
        if (weapon != null) {
            run.player.worn[slot] = null
            run.frozenWeapon = InvObj(weapon)
            persistFrozenWeapon(run.player, weapon)
            run.player.spotanim(MoonAssets.DISARM_SPOTANIM)
        } else {
            queueMoonHit(
                run,
                random.of(BLUE_FREEZE_NO_WEAPON_MIN_HIT..BLUE_FREEZE_NO_WEAPON_MAX_HIT),
            )
            run.player.mes("Blue Moon finds no weapon to freeze and lashes you with cold.")
        }
        val blocks =
            listOf(CoordGrid(1427, 9680, 0), CoordGrid(1442, 9683, 0), CoordGrid(1453, 9680, 0))
        val names =
            listOf(
                MoonAssets.ICE_BLOCK_ONE_NPC,
                MoonAssets.ICE_BLOCK_TWO_NPC,
                MoonAssets.ICE_BLOCK_THREE_NPC,
            )
        val selected = random.of(0..2)
        names.indices.forEach { index ->
            val block = spawnHelper(run, names[index], blocks[index], MoonHelperRole.ICE_BLOCK, 100)
            if (index == selected) run.frozenBlock = block
        }
        run.frozenBlock?.spotanim(MoonAssets.FLOOR_SYMBOL_IDLE_SPOTANIM)
        run.player.mes("Blue Moon freezes your exact equipped weapon inside the highlighted ice.")
    }

    private fun tickWeaponFreeze(run: Run) {
        val impactCycle = run.icicleImpactCycle
        if (impactCycle != null && clock.cycle >= impactCycle) {
            if (run.icicleWarning == run.player.coords) {
                queueMoonHit(run, random.of(1..BLUE_FREEZE_MAX_FAILURE))
            }
            run.icicleWarning = null
            run.icicleImpactCycle = null
        }
        if (run.specialAge >= 24) {
            if (run.frozenWeapon != null) {
                queueMoonHit(run, random.of(1..BLUE_FREEZE_MAX_FAILURE))
            }
            recoverFrozenWeapon(run)
            finishSpecial(run)
        }
    }

    private fun beginFrostStorm(run: Run) {
        run.braziersLit.fill(false)
        run.player.blueMoonBrazierLeft = 1
        run.player.blueMoonBrazierRight = 1
        val starts =
            listOf(
                CoordGrid(1439, 9677, 0),
                CoordGrid(1427, 9682, 0),
                CoordGrid(1453, 9682, 0),
                CoordGrid(1440, 9690, 0),
            )
        starts.forEach {
            spawnHelper(run, MoonAssets.WINTER_STORM_NPC, it, MoonHelperRole.WINTER_STORM)
        }
    }

    private fun tickFrostStorm(run: Run) {
        val stormRoute =
            listOf(
                CoordGrid(1439, 9677, 0),
                CoordGrid(1441, 9681, 0),
                CoordGrid(1453, 9678, 0),
                CoordGrid(1453, 9682, 0),
                CoordGrid(1440, 9690, 0),
                CoordGrid(1427, 9682, 0),
                CoordGrid(1427, 9678, 0),
            )
        val storms = run.helpers.filterValues { it == MoonHelperRole.WINTER_STORM }.keys.toList()
        storms.forEachIndexed { index, storm ->
            val next = stormRoute[(run.specialAge + index * 2) % stormRoute.size]
            storm.walk(next)
            if (run.player.coords.chebyshevDistance(storm.coords) <= 1) {
                run.player.tempMoveSpeed = MoveSpeed.Walk
                run.walkOnlyUntil = clock.cycle + 2
                queueMoonHit(run, 10)
            }
        }
        if (clock.cycle <= run.walkOnlyUntil) {
            run.player.tempMoveSpeed = MoveSpeed.Walk
        }
        if (run.specialAge in 4..20 && run.specialAge % 4 == 0) {
            val unlit = run.braziersLit.count { !it }
            run.boss.heal(PerilousMoonRules.frostStormHeal(unlit), showHitsplat = true)
        }
        if (run.specialAge >= 20) finishSpecial(run)
    }

    private fun beginMoonShield(run: Run) {
        val route = eclipseShieldRoute(run.moon.center)
        run.shieldRoute = route
        val shield =
            spawnHelper(
                run,
                MoonAssets.MOON_SHIELD_NPC,
                moonGlyphSouthWest(route.first()),
                MoonHelperRole.MOON_SHIELD,
            )
        run.shield = shield
        val start = route.first().translate(0, -1)
        if (!collision.isWalkBlocked(start))
            PathingEntityCommon.telejump(run.player, collision, start)
    }

    private fun tickMoonShield(run: Run) {
        val route = run.shieldRoute
        val shield = run.shield
        if (shield == null || route.isEmpty()) {
            finishSpecial(run)
            return
        }
        val index = run.specialAge.coerceAtMost(route.lastIndex)
        val next = route[index]
        shield.walk(moonGlyphSouthWest(next))
        if (run.specialAge % 2 == 0 && !shieldProtects(run, shield)) {
            queueMoonHit(run, 10)
            run.player.spotanim(2786)
        }
        if (run.specialAge >= route.lastIndex) finishSpecial(run)
    }

    private fun beginClones(run: Run) {
        val center = run.moon.center
        if (!collision.isWalkBlocked(center)) {
            PathingEntityCommon.telejump(run.player, collision, center)
        }
        run.boss.vars["varn.flat_armour"] = ECLIPSE_CLONE_FLAT_ARMOUR
        run.cloneAttacks = 0
        run.cloneNextSpawnCycle = clock.cycle
        spawnNextClone(run)
        run.cloneDeadlineCycle = clock.cycle + 2
    }

    private fun tickClones(run: Run) {
        run.player.abortRoute()
        run.player.clearMapFlag()
        val clone = run.activeClone
        if (clone == null) {
            if (clock.cycle >= run.cloneNextSpawnCycle) {
                spawnNextClone(run)
                run.cloneDeadlineCycle = clock.cycle + 2
            }
            return
        }
        if (clock.cycle < run.cloneDeadlineCycle) return
        if (!run.cloneParried) queueMoonHit(run, 20)
        deleteHelper(run, clone)
        run.activeClone = null
        run.cloneAttacks++
        if (run.cloneAttacks >= PerilousMoonRules.eclipseCloneCount || run.boss.hitpoints <= 0) {
            finishSpecial(run)
            return
        }
        if (PerilousMoonRules.pauseAfterClone(run.cloneAttacks)) {
            run.cloneNextSpawnCycle = clock.cycle + 2
        } else {
            spawnNextClone(run)
            run.cloneDeadlineCycle = clock.cycle + 2
        }
    }

    private fun spawnNextClone(run: Run) {
        val offsets = listOf(0 to 6, 6 to 0, 0 to -6, -6 to 0, 5 to 5, 5 to -5, -5 to -5, -5 to 5)
        val offset = offsets[run.cloneAttacks % offsets.size]
        val center = run.moon.center.translate(offset.first, offset.second)
        val clone =
            spawnHelper(
                run,
                MoonAssets.ECLIPSE_CLONE_NPC,
                center.translate(-2, -2),
                MoonHelperRole.ECLIPSE_CLONE,
                MOON_MAX_HITPOINTS,
            )
        clone.faceSquare(run.player.coords)
        clone.anim("seq.npc_djinn_02_combo")
        run.activeClone = clone
        run.cloneParried = false
        run.cloneParryDamageQueued = false
    }

    private fun counterattack(run: Run) {
        if (run.cloneParryDamageQueued) return
        val maximum = run.player.vars["varp.com_maxhit"].coerceAtLeast(1)
        val damage = random.of(0..maximum)
        run.cloneParryDamageQueued = true
        run.mechanicDamage = true
        run.boss.queueHit(
            source = run.player,
            delay = MOON_MECHANIC_HIT_DELAY,
            type = HitType.Melee,
            damage = damage,
            modifier = npcHitModifier,
            specific = true,
        )
        run.mechanicDamage = false
    }

    private fun finishSpecial(run: Run) {
        if (runs[run.player.uid] !== run || run.boss.hitpoints <= 0) return
        clearHelpers(run)
        recoverFrozenWeapon(run)
        run.player.tempMoveSpeed = null
        run.player.blueMoonBrazierLeft = 0
        run.player.blueMoonBrazierRight = 0
        run.boss.vars["varn.flat_armour"] = run.moon.flatArmour
        run.boss.resetTransmog()
        run.boss.anim(run.moon.appearAnimation)
        run.nextIdleCycle = clock.cycle + 2
        run.state = MoonEncounterState.STANDARD_COMBAT
        run.standardAttacks = 0
        run.nextSpecial = PerilousMoonRules.nextSpecial(run.nextSpecial)
        run.nextAttackCycle = clock.cycle + 4
        run.specialAge = 0
        run.icicleWarning = null
        run.icicleImpactCycle = null
        run.iceBlockHitsAtTile = 0
        run.iceBlockLastTile = null
        run.highlightedJaguar = null
        run.jaguarSafeGlyph = null
        run.frozenBlock = null
        run.shield = null
        run.shieldRoute = emptyList()
        run.activeClone = null
        run.cloneParried = false
        run.cloneParryDamageQueued = false
        run.cloneDeadlineCycle = Int.MIN_VALUE
        run.cloneNextSpawnCycle = Int.MIN_VALUE
        run.mechanicDamage = false
        run.auraRamp = 0
        spawnGlyph(run)
    }

    private fun queueMoonHit(run: Run, damage: Int, effect: (Int) -> Unit = {}) {
        if (damage <= 0 || runs[run.player.uid] !== run) return
        run.player.queueImpactHit(
            source = run.boss,
            delay = MOON_MECHANIC_HIT_DELAY,
            type = HitType.Typeless,
            damage = damage,
            modifier = NpcBypassProtectionPrayerPlayerHitModifier,
            impactHandler = { impact -> if (runs[run.player.uid] === run) effect(impact.damage) },
        )
    }

    private fun queueMechanicBossHit(run: Run, damage: Int) {
        if (damage <= 0 || runs[run.player.uid] !== run || run.boss.hitpoints <= 0) return
        run.mechanicDamage = true
        run.boss.queueHit(
            source = run.player,
            delay = MOON_MECHANIC_HIT_DELAY,
            type = HitType.Typeless,
            damage = damage,
            modifier = npcHitModifier,
            specific = true,
        )
        run.mechanicDamage = false
    }

    private fun spawnGlyph(run: Run) {
        run.helpers
            .filterValues { it == MoonHelperRole.GLYPH }
            .keys
            .toList()
            .forEach { deleteHelper(run, it) }
        run.player.moonBossLastFocus = run.player.moonBossCurrentFocus
        run.player.moonBossCurrentFocus = run.glyphIndex
        val coords = run.moon.glyphs[run.glyphIndex]
        val glyph =
            spawnHelper(run, MoonAssets.GLYPH_NPC, moonGlyphSouthWest(coords), MoonHelperRole.GLYPH)
        glyph.spotanim(MoonAssets.FLOOR_SYMBOL_SPAWN_SPOTANIM)
    }

    private fun spawnHelper(
        run: Run,
        internalName: String,
        coords: CoordGrid,
        role: MoonHelperRole,
        hitpoints: Int? = null,
    ): Npc {
        val npc = spawnNpc(run.player, internalName, coords, hitpoints)
        npc.movementLocked = role !in setOf(MoonHelperRole.WINTER_STORM, MoonHelperRole.MOON_SHIELD)
        run.helpers[npc] = role
        return npc
    }

    private fun spawnNpc(
        player: Player,
        internalName: String,
        coords: CoordGrid,
        hitpoints: Int? = null,
    ): Npc {
        val id = internalName.asRSCM(RSCMType.NPC)
        val type =
            requireNotNull(ServerCacheManager.getNpc(id)) { "Missing Moon npc: $internalName" }
        val npc = Npc(type, coords)
        if (hitpoints != null) {
            npc.baseHitpointsLvl = hitpoints
            npc.hitpoints = hitpoints
        }
        npc.mode = null
        npc.assignSpawnOwner(player, clock.cycle)
        npcs.add(npc, MOON_NPC_LIFETIME)
        return npc
    }

    private fun clearHelpers(run: Run) {
        run.helpers.keys.toList().forEach { helper ->
            if (helper.isSlotAssigned) npcs.del(helper, Int.MAX_VALUE)
        }
        run.helpers.clear()
        run.highlightedJaguar = null
        run.frozenBlock = null
        run.shield = null
        run.activeClone = null
    }

    private fun deleteHelper(run: Run, npc: Npc) {
        run.helpers.remove(npc)
        if (npc.isSlotAssigned) npcs.del(npc, Int.MAX_VALUE)
    }

    private fun recoverFrozenWeapon(run: Run) {
        run.player.blueMoonWearPrevent = 0
        val weapon = run.frozenWeapon ?: return
        if (restoreExactWeapon(run.player, weapon)) {
            run.frozenWeapon = null
            clearFrozenPersistence(run.player)
            run.player.mes("Blue Moon releases your exact weapon from the ice.")
        }
    }

    private fun restoreExactWeapon(player: Player, weapon: InvObj): Boolean {
        val weaponSlot = Wearpos.RightHand.slot
        if (player.worn[weaponSlot] == null) {
            player.worn[weaponSlot] = InvObj(weapon)
            return true
        }
        val invSlot = player.inv.objs.indexOfFirst { it == null }
        if (invSlot >= 0) {
            player.inv[invSlot] = InvObj(weapon)
            return true
        }
        val bank = player.invMap.getOrPut("inv.bank")
        val bankSlot = bank.objs.indexOfFirst { it == null }
        if (bankSlot >= 0) {
            bank[bankSlot] = InvObj(weapon)
            player.mes(
                "Your weapon slot and inventory were full, so the exact weapon was restored to your bank."
            )
            return true
        }
        player.mes(
            "Make one equipment, inventory, or bank space so your frozen weapon can be restored."
        )
        return false
    }

    private fun persistFrozenWeapon(player: Player, weapon: InvObj) {
        player.attr[FROZEN_WEAPON_ID] = weapon.id
        player.attr[FROZEN_WEAPON_COUNT] = weapon.count
        player.attr[FROZEN_WEAPON_VARS] = weapon.vars
    }

    private fun clearFrozenPersistence(player: Player) {
        player.attr.remove(FROZEN_WEAPON_ID)
        player.attr.remove(FROZEN_WEAPON_COUNT)
        player.attr.remove(FROZEN_WEAPON_VARS)
    }

    private fun transmogInvisible(run: Run) {
        val name =
            when (run.moon) {
                PerilousMoon.Blood -> "npc.pmoon_blood_boss_invis"
                PerilousMoon.Blue -> "npc.pmoon_blue_boss_invis"
                PerilousMoon.Eclipse -> "npc.pmoon_eclipse_boss_invis"
            }
        val type = requireNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)))
        run.boss.transmog(type, Int.MAX_VALUE)
    }

    private fun removeDecorativeShell(moon: PerilousMoon) {
        val shells =
            npcs
                .findAll(org.rsmod.map.zone.ZoneKey.from(moon.center), zoneRadius = 1)
                .filter { it.id == moon.decorativeNpcId && it.isSlotAssigned }
                .toList()
        shells.forEach { shell ->
            shell.movementLocked = true
            npcs.del(shell, Int.MAX_VALUE)
            removedDecorativeShellIds += moon.decorativeNpcId
        }
        if (shells.isNotEmpty()) {
            log.info {
                "[PerilousMoon] removed decorative shell id=${moon.decorativeNpcId} " +
                    "moon=${moon.name} count=${shells.size}"
            }
        }
    }

    private fun resumeIdleIfDue(run: Run) {
        val dueCycle = run.nextIdleCycle ?: return
        if (clock.cycle < dueCycle) return
        run.nextIdleCycle = null
        if (
            !run.boss.isSlotAssigned ||
                run.boss.hitpoints <= 0 ||
                run.state == MoonEncounterState.SUBDUED ||
                run.state == MoonEncounterState.RESETTING
        ) {
            return
        }
        run.boss.anim(run.moon.idleAnimation)
    }

    private fun standingUnderBoss(run: Run): Boolean {
        val sw = run.boss.coords
        val player = run.player.coords
        return player.level == sw.level &&
            player.x in sw.x until sw.x + run.boss.size &&
            player.z in sw.z until sw.z + run.boss.size
    }

    private fun npcCenter(npc: Npc): CoordGrid = npc.coords.translate(npc.size / 2, npc.size / 2)

    private fun shieldProtects(run: Run, shield: Npc): Boolean {
        val center = run.moon.center
        val player = run.player.coords
        val blocker = npcCenter(shield)
        if (!rayCast.hasLineOfSight(center, player, srcWidth = 1, srcLength = 1)) return false
        val vx = (player.x - center.x).toDouble()
        val vz = (player.z - center.z).toDouble()
        val wx = (blocker.x - center.x).toDouble()
        val wz = (blocker.z - center.z).toDouble()
        val lengthSquared = vx * vx + vz * vz
        if (lengthSquared <= 0.0) return false
        val projection = (wx * vx + wz * vz) / lengthSquared
        if (projection !in 0.0..1.0) return false
        val closestX = center.x + projection * vx
        val closestZ = center.z + projection * vz
        val perpendicular = abs(blocker.x - closestX) + abs(blocker.z - closestZ)
        return perpendicular <= 2.0 && player.chebyshevDistance(blocker) <= 3
    }

    private fun eclipseShieldRoute(center: CoordGrid): List<CoordGrid> = buildList {
        val radius = 8
        for (x in center.x - radius..center.x + radius) add(
            CoordGrid(x, center.z - radius, center.level)
        )
        repeat(2) { add(last()) }
        for (z in center.z - radius + 1..center.z + radius) add(
            CoordGrid(center.x + radius, z, center.level)
        )
        repeat(2) { add(last()) }
        for (x in center.x + radius - 1 downTo center.x - radius) add(
            CoordGrid(x, center.z + radius, center.level)
        )
        repeat(2) { add(last()) }
        for (z in center.z + radius - 1 downTo center.z - radius) add(
            CoordGrid(center.x - radius, z, center.level)
        )
        repeat(2) { add(last()) }
    }

    private fun preparePlayer(player: Player) {
        player.combatClearQueue()
        player.tempMoveSpeed = null
        player.clearInteractionRoute()
        player.clearMapFlag()
    }

    private fun verifyDefinitions(moon: PerilousMoon) {
        val type = requireNotNull(ServerCacheManager.getNpc(moon.bossNpc.asRSCM(RSCMType.NPC)))
        check(type.id == moon.bossNpcId && type.size == 5 && type.hitpoints == MOON_MAX_HITPOINTS) {
            "${moon.displayName} cache definition drifted: id=${type.id} size=${type.size} hp=${type.hitpoints}"
        }
        listOf(
                moon.standardAnimation,
                moon.idleAnimation,
                moon.spawnAnimation,
                moon.disappearAnimation,
                moon.appearAnimation,
                moon.subdueAnimation,
            )
            .forEach { animation ->
                requireNotNull(ServerCacheManager.getAnim(animation.asRSCM(RSCMType.SEQ))) {
                    "Missing Moon animation: $animation"
                }
            }
    }

    internal data class Run(
        val player: Player,
        val moon: PerilousMoon,
        val generation: Long,
        var boss: Npc,
        val helpers: LinkedHashMap<Npc, MoonHelperRole> = linkedMapOf(),
        var state: MoonEncounterState,
        var glyphIndex: Int = 0,
        var standardAttacks: Int = 0,
        var nextSpecial: Int = 1,
        var curse: Int = 0,
        var auraRamp: Int = 0,
        var nextAttackCycle: Int,
        var nextCurseDecayCycle: Int,
        var nextIdleCycle: Int? = null,
        var specialStartCycle: Int? = null,
        var specialAge: Int = 0,
        var comboSerial: Int = 0,
        val comboAccuracy: BooleanArray = BooleanArray(3),
        var warnedColdThree: Boolean = false,
        var warnedColdNine: Boolean = false,
        var highlightedJaguar: Npc? = null,
        var jaguarSafeGlyph: CoordGrid? = null,
        var frozenWeapon: InvObj? = null,
        var frozenBlock: Npc? = null,
        var icicleWarning: CoordGrid? = null,
        var icicleImpactCycle: Int? = null,
        var iceBlockHitsAtTile: Int = 0,
        var iceBlockLastTile: CoordGrid? = null,
        val braziersLit: BooleanArray = BooleanArray(2),
        var walkOnlyUntil: Int = Int.MIN_VALUE,
        var shield: Npc? = null,
        var shieldRoute: List<CoordGrid> = emptyList(),
        var activeClone: Npc? = null,
        var cloneParried: Boolean = false,
        var cloneParryDamageQueued: Boolean = false,
        var cloneAttacks: Int = 0,
        var cloneDeadlineCycle: Int = Int.MIN_VALUE,
        var cloneNextSpawnCycle: Int = Int.MIN_VALUE,
        var mechanicDamage: Boolean = false,
        var respawnCycle: Int? = null,
    ) {
        fun owns(npc: Npc): Boolean = npc === boss || helpers.containsKey(npc)
    }
}

private var Player.blueMoonBrazierLeft by intVarBit("varbit.pmoon_boss_blue_brazier_lit_left")

private var Player.blueMoonBrazierRight by intVarBit("varbit.pmoon_boss_blue_brazier_lit_right")

private var Player.blueMoonWearPrevent by intVarBit("varbit.pmoon_boss_blue_wearprevent")

private var Player.moonBossHidden by intVarBit("varbit.pmoon_boss_hide_npc")

private var Player.moonBossHitpoints by intVarBit("varbit.pmoon_boss_hp")

private var Player.moonBossInCombat by intVarBit("varbit.pmoon_boss_in_combat")

private var Player.moonBossCurrentFocus by intVarBit("varbit.pmoon_boss_current_focus")

private var Player.moonBossLastFocus by intVarBit("varbit.pmoon_boss_last_focus")

private var Player.bloodMoonSubdued by intVarBit("varbit.pmoon_boss_blood_dead")

private var Player.blueMoonSubdued by intVarBit("varbit.pmoon_boss_blue_dead")

private var Player.eclipseMoonSubdued by intVarBit("varbit.pmoon_boss_eclipse_dead")

private fun Player.setMoonSubduedVarbit(moon: PerilousMoon, value: Int) {
    when (moon) {
        PerilousMoon.Blood -> bloodMoonSubdued = value
        PerilousMoon.Blue -> blueMoonSubdued = value
        PerilousMoon.Eclipse -> eclipseMoonSubdued = value
    }
}
