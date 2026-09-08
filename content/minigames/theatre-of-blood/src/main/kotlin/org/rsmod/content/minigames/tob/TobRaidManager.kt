package org.rsmod.content.minigames.tob

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.done.hitmark_groups
import org.rsmod.api.config.refs.params
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSettings
import org.rsmod.api.instances.RaidLeaveResult
import org.rsmod.api.instances.RaidRewardClaimResult
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.heal
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.clearInteractionRoute
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.disablePrayers
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.NpcPartialProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.MiscOutput
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statRestoreAll
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.walkTo
import org.rsmod.api.specials.energy.SpecialAttackEnergy
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.hit.Hitmark
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.Direction
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

internal fun tobChestTestScore(currentScore: Int): Int = currentScore.coerceAtLeast(1)

/** Donor lobby bounds from Kronos' recruiting-board BOUNDS2 listener. */
internal fun isTobLobby(coords: CoordGrid): Boolean =
    coords.level == 0 && coords.x in 3640..3680 && coords.z in 3201..3235

internal fun normalizeTobSpecialHitDelay(delay: Int): Int = delay.coerceAtLeast(1)

internal fun canQueueTobSpecialHit(sourceSlotAssigned: Boolean, sourceHitpoints: Int): Boolean =
    sourceSlotAssigned && sourceHitpoints > 0

internal fun isShieldedByVerzikPillar(
    source: CoordGrid,
    sourceSize: Int,
    target: CoordGrid,
    pillar: CoordGrid,
    pillarSize: Int,
    canonicalSafeTiles: Set<CoordGrid>,
): Boolean {
    if (source.level != target.level || source.level != pillar.level) return false
    if (target in canonicalSafeTiles) return true

    val sourceCenterX = source.x + sourceSize.coerceAtLeast(1) / 2.0
    val sourceCenterZ = source.z + sourceSize.coerceAtLeast(1) / 2.0
    val targetCenterX = target.x + 0.5
    val targetCenterZ = target.z + 0.5
    val pillarCenterX = pillar.x + pillarSize.coerceAtLeast(1) / 2.0
    val pillarCenterZ = pillar.z + pillarSize.coerceAtLeast(1) / 2.0
    val rayX = targetCenterX - sourceCenterX
    val rayZ = targetCenterZ - sourceCenterZ
    val rayLengthSquared = rayX * rayX + rayZ * rayZ
    if (rayLengthSquared == 0.0) return false

    val projection =
        ((pillarCenterX - sourceCenterX) * rayX + (pillarCenterZ - sourceCenterZ) * rayZ) /
            rayLengthSquared
    if (projection <= 0.0 || projection >= 1.0) return false

    val closestX = sourceCenterX + projection * rayX
    val closestZ = sourceCenterZ + projection * rayZ
    val offsetX = pillarCenterX - closestX
    val offsetZ = pillarCenterZ - closestZ
    val protectionRadius = maxOf(1.75, pillarSize.coerceAtLeast(1) / 2.0 + 0.75)
    return offsetX * offsetX + offsetZ * offsetZ <= protectionRadius * protectionRadius
}

internal fun verzikPillarHealthAfterHit(current: Int, damage: Int): Int {
    require(current >= 0) { "Pillar health cannot be negative." }
    require(damage >= 0) { "Pillar damage cannot be negative." }
    return (current - damage).coerceAtLeast(0)
}

internal fun maidenMatomenosHealAmount(remainingHitpoints: Int): Int =
    remainingHitpoints.coerceAtLeast(0) * 2

internal fun maidenBarrierDestination(player: CoordGrid, barrier: CoordGrid): CoordGrid {
    val dx = player.x - barrier.x
    val dz = player.z - barrier.z
    return when {
        abs(dx) >= abs(dz) && dx > 0 -> CoordGrid(barrier.x - 2, barrier.z, player.level)
        abs(dx) >= abs(dz) -> CoordGrid(barrier.x + 2, barrier.z, player.level)
        dz > 0 -> CoordGrid(barrier.x, barrier.z - 2, player.level)
        else -> CoordGrid(barrier.x, barrier.z + 2, player.level)
    }
}

internal fun isEnteringMaidenArena(player: CoordGrid, barrier: CoordGrid): Boolean =
    player.level == barrier.level && player.x > barrier.x

internal fun isXarpusStagingBarrierCrossing(
    player: CoordGrid,
    destination: CoordGrid,
    barrier: CoordGrid,
    exitBarrierCenter: CoordGrid,
    arena: CoordGrid,
): Boolean =
    barrier.level == exitBarrierCenter.level &&
        barrier.z == exitBarrierCenter.z &&
        abs(barrier.x - exitBarrierCenter.x) <= 1 &&
        destination.chebyshevDistance(arena) > player.chebyshevDistance(arena)

internal fun completedBarrierAdvancesRoom(room: TobRoom): Boolean =
    when (room) {
        TobRoom.MAIDEN,
        TobRoom.BLOAT,
        TobRoom.NYLOCAS,
        TobRoom.SOTETSEG -> true
        TobRoom.XARPUS,
        TobRoom.VERZIK,
        TobRoom.TREASURE -> false
    }

/**
 * The transforming throne (32737) and opened treasure doorway (32738) are equal-sized cache states
 * of the same scene object and must retain the source-map throne anchor through the whole reveal.
 */
internal fun verzikTreasurePassageAnchor(animationAnchor: CoordGrid): CoordGrid = animationAnchor

/**
 * Monumental chest morph states: teammate/common, teammate/unique, owner/common, owner/unique. The
 * unique states are the cache-native purple aura/beam used by the Theatre reward vault.
 */
internal fun tobRewardChestState(isOwner: Boolean, isUnique: Boolean): Int =
    when {
        isOwner && isUnique -> 3
        isOwner -> 2
        isUnique -> 1
        else -> 0
    }

internal fun shouldReconcileSuppressedRaidNpcDeath(
    suppressRewards: Boolean,
    isSlotAssigned: Boolean,
    hitpoints: Int,
): Boolean = suppressRewards && !isSlotAssigned && hitpoints <= 0

internal fun shouldStartChallengeAtBarrier(
    roomIndex: Int,
    currentRoomIndex: Int,
    roomState: TobRoomState,
): Boolean = roomIndex == currentRoomIndex && roomState == TobRoomState.READY

internal fun squareChebyshevDistance(
    first: CoordGrid,
    firstSize: Int,
    second: CoordGrid,
    secondSize: Int,
): Int {
    require(firstSize > 0 && secondSize > 0)
    if (first.level != second.level) return Int.MAX_VALUE
    val firstMaxX = first.x + firstSize - 1
    val firstMaxZ = first.z + firstSize - 1
    val secondMaxX = second.x + secondSize - 1
    val secondMaxZ = second.z + secondSize - 1
    val xDistance = maxOf(0, second.x - firstMaxX, first.x - secondMaxX)
    val zDistance = maxOf(0, second.z - firstMaxZ, first.z - secondMaxZ)
    return maxOf(xDistance, zDistance)
}

internal enum class VerzikPhaseTwoMeleePosition {
    OUTSIDE,
    SLAM,
}

internal fun verzikPhaseTwoMeleePosition(
    player: CoordGrid,
    boss: CoordGrid,
    bossSize: Int,
): VerzikPhaseTwoMeleePosition {
    require(bossSize > 0)
    if (player.level != boss.level) return VerzikPhaseTwoMeleePosition.OUTSIDE
    val bossMaxX = boss.x + bossSize - 1
    val bossMaxZ = boss.z + bossSize - 1
    val xDistance = maxOf(0, boss.x - player.x, player.x - bossMaxX)
    val zDistance = maxOf(0, boss.z - player.z, player.z - bossMaxZ)
    return when (xDistance + zDistance) {
        1 -> VerzikPhaseTwoMeleePosition.SLAM
        else -> VerzikPhaseTwoMeleePosition.OUTSIDE
    }
}

internal fun verzikPhaseTwoActiveAnchor(transitionEnd: CoordGrid): CoordGrid = transitionEnd

internal fun isVerzikEmptyThroneVisible(stage: VerzikEncounterStage): Boolean =
    stage in
        setOf(
            VerzikEncounterStage.PHASE_ONE_TO_TWO,
            VerzikEncounterStage.PHASE_TWO,
            VerzikEncounterStage.PHASE_TWO_TO_THREE,
            VerzikEncounterStage.PHASE_THREE,
            VerzikEncounterStage.ENRAGED,
            VerzikEncounterStage.DYING,
        )

internal fun shouldVerzikBodySlam(chanceRoll: Int): Boolean {
    require(chanceRoll in 0 until VerzikNormalRules.PHASE_TWO_SLAM_CHANCE_DENOMINATOR)
    return chanceRoll != 0
}

internal fun verzikKnockbackVector(
    player: CoordGrid,
    boss: CoordGrid,
    bossSize: Int,
): Pair<Int, Int> {
    val centerX = boss.x + bossSize / 2
    val centerZ = boss.z + bossSize / 2
    val dx = player.x.compareTo(centerX)
    val dz = player.z.compareTo(centerZ)
    return if (dx == 0 && dz == 0) 0 to -1 else dx to dz
}

internal fun shouldSummonVerzikNylocas(
    lightningCount: Int,
    chanceRoll: Int,
    hasAthanatos: Boolean,
    healthPercent: Int,
): Boolean =
    lightningCount > 0 &&
        lightningCount % 3 == 0 &&
        chanceRoll == 0 &&
        !hasAthanatos &&
        healthPercent > 35

internal fun isVerzikPhaseThreeEnraged(healthPercent: Int): Boolean = healthPercent <= 20

internal fun verzikBombBaseDamage(distance: Int): Int = if (distance in 0..3) 64 shr distance else 0

internal fun isAthanatosPoisonSource(weaponKey: String?, helmKey: String?): Boolean {
    val weapon = weaponKey?.lowercase(Locale.ROOT).orEmpty()
    val helm = helmKey?.lowercase(Locale.ROOT).orEmpty()
    val poisonedWeapon = Regex(".*_p(\\+{0,2})(?:_|$).*").matches(weapon)
    val venomWeapon =
        weapon.contains("toxic_blowpipe_loaded") ||
            weapon.contains("abyssal_tentacle") ||
            weapon.contains("toxic_staff_of_the_dead")
    val venomHelm =
        helm in
            setOf(
                "obj.serpentine_helm_charged",
                "obj.serpentine_helm_charged_cyan",
                "obj.serpentine_helm_charged_red",
            )
    return poisonedWeapon || venomWeapon || venomHelm
}

internal fun verzikMatomenosCount(activePlayers: Int): Int = if (activePlayers > 0) 2 else 0

internal fun nylocasPillarApproach(
    nylocas: CoordGrid,
    nylocasSize: Int,
    pillar: CoordGrid,
    pillarSize: Int,
): CoordGrid {
    require(nylocasSize > 0 && pillarSize > 0)
    val nylocasMaxX = nylocas.x + nylocasSize - 1
    val nylocasMaxZ = nylocas.z + nylocasSize - 1
    val pillarMaxX = pillar.x + pillarSize - 1
    val pillarMaxZ = pillar.z + pillarSize - 1
    val centerOffset = ((pillarSize - nylocasSize) / 2).coerceAtLeast(0)
    return when {
        nylocasMaxX < pillar.x ->
            CoordGrid(pillar.x - nylocasSize, pillar.z + centerOffset, pillar.level)
        nylocas.x > pillarMaxX -> CoordGrid(pillarMaxX + 1, pillar.z + centerOffset, pillar.level)
        nylocasMaxZ < pillar.z ->
            CoordGrid(pillar.x + centerOffset, pillar.z - nylocasSize, pillar.level)
        nylocas.z > pillarMaxZ -> CoordGrid(pillar.x + centerOffset, pillarMaxZ + 1, pillar.level)
        else -> nylocas
    }
}

