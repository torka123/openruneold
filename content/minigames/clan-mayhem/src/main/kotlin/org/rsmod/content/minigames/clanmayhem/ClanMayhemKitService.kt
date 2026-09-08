@file:OptIn(org.rsmod.annotations.InternalApi::class)

package org.rsmod.content.minigames.clanmayhem

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.disablePrayers
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.stat.PlayerSkillXP
import org.rsmod.api.player.ui.PlayerInterfaceUpdates
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.setActiveMoveSpeed
import org.rsmod.api.spells.autocast.MagicSpellbookManager
import org.rsmod.content.other.pkkits.KitDefinition
import org.rsmod.content.other.pkkits.KitItem
import org.rsmod.content.other.pkkits.StarterKitRegistry
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.stat.PlayerSkillXPTable

@Singleton
internal class ClanMayhemKitService
@Inject
constructor(
    private val registry: StarterKitRegistry,
    private val spellbooks: MagicSpellbookManager,
) {
    private val log = InlineLogger()

    fun validate(): List<String> =
        ClanMayhemKitTier.entries.mapNotNull { tier ->
            if (registry.get(tier.presetId) == null) {
                "${tier.name}: missing or invalid starter preset '${tier.presetId}'"
            } else {
                null
            }
        }

    fun applyStarting(player: Player, tier: ClanMayhemKitTier): Boolean =
        apply(player, definition(tier), respawn = false)

    fun applyRespawn(player: Player, tier: ClanMayhemKitTier): Boolean =
        apply(player, definition(tier), respawn = true)

    private fun definition(tier: ClanMayhemKitTier): KitDefinition? = registry.get(tier.presetId)

    private fun apply(player: Player, definition: KitDefinition?, respawn: Boolean): Boolean {
        if (definition == null) return false
        return try {
            val inventory =
                if (respawn) reducedRespawnInventory(definition.inventory) else definition.inventory
            replace(player.inv.size, inventory) { slot, obj -> player.inv[slot] = obj }
            replace(player.worn.size, definition.equipment) { slot, obj -> player.worn[slot] = obj }
            for ((internal, level) in definition.stats.asMap()) {
                val xp = PlayerSkillXPTable.getXPFromLevel(level)
                player.statMap.setXP(internal, xp)
                player.statMap.setBaseLevel(internal, level.toByte())
                player.statMap.setCurrentLevel(internal, level.toByte())
                player.markStatUpdate(internal)
            }
            spellbooks.setSpellbook(player, definition.settings.spellbook)
            player.disablePrayers()
            set(player, "varbit.quickprayer_selected", definition.settings.quickPrayers)
            set(player, "varbit.quickprayer_active", 0)
            set(player, "varp.com_mode", definition.settings.combatStance.coerceIn(0, 3))
            set(player, "varp.com_stance", definition.settings.meleeStyle.coerceIn(0, 3))
            set(player, "varp.sa_attack", 0)
            set(player, "varp.sa_energy", 1_000)
            set(player, "varbit.autocast_set", 0)
            set(player, "varbit.autocast_spell", 0)
            set(player, "varbit.autocast_defmode", 0)
            player.runEnergy = 1_000
            UpdateRun.energy(player, 1_000)
            player.setActiveMoveSpeed(MoveSpeed.Run)
            player.appearance.combatLevel = PlayerSkillXP.calculateCombatLevel(player)
            player.rebuildAppearance()
            PlayerInterfaceUpdates.updateCombatLevel(player)
            PlayerInterfaceUpdates.updateCombatTab(player)
            true
        } catch (error: Throwable) {
            log.error(error) {
                "[ClanMayhem] Failed to apply ${if (respawn) "respawn" else "starting"} kit " +
                    "${definition.id} account=${player.accountId}"
            }
            false
        }
    }

    private fun reducedRespawnInventory(items: List<KitItem>): List<KitItem> {
        val consumables = items.filter(::isConsumable)
        val retainedSlots =
            consumables
                .sortedBy(KitItem::slot)
                .take((consumables.size + 1) / 2)
                .mapTo(hashSetOf(), KitItem::slot)
        return items.filter { !isConsumable(it) || it.slot in retainedSlots }
    }

    private fun isConsumable(item: KitItem): Boolean {
        val name = ServerCacheManager.getItem(item.id)?.name?.lowercase().orEmpty()
        return CONSUMABLE_TERMS.any(name::contains)
    }

    private fun replace(size: Int, items: List<KitItem>, setSlot: (Int, InvObj?) -> Unit) {
        for (slot in 0 until size) setSlot(slot, null)
        for (item in items) {
            require(item.slot in 0 until size) { "Kit item ${item.internalName} has invalid slot." }
            setSlot(item.slot, item.obj)
        }
    }

    private fun set(player: Player, internal: String, value: Int) {
        VarPlayerIntMapSetter.set(player, internal, value)
    }

    private companion object {
        val CONSUMABLE_TERMS =
            listOf(
                "potion",
                "brew",
                "restore",
                "shark",
                "anglerfish",
                "karambwan",
                "manta ray",
                "sea turtle",
                "dark crab",
            )
    }
}
