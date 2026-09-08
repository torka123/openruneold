package org.rsmod.content.interfaces.spellbook.tab

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.spells.autocast.MagicSpellbookManager
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.loc.LocLayerConstants

internal const val HOME_OCCULT_ALTAR_ID = 31858
internal val HOME_OCCULT_ALTAR_COORDS = CoordGrid(3223, 3218, 0)

internal fun spellbookForAltarChoice(choice: Int): Spellbook? = Spellbook[choice]

class HomeSpellbookAltarScript
@Inject
constructor(private val locs: LocRepository, private val spellbooks: MagicSpellbookManager) :
    PluginScript() {
    override fun ScriptContext.startup() {
        val altar =
            requireNotNull(ServerCacheManager.getObject(HOME_OCCULT_ALTAR_ID)) {
                "Missing home Altar of the Occult object $HOME_OCCULT_ALTAR_ID"
            }
        require(altar.name == "Altar of the Occult") {
            "Home spellbook altar $HOME_OCCULT_ALTAR_ID resolved to ${altar.name}"
        }
        listOf("Venerate", "Ancient", "Lunar", "Arceuus").forEachIndexed { index, action ->
            require(altar.actions.getOpOrNull(index).equals(action, ignoreCase = true)) {
                "Home spellbook altar option ${index + 1} is not $action"
            }
        }

        spawnHomeAltar()
        onOpLoc1(altar) {
            arriveDelay()
            chooseSpellbook()
        }
        onOpLoc2(altar) {
            arriveDelay()
            switchSpellbook(Spellbook.Ancients)
        }
        onOpLoc3(altar) {
            arriveDelay()
            switchSpellbook(Spellbook.Lunars)
        }
        onOpLoc4(altar) {
            arriveDelay()
            switchSpellbook(Spellbook.Arceuus)
        }
    }

    private fun spawnHomeAltar() {
        val shape = LocShape.CentrepieceStraight
        val angle = LocAngle.North
        val layer = LocLayerConstants.of(shape.id)
        val existing = locs.findAll(HOME_OCCULT_ALTAR_COORDS).firstOrNull { it.layer == layer }
        if (existing != null) {
            check(existing.id == HOME_OCCULT_ALTAR_ID) {
                "Cannot place the home spellbook altar: centrepiece ${existing.id} already occupies " +
                    "$HOME_OCCULT_ALTAR_COORDS"
            }
            return
        }
        val altar =
            LocInfo(
                layer = layer,
                coords = HOME_OCCULT_ALTAR_COORDS,
                entity = LocEntity(HOME_OCCULT_ALTAR_ID, shape.id, angle.id),
            )
        check(locs.add(altar, Int.MAX_VALUE)) {
            "Unable to spawn the home spellbook altar at $HOME_OCCULT_ALTAR_COORDS"
        }
    }

    private suspend fun ProtectedAccess.chooseSpellbook() {
        val choice =
            choice4(
                "Standard spellbook",
                Spellbook.Standard.varValue,
                "Ancient spellbook",
                Spellbook.Ancients.varValue,
                "Lunar spellbook",
                Spellbook.Lunars.varValue,
                "Arceuus spellbook",
                Spellbook.Arceuus.varValue,
                title = "Choose a spellbook",
            )
        val selected = spellbookForAltarChoice(choice) ?: return
        switchSpellbook(selected)
    }

    private fun ProtectedAccess.switchSpellbook(spellbook: Spellbook) {
        val name = spellbook.displayName()
        when (spellbooks.setSpellbook(player, spellbook)) {
            is MagicSpellbookManager.ChangeResult.Changed ->
                mes("You switch to the $name spellbook.")
            is MagicSpellbookManager.ChangeResult.Unchanged ->
                mes("You are already using the $name spellbook.")
        }
    }
}

private fun Spellbook.displayName(): String =
    when (this) {
        Spellbook.Standard -> "Standard"
        Spellbook.Ancients -> "Ancient"
        Spellbook.Lunars -> "Lunar"
        Spellbook.Arceuus -> "Arceuus"
    }
