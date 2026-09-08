package org.rsmod.api.specials.scripts

import dev.openrune.util.Wearpos
import kotlin.math.min
import org.rsmod.api.config.constants
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.script.advanced.onWearposChange
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.api.specials.energy.SpecialAttackEnergyRegeneration
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

public class SpecialEnergyScript() : PluginScript() {
    private val Player.newAccount by boolVarBit("varbit.new_player_account")
    private var Player.specialAttackEnergy by intVarp("varp.sa_energy")

    override fun ScriptContext.startup() {
        onPlayerLogin { player.initRegenTimer() }
        onPlayerSoftTimer(SpecialAttackEnergyRegeneration.TIMER) { player.specRegen() }
        onWearposChange {
            if (wearpos == Wearpos.Ring) {
                SpecialAttackEnergyRegeneration.refreshInterval(player)
            }
        }
    }

    private fun Player.initRegenTimer() {
        if (newAccount) {
            specialAttackEnergy = constants.sa_max_energy
        }
        SpecialAttackEnergyRegeneration.restart(this)
    }

    private fun Player.specRegen() {
        if (SpecialAttackEnergyRegeneration.isPaused(this)) return
        val increased = min(constants.sa_max_energy, specialAttackEnergy + 100)
        if (increased > specialAttackEnergy) {
            specialAttackEnergy = increased
        }
        SpecialAttackEnergyRegeneration.restart(this)
    }
}
