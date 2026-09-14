package org.rsmod.content.minigames.toa

import java.util.UUID
import org.rsmod.api.instances.InstanceSession
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.map.CoordGrid

internal enum class ToaMode {
    ENTRY,
    NORMAL,
    EXPERT,
}

internal enum class ToaRaidState {
    FORMING,
    ACTIVE,
    WARDENS,
    REWARDS,
    ABANDONED,
    COMPLETE,
}

internal enum class ToaRoomState {
    LOCKED,
    ACTIVE,
    COMPLETE,
}

internal enum class ToaPath(val displayName: String, val challenge: ToaRoom, val boss: ToaRoom) {
    SCABARAS("Scabaras", ToaRoom.SCABARAS, ToaRoom.KEPHRI),
    APMEKEN("Apmeken", ToaRoom.APMEKEN, ToaRoom.BABA),
    CRONDIS("Crondis", ToaRoom.CRONDIS, ToaRoom.ZEBAK),
    HET("Het", ToaRoom.HET, ToaRoom.AKKHA),
}

internal val TOA_ACTIVE_PATHS: Set<ToaPath> =
    linkedSetOf(ToaPath.SCABARAS, ToaPath.APMEKEN, ToaPath.CRONDIS, ToaPath.HET)

internal fun isToaWardensUnlocked(completedPaths: Set<ToaPath>): Boolean =
    completedPaths.containsAll(TOA_ACTIVE_PATHS)

internal data class ToaTemplateSegment(
    val sourceRegion: Int,
    val sourceMaxLevel: Int,
    val destinationZoneX: Int,
    val destinationZoneZ: Int,
)

internal enum class ToaRoom(
    val displayName: String,
    val sourceRegion: Int,
    val sourceMaxLevel: Int,
    val entryLevel: Int,
    val destinationZoneX: Int,
    val destinationZoneZ: Int,
    val entryLocalX: Int,
    val entryLocalZ: Int,
    val bossLocalX: Int = entryLocalX,
    val bossLocalZ: Int = entryLocalZ,
    val fightLocalX: Int = bossLocalX,
    val fightLocalZ: Int = bossLocalZ,
    val spiritLocalX: Int = bossLocalX,
    val spiritLocalZ: Int = bossLocalZ,
) {
    LOBBY("Tombs of Amascut", 14160, 1, 0, 0, 0, 31, 42),
    SCABARAS("Path of Scabaras", 14162, 0, 0, 8, 0, 2, 31),
    KEPHRI("Kephri", 14164, 0, 0, 16, 0, 15, 32, 29, 30, 24, 32, 38, 32),
    HET("Path of Het", 14674, 0, 0, 24, 0, 50, 31),
    AKKHA("Akkha", 14676, 1, 1, 0, 8, 48, 32, 32, 32, 41, 32, 25, 31),
    APMEKEN("Path of Apmeken", 15186, 0, 0, 8, 8, 16, 31, 32, 32),
    BABA("Ba-Ba", 15188, 1, 0, 16, 8, 14, 31, 38, 32, 24, 32, 41, 32),
    CRONDIS("Path of Crondis", 15698, 0, 0, 24, 8, 50, 31, 32, 32),
    ZEBAK("Zebak", 15700, 1, 0, 0, 16, 54, 31, 16, 28, 37, 32, 24, 32),
    WARDENS(
        "The Wardens",
        15184,
        1,
        1,
        8,
        16,
        WardensEntryLifecycle.WAITING_LOCAL_X,
        WardensEntryLifecycle.WAITING_LOCAL_Z,
        23,
        34,
        WardensEntryLifecycle.FIGHT_LOCAL_X,
        WardensEntryLifecycle.FIGHT_LOCAL_Z,
    ),
    TUMEKEN_WARDEN(
        "Tumeken's Warden",
        15696,
        1,
        1,
        24,
        16,
        WardenPhaseThreeTransition.ENTRY_LOCAL_X,
        WardenPhaseThreeTransition.ENTRY_LOCAL_Z,
        30,
        32,
        32,
        37,
    ),
    REWARD(
        "Treasure Room",
        14672,
        1,
        0,
        16,
        16,
        WardenRewardTransition.ENTRY_LOCAL_X,
        WardenRewardTransition.ENTRY_LOCAL_Z,
    );

    val segment: ToaTemplateSegment
        get() = ToaTemplateSegment(sourceRegion, sourceMaxLevel, destinationZoneX, destinationZoneZ)
}

