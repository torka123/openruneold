@file:OptIn(dev.openrune.types.util.UncheckedType::class)

package org.rsmod.content.minigames.clanmayhem

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import org.rsmod.api.config.constants
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.PvPAttackValidateResult
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSettings
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.mechanics.toxins.Toxin.cureAllToxins
import org.rsmod.api.player.disablePrayers
import org.rsmod.api.player.hit.processor.DamageOnlyPlayerHitProcessor
import org.rsmod.api.player.hit.takeInstantHit
import org.rsmod.api.player.isInCombat
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.specials.energy.SpecialAttackEnergy
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.damage.DamageContributor
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

private data class ClanMayhemRegistration(
    val id: UUID,
    val format: ClanMayhemFormat,
    val openedAtTick: Int,
    val endsAtTick: Int,
    val debugOverride: Boolean,
    val teams: LinkedHashMap<UUID, ClanMayhemTeam> = linkedMapOf(),
    val announcements: MutableSet<Int> = mutableSetOf(),
    var rosterLocked: Boolean = false,
)

@Singleton
internal class ClanMayhemManager
@Inject
constructor(
    private val clock: MapClock,
    private val players: PlayerList,
    private val instances: InstanceManager,
    private val collision: CollisionFlagMap,
    private val npcs: NpcRepository,
    private val locs: LocRepository,
    private val random: GameRandom,
    private val hitProcessor: DamageOnlyPlayerHitProcessor,
    private val specialEnergy: SpecialAttackEnergy,
    private val eventBus: EventBus,
    private val clans: ClanMayhemClanDirectory,
    private val snapshots: ClanMayhemSnapshotService,
    private val kits: ClanMayhemKitService,
    private val rewards: ClanMayhemRewardService,
    private val progression: ClanMayhemProgressionService,
) {
    private val log = InlineLogger()
    private var registration: ClanMayhemRegistration? = null
    private var active: ClanMayhemMatch? = null
    private var nextFormatIndex = 0
    private var nextRegistrationTick = clock.cycle + AUTOMATIC_EVENT_INTERVAL_TICKS
    private val fogExposure = mutableMapOf<Int, Int>()

    fun tick() {
        val currentRegistration = registration
        if (currentRegistration != null) tickRegistration(currentRegistration)
        val match = active
        if (match != null) tickMatch(match)
        if (registration == null && active == null && clock.cycle >= nextRegistrationTick) {
            openRegistration(ClanMayhemConfig.formats[nextFormatIndex], debugOverride = false)
        }
    }

    fun openRegistration(
        format: ClanMayhemFormat? = null,
        debugOverride: Boolean = false,
    ): Boolean {
        if (!ClanMayhemConfig.enabled || registration != null || active != null) return false
        val selected = format ?: ClanMayhemConfig.formats[nextFormatIndex]
        registration =
            ClanMayhemRegistration(
                id = UUID.randomUUID(),
                format = selected,
                openedAtTick = clock.cycle,
                endsAtTick = clock.cycle + ClanMayhemConfig.registrationTicks,
                debugOverride = debugOverride,
            )
        broadcast(
            "Clan Mayhem registration is open: ${selected.teamSize.players}-man ${selected.kitTier.name.lowercase()}! Use ::mayhem."
        )
        return true
    }

    fun join(player: Player): Boolean {
        val reg = registration
        if (reg == null) {
            player.mes(status(player))
            return false
        }
        if (reg.rosterLocked) {
            player.mes("Clan Mayhem rosters are locked.")
            return false
        }
        if (
            active?.participant(player.accountId) != null || registeredParticipant(player) != null
        ) {
            player.mes("You are already registered for Clan Mayhem.")
            return false
        }
        if (rewards.hasPendingReward(player)) {
            player.mes("Claim your previous Clan Mayhem reward before registering again.")
            return false
        }
        if (!hasEmptyEntryLoadout(player)) {
            player.mes(
                "You must have an empty inventory and wear no equipment to enter Clan Mayhem."
            )
            return false
        }
        if (snapshots.hasUnresolved(player)) {
            player.mes(
                "Your account has an unresolved Clan Mayhem item sandbox. Re-log or contact staff."
            )
            return false
        }
        val identity = clans.identity(player)
        if (identity == null) {
            player.mes("You must belong to a clan before entering Clan Mayhem.")
            return false
        }
        if (
            System.currentTimeMillis() - identity.memberSinceEpochMillis <
                MINIMUM_CLAN_MEMBERSHIP_MILLIS
        ) {
            player.mes("You must be a member of your clan for at least 30 minutes.")
            return false
        }
        if (
            player.hitpoints <= 0 ||
                player.isInCombat() ||
                instances.sessionForPlayer(player) != null
        ) {
            player.mes("You cannot register while dead, in combat, or inside another minigame.")
            return false
        }
        if (identity.clanId !in reg.teams && reg.teams.size >= reg.format.teamSize.maxClans) {
            player.mes(
                "This Clan Mayhem bracket has reached its ${reg.format.teamSize.maxClans}-clan limit."
            )
            return false
        }
        val team =
            reg.teams.getOrPut(identity.clanId) {
                ClanMayhemTeam(UUID.randomUUID(), identity.clanId, identity.displayName)
            }
        if (team.roster.size >= reg.format.teamSize.players) {
            player.mes("Your clan's ${reg.format.teamSize.players}-player roster is already full.")
            return false
        }
        player.ifClose(eventBus)
        if (!snapshots.capture(player, reg.id)) {
            player.mes(
                "Your account has an unresolved minigame snapshot. An administrator must restore it first."
            )
            return false
        }
        val participant =
            ClanMayhemParticipant(
                accountId = player.accountId,
                characterId = player.characterId,
                playerUid = player.uuid ?: player.uid.packed.toLong(),
                joinedAtTick = clock.cycle,
            )
        team.roster[player.accountId] = participant
        PathingEntityCommon.telejump(
            player,
            collision,
            sourceCoord(ClanMayhemCrater.waitingRoom.tile),
        )
        player.mes("You join ${team.clanName}'s Clan Mayhem roster. Use ::mayhemready.")
        return true
    }

    fun toggleReady(player: Player): Boolean {
        val participant = registeredParticipant(player) ?: return false
        if (!participant.ready && !hasEmptyEntryLoadout(player)) {
            player.mes(
                "You cannot ready up with items. Empty your inventory and remove all equipment."
            )
            return true
        }
        participant.ready = !participant.ready
        player.mes(
            if (participant.ready) "You are ready for Clan Mayhem." else "You are no longer ready."
        )
        return true
    }

    fun leaveRegistration(player: Player): Boolean {
        val reg = registration ?: return false
        val team = reg.teams.values.firstOrNull { player.accountId in it.roster } ?: return false
        if (reg.rosterLocked) {
            player.mes(
                "The roster is locked; staff must cancel the registration to remove players now."
            )
            return false
        }
        team.roster.remove(player.accountId)
        if (team.roster.isEmpty()) reg.teams.remove(team.clanId)
        snapshots.restore(player)
        player.mes("You leave the Clan Mayhem roster.")
        return true
    }

    fun startNow(debugOverride: Boolean = false): Boolean {
        val reg = registration ?: return false
        return startMatch(reg.copy(debugOverride = reg.debugOverride || debugOverride))
    }

    fun fastForwardForTesting(player: Player, targetElapsedTicks: Int): Boolean {
        val match = active
        if (
            match == null ||
                match.state !in setOf(ClanMayhemState.ACTIVE, ClanMayhemState.FINAL_CIRCLE)
        ) {
            player.mes("A Clan Mayhem match must be running before its clock can be changed.")
            return false
        }
        if (targetElapsedTicks !in 0..ClanMayhemConfig.activeMatchTicks) {
            player.mes("Clan Mayhem time must be between 0:00 and 25:00.")
            return false
        }
        val previousElapsed = match.elapsed(clock.cycle)
        if (!match.fastForwardTo(clock.cycle, targetElapsedTicks)) {
            player.mes(
                "The Mayhem test clock only moves forward; it is already at ${formatMayhemElapsedTicks(previousElapsed)}."
            )
            return false
        }
        val due = match.timeline.due(match.id, match.elapsed(clock.cycle))
        due.forEach { event ->
            if (active?.id == match.id && match.state != ClanMayhemState.FINISHED) {
                handleTimeline(match, event)
            }
        }
        player.mes(
            "Clan Mayhem clock: ${formatMayhemElapsedTicks(previousElapsed)} -> " +
                "${formatMayhemElapsedTicks(targetElapsedTicks)}; fog=${match.fogStage.name.lowercase()}."
        )
        log.info {
            "[ClanMayhem] admin=${player.displayName} fast-forwarded match=${match.id} " +
                "from=$previousElapsed to=$targetElapsedTicks fog=${match.fogStage} events=${due.joinToString()}"
        }
        return true
    }

    fun cancel(reason: String = "cancelled by staff"): Boolean {
        val reg = registration
        if (reg != null) {
            registration = null
            reg.teams.values
                .flatMap { it.roster.values }
                .forEach { participant -> online(participant.accountId)?.let(snapshots::restore) }
            broadcast("Clan Mayhem was cancelled: $reason.")
            scheduleNext()
            return true
        }
        val match = active ?: return false
        broadcastToMatch(match, "Clan Mayhem was cancelled: $reason.")
        cleanup(match)
        return true
    }

    fun prepareAdminTestParticipant(player: Player): Boolean {
        if (registration == null) {
            player.mes("Open a Clan Mayhem test registration first.")
            return false
        }
        if (!hasEmptyEntryLoadout(player)) {
            player.mes(
                "Bank every inventory item and remove all equipment before testing Clan Mayhem."
            )
            return false
        }
        if (registeredParticipant(player) == null) {
            if (clans.identity(player) == null) {
                clans.assignForTesting(player, "Solo Test ${player.accountId}")
            }
            if (!join(player)) return false
        }
        val participant = registeredParticipant(player) ?: return false
        participant.ready = true
        player.mes(
            "Solo administrator test roster ready; no second clan is required. Use ::mayhemforcestart."
        )
        return true
    }

    fun assignTestClan(player: Player, name: String) {
        clans.assignForTesting(player, name)
        player.mes("Your Clan Mayhem test clan is now $name.")
    }

    fun status(player: Player): String {
        val reg = registration
        if (reg != null) {
            val remaining = ((reg.endsAtTick - clock.cycle).coerceAtLeast(0) * 3 + 4) / 5
            return "Clan Mayhem: registration, ${reg.format.id}, ${remaining}s remaining, ${reg.teams.size} clans."
        }
        val match = active
        if (match != null) {
            val seconds =
                ((ClanMayhemConfig.activeMatchTicks - match.elapsed(clock.cycle)).coerceAtLeast(0) *
                    3 + 4) / 5
            val participant = match.participant(player.accountId)
            return "Clan Mayhem: ${match.state}, ${seconds}s remaining, score=${match.teamFor(player.accountId)?.score?.matchPoints ?: 0}, state=${participant?.state}."
        }
        val seconds = ((nextRegistrationTick - clock.cycle).coerceAtLeast(0) * 3 + 4) / 5
        val upcoming = ClanMayhemConfig.formats[nextFormatIndex]
        return "Next Clan Mayhem in ${seconds}s: ${upcoming.id}. Points=${rewards.points(player)}, wins=${rewards.wins(player)}."
    }

    fun matchFor(player: Player): ClanMayhemMatch? =
        active?.takeIf { it.participant(player.accountId) != null }

    fun validatePvp(attacker: Player, target: Player): PvPAttackValidateResult {
        val match = active ?: return PvPAttackValidateResult.Pass
        val a = match.participant(attacker.accountId)
        val t = match.participant(target.accountId)
        if (a == null && t == null) return PvPAttackValidateResult.Pass
        if (a == null || t == null)
            return PvPAttackValidateResult.Deny("That player is not in your Clan Mayhem match.")
        val aTeam = match.teamFor(attacker.accountId)!!
        val tTeam = match.teamFor(target.accountId)!!
        val allowed =
            ClanMayhemCombatRules.canAttack(
                aTeam.matchTeamId,
                tTeam.matchTeamId,
                a.state,
                t.state,
                protected(a),
                protected(t),
                match.state == ClanMayhemState.ACTIVE || match.state == ClanMayhemState.FINAL_CIRCLE,
            )
        return if (allowed) PvPAttackValidateResult.Pass
        else PvPAttackValidateResult.Deny("You cannot attack that player in Clan Mayhem.")
    }

    fun validateNpcAttack(player: Player, npc: Npc): NpcAttackValidateResult {
        val match = active ?: return NpcAttackValidateResult.Pass
        val participant = match.participant(player.accountId) ?: return NpcAttackValidateResult.Pass
        if (participant.state != ClanMayhemParticipantState.ARENA) {
            return NpcAttackValidateResult.Deny("You cannot attack from the respawn box.")
        }
        if (protected(participant)) {
            return NpcAttackValidateResult.Deny("Your Clan Mayhem protection must expire first.")
        }
        if (match.bossNpc !== npc && match.supplies.none { it.npc === npc }) {
            return NpcAttackValidateResult.Deny("That NPC is not part of this Clan Mayhem match.")
        }
        cancelProtection(participant)
        participant.score.activityActions++
        return NpcAttackValidateResult.Pass
    }

    fun onPvpHit(attacker: Player, target: Player) {
        val match = active ?: return
        if (validatePvp(attacker, target) !is PvPAttackValidateResult.Pass) return
        val participant = match.participant(attacker.accountId) ?: return
        cancelProtection(participant)
        participant.score.activityActions++
        match.lastEnemyAttacker[target.accountId] = attacker.accountId to clock.cycle
    }

    fun markDeath(player: Player, explicitKiller: Player? = null): Boolean {
        val match = active ?: return false
        val participant = match.participant(player.accountId) ?: return false
        if (participant.state != ClanMayhemParticipantState.ARENA) return true
        val killer =
            explicitKiller?.takeIf { enemy(match, it.accountId, player.accountId) }
                ?: match.lastEnemyAttacker[player.accountId]
                    ?.takeIf { clock.cycle - it.second <= FOG_KILL_CREDIT_TICKS }
                    ?.first
                    ?.let(::online)
        if (killer != null) creditKill(match, killer, participant)
        participant.score.deaths++
        match.teamFor(player.accountId)?.score?.deaths =
            (match.teamFor(player.accountId)?.score?.deaths ?: 0) + 1
        participant.state = ClanMayhemParticipantState.RESPAWN_BOX
        participant.respawnReadyAtTick = clock.cycle + ClanMayhemConfig.respawnPenaltyTicks
        participant.spawnProtectionUntilTick = 0
        participant.returnProtectionUntilTick = 0
        clearEventInventory(player)
        player.damageContributions.clear()
        PathingEntityCommon.telejump(
            player,
            collision,
            match.mapSourceCoord(ClanMayhemCrater.respawnBox.tile),
        )
        player.mes("You may return to your clan in 01:00.")
        return true
    }

    fun returnToTeam(player: Player): Boolean {
        val match = active ?: return false
        val participant = match.participant(player.accountId) ?: return false
        if (participant.state != ClanMayhemParticipantState.RESPAWN_BOX) {
            player.mes("Return to Team is available only from the Clan Mayhem respawn box.")
            return false
        }
        val readyAt = participant.respawnReadyAtTick ?: Int.MAX_VALUE
        if (clock.cycle < readyAt) {
            val seconds = ((readyAt - clock.cycle) * 3 + 4) / 5
            player.mes("You may return to your team in $seconds seconds.")
            return false
        }
        if (match.elapsed(clock.cycle) >= ClanMayhemConfig.activeMatchTicks) return false
        val team = match.teamFor(player.accountId) ?: return false
        val teammate =
            team.roster.values
                .asSequence()
                .filter {
                    it.accountId != player.accountId && it.state == ClanMayhemParticipantState.ARENA
                }
                .mapNotNull { online(it.accountId) }
                .firstOrNull { match.safe(it.coords) }
        val fallbackIndex = match.teams.values.indexOf(team).coerceAtLeast(0)
        val preferred =
            teammate?.coords ?: match.mapSourceCoord(ClanMayhemCrater.deploymentPads[fallbackIndex])
        val destination = findValidTile(match, preferred, RETURN_SEARCH_RADIUS)
        if (destination == null) {
            player.mes("No safe Return-to-Team tile is currently available.")
            return false
        }
        if (!kits.applyRespawn(player, match.format.kitTier)) {
            player.mes("Your respawn kit could not be applied; you remain safely in the box.")
            return false
        }
        deliverPendingCrates(match, participant, player)
        PathingEntityCommon.telejump(player, collision, destination)
        participant.state = ClanMayhemParticipantState.ARENA
        participant.respawnReadyAtTick = null
        participant.returnProtectionUntilTick = clock.cycle + ClanMayhemConfig.returnProtectionTicks
        player.mes("You return to your clan with five seconds of protection.")
        return true
    }

    fun claimCrate(player: Player, coords: CoordGrid): Boolean {
        val match = active ?: return false
        val participant = match.participant(player.accountId) ?: return false
        val crate =
            match.crates.firstOrNull { it.loc.coords == coords && !it.claimed } ?: return false
        if (participant.state != ClanMayhemParticipantState.ARENA || protected(participant)) {
            player.mes("You cannot claim a supply crate while protected or outside the arena.")
            return true
        }
        crate.claimed = true
        locs.del(crate.loc, Int.MAX_VALUE)
        val team = match.teamFor(player.accountId) ?: return true
        for (member in team.roster.values) {
            val packageRoll = rollCratePackage(match.format.kitTier)
            if (member.state == ClanMayhemParticipantState.RESPAWN_BOX) {
                match.pendingCratePackages.getOrPut(member.accountId, ::mutableListOf) +=
                    packageRoll
            } else if (member.state == ClanMayhemParticipantState.ARENA) {
                val recipient = online(member.accountId)
                if (recipient == null) {
                    match.pendingCratePackages.getOrPut(member.accountId, ::mutableListOf) +=
                        packageRoll
                } else {
                    giveCratePackage(match, member, recipient, packageRoll)
                }
            }
        }
        participant.score.cratesClaimed++
        participant.score.activityActions++
        team.score.cratesClaimed++
        broadcastToMatch(
            match,
            "${team.clanName} claimed individually rolled supply packages for every registered member!",
        )
        return true
    }

    fun claimReward(player: Player, coords: CoordGrid? = null): List<InvObj>? {
        val match = active
        val participant = match?.participant(player.accountId)
        if (
            coords != null &&
                (participant?.state != ClanMayhemParticipantState.REWARD_ROOM ||
                    match.rewardChest?.coords != coords)
        ) {
            return null
        }
        if (participant?.state == ClanMayhemParticipantState.REWARD_ROOM) {
            restoreAndLeave(player, match)
            participant.state = ClanMayhemParticipantState.RESTORED
        }
        return rewards.claim(player)
    }

    fun onLogin(player: Player) {
        val match = active
        val participant = match?.participant(player.accountId)
        if (participant != null && snapshots.matchId(player) == match.id) {
            participant.connected = true
            if (participant.state == ClanMayhemParticipantState.ARENA) {
                participant.state = ClanMayhemParticipantState.RESPAWN_BOX
                participant.respawnReadyAtTick =
                    maxOf(
                        participant.respawnReadyAtTick ?: 0,
                        clock.cycle + ClanMayhemConfig.respawnPenaltyTicks,
                    )
            }
            val destination =
                if (participant.state == ClanMayhemParticipantState.REWARD_ROOM)
                    match.mapSourceCoord(ClanMayhemCrater.rewardRoom.tile)
                else match.mapSourceCoord(ClanMayhemCrater.respawnBox.tile)
            PathingEntityCommon.telejump(player, collision, destination)
            return
        }
        val reg = registration
        if (
            reg != null &&
                registeredParticipant(player) != null &&
                snapshots.matchId(player) == reg.id
        ) {
            PathingEntityCommon.telejump(
                player,
                collision,
                sourceCoord(ClanMayhemCrater.waitingRoom.tile),
            )
            return
        }
        if (snapshots.hasUnresolved(player)) {
            if (snapshots.restore(player))
                player.mes("Your interrupted Clan Mayhem state was safely restored.")
        }
        if (rewards.hasPendingReward(player))
            player.mes("A Clan Mayhem reward is waiting. Use ::mayhemclaim.")
    }

    fun onLogout(player: Player) {
        val match = active
        val participant = match?.participant(player.accountId)
        if (participant != null) {
            participant.connected = false
            participant.state = ClanMayhemParticipantState.RESTORED
            participant.respawnReadyAtTick = null
            restoreAndLeave(player, match)
            return
        }
        val reg = registration ?: return
        val team = reg.teams.values.firstOrNull { player.accountId in it.roster } ?: return
        team.roster.remove(player.accountId)
        if (team.roster.isEmpty()) reg.teams.remove(team.clanId)
        snapshots.restore(player)
    }

    fun onPostTick(player: Player) {
        val waiting = registeredParticipant(player)
        if (waiting != null) {
            if (!hasEmptyEntryLoadout(player)) {
                removeUnsafeRegistrationPlayer(player)
            }
            return
        }
        val match = matchFor(player) ?: return
        if (match.state != ClanMayhemState.ACTIVE && match.state != ClanMayhemState.FINAL_CIRCLE)
            return
        val participant = match.participant(player.accountId) ?: return
        if (participant.state == ClanMayhemParticipantState.ARENA) {
            cancelSpawnProtectionOutsidePad(match, player, participant)
            player.runEnergy = constants.run_max_energy
            UpdateRun.energy(player, player.runEnergy)
            trackPvpDamage(match, player)
        }
    }

    private fun tickRegistration(reg: ClanMayhemRegistration) {
        val remaining = reg.endsAtTick - clock.cycle
        if (!reg.rosterLocked && remaining <= ClanMayhemConfig.rosterLockRemainingTicks) {
            reg.rosterLocked = true
            broadcast("Clan Mayhem rosters are now locked.")
        }
        ClanMayhemConfig.registrationAnnouncementsRemainingTicks
            .filter { remaining <= it && reg.announcements.add(it) }
            .sortedDescending()
            .forEach {
                val seconds = (it * 3) / 5
                broadcast("Clan Mayhem ${reg.format.id} starts in ${seconds}s. Use ::mayhem.")
            }
        if (remaining <= 0) startMatch(reg)
    }

    private fun startMatch(reg: ClanMayhemRegistration): Boolean {
        if (registration?.id != reg.id) return false
        val complete =
            (if (reg.debugOverride) {
                    reg.teams.values.filter {
                        it.roster.isNotEmpty() && it.roster.values.all(ClanMayhemParticipant::ready)
                    }
                } else {
                    ClanMayhemRosterRules.completeTeams(reg.teams.values, reg.format)
                })
                .filter { team -> team.roster.values.all { online(it.accountId) != null } }
                .take(reg.format.teamSize.maxClans)
        val mayStart =
            complete.size >= (if (reg.debugOverride) 1 else ClanMayhemConfig.minimumClans) &&
                ClanMayhemRosterRules.uniqueClanIds(complete) &&
                ClanMayhemRosterRules.uniqueAccounts(complete)
        if (!mayStart) {
            registration = null
            reg.teams.values
                .flatMap { it.roster.values }
                .forEach { online(it.accountId)?.let(snapshots::restore) }
            broadcast("Clan Mayhem was cancelled because too few complete clans were ready.")
            scheduleNext()
            return false
        }
        val kitErrors = kits.validate()
        if (kitErrors.isNotEmpty()) {
            registration = null
            reg.teams.values
                .flatMap { it.roster.values }
                .forEach { online(it.accountId)?.let(snapshots::restore) }
            log.error { "[ClanMayhem] Cannot start: ${kitErrors.joinToString()}" }
            broadcast("Clan Mayhem was cancelled because its standardized kit is unavailable.")
            scheduleNext()
            return false
        }
        val selectedPlayers =
            complete.flatMap { team -> team.roster.values.mapNotNull { online(it.accountId) } }
        if (selectedPlayers.isEmpty()) return false
        val unsafePlayers = selectedPlayers.filterNot(::hasEmptyEntryLoadout)
        if (unsafePlayers.isNotEmpty()) {
            registration = null
            unsafePlayers.forEach {
                it.mes("Clan Mayhem rejected your non-empty inventory or equipment at launch.")
            }
            reg.teams.values
                .flatMap { it.roster.values }
                .forEach { online(it.accountId)?.let(snapshots::restore) }
            broadcast(
                "Clan Mayhem was cancelled because every player must remain empty and unequipped until deployment."
            )
            scheduleNext()
            return false
        }
        val owner = selectedPlayers.first()
        val waiting = ClanMayhemCrater.waitingRoom.tile
        val spec =
            InstanceSettings(
                    maxPlayers = 40,
                    reclaimTicks = 1,
                    graceTicks = 1,
                    destroyWhenEmpty = true,
                    description = "Clan Mayhem crater",
                    timeLimitTicks = ClanMayhemConfig.activeMatchTicks + REWARD_ROOM_TICKS,
                )
                .withArea(
                    InstanceArea.copyRegions(
                        centerRegionId = ClanMayhemConfig.craterCenterRegion,
                        gridSize = ClanMayhemConfig.craterGridSize,
                        level = 0,
                        enterCoord =
                            RegionLocal(
                                waiting.level,
                                waiting.x / 64,
                                waiting.z / 64,
                                waiting.x % 64,
                                waiting.z % 64,
                            ),
                        exitCoord = sourceCoord(waiting),
                    ),
                    settingsRowId = 0,
                )
        val created =
            instances.create(owner, INSTANCE_KEY, spec, InstanceAccess.Private, clock.cycle)
        if (created !is InstanceManager.Result.Created) {
            registration = null
            reg.teams.values
                .flatMap { it.roster.values }
                .forEach { online(it.accountId)?.let(snapshots::restore) }
            broadcast("Clan Mayhem could not create its crater instance.")
            scheduleNext()
            return false
        }
        val routeRoll = random.of(ClanMayhemFogRoutes.all.size)
        val match =
            ClanMayhemMatch(
                reg.id,
                created.session,
                reg.format,
                clock.cycle,
                created.enter,
                complete.associateByTo(linkedMapOf(), ClanMayhemTeam::matchTeamId),
                ClanMayhemFogRoutes.all[routeRoll],
                routeRoll,
            )
        val selectedAccounts = selectedPlayers.mapTo(hashSetOf(), Player::accountId)
        reg.teams.values
            .flatMap { it.roster.values }
            .filterNot { it.accountId in selectedAccounts }
            .forEach { participant ->
                online(participant.accountId)?.let { player ->
                    snapshots.restore(player)
                    player.mes("Your roster did not qualify for this Clan Mayhem match.")
                }
            }
        active = match
        registration = null
        for ((index, player) in selectedPlayers.withIndex()) {
            player.ifClose(eventBus)
            val entry =
                if (index == 0) created.enter
                else
                    when (
                        val joined =
                            instances.join(player, created.session, clock.cycle, forceAccess = true)
                    ) {
                        is InstanceManager.Result.Joined -> joined.enter
                        else -> continue
                    }
            val participant = match.participant(player.accountId) ?: continue
            if (!kits.applyStarting(player, match.format.kitTier)) {
                cancel("a standardized kit failed to apply")
                return false
            }
            instances.finalizeEntry(player, created.session, clock.cycle)
            val teamIndex =
                match.teams.values.indexOf(match.teamFor(player.accountId)).coerceAtLeast(0)
            val pad =
                findValidTile(
                    match,
                    match.mapSourceCoord(ClanMayhemCrater.deploymentPads[teamIndex]),
                    5,
                ) ?: entry
            PathingEntityCommon.telejump(player, collision, pad)
            participant.state = ClanMayhemParticipantState.ARENA
            participant.spawnProtectionUntilTick =
                clock.cycle + ClanMayhemConfig.spawnProtectionTicks
            participant.score.activityActions++
            player.mes(
                "Clan Mayhem begins! The dice rolled ${routeRoll + 1}; fog closes toward ${match.fogRoute.name}."
            )
        }
        spawnSupplyNpcs(match)
        broadcast("Clan Mayhem ${match.format.id} has begun in Daimon's Crater!")
        return true
    }

    private fun tickMatch(match: ClanMayhemMatch) {
        if (active?.id != match.id) return
        if (match.state == ClanMayhemState.FINISHED) {
            if (clock.cycle >= (match.cleanupAtTick ?: Int.MAX_VALUE)) cleanup(match)
            return
        }
        val elapsed = match.elapsed(clock.cycle)
        match.timeline.due(match.id, elapsed).forEach { handleTimeline(match, it) }
        tickSupplyNpcs(match)
        tickBoss(match)
        tickFog(match)
        tickRespawnMessages(match)
    }

    private fun handleTimeline(match: ClanMayhemMatch, event: ClanMayhemTimelineEvent) {
        when (event) {
            ClanMayhemTimelineEvent.DEPLOY -> Unit
            ClanMayhemTimelineEvent.SPAWN_PROTECTION_EXPIRES ->
                broadcastToMatch(match, "Spawn protection has expired.")
            ClanMayhemTimelineEvent.BOSS_WARNING ->
                broadcastToMatch(
                    match,
                    "A Wilderness boss will appear at a contested objective in 30 seconds!",
                )
            ClanMayhemTimelineEvent.BOSS_SPAWN -> spawnBoss(match)
            ClanMayhemTimelineEvent.SUPPLY_CRATE_ONE,
            ClanMayhemTimelineEvent.SUPPLY_CRATE_TWO,
            ClanMayhemTimelineEvent.SUPPLY_CRATE_THREE,
            ClanMayhemTimelineEvent.SUPPLY_CRATE_FOUR -> spawnCrate(match)
            ClanMayhemTimelineEvent.FOG_ONE_WARNING ->
                broadcastToMatch(match, "Fog warning: move toward ${match.fogRoute.name}.")
            ClanMayhemTimelineEvent.FOG_ONE_CLOSE -> setFog(match, ClanMayhemFogStage.FIRST)
            ClanMayhemTimelineEvent.FOG_TWO_WARNING ->
                broadcastToMatch(match, "The greater fog closes in one minute.")
            ClanMayhemTimelineEvent.FOG_TWO_CLOSE -> setFog(match, ClanMayhemFogStage.SECOND)
            ClanMayhemTimelineEvent.FOG_THREE_WARNING ->
                broadcastToMatch(match, "The third fog closure begins in one minute.")
            ClanMayhemTimelineEvent.FOG_THREE_CLOSE -> setFog(match, ClanMayhemFogStage.THIRD)
            ClanMayhemTimelineEvent.FINAL_CIRCLE_WARNING ->
                broadcastToMatch(match, "Final-circle warning: ${match.fogRoute.name}.")
            ClanMayhemTimelineEvent.FINAL_CIRCLE_CLOSE -> {
                match.state = ClanMayhemState.FINAL_CIRCLE
                setFog(match, ClanMayhemFogStage.FINAL)
            }
            ClanMayhemTimelineEvent.LETHAL_FOG -> setFog(match, ClanMayhemFogStage.LETHAL)
            ClanMayhemTimelineEvent.MATCH_END -> finish(match)
        }
    }

    private fun spawnBoss(match: ClanMayhemMatch) {
        if (match.bossNpc != null || match.bossResolved) return
        val bossIndex = random.of(ClanMayhemBoss.entries.size)
        val locationIndex = random.of(ClanMayhemCrater.bossNodes.size)
        val roll =
            ClanMayhemBossRoll(
                ClanMayhemBoss.entries[bossIndex],
                ClanMayhemCrater.bossNodes[locationIndex],
                bossIndex,
                locationIndex,
            )
        val preferred = match.mapSourceCoord(roll.node.tile)
        val tile = findValidTile(match, preferred, 8)
        if (tile == null) {
            log.error { "[ClanMayhem] No valid boss tile match=${match.id} node=${roll.node.id}" }
            match.bossResolved = true
            return
        }
        val npc = Npc(roll.boss.npcInternal, tile)
        val participants = match.teams.values.sumOf { it.roster.size }
        val scaled =
            ceil(npc.baseHitpointsLvl * (1.0 + (participants - 1).coerceAtLeast(0) * 0.18)).toInt()
        npc.baseHitpointsLvl = scaled
        npc.hitpoints = scaled
        npc.respawns = false
        npc.suppressRewards = true
        npcs.add(npc, ClanMayhemConfig.activeMatchTicks)
        instances.attachNpc(match.session.id, npc)
        match.bossRoll = roll
        match.bossNpc = npc
        broadcastToMatch(match, "${roll.boss.displayName} has appeared at ${roll.node.name}!")
        log.info {
            "[ClanMayhem] boss match=${match.id} bossRoll=$bossIndex locationRoll=$locationIndex tile=$tile"
        }
    }

    private fun tickBoss(match: ClanMayhemMatch) {
        val boss = match.bossNpc ?: return
        if (match.bossResolved || boss.hitpoints > 0) return
        match.bossResolved = true
        val damage = linkedMapOf<UUID, Int>()
        val byUuid =
            match.teams.values
                .flatMap { team -> team.roster.values.map { it.playerUid to team } }
                .toMap()
        for (entry in
            boss.damageContributions.entries().filterIsInstance<DamageContributor.ByPlayer>()) {
            val team = byUuid[entry.uuid] ?: continue
            damage[team.matchTeamId] = (damage[team.matchTeamId] ?: 0) + entry.damage
            team.roster.values.firstOrNull { it.playerUid == entry.uuid }?.score?.bossDamage =
                entry.damage
        }
        val owner = ClanMayhemBossRules.owner(boss.baseHitpointsLvl, damage)
        val team = match.teams[owner]
        if (team != null) {
            team.score.bossObjective = true
            team.roster.values.forEach { it.score.activityActions++ }
            broadcastToMatch(
                match,
                "${team.clanName} owns the boss objective and gains five match points!",
            )
        } else {
            broadcastToMatch(
                match,
                "The boss died without a clan reaching 15% contribution; no objective was awarded.",
            )
        }
    }

    private fun spawnSupplyNpcs(match: ClanMayhemMatch) {
        repeat(match.format.teamSize.supplyNpcs) { index ->
            val internal = SUPPLY_NPCS[index % SUPPLY_NPCS.size]
            val source =
                ClanMayhemCrater.deploymentPads[index % ClanMayhemCrater.deploymentPads.size]
            val tile = findValidTile(match, match.mapSourceCoord(source), 10) ?: return@repeat
            val npc = Npc(internal, tile)
            npc.respawns = false
            npc.suppressRewards = true
            npcs.add(npc, ClanMayhemConfig.activeMatchTicks)
            instances.attachNpc(match.session.id, npc)
            match.supplies += MayhemSupplyRuntime(npc, MayhemSupplyNpcEncounter(UUID.randomUUID()))
        }
    }

    private fun tickSupplyNpcs(match: ClanMayhemMatch) {
        for (runtime in match.supplies.toList()) {
            val npc = runtime.npc
            if (runtime.encounter.stage == MayhemSupplyNpcStage.NORMAL && npc.hitpoints <= 0) {
                val transformed = runtime.encounter.killNormal(random.of(100))
                if (transformed) {
                    val ghost = Npc(npc.type, npc.coords)
                    ghost.baseHitpointsLvl =
                        npc.baseHitpointsLvl * ClanMayhemConfig.ghostHitpointsPercent / 100
                    ghost.baseAttackLvl =
                        npc.baseAttackLvl * ClanMayhemConfig.ghostAccuracyPercent / 100
                    ghost.baseRangedLvl =
                        npc.baseRangedLvl * ClanMayhemConfig.ghostAccuracyPercent / 100
                    ghost.baseMagicLvl =
                        npc.baseMagicLvl * ClanMayhemConfig.ghostAccuracyPercent / 100
                    ghost.baseDefenceLvl =
                        npc.baseDefenceLvl * ClanMayhemConfig.ghostDefencePercent / 100
                    ghost.hitpoints = ghost.baseHitpointsLvl
                    ghost.respawns = false
                    ghost.suppressRewards = true
                    npcs.add(ghost, ClanMayhemConfig.activeMatchTicks)
                    instances.attachNpc(match.session.id, ghost)
                    runtime.npc = ghost
                } else {
                    rewardSupplyNpc(match, npc)
                }
            } else if (
                runtime.encounter.stage == MayhemSupplyNpcStage.GHOST && npc.hitpoints <= 0
            ) {
                runtime.encounter.killGhost()
                rewardSupplyNpc(match, npc)
            }
            if (
                runtime.encounter.stage in
                    setOf(MayhemSupplyNpcStage.NORMAL, MayhemSupplyNpcStage.GHOST) &&
                    !match.safe(npc.coords) &&
                    match.fogStage != ClanMayhemFogStage.OPEN
            ) {
                runtime.encounter.consumeByFog()
                if (npc.isSlotAssigned) npcs.del(npc, Int.MAX_VALUE)
            }
        }
    }

    private fun spawnCrate(match: ClanMayhemMatch) {
        val node =
            ClanMayhemCrater.crateNodes
                .filter { match.safe(match.mapSourceCoord(it.tile)) }
                .let { random.pickOrNull(it) } ?: return
        val tile = findValidTile(match, match.mapSourceCoord(node.tile), 7) ?: return
        val definition = ServerCacheManager.getObject(CRATE_OBJECT_ID)
        if (definition == null) {
            log.error { "[ClanMayhem] Missing verified crate object $CRATE_OBJECT_ID" }
            return
        }
        val loc =
            locs.add(
                tile,
                definition.internalName,
                Int.MAX_VALUE,
                LocAngle.North,
                LocShape.CentrepieceStraight,
            )
        match.crates += MayhemCrateRuntime(loc)
        broadcastToMatch(match, "A Clan Mayhem supply crate has appeared at ${node.name}!")
    }

    private fun setFog(match: ClanMayhemMatch, stage: ClanMayhemFogStage) {
        match.fogStage = stage
        fogExposure.clear()
        broadcastToMatch(
            match,
            "The ${stage.name.lowercase()} fog is now active around ${match.fogRoute.name}.",
        )
        val boss = match.bossNpc
        if (boss != null && !match.bossResolved && !match.safe(boss.coords)) {
            match.bossResolved = true
            if (boss.isSlotAssigned) npcs.del(boss, Int.MAX_VALUE)
            broadcastToMatch(
                match,
                "The fog consumed the Wilderness boss; no clan receives its objective.",
            )
        }
    }

    private fun tickFog(match: ClanMayhemMatch) {
        val stage = match.fogStage
        if (stage == ClanMayhemFogStage.OPEN) return
        val interval = if (stage == ClanMayhemFogStage.LETHAL) 1 else 2
        if (clock.cycle % interval != 0) return
        forEachOnline(match) { player, participant ->
            if (
                participant.state != ClanMayhemParticipantState.ARENA || match.safe(player.coords)
            ) {
                fogExposure.remove(player.accountId)
                return@forEachOnline
            }
            val exposure = (fogExposure[player.accountId] ?: 0) + 1
            fogExposure[player.accountId] = exposure
            val base =
                when (stage) {
                    ClanMayhemFogStage.FIRST -> random.of(1..3)
                    ClanMayhemFogStage.SECOND,
                    ClanMayhemFogStage.THIRD,
                    ClanMayhemFogStage.FINAL -> random.of(4..8)
                    ClanMayhemFogStage.LETHAL -> random.of(8..15)
                    ClanMayhemFogStage.OPEN -> 0
                }
            val ramp = if (stage == ClanMayhemFogStage.LETHAL) exposure / 2 else exposure / 5
            player.takeInstantHit(HitType.Typeless, base + ramp, hitProcessor)
        }
    }

    private fun tickRespawnMessages(match: ClanMayhemMatch) {
        if (clock.cycle % 10 != 0) return
        forEachOnline(match) { player, participant ->
            if (participant.state != ClanMayhemParticipantState.RESPAWN_BOX) return@forEachOnline
            val readyAt = participant.respawnReadyAtTick ?: return@forEachOnline
            if (readyAt <= clock.cycle) {
                player.mes("Return to Team is ready. Use the minigame teleport or ::mayhemreturn.")
            } else if ((readyAt - clock.cycle) % 50 == 0) {
                player.mes(
                    "You may return to your clan in ${((readyAt - clock.cycle) * 3) / 5} seconds."
                )
            }
        }
    }

    private fun finish(match: ClanMayhemMatch) {
        if (!match.resultGate.commitOnce { match.state = ClanMayhemState.FINISHED }) return
        var ordered = ClanMayhemStandings.ordered(match.teams.values)
        when (val resolution = ClanMayhemStandings.resolve(match.teams.values)) {
            is ClanMayhemWinnerResolution.Winner -> Unit
            is ClanMayhemWinnerResolution.GenuineTie -> {
                val winner = resolution.teams[random.of(resolution.teams.size)]
                ordered = listOf(winner) + ordered.filterNot { it === winner }
            }
        }
        match.finalStandings =
            ordered.mapIndexed { index, team ->
                ClanMayhemFinalStanding(
                    index + 1,
                    team.matchTeamId,
                    team.clanId,
                    team.clanName,
                    team.score.copy(),
                )
            }
        val winner = ordered.first()
        spawnRewardChest(match)
        ordered.forEachIndexed { index, team ->
            val placement = index + 1
            team.roster.values.forEach { participant ->
                if (participant.state == ClanMayhemParticipantState.RESTORED) return@forEach
                val player = online(participant.accountId) ?: return@forEach
                val points =
                    rewards.awardPoints(
                        player,
                        match.id,
                        placement,
                        participant,
                        team.score.bossObjective,
                    )
                progression.record(
                    player,
                    match.id,
                    placement,
                    participant,
                    team,
                    match.format,
                    points,
                )
                if (points > 0) player.mes("You receive $points Mayhem Shop Points.")
                if (team === winner) {
                    val pending =
                        rewards.prepareWinnerReward(player, match.id, team.clanId, participant)
                    if (pending != null) {
                        participant.state = ClanMayhemParticipantState.REWARD_ROOM
                        PathingEntityCommon.telejump(
                            player,
                            collision,
                            match.mapSourceCoord(ClanMayhemCrater.rewardRoom.tile),
                        )
                        player.mes(
                            "Your Final War Chest reward is ready. Search the monumental chest."
                        )
                    } else {
                        restoreAndLeave(player, match)
                    }
                } else {
                    restoreAndLeave(player, match)
                    participant.state = ClanMayhemParticipantState.RESTORED
                }
            }
        }
        match.cleanupAtTick = clock.cycle + REWARD_ROOM_TICKS
        broadcast(
            "${winner.clanName} wins Clan Mayhem with ${winner.score.matchPoints} match points!"
        )
    }

    private fun spawnRewardChest(match: ClanMayhemMatch) {
        val definition = ServerCacheManager.getObject(CRATE_OBJECT_ID)
        if (definition == null) {
            log.error { "[ClanMayhem] Missing verified reward chest object $CRATE_OBJECT_ID" }
            return
        }
        match.rewardChest =
            locs.add(
                match.mapSourceCoord(ClanMayhemCrater.rewardRoom.tile),
                definition.internalName,
                Int.MAX_VALUE,
                LocAngle.North,
                LocShape.CentrepieceStraight,
            )
    }

    private fun cleanup(match: ClanMayhemMatch) {
        if (active?.id != match.id) return
        match.rewardChest?.let {
            if (locs.findAll(it.coords).any { current -> current == it })
                locs.del(it, Int.MAX_VALUE)
        }
        match.crates.forEach {
            if (locs.findAll(it.loc.coords).any { current -> current == it.loc })
                locs.del(it.loc, Int.MAX_VALUE)
        }
        match.supplies.forEach { if (it.npc.isSlotAssigned) npcs.del(it.npc, Int.MAX_VALUE) }
        match.bossNpc?.let { if (it.isSlotAssigned) npcs.del(it, Int.MAX_VALUE) }
        forEachOnline(match) { player, participant ->
            restoreAndLeave(player, match)
            participant.state = ClanMayhemParticipantState.RESTORED
        }
        active = null
        fogExposure.clear()
        scheduleNext()
    }

    private fun creditKill(match: ClanMayhemMatch, killer: Player, victim: ClanMayhemParticipant) {
        val killerParticipant = match.participant(killer.accountId) ?: return
        val team = match.teamFor(killer.accountId) ?: return
        if (!enemy(match, killer.accountId, victim.accountId)) return
        killerParticipant.score.kills++
        killerParticipant.score.activityActions++
        team.score.pvpKills++
        team.score.finalKillTick = clock.cycle
        killer.mes("You earned one Clan Mayhem match point for defeating an enemy.")
    }

    private fun trackPvpDamage(match: ClanMayhemMatch, target: Player) {
        forEachOnline(match) { attacker, attackerParticipant ->
            if (!enemy(match, attacker.accountId, target.accountId)) return@forEachOnline
            val key = target.accountId to attacker.accountId
            val total = target.damageContributions.damageBy(attacker)
            val previous = match.pvpDamageSnapshots[key] ?: 0
            if (total > previous) {
                val delta = total - previous
                attackerParticipant.score.enemyDamage += delta
                match.teamFor(attacker.accountId)?.score?.enemyDamage =
                    (match.teamFor(attacker.accountId)?.score?.enemyDamage ?: 0) + delta
                match.pvpDamageSnapshots[key] = total
            }
        }
    }

    private fun rewardSupplyNpc(match: ClanMayhemMatch, npc: Npc) {
        val contributor =
            npc.damageContributions
                .entries()
                .filterIsInstance<DamageContributor.ByPlayer>()
                .maxWithOrNull(
                    compareBy<DamageContributor.ByPlayer> { it.damage }.thenBy { it.uuid }
                ) ?: return
        val participant =
            match.teams.values
                .asSequence()
                .flatMap { it.roster.values.asSequence() }
                .firstOrNull { it.playerUid == contributor.uuid } ?: return
        val player = online(participant.accountId) ?: return
        val stack = SUPPLY_NPC_DROPS[random.of(SUPPLY_NPC_DROPS.size)]
        val result =
            player.invTransaction(player.inv) {
                add(select(player.inv), stack.internalName.asRSCM(RSCMType.OBJ), stack.quantity)
            }
        if (result.success) {
            participant.score.activityActions++
            player.mes("You receive ${stack.quantity} event supplies from the fallen creature.")
        } else {
            player.mes("You have no room for the creature's temporary supplies.")
        }
    }

    private fun giveCratePackage(
        match: ClanMayhemMatch,
        participant: ClanMayhemParticipant,
        player: Player,
        roll: MayhemEquipmentRoll,
    ) {
        val result =
            player.invTransaction(player.inv) {
                val inv = select(player.inv)
                add(inv, roll.weapon.asRSCM(RSCMType.OBJ), 1)
                roll.supplies.forEach {
                    add(inv, it.internalName.asRSCM(RSCMType.OBJ), it.quantity)
                }
                add(inv, roll.armour.asRSCM(RSCMType.OBJ), 1)
            }
        if (!result.success) {
            match.pendingCratePackages.getOrPut(participant.accountId, ::mutableListOf) += roll
            player.mes(
                "Your crate package is pending until your next respawn because your inventory is full."
            )
        }
    }

    private fun deliverPendingCrates(
        match: ClanMayhemMatch,
        participant: ClanMayhemParticipant,
        player: Player,
    ) {
        val packages = match.pendingCratePackages[participant.accountId] ?: return
        val delivered = mutableListOf<MayhemEquipmentRoll>()
        for (roll in packages) {
            val result =
                player.invTransaction(player.inv) {
                    val inv = select(player.inv)
                    add(inv, roll.weapon.asRSCM(RSCMType.OBJ), 1)
                    roll.supplies.forEach {
                        add(inv, it.internalName.asRSCM(RSCMType.OBJ), it.quantity)
                    }
                    add(inv, roll.armour.asRSCM(RSCMType.OBJ), 1)
                }
            if (result.success) delivered += roll else break
        }
        packages.removeAll(delivered.toSet())
        if (packages.isEmpty()) match.pendingCratePackages.remove(participant.accountId)
    }

    private fun rollCratePackage(tier: ClanMayhemKitTier): MayhemEquipmentRoll {
        val style = MayhemCombatStyle.entries[random.of(MayhemCombatStyle.entries.size)]
        val table = ClanMayhemCrateTables.table(tier, style)
        return MayhemEquipmentRoll(
            style = style,
            weapon = table.weapons[random.of(table.weapons.size)],
            armour = table.armour[random.of(table.armour.size)],
            supplies = ClanMayhemCrateTables.refill(style),
        )
    }

    private fun clearEventInventory(player: Player) {
        for (slot in 0 until player.inv.size) player.inv[slot] = null
        for (slot in 0 until player.worn.size) player.worn[slot] = null
        player.disablePrayers()
        player.cureAllToxins()
        specialEnergy.restoreFullSpecialEnergy(player)
        player.rebuildAppearance()
    }

    private fun restoreAndLeave(player: Player, match: ClanMayhemMatch) {
        snapshots.restore(player)
        if (instances.sessionForPlayer(player) === match.session) {
            instances.leave(player, match.session, clock.cycle)
        }
    }

    private fun findValidTile(
        match: ClanMayhemMatch,
        preferred: CoordGrid,
        radius: Int,
    ): CoordGrid? {
        val candidates = sequence {
            yield(preferred)
            for (distance in 1..radius) {
                for (dx in -distance..distance) {
                    yield(preferred.translate(dx, -distance))
                    yield(preferred.translate(dx, distance))
                }
                for (dz in (-distance + 1) until distance) {
                    yield(preferred.translate(-distance, dz))
                    yield(preferred.translate(distance, dz))
                }
            }
        }
        return candidates.firstOrNull {
            match.safe(it) &&
                collision.isZoneValid(it) &&
                !collision.isWalkBlocked(it) &&
                players.none { other -> other.coords == it }
        }
    }

    private fun cancelSpawnProtectionOutsidePad(
        match: ClanMayhemMatch,
        player: Player,
        participant: ClanMayhemParticipant,
    ) {
        if (clock.cycle >= participant.spawnProtectionUntilTick) return
        val team = match.teamFor(player.accountId) ?: return
        val teamIndex = match.teams.values.indexOf(team)
        val sourcePad = ClanMayhemCrater.deploymentPads.getOrNull(teamIndex) ?: return
        val pad = match.mapSourceCoord(sourcePad)
        val outside =
            player.coords.level != pad.level ||
                maxOf(abs(player.coords.x - pad.x), abs(player.coords.z - pad.z)) >
                    SPAWN_PROTECTION_RADIUS
        if (outside) {
            participant.spawnProtectionUntilTick = 0
            player.mes("Your Clan Mayhem spawn protection has ended.")
        }
    }

    private fun protected(participant: ClanMayhemParticipant): Boolean =
        clock.cycle < participant.spawnProtectionUntilTick ||
            clock.cycle < participant.returnProtectionUntilTick

    private fun cancelProtection(participant: ClanMayhemParticipant) {
        participant.spawnProtectionUntilTick = 0
        participant.returnProtectionUntilTick = 0
    }

    private fun enemy(match: ClanMayhemMatch, first: Int, second: Int): Boolean {
        val a = match.teamFor(first) ?: return false
        val b = match.teamFor(second) ?: return false
        return a.matchTeamId != b.matchTeamId
    }

    private fun hasEmptyEntryLoadout(player: Player): Boolean =
        ClanMayhemEntryRules.canEnter(
            inventoryEmpty = player.inv.isEmpty(),
            equipmentEmpty = player.worn.isEmpty(),
        )

    private fun removeUnsafeRegistrationPlayer(player: Player) {
        val reg = registration ?: return
        val team = reg.teams.values.firstOrNull { player.accountId in it.roster } ?: return
        team.roster.remove(player.accountId)
        if (team.roster.isEmpty()) reg.teams.remove(team.clanId)
        val restored = snapshots.restore(player)
        player.mes(
            if (restored) {
                "You were removed from Clan Mayhem because your inventory or equipment changed."
            } else {
                "Your Mayhem item sandbox could not be restored; banking and teleporting remain locked."
            }
        )
    }

    private fun registeredParticipant(player: Player): ClanMayhemParticipant? =
        registration?.teams?.values?.firstNotNullOfOrNull { it.roster[player.accountId] }

    private fun online(accountId: Int): Player? = players.firstOrNull { it.accountId == accountId }

    private inline fun forEachOnline(
        match: ClanMayhemMatch,
        action: (Player, ClanMayhemParticipant) -> Unit,
    ) {
        match.teams.values.forEach { team ->
            team.roster.values.forEach { participant ->
                online(participant.accountId)?.let { action(it, participant) }
            }
        }
    }

    private fun broadcast(message: String) {
        players.forEach { it.mes(message, ChatType.Broadcast) }
    }

    private fun broadcastToMatch(match: ClanMayhemMatch, message: String) {
        forEachOnline(match) { player, _ -> player.mes(message, ChatType.Broadcast) }
    }

    private fun scheduleNext() {
        nextFormatIndex = (nextFormatIndex + 1) % ClanMayhemConfig.formats.size
        nextRegistrationTick = clock.cycle + AUTOMATIC_EVENT_INTERVAL_TICKS
    }

    private fun sourceCoord(tile: MayhemSourceTile): CoordGrid =
        CoordGrid(tile.x, tile.z, tile.level)

    private companion object {
        const val INSTANCE_KEY = "clan-mayhem"
        const val AUTOMATIC_EVENT_INTERVAL_TICKS = 6_000
        const val REWARD_ROOM_TICKS = 500
        const val RETURN_SEARCH_RADIUS = 10
        const val SPAWN_PROTECTION_RADIUS = 8
        const val FOG_KILL_CREDIT_TICKS = 34
        const val MINIMUM_CLAN_MEMBERSHIP_MILLIS = 30 * 60 * 1_000L
        const val CRATE_OBJECT_ID = 33_016
        val SUPPLY_NPCS =
            listOf(
                "npc.wild_cave_imp",
                "npc.wild_cave_goblin",
                "npc.wild_cave_pyrefiend",
                "npc.wild_cave_hobgoblin",
                "npc.wild_cave_cyclops",
                "npc.wild_cave_hellhound",
                "npc.wild_cave_demon",
                "npc.wild_cave_ork",
                "npc.wild_cave_darkbeast",
                "npc.wild_cave_knight",
                "npc.wild_cave_dragon",
            )
        val SUPPLY_NPC_DROPS =
            listOf(
                MayhemSupplyStack("obj.shark", 2),
                MayhemSupplyStack("obj.tbwt_cooked_karambwan", 2),
                MayhemSupplyStack("obj.deathrune", 60),
                MayhemSupplyStack("obj.bloodrune", 30),
                MayhemSupplyStack("obj.rune_arrow", 80),
            )
    }
}
