package org.rsmod.content.skills.construction

import org.rsmod.api.attr.AttributeKey
import org.rsmod.game.entity.Player

internal val CONSTRUCTION_HOUSE_STATE =
    AttributeKey<MutableMap<String, Any>>("construction_house_v1")

enum class HouseStyle(
    val displayName: String,
    val level: Int,
    val price: Int,
    val zoneXOffset: Int,
    val sourceLevel: Int,
    val wallLocId: Int,
    val windowLocId: Int,
    val doorLocId1: Int,
    val doorLocId2: Int,
    val openedDoorLocId1: Int,
    val openedDoorLocId2: Int,
    val estateAgentAvailable: Boolean = true,
) {
    BASIC_WOOD("Basic wood", 1, 5_000, 0, 0, 13098, 13099, 13100, 13101, 13102, 13103),
    BASIC_STONE("Basic stone", 10, 5_000, 0, 1, 1902, 13091, 13094, 13096, 13095, 13097),
    WHITEWASHED_STONE(
        "Whitewashed stone",
        20,
        7_500,
        0,
        2,
        1415,
        13005,
        13006,
        13007,
        13008,
        13009,
    ),
    FREMENNIK_WOOD(
        "Fremennik-style wood",
        30,
        10_000,
        0,
        3,
        13111,
        13112,
        13109,
        13107,
        13110,
        13108,
    ),
    TROPICAL_WOOD("Tropical wood", 40, 15_000, 8, 0, 13011, 10816, 13016, 13015, 13018, 13017),
    FANCY_STONE("Fancy stone", 50, 25_000, 8, 1, 13116, 13117, 13119, 13118, 13121, 13120),
    DEATHLY_MANSION("Deathly mansion", 25, 35_000, 8, 2, 27082, 27083, 27084, 27085, 27086, 27087),
    TWISTED_THEME("Twisted theme", 1, 5_000, 8, 3, 37467, 37469, 37463, 37465, 37464, 37466),
    HOSIDIUS_HOUSE("Hosidius house", 1, 5_000, 16, 0, 39883, 39884, 37455, 37457, 37456, 39882),
    COSY_CABIN("Cosy cabin", 1, 35_000, 16, 1, 40862, 40863, 40858, 40860, 40859, 40861);

    companion object {
        fun fromStorage(value: String?): HouseStyle =
            entries.firstOrNull { it.name == value } ?: BASIC_WOOD
    }
}

/**
 * Resolves the finished architectural object that replaces the cache's translucent window template.
 * Exterior walls use the selected house-style window. Basement walls and walls shared by two indoor
 * rooms use the selected house-style wall, matching the OSRS/Kronos room renderer.
 *
 * [adjacentRoomOutdoors] is null when there is no adjacent room.
 */
internal fun HouseStyle.architecturalWindowLocId(level: Int, adjacentRoomOutdoors: Boolean?): Int =
    if (level > 0 && adjacentRoomOutdoors != false) {
        windowLocId
    } else {
        wallLocId
    }

enum class HouseLocation(
    val displayName: String,
    val level: Int,
    val price: Int,
    val portalId: Int,
    val outside: org.rsmod.map.CoordGrid,
) {
    RIMMINGTON("Rimmington", 1, 5_000, 15477, org.rsmod.map.CoordGrid(2953, 3224, 0)),
    TAVERLEY("Taverley", 10, 5_000, 15478, org.rsmod.map.CoordGrid(2893, 3465, 0)),
    POLLNIVNEACH("Pollnivneach", 20, 7_500, 15479, org.rsmod.map.CoordGrid(3340, 3003, 0)),
    HOSIDIUS("Hosidius", 25, 8_750, 28822, org.rsmod.map.CoordGrid(1742, 3517, 0)),
    RELLEKKA("Rellekka", 30, 10_000, 15480, org.rsmod.map.CoordGrid(2670, 3631, 0)),
    BRIMHAVEN("Brimhaven", 40, 15_000, 15481, org.rsmod.map.CoordGrid(2757, 3178, 0)),
    YANILLE("Yanille", 50, 25_000, 15482, org.rsmod.map.CoordGrid(2544, 3096, 0)),
    PRIFDDINAS("Prifddinas", 70, 50_000, 34947, org.rsmod.map.CoordGrid(3237, 6077, 0));

    companion object {
        fun fromStorage(value: String?): HouseLocation =
            entries.firstOrNull { it.name == value } ?: RIMMINGTON

        fun fromPortalId(id: Int): HouseLocation? = entries.firstOrNull { it.portalId == id }
    }
}

