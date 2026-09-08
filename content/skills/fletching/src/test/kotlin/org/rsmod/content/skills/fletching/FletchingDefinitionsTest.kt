package org.rsmod.content.skills.fletching

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FletchingDefinitionsTest {
    @Test
    fun `recipe keys are unique and every recipe is reachable`() {
        val recipes = FletchingDefinitions.recipes
        assertEquals(recipes.size, recipes.map(FletchingRecipe::key).toSet().size)
        val reachable = FletchingDefinitions.interactions.flatMap(FletchingInteraction::recipes)
        assertEquals(recipes.toSet(), reachable.toSet())
    }

    @Test
    fun `corrected current OSRS values replace stale donor values`() {
        assertRecipe("oak_longbow_u", level = 25, xp = 25.0)
        assertRecipe("maple_longbow_u", level = 55, xp = 58.3)
        assertRecipe("maple_longbow", level = 55, xp = 58.2)
        assertRecipe("yew_stock", level = 69, xp = 50.0)
        assertRecipe("sapphire_bolts", level = 56, xp = 4.7)
        assertRecipe("emerald_bolts", level = 58, xp = 5.5)
        assertRecipe("onyx_bolts", level = 73, xp = 9.4)
        assertRecipe("rune_javelins", level = 77, xp = 12.5)
        assertRecipe("light_ballista", level = 47, xp = 300.0)
    }

    @Test
    fun `ammunition uses OSRS batch sizes and partial final batches are supported`() {
        val arrows = recipe("bronze_arrows")
        val darts = recipe("bronze_darts")
        assertEquals(15, arrows.batchSize)
        assertEquals(10, darts.batchSize)
        assertEquals(1, arrows.ingredients[0].amount)
        assertEquals(1, arrows.ingredients[1].amount)
    }

    @Test
    fun `shields consume two logs while tools are never ingredients`() {
        val shield = recipe("redwood_shield")
        assertEquals(listOf(FletchingIngredient(19669, 2)), shield.ingredients)
        assertEquals(FletchingDefinitions.KNIFE, shield.requiredToolId)
        assertTrue(shield.ingredients.none { it.id == FletchingDefinitions.KNIFE })
    }

    private fun assertRecipe(key: String, level: Int, xp: Double) {
        val recipe = recipe(key)
        assertEquals(level, recipe.level)
        assertEquals(xp, recipe.baseXp)
    }

    private fun recipe(key: String): FletchingRecipe =
        assertNotNull(FletchingDefinitions.recipes.singleOrNull { it.key == key })
}
