package org.rsmod.content.bosses.royaltitans

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.commons.magic.MagicSpellChecks
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.enumVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onApNpc1
import org.rsmod.api.script.onApNpcT
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.spells.attack.SpellAttackRegistry
import org.rsmod.api.spells.attack.attack
import org.rsmod.api.spells.autocast.AutocastWeapons
import org.rsmod.content.skills.magic.spell.attacks.RoyalTitansWallSpells
import org.rsmod.game.entity.Npc
import org.rsmod.game.type.getInvObj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Inanimate wall casting is an interaction, not another five-tick PvN combat attack. */
internal class RoyalTitansWallInteractions
@Inject
constructor(
    private val encounters: RoyalTitansEncounterManager,
    private val spells: MagicSpellRegistry,
    private val attacks: SpellAttackRegistry,
    private val runes: MagicRuneManager,
    private val autocast: AutocastWeapons,
) : PluginScript() {
    private var ProtectedAccess.autocastEnabled by boolVarBit("varbit.autocast_set")
    private val ProtectedAccess.autocastSpell by intVarBit("varbit.autocast_spell")
    private val ProtectedAccess.defensiveCasting by boolVarBit("varbit.autocast_defmode")
    private val ProtectedAccess.spellbook by enumVarBit<Spellbook>("varbit.spellbook")

    override fun ScriptContext.startup() {
        for (id in listOf(RoyalTitansWallSpells.FIRE, RoyalTitansWallSpells.ICE)) {
            val type = requireNotNull(ServerCacheManager.getNpc(id))
            for (spell in spells.combatSpells()) {
                onApNpcT(type, spell.component) { cast(it.npc, spell) }
            }
            // Cache options are Douse/Melt (op1), not Attack (op2). Normal2283 T1339.
            onApNpc1(type.internalName) { autocast(it.npc) }
            onOpNpc1(type.internalName) { autocast(it.npc) }
        }
    }

    private suspend fun ProtectedAccess.autocast(target: Npc) {
        if (!encounters.canCastOnWall(player, target)) return
        if (!isWithinDistance(target, 10)) {
            apRange(10)
            return
        }
        val weapon = player.righthand
        val spell = if (autocastEnabled) spells.getAutocastSpell(autocastSpell) else null
        if (weapon == null || spell == null) {
            faceEntitySquare(target)
            mes(RoyalTitansWallSpells.rejection(target.visType.id))
            clearPendingAction()
            return
        }
        val weaponType = getInvObj(weapon)
        // Match the public validators used by native PvN's internal autocast resolver. This
        // interaction intentionally does not enter PvN's action-delay gate or damage pipeline.
        if (spell.spellbook != spellbook) {
            mes("You can't autocast that spell with your current active spellbook.")
            autocastEnabled = false
            autocast.reset(player, weaponType)
            clearPendingAction()
            return
        }
        if (!autocast.canStaffAutocast(player, weaponType, autocastSpell)) {
            clearPendingAction()
            return
        }
        if (!runes.canCastSpell(player, spell)) {
            autocastEnabled = false
            autocast.reset(player, weaponType)
            clearPendingAction()
            return
        }
        cast(target, spell)
    }

    private suspend fun ProtectedAccess.cast(target: Npc, spell: MagicSpell) {
        if (!encounters.canCastOnWall(player, target)) return
        if (!isWithinDistance(target, 10)) {
            apRange(10)
            return
        }
        faceEntitySquare(target)
        val handler = attacks[spell.obj.internalName]
        val elemental =
            MagicSpellChecks.isWindSpell(spell.obj) ||
                MagicSpellChecks.isWaterSpell(spell.obj) ||
                MagicSpellChecks.isEarthSpell(spell.obj) ||
                MagicSpellChecks.isFireSpell(spell.obj)
        if (handler == null || (!elemental && spell.spellbook != Spellbook.Ancients)) {
            mes(RoyalTitansWallSpells.rejection(target.visType.id))
            clearPendingAction()
            return
        }
        handler.attack(this, target, CombatAttack.Spell(player.righthand, spell, defensiveCasting))
    }
}
