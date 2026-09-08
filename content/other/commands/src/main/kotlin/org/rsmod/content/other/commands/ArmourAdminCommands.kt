package org.rsmod.content.other.commands

import dev.openrune.audit.ArmourAudit
import dev.openrune.audit.ArmourAuditRow
import dev.openrune.audit.ArmourAuditSnapshot
import dev.openrune.audit.ArmourAuditWriter
import jakarta.inject.Inject
import java.nio.file.Files
import java.nio.file.Path
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.worn.HeldEquipResult
import org.rsmod.content.interfaces.bank.BankTab
import org.rsmod.game.cheat.Cheat
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Revision-aware administrator tools for the cache-first equipment audit. */
public class ArmourAdminCommands
@Inject
constructor(private val protectedAccess: ProtectedAccessLauncher) : PluginScript() {
    @Volatile private var cachedSnapshot: ArmourAuditSnapshot? = null

    override fun ScriptContext.startup() {
        onCommand("armouraudit", "Inspect one raw armour item definition", ::armourAudit) {
            invalidArgs = "Usage: ::armouraudit <itemId>"
        }
        onCommand("armourdiff", "Regenerate the revision-aware armour audit", ::armourDiff)
        onCommand(
            "armourtestbank",
            "Bank the first half of safe armour families",
            { armourTestBank(this, part = 1) },
        ) {
            invalidArgs =
                "Usage: ::armourtestbank [head|cape|neck|ammo|body|shield|legs|hands|feet|ring]"
        }
        onCommand(
            "armourtestbank2",
            "Bank the second half of safe armour families",
            { armourTestBank(this, part = 2) },
        ) {
            invalidArgs =
                "Usage: ::armourtestbank2 [head|cape|neck|ammo|body|shield|legs|hands|feet|ring]"
        }
        onCommand("modeltest", "Equip an item and print its cache appearance data", ::modelTest) {
            invalidArgs = "Usage: ::modeltest <itemId>"
        }
    }

    private fun armourAudit(cheat: Cheat) =
        with(cheat) {
            val id = args.singleOrNull()?.toIntOrNull()
            if (id == null) {
                player.mes("Usage: ::armouraudit <itemId>")
                return@with
            }
            val row = snapshot().items.firstOrNull { it.id == id }
            if (row == null) {
                player.mes("Item $id is not in the stat-bearing wearable audit scope.")
                return@with
            }
            printRow(row)
        }

    private fun armourDiff(cheat: Cheat) =
        with(cheat) {
            val fresh = ArmourAudit.scan(projectRoot, activeRevision)
            val output = projectRoot.resolve(Path.of("docs", "audit", "armour", "current"))
            val paths = ArmourAuditWriter.write(fresh, output)
            cachedSnapshot = fresh
            player.mes(
                "Armour audit: ${fresh.summary.scopedRawIds} raw IDs, " +
                    "${fresh.summary.normalizedFamilies} families, ${fresh.summary.mismatchRows} cache mismatches."
            )
            player.mes("JSON: ${paths.json}; CSV: ${paths.csv}")
            player.mes("Effects: ${paths.effects}; Models: ${paths.models}")
        }

    private fun armourTestBank(cheat: Cheat, part: Int) =
        with(cheat) {
            val command = if (part == 1) "armourtestbank" else "armourtestbank2"
            val usage = "Usage: ::$command [head|cape|neck|ammo|body|shield|legs|hands|feet|ring]"
            val requested = args.singleOrNull()?.let(::normalizeSlot)
            if (args.size > 1 || (args.isNotEmpty() && requested == null)) {
                player.mes(usage)
                return@with
            }
            val allCandidates =
                snapshot()
                    .items
                    .asSequence()
                    .filter { requested == null || it.slot.equals(requested, ignoreCase = true) }
                    .filter { it.degradeState != "BROKEN_STAGE" }
                    .groupBy(ArmourAuditRow::sourceFamily)
                    .values
                    .map { family -> family.minBy(ArmourAuditRow::id) }
                    .sortedBy(ArmourAuditRow::id)
            val splitIndex = (allCandidates.size + 1) / 2
            val candidates =
                if (part == 1) allCandidates.take(splitIndex) else allCandidates.drop(splitIndex)
            if (candidates.isEmpty()) {
                player.mes("No audited armour families matched that slot in test-bank part $part.")
                return@with
            }
            protectedAccess.launch(player) {
                val existing = bank.objs.asSequence().filterNotNull().map { it.id }.toHashSet()
                val missing = candidates.filterNot { it.id in existing }
                if (missing.isEmpty()) {
                    mes(
                        "Your bank already has all ${candidates.size} items from armour test-bank part $part."
                    )
                    return@launch
                }
                val result =
                    player.invTransaction(bank) {
                        val target = select(bank)
                        for (row in missing) {
                            add(target, obj = row.id, count = 1, strict = true)
                        }
                    }
                if (!result.success) {
                    mes("No items were added. Free ${missing.size} bank slots and try again.")
                    return@launch
                }
                BankTab.repairLayout(player, bank)
                mes(
                    "Added ${missing.size} safe armour test items from part $part " +
                        "(${candidates.size} in this part; ${allCandidates.size} total families)."
                )
            }
        }

    private fun modelTest(cheat: Cheat) =
        with(cheat) {
            val id = args.singleOrNull()?.toIntOrNull()
            if (id == null) {
                player.mes("Usage: ::modeltest <itemId>")
                return@with
            }
            val row = snapshot().items.firstOrNull { it.id == id }
            if (row == null) {
                player.mes("Item $id is not in the stat-bearing wearable audit scope.")
                return@with
            }
            protectedAccess.launch(player) {
                var slot = inv.objs.indexOfFirst { it?.id == id }
                if (slot == -1) {
                    val result =
                        player.invTransaction(inv) {
                            add(select(inv), obj = id, count = 1, strict = true)
                        }
                    if (!result.success) {
                        mes("Make one inventory slot available before using ::modeltest.")
                        return@launch
                    }
                    slot = inv.objs.indexOfFirst { it?.id == id }
                }
                when (val result = invEquip(slot)) {
                    is HeldEquipResult.Success ->
                        mes("Equipped ${row.name} in ${result.equipWearpos} for model testing.")
                    is HeldEquipResult.Fail -> result.messages.forEach(::mes)
                }
                printRow(row)
            }
        }

    private fun Cheat.printRow(row: ArmourAuditRow) {
        player.mes(
            "${row.name} (ID ${row.id}, ${row.internalName}) — ${row.slot}; status=${row.auditStatus}"
        )
        player.mes("Requirements: ${row.requirements.ifEmpty { listOf("none") }.joinToString()}")
        player.mes("Bonuses: ${row.stats.entries.joinToString { "${it.key}=${it.value}" }}")
        player.mes("Weight=${row.weight}; charge=${row.chargeState}; degrade=${row.degradeState}")
        player.mes(
            "Inv=${row.model.inventoryModel}; male=${row.model.maleWornModels}; " +
                "female=${row.model.femaleWornModels}; headM=${row.model.maleHeadModels}; headF=${row.model.femaleHeadModels}"
        )
        player.mes(
            "Recolours=${row.model.recolourFrom.zip(row.model.recolourTo)}; " +
                "retextures=${row.model.retextureFrom.zip(row.model.retextureTo)}"
        )
        player.mes(
            "Effects: ${row.effects.ifEmpty { listOf("none") }.joinToString()}; source=${row.sourceFamily}"
        )
    }

    private fun snapshot(): ArmourAuditSnapshot =
        cachedSnapshot
            ?: synchronized(this) {
                cachedSnapshot
                    ?: ArmourAudit.scan(projectRoot, activeRevision).also { cachedSnapshot = it }
            }

    private fun normalizeSlot(value: String): String? =
        when (value.lowercase()) {
            "head",
            "helm",
            "helmet" -> "Head"
            "cape",
            "back" -> "Cape"
            "neck",
            "amulet",
            "necklace" -> "Neck"
            "ammo",
            "ammunition",
            "quiver" -> "Ammunition"
            "body",
            "torso",
            "chest" -> "Body"
            "shield",
            "offhand",
            "off-hand" -> "Shield"
            "legs",
            "leg" -> "Legs"
            "hands",
            "hand",
            "gloves" -> "Hands"
            "feet",
            "foot",
            "boots" -> "Feet"
            "ring" -> "Ring"
            else -> null
        }

    private val projectRoot: Path by lazy {
        Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
    }

    private val activeRevision: Int by lazy {
        Files.readAllLines(projectRoot.resolve("game.yml"))
            .first { it.trimStart().startsWith("revision:") }
            .substringAfter("revision:")
            .trim()
            .substringBefore('.')
            .toInt()
    }
}