/** Packet-audited revision-240 Akkha crystal transition. */
internal object AkkhaEntryLifecycle {
    const val WAITING_LOCAL_X = 25
    const val WAITING_LOCAL_Z = 30
    const val FIGHT_LOCAL_X = 41
    const val FIGHT_LOCAL_Z = 32
    const val PLAYER_SPOTANIM = 409
    const val START_SOUND = 198
    const val MIDI = 739
    const val START_MESSAGE = "Challenge started: Akkha."
}

/**
 * Every map segment used by the level-zero route. Challenge rooms must be copied into each private
 * instance before their path object can be used; routing a player to a boss map directly skips the
 * OSRS challenge and is not an acceptable fallback.
 */
internal val TOA_ACTIVE_ROOMS: List<ToaRoom> =
    listOf(
        ToaRoom.LOBBY,
        ToaRoom.SCABARAS,
        ToaRoom.KEPHRI,
        ToaRoom.HET,
        ToaRoom.AKKHA,
        ToaRoom.APMEKEN,
        ToaRoom.BABA,
        ToaRoom.CRONDIS,
        ToaRoom.ZEBAK,
        ToaRoom.WARDENS,
        ToaRoom.TUMEKEN_WARDEN,
        ToaRoom.REWARD,
    )

internal data class ToaParticipant(
    var uid: PlayerUid,
    val accountHash: Long,
    var points: Int = ToaRewardPointRules.STARTING_POINTS,
    var deaths: Int = 0,
    var connected: Boolean = true,
    var spectating: Boolean = false,
    var rewardClaimed: Boolean = false,
    var pendingReward: ToaRewardRoll? = null,
    var currentRoom: ToaRoom = ToaRoom.LOBBY,
    var returnRoom: ToaRoom = ToaRoom.LOBBY,
    var rewardEligible: Boolean = true,
    var completionRecorded: Boolean = false,
    val tertiaryProfile: ToaTertiaryProfile = ToaTertiaryProfile(),
)

internal class ToaParty(var leader: PlayerUid) {
    val id: UUID = UUID.randomUUID()
    val members: LinkedHashSet<PlayerUid> = linkedSetOf(leader)
    val applicants: LinkedHashSet<PlayerUid> = linkedSetOf()
    val blocked: LinkedHashSet<PlayerUid> = linkedSetOf()
    val invocations = ToaInvocationSet()
    var activeSession: ToaRaidSession? = null
}

internal enum class ToaNpcRole {
    BOSS,
    CHALLENGE,
    WARDEN_PHASE,
    OBELISK,
    ADD,
    HAZARD,
    CORE,
    SKULL,
    BOULDER,
    COMPLETION_SPIRIT,
    HELPFUL_SPIRIT,
}

internal class ToaChallengeState(val path: ToaPath) {
    val activatedObjects = linkedSetOf<Int>()
    var stage = ToaChallengeStage.INACTIVE
    var entryStarter: PlayerUid? = null
    var entryDeadline = -1
    var wave = 0
    var waveAlive = 0
    var complete = false
}

internal data class ToaPendingHit(
    val target: PlayerUid,
    val tile: CoordGrid,
    val landsAt: Int,
    val damage: Int,
    val impactGraphic: Int?,
    val radius: Int = 0,
    val requireTargetOnTile: Boolean = true,
    val hitType: org.rsmod.game.hit.HitType = org.rsmod.game.hit.HitType.Typeless,
    val prayerReductionPercent: Int? = null,
    val akkhaElement: AkkhaQuadrant? = null,
    val wardenImpact: WardenImpact? = null,
    val generation: Int = 0,
    val zebakBloodHealing: Boolean = false,
)

/** Effects which resolve only when their matching Warden hazard actually lands. */
internal enum class WardenImpact {
    IMPRISONMENT
}

internal data class WardenAreaPulse(
    val tiles: Set<CoordGrid>,
    val warnsAt: Int,
    val landsAt: Int,
    val damage: Int,
    val warningGraphic: Int,
    val impactGraphic: Int,
    val impact: WardenImpact? = null,
    var warned: Boolean = false,
)

