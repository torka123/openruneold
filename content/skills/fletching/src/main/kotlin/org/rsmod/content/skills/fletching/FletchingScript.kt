package org.rsmod.content.skills.fletching

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.fletchingLvl
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.slf4j.LoggerFactory

/** Revision-240 Fletching rebuilt from the donor behavior on OpenRune's protected action system. */
class FletchingScript @Inject constructor() : PluginScript() {
    override fun ScriptContext.startup() {
        val registered = registerInteractions()
        onPlayerQueueWithArgs(QUEUE) { processTick(it.args) }
        log.info(
            "Registered Fletching: {} recipes across {} item-on-item interactions",
            FletchingDefinitions.recipes.size,
            registered,
        )
    }

    private fun ScriptContext.registerInteractions(): Int {
        var count = 0
        for (interaction in FletchingDefinitions.interactions) {
            val first = ServerCacheManager.getItem(interaction.firstId)
            val second = ServerCacheManager.getItem(interaction.secondId)
            if (first == null || second == null) {
                log.warn(
                    "Skipping Fletching interaction {} + {} because revision 240 does not contain both items",
                    interaction.firstId,
                    interaction.secondId,
                )
                continue
            }
            onOpHeldU(first, second) { openMenu(interaction) }
            count++
        }
        return count
    }

    private suspend fun ProtectedAccess.openMenu(interaction: FletchingInteraction) {
        val offered = interaction.recipes.filter { maxSets(it, inv) > 0 }
        if (offered.isEmpty()) {
            mes("You don't have enough materials to fletch that.")
            return
        }

        val byOutput = offered.associateBy(FletchingRecipe::outputId)
        val config =
            SkillMultiConfig(
                actionType = interaction.actionType,
                verb = interaction.verb,
                entries =
                    offered.map { recipe ->
                        SkillMultiEntry(
                            internal = internalItem(recipe.outputId),
                            materials = emptyList(),
                        )
                    },
                maxCountProvider = { inventory, entry ->
                    val recipe = byOutput[entry.item.id] ?: return@SkillMultiConfig 0
                    maxSets(recipe, inventory)
                },
            )

        openSkillMulti(config) { selection ->
            val recipe = byOutput[selection.entry.item.id] ?: return@openSkillMulti
            startRecipe(recipe, selection.amount)
        }
    }

    private fun ProtectedAccess.startRecipe(recipe: FletchingRecipe, requestedSets: Int) {
        if (requestedSets <= 0) return
        clearQueue(QUEUE)
        processTick(FletchingTask(recipe.key, requestedSets, 0))
    }

    private fun ProtectedAccess.processTick(task: FletchingTask) {
        val recipe = FletchingDefinitions.recipesByKey[task.recipeKey] ?: return
        if (player.fletchingLvl < recipe.level) {
            mes("You need a Fletching level of ${recipe.level} to make that.")
            return
        }
        if (recipe.requiredToolId != null && inv.count(internalItem(recipe.requiredToolId)) == 0) {
            mes("You need a ${itemName(recipe.requiredToolId).lowercase()} to do that.")
            return
        }

        val units = unitsForNextAction(recipe, inv)
        if (units <= 0) return

        val outputCount = recipe.outputAmount * units
        val transaction =
            player.invTransaction(inv) {
                val inventory = select(inv)
                for (ingredient in recipe.ingredients) {
                    delete {
                        from = inventory
                        obj = ingredient.id
                        strictCount = ingredient.amount * units
                    }
                }
                insert {
                    into = inventory
                    obj = recipe.outputId
                    strictCount = outputCount
                }
            }
        if (!transaction.success) {
            log.warn("Atomic Fletching transaction failed for recipe {}", recipe.key)
            return
        }

        recipe.animation?.let(::anim)
        statAdvance(FLETCHING_STAT, recipe.baseXp * units)
        mes(successMessage(recipe, outputCount))

        val completed = task.completedSets + 1
        if (completed < task.requestedSets && maxSets(recipe, inv) > 0) {
            queue(QUEUE, recipe.delayTicks, task.copy(completedSets = completed))
        }
    }

    private fun unitsForNextAction(recipe: FletchingRecipe, inventory: Inventory): Int {
        val available =
            recipe.ingredients.minOf {
                inventory.count(internalItem(it.id)) / it.amount
            }
        return if (recipe.batchSize == 1) {
            if (available > 0) 1 else 0
        } else {
            available.coerceAtMost(recipe.batchSize)
        }
    }

    private fun maxSets(recipe: FletchingRecipe, inventory: Inventory): Int {
        if (recipe.requiredToolId != null && inventory.count(internalItem(recipe.requiredToolId)) == 0) {
            return 0
        }
        val available =
            recipe.ingredients.minOf {
                inventory.count(internalItem(it.id)) / it.amount
            }
        return if (recipe.batchSize == 1) {
            available
        } else {
            (available + recipe.batchSize - 1) / recipe.batchSize
        }
    }

    private fun successMessage(recipe: FletchingRecipe, amount: Int): String {
        val name = itemName(recipe.outputId).lowercase()
        return if (amount == 1) "You make a $name." else "You make $amount $name."
    }

    private fun itemName(id: Int): String =
        ServerCacheManager.getItem(id)?.name ?: "fletching item"

    private fun internalItem(id: Int): String = RSCM.getReverseMapping(RSCMType.OBJ, id)

    private data class FletchingTask(
        val recipeKey: String,
        val requestedSets: Int,
        val completedSets: Int,
    )

    private companion object {
        private const val QUEUE = "queue.fletching_production"
        private const val FLETCHING_STAT = "stat.fletching"
        private val log = LoggerFactory.getLogger(FletchingScript::class.java)
    }
}

private val FletchingDefinitions.recipesByKey: Map<String, FletchingRecipe>
    get() = RecipeIndex.byKey

private object RecipeIndex {
    val byKey = FletchingDefinitions.recipes.associateBy(FletchingRecipe::key)
}