@Singleton
internal class TobRaidManager
@Inject
constructor(
    private val clock: MapClock,
    private val players: PlayerList,
    private val instances: InstanceManager,
    private val npcs: NpcRepository,
    private val objects: ObjRepository,
    private val interactions: AiPlayerInteractions,
    private val world: WorldRepository,
    private val locs: LocRepository,
    private val routeFactory: RouteFactory,
    private val rayCast: RayCastValidator,
    private val collision: CollisionFlagMap,
    private val rewards: TobRewardService,
    private val ui: TobUi,
    private val supplies: TobSupplies,
    private val partyBoard: TobPartyBoard,
    private val random: GameRandom,
    private val specialEnergy: SpecialAttackEnergy,
    private val npcHitModifier: StandardNpcHitModifier,
) {
    private val log = InlineLogger()
    private val parties = linkedMapOf<PlayerUid, TobParty>()
    private val membership = mutableMapOf<PlayerUid, TobParty>()
    private val invitations = mutableMapOf<PlayerUid, TobParty>()
    private val previousLobbyPlayerOps = mutableMapOf<PlayerUid, String?>()
    private val viewedParties = mutableMapOf<PlayerUid, java.util.UUID>()
    private val raids = mutableMapOf<InstanceId, TobRaidSession>()
    private val participantRaids = mutableMapOf<PlayerUid, TobRaidSession>()
    private val hazardExpiry = mutableMapOf<Npc, Int>()
    private val pendingAborts = mutableMapOf<InstanceId, Int>()
    private val pendingEntryResets = mutableMapOf<InstanceId, Int>()
    private val pendingVerzikTreasurePassages =
        mutableMapOf<InstanceId, VerzikTreasurePassageRuntime>()
    private val pendingSoloDeathExits = mutableSetOf<PlayerUid>()
    private val rewardChestTiles = mutableMapOf<InstanceId, Map<PlayerUid, CoordGrid>>()
    private val rewardChestLocs = mutableMapOf<InstanceId, List<LocInfo>>()
    private val rewardChestLocIds = mutableMapOf<InstanceId, Map<PlayerUid, Int>>()

    fun validateAssets() {
        TobLocs.all.forEach {
            requireNotNull(ServerCacheManager.getObject(it)) { "Missing ToB loc $it" }
        }
        TobNpcs.allCore.forEach {
            requireNotNull(ServerCacheManager.getNpc(it)) { "Missing ToB npc $it" }
        }
        TobAssets.sequences.forEach {
            requireNotNull(ServerCacheManager.getAnim(it)) { "Missing ToB sequence $it" }
        }
        TobAssets.spotanims.forEach {
            requireNotNull(RSCM.getReverseMapping(RSCMType.SPOTANIM, it)) {
                "Missing ToB spotanim $it"
            }
        }
        requireNotNull(ServerCacheManager.getVarbit(TobLocs.VERZIK_THRONE_VISIBLE_VARBIT)) {
            "Missing Verzik throne visibility varbit ${TobLocs.VERZIK_THRONE_VISIBLE_VARBIT}"
        }
        TobLocs.rewardChestVarbits.values.forEach { varbit ->
            requireNotNull(ServerCacheManager.getVarbit(varbit)) {
                "Missing ToB monumental chest varbit $varbit"
            }
        }
        requireNotNull(ServerCacheManager.getItem(TobItems.DAWNBRINGER)) { "Missing Dawnbringer" }
        partyBoard.validateAssets()
        rewards.verifyDefinitions()
        supplies.verifyDefinitions()
    }

    fun teleportLobby(player: Player): Boolean {
        if (raidFor(player) != null) {
            player.mes("Leave or complete your active Theatre before returning to Ver Sinhaza.")
            return false
        }
        prepare(player)
        PathingEntityCommon.telejump(player, collision, TOB_LOBBY)
        syncLobbyInviteOption(player)
        player.mes("You arrive at Ver Sinhaza, outside the Theatre of Blood.")
        return true
    }

    fun syncLobbyInviteOption(player: Player) {
        val current = player.options.getOrNull(TOB_INVITE_PLAYER_OP_SLOT)
        if (isTobLobby(player.coords) && raidFor(player) == null) {
            if (current != TOB_INVITE_PLAYER_OP) {
                previousLobbyPlayerOps[player.uid] = current
                MiscOutput.setPlayerOp(player, TOB_INVITE_PLAYER_OP_SLOT, TOB_INVITE_PLAYER_OP)
            }
        } else {
            val previous = previousLobbyPlayerOps.remove(player.uid)
            if (current == TOB_INVITE_PLAYER_OP) {
                MiscOutput.setPlayerOp(
                    player,
                    TOB_INVITE_PLAYER_OP_SLOT,
                    previous,
                    priority = previous != null,
                )
            }
            invitations.remove(player.uid)
        }
    }

    private fun syncLobbyInviteOptions() = players.forEach(::syncLobbyInviteOption)

    fun openLobby(player: Player): Boolean {
        raidFor(player)?.let {
            return rejoin(player, it)
        }
        openPartyList(player)
        return true
    }

    fun openPartyList(player: Player) =
        partyBoard.openList(
            player,
            parties.values.map { party ->
                TobPartyListEntry(
                    party.boardSlot,
                    label(party.leader),
                    party.members.size,
                    party.preferredSize,
                    party.preferredCombat,
                )
            },
        )

    fun enterTheatre(player: Player): Boolean {
        raidFor(player)?.let {
            return rejoin(player, it)
        }
        val existing = partyFor(player)
        if (existing == null) {
            createParty(player)
            player.mes(
                "You create a Theatre party. Right-click players in this lobby and choose Invite, " +
                    "or click the entrance again to begin solo."
            )
            return true
        }
        existing.activeSession?.let {
            return rejoin(player, it)
        }
        if (existing.leader != player.uid) {
            player.mes("The party leader must enter first to build the Theatre.")
            return false
        }
        return start(player, TobMode.NORMAL)
    }

    fun createParty(player: Player): TobParty {
        partyFor(player)?.let {
            return it
        }
        val slot =
            TobPartyBoard.BOARD_SLOTS.first { candidate ->
                parties.values.none { it.boardSlot == candidate }
            }
        return TobParty(player.uid, slot).also {
            parties[player.uid] = it
            membership[player.uid] = it
            player.mes("You create a Theatre of Blood party.")
        }
    }

    fun invite(player: Player, targetName: String): Boolean {
        val target = findPlayer(targetName)
        if (target == null) {
            player.mes("That player is unavailable.")
            return false
        }
        val partyId = offerLobbyInvite(player, target) ?: return false
        target.mes(
            "${player.displayName} invited you to a Theatre party. Use ::tobparty join to accept."
        )
        return invitations[target.uid]?.id == partyId
    }

    fun offerLobbyInvite(player: Player, target: Player): UUID? {
        val party = partyFor(player)
        if (party == null) {
            player.mes("Click the Theatre entrance first to create your party.")
            return null
        }
        if (party.leader != player.uid || party.activeSession != null) {
            player.mes("Only the leader can invite before the raid starts.")
            return null
        }
        if (!isTobLobby(player.coords) || !isTobLobby(target.coords)) {
            player.mes("Both players must be inside the Theatre lobby to use Invite.")
            return null
        }
        if (party.members.size >= TOB_MAX_PARTY_SIZE) {
            player.mes("Your Theatre party is full.")
            return null
        }
        if (target.uid == player.uid || partyFor(target) != null) {
            player.mes("That player is unavailable or already in a Theatre party.")
            return null
        }
        invitations[target.uid] = party
        player.mes("Invitation sent to ${target.displayName}.")
        refreshPartyViewers(party)
        return party.id
    }

    fun joinInvited(player: Player): Boolean {
        if (partyFor(player) != null) return false
        val party = invitations.remove(player.uid)
        if (party == null) {
            player.mes("You do not have a valid Theatre invitation.")
            return false
        }
        return joinInvitedParty(player, party)
    }

    fun respondToLobbyInvite(player: Player, expectedPartyId: UUID, accepted: Boolean): Boolean {
        val party = invitations[player.uid]
        if (party == null || party.id != expectedPartyId) {
            player.mes("That Theatre invitation is no longer available.")
            return false
        }
        invitations.remove(player.uid)
        if (!accepted) {
            party.leader
                .resolve(players)
                ?.mes("${player.displayName} declined your Theatre invite.")
            player.mes("You decline the Theatre party invitation.")
            return true
        }
        return joinInvitedParty(player, party)
    }

    fun cancelLobbyInvite(player: Player, expectedPartyId: UUID) {
        if (invitations[player.uid]?.id == expectedPartyId) invitations.remove(player.uid)
    }

    private fun joinInvitedParty(player: Player, party: TobParty): Boolean {
        if (
            party.activeSession != null ||
                party.members.size >= TOB_MAX_PARTY_SIZE ||
                membership.containsKey(player.uid) ||
                !isTobLobby(player.coords)
        ) {
            player.mes("You do not have a valid Theatre invitation.")
            return false
        }
        party.members += player.uid
        membership[player.uid] = party
        message(party, "${player.displayName} joins the Theatre party.")
        refreshPartyViewers(party)
        return true
    }

    fun kick(player: Player, targetName: String): Boolean {
        val party = partyFor(player) ?: return false
        if (party.leader != player.uid || party.activeSession != null) return false
        val target =
            party.members.firstOrNull { label(it).equals(targetName, true) } ?: return false
        if (target == party.leader) return false
        party.members.remove(target)
        membership.remove(target)
        target.resolve(players)?.mes("You were removed from the Theatre party.")
        refreshPartyViewers(party)
        return true
    }

    fun leaveParty(player: Player): Boolean {
        val party = partyFor(player) ?: return false
        party.activeSession?.let {
            return leaveRaid(player, it, true)
        }
        party.members.remove(player.uid)
        membership.remove(player.uid)
        if (party.leader == player.uid) transferOrDisband(party)
        player.mes("You leave the Theatre party.")
        refreshPartyViewers(party)
        return true
    }

    fun openOwnPartyDetails(player: Player): Boolean {
        val party = partyFor(player) ?: createParty(player)
        return openPartyDetails(player, party)
    }

    fun openPartyDetails(player: Player, slot: Int): Boolean {
        val party = parties.values.firstOrNull { it.boardSlot == slot }
        if (party == null) {
            player.mes("That Theatre party is no longer available.")
            openPartyList(player)
            return false
        }
        return openPartyDetails(player, party)
    }

    fun refreshViewedParty(player: Player): Boolean {
        val party = viewedParties[player.uid]?.let(::partyById)
        if (party == null) {
            openPartyList(player)
            return false
        }
        return openPartyDetails(player, party)
    }

    fun clearViewedPartyBlocks(player: Player): Boolean {
        val party = viewedParty(player) ?: return false
        if (party.leader != player.uid) return false
        party.blocked.clear()
        player.mes("All rejected applicants may apply to your party again.")
        return openPartyDetails(player, party)
    }

    fun setViewedPartySize(player: Player, size: Int): Boolean {
        val party = viewedParty(player) ?: return false
        if (party.leader != player.uid || size !in 1..TOB_MAX_PARTY_SIZE) {
            player.mes("Enter a preferred party size from 1 to $TOB_MAX_PARTY_SIZE.")
            return openPartyDetails(player, party)
        }
        party.preferredSize = size
        refreshPartyViewers(party)
        return true
    }

    fun setViewedPartyCombat(player: Player, level: Int): Boolean {
        val party = viewedParty(player) ?: return false
        if (party.leader != player.uid || level !in 3..126) {
            player.mes("Enter a preferred combat level from 3 to 126.")
            return openPartyDetails(player, party)
        }
        party.preferredCombat = level
        refreshPartyViewers(party)
        return true
    }

    fun partyDetailsAction(player: Player, slot: Int): Boolean {
        val party = viewedParty(player) ?: return false
        return when (slot) {
            0 -> {
                openPartyList(player)
                true
            }
            1 -> openPartyDetails(player, party)
            2 -> clearViewedPartyBlocks(player)
            5 -> bottomPartyAction(player, party)
            7 -> disbandViewedParty(player, party)
            in 8..11 -> kickPartyIndex(player, party, slot - 7)
            in 12..16 -> acceptApplicant(player, party, slot - 12)
            in 22..26 -> rejectApplicant(player, party, slot - 22)
            else -> false
        }
    }

    private fun bottomPartyAction(player: Player, party: TobParty): Boolean =
        when (partyStatus(player, party)) {
            TobPartyBoard.STATUS_LEADER -> disbandViewedParty(player, party)
            TobPartyBoard.STATUS_IN_PARTY -> leaveParty(player).also { openPartyList(player) }
            TobPartyBoard.STATUS_WITHDRAW_APPLICATION -> {
                party.applicants.remove(player.uid)
                refreshPartyViewers(party)
                true
            }
            else -> applyToParty(player, party)
        }

    private fun applyToParty(player: Player, party: TobParty): Boolean {
        if (partyFor(player) != null) {
            player.mes("You cannot apply while you are already in another Theatre party.")
            return false
        }
        if (player.uid in party.blocked) {
            player.mes("You cannot apply to that party at the moment.")
            return false
        }
        if (party.activeSession != null || party.members.size >= TOB_MAX_PARTY_SIZE) {
            player.mes("That party is no longer accepting applications.")
            return false
        }
        party.applicants += player.uid
        player.mes("You apply to ${label(party.leader)}'s Theatre party.")
        refreshPartyViewers(party)
        return true
    }

    private fun acceptApplicant(player: Player, party: TobParty, index: Int): Boolean {
        if (party.leader != player.uid || party.activeSession != null) return false
        val applicant = party.applicants.elementAtOrNull(index) ?: return false
        if (party.members.size >= TOB_MAX_PARTY_SIZE || membership.containsKey(applicant)) {
            party.applicants.remove(applicant)
            refreshPartyViewers(party)
            return false
        }
        party.applicants.remove(applicant)
        party.members += applicant
        membership[applicant] = party
        applicant
            .resolve(players)
            ?.mes("You have been accepted into ${label(party.leader)}'s Theatre party.")
        message(party, "${label(applicant)} joins the Theatre party.")
        refreshPartyViewers(party)
        return true
    }

    private fun rejectApplicant(player: Player, party: TobParty, index: Int): Boolean {
        if (party.leader != player.uid) return false
        val applicant = party.applicants.elementAtOrNull(index) ?: return false
        party.applicants.remove(applicant)
        party.blocked += applicant
        applicant.resolve(players)?.mes("Your Theatre party application was declined.")
        refreshPartyViewers(party)
        return true
    }

    private fun kickPartyIndex(player: Player, party: TobParty, index: Int): Boolean {
        if (party.leader != player.uid || index !in 1 until party.members.size) return false
        val target = party.members.elementAt(index)
        party.members.remove(target)
        membership.remove(target)
        target.resolve(players)?.mes("You were removed from the Theatre party.")
        refreshPartyViewers(party)
        return true
    }

    private fun disbandViewedParty(player: Player, party: TobParty): Boolean {
        if (party.leader != player.uid || party.activeSession != null) return false
        parties.remove(party.leader)
        party.members.forEach {
            membership.remove(it)
            it.resolve(players)?.mes("The Theatre party has been disbanded.")
        }
        party.applicants.forEach { applicant ->
            applicant.resolve(players)?.mes("The Theatre party you applied to was disbanded.")
        }
        party.members.clear()
        party.applicants.clear()
        viewedParties.entries.removeIf { it.value == party.id }
        openPartyList(player)
        return true
    }

    private fun openPartyDetails(player: Player, party: TobParty): Boolean {
        viewedParties[player.uid] = party.id
        val status = partyStatus(player, party)
        partyBoard.openDetails(
            player,
            TobPartyDetails(
                status,
                party.members.map { it.resolve(players) },
                party.applicants.mapNotNull { it.resolve(players) },
                party.preferredSize,
                party.preferredCombat,
            ),
        )
        return true
    }

    private fun partyStatus(player: Player, party: TobParty): Int =
        when {
            party.leader == player.uid -> TobPartyBoard.STATUS_LEADER
            player.uid in party.members -> TobPartyBoard.STATUS_IN_PARTY
            player.uid in party.applicants -> TobPartyBoard.STATUS_WITHDRAW_APPLICATION
            else -> TobPartyBoard.STATUS_APPLY
        }

    private fun viewedParty(player: Player): TobParty? = viewedParties[player.uid]?.let(::partyById)

    private fun partyById(id: java.util.UUID): TobParty? =
        parties.values.firstOrNull { it.id == id }

    private fun refreshPartyViewers(party: TobParty) {
        (party.members + party.applicants).forEach { uid ->
            val viewer = uid.resolve(players) ?: return@forEach
            if (viewedParties[uid] == party.id && partyBoard.isDetailsOpen(viewer)) {
                openPartyDetails(viewer, party)
            }
        }
    }

    fun start(player: Player, mode: TobMode, bypassRequirements: Boolean = false): Boolean {
        val party = partyFor(player) ?: createParty(player)
        if (party.leader != player.uid) {
            player.mes("Only the party leader can start the Theatre.")
            return false
        }
        if (party.activeSession != null || raidFor(player) != null) return false
        val online = party.members.mapNotNull { it.resolve(players) }
        if (online.isEmpty() || online.size > TOB_MAX_PARTY_SIZE) return false
        if (online.size != party.members.size) {
            player.mes("Every accepted party member must be online before the Theatre can start.")
            return false
        }
        if (online.any { !isTobLobby(it.coords) }) {
            player.mes("Every accepted party member must be inside the Theatre lobby.")
            return false
        }
        if (online.any { instances.sessionForPlayer(it) != null }) {
            player.mes("Every party member must leave their current instance first.")
            return false
        }
        if (
            mode == TobMode.HARD &&
                !bypassRequirements &&
                online.any { it.attr.getOrDefault(TOB_NORMAL_COMPLETIONS, 0) < 1 }
        ) {
            player.mes("Every party member needs one Normal Mode completion before Hard Mode.")
            return false
        }
        val settings =
            InstanceSettings(
                    maxPlayers = TOB_MAX_PARTY_SIZE,
                    reclaimTicks = 2_000,
                    graceTicks = 10,
                    destroyWhenEmpty = false,
                    description =
                        "Theatre of Blood ${mode.name.lowercase(Locale.ROOT)} party=${party.id}",
                    timeLimitTicks = null,
                )
                .withArea(TobInstanceLayout.area(), settingsRowId = 0)
        val created =
            try {
                instances.create(
                    player,
                    TOB_INSTANCE_KEY,
                    settings,
                    InstanceAccess.Private,
                    clock.cycle,
                )
            } catch (error: Exception) {
                log.error(error) { "[ToB] failed to create instance party=${party.id} mode=$mode" }
                player.mes(
                    "The Theatre could not be created safely. You remain outside; please try again."
                )
                return false
            }
        if (created !is InstanceManager.Result.Created) {
            player.mes(
                (created as? InstanceManager.Result.Failed)?.reason
                    ?: "The Theatre could not be created."
            )
            return false
        }
        val raid =
            TobRaidSession(party, mode, clock.cycle).also {
                it.instance = created.session
                it.state = TobRaidState.ACTIVE
            }
        online.forEach {
            raid.participants[it.uid] = TobParticipant(it.uid, entered = true, connected = true)
        }
        raids[created.session.id] = raid
        party.activeSession = raid
        invitations.entries.removeIf { it.value === party }
        for ((index, member) in online.withIndex()) {
            val entry =
                if (index == 0) {
                    created.enter
                } else {
                    when (
                        val joined =
                            instances.join(member, created.session, clock.cycle, forceAccess = true)
                    ) {
                        is InstanceManager.Result.Joined -> joined.enter
                        is InstanceManager.Result.Failed -> {
                            log.error {
                                "[ToB] failed party transfer member=${member.displayName} " +
                                    "party=${party.id}: ${joined.reason}"
                            }
                            abort(raid, "a party member could not enter safely")
                            return false
                        }
                        else -> {
                            abort(raid, "a party member could not enter safely")
                            return false
                        }
                    }
                }
            prepare(member)
            PathingEntityCommon.telejump(member, collision, entry)
            instances.finalizeEntry(member, created.session, clock.cycle)
            participantRaids[member.uid] = raid
            resetCombat(member)
            ui.open(member, raid, clock.cycle)
            syncLobbyInviteOption(member)
            member.mes("You enter the Theatre of Blood in ${mode.name.lowercase()} mode.")
        }
        raid.currentRoomIndex = TobRoom.MAIDEN.ordinal
        raid.currentRoom.state = TobRoomState.READY
        raid.encounter = null
        message(raid.party, "Pass through the Maiden barrier when your party is ready.")
        log.info {
            "[ToB] start party=${party.id} instance=${raid.instance.id} players=${online.size} mode=$mode"
        }
        return true
    }

    fun rejoin(player: Player, raid: TobRaidSession): Boolean {
        if (raid.state in setOf(TobRaidState.ABANDONED, TobRaidState.COMPLETE)) return false
        val participant = raid.participant(player.uid) ?: return false
        val result = instances.join(player, raid.instance, clock.cycle, forceAccess = true)
        if (result !is InstanceManager.Result.Joined) return false
        val room = raid.rooms[participant.roomIndex].room
        prepare(player)
        val destination =
            if (participant.spectating) spectatorCoord(raid, room) else roomCoord(raid, room)
        PathingEntityCommon.telejump(player, collision, destination)
        instances.finalizeEntry(player, raid.instance, clock.cycle)
        participant.entered = true
        participant.connected = true
        participantRaids[player.uid] = raid
        ui.open(player, raid, clock.cycle)
        when (room) {
            TobRoom.VERZIK -> syncVerzikThroneVisibility(player, raid)
            TobRoom.TREASURE -> syncTreasureChestStates(player, raid)
            else -> Unit
        }
        player.mes("You rejoin your Theatre party in ${room.displayName}.")
        return true
    }

    fun passChallengeBarrier(player: Player, barrier: CoordGrid): Boolean {
        val raid = raidFor(player) ?: return false
        val participant = raid.participant(player.uid) ?: return false
        val room = raid.rooms.getOrNull(participant.roomIndex) ?: return false
        if (!room.room.hasChallengeBarrier) {
            player.mes("There is no challenge beyond this barrier.")
            return false
        }
        val destination = maidenBarrierDestination(player.coords, barrier)
        val arena = roomCoord(raid, room.room, true)
        if (room.state == TobRoomState.COMPLETE) {
            if (completedBarrierAdvancesRoom(room.room)) {
                val next = raid.rooms.getOrNull(room.index + 1) ?: return false
                return advanceFromCompletedRoom(player, raid, participant, room, next)
            }
            if (room.room != TobRoom.XARPUS) {
                player.mes("Use the forward passage to continue.")
                return false
            }
            val xarpusExitCenter = roomCoord(raid, TobRoom.XARPUS, 34, 43)
            if (
                room.room == TobRoom.XARPUS &&
                    isXarpusStagingBarrierCrossing(
                        player = player.coords,
                        destination = destination,
                        barrier = barrier,
                        exitBarrierCenter = xarpusExitCenter,
                        arena = arena,
                    )
            ) {
                prepare(player)
                PathingEntityCommon.telejump(player, collision, destination)
                player.mes("Search the skeleton for the Dawnbringer, then use the door to Verzik.")
                return true
            }
            player.mes("Use Xarpus's north barrier to reach the skeleton and forward door.")
            return false
        }
        val entering = destination.chebyshevDistance(arena) < player.coords.chebyshevDistance(arena)
        if (!entering) {
            player.mes("The Theatre barriers only allow the party to move forward.")
            return false
        }
        if (room.state == TobRoomState.READY && room.room == TobRoom.VERZIK) {
            if (!partyHasDawnbringer(raid)) {
                player.mes("Search the skeleton in the staging room for the Dawnbringer first.")
                return false
            }
            prepareVerzikRoom(raid)
        }
        val startsEncounter =
            shouldStartChallengeAtBarrier(room.index, raid.currentRoomIndex, room.state) &&
                room.room != TobRoom.VERZIK
        prepare(player)
        PathingEntityCommon.telejump(player, collision, destination)
        if (startsEncounter) {
            activateRoom(raid, room.index)
        }
        return true
    }

    fun enterPassage(player: Player, locId: Int): Boolean {
        val raid = raidFor(player) ?: return false
        val participant = raid.participant(player.uid) ?: return false
        val source = raid.rooms.getOrNull(participant.roomIndex) ?: return false
        val passage = TobPassage.forClick(locId, source.room)
        if (passage == null) {
            player.mes("That passage does not lead onward from ${source.room.displayName}.")
            return false
        }
        if (source.state != TobRoomState.COMPLETE) {
            player.mes("Defeat ${source.room.displayName} before continuing.")
            return false
        }
        if (passage == TobPassage.XARPUS_TO_VERZIK && !partyHasDawnbringer(raid)) {
            player.mes("Search the skeleton for the Dawnbringer before entering Verzik's chamber.")
            return false
        }
        val destination = raid.rooms[passage.destination.ordinal]
        return advanceFromCompletedRoom(player, raid, participant, source, destination)
    }

    private fun advanceFromCompletedRoom(
        player: Player,
        raid: TobRaidSession,
        participant: TobParticipant,
        source: TobRoomProgress,
        destination: TobRoomProgress,
    ): Boolean {
        if (!canAdvanceTobRoom(source, destination)) {
            player.mes("That room cannot be entered again during this Theatre run.")
            return false
        }
        participant.roomIndex = destination.index
        participant.spectating = false
        raid.currentRoomIndex = destination.index
        if (destination.state == TobRoomState.LOCKED) {
            destination.state = TobRoomState.READY
            raid.encounter = null
        }
        if (destination.room == TobRoom.VERZIK) prepareVerzikRoom(raid)
        prepare(player)
        PathingEntityCommon.telejump(player, collision, roomCoord(raid, destination.room))
        ui.open(player, raid, clock.cycle)
        if (destination.room == TobRoom.TREASURE) {
            if (raid.state != TobRaidState.REWARDS) {
                activateRoom(raid, destination.index)
            }
            syncTreasureChestStates(player, raid)
            player.mes("You enter the treasure vault. Search your reward chest when ready.")
        } else if (destination.room == TobRoom.VERZIK) {
            player.mes("You enter Verzik Vitur's chamber. Talk to her when your party is ready.")
        } else {
            player.mes("You enter ${destination.room.displayName}. Pass the barrier when ready.")
        }
        return true
    }

    fun claimDawnbringer(player: Player): Boolean {
        val raid = raidFor(player) ?: return false
        val participant = raid.participant(player.uid) ?: return false
        if (
            participant.roomIndex != TobRoom.XARPUS.ordinal ||
                raid.rooms[TobRoom.XARPUS.ordinal].state != TobRoomState.COMPLETE
        ) {
            player.mes("Defeat Xarpus before searching the skeleton.")
            return false
        }
        if (partyHasDawnbringer(raid)) {
            player.mes("The Dawnbringer has already been claimed by your party.")
            return false
        }
        raid.dawnbringerClaimed = false
        if (!player.invAdd(player.inv, TobItems.DAWNBRINGER, 1).success) {
            player.mes("You need a free inventory space.")
            return false
        }
        raid.dawnbringerClaimed = true
        message(raid.party, "${player.displayName} claims the Dawnbringer.")
        return true
    }

    fun canStartVerzik(player: Player, npc: Npc): Boolean {
        val raid = raidFor(player) ?: return false
        val participant = raid.participant(player.uid) ?: return false
        val progress = raid.rooms[TobRoom.VERZIK.ordinal]
        val encounter = raid.encounter as? VerzikEncounter
        val valid =
            participant.roomIndex == TobRoom.VERZIK.ordinal &&
                !participant.spectating &&
                raid.rooms[TobRoom.XARPUS.ordinal].state == TobRoomState.COMPLETE &&
                raid.currentRoomIndex == TobRoom.VERZIK.ordinal &&
                progress.state == TobRoomState.READY &&
                encounter != null &&
                raid.participants.values
                    .filter { it.entered && it.connected }
                    .all { it.roomIndex == TobRoom.VERZIK.ordinal && !it.spectating } &&
                !encounter.started &&
                raid.npcRoles[npc] == TobNpcRole.VERZIK_HOST
        if (!valid) player.mes("Verzik cannot be challenged from here right now.")
        return valid
    }

    fun startVerzik(player: Player, npc: Npc): Boolean {
        if (!canStartVerzik(player, npc)) return false
        val raid = raidFor(player) ?: return false
        val encounter = raid.encounter as? VerzikEncounter ?: return false
        if (encounter.started) return false
        encounter.started = true
        encounter.state.activate()
        val progress = raid.rooms[TobRoom.VERZIK.ordinal]
        progress.state = TobRoomState.ACTIVE
        progress.startedAt = clock.cycle
        raid.participants.values.forEach { it.spectating = false }
        removeRaidNpc(raid, npc)
        val phaseOne =
            spawnBoss(raid, TobNpcs.verzikPhases(raid.mode).first(), TobNpcRole.VERZIK_PHASE)
        phaseOne.movementLocked = true
        phaseOne.lockFacingDirection(Direction.South)
        raid.nextMechanicAt = clock.cycle + VerzikNormalRules.PHASE_ONE_INITIAL_DELAY
        message(raid.party, "Verzik Vitur rises from her throne. The final battle begins!")
        return true
    }

    fun claim(player: Player, chest: CoordGrid): TobRewardRoll? {
        val raid = raidFor(player) ?: return null
        val participant = raid.participant(player.uid) ?: return null
        if (
            raid.state != TobRaidState.REWARDS || participant.roomIndex != TobRoom.TREASURE.ordinal
        ) {
            player.mes("Verzik must be defeated before this chest can be searched.")
            return null
        }
        val ownedChest = rewardChestTiles[raid.instance.id]?.get(player.uid)
        if (ownedChest == null || chest != ownedChest) {
            player.mes("This isn't your reward chest.")
            return null
        }
        val claimed = rewards.claim(player, raid) ?: return null
        updateRewardCompletion(raid)
        return claimed
    }

    private fun updateRewardCompletion(raid: TobRaidSession) {
        if (
            raid.participants.values
                .filter(TobParticipant::entered)
                .all(TobParticipant::rewardClaimed)
        ) {
            raid.state = TobRaidState.COMPLETE
            raid.rooms[TobRoom.TREASURE.ordinal].state = TobRoomState.COMPLETE
        }
    }

    fun exit(player: Player): Boolean {
        val raid = raidFor(player) ?: return false
        return leaveRaid(player, raid, removeFromParty = true)
    }

    fun leaveFromLogoutTab(player: Player, expectedInstanceId: InstanceId): RaidLeaveResult {
        val raid = raidFor(player) ?: return RaidLeaveResult.Unhandled
        if (raid.instance.id != expectedInstanceId) return RaidLeaveResult.Unhandled
        if (raid.state == TobRaidState.REWARDS) {
            when (rewards.claimForLeave(player, raid)) {
                RaidRewardClaimResult.Claimed -> updateRewardCompletion(raid)
                RaidRewardClaimResult.AlreadyClaimed -> Unit
                RaidRewardClaimResult.InventoryFull -> return RaidLeaveResult.InventoryFull
                RaidRewardClaimResult.Busy -> return RaidLeaveResult.Busy
                RaidRewardClaimResult.NothingToClaim -> {
                    player.mes(
                        "Your Theatre reward is not ready. You have not been moved."
                    )
                    return RaidLeaveResult.RewardUnavailable
                }
            }
        }
        return if (leaveRaid(player, raid, removeFromParty = true)) {
            RaidLeaveResult.Left
        } else {
            RaidLeaveResult.Failed
        }
    }

    fun validateAttack(player: Player, npc: Npc): NpcAttackValidateResult {
        val raid = raidFor(npc) ?: return NpcAttackValidateResult.Pass
        if (raidFor(player) !== raid)
            return NpcAttackValidateResult.Deny("That creature belongs to another Theatre party.")
        if (raid.participant(player.uid)?.spectating == true)
            return NpcAttackValidateResult.Deny("Spectators cannot attack until the next room.")
        if (raid.currentRoom.state != TobRoomState.ACTIVE)
            return NpcAttackValidateResult.Deny("This Theatre encounter has not started.")
        val web = (raid.encounter as? VerzikEncounter)?.webs?.get(npc)
        if (web != null && web.trapped.remove(player.uid)) {
            CombatEffects.unfreeze(player)
            player.mes("You attack the sticky web and tear yourself free!")
        }
        if (
            raid.currentRoom.room == TobRoom.SOTETSEG &&
                (raid.encounter as? SotetsegEncounter)?.mazeActive == true
        ) {
            return NpcAttackValidateResult.Deny("Sotetseg cannot be attacked during the maze.")
        }
        if (
            raid.npcRoles[npc] in
                setOf(
                    TobNpcRole.HAZARD,
                    TobNpcRole.PILLAR,
                    TobNpcRole.VERZIK_HOST,
                    TobNpcRole.VERZIK_TRANSITION,
                )
        )
            return NpcAttackValidateResult.Deny("That cannot be attacked.")
        return NpcAttackValidateResult.BypassSingleWayPvnRestriction
    }

    fun handleNpcKill(hero: Player, npc: Npc) {
        val raid = raidFor(npc) ?: return
        handleResolvedNpcKill(raid, hero, npc)
    }

    fun handleOwnedNpcDeath(npc: Npc, hero: Player?) {
        val raid = raidFor(npc) ?: return
        val creditedHero =
            hero ?: raid.participants.keys.firstNotNullOfOrNull { it.resolve(players) }
        handleResolvedNpcKill(raid, creditedHero, npc)
    }

    private fun handleResolvedNpcKill(raid: TobRaidSession, hero: Player?, npc: Npc) {
        val role = raid.npcRoles.remove(npc) ?: return
        raid.spawnedNpcs.remove(npc)
        hazardExpiry.remove(npc)
        when (role) {
            TobNpcRole.VERZIK_HOST,
            TobNpcRole.VERZIK_TRANSITION -> Unit
            TobNpcRole.VERZIK_PHASE -> {
                if (hero == null) {
                    log.error { "[ToB] could not resolve Verzik's killer raid=${raid.instance.id}" }
                    return
                }
                handleVerzikDeath(raid, hero)
            }
            TobNpcRole.VERZIK_ADD -> handleVerzikAddDeath(raid, npc)
            TobNpcRole.BOSS -> completeRoom(raid, hero)
            TobNpcRole.NYLOCAS_WAVE -> splitNylocas(raid, npc)
            TobNpcRole.MAIDEN_ADD,
            TobNpcRole.HAZARD,
            TobNpcRole.PILLAR -> Unit
        }
    }

    private fun reconcileSuppressedRaidNpcDeaths(raid: TobRaidSession) {
        val resolved =
            raid.npcRoles.keys
                .filter {
                    shouldReconcileSuppressedRaidNpcDeath(
                        suppressRewards = it.suppressRewards,
                        isSlotAssigned = it.isSlotAssigned,
                        hitpoints = it.hitpoints,
                    )
                }
                .toList()
        resolved.forEach { npc ->
            // suppressRewards deliberately prevents the global drop and kill-hook pipeline. ToB
            // owns its room lifecycle, so reconcile the removed NPC here without enabling bones,
            // global drop tables, kill-count rewards, or respawns.
            val hero =
                npc.findHero(players)
                    ?: raid.participants.keys.firstNotNullOfOrNull { it.resolve(players) }
            handleResolvedNpcKill(raid, hero, npc)
        }
    }

    fun markDeath(player: Player) {
        val raid = raidFor(player) ?: return
        val participant = raid.participant(player.uid) ?: return
        player.combatClearQueue()
        player.clearQueue("queue.hit")
        player.resetSpotanim()
        TobScoreRules.recordDeath(participant)
        val activePartySize =
            raid.participants.values.count { it.entered && it.connected && !it.spectating }
        if (activePartySize == 0) {
            val enteredPartySize = raid.participants.values.count { it.entered && it.connected }
            if (enteredPartySize <= 1) {
                pendingSoloDeathExits += player.uid
                pendingAborts[raid.instance.id] = clock.cycle + 1
                message(
                    raid.party,
                    "${player.displayName} has fallen. The solo Theatre run is over.",
                )
                return
            }
        }
        message(raid.party, "${player.displayName} has fallen and is now spectating this room.")
        if (
            raid.participants.values.filter(TobParticipant::entered).all {
                it.spectating || !it.connected
            }
        ) {
            if (raid.mode == TobMode.ENTRY) pendingEntryResets[raid.instance.id] = clock.cycle + 1
            else pendingAborts[raid.instance.id] = clock.cycle + 1
        }
    }

    fun respawnAfterDeath(player: Player) {
        player.combatClearQueue()
        player.clearQueue("queue.hit")
        player.resetSpotanim()
        if (pendingSoloDeathExits.remove(player.uid)) {
            PathingEntityCommon.telejump(player, collision, TOB_LOBBY)
            cleanupRaidItems(player)
            ui.close(player)
            player.mes("Your solo Theatre run has ended.")
            return
        }
        val raid = partyFor(player)?.activeSession ?: return
        val participant = raid.participant(player.uid) ?: return
        participantRaids[player.uid] = raid
        participant.connected = true
        val progress = raid.rooms[participant.roomIndex]
        val destination =
            when {
                participant.spectating -> spectatorCoord(raid, progress.room)
                progress.state == TobRoomState.COMPLETE -> completionCoord(raid, progress.room)
                else -> roomCoord(raid, progress.room)
            }
        PathingEntityCommon.telejump(player, collision, destination)
        ui.open(player, raid, clock.cycle)
        player.mes(
            if (participant.spectating)
                "You spectate behind the room barrier until your party clears the room."
            else "The room is complete. Continue with your party."
        )
    }

    fun logout(player: Player) {
        invitations.remove(player.uid)
        previousLobbyPlayerOps.remove(player.uid)
        val raid = raidFor(player)
        if (raid == null) {
            participantRaids.remove(player.uid)
            return
        }
        if (playerHasDawnbringer(player)) {
            removeDawnbringer(player)
            raid.dawnbringerClaimed = false
            message(raid.party, "The Dawnbringer returns to the skeleton for safekeeping.")
        }
        instances.leaveForLogout(player, clock.cycle)
        leaveRaid(player, raid, removeFromParty = true, teleport = false)
    }

    fun externalTeleport(player: Player, instanceId: InstanceId) {
        val raid = raidFor(player) ?: return
        if (raid.instance.id != instanceId) return
        if (playerHasDawnbringer(player)) {
            raid.dawnbringerClaimed = false
            message(raid.party, "The Dawnbringer returns to the skeleton for safekeeping.")
        }
        participantRaids.remove(player.uid)
        raid.participants.remove(player.uid)
        prepare(player)
        cleanupRaidItems(player)
        ui.close(player)
        raid.party.members.remove(player.uid)
        membership.remove(player.uid)
        player.mes("Your Theatre ended because you teleported out of its instance.")
        when {
            raid.party.members.isEmpty() -> abort(raid, "party left the instance")
            raid.party.leader == player.uid -> transferOrDisband(raid.party)
        }
    }

    fun cleanupOrphanedRaidItems(player: Player) {
        if (raidFor(player) == null) cleanupRaidItems(player)
    }

    fun tick() {
        syncLobbyInviteOptions()
        hazardExpiry
            .filterValues { clock.cycle >= it }
            .keys
            .toList()
            .forEach { npc ->
                hazardExpiry.remove(npc)
                raidFor(npc)?.let {
                    it.spawnedNpcs.remove(npc)
                    it.npcRoles.remove(npc)
                }
                if (npc.isSlotAssigned) npcs.del(npc, Int.MAX_VALUE)
            }
        pendingEntryResets
            .filterValues { clock.cycle >= it }
            .keys
            .toList()
            .forEach { id ->
                pendingEntryResets.remove(id)
                raids[id]?.let(::resetEntryRoom)
            }
        pendingAborts
            .filterValues { clock.cycle >= it }
            .keys
            .toList()
            .forEach { id ->
                pendingAborts.remove(id)
                raids[id]?.let { abort(it, "the whole party was defeated") }
            }
        pendingVerzikTreasurePassages
            .filterValues { clock.cycle >= it.opensAt }
            .keys
            .toList()
            .forEach { id ->
                val pending = pendingVerzikTreasurePassages.remove(id) ?: return@forEach
                raids[id]?.let { raid -> openVerzikTreasurePassage(raid, pending) }
            }
        raids.values.toList().forEach { raid ->
            if (raid.state !in setOf(TobRaidState.ACTIVE, TobRaidState.REWARDS)) return@forEach
            reconcileSuppressedRaidNpcDeaths(raid)
            if (clock.cycle % 5 == 0) forEachOnline(raid) { ui.sync(it, raid, clock.cycle) }
            if (raid.state == TobRaidState.ACTIVE && raid.currentRoom.state == TobRoomState.ACTIVE)
                tickEncounter(raid)
        }
    }

    fun raidFor(player: Player): TobRaidSession? =
        participantRaids[player.uid]
            ?: partyFor(player)?.activeSession?.takeIf { player.uid in it.participants }

    fun raidFor(npc: Npc): TobRaidSession? =
        instances.instanceForNpc(npc)?.let(raids::get)?.takeIf { it.owns(npc) }
            // NpcDeath removes the NPC from its repository before it invokes kill hooks. That
            // clears the UID used by InstanceManager, while the raid still owns the same object.
            // Retain an identity-based fallback so the kill can complete its Theatre room.
            ?: raids.values.firstOrNull { it.owns(npc) }

    fun partyFor(player: Player): TobParty? = membership[player.uid]

    fun attackTargetValid(player: Player, npc: Npc): Boolean =
        raidFor(player)?.owns(npc) == true &&
            raidFor(player)?.participant(player.uid)?.spectating == false

    fun combatProfile(npc: Npc): TobCombatProfile? =
        raidFor(npc)?.let { baseCombatProfile(npc, it) }

    fun canUseDawnbringer(player: Player, target: Npc): Boolean {
        val raid = raidFor(player) ?: return false
        return raidFor(target) === raid &&
            raid.currentRoom.room == TobRoom.VERZIK &&
            (raid.encounter as? VerzikEncounter)?.state?.phase == VerzikPhase.ONE &&
            (raid.encounter as? VerzikEncounter)?.started == true &&
            boss(raid) === target &&
            target.id == TobNpcs.verzikPhases(raid.mode).first()
    }

    fun modifyNpcHit(player: Player, npc: Npc, hit: HitBuilder) {
        val raid = raidFor(npc)
        if (raid == null || raidFor(player) !== raid) {
            hit.damage = 0
            return
        }
        if (
            raid.currentRoom.room == TobRoom.BLOAT &&
                (raid.encounter as? BloatState)?.phase == BloatPhase.WALKING
        ) {
            hit.damage /= 2
        }
        if (
            raid.currentRoom.room == TobRoom.SOTETSEG &&
                (raid.encounter as? SotetsegEncounter)?.mazeActive == true
        ) {
            hit.damage = 0
            return
        }
        if (
            raid.currentRoom.room == TobRoom.XARPUS &&
                npc.id == TobNpcs.boss(TobRoom.XARPUS, raid.mode)
        ) {
            hit.damage = hit.damage.coerceAtMost(50)
            val encounter = raid.encounter as? XarpusEncounter
            if (
                encounter?.state?.phase == XarpusPhase.SCREECH &&
                    hit.damage > 0 &&
                    xarpusFacesPlayer(encounter.state.stare, npc, player)
            ) {
                queueSpecialHit(player, npc, 50 + hit.damage, 0)
                player.mes("Xarpus retaliates against attacks from the watched quadrant!")
            }
        }
        if (
            raid.currentRoom.room == TobRoom.VERZIK &&
                npc.id == TobNpcs.verzikPhases(raid.mode).first() &&
                (raid.encounter as? VerzikEncounter)?.state?.phase == VerzikPhase.ONE &&
                player.worn[dev.openrune.util.Wearpos.RightHand.slot]?.id != TobItems.DAWNBRINGER
        ) {
            hit.damage = hit.damage.coerceAtMost(if (hit.type == HitType.Melee) 10 else 3)
        }
        if (raid.currentRoom.room == TobRoom.VERZIK) {
            val encounter = raid.encounter as? VerzikEncounter
            val athanatos = encounter?.athanatos?.get(npc)
            if (athanatos != null) {
                hit.damage = 0
                if (athanatos.poisonedAt < 0) {
                    val weaponKey =
                        player.worn[dev.openrune.util.Wearpos.RightHand.slot]?.let {
                            runCatching { RSCM.getReverseMapping(RSCMType.OBJ, it.id) }.getOrNull()
                        }
                    val helmKey =
                        player.worn[dev.openrune.util.Wearpos.Hat.slot]?.let {
                            runCatching { RSCM.getReverseMapping(RSCMType.OBJ, it.id) }.getOrNull()
                        }
                    if (isAthanatosPoisonSource(weaponKey, helmKey)) {
                        athanatos.poisonedAt = clock.cycle + 2
                        athanatos.poisonSource = player.uid
                        athanatos.poisonDamage = random.of(65..75)
                        npc.abortRoute()
                        npc.anim(TobAssets.VERZIK_ATHANATOS_DEATH, priority = 254)
                        player.mes("The Nylocas Athanatos absorbs the poison and turns on Verzik!")
                    } else {
                        player.mes("Only a poisoned or venomous attack can kill this Nylocas.")
                    }
                }
                return
            }
            if (encounter?.bombs?.containsKey(npc) == true) {
                val required = nylocasRequiredHitType(npc.id, raid.mode)
                if (required != null && hit.type != required) {
                    hit.damage = 0
                    player.mes(
                        "That Nylocas can only be harmed with ${required.name.lowercase()} attacks."
                    )
                }
                return
            }
            if (
                encounter != null &&
                    boss(raid) === npc &&
                    encounter.state.phase == VerzikPhase.TWO &&
                    clock.cycle < encounter.healFromDamageUntil &&
                    hit.damage > 0
            ) {
                val absorbed = hit.damage
                hit.damage = 0
                npc.heal(absorbed, showHitsplat = true)
                if (encounter.summonHealWarned.add(player.uid)) {
                    player.mes("Verzik absorbs the attack while summoning the Nylocas Matomenos.")
                }
                return
            }
        }
        if (raid.currentRoom.room != TobRoom.NYLOCAS) return
        val activeId = TobNpcs.nylocasActiveStyleId(npc.id, npc.visType.id, raid.mode)
        val required = nylocasRequiredHitType(activeId, raid.mode) ?: return
        if (hit.type == required) return
        val reflected = hit.damage
        hit.damage = 0
        if (reflected > 0 && activeId in TobNpcs.nylocasBossStyles(raid.mode)) {
            npc.heal(reflected, showHitsplat = true)
        }
        if (reflected > 0) queueSpecialHit(player, npc, reflected, 0)
        player.mes("That Nylocas can only be harmed with ${required.name.lowercase()} attacks.")
    }

    private fun nylocasRequiredHitType(id: Int, mode: TobMode): HitType? {
        val index = TobNpcs.nylocasStyleIndex(id, mode) ?: return null
        return when (index) {
            0 -> HitType.Melee
            1 -> HitType.Ranged
            else -> HitType.Magic
        }
    }

    fun status(player: Player): String {
        val party = partyFor(player) ?: return "ToB: no party."
        val raid =
            party.activeSession
                ?: return "ToB party=${party.id} leader=${label(party.leader)} members=${party.members.size} waiting."
        val participantRoom = raid.participant(player.uid)?.roomIndex ?: raid.currentRoomIndex
        val room = raid.rooms[participantRoom].room
        return "ToB party=${party.id} instance=${raid.instance.id} mode=${raid.mode} state=${raid.state} room=${participantRoom + 1}/7:${room.key} active=${raid.currentRoom.room.key} score=${raid.participants.values.sumOf(TobParticipant::score)}"
    }

    fun forceRoom(player: Player, room: TobRoom): Boolean {
        var raid = raidFor(player)
        if (raid == null) {
            if (!start(player, TobMode.NORMAL, true)) return false
            raid = raidFor(player) ?: return false
        }
        clearRaidNpcs(raid)
        raid.participant(player.uid)?.roomIndex = room.ordinal
        activateRoom(raid, room.ordinal)
        PathingEntityCommon.telejump(player, collision, roomCoord(raid, room))
        return true
    }

    fun forceWave(player: Player, wave: Int): Boolean {
        val raid = raidFor(player) ?: return false
        val state = raid.encounter as? NylocasWaveState ?: return false
        while (state.wave < wave.coerceIn(1, state.totalWaves)) state.nextWave()
        return true
    }

    fun forceVerzikPhase(player: Player, phase: Int): Boolean {
        var raid = raidFor(player)
        if (raid == null) {
            if (!start(player, TobMode.NORMAL, true)) return false
            raid = raidFor(player) ?: return false
        }
        if (raid.currentRoom.room != TobRoom.VERZIK) forceRoom(player, TobRoom.VERZIK)
        clearRaidNpcs(raid)
        val state = VerzikState(raid.mode)
        state.forcePhase(
            when ((phase - 1).coerceIn(0, 2)) {
                0 -> VerzikPhase.ONE
                1 -> VerzikPhase.TWO
                else -> VerzikPhase.THREE
            }
        )
        val encounter = VerzikEncounter(state)
        encounter.started = true
        raid.encounter = encounter
        if (state.phase == VerzikPhase.ONE) spawnVerzikPillars(raid, encounter)
        val tile =
            if (state.phase == VerzikPhase.ONE) roomCoord(raid, TobRoom.VERZIK, true)
            else verzikRoomCenter(raid)
        val boss =
            spawnBossAt(
                raid,
                TobNpcs.verzikPhases(raid.mode)[(phase - 1).coerceIn(0, 2)],
                TobNpcRole.VERZIK_PHASE,
                tile,
            )
        boss.movementLocked = state.phase != VerzikPhase.THREE
        raid.nextMechanicAt = clock.cycle + if (state.phase == VerzikPhase.THREE) 7 else 4
        return true
    }

    fun forceNylocasBoss(player: Player): Boolean {
        if (
            raidFor(player)?.currentRoom?.room != TobRoom.NYLOCAS &&
                !forceRoom(player, TobRoom.NYLOCAS)
        )
            return false
        val raid = raidFor(player) ?: return false
        clearRaidNpcs(raid)
        val state = NylocasWaveState()
        repeat(state.totalWaves) { state.nextWave() }
        state.bossSpawned = true
        raid.encounter = state
        spawnBoss(raid, TobNpcs.boss(TobRoom.NYLOCAS, raid.mode), TobNpcRole.BOSS)
        return true
    }

    fun forceSotetsegPhase(player: Player, phase: Int): Boolean {
        if (
            raidFor(player)?.currentRoom?.room != TobRoom.SOTETSEG &&
                !forceRoom(player, TobRoom.SOTETSEG)
        )
            return false
        val raid = raidFor(player) ?: return false
        val state = raid.encounter as? SotetsegEncounter ?: return false
        repeat((phase - 1).coerceIn(0, 2)) {
            if (state.thresholds.isNotEmpty()) state.thresholds.removeFirst()
        }
        player.mes("Sotetseg development phase ${phase.coerceIn(1, 3)} is ready.")
        return true
    }

    fun forceXarpusPhase(player: Player, phase: Int): Boolean {
        if (
            raidFor(player)?.currentRoom?.room != TobRoom.XARPUS &&
                !forceRoom(player, TobRoom.XARPUS)
        )
            return false
        val raid = raidFor(player) ?: return false
        val encounter = raid.encounter as? XarpusEncounter ?: return false
        if (phase >= 2) encounter.state.forceCombat()
        if (phase >= 3) encounter.state.updateHealth(0)
        boss(raid)?.movementLocked = true
        player.mes("Xarpus development phase ${phase.coerceIn(1, 3)} is ready.")
        return true
    }

    fun forceWipe(player: Player): Boolean {
        val raid = raidFor(player) ?: return false
        if (raid.mode == TobMode.ENTRY) resetEntryRoom(raid)
        else abort(raid, "administrator wipe test")
        return true
    }

    fun setScore(player: Player, score: Int): Boolean {
        val raid = raidFor(player) ?: return false
        raid.participant(player.uid)?.score = score.coerceAtLeast(0)
        return true
    }

    fun forceCompleteRoom(player: Player): Boolean {
        val raid = raidFor(player) ?: return false
        completeRoom(raid, player)
        return true
    }

    fun forceRewards(player: Player, forceUnique: Boolean = false): Boolean {
        var raid = raidFor(player)
        if (raid == null) {
            if (!start(player, TobMode.NORMAL, bypassRequirements = true)) return false
            raid = raidFor(player) ?: return false
        }
        clearRaidNpcs(raid)
        val participant = raid.participant(player.uid) ?: return false
        participant.entered = true
        participant.connected = true
        participant.roomIndex = TobRoom.TREASURE.ordinal
        participant.spectating = false
        participant.rewardClaimed = false
        participant.pendingReward = null
        participant.score = tobChestTestScore(participant.score)
        activateRoom(
            raid,
            TobRoom.TREASURE.ordinal,
            forceUniqueFor = player.uid.takeIf { forceUnique },
        )
        prepare(player)
        PathingEntityCommon.telejump(player, collision, roomCoord(raid, TobRoom.TREASURE))
        ui.open(player, raid, clock.cycle)
        player.mes(
            if (forceUnique) "ToB purple chest test ready. Search your monumental chest."
            else "ToB chest test ready. Purple chance is 1/5. Search your monumental chest."
        )
        return true
    }

    fun reset(player: Player): Boolean {
        raidFor(player)?.let { abort(it, "administrator reset") }
        return true
    }

    private fun activateRoom(raid: TobRaidSession, index: Int, forceUniqueFor: PlayerUid? = null) {
        raid.currentRoomIndex = index
        val progress = raid.currentRoom
        progress.state = TobRoomState.ACTIVE
        progress.startedAt = clock.cycle
        raid.nextMechanicAt = clock.cycle + 5
        raid.participants.values.forEach { it.spectating = false }
        when (progress.room) {
            TobRoom.MAIDEN -> {
                raid.encounter = MaidenState()
                val maiden =
                    spawnBoss(raid, TobNpcs.boss(progress.room, raid.mode), TobNpcRole.BOSS)
                maiden.movementLocked = true
            }
            TobRoom.BLOAT -> {
                raid.encounter = BloatState()
                val bloat = spawnBoss(raid, TobNpcs.boss(progress.room, raid.mode), TobNpcRole.BOSS)
                bloat.ignoreCombatInteractions = true
                bloat.walkTo(routeFactory, roomCoord(raid, TobRoom.BLOAT, 25, 25))
            }
            TobRoom.NYLOCAS -> {
                val state = NylocasWaveState()
                raid.encounter = state
                spawnNylocasPillars(raid, state)
                message(raid.party, "Defeat all three Nylocas waves. The pillars cannot collapse.")
            }
            TobRoom.SOTETSEG -> {
                raid.encounter = SotetsegEncounter()
                spawnBoss(raid, TobNpcs.boss(progress.room, raid.mode), TobNpcRole.BOSS)
            }
            TobRoom.XARPUS -> {
                val partySize =
                    raid.participants.values
                        .count(TobParticipant::entered)
                        .coerceIn(1, TOB_MAX_PARTY_SIZE)
                raid.encounter =
                    XarpusEncounter(
                        XarpusState(partySize, raid.mode),
                        recoveryEndsAt = clock.cycle + XARPUS_RECOVERY_TICKS,
                    )
                val xarpus =
                    spawnBoss(raid, TobNpcs.boss(progress.room, raid.mode), TobNpcRole.BOSS)
                xarpus.movementLocked = true
                xarpus.hitpoints = (xarpus.baseHitpointsLvl * 75 / 100).coerceAtLeast(1)
            }
            TobRoom.VERZIK -> {
                progress.state = TobRoomState.READY
                progress.startedAt = null
                prepareVerzikRoom(raid)
                message(
                    raid.party,
                    "Enter the arena, then talk to Verzik when your party is ready.",
                )
                return
            }
            TobRoom.TREASURE -> {
                raid.encounter = null
                raid.state = TobRaidState.REWARDS
                rewards.prepare(raid, clock.cycle, forceUniqueFor)
                spawnTreasureChests(raid)
                // Keep this room active until every entered party member has claimed. Marking the
                // shared room complete here prevents the remaining party members from advancing
                // through Verzik's passage to their own reward chest.
                progress.state = TobRoomState.ACTIVE
                recordCompletion(raid)
            }
        }
        message(raid.party, "${progress.room.displayName} begins.")
    }

    private fun tickEncounter(raid: TobRaidSession) {
        when (val state = raid.encounter) {
            is MaidenState -> tickMaiden(raid, state)
            is BloatState -> tickBloat(raid, state)
            is NylocasWaveState -> tickNylocas(raid, state)
            is SotetsegEncounter -> tickSotetseg(raid, state)
            is XarpusEncounter -> tickXarpus(raid, state)
            is VerzikEncounter -> tickVerzik(raid, state)
        }
    }

    private fun tickMaiden(raid: TobRaidSession, state: MaidenState) {
        val boss =
            boss(raid)?.takeIf { canQueueTobSpecialHit(it.isSlotAssigned, it.hitpoints) } ?: return
        val arenaPlayers = activePlayersOnLevel(raid, boss.coords.level)
        state.tick()
        state.expirePools(clock.cycle)
        processMaidenMatomenos(raid, state, boss)
        arenaPlayers.forEach { player ->
            if (player.coords in state.bloodPools) {
                val damage = TobScaling.maxHit(random.of(8..state.bloodDamage()), raid.mode)
                queueSpecialHit(player, boss, damage, 1)
                boss.heal(damage, showHitsplat = true)
            }
        }
        val percent = boss.hitpoints * 100 / boss.baseHitpointsLvl.coerceAtLeast(1)
        state.threshold(percent)?.let { threshold ->
            val formIndex =
                when (threshold) {
                    70 -> 1
                    50 -> 2
                    else -> 3
                }
            boss.transmog(
                requireNotNull(
                    ServerCacheManager.getNpc(TobNpcs.maidenForms(raid.mode)[formIndex])
                ),
                Int.MAX_VALUE,
            )
            spawnMaidenMatomenos(raid, state, boss)
            message(raid.party, "The Maiden summons Nylocas Matomenos at $threshold% health!")
        }
        if (clock.cycle >= raid.nextMechanicAt && state.bloodAttackCooldown == 0) {
            val targets = arenaPlayers
            if (targets.isEmpty()) return
            boss.anim(TobAssets.MAIDEN_BLOOD_CAST)
            targets.forEach { target ->
                launchTileProjectile(boss, target.coords, TobAssets.MAIDEN_BLOOD_PROJECTILE)
                addMaidenBloodPool(state, target.coords)
            }
            val extraTarget = targets[random.of(targets.indices)]
            repeat(2) {
                val tile =
                    CoordGrid(
                        extraTarget.coords.x + random.of(-2..2),
                        extraTarget.coords.z + random.of(-2..2),
                        extraTarget.coords.level,
                    )
                launchTileProjectile(boss, tile, TobAssets.MAIDEN_BLOOD_PROJECTILE)
                addMaidenBloodPool(state, tile)
            }
            state.armBloodCooldown()
            raid.nextMechanicAt = clock.cycle + 20
            if (random.of(1..10) == 1) {
                val spawn =
                    spawnAround(
                        raid,
                        TobNpcs.bloodSpawn(raid.mode),
                        TobNpcRole.HAZARD,
                        random.of(0..7),
                        8,
                        5,
                    )
                spawn.movementLocked = false
                hazardExpiry[spawn] = clock.cycle + 50
            }
        }
    }

    private fun tickBloat(raid: TobRaidSession, state: BloatState) {
        val boss = boss(raid) ?: return
        val before = state.phase
        val phase = state.tick()
        boss.movementLocked = phase == BloatPhase.DOWN
        if (before != phase) {
            boss.abortRoute()
            if (phase == BloatPhase.DOWN) boss.anim(TobAssets.BLOAT_DOWN)
            else {
                boss.resetAnim()
                boss.walkTo(
                    routeFactory,
                    bloatCorners(raid)[state.cornerIndex],
                    passThroughEntities = true,
                )
            }
            message(
                raid.party,
                if (phase == BloatPhase.DOWN) "Bloat shuts down. Attack now!"
                else "Bloat lurches back to life!",
            )
        }
        if (phase == BloatPhase.WALKING) {
            boss.resetAnim()
            val corners = bloatCorners(raid)
            val target = corners[state.cornerIndex]
            if (boss.coords.chebyshevDistance(target) <= 2) {
                boss.walkTo(routeFactory, corners[state.nextCorner()], passThroughEntities = true)
            } else if (boss.routeDestination.isEmpty()) {
                boss.walkTo(routeFactory, target, passThroughEntities = true)
            }
            forEachActive(raid) { player ->
                if (
                    rayCast.hasLineOfSight(
                        boss.coords,
                        player.coords,
                        srcWidth = boss.size,
                        srcLength = boss.size,
                    )
                ) {
                    player.spotanim(TobAssets.BLOAT_FLIES)
                    queueSpecialHit(
                        player,
                        boss,
                        TobScaling.maxHit(random.of(10..20), raid.mode),
                        0,
                    )
                }
            }
            if (clock.cycle % 8 == 0) {
                randomActive(raid)?.let { target ->
                    state.fallingBodyAt = target.coords
                    state.fallingBodyHitsAt = clock.cycle + 2
                    world.spotanimMap(
                        SpotanimType(
                            if (random.of(0..1) == 0) TobAssets.BLOAT_FALLING_BODY_A
                            else TobAssets.BLOAT_FALLING_BODY_B
                        ),
                        target.coords,
                    )
                }
            }
        }
        if (state.fallingBodyHitsAt == clock.cycle) {
            val tile = state.fallingBodyAt
            if (tile != null)
                forEachActive(raid) { player ->
                    if (player.coords == tile)
                        queueSpecialHit(
                            player,
                            boss,
                            TobScaling.maxHit(random.of(30..60), raid.mode),
                            0,
                        )
                }
            state.fallingBodyAt = null
            state.fallingBodyHitsAt = -1
        }
        if (state.stompNow())
            forEachActive(raid) { player ->
                if (player.coords.chebyshevDistance(boss.coords) <= 2) {
                    player.mes("Bloat stomps the arena!")
                    queueSpecialHit(
                        player,
                        boss,
                        TobScaling.maxHit(random.of(40..60), raid.mode),
                        1,
                    )
                }
            }
    }

    private fun tickNylocas(raid: TobRaidSession, state: NylocasWaveState) {
        processNylocasPillarRoutes(raid, state)
        var alive = raid.npcRoles.count { it.value == TobNpcRole.NYLOCAS_WAVE }
        if (clock.cycle >= raid.nextMechanicAt && state.canSpawn(alive)) {
            val wave = state.nextWave(clock.cycle)
            val styles =
                if (wave == 1) TobNpcs.nylocasStyles(raid.mode)
                else TobNpcs.nylocasBigIncomingStyles(raid.mode)
            val count = minOf(3 + (wave - 1) % 4, 6)
            repeat(count) { index ->
                val fromEast = (wave + index) % 2 == 0
                val targetIndex = if (fromEast) 2 + (index % 2) else index % 2
                val spawn =
                    spawnNpc(
                        raid,
                        styles[(wave + index) % styles.size],
                        roomCoord(
                            raid,
                            TobRoom.NYLOCAS,
                            if (fromEast) 46 else 17,
                            24 + (index % 2),
                        ),
                        TobNpcRole.NYLOCAS_WAVE,
                    )
                state.pillarTargets[spawn] = targetIndex
                routeNylocasToPillar(state, spawn, targetIndex)
            }
            raid.nextMechanicAt = clock.cycle + 4
        }
        if (state.shouldSpawnBoss(clock.cycle)) {
            state.markBossSpawned()
            spawnBoss(raid, TobNpcs.nylocasBossStyles(raid.mode).first(), TobNpcRole.BOSS)
            message(raid.party, "Nylocas Vasilias descends into the arena!")
        } else if (state.bossSpawned) {
            val vasilias = boss(raid) ?: return
            state.bossFormTicks++
            if (state.bossFormTicks >= NYLOCAS_BOSS_FORM_TICKS) {
                val forms = TobNpcs.nylocasBossStyles(raid.mode)
                val next = state.nextBossForm(vasilias.visType.id, forms)
                vasilias.transmog(requireNotNull(ServerCacheManager.getNpc(next)), Int.MAX_VALUE)
            }
        }
    }

    private fun tickSotetseg(raid: TobRaidSession, state: SotetsegEncounter) {
        val boss = boss(raid) ?: return
        if (state.mazeActive) {
            tickSotetsegMaze(raid, state, boss)
            return
        }
        val percent = boss.hitpoints * 100 / boss.baseHitpointsLvl.coerceAtLeast(1)
        val threshold = state.thresholds.firstOrNull()
        if (threshold != null && percent <= threshold) {
            state.thresholds.removeFirst()
            startSotetsegMaze(raid, state, boss)
            return
        }
        if (clock.cycle < raid.nextMechanicAt) return
        val targets = activePlayers(raid)
        if (targets.isEmpty()) {
            boss.resetFaceEntity()
            return
        }
        val primary = targets[random.of(targets.indices)]
        boss.facePlayer(primary)
        boss.anim(TobAssets.SOTETSEG_ATTACK)
        state.attackCount++
        if (state.attackCount % 10 == 0) {
            val target = primary
            target.mes("You have been chosen! Stack together to share Sotetseg's red ball.")
            world.projAnim(
                boss,
                target,
                SpotanimType(TobAssets.SOTETSEG_BIG_BALL),
                "projanim.magic_spell",
            )
            val sharing = targets.count { it.coords == target.coords }.coerceAtLeast(1)
            val damage =
                when (raid.mode) {
                    TobMode.ENTRY -> 15
                    TobMode.NORMAL -> (115 / sharing).coerceAtLeast(1)
                    TobMode.HARD -> (125 / sharing).coerceAtLeast(1)
                }
            targets
                .filter { it.coords == target.coords }
                .forEach { queueSpecialHit(it, boss, damage, 3) }
        } else {
            targets.forEach { target ->
                val magic = random.of(0..1) == 0
                val graphic =
                    if (magic) TobAssets.SOTETSEG_MAGIC_PROJECTILE
                    else TobAssets.SOTETSEG_RANGED_PROJECTILE
                world.projAnim(boss, target, SpotanimType(graphic), "projanim.magic_spell")
                target.queueHit(
                    source = boss,
                    delay = 2,
                    type = if (magic) HitType.Magic else HitType.Ranged,
                    damage = random.of(0..TobScaling.maxHit(47, raid.mode)),
                )
            }
        }
        raid.nextMechanicAt = clock.cycle + 5
    }

    private fun startSotetsegMaze(raid: TobRaidSession, state: SotetsegEncounter, boss: Npc) {
        state.mazeActive = true
        state.mazeStartedAt = clock.cycle
        state.path =
            SotetsegMazeRules.generate(
                    (clock.cycle.toLong() shl 16) xor raid.instance.id.hashCode().toLong()
                )
                .map { roomCoord(raid, TobRoom.SOTETSEG, 15 + it.x, 22 + it.z) }
        state.pathTiles = state.path.toHashSet()
        state.tornadoIndex = 0
        state.tornadoWalking = false
        state.bleeds.clear()
        boss.abortRoute()
        boss.resetFaceEntity()
        boss.movementLocked = true
        boss.anim(TobAssets.SOTETSEG_MAZE_TRANSITION)
        val start = roomCoord(raid, TobRoom.SOTETSEG, 15, 20)
        forEachActive(raid) { player ->
            prepare(player)
            player.anim(TobAssets.SOTETSEG_PLAYER_KNOCKBACK)
            PathingEntityCommon.telejump(player, collision, start)
            player.mes("Follow the glowing path. The maze floor is lethal.")
        }
        message(raid.party, "Sotetseg drags the party into the shadow maze!")
    }

    private fun tickSotetsegMaze(raid: TobRaidSession, state: SotetsegEncounter, boss: Npc) {
        state.path.forEach { world.spotanimMap(SpotanimType(TobAssets.SOTETSEG_PATH), it) }
        val startZ = state.path.firstOrNull()?.z ?: return
        val finishZ = state.path.lastOrNull()?.z ?: return
        val minX = state.path.minOf(CoordGrid::x) - 1
        val maxX = state.path.maxOf(CoordGrid::x) + 1
        forEachActive(raid) { player ->
            val inMaze = player.coords.x in minX..maxX && player.coords.z in startZ..finishZ
            if (inMaze && player.coords !in state.pathTiles) {
                val base = if (raid.mode == TobMode.ENTRY) 11 else 15
                val damage = (player.hitpoints * 667 / 10_000 + base).coerceAtLeast(1)
                queueSpecialHit(player, boss, damage, 0)
            }
            if (clock.cycle == state.mazeStartedAt + 11 && player.coords.z < startZ) {
                player.mes("The maze floor consumes you.")
                queueSpecialHit(player, boss, player.hitpoints, 0)
            }
        }
        tickSotetsegBleeds(raid, state, boss)
        if (state.tornado == null && activePlayers(raid).any { it.coords.z >= startZ + 3 }) {
            state.tornado =
                spawnNpc(raid, TobNpcs.SOTETSEG_TORNADO, state.path.first(), TobNpcRole.HAZARD)
            message(raid.party, "The shadow tornado begins consuming the maze!")
        }
        val tornado = state.tornado
        if (tornado != null && raid.owns(tornado)) {
            if (activePlayers(raid).any { it.coords == tornado.coords }) {
                activePlayers(raid)
                    .filter { it.coords == tornado.coords }
                    .forEach { player ->
                        queueSpecialHit(
                            player,
                            boss,
                            TobScaling.maxHit(random.of(25..41), raid.mode),
                            0,
                        )
                        state.bleeds[player.uid] = SotetsegBleed(10, clock.cycle + 3)
                        player.mes("The tornado inflicts a deep bleed!")
                    }
            }
            if (!state.tornadoWalking) {
                if (state.tornadoIndex >= state.path.size) {
                    activePlayers(raid)
                        .filter { it.coords.z < finishZ }
                        .forEach { player -> queueSpecialHit(player, boss, player.hitpoints, 0) }
                    finishSotetsegMaze(raid, state, boss)
                    return
                }
                state.tornadoWalking = true
                tornado.walkTo(
                    routeFactory,
                    state.path[state.tornadoIndex],
                    passThroughEntities = true,
                ) {
                    state.tornadoIndex++
                    state.tornadoWalking = false
                }
            }
        }
        val active = activePlayers(raid)
        if (active.isNotEmpty() && active.all { it.coords.z >= finishZ }) {
            finishSotetsegMaze(raid, state, boss)
        }
    }

    private fun tickSotetsegBleeds(raid: TobRaidSession, state: SotetsegEncounter, boss: Npc) {
        val iterator = state.bleeds.iterator()
        while (iterator.hasNext()) {
            val (uid, bleed) = iterator.next()
            val player = uid.resolve(players)
            if (player == null || !raid.participant(uid)!!.connected || bleed.remaining == 0) {
                iterator.remove()
                continue
            }
            if (clock.cycle >= bleed.nextHitAt) {
                queueSpecialHit(player, boss, random.of(8..19), 0)
                bleed.remaining--
                bleed.nextHitAt = clock.cycle + 3
                if (bleed.remaining == 0) iterator.remove()
            }
        }
    }

    private fun finishSotetsegMaze(raid: TobRaidSession, state: SotetsegEncounter, boss: Npc) {
        state.tornado?.let { removeRaidNpc(raid, it) }
        state.tornado = null
        state.path = emptyList()
        state.pathTiles = emptySet()
        state.bleeds.clear()
        state.mazeActive = false
        state.tornadoWalking = false
        boss.movementLocked = false
        raid.nextMechanicAt = clock.cycle + 5
        forEachActive(raid) { it.mes("You escape Sotetseg's shadow maze.") }
    }

    private fun tickXarpus(raid: TobRaidSession, encounter: XarpusEncounter) {
        val boss = boss(raid) ?: return
        boss.movementLocked = true
        when (encounter.state.phase) {
            XarpusPhase.EXHUMEDS -> tickXarpusExhumeds(raid, encounter, boss)
            XarpusPhase.COMBAT -> {
                if (
                    encounter.state.updateHealth(
                        boss.hitpoints * 100 / boss.baseHitpointsLvl.coerceAtLeast(1)
                    ) == XarpusPhase.SCREECH
                ) {
                    beginXarpusScreech(raid, encounter, boss)
                    return
                }
                tickXarpusPoison(raid, encounter, boss)
            }
            XarpusPhase.SCREECH -> tickXarpusScreech(encounter, boss)
        }
    }

    private fun tickXarpusExhumeds(raid: TobRaidSession, encounter: XarpusEncounter, boss: Npc) {
        if (clock.cycle >= encounter.recoveryEndsAt) {
            encounter.exhumeds.forEach { (tile, exhumed) ->
                locs.del(exhumed.visual, Int.MAX_VALUE)
                world.spotanimMap(SpotanimType(TobAssets.XARPUS_EXHUMED_END), tile)
            }
            encounter.exhumeds.clear()
            encounter.state.forceCombat()
            boss.anim(TobAssets.XARPUS_SCREECH_ANIM)
            raid.nextMechanicAt = clock.cycle + 4
            message(raid.party, "Xarpus finishes feeding and begins spitting poison!")
            return
        }
        if (
            encounter.spawnedExhumeds < encounter.state.requiredExhumeds &&
                clock.cycle >= encounter.nextExhumedAt
        ) {
            spawnXarpusExhumed(raid, encounter)
            encounter.nextExhumedAt = clock.cycle + encounter.state.exhumedSpawnInterval
        }
        val iterator = encounter.exhumeds.iterator()
        while (iterator.hasNext()) {
            val (tile, exhumed) = iterator.next()
            if (activePlayers(raid).none { it.coords == tile }) {
                world.projAnim(
                    ProjAnim.fromCoordToNpc(
                        tile,
                        boss,
                        TobAssets.XARPUS_HEAL_PROJECTILE,
                        "projanim.magic_spell",
                    )
                )
                boss.heal(encounter.state.healPerUnblockedTick, showHitsplat = true)
            }
            exhumed.remainingTicks--
            if (exhumed.remainingTicks <= 0) {
                locs.del(exhumed.visual, Int.MAX_VALUE)
                world.spotanimMap(SpotanimType(TobAssets.XARPUS_EXHUMED_END), tile)
                iterator.remove()
                encounter.state.completeExhumed()
            }
        }
    }

    private fun spawnXarpusExhumed(raid: TobRaidSession, encounter: XarpusEncounter) {
        val occupied = encounter.exhumeds.keys
        val tile =
            generateSequence {
                    roomCoord(raid, TobRoom.XARPUS, random.of(27..41), random.of(28..42))
                }
                .take(30)
                .firstOrNull { it !in occupied } ?: roomCoord(raid, TobRoom.XARPUS, 27, 28)
        val visual =
            locs.add(
                tile,
                requireNotNull(ServerCacheManager.getObject(TobLocs.XARPUS_EXHUMED)),
                Int.MAX_VALUE,
                LocAngle.North,
                LocShape.CentrepieceStraight,
            )
        world.locAnim(
            visual,
            requireNotNull(RSCM.getReverseMapping(RSCMType.SEQ, TobAssets.XARPUS_POOL_ANIM)),
        )
        encounter.exhumeds[tile] = XarpusExhumed(visual, 15)
        encounter.spawnedExhumeds++
    }

    private fun tickXarpusPoison(raid: TobRaidSession, encounter: XarpusEncounter, boss: Npc) {
        landXarpusPoison(raid, encounter, boss)
        if (clock.cycle % 10 == 0) {
            encounter.poisonPools.forEach {
                world.spotanimMap(SpotanimType(TobAssets.XARPUS_POOL), it)
            }
        }
        forEachActive(raid) { player ->
            if (player.coords in encounter.poisonPools) {
                queueSpecialHit(player, boss, random.of(4..8), 0)
            }
        }
        if (clock.cycle < raid.nextMechanicAt) return
        boss.anim(TobAssets.XARPUS_ATTACK_ANIM)
        forEachActive(raid) { target ->
            val tile = target.coords
            launchTileProjectile(boss, tile, TobAssets.XARPUS_SPIT_PROJECTILE)
            encounter.pendingPoison[tile] = clock.cycle + 2
        }
        raid.nextMechanicAt = clock.cycle + 4
    }

    private fun landXarpusPoison(raid: TobRaidSession, encounter: XarpusEncounter, boss: Npc) {
        val iterator = encounter.pendingPoison.iterator()
        while (iterator.hasNext()) {
            val (tile, landsAt) = iterator.next()
            if (clock.cycle < landsAt) continue
            iterator.remove()
            encounter.poisonPools += tile
            world.spotanimMap(SpotanimType(TobAssets.XARPUS_POOL), tile)
            forEachActive(raid) { player ->
                if (player.coords.chebyshevDistance(tile) <= 1) {
                    queueSpecialHit(player, boss, TobScaling.maxHit(random.of(6..12), raid.mode), 0)
                }
            }
        }
    }

    private fun beginXarpusScreech(raid: TobRaidSession, encounter: XarpusEncounter, boss: Npc) {
        encounter.poisonPools.clear()
        encounter.pendingPoison.clear()
        encounter.nextTurnAt = clock.cycle
        boss.anim(TobAssets.XARPUS_SCREECH_ANIM)
        message(raid.party, "SCREEECH! Do not attack from Xarpus's watched quadrant.")
        tickXarpusScreech(encounter, boss)
    }

    private fun tickXarpusScreech(encounter: XarpusEncounter, boss: Npc) {
        if (clock.cycle < encounter.nextTurnAt) return
        val stare = encounter.state.rotateStare()
        val (dx, dz) =
            when (stare) {
                XarpusStare.NORTH -> 0 to 8
                XarpusStare.EAST -> 8 to 0
                XarpusStare.SOUTH -> 0 to -8
                XarpusStare.WEST -> -8 to 0
            }
        boss.faceSquare(CoordGrid(boss.coords.x + dx, boss.coords.z + dz, boss.coords.level))
        encounter.nextTurnAt = clock.cycle + 8
    }

    private fun xarpusFacesPlayer(stare: XarpusStare, boss: Npc, player: Player): Boolean {
        val dx = player.coords.x - boss.coords.x
        val dz = player.coords.z - boss.coords.z
        return when (stare) {
            XarpusStare.NORTH -> dz > 0 && abs(dz) >= abs(dx)
            XarpusStare.EAST -> dx > 0 && abs(dx) >= abs(dz)
            XarpusStare.SOUTH -> dz < 0 && abs(dz) >= abs(dx)
            XarpusStare.WEST -> dx < 0 && abs(dx) >= abs(dz)
        }
    }

    private fun tickVerzik(raid: TobRaidSession, encounter: VerzikEncounter) {
        if (tickVerzikDeathSequence(raid, encounter)) return
        if (tickVerzikTransition(raid, encounter)) return
        val boss = boss(raid) ?: return
        processVerzikPendingHits(raid, encounter, boss)
        processVerzikAdds(raid, encounter, boss)
        when (encounter.state.phase) {
            VerzikPhase.ONE -> tickVerzikPhaseOne(raid, encounter, boss)
            VerzikPhase.TWO -> tickVerzikPhaseTwo(raid, encounter, boss)
            VerzikPhase.THREE -> tickVerzikPhaseThree(raid, encounter, boss)
            VerzikPhase.DEFEATED -> Unit
        }
    }

    private fun tickVerzikPhaseOne(raid: TobRaidSession, encounter: VerzikEncounter, boss: Npc) {
        if (clock.cycle < raid.nextMechanicAt) return
        val players = activePlayers(raid)
        if (players.isEmpty()) return
        boss.anim(TobAssets.VERZIK_PHASE_ONE_ATTACK, priority = 254)
        val protectionByPlayer =
            players.associateWith { player ->
                encounter.pillars.values
                    .filter { pillar ->
                        isShieldedByVerzikPillar(
                            source = boss.coords,
                            sourceSize = boss.size,
                            target = player.coords,
                            pillar = pillar.npc.coords,
                            pillarSize = pillar.npc.size,
                            canonicalSafeTiles = pillar.safeTiles,
                        )
                    }
                    .minByOrNull { pillar -> pillar.npc.coords.chebyshevDistance(boss.coords) }
            }
        players
            .filter { protectionByPlayer[it] == null }
            .forEach { target ->
                world.projAnim(
                    boss,
                    target,
                    SpotanimType(TobAssets.VERZIK_PHASE_ONE_PROJECTILE),
                    "projanim.magic_spell",
                )
                val rolled =
                    random.of(0..TobScaling.maxHit(VerzikNormalRules.PHASE_ONE_MAX_HIT, raid.mode))
                val protectedAtLaunch = target.vars["varbit.prayer_protectfrommagic"] == 1
                encounter.pendingHits +=
                    VerzikPendingHit(
                        target.uid,
                        clock.cycle + VerzikNormalRules.PHASE_ONE_IMPACT_DELAY,
                        HitType.Magic,
                        VerzikNormalRules.phaseOneDamage(rolled, protectedAtLaunch),
                        TobAssets.VERZIK_PHASE_ONE_IMPACT,
                        0,
                        bypassPrayer = true,
                    )
            }
        protectionByPlayer.values.filterNotNull().distinct().forEach { pillar ->
            world.projAnim(
                boss,
                pillar.npc,
                SpotanimType(TobAssets.VERZIK_PHASE_ONE_PROJECTILE),
                "projanim.magic_spell",
            )
            encounter.pendingPillarHits +=
                VerzikPendingPillarHit(
                    pillar.npc,
                    clock.cycle + VerzikNormalRules.PHASE_ONE_IMPACT_DELAY,
                    random.of(
                        1..TobScaling.maxHit(VerzikNormalRules.PHASE_ONE_PILLAR_MAX_HIT, raid.mode)
                                .coerceAtLeast(1)
                    ),
                )
        }
        raid.nextMechanicAt = clock.cycle + VerzikNormalRules.PHASE_ONE_ATTACK_INTERVAL
    }

    private fun tickVerzikPhaseTwo(raid: TobRaidSession, encounter: VerzikEncounter, boss: Npc) {
        val healthPercent = boss.hitpoints * 100 / boss.baseHitpointsLvl.coerceAtLeast(1)
        if (
            healthPercent <= VerzikNormalRules.PHASE_TWO_POINT_FIVE_PERCENT &&
                !encounter.phaseTwoPointFiveStarted
        ) {
            beginVerzikPhaseTwoPointFive(raid, encounter, boss)
        }
        if (
            healthPercent > VerzikNormalRules.PHASE_TWO_POINT_FIVE_PERCENT &&
                clock.cycle >= encounter.nextBombAt
        ) {
            summonVerzikNylocas(raid, encounter, boss)
            encounter.nextBombAt = Int.MAX_VALUE
        }
        if (
            healthPercent > VerzikNormalRules.PHASE_TWO_POINT_FIVE_PERCENT &&
                encounter.spawnAthanatosAfter >= 0 &&
                clock.cycle >= encounter.spawnAthanatosAfter
        ) {
            spawnVerzikAthanatos(raid, encounter, boss)
            encounter.spawnAthanatosAfter = -1
        }
        if (
            encounter.phaseTwoPointFiveStarted &&
                encounter.nextMatomenosAt >= 0 &&
                clock.cycle >= encounter.nextMatomenosAt
        ) {
            spawnVerzikMatomenosCycle(raid, encounter, boss)
        }
        if (clock.cycle < raid.nextMechanicAt) return
        val targets = activePlayers(raid)
        if (targets.isEmpty()) {
            boss.resetFaceEntity()
            return
        }
        val primaryTarget = targets[random.of(targets.indices)]
        encounter.attackCounter++
        if (!handleVerzikPhaseTwoMeleeResponse(raid, encounter, boss, targets)) {
            when {
                VerzikNormalRules.lightningDue(encounter.attackCounter) ->
                    verzikLightningAttack(raid, encounter, boss, targets)
                healthPercent > 70 || random.of(0..3) == 0 ->
                    verzikPhaseTwoRanged(raid, encounter, boss, targets, primaryTarget)
                else -> verzikBloodAttack(raid, encounter, boss, primaryTarget)
            }
        }
        if (encounter.phaseTwoPointFiveStarted && encounter.matomenosAttacksRemaining > 0) {
            encounter.matomenosAttacksRemaining--
            if (encounter.matomenosAttacksRemaining == 0) {
                absorbVerzikMatomenos(raid, encounter, boss)
                encounter.nextMatomenosAt = clock.cycle + VerzikNormalRules.MATOMENOS_RESPAWN_DELAY
            }
        }
        raid.nextMechanicAt = clock.cycle + VerzikNormalRules.PHASE_TWO_ATTACK_INTERVAL
    }

    private fun tickVerzikPhaseThree(raid: TobRaidSession, encounter: VerzikEncounter, boss: Npc) {
        tickVerzikWebs(raid, encounter, boss)
        tickVerzikYellowPools(raid, encounter, boss)
        tickVerzikGreenBall(raid, encounter, boss)
        if (encounter.webSpecialEndsAt >= 0) {
            if (clock.cycle < encounter.webSpecialEndsAt) return
            encounter.webSpecialEndsAt = -1
            boss.ignoreCombatInteractions = false
        }
        val healthPercent = boss.hitpoints * 100 / boss.baseHitpointsLvl.coerceAtLeast(1)
        if (isVerzikPhaseThreeEnraged(healthPercent) && !encounter.tornadosSpawned) {
            encounter.state.markEnraged()
            spawnVerzikTornados(raid, encounter)
            encounter.tornadosSpawned = true
            message(raid.party, "Verzik calls forth a tornado for every surviving raider!")
        }
        if (
            clock.cycle < raid.nextMechanicAt ||
                encounter.yellowStartedAt >= 0 ||
                encounter.greenBall != null
        )
            return
        val targets = activePlayers(raid)
        if (targets.isEmpty()) {
            boss.resetFaceEntity()
            return
        }
        val tank = resolveVerzikPhaseThreeTank(encounter, targets)
        boss.facePlayer(tank)
        if (
            squareChebyshevDistance(tank.coords, 1, boss.coords, boss.size) > 1 &&
                boss.routeDestination.isEmpty()
        ) {
            boss.walkTo(routeFactory, tank.coords)
        }
        if (encounter.attackCounter < VerzikNormalRules.PHASE_THREE_AUTOS_BEFORE_SPECIAL) {
            verzikPhaseThreeAuto(raid, encounter, boss, targets, tank)
            encounter.attackCounter++
        } else {
            when (encounter.state.nextSpecial()) {
                VerzikSpecial.NYLOCAS -> spawnVerzikBombs(raid, encounter, boss)
                VerzikSpecial.WEBS ->
                    spawnVerzikWebs(raid, encounter, boss, targets[random.of(targets.indices)])
                VerzikSpecial.YELLOW_POOLS -> startVerzikYellowPools(raid, encounter, boss, targets)
                VerzikSpecial.GREEN_BALL -> verzikGreenBall(raid, encounter, boss, targets)
                VerzikSpecial.ELECTRIC -> verzikLightningAttack(raid, encounter, boss, targets)
                VerzikSpecial.BLOOD ->
                    verzikBloodAttack(raid, encounter, boss, targets[random.of(targets.indices)])
                VerzikSpecial.TORNADO -> {
                    if (!encounter.tornadosSpawned) spawnVerzikTornados(raid, encounter)
                    encounter.tornadosSpawned = true
                }
            }
            encounter.attackCounter = 0
        }
        val delay =
            when {
                raid.mode == TobMode.HARD && healthPercent <= 20 -> 4
                raid.mode == TobMode.HARD -> 5
                healthPercent <= 20 -> VerzikNormalRules.PHASE_THREE_ENRAGED_INTERVAL
                else -> VerzikNormalRules.PHASE_THREE_ATTACK_INTERVAL
            }
        raid.nextMechanicAt = clock.cycle + delay
    }

    private fun resolveVerzikPhaseThreeTank(
        encounter: VerzikEncounter,
        targets: List<Player>,
    ): Player {
        val retained = encounter.primaryTank?.resolve(players)?.takeIf { it in targets }
        if (retained != null) return retained
        return targets[random.of(targets.indices)].also { encounter.primaryTank = it.uid }
    }

    private fun processVerzikPendingHits(
        raid: TobRaidSession,
        encounter: VerzikEncounter,
        boss: Npc,
    ) {
        val hitIterator = encounter.pendingHits.iterator()
        while (hitIterator.hasNext()) {
            val pending = hitIterator.next()
            if (clock.cycle < pending.landsAt) continue
            hitIterator.remove()
            val target = pending.target.resolve(players) ?: continue
            if (target !in activePlayers(raid)) continue
            if (pending.impactGraphic != null) target.spotanim(pending.impactGraphic)
            if (pending.bypassPrayer) {
                target.queueHit(
                    source = boss,
                    delay = normalizeTobSpecialHitDelay(0),
                    type = pending.type,
                    damage = pending.damage,
                    modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                )
            } else {
                target.queueHit(
                    source = boss,
                    delay = normalizeTobSpecialHitDelay(0),
                    type = pending.type,
                    damage = pending.damage,
                    modifier = NpcPartialProtectionPrayerPlayerHitModifier(pending.prayerReduction),
                )
            }
            if (pending.bossHealAmount > 0) boss.heal(pending.bossHealAmount, true)
        }
        val tileIterator = encounter.pendingTileHits.iterator()
        while (tileIterator.hasNext()) {
            val pending = tileIterator.next()
            if (clock.cycle < pending.landsAt) continue
            tileIterator.remove()
            world.spotanimMap(SpotanimType(pending.impactGraphic), pending.tile)
            val target = pending.target.resolve(players) ?: continue
            if (target.coords != pending.tile || target !in activePlayers(raid)) continue
            target.queueHit(
                source = boss,
                delay = normalizeTobSpecialHitDelay(0),
                type = pending.type,
                damage = pending.damage,
                modifier = NpcPartialProtectionPrayerPlayerHitModifier(pending.prayerReduction),
            )
        }
        val pillarIterator = encounter.pendingPillarHits.iterator()
        while (pillarIterator.hasNext()) {
            val pending = pillarIterator.next()
            if (clock.cycle < pending.landsAt) continue
            pillarIterator.remove()
            val pillar = encounter.pillars[pending.pillar] ?: continue
            if (!pillar.npc.isSlotAssigned || pillar.health <= 0) continue
            pillar.npc.spotanim(TobAssets.VERZIK_PILLAR_IMPACT)
            val previousHealth = pillar.health
            pillar.health = verzikPillarHealthAfterHit(previousHealth, pending.damage)
            pillar.npc.hitpoints = pillar.health.coerceAtLeast(1)
            showVerzikPillarDamage(pillar.npc, previousHealth, pillar.health)
            if (pillar.health == 0) collapseVerzikPillar(raid, encounter, pillar)
        }
    }

    private fun showVerzikPillarDamage(npc: Npc, previousHealth: Int, currentHealth: Int) {
        val damage = (previousHealth - currentHealth).coerceAtLeast(0)
        val hitmarkGroup = hitmark_groups.regular_damage
        val lit = hitmarkGroup.lit.asRSCM(RSCMType.HITMARK)
        val tint = hitmarkGroup.tint?.asRSCM(RSCMType.HITMARK) ?: lit
        npc.showHitmark(Hitmark.fromNoSource(lit, lit, tint, damage, 0))

        val headbar = npc.visHeadbar(params.headbar)
        val maximum = npc.baseHitpointsLvl.coerceAtLeast(1)
        val fill = (currentHealth * headbar.segments / maximum).coerceIn(0, headbar.segments)
        npc.showHeadbar(
            Headbar.fromNoSource(
                self = headbar.id,
                public = headbar.id,
                startFill = fill,
                endFill = fill,
                startTime = 0,
                endTime = 0,
            )
        )
    }

    private fun processVerzikAdds(raid: TobRaidSession, encounter: VerzikEncounter, boss: Npc) {
        val active = activePlayers(raid)
        val bombIterator = encounter.bombs.iterator()
        while (bombIterator.hasNext()) {
            val (npc, bomb) = bombIterator.next()
            if (!npc.isSlotAssigned || raid.npcRoles[npc] != TobNpcRole.VERZIK_ADD) {
                bombIterator.remove()
                continue
            }
            var target = bomb.target.resolve(players)?.takeIf { it in active }
            if (target == null && active.isNotEmpty() && bomb.explodesAt < 0) {
                target = active[random.of(active.indices)]
                bomb.target = target.uid
            }
            if (target != null && bomb.explodesAt < 0) {
                if (clock.cycle % 3 == 0) npc.walkTo(routeFactory, target.coords)
                val targetTriggered = npc.coords.chebyshevDistance(target.coords) <= 1
                val otherPlayerTriggered =
                    active.any { it.uid != target.uid && it.coords == npc.coords }
                if (targetTriggered || otherPlayerTriggered || clock.cycle >= bomb.expiresAt) {
                    bomb.explodesAt = clock.cycle + 5
                }
            }
            if (bomb.explodesAt == clock.cycle + 2) {
                npc.anim(TobAssets.nylocasDetonateAnimation(npc.id, raid.mode), priority = 254)
                npc.abortRoute()
            }
            if (bomb.explodesAt >= 0 && clock.cycle >= bomb.explodesAt) {
                damagePlayersFromVerzikBomb(raid, npc, npc.coords)
                removeRaidNpc(raid, npc)
                bombIterator.remove()
            }
        }
        val pendingAthanatosIterator = encounter.pendingAthanatos.iterator()
        while (pendingAthanatosIterator.hasNext()) {
            val pending = pendingAthanatosIterator.next()
            if (clock.cycle < pending.landsAt) continue
            pendingAthanatosIterator.remove()
            val npc =
                spawnNpc(
                    raid,
                    TobNpcs.verzikAthanatos(raid.mode),
                    pending.tile,
                    TobNpcRole.VERZIK_ADD,
                )
            npc.mode = null
            encounter.athanatos[npc] = VerzikAthanatosRuntime(clock.cycle + 3)
            npc.walkTo(routeFactory, boss.coords)
            active
                .filter { it.coords == pending.tile }
                .forEach {
                    queueSpecialHit(it, boss, TobScaling.maxHit(random.of(50..78), raid.mode), 0)
                }
        }
        val athanatosIterator = encounter.athanatos.iterator()
        while (athanatosIterator.hasNext()) {
            val (npc, runtime) = athanatosIterator.next()
            if (!npc.isSlotAssigned || raid.npcRoles[npc] != TobNpcRole.VERZIK_ADD) {
                athanatosIterator.remove()
                continue
            }
            if (runtime.poisonedAt >= 0) {
                if (clock.cycle >= runtime.poisonedAt) {
                    val source = runtime.poisonSource?.resolve(players) ?: randomActive(raid)
                    if (source != null) {
                        boss.queueHit(
                            source = source,
                            delay = 1,
                            type = HitType.Typeless,
                            damage = runtime.poisonDamage,
                            modifier = npcHitModifier,
                            hitmark = hitmark_groups.poison_damage,
                        )
                    }
                    removeRaidNpc(raid, npc)
                    athanatosIterator.remove()
                }
                continue
            }
            if (
                npc.coords.chebyshevDistance(boss.coords) < 5 && clock.cycle >= runtime.nextHealAt
            ) {
                npc.faceSquare(boss.coords)
                boss.heal(random.of(9..10), true)
                runtime.nextHealAt = clock.cycle + 3
            } else if (clock.cycle % 3 == 0) {
                npc.walkTo(routeFactory, boss.coords)
            }
        }
        val matomenosIterator = encounter.matomenos.iterator()
        while (matomenosIterator.hasNext()) {
            val (npc, runtime) = matomenosIterator.next()
            if (!npc.isSlotAssigned || raid.npcRoles[npc] != TobNpcRole.VERZIK_ADD) {
                matomenosIterator.remove()
                continue
            }
            if (clock.cycle >= runtime.nextWanderAt && !npc.isBound) {
                val destination = npc.coords.translate(random.of(-2..2), random.of(-2..2))
                npc.walkTo(routeFactory, destination)
                runtime.nextWanderAt = clock.cycle + random.of(3..6)
            }
        }
        val tornadoIterator = encounter.tornados.iterator()
        while (tornadoIterator.hasNext()) {
            val (npc, tornado) = tornadoIterator.next()
            val target = tornado.target.resolve(players)
            if (target == null || target !in active || !npc.isSlotAssigned) {
                if (npc.isSlotAssigned) removeRaidNpc(raid, npc)
                tornadoIterator.remove()
                continue
            }
            if (clock.cycle % 2 == 0) npc.walkTo(routeFactory, target.coords)
            if (clock.cycle % 5 == 0) npc.spotanim(TobAssets.VERZIK_TORNADO_SPOTANIM)
            if (npc.coords == target.coords && clock.cycle >= tornado.nextDamageAt) {
                val damage =
                    (target.hitpoints * VerzikNormalRules.TORNADO_DAMAGE_PERCENT / 100)
                        .coerceAtLeast(1)
                queueSpecialHit(target, npc, damage, 0)
                boss.heal(damage * VerzikNormalRules.TORNADO_HEAL_MULTIPLIER, true)
                tornado.nextDamageAt = clock.cycle + 5
            }
        }
    }

    private fun verzikPhaseTwoRanged(
        raid: TobRaidSession,
        encounter: VerzikEncounter,
        boss: Npc,
        targets: List<Player>,
        primaryTarget: Player = targets.first(),
    ) {
        boss.facePlayer(primaryTarget)
        boss.anim(TobAssets.VERZIK_PHASE_TWO_ATTACK)
        targets.forEach { target ->
            val tile = target.coords
            launchTileProjectile(boss, tile, TobAssets.VERZIK_PHASE_TWO_RANGED_PROJECTILE)
            encounter.pendingTileHits +=
                VerzikPendingTileHit(
                    target.uid,
                    tile,
                    clock.cycle + 2,
                    HitType.Ranged,
                    random.of(
                        0..TobScaling.maxHit(VerzikNormalRules.PHASE_TWO_URN_MAX_HIT, raid.mode)
                    ),
                    TobAssets.VERZIK_PHASE_TWO_RANGED_IMPACT,
                    50,
                )
        }
    }

    private fun verzikBloodAttack(
        raid: TobRaidSession,
        encounter: VerzikEncounter,
        boss: Npc,
        target: Player,
    ) {
        boss.facePlayer(target)
        boss.anim(TobAssets.VERZIK_PHASE_TWO_ATTACK)
        world.projAnim(
            boss,
            target,
            SpotanimType(TobAssets.VERZIK_PHASE_TWO_BLOOD_PROJECTILE),
            "projanim.magic_spell",
        )
        val rolled =
            random.of(0..TobScaling.maxHit(VerzikNormalRules.PHASE_TWO_BLOOD_MAX_HIT, raid.mode))
        val protectedAtLaunch = target.vars["varbit.prayer_protectfrommagic"] == 1
        encounter.pendingHits +=
            VerzikPendingHit(
                target.uid,
                clock.cycle + 2,
                HitType.Magic,
                if (protectedAtLaunch) 0 else rolled,
                TobAssets.VERZIK_PHASE_TWO_BLOOD_IMPACT,
                0,
                bossHealAmount = rolled / 2,
                bypassPrayer = true,
            )
    }

    private fun verzikLightningAttack(
        raid: TobRaidSession,
        encounter: VerzikEncounter,
        boss: Npc,
        targets: List<Player>,
    ) {
        var from: org.rsmod.game.entity.PathingEntity = boss
        val remaining = targets.toMutableList()
        var current = remaining.removeAt(random.of(remaining.indices))
        boss.facePlayer(current)
        boss.anim(TobAssets.VERZIK_PHASE_TWO_LIGHTNING)
        var delay = 1
        var bounces = 0
        while (bounces < 4) {
            when (from) {
                is Npc ->
                    world.projAnim(
                        from,
                        current,
                        SpotanimType(TobAssets.VERZIK_LIGHTNING_PROJECTILE),
                        "projanim.magic_spell",
                    )
                is Player ->
                    world.projAnim(
                        from,
                        current,
                        SpotanimType(TobAssets.VERZIK_LIGHTNING_PROJECTILE),
                        "projanim.magic_spell",
                    )
            }
            encounter.pendingHits +=
                VerzikPendingHit(
                    current.uid,
                    clock.cycle + delay,
                    HitType.Typeless,
                    random.of(8..TobScaling.maxHit(12, raid.mode)),
                    null,
                    0,
                )
            bounces++
            val next =
                remaining
                    .filter { it.coords.chebyshevDistance(current.coords) <= 9 }
                    .minByOrNull { it.coords.chebyshevDistance(current.coords) }
            if (next == null) {
                val insulated =
                    current.worn[dev.openrune.util.Wearpos.Feet.slot]?.id ==
                        TobItems.INSULATED_BOOTS
                encounter.pendingHits +=
                    VerzikPendingHit(
                        current.uid,
                        clock.cycle + delay + 1,
                        HitType.Typeless,
                        TobScaling.maxHit(if (insulated) 25 else 50, raid.mode),
                        null,
                        0,
                    )
                break
            }
            remaining.remove(next)
            from = current
            current = next
            delay++
        }
        encounter.lightningCount++
        if (encounter.lightningCount % 3 == 0) {
            val hasAthanatos =
                encounter.athanatos.keys.any(Npc::isSlotAssigned) ||
                    encounter.pendingAthanatos.isNotEmpty() ||
                    encounter.spawnAthanatosAfter >= 0
            if (
                shouldSummonVerzikNylocas(
                    encounter.lightningCount,
                    random.of(0..3),
                    hasAthanatos,
                    boss.hitpoints * 100 / boss.baseHitpointsLvl.coerceAtLeast(1),
                )
            ) {
                summonVerzikNylocas(raid, encounter, boss)
            }
        }
    }

    private fun handleVerzikPhaseTwoMeleeResponse(
        raid: TobRaidSession,
        encounter: VerzikEncounter,
        boss: Npc,
        targets: List<Player>,
    ): Boolean {
        val victims =
            targets.filter {
                verzikPhaseTwoMeleePosition(it.coords, boss.coords, boss.size) ==
                    VerzikPhaseTwoMeleePosition.SLAM
            }
        if (victims.isEmpty()) return false
        if (
            !shouldVerzikBodySlam(
                random.of(0 until VerzikNormalRules.PHASE_TWO_SLAM_CHANCE_DENOMINATOR)
            )
        )
            return false
        boss.facePlayer(victims.first())
        boss.anim(TobAssets.VERZIK_PHASE_TWO_MELEE, priority = 254)
        var affected = false
        victims.forEach { player ->
            if (
                verzikPhaseTwoMeleePosition(player.coords, boss.coords, boss.size) !=
                    VerzikPhaseTwoMeleePosition.SLAM
            ) {
                return@forEach
            }
            if (clock.cycle < encounter.bounceCooldown.getOrDefault(player.uid, 0)) return@forEach
            affected = true
            val (dx, dz) = verzikKnockbackVector(player.coords, boss.coords, boss.size)
            val destination =
                player.coords.translate(
                    dx * VerzikNormalRules.PHASE_TWO_KNOCKBACK_DISTANCE,
                    dz * VerzikNormalRules.PHASE_TWO_KNOCKBACK_DISTANCE,
                )
            player.anim(TobAssets.VERZIK_PLAYER_KNOCKBACK)
            CombatEffects.bind(player, VerzikNormalRules.PHASE_TWO_STUN_TICKS)
            queueSpecialHit(
                player,
                boss,
                random.of(
                    0..TobScaling.maxHit(VerzikNormalRules.PHASE_TWO_SLAM_MAX_HIT, raid.mode)
                ),
                0,
            )
            PathingEntityCommon.exactMove(
                player,
                player.coords,
                destination,
                delay1 = 0,
                delay2 = VerzikNormalRules.PHASE_TWO_KNOCKBACK_CLIENT_CYCLES,
                dir = verzikKnockbackFacing(dx, dz),
                collision = collision,
            )
            encounter.bounceCooldown[player.uid] = clock.cycle + 4
        }
        return affected
    }

    private fun verzikKnockbackFacing(dx: Int, dz: Int): Int =
        when {
            dx < 0 && dz < 0 -> constants.em_face_northeast
            dx < 0 && dz > 0 -> constants.em_face_southeast
            dx > 0 && dz < 0 -> constants.em_face_northwest
            dx > 0 && dz > 0 -> constants.em_face_southwest
            dx < 0 -> constants.em_face_east
            dx > 0 -> constants.em_face_west
            dz < 0 -> constants.em_face_north
            else -> constants.em_face_south
        }

    private fun summonVerzikNylocas(
        raid: TobRaidSession,
        encounter: VerzikEncounter,
        boss: Npc,
    ): Boolean {
        val alreadyActive =
            encounter.athanatos.keys.any(Npc::isSlotAssigned) ||
                encounter.pendingAthanatos.isNotEmpty() ||
                encounter.spawnAthanatosAfter >= 0
        if (alreadyActive) return false
        spawnVerzikBombs(raid, encounter, boss)
        encounter.spawnAthanatosAfter = clock.cycle + 8
        return true
    }

    private fun spawnVerzikBombs(raid: TobRaidSession, encounter: VerzikEncounter, boss: Npc) {
        val targets = activePlayers(raid)
        if (targets.isEmpty()) return
        val spawns = listOf(22 to 18, 22 to 24, 22 to 30, 42 to 20, 42 to 26, 42 to 32)
        targets.forEachIndexed { index, target ->
            val local = spawns[index % spawns.size]
            val npc =
                spawnNpc(
                    raid,
                    TobNpcs.verzikBombs(raid.mode).let { ids -> ids[random.of(ids.indices)] },
                    roomCoord(raid, TobRoom.VERZIK, local.first, local.second),
                    TobNpcRole.VERZIK_ADD,
                )
            npc.mode = null
            encounter.bombs[npc] = VerzikBomb(target.uid, expiresAt = clock.cycle + 25)
            npc.walkTo(routeFactory, target.coords)
        }
        boss.anim(TobAssets.VERZIK_SUMMON)
    }

    private fun spawnVerzikAthanatos(raid: TobRaidSession, encounter: VerzikEncounter, boss: Npc) {
        val target = randomActive(raid) ?: return
        val tile = target.coords
        boss.facePlayer(target)
        launchTileProjectile(boss, tile, TobAssets.VERZIK_ATHANATOS_PROJECTILE)
        encounter.pendingAthanatos += VerzikPendingAthanatos(tile, clock.cycle + 2)
    }

    private fun beginVerzikPhaseTwoPointFive(
        raid: TobRaidSession,
        encounter: VerzikEncounter,
        boss: Npc,
    ) {
        encounter.phaseTwoPointFiveStarted = true
        encounter.spawnAthanatosAfter = -1
        spawnVerzikMatomenosCycle(raid, encounter, boss)
        message(
            raid.party,
            "Verzik summons Nylocas Matomenos. Damage them before she absorbs their health!",
        )
    }

    private fun spawnVerzikMatomenosCycle(
        raid: TobRaidSession,
        encounter: VerzikEncounter,
        boss: Npc,
    ) {
        encounter.nextMatomenosAt = -1
        encounter.summonHealWarned.clear()
        encounter.healFromDamageUntil = clock.cycle + VerzikNormalRules.MATOMENOS_DAMAGE_HEAL_TICKS
        encounter.matomenosAttacksRemaining = VerzikNormalRules.MATOMENOS_ATTACKS_BEFORE_ABSORB
        boss.anim(TobAssets.VERZIK_PHASE_TWO_SUMMON, priority = 254)
        val count = verzikMatomenosCount(activePlayers(raid).size)
        val tiles =
            listOf(roomCoord(raid, TobRoom.VERZIK, 27, 23), roomCoord(raid, TobRoom.VERZIK, 36, 23))
        repeat(count) { index ->
            val npc =
                spawnNpc(
                    raid,
                    TobNpcs.verzikMatomenos(raid.mode),
                    tiles[index],
                    TobNpcRole.VERZIK_ADD,
                )
            npc.mode = null
            npc.movementLocked = false
            npc.baseHitpointsLvl = VerzikNormalRules.MATOMENOS_HITPOINTS
            npc.hitpoints = VerzikNormalRules.MATOMENOS_HITPOINTS
            encounter.matomenos[npc] = VerzikMatomenosRuntime(clock.cycle + 2)
        }
        raid.nextMechanicAt = clock.cycle + VerzikNormalRules.MATOMENOS_SUMMON_TICKS
    }

    private fun absorbVerzikMatomenos(raid: TobRaidSession, encounter: VerzikEncounter, boss: Npc) {
        encounter.matomenos.keys.toList().forEach { npc ->
            if (npc.isSlotAssigned) {
                boss.heal(npc.hitpoints.coerceAtLeast(0), showHitsplat = true)
                removeRaidNpc(raid, npc)
            }
        }
        encounter.matomenos.clear()
    }

    private fun damagePlayersFromVerzikBomb(raid: TobRaidSession, npc: Npc, tile: CoordGrid) {
        activePlayers(raid).forEach { player ->
            val baseDamage = verzikBombBaseDamage(player.coords.chebyshevDistance(tile))
            if (baseDamage > 0) {
                queueSpecialHit(player, npc, TobScaling.maxHit(baseDamage, raid.mode), 0)
            }
        }
    }

    private fun verzikPhaseThreeAuto(
        raid: TobRaidSession,
        encounter: VerzikEncounter,
        boss: Npc,
        targets: List<Player>,
        tank: Player,
    ) {
        val tankInMeleeRange = squareChebyshevDistance(tank.coords, 1, boss.coords, boss.size) <= 1
        if (tankInMeleeRange && random.of(0..2) == 2) {
            boss.anim(TobAssets.VERZIK_PHASE_THREE_MELEE)
            targets
                .filter { squareChebyshevDistance(it.coords, 1, boss.coords, boss.size) <= 1 }
                .forEach { target ->
                    queueSpecialHit(
                        target,
                        boss,
                        random.of(
                            0..TobScaling.maxHit(
                                    VerzikNormalRules.PHASE_THREE_MELEE_MAX_HIT,
                                    raid.mode,
                                )
                        ),
                        1,
                    )
                }
            return
        }
        val magic = random.of(0..1) == 0
        boss.anim(
            if (magic) TobAssets.VERZIK_PHASE_THREE_MAGIC else TobAssets.VERZIK_PHASE_THREE_RANGED
        )
        targets.forEach { target ->
            world.projAnim(
                boss,
                target,
                SpotanimType(
                    if (magic) TobAssets.VERZIK_PHASE_THREE_MAGIC_PROJECTILE
                    else TobAssets.VERZIK_PHASE_THREE_RANGED_PROJECTILE
                ),
                "projanim.magic_spell",
            )
            encounter.pendingHits +=
                VerzikPendingHit(
                    target.uid,
                    clock.cycle + 2,
                    if (magic) HitType.Magic else HitType.Ranged,
                    random.of(
                        0..TobScaling.maxHit(VerzikNormalRules.PHASE_THREE_AUTO_MAX_HIT, raid.mode)
                    ),
                    null,
                    50,
                )
        }
    }

    private fun spawnVerzikWebs(
        raid: TobRaidSession,
        encounter: VerzikEncounter,
        boss: Npc,
        target: Player,
    ) {
        boss.ignoreCombatInteractions = true
        boss.walkTo(routeFactory, verzikRoomCenter(raid), passThroughEntities = true)
        encounter.webSpecialEndsAt =
            clock.cycle + VerzikNormalRules.WEB_VOLLEYS * VerzikNormalRules.WEB_VOLLEY_INTERVAL + 3
        encounter.webVolleyTarget = target.uid
        encounter.webVolleysRemaining = VerzikNormalRules.WEB_VOLLEYS
        boss.anim(TobAssets.VERZIK_WEBSPIN)
        launchVerzikWebVolley(raid, encounter, boss, target)
        encounter.webVolleysRemaining--
        encounter.nextWebVolleyAt = clock.cycle + VerzikNormalRules.WEB_VOLLEY_INTERVAL
        message(raid.party, "Verzik spins webs around the party. Keep moving or cut free!")
    }

    private fun launchVerzikWebVolley(
        raid: TobRaidSession,
        encounter: VerzikEncounter,
        boss: Npc,
        target: Player,
    ) {
        val possible =
            buildList {
                    for (dx in -4..4) for (dz in -4..4) {
                        val tile = target.coords.translate(dx, dz)
                        if (
                            tile != target.coords &&
                                tile.x in
                                    roomCoord(raid, TobRoom.VERZIK, 19, 15).x..roomCoord(
                                                raid,
                                                TobRoom.VERZIK,
                                                44,
                                                35,
                                            )
                                            .x &&
                                tile.z in
                                    roomCoord(raid, TobRoom.VERZIK, 19, 15).z..roomCoord(
                                                raid,
                                                TobRoom.VERZIK,
                                                44,
                                                35,
                                            )
                                            .z
                        )
                            add(tile)
                    }
                }
                .shuffled()
                .take(VerzikNormalRules.WEB_PROJECTILES_PER_VOLLEY)
        possible.forEach { tile ->
            launchTileProjectile(boss, tile, TobAssets.VERZIK_WEB_PROJECTILE)
            val visual =
                locs.add(
                    tile,
                    requireNotNull(ServerCacheManager.getObject(TobLocs.VERZIK_WEB)),
                    Int.MAX_VALUE,
                    LocAngle.North,
                    LocShape.CentrepieceStraight,
                )
            val web = spawnNpc(raid, TobNpcs.verzikWeb(raid.mode), tile, TobNpcRole.VERZIK_ADD)
            web.movementLocked = true
            web.baseHitpointsLvl = VerzikNormalRules.WEB_HITPOINTS
            web.hitpoints = VerzikNormalRules.WEB_HITPOINTS
            web.anim(TobAssets.VERZIK_WEB_IDLE)
            encounter.webs[web] =
                VerzikWebRuntime(visual, clock.cycle + VerzikNormalRules.WEB_LIFETIME)
        }
    }

    private fun tickVerzikWebs(raid: TobRaidSession, encounter: VerzikEncounter, boss: Npc) {
        if (encounter.webVolleysRemaining > 0 && clock.cycle >= encounter.nextWebVolleyAt) {
            val active = activePlayers(raid)
            val target =
                encounter.webVolleyTarget?.resolve(players)?.takeIf { it in active }
                    ?: active.firstOrNull()
            if (target != null) {
                launchVerzikWebVolley(raid, encounter, boss, target)
                encounter.webVolleysRemaining--
                encounter.nextWebVolleyAt = clock.cycle + VerzikNormalRules.WEB_VOLLEY_INTERVAL
            } else {
                encounter.webVolleysRemaining = 0
            }
        }
        val iterator = encounter.webs.iterator()
        while (iterator.hasNext()) {
            val (npc, web) = iterator.next()
            if (clock.cycle >= web.expiresAt || !npc.isSlotAssigned) {
                locs.del(web.visual, Int.MAX_VALUE)
                releaseVerzikWeb(web)
                if (npc.isSlotAssigned) removeRaidNpc(raid, npc)
                iterator.remove()
                continue
            }
            activePlayers(raid).forEach { player ->
                if (player.coords == npc.coords && web.trapped.add(player.uid)) {
                    CombatEffects.freeze(player, VerzikNormalRules.WEB_BIND_TICKS)
                    queueSpecialHit(player, boss, TobScaling.maxHit(20, raid.mode), 0)
                    player.mes("The sticky web traps you! Attack it to break free.")
                }
            }
        }
    }

    private fun releaseVerzikWeb(web: VerzikWebRuntime) {
        web.trapped.forEach { uid -> uid.resolve(players)?.let(CombatEffects::unfreeze) }
        web.trapped.clear()
    }

    private fun startVerzikYellowPools(
        raid: TobRaidSession,
        encounter: VerzikEncounter,
        boss: Npc,
        targets: List<Player>,
    ) {
        boss.ignoreCombatInteractions = true
        boss.anim(TobAssets.VERZIK_POWERBLAST)
        encounter.yellowPools.clear()
        repeat(targets.size) {
            val tile =
                generateSequence {
                        roomCoord(raid, TobRoom.VERZIK, random.of(21..42), random.of(17..34))
                    }
                    .first { it !in encounter.yellowPools }
            encounter.yellowPools += tile
        }
        encounter.yellowStartedAt = clock.cycle
        message(raid.party, "Stand alone on a yellow pool before Verzik's power blast lands!")
    }

    private fun tickVerzikYellowPools(raid: TobRaidSession, encounter: VerzikEncounter, boss: Npc) {
        if (encounter.yellowStartedAt < 0) return
        val elapsed = clock.cycle - encounter.yellowStartedAt
        encounter.yellowPools.forEach {
            world.spotanimMap(SpotanimType(TobAssets.VERZIK_YELLOW_POOL), it)
        }
        if (elapsed == VerzikNormalRules.YELLOW_PROJECTILE_TICK) {
            activePlayers(raid).forEach { target ->
                world.projAnim(
                    boss,
                    target,
                    SpotanimType(TobAssets.VERZIK_POWERBLAST_PROJECTILE),
                    "projanim.magic_spell",
                )
            }
        }
        if (elapsed == VerzikNormalRules.YELLOW_IMPACT_TICK) {
            val occupancy =
                encounter.yellowPools.associateWith { tile ->
                    activePlayers(raid).count { it.coords == tile }
                }
            activePlayers(raid).forEach { player ->
                if (!isProtectedByVerzikYellowPool(player.coords, occupancy)) {
                    player.mes("Verzik's power blast strikes you for missing a yellow pool!")
                    queueSpecialHit(
                        player,
                        boss,
                        TobScaling.maxHit(random.of(50..80), raid.mode),
                        0,
                    )
                }
            }
        }
        if (elapsed >= VerzikNormalRules.YELLOW_END_TICK) {
            encounter.yellowPools.clear()
            encounter.yellowStartedAt = -1
            boss.ignoreCombatInteractions = false
            raid.nextMechanicAt = clock.cycle + VerzikNormalRules.PHASE_THREE_ATTACK_INTERVAL
        }
    }

    private fun verzikGreenBall(
        raid: TobRaidSession,
        encounter: VerzikEncounter,
        boss: Npc,
        targets: List<Player>,
    ) {
        boss.anim(TobAssets.VERZIK_PHASE_THREE_MELEE)
        val target = targets[random.of(targets.indices)]
        world.projAnim(
            boss,
            target,
            SpotanimType(TobAssets.VERZIK_POWERBLAST_PROJECTILE),
            "projanim.magic_spell",
        )
        target.mes("Bounce the green ball to another player!")
        encounter.greenBall =
            VerzikGreenBallRuntime(
                holder = target.uid,
                bounces = 0,
                landsAt = clock.cycle + VerzikNormalRules.GREEN_BALL_TRAVEL_TICKS,
            )
    }

    private fun tickVerzikGreenBall(raid: TobRaidSession, encounter: VerzikEncounter, boss: Npc) {
        val ball = encounter.greenBall ?: return
        if (clock.cycle < ball.landsAt) return
        val active = activePlayers(raid)
        val holder = ball.holder.resolve(players)?.takeIf { it in active }
        if (holder == null) {
            encounter.greenBall = null
            return
        }
        if (ball.bounces >= VerzikNormalRules.GREEN_BALL_BOUNCES) {
            holder.mes("The green ball dissipates after the final bounce.")
            encounter.greenBall = null
            return
        }
        val receiver =
            active
                .filter {
                    it.uid != holder.uid &&
                        it.coords.chebyshevDistance(holder.coords) <=
                            VerzikNormalRules.GREEN_BALL_RANGE
                }
                .minByOrNull { it.coords.chebyshevDistance(holder.coords) }
        if (receiver == null) {
            encounter.pendingHits +=
                VerzikPendingHit(
                    holder.uid,
                    clock.cycle,
                    HitType.Typeless,
                    TobScaling.maxHit(
                        VerzikNormalRules.greenBallDamage(holder.baseHitpointsLvl),
                        raid.mode,
                    ),
                    null,
                    0,
                )
            encounter.greenBall = null
        } else {
            world.projAnim(
                holder,
                receiver,
                SpotanimType(TobAssets.VERZIK_POWERBLAST_PROJECTILE),
                "projanim.magic_spell",
            )
            ball.holder = receiver.uid
            ball.bounces++
            ball.landsAt = clock.cycle + VerzikNormalRules.GREEN_BALL_TRAVEL_TICKS
            receiver.mes("Pass Verzik's green ball to an adjacent player!")
        }
    }

    private fun spawnVerzikTornados(raid: TobRaidSession, encounter: VerzikEncounter) {
        activePlayers(raid).forEachIndexed { index, target ->
            val npc =
                spawnAround(
                    raid,
                    TobNpcs.verzikTornado(raid.mode),
                    TobNpcRole.HAZARD,
                    index,
                    activePlayers(raid).size,
                    7,
                )
            npc.spotanim(TobAssets.VERZIK_TORNADO_SPOTANIM)
            encounter.tornados[npc] = VerzikTornadoRuntime(target.uid)
        }
    }

    private fun collapseVerzikPillar(
        raid: TobRaidSession,
        encounter: VerzikEncounter,
        pillar: VerzikPillarRuntime,
        announce: Boolean = true,
    ) {
        encounter.pillars.remove(pillar.npc)
        locs.del(pillar.visual, Int.MAX_VALUE)
        pillar.npc.anim(TobAssets.VERZIK_PILLAR_COLLAPSE)
        activePlayers(raid)
            .filter { it.coords.chebyshevDistance(pillar.npc.coords) < 2 }
            .forEach {
                queueSpecialHit(it, pillar.npc, TobScaling.maxHit(random.of(50..70), raid.mode), 3)
            }
        hazardExpiry[pillar.npc] = clock.cycle + 3
        if (announce) message(raid.party, "A supporting pillar collapses!")
    }

    private fun handleVerzikAddDeath(raid: TobRaidSession, npc: Npc) {
        val encounter = raid.encounter as? VerzikEncounter ?: return
        val bomb = encounter.bombs.remove(npc)
        if (bomb != null) {
            damagePlayersFromVerzikBomb(raid, npc, npc.coords)
        }
        encounter.athanatos.remove(npc)
        encounter.matomenos.remove(npc)
        encounter.tornados.remove(npc)
        encounter.webs.remove(npc)?.let {
            releaseVerzikWeb(it)
            locs.del(it.visual, Int.MAX_VALUE)
        }
    }

    private fun handleVerzikDeath(raid: TobRaidSession, hero: Player) {
        val encounter = raid.encounter as? VerzikEncounter ?: return
        val state = encounter.state
        if (state.phase == VerzikPhase.THREE) {
            state.beginTransition()
            clearVerzikPhaseArtifacts(raid, encounter)
            val death =
                spawnNpc(
                    raid,
                    TobNpcs.verzikDeath(raid.mode),
                    verzikRoomCenter(raid),
                    TobNpcRole.VERZIK_TRANSITION,
                )
            death.ignoreCombatInteractions = true
            death.movementLocked = true
            death.anim(TobAssets.VERZIK_PHASE_THREE_DEATH, priority = 254)
            encounter.deathSequence = VerzikDeathRuntime(death, hero.uid, clock.cycle + 8)
            raid.nextMechanicAt = Int.MAX_VALUE
            return
        }
        state.beginTransition()
        clearVerzikPhaseArtifacts(raid, encounter)
        val target =
            when (state.phase) {
                VerzikPhase.ONE -> {
                    cleanupDawnbringerForRaid(raid)
                    encounter.pillars.values.toList().forEach {
                        collapseVerzikPillar(raid, encounter, it, announce = false)
                    }
                    message(raid.party, "The remaining supporting pillars collapse!")
                    VerzikTransitionTarget.PHASE_TWO
                }
                VerzikPhase.TWO -> VerzikTransitionTarget.PHASE_THREE
                VerzikPhase.THREE,
                VerzikPhase.DEFEATED -> return
            }
        beginVerzikTransition(raid, encounter, target)
    }

    private fun tickVerzikDeathSequence(raid: TobRaidSession, encounter: VerzikEncounter): Boolean {
        val death = encounter.deathSequence ?: return false
        if (clock.cycle < death.completesAt) return true
        encounter.deathSequence = null
        if (death.npc.isSlotAssigned) removeRaidNpc(raid, death.npc)
        check(encounter.state.completeTransition())
        completeRoom(raid, death.hero.resolve(players))
        return true
    }

    private fun beginVerzikTransition(
        raid: TobRaidSession,
        encounter: VerzikEncounter,
        target: VerzikTransitionTarget,
    ) {
        val phaseTwo = target == VerzikTransitionTarget.PHASE_TWO
        if (phaseTwo) setVerzikThroneVisible(raid, true)
        val start = if (phaseTwo) roomCoord(raid, TobRoom.VERZIK, true) else verzikRoomCenter(raid)
        val transitionId = TobNpcs.verzikTransitions(raid.mode)[if (phaseTwo) 0 else 1]
        val transitionNpc = spawnNpc(raid, transitionId, start, TobNpcRole.VERZIK_TRANSITION)
        transitionNpc.ignoreCombatInteractions = true
        transitionNpc.movementLocked = true
        transitionNpc.anim(
            if (phaseTwo) TobAssets.VERZIK_PHASE_TWO_SPAWN else TobAssets.VERZIK_PHASE_THREE_SPAWN,
            priority = 254,
        )
        if (phaseTwo) {
            PathingEntityCommon.exactMove(
                transitionNpc,
                start,
                verzikRoomCenter(raid),
                delay1 = 0,
                delay2 = VerzikNormalRules.PHASE_TWO_TRANSITION_CLIENT_CYCLES,
                dir = constants.em_face_south,
                collision = collision,
            )
        }
        encounter.transition =
            VerzikTransitionRuntime(
                transitionNpc,
                target,
                clock.cycle + if (phaseTwo) VerzikNormalRules.PHASE_TWO_TRANSITION_TICKS else 7,
            )
        raid.nextMechanicAt = Int.MAX_VALUE
    }

    private fun tickVerzikTransition(raid: TobRaidSession, encounter: VerzikEncounter): Boolean {
        val transition = encounter.transition ?: return false
        val phaseTwo = transition.target == VerzikTransitionTarget.PHASE_TWO
        if (clock.cycle < transition.completesAt) return true
        val transformed = transition.npc
        if (!transformed.isSlotAssigned) {
            encounter.transition = null
            return true
        }
        encounter.state.completeTransition()
        val phaseIndex = if (phaseTwo) 1 else 2
        val activeId = TobNpcs.verzikPhases(raid.mode)[phaseIndex]
        val activeType =
            requireNotNull(ServerCacheManager.getNpc(activeId)) {
                "Missing Verzik phase ${phaseIndex + 1} npc"
            }
        // Transmog changes only the visual type; it keeps the transition NPC's avatar size and
        // collision footprint. Replace it with the real combat NPC, but keep the transition's
        // canonical carpet anchor. The phase models are cache-aligned and must not be recentered
        // based on their different server sizes.
        val activeCoords =
            if (phaseTwo) verzikPhaseTwoActiveAnchor(transformed.coords) else transformed.coords
        removeRaidNpc(raid, transformed)
        val active = spawnNpc(raid, activeId, activeCoords, TobNpcRole.VERZIK_PHASE)
        active.copyStats(activeType)
        active.mode = activeType.defaultMode
        val baseHitpoints = active.baseHitpointsLvl.coerceAtLeast(100)
        val partySize = raid.participants.values.count { it.entered }.coerceAtLeast(1)
        val hp =
            if (raid.mode == TobMode.NORMAL) {
                VerzikNormalRules.scaledHitpoints(baseHitpoints, partySize)
            } else {
                TobScaling.hitpoints(baseHitpoints, partySize, raid.mode, raid.currentRoom.room)
            }
        active.baseHitpointsLvl = hp
        active.hitpoints = hp
        active.apRangeOverride = 32
        active.ignoreCombatInteractions = false
        active.movementLocked = phaseTwo
        nearestActive(raid, active.coords)?.let { active.apPlayer2(it, interactions) }
        encounter.transition = null
        encounter.attackCounter = 0
        encounter.nextBombAt = if (phaseTwo) clock.cycle + 1 else Int.MAX_VALUE
        encounter.spawnAthanatosAfter = -1
        raid.nextMechanicAt =
            clock.cycle +
                if (phaseTwo) VerzikNormalRules.PHASE_TWO_ATTACK_INTERVAL
                else VerzikNormalRules.PHASE_THREE_ATTACK_INTERVAL
        message(raid.party, "Verzik transforms into phase ${phaseIndex + 1}.")
        return true
    }

    private fun clearVerzikPhaseArtifacts(raid: TobRaidSession, encounter: VerzikEncounter) {
        encounter.webs.values.forEach {
            releaseVerzikWeb(it)
            locs.del(it.visual, Int.MAX_VALUE)
        }
        val removals =
            linkedSetOf<Npc>().apply {
                addAll(encounter.bombs.keys)
                addAll(encounter.athanatos.keys)
                addAll(encounter.matomenos.keys)
                addAll(encounter.webs.keys)
                addAll(encounter.tornados.keys)
            }
        removals.filter(Npc::isSlotAssigned).forEach { removeRaidNpc(raid, it) }
        encounter.bombs.clear()
        encounter.athanatos.clear()
        encounter.pendingAthanatos.clear()
        encounter.matomenos.clear()
        encounter.webs.clear()
        encounter.tornados.clear()
        encounter.pendingHits.clear()
        encounter.pendingTileHits.clear()
        encounter.pendingPillarHits.clear()
        encounter.yellowPools.clear()
        encounter.yellowStartedAt = -1
        encounter.bounceCooldown.clear()
        encounter.summonHealWarned.clear()
        encounter.primaryTank = null
        encounter.webSpecialEndsAt = -1
        encounter.webVolleysRemaining = 0
        encounter.webVolleyTarget = null
        encounter.greenBall = null
    }

    private fun completeRoom(raid: TobRaidSession, hero: Player?) {
        if (raid.currentRoom.state == TobRoomState.COMPLETE) return
        raid.currentRoom.state = TobRoomState.COMPLETE
        raid.currentRoom.completedAt = clock.cycle
        if (raid.currentRoom.room == TobRoom.VERZIK) {
            cleanupDawnbringerForRaid(raid)
            beginVerzikTreasurePassageReveal(raid)
        }
        clearRaidNpcs(raid)
        val fallen =
            raid.participants.values.filter(TobParticipant::spectating).mapTo(hashSetOf()) {
                it.uid
            }
        TobScoreRules.awardRoom(raid, hero?.let { raid.participant(it.uid) })
        if (raid.currentRoom.room in setOf(TobRoom.BLOAT, TobRoom.SOTETSEG))
            supplies.awardPoints(raid)
        forEachOnline(raid) {
            prepare(it)
            it.disablePrayers()
            raid.participant(it.uid)?.spectating = false
            if (it.uid in fallen) {
                PathingEntityCommon.telejump(
                    it,
                    collision,
                    completionCoord(raid, raid.currentRoom.room),
                )
            }
            it.mes("${raid.currentRoom.room.displayName} is complete. The way forward is open.")
        }
    }

    private fun beginVerzikTreasurePassageReveal(raid: TobRaidSession) {
        // The cache-map empty throne and 32738 both include a chair. Hide the map morph before
        // animating the separate 32737 state so completion leaves exactly one final chair.
        setVerzikThroneVisible(raid, false)

        val current =
            findVerzikThroneState(raid)
                ?: run {
                    ensureVerzikEmptyThrone(raid)
                    findVerzikThroneState(raid)
                }
                ?: run {
                    log.error { "[ToB] missing Verzik throne state raid=${raid.instance.id}" }
                    return
                }
        val transforming =
            if (current.id == TobLocs.VERZIK_THRONE_TRANSFORMING) {
                current
            } else {
                val definition =
                    requireNotNull(ServerCacheManager.getObject(TobLocs.VERZIK_THRONE_TRANSFORMING))
                locs.del(current, Int.MAX_VALUE)
                locs.add(
                    current.coords,
                    definition.internalName,
                    Int.MAX_VALUE,
                    current.angle,
                    current.shape,
                )
            }
        world.locAnim(
            transforming,
            requireNotNull(RSCM.getReverseMapping(RSCMType.SEQ, TobAssets.VERZIK_THRONE_TRANSFORM)),
        )
        pendingVerzikTreasurePassages[raid.instance.id] =
            VerzikTreasurePassageRuntime(
                anchor = verzikTreasurePassageAnchor(transforming.coords),
                angle = transforming.angle,
                shape = transforming.shape,
                opensAt = clock.cycle + VerzikNormalRules.THRONE_OPEN_TICKS,
            )
    }

    private fun findVerzikThroneState(raid: TobRaidSession): LocInfo? {
        val expected =
            roomCoord(
                raid,
                TobRoom.VERZIK,
                TobLocs.VERZIK_THRONE_LOCAL_X,
                TobLocs.VERZIK_THRONE_LOCAL_Z,
            )
        val throneStates =
            setOf(
                TobLocs.VERZIK_THRONE_EMPTY,
                TobLocs.VERZIK_THRONE_TRANSFORMING,
                TobLocs.TREASURE_ROOM,
            )
        return locs.findAll(expected).firstOrNull { it.id in throneStates }
    }

    private fun ensureVerzikEmptyThrone(raid: TobRaidSession) {
        if (findVerzikThroneState(raid) != null) return
        val tile =
            roomCoord(
                raid,
                TobRoom.VERZIK,
                TobLocs.VERZIK_THRONE_LOCAL_X,
                TobLocs.VERZIK_THRONE_LOCAL_Z,
            )
        val definition = requireNotNull(ServerCacheManager.getObject(TobLocs.VERZIK_THRONE_EMPTY))
        locs.add(
            tile,
            definition.internalName,
            Int.MAX_VALUE,
            LocAngle.North,
            LocShape.CentrepieceStraight,
        )
        log.warn {
            "[ToB] restored missing empty Verzik throne raid=${raid.instance.id} tile=$tile"
        }
    }

    private fun openVerzikTreasurePassage(
        raid: TobRaidSession,
        pending: VerzikTreasurePassageRuntime,
    ) {
        val treasureRoom = requireNotNull(ServerCacheManager.getObject(TobLocs.TREASURE_ROOM))
        locs
            .findAll(pending.anchor)
            .filter {
                it.id in
                    setOf(
                        TobLocs.VERZIK_THRONE_EMPTY,
                        TobLocs.VERZIK_THRONE_TRANSFORMING,
                        TobLocs.TREASURE_ROOM,
                    )
            }
            .forEach { locs.del(it, Int.MAX_VALUE) }
        locs.add(
            pending.anchor,
            treasureRoom.internalName,
            Int.MAX_VALUE,
            pending.angle,
            pending.shape,
        )
        if (locs.findAll(pending.anchor).none { it.id == TobLocs.TREASURE_ROOM }) {
            log.error {
                "[ToB] failed to reveal Verzik treasure passage raid=${raid.instance.id} " +
                    "anchor=${pending.anchor}"
            }
        }
    }

    private fun spawnTreasureChests(raid: TobRaidSession) {
        clearTreasureChests(raid)
        val placements =
            listOf(
                TreasureChestPlacement(33086, 40, 35, LocAngle.North),
                TreasureChestPlacement(33087, 40, 40, LocAngle.North),
                TreasureChestPlacement(33088, 26, 39, LocAngle.South),
                TreasureChestPlacement(33089, 26, 35, LocAngle.South),
                TreasureChestPlacement(33090, 33, 43, LocAngle.West),
            )
        val owners = linkedMapOf<PlayerUid, CoordGrid>()
        val ownerLocIds = linkedMapOf<PlayerUid, Int>()
        val visuals = mutableListOf<LocInfo>()
        raid.participants.values
            .filter(TobParticipant::entered)
            .take(placements.size)
            .forEachIndexed { index, participant ->
                val placement = placements[index]
                val tile = roomCoord(raid, TobRoom.TREASURE, placement.localX, placement.localZ)
                val definition = requireNotNull(ServerCacheManager.getObject(placement.id))
                locs
                    .findAll(tile)
                    .filter { it.id in TobLocs.rewardChests }
                    .forEach { locs.del(it, Int.MAX_VALUE) }
                visuals +=
                    locs.add(
                        tile,
                        definition.internalName,
                        Int.MAX_VALUE,
                        placement.angle,
                        LocShape.CentrepieceStraight,
                    )
                owners[participant.uid] = tile
                ownerLocIds[participant.uid] = placement.id
            }
        rewardChestTiles[raid.instance.id] = owners
        rewardChestLocs[raid.instance.id] = visuals
        rewardChestLocIds[raid.instance.id] = ownerLocIds
        forEachOnline(raid) { syncTreasureChestStates(it, raid) }
        log.info { "[ToB] spawned ${visuals.size} reward chest(s) raid=${raid.instance.id}" }
    }

    private fun syncTreasureChestStates(player: Player, raid: TobRaidSession) {
        val locIdsByOwner = rewardChestLocIds[raid.instance.id]
        if (locIdsByOwner == null) {
            resetTreasureChestStates(player)
            return
        }
        val ownersByLocId = locIdsByOwner.entries.associate { (uid, locId) -> locId to uid }
        TobLocs.rewardChestVarbits.forEach { (locId, varbitId) ->
            val ownerUid = ownersByLocId[locId]
            val unique = ownerUid?.let(raid::participant)?.pendingReward?.unique == true
            val state =
                if (ownerUid == null) 0
                else tobRewardChestState(isOwner = ownerUid == player.uid, isUnique = unique)
            setTreasureChestState(player, varbitId, state)
        }
    }

    private fun resetTreasureChestStates(player: Player) {
        TobLocs.rewardChestVarbits.values.forEach { setTreasureChestState(player, it, 0) }
    }

    private fun setTreasureChestState(player: Player, varbitId: Int, state: Int) {
        val type =
            requireNotNull(ServerCacheManager.getVarbit(varbitId)) {
                "Missing ToB monumental chest varbit $varbitId"
            }
        VarPlayerIntMapSetter.set(player, type, state)
    }

    private fun clearTreasureChests(raid: TobRaidSession) {
        rewardChestLocs.remove(raid.instance.id)?.forEach { loc ->
            if (locs.findAll(loc.coords).any { it == loc }) locs.del(loc, Int.MAX_VALUE)
        }
        rewardChestTiles.remove(raid.instance.id)
        rewardChestLocIds.remove(raid.instance.id)
    }

    private fun resetEntryRoom(raid: TobRaidSession) {
        clearRaidNpcs(raid)
        raid.participants.values.forEach { it.spectating = false }
        activateRoom(raid, raid.currentRoomIndex)
        forEachOnline(raid) {
            resetCombat(it)
            PathingEntityCommon.telejump(it, collision, roomCoord(raid, raid.currentRoom.room))
            it.mes("Entry Mode resets the room after the party wipe.")
        }
    }

    private fun recordCompletion(raid: TobRaidSession) {
        if (raid.completedAt != null) return
        raid.completedAt = clock.cycle
        val elapsed = clock.cycle - raid.startedAt
        forEachOnline(raid) { player ->
            val countKey =
                when (raid.mode) {
                    TobMode.ENTRY -> TOB_ENTRY_COMPLETIONS
                    TobMode.NORMAL -> TOB_NORMAL_COMPLETIONS
                    TobMode.HARD -> TOB_HARD_COMPLETIONS
                }
            val bestKey =
                when (raid.mode) {
                    TobMode.ENTRY -> TOB_ENTRY_BEST
                    TobMode.NORMAL -> TOB_NORMAL_BEST
                    TobMode.HARD -> TOB_HARD_BEST
                }
            player.attr[countKey] = player.attr.getOrDefault(countKey, 0) + 1
            val old = player.attr[bestKey]
            if (old == null || elapsed < old) player.attr[bestKey] = elapsed
            player.mes(
                "Theatre complete in ${elapsed / 100}:${((elapsed % 100) * 3 / 5).toString().padStart(2, '0')}. Search your monumental chest."
            )
        }
        log.info {
            "[ToB] complete party=${raid.party.id} instance=${raid.instance.id} mode=${raid.mode} ticks=$elapsed score=${raid.participants.values.sumOf(TobParticipant::score)}"
        }
    }

    private fun addMaidenBloodPool(state: MaidenState, tile: CoordGrid) {
        state.addPool(tile, clock.cycle + 10)
        world.spotanimMap(SpotanimType(TobAssets.MAIDEN_BLOOD_POOL), tile)
    }

    private fun launchTileProjectile(source: Npc, tile: CoordGrid, graphic: Int) {
        if (source.coords.level != tile.level) return
        world.projAnim(source, tile, SpotanimType(graphic), "projanim.magic_spell")
    }

    private fun spawnMaidenMatomenos(raid: TobRaidSession, state: MaidenState, maiden: Npc) {
        val spawnTiles =
            listOf(
                45 to 18,
                46 to 20,
                47 to 22,
                48 to 19,
                46 to 21,
                45 to 40,
                46 to 42,
                47 to 43,
                48 to 40,
                46 to 41,
            )
        val count = activePlayers(raid).size.coerceAtLeast(1) * 2
        repeat(count) { index ->
            val local = spawnTiles[(index + random.of(spawnTiles.indices)) % spawnTiles.size]
            val crab =
                spawnNpc(
                    raid,
                    TobNpcs.maidenMatomenos(raid.mode),
                    roomCoord(raid, TobRoom.MAIDEN, local.first, local.second),
                    TobNpcRole.MAIDEN_ADD,
                )
            val hitpoints =
                when {
                    raid.mode == TobMode.ENTRY -> 75
                    raid.participants.size >= 5 -> 200
                    raid.participants.size == 4 -> 175
                    else -> 150
                }
            crab.baseHitpointsLvl = hitpoints
            crab.hitpoints = hitpoints
            crab.ignoreCombatInteractions = true
            crab.walkTo(routeFactory, maiden.coords, passThroughEntities = true)
        }
    }

    private fun processMaidenMatomenos(raid: TobRaidSession, state: MaidenState, maiden: Npc) {
        val matomenos =
            raid.spawnedNpcs.filter { raid.npcRoles[it] == TobNpcRole.MAIDEN_ADD }.toList()
        for (crab in matomenos) {
            if (!crab.isSlotAssigned || crab.hitpoints <= 0) continue
            crab.ignoreCombatInteractions = true
            if (
                raid.owns(maiden) &&
                    maiden.hitpoints > 0 &&
                    crab.coords.chebyshevDistance(maiden.coords) <= maiden.visType.size
            ) {
                maiden.heal(maidenMatomenosHealAmount(crab.hitpoints), showHitsplat = true)
                state.empower()
                removeRaidNpc(raid, crab)
                continue
            }
            if (crab.routeDestination.isEmpty()) {
                crab.walkTo(routeFactory, maiden.coords, passThroughEntities = true)
            }
        }
    }

    private fun bloatCorners(raid: TobRaidSession): List<CoordGrid> =
        listOf(
            roomCoord(raid, TobRoom.BLOAT, 25, 25),
            roomCoord(raid, TobRoom.BLOAT, 25, 35),
            roomCoord(raid, TobRoom.BLOAT, 35, 35),
            roomCoord(raid, TobRoom.BLOAT, 35, 25),
        )

    private fun spawnNylocasPillars(raid: TobRaidSession, state: NylocasWaveState) {
        val tiles = listOf(26 to 19, 26 to 28, 35 to 28, 35 to 19)
        tiles.forEach { (x, z) ->
            val pillar =
                spawnNpc(
                    raid,
                    TobNpcs.supportingPillar(raid.mode),
                    roomCoord(raid, TobRoom.NYLOCAS, x, z),
                    TobNpcRole.PILLAR,
                )
            pillar.baseHitpointsLvl = 100
            pillar.hitpoints = 100
            pillar.movementLocked = true
            state.pillars += pillar
        }
    }

    private fun routeNylocasToPillar(state: NylocasWaveState, nylocas: Npc, requestedIndex: Int) {
        val pillar = state.pillars.getOrNull(requestedIndex) ?: return
        state.pillarTargets[nylocas] = requestedIndex
        if (
            squareChebyshevDistance(nylocas.coords, nylocas.size, pillar.coords, pillar.size) <= 1
        ) {
            nylocas.movementLocked = true
            nylocas.abortRoute()
            nylocas.faceNpc(pillar)
            return
        }
        nylocas.movementLocked = false
        val approach =
            nylocasPillarApproach(nylocas.coords, nylocas.size, pillar.coords, pillar.size)
        nylocas.walkTo(routeFactory, approach, passThroughEntities = false)
    }

    private fun processNylocasPillarRoutes(raid: TobRaidSession, state: NylocasWaveState) {
        state.pillarTargets.entries.toList().forEach { (nylocas, targetIndex) ->
            if (raid.npcRoles[nylocas] != TobNpcRole.NYLOCAS_WAVE) {
                state.pillarTargets.remove(nylocas)
                return@forEach
            }
            val pillar = state.pillars.getOrNull(targetIndex) ?: return@forEach
            val arrived =
                squareChebyshevDistance(nylocas.coords, nylocas.size, pillar.coords, pillar.size) <=
                    1
            nylocas.movementLocked = arrived
            if (arrived) {
                nylocas.abortRoute()
                nylocas.faceNpc(pillar)
                if (clock.cycle >= state.pillarAttackAt.getOrDefault(nylocas, 0)) {
                    nylocas.anim(nylocasAttackAnimation(nylocas.id, raid.mode))
                    showZeroDamageHitmark(pillar)
                    state.pillarAttackAt[nylocas] = clock.cycle + 4
                }
            } else if (nylocas.routeDestination.isEmpty()) {
                routeNylocasToPillar(state, nylocas, targetIndex)
            }
        }
    }

    private fun nylocasAttackAnimation(id: Int, mode: TobMode): Int =
        when (TobNpcs.nylocasStyleIndex(id, mode) ?: 0) {
            1 -> TobAssets.NYLOCAS_RANGED_CAST
            2 -> TobAssets.NYLOCAS_MAGIC_CAST
            else -> TobAssets.NYLOCAS_MELEE_ATTACK
        }

    private fun showZeroDamageHitmark(npc: Npc) {
        val id = hitmark_groups.zero_damage.lit.asRSCM(RSCMType.HITMARK)
        npc.showHitmark(
            Hitmark.fromNoSource(self = id, source = id, public = id, damage = 0, delay = 0)
        )
    }

    private fun splitNylocas(raid: TobRaidSession, parent: Npc) {
        if (raid.currentRoom.room != TobRoom.NYLOCAS) return
        val state = raid.encounter as? NylocasWaveState ?: return
        val childId =
            TobNpcs.nylocasSplitStyle(parent.id, raid.mode)
                ?: run {
                    state.pillarTargets.remove(parent)
                    state.pillarAttackAt.remove(parent)
                    return
                }
        val targetIndex = state.pillarTargets.remove(parent) ?: 0
        state.pillarAttackAt.remove(parent)
        repeat(2) { index ->
            val tile = parent.coords.translate(index, 0)
            val child = spawnNpc(raid, childId, tile, TobNpcRole.NYLOCAS_WAVE)
            state.pillarTargets[child] = targetIndex
            routeNylocasToPillar(state, child, targetIndex)
        }
    }

    private fun removeRaidNpc(raid: TobRaidSession, npc: Npc) {
        hazardExpiry.remove(npc)
        raid.npcRoles.remove(npc)
        raid.spawnedNpcs.remove(npc)
        if (npc.isSlotAssigned) npcs.del(npc, Int.MAX_VALUE)
    }

    private fun spawnBoss(raid: TobRaidSession, id: Int, role: TobNpcRole): Npc =
        spawnBossAt(raid, id, role, roomCoord(raid, raid.currentRoom.room, true))

    private fun spawnBossAt(raid: TobRaidSession, id: Int, role: TobNpcRole, tile: CoordGrid): Npc =
        spawnNpc(raid, id, tile, role).also { npc ->
            val hp =
                TobScaling.hitpoints(
                    npc.baseHitpointsLvl.coerceAtLeast(100),
                    raid.participants.size,
                    raid.mode,
                    raid.currentRoom.room,
                )
            npc.baseHitpointsLvl = hp
            npc.hitpoints = hp
            npc.apRangeOverride = 32
            nearestActive(raid, npc.coords)?.let { npc.apPlayer2(it, interactions) }
        }

    private fun spawnVerzikPillars(raid: TobRaidSession, encounter: VerzikEncounter) {
        data class PillarPlacement(val x: Int, val z: Int, val safeX: IntRange, val safeZ: Int)
        val placements =
            listOf(
                PillarPlacement(25, 30, 25..26, 29),
                PillarPlacement(25, 24, 25..26, 23),
                PillarPlacement(25, 18, 25..26, 17),
                PillarPlacement(37, 30, 38..39, 29),
                PillarPlacement(37, 24, 38..39, 23),
                PillarPlacement(37, 18, 38..39, 17),
            )
        placements.forEach { placement ->
            val tile = roomCoord(raid, TobRoom.VERZIK, placement.x, placement.z)
            val visual =
                locs.add(
                    tile,
                    requireNotNull(ServerCacheManager.getObject(TobLocs.VERZIK_PILLAR)),
                    Int.MAX_VALUE,
                    LocAngle.North,
                    LocShape.CentrepieceStraight,
                )
            val npc = spawnNpc(raid, TobNpcs.supportingPillar(raid.mode), tile, TobNpcRole.PILLAR)
            val hitpoints = if (raid.mode == TobMode.ENTRY) 200 else 185
            npc.movementLocked = true
            npc.baseHitpointsLvl = hitpoints
            npc.hitpoints = hitpoints
            val safeTiles =
                placement.safeX.mapTo(linkedSetOf()) { localX ->
                    roomCoord(raid, TobRoom.VERZIK, localX, placement.safeZ)
                }
            encounter.pillars[npc] = VerzikPillarRuntime(npc, visual, safeTiles, npc.hitpoints)
        }
    }

    private fun prepareVerzikRoom(raid: TobRaidSession) {
        val existing = raid.encounter as? VerzikEncounter
        if (
            existing != null && raid.spawnedNpcs.any { raid.npcRoles[it] == TobNpcRole.VERZIK_HOST }
        )
            return
        clearRaidNpcs(raid)
        val encounter = VerzikEncounter(VerzikState(raid.mode))
        raid.encounter = encounter
        spawnVerzikPillars(raid, encounter)
        ensureVerzikEmptyThrone(raid)
        setVerzikThroneVisible(raid, false)
        val host =
            spawnNpc(
                raid,
                TobNpcs.verzikInitial(raid.mode),
                roomCoord(raid, TobRoom.VERZIK, true),
                TobNpcRole.VERZIK_HOST,
            )
        host.respawnDir = Direction.South
        host.mode = null
        host.movementLocked = true
        host.ignoreCombatInteractions = true
        host.lockFacingDirection(Direction.South)
    }

    private fun spawnAround(
        raid: TobRaidSession,
        id: Int,
        role: TobNpcRole,
        index: Int,
        count: Int,
        radius: Int,
    ): Npc {
        val center = roomCoord(raid, raid.currentRoom.room, true)
        val points =
            arrayOf(
                -radius to -radius,
                radius to -radius,
                radius to radius,
                -radius to radius,
                0 to -radius,
                radius to 0,
                0 to radius,
                -radius to 0,
            )
        val offset = points[index % points.size]
        return spawnNpc(
                raid,
                id,
                CoordGrid(center.x + offset.first, center.z + offset.second, center.level),
                role,
            )
            .also {
                nearestActive(raid, it.coords)?.let { player -> it.apPlayer2(player, interactions) }
            }
    }

    private fun spawnNpc(raid: TobRaidSession, id: Int, tile: CoordGrid, role: TobNpcRole): Npc {
        val type = requireNotNull(ServerCacheManager.getNpc(id)) { "Missing ToB npc $id" }
        return Npc(type, tile).also { npc ->
            npc.mode = type.defaultMode
            npcs.add(npc, Int.MAX_VALUE)
            // NpcRepository marks Int.MAX_VALUE spawns as permanent/respawning. Theatre NPCs are
            // owned by one raid instance and must never return after death or room completion.
            npc.respawns = false
            // Theatre rewards are prepared and claimed from the raid's monumental chest. Never
            // let the global NPC drop pipeline add bones or lore-book records to boss-room floors.
            // In particular, Verzik must not leave "Verzik vitur - patient record" after phase 3.
            npc.suppressRewards = true
            instances.attachNpc(raid.instance.id, npc)
            raid.spawnedNpcs += npc
            raid.npcRoles[npc] = role
        }
    }

    private fun boss(raid: TobRaidSession): Npc? =
        raid.npcRoles.entries
            .firstOrNull { it.value in setOf(TobNpcRole.BOSS, TobNpcRole.VERZIK_PHASE) }
            ?.key

    private fun verzikRoomCenter(raid: TobRaidSession): CoordGrid =
        roomCoord(raid, TobRoom.VERZIK, 31, 23)

    private fun roomCoord(raid: TobRaidSession, room: TobRoom, boss: Boolean = false): CoordGrid =
        requireNotNull(instances.localCoord(raid.instance, TobInstanceLayout.local(room, boss))) {
            "Missing ToB room coordinate $room"
        }

    private fun roomCoord(
        raid: TobRaidSession,
        room: TobRoom,
        localX: Int,
        localZ: Int,
    ): CoordGrid =
        requireNotNull(
            instances.localCoord(raid.instance, TobInstanceLayout.local(room, localX, localZ))
        )

    private fun completionCoord(raid: TobRaidSession, room: TobRoom): CoordGrid =
        requireNotNull(instances.localCoord(raid.instance, TobInstanceLayout.completion(room))) {
            "Missing ToB completion coordinate $room"
        }

    private fun spectatorCoord(raid: TobRaidSession, room: TobRoom): CoordGrid =
        requireNotNull(instances.localCoord(raid.instance, TobInstanceLayout.spectator(room))) {
            "Missing ToB spectator coordinate $room"
        }

    private fun queueSpecialHit(player: Player, source: Npc, damage: Int, delay: Int) =
        if (canQueueTobSpecialHit(source.isSlotAssigned, source.hitpoints)) {
            player.queueHit(
                source = source,
                delay = normalizeTobSpecialHitDelay(delay),
                type = HitType.Typeless,
                damage = damage,
                modifier = NpcBypassProtectionPrayerPlayerHitModifier,
            )
        } else {
            Unit
        }

    private fun randomActive(raid: TobRaidSession): Player? =
        activePlayers(raid).takeIf { it.isNotEmpty() }?.let { it[random.of(it.indices)] }

    private fun activePlayersOnLevel(raid: TobRaidSession, level: Int): List<Player> =
        activePlayers(raid).filter { it.coords.level == level }

    private fun activePlayers(raid: TobRaidSession): List<Player> =
        raid.participants.values
            .filter(TobParticipant::entered)
            .filter(TobParticipant::connected)
            .filterNot(TobParticipant::spectating)
            .filter { it.roomIndex == raid.currentRoomIndex }
            .mapNotNull { it.uid.resolve(players) }

    private fun nearestActive(raid: TobRaidSession, tile: CoordGrid): Player? =
        activePlayersOnLevel(raid, tile.level).minByOrNull { it.coords.chebyshevDistance(tile) }

    private inline fun forEachActive(raid: TobRaidSession, action: (Player) -> Unit) =
        activePlayers(raid).forEach(action)

    private inline fun forEachOnline(raid: TobRaidSession, action: (Player) -> Unit) =
        raid.participants.values
            .filter(TobParticipant::connected)
            .mapNotNull { it.uid.resolve(players) }
            .forEach(action)

    private fun clearRaidNpcs(raid: TobRaidSession) {
        (raid.encounter as? XarpusEncounter)?.let { encounter ->
            encounter.exhumeds.values.forEach { locs.del(it.visual, Int.MAX_VALUE) }
            encounter.exhumeds.clear()
            encounter.poisonPools.clear()
            encounter.pendingPoison.clear()
        }
        (raid.encounter as? VerzikEncounter)?.let { encounter ->
            encounter.pillars.values.forEach { locs.del(it.visual, Int.MAX_VALUE) }
            encounter.webs.values.forEach { locs.del(it.visual, Int.MAX_VALUE) }
            encounter.pillars.clear()
            encounter.webs.clear()
            encounter.bombs.clear()
            encounter.athanatos.clear()
            encounter.pendingAthanatos.clear()
            encounter.matomenos.clear()
            encounter.tornados.clear()
            encounter.pendingHits.clear()
            encounter.pendingTileHits.clear()
            encounter.pendingPillarHits.clear()
            encounter.yellowPools.clear()
            encounter.yellowStartedAt = -1
            encounter.transition = null
            encounter.primaryTank = null
            encounter.webSpecialEndsAt = -1
        }
        raid.spawnedNpcs.toList().forEach {
            hazardExpiry.remove(it)
            if (it.isSlotAssigned) npcs.del(it, Int.MAX_VALUE)
        }
        raid.spawnedNpcs.clear()
        raid.npcRoles.clear()
    }

    private fun prepare(player: Player) {
        player.cancelActiveCoroutine()
        player.combatClearQueue()
        player.clearInteractionRoute()
    }

    private fun resetCombat(player: Player) {
        player.disablePrayers()
        player.statRestoreAll(COMBAT_STATS)
        specialEnergy.restoreFullSpecialEnergy(player)
        player.runEnergy = constants.run_max_energy
        UpdateRun.energy(player, player.runEnergy)
    }

    private fun leaveRaid(
        player: Player,
        raid: TobRaidSession,
        removeFromParty: Boolean,
        teleport: Boolean = true,
    ): Boolean {
        if (instances.sessionForPlayer(player)?.id == raid.instance.id)
            instances.leave(player, raid.instance, clock.cycle)
        prepare(player)
        if (teleport) PathingEntityCommon.telejump(player, collision, TOB_LOBBY)
        else player.coords = TOB_LOBBY
        participantRaids.remove(player.uid)
        raid.participant(player.uid)?.connected = false
        cleanupRaidItems(player)
        ui.close(player)
        if (removeFromParty) {
            raid.party.members.remove(player.uid)
            membership.remove(player.uid)
        }
        if (raid.party.members.isEmpty()) abort(raid, "party empty")
        else if (raid.party.leader == player.uid) transferOrDisband(raid.party)
        return true
    }

    private fun abort(raid: TobRaidSession, reason: String) {
        if (raid.cleanupStarted) return
        raid.cleanupStarted = true
        raid.state = TobRaidState.ABANDONED
        cleanupDawnbringerForRaid(raid)
        clearTreasureChests(raid)
        clearRaidNpcs(raid)
        forEachOnline(raid) { player ->
            if (instances.sessionForPlayer(player)?.id == raid.instance.id)
                instances.leave(player, raid.instance, clock.cycle)
            prepare(player)
            PathingEntityCommon.telejump(player, collision, TOB_LOBBY)
            cleanupRaidItems(player)
            ui.close(player)
            player.mes("Your Theatre has ended: $reason.")
        }
        raid.participants.keys.forEach(participantRaids::remove)
        raids.remove(raid.instance.id)
        raid.party.activeSession = null
        pendingAborts.remove(raid.instance.id)
        pendingEntryResets.remove(raid.instance.id)
        pendingVerzikTreasurePassages.remove(raid.instance.id)
        instances.terminate(raid.instance)
        log.info {
            "[ToB] abort party=${raid.party.id} instance=${raid.instance.id} reason=$reason"
        }
    }

    private fun cleanupRaidItems(player: Player) {
        supplies.cleanup(player)
        removeDawnbringer(player)
        setVerzikThroneVisible(player, false)
        resetTreasureChestStates(player)
    }

    private fun syncVerzikThroneVisibility(player: Player, raid: TobRaidSession) {
        val encounter = raid.encounter as? VerzikEncounter
        setVerzikThroneVisible(
            player,
            encounter != null && isVerzikEmptyThroneVisible(encounter.state.stage),
        )
    }

    private fun setVerzikThroneVisible(raid: TobRaidSession, visible: Boolean) {
        forEachOnline(raid) { setVerzikThroneVisible(it, visible) }
    }

    private fun setVerzikThroneVisible(player: Player, visible: Boolean) {
        val type =
            requireNotNull(ServerCacheManager.getVarbit(TobLocs.VERZIK_THRONE_VISIBLE_VARBIT)) {
                "Missing Verzik throne visibility varbit ${TobLocs.VERZIK_THRONE_VISIBLE_VARBIT}"
            }
        VarPlayerIntMapSetter.set(player, type, if (visible) 1 else 0)
    }

    private fun removeDawnbringer(player: Player) {
        player.invDel(player.inv, TobItems.DAWNBRINGER, Int.MAX_VALUE, strict = false)
        player.invDel(player.worn, TobItems.DAWNBRINGER, Int.MAX_VALUE, strict = false)
    }

    private fun playerHasDawnbringer(player: Player): Boolean =
        player.inv.any { it?.id == TobItems.DAWNBRINGER } ||
            player.worn.any { it?.id == TobItems.DAWNBRINGER }

    private fun partyHasDawnbringer(raid: TobRaidSession): Boolean =
        raid.participants.values
            .filter(TobParticipant::connected)
            .mapNotNull { it.uid.resolve(players) }
            .any(::playerHasDawnbringer) || dawnbringersOnFloor(raid).isNotEmpty()

    private fun dawnbringersOnFloor(raid: TobRaidSession): List<org.rsmod.game.obj.Obj> =
        buildList {
            for (room in listOf(TobRoom.XARPUS, TobRoom.VERZIK)) {
                for (localX in 0..63) {
                    for (localZ in 0..63) {
                        val tile = roomCoord(raid, room, localX, localZ)
                        objects.findAll(tile).filterTo(this) { it.type == TobItems.DAWNBRINGER }
                    }
                }
            }
        }

    private fun cleanupDawnbringerForRaid(raid: TobRaidSession) {
        raid.participants.values.mapNotNull { it.uid.resolve(players) }.forEach(::removeDawnbringer)
        dawnbringersOnFloor(raid).forEach { objects.del(it, Int.MAX_VALUE) }
        raid.dawnbringerClaimed = false
    }

    private fun transferOrDisband(party: TobParty) {
        val replacement = party.members.firstOrNull()
        parties.remove(party.leader)
        invitations.entries.removeIf { it.value === party }
        if (replacement != null) {
            party.leader = replacement
            parties[replacement] = party
            message(party, "${label(replacement)} is now the party leader.")
            refreshPartyViewers(party)
        }
    }

    private fun findPlayer(name: String): Player? =
        players.firstOrNull { it.displayName.equals(name, true) || it.username.equals(name, true) }

    private fun label(uid: PlayerUid): String = uid.resolve(players)?.displayName ?: uid.toString()

    private fun message(party: TobParty, text: String) =
        party.members.forEach { it.resolve(players)?.mes(text) }

    private class SotetsegEncounter {
        val thresholds = ArrayDeque(listOf(66, 33))
        var mazeActive: Boolean = false
        var mazeStartedAt: Int = -1
        var path: List<CoordGrid> = emptyList()
        var pathTiles: Set<CoordGrid> = emptySet()
        var tornado: Npc? = null
        var tornadoIndex: Int = 0
        var tornadoWalking: Boolean = false
        val bleeds = mutableMapOf<PlayerUid, SotetsegBleed>()
        var attackCount: Int = 0
    }

    private data class SotetsegBleed(var remaining: Int, var nextHitAt: Int)

    private class XarpusEncounter(val state: XarpusState, val recoveryEndsAt: Int) {
        val exhumeds = linkedMapOf<CoordGrid, XarpusExhumed>()
        val poisonPools = linkedSetOf<CoordGrid>()
        val pendingPoison = linkedMapOf<CoordGrid, Int>()
        var spawnedExhumeds: Int = 0
        var nextExhumedAt: Int = 0
        var nextTurnAt: Int = 0
    }

    private data class XarpusExhumed(val visual: LocInfo, var remainingTicks: Int)

    private enum class VerzikTransitionTarget {
        PHASE_TWO,
        PHASE_THREE,
    }

    private data class VerzikTransitionRuntime(
        val npc: Npc,
        val target: VerzikTransitionTarget,
        val completesAt: Int,
    )

    private data class VerzikDeathRuntime(val npc: Npc, val hero: PlayerUid, val completesAt: Int)

    private data class VerzikTreasurePassageRuntime(
        val anchor: CoordGrid,
        val angle: LocAngle,
        val shape: LocShape,
        val opensAt: Int,
    )

    private class VerzikEncounter(val state: VerzikState) {
        var started: Boolean = false
        var transition: VerzikTransitionRuntime? = null
        var deathSequence: VerzikDeathRuntime? = null
        val pillars = linkedMapOf<Npc, VerzikPillarRuntime>()
        val bombs = linkedMapOf<Npc, VerzikBomb>()
        val athanatos = linkedMapOf<Npc, VerzikAthanatosRuntime>()
        val pendingAthanatos = mutableListOf<VerzikPendingAthanatos>()
        val matomenos = linkedMapOf<Npc, VerzikMatomenosRuntime>()
        val webs = linkedMapOf<Npc, VerzikWebRuntime>()
        val tornados = linkedMapOf<Npc, VerzikTornadoRuntime>()
        val pendingHits = mutableListOf<VerzikPendingHit>()
        val pendingTileHits = mutableListOf<VerzikPendingTileHit>()
        val pendingPillarHits = mutableListOf<VerzikPendingPillarHit>()
        val yellowPools = linkedSetOf<CoordGrid>()
        val bounceCooldown = mutableMapOf<PlayerUid, Int>()
        val summonHealWarned = hashSetOf<PlayerUid>()
        var yellowStartedAt: Int = -1
        var nextBombAt: Int = 0
        var spawnAthanatosAfter: Int = -1
        var attackCounter: Int = 0
        var lightningCount: Int = 0
        var phaseTwoPointFiveStarted: Boolean = false
        var healFromDamageUntil: Int = -1
        var matomenosAttacksRemaining: Int = 0
        var nextMatomenosAt: Int = -1
        var tornadosSpawned: Boolean = false
        var primaryTank: PlayerUid? = null
        var webSpecialEndsAt: Int = -1
        var greenBall: VerzikGreenBallRuntime? = null
        var webVolleysRemaining: Int = 0
        var nextWebVolleyAt: Int = -1
        var webVolleyTarget: PlayerUid? = null
    }

    private data class VerzikPillarRuntime(
        val npc: Npc,
        val visual: LocInfo,
        val safeTiles: Set<CoordGrid>,
        var health: Int,
    )

    private data class VerzikBomb(
        var target: PlayerUid,
        val expiresAt: Int,
        var explodesAt: Int = -1,
    )

    private data class VerzikPendingAthanatos(val tile: CoordGrid, val landsAt: Int)

    private data class VerzikAthanatosRuntime(
        var nextHealAt: Int,
        var poisonedAt: Int = -1,
        var poisonSource: PlayerUid? = null,
        var poisonDamage: Int = 0,
    )

    private data class VerzikMatomenosRuntime(var nextWanderAt: Int)

    private data class VerzikWebRuntime(
        val visual: LocInfo,
        val expiresAt: Int,
        val trapped: MutableSet<PlayerUid> = hashSetOf(),
    )

    private data class VerzikTornadoRuntime(val target: PlayerUid, var nextDamageAt: Int = 0)

    private data class VerzikGreenBallRuntime(
        var holder: PlayerUid,
        var bounces: Int,
        var landsAt: Int,
    )

    private data class TreasureChestPlacement(
        val id: Int,
        val localX: Int,
        val localZ: Int,
        val angle: LocAngle,
    )

    private data class VerzikPendingHit(
        val target: PlayerUid,
        val landsAt: Int,
        val type: HitType,
        val damage: Int,
        val impactGraphic: Int?,
        val prayerReduction: Int,
        val bossHealAmount: Int = 0,
        val bypassPrayer: Boolean = false,
    )

    private data class VerzikPendingTileHit(
        val target: PlayerUid,
        val tile: CoordGrid,
        val landsAt: Int,
        val type: HitType,
        val damage: Int,
        val impactGraphic: Int,
        val prayerReduction: Int,
    )

    private data class VerzikPendingPillarHit(val pillar: Npc, val landsAt: Int, val damage: Int)

    private companion object {
        private const val TOB_INVITE_PLAYER_OP_SLOT = 5
        private const val TOB_INVITE_PLAYER_OP = "Invite"

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
