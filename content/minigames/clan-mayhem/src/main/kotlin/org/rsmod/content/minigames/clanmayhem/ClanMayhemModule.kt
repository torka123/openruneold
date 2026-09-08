package org.rsmod.content.minigames.clanmayhem

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.death.PvPAttackValidateHook
import org.rsmod.api.death.PvPPlayerHitHook
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.plugin.module.PluginModule

public class ClanMayhemModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(ClanMayhemNpcAttackHook::class.java)
        addSetBinding<PvPAttackValidateHook>(ClanMayhemPvpHook::class.java)
        addSetBinding<PvPPlayerHitHook>(ClanMayhemPvpHook::class.java)
        addSetBinding<PlayerDeathHook>(ClanMayhemPlayerDeathHook::class.java)
        addSetBinding<PlayerDeathCleanupHook>(ClanMayhemDeathCleanupHook::class.java)
        addSetBinding<PlayerPostTickHook>(ClanMayhemLifecycle::class.java)
        addSetBinding<PlayerTeleportValidateHook>(ClanMayhemTeleportHook::class.java)
    }
}
