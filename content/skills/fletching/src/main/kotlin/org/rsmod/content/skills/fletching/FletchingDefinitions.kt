package org.rsmod.content.skills.fletching

import org.rsmod.content.skills.SkillingActionType

internal data class FletchingIngredient(
    val id: Int,
    val amount: Int = 1,
)

/**
 * A recipe awards [baseXp] per produced unit for batched ammunition and once per action for every
 * other recipe. The global/account XP multiplier is deliberately not represented here; OpenRune's
 * statAdvance API applies it exactly once.
 */
internal data class FletchingRecipe(
    val key: String,
    val level: Int,
    val baseXp: Double,
    val outputId: Int,
    val outputAmount: Int,
    val ingredients: List<FletchingIngredient>,
    val triggerFirstId: Int,
    val triggerSecondId: Int,
    val actionType: SkillingActionType,
    val verb: String,
    val animation: Int?,
    val delayTicks: Int,
    val batchSize: Int = 1,
    val requiredToolId: Int? = null,
) {
    init {
        require(level in 1..99)
        require(baseXp >= 0.0)
        require(outputAmount > 0)
        require(ingredients.isNotEmpty())
        require(ingredients.all { it.amount > 0 })
        require(delayTicks > 0)
        require(batchSize > 0)
        if (batchSize > 1) {
            require(outputAmount == 1) { "Batched recipes produce one output per consumed unit" }
        }
    }
}

internal data class FletchingInteraction(
    val firstId: Int,
    val secondId: Int,
    val actionType: SkillingActionType,
    val verb: String,
    val recipes: List<FletchingRecipe>,
)

internal object FletchingDefinitions {
    const val KNIFE = 946
    const val CHISEL = 1755
    const val HAMMER = 2347
    const val FEATHER = 314
    const val BOW_STRING = 1777
    const val CROSSBOW_STRING = 9438

    val recipes: List<FletchingRecipe> = buildList {
        addLogRecipes()
        addBowStringingRecipes()
        addArrowRecipes()
        addDartAndBoltRecipes()
        addJavelinRecipes()
        addCrossbowRecipes()
        addBoltTipRecipes()
        addTippedBoltRecipes()
        addBallistaRecipes()
    }

    val interactions: List<FletchingInteraction> =
        recipes
            .groupBy {
                InteractionKey(
                    firstId = it.triggerFirstId,
                    secondId = it.triggerSecondId,
                    actionType = it.actionType,
                    verb = it.verb,
                )
            }
            .map { (key, grouped) ->
                FletchingInteraction(
                    firstId = key.firstId,
                    secondId = key.secondId,
                    actionType = key.actionType,
                    verb = key.verb,
                    recipes = grouped,
                )
            }

    private data class InteractionKey(
        val firstId: Int,
        val secondId: Int,
        val actionType: SkillingActionType,
        val verb: String,
    )

