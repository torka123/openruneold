package org.rsmod.content.bosses.kraken

import org.rsmod.api.player.stat.hitpoints as currentHitpoints
import org.rsmod.game.entity.Player

/** Keeps the encounter guard explicit without leaking player-stat imports into lifecycle code. */
internal val Player.hitpoints: Int
    get() = currentHitpoints
