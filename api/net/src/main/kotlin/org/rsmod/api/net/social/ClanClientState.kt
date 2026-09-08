package org.rsmod.api.net.social

import dev.openrune.ServerCacheManager
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

private const val CLAN_EMPTY = 0
private const val CLAN_LOADED = 3
private const val OWN_AFFINITY_STATE_VARBIT = 933
private const val GUEST_AFFINITY_STATE_VARBIT = 12_164
private const val GUEST_LOAD_FLAG_VARBIT = 12_165
private const val OWN_HOME_WORLD_VARBIT = 12_167
private const val GUEST_HOME_WORLD_VARBIT = 12_168
private const val GUEST_JOINED_VARBIT = 13_128
private const val OWN_LEFT_VARBIT = 13_805
private const val OWNER_CREATION_STATE_VARBIT = 13_058

public fun setClanCreationClientState(player: Player, enabled: Boolean) {
    setClanVarbit(player, OWNER_CREATION_STATE_VARBIT, if (enabled) 1 else 0)
}

internal fun setOwnClientState(player: Player, loaded: Boolean, homeWorld: Int = 0) {
    setClanVarbit(player, OWN_AFFINITY_STATE_VARBIT, if (loaded) CLAN_LOADED else CLAN_EMPTY)
    setClanVarbit(player, OWN_HOME_WORLD_VARBIT, if (loaded) homeWorld else 0)
    setClanVarbit(player, OWN_LEFT_VARBIT, if (loaded) 0 else 1)
}

internal fun setGuestClientState(player: Player, loaded: Boolean, homeWorld: Int = 0) {
    setClanVarbit(player, GUEST_AFFINITY_STATE_VARBIT, if (loaded) CLAN_LOADED else CLAN_EMPTY)
    setClanVarbit(player, GUEST_LOAD_FLAG_VARBIT, 0)
    setClanVarbit(player, GUEST_HOME_WORLD_VARBIT, if (loaded) homeWorld else 0)
    setClanVarbit(player, GUEST_JOINED_VARBIT, if (loaded) 1 else 0)
}

private fun setClanVarbit(player: Player, id: Int, value: Int) {
    val type = requireNotNull(ServerCacheManager.getVarbit(id)) { "Missing clan varbit $id" }
    VarPlayerIntMapSetter.set(player, type, value)
}
