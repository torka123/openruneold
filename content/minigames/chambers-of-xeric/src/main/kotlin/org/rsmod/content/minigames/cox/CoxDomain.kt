package org.rsmod.content.minigames.cox

import java.util.UUID
import org.rsmod.api.instances.InstanceSession
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.obj.Obj

internal enum class RaidDifficulty {
    NORMAL,
    CHALLENGE,
}

internal enum class RaidState {
    LOBBY,
    STARTING,
    ACTIVE,
    OLM,
    REWARDS,
    ABANDONED,
    COMPLETE,
}

internal enum class RaidRoomState {
    LOCKED,
    READY,
    ACTIVE,
    COMPLETE,
}

internal enum class CoxDonorStage {
    FLOOR_ONE,
    FLOOR_TWO,
    OLM,
    REWARDS,
}

internal enum class CoxDonorEncounter {
    MUTTADILES,
    TIGHTROPE,
    FLOOR_ONE_SCAVENGERS,
    VANGUARDS,
    FLOOR_TWO_SCAVENGERS,
    SKELETAL_MYSTICS,
    GUARDIANS,
}

internal enum class CoxRoomCategory {
    START,
    COMBAT,
    PUZZLE,
    SCAVENGER,
    RESOURCE,
    OLM,
}

/**
 * Native build-240 Chambers templates. Source chunks were verified against the read-only Reason
 * server and then resolved in the destination cache by the CoX cache audit.
 */
internal enum class CoxRoom(
    val key: String,
    val displayName: String,
    val category: CoxRoomCategory,
    val sourceChunkX: Int,
    val sourceChunkZ: Int,
    val sourceLevel: Int,
) {
    START("start", "Starting room", CoxRoomCategory.START, 412, 648, 0),
    SCAVENGERS("scavengers", "Scavengers", CoxRoomCategory.SCAVENGER, 412, 652, 0),
    SHAMANS("shamans", "Lizardman shamans", CoxRoomCategory.COMBAT, 412, 656, 0),
    VASA("vasa", "Vasa Nistirio", CoxRoomCategory.COMBAT, 412, 660, 0),
    VANGUARDS("vanguards", "Vanguards", CoxRoomCategory.COMBAT, 412, 664, 0),
    ICE_DEMON("ice_demon", "Ice Demon", CoxRoomCategory.PUZZLE, 412, 668, 0),
    SKELETAL_MYSTICS("mystics", "Skeletal mystics", CoxRoomCategory.COMBAT, 412, 656, 1),
    TEKTON("tekton", "Tekton", CoxRoomCategory.COMBAT, 412, 660, 1),
    MUTTADILES("muttadiles", "Muttadiles", CoxRoomCategory.COMBAT, 412, 664, 1),
    TIGHTROPE("tightrope", "Tightrope", CoxRoomCategory.PUZZLE, 412, 668, 1),
    GUARDIANS("guardians", "Guardians", CoxRoomCategory.COMBAT, 412, 656, 2),
    VESPULA("vespula", "Vespula", CoxRoomCategory.COMBAT, 412, 660, 2),
    JEWELLED_CRABS("jewelled_crabs", "Jewelled Crabs", CoxRoomCategory.PUZZLE, 412, 668, 2),
    THIEVING("thieving", "Thieving", CoxRoomCategory.PUZZLE, 412, 668, 1),
    RESOURCE_FISHING(
        "resources_fishing",
        "Fishing resources",
        CoxRoomCategory.RESOURCE,
        412,
        680,
        0,
    ),
    RESOURCE_HUNTER("resources_hunter", "Hunter resources", CoxRoomCategory.RESOURCE, 412, 680, 1),
    OLM("olm", "Great Olm", CoxRoomCategory.OLM, 400, 712, 0);

    companion object {
        fun find(value: String): CoxRoom? =
            entries.firstOrNull {
                it.key.equals(value, true) ||
                    it.name.equals(value, true) ||
                    it.displayName.replace(" ", "_").equals(value, true)
            }
    }
}