/** One launched phase-one orb waiting to reach its Warden's charging circle. */
internal data class WardenChargeBallRuntime(
    val god: WardenGod,
    val landsAt: Int,
    val chargeAmount: Int,
    val indicatorTile: CoordGrid,
)

internal data class WardenChargeCircleResetRuntime(val god: WardenGod, val resetsAt: Int)

internal object ToaProjectileTiming {
    fun clientCyclesForServerTicks(serverTicks: Int): Int {
        require(serverTicks > 0)
        return (serverTicks - 1) * 30
    }

    fun clientCyclesForServerDelay(serverTicks: Int): Int {
        require(serverTicks >= 0)
        return if (serverTicks == 0) 0 else clientCyclesForServerTicks(serverTicks)
    }
}

internal class ToaEncounterState(
    /** Immutable encounter rules derived only from the raid-entry snapshot. */
    val wardensRuleset: WardensRuleset = WardensRuleset.BASELINE
) {
    var attackCounter = 0
    var phase = 0
    var style = 0
    var kephriRecovering = false
    var kephriActivated = false
    var kephriRecoverySwarms = 0
    var kephriRecoveryTotal = 0
    var kephriRecoveryHealCap = 0
    var kephriRecoveryResolved = 0
    var kephriNextSwarmAt = -1
    var kephriSwarmStartLane = 0
    var kephriNextMedicAt = -1
    var kephriNextBomberBombAt = -1
    var kephriMedicSpawnIndex = 0
    var kephriDungTargetCursor = 0
    var kephriDying = false
    var kephriCompletesAt = -1
    var kephriDeathHero: PlayerUid? = null
    val kephriSwarms = linkedMapOf<Int, KephriSwarmRuntime>()
    val kephriEggs = linkedMapOf<Int, KephriEggRuntime>()
    val kephriSoldiers = linkedMapOf<Int, KephriSoldierRuntime>()
    val kephriSpitters = linkedMapOf<Int, KephriSpittingRuntime>()
    val kephriArcanes = linkedMapOf<Int, KephriArcaneRuntime>()
    val kephriBombs = mutableListOf<KephriBombRuntime>()
    val kephriBombTargets = mutableListOf<KephriBombTargetRuntime>()
    val kephriEggBatches = mutableListOf<KephriEggBatchRuntime>()
    val kephriPendingDungStrikes = mutableListOf<KephriDungStrikeRuntime>()
    val kephriDungPlacements = mutableListOf<KephriDungPlacementRuntime>()
    val kephriDungTiles = linkedSetOf<CoordGrid>()
    var zebakSpecials = 0
    var zebakFirstSpecialWaves = true
    var zebakSpecialActive = false
    var zebakSpecialEndsAt = -1
    var wardenStage = ToaWardenStage.OBELISK
    var wardenPhaseTwoGod = WardenGod.ELIDINIS
    var wardenPhaseThreeGod = WardenGod.TUMEKEN
    var wardenElidinisCharge = 0
    var wardenTumekenCharge = 0
    var wardenObeliskActor = -1
    var wardenElidinisChargeActor = -1
    var wardenTumekenChargeActor = -1
    var wardenObeliskActivated = false
    var wardenElidinisChargeCycles = 0
    var wardenTumekenChargeCycles = 0
    val wardenChargeBalls = mutableListOf<WardenChargeBallRuntime>()
    var wardenElidinisCircleUnits = 0
    var wardenTumekenCircleUnits = 0
    val wardenChargeCircleResets = mutableListOf<WardenChargeCircleResetRuntime>()
    var wardenNextEnergyPulseAt = -1
    var wardenNextPassiveChargeAt = -1
    val wardenPhaseOneSpecials = mutableMapOf<WardenGod, WardenPhaseOneSpecial>()
    val wardenPhaseOneSpecialQueue = mutableListOf<WardenPhaseOneSpecial>()
    val wardenPhaseOneSpecialEndsAt = mutableMapOf<WardenGod, Int>()
    val wardenPhaseOneHazardTiles = mutableMapOf<WardenGod, MutableSet<CoordGrid>>()
    var wardenTransitionHero: PlayerUid? = null
    var wardenTransitionEndsAt = -1
    var wardenTransitionDestroyedAt = -1
    var wardenTransitionDyingActor = -1
    var wardenTransitionDestroyed = false
    var wardenPhaseThreeTransitionEndsAt = -1
    var wardenCoreDowns = 0
    var wardenCoreReturnsAt = -1
    var wardenCoreProjectileAt = -1
    var wardenCoreSpawnsAt = -1
    var wardenCorePendingBody = -1
    var wardenCorePendingTile: CoordGrid? = null
    var wardenCoreStartHitpoints = 0
    var wardenPhaseTwoShieldCharge = 0
    var wardenPhaseTwoTrueMax = 0
    var wardenPhaseTwoRemaining = 0
    var wardenLastForm = ElidinisWarden.MAGIC_FORM
    var wardenObeliskSpecialStep = 0
    var wardenNextPhaseTwoObeliskAttackAt = -1
    val wardenStoneReleaseAt = mutableMapOf<PlayerUid, Int>()
    val wardenAreaPulses = mutableListOf<WardenAreaPulse>()
    var wardenSkullsSent = 0
    var wardenSiphonsSpawned = 0
    var wardenSiphonPartySize = 1
    var wardenSkullsActive = false
    var wardenSiphonEndsAt = -1
    var wardenSiphonProjectileAt = -1
    var wardenSiphonLinkAt = -1
    var wardenSiphonsSpawnAt = -1
    var wardenSiphonPhase = -1
    var wardenEnraged = false
    var wardenFloorRow = 0
    var wardenNextFloorBreakAt = -1
    var wardenSlamStep = 0
    var wardenNextSlamAt = -1
    var wardenSlamInterruptedAt = -1
    var wardenPendingSlam: WardenSlamRuntime? = null
    var wardenEnrageStartedAt = -1
    var wardenNextLightningAt = -1
    val wardenPhantomNextAttackAt = mutableMapOf<Int, Int>()
    var wardenNextPhantomAttackAt = -1
    var wardenPhantomAttackCounter = 0
    var wardenDamageRollCounter = 0
    var wardenPhantomsSpawned = 0
    var wardenDeathStartedAt = -1
    var wardenDeathSource = -1
    var wardenDeathBossRemoved = false
    var wardenDeathPhantomsRemoved = false
    var wardenDeathBossBarCleared = false
    var wardenDeathLightOpened = false
    var wardenAkkhaPhantomStyle = 0
    val pendingHits = mutableListOf<ToaPendingHit>()

    fun reset() {
        attackCounter = 0
        phase = 0
        style = 0
        kephriRecovering = false
        kephriActivated = false
        kephriRecoverySwarms = 0
        kephriRecoveryTotal = 0
        kephriRecoveryHealCap = 0
        kephriRecoveryResolved = 0
        kephriNextSwarmAt = -1
        kephriSwarmStartLane = 0
        kephriNextMedicAt = -1
        kephriNextBomberBombAt = -1
        kephriMedicSpawnIndex = 0
        kephriDungTargetCursor = 0
        kephriDying = false
        kephriCompletesAt = -1
        kephriDeathHero = null
        kephriSwarms.clear()
        kephriEggs.clear()
        kephriSoldiers.clear()
        kephriSpitters.clear()
        kephriArcanes.clear()
        kephriBombs.clear()
        kephriBombTargets.clear()
        kephriEggBatches.clear()
        kephriPendingDungStrikes.clear()
        kephriDungPlacements.clear()
        kephriDungTiles.clear()
        zebakSpecials = 0
        zebakFirstSpecialWaves = true
        zebakSpecialActive = false
        zebakSpecialEndsAt = -1
        wardenStage = ToaWardenStage.OBELISK
        wardenPhaseTwoGod = WardenGod.ELIDINIS
        wardenPhaseThreeGod = WardenGod.TUMEKEN
        wardenElidinisCharge = 0
        wardenTumekenCharge = 0
        wardenObeliskActor = -1
        wardenElidinisChargeActor = -1
        wardenTumekenChargeActor = -1
        wardenObeliskActivated = false
        wardenElidinisChargeCycles = 0
        wardenTumekenChargeCycles = 0
        wardenChargeBalls.clear()
        wardenElidinisCircleUnits = 0
        wardenTumekenCircleUnits = 0
        wardenChargeCircleResets.clear()
        wardenNextEnergyPulseAt = -1
        wardenNextPassiveChargeAt = -1
        wardenPhaseOneSpecials.clear()
        wardenPhaseOneSpecialQueue.clear()
        wardenPhaseOneSpecialEndsAt.clear()
        wardenPhaseOneHazardTiles.clear()
        wardenTransitionHero = null
        wardenTransitionEndsAt = -1
        wardenTransitionDestroyedAt = -1
        wardenTransitionDyingActor = -1
        wardenTransitionDestroyed = false
        wardenPhaseThreeTransitionEndsAt = -1
        wardenCoreDowns = 0
        wardenCoreReturnsAt = -1
        wardenCoreProjectileAt = -1
        wardenCoreSpawnsAt = -1
        wardenCorePendingBody = -1
        wardenCorePendingTile = null
        wardenCoreStartHitpoints = 0
        wardenPhaseTwoShieldCharge = 0
        wardenPhaseTwoTrueMax = 0
        wardenPhaseTwoRemaining = 0
        wardenLastForm = ElidinisWarden.MAGIC_FORM
        wardenObeliskSpecialStep = 0
        wardenNextPhaseTwoObeliskAttackAt = -1
        wardenStoneReleaseAt.clear()
        wardenAreaPulses.clear()
        wardenSkullsSent = 0
        wardenSiphonsSpawned = 0
        wardenSiphonPartySize = 1
        wardenSkullsActive = false
        wardenSiphonEndsAt = -1
        wardenSiphonProjectileAt = -1
        wardenSiphonLinkAt = -1
        wardenSiphonsSpawnAt = -1
        wardenSiphonPhase = -1
        wardenEnraged = false
        wardenFloorRow = 0
        wardenNextFloorBreakAt = -1
        wardenSlamStep = 0
        wardenNextSlamAt = -1
        wardenSlamInterruptedAt = -1
        wardenPendingSlam = null
        wardenEnrageStartedAt = -1
        wardenNextLightningAt = -1
        wardenNextPhantomAttackAt = -1
        wardenPhantomAttackCounter = 0
        wardenDamageRollCounter = 0
        wardenPhantomNextAttackAt.clear()
        wardenPhantomsSpawned = 0
        wardenAkkhaPhantomStyle = 0
        pendingHits.clear()
        wardenDeathStartedAt = -1
        wardenDeathSource = -1
        wardenDeathBossRemoved = false
        wardenDeathPhantomsRemoved = false
        wardenDeathBossBarCleared = false
        wardenDeathLightOpened = false
    }

    fun rebindPlayer(oldUid: PlayerUid, newUid: PlayerUid) {
        if (oldUid == newUid) return
        if (wardenTransitionHero == oldUid) wardenTransitionHero = newUid
        if (kephriDeathHero == oldUid) kephriDeathHero = newUid
        wardenStoneReleaseAt.rebindKey(oldUid, newUid)
        kephriBombTargets.replaceAll { runtime ->
            if (runtime.target == oldUid) runtime.copy(target = newUid) else runtime
        }
        kephriPendingDungStrikes.replaceAll { strike ->
            if (strike.target == oldUid) strike.copy(target = newUid) else strike
        }
        kephriArcanes.replaceAll { _, runtime ->
            if (runtime.target == oldUid) runtime.copy(target = newUid) else runtime
        }
        pendingHits.replaceAll { hit ->
            if (hit.target == oldUid) hit.copy(target = newUid) else hit
        }
    }

    fun retirePlayer(uid: PlayerUid) {
        if (wardenTransitionHero == uid) wardenTransitionHero = null
        if (kephriDeathHero == uid) kephriDeathHero = null
        wardenStoneReleaseAt.remove(uid)
        kephriBombTargets.removeAll { it.target == uid }
        kephriPendingDungStrikes.removeAll { it.target == uid }
        kephriArcanes.values.forEach { if (it.target == uid) it.target = null }
        pendingHits.removeAll { it.target == uid }
    }
}