    private fun MutableList<FletchingRecipe>.addLogRecipes() {
        fun log(
            key: String,
            logId: Int,
            outputId: Int,
            outputAmount: Int,
            level: Int,
            xp: Double,
            logsUsed: Int = 1,
        ) = fixed(
            key = key,
            first = KNIFE,
            second = logId,
            output = outputId,
            outputAmount = outputAmount,
            level = level,
            xp = xp,
            ingredients = listOf(FletchingIngredient(logId, logsUsed)),
            action = SkillingActionType.CARVE,
            verb = "fletch",
            animation = 1248,
            delay = 3,
            tool = KNIFE,
        )

        log("normal_arrow_shafts", 1511, 52, 15, 1, 5.0)
        log("normal_javelin_shafts", 1511, 19584, 15, 3, 5.0)
        log("normal_shortbow_u", 1511, 50, 1, 5, 5.0)
        log("normal_longbow_u", 1511, 48, 1, 10, 10.0)
        log("wooden_stock", 1511, 9440, 1, 9, 6.0)

        log("oak_arrow_shafts", 1521, 52, 30, 15, 10.0)
        log("oak_shortbow_u", 1521, 54, 1, 20, 16.5)
        log("oak_longbow_u", 1521, 56, 1, 25, 25.0)
        log("oak_stock", 1521, 9442, 1, 24, 16.0)
        log("oak_shield", 1521, 22251, 1, 27, 50.0, logsUsed = 2)

        log("willow_arrow_shafts", 1519, 52, 45, 30, 15.0)
        log("willow_shortbow_u", 1519, 60, 1, 35, 33.3)
        log("willow_longbow_u", 1519, 58, 1, 40, 41.5)
        log("willow_stock", 1519, 9444, 1, 39, 22.0)
        log("willow_shield", 1519, 22254, 1, 42, 83.0, logsUsed = 2)

        log("teak_stock", 6333, 9446, 1, 46, 27.0)

        log("maple_arrow_shafts", 1517, 52, 60, 45, 20.0)
        log("maple_shortbow_u", 1517, 64, 1, 50, 50.0)
        log("maple_longbow_u", 1517, 62, 1, 55, 58.3)
        log("maple_stock", 1517, 9448, 1, 54, 32.0)
        log("maple_shield", 1517, 22257, 1, 57, 116.5, logsUsed = 2)

        log("mahogany_stock", 6332, 9450, 1, 61, 41.0)

        log("yew_arrow_shafts", 1515, 52, 75, 60, 25.0)
        log("yew_shortbow_u", 1515, 68, 1, 65, 67.5)
        log("yew_longbow_u", 1515, 66, 1, 70, 75.0)
        log("yew_stock", 1515, 9452, 1, 69, 50.0)
        log("yew_shield", 1515, 22260, 1, 72, 150.0, logsUsed = 2)

        log("magic_arrow_shafts", 1513, 52, 90, 75, 30.0)
        log("magic_shortbow_u", 1513, 72, 1, 80, 83.3)
        log("magic_longbow_u", 1513, 70, 1, 85, 91.5)
        log("magic_stock", 1513, 21952, 1, 78, 70.0)
        log("magic_shield", 1513, 22263, 1, 87, 183.0, logsUsed = 2)

        log("redwood_arrow_shafts", 19669, 52, 105, 90, 35.0)
        log("redwood_shield", 19669, 22266, 1, 92, 216.0, logsUsed = 2)
    }

    private fun MutableList<FletchingRecipe>.addBowStringingRecipes() {
        fun bow(key: String, unstrung: Int, output: Int, level: Int, xp: Double, animation: Int) =
            fixed(
                key = key,
                first = unstrung,
                second = BOW_STRING,
                output = output,
                level = level,
                xp = xp,
                ingredients = listOf(FletchingIngredient(unstrung), FletchingIngredient(BOW_STRING)),
                action = SkillingActionType.STRING,
                verb = "string",
                animation = animation,
                delay = 2,
            )

        bow("shortbow", 50, 841, 5, 5.0, 6678)
        bow("longbow", 48, 839, 10, 10.0, 6684)
        bow("oak_shortbow", 54, 843, 20, 16.5, 6679)
        bow("oak_longbow", 56, 845, 25, 25.0, 6685)
        bow("comp_ogre_bow", 4825, 4827, 30, 45.0, 6686)
        bow("willow_shortbow", 60, 849, 35, 33.2, 6680)
        bow("willow_longbow", 58, 847, 40, 41.5, 6686)
        bow("maple_shortbow", 64, 853, 50, 50.0, 6681)
        bow("maple_longbow", 62, 851, 55, 58.2, 6687)
        bow("yew_shortbow", 68, 857, 65, 67.5, 6682)
        bow("yew_longbow", 66, 855, 70, 75.0, 6688)
        bow("magic_shortbow", 72, 861, 80, 83.3, 6683)
        bow("magic_longbow", 70, 859, 85, 91.5, 6689)
    }

    private fun MutableList<FletchingRecipe>.addArrowRecipes() {
        batch("headless_arrows", 52, FEATHER, 53, 1, 1.0, 15, 8480)
        batch("bronze_arrows", 53, 39, 882, 1, 1.3, 15, 8480)
        batch("iron_arrows", 53, 40, 884, 15, 2.5, 15, 8480)
        batch("steel_arrows", 53, 41, 886, 30, 5.0, 15, 8480)
        batch("mithril_arrows", 53, 42, 888, 45, 7.5, 15, 8480)
        batch("broad_arrows", 53, 11874, 4160, 52, 10.0, 15, 8480)
        batch("adamant_arrows", 53, 43, 890, 60, 10.0, 15, 8480)
        batch("rune_arrows", 53, 44, 892, 75, 12.5, 15, 8480)
        batch("amethyst_arrows", 53, 21350, 21326, 82, 13.5, 15, 8480)
        batch("dragon_arrows", 53, 11237, 11212, 90, 15.0, 15, 8480)
    }

