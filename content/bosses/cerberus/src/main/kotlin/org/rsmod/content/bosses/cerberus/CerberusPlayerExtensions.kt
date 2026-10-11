package org.rsmod.content.bosses.cerberus

import org.rsmod.api.player.stat.hitpoints as currentHitpoints
import org.rsmod.game.entity.Player

internal val Player.hitpoints: Int
    get() = currentHitpoints