internal class ToaRaidSession(
    val party: ToaParty,
    val startSnapshot: ToaRaidStartSnapshot,
    val startedAt: Int,
) {
    lateinit var instance: InstanceSession
    val participants = ToaRaidRoster()
    val rewardPointLedger =
        ToaRewardPointsLedger(
            startSnapshot.participantAccountHashes.toList(),
            startSnapshot.partySize,
        )
    val raidLevel: Int = startSnapshot.raidLevel
    var rewardRaidLevel: Int = raidLevel
    var timerStartedAt: Int? = null
    var timerExpired: Boolean = false
    val pathLevels: MutableMap<ToaPath, Int> = startSnapshot.pathLevels.toMutableMap()
    val completedPaths: LinkedHashSet<ToaPath> = linkedSetOf()
    val completedRooms: LinkedHashSet<ToaRoom> = linkedSetOf()
    val spawnedNpcs: LinkedHashSet<Npc> = linkedSetOf()
    val npcRoles: MutableMap<Npc, ToaNpcRole> = mutableMapOf()
    var state = ToaRaidState.ACTIVE
    var currentRoom = ToaRoom.LOBBY
    var currentPath: ToaPath? = null
    var challenge: ToaChallengeState? = null
    var wardenPhase = 0
    var attemptsUsed = 0
    var nextMechanicAt = 0
    var encounterStarted = false
    var completionPending = false
    var helpfulSpiritSpawned = false
    var cleanupStarted = false
    var completedAt: Int? = null
    var rewardPlan: Map<Long, ToaRewardRoll>? = null
    val encounter = ToaEncounterState(WardensRuleset.from(startSnapshot.invocations))
    val akkha = AkkhaEncounter(encounterId = party.id, seed = startSnapshot.seed)
    val baba = BabaEncounter(encounterId = party.id, seed = startSnapshot.seed xor 0x42414241L)
    val crondis =
        CrondisEncounter(encounterId = party.id, seed = startSnapshot.seed xor 0x43524F4E444953L)
    val zebak =
        ZebakEncounter(encounterId = party.id, rngState = startSnapshot.seed xor 0x5A4542414BL)

    val mode: ToaMode
        get() = ToaRules.mode(rewardRaidLevel)

    fun participant(uid: PlayerUid): ToaParticipant? = participants[uid]

    fun participant(accountHash: Long): ToaParticipant? = participants.byAccountHash(accountHash)

    fun awardRewardDamage(
        accountHash: Long,
        room: ToaRewardPointRoom,
        target: ToaRewardPointTarget,
        actualDamage: Int,
    ): ToaRewardPointAward =
        rewardPointLedger.awardDamage(accountHash, room, target, actualDamage).also {
            syncRewardPointTotal(accountHash)
        }

    fun awardRewardActionPoints(
        accountHash: Long,
        room: ToaRewardPointRoom,
        points: Int,
    ): ToaRewardPointAward =
        rewardPointLedger.awardActionPoints(accountHash, room, points).also {
            syncRewardPointTotal(accountHash)
        }

    fun applyRewardPointDeath(accountHash: Long, generation: Long): ToaRewardPointDeath =
        rewardPointLedger.applyDeath(accountHash, generation).also {
            syncRewardPointTotal(accountHash)
        }

    fun settleRewardPointRoom(
        room: ToaRewardPointRoom,
        generation: Long,
    ): ToaRewardPointRoomSettlement =
        rewardPointLedger.settleRoom(room, generation).also { syncRewardPointTotals() }

    fun syncRewardPointTotals() {
        participants.values.forEach { participant ->
            participant.points = rewardPointLedger.totalPoints(participant.accountHash)
        }
    }

    private fun syncRewardPointTotal(accountHash: Long) {
        participant(accountHash)?.points = rewardPointLedger.totalPoints(accountHash)
    }

    fun owns(npc: Npc): Boolean = npc in spawnedNpcs
}

internal data class ToaReward(
    val objId: Int,
    val count: Int,
    val unique: Boolean,
    val noted: Boolean = false,
)

internal data class ToaRewardClaimId(
    val raidMostSignificantBits: Long,
    val raidLeastSignificantBits: Long,
    val accountHash: Long,
)

internal data class ToaRewardRoll(
    val rewards: List<ToaReward>,
    val unique: Boolean,
    val pet: Boolean = false,
    val claimId: ToaRewardClaimId? = null,
)
