package org.rsmod.content.minigames.pestcontrol

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.constants
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSettings
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.mechanics.toxins.Toxin.cureAllToxins
import org.rsmod.api.player.disablePrayers
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.statRestoreAll
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.ui.ifSetHide
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.specials.energy.SpecialAttackEnergy
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap

@Singleton
internal class PestControlManager
@Inject
constructor(
    private val clock: MapClock,
    private val players: PlayerList,
    private val instances: InstanceManager,
    private val factory: PestControlNpcFactory,
    private val rewards: PestControlRewardService,
    private val eventBus: EventBus,
    private val collision: CollisionFlagMap,
    private val specialEnergy: SpecialAttackEnergy,
    private val locs: LocRepository,
) {
    private val log = InlineLogger()
    private val queue = PestControlQueue<PlayerUid>()
    private val matches = linkedMapOf<InstanceId, PestControlMatch>()
    private val playerMatches = mutableMapOf<PlayerUid, PestControlMatch>()

    fun joinQueue(player: Player): Boolean {
        if (matchFor(player) != null) {
            player.mes("You are already in a Pest Control game.")
            return false
        }
        if (queue.contains(player.uid)) {
            player.mes("You are already waiting for a Pest Control game.")
            return false
        }
        if (player.combatLevel < PestControlLander.minimumCombat) {
            player.mes(
                "You need a combat level of ${PestControlLander.minimumCombat} to use this lander."
            )
            return false
        }
        return when (queue.join(player.uid, clock.cycle)) {
            QueueJoinResult.Joined -> {
                PathingEntityCommon.telejump(player, collision, PestControlLander.queueCenter)
                player.ifOpenOverlay("interface.pest_lander_overlay", eventBus)
                updateQueueUi(player)
                player.mes("You board the Pest Control lander.")
                true
            }
            QueueJoinResult.Duplicate -> {
                player.mes("You are already on the lander.")
                false
            }
            QueueJoinResult.Full -> {
                player.mes("The lander is full. Please wait for the next departure.")
                false
            }
        }
    }

    fun teleportToOutpost(player: Player): Boolean {
        if (matchFor(player) != null) {
            player.mes("You cannot use that command during a Pest Control game.")
            return false
        }
        leaveQueue(player, message = false)
        PathingEntityCommon.telejump(player, collision, PestControlCoords.outpostExit)
        player.mes("You arrive at the Pest Control Novice boat.")
        return true
    }

    fun leaveQueue(player: Player, message: Boolean = true): Boolean {
        if (!queue.leave(player.uid)) return false
        player.ifCloseOverlay("interface.pest_lander_overlay", eventBus)
        if (message) player.mes("You leave the Pest Control lander.")
        return true
    }

    fun leaveLander(player: Player): Boolean {
        if (matchFor(player) != null) return false
        leaveQueue(player, message = false)
        PathingEntityCommon.telejump(player, collision, PestControlLander.exit)
        player.mes("You climb out of the Pest Control lander.")
        return true
    }

    fun matchFor(player: Player): PestControlMatch? = playerMatches[player.uid]

    fun isParticipant(player: Player, match: PestControlMatch): Boolean =
        playerMatches[player.uid] === match && player.uid in match.participants

    fun matchFor(npc: Npc): PestControlMatch? {
        val instanceId = instances.instanceForNpc(npc) ?: return null
        return matches[instanceId]?.takeIf { it.owns(npc) }
    }

    fun validateAttack(player: Player, npc: Npc): NpcAttackValidateResult {
        val match = matchFor(npc) ?: return NpcAttackValidateResult.Pass
        if (!isParticipant(player, match)) {
            return NpcAttackValidateResult.Deny("That portal belongs to another Pest Control game.")
        }
        val portal = match.portalFor(npc) ?: return NpcAttackValidateResult.Pass
        if (
            !PestControlRules.portalCanTakeDamage(
                destroyed = match.portalTracker.isDestroyed(portal.color),
                running = match.isRunning,
            )
        ) {
            return NpcAttackValidateResult.Deny("That portal can no longer be attacked.")
        }
        return NpcAttackValidateResult.BypassSingleWayPvnRestriction
    }

    fun handleNpcKill(npc: Npc) {
        val match = matchFor(npc) ?: return
        val portal = match.portalFor(npc) ?: return
        handlePortalDeath(match, portal)
    }

    fun markPlayerDeath(player: Player) {
        val participant = matchFor(player)?.participant(player.uid) ?: return
        participant.pendingDeathRespawn = true
        participant.lastUi = null
    }

    fun logout(player: Player) {
        leaveQueue(player, message = false)
        val match = matchFor(player) ?: return
        match.participants.remove(player.uid) ?: return
        playerMatches.remove(player.uid)
        if (match.participants.isEmpty() && match.isRunning) {
            beginResult(match, PestControlResult.Lost)
        }
    }

    fun onPlayerPostTick(player: Player) {
        if (queue.contains(player.uid) && !insideLander(player.coords)) {
            leaveQueue(player)
        }

        val match = matchFor(player) ?: return
        val participant = match.participant(player.uid) ?: return
        if (participant.pendingDeathRespawn) {
            val respawn = match.mapSourceCoord(PestControlCoords.playerEntry)
            PathingEntityCommon.telejump(player, collision, respawn)
            instances.finalizeEntry(player, match.session, clock.cycle)
            participant.pendingDeathRespawn = false
            participant.lastUi = null
            player.ifOpenOverlay("interface.pest_status_overlay", eventBus)
            updateMatchUi(player, match, participant, force = true)
            player.mes("You are returned to the Pest Control island.")
            return
        }
        if (!insideMatch(player.coords, match)) {
            removeParticipant(player, match, "You have left the Pest Control game.")
        }
    }

    fun tick() {
        tickQueue()
        for (match in matches.values.toList()) tickMatch(match)
    }

    fun startImmediately(player: Player): Boolean {
        if (matchFor(player) != null) {
            player.mes("You are already in a Pest Control game.")
            return false
        }
        if (!queue.contains(player.uid)) {
            player.mes("Use ::pc, then climb the Novice gangplank before using ::pcstart.")
            return false
        }
        if (!insideLander(player.coords)) {
            leaveQueue(player, message = false)
            player.mes("You must be inside the waiting lander to use ::pcstart.")
            return false
        }
        player.mes("Launching the Pest Control lander now.")
        return startMatch(listOf(player))
    }

    fun status(player: Player): String {
        if (queue.contains(player.uid)) {
            return "Pest Control queue players=${queue.size}/$PC_MAX_PLAYERS time=${formatSeconds(queue.secondsRemaining(clock.cycle))}"
        }
        val match = matchFor(player) ?: return "You are not queued or in a Pest Control match."
        return "Pest Control instance=${match.session.id} state=${match.state} " +
            "time=${formatTicks(match.remainingTicks(clock.cycle))} portals=${match.destroyedPortalCount}/4"
    }

    private fun tickQueue() {
        for (uid in queue.snapshot()) {
            val player = uid.resolve(players)
            if (player == null || !insideLander(player.coords)) {
                queue.leave(uid)
                player?.ifCloseOverlay("interface.pest_lander_overlay", eventBus)
                continue
            }
            if (clock.cycle % 5 == 0) updateQueueUi(player)
        }
        if (queue.ready(clock.cycle)) startQueue()
    }

    private fun startQueue(): Boolean {
        val valid =
            queue
                .snapshot()
                .mapNotNull { it.resolve(players) }
                .filter {
                    it.combatLevel >= PestControlLander.minimumCombat && insideLander(it.coords)
                }
        if (valid.size < PestControlTuning.minimumPlayers) return false
        return startMatch(valid.take(PC_MAX_PLAYERS))
    }

    private fun startMatch(selected: List<Player>): Boolean {
        if (selected.isEmpty()) return false
        val missingDefinition = factory.firstMissingPortalDefinition()
        if (missingDefinition != null) {
            log.error { "[PestControl] cannot start; missing portal mapping=$missingDefinition" }
            selected.forEach {
                it.mes("Pest Control cannot start because a portal is unavailable.")
            }
            return false
        }
        val owner = selected.first()
        val spec =
            InstanceSettings(
                    maxPlayers = PC_MAX_PLAYERS,
                    reclaimTicks = 1,
                    graceTicks = 1,
                    destroyWhenEmpty = true,
                    description = "Pest Control portal-only",
                )
                .withArea(
                    InstanceArea.copyRegions(
                        regionIds = listOf(PC_SOURCE_REGION),
                        level = 0,
                        enterCoord =
                            RegionLocal(
                                level = PestControlCoords.playerEntry.level,
                                regionZoneX = PestControlCoords.playerEntry.mx,
                                regionZoneZ = PestControlCoords.playerEntry.mz,
                                localX = PestControlCoords.playerEntry.lx,
                                localZ = PestControlCoords.playerEntry.lz,
                            ),
                        exitCoord = PestControlCoords.outpostExit,
                    ),
                    settingsRowId = 0,
                )
        val created =
            instances.create(owner, INSTANCE_KEY, spec, InstanceAccess.Friends, clock.cycle)
        if (created !is InstanceManager.Result.Created) {
            val reason =
                (created as? InstanceManager.Result.Failed)?.reason
                    ?: "The Pest Control island could not be created."
            selected.forEach { it.mes(reason) }
            return false
        }

        val match =
            PestControlMatch(
                session = created.session,
                startedAt = clock.cycle,
                instanceEntry = created.enter,
            )
        matches[created.session.id] = match
        val openedGates: Int
        try {
            openedGates = openArenaGates(match)
            for (color in PortalColor.entries) {
                match.portals[color] = factory.spawnPortal(match, color)
            }
        } catch (error: Exception) {
            matches.remove(created.session.id)
            instances.cancelPendingEntry(owner, clock.cycle)
            log.error { "[PestControl] rolled back failed startup: ${error.message}" }
            selected.forEach { it.mes("Pest Control failed to start. Please try again.") }
            return false
        }

        match.state = PestControlState.RUNNING
        for ((index, player) in selected.withIndex()) {
            queue.leave(player.uid)
            val entry =
                if (index == 0) {
                    created.enter
                } else {
                    when (
                        val joined =
                            instances.join(player, created.session, clock.cycle, forceAccess = true)
                    ) {
                        is InstanceManager.Result.Joined -> joined.enter
                        is InstanceManager.Result.Failed -> {
                            player.mes(joined.reason)
                            continue
                        }
                        else -> continue
                    }
                }
            player.ifCloseOverlay("interface.pest_lander_overlay", eventBus)
            PathingEntityCommon.telejump(player, collision, entry)
            instances.finalizeEntry(player, created.session, clock.cycle)
            val participant = PestControlParticipant(player.uid)
            match.participants[player.uid] = participant
            playerMatches[player.uid] = match
            player.ifOpenOverlay("interface.pest_status_overlay", eventBus)
            updateMatchUi(player, match, participant, force = true)
            player.mes("Destroy all four portals. Every portal is attackable now!")
        }
        log.info {
            "[PestControl] start instance=${match.session.id} " +
                "players=${match.participants.size} portals=4 gatesOpened=$openedGates"
        }
        return true
    }

    private fun tickMatch(match: PestControlMatch) {
        when (match.state) {
            PestControlState.RUNNING -> tickRunning(match)
            PestControlState.WON,
            PestControlState.LOST -> {
                if (clock.cycle >= (match.resultTick ?: clock.cycle) + RESULT_DELAY_TICKS) {
                    rewardAndCleanup(match)
                }
            }
            else -> Unit
        }
    }

    private fun tickRunning(match: PestControlMatch) {
        for (portal in match.portals.values) {
            if (!match.portalTracker.isDestroyed(portal.color) && portal.npc.hitpoints <= 0) {
                handlePortalDeath(match, portal)
                if (!match.isRunning) return
            }
        }

        val decision =
            PestControlResultArbiter.decide(
                destroyedPortals = match.destroyedPortalCount,
                timerExpired = match.remainingTicks(clock.cycle) <= 0,
            )
        if (decision != null) {
            beginResult(match, decision)
            return
        }

        for ((uid, participant) in match.participants) {
            val player = uid.resolve(players) ?: continue
            updateMatchUi(player, match, participant, force = clock.cycle % UI_INTERVAL_TICKS == 0)
        }
    }

    private fun handlePortalDeath(match: PestControlMatch, portal: PestControlPortal) {
        if (!match.isRunning) return
        val progress = match.portalTracker.markDestroyed(portal.color)
        if (!progress.newlyDestroyed) return
        val text =
            when (progress.destroyedCount) {
                1 -> "One portal has been destroyed. Three remain."
                2 -> "Two portals remain."
                3 -> "One portal remains."
                else -> "All four portals have been destroyed!"
            }
        message(match, text)
        forceUi(match)
        if (progress.complete) beginResult(match, PestControlResult.Won)
    }

    private fun beginResult(match: PestControlMatch, result: PestControlResult) {
        if (!match.isRunning) return
        match.result = result
        match.resultTick = clock.cycle
        match.state =
            if (result == PestControlResult.Won) PestControlState.WON else PestControlState.LOST
        if (result == PestControlResult.Lost) {
            message(match, "The abandoned Pest Control game has timed out. No points were awarded.")
        }
        log.info {
            "[PestControl] result instance=${match.session.id} result=$result portals=${match.destroyedPortalCount}"
        }
    }

    private fun rewardAndCleanup(match: PestControlMatch) {
        if (match.cleanupStarted) return
        match.cleanupStarted = true
        match.state = PestControlState.REWARDING
        for ((uid, participant) in match.participants.toMap()) {
            val player = uid.resolve(players) ?: continue
            if (match.result == PestControlResult.Won) rewards.award(player, match, participant)
        }
        match.state = PestControlState.CLEANING_UP
        cleanup(match)
    }

    private fun cleanup(match: PestControlMatch) {
        for ((uid, participant) in match.participants.toMap()) {
            val player = uid.resolve(players) ?: continue
            participant.lastUi = null
            player.cancelActiveCoroutine()
            player.clearInteraction()
            player.abortRoute()
            player.ifCloseOverlay("interface.pest_status_overlay", eventBus)
            if (instances.sessionForPlayer(player) === match.session) {
                instances.leave(player, match.session, clock.cycle)
            }
            PathingEntityCommon.telejump(player, collision, PestControlCoords.outpostExit)
            resetPlayer(player)
            playerMatches.remove(uid)
        }
        match.participants.clear()
        for (portal in match.portals.values) factory.delete(portal.npc)
        match.portals.clear()
        match.state = PestControlState.CLOSED
        matches.remove(match.session.id)
        log.info { "[PestControl] cleanup instance=${match.session.id}" }
    }

    private fun removeParticipant(player: Player, match: PestControlMatch, reason: String) {
        match.participants.remove(player.uid) ?: return
        playerMatches.remove(player.uid)
        player.cancelActiveCoroutine()
        player.clearInteraction()
        player.abortRoute()
        player.ifCloseOverlay("interface.pest_status_overlay", eventBus)
        if (instances.sessionForPlayer(player) === match.session) {
            instances.leave(player, match.session, clock.cycle)
        }
        player.mes(reason)
        if (match.participants.isEmpty() && match.isRunning) {
            beginResult(match, PestControlResult.Lost)
        }
    }

    private fun resetPlayer(player: Player) {
        player.disablePrayers()
        player.cureAllToxins()
        player.statRestoreAll(COMBAT_STATS)
        specialEnergy.restoreFullSpecialEnergy(player)
        player.runEnergy = constants.run_max_energy
        UpdateRun.energy(player, player.runEnergy)
    }

    private fun updateQueueUi(player: Player) {
        player.ifSetText(
            "component.pest_lander_overlay:pest_lander_over_time",
            formatSeconds(queue.secondsRemaining(clock.cycle)),
        )
        player.ifSetText(
            "component.pest_lander_overlay:pest_lander_over_pl",
            "${queue.size}/$PC_MAX_PLAYERS",
        )
        player.ifSetText(
            "component.pest_lander_overlay:pest_lander_over_points",
            rewards.points(player).toString(),
        )
        player.ifSetText(
            "component.pest_lander_overlay:pest_lander_over_level",
            PestControlLander.minimumCombat.toString(),
        )
    }

    private fun updateMatchUi(
        player: Player,
        match: PestControlMatch,
        participant: PestControlParticipant,
        force: Boolean,
    ) {
        val portals = PortalColor.entries.map { match.portals.getValue(it) }
        val snapshot =
            PestControlUiSnapshot(
                secondsRemaining = ticksToSeconds(match.remainingTicks(clock.cycle)),
                portalHitpoints = portals.map { it.hitpoints },
                destroyed = portals.map { match.portalTracker.isDestroyed(it.color) },
            )
        if (!force && participant.lastUi == snapshot) return
        participant.lastUi = snapshot
        PestControlOverlay.hiddenComponents.forEach { player.ifSetHide(it, true) }
        for ((index, color) in PortalColor.entries.withIndex()) {
            player.ifSetText(color.healthComponent, snapshot.portalHitpoints[index].toString())
        }
    }

    private fun openArenaGates(match: PestControlMatch): Int {
        val southwestZone = ZoneKey.from(match.mapSourceCoord(SOURCE_ORIGIN))
        var opened = 0
        for (zoneX in 0 until ARENA_ZONE_LENGTH) {
            for (zoneZ in 0 until ARENA_ZONE_LENGTH) {
                val gates =
                    locs
                        .findAll(southwestZone.translate(zoneX, zoneZ))
                        .filter { loc ->
                            val type = ServerCacheManager.getObject(loc.id) ?: return@filter false
                            val actions = (0 until 5).map(type.actions::getOpOrNull)
                            PestControlRules.isArenaGate(type.name, type.internalName, actions)
                        }
                        .toList()
                for (gate in gates) {
                    if (locs.del(gate, Int.MAX_VALUE)) opened++
                }
            }
        }
        if (opened == 0) log.warn { "[PestControl] no openable arena gates were found" }
        return opened
    }

    private fun forceUi(match: PestControlMatch) {
        for ((uid, participant) in match.participants) {
            uid.resolve(players)?.let { updateMatchUi(it, match, participant, force = true) }
        }
    }

    private fun message(match: PestControlMatch, text: String) {
        match.participants.keys.mapNotNull { it.resolve(players) }.forEach { it.mes(text) }
    }

    private fun insideLander(coords: CoordGrid): Boolean =
        coords.level == PestControlLander.queueCenter.level &&
            coords.chebyshevDistance(PestControlLander.queueCenter) <= LANDER_RADIUS

    private fun insideMatch(coords: CoordGrid, match: PestControlMatch): Boolean {
        val origin = match.mapSourceCoord(SOURCE_ORIGIN)
        return coords.level == origin.level &&
            coords.x in origin.x until origin.x + 64 &&
            coords.z in origin.z until origin.z + 64
    }

    private fun formatTicks(ticks: Int): String = formatSeconds(ticksToSeconds(ticks))

    private fun formatSeconds(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

    private fun ticksToSeconds(ticks: Int): Int = (ticks.coerceAtLeast(0) * 3 + 4) / 5

    private companion object {
        const val INSTANCE_KEY = "pest_control"
        const val LANDER_RADIUS = 6
        const val UI_INTERVAL_TICKS = 2
        const val RESULT_DELAY_TICKS = 1
        const val ARENA_ZONE_LENGTH = 8
        val SOURCE_ORIGIN = CoordGrid(2624, 2560, 0)
        val COMBAT_STATS =
            listOf(
                "stat.attack",
                "stat.strength",
                "stat.defence",
                "stat.hitpoints",
                "stat.ranged",
                "stat.magic",
                "stat.prayer",
            )
    }
}
