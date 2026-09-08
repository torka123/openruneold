package org.rsmod.content.slayer.core

import kotlin.random.Random
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.area.checker.isInEdgevilleDungeon
import org.rsmod.api.area.checker.isInWilderness
import org.rsmod.api.config.refs.CanonicalSlayerCategoryMembership
import org.rsmod.map.CoordGrid

/**
 * Deny-by-default task roster for regular Slayer.
 *
 * Each category owns an exact NPC-ID set in [CanonicalSlayerCategoryMembership], explicit kill
 * locations, a real cache task row used for client display compatibility, quantities, weight and
 * Slayer requirement. The old per-master task tables are not consulted when rolling assignments.
 */
object CanonicalSlayerRegistry {

    const val PRIMARY_MASTER_ID = 7
    const val CATEGORY_VARP = CanonicalSlayerCategoryMembership.ASSIGNMENT_CATEGORY_VARP
    const val LAST_CATEGORY_VARP = "varp.slayer_last_category"
    private val wildernessOnly = setOf(Location.Wilderness)
    private val edgeOnly = setOf(Location.EdgevilleDungeon)
    private val caveOnly = setOf(Location.CustomSlayerCave)

    val categories: List<Category> = listOf(
        c(1, "rats", "Rats", 3, locations = edgeOnly),
        c(2, "spiders", "Spiders", 4, locations = edgeOnly),
        c(3, "scorpions", "Scorpions", 7, locations = edgeOnly),
        c(4, "bats", "Bats", 8),
        c(5, "wolves", "Wolves", 9),
        c(6, "skeletons", "Skeletons", 11, locations = edgeOnly),
        c(7, "ghosts", "Ghosts", 12, locations = edgeOnly),
        c(8, "bears", "Bears", 13),
        c(9, "hill_giants", "Hill Giants", 14),
        c(10, "ice_giants", "Ice Giants", 15),
        c(11, "fire_giants", "Fire Giants", 16),
        c(12, "moss_giants", "Moss Giants", 17),
        c(13, "ice_warriors", "Ice Warriors", 19),
        c(14, "ogres", "Ogres", 20),
        c(15, "hobgoblins", "Hobgoblins", 21, locations = edgeOnly),
        c(16, "green_dragons", "Green Dragons", 24),
        c(17, "black_dragons", "Black Dragons", 27, min = 10, max = 25),
        c(18, "lesser_demons", "Lesser Demons", 28),
        c(19, "greater_demons", "Greater Demons", 29),
        c(20, "black_demons", "Black Demons", 30, locations = edgeOnly),
        c(21, "hellhounds", "Hellhounds", 31),
        c(22, "werewolves", "Werewolves", 33),
        c(23, "vampyres", "Vampyres", 34),
        c(24, "abyssal_demons", "Abyssal Demons", 42, locations = caveOnly, requirement = 85),
        c(25, "pyrefiends", "Pyrefiends", 47, requirement = 30),
        c(26, "bloodvelds", "Bloodvelds", 48, requirement = 50),
        c(27, "dust_devils", "Dust Devils", 49, locations = edgeOnly, requirement = 65),
        c(28, "jellies", "Jellies", 50, locations = edgeOnly, requirement = 52),
        c(29, "nechryaels", "Nechryaels", 52, locations = caveOnly, requirement = 80),
        c(30, "earth_warriors", "Earth Warriors", 54, locations = edgeOnly),
        c(31, "chaos_dwarves", "Chaos Dwarves", 57),
        c(32, "gargoyles", "Gargoyles", 46, requirement = 75, locations = caveOnly),
        c(33, "skeletal_wyverns", "Skeletal Wyverns", 72, requirement = 72, locations = edgeOnly, min = 10, max = 25),
        c(34, "smoke_devils", "Smoke Devils", 95, requirement = 93),
        c(35, "mammoths", "Mammoths", 99),
        c(36, "rogues", "Rogues", 100),
        c(37, "ents", "Ents", 101),
        c(38, "bandits", "Bandits", 102),
        c(39, "dark_warriors", "Dark Warriors", 103),
        c(40, "lava_dragons", "Lava Dragons", 104, min = 10, max = 25),
        c(41, "revenants", "Revenants", 107, min = 20, max = 35),
        c(42, "chaos_druids", "Chaos Druids", 110),
        c(43, "black_knights", "Black Knights", 119),
        c(44, "pirates", "Pirates", 120),
        c(45, "zombies", "Zombies", 10, locations = edgeOnly),
        c(46, "hydras", "Hydras", 113, requirement = 95, locations = caveOnly, min = 10, max = 25),
        c(47, "demonic_gorillas", "Demonic Gorillas", 30, locations = caveOnly, min = 10, max = 25),
        c(48, "dark_beasts", "Dark Beasts", 66, requirement = 90, locations = caveOnly, min = 10, max = 25),
        c(49, "ancient_wyverns", "Ancient Wyverns", 106, requirement = 66, locations = caveOnly, min = 10, max = 25),
        c(50, "brutal_black_dragons", "Brutal Black Dragons", 27, requirement = 77, locations = caveOnly, min = 10, max = 20),
        c(51, "cave_horrors", "Cave Horrors", 80, requirement = 58, locations = edgeOnly),
        c(52, "venators", "Venators", 131, requirement = 74, locations = caveOnly, min = 10, max = 25),
        c(53, "elder_custodian_stalkers", "Elder Custodian Stalkers", 126, requirement = 54, locations = caveOnly, min = 10, max = 25),
        c(54, "mad_angel", "Mad Angel", 98, locations = caveOnly, min = 3, max = 5, weight = 2, boss = true),
        c(55, "chronozon", "Chronozon", 98, locations = edgeOnly, min = 3, max = 5, weight = 2, boss = true),
        c(56, "scurrius", "Scurrius", 98, locations = edgeOnly, min = 3, max = 5, weight = 2, boss = true),
        c(57, "bryophyta", "Bryophyta", 98, locations = edgeOnly, min = 3, max = 5, weight = 2, boss = true),
        c(58, "obor", "Obor", 98, locations = edgeOnly, min = 3, max = 5, weight = 2, boss = true),
        boss(59, "callisto", "Callisto"),
        boss(60, "artio", "Artio"),
        boss(61, "vetion", "Vet'ion"),
        boss(62, "calvarion", "Calvar'ion"),
        boss(63, "venenatis", "Venenatis"),
        boss(64, "spindel", "Spindel"),
        boss(65, "chaos_elemental", "Chaos Elemental"),
        boss(66, "chaos_fanatic", "Chaos Fanatic"),
        boss(67, "crazy_archaeologist", "Crazy Archaeologist"),
        boss(68, "scorpia", "Scorpia"),
        boss(69, "revenant_maledictus", "Revenant Maledictus"),
        c(70, "king_black_dragon", "King Black Dragon", 98, locations = setOf(Location.KingBlackDragon), min = 3, max = 5, weight = 2, boss = true),
        c(71, "corporeal_beast", "Corporeal Beast", 98, locations = setOf(Location.CorporealBeast), min = 3, max = 5, weight = 2, boss = true),
        c(72, "runite_golems", "Runite Golems", 98, min = 10, max = 20),
        c(73, "magic_axes", "Magic Axes", 91),
        c(74, "spiritual_creatures", "Spiritual Creatures", 89, requirement = 63),
        c(75, "aviansies", "Aviansies", 94),
        c(76, "icefiends", "Icefiends", 75),
        c(77, "ankous", "Ankous", 79, locations = setOf(Location.Wilderness, Location.CustomSlayerCave)),
    )