internal fun legacyHouseRoomId(room: String, x: Int, z: Int, level: Int, ordinal: Int = 0): String =
    "legacy-${room.lowercase()}-$x-$z-$level-$ordinal"

data class HouseRoom(
    val room: String,
    val zoneX: Int,
    val zoneZ: Int,
    val level: Int,
    val rotation: Int = 0,
    /** Persistent identity: furniture follows this room when the House Viewer moves it. */
    val stableId: String = legacyHouseRoomId(room, zoneX, zoneZ, level),
)

enum class HouseDoorsMode(val cacheValue: Int) {
    CLOSED(0),
    OPEN(1),
    NONE(2);

    companion object {
        fun fromStorage(value: Any?): HouseDoorsMode =
            when (value) {
                is Number -> entries.firstOrNull { it.cacheValue == value.toInt() } ?: CLOSED
                is String -> entries.firstOrNull { it.name == value } ?: CLOSED
                else -> CLOSED
            }
    }
}

data class HouseOptions(
    val teleportInside: Boolean = false,
    val defaultBuildMode: Boolean = false,
    val doors: HouseDoorsMode = HouseDoorsMode.CLOSED,
)

enum class ConstructionServant(
    val displayName: String,
    val level: Int,
    val wage: Int,
    val capacity: Int,
    val tripSeconds: Int,
    val sawmill: Boolean,
    val foodItemId: Int,
    val npcGameval: String,
) {
    RICK("Rick", 20, 500, 6, 60, false, 315, "npc.poh_servant_dogsbody"),
    MAID("Maid", 25, 1_000, 10, 30, false, 2003, "npc.poh_servant_waiter_woman"),
    COOK("Cook", 30, 3_000, 16, 17, true, 2301, "npc.poh_servant_cook_woman"),
    BUTLER("Butler", 40, 5_000, 20, 12, true, 1897, "npc.poh_servant_maitre_d_man"),
    DEMON_BUTLER("Demon butler", 50, 10_000, 26, 7, true, 2011, "npc.poh_servant_demon");

    companion object {
        fun fromStorage(value: String?): ConstructionServant? =
            entries.firstOrNull { it.name == value }
    }
}

enum class ServantRuntimeState {
    IDLE,
    FOLLOWING_OR_SUMMONED,
    ON_ERRAND,
    RETURNING,
    HOLDING_ITEMS,
    AWAITING_PAYMENT;

    companion object {
        fun fromStorage(value: String?): ServantRuntimeState =
            entries.firstOrNull { it.name == value } ?: IDLE
    }
}

enum class ServantTaskType {
    FETCH_FROM_BANK,
    DEPOSIT_TO_BANK,
    UNNOTE,
    SAWMILL;

    companion object {
        fun fromStorage(value: String?): ServantTaskType? = entries.firstOrNull { it.name == value }
    }
}

enum class ServantTaskStatus {
    DISPATCHED,
    COMPLETED,
    DELIVERED;

    companion object {
        fun fromStorage(value: String?): ServantTaskStatus =
            entries.firstOrNull { it.name == value } ?: DISPATCHED
    }
}

data class ServantTaskRecord(
    val id: String,
    val servant: ConstructionServant,
    val type: ServantTaskType,
    val input: MutableMap<Int, Int>,
    val output: MutableMap<Int, Int>,
    val coinPayment: Int,
    val dispatchCycle: Int,
    val completionCycle: Int,
    val status: ServantTaskStatus = ServantTaskStatus.DISPATCHED,
)

data class ServantState(
    val type: ConstructionServant,
    val completedServices: Int = 0,
    val paymentDue: Boolean = false,
    val lastRequest: String? = null,
    val runtimeState: ServantRuntimeState = ServantRuntimeState.IDLE,
    val task: ServantTaskRecord? = null,
    /** Owner-scoped items already removed from inventory/bank for an in-flight errand. */
    val escrow: MutableMap<Int, Int> = mutableMapOf(),
)

internal fun servantTripTicks(seconds: Int): Int {
    require(seconds >= 0)
    // OpenRune advances game content every 600 ms. Ceiling preserves the verified minimum trip.
    return (seconds * 5 + 2) / 3
}

