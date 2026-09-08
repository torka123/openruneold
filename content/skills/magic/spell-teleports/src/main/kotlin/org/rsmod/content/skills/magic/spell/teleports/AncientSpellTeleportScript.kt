package org.rsmod.content.skills.magic.spell.teleports

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.combat.commons.magic.AncientMagicksAccessPolicy
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.isInCombat
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.clearMapFlag
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.content.skills.magic.spell.teleports.HomeTeleport.castHome
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** All nine Ancient spellbook teleports, intentionally free of Desert Treasure checks. */
class AncientSpellTeleportScript
@Inject
constructor(
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
    private val teleportValidator: PlayerTeleportValidator,
    private val areaChecker: AreaChecker,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (teleport in AncientSpellTeleport.entries) {
            val spell = teleport.resolveSpell() ?: continue
            onIfOverlayButton(spell.component, ignoreRepeatedClicks = true) { cast(spell, teleport) }
        }
    }

    private fun AncientSpellTeleport.resolveSpell(): MagicSpell? {
        val obj = ServerCacheManager.getItem(spellObj.asRSCM(RSCMType.OBJ)) ?: return null
        return spells.getObjSpell(obj)
    }

    private suspend fun ProtectedAccess.cast(spell: MagicSpell, teleport: AncientSpellTeleport) {
        if (actionDelay > mapClock || !AncientMagicksAccessPolicy.canAccess(player)) {
            return
        }
        val destination =
            teleport.destination(spell)
                ?: run {
                    mes("That teleport is not implemented yet.")
                    return
                }
        if (teleport == AncientSpellTeleport.Home) {
            if (consumeRequirements(spell)) castHome(destination, { canTeleport() })
            return
        }

        if (!canTeleport() || !consumeRequirements(spell)) {
            return
        }

        actionDelay = mapClock + TELEPORT_ACTION_DELAY
        player.anim(ANCIENT_TELEPORT_ANIM, priority = CAST_ANIMATION_PRIORITY)
        spotanim(ANCIENT_TELEPORT_SPOTANIM, height = TELEPORT_SPOTANIM_HEIGHT)
        soundSynth(TELEPORT_SOUND)
        delay(TELEPORT_DELAY)
        if (!canTeleport()) {
            return
        }
        telejump(destination)
        statAdvance("stat.magic", spell.castXp)
    }

    private fun ProtectedAccess.consumeRequirements(spell: MagicSpell): Boolean =
        if (spell.objReqs.isEmpty()) {
            runes.canCastSpell(player, spell)
        } else {
            !runes.attemptCast(player, spell).isFailure()
        }

    private fun ProtectedAccess.canTeleport(): Boolean {
        val denial = teleportValidator.validate(player, TeleportType.Standard, areaChecker)
        if (denial == null) {
            return true
        }
        player.clearMapFlag()
        player.mes(denial, ChatType.Engine)
        return false
    }

    internal enum class AncientSpellTeleport(
        val spellObj: String,
        val fixedDestination: CoordGrid? = null,
    ) {
        Home("obj.01_zaros_home_tele", CoordGrid(3087, 3495, 0)),
        Paddewwa("obj.54_paddewwa_teleport"),
        Senntisten("obj.60_senntisten_teleport"),
        Kharyrll("obj.66_kharyllyl_teleport"),
        Lassar("obj.72_lassar_teleport"),
        Dareeyak("obj.78_dareeyak_teleport"),
        Carrallanger("obj.84_carrallagar_teleport"),
        Annakarl("obj.90_annakarl_teleport"),
        Ghorrock("obj.96_ghorrock_teleport");

        fun destination(spell: MagicSpell): CoordGrid? =
            fixedDestination ?: spell.obj.paramOrNull(params.spell_telecoord)
    }

    private companion object {
        val ANCIENT_TELEPORT_ANIM: String =
            RSCM.getReverseMapping(RSCMType.SEQ, AncientTeleportVisualRules.PLAYER_ANIMATION_ID)
        val ANCIENT_TELEPORT_SPOTANIM: String =
            RSCM.getReverseMapping(RSCMType.SPOTANIM, AncientTeleportVisualRules.SPOTANIM_ID)
        const val TELEPORT_SOUND: String = "synth.teleport_all"
        const val CAST_ANIMATION_PRIORITY: Int = 6
        const val TELEPORT_SPOTANIM_HEIGHT: Int = AncientTeleportVisualRules.SPOTANIM_HEIGHT
        const val TELEPORT_DELAY: Int = AncientTeleportVisualRules.CAST_DELAY
        const val TELEPORT_ACTION_DELAY: Int = 5
    }
}

internal object AncientTeleportVisualRules {
    const val PLAYER_ANIMATION_ID: Int = 1979
    const val SPOTANIM_ID: Int = 392
    const val SPOTANIM_HEIGHT: Int = 0
    const val CAST_DELAY: Int = 3
}
