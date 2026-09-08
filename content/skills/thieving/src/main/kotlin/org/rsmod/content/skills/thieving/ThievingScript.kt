package org.rsmod.content.skills.thieving

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.combat.commons.magic.ArceuusSpellState
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.thievingLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.MapClock
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.slf4j.LoggerFactory

/**
 * Revision-240 Thieving entry point.
 *
 * Interaction slots are discovered from the loaded cache instead of being assumed. This matters for
 * NPCs whose Pickpocket option is op1 and others whose Pickpocket option is op3. All XP passed to
 * [ProtectedAccess.statAdvance] is unmodified OSRS base XP; OpenRune applies the account and global
 * multipliers exactly once inside that API.
 */
public class ThievingScript
@Inject
constructor(
    private val random: GameRandom,
    private val locs: LocRepository,
    private val npcs: NpcRepository,
    private val clock: MapClock,
) : PluginScript() {
    override fun ScriptContext.startup() {
        val pickpockets = registerPickpockets()
        val stalls = registerStalls()
        val safes = registerWallSafes()
        registerCoinPouches()
        log.info(
            "Registered Thieving: {} pickpocket definitions, {} stall definitions, {} wall safes, {} coin pouches",
            pickpockets,
            stalls,
            safes,
            ThievingDefinitions.coinPouchValues.size,
        )
    }

    private fun ScriptContext.registerPickpockets(): Int {
        var count = 0
        val unsupported = mutableListOf<String>()
        for (type in ServerCacheManager.getNpcs().values) {
            val opIndex =
                (0 until 5).firstOrNull {
                    type.actions.getOpOrNull(it).equals("Pickpocket", ignoreCase = true)
                } ?: continue
            val definition = ThievingDefinitions.pickpocketForName(type.name)
            if (definition == null) {
                unsupported += "${type.id}:${type.name}"
                continue
            }
            when (opIndex) {
                0 -> onProtectedEvent<NpcEvents.Op1>(type.id) { pickpocket(it.npc, definition) }
                1 -> onProtectedEvent<NpcEvents.Op2>(type.id) { pickpocket(it.npc, definition) }
                2 -> onProtectedEvent<NpcEvents.Op3>(type.id) { pickpocket(it.npc, definition) }
                3 -> onProtectedEvent<NpcEvents.Op4>(type.id) { pickpocket(it.npc, definition) }
                4 -> onProtectedEvent<NpcEvents.Op5>(type.id) { pickpocket(it.npc, definition) }
            }
            count++
        }
        if (unsupported.isNotEmpty()) {
            log.info(
                "Skipped {} quest/decorative Pickpocket definitions without a supported OSRS profile (sample: {})",
                unsupported.size,
                unsupported.take(8).joinToString(),
            )
        }
        return count
    }

    private fun ScriptContext.registerStalls(): Int {
        var count = 0
        val inactive = mutableListOf<String>()
        for ((id, definition) in ThievingDefinitions.stallByObjectId) {
            val type = ServerCacheManager.getObject(id) ?: continue
            val opIndex =
                (0 until 5).firstOrNull {
                    val option = type.actions.getOpOrNull(it)
                    option.equals("Steal-from", ignoreCase = true) ||
                        option.equals("Steal", ignoreCase = true)
                }
            if (opIndex == null) {
                inactive += "$id:${type.name}"
                continue
            }
            when (opIndex) {
                0 -> onProtectedEvent<LocEvents.Op1>(id) { stealFromStall(it.loc, definition) }
                1 -> onProtectedEvent<LocEvents.Op2>(id) { stealFromStall(it.loc, definition) }
                2 -> onProtectedEvent<LocEvents.Op3>(id) { stealFromStall(it.loc, definition) }
                3 -> onProtectedEvent<LocEvents.Op4>(id) { stealFromStall(it.loc, definition) }
                4 -> onProtectedEvent<LocEvents.Op5>(id) { stealFromStall(it.loc, definition) }
            }
            count++
        }
        if (inactive.isNotEmpty()) {
            log.info(
                "Skipped {} inactive quest stall definitions without a cache interaction: {}",
                inactive.size,
                inactive.joinToString(),
            )
        }
        return count
    }

    private fun ScriptContext.registerWallSafes(): Int {
        var count = 0
        for (id in
            listOf(
                ThievingDefinitions.WALL_SAFE_CLOSED,
                ThievingDefinitions.WALL_SAFE_CLOSED_ALT,
            )) {
            val type = ServerCacheManager.getObject(id) ?: continue
            val opIndex =
                (0 until 5).firstOrNull {
                    type.actions.getOpOrNull(it).equals("Crack", ignoreCase = true)
                }
            if (opIndex == null) {
                log.warn("Configured wall safe {} ({}) has no Crack option", id, type.name)
                continue
            }
            when (opIndex) {
                0 -> onProtectedEvent<LocEvents.Op1>(id) { crackWallSafe(it.loc) }
                1 -> onProtectedEvent<LocEvents.Op2>(id) { crackWallSafe(it.loc) }
                2 -> onProtectedEvent<LocEvents.Op3>(id) { crackWallSafe(it.loc) }
                3 -> onProtectedEvent<LocEvents.Op4>(id) { crackWallSafe(it.loc) }
                4 -> onProtectedEvent<LocEvents.Op5>(id) { crackWallSafe(it.loc) }
            }
            count++
        }
        return count
    }

    private fun ScriptContext.registerCoinPouches() {
        for ((id, coins) in ThievingDefinitions.coinPouchValues) {
            if (ServerCacheManager.getItem(id) == null) {
                log.warn("Coin pouch item {} is missing from revision 240", id)
                continue
            }
            onProtectedEvent<HeldObjEvents.Op1>(id) {
                openCoinPouch(it.slot, it.obj.count, id, coins)
            }
            onProtectedEvent<HeldObjEvents.Op2>(id) { openCoinPouch(it.slot, 1, id, coins) }
        }
    }

    internal suspend fun ProtectedAccess.pickpocket(npc: Npc, definition: PickpocketDefinition) {
        arriveDelay()
        if (player.coords.chebyshevDistance(npc.coords) > 1) return
        if (player.thievingLvl < definition.level) {
            mes(
                "You need a Thieving level of ${definition.level} to pickpocket this ${definition.displayName}."
            )
            return
        }
        if (inv.isFull()) {
            mes(
                "You don't have enough inventory space to pickpocket this ${definition.displayName}."
            )
            return
        }

        faceEntitySquare(npc)
        anim(ThievingDefinitions.PICKPOCKET_ANIMATION)
        spam("You attempt to pick the ${definition.displayName}'s pocket.")
        delay(PICKPOCKET_INTERVAL_TICKS)
        if (player.coords.chebyshevDistance(npc.coords) > 1) return

        val success =
            random.of(100) < ThievingDefinitions.successChance(player.thievingLvl, definition)
        if (!success) {
            if (shadowVeilAvoidsFailure()) {
                spam("Your Shadow Veil conceals your failed pickpocket attempt.")
                return
            }
            failedPickpocket(npc, definition)
            return
        }

        val loot = roll(definition.loot)
        if (
            loot.pouchCoins > 0 && pouchCount(loot.itemId) >= ThievingDefinitions.MAX_COIN_POUCHES
        ) {
            mes(
                "You have too many coin pouches to pickpocket more from this ${definition.displayName}."
            )
            return
        }
        val doubled = rollRogueDouble()
        val rewards = rewardBundle(loot, doubled)
        if (!canFit(rewards)) {
            mes("You don't have enough inventory space to hold your loot.")
            return
        }
        if (!addAtomically(rewards)) return

        soundSynth(2581)
        spam("You pick the ${definition.displayName}'s pocket.")
        statAdvance(THIEVING_STAT, definition.baseXp)
    }

    private fun ProtectedAccess.failedPickpocket(npc: Npc, definition: PickpocketDefinition) {
        spam("You fail to pick the ${definition.displayName}'s pocket.")
        npc.playerFace(player)
        npc.say("What do you think you're doing?")
        if (dodgyNecklaceAvoidsFailure()) return
        npc.anim(definition.stunAnimation)
        anim(PLAYER_STUN_ANIMATION)
        takeInstantHit(HitType.Typeless, random.of(definition.failureDamage))
        player.applyShoveStun(FAILURE_STUN_TICKS)
    }

    private fun ProtectedAccess.shadowVeilAvoidsFailure(): Boolean {
        if (!ArceuusSpellState.hasShadowVeil(player, clock.cycle)) return false
        return random.of(CHANCE_SCALE) < SHADOW_VEIL_CHANCE
    }

    private fun ProtectedAccess.dodgyNecklaceAvoidsFailure(): Boolean {
        val slot = worn.indices.firstOrNull { worn[it]?.id == DODGY_NECKLACE } ?: return false
        if (random.of(CHANCE_SCALE) >= DODGY_NECKLACE_CHANCE) return false
        // Charges belong to the player, so changing necklaces or logging out must not refill them.
        val charges = player.attr.getOrDefault(DODGY_NECKLACE_CHARGES, 10).coerceIn(1, 10)
        val remaining = charges - 1
        if (remaining == 0) {
            check(invDel(worn, "obj.dodgy_necklace", 1, slot = slot).success)
            player.attr.remove(DODGY_NECKLACE_CHARGES)
            mes("Your dodgy necklace protects you. It then crumbles to dust.")
        } else {
            player.attr[DODGY_NECKLACE_CHARGES] = remaining
            mes("Your dodgy necklace protects you. It has $remaining charges left.")
        }
        return true
    }

    private suspend fun ProtectedAccess.stealFromStall(
        loc: BoundLocInfo,
        definition: StallDefinition,
    ) {
        arriveDelay()
        if (player.thievingLvl < definition.level) {
            mes(
                "You need a Thieving level of ${definition.level} to steal from this ${definition.displayName}."
            )
            return
        }
        if (inv.isFull()) {
            mes("You don't have enough inventory space to steal from this stall.")
            return
        }

        val watcher = findStallWatcher(loc)
        if (watcher != null) {
            watcher.playerFace(player)
            watcher.say("Hey! Get your hands off there!")
            mes("The stall owner catches you trying to steal.")
            return
        }

        faceLoc(loc)
        anim(ThievingDefinitions.STALL_ANIMATION)
        delay(STALL_ACTION_TICKS)
        if (player.coords.chebyshevDistance(loc.coords) > 2) return

        val loot = roll(definition.loot)
        val rewards = rewardBundle(loot, rollRogueDouble())
        if (!canFit(rewards)) {
            mes("You don't have enough inventory space to hold your loot.")
            return
        }

        // Depleting the real world object is the synchronization lock. Only the first interaction
        // can remove it; later same-tick or stale clicks fail without receiving XP or items.
        if (!replaceObject(loc, definition.emptyObjectId, definition.respawnTicks)) return
        if (!addAtomically(rewards)) return
        spam("You steal from the ${definition.displayName}.")
        statAdvance(THIEVING_STAT, definition.baseXp)
    }

    private suspend fun ProtectedAccess.crackWallSafe(loc: BoundLocInfo) {
        arriveDelay()
        if (player.thievingLvl < ThievingDefinitions.WALL_SAFE_LEVEL) {
            mes(
                "You need a Thieving level of ${ThievingDefinitions.WALL_SAFE_LEVEL} to crack this safe."
            )
            return
        }
        if (inv.isFull()) {
            mes("You don't have enough inventory space to take anything from the safe.")
            return
        }

        faceLoc(loc)
        anim(WALL_SAFE_START_ANIMATION)
        soundSynth(WALL_SAFE_START_SOUND)
        delay(WALL_SAFE_ACTION_TICKS)
        if (player.coords.chebyshevDistance(loc.coords) > 2) return

        val chance =
            ThievingDefinitions.wallSafeChance(
                player.thievingLvl,
                inv.any { it?.id == ThievingDefinitions.STETHOSCOPE },
            )
        if (random.of(100) >= chance) {
            anim(WALL_SAFE_TRAP_ANIMATION)
            soundSynth(WALL_SAFE_TRAP_SOUND)
            takeInstantHit(HitType.Typeless, random.of(2..6))
            mes("You trigger a trap inside the safe.")
            player.applyShoveStun(WALL_SAFE_FAILURE_STUN_TICKS)
            return
        }

        val loot = roll(WALL_SAFE_LOOT)
        val rewards = rewardBundle(loot, rollRogueDouble())
        if (!canFit(rewards)) {
            mes("You don't have enough inventory space to hold your loot.")
            return
        }
        if (!replaceObject(loc, ThievingDefinitions.WALL_SAFE_OPEN, WALL_SAFE_RESPAWN_TICKS)) return
        if (!addAtomically(rewards)) return

        anim(WALL_SAFE_SUCCESS_ANIMATION)
        soundSynth(WALL_SAFE_SUCCESS_SOUND)
        mes("You crack the safe and take its contents.")
        statAdvance(THIEVING_STAT, ThievingDefinitions.WALL_SAFE_XP)
    }

    private fun ProtectedAccess.openCoinPouch(
        slot: Int,
        requested: Int,
        pouchId: Int,
        coinValue: Int,
    ) {
        val current = inv[slot] ?: return
        if (current.id != pouchId) return
        val amount = requested.coerceIn(1, current.count)
        val coins = Math.multiplyExact(amount, coinValue)
        val coinInternal = internalItem(ThievingDefinitions.COINS)
        val pouchInternal = internalItem(pouchId)

        if (invDel(inv, pouchInternal, amount, slot = slot).failure) return
        if (invAdd(inv, coinInternal, coins).failure) {
            check(invAdd(inv, pouchInternal, amount).success) {
                "Failed to roll back coin pouch opening"
            }
            mes("You don't have enough inventory space to open that pouch.")
            return
        }
        mes(if (amount == 1) "You open the coin pouch." else "You open $amount coin pouches.")
    }

    private fun ProtectedAccess.findStallWatcher(loc: BoundLocInfo): Npc? =
        npcs.findAll(ZoneKey.from(loc.coords), zoneRadius = 1).firstOrNull { npc ->
            if (
                npc.coords.level != loc.coords.level ||
                    npc.coords.chebyshevDistance(loc.coords) > WATCH_RANGE
            ) {
                return@firstOrNull false
            }
            val name = ServerCacheManager.getNpc(npc.id)?.name?.lowercase().orEmpty()
            watcherTerms.any(name::contains) && lineOfSight(npc.coords, player.coords)
        }

    private fun ProtectedAccess.replaceObject(
        loc: BoundLocInfo,
        replacementId: Int,
        ticks: Int,
    ): Boolean {
        val original = LocInfo(loc.layer, loc.coords, LocEntity(loc.id, loc.shapeId, loc.angleId))
        val replacement =
            LocInfo(loc.layer, loc.coords, LocEntity(replacementId, loc.shapeId, loc.angleId))
        if (!locs.del(loc, Int.MAX_VALUE)) return false
        val added = locs.add(replacement, ticks) { locs.add(original, Int.MAX_VALUE) }
        if (!added) {
            check(locs.add(original, Int.MAX_VALUE)) {
                "Failed to restore Thieving object $original"
            }
            return false
        }
        return true
    }

    private fun ProtectedAccess.rollRogueDouble(): Boolean {
        val pieces = player.worn.count { it?.id in ThievingDefinitions.roguePieces }
        return random.of(100) < ThievingDefinitions.rogueDoubleChance(pieces)
    }

    private fun roll(table: List<ThievingLoot>): ThievingLoot {
        val total = table.sumOf(ThievingLoot::weight)
        return ThievingDefinitions.selectLoot(table, random.of(total))
    }

    private fun rewardBundle(loot: ThievingLoot, doubled: Boolean): Map<Int, Int> {
        val amount = random.of(loot.minimum, loot.maximum)
        if (!doubled) return mapOf(loot.itemId to amount)
        return if (loot.pouchCoins > 0) {
            mapOf(loot.itemId to amount, ThievingDefinitions.COINS to loot.pouchCoins * amount)
        } else {
            mapOf(loot.itemId to amount * 2)
        }
    }

    private fun ProtectedAccess.pouchCount(itemId: Int): Int =
        inv.filterNotNull { it.id == itemId }.sumOf { it.count }

    private fun ProtectedAccess.canFit(rewards: Map<Int, Int>): Boolean {
        var slots = inv.freeSpace()
        for ((id, count) in rewards) {
            val type = ServerCacheManager.getItem(id) ?: return false
            if (type.isStackable) {
                if (inv.none { it?.id == id } && slots-- <= 0) return false
            } else {
                if (slots < count) return false
                slots -= count
            }
        }
        return true
    }

    private fun ProtectedAccess.addAtomically(rewards: Map<Int, Int>): Boolean {
        val added = mutableListOf<Pair<String, Int>>()
        for ((id, count) in rewards) {
            val internal = internalItem(id)
            if (invAdd(inv, internal, count).failure) {
                added.forEach { (item, addedCount) -> check(invDel(inv, item, addedCount).success) }
                mes("You don't have enough inventory space to hold your loot.")
                return false
            }
            added += internal to count
        }
        return true
    }

    private fun internalItem(id: Int): String = RSCM.getReverseMapping(RSCMType.OBJ, id)

    private companion object {
        private val log = LoggerFactory.getLogger(ThievingScript::class.java)
        private const val THIEVING_STAT = "stat.thieving"
        private const val PICKPOCKET_INTERVAL_TICKS = 2
        private const val STALL_ACTION_TICKS = 2
        private const val FAILURE_STUN_TICKS = 8
        private const val PLAYER_STUN_ANIMATION = 424
        private const val WATCH_RANGE = 5
        private const val DODGY_NECKLACE = 21143
        private const val CHANCE_SCALE = 10_000
        private const val SHADOW_VEIL_CHANCE = 1_500
        private const val DODGY_NECKLACE_CHANCE = 2_500
        private val DODGY_NECKLACE_CHARGES = AttributeKey<Int>("dodgy_necklace_charges")

        private const val WALL_SAFE_START_ANIMATION = 2247
        private const val WALL_SAFE_SUCCESS_ANIMATION = 2248
        private const val WALL_SAFE_TRAP_ANIMATION = 1113
        private const val WALL_SAFE_START_SOUND = 1243
        private const val WALL_SAFE_SUCCESS_SOUND = 1242
        private const val WALL_SAFE_TRAP_SOUND = 1238
        private const val WALL_SAFE_ACTION_TICKS = 4
        private const val WALL_SAFE_RESPAWN_TICKS = 3
        private const val WALL_SAFE_FAILURE_STUN_TICKS = 3

        private val watcherTerms =
            listOf(
                "guard",
                "baker",
                "merchant",
                "trader",
                "stall owner",
                "silk merchant",
                "fur trader",
                "greengrocer",
                "spice seller",
                "gem merchant",
                "silver merchant",
            )

        private val WALL_SAFE_LOOT =
            listOf(
                ThievingLoot(ThievingDefinitions.COINS, 20, 20, 40),
                ThievingLoot(ThievingDefinitions.COINS, 40, 40, 25),
                ThievingLoot(1623, weight = 15),
                ThievingLoot(1621, weight = 10),
                ThievingLoot(1619, weight = 7),
                ThievingLoot(1617, weight = 3),
            )
    }
}
