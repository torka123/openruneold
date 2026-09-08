package org.rsmod.content.skills.thieving

/** A single weighted Thieving reward. All quantities and XP in this module are OSRS base values. */
public data class ThievingLoot(
    public val itemId: Int,
    public val minimum: Int = 1,
    public val maximum: Int = minimum,
    public val weight: Int = 1,
    public val pouchCoins: Int = 0,
)

public data class PickpocketDefinition(
    public val displayName: String,
    public val level: Int,
    public val baseXp: Double,
    public val noFailLevel: Int,
    public val stunAnimation: Int,
    public val failureDamage: IntRange,
    public val loot: List<ThievingLoot>,
)

public data class StallDefinition(
    public val objectIds: Set<Int>,
    public val displayName: String,
    public val level: Int,
    public val baseXp: Double,
    public val emptyObjectId: Int,
    public val respawnTicks: Int,
    public val loot: List<ThievingLoot>,
)

public object ThievingDefinitions {
    public const val COINS: Int = 995
    public const val PICKPOCKET_ANIMATION: Int = 881
    public const val STALL_ANIMATION: Int = 832
    public const val WALL_SAFE_LEVEL: Int = 50
    public const val WALL_SAFE_XP: Double = 70.0
    public const val WALL_SAFE_CLOSED: Int = 7236
    public const val WALL_SAFE_CLOSED_ALT: Int = 7237
    public const val WALL_SAFE_OPEN: Int = 7238
    public const val STETHOSCOPE: Int = 5560
    public const val MAX_COIN_POUCHES: Int = 28

    public val roguePieces: Set<Int> = setOf(5553, 5554, 5555, 5556, 5557)

    private fun pouch(item: Int, coins: Int): List<ThievingLoot> =
        listOf(ThievingLoot(itemId = item, pouchCoins = coins))