internal data class CoxRoomPlacement(
    val room: CoxRoom,
    val index: Int,
    val floor: Int,
    val destinationZoneX: Int,
    val destinationZoneZ: Int,
    val rotation: Int = 0,
    var state: RaidRoomState = if (index == 0) RaidRoomState.READY else RaidRoomState.LOCKED,
)

internal data class CoxLayout(
    val seed: Long,
    val difficulty: RaidDifficulty,
    val placements: List<CoxRoomPlacement>,
) {
    init {
        require(placements.isNotEmpty() && placements.first().room == CoxRoom.START)
        require(placements.last().room == CoxRoom.OLM)
        require(placements.map(CoxRoomPlacement::index) == placements.indices.toList())
    }

    fun room(index: Int): CoxRoomPlacement? = placements.getOrNull(index)
}

internal data class RaidParticipant(
    val uid: PlayerUid,
    var personalPoints: Int = 0,
    var deaths: Int = 0,
    var connected: Boolean = true,
    var rewardClaimed: Boolean = false,
    var pendingReward: CoxRewardRoll? = null,
)

internal class RaidParty(var leader: PlayerUid) {
    val id: UUID = UUID.randomUUID()
    val members: LinkedHashSet<PlayerUid> = linkedSetOf(leader)
    var activeSession: CoxRaidSession? = null
}

internal data class MuttadileEatingAttempt(
    val giveUpAt: Int,
    var resumeCombatAt: Int? = null,
)

internal class CoxRaidSession(
    val party: RaidParty,
    val difficulty: RaidDifficulty,
    val layout: CoxLayout,
    val startedAt: Int,
    val olmOnly: Boolean = false,
) {
    lateinit var instance: InstanceSession
    val participants: LinkedHashMap<PlayerUid, RaidParticipant> = linkedMapOf()
    val spawnedNpcs: LinkedHashSet<Npc> = linkedSetOf()
    val npcRooms: MutableMap<Npc, Int> = mutableMapOf()
    val donorEncounters: MutableMap<Npc, CoxDonorEncounter> = mutableMapOf()
    val donorBlockers: MutableMap<CoxDonorEncounter, LocInfo> = mutableMapOf()
    val spawnedLocs: LinkedHashSet<LocInfo> = linkedSetOf()
    val spawnedObjs: LinkedHashSet<Obj> = linkedSetOf()
    val completedDonorEncounters: MutableSet<CoxDonorEncounter> = mutableSetOf()
    var state: RaidState = RaidState.STARTING
    var currentRoomIndex: Int = 0
    var teamPoints: Int = 0
    val donorVanguardForms: MutableMap<Npc, Int> = mutableMapOf()
    val donorVanguardWaypoints: MutableMap<Npc, Int> = mutableMapOf()
    var completedAt: Int? = null
    var olm: GreatOlmState? = null
    var cleanupStarted: Boolean = false
    var donorStage: CoxDonorStage = if (olmOnly) CoxDonorStage.OLM else CoxDonorStage.FLOOR_ONE
    var largeMuttadileUnlocked: Boolean = false
    var largeMuttadile: Npc? = null
    var muttadileMeatTree: Npc? = null
    var muttadileMeatTreeLoc: LocInfo? = null
    var muttadileMeatTreeFelled: Boolean = false
    var muttadileCombatDamageCredited: Int = 0
    var muttadileTreePointsCredited: Int = 0
    val muttadileEatAttempted: MutableSet<Npc> = mutableSetOf()
    val muttadileEating: MutableMap<Npc, MuttadileEatingAttempt> = mutableMapOf()

    var vanguardsActivated: Boolean = false
    var nextVanguardShuffle: Int = Int.MAX_VALUE
    val currentRoom: CoxRoomPlacement
        get() = checkNotNull(layout.room(currentRoomIndex))

    fun participant(uid: PlayerUid): RaidParticipant? = participants[uid]

    fun owns(npc: Npc): Boolean = npc in spawnedNpcs
}

internal data class CoxReward(val objId: Int, val count: Int, val unique: Boolean)

internal data class CoxRewardRoll(
    val rewards: List<CoxReward>,
    val uniqueAwarded: Boolean,
    val uniqueOwner: PlayerUid? = null,
)
