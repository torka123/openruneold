package org.rsmod.content.skills.hunter

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hunterLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onAiTimer
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.loc.LocLayerConstants
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.slf4j.LoggerFactory

/**
 * Server-owned Hunter world logic.  Every trap is held in one authoritative record keyed by its
 * tile; dynamic loc replacement performs the visual, collision, and nearby-player update.
 */
public class HunterScript
@Inject
constructor(
    private val random: GameRandom,
    private val locs: LocRepository,
    private val npcs: NpcRepository,
) : PluginScript() {
    private val traps = hashMapOf<CoordGrid, HunterTrap>()
    private val registeredDirectIds = hashSetOf<Int>()

    override fun ScriptContext.startup() {
        registerTrapItems()
        registerTrapObjects()
        val trapNpcCount = registerTrapCreatures()
        val directNpcCount = registerDirectCreatures()
        registerHunterNpcLifecycle()
        auditCatchOptions()
        log.info(
            "Registered Hunter: {} trap creatures, {} direct creatures, {} trap object states",
            trapNpcCount,
            directNpcCount,
            HunterDefinitions.trappedObjectIds.size + HunterDefinitions.trapKinds.size * 2,
        )
    }

    private fun ScriptContext.registerTrapItems() {
        HunterDefinitions.trapKinds.forEach { kind ->
            if (ServerCacheManager.getItem(kind.itemId) == null) {
                log.warn("Hunter trap item {} is missing from revision 240", kind.itemId)
                return@forEach
            }
            onProtectedEvent<HeldObjEvents.Op1>(kind.itemId) { layTrap(kind) }
        }
    }

    private fun ScriptContext.registerTrapObjects() {
        val objectIds =
            HunterDefinitions.trapKinds.flatMap { listOf(it.readyLocId, it.failedLocId) } +
                HunterDefinitions.trappedObjectIds
        objectIds.distinct().forEach { id ->
            if (ServerCacheManager.getObject(id) == null) {
                log.warn("Hunter trap object {} is missing from revision 240", id)
                return@forEach
            }
            onProtectedEvent<LocEvents.Op1>(id) { interactTrap(it.loc) }
            onProtectedEvent<LocEvents.Op2>(id) { interactTrap(it.loc) }
            onProtectedEvent<LocEvents.Op3>(id) { interactTrap(it.loc) }
        }
    }

    private fun ScriptContext.registerTrapCreatures(): Int {
        HunterDefinitions.trapCreatures.forEach { creature ->
            val type = ServerCacheManager.getNpc(creature.npcId)
            if (type == null) {
                log.warn("Hunter creature {} ({}) is missing from revision 240", creature.npcId, creature.displayName)
                return@forEach
            }
            onAiTimer(type) { scanForTrap(npc, creature) }
        }
        return HunterDefinitions.trapCreatures.count { ServerCacheManager.getNpc(it.npcId) != null }
    }

    private fun ScriptContext.registerDirectCreatures(): Int {
        var count = 0
        HunterDefinitions.directByNpcId.values.forEach { configured ->
            val type = ServerCacheManager.getNpc(configured.npcId) ?: run {
                log.warn("Hunter creature {} ({}) is missing from revision 240", configured.npcId, configured.displayName)
                return@forEach
            }
            val lootId = resolveLootId(configured) ?: run {
                log.warn("Hunter creature {} ({}) has no matching reward item in revision 240", configured.npcId, configured.displayName)
                return@forEach
            }
            val creature = configured.copy(lootId = lootId)
            val option = if (creature.method == DirectMethod.NET) "Trap" else "Catch"
            val opIndex = (0 until 5).firstOrNull { type.actions.getOpOrNull(it).equals(option, true) }
                ?: (0 until 5).firstOrNull { type.actions.getOpOrNull(it).equals("Catch", true) }
            if (opIndex == null) {
                log.warn("Hunter creature {} ({}) has no {} option in revision 240", creature.npcId, type.name, option)
                return@forEach
            }
            when (opIndex) {
                0 -> onProtectedEvent<NpcEvents.Op1>(creature.npcId) { catchDirect(it.npc, creature) }
                1 -> onProtectedEvent<NpcEvents.Op2>(creature.npcId) { catchDirect(it.npc, creature) }
                2 -> onProtectedEvent<NpcEvents.Op3>(creature.npcId) { catchDirect(it.npc, creature) }
                3 -> onProtectedEvent<NpcEvents.Op4>(creature.npcId) { catchDirect(it.npc, creature) }
                4 -> onProtectedEvent<NpcEvents.Op5>(creature.npcId) { catchDirect(it.npc, creature) }
            }
            registeredDirectIds += creature.npcId
            count++
        }
        return count
    }    private fun ScriptContext.registerHunterNpcLifecycle() {
        onEvent<NpcStateEvents.Create> { HunterDefinitions.trapByNpcId[npc.type.id]?.let { npc.aiTimer(5) } }
        onEvent<NpcStateEvents.Respawn> { HunterDefinitions.trapByNpcId[npc.type.id]?.let { npc.aiTimer(5) } }
    }

    private fun ScriptContext.auditCatchOptions() {
        val nonHunterCatchIds = setOf(1817, 5573, 8523, 8667, 16346)
        val unsupported = ServerCacheManager.getNpcs().values
            .filter { type -> (0 until 5).any { type.actions.getOpOrNull(it).equals("Catch", true) } }
            .filterNot { it.id in registeredDirectIds || it.id in nonHunterCatchIds }
            .map { "${it.id}:${it.name}" }
        if (unsupported.isNotEmpty()) {
            log.warn("Hunter cache audit found {} unmapped Hunter Catch definitions: {}", unsupported.size, unsupported.joinToString())
        } else {
            log.info("Hunter cache audit mapped every Hunter Catch definition in revision 240.")
        }
    }    private fun ProtectedAccess.layTrap(kind: TrapKind) {
        if (player.hunterLvl < trapLevel(kind)) {
            mes("You need a Hunter level of ${trapLevel(kind)} to set a ${kind.displayName}.")
            return
        }
        val owner = player.username.lowercase()
        if (traps.values.count { it.owner == owner } >= HunterDefinitions.maxTraps(player.hunterLvl)) {
            mes("You cannot have any more traps set at your current Hunter level.")
            return
        }
        val coords = player.coords
        if (traps.containsKey(coords)) {
            mes("You need a clear space to set your ${kind.displayName}.")
            return
        }
        val type = ServerCacheManager.getObject(kind.readyLocId) ?: return
        val loc = LocInfo(LocLayerConstants.of(LocShape.CentrepieceStraight.id), coords, LocEntity(type.id, LocShape.CentrepieceStraight.id, LocAngle.North.id))
        if (!locs.add(loc, Int.MAX_VALUE)) {
            mes("You need a clear space to set your ${kind.displayName}.")
            return
        }
        if (invDel(inv, internalItem(kind.itemId), 1).failure) {
            check(locs.del(loc, Int.MAX_VALUE)) { "Failed to remove unowned Hunter trap $loc" }
            return
        }
        traps[coords] = HunterTrap(owner, player.hunterLvl, kind, TrapState.READY, loc, null)
        anim(kind.setAnimation)
        mes("You set up your ${kind.displayName}.")
    }

    private suspend fun ProtectedAccess.interactTrap(loc: org.rsmod.game.loc.BoundLocInfo) {
        arriveDelay()
        val trap = traps[loc.coords] ?: return
        if (player.coords.chebyshevDistance(loc.coords) > 1) return
        val owner = player.username.lowercase()
        if (trap.owner != owner) {
            mes("This is someone else's ${trap.kind.displayName}.")
            return
        }
        when (trap.state) {
            TrapState.CAUGHT -> claimTrap(trap)
            TrapState.FAILED -> resetTrap(trap)
            TrapState.READY -> dismantleTrap(trap)
        }
    }

    private fun ProtectedAccess.claimTrap(trap: HunterTrap) {
        val creature = trap.creature ?: return
        if (!canAdd(creature.lootId, 1) || !canAdd(trap.kind.itemId, 1)) {
            mes("You don't have enough inventory space to take your catch and trap.")
            return
        }
        if (!removeTrap(trap)) return
        check(invAdd(inv, internalItem(creature.lootId), 1).success)
        check(invAdd(inv, internalItem(trap.kind.itemId), 1).success)
        anim(trap.kind.resetAnimation)
        statAdvance(HUNTER_STAT, creature.baseXp)
        mes("You catch the ${creature.displayName}.")
    }

    private fun ProtectedAccess.resetTrap(trap: HunterTrap) {
        if (!replaceTrap(trap, trap.kind.readyLocId, TrapState.READY, null)) return
        anim(trap.kind.resetAnimation)
        mes("You reset your ${trap.kind.displayName}.")
    }

    private fun ProtectedAccess.dismantleTrap(trap: HunterTrap) {
        if (!canAdd(trap.kind.itemId, 1)) {
            mes("You don't have enough inventory space to pick up your ${trap.kind.displayName}.")
            return
        }
        if (!removeTrap(trap)) return
        check(invAdd(inv, internalItem(trap.kind.itemId), 1).success)
        anim(trap.kind.resetAnimation)
        mes("You pick up your ${trap.kind.displayName}.")
    }

    private fun scanForTrap(npc: Npc, creature: TrapCreature) {
        val trap = traps.values.firstOrNull {
            it.state == TrapState.READY &&
                it.kind == creature.trap &&
                it.loc.coords.level == npc.coords.level &&
                it.loc.coords.chebyshevDistance(npc.coords) <= TRAP_RANGE
        }
        if (trap != null) {
            val successChance = trapChance(trap.ownerLevel, creature.level)
            if (random.of(100) < successChance) {
                if (replaceTrap(trap, creature.caughtLocId, TrapState.CAUGHT, creature)) {
                    npcs.despawn(npc, CREATURE_RESPAWN_TICKS)
                    return
                }
            } else {
                replaceTrap(trap, trap.kind.failedLocId, TrapState.FAILED, null)
            }
        }
        npc.aiTimer(random.of(4..8))
    }

    private suspend fun ProtectedAccess.catchDirect(npc: Npc, creature: DirectCreature) {
        arriveDelay()
        if (player.coords.chebyshevDistance(npc.coords) > 1) return
        if (player.hunterLvl < creature.level) {
            mes("You need a Hunter level of ${creature.level} to catch this ${creature.displayName}.")
            return
        }
        val containerId = when (creature.method) {
            DirectMethod.IMPLING -> HunterDefinitions.EMPTY_IMPLING_JAR
            DirectMethod.BUTTERFLY -> HunterDefinitions.BUTTERFLY_JAR
            else -> null
        }
        when (creature.method) {
            DirectMethod.IMPLING -> {
                if (!inv.contains(internalItem(HunterDefinitions.EMPTY_IMPLING_JAR))) {
                    mes("You need an empty impling jar to catch this impling.")
                    return
                }
                if (!hasButterflyNet()) {
                    mes("You need a butterfly net to catch implings.")
                    return
                }
            }
            DirectMethod.BUTTERFLY -> {
                if (!inv.contains(internalItem(HunterDefinitions.BUTTERFLY_JAR)) || !hasButterflyNet()) {
                    mes("You need a butterfly net and empty butterfly jar to catch this creature.")
                    return
                }
            }
            DirectMethod.NET -> {
                if (!inv.contains(internalItem(HunterDefinitions.SMALL_FISHING_NET)) || !inv.contains(internalItem(HunterDefinitions.ROPE))) {
                    mes("You need a small fishing net and rope to trap this salamander.")
                    return
                }
            }
            DirectMethod.FALCONRY, DirectMethod.BAT -> Unit
        }
        faceEntitySquare(npc)
        delay(DIRECT_CATCH_TICKS)
        if (player.coords.chebyshevDistance(npc.coords) > 1) return
        if (random.of(100) >= directChance(creature)) {
            mes("The ${creature.displayName} escapes.")
            return
        }
        if (containerId != null && invDel(inv, internalItem(containerId), 1).failure) return
        if (invAdd(inv, internalItem(creature.lootId), 1).failure) {
            if (containerId != null) check(invAdd(inv, internalItem(containerId), 1).success)
            mes("You don't have enough inventory space to hold your catch.")
            return
        }
        npcs.despawn(npc, CREATURE_RESPAWN_TICKS)
        statAdvance(HUNTER_STAT, creature.baseXp)
        mes("You catch the ${creature.displayName}.")
    }    private fun ProtectedAccess.hasButterflyNet(): Boolean =
        inv.contains(internalItem(HunterDefinitions.BUTTERFLY_NET)) || inv.contains(internalItem(HunterDefinitions.MAGIC_BUTTERFLY_NET))

    private fun ProtectedAccess.canAdd(itemId: Int, count: Int): Boolean {
        val type = ServerCacheManager.getItem(itemId) ?: return false
        return type.isStackable && inv.any { it?.id == itemId } || inv.freeSpace() >= count
    }

    private fun trapChance(ownerLevel: Int, requiredLevel: Int): Int {
        return (50 + (ownerLevel - requiredLevel).coerceAtLeast(0) * 2).coerceAtMost(90)
    }

    private fun directChance(creature: DirectCreature): Int =
        when (creature.method) {
            DirectMethod.IMPLING -> 70
            DirectMethod.NET -> 80
            DirectMethod.BUTTERFLY -> 80
            DirectMethod.FALCONRY -> 85
            DirectMethod.BAT -> 90
        }

    private fun resolveLootId(creature: DirectCreature): Int? {
        if (creature.lootId > 0) return creature.lootId.takeIf { ServerCacheManager.getItem(it) != null }
        val name = creature.lootName ?: return null
        return ServerCacheManager.getItems().values.firstOrNull { it.name.equals(name, true) }?.id
            ?: ServerCacheManager.getItems().values.firstOrNull { it.name.startsWith(name, true) }?.id
    }    private fun trapLevel(kind: TrapKind): Int = if (kind == HunterDefinitions.birdSnare) 1 else 27

    private fun replaceTrap(
        trap: HunterTrap,
        replacementId: Int,
        state: TrapState,
        creature: TrapCreature?,
    ): Boolean {
        val replacement = LocInfo(trap.loc.layer, trap.loc.coords, LocEntity(replacementId, trap.loc.shapeId, trap.loc.angleId))
        if (!locs.del(trap.loc, Int.MAX_VALUE)) return false
        if (!locs.add(replacement, Int.MAX_VALUE)) {
            check(locs.add(trap.loc, Int.MAX_VALUE)) { "Failed to restore Hunter trap ${trap.loc}" }
            return false
        }
        traps[trap.loc.coords] = trap.copy(state = state, loc = replacement, creature = creature)
        return true
    }

    private fun removeTrap(trap: HunterTrap): Boolean {
        if (!locs.del(trap.loc, Int.MAX_VALUE)) return false
        traps.remove(trap.loc.coords)
        return true
    }

    private fun internalItem(id: Int): String = RSCM.getReverseMapping(RSCMType.OBJ, id)

    private data class HunterTrap(
        val owner: String,
        val ownerLevel: Int,
        val kind: TrapKind,
        val state: TrapState,
        val loc: LocInfo,
        val creature: TrapCreature?,
    )

    private enum class TrapState { READY, FAILED, CAUGHT }

    private companion object {
        private val log = LoggerFactory.getLogger(HunterScript::class.java)
        private const val HUNTER_STAT = "stat.hunter"
        private const val TRAP_RANGE = 2
        private const val CREATURE_RESPAWN_TICKS = 20
        private const val DIRECT_CATCH_TICKS = 2
    }
}
