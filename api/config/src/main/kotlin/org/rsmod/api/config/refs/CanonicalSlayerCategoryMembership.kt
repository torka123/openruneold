package org.rsmod.api.config.refs

/**
 * Exact revision-240 NPC membership for the restricted Wilderness-centric Slayer roster.
 *
 * This deliberately lives below both the Slayer content and combat-formula modules so kill credit
 * and Slayer-helmet accuracy/max-hit checks cannot drift apart. Membership is explicit: no name
 * fragments, combat-stat inference, or broad parent-category fallback is performed.
 */
public object CanonicalSlayerCategoryMembership {
    public const val ASSIGNMENT_CATEGORY_VARP: String = "varp.slayer_assignment_category"

    private val members: Map<Int, IntArray> =
        mapOf(
            1 to intArrayOf(2854, 2856, 2862, 2863, 2864),
            2 to intArrayOf(3016, 3017, 3018, 3019, 3021, 3022, 3023, 11990, 11999),
            3 to intArrayOf(3024, 3027),
            4 to intArrayOf(2834),
            5 to intArrayOf(106, 108),
            6 to
                intArrayOf(
                    70,
                    71,
                    72,
                    73,
                    77,
                    78,
                    79,
                    80,
                    81,
                    13495,
                    13496,
                    13497,
                    13498,
                    13499,
                    13500,
                    13501,
                ),
            7 to intArrayOf(85, 86, 87, 90, 91, 92),
            8 to intArrayOf(2838, 11989),
            9 to intArrayOf(2098, 2099, 2100, 2101, 2102, 2103),
            10 to intArrayOf(2085, 2086, 2087, 7878, 7879, 7880),
            11 to intArrayOf(2078, 2079, 2080),
            12 to intArrayOf(2090, 2091, 2093),
            13 to intArrayOf(2841),
            14 to intArrayOf(2233),
            15 to intArrayOf(3049),
            16 to intArrayOf(260, 261, 262, 263, 264),
            17 to intArrayOf(255, 256),
            18 to intArrayOf(2005, 2006, 2007, 2008, 2018),
            19 to intArrayOf(2026, 2027, 2028, 2029),
            20 to intArrayOf(2049, 2050, 2052),
            21 to intArrayOf(104),
            22 to intArrayOf(3135, 3136),
            23 to intArrayOf(3137),
            24 to intArrayOf(11239),
            25 to intArrayOf(3139),
            26 to intArrayOf(3138),
            27 to intArrayOf(423, 11238),
            28 to intArrayOf(437, 438, 439, 440, 441, 442, 11241, 11242, 11243, 11244, 11245),
            29 to intArrayOf(11240),
            30 to intArrayOf(2840),
            31 to intArrayOf(291),
            32 to intArrayOf(412),
            33 to intArrayOf(465),
            34 to intArrayOf(498),
            35 to intArrayOf(6604),
            36 to intArrayOf(526, 6603),
            37 to intArrayOf(6594, 7234),
            38 to intArrayOf(301, 302, 303, 1026, 1027, 6605),
            39 to intArrayOf(531, 6606),
            40 to intArrayOf(6593),
            // Maledictus is a directed alias for Revenants; ordinary Revenants are not aliases
            // for the dedicated Maledictus category below.
            41 to
                intArrayOf(7881, 7931, 7932, 7933, 7934, 7935, 7936, 7937, 7938, 7939, 7940, 11246),
            42 to intArrayOf(520, 6607),
            43 to intArrayOf(517, 11953),
            44 to intArrayOf(523),
            45 to intArrayOf(45, 46, 47, 48, 59, 60, 61, 62, 63, 6596, 6597, 6598),
            46 to intArrayOf(8609),
            47 to intArrayOf(7144, 7145, 7146, 7147, 7148, 7149),
            48 to intArrayOf(7250),
            49 to intArrayOf(7795),
            50 to intArrayOf(7275),
            51 to intArrayOf(1047, 1048, 1049, 1050, 1051),
            // Superior Venator (15770) is a directed alias for an ordinary Venator task.
            52 to intArrayOf(15765, 15766, 15767, 15768, 15769, 15770),
            53 to intArrayOf(14704),
            54 to intArrayOf(16305),
            55 to intArrayOf(4987),
            56 to intArrayOf(7221),
            57 to intArrayOf(8195),
            58 to intArrayOf(7416),
            59 to intArrayOf(6609),
            60 to intArrayOf(11992),
            61 to intArrayOf(6611, 6612),
            62 to intArrayOf(11993, 11994),
            63 to intArrayOf(6610),
            64 to intArrayOf(11998),
            65 to intArrayOf(2054),
            66 to intArrayOf(6619),
            67 to intArrayOf(6618),
            68 to intArrayOf(6615),
            69 to intArrayOf(11246),
            70 to intArrayOf(239),
            71 to intArrayOf(319),
            72 to intArrayOf(6600),
            73 to intArrayOf(2844),
            74 to
                intArrayOf(2210, 2211, 2212, 2242, 2243, 2244, 3159, 3160, 3161, 3166, 3167, 3168),
            75 to intArrayOf(3169, 3171, 3175, 3177, 3179, 3181, 3182, 3183),
            76 to intArrayOf(3140),
            77 to intArrayOf(6608),
        )

    public fun matches(categoryId: Int, npcId: Int): Boolean =
        members[categoryId]?.contains(npcId) == true

    public fun npcIds(categoryId: Int): IntArray = members[categoryId]?.copyOf() ?: intArrayOf()

    public fun isKnown(categoryId: Int): Boolean = categoryId in members

    public fun categoryIds(): Set<Int> = members.keys
}
