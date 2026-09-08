package org.rsmod.content.skills.fletching

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FletchingTestBankMaterialsTest {
    @Test
    fun `test bank covers every recipe ingredient and reusable tool exactly once`() {
        val required =
            FletchingDefinitions.recipes
                .flatMap { recipe ->
                    recipe.ingredients.map(FletchingIngredient::id) +
                        listOfNotNull(recipe.requiredToolId)
                }
                .distinct()
                .sorted()

        assertEquals(required, FletchingTestBankMaterials.ids)
        assertEquals(FletchingTestBankMaterials.ids.size, FletchingTestBankMaterials.ids.toSet().size)
        assertTrue(FletchingDefinitions.KNIFE in FletchingTestBankMaterials.ids)
        assertTrue(FletchingDefinitions.CHISEL in FletchingTestBankMaterials.ids)
        assertTrue(FletchingDefinitions.HAMMER in FletchingTestBankMaterials.ids)
        assertTrue(FletchingDefinitions.FEATHER in FletchingTestBankMaterials.ids)
        assertTrue(52 in FletchingTestBankMaterials.ids) // Arrow shafts.
    }
}
