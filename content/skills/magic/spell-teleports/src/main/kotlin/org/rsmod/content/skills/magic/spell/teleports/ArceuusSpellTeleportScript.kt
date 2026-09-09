package org.rsmod.content.skills.magic.spell.teleports

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.clearMapFlag
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.realm.Realm
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.content.skills.magic.spell.teleports.HomeTeleport.castHome
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Revision-240 Arceuus teleports, with Home set to OpenRune's Edgeville destination. */
public class ArceuusSpellTeleportScript
@Inject
constructor(
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
    private val teleportValidator: PlayerTeleportValidator,
    private val areaChecker: AreaChecker,
    private val realm: Realm,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (teleport in ArceuusTeleport.entries) {
            val spell = teleport.resolveSpell() ?: continue
            onIfOverlayButton(spell.component, ignoreRepeatedClicks = true) {
                cast(spell, teleport)
            }
        }
    }

    private fun ArceuusTeleport.resolveSpell(): MagicSpell? {
        val type = ServerCacheManager.getItem(spellObj.asRSCM(RSCMType.OBJ)) ?: return null
        return spells.getObjSpell(type)
    }

    internal suspend fun ProtectedAccess.cast(spell: MagicSpell, teleport: ArceuusTeleport) {
        if (teleport != ArceuusTeleport.Home && actionDelay > mapClock) return
        val destination =
            when (teleport) {
                ArceuusTeleport.Respawn -> realm.config.respawnCoord
                else -> teleport.destination ?: spell.obj.paramOrNull(params.spell_telecoord)
            }
        if (destination == null) {
            mes("That teleport is not implemented yet.")
            return
        }
        if (teleport == ArceuusTeleport.Home) {
            if (consume(spell)) {
                castHome(destination, { canTeleport() }, areaChecker)
            }
            return
        }
        if (!canTeleport() || !consume(spell)) return
        performTeleport(spell, destination)
    }

    private suspend fun ProtectedAccess.performTeleport(
        spell: MagicSpell,
        destination: CoordGrid,
    ): Boolean {
        actionDelay = mapClock + ACTION_DELAY
        try {
            player.anim(ANIMATION, priority = 6)
            spotanim(GRAPHIC)
            soundSynth(TELEPORT_SOUND)
            delay(CAST_DELAY)
            if (!canTeleport()) return false
            telejump(destination)
            if (coords != destination) return false
            if (spell.castXp > 0.0) statAdvance("stat.magic", spell.castXp)
            return true
        } finally {
            // The native departure sequence ends with the model hidden. Always restore idle.
            player.resetAnim()
            player.resetSpotanim()
        }
    }

    private fun ProtectedAccess.consume(spell: MagicSpell): Boolean =
        if (spell.objReqs.isEmpty()) runes.canCastSpell(player, spell)
        else !runes.attemptCast(player, spell).isFailure()

    private fun ProtectedAccess.canTeleport(): Boolean {
        val denial = teleportValidator.validate(player, TeleportType.Standard, areaChecker)
        if (denial == null) return true
        player.clearMapFlag()
        player.mes(denial, ChatType.Engine)
        return false
    }

    internal enum class ArceuusTeleport(val spellObj: String, val destination: CoordGrid? = null) {
        Home("obj.deadman_level99_lamp", CoordGrid(3087, 3495, 0)),
        Library("obj.br_mithril_platebody"),
        DraynorManor("obj.br_mithril_platelegs"),
        Battlefront("obj.23_teleport_battlefront"),
        MindAltar("obj.br_greendhide_body"),
        Respawn("obj.poh_guide_guildtrophy"),
        SalveGraveyard("obj.br_greendhide_chaps"),
        Fenkenstrain("obj.br_moonclan_body"),
        WestArdougne("obj.br_moonclan_legs"),
        HarmonyIsland("obj.br_xeric_body"),
        Cemetery("obj.br_xeric_legs"),
        Barrows("obj.br_air_staff"),
        ApeAtoll("obj.br_dragon_helm"),
    }

    private companion object {
        const val ANIMATION: String = "seq.human_spellcast_arceuus_teleport"
        const val GRAPHIC: String = "spotanim.arceuus_teleport_spotanim"
        const val TELEPORT_SOUND: String = "synth.teleport_all"
        const val CAST_DELAY: Int = 3
        const val ACTION_DELAY: Int = 5
    }
}
