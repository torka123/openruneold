package org.rsmod.content.areas.wilderness

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.util.Wearpos
import org.rsmod.annotations.InternalApi
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.config.constants
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType

enum class SkullSource(val timerTicks: Int) {
    COMBAT(3000),
    ABYSS(1000),
    VOLUNTARY(3000),
    POST_EQUIP(2000),
    CAPE(2000),
    FORINTHRY_SURGE(3000),
    EQUIPMENT(Int.MAX_VALUE),
}

internal const val EMBLEM_EXTEND_TICKS = 2000

internal val IS_SKULLED = AttributeKey<Boolean>(persistenceKey = "is_skulled")
internal val SKULL_ICON_TYPE = AttributeKey<Int>(persistenceKey = "skull_icon_type")
internal val SKULL_SOURCE = AttributeKey<SkullSource>()
internal val SKULL_EQUIP_LOCKED = AttributeKey<Boolean>(persistenceKey = "skull_equip_locked")
internal val FORINTHRY_LAST_WORLD_ID =
    AttributeKey<Int>(persistenceKey = "forinthry_surge_last_world_id")
internal val WILDY_LOOT_KEY_COUNT = AttributeKey<Int>()

private var Player.forinthrySurgeExpiration by intVarp("varp.forinthry_surge_expiration")
private var Player.skullPreventionEnabled by boolVarBit("varbit.skull_prevent_enabled")

internal fun Player.isSkulled(): Boolean = attr.getOrDefault(IS_SKULLED, false)

internal fun Player.isSkullPreventionEnabled(): Boolean = skullPreventionEnabled

internal fun Player.isSkullEquipLocked(): Boolean = attr.getOrDefault(SKULL_EQUIP_LOCKED, false)

internal fun Player.applySkull(
    source: SkullSource = SkullSource.COMBAT,
    skullType: Int = constants.skullicon_default,
) {
    attr[IS_SKULLED] = true
    attr[SKULL_ICON_TYPE] = skullType
    attr[SKULL_SOURCE] = source
    attr[SKULL_EQUIP_LOCKED] = false
    if (source != SkullSource.FORINTHRY_SURGE) {
        forinthrySurgeExpiration = 0
        attr.remove(FORINTHRY_LAST_WORLD_ID)
    }
    if (source.timerTicks != Int.MAX_VALUE) {
        softTimer("timer.skull_timer", source.timerTicks)
    } else {
        clearSoftTimer("timer.skull_timer")
    }
    refreshSkullIcon()
}

internal fun Player.applyEquipmentSkull() {
    attr[IS_SKULLED] = true
    attr[SKULL_ICON_TYPE] = constants.skullicon_default
    attr[SKULL_SOURCE] = SkullSource.EQUIPMENT
    attr[SKULL_EQUIP_LOCKED] = true
    forinthrySurgeExpiration = 0
    attr.remove(FORINTHRY_LAST_WORLD_ID)
    clearSoftTimer("timer.skull_timer")
    refreshSkullIcon()
}

internal fun Player.applyPostEquipSkull() {
    attr[SKULL_EQUIP_LOCKED] = false
    applySkull(SkullSource.POST_EQUIP)
}

internal fun Player.applyVoluntarySkull() {
    if (isSkullEquipLocked()) {
        return
    }
    applySkull(SkullSource.VOLUNTARY)
}

internal fun Player.extendEmblemTraderSkull(): Boolean {
    if (isSkullEquipLocked() || !isSkulled()) {
        return false
    }
    if (skullTimerRemaining() >= EMBLEM_EXTEND_TICKS) {
        return false
    }
    attr[SKULL_SOURCE] = SkullSource.VOLUNTARY
    softTimer("timer.skull_timer", EMBLEM_EXTEND_TICKS)
    return true
}

internal fun Player.isWearingAvarice(): Boolean =
    worn[Wearpos.Front.slot]?.isType("obj.wild_cave_amulet") == true

internal fun Player.applyForinthrySurge(worldId: Int, now: Int = currentEpochSecond()) {
    if (!isWearingAvarice()) return
    attr[IS_SKULLED] = true
    attr[SKULL_ICON_TYPE] = constants.skullicon_forinthry_surge
    attr[SKULL_SOURCE] = SkullSource.FORINTHRY_SURGE
    attr[SKULL_EQUIP_LOCKED] = true
    attr[FORINTHRY_LAST_WORLD_ID] = worldId
    forinthrySurgeExpiration = ForinthrySurgeTime.startExpiry(now)
    softTimer(
        "timer.skull_timer",
        ForinthrySurgeTime.secondsToTicks(ForinthrySurgeTime.DURATION_SECONDS),
    )
    refreshSkullIcon()
}

internal fun Player.hasForinthrySurge(now: Int = currentEpochSecond()): Boolean =
    isWearingAvarice() &&
        attr.getOrDefault(SKULL_ICON_TYPE, constants.skullicon_default) ==
            constants.skullicon_forinthry_surge &&
        ForinthrySurgeTime.remainingSeconds(forinthrySurgeExpiration, now) > 0

internal fun Player.hasForinthrySurgeMarker(): Boolean =
    forinthrySurgeExpiration > 0 ||
        attr.getOrDefault(SKULL_ICON_TYPE, constants.skullicon_default) ==
            constants.skullicon_forinthry_surge

internal fun Player.forinthrySurgeRemainingSeconds(now: Int = currentEpochSecond()): Int =
    if (isWearingAvarice()) {
        ForinthrySurgeTime.remainingSeconds(forinthrySurgeExpiration, now)
    } else {
        0
    }

