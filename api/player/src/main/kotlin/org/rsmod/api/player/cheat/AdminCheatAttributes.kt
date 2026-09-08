package org.rsmod.api.player.cheat

import dev.or2.central.account.Rights
import org.rsmod.api.attr.AttributeKey
import org.rsmod.game.entity.Player

public val ADMIN_GOD_MODE_ATTR: AttributeKey<Boolean> = AttributeKey()

public val ADMIN_GOD_HITPOINTS_ATTR: AttributeKey<Int> = AttributeKey()

public val ADMIN_ONE_HIT_ATTR: AttributeKey<Boolean> = AttributeKey()

public val ADMIN_MAX_HIT_ATTR: AttributeKey<Boolean> = AttributeKey()

public const val ADMIN_GOD_HITPOINTS_MAX: Int = 10_000_000

public val ADMIN_ITEM_SEARCH_ATTR: AttributeKey<AdminItemSearchState> = AttributeKey()

public data class AdminItemSearchState(
    public val query: String,
    public val resultIds: List<Int>,
    public val totalMatches: Int,
    public val page: Int = 0,
)

public var Player.adminGodMode: Boolean
    get() {
        if (!modLevel.isAtLeast(Rights.ADMINISTRATOR)) {
            attr.remove(ADMIN_GOD_MODE_ATTR)
            return false
        }
        return attr[ADMIN_GOD_MODE_ATTR] == true
    }
    set(value) {
        if (value) {
            attr[ADMIN_GOD_MODE_ATTR] = true
        } else {
            attr.remove(ADMIN_GOD_MODE_ATTR)
        }
    }

/** A temporary high-capacity hitpoint pool used by the `::god` administrator command. */
public var Player.adminGodHitpoints: Int?
    get() {
        if (!modLevel.isAtLeast(Rights.ADMINISTRATOR)) {
            attr.remove(ADMIN_GOD_HITPOINTS_ATTR)
            return null
        }
        return attr[ADMIN_GOD_HITPOINTS_ATTR]
    }
    set(value) {
        if (value == null || value <= 0) {
            attr.remove(ADMIN_GOD_HITPOINTS_ATTR)
        } else {
            attr[ADMIN_GOD_HITPOINTS_ATTR] = value.coerceAtMost(ADMIN_GOD_HITPOINTS_MAX)
        }
    }

/** Makes every player-originated hit kill its npc target. */
public var Player.adminOneHit: Boolean
    get() {
        if (!modLevel.isAtLeast(Rights.ADMINISTRATOR)) {
            attr.remove(ADMIN_ONE_HIT_ATTR)
            return false
        }
        return attr[ADMIN_ONE_HIT_ATTR] == true
    }
    set(value) {
        if (value) {
            attr[ADMIN_ONE_HIT_ATTR] = true
        } else {
            attr.remove(ADMIN_ONE_HIT_ATTR)
        }
    }

public var Player.adminMaxHit: Boolean
    get() = attr[ADMIN_MAX_HIT_ATTR] == true
    set(value) {
        if (value) {
            attr[ADMIN_MAX_HIT_ATTR] = true
        } else {
            attr.remove(ADMIN_MAX_HIT_ATTR)
        }
    }

public var Player.adminItemSearch: AdminItemSearchState?
    get() = attr[ADMIN_ITEM_SEARCH_ATTR]
    set(value) {
        if (value == null) {
            attr.remove(ADMIN_ITEM_SEARCH_ATTR)
        } else {
            attr[ADMIN_ITEM_SEARCH_ATTR] = value
        }
    }
