package org.rsmod.content.minigames.tob

import java.util.UUID
import org.rsmod.api.instances.InstanceSession
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.player.PlayerUid

internal enum class TobMode {
    ENTRY,
    NORMAL,
    HARD,
}

internal enum class TobRaidState {
    STARTING,
    ACTIVE,
    REWARDS,
    ABANDONED,
    COMPLETE,
}

internal enum class TobRoomState {
    LOCKED,
    READY,
    ACTIVE,
    COMPLETE,
}

internal enum class TobRoomCategory {
    BOSS,
    NYLOCAS,
    REWARD,
}

internal data class TobTemplateSegment(
    val sourceRegion: Int,
    val sourceLevel: Int,
    val destinationZoneX: Int,
    val destinationZoneZ: Int,
)

internal enum class TobRoom(
    val key: String,
    val displayName: String,
    val category: TobRoomCategory,
    val segments: List<TobTemplateSegment>,
    val entryLocalX: Int,
    val entryLocalZ: Int,
    val completionLocalX: Int,
    val completionLocalZ: Int,
    val spectatorLocalX: Int,
    val spectatorLocalZ: Int,
    val bossLocalX: Int,
    val bossLocalZ: Int,
) {
    MAIDEN(
        "maiden",
        "The Maiden of Sugadinti",
        TobRoomCategory.BOSS,
        listOf(TobTemplateSegment(12613, 0, 0, 0), TobTemplateSegment(12869, 0, 8, 0)),
        50,
        30,
        40,
        12,
        31,
        44,
        26,
        28,
    ),
    BLOAT(
        "bloat",
        "Pestilent Bloat",
        TobRoomCategory.BOSS,
        listOf(TobTemplateSegment(13125, 0, 16, 0)),
        57,
        32,
        39,
        31,
        33,
        19,
        35,
        32,
    ),
    NYLOCAS(
        "nylocas",
        "The Nylocas",
        TobRoomCategory.NYLOCAS,
        listOf(TobTemplateSegment(13122, 0, 24, 0)),
        32,
        59,
        32,
        25,
        37,
        16,
        30,
        23,
    ),
    SOTETSEG(
        "sotetseg",
        "Sotetseg",
        TobRoomCategory.BOSS,
        listOf(TobTemplateSegment(13123, 0, 0, 8)),
        15,
        5,
        15,
        37,
        6,
        26,
        13,
        40,
    ),
    XARPUS(
        "xarpus",
        "Xarpus",
        TobRoomCategory.BOSS,
        listOf(TobTemplateSegment(12612, 1, 8, 8)),
        34,
        25,
        34,
        35,
        21,
        35,
        34,
        35,
    ),
    VERZIK(
        "verzik",
        "Verzik Vitur",
        TobRoomCategory.BOSS,
        listOf(TobTemplateSegment(12611, 0, 16, 8)),
        31,
        16,
        21,
        37,
        21,
        37,
        30,
        35,
    ),
    TREASURE(
        "treasure",
        "Verzik's treasure vault",
        TobRoomCategory.REWARD,
        listOf(TobTemplateSegment(12867, 0, 24, 8)),
        37,
        18,
        37,
        18,
        37,
        18,
        40,
        35,
    );

    val entrySourceLevel: Int
        get() = segments.first().sourceLevel

    val gameplaySourceLevel: Int
        get() = segments.first().sourceLevel

    companion object {
        fun find(value: String): TobRoom? =
            entries.firstOrNull {
                it.key.equals(value, true) ||
                    it.name.equals(value, true) ||
                    it.displayName.replace(" ", "_").equals(value, true)
            }
    }
}

internal val TobRoom.hasChallengeBarrier: Boolean
    get() = category != TobRoomCategory.REWARD

internal enum class TobPassage(val locId: Int, val source: TobRoom, val destination: TobRoom) {
    MAIDEN_TO_BLOAT(TobLocs.FORMIDABLE_PASSAGE, TobRoom.MAIDEN, TobRoom.BLOAT),
    BLOAT_TO_NYLOCAS(TobLocs.FORMIDABLE_PASSAGE, TobRoom.BLOAT, TobRoom.NYLOCAS),
    NYLOCAS_TO_SOTETSEG(TobLocs.FORMIDABLE_PASSAGE, TobRoom.NYLOCAS, TobRoom.SOTETSEG),
    SOTETSEG_TO_XARPUS(TobLocs.FORMIDABLE_PASSAGE, TobRoom.SOTETSEG, TobRoom.XARPUS),
    XARPUS_TO_VERZIK(TobLocs.XARPUS_DOOR, TobRoom.XARPUS, TobRoom.VERZIK),
    VERZIK_TO_TREASURE(TobLocs.TREASURE_ROOM, TobRoom.VERZIK, TobRoom.TREASURE);

    companion object {
        fun forClick(locId: Int, source: TobRoom): TobPassage? =
            entries.firstOrNull { it.locId == locId && it.source == source }
    }
}

internal data class TobRoomProgress(
    val room: TobRoom,
    val index: Int,
    var state: TobRoomState = TobRoomState.LOCKED,
    var startedAt: Int? = null,
    var completedAt: Int? = null,
)

internal data class TobParticipant(
    val uid: PlayerUid,
    var roomIndex: Int = 0,
    var entered: Boolean = false,
    var score: Int = 0,
    var mvpPoints: Int = 0,
    var deaths: Int = 0,
    var supplyPoints: Int = 0,
    var connected: Boolean = true,
    var spectating: Boolean = false,
    var rewardClaimed: Boolean = false,
    var pendingReward: TobRewardRoll? = null,
)

internal class TobParty(var leader: PlayerUid, val boardSlot: Int) {
    val id: UUID = UUID.randomUUID()
    val members: LinkedHashSet<PlayerUid> = linkedSetOf(leader)
    val applicants: LinkedHashSet<PlayerUid> = linkedSetOf()
    val blocked: LinkedHashSet<PlayerUid> = linkedSetOf()
    var preferredSize: Int = 5
    var preferredCombat: Int = 126
    var activeSession: TobRaidSession? = null
}

internal enum class TobNpcRole {
    BOSS,
    VERZIK_HOST,
    VERZIK_TRANSITION,
    VERZIK_PHASE,
    NYLOCAS_WAVE,
    MAIDEN_ADD,
    VERZIK_ADD,
    HAZARD,
    PILLAR,
}

internal class TobRaidSession(val party: TobParty, val mode: TobMode, val startedAt: Int) {
    lateinit var instance: InstanceSession
    val rooms = TobRoom.entries.mapIndexed { index, room -> TobRoomProgress(room, index) }
    val participants: LinkedHashMap<PlayerUid, TobParticipant> = linkedMapOf()
    val spawnedNpcs: LinkedHashSet<Npc> = linkedSetOf()
    val npcRoles: MutableMap<Npc, TobNpcRole> = mutableMapOf()
    var state: TobRaidState = TobRaidState.STARTING
    var currentRoomIndex: Int = 0
    var encounter: Any? = null
    var nextMechanicAt: Int = 0
    var completedAt: Int? = null
    var cleanupStarted: Boolean = false
    var dawnbringerClaimed: Boolean = false

    val currentRoom: TobRoomProgress
        get() = rooms[currentRoomIndex]

    fun participant(uid: PlayerUid): TobParticipant? = participants[uid]

    fun owns(npc: Npc): Boolean = npc in spawnedNpcs
}

internal data class TobReward(val objId: Int, val count: Int, val unique: Boolean)

internal data class TobRewardRoll(val rewards: List<TobReward>, val unique: Boolean)
