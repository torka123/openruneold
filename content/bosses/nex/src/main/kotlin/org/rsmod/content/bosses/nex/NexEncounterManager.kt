package org.rsmod.content.bosses.nex

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ProjAnimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import org.rsmod.api.combat.NpcAttackOverride
import org.rsmod.api.combat.NpcProjectile
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.heal
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.NpcCalculatedProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.NpcPartialProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitImpactHandler
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@Singleton
internal class NexEncounterManager
@Inject
constructor(
    private val clock: MapClock,
    private val npcs: NpcRepository,
    private val players: PlayerList,
    private val locs: LocRepository,
    private val interactions: AiPlayerInteractions,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
    private val combat: NvPCombat,
    private val worldQueues: WorldQueueList,
    private val world: WorldRepository,
    private val rewards: NexRewardService,
) {
    private val log = InlineLogger()
    private var generation = 1L
    private var run: Run? = null
    private var lastWorldTick = Int.MIN_VALUE

    fun startup() {
        validateAssets()
        rewards.validateAssets()
        worldQueues.add(1) { if (run == null) spawnFresh() }
    }

    fun teleportBank(player: Player): Boolean {
        if (player.hitpoints <= 0 || player.isAccessProtected) {
            player.mes("Finish what you are doing before travelling to the Ancient Prison.")
            return false
        }
        leaveAll(player, reason = "travelled to the bank room")
        prepare(player)
        PathingEntityCommon.telejump(player, collision, NEX_BANK_TELEPORT)
        setBarrierState(player)
        player.mes("You arrive in the safe Ancient Prison bank room.")
        return true
    }

    fun enter(player: Player): Boolean {
        val current = run ?: return false
        if (player.hitpoints <= 0 || player.isAccessProtected) {
            player.mes("Finish what you are doing before passing the barrier.")
            return false
        }
        if (current.state !in setOf(NexState.IDLE, NexState.SPAWNING)) {
            player.mes("The barrier is sealed while Nex is being fought.")
            return false
        }
        prepare(player)
        PathingEntityCommon.telejump(player, collision, NEX_ARENA_ENTRY)
        current.participants += player.uid
        current.flags.putIfAbsent(player.uid, NexAchievementFlags())
        player.lootDropDuration = NEX_DROP_DURATION
        setBarrierState(player)
        if (current.state == NexState.IDLE) beginSpawn(current)
        player.mes("You pass into Nex's public chamber.")
        return true
    }

    fun exit(player: Player): Boolean {
        val current = run ?: return false
        if (player.uid !in current.participants) return false
        leave(current, player, teleport = true, reason = "used the Zaros altar exit")
        player.mes("You return to the Ancient Prison bank room.")
        return true
    }

    fun prayAtAltar(player: Player) {
        if (!NEX_CHAMBER_ARENA.contains(player.coords)) {
            player.mes("You cannot reach the power of this altar from here.")
            return
        }
        player.statHeal("stat.prayer", constant = 0, percent = 100)
        player.mes("The altar restores your Prayer points.")
    }

    fun peek(player: Player) {
        val current = run
        val count =
            current?.participants?.count { uid ->
                uid.resolve(players)?.let { activeParticipant(current, it) } == true
            } ?: 0
        player.mes(
            "Nex's public chamber contains $count player${if (count == 1) "" else "s"}; " +
                "state: ${current?.state ?: NexState.RESETTING}."
        )
    }

    fun tick(player: Player) {
        val chamber = run
        if (chamber != null && player.uid in chamber.participants) {
            if (!activeParticipant(chamber, player))
                leave(chamber, player, teleport = false, reason = "left the chamber")
            else tickPlayerEffects(chamber, player)
        }
        setBarrierState(player)
        if (lastWorldTick != clock.cycle) {
            lastWorldTick = clock.cycle
            activeRuns().forEach(::tickWorld)
        }
    }

    fun attack(access: StandardNpcAccess, target: Player) {
        val npc = access.npc
        val current = encounterFor(npc) ?: return
        if (target.uid !in current.participants || !activeParticipant(current, target)) return
        when {
            npc === current.boss -> attackNex(access, current, target)
            current.bodyguards.values.any { it === npc } -> attackBodyguard(access, current, target)
            npc in current.reavers -> attackReaver(access, target)
        }
    }

    fun modifyNpcHit(player: Player, npc: Npc, hit: HitBuilder) {
        val current = encounterFor(npc)
        if (
            current == null ||
                player.uid !in current.participants ||
                !activeParticipant(current, player) ||
                !hit.isFromPlayer
        ) {
            hit.damage = 0
            return
        }
        when {
            npc === current.boss -> modifyNexHit(current, player, hit)
            current.bodyguards.values.any { it === npc } ->
                modifyBodyguardHit(current, player, npc, hit)
            npc in current.reavers -> modifyReaverHit(current, player, npc, hit)
            else -> hit.damage = 0
        }
    }

    fun handleDeath(npc: Npc): DeathKind {
        val current = encounterFor(npc) ?: return DeathKind.UNRELATED
        if (npc === current.boss) {
            if (current.state != NexState.ZAROS || current.deathStarted) return DeathKind.UNRELATED
            current.deathStarted = true
            current.state = NexState.DYING
            current.boss.transmog(requireNpc(NEX_DYING_ID), Int.MAX_VALUE)
            current.boss.anim(anim(9184))
            participants(current).forEach { it.combatClearQueue() }
            return DeathKind.NEX
        }
        val guard = current.bodyguards.entries.firstOrNull { it.value === npc }?.key
        if (guard != null) {
            if (current.state == guard && current.calledGuardResolved.add(guard))
                advanceFromBodyguard(current, guard)
            return DeathKind.BODYGUARD
        }
        if (current.reavers.remove(npc)) return DeathKind.REAVER
        return DeathKind.UNRELATED
    }

    fun finishDeath(npc: Npc, kind: DeathKind) {
        val current = encounterFor(npc) ?: return
        if (kind != DeathKind.NEX || current.boss !== npc || current.rewardFinished) return
        current.rewardFinished = true
        val generationAtDeath = current.generation
        worldQueues.add(3) {
            if (!isActiveRun(current) || current.generation != generationAtDeath) return@add
            wrath(current)
            rewards.reward(
                current.contributions.filterKeys { it in current.participants },
                current.rewarded,
                current.arena.spawn,
            )
            current.state = NexState.RESETTING
            current.resetCycle = clock.cycle + NEX_RESPAWN_TICKS
        }
    }

    fun isEncounterNpc(npc: Npc): Boolean = encounterFor(npc) != null

    fun attackDenial(player: Player, npc: Npc): String? {
        val current = encounterFor(npc) ?: return "This Nex encounter is resetting."
        if (player.uid !in current.participants)
            return "Pass the barrier before attacking this encounter."
        if (!activeParticipant(current, player)) return "You are no longer inside Nex's chamber."
        if (npc === current.boss) {
            return when (current.state) {
                NexState.SMOKE,
                NexState.SHADOW,
                NexState.BLOOD,
                NexState.ICE,
                NexState.ZAROS -> null
                NexState.FUMUS,
                NexState.UMBRA,
                NexState.CRUOR,
                NexState.GLACIES ->
                    if (current.boss.hitpoints > (current.callFloor ?: Int.MAX_VALUE)) null
                    else "Nex is protected until her bodyguard falls."
                else -> "Nex cannot be attacked right now."
            }
        }
        val guard = current.bodyguards.entries.firstOrNull { it.value === npc }?.key
        if (guard != null && guard != current.state) return "That bodyguard is protected by Nex."
        return null
    }

    fun isParticipant(player: Player): Boolean = activeRuns().any { player.uid in it.participants }

    fun isChamberParticipant(player: Player): Boolean =
        player.uid in (run?.participants ?: emptySet())

    fun playerDied(player: Player) {
        activeRuns()
            .filter { player.uid in it.participants }
            .forEach { current ->
                current.flags[player.uid]?.playerDeath = true
                leave(current, player, teleport = false, reason = "player death")
            }
    }

    fun playerLoggedOut(player: Player) = leaveAll(player, reason = "logout or disconnect")

    fun breakIce(player: Player): Boolean {
        val current = run ?: return false
        if (player.uid !in current.participants || current.state != NexState.ICE) return false
        val prisoner = current.prisonedUntil.keys.minByOrNull { it.packed } ?: return false
        current.prisonedUntil.remove(prisoner)
        player.mes("You break the ice prison before it can shatter.")
        return true
    }

    private fun tickWorld(current: Run) {
        if (!isActiveRun(current)) return
        current.participants.removeIf { uid ->
            uid.resolve(players)?.let { activeParticipant(current, it) } != true
        }
        if (current.state == NexState.SPAWNING && clock.cycle >= current.stateReadyCycle)
            activateSmoke(current)
        if (current.state == NexState.RESETTING && clock.cycle >= current.resetCycle) reset(current)
        if (current.state in activeStates && current.participants.isEmpty()) {
            current.state = NexState.RESETTING
            current.resetCycle = clock.cycle + 1
        }
        if (current.state in combatStates) keepAggressive(current)
        val defenceFloor = current.boss.baseDefenceLvl - 10
        if (current.boss.defenceLvl < defenceFloor) current.boss.defenceLvl = defenceFloor
    }

    private fun beginSpawn(current: Run) {
        current.state = NexState.SPAWNING
        current.startingTeamSize = current.participants.size
        current.stateReadyCycle = clock.cycle + 5
        current.boss.transmog(requireNpc(NEX_SPAWNING_ID), Int.MAX_VALUE)
        current.boss.anim(anim(9203))
        current.boss.movementLocked = true
        announce(current, "AT LAST!")
    }

    private fun activateSmoke(current: Run) {
        if (current.state != NexState.SPAWNING) return
        current.boss.transmog(requireNpc(NEX_ID), Int.MAX_VALUE)
        current.state = NexState.SMOKE
        current.boss.movementLocked = false
        current.boss.apRangeOverride = 10
        announce(current, "Fill my soul with smoke!")
        keepAggressive(current)
    }

    private fun attackNex(access: StandardNpcAccess, current: Run, target: Player) {
        if (current.state !in combatStates || clock.cycle < current.specialLockedUntil) return
        if (
            current.state in calledStates &&
                current.boss.hitpoints <= (current.callFloor ?: Int.MAX_VALUE)
        )
            return
        if (current.state != NexState.ZAROS) {
            val special =
                NexRules.special(current.state, current.attackCounter, current.specialIndex)
            if (special != null) {
                current.attackCounter = 0
                current.specialIndex++
                startSpecial(current, special, target)
                return
            }
        }
        if (current.state == NexState.ZAROS) {
            setZarosOverhead(current)
            attackZaros(access, current, target)
            current.zarosAttackCounter++
        } else {
            val inMelee = distance(current.boss.coords, target.coords) <= 1
            val continuingMelee =
                current.meleeChainRemaining > 0 && current.meleeChainTarget == target.uid
            val melee = inMelee && (continuingMelee || random.of(3) == 0)
            if (melee) {
                if (continuingMelee) {
                    current.meleeChainRemaining--
                } else {
                    current.meleeChainRemaining = 2
                    current.meleeChainTarget = target.uid
                }
                if (current.meleeChainRemaining == 0) current.meleeChainTarget = null
                meleeAttack(access, current, target, 27, 80)
            } else {
                current.meleeChainRemaining = 0
                current.meleeChainTarget = null
                if (current.state == NexState.SHADOW) rangedAttack(access, current, target, 60)
                else magicAttack(access, current, target, 33)
            }
            current.attackCounter++
        }
    }

    private fun attackZaros(access: StandardNpcAccess, current: Run, target: Player) {
        val inMelee = distance(current.boss.coords, target.coords) <= 1
        val continuingMelee =
            current.meleeChainRemaining > 0 && current.meleeChainTarget == target.uid
        val melee = inMelee && (continuingMelee || random.of(3) == 0)
        if (melee) {
            if (continuingMelee) current.meleeChainRemaining--
            else {
                current.meleeChainRemaining = 2
                current.meleeChainTarget = target.uid
            }
            if (current.meleeChainRemaining == 0) current.meleeChainTarget = null
            meleeAttack(access, current, target, 30, 66)
        } else {
            current.meleeChainRemaining = 0
            current.meleeChainTarget = null
            magicAttack(access, current, target, 33, finalPhase = true)
        }
    }

    private fun meleeAttack(
        access: StandardNpcAccess,
        current: Run,
        target: Player,
        maxHit: Int,
        prayerReduction: Int,
        attackRate: Int = NEX_ATTACK_RATE,
    ) {
        combat.attack(
            access,
            target,
            CombatAttack.NpcMelee(MeleeAttackType.Crush),
            NpcAttackOverride(
                attackAnimation = anim(9180),
                maxHit = maxHit,
                attackRate = attackRate,
                hitModifier = NpcPartialProtectionPrayerPlayerHitModifier(prayerReduction),
                onImpact = { _, impact -> soulSplitHeal(current, impact.damage) },
            ),
        )
    }

    private fun magicAttack(
        access: StandardNpcAccess,
        current: Run,
        target: Player,
        maxHit: Int,
        finalPhase: Boolean = false,
        attackRate: Int = NEX_ATTACK_RATE,
    ) {
        val protectedAtLaunch = target.vars["varbit.prayer_protectfrommagic"] == 1
        val stateAtLaunch = current.state
        val reduction = if (stateAtLaunch == NexState.BLOOD) 66 else 50
        val projectileId =
            if (finalPhase) 2007
            else
                when (current.state) {
                    NexState.SMOKE -> 1997
                    NexState.BLOOD -> 2000
                    NexState.ICE -> 2004
                    else -> 1999
                }
        val modifier =
            if (finalPhase) finalMagicModifier(protectedAtLaunch)
            else NpcCalculatedProtectionPrayerPlayerHitModifier(protectedAtLaunch, reduction)
        combat.attack(
            access,
            target,
            CombatAttack.NpcMagic(maxHit),
            NpcAttackOverride(
                attackAnimation = anim(if (finalPhase) 9188 else 9189),
                maxHit = maxHit,
                attackRate = attackRate,
                projectile =
                    NpcProjectile(
                        spotInternal(projectileId),
                        projectileType("projanim.magic_spell"),
                    ),
                applyModifierOnImpact = finalPhase,
                hitModifier = modifier,
                onImpact = { _, impact ->
                    target.spotanim(
                        spotInternal(if (finalPhase) 2008 else impactFor(stateAtLaunch))
                    )
                    applyMagicImpact(
                        current,
                        target,
                        stateAtLaunch,
                        impact.damage,
                        protectedAtLaunch,
                    )
                },
            ),
        )
        splashSecondaries(current, target, maxHit, HitType.Magic, reduction, stateAtLaunch)
    }

    private fun rangedAttack(access: StandardNpcAccess, current: Run, target: Player, maxHit: Int) {
        combat.attack(
            access,
            target,
            CombatAttack.NpcRanged(RangedAttackType.Standard),
            NpcAttackOverride(
                attackAnimation = anim(9189),
                maxHit = maxHit,
                attackRate = NEX_ATTACK_RATE,
                projectile = NpcProjectile(spotInternal(1999), projectileType("projanim.arrow")),
                hitModifier = NpcPartialProtectionPrayerPlayerHitModifier(50),
                onImpact = { _, impact ->
                    soulSplitHeal(current, impact.damage)
                    if (impact.damage > 0) {
                        target.statSub(
                            "stat.prayer",
                            constant = max(1, impact.damage / 10),
                            percent = 0,
                        )
                    }
                },
            ),
        )
    }

    private fun splashSecondaries(
        current: Run,
        primary: Player,
        maxHit: Int,
        type: HitType,
        reduction: Int,
        stateAtLaunch: NexState,
    ) {
        participants(current)
            .filter { it !== primary && distance(it.coords, primary.coords) <= 1 }
            .forEach { secondary ->
                val protectedAtLaunch = secondary.vars["varbit.prayer_protectfrommagic"] == 1
                val modifier =
                    if (stateAtLaunch == NexState.ZAROS) finalMagicModifier(protectedAtLaunch)
                    else NpcPartialProtectionPrayerPlayerHitModifier(reduction)
                secondary.queueHit(
                    source = current.boss,
                    delay = 3,
                    type = type,
                    damage = random.of(0..maxHit),
                    modifier = modifier,
                    impactHandler =
                        HitImpactHandler { impact ->
                            secondary.spotanim(
                                spotInternal(
                                    if (stateAtLaunch == NexState.ZAROS) 2008
                                    else impactFor(stateAtLaunch)
                                )
                            )
                            applyMagicImpact(
                                current,
                                secondary,
                                stateAtLaunch,
                                impact.damage,
                                protectedAtLaunch,
                            )
                        },
                )
            }
    }

    private fun applyMagicImpact(
        current: Run,
        target: Player,
        stateAtLaunch: NexState,
        damage: Int,
        protectedAtLaunch: Boolean,
    ) {
        soulSplitHeal(current, damage)
        if (damage <= 0) return
        when (stateAtLaunch) {
            NexState.SMOKE -> CombatEffects.poison(target, 8)
            NexState.BLOOD -> {
                current.boss.heal(damage)
                target.statSub("stat.prayer", constant = max(1, damage / 2), percent = 0)
            }
            NexState.ICE -> {
                target.statSub("stat.prayer", constant = max(1, damage / 3), percent = 0)
                if (!protectedAtLaunch) CombatEffects.freeze(target, 10)
            }
            NexState.ZAROS ->
                target.statSub("stat.prayer", constant = max(1, damage / 10), percent = 0)
            else -> Unit
        }
    }

    private fun finalMagicModifier(protectedAtLaunch: Boolean): PlayerHitModifier =
        PlayerHitModifier { target ->
            NpcBypassProtectionPrayerPlayerHitModifier.run { modify(target) }
            val reduction =
                when {
                    protectedAtLaunch -> 50
                    target.vars["varbit.prayer_protectfrommagic"] == 1 -> 25
                    else -> 0
                }
            damage = damage * (100 - reduction) / 100
        }

    private fun attackBodyguard(access: StandardNpcAccess, current: Run, target: Player) {
        val state =
            current.bodyguards.entries.firstOrNull { it.value === access.npc }?.key ?: return
        if (current.state != state) return
        combat.attack(
            access,
            target,
            CombatAttack.NpcMagic(20),
            NpcAttackOverride(
                attackAnimation = anim(811),
                maxHit = 20,
                attackRate = 5,
                projectile =
                    NpcProjectile(spotInternal(1999), projectileType("projanim.magic_spell")),
                hitModifier = NpcPartialProtectionPrayerPlayerHitModifier(50),
            ),
        )
    }

    private fun attackReaver(access: StandardNpcAccess, target: Player) {
        combat.attack(
            access,
            target,
            CombatAttack.NpcMagic(18),
            NpcAttackOverride(
                attackAnimation = anim(811),
                maxHit = 18,
                attackRate = 5,
                hitModifier = NpcPartialProtectionPrayerPlayerHitModifier(50),
            ),
        )
    }

    private fun modifyNexHit(current: Run, player: Player, hit: HitBuilder) {
        if (current.state == NexState.BLOOD && clock.cycle < current.siphonUntil) {
            current.boss.heal(hit.damage.coerceAtLeast(0))
            current.flags[player.uid]?.bloodSiphonHeal = hit.damage > 0
            hit.damage = 0
            return
        }
        var accepted =
            when {
                current.state in phaseStates ->
                    NexRules.phaseDamage(current.boss.hitpoints, hit.damage, current.state)
                current.state in calledStates -> {
                    val floor = current.callFloor ?: current.boss.hitpoints
                    if (current.boss.hitpoints <= floor) 0
                    else
                        (NexRules.softCap(hit.damage) / 10)
                            .coerceAtLeast(if (hit.damage > 0) 1 else 0)
                            .coerceAtMost(current.boss.hitpoints - floor)
                }
                current.state == NexState.ZAROS ->
                    NexRules.softCap(hit.damage).coerceAtMost(current.boss.hitpoints)
                else -> 0
            }
        if (
            current.state == NexState.ZAROS &&
                current.overhead == NexOverhead.DEFLECT_MELEE &&
                hit.type == HitType.Melee
        ) {
            accepted /= 2
            if (accepted > 0) {
                player.queueHit(
                    source = current.boss,
                    delay = 1,
                    type = HitType.Typeless,
                    damage = accepted,
                    modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                )
            }
        }
        hit.damage = accepted
        record(current, player, accepted)
        val threshold = NexRules.threshold(current.state)
        if (threshold != null && current.boss.hitpoints - accepted <= threshold)
            callBodyguard(current, current.state)
    }

    private fun modifyBodyguardHit(current: Run, player: Player, npc: Npc, hit: HitBuilder) {
        val state = current.bodyguards.entries.firstOrNull { it.value === npc }?.key
        if (state == null || current.state != state) {
            hit.damage = 0
            return
        }
        hit.damage = NexRules.softCap(hit.damage).coerceAtMost(npc.hitpoints)
        record(current, player, hit.damage)
    }

    private fun modifyReaverHit(current: Run, player: Player, npc: Npc, hit: HitBuilder) {
        if (npc !in current.reavers || current.state != NexState.BLOOD) {
            hit.damage = 0
            return
        }
        hit.damage = hit.damage.coerceIn(0, npc.hitpoints)
        record(current, player, hit.damage)
        val healing = NexRules.reaverHeal(hit.damage)
        if (healing > 0) {
            player.statHeal("stat.hitpoints", constant = healing, percent = 0)
            current.flags[player.uid]?.bloodSiphonHeal = true
        }
    }

    private fun callBodyguard(current: Run, phase: NexState) {
        val called = NexRules.calledBodyguard(phase) ?: return
        if (current.state != phase) return
        current.state = called
        current.callFloor = NexRules.lowerFloor(phase)
        current.attackCounter = 0
        current.meleeChainRemaining = 0
        current.bodyguards[called]?.let { guard ->
            nearestParticipant(current, guard.coords)?.let { guard.apPlayer2(it, interactions) }
        }
        announce(
            current,
            "${called.name.lowercase().replaceFirstChar { it.uppercase() }}, don't fail me!",
        )
    }

    private fun advanceFromBodyguard(current: Run, called: NexState) {
        val next = NexRules.nextPhase(called) ?: return
        current.state = next
        current.callFloor = null
        current.attackCounter = 0
        current.meleeChainRemaining = 0
        current.specialLockedUntil = clock.cycle + 2
        current.boss.transmog(requireNpc(NEX_ID), Int.MAX_VALUE)
        if (next == NexState.ZAROS) {
            current.boss.heal(500)
            current.boss.anim(anim(9179))
            announce(current, "NOW, THE POWER OF ZAROS!")
        } else {
            val phaseCall =
                when (next) {
                    NexState.SHADOW -> "Darken my shadow!"
                    NexState.BLOOD -> "Flood my lungs with blood!"
                    NexState.ICE -> "Infuse me with the power of ice!"
                    else -> error("Unexpected Nex phase after $called: $next")
                }
            announce(current, phaseCall)
        }
        keepAggressive(current)
    }

    private fun startSpecial(current: Run, special: NexSpecial, target: Player) {
        current.specialLockedUntil = clock.cycle + 5
        when (special) {
            NexSpecial.CHOKE -> choke(current, target)
            NexSpecial.DRAG -> drag(current, target)
            NexSpecial.SMOKE_DASH -> smokeDash(current)
            NexSpecial.SHADOW_SMASH -> shadowSmash(current)
            NexSpecial.EMBRACE_DARKNESS -> darkness(current)
            NexSpecial.BLOOD_SIPHON -> bloodSiphon(current)
            NexSpecial.BLOOD_SACRIFICE -> bloodSacrifice(current, target)
            NexSpecial.CONTAINMENT -> containment(current)
            NexSpecial.ICE_PRISON -> icePrison(current, target)
        }
    }

    private fun choke(current: Run, target: Player) {
        announce(current, "Let the virus flow through you!")
        current.coughUntil[target.uid] = clock.cycle + 100
        target.mes("<col=ff0000>You have been infected by Nex's smoke.</col>")
    }

    private fun drag(current: Run, target: Player) {
        announce(current, "There is... NO ESCAPE!")
        current.boss.anim(anim(9182))
        val castGeneration = current.generation
        worldQueues.add(2) {
            if (!validDelayed(current, castGeneration, NexState.SMOKE, target)) return@add
            PathingEntityCommon.telejump(target, collision, current.boss.coords.translateX(-1))
            clearProtectionPrayers(target)
            target.queueHit(
                source = current.boss,
                delay = 1,
                type = HitType.Typeless,
                damage = random.of(0..30),
                modifier = NpcBypassProtectionPrayerPlayerHitModifier,
            )
        }
    }

    private fun smokeDash(current: Run) {
        announce(current, "There is... NO ESCAPE!")
        current.boss.anim(anim(9178))
        val castGeneration = current.generation
        val routes =
            listOf(
                CoordGrid(2912, 5202),
                CoordGrid(2935, 5202),
                CoordGrid(2924, 5191),
                CoordGrid(2924, 5214),
            )
        val destination = routes[random.of(routes.size)]
        val pullTiles =
            listOf(
                CoordGrid(2923, 5202),
                CoordGrid(2927, 5202),
                CoordGrid(2924, 5201),
                CoordGrid(2924, 5205),
            )
        participants(current).forEachIndexed { index, player ->
            PathingEntityCommon.telejump(player, collision, pullTiles[index % pullTiles.size])
            clearProtectionPrayers(player)
            player.mes("Nex drags you towards the centre of the chamber!")
        }
        worldQueues.add(2) {
            if (!validDelayed(current, castGeneration, NexState.SMOKE)) return@add
            current.boss.teleport(collision, destination)
            participants(current).forEach { player ->
                if (player.coords.x == destination.x || player.coords.z == destination.z) {
                    player.queueHit(
                        source = current.boss,
                        delay = 1,
                        type = HitType.Typeless,
                        damage = random.of(0..50),
                        modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                    )
                    current.flags[player.uid]?.smokeDashHit = true
                }
            }
        }
        worldQueues.add(4) {
            if (validDelayed(current, castGeneration, NexState.SMOKE))
                current.boss.teleport(collision, current.arena.spawn)
        }
    }

    private fun shadowSmash(current: Run) {
        announce(current, "Fear the shadow!")
        current.boss.anim(anim(9186))
        val castGeneration = current.generation
        participants(current).forEach { player ->
            val tile = player.coords
            spawnLoc(tile, SHADOW_SMASH_LOC, 3)?.let { current.temporaryLocs += it }
            worldQueues.add(3) {
                if (
                    !validDelayed(current, castGeneration, NexState.SHADOW, player) ||
                        player.coords != tile
                )
                    return@add
                player.queueHit(
                    source = current.boss,
                    delay = 1,
                    type = HitType.Typeless,
                    damage = random.of(0..50),
                    modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                )
                current.flags[player.uid]?.shadowSmashHit = true
            }
        }
    }

    private fun darkness(current: Run) {
        announce(current, "Embrace darkness!")
        current.boss.anim(anim(9187))
        current.darknessUntil = clock.cycle + 10
    }

    private fun bloodSiphon(current: Run) {
        announce(current, "A siphon will solve this!")
        current.boss.anim(anim(9183))
        current.siphonUntil = clock.cycle + 8
        val offsets = listOf(-4 to -4, 0 to -5, 4 to -4, 5 to 0, 4 to 4, 0 to 5, -4 to 4, -5 to 0)
        repeat(participants(current).size.coerceIn(1, 8)) { index ->
            val offset = offsets[index]
            val reaver =
                spawnNpc(
                    BLOOD_REAVER_ID,
                    current.arena.spawn.translate(offset.first, offset.second),
                    NEX_REAVER_HITPOINTS,
                )
            current.reavers += reaver
            nearestParticipant(current, reaver.coords)?.let { reaver.apPlayer2(it, interactions) }
        }
    }

    private fun bloodSacrifice(current: Run, target: Player) {
        announce(current, "I demand a blood sacrifice!")
        target.mes("<col=ff0000>Nex has marked you for a blood sacrifice. Run away!</col>")
        val castGeneration = current.generation
        worldQueues.add(8) {
            if (!validDelayed(current, castGeneration, NexState.BLOOD, target)) return@add
            if (distance(current.boss.coords, target.coords) < 7) {
                target.queueHit(
                    source = current.boss,
                    delay = 1,
                    type = HitType.Typeless,
                    damage = random.of(0..80),
                    modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                    impactHandler = HitImpactHandler { impact -> current.boss.heal(impact.damage) },
                )
                target.statSub("stat.prayer", constant = 10, percent = 0)
            }
        }
    }

    private fun containment(current: Run) {
        announce(current, "Contain this!")
        current.boss.anim(anim(9186))
        val castGeneration = current.generation
        worldQueues.add(3) {
            if (!validDelayed(current, castGeneration, NexState.ICE)) return@add
            participants(current).forEach { player ->
                if (distance(current.boss.coords, player.coords) <= 2) {
                    clearProtectionPrayers(player)
                    player.queueHit(
                        source = current.boss,
                        delay = 1,
                        type = HitType.Typeless,
                        damage = random.of(0..60),
                        modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                    )
                    current.flags[player.uid]?.iceSpecialHit = true
                }
            }
        }
    }

    private fun icePrison(current: Run, target: Player) {
        announce(current, "Die now, in a prison of ice!")
        target.mes(
            "<col=ff0000>Nex has trapped you in an ice prison. Allies can break the icicles.</col>"
        )
        val castGeneration = current.generation
        adjacentTiles(target.coords).forEachIndexed { index, tile ->
            spawnLoc(tile, if (index % 2 == 0) ICE_PRISON_LOC else ICE_PRISON_ALT_LOC, 5)?.let {
                current.temporaryLocs += it
            }
        }
        current.prisonedUntil[target.uid] = clock.cycle + 5
        worldQueues.add(5) {
            if (!validDelayed(current, castGeneration, NexState.ICE, target)) return@add
            if ((current.prisonedUntil[target.uid] ?: 0) <= clock.cycle) {
                target.queueHit(
                    source = current.boss,
                    delay = 1,
                    type = HitType.Typeless,
                    damage = random.of(0..75),
                    modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                )
                current.flags[target.uid]?.iceSpecialHit = true
            }
            current.prisonedUntil.remove(target.uid)
        }
    }

    private fun tickPlayerEffects(current: Run, player: Player) {
        val coughEnd = current.coughUntil[player.uid]
        if (coughEnd != null) {
            if (clock.cycle >= coughEnd) current.coughUntil.remove(player.uid)
            else if (clock.cycle % 2 == 0) {
                player.statSub("stat.prayer", constant = 2, percent = 0)
                listOf("stat.attack", "stat.strength", "stat.defence", "stat.ranged", "stat.magic")
                    .forEach { player.statSub(it, constant = 2, percent = 0) }
                participants(current)
                    .filter { it !== player && distance(it.coords, player.coords) <= 1 }
                    .forEach { current.coughUntil.putIfAbsent(it.uid, clock.cycle + 100) }
            }
        }
        if (
            clock.cycle < current.darknessUntil &&
                distance(current.boss.coords, player.coords) <= 5 &&
                clock.cycle % 2 == 0
        ) {
            player.queueHit(
                source = current.boss,
                delay = 1,
                type = HitType.Typeless,
                damage = random.of(0..8),
                modifier = NpcBypassProtectionPrayerPlayerHitModifier,
            )
        }
    }

    private fun setZarosOverhead(current: Run) {
        val overhead = NexRules.zarosOverhead(current.zarosAttackCounter)
        if (current.overhead == overhead) return
        current.overhead = overhead
        val id =
            when (overhead) {
                NexOverhead.SOUL_SPLIT -> NEX_SOUL_SPLIT_ID
                NexOverhead.DEFLECT_MELEE -> NEX_DEFLECT_ID
                NexOverhead.NONE -> NEX_ID
            }
        current.boss.transmog(requireNpc(id), Int.MAX_VALUE)
    }

    private fun soulSplitHeal(current: Run, damage: Int) {
        if (
            current.state == NexState.ZAROS &&
                current.overhead == NexOverhead.SOUL_SPLIT &&
                damage > 0
        ) {
            current.boss.heal(damage / 2)
        }
    }

    private fun wrath(current: Run) {
        announce(current, "Taste my wrath!")
        participants(current).forEach { player ->
            if (distance(current.arena.spawn, player.coords) <= 5) {
                player.queueHit(
                    source = current.boss,
                    delay = 1,
                    type = HitType.Typeless,
                    damage = random.of(0..55),
                    modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                )
            }
        }
    }

    private fun reset(current: Run) {
        when {
            run === current -> {
                cleanup(current)
                spawnFresh()
            }
        }
    }

    private fun spawnFresh() {
        val boss = spawnNpc(NEX_ID, NEX_SPAWN, NEX_MAX_HITPOINTS)
        boss.mode = null
        boss.movementLocked = true
        boss.apRangeOverride = 10
        boss.apRequiresLineOfSight = true
        val bodyguards =
            linkedMapOf(
                NexState.FUMUS to
                    spawnNpc(FUMUS_ID, CoordGrid(2912, 5216), NEX_BODYGUARD_HITPOINTS),
                NexState.UMBRA to
                    spawnNpc(UMBRA_ID, CoordGrid(2937, 5216), NEX_BODYGUARD_HITPOINTS),
                NexState.CRUOR to
                    spawnNpc(CRUOR_ID, CoordGrid(2937, 5190), NEX_BODYGUARD_HITPOINTS),
                NexState.GLACIES to
                    spawnNpc(GLACIES_ID, CoordGrid(2912, 5190), NEX_BODYGUARD_HITPOINTS),
            )
        bodyguards.values.forEach {
            it.mode = null
            it.movementLocked = true
            it.apRangeOverride = 10
            it.apRequiresLineOfSight = true
        }
        run =
            Run(
                generation = generation++,
                arena = NEX_CHAMBER_ARENA,
                boss = boss,
                bodyguards = bodyguards,
            )
        log.info { "[Nex] spawned generation=${run?.generation} state=IDLE" }
    }

    private fun spawnNpc(id: Int, tile: CoordGrid, hitpoints: Int): Npc {
        val npc = Npc(requireNpc(id), tile)
        npc.baseHitpointsLvl = hitpoints
        npc.hitpoints = hitpoints
        npc.respawns = false
        npcs.add(npc, Int.MAX_VALUE)
        return npc
    }

    private fun cleanup(current: Run) {
        (listOf(current.boss) + current.bodyguards.values + current.reavers).forEach {
            if (it.isSlotAssigned) npcs.del(it, Int.MAX_VALUE)
        }
        current.temporaryLocs.forEach { locs.del(it, Int.MAX_VALUE) }
        current.participants
            .mapNotNull { it.resolve(players) }
            .forEach { it.lootDropDuration = null }
        current.reavers.clear()
        current.temporaryLocs.clear()
    }

    private fun leave(current: Run, player: Player, teleport: Boolean, reason: String) {
        if (!current.participants.remove(player.uid)) return
        current.coughUntil.remove(player.uid)
        current.prisonedUntil.remove(player.uid)
        player.lootDropDuration = null
        prepare(player)
        if (teleport && player.isSlotAssigned)
            PathingEntityCommon.telejump(player, collision, NEX_BANK_RETURN)
        setBarrierState(player)
        log.info {
            "[Nex:${current.arena.name}] player=${player.displayName} " +
                "generation=${current.generation} leave=$reason"
        }
    }

    private fun leaveAll(player: Player, reason: String) {
        activeRuns()
            .filter { player.uid in it.participants }
            .forEach { leave(it, player, teleport = false, reason = reason) }
    }

    private fun keepAggressive(current: Run) {
        if (
            current.boss.isSlotAssigned &&
                current.boss.mode == null &&
                current.state !in calledStates
        ) {
            nearestParticipant(current, current.boss.coords)?.let {
                current.boss.apPlayer2(it, interactions)
            }
        }
        if (current.state in calledStates) {
            current.bodyguards[current.state]?.let { guard ->
                if (guard.isSlotAssigned && guard.mode == null) {
                    nearestParticipant(current, guard.coords)?.let {
                        guard.apPlayer2(it, interactions)
                    }
                }
            }
        }
        current.reavers.forEach { reaver ->
            if (reaver.isSlotAssigned && reaver.mode == null) {
                nearestParticipant(current, reaver.coords)?.let {
                    reaver.apPlayer2(it, interactions)
                }
            }
        }
    }

    private fun record(current: Run, player: Player, damage: Int) {
        if (damage <= 0) return
        val contribution =
            current.contributions.getOrPut(player.uid) { NexContribution(firstCycle = clock.cycle) }
        contribution.damage =
            (contribution.damage.toLong() + damage).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        contribution.firstCycle = minOf(contribution.firstCycle, clock.cycle)
    }

    private fun activeRuns(): List<Run> = listOfNotNull(run)

    private fun isActiveRun(current: Run): Boolean = run === current

    private fun encounterFor(npc: Npc): Run? =
        activeRuns().firstOrNull { current ->
            npc === current.boss ||
                current.bodyguards.values.any { it === npc } ||
                npc in current.reavers
        }

    private fun activeParticipant(current: Run, player: Player): Boolean =
        player.isSlotAssigned &&
            !player.pendingLogout &&
            !player.loggingOut &&
            player.hitpoints > 0 &&
            current.arena.contains(player.coords)

    private fun participants(current: Run): List<Player> =
        current.participants
            .mapNotNull { it.resolve(players) }
            .filter { activeParticipant(current, it) }

    private fun nearestParticipant(current: Run, from: CoordGrid): Player? =
        participants(current)
            .minWithOrNull(compareBy<Player> { distance(from, it.coords) }.thenBy { it.uid.packed })

    private fun distance(a: CoordGrid, b: CoordGrid): Int = max(abs(a.x - b.x), abs(a.z - b.z))

    private fun validDelayed(
        current: Run,
        generation: Long,
        state: NexState,
        player: Player? = null,
    ): Boolean =
        isActiveRun(current) &&
            current.generation == generation &&
            current.state == state &&
            current.boss.isSlotAssigned &&
            (player == null ||
                (player.uid in current.participants && activeParticipant(current, player)))

    private fun spawnLoc(tile: CoordGrid, id: Int, duration: Int): LocInfo? {
        val type = ServerCacheManager.getObject(id) ?: return null
        return locs.add(tile, type, duration, LocAngle.North, LocShape.CentrepieceStraight)
    }

    private fun adjacentTiles(tile: CoordGrid): List<CoordGrid> =
        listOf(
            tile.translate(-1, -1),
            tile.translate(0, -1),
            tile.translate(1, -1),
            tile.translate(-1, 0),
            tile.translate(1, 0),
            tile.translate(-1, 1),
            tile.translate(0, 1),
            tile.translate(1, 1),
        )

    private fun clearProtectionPrayers(player: Player) {
        VarPlayerIntMapSetter.set(player, "varbit.prayer_protectfrommelee", 0)
        VarPlayerIntMapSetter.set(player, "varbit.prayer_protectfrommagic", 0)
        VarPlayerIntMapSetter.set(player, "varbit.prayer_protectfrommissiles", 0)
    }

    private fun setBarrierState(player: Player) {
        val state = run?.state
        val busy =
            state != null && state !in setOf(NexState.IDLE, NexState.SPAWNING, NexState.RESETTING)
        VarPlayerIntMapSetter.set(player, "varbit.nex_barrier", if (busy) 2 else 0)
    }

    private fun announce(current: Run, message: String) {
        participants(current).forEach { it.mes("<col=7f00ff>Nex: $message</col>") }
    }

    private fun prepare(player: Player) {
        player.combatClearQueue()
        player.clearInteraction()
        player.resetAnim()
    }

    private fun impactFor(state: NexState): Int =
        when (state) {
            NexState.SMOKE -> 1998
            NexState.BLOOD -> 2001
            NexState.ICE -> 2005
            else -> 2008
        }

    private fun anim(id: Int): String =
        requireNotNull(RSCM.getReverseMapping(RSCMType.SEQ, id)) { "Missing Nex sequence $id" }

    private fun spotInternal(id: Int): String =
        requireNotNull(RSCM.getReverseMapping(RSCMType.SPOTANIM, id)) { "Missing Nex spotanim $id" }

    private fun projectileType(internal: String): ProjAnimType =
        requireNotNull(ServerCacheManager.getProjectile(internal.asRSCM(RSCMType.PROJANIM))) {
            "Missing Nex projectile controller: $internal"
        }

    private fun requireNpc(id: Int) =
        requireNotNull(ServerCacheManager.getNpc(id)) { "Missing Nex NPC $id" }

    private fun validateAssets() {
        NPC_IDS.forEach(::requireNpc)
        listOf(811, 9178, 9179, 9180, 9182, 9183, 9184, 9185, 9186, 9187, 9188, 9189, 9202, 9203)
            .forEach { anim(it) }
        listOf(
                1997,
                1998,
                1999,
                2000,
                2001,
                2002,
                2004,
                2005,
                2006,
                2007,
                2008,
                2009,
                2010,
                2011,
                2012,
                2013,
                2014,
                2015,
                2016,
                2017,
            )
            .forEach { spotInternal(it) }
        listOf(
                BARRIER_CONTROLLER,
                BARRIER_BUSY_CONTROLLER,
                ZAROS_ALTAR,
                SHADOW_SMASH_LOC,
                ICE_PRISON_LOC,
                ICE_PRISON_ALT_LOC,
            )
            .plus(BARRIER_FORMS)
            .forEach { id ->
                requireNotNull(ServerCacheManager.getObject(id)) { "Missing Nex location $id" }
            }
        projectileType("projanim.magic_spell")
        projectileType("projanim.arrow")
    }

    internal enum class DeathKind {
        NEX,
        BODYGUARD,
        REAVER,
        UNRELATED,
    }

    internal data class Run(
        val generation: Long,
        val arena: NexArena,
        var boss: Npc,
        val bodyguards: LinkedHashMap<NexState, Npc>,
        var state: NexState = NexState.IDLE,
        val participants: MutableSet<PlayerUid> = linkedSetOf(),
        val contributions: MutableMap<PlayerUid, NexContribution> = linkedMapOf(),
        val rewarded: MutableSet<PlayerUid> = linkedSetOf(),
        val reavers: MutableSet<Npc> = linkedSetOf(),
        val temporaryLocs: MutableList<LocInfo> = mutableListOf(),
        val calledGuardResolved: MutableSet<NexState> = linkedSetOf(),
        val coughUntil: MutableMap<PlayerUid, Int> = linkedMapOf(),
        val prisonedUntil: MutableMap<PlayerUid, Int> = linkedMapOf(),
        val flags: MutableMap<PlayerUid, NexAchievementFlags> = linkedMapOf(),
        var callFloor: Int? = null,
        var stateReadyCycle: Int = Int.MAX_VALUE,
        var resetCycle: Int = Int.MAX_VALUE,
        var specialLockedUntil: Int = Int.MIN_VALUE,
        var siphonUntil: Int = Int.MIN_VALUE,
        var darknessUntil: Int = Int.MIN_VALUE,
        var attackCounter: Int = 0,
        var specialIndex: Int = 0,
        var zarosAttackCounter: Int = 0,
        var meleeChainTarget: PlayerUid? = null,
        var meleeChainRemaining: Int = 0,
        var overhead: NexOverhead = NexOverhead.NONE,
        var startingTeamSize: Int = 0,
        var deathStarted: Boolean = false,
        var rewardFinished: Boolean = false,
    )

    private companion object {
        const val NEX_ID = 11278
        const val NEX_SPAWNING_ID = 11279
        const val NEX_SOUL_SPLIT_ID = 11280
        const val NEX_DEFLECT_ID = 11281
        const val NEX_DYING_ID = 11282
        const val FUMUS_ID = 11283
        const val UMBRA_ID = 11284
        const val CRUOR_ID = 11285
        const val GLACIES_ID = 11286
        const val BLOOD_REAVER_ID = 11294
        const val BARRIER_CONTROLLER = 42967
        const val BARRIER_BUSY_CONTROLLER = 42968
        const val ZAROS_ALTAR = 42965
        const val SHADOW_SMASH_LOC = 42942
        const val ICE_PRISON_LOC = 42943
        const val ICE_PRISON_ALT_LOC = 42944
        val NPC_IDS =
            listOf(
                NEX_ID,
                NEX_SPAWNING_ID,
                NEX_SOUL_SPLIT_ID,
                NEX_DEFLECT_ID,
                NEX_DYING_ID,
                FUMUS_ID,
                UMBRA_ID,
                CRUOR_ID,
                GLACIES_ID,
                BLOOD_REAVER_ID,
            )
        val BARRIER_FORMS = listOf(42937, 42938, 42939, 42940, 42941)
        val phaseStates = setOf(NexState.SMOKE, NexState.SHADOW, NexState.BLOOD, NexState.ICE)
        val calledStates = setOf(NexState.FUMUS, NexState.UMBRA, NexState.CRUOR, NexState.GLACIES)
        val combatStates = phaseStates + calledStates + NexState.ZAROS
        val activeStates = combatStates + NexState.SPAWNING + NexState.DYING
    }
}
