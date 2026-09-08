package org.rsmod.content.skills.farming

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import kotlin.math.min
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.magicLvl
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onApLocT
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.game.MapClock
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Arceuus Resurrect Crops integration for the persistent native Farming patch model. */
class ResurrectCropsScript
@Inject
constructor(
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
    private val farming: FarmingService,
    private val random: GameRandom,
    private val clock: MapClock,
) : PluginScript() {
    override fun ScriptContext.startup() {
        val spellType = ServerCacheManager.getItem(SPELL_OBJECT.asRSCM(RSCMType.OBJ)) ?: return
        val spell = spells.getObjSpell(spellType) ?: return
        onApLocT(spell.component) { event ->
            if (actionDelay > mapClock) return@onApLocT
            when (farming.resurrectionStatus(player, event.loc.id)) {
                FarmingResurrectionStatus.Ready -> cast(spell, event.loc.id)
                FarmingResurrectionStatus.AlreadyAttempted ->
                    mes("You have already attempted to resurrect this crop.")
                FarmingResurrectionStatus.NotDead -> mes("This patch does not contain dead crops.")
                FarmingResurrectionStatus.NotFarmingPatch -> mes("This spell can only be cast on a Farming patch.")
                else -> Unit
            }
        }
    }

    private suspend fun org.rsmod.api.player.protect.ProtectedAccess.cast(
        spell: MagicSpell,
        baseLocId: Int,
    ) {
        if (runes.attemptCast(player, spell).isFailure()) return
        actionDelay = mapClock + ACTION_DELAY
        player.anim(RSCM.getReverseMapping(RSCMType.SEQ, ANIMATION), priority = 6)
        spotanim(RSCM.getReverseMapping(RSCMType.SPOTANIM, GRAPHIC))
        statAdvance(MAGIC_STAT, spell.castXp)
        val success = random.of(100) < successChance(player.magicLvl)
        when (farming.resurrect(player, baseLocId, success)) {
            FarmingResurrectionStatus.Resurrected -> {
                mes("The produce in this patch has been restored to its natural health.")
            }
            FarmingResurrectionStatus.Cleared -> mes("You were unable to resurrect the crops.")
            else -> mes("The patch can no longer be resurrected.")
        }
    }

    internal companion object {
        const val SPELL_OBJECT = "obj.br_swordfish"
        const val MAGIC_STAT = "stat.magic"
        const val ANIMATION = 7163
        const val GRAPHIC = 1298
        const val ACTION_DELAY = 5

        fun successChance(magicLevel: Int): Int =
            50 + ((min(99, magicLevel).coerceAtLeast(78) - 78) * 25 / 21)
    }
}
