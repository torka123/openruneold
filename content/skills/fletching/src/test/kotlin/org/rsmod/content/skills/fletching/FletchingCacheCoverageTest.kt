package org.rsmod.content.skills.fletching

import dev.openrune.ServerCacheManager
import kotlin.test.Test
import kotlin.test.assertNotNull

class FletchingCacheCoverageTest {
    @Test
    fun `all configured recipe items exist in revision 240`() {
        val cache = ServerCacheManager.init(240)
        try {
            for (recipe in FletchingDefinitions.recipes) {
                val ids = buildSet {
                    add(recipe.triggerFirstId)
                    add(recipe.triggerSecondId)
                    add(recipe.outputId)
                    recipe.ingredients.forEach { add(it.id) }
                    recipe.requiredToolId?.let(::add)
                }
                ids.forEach { id ->
                    assertNotNull(ServerCacheManager.getItem(id), "${recipe.key}: missing revision-240 item $id")
                }
            }
        } finally {
            cache.close()
        }
    }
}