    public val man: PickpocketDefinition =
        PickpocketDefinition("citizen", 1, 8.0, 85, 422, 1..1, pouch(22521, 3))
    public val farmer: PickpocketDefinition =
        PickpocketDefinition("farmer", 10, 14.5, 90, 433, 1..1, pouch(22522, 9))
    public val ham: PickpocketDefinition =
        PickpocketDefinition(
            "H.A.M. member",
            15,
            22.2,
            91,
            433,
            1..3,
            listOf(
                ThievingLoot(22523, weight = 20, pouchCoins = 3),
                ThievingLoot(688, weight = 14),
                ThievingLoot(686, weight = 8),
                ThievingLoot(697, weight = 6),
                ThievingLoot(1511, weight = 8),
                ThievingLoot(321, weight = 6),
                ThievingLoot(314, 1, 4, 10),
                ThievingLoot(946, weight = 4),
                ThievingLoot(590, weight = 5),
                ThievingLoot(1739, weight = 5),
                ThievingLoot(886, 1, 4, 8),
                ThievingLoot(1625, weight = 3),
                ThievingLoot(1627, weight = 3),
            ),
        )
    public val warrior: PickpocketDefinition =
        PickpocketDefinition("warrior", 25, 26.0, 93, 386, 2..2, pouch(22524, 18))
    public val rogue: PickpocketDefinition =
        PickpocketDefinition(
            "rogue",
            32,
            36.5,
            94,
            422,
            2..2,
            listOf(
                ThievingLoot(22525, weight = 45, pouchCoins = 40),
                ThievingLoot(556, 8, 8, 15),
                ThievingLoot(1993, weight = 12),
                ThievingLoot(1219, weight = 10),
                ThievingLoot(1523, weight = 8),
            ),
        )
    public val masterFarmer: PickpocketDefinition =
        PickpocketDefinition("master farmer", 38, 43.0, 94, 386, 3..3, masterFarmerSeeds())
    public val guard: PickpocketDefinition =
        PickpocketDefinition("guard", 40, 46.8, 95, 386, 2..2, pouch(22527, 30))
    public val bandit: PickpocketDefinition =
        PickpocketDefinition("desert bandit", 53, 79.4, 95, 422, 3..3, pouch(22530, 40))
    public val knight: PickpocketDefinition =
        PickpocketDefinition("knight", 55, 84.3, 95, 386, 3..3, pouch(22531, 50))
    public val menaphite: PickpocketDefinition =
        PickpocketDefinition("Menaphite thug", 65, 137.5, 99, 386, 6..6, pouch(22534, 60))
    public val paladin: PickpocketDefinition =
        PickpocketDefinition(
            "paladin",
            70,
            131.8,
            99,
            386,
            3..3,
            listOf(ThievingLoot(22535, weight = 7, pouchCoins = 80), ThievingLoot(562, 2, 2, 1)),
        )
    public val gnome: PickpocketDefinition =
        PickpocketDefinition(
            "gnome",
            75,
            133.3,
            99,
            201,
            1..1,
            listOf(
                ThievingLoot(22536, weight = 45, pouchCoins = 300),
                ThievingLoot(52, 2, 4, 18),
                ThievingLoot(2150, weight = 12),
                ThievingLoot(444, weight = 8),
                ThievingLoot(557, weight = 8),
                ThievingLoot(2162, weight = 5),
                ThievingLoot(569, weight = 3),
                ThievingLoot(2801, weight = 1),
            ),
        )
    public val hero: PickpocketDefinition =
        PickpocketDefinition(
            "hero",
            80,
            163.3,
            99,
            386,
            3..3,
            listOf(
                ThievingLoot(22537, weight = 50, pouchCoins = 300),
                ThievingLoot(560, 2, 2, 12),
                ThievingLoot(565, 1, 1, 8),
                ThievingLoot(1993, weight = 9),
                ThievingLoot(445, weight = 7),
                ThievingLoot(1601, weight = 4),
                ThievingLoot(569, weight = 3),
                ThievingLoot(2363, weight = 2),
                ThievingLoot(1215, weight = 2),
            ),
        )
    public val vyre: PickpocketDefinition =
        PickpocketDefinition(
            "vyre",
            82,
            306.9,
            99,
            433,
            5..5,
            listOf(
                ThievingLoot(24703, weight = 50, pouchCoins = 100),
                ThievingLoot(7937, 10, 20, 10),
                ThievingLoot(565, 2, 2, 8),
                ThievingLoot(566, 2, 2, 8),
                ThievingLoot(560, 2, 2, 8),
                ThievingLoot(21880, weight = 4),
                ThievingLoot(24777, weight = 2),
            ),
        )
    public val elf: PickpocketDefinition =
        PickpocketDefinition(
            "elf",
            85,
            353.3,
            99,
            422,
            5..5,
            listOf(
                ThievingLoot(22538, weight = 60, pouchCoins = 280),
                ThievingLoot(561, 2, 2, 12),
                ThievingLoot(564, 3, 3, 12),
                ThievingLoot(563, 2, 2, 8),
                ThievingLoot(560, 2, 2, 8),
                ThievingLoot(1993, weight = 6),
                ThievingLoot(1601, weight = 3),
            ),
        )
    public val tzhaar: PickpocketDefinition =
        PickpocketDefinition(
            "TzHaar-Hur",
            90,
            103.4,
            99,
            2609,
            5..5,
            listOf(
                ThievingLoot(6529, 3, 10, 80),
                ThievingLoot(1623, weight = 8),
                ThievingLoot(1621, weight = 6),
                ThievingLoot(1619, weight = 4),
                ThievingLoot(1617, weight = 2),
            ),
        )

    public val coinPouchValues: Map<Int, Int> =
        listOf(
                man,
                farmer,
                ham,
                warrior,
                rogue,
                guard,
                bandit,
                knight,
                menaphite,
                paladin,
                gnome,
                hero,
                vyre,
                elf,
            )
            .flatMap(PickpocketDefinition::loot)
            .filter { it.pouchCoins > 0 }
            .associate { it.itemId to it.pouchCoins }

