package org.rsmod.content.skills.farming

/**
 * Farming data is intentionally expressed in cache (not internal) ids.  The service resolves
 * those ids through RSCM on startup, so cache remaps do not silently break item interactions.
 * Growth values are the current OSRS minimum growth durations, split into the normal number of
 * stages.  OpenRune's configured Farming XP multiplier is applied at the point XP is awarded.
 */
internal enum class FarmingPatchKind {
    ALLOTMENT, HERB, FLOWER, TREE, FRUIT_TREE, BUSH, HOPS, CACTUS, CALQUAT, MUSHROOM, SPIRIT_TREE, ANIMA, HESPORI,
}

internal data class FarmingPatchDef(
    val key: String,
    val baseLocId: Int,
    val kind: FarmingPatchKind,
    val label: String,
)

internal data class FarmingCrop(
    val key: String,
    val kind: FarmingPatchKind,
    val inputId: Int,
    val outputId: Int,
    val level: Int,
    val plantXp: Double,
    val harvestXp: Double,
    val healthXp: Double = 0.0,
    val stages: Int,
    val growthMinutes: Int,
    val visualIndex: Int,
    val harvests: Int = 0,
    val name: String,
) {
    val stageMillis: Long get() = growthMinutes.toLong() * 60_000L / stages.coerceAtLeast(1)
}

internal object FarmingData {
    // Established donor patch catalogue, checked against the live cache during startup.
    val patches = listOf(
        *listOf(8150, 8151, 8152, 8153, 18816, 27115, 33979).mapIndexed { i, id -> FarmingPatchDef("herb_$i", id, FarmingPatchKind.HERB, "herb") }.toTypedArray(),
        *listOf(7847, 7848, 7849, 7850, 27111, 33649).mapIndexed { i, id -> FarmingPatchDef("flower_$i", id, FarmingPatchKind.FLOWER, "flower") }.toTypedArray(),
        *listOf(8550, 8551, 8552, 8553, 8554, 8555, 8556, 8557, 27113, 27114, 33694, 33693).mapIndexed { i, id -> FarmingPatchDef("allotment_$i", id, FarmingPatchKind.ALLOTMENT, "allotment") }.toTypedArray(),
        *listOf(8388, 8389, 8390, 8391, 19147, 33732).mapIndexed { i, id -> FarmingPatchDef("tree_$i", id, FarmingPatchKind.TREE, "tree") }.toTypedArray(),
        *listOf(34051, 34052, 34053, 34054, 34055, 34056, 34057, 34058, 34059).mapIndexed { i, id -> FarmingPatchDef("redwood_$i", id, FarmingPatchKind.TREE, "redwood tree") }.toTypedArray(),
        FarmingPatchDef("celastrus", 34629, FarmingPatchKind.TREE, "celastrus tree"),
        *listOf(7962, 7963, 7964, 7965, 26579, 34007).mapIndexed { i, id -> FarmingPatchDef("fruit_$i", id, FarmingPatchKind.FRUIT_TREE, "fruit tree") }.toTypedArray(),
        *listOf(7577, 7578, 7579, 7580, 34006).mapIndexed { i, id -> FarmingPatchDef("bush_$i", id, FarmingPatchKind.BUSH, "bush") }.toTypedArray(),
        *listOf(8173, 8174, 8175, 8176).mapIndexed { i, id -> FarmingPatchDef("hops_$i", id, FarmingPatchKind.HOPS, "hops") }.toTypedArray(),
        FarmingPatchDef("calquat", 7807, FarmingPatchKind.CALQUAT, "calquat tree"),
        FarmingPatchDef("cactus", 7771, FarmingPatchKind.CACTUS, "cactus"),
        FarmingPatchDef("guild_cactus", 33761, FarmingPatchKind.CACTUS, "cactus"),
        FarmingPatchDef("mushroom", 8337, FarmingPatchKind.MUSHROOM, "mushroom"),
        *listOf(8383, 8338, 8382, 27116, 33733).mapIndexed { i, id -> FarmingPatchDef("spirit_$i", id, FarmingPatchKind.SPIRIT_TREE, "spirit tree") }.toTypedArray(),
        FarmingPatchDef("anima", 33998, FarmingPatchKind.ANIMA, "anima"),
        FarmingPatchDef("hespori", 34630, FarmingPatchKind.HESPORI, "Hespori"),
    )

