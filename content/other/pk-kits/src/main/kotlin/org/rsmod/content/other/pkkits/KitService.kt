@file:OptIn(org.rsmod.annotations.InternalApi::class)

package org.rsmod.content.other.pkkits

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.player.disablePrayers
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.PlayerSkillXP
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.ui.PlayerInterfaceUpdates
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.setActiveMoveSpeed
import org.rsmod.api.specials.energy.SpecialAttackEnergy
import org.rsmod.api.spells.autocast.AutocastWeapons
import org.rsmod.api.spells.autocast.MagicSpellbookManager
import org.rsmod.content.interfaces.bank.BankTab
import org.rsmod.content.interfaces.bank.bankCapacity
import org.rsmod.content.interfaces.bank.configs.BankConstants
import org.rsmod.content.interfaces.prayer.tab.PrayerRepository
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.stat.PlayerSkillXPTable

@Singleton
public class KitService
@Inject
constructor(
    private val registry: StarterKitRegistry,
    private val persistence: KitPersistence,
    private val itemPolicy: KitItemPolicy,
    private val planner: KitBankPlanner,
    private val restrictions: KitRestrictionService,
    private val spellbooks: MagicSpellbookManager,
    private val autocast: AutocastWeapons,
    private val attackStyles: AttackStyles,
    private val prayers: PrayerRepository,
) {
    private val logger = InlineLogger()
    private val loadingPlayers = ConcurrentHashMap.newKeySet<Player>()

    public fun starterKits(): List<KitDefinition> = registry.all()

    public fun customKits(player: Player, includeLocked: Boolean = true): List<CustomKit> {
        val data = persistence.read(player).data
        val capacity = KitSlotManager.capacity(data.slotTier)
        return data.customKits
            .filter { includeLocked || it.slot < capacity }
            .sortedBy(CustomKit::slot)
    }

    public fun resolve(player: Player, query: String?): KitDefinition? {
        val trimmed = query?.trim().orEmpty()
        val data = persistence.read(player).data
        if (trimmed.isBlank()) {
            return data.defaultKit?.let { resolve(player, it) }
        }
        registry.get(trimmed.lowercase())?.let {
            return it
        }
        val capacity = KitSlotManager.capacity(data.slotTier)
        val slot = trimmed.toIntOrNull()?.minus(1)
        val custom =
            data.customKits.firstOrNull {
                it.slot < capacity &&
                    (it.id.equals(trimmed, true) ||
                        it.name.equals(trimmed, true) ||
                        it.slot == slot)
            }
        return custom?.asDefinition(itemPolicy.riskValue(custom.inventory + custom.equipment))
    }

    public fun resolve(player: Player, reference: KitReference): KitDefinition? =
        when (reference.source) {
            KitSource.PREMADE -> registry.get(reference.id)
            KitSource.CUSTOM -> {
                val data = persistence.read(player).data
                val capacity = KitSlotManager.capacity(data.slotTier)
                val custom =
                    data.customKits.firstOrNull { it.id == reference.id && it.slot < capacity }
                custom?.asDefinition(itemPolicy.riskValue(custom.inventory + custom.equipment))
            }
        }

    public fun preview(definition: KitDefinition): KitPreview {
        val free = mutableListOf<KitItem>()
        val owned = mutableListOf<KitItem>()
        val forbidden = mutableListOf<KitItem>()
        for (item in definition.inventory + definition.equipment) {
            when (itemPolicy.access(definition, item)) {
                KitItemAccess.FREE -> free += item
                KitItemAccess.OWNED -> owned += item
                KitItemAccess.FORBIDDEN -> forbidden += item
            }
        }
        return KitPreview(definition, free, owned, forbidden)
    }

    /** Read-only preflight used by the dialogue preview. The real load repeats every check. */
    public fun readiness(access: ProtectedAccess, definition: KitDefinition): List<String> {
        val player = access.player
        val restrictions = restrictions.validate(player)
        if (restrictions.isNotEmpty()) return restrictions
        val validation = registry.validate(definition)
        if (validation.isNotEmpty()) return validation
        val quickPrayerErrors = validateQuickPrayers(player, definition)
        if (quickPrayerErrors.isNotEmpty()) return quickPrayerErrors
        val unbankable = unbankableItems(player)
        if (unbankable.isNotEmpty()) {
            return listOf(
                "Bank or remove these non-bankable items first: ${unbankable.joinToString()}."
            )
        }
        val bank = access.bank
        val bankCapacity = player.bankCapacity.takeIf { it > 0 } ?: BankConstants.default_capacity
        return when (
            val result =
                planner.plan(
                    definition = definition,
                    currentInventory = copy(player.inv),
                    currentEquipment = copy(player.worn),
                    currentBank = copy(bank),
                    bankCapacity = bankCapacity,
                )
        ) {
            is KitPlanResult.Failure -> result.reasons
            is KitPlanResult.Success -> emptyList()
        }
    }

    public fun load(
        access: ProtectedAccess,
        definition: KitDefinition,
        bypassRestrictions: Boolean = false,
    ): KitLoadResult {
        val player = access.player
        if (!loadingPlayers.add(player)) {
            return KitLoadResult.Blocked(
                listOf("A PK kit is already being loaded for your account.")
            )
        }
        return try {
            loadLocked(access, definition, bypassRestrictions)
        } finally {
            loadingPlayers.remove(player)
        }
    }

    private fun loadLocked(
        access: ProtectedAccess,
        definition: KitDefinition,
        bypassRestrictions: Boolean = false,
    ): KitLoadResult {
        val player = access.player
        val restrictionErrors =
            if (bypassRestrictions) validateAccountState(player) else restrictions.validate(player)
        if (restrictionErrors.isNotEmpty()) return KitLoadResult.Blocked(restrictionErrors)

        val validation = registry.validate(definition)
        if (validation.isNotEmpty()) return KitLoadResult.Failed(validation)

        val quickPrayerErrors = validateQuickPrayers(player, definition)
        if (quickPrayerErrors.isNotEmpty()) return KitLoadResult.Failed(quickPrayerErrors)

        val unbankable = unbankableItems(player)
        if (unbankable.isNotEmpty()) {
            return KitLoadResult.Blocked(
                listOf(
                    "Bank or remove these non-bankable items first: ${unbankable.joinToString()}."
                )
            )
        }

        val bank = access.bank
        val bankCapacity = player.bankCapacity.takeIf { it > 0 } ?: BankConstants.default_capacity
        val result =
            planner.plan(
                definition = definition,
                currentInventory = copy(player.inv),
                currentEquipment = copy(player.worn),
                currentBank = copy(bank),
                bankCapacity = bankCapacity,
            )
        if (result is KitPlanResult.Failure) return KitLoadResult.Failed(result.reasons)
        val plan = (result as KitPlanResult.Success).plan
        val snapshot = PlayerSnapshot.capture(player, bank)
        return try {
            applyInventory(player.inv, plan.inventory)
            applyInventory(player.worn, plan.equipment)
            applyInventory(bank, plan.bank)
            applyStats(player, definition.stats)
            applySettings(player, definition.settings)
            BankTab.repairLayout(player, bank)
            access.rebuildAppearance()
            PlayerInterfaceUpdates.updateCombatTab(player)
            KitLoadResult.Success(definition)
        } catch (t: Throwable) {
            val rollbackFailure =
                try {
                    snapshot.restore(player, bank)
                    access.rebuildAppearance()
                    null
                } catch (rollback: Throwable) {
                    rollback
                }
            if (rollbackFailure == null) {
                logger.error(t) {
                    "PK kit transaction failed: player=${player.displayName}, kit=${definition.id}, " +
                        "stage=apply, missingItems=none, rollback=successful"
                }
                KitLoadResult.Failed(
                    listOf("The kit transaction failed and your previous setup was restored.")
                )
            } else {
                t.addSuppressed(rollbackFailure)
                logger.error(t) {
                    "PK kit transaction failed: player=${player.displayName}, kit=${definition.id}, " +
                        "stage=apply, missingItems=none, rollback=FAILED"
                }
                KitLoadResult.Failed(
                    listOf(
                        "The kit transaction failed and rollback could not be confirmed; contact an administrator immediately."
                    )
                )
            }
        }
    }

    private fun unbankableItems(player: Player): List<String> =
        (player.inv.filterNotNull { true } + player.worn.filterNotNull { true })
            .mapNotNull { obj ->
                val type =
                    ServerCacheManager.getItem(obj.id) ?: return@mapNotNull "object ${obj.id}"
                type.takeIf { it.param(BaseParams.no_bank) != 0 }?.name
            }
            .distinct()

    private fun validateQuickPrayers(player: Player, definition: KitDefinition): List<String> =
        buildList {
                val mask = definition.settings.quickPrayers
                if (mask == 0) return@buildList
                val selected = prayers.toPrayerList(mask)
                val knownMask = selected.fold(0) { flags, prayer -> flags or (1 shl prayer.id) }
                if (knownMask != mask)
                    add("The saved quick-prayer selection contains unknown prayer bits.")
                val selectedIds = selected.mapTo(hashSetOf()) { it.id }
                for (prayer in selected) {
                    if (prayer.level > definition.stats.prayer) {
                        add("${prayer.name} requires Prayer level ${prayer.level}.")
                    }
                    val defenceReq = prayer.defenceReq
                    if (defenceReq != null && defenceReq > definition.stats.defence) {
                        add("${prayer.name} requires Defence level $defenceReq.")
                    }
                    val unlockVar = prayer.unlocked
                    if (unlockVar != null && player.vars[unlockVar] < prayer.unlockState) {
                        add("${prayer.name} is not unlocked on this account.")
                    }
                    val collision =
                        prayers[prayer].firstOrNull { it.id != prayer.id && it.id in selectedIds }
                    if (collision != null)
                        add("${prayer.name} conflicts with ${collision.name} in quick prayers.")
                }
            }
            .distinct()

    public fun saveCurrent(player: Player, slot: Int, requestedName: String?): KitMutationResult {
        val restrictions = restrictions.validate(player)
        if (restrictions.isNotEmpty()) return KitMutationResult.Failure(restrictions.first())
        val data = persistence.read(player).data
        if (!KitSlotManager.isUnlocked(data.slotTier, slot)) {
            return KitMutationResult.Failure(
                "Custom slot ${slot + 1} is locked; you have ${KitSlotManager.capacity(data.slotTier)} slots."
            )
        }
        val existing = data.customKits.firstOrNull { it.slot == slot }
        val cleanName =
            requestedName?.trim()?.takeIf { it.isNotBlank() }
                ?: existing?.name
                ?: "Custom Kit ${slot + 1}"
        val now = System.currentTimeMillis()
        val inventory = capture(player.inv)
        val equipment = capture(player.worn)
        val custom =
            CustomKit(
                slot = slot,
                id = existing?.id ?: "custom_${slot + 1}",
                name = cleanName,
                category = KitCategory.Custom,
                inventory = inventory,
                equipment = equipment,
                stats = captureStats(player),
                settings = captureSettings(player),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
        val errors =
            registry.validate(custom.asDefinition(itemPolicy.riskValue(inventory + equipment)))
        if (errors.isNotEmpty()) return KitMutationResult.Failure(errors.joinToString("; "))
        return persistence.saveCustom(player, custom)
    }

    public fun deleteCustom(player: Player, slot: Int): KitMutationResult =
        persistence.deleteCustom(player, slot)

    public fun renameCustom(player: Player, slot: Int, name: String): KitMutationResult =
        persistence.renameCustom(player, slot, name)

    public fun setDefault(player: Player, definition: KitDefinition?): KitMutationResult =
        persistence.setDefault(player, definition?.let { KitReference(it.source, it.id) })

    public fun setSlotTier(player: Player, tier: Int): KitMutationResult =
        persistence.setSlotTier(player, tier)

    public fun setOpenAfterDeath(player: Player, enabled: Boolean): KitMutationResult =
        persistence.setOpenAfterDeath(player, enabled)

    public fun openAfterDeathEnabled(player: Player): Boolean =
        persistence.read(player).data.openAfterDeath

    public fun canOpenAfterDeath(player: Player): Boolean = restrictions.validate(player).isEmpty()

    public fun slotSummary(player: Player): KitSlotSummary {
        val read = persistence.read(player)
        val capacity = KitSlotManager.capacity(read.data.slotTier)
        return KitSlotSummary(
            read.data.slotTier,
            capacity,
            read.data.customKits,
            read.data.defaultKit,
            read.warnings,
            read.data.openAfterDeath,
        )
    }

    public fun reload(): StarterKitRegistry.KitRegistryReport = registry.reload()

    public fun registryReport(): StarterKitRegistry.KitRegistryReport = registry.report()

    public fun validationErrors(id: String): List<String> = registry.validationErrors(id)

    private fun validateAccountState(player: Player): List<String> = buildList {
        if (
            !player.isSlotAssigned ||
                player.loggingOut ||
                player.pendingLogout ||
                player.pendingShutdown
        ) {
            add("The player is not in a mutable online state.")
        }
        if (player.hitpoints <= 0) add("The player is dead.")
    }

    private fun capture(inventory: Inventory): List<KitItem> =
        inventory
            .mapSlots { _, obj -> obj != null }
            .sorted()
            .mapNotNull { slot ->
                val obj = inventory[slot] ?: return@mapNotNull null
                val type = ServerCacheManager.getItem(obj.id) ?: return@mapNotNull null
                KitItem(type.internalName, obj.id, obj.count, obj.vars, slot)
            }

    private fun captureStats(player: Player): KitStats =
        KitStats(
            attack = player.statMap.getBaseLevel("stat.attack").toInt() and 0xff,
            strength = player.statMap.getBaseLevel("stat.strength").toInt() and 0xff,
            defence = player.statMap.getBaseLevel("stat.defence").toInt() and 0xff,
            hitpoints = player.statMap.getBaseLevel("stat.hitpoints").toInt() and 0xff,
            ranged = player.statMap.getBaseLevel("stat.ranged").toInt() and 0xff,
            magic = player.statMap.getBaseLevel("stat.magic").toInt() and 0xff,
            prayer = player.statMap.getBaseLevel("stat.prayer").toInt() and 0xff,
        )

    private fun captureSettings(player: Player): KitCombatSettings =
        KitCombatSettings(
            spellbook = Spellbook[player.vars["varbit.spellbook"]] ?: Spellbook.Standard,
            quickPrayers = player.vars["varbit.quickprayer_selected"],
            combatStance = player.vars["varp.com_mode"],
            meleeStyle = player.vars["varp.com_stance"],
            autocastSpell = player.vars["varbit.autocast_spell"],
            autocastDefensive = player.vars["varbit.autocast_defmode"] != 0,
            specialEnergy =
                player.vars["varp.sa_energy"].coerceIn(0, SpecialAttackEnergy.MAX_ENERGY),
            runEnergy = player.runEnergy.coerceIn(0, 1000),
            runEnabled = player.vars["varp.option_run"] == 1,
        )

    @OptIn(InternalApi::class)
    private fun applyStats(player: Player, stats: KitStats) {
        for ((internal, level) in stats.asMap()) {
            val xp = PlayerSkillXPTable.getXPFromLevel(level)
            player.statMap.setXP(internal, xp)
            player.statMap.setBaseLevel(internal, level.toByte())
            player.statMap.setCurrentLevel(internal, level.toByte())
            player.markStatUpdate(internal)
        }
        player.appearance.combatLevel = PlayerSkillXP.calculateCombatLevel(player)
        PlayerInterfaceUpdates.updateCombatLevel(player)
    }

    private fun applySettings(player: Player, settings: KitCombatSettings) {
        spellbooks.setSpellbook(player, settings.spellbook)
        player.disablePrayers()
        set(player, "varbit.quickprayer_selected", settings.quickPrayers)
        set(player, "varbit.quickprayer_active", 0)

        val weaponObj = player.worn[3]
        val weapon = weaponObj?.let { ServerCacheManager.getItem(it.id) }
        val validStance =
            settings.combatStance.takeIf { attackStyles.resolve(weapon, it) != null } ?: 0
        set(player, "varp.com_mode", validStance)
        set(
            player,
            "varp.com_stance",
            if (validStance == settings.combatStance) settings.meleeStyle else 0,
        )
        set(player, "varp.sa_attack", 0)
        set(player, "varp.sa_energy", settings.specialEnergy)
        player.runEnergy = settings.runEnergy
        UpdateRun.energy(player, player.runEnergy)
        player.setActiveMoveSpeed(if (settings.runEnabled) MoveSpeed.Run else MoveSpeed.Walk)

        val canAutocast =
            settings.autocastSpell > 0 &&
                weapon != null &&
                autocast.canStaffAutocast(weapon, settings.autocastSpell)
        if (canAutocast) {
            val saved = autocast.getVarBits(weapon)
            if (saved != null)
                autocast.set(player, saved, settings.autocastSpell, settings.autocastDefensive)
            set(player, "varbit.autocast_set", 1)
            set(player, "varbit.autocast_spell", settings.autocastSpell)
            set(player, "varbit.autocast_defmode", if (settings.autocastDefensive) 1 else 0)
        } else {
            if (weapon != null) autocast.reset(player, weapon)
            set(player, "varbit.autocast_set", 0)
            set(player, "varbit.autocast_spell", 0)
            set(player, "varbit.autocast_defmode", 0)
        }
    }

    private fun set(player: Player, internal: String, value: Int) {
        VarPlayerIntMapSetter.set(player, internal, value)
    }

    private fun applyInventory(inventory: Inventory, values: Array<InvObj?>) {
        require(inventory.size == values.size) { "Inventory size changed during kit transaction." }
        for (slot in values.indices) inventory[slot] = values[slot]?.let(::InvObj)
    }

    private fun copy(inventory: Inventory): Array<InvObj?> =
        Array(inventory.size) { inventory[it]?.let(::InvObj) }

    private data class PlayerSnapshot(
        val inventory: Array<InvObj?>,
        val equipment: Array<InvObj?>,
        val bank: Array<InvObj?>,
        val vars: Map<Int, Int>,
        val stats: Map<String, StatSnapshot>,
        val runEnergy: Int,
        val combatLevel: Int,
    ) {
        fun restore(player: Player, bankInventory: Inventory) {
            restoreInventory(player.inv, inventory)
            restoreInventory(player.worn, equipment)
            restoreInventory(bankInventory, bank)
            player.vars.backing.clear()
            player.vars.backing.putAll(vars)
            for ((internal, snapshot) in stats) {
                player.statMap.setXP(internal, snapshot.xp)
                player.statMap.setBaseLevel(internal, snapshot.base.toByte())
                player.statMap.setCurrentLevel(internal, snapshot.current.toByte())
                player.markStatUpdate(internal)
            }
            player.runEnergy = runEnergy
            UpdateRun.energy(player, runEnergy)
            player.appearance.combatLevel = combatLevel
            PlayerInterfaceUpdates.updateCombatLevel(player)
        }

        companion object {
            fun capture(player: Player, bank: Inventory): PlayerSnapshot {
                val stats =
                    StatInternals.associateWith { internal ->
                        StatSnapshot(
                            xp = player.statMap.getXP(internal),
                            base = player.statMap.getBaseLevel(internal).toInt() and 0xff,
                            current = player.statMap.getCurrentLevel(internal).toInt() and 0xff,
                        )
                    }
                return PlayerSnapshot(
                    inventory = copyInventory(player.inv),
                    equipment = copyInventory(player.worn),
                    bank = copyInventory(bank),
                    vars = player.vars.backing.toMap(),
                    stats = stats,
                    runEnergy = player.runEnergy,
                    combatLevel = player.combatLevel,
                )
            }

            private fun copyInventory(inventory: Inventory): Array<InvObj?> =
                Array(inventory.size) { inventory[it]?.let(::InvObj) }

            private fun restoreInventory(inventory: Inventory, values: Array<InvObj?>) {
                for (slot in values.indices) inventory[slot] = values[slot]?.let(::InvObj)
            }
        }
    }

    private data class StatSnapshot(val xp: Int, val base: Int, val current: Int)

    private companion object {
        val StatInternals =
            listOf(
                "stat.attack",
                "stat.strength",
                "stat.defence",
                "stat.hitpoints",
                "stat.ranged",
                "stat.magic",
                "stat.prayer",
            )
    }
}

public data class KitPreview(
    val definition: KitDefinition,
    val freeItems: List<KitItem>,
    val ownedItems: List<KitItem>,
    val forbiddenItems: List<KitItem>,
)

public data class KitSlotSummary(
    val tier: Int,
    val capacity: Int,
    val kits: List<CustomKit>,
    val defaultKit: KitReference?,
    val warnings: List<String>,
    val openAfterDeath: Boolean,
)
