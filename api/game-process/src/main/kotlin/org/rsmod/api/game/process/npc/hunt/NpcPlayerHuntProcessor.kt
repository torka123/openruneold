package org.rsmod.api.game.process.npc.hunt

import dev.openrune.ServerCacheManager
import dev.openrune.types.HuntModeType
import dev.openrune.types.hunt.HuntCheckNotTooStrong
import dev.openrune.types.hunt.HuntType
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.area.checker.isInWilderness
import org.rsmod.api.config.constants
import org.rsmod.api.hunt.Hunt
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.mapNpcMultiway
import org.rsmod.api.random.CoreRandom
import org.rsmod.api.random.GameRandom
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.type.getOrNull
import org.rsmod.game.vars.VarPlayerIntMap
import org.rsmod.map.CoordGrid
import java.util.WeakHashMap

public class NpcPlayerHuntProcessor
@Inject
constructor(
    @CoreRandom private val random: GameRandom,
    private val mapClock: MapClock,
    private val hunt: Hunt,
    private val areaChecker: AreaChecker,
    private val validateHooks: Set<NpcPlayerHuntValidateHook>,
    private val profileOverrides: Set<NpcPlayerHuntProfileOverride>,
) {
    /**
     * Profile providers are location-aware. Resolving them for every idle NPC on every game tick
     * is needlessly expensive, but caching only by NPC would be wrong when an NPC crosses a
     * Wilderness boundary. Keep the result for the current tile and refresh it after movement.
     */
    private val profileOverrideCache: WeakHashMap<Npc, CachedProfileOverride> = WeakHashMap()

    public fun process(npc: Npc) {
        if (
            !npc.isValidTarget() ||
                npc.isDelayed ||
                npc.isShoveStunned ||
                npc.ignoreCombatInteractions
        ) {
            return
        }
        if (!npc.hasInteraction()) npc.applyProfileOverride()

        val huntMode = npc.huntMode
        val huntDisabled = npc.huntRange == 0 || huntMode == null
        if (huntDisabled) {
            return
        }

        val skipHunt = !npc.isAnyoneNear()
        if (skipHunt) {
            return
        }

        val huntType = ServerCacheManager.getHunt(huntMode) ?: return
        val huntDelayed = npc.huntClock < huntType.rate - 1
        if (huntDelayed) {
            return
        }

        if (huntType.type == HuntType.Player && !npc.hasInteraction()) {
            npc.huntPlayer(huntType)
        }
    }

    private fun Npc.huntPlayer(mode: HuntModeType) {
        var target = PlayerUid.NULL
        var count = 0

        val players = hunt.findPlayers(coords, huntRange, mode.checkVis)
        for (player in players) {
            if (!player.isValidTarget() || player.pendingTeleport || player.pendingTelejump) {
                continue
            }

            if (validateHooks.any { !it.canHunt(this, player) }) {
                continue
            }

            if (mode.checkNotBusy && player.isBusy) {
                continue
            }

            if (mode.checkAfk && player.isAfk()) {
                continue
            }

            if (mode.checkNotTooStrong == HuntCheckNotTooStrong.OutsideWilderness) {
                if (
                    blockedByOutsideWildernessTolerance(
                        npcCombatLevel = type.combatLevel,
                        playerCombatLevel = player.combatLevel,
                        playerInWilderness = player.coords.isInWilderness(areaChecker),
                    )
                ) {
                    continue
                }
            }

            if (!player.isInMulti()) {
                if (mode.checkNotCombat != -1) {
                    val delay =
                        player.vars.huntVarp(mode.checkNotCombat) +
                            constants.combat_activecombat_delay
                    if (delay > mapClock.cycle) {
                        continue
                    }
                }

                if (mode.checkNotCombatSelf != -1) {
                    val varn =
                        ServerCacheManager.getVarn(mode.checkNotCombatSelf)
                            ?: error("Unable to find varn: ${mode.checkNotCombatSelf}")
                    val delay = vars[varn] + constants.combat_activecombat_delay
                    if (delay > mapClock.cycle) {
                        continue
                    }
                }
            }

            val checkVar1 = mode.checkVar1
            if (checkVar1 != null) {
                val varp =
                    ServerCacheManager.getVarp(checkVar1.varp) ?: error("Error finding varp 1")
                val actual = player.vars[varp]
                if (!checkVar1.evaluate(actual)) {
                    continue
                }
            }

            val checkVar2 = mode.checkVar2
            if (checkVar2 != null) {
                val varp =
                    ServerCacheManager.getVarp(checkVar2.varp) ?: error("Error finding varp 2")
                val actual = player.vars[varp]
                if (!checkVar2.evaluate(actual)) {
                    continue
                }
            }

            val checkVar3 = mode.checkVar3
            if (checkVar3 != null) {
                val varp =
                    ServerCacheManager.getVarp(checkVar3.varp) ?: error("Error finding varp 3")
                val actual = player.vars[varp]
                if (!checkVar3.evaluate(actual)) {
                    continue
                }
            }

            val checkInvObj = mode.checkInvObj
            if (checkInvObj != null) {
                val inventory = player.invMap.backing[checkInvObj.inv]

                val obj = checkInvObj.type
                val count = inventory?.sumOf { if (it?.id == obj) it.count else 0 } ?: 0

                if (!checkInvObj.evaluate(count)) {
                    continue
                }
            }

            val checkInvParam = mode.checkInvParam
            if (checkInvParam != null) {
                val inventory = player.invMap.backing[checkInvParam.inv]
                val param = checkInvParam.type

                var count = 0
                if (inventory != null) {
                    for (invObj in inventory) {
                        val objType = getOrNull(invObj) ?: continue
                        val value = objType.paramMap?.primitiveMap?.get(param) ?: continue
                        if (value !is Int) {
                            val message = "Expected param value to be Int: $value (param=$param)"
                            throw IllegalStateException(message)
                        }
                        count += value
                    }
                }

                if (!checkInvParam.evaluate(count)) {
                    continue
                }
            }

            count++
            if (random.of(minInclusive = 0, maxInclusive = count) == 0) {
                target = player.uid
            }
        }

        if (target != PlayerUid.NULL) {
            huntPlayer = target
        }
    }

    // TODO: Investigate what `checkAfk` actually checks.
    private fun Player.isAfk(): Boolean {
        return false
    }

    private fun Npc.applyProfileOverride() {
        val cached = profileOverrideCache[this]
        val profile =
            if (cached != null && cached.coords == coords) {
                cached.profile
            } else {
                resolveProfileOverride().also {
                    profileOverrideCache[this] = CachedProfileOverride(coords = coords, profile = it)
                }
            }
        if (profile == null) return
        require(profile.range > 0) { "Invalid player-hunt range: $profile" }
        val mode =
            requireNotNull(ServerCacheManager.getHunt(profile.modeId)) {
                "Unknown player-hunt mode: $profile"
            }
        require(mode.type == HuntType.Player) { "Non-player hunt profile: $profile" }
        if (huntMode != profile.modeId) {
            setHuntMode(mode)
        }
        if (huntRange != profile.range) {
            setHunt(profile.range)
        }
    }

    private fun Npc.resolveProfileOverride(): NpcPlayerHuntProfile? {
        var profile: NpcPlayerHuntProfile? = null
        for (override in profileOverrides) {
            val candidate = override.resolve(this) ?: continue
            if (profile == null) {
                profile = candidate
                continue
            }
            check(candidate.modeId == profile.modeId && candidate.range == profile.range) {
                "Conflicting player-hunt profiles for npc=${type.internalName} at $coords: " +
                    "first=$profile, conflicting=$candidate"
            }
        }
        return profile
    }

    // NPC-only multiway areas intentionally do not set the player's client multiway indicator.
    private fun Player.isInMulti(): Boolean {
        return mapNpcMultiway(areaChecker)
    }

    private data class CachedProfileOverride(
        val coords: CoordGrid,
        val profile: NpcPlayerHuntProfile?,
    )
}

/**
 * Hunt definitions store raw varp ids. Some of those ids, including the server-only
 * `varp.lastcombat` used by every aggressive hunt mode, do not have a client-cache definition and
 * therefore cannot be resolved through [ServerCacheManager.getVarp]. The player's backing map is
 * already keyed by those raw ids, so read it directly and retain the normal zero default.
 */
internal fun VarPlayerIntMap.huntVarp(id: Int): Int = backing.getOrDefault(id, 0)

internal fun blockedByOutsideWildernessTolerance(
    npcCombatLevel: Int,
    playerCombatLevel: Int,
    playerInWilderness: Boolean,
): Boolean = !playerInWilderness && playerCombatLevel > npcCombatLevel * 2
