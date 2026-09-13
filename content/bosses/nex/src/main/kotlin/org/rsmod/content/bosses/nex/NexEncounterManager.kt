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
import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.bosses.runtime.EncounterRegistry
import org.rsmod.api.combat.NpcAttackOverride
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.InstanceStateKey
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.heal
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.NpcCalculatedProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.NpcPartialProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerTintToken
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.entity.util.EntityFaceAngle
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitImpactHandler
import org.rsmod.game.hit.HitType
import org.rsmod.game.hit.HitValidity
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.Direction
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.loc.LocLayerConstants

@Singleton
internal class NexEncounterManager
@Inject
constructor(
    private val clock: MapClock,
    private val npcs: NpcList,
    private val players: PlayerList,
    private val locs: LocRepository,
    private val interactions: AiPlayerInteractions,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
    private val bossEncounters: EncounterRegistry,
    private val worldQueues: WorldQueueList,
    private val world: WorldRepository,
    private val rewards: NexRewardService,
    private val instances: InstanceManager,
) {
    private val log = InlineLogger()
    private var generation = 1L
    private var run: Run? = null
    private var publicSession: InstanceSession? = null

    fun startup() {
        validateAssets()
        rewards.validateAssets()
        worldQueues.add(1) { if (run == null) spawnFresh() }
    }

    fun teleportBank(access: ProtectedAccess): Boolean {
        val player = access.player
        val uid = player.uid
        if (player.hitpoints <= 0) {
            player.mes("Finish what you are doing before travelling to the Ancient Prison.")
            return false
        }
        if (player.coords == NEX_BANK_TELEPORT) return false
        access.telejump(NEX_BANK_TELEPORT, TeleportType.Exempt)
        if (
            player.coords != NEX_BANK_TELEPORT ||
                uid.resolve(players) !== player ||
                player.uid != uid
        )
            return false
        leaveAll(player, reason = "travelled to the bank room")
        prepare(player)
        setBarrierState(player)
        player.mes("You arrive in the safe Ancient Prison bank room.")
        return true
    }

    fun enter(access: ProtectedAccess): Boolean {
        val player = access.player
        val uid = player.uid
        val current = run ?: return false
        if (player.hitpoints <= 0) {
            player.mes("Finish what you are doing before passing the barrier.")
            return false
        }
        if (current.state !in setOf(NexState.IDLE, NexState.SPAWNING)) {
            player.mes("The barrier is sealed while Nex is being fought.")
            return false
        }
        // The native movement API returns Unit. A same-tile request cannot establish arrival.
        if (player.coords == NEX_ARENA_ENTRY) return false
        access.telejump(NEX_ARENA_ENTRY, TeleportType.Exempt)
        // Native hooks may retire the room before movement commits. Never attach to that old run.
        if (
            player.coords != NEX_ARENA_ENTRY ||
                uid.resolve(players) !== player ||
                player.uid != uid ||
                player.hitpoints <= 0 ||
                !isActiveRun(current) ||
                !bossEncounters.isAttached(current.boss, current.encounter) ||
                current.state !in setOf(NexState.IDLE, NexState.SPAWNING)
        )
            return false
        val previousAdmission = instances.sessionForPlayer(player)
        instances.finalizeEntry(player, current.session, clock.cycle)
        if (
            !isActiveRun(current) ||
                !bossEncounters.isAttached(current.boss, current.encounter) ||
                instances.sessionForPlayer(player) !== current.session ||
                uid.resolve(players) !== player ||
                player.uid != uid ||
                player.hitpoints <= 0 ||
                player.coords != NEX_ARENA_ENTRY ||
                current.state !in setOf(NexState.IDLE, NexState.SPAWNING)
        ) {
            // Join callbacks can invalidate content admission after native membership commits.
            // Preserve a pre-existing admission or a newer successfully enrolled round.
            if (
                previousAdmission !== current.session &&
                    !isParticipant(player) &&
                    instances.sessionForPlayer(player) === current.session
            ) {
                instances.leave(player, current.session, clock.cycle)
            }
            return false
        }
        prepare(player)
        current.participants += player.uid
        current.participantPlayers[player.uid] = player
        current.flags.putIfAbsent(player.uid, NexAchievementFlags())
        player.lootDropDuration = NEX_DROP_DURATION
        setBarrierState(player)
        if (current.state == NexState.IDLE) beginSpawn(current)
        player.mes("You pass into Nex's public chamber.")
        return true
    }

    fun exit(access: ProtectedAccess): Boolean {
        val player = access.player
        val uid = player.uid
        val current = run ?: return false
        if (!isRoundMember(current, player)) return false
        if (player.coords == NEX_BANK_RETURN) return false
        access.telejump(NEX_BANK_RETURN, TeleportType.Standard)
        if (
            player.coords != NEX_BANK_RETURN ||
                uid.resolve(players) !== player ||
                player.uid != uid ||
                !isActiveRun(current)
        )
            return false
        leave(current, player, reason = "used the Zaros altar exit")
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
            if (
                !bossEncounters.isAttached(chamber.boss, chamber.encounter) ||
                    !activeParticipant(chamber, player)
            )
                leave(chamber, player, reason = "left the chamber")
            else tickPlayerEffects(chamber, player)
        }
        setBarrierState(player)
    }

    /** Public-room maintenance follows the world clock, including when no player is online. */
    fun tickWorld() = activeRuns().forEach(::tickWorld)

    fun canAttackNex(npc: Npc, target: Player): Boolean {
        val current = encounterFor(npc) ?: return false
        if (
            npc !== current.boss ||
                target.uid !in current.participants ||
                !activeParticipant(current, target)
        )
            return false
        // Incoming damage immunity never suppresses outgoing elemental attacks (3284 t632).
        return current.phase in phaseStates || current.phase == NexState.ZAROS
    }

    /** Only Nex-specific launch eligibility remains here; the DSL owns ordinary minion combat. */
    fun canAttackMinion(npc: Npc, target: Player): Boolean {
        val current = encounterFor(npc) ?: return false
        if (target.uid !in current.participants || !activeParticipant(current, target)) return false
        val guardState = current.bodyguards.entries.firstOrNull { it.value === npc }?.key
        return if (guardState != null) current.state == guardState else npc in current.reavers
    }

    /**
     * A fired spell can survive phase handoff, but cannot acquire a new room or player identity.
     */
    fun minionHitValidity(npc: Npc, target: Player): HitValidity {
        val current = encounterFor(npc)
        val targetUid = target.uid
        return HitValidity {
            current != null &&
                isActiveRun(current) &&
                targetUid.resolve(players) === target &&
                targetUid in current.participants &&
                activeParticipant(current, target)
        }
    }

    /** Already-emitted room damage survives source death, but never a retired encounter/member. */
    private fun roomHitValidity(current: Run, target: Player): HitValidity {
        val membership = minionHitValidity(current.boss, target)
        return HitValidity {
            bossEncounters.isAttached(current.boss, current.encounter) && membership.isValid()
        }
    }

    /** Queue-time work only binds native validity; HP, credit and phase state wait for impact. */
    fun prepareNpcHit(npc: Npc, hit: HitBuilder) {
        val current = encounterFor(npc)
        val ownerUuid = hit.context.attribution.ownerUuid
        val owner =
            if (ownerUuid != null) players.firstOrNull { it.uuid == ownerUuid }
            else if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players) else null
        val hasOwner = ownerUuid != null || hit.isFromPlayer
        val ownerUid = owner?.uid
        val previous = hit.validity
        hit.validity = HitValidity {
            (previous?.isValid() ?: true) &&
                current != null &&
                isActiveRun(current) &&
                bossEncounters.isAttached(current.boss, current.encounter) &&
                encounterFor(npc) === current &&
                (!hasOwner ||
                    (owner != null &&
                        ownerUid?.resolve(players) === owner &&
                        ownerUid in current.participants &&
                        activeParticipant(current, owner)))
        }
    }

    /**
     * Native Resolve owns the current-HP policy; it has no healing, credit or death side effects.
     */
    fun resolveNpcHit(event: NpcHitEvents.Resolve) {
        val current = encounterFor(event.npc)
        event.damage =
            when {
                current == null || !isActiveRun(current) -> 0
                event.npc === current.boss -> resolveNexDamage(current, event.hit, event.damage)
                current.bodyguards.values.any { it === event.npc } -> {
                    val guard = current.bodyguards.entries.first { it.value === event.npc }.key
                    if (current.state == guard) NexRules.softCap(event.damage) else 0
                }
                event.npc in current.reavers && current.phase == NexState.BLOOD -> event.damage
                else -> 0
            }
    }

    /** Observe accepted native damage; the existing reward adapter remains pending M28/S04. */
    fun impactNpcHit(event: NpcHitEvents.Impact) {
        val current = encounterFor(event.npc) ?: return
        if (!isActiveRun(current) || !event.hit.isValidNow()) return
        val ownerUuid = event.hit.context.attribution.ownerUuid
        val owner =
            if (ownerUuid != null) players.firstOrNull { it.uuid == ownerUuid }
            else if (event.hit.isFromPlayer) event.hit.resolvePlayerSource(players) else null
        if (event.npc === current.boss) {
            if (
                current.boss.hitpoints > 0 &&
                    current.phase == NexState.BLOOD &&
                    clock.cycle < current.siphonUntil
            ) {
                // Preserve the characterized ordinary input, never admin-expanded instant healing.
                val healed = current.boss.heal(event.originalHit.damage.coerceAtLeast(0))
                if (healed > 0 && owner != null) current.flags[owner.uid]?.bloodSiphonHeal = true
                return
            }
            if (
                event.hit.damage > 0 &&
                    owner != null &&
                    current.state == NexState.ZAROS &&
                    current.overhead == NexOverhead.DEFLECT_MELEE &&
                    event.hit.type == HitType.Melee
            ) {
                owner.queueHit(
                    current.boss,
                    1,
                    HitType.Typeless,
                    event.hit.damage,
                    modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                    validity = roomHitValidity(current, owner),
                )
            }
            val threshold = NexRules.threshold(current.state)
            if (event.hit.damage > 0 && threshold != null && current.boss.hitpoints <= threshold)
                callBodyguard(current, current.state)
        } else if (event.npc in current.reavers && owner != null && event.hit.damage > 0) {
            val healing = NexRules.reaverHeal(event.hit.damage)
            if (healing > 0) {
                owner.statHeal("stat.hitpoints", constant = healing, percent = 0)
                current.flags[owner.uid]?.bloodSiphonHeal = true
            }
        }
        if (owner != null) record(current, owner, event.hit.damage)
    }

    fun handleDeath(npc: Npc): DeathKind {
        val current = encounterFor(npc) ?: return DeathKind.UNRELATED
        if (npc === current.boss) {
            if (current.state != NexState.ZAROS || current.deathStarted) return DeathKind.UNRELATED
            current.deathStarted = true
            current.deathCoords = npc.coords
            transition(current, NexState.DYING)
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
        val deathCoords = checkNotNull(current.deathCoords) { "Nex death has not been accepted." }
        current.rewardFinished = true
        val generationAtDeath = current.generation
        current.encounter.schedule(worldQueues, 3) {
            if (!isActiveRun(current) || current.generation != generationAtDeath) return@schedule
            wrath(current, deathCoords)
            rewards.reward(
                current.contributions.filterKeys { uid ->
                    uid.resolve(players)?.let { isRoundMember(current, it) } == true
                },
                current.rewarded,
                current.arena.spawn,
            )
            if (
                !isActiveRun(current) || !bossEncounters.isAttached(current.boss, current.encounter)
            )
                return@schedule
            current.roundCompleted = true
            transition(current, NexState.RESETTING)
            current.resetCycle = clock.cycle + NEX_RESPAWN_TICKS
        }
    }

    fun isEncounterNpc(npc: Npc): Boolean = encounterFor(npc) != null

    fun attackDenial(player: Player, npc: Npc): String? {
        val current = encounterFor(npc) ?: return "This Nex encounter is resetting."
        if (!isRoundMember(current, player))
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

    fun isParticipant(player: Player): Boolean = activeRuns().any { isRoundMember(it, player) }

    fun isChamberParticipant(player: Player): Boolean =
        run?.let { isRoundMember(it, player) } == true

    fun playerLeftSession(player: Player, instanceId: InstanceId) {
        val current = run?.takeIf { it.session.id == instanceId } ?: return
        if (current.participantPlayers[player.uid] !== player) return
        if (player.hitpoints <= 0) current.flags[player.uid]?.playerDeath = true
        clearParticipant(current, player, "left native instance membership")
    }

    fun playerDied(player: Player) {
        activeRuns()
            .filter { it.participantPlayers[player.uid] === player }
            .forEach { current ->
                current.flags[player.uid]?.playerDeath = true
                leave(current, player, reason = "player death")
            }
    }

    fun playerLoggedOut(player: Player) = leaveAll(player, reason = "logout or disconnect")

    /** Capture content identity before native arrival delay; loc values alone can be replaced. */
    fun selectIce(player: Player, clicked: BoundLocInfo): IcePrisonSelection? {
        val current = run ?: return null
        val loc = LocInfo(clicked.layer, clicked.coords, clicked.entity)
        val segment = current.prisonSegments[loc] ?: return null
        return IcePrisonSelection(current, segment, player, player.uid)
    }

    fun breakIce(player: Player, selection: IcePrisonSelection?): Boolean {
        if (selection == null || selection.helper !== player) return false
        val current = selection.run
        val segment = selection.segment
        val prison = segment.prison
        if (
            !isActiveRun(current) ||
                !bossEncounters.isAttached(current.boss, current.encounter) ||
                current.phase != NexState.ICE ||
                selection.helperUid != player.uid ||
                selection.helperUid.resolve(players) !== player ||
                player.uid !in current.participants ||
                !activeParticipant(current, player) ||
                prison.target === player ||
                !ownsPrison(current, prison) ||
                clock.cycle >= prison.deadline ||
                current.prisonSegments[segment.loc] !== segment ||
                locs.findExact(segment.loc.coords, segment.loc.shape) != segment.loc
        )
            return false
        if (!locs.del(segment.loc, Int.MAX_VALUE)) return false
        forgetPrisonSegment(current, segment)
        current.prisons.remove(prison.uid)
        player.mes("You break the ice prison before it can shatter.")
        return true
    }

    private fun tickWorld(current: Run) {
        if (!isActiveRun(current)) return
        if (!bossEncounters.isAttached(current.boss, current.encounter)) {
            reset(current)
            return
        }
        for (uid in current.participants.toList()) {
            val departed = uid.resolve(players)?.let { activeParticipant(current, it) } != true
            if (departed) {
                val player = current.participantPlayers[uid]
                if (player != null) leave(current, player, "left the chamber")
                else {
                    current.participants.remove(uid)
                    clearPrisons(current, uid)
                }
            }
        }
        if (current.state == NexState.SPAWNING && clock.cycle >= current.stateReadyCycle)
            activateSmoke(current)
        if (current.state == NexState.RESETTING && clock.cycle >= current.resetCycle) {
            reset(current, retainOccupants = current.roundCompleted)
            return
        }
        if (current.state in activeStates && current.participants.isEmpty()) {
            transition(current, NexState.RESETTING)
            current.resetCycle = clock.cycle + 1
        }
        if (current.state in combatStates) keepAggressive(current)
    }

    private fun beginSpawn(current: Run) {
        transition(current, NexState.SPAWNING)
        current.startingTeamSize = current.participants.size
        current.stateReadyCycle = clock.cycle + 5
        current.boss.transmog(requireNpc(NEX_SPAWNING_ID), Int.MAX_VALUE)
        current.boss.resetFaceEntity()
        current.boss.resetPendingFaceSquare()
        current.boss.pendingFaceAngle = EntityFaceAngle(Direction.West.angle)
        current.boss.anim(anim(9182))
        current.boss.movementLocked = true
        current.boss.say("AT LAST!")
        announce(current, "AT LAST!")
        val centerOffset = current.boss.size / 2
        world.soundArea(
            current.boss.coords.translate(centerOffset, centerOffset),
            NEX_OPENING_SOUND,
            delay = 0,
            loops = 1,
            radius = 15,
            size = 0,
        )
    }

    private fun activateSmoke(current: Run) {
        if (current.state != NexState.SPAWNING) return
        current.boss.transmog(requireNpc(NEX_ID), Int.MAX_VALUE)
        transition(current, NexState.SMOKE)
        current.boss.movementLocked = false
        current.boss.apRangeOverride = 10
        announce(current, "Fill my soul with smoke!")
        keepAggressive(current)
    }

    /** Named DSL parameter resolver. It never chooses, launches or schedules a primary attack. */
    fun normalParameters(npc: Npc, target: Player, attack: NexNormalAttack): NpcAttackOverride {
        val current = requireNotNull(encounterFor(npc))
        val validity = minionHitValidity(npc, target)
        val protected = target.vars["varbit.prayer_protectfrommagic"] == 1
        val launch = MagicLaunch(current, target.uid, clock.cycle, attack.phase)
        val slot = current.encounter.state("nex.magic-launch") { MagicLaunchSlot() }
        slot.launch = null
        return when (attack.style) {
            NexNormalStyle.MELEE ->
                NpcAttackOverride(
                    hitValidity = validity,
                    onImpact = { _, impact -> soulSplitHeal(current, impact.damage) },
                )
            NexNormalStyle.RANGED ->
                NpcAttackOverride(
                    hitValidity = validity,
                    onImpact = { _, impact ->
                        soulSplitHeal(current, impact.damage)
                        if (impact.damage > 0)
                            target.statSub(
                                "stat.prayer",
                                constant = max(1, impact.damage / 10),
                                percent = 0,
                            )
                    },
                )
            NexNormalStyle.MAGIC ->
                NpcAttackOverride(
                    hitValidity = validity,
                    applyModifierOnImpact = attack.phase == NexState.ZAROS,
                    hitModifier =
                        NpcCalculatedProtectionPrayerPlayerHitModifier(
                            protected,
                            if (attack.phase == NexState.BLOOD) 66 else 50,
                            lateReductionPercent = if (attack.phase == NexState.ZAROS) 25 else 0,
                        ),
                    // Arm only once native beginAttack accepts; secondaries follow primary RNG/FX.
                    onAttack = { slot.launch = launch },
                    onImpact = { _, impact ->
                        target.spotanim(spotInternal(impactFor(attack.phase)))
                        applyMagicImpact(current, target, attack.phase, impact.damage, protected)
                    },
                )
        }
    }

    fun magicSplash(npc: Npc, primary: Player) {
        val current = encounterFor(npc) ?: return
        val slot = current.encounter.stateOrNull<MagicLaunchSlot>("nex.magic-launch") ?: return
        val launch = slot.launch ?: return
        slot.launch = null
        if (
            launch.run !== current ||
                launch.target != primary.uid ||
                launch.cycle != clock.cycle ||
                !minionHitValidity(npc, primary).isValid()
        )
            return
        splashSecondaries(
            current,
            primary,
            33,
            HitType.Magic,
            if (launch.phase == NexState.BLOOD) 66 else 50,
            launch.phase,
        )
    }

    /** Special alternation is Nex-specific; the DSL owns the five-normal cadence. */
    fun executeSpecial(npc: Npc, target: Player) {
        val current = encounterFor(npc) ?: return
        val special = NexRules.special(current.phase, current.specialIndex) ?: return
        current.specialIndex++
        startSpecial(current, special, target)
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
                    if (stateAtLaunch == NexState.ZAROS)
                        NpcCalculatedProtectionPrayerPlayerHitModifier(protectedAtLaunch, 50, 25)
                    else NpcPartialProtectionPrayerPlayerHitModifier(reduction)
                secondary.queueHit(
                    source = current.boss,
                    delay = 3,
                    type = type,
                    validity = roomHitValidity(current, secondary),
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

    private fun resolveNexDamage(current: Run, hit: Hit, incoming: Int): Int {
        if (current.phase == NexState.BLOOD && clock.cycle < current.siphonUntil) {
            return 0
        }
        var accepted =
            when {
                current.state in phaseStates ->
                    NexRules.phaseDamage(current.boss.hitpoints, incoming, current.state)
                current.state in calledStates -> {
                    val floor = current.callFloor ?: current.boss.hitpoints
                    if (current.boss.hitpoints <= floor) 0
                    else
                        (NexRules.softCap(incoming) / 10)
                            .coerceAtLeast(if (incoming > 0) 1 else 0)
                            .coerceAtMost(current.boss.hitpoints - floor)
                }
                current.state == NexState.ZAROS ->
                    NexRules.softCap(incoming).coerceAtMost(current.boss.hitpoints)
                else -> 0
            }
        if (
            current.state == NexState.ZAROS &&
                current.overhead == NexOverhead.DEFLECT_MELEE &&
                hit.type == HitType.Melee
        ) {
            accepted /= 2
        }
        return accepted
    }

    private fun callBodyguard(current: Run, phase: NexState) {
        val called = NexRules.calledBodyguard(phase) ?: return
        if (current.state != phase) return
        // Calling a guard changes vulnerability, not Nex's elemental phase or attack count.
        current.calledGuard = called
        current.callFloor = NexRules.lowerFloor(phase)
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
        current.calledGuard = null
        transition(current, next)
        current.callFloor = null
        // A handoff may shorten a special, but never an already-launched ordinary attack.
        val encounter = current.encounter
        val specialGate = encounter.lastAbilityName?.endsWith(".special") == true
        if (specialGate && current.boss.actionDelay == encounter.lastAbilityTick + 5)
            current.boss.actionDelay = clock.cycle + 2
        encounter.attackRateOverride = null
        encounter.lastAbilityTick =
            maxOf(current.boss.actionDelay, clock.cycle + 2) - NEX_ATTACK_RATE
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
        current.encounter.schedule(worldQueues, 2) {
            if (!validDelayed(current, castGeneration, NexState.SMOKE, target)) return@schedule
            PathingEntityCommon.telejump(target, collision, current.boss.coords.translateX(-1))
            clearProtectionPrayers(target)
            target.queueHit(
                source = current.boss,
                delay = 1,
                type = HitType.Typeless,
                damage = random.of(0..30),
                modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                validity = roomHitValidity(current, target),
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
        current.encounter.schedule(worldQueues, 2) {
            if (!validDelayed(current, castGeneration, NexState.SMOKE)) return@schedule
            current.boss.teleport(collision, destination)
            participants(current).forEach { player ->
                if (player.coords.x == destination.x || player.coords.z == destination.z) {
                    player.queueHit(
                        source = current.boss,
                        delay = 1,
                        type = HitType.Typeless,
                        damage = random.of(0..50),
                        modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                        validity = roomHitValidity(current, player),
                    )
                    current.flags[player.uid]?.smokeDashHit = true
                }
            }
        }
        current.encounter.schedule(worldQueues, 4) {
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
            spawnShadowSmashLoc(current, tile)
            current.encounter.schedule(worldQueues, 3) {
                if (
                    !validDelayed(current, castGeneration, NexState.SHADOW, player) ||
                        player.coords != tile
                )
                    return@schedule
                player.queueHit(
                    source = current.boss,
                    delay = 1,
                    type = HitType.Typeless,
                    damage = random.of(0..50),
                    modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                    validity = roomHitValidity(current, player),
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
                    current.session,
                    BLOOD_REAVER_ID,
                    current.arena.spawn.translate(offset.first, offset.second),
                    NEX_REAVER_HITPOINTS,
                ) ?: return
            if (!isActiveRun(current)) return
            current.reavers += reaver
            nearestParticipant(current, reaver.coords)?.let { reaver.apPlayer2(it, interactions) }
        }
    }

    private fun bloodSacrifice(current: Run, target: Player) {
        announce(current, "I demand a blood sacrifice!")
        target.mes("<col=ff0000>Nex has marked you for a blood sacrifice. Run away!</col>")
        clearSacrificeMarks(current, target.uid)
        val mark = BloodSacrificeMark(target, target.uid, target.tintOwned(NEX_SACRIFICE_TINT))
        current.sacrificeMarks[mark.uid] = mark
        val centerOffset = current.boss.size / 2
        world.soundArea(
            current.boss.coords.translate(centerOffset, centerOffset),
            NEX_SACRIFICE_SOUND,
            delay = 0,
            loops = 1,
            radius = 15,
            size = 0,
        )
        val castGeneration = current.generation
        current.encounter.schedule(worldQueues, 8) {
            clearSacrificeMark(current, mark)
            if (!validDelayed(current, castGeneration, NexState.BLOOD, target)) return@schedule
            if (distance(current.boss.coords, target.coords) < 7) {
                target.queueHit(
                    source = current.boss,
                    delay = 1,
                    type = HitType.Typeless,
                    damage = random.of(0..80),
                    modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                    validity = roomHitValidity(current, target),
                    impactHandler = HitImpactHandler { impact -> current.boss.heal(impact.damage) },
                )
                target.statSub("stat.prayer", constant = 10, percent = 0)
            }
        }
    }

    private fun clearSacrificeMark(current: Run, mark: BloodSacrificeMark) {
        if (current.sacrificeMarks[mark.uid] !== mark) return
        current.sacrificeMarks.remove(mark.uid)
        if (mark.target.uid == mark.uid) mark.target.clearTint(mark.token)
    }

    private fun clearSacrificeMarks(current: Run, uid: PlayerUid? = null) {
        current.sacrificeMarks.values
            .filter { uid == null || it.uid == uid }
            .forEach { clearSacrificeMark(current, it) }
    }

    private fun containment(current: Run) {
        announce(current, "Contain this!")
        current.boss.anim(anim(9186))
        val castGeneration = current.generation
        current.encounter.schedule(worldQueues, 3) {
            if (!validDelayed(current, castGeneration, NexState.ICE)) return@schedule
            participants(current).forEach { player ->
                if (distance(current.boss.coords, player.coords) <= 2) {
                    clearProtectionPrayers(player)
                    player.queueHit(
                        source = current.boss,
                        delay = 1,
                        type = HitType.Typeless,
                        damage = random.of(0..60),
                        modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                        validity = roomHitValidity(current, player),
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
        clearPrisons(current, target.uid)
        val prison = IcePrison(target, target.uid, clock.cycle + 5)
        current.prisons[prison.uid] = prison
        adjacentTiles(target.coords).forEachIndexed { index, tile ->
            val id = if (index % 2 == 0) ICE_PRISON_LOC else ICE_PRISON_ALT_LOC
            val shape = LocShape.CentrepieceStraight
            val loc =
                LocInfo(
                    LocLayerConstants.of(shape.id),
                    tile,
                    LocEntity(id, shape.id, LocAngle.North.id),
                )
            val segment = IcePrisonSegment(loc, prison)
            if (locs.add(loc, 5) { forgetPrisonSegment(current, segment) }) {
                // Native replacement cancels the old timer without invoking its callback.
                current.prisonSegments.values
                    .filter { it.loc.coords == loc.coords && it.loc.layer == loc.layer }
                    .forEach { forgetPrisonSegment(current, it) }
                current.prisonSegments[loc] = segment
            }
        }
        current.encounter.schedule(worldQueues, 5) {
            if (!ownsPrison(current, prison)) return@schedule
            if (!validDelayed(current, castGeneration, NexState.ICE, target)) {
                current.prisons.remove(prison.uid)
                return@schedule
            }
            if (clock.cycle < prison.deadline) return@schedule
            val membership = roomHitValidity(current, target)
            target.queueHit(
                source = current.boss,
                delay = 1,
                type = HitType.Typeless,
                damage = random.of(0..75),
                modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                // Emitted hits retain native phase lifetime, but a recast cannot inherit them.
                validity = HitValidity { ownsPrison(current, prison) && membership.isValid() },
                impactHandler =
                    HitImpactHandler {
                        if (current.prisons[prison.uid] === prison)
                            current.prisons.remove(prison.uid)
                    },
            )
            current.flags[prison.uid]?.iceSpecialHit = true
        }
    }

    private fun ownsPrison(current: Run, prison: IcePrison): Boolean =
        current.prisons[prison.uid] === prison &&
            prison.target.uid == prison.uid &&
            prison.uid.resolve(players) === prison.target &&
            prison.uid in current.participants &&
            activeParticipant(current, prison.target)

    /** Despawn callbacks run inside the repository iterator: mutate only content state here. */
    private fun forgetPrisonSegment(current: Run, segment: IcePrisonSegment) {
        if (current.prisonSegments[segment.loc] === segment)
            current.prisonSegments.remove(segment.loc)
    }

    private fun clearPrisons(current: Run, uid: PlayerUid? = null) {
        val segments = current.prisonSegments.values.filter { uid == null || it.prison.uid == uid }
        for (segment in segments) {
            if (locs.findExact(segment.loc.coords, segment.loc.shape) == segment.loc)
                locs.del(segment.loc, Int.MAX_VALUE)
            forgetPrisonSegment(current, segment)
        }
        if (uid == null) current.prisons.clear() else current.prisons.remove(uid)
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
                validity = roomHitValidity(current, player),
            )
        }
    }

    fun setZarosOverhead(npc: Npc, overhead: NexOverhead) {
        val current = encounterFor(npc) ?: return
        if (current.state != NexState.ZAROS) return
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

    private fun wrath(current: Run, deathCoords: CoordGrid) {
        announce(current, "Taste my wrath!")
        participants(current).forEach { player ->
            if (distance(deathCoords, player.coords) <= 5) {
                player.queueHit(
                    source = current.boss,
                    delay = 1,
                    type = HitType.Typeless,
                    damage = random.of(0..55),
                    modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                    validity = roomHitValidity(current, player),
                )
            }
        }
    }

    private fun reset(current: Run, retainOccupants: Boolean = false) {
        if (!isActiveRun(current) || current.resetInProgress) return
        current.resetInProgress = true
        try {
            resetRound(current, retainOccupants)
        } finally {
            current.resetInProgress = false
        }
    }

    private fun resetRound(current: Run, retainOccupants: Boolean) {
        val occupants =
            if (retainOccupants)
                current.participants.mapNotNull { uid ->
                    uid.resolve(players)
                        ?.takeIf { activeParticipant(current, it) }
                        ?.let { uid to it }
                }
            else emptyList()
        if (!retainOccupants) {
            current.participantPlayers.values.toList().forEach { leave(current, it, "round reset") }
        }
        if (!cleanup(current)) return
        if (!isActiveRun(current)) return
        val fresh = spawnFresh(current.session) ?: return
        for ((uid, player) in occupants) {
            // Cleanup and actor creation publish native callbacks before enrolment commits.
            if (
                uid !in current.participants ||
                    player.uid != uid ||
                    uid.resolve(players) !== player ||
                    !activeParticipant(fresh, player) ||
                    !isActiveRun(fresh) ||
                    !bossEncounters.isAttached(fresh.boss, fresh.encounter)
            )
                continue
            prepare(player)
            fresh.participants += uid
            fresh.participantPlayers[uid] = player
            fresh.flags[uid] = NexAchievementFlags()
            player.lootDropDuration = NEX_DROP_DURATION
        }
        if (
            fresh.participants.isNotEmpty() &&
                isActiveRun(fresh) &&
                bossEncounters.isAttached(fresh.boss, fresh.encounter)
        )
            beginSpawn(fresh)
    }

    private fun spawnFresh(existingSession: InstanceSession? = null): Run? {
        val session = existingSession ?: publicSession ?: createPublicSession() ?: return null
        if (instances.sessionForId(session.id) !== session) return null
        try {
            val fresh = spawnRound(session)
            if (fresh == null) instances.terminate(session)
            return fresh
        } catch (error: Throwable) {
            try {
                instances.terminate(session)
            } catch (cleanupError: Throwable) {
                if (cleanupError !== error) error.addSuppressed(cleanupError)
            }
            throw error
        }
    }

    private fun createPublicSession(): InstanceSession? {
        val session =
            instances.createServerOwned(
                NEX_PUBLIC_INSTANCE_KEY,
                NEX_PUBLIC_INSTANCE_SPEC,
                InstanceAccess.Friends,
                clock.cycle,
            ) ?: return null
        val state = session.state(ROOM_STATE) { RoomState() }
        publicSession = session
        session.onCleanup {
            val previous = state.run
            state.run = null
            if (run === previous) run = null
            if (publicSession === session) publicSession = null
            if (previous != null) retireRoundResources(previous)
        }
        return session
    }

    private fun spawnRound(session: InstanceSession): Run? {
        val boss = spawnNpc(session, NEX_ID, NEX_SPAWN, NEX_MAX_HITPOINTS) ?: return null
        boss.mode = null
        boss.movementLocked = true
        boss.apRangeOverride = 10
        boss.apRequiresLineOfSight = true
        val bodyguards =
            linkedMapOf(
                NexState.FUMUS to
                    (spawnNpc(session, FUMUS_ID, CoordGrid(2913, 5215), NEX_BODYGUARD_HITPOINTS)
                        ?: return null),
                NexState.UMBRA to
                    (spawnNpc(session, UMBRA_ID, CoordGrid(2937, 5215), NEX_BODYGUARD_HITPOINTS)
                        ?: return null),
                NexState.CRUOR to
                    (spawnNpc(session, CRUOR_ID, CoordGrid(2937, 5191), NEX_BODYGUARD_HITPOINTS)
                        ?: return null),
                NexState.GLACIES to
                    (spawnNpc(session, GLACIES_ID, CoordGrid(2913, 5191), NEX_BODYGUARD_HITPOINTS)
                        ?: return null),
            )
        bodyguards.values.forEach {
            it.mode = null
            it.movementLocked = true
            it.apRangeOverride = 10
            it.apRequiresLineOfSight = true
        }
        // Each native add can publish callbacks that retire an earlier roster member.
        // Validate the entire roster before resolving or publishing an encounter.
        if (
            instances.sessionForId(session.id) !== session ||
                (listOf(boss) + bodyguards.values).any {
                    it.uid.resolve(npcs) !== it || instances.instanceForNpc(it) != session.id
                }
        )
            return null
        val encounter = bossEncounters.of(boss)
        if (!bossEncounters.isAttached(boss, encounter)) return null
        val fresh =
            Run(
                generation = generation++,
                arena = NEX_CHAMBER_ARENA,
                boss = boss,
                bodyguards = bodyguards,
                encounter = encounter,
                session = session,
            )
        if (instances.sessionForId(session.id) !== session) return null
        checkNotNull(session.stateOrNull(ROOM_STATE)).run = fresh
        run = fresh
        fresh.encounter.onCleanup { clearPrisons(fresh) }
        fresh.encounter.onCleanup { clearShadowSmashLocs(fresh) }
        fresh.encounter.onCleanup { clearSacrificeMarks(fresh) }
        log.info { "[Nex] spawned generation=${run?.generation} state=IDLE" }
        return fresh
    }

    private fun spawnNpc(session: InstanceSession, id: Int, tile: CoordGrid, hitpoints: Int): Npc? {
        val npc = Npc(requireNpc(id), tile)
        npc.baseHitpointsLvl = hitpoints
        npc.hitpoints = hitpoints
        if (id == NEX_ID) {
            npc.defenceDrainMinimum = NEX_DEFENCE_DRAIN_MINIMUM
            npc.respawnDir = Direction.West
        }
        val guardDirection =
            when (id) {
                FUMUS_ID -> Direction.SouthEast
                UMBRA_ID -> Direction.SouthWest
                CRUOR_ID -> Direction.NorthWest
                GLACIES_ID -> Direction.NorthEast
                else -> null
            }
        if (guardDirection != null) {
            // Prepare both native add-header direction and the captured explicit face update.
            npc.respawnDir = guardDirection
            npc.pendingFaceAngle = EntityFaceAngle(guardDirection.angle)
        }
        if (!instances.spawnFixedNpc(session, npc)) return null
        // Retain Nex's source identity through native death and Wrath. NexCombat disables her
        // automatic reveal; the room owns replacement. Minions retire through native deletion.
        npc.respawns = id == NEX_ID
        return npc
    }

    private fun cleanup(current: Run): Boolean {
        retireRoundResources(current)
        return instances.resetNpcs(current.session, clock.cycle)
    }

    private fun retireRoundResources(current: Run) {
        val occupants = current.participantPlayers.toList()
        clearPrisons(current)
        clearSacrificeMarks(current)
        bossEncounters.remove(current.boss)
        clearShadowSmashLocs(current)
        occupants.forEach { (uid, player) ->
            if (player.uid == uid && player.lootDropDuration == NEX_DROP_DURATION)
                player.lootDropDuration = null
        }
        current.reavers.clear()
    }

    private fun leave(current: Run, player: Player, reason: String) {
        if (current.participantPlayers[player.uid] !== player) return
        if (instances.sessionForPlayer(player) === current.session) {
            instances.leave(player, current.session, clock.cycle)
        }
        clearParticipant(current, player, reason)
    }

    private fun clearParticipant(current: Run, player: Player, reason: String) {
        if (current.participantPlayers[player.uid] !== player) return
        if (!current.participants.remove(player.uid)) return
        current.participantPlayers.remove(player.uid)
        current.coughUntil.remove(player.uid)
        clearPrisons(current, player.uid)
        clearSacrificeMarks(current, player.uid)
        if (player.lootDropDuration == NEX_DROP_DURATION) player.lootDropDuration = null
        if (player.uid.resolve(players) === player) {
            prepare(player)
            setBarrierState(player)
        }
        log.info {
            "[Nex:${current.arena.name}] player=${player.displayName} " +
                "generation=${current.generation} leave=$reason"
        }
    }

    private fun leaveAll(player: Player, reason: String) {
        activeRuns()
            .filter { it.participantPlayers[player.uid] === player }
            .forEach { leave(it, player, reason = reason) }
    }

    private fun keepAggressive(current: Run) {
        if (current.boss.isSlotAssigned && current.boss.mode == null) {
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

    private fun isActiveRun(current: Run): Boolean =
        run === current &&
            instances.sessionForId(current.session.id) === current.session &&
            current.session.stateOrNull(ROOM_STATE)?.run === current

    private fun encounterFor(npc: Npc): Run? =
        activeRuns().firstOrNull { current ->
            npc === current.boss ||
                current.bodyguards.values.any { it === npc } ||
                npc in current.reavers
        }

    private fun activeParticipant(current: Run, player: Player): Boolean =
        player.isSlotAssigned &&
            instances.sessionForPlayer(player) === current.session &&
            (current.participantPlayers[player.uid]?.let { it === player } != false) &&
            !player.clientDisconnected.get() &&
            !player.pendingLogout &&
            !player.loggingOut &&
            player.hitpoints > 0 &&
            current.arena.contains(player.coords)

    private fun isRoundMember(current: Run, player: Player): Boolean =
        isActiveRun(current) &&
            player.uid in current.participants &&
            current.participantPlayers[player.uid] === player &&
            instances.sessionForPlayer(player) === current.session

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
            current.phase == state &&
            current.boss.isSlotAssigned &&
            (player == null ||
                (player.uid in current.participants && activeParticipant(current, player)))

    private fun spawnShadowSmashLoc(current: Run, tile: CoordGrid) {
        val shape = LocShape.CentrepieceStraight
        val loc =
            LocInfo(
                LocLayerConstants.of(shape.id),
                tile,
                LocEntity(SHADOW_SMASH_LOC, shape.id, LocAngle.North.id),
            )
        // Native replacement cancels the previous timer. Despawn only releases content ownership.
        if (locs.add(loc, 3) { current.shadowSmashLocs.remove(loc) }) current.shadowSmashLocs += loc
    }

    private fun clearShadowSmashLocs(current: Run) {
        current.shadowSmashLocs.forEach { loc ->
            if (locs.findExact(loc.coords, loc.shape) == loc) locs.del(loc, Int.MAX_VALUE)
        }
        current.shadowSmashLocs.clear()
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
        val encounter: BossEncounter,
        val session: InstanceSession,
        // Per-round mechanic tracking; only the native session can authorize room admission.
        val participants: MutableSet<PlayerUid> = linkedSetOf(),
        val participantPlayers: MutableMap<PlayerUid, Player> = linkedMapOf(),
        val contributions: MutableMap<PlayerUid, NexContribution> = linkedMapOf(),
        val rewarded: MutableSet<PlayerUid> = linkedSetOf(),
        val reavers: MutableSet<Npc> = linkedSetOf(),
        val shadowSmashLocs: MutableSet<LocInfo> = linkedSetOf(),
        val calledGuardResolved: MutableSet<NexState> = linkedSetOf(),
        val coughUntil: MutableMap<PlayerUid, Int> = linkedMapOf(),
        val prisons: MutableMap<PlayerUid, IcePrison> = linkedMapOf(),
        val sacrificeMarks: MutableMap<PlayerUid, BloodSacrificeMark> = linkedMapOf(),
        val prisonSegments: MutableMap<LocInfo, IcePrisonSegment> = linkedMapOf(),
        val flags: MutableMap<PlayerUid, NexAchievementFlags> = linkedMapOf(),
        var callFloor: Int? = null,
        var calledGuard: NexState? = null,
        var stateReadyCycle: Int = Int.MAX_VALUE,
        var resetCycle: Int = Int.MAX_VALUE,
        var siphonUntil: Int = Int.MIN_VALUE,
        var darknessUntil: Int = Int.MIN_VALUE,
        var specialIndex: Int = 0,
        var overhead: NexOverhead = NexOverhead.NONE,
        var startingTeamSize: Int = 0,
        var deathStarted: Boolean = false,
        var deathCoords: CoordGrid? = null,
        var rewardFinished: Boolean = false,
        var roundCompleted: Boolean = false,
        var resetInProgress: Boolean = false,
    ) {
        val state: NexState
            get() = calledGuard ?: phase

        val phase: NexState
            get() = NexState.valueOf(encounter.currentPhaseName)
    }

    internal class IcePrison(val target: Player, val uid: PlayerUid, val deadline: Int)

    internal class BloodSacrificeMark(
        val target: Player,
        val uid: PlayerUid,
        val token: PlayerTintToken,
    )

    private class RoomState(var run: Run? = null)

    internal class IcePrisonSegment(val loc: LocInfo, val prison: IcePrison)

    internal class IcePrisonSelection(
        val run: Run,
        val segment: IcePrisonSegment,
        val helper: Player,
        val helperUid: PlayerUid,
    )

    private data class MagicLaunch(
        val run: Run,
        val target: PlayerUid,
        val cycle: Int,
        val phase: NexState,
    )

    private class MagicLaunchSlot(var launch: MagicLaunch? = null)

    private fun transition(current: Run, state: NexState) {
        current.calledGuard = null
        current.encounter.transitionTo(state.name, clock.cycle)
    }

    private companion object {
        val ROOM_STATE = InstanceStateKey<RoomState>("nex-public-round")
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
