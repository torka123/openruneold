package org.rsmod.content.bosses.diagnostics

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.bosses.runtime.BossExtensionRegistry
import org.rsmod.api.bosses.runtime.EncounterRegistry
import org.rsmod.api.bosses.validation.BossAssetValidator
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.game.cheat.Cheat
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Administrator-only inspection of boss specs that are actually registered in this server process. */
class BossCheckCommand
@Inject
constructor(
    private val encounters: EncounterRegistry,
    private val extensions: BossExtensionRegistry,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("bosscheck") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Validate a loaded boss spec or all loaded boss specs"
            invalidArgs = "Use as ::bosscheck <boss-slug|all>"
            cheat { bossCheck(this) }
        }
    }

    private fun bossCheck(cheat: Cheat) =
        with(cheat) {
            val requested = args.firstOrNull()?.lowercase()
            if (requested == null) {
                player.mes("Use as ::bosscheck <boss-slug|all>")
                return@with
            }
            val bosses = encounters.registeredBosses()
            if (requested == "all") {
                val results = bosses.mapValues { BossAssetValidator.validate(it.value, extensions) }
                val failed = results.filterValues { !it.valid }
                player.mes("Boss check: ${bosses.size} loaded owner(s); ${bosses.size - failed.size} valid, ${failed.size} invalid.")
                failed.entries.chunked(3).forEach { chunk ->
                    player.mes(chunk.joinToString("; ") { (slug, result) -> "$slug=${result.missing.size} missing" })
                }
                player.mes("Production access and reward loops remain module-specific and are not inferred from combat registration.")
                return@with
            }

            val spec = encounters.registeredBoss(requested)
            if (spec == null) {
                player.mes("Boss '$requested' is not loaded. Loaded: ${bosses.keys.sorted().joinToString()}.")
                return@with
            }
            val result = BossAssetValidator.validate(spec, extensions)
            player.mes("Boss $requested: ${if (result.valid) "definitions valid" else "INVALID (${result.missing.size} missing)"}.")
            player.mes("NPC forms: ${spec.npcTypes.joinToString()}.")
            player.mes("Phases: ${result.phases.ifEmpty { listOf("none") }.joinToString()}.")
            player.mes("Production access: module-specific; this command never teleports or grants rewards.")
            result.missing.chunked(2).forEach { chunk -> player.mes("Missing: ${chunk.joinToString()}") }
        }
}