    private fun MutableList<FletchingRecipe>.addDartAndBoltRecipes() {
        batch("bronze_darts", 819, FEATHER, 806, 10, 1.8, 10, 8482, delay = 1)
        batch("iron_darts", 820, FEATHER, 807, 22, 3.8, 10, 8483, delay = 1)
        batch("steel_darts", 821, FEATHER, 808, 37, 7.5, 10, 8484, delay = 1)
        batch("mithril_darts", 822, FEATHER, 809, 52, 11.2, 10, 8485, delay = 1)
        batch("adamant_darts", 823, FEATHER, 810, 67, 15.0, 10, 8486, delay = 1)
        batch("rune_darts", 824, FEATHER, 811, 81, 18.8, 10, 8487, delay = 1)
        batch("amethyst_darts", 25853, FEATHER, 25849, 90, 21.0, 10, 8488, delay = 1)
        batch("dragon_darts", 11232, FEATHER, 11230, 95, 25.0, 10, 8488, delay = 1)

        batch("bronze_bolts", 9375, FEATHER, 877, 9, 0.5, 10, 8463, delay = 1)
        batch("blurite_bolts", 9376, FEATHER, 9139, 24, 1.0, 10, 8464, delay = 1)
        batch("iron_bolts", 9377, FEATHER, 9140, 39, 1.5, 10, 8464, delay = 1)
        batch("silver_bolts", 9382, FEATHER, 9145, 43, 2.5, 10, 8467, delay = 1)
        batch("steel_bolts", 9378, FEATHER, 9141, 46, 3.5, 10, 8467, delay = 1)
        batch("mithril_bolts", 9379, FEATHER, 9142, 54, 5.0, 10, 8468, delay = 1)
        batch("broad_bolts", 11875, FEATHER, 11876, 55, 3.0, 10, 8467, delay = 1)
        batch("adamant_bolts", 9380, FEATHER, 9143, 61, 7.0, 10, 8469, delay = 1)
        batch("rune_bolts", 9381, FEATHER, 9144, 69, 10.0, 10, 8470, delay = 1)
        batch("dragon_bolts", 21930, FEATHER, 21905, 84, 12.0, 10, 8471, delay = 1)
    }

    private fun MutableList<FletchingRecipe>.addJavelinRecipes() {
        batch("bronze_javelins", 19584, 19570, 825, 3, 1.0, 15, 8480)
        batch("iron_javelins", 19584, 19572, 826, 17, 2.0, 15, 8480)
        batch("steel_javelins", 19584, 19574, 827, 32, 5.0, 15, 8480)
        batch("mithril_javelins", 19584, 19576, 828, 47, 8.0, 15, 8480)
        batch("adamant_javelins", 19584, 19578, 829, 62, 10.0, 15, 8480)
        batch("rune_javelins", 19584, 19580, 830, 77, 12.5, 15, 8480)
        batch("amethyst_javelins", 19584, 21352, 21318, 84, 13.5, 15, 8480)
        batch("dragon_javelins", 19584, 19582, 19484, 92, 15.0, 15, 8480)
    }

