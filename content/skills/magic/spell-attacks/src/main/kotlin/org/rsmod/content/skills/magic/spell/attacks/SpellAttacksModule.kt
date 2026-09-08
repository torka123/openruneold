package org.rsmod.content.skills.magic.spell.attacks

import org.rsmod.api.spells.attack.SpellAttackMap
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.PvPPlayerImpactHook
import org.rsmod.api.npc.hit.NpcDamageContributor
import org.rsmod.api.npc.hit.NpcAttackLaunchHook
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.content.skills.magic.spell.attacks.ancient.AncientCombatSpells
import org.rsmod.content.skills.magic.spell.attacks.arceuus.ArceuusCombatSpells
import org.rsmod.content.skills.magic.spell.attacks.arceuus.ArceuusSpellLifecycle
import org.rsmod.content.skills.magic.spell.attacks.arceuus.DarkLureLifecycle
import org.rsmod.content.skills.magic.spell.attacks.standard.CrumbleUndeadSpells
import org.rsmod.content.skills.magic.spell.attacks.standard.ElementalSpells
import org.rsmod.plugin.module.PluginModule

class SpellAttacksModule : PluginModule() {
    override fun bind() {
        addSetBinding<SpellAttackMap>(AncientCombatSpells::class.java)
        addSetBinding<SpellAttackMap>(ArceuusCombatSpells::class.java)
        addSetBinding<SpellAttackMap>(ElementalSpells::class.java)
        addSetBinding<SpellAttackMap>(CrumbleUndeadSpells::class.java)
        addSetBinding<PlayerPostTickHook>(ArceuusSpellLifecycle::class.java)
        addSetBinding<PlayerPostTickHook>(DarkLureLifecycle::class.java)
        addSetBinding<NpcAttackValidateHook>(ArceuusSpellLifecycle::class.java)
        addSetBinding<PvPPlayerImpactHook>(ArceuusSpellLifecycle::class.java)
        addSetBinding<NpcDamageContributor>(ArceuusSpellLifecycle::class.java)
        addSetBinding<NpcAttackLaunchHook>(ArceuusSpellLifecycle::class.java)
    }
}
