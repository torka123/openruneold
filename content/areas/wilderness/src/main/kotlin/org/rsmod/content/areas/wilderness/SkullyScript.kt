package org.rsmod.content.areas.wilderness

import jakarta.inject.Inject
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc2
import org.rsmod.api.script.onOpNpc3
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.map.Direction
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Ferox Enclave loot-key custodian and persistent loot-key settings. */
public class SkullyScript @Inject constructor() : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(SKULLY) { talk(it.npc) }
        onOpNpc2(SKULLY) { value(it.npc) }
        onOpNpc3(SKULLY) { settings(it.npc) }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        npc.lockFacingDirection(Direction.West)
        startDialogue(npc) {
            if (player.lootKeysUnlocked()) {
                chatNpcNoTurn(
                    neutral,
                    "Your loot-key service is ready. Use my Settings option if you want to disable or enable it.",
                )
                return@startDialogue
            }
            chatNpcNoTurn(
                neutral,
                "I can enable loot keys for a one-off payment of 1,000,000 coins. The payment can come from your inventory and bank.",
            )
            when (choice2("Pay 1,000,000 coins.", true, "No thanks.", false)) {
                true -> access.activateLootKeys()
                false -> chatPlayer(neutral, "No thanks.")
            }
        }
    }

    private suspend fun ProtectedAccess.value(npc: Npc) {
        npc.lockFacingDirection(Direction.West)
        startDialogue(npc) {
            if (!player.lootKeysUnlocked()) {
                chatNpcNoTurn(
                    neutral,
                    "You have not unlocked loot keys yet. Talk to me if you want to activate them.",
                )
                return@startDialogue
            }
            val count = player.attr.getOrDefault(LOOT_KEYS_HELD, 0)
            chatNpcNoTurn(
                neutral,
                if (count == 0) {
                    "You do not currently have any loot keys for me to value."
                } else {
                    "You currently have $count loot key${if (count == 1) "" else "s"} stored."
                },
            )
        }
    }

    private suspend fun ProtectedAccess.settings(npc: Npc) {
        npc.lockFacingDirection(Direction.West)
        startDialogue(npc) {
            if (!player.lootKeysUnlocked()) {
                chatNpcNoTurn(
                    neutral,
                    "You need to unlock loot keys before changing their settings.",
                )
                return@startDialogue
            }
            val enabled = player.lootKeysEnabled()
            val enable =
                choice2(
                    if (enabled) "Keep loot keys enabled." else "Enable loot keys.",
                    true,
                    if (enabled) "Disable loot keys." else "Keep loot keys disabled.",
                    false,
                )
            player.attr[LOOT_KEYS_ENABLED] = enable
            chatNpcNoTurn(neutral, "Loot keys are now ${if (enable) "enabled" else "disabled"}.")
        }
    }

    private fun ProtectedAccess.activateLootKeys() {
        val inventoryCount = inv.count(COINS)
        val bankCount = bank.count(COINS)
        if (inventoryCount.toLong() + bankCount.toLong() < ACTIVATION_FEE.toLong()) {
            mes("You need 1,000,000 coins in your inventory or bank.")
            return
        }
        val fromInventory = minOf(inventoryCount, ACTIVATION_FEE)
        val fromBank = ACTIVATION_FEE - fromInventory
        if (fromInventory > 0 && invDel(inv, COINS, fromInventory).failure) {
            mes("The payment could not be collected. No coins were taken.")
            return
        }
        if (fromBank > 0 && invDel(bank, COINS, fromBank).failure) {
            if (fromInventory > 0) check(invAdd(inv, COINS, fromInventory).success)
            mes("The payment could not be collected. No coins were taken.")
            return
        }
        player.attr[LOOT_KEYS_UNLOCKED] = true
        player.attr[LOOT_KEYS_ENABLED] = true
        mes("Skully enables loot keys for your account.")
    }

    public companion object {
        public const val SKULLY: String = "npc.wildy_hub_refugee1"
        public const val ACTIVATION_FEE: Int = 1_000_000
        public const val COINS: String = "obj.coins"

        public val LOOT_KEYS_UNLOCKED: AttributeKey<Boolean> =
            AttributeKey(persistenceKey = "wilderness_loot_keys_unlocked")
        public val LOOT_KEYS_ENABLED: AttributeKey<Boolean> =
            AttributeKey(persistenceKey = "wilderness_loot_keys_enabled")
        public val LOOT_KEYS_HELD: AttributeKey<Int> =
            AttributeKey(persistenceKey = "wilderness_loot_keys_held")

        public fun Player.lootKeysUnlocked(): Boolean = attr.getOrDefault(LOOT_KEYS_UNLOCKED, false)

        public fun Player.lootKeysEnabled(): Boolean =
            lootKeysUnlocked() && attr.getOrDefault(LOOT_KEYS_ENABLED, true)
    }
}
