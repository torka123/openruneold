package org.rsmod.content.skills.fletching

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.game.cheat.Cheat
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Administrator test kit generated directly from the live Fletching recipe registry. */
class FletchingTestBankCommand
@Inject
constructor(private val protectedAccess: ProtectedAccessLauncher) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("fletchingtestbank") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Bank 1,000 of every material used by the live Fletching recipes"
            invalidArgs = "Usage: ::fletchingtestbank"
            cheat { populateTestBank(this) }
        }
        onCommand("fletchtestbank") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Bank 1,000 of every material used by the live Fletching recipes"
            invalidArgs = "Usage: ::fletchtestbank"
            cheat { populateTestBank(this) }
        }
    }

    private fun populateTestBank(cheat: Cheat) =
        with(cheat) {
            if (args.isNotEmpty()) {
                player.mes("Usage: ::fletchingtestbank")
                return@with
            }
            protectedAccess.launch(player) {
                val additions =
                    FletchingTestBankMaterials.ids
                        .associateWith { id ->
                            val existing =
                                bank.objs
                                    .asSequence()
                                    .filterNotNull()
                                    .filter { it.id == id }
                                    .sumOf { it.count }
                            (MATERIAL_COUNT - existing).coerceAtLeast(0)
                        }
                        .filterValues { it > 0 }

                if (additions.isEmpty()) {
                    mes(
                        "Your bank already contains at least $MATERIAL_COUNT of all " +
                            "${FletchingTestBankMaterials.ids.size} Fletching materials and tools."
                    )
                    return@launch
                }

                val requiredSlots = additions.keys.count { id -> bank.objs.none { it?.id == id } }
                val result =
                    player.invTransaction(bank) {
                        val target = select(bank)
                        for ((id, count) in additions) {
                            add(target, obj = id, count = count, strict = true)
                        }
                    }
                if (!result.success) {
                    mes("No materials were added. Free $requiredSlots bank slots and try again.")
                    return@launch
                }
                mes(
                    "Your bank now contains at least $MATERIAL_COUNT of every one of the " +
                        "${FletchingTestBankMaterials.ids.size} live Fletching materials and tools."
                )
            }
        }

    private companion object {
        private const val MATERIAL_COUNT = 1_000
    }
}

internal object FletchingTestBankMaterials {
    val ids: List<Int> =
        FletchingDefinitions.recipes
            .flatMap { recipe ->
                recipe.ingredients.map(FletchingIngredient::id) + listOfNotNull(recipe.requiredToolId)
            }
            .distinct()
            .sorted()
}