    private val byId = categories.associateBy(Category::id)

    init {
        require(byId.size == categories.size) { "Duplicate canonical Slayer category ID." }
        require(byId.keys == CanonicalSlayerCategoryMembership.categoryIds()) {
            "Canonical Slayer metadata and NPC membership are out of sync."
        }
        require(categories.all { it.minAmount in 1..it.maxAmount && it.weight > 0 })
    }

    fun get(categoryId: Int): Category? = byId[categoryId]

    fun isEnabledMaster(masterId: Int): Boolean = masterId == PRIMARY_MASTER_ID

    private val assignmentDisabledCategoryIds: Set<Int> = emptySet()

    fun isAssignmentEnabled(categoryId: Int): Boolean = categoryId !in assignmentDisabledCategoryIds

    fun rollDistinct(
        slayerLevel: Int,
        blockedTaskIds: Set<Int>,
        count: Int,
        random: Random = Random.Default,
    ): List<Category> {
        val pool =
            categories.filter { category ->
                isAssignmentEnabled(category.id) &&
                    slayerLevel >= category.slayerRequirement &&
                    category.baseTaskId !in blockedTaskIds
            }.toMutableList()
        val results = ArrayList<Category>(count)
        repeat(count) {
            if (pool.isEmpty()) return@repeat
            val totalWeight = pool.sumOf(Category::weight)
            var value = random.nextInt(totalWeight)
            val selected = pool.first { category ->
                value -= category.weight
                value < 0
            }
            results += selected
            pool.remove(selected)
        }
        return results
    }

