package org.rsmod.content.interfaces.spellbook.tab

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.commons.magic.AncientMagicksAccessPolicy
import org.rsmod.api.combat.commons.magic.HomeTeleportCooldown
import org.rsmod.api.player.output.VarpSync
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal const val ANCIENT_CLIENT_UNLOCK_DELAY_TICKS: Int = 1
internal val MAGIC_FILTER_ROW_RANGE: IntRange = 0..6

internal enum class MagicFilterOption(val comsub: Int) {
    Combat(0),
    Teleport(1),
    Utility(2),
    LackLevel(3),
    LackRunes(4),
    Locked(5),
    Resizing(6),
}

internal fun magicFilterOption(comsub: Int): MagicFilterOption? =
    MagicFilterOption.entries.firstOrNull { it.comsub == comsub }

class MagicTabFilterScript
@Inject
constructor(private val protectedAccess: ProtectedAccessLauncher) : PluginScript() {

    @OptIn(InternalApi::class)
    override fun ScriptContext.startup() {
        val desertTreasureVarp =
            requireNotNull(
                ServerCacheManager.getVarp("varp.deserttreasuremain".asRSCM(RSCMType.VARP))
            ) {
                "Missing Desert Treasure client varp"
            }

        onPlayerLogin {
            protectedAccess.launchLenient(player) {
                // EngineLogin resets all client varps after Login callbacks. Apply this client-only
                // unlock on the following tick so the real quest state remains untouched.
                delay(ANCIENT_CLIENT_UNLOCK_DELAY_TICKS)
                VarpSync.writeVarp(
                    player,
                    desertTreasureVarp,
                    AncientMagicksAccessPolicy.CLIENT_UNLOCK_STAGE,
                )
                HomeTeleportCooldown.syncClient(player)
            }
        }

        onIfOpen("interface.magic_spellbook") {
            HomeTeleportCooldown.syncClient(player)
            player.ifSetEvents(
                "component.magic_spellbook:filtermenu",
                MAGIC_FILTER_ROW_RANGE,
                IfEvent.Op1,
            )
        }

        onIfOverlayButton("component.magic_spellbook:filtermenu") { player.toggleFilter(it.comsub) }
    }

    private fun Player.toggleFilter(comsub: Int) {
        when (magicFilterOption(comsub) ?: return) {
            MagicFilterOption.Combat -> blockCombatSpells = !blockCombatSpells
            MagicFilterOption.Teleport -> blockTeleportSpells = !blockTeleportSpells
            MagicFilterOption.Utility -> blockUtilitySpells = !blockUtilitySpells
            MagicFilterOption.LackLevel -> blockLackLevelSpells = !blockLackLevelSpells
            MagicFilterOption.LackRunes -> blockLackRuneSpells = !blockLackRuneSpells
            MagicFilterOption.Locked -> blockLockedSpells = !blockLockedSpells
            MagicFilterOption.Resizing -> blockResizing = !blockResizing
        }
    }
}

private var Player.blockCombatSpells by boolVarBit("varbit.magic_filter_blockcombat")
private var Player.blockTeleportSpells by boolVarBit("varbit.magic_filter_blockteleport")
private var Player.blockUtilitySpells by boolVarBit("varbit.magic_filter_blockutility")
private var Player.blockLackRuneSpells by boolVarBit("varbit.magic_filter_blocklackrunes")
private var Player.blockLackLevelSpells by boolVarBit("varbit.magic_filter_blocklacklevel")
private var Player.blockLockedSpells by boolVarBit("varbit.magic_filter_blocklocked")
private var Player.blockResizing by boolVarBit("varbit.magic_spellbook_blockresizing")