    public fun pickpocketForName(rawName: String): PickpocketDefinition? {
        val name = rawName.lowercase().trim()
        return when {
            name == "man" || name == "woman" -> man
            name.contains("master farmer") || name == "martin the master gardener" -> masterFarmer
            name == "farmer" -> farmer
            name.contains("h.a.m.") || name.contains("ham member") -> ham
            name.contains("menaphite thug") -> menaphite
            name.contains("knight of ardougne") || name.contains("knight of varlamore") -> knight
            name == "knight" -> knight
            name.contains("desert bandit") ||
                name.contains("kharidian bandit") ||
                name == "bandit" -> bandit
            name.contains("warrior") -> warrior
            name == "rogue" -> rogue
            name.contains("guard") -> guard
            name == "paladin" -> paladin
            name.startsWith("gnome") -> gnome
            name == "hero" -> hero
            name.contains("vyre") || name == "noctillion lugosi" -> vyre
            name.startsWith("tzhaar-hur") -> tzhaar
            name == "elf" || name in namedElves -> elf
            else -> null
        }
    }

    /**
     * OSRS chance interpolation for the supported classic pickpockets. The requirement-level chance
     * starts at 50%, then reaches guaranteed success at the current no-fail level.
     */
    public fun successChance(level: Int, definition: PickpocketDefinition): Int {
        if (level < definition.level) return 0
        if (level >= definition.noFailLevel) return 100
        val span = definition.noFailLevel - definition.level
        return 50 + ((level - definition.level) * 50 / span.coerceAtLeast(1))
    }

    public fun rogueDoubleChance(pieceCount: Int): Int = pieceCount.coerceIn(0, 5) * 20

    public fun wallSafeChance(level: Int, hasStethoscope: Boolean): Int {
        if (level < WALL_SAFE_LEVEL) return 0
        val base = (50 + (level - WALL_SAFE_LEVEL)).coerceAtMost(85)
        return (base + if (hasStethoscope) 10 else 0).coerceAtMost(95)
    }

    public fun selectLoot(loot: List<ThievingLoot>, roll: Int): ThievingLoot {
        require(loot.isNotEmpty())
        val total = loot.sumOf(ThievingLoot::weight)
        require(roll in 0 until total)
        var cursor = roll
        for (entry in loot) {
            cursor -= entry.weight
            if (cursor < 0) return entry
        }
        error("Unreachable weighted Thieving loot selection")
    }