    private val exactRooms: Map<Int, List<RoomBounds>> = mapOf(
        24 to listOf(RoomBounds(3329, 10152, 3353, 10174)),
        27 to listOf(RoomBounds(3115, 9887, 3124, 9895)),
        28 to listOf(RoomBounds(3092, 9887, 3101, 9921)),
        29 to listOf(RoomBounds(3328, 10095, 3345, 10120)),
        32 to listOf(RoomBounds(3391, 10114, 3407, 10129)),
        33 to listOf(RoomBounds(3085, 9875, 3106, 9904)),
        46 to listOf(RoomBounds(3348, 10108, 3373, 10131)),
        47 to listOf(RoomBounds(3342, 10058, 3378, 10090)),
        48 to listOf(RoomBounds(3422, 10094, 3441, 10111)),
        49 to listOf(RoomBounds(3434, 10072, 3451, 10101)),
        50 to listOf(RoomBounds(3350, 10149, 3372, 10165)),
        51 to listOf(RoomBounds(3158, 9872, 3173, 9887)),
        52 to listOf(RoomBounds(3328, 10119, 3347, 10150)),
        53 to listOf(RoomBounds(3404, 10057, 3424, 10074)),
        54 to listOf(RoomBounds(3420, 10142, 3436, 10158)),
        55 to listOf(RoomBounds(3076, 9925, 3098, 9945)),
        56 to listOf(RoomBounds(3280, 9856, 3315, 9895)),
        57 to listOf(RoomBounds(3145, 9885, 3172, 9912)),
        58 to listOf(RoomBounds(3098, 9828, 3125, 9860)),
    )

    data class RoomBounds(val minX: Int, val minZ: Int, val maxX: Int, val maxZ: Int, val level: Int = 0) {
        fun contains(coords: CoordGrid): Boolean =
            coords.level == level && coords.x in minX..maxX && coords.z in minZ..maxZ
    }

    fun authorizedRooms(categoryId: Int): List<RoomBounds> = exactRooms[categoryId].orEmpty()

    fun rollAmount(category: Category, random: Random = Random.Default): Int =
        random.nextInt(category.minAmount, category.maxAmount + 1)

    fun requiredLevel(npcId: Int): Int? =
        categories
            .filter { CanonicalSlayerCategoryMembership.matches(it.id, npcId) }
            .maxOfOrNull(Category::slayerRequirement)

    fun isAuthorizedKill(
        categoryId: Int,
        npcId: Int,
        coords: CoordGrid,
        areaChecker: AreaChecker,
    ): Boolean {
        val category = byId[categoryId] ?: return false
        if (!CanonicalSlayerCategoryMembership.matches(categoryId, npcId)) return false
        return isAuthorizedTile(category, coords, areaChecker)
    }

    fun isAuthorizedTile(
        category: Category,
        coords: CoordGrid,
        areaChecker: AreaChecker,
    ): Boolean {
        val rooms = exactRooms[category.id]
        if (!rooms.isNullOrEmpty()) {
            return rooms.any { it.contains(coords) }
        }
        return category.locations.any { it.contains(coords, areaChecker) }
    }

    /** Only unambiguous legacy task rows are migrated automatically. Generic boss task 98 clears. */
    fun migrateLegacyTask(taskId: Int): Category? {
        if (taskId == 98) return null
        val candidates = categories.filter { it.baseTaskId == taskId }
        return candidates.singleOrNull()
    }

    data class Category(
        val id: Int,
        val key: String,
        val displayName: String,
        val baseTaskId: Int,
        val locations: Set<Location>,
        val slayerRequirement: Int,
        val minAmount: Int,
        val maxAmount: Int,
        val weight: Int,
        val boss: Boolean,
    ) {
        val npcIds: IntArray
            get() = CanonicalSlayerCategoryMembership.npcIds(id)
    }

    enum class Location {
        Wilderness,
        EdgevilleDungeon,
        CustomSlayerCave,
        KingBlackDragon,
        CorporealBeast;

        fun contains(coords: CoordGrid, areaChecker: AreaChecker): Boolean =
            when (this) {
                Wilderness -> coords.isInWilderness(areaChecker)
                EdgevilleDungeon -> coords.isInEdgevilleDungeon()
                CustomSlayerCave -> coords.level == 0 && coords.x / 64 in 49..53 && coords.z / 64 in 157..159
                KingBlackDragon -> coords.level == 0 && coords.x in 2250..2295 && coords.z in 4660..4715
                CorporealBeast -> coords.level == 2 && coords.x in 2955..2995 && coords.z in 4240..4400
            }
    }

    /** Disabled until a future NPC and reviewed out-of-Wilderness boss allowlist are configured. */
    object FutureBossTaskService {
        const val enabled: Boolean = false
        const val minimumKills: Int = 3
        const val maximumKills: Int = 5
        val bosses: List<Nothing> = emptyList()
    }


    private fun c(
        id: Int,
        key: String,
        displayName: String,
        taskId: Int,
        locations: Set<Location> = wildernessOnly,
        requirement: Int = 1,
        min: Int = 20,
        max: Int = 40,
        weight: Int = 8,
        boss: Boolean = false,
    ) = Category(id, key, displayName, taskId, locations, requirement, min, max, weight, boss)

    private fun boss(id: Int, key: String, displayName: String) =
        c(id, key, displayName, 98, locations = setOf(Location.Wilderness), min = 3, max = 5, weight = 2, boss = true)
}
