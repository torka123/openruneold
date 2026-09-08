package org.rsmod.content.minigames.giantsfoundry

import org.rsmod.api.attr.AttributeKey
import org.rsmod.game.entity.Player

internal val GIANTS_FOUNDRY_STATE =
    AttributeKey<MutableMap<String, Any>>(persistenceKey = "giants_foundry_state_v1")

internal sealed interface FoundryLoad {
    data class Ready(val state: FoundryState) : FoundryLoad

    data object Corrupt : FoundryLoad
}

internal fun Player.loadFoundryState(): FoundryLoad {
    val raw = attr[GIANTS_FOUNDRY_STATE] ?: return FoundryLoad.Ready(FoundryState())
    return FoundryStateCodec.decode(raw)?.let(FoundryLoad::Ready) ?: FoundryLoad.Corrupt
}

internal fun Player.saveFoundryState(state: FoundryState) {
    attr[GIANTS_FOUNDRY_STATE] = FoundryStateCodec.encode(state)
}