data class HouseBlueprint(
    val rooms: MutableList<HouseRoom>,
    val furniture: MutableMap<String, String>,
    val style: HouseStyle = HouseStyle.BASIC_WOOD,
    val location: HouseLocation = HouseLocation.RIMMINGTON,
    val options: HouseOptions = HouseOptions(),
    val servant: ServantState? = null,
    /** Optimistic-lock revision for atomic House Viewer commits. */
    val layoutRevision: Long = 0L,
) {
    companion object {
        fun default(): HouseBlueprint {
            val parlour = HouseRoom("PARLOUR", 3, 4, 1, stableId = "starter-parlour")
            val garden = HouseRoom("GARDEN", 3, 3, 1, stableId = "starter-garden")
            return HouseBlueprint(
                rooms = mutableListOf(parlour, garden),
                furniture = mutableMapOf(furnitureKey(garden, "CENTERPIECE") to "EXIT_PORTAL"),
                style = HouseStyle.BASIC_WOOD,
                location = HouseLocation.RIMMINGTON,
            )
        }
    }
}

internal fun constructionHouseOwned(raw: Map<String, Any>?): Boolean {
    if (raw == null) return false
    val explicit = raw["owned"] as? Boolean
    if (explicit != null) return explicit
    // House data written before ownership was introduced represents a real saved house. This
    // keeps every existing player's Construction work while new accounts must buy their house.
    return raw["rooms"] is List<*>
}

internal fun Player.hasConstructionHouse(): Boolean =
    constructionHouseOwned(attr[CONSTRUCTION_HOUSE_STATE])

internal fun Player.createConstructionHouse() = saveHouseBlueprint(HouseBlueprint.default())