    private fun crop(
        key: String, kind: FarmingPatchKind, input: Int, output: Int, level: Int, plantXp: Double, harvestXp: Double,
        stages: Int, minutes: Int, visual: Int, harvests: Int, name: String, healthXp: Double = 0.0,
    ) = FarmingCrop(key, kind, input, output, level, plantXp, harvestXp, healthXp, stages, minutes, visual, harvests, name)

    val crops = listOf(
        // Herbs: 4 × 20 minute growth cycles (80 minutes total).
        crop("guam", FarmingPatchKind.HERB, 5291, 199, 9, 11.0, 12.5, 4, 80, 4, 6, "guam"),
        crop("marrentill", FarmingPatchKind.HERB, 5292, 201, 14, 13.5, 15.0, 4, 80, 5, 6, "marrentill"),
        crop("tarromin", FarmingPatchKind.HERB, 5293, 203, 19, 16.0, 18.0, 4, 80, 6, 6, "tarromin"),
        crop("harralander", FarmingPatchKind.HERB, 5294, 205, 26, 21.5, 24.0, 4, 80, 7, 6, "harralander"),
        crop("ranarr", FarmingPatchKind.HERB, 5295, 207, 32, 27.0, 30.5, 4, 80, 8, 6, "ranarr"),
        crop("toadflax", FarmingPatchKind.HERB, 5296, 3049, 38, 34.0, 38.5, 4, 80, 9, 6, "toadflax"),
        crop("irit", FarmingPatchKind.HERB, 5297, 209, 44, 43.0, 48.5, 4, 80, 10, 6, "irit"),
        crop("avantoe", FarmingPatchKind.HERB, 5298, 211, 50, 54.5, 61.5, 4, 80, 11, 6, "avantoe"),
        crop("kwuarm", FarmingPatchKind.HERB, 5299, 213, 56, 69.0, 78.0, 4, 80, 12, 6, "kwuarm"),
        crop("snapdragon", FarmingPatchKind.HERB, 5300, 3051, 62, 87.5, 98.5, 4, 80, 13, 6, "snapdragon"),
        crop("cadantine", FarmingPatchKind.HERB, 5301, 215, 67, 106.5, 120.0, 4, 80, 14, 6, "cadantine"),
        crop("lantadyme", FarmingPatchKind.HERB, 5302, 2485, 73, 134.5, 151.5, 4, 80, 15, 6, "lantadyme"),
        crop("dwarf_weed", FarmingPatchKind.HERB, 5303, 217, 79, 170.5, 192.0, 4, 80, 16, 6, "dwarf weed"),
        crop("torstol", FarmingPatchKind.HERB, 5304, 219, 85, 199.5, 224.5, 4, 80, 17, 6, "torstol"),
        // Allotments, flowers and hops use 10-, 5- and 10-minute cycles respectively.
        crop("potato", FarmingPatchKind.ALLOTMENT, 5318, 1942, 1, 8.0, 9.0, 4, 40, 6, 4, "potato"),
        crop("onion", FarmingPatchKind.ALLOTMENT, 5319, 1957, 5, 9.5, 10.5, 4, 40, 13, 4, "onion"),
        crop("cabbage", FarmingPatchKind.ALLOTMENT, 5324, 1965, 7, 10.0, 11.5, 4, 40, 20, 4, "cabbage"),
        crop("tomato", FarmingPatchKind.ALLOTMENT, 5322, 1982, 12, 12.5, 14.0, 4, 40, 27, 4, "tomato"),
        crop("sweetcorn", FarmingPatchKind.ALLOTMENT, 5320, 5986, 20, 17.0, 19.0, 6, 60, 34, 6, "sweetcorn"),
        crop("strawberry", FarmingPatchKind.ALLOTMENT, 5323, 5504, 31, 26.0, 29.0, 6, 60, 43, 6, "strawberry"),
        crop("watermelon", FarmingPatchKind.ALLOTMENT, 5321, 5982, 47, 48.5, 54.5, 8, 80, 52, 6, "watermelon"),
        crop("snape_grass", FarmingPatchKind.ALLOTMENT, 22879, 231, 61, 82.0, 82.0, 7, 70, 62, 6, "snape grass"),
        crop("marigold", FarmingPatchKind.FLOWER, 5096, 6010, 2, 8.5, 47.0, 4, 20, 8, 1, "marigold"),
        crop("rosemary", FarmingPatchKind.FLOWER, 5097, 6014, 11, 12.0, 66.5, 4, 20, 13, 1, "rosemary"),
        crop("nasturtium", FarmingPatchKind.FLOWER, 5098, 6012, 24, 19.5, 111.0, 4, 20, 18, 1, "nasturtium"),
        crop("woad", FarmingPatchKind.FLOWER, 5099, 1793, 25, 20.5, 115.5, 4, 20, 23, 1, "woad"),
        crop("limpwurt", FarmingPatchKind.FLOWER, 5100, 225, 26, 21.5, 120.0, 4, 20, 28, 3, "limpwurt"),
        crop("barley", FarmingPatchKind.HOPS, 5305, 6006, 3, 8.5, 9.5, 4, 40, 49, 4, "barley"),
        crop("hammerstone", FarmingPatchKind.HOPS, 5307, 5994, 4, 9.0, 10.0, 4, 40, 4, 4, "hammerstone hops"),
        crop("asgarnian", FarmingPatchKind.HOPS, 5308, 5996, 8, 10.5, 12.0, 5, 50, 11, 4, "asgarnian hops"),
        crop("jute", FarmingPatchKind.HOPS, 5306, 5931, 13, 13.0, 14.5, 5, 50, 56, 4, "jute"),
        crop("yanillian", FarmingPatchKind.HOPS, 5309, 5998, 16, 14.5, 16.0, 6, 60, 19, 4, "yanillian hops"),
        crop("krandorian", FarmingPatchKind.HOPS, 5310, 6000, 21, 17.5, 19.5, 7, 70, 28, 4, "krandorian hops"),
        crop("wildblood", FarmingPatchKind.HOPS, 5311, 6002, 28, 23.0, 26.0, 8, 80, 38, 4, "wildblood hops"),
        // Trees grow in 40-minute cycles.  Health XP is awarded once when a mature tree is checked.
        crop("oak", FarmingPatchKind.TREE, 5370, -1, 15, 14.0, 0.0, 4, 200, 8, 0, "oak tree", 467.3),
        crop("willow", FarmingPatchKind.TREE, 5371, -1, 30, 25.0, 0.0, 6, 280, 15, 0, "willow tree", 1456.5),
        crop("maple", FarmingPatchKind.TREE, 5372, -1, 45, 45.0, 0.0, 8, 320, 24, 0, "maple tree", 3403.4),
        crop("yew", FarmingPatchKind.TREE, 5373, -1, 60, 81.0, 0.0, 10, 400, 35, 0, "yew tree", 7069.0),
        crop("magic", FarmingPatchKind.TREE, 5374, -1, 75, 145.5, 0.0, 12, 480, 48, 0, "magic tree", 13768.3),
        crop("redwood", FarmingPatchKind.TREE, 22859, -1, 90, 230.0, 0.0, 11, 640, 8, 0, "redwood tree", 22450.0),
        crop("celastrus", FarmingPatchKind.TREE, 22856, 22842, 50, 500.0, 0.0, 6, 560, 8, 6, "celastrus tree", 5555.0),
        // Fruit trees: 6 × 160-minute cycles (16 hours total); fruit regrows in normal cycles after harvest.
        crop("apple", FarmingPatchKind.FRUIT_TREE, 5496, 1955, 27, 22.0, 8.5, 6, 960, 8, 6, "apple tree", 1199.5),
        crop("banana", FarmingPatchKind.FRUIT_TREE, 5497, 1963, 33, 28.0, 10.5, 6, 960, 35, 6, "banana tree", 1750.0),
        crop("orange", FarmingPatchKind.FRUIT_TREE, 5498, 2108, 39, 35.5, 13.5, 6, 960, 72, 6, "orange tree", 2470.2),
        crop("curry", FarmingPatchKind.FRUIT_TREE, 5499, 5970, 42, 40.0, 15.0, 6, 960, 99, 6, "curry tree", 2906.9),
        crop("pineapple", FarmingPatchKind.FRUIT_TREE, 5500, 2114, 51, 57.0, 21.5, 6, 960, 136, 6, "pineapple tree", 4605.7),
        crop("papaya", FarmingPatchKind.FRUIT_TREE, 5501, 5972, 57, 72.0, 27.0, 6, 960, 163, 6, "papaya tree", 6146.4),
        crop("palm", FarmingPatchKind.FRUIT_TREE, 5502, 5974, 68, 110.5, 41.5, 6, 960, 200, 6, "palm tree", 10150.1),
        crop("dragonfruit", FarmingPatchKind.FRUIT_TREE, 22866, 22929, 81, 140.0, 70.0, 6, 960, 200, 6, "dragonfruit tree", 17335.0),
        crop("redberry", FarmingPatchKind.BUSH, 5101, 1951, 10, 11.5, 4.5, 5, 320, 5, 4, "redberry bush", 64.0),
        crop("cadavaberry", FarmingPatchKind.BUSH, 5102, 753, 22, 18.0, 7.0, 6, 320, 15, 4, "cadavaberry bush", 102.5),
        crop("dwellberry", FarmingPatchKind.BUSH, 5103, 2126, 36, 31.5, 12.0, 7, 320, 26, 4, "dwellberry bush", 177.5),
        crop("jangerberry", FarmingPatchKind.BUSH, 5104, 247, 48, 50.5, 19.0, 8, 320, 38, 4, "jangerberry bush", 284.5),
        crop("whiteberry", FarmingPatchKind.BUSH, 5105, 239, 59, 78.0, 4.5, 8, 320, 51, 4, "whiteberry bush", 437.5),
        crop("poison_ivy", FarmingPatchKind.BUSH, 5106, 6018, 70, 120.0, 4.5, 8, 320, 197, 4, "poison ivy bush", 675.0),
        crop("cactus", FarmingPatchKind.CACTUS, 5280, 6016, 55, 66.5, 25.0, 7, 560, 8, 3, "potato cactus", 374.0),
        crop("calquat", FarmingPatchKind.CALQUAT, 5503, 5980, 72, 129.5, 48.5, 8, 1280, 4, 6, "calquat tree", 12096.0),
        crop("bittercap", FarmingPatchKind.MUSHROOM, 5282, 6004, 53, 61.5, 57.7, 6, 240, 4, 2, "bittercap mushroom"),
        crop("spirit_tree", FarmingPatchKind.SPIRIT_TREE, 5375, -1, 83, 199.0, 0.0, 11, 3520, 8, 0, "spirit tree", 19301.0),
        crop("attas", FarmingPatchKind.ANIMA, 22881, -1, 76, 100.0, 0.0, 5, 1280, 8, 0, "Attas plant", 555.0),
        crop("iasor", FarmingPatchKind.ANIMA, 22883, -1, 76, 100.0, 0.0, 5, 1280, 18, 0, "Iasor plant", 555.0),
        crop("kronos", FarmingPatchKind.ANIMA, 22885, -1, 76, 100.0, 0.0, 5, 1280, 28, 0, "Kronos plant", 555.0),
        crop("hespori", FarmingPatchKind.HESPORI, 22875, -1, 68, 68.0, 0.0, 4, 1920, 4, 0, "Hespori seed", 0.0),
    )

    val cropsByKey = crops.associateBy(FarmingCrop::key)
    val cropsByInput = crops.associateBy(FarmingCrop::inputId)
}