    private fun MutableList<FletchingRecipe>.addCrossbowRecipes() {
        fun assemble(key: String, limbs: Int, stock: Int, output: Int, level: Int, xp: Double, animation: Int) =
            fixed(
                key = "${key}_u",
                first = limbs,
                second = stock,
                output = output,
                level = level,
                xp = xp,
                ingredients = listOf(FletchingIngredient(limbs), FletchingIngredient(stock)),
                action = SkillingActionType.MAKE,
                verb = "assemble",
                animation = animation,
                delay = 2,
                tool = HAMMER,
            )

        fun string(key: String, unstrung: Int, output: Int, level: Int, xp: Double, animation: Int) =
            fixed(
                key = key,
                first = unstrung,
                second = CROSSBOW_STRING,
                output = output,
                level = level,
                xp = xp,
                ingredients = listOf(FletchingIngredient(unstrung), FletchingIngredient(CROSSBOW_STRING)),
                action = SkillingActionType.STRING,
                verb = "string",
                animation = animation,
                delay = 2,
            )

        assemble("bronze_crossbow", 9420, 9440, 9454, 9, 12.0, 4436)
        assemble("blurite_crossbow", 9422, 9442, 9456, 24, 32.0, 4437)
        assemble("iron_crossbow", 9423, 9444, 9457, 39, 44.0, 4438)
        assemble("steel_crossbow", 9425, 9446, 9459, 46, 54.0, 4439)
        assemble("mithril_crossbow", 9427, 9448, 9461, 54, 64.0, 4440)
        assemble("adamant_crossbow", 9429, 9450, 9463, 61, 82.0, 4441)
        assemble("rune_crossbow", 9431, 9452, 9465, 69, 100.0, 4442)
        assemble("dragon_crossbow", 21918, 21952, 21921, 78, 135.0, 7860)

        string("bronze_crossbow", 9454, 9174, 9, 6.0, 6671)
        string("blurite_crossbow", 9456, 9176, 24, 16.0, 6672)
        string("iron_crossbow", 9457, 9177, 39, 22.0, 6673)
        string("steel_crossbow", 9459, 9179, 46, 27.0, 6674)
        string("mithril_crossbow", 9461, 9181, 54, 32.0, 6675)
        string("adamant_crossbow", 9463, 9183, 61, 41.0, 6676)
        string("rune_crossbow", 9465, 9185, 69, 50.0, 6677)
        string("dragon_crossbow", 21921, 21902, 78, 70.0, 7961)
    }

    private fun MutableList<FletchingRecipe>.addBoltTipRecipes() {
        fun tips(key: String, gem: Int, output: Int, outputAmount: Int, level: Int, xp: Double, animation: Int) =
            fixed(
                key = "${key}_bolt_tips",
                first = CHISEL,
                second = gem,
                output = output,
                outputAmount = outputAmount,
                level = level,
                xp = xp,
                ingredients = listOf(FletchingIngredient(gem)),
                action = SkillingActionType.CUT,
                verb = "cut",
                animation = animation,
                delay = 2,
                tool = CHISEL,
            )

        tips("opal", 1609, 45, 12, 11, 1.5, 890)
        tips("jade", 1611, 9187, 12, 26, 2.0, 891)
        tips("pearl", 411, 46, 6, 41, 3.2, 886)
        tips("oyster_pearl", 413, 46, 24, 41, 3.2, 886)
        tips("topaz", 1613, 9188, 12, 48, 3.9, 892)
        tips("sapphire", 1607, 9189, 12, 56, 4.0, 888)
        tips("emerald", 1605, 9190, 12, 58, 5.5, 889)
        tips("ruby", 1603, 9191, 12, 63, 6.0, 887)
        tips("diamond", 1601, 9192, 12, 65, 7.0, 886)
        tips("dragonstone", 1615, 9193, 12, 71, 8.2, 885)
        tips("onyx", 6573, 9194, 24, 73, 9.4, 2717)
    }