    public val stalls: List<StallDefinition> =
        listOf(
            StallDefinition(
                setOf(4706, 4708),
                "vegetable stall",
                2,
                10.0,
                634,
                2,
                listOf(
                    ThievingLoot(1942),
                    ThievingLoot(1957),
                    ThievingLoot(1965),
                    ThievingLoot(1982),
                    ThievingLoot(1550),
                ),
            ),
            StallDefinition(
                setOf(6163),
                "baker's stall",
                5,
                16.0,
                6984,
                4,
                listOf(
                    ThievingLoot(2309, weight = 5),
                    ThievingLoot(1891, weight = 3),
                    ThievingLoot(1901, weight = 2),
                ),
            ),
            StallDefinition(
                setOf(11730),
                "baker's stall",
                5,
                16.0,
                634,
                4,
                listOf(
                    ThievingLoot(2309, weight = 5),
                    ThievingLoot(1891, weight = 3),
                    ThievingLoot(1901, weight = 2),
                ),
            ),
            StallDefinition(setOf(635), "tea stall", 5, 16.0, 634, 4, listOf(ThievingLoot(1978))),
            StallDefinition(setOf(6574), "tea stall", 5, 16.0, 6573, 4, listOf(ThievingLoot(1978))),
            StallDefinition(
                setOf(20350),
                "tea stall",
                5,
                16.0,
                20349,
                4,
                listOf(ThievingLoot(1978)),
            ),
            StallDefinition(
                setOf(4875),
                "monkey food stall",
                5,
                16.0,
                4797,
                6,
                listOf(ThievingLoot(1963)),
            ),
            StallDefinition(
                setOf(4874),
                "crafting stall",
                5,
                20.0,
                4797,
                8,
                listOf(ThievingLoot(1755), ThievingLoot(2347), ThievingLoot(1597)),
            ),
            StallDefinition(
                setOf(6166),
                "crafting stall",
                5,
                20.0,
                6984,
                8,
                listOf(ThievingLoot(1755), ThievingLoot(2347), ThievingLoot(1597)),
            ),
            StallDefinition(
                setOf(4876),
                "monkey general stall",
                5,
                25.0,
                4797,
                8,
                listOf(ThievingLoot(2347), ThievingLoot(590)),
            ),
            StallDefinition(
                setOf(6165),
                "silk stall",
                20,
                24.0,
                6984,
                8,
                listOf(ThievingLoot(950)),
            ),
            StallDefinition(
                setOf(11729),
                "silk stall",
                20,
                24.0,
                634,
                8,
                listOf(ThievingLoot(950)),
            ),
            StallDefinition(
                setOf(14011),
                "wine stall",
                22,
                27.0,
                634,
                8,
                listOf(ThievingLoot(1993)),
            ),
            StallDefinition(
                setOf(28823),
                "fruit stall",
                25,
                28.5,
                28824,
                4,
                listOf(
                    ThievingLoot(1963, weight = 5),
                    ThievingLoot(1951, weight = 4),
                    ThievingLoot(464, weight = 2),
                    ThievingLoot(247, weight = 1),
                ),
            ),
            StallDefinition(setOf(7053), "seed stall", 27, 10.0, 634, 4, masterFarmerSeeds()),
            StallDefinition(
                setOf(4278, 11732),
                "fur stall",
                35,
                45.0,
                634,
                12,
                listOf(ThievingLoot(958)),
            ),
            StallDefinition(
                setOf(4277, 4705, 4707),
                "fish stall",
                42,
                42.0,
                634,
                12,
                listOf(
                    ThievingLoot(315, weight = 4),
                    ThievingLoot(331, weight = 3),
                    ThievingLoot(359, weight = 2),
                ),
            ),
            StallDefinition(
                setOf(17031),
                "crossbow stall",
                49,
                52.0,
                6984,
                8,
                listOf(ThievingLoot(9420), ThievingLoot(9440), ThievingLoot(877, 3, 8)),
            ),
            StallDefinition(
                setOf(6164),
                "silver stall",
                50,
                205.0,
                6984,
                32,
                listOf(ThievingLoot(2355)),
            ),
            StallDefinition(
                setOf(11734),
                "silver stall",
                50,
                205.0,
                634,
                32,
                listOf(ThievingLoot(2355)),
            ),
            StallDefinition(
                setOf(6572),
                "spice stall",
                65,
                92.0,
                6573,
                10,
                listOf(ThievingLoot(2007)),
            ),
            StallDefinition(
                setOf(11733),
                "spice stall",
                65,
                92.0,
                634,
                10,
                listOf(ThievingLoot(2007)),
            ),
            StallDefinition(
                setOf(20348),
                "spice stall",
                65,
                92.0,
                20349,
                10,
                listOf(ThievingLoot(2007)),
            ),
            StallDefinition(
                setOf(4877),
                "magic stall",
                65,
                90.0,
                4797,
                12,
                listOf(
                    ThievingLoot(556, 5, 15),
                    ThievingLoot(557, 5, 15),
                    ThievingLoot(558, 5, 15),
                    ThievingLoot(559, 5, 15),
                ),
            ),
            StallDefinition(
                setOf(4878),
                "scimitar stall",
                65,
                210.0,
                4797,
                32,
                listOf(ThievingLoot(1321), ThievingLoot(1323), ThievingLoot(1325)),
            ),
            StallDefinition(setOf(6162), "gem stall", 75, 408.0, 6984, 100, gemLoot()),
            StallDefinition(setOf(11731), "gem stall", 75, 408.0, 634, 100, gemLoot()),
            StallDefinition(setOf(30280), "Mor Ul Rek gem stall", 75, 408.0, 30278, 175, gemLoot()),
            StallDefinition(
                setOf(30279),
                "Mor Ul Rek ore stall",
                82,
                350.0,
                30278,
                50,
                listOf(
                    ThievingLoot(447, weight = 6),
                    ThievingLoot(449, weight = 3),
                    ThievingLoot(451, weight = 1),
                ),
            ),
        )