internal fun Player.syncForinthrySurgeOnLogin(worldId: Int, now: Int = currentEpochSecond()) {
    if (!hasForinthrySurgeMarker()) return
    if (!isWearingAvarice()) {
        clearForinthrySurge()
        return
    }
    forinthrySurgeExpiration =
        ForinthrySurgeTime.applyWorldHopPenalty(
            expiry = forinthrySurgeExpiration,
            now = now,
            previousWorld = attr[FORINTHRY_LAST_WORLD_ID],
            currentWorld = worldId,
        )
    attr[FORINTHRY_LAST_WORLD_ID] = worldId
    if (ForinthrySurgeTime.remainingSeconds(forinthrySurgeExpiration, now) <= 0) {
        clearForinthrySurge()
        return
    }
    attr[IS_SKULLED] = true
    attr[SKULL_ICON_TYPE] = constants.skullicon_forinthry_surge
    attr[SKULL_SOURCE] = SkullSource.FORINTHRY_SURGE
    attr[SKULL_EQUIP_LOCKED] = true
    softTimer(
        "timer.skull_timer",
        ForinthrySurgeTime.secondsToTicks(
            ForinthrySurgeTime.remainingSeconds(forinthrySurgeExpiration, now)
        ),
    )
    refreshSkullIcon()
}

internal fun Player.clearForinthrySurge() {
    if (!hasForinthrySurgeMarker()) return
    forinthrySurgeExpiration = 0
    attr.remove(FORINTHRY_LAST_WORLD_ID)
    attr[SKULL_ICON_TYPE] = constants.skullicon_default
    clearSoftTimer("timer.skull_timer")
    if (isWearingAvarice()) {
        attr[SKULL_SOURCE] = SkullSource.EQUIPMENT
        attr[SKULL_EQUIP_LOCKED] = true
    } else {
        attr.remove(SKULL_SOURCE)
    }
    refreshSkullIcon()
}

internal fun Player.unlockEquipmentSkull() {
    attr[SKULL_EQUIP_LOCKED] = false
    clearForinthrySurge()
}

internal fun Player.clearSkull(force: Boolean = false) {
    if (!force && isSkullEquipLocked()) {
        return
    }
    attr[IS_SKULLED] = false
    attr.remove(SKULL_SOURCE)
    attr.remove(SKULL_EQUIP_LOCKED)
    forinthrySurgeExpiration = 0
    attr.remove(FORINTHRY_LAST_WORLD_ID)
    clearSoftTimer("timer.skull_timer")
    skullIcon = null
}

internal fun Player.setSkull(skulled: Boolean) {
    if (skulled) applySkull() else clearSkull()
}

@OptIn(InternalApi::class)
internal fun Player.skullTimerRemaining(): Int {
    val timerId = "timer.skull_timer".asRSCM(RSCMType.TIMER).toShort()
    val packed = softTimerMap[timerId] ?: return 0
    val expiry = softTimerMap.extractExpiry(packed)
    return (expiry - currentMapClock).coerceAtLeast(0)
}

internal fun Player.expireForinthrySurgeIfNeeded(now: Int = currentEpochSecond()) {
    if (!hasForinthrySurgeMarker()) return
    if (
        !isWearingAvarice() ||
            ForinthrySurgeTime.remainingSeconds(forinthrySurgeExpiration, now) <= 0
    ) {
        clearForinthrySurge()
    }
}

internal object ForinthrySurgeTime {
    const val DURATION_SECONDS: Int = 30 * 60
    const val WORLD_HOP_PENALTY_SECONDS: Int = 60

    fun startExpiry(now: Int): Int = now + DURATION_SECONDS

    fun applyWorldHopPenalty(expiry: Int, now: Int, previousWorld: Int?, currentWorld: Int): Int =
        if (previousWorld != null && previousWorld != currentWorld && expiry > now) {
            (expiry - WORLD_HOP_PENALTY_SECONDS).coerceAtLeast(now)
        } else {
            expiry
        }

    fun remainingSeconds(expiry: Int, now: Int): Int = (expiry - now).coerceAtLeast(0)

    fun secondsToTicks(seconds: Int): Int = ((seconds.coerceAtLeast(0) * 5) + 2) / 3
}

private fun currentEpochSecond(): Int =
    (System.currentTimeMillis() / 1_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

internal fun Player.refreshSkullIcon() {
    expireForinthrySurgeIfNeeded()
    val keyCount = countWildyLootKeys()
    attr[WILDY_LOOT_KEY_COUNT] = keyCount
    if (!attr.getOrDefault(IS_SKULLED, false)) {
        skullIcon = null
        return
    }
    val baseIcon = attr.getOrDefault(SKULL_ICON_TYPE, constants.skullicon_default)
    val cappedKeyCount = keyCount.coerceIn(0, 5)
    skullIcon =
        when {
            cappedKeyCount > 0 && baseIcon == constants.skullicon_forinthry_surge ->
                constants.skullicon_forinthry_surge_keys_1 + (cappedKeyCount - 1)
            cappedKeyCount > 0 -> constants.skullicon_loot_key_1 + (cappedKeyCount - 1)
            else -> baseIcon
        }
}

internal fun Player.refreshSkullIconIfLootKeysChanged() {
    val keyCount = countWildyLootKeys()
    if (keyCount == attr.getOrDefault(WILDY_LOOT_KEY_COUNT, 0)) {
        return
    }
    refreshSkullIcon()
}

private fun Player.countWildyLootKeys(): Int {
    var total = 0
    for (i in 0..4) {
        total += inv.physicalCount("obj.wildy_loot_key$i")
    }
    return total
}