internal fun Player.loadHouseBlueprint(): HouseBlueprint {
    val raw = attr[CONSTRUCTION_HOUSE_STATE] ?: return HouseBlueprint.default()
    val rawRooms = raw["rooms"] as? List<*> ?: return HouseBlueprint.default()
    val rooms =
        rawRooms
            .mapIndexedNotNull { index, entry ->
                val room = entry as? Map<*, *> ?: return@mapIndexedNotNull null
                val name = room["room"] as? String ?: return@mapIndexedNotNull null
                val x = (room["x"] as? Number)?.toInt() ?: return@mapIndexedNotNull null
                val z = (room["z"] as? Number)?.toInt() ?: return@mapIndexedNotNull null
                val level = (room["level"] as? Number)?.toInt() ?: return@mapIndexedNotNull null
                val rotation = (room["rotation"] as? Number)?.toInt() ?: 0
                val stableId =
                    (room["id"] as? String)?.takeIf { it.isNotBlank() }
                        ?: legacyHouseRoomId(name, x, z, level, index)
                HouseRoom(name, x, z, level, rotation, stableId)
            }
            .toMutableList()
    val garden = rooms.singleOrNull { it.room == "GARDEN" && it.level == 1 }
    if (garden == null || rooms.map(HouseRoom::stableId).distinct().size != rooms.size) {
        return HouseBlueprint.default()
    }
    val rawFurniture = raw["furniture"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
    val legacyKey = Regex("^(-?\\d+):(-?\\d+):(-?\\d+):(.+)$")
    val furniture =
        rawFurniture
            .mapNotNull { (key, value) ->
                val name = value as? String ?: return@mapNotNull null
                val text = key.toString()
                val match = legacyKey.matchEntire(text)
                val normalized =
                    if (match == null) {
                        text
                    } else {
                        val (x, z, level, hotspot) = match.destructured
                        val placement =
                            rooms.firstOrNull {
                                it.zoneX == x.toInt() &&
                                    it.zoneZ == z.toInt() &&
                                    it.level == level.toInt()
                            } ?: return@mapNotNull null
                        furnitureKey(placement, hotspot)
                    }
                normalized to name
            }
            .toMap()
            .toMutableMap()
    furniture.putIfAbsent(furnitureKey(garden, "CENTERPIECE"), "EXIT_PORTAL")
    val style = HouseStyle.fromStorage(raw["style"] as? String)
    val location = HouseLocation.fromStorage(raw["location"] as? String)
    val rawOptions = raw["options"] as? Map<*, *>
    val options =
        HouseOptions(
            teleportInside = rawOptions?.get("teleportInside") as? Boolean ?: false,
            defaultBuildMode = rawOptions?.get("defaultBuildMode") as? Boolean ?: false,
            doors = HouseDoorsMode.fromStorage(rawOptions?.get("doors")),
        )
    val rawServant = raw["servant"] as? Map<*, *>
    val servantType = ConstructionServant.fromStorage(rawServant?.get("type") as? String)
    val servant =
        servantType?.let { type ->
            val rawEscrow = rawServant?.get("escrow") as? Map<*, *> ?: emptyMap<Any?, Any?>()
            val rawTask = rawServant?.get("task") as? Map<*, *>
            val taskType = ServantTaskType.fromStorage(rawTask?.get("type") as? String)
            val taskServant =
                ConstructionServant.fromStorage(rawTask?.get("servant") as? String) ?: type
            val task =
                if (rawTask != null && taskType != null) {
                    ServantTaskRecord(
                        id = rawTask["id"] as? String ?: "recovered",
                        servant = taskServant,
                        type = taskType,
                        input = loadConstructionItemMap(rawTask["input"]),
                        output = loadConstructionItemMap(rawTask["output"]),
                        coinPayment =
                            ((rawTask["coinPayment"] as? Number)?.toInt() ?: 0).coerceAtLeast(0),
                        dispatchCycle = (rawTask["dispatchCycle"] as? Number)?.toInt() ?: 0,
                        completionCycle = (rawTask["completionCycle"] as? Number)?.toInt() ?: 0,
                        status = ServantTaskStatus.fromStorage(rawTask["status"] as? String),
                    )
                } else {
                    null
                }
            ServantState(
                type = type,
                completedServices =
                    ((rawServant?.get("completedServices") as? Number)?.toInt() ?: 0).coerceIn(
                        0,
                        8,
                    ),
                paymentDue = rawServant?.get("paymentDue") as? Boolean ?: false,
                lastRequest = rawServant?.get("lastRequest") as? String,
                runtimeState =
                    ServantRuntimeState.fromStorage(rawServant?.get("runtimeState") as? String),
                task = task,
                escrow = loadConstructionItemMap(rawEscrow),
            )
        }
    val layoutRevision = (raw["layoutRevision"] as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L
    return HouseBlueprint(
        rooms = rooms,
        furniture = furniture,
        style = style,
        location = location,
        options = options,
        servant = servant,
        layoutRevision = layoutRevision,
    )
}

internal fun Player.saveHouseBlueprint(blueprint: HouseBlueprint) {
    attr[CONSTRUCTION_HOUSE_STATE] =
        mutableMapOf(
            "owned" to true,
            "style" to blueprint.style.name,
            "location" to blueprint.location.name,
            "layoutRevision" to blueprint.layoutRevision,
            "options" to
                mutableMapOf(
                    "teleportInside" to blueprint.options.teleportInside,
                    "defaultBuildMode" to blueprint.options.defaultBuildMode,
                    "doors" to blueprint.options.doors.name,
                ),
            "rooms" to
                blueprint.rooms
                    .map { room ->
                        mutableMapOf(
                            "room" to room.room,
                            "x" to room.zoneX,
                            "z" to room.zoneZ,
                            "level" to room.level,
                            "rotation" to room.rotation,
                            "id" to room.stableId,
                        )
                    }
                    .toMutableList(),
            "furniture" to blueprint.furniture.toMutableMap(),
            "servant" to
                (blueprint.servant?.let { servant ->
                    mutableMapOf(
                        "type" to servant.type.name,
                        "completedServices" to servant.completedServices,
                        "paymentDue" to servant.paymentDue,
                        "lastRequest" to servant.lastRequest,
                        "runtimeState" to servant.runtimeState.name,
                        "task" to
                            (servant.task?.let { task ->
                                mutableMapOf(
                                    "id" to task.id,
                                    "servant" to task.servant.name,
                                    "type" to task.type.name,
                                    "input" to saveConstructionItemMap(task.input),
                                    "output" to saveConstructionItemMap(task.output),
                                    "coinPayment" to task.coinPayment,
                                    "dispatchCycle" to task.dispatchCycle,
                                    "completionCycle" to task.completionCycle,
                                    "status" to task.status.name,
                                )
                            } ?: mutableMapOf<String, Any>()),
                        "escrow" to saveConstructionItemMap(servant.escrow),
                    )
                } ?: mutableMapOf<String, Any>()),
        )
}

private fun loadConstructionItemMap(value: Any?): MutableMap<Int, Int> {
    val raw = value as? Map<*, *> ?: return mutableMapOf()
    return raw.mapNotNull { (item, amount) ->
            val itemId = item.toString().toIntOrNull() ?: return@mapNotNull null
            val count = (amount as? Number)?.toInt() ?: return@mapNotNull null
            if (itemId < 0 || count <= 0) null else itemId to count
        }
        .toMap()
        .toMutableMap()
}

private fun saveConstructionItemMap(value: Map<Int, Int>): MutableMap<String, Int> =
    value.mapKeys { (itemId, _) -> itemId.toString() }.toMutableMap()

internal fun furnitureKey(room: HouseRoom, hotspot: String): String = "${room.stableId}:$hotspot"