    public val stallByObjectId: Map<Int, StallDefinition> = buildMap {
        stalls.forEach { definition -> definition.objectIds.forEach { put(it, definition) } }
    }

    private fun masterFarmerSeeds(): List<ThievingLoot> =
        listOf(
            ThievingLoot(5318, weight = 18),
            ThievingLoot(5319, weight = 15),
            ThievingLoot(5324, weight = 13),
            ThievingLoot(5322, weight = 12),
            ThievingLoot(5320, weight = 10),
            ThievingLoot(5323, weight = 8),
            ThievingLoot(5321, weight = 6),
            ThievingLoot(5305, weight = 7),
            ThievingLoot(5307, weight = 7),
            ThievingLoot(5308, weight = 6),
            ThievingLoot(5306, weight = 6),
            ThievingLoot(5309, weight = 5),
            ThievingLoot(5310, weight = 4),
            ThievingLoot(5311, weight = 3),
            ThievingLoot(5291, weight = 3),
            ThievingLoot(5292, weight = 3),
            ThievingLoot(5293, weight = 3),
            ThievingLoot(5294, weight = 3),
            ThievingLoot(5295, weight = 2),
            ThievingLoot(5296, weight = 2),
            ThievingLoot(5297, weight = 2),
            ThievingLoot(5298, weight = 2),
            ThievingLoot(5299, weight = 1),
            ThievingLoot(5300, weight = 1),
            ThievingLoot(5301, weight = 1),
            ThievingLoot(5302, weight = 1),
            ThievingLoot(5303, weight = 1),
            ThievingLoot(5304, weight = 1),
            ThievingLoot(5282, weight = 1),
            ThievingLoot(5281, weight = 1),
            ThievingLoot(5280, weight = 1),
        )

    private fun gemLoot(): List<ThievingLoot> =
        listOf(
            ThievingLoot(1623, weight = 40),
            ThievingLoot(1621, weight = 30),
            ThievingLoot(1619, weight = 20),
            ThievingLoot(1617, weight = 10),
        )

    private val namedElves: Set<String> =
        setOf(
            "idril",
            "mawrth",
            "goreu",
            "arvel",
            "kelyn",
            "anaire",
            "aranwe",
            "aredhel",
            "caranthir",
            "celebrian",
            "celegorm",
            "cirdan",
            "curufin",
            "earwen",
            "edrahil",
            "elenwe",
            "elladan",
            "enel",
            "erestor",
            "enerdhil",
            "enelye",
            "feanor",
            "findis",
            "finduilas",
            "fingolfin",
            "fingon",
            "galathil",
            "gelmir",
            "glorfindel",
            "guilin",
            "hendor",
            "imin",
            "iminye",
            "indis",
            "ingwe",
            "ingwion",
            "lenwe",
            "lindir",
            "maeglin",
            "mahtan",
            "miriel",
            "mithrellas",
            "nellas",
            "nerdanel",
            "nimloth",
            "oropher",
            "orophin",
            "saeros",
            "salgant",
            "tatie",
            "thingol",
            "turgon",
            "vaire",
        )
}
