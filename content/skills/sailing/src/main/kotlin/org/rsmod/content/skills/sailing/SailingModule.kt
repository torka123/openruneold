package org.rsmod.content.skills.sailing

import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.player.hook.PlayerTeleportHook
import org.rsmod.content.skills.sailing.ownership.SailingOwnershipPipeline
import org.rsmod.plugin.module.PluginModule

class SailingModule : PluginModule() {
    override fun bind() {
        addSetBinding<CharacterDataStage.Pipeline>(SailingOwnershipPipeline::class.java)
        addSetBinding<PlayerTeleportHook>(SailingTeleportHook::class.java)
    }
}