    private fun MutableList<FletchingRecipe>.addTippedBoltRecipes() {
        fun tip(key: String, bolts: Int, tips: Int, output: Int, level: Int, xp: Double) =
            batch(key, bolts, tips, output, level, xp, 10, animation = null)

        tip("opal_bolts", 877, 45, 879, 11, 1.6)
        tip("jade_bolts", 9139, 9187, 9335, 26, 2.4)
        tip("pearl_bolts", 9140, 46, 880, 41, 3.2)
        tip("topaz_bolts", 9141, 9188, 9336, 48, 3.9)
        tip("sapphire_bolts", 9142, 9189, 9337, 56, 4.7)
        tip("emerald_bolts", 9142, 9190, 9338, 58, 5.5)
        tip("ruby_bolts", 9143, 9191, 9339, 63, 6.3)
        tip("diamond_bolts", 9143, 9192, 9340, 65, 7.0)
        tip("dragonstone_bolts", 9144, 9193, 9341, 71, 8.2)
        tip("onyx_bolts", 9144, 9194, 9342, 73, 9.4)
        tip("amethyst_broad_bolts", 11876, 21338, 21316, 76, 10.6)

        tip("opal_dragon_bolts", 21905, 45, 21955, 84, 1.6)
        tip("jade_dragon_bolts", 21905, 9187, 21957, 84, 2.4)
        tip("pearl_dragon_bolts", 21905, 46, 21959, 84, 3.2)
        tip("topaz_dragon_bolts", 21905, 9188, 21961, 84, 3.9)
        tip("sapphire_dragon_bolts", 21905, 9189, 21963, 84, 4.7)
        tip("emerald_dragon_bolts", 21905, 9190, 21965, 84, 5.5)
        tip("ruby_dragon_bolts", 21905, 9191, 21967, 84, 6.3)
        tip("diamond_dragon_bolts", 21905, 9192, 21969, 84, 7.0)
        tip("dragonstone_dragon_bolts", 21905, 9193, 21971, 84, 8.2)
        tip("onyx_dragon_bolts", 21905, 9194, 21973, 84, 9.4)
    }

    private fun MutableList<FletchingRecipe>.addBallistaRecipes() {
        fixed("incomplete_light_ballista", 19592, 19586, 19595, 47, 15.0, listOf(FletchingIngredient(19592), FletchingIngredient(19586)), SkillingActionType.MAKE, "assemble", 7172, 4)
        fixed("incomplete_heavy_ballista", 19592, 19589, 19598, 72, 30.0, listOf(FletchingIngredient(19592), FletchingIngredient(19589)), SkillingActionType.MAKE, "assemble", 7172, 4)
        fixed("unstrung_light_ballista", 19601, 19595, 19604, 47, 15.0, listOf(FletchingIngredient(19601), FletchingIngredient(19595)), SkillingActionType.MAKE, "assemble", 7172, 4)
        fixed("unstrung_heavy_ballista", 19601, 19598, 19607, 72, 30.0, listOf(FletchingIngredient(19601), FletchingIngredient(19598)), SkillingActionType.MAKE, "assemble", 7172, 4)
        fixed("light_ballista", 19610, 19604, 19478, 47, 300.0, listOf(FletchingIngredient(19610), FletchingIngredient(19604)), SkillingActionType.MAKE, "assemble", 7172, 4)
        fixed("heavy_ballista", 19610, 19607, 19481, 72, 600.0, listOf(FletchingIngredient(19610), FletchingIngredient(19607)), SkillingActionType.MAKE, "assemble", 7172, 4)
    }

    private fun MutableList<FletchingRecipe>.fixed(
        key: String,
        first: Int,
        second: Int,
        output: Int,
        level: Int,
        xp: Double,
        ingredients: List<FletchingIngredient>,
        action: SkillingActionType,
        verb: String,
        animation: Int?,
        delay: Int,
        outputAmount: Int = 1,
        tool: Int? = null,
    ) {
        add(
            FletchingRecipe(
                key = key,
                level = level,
                baseXp = xp,
                outputId = output,
                outputAmount = outputAmount,
                ingredients = ingredients,
                triggerFirstId = first,
                triggerSecondId = second,
                actionType = action,
                verb = verb,
                animation = animation,
                delayTicks = delay,
                requiredToolId = tool,
            )
        )
    }

    private fun MutableList<FletchingRecipe>.batch(
        key: String,
        first: Int,
        second: Int,
        output: Int,
        level: Int,
        xpPerUnit: Double,
        batchSize: Int,
        animation: Int?,
        delay: Int = 2,
    ) {
        add(
            FletchingRecipe(
                key = key,
                level = level,
                baseXp = xpPerUnit,
                outputId = output,
                outputAmount = 1,
                ingredients = listOf(FletchingIngredient(first), FletchingIngredient(second)),
                triggerFirstId = first,
                triggerSecondId = second,
                actionType = SkillingActionType.MAKE_SETS,
                verb = "make",
                animation = animation,
                delayTicks = delay,
                batchSize = batchSize,
            )
        )
    }
}
