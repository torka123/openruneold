package org.rsmod.api.game.process.npc

import jakarta.inject.Inject
import org.rsmod.api.game.process.npc.hunt.NpcHuntProcessor
import org.rsmod.api.game.process.npc.mode.NpcModeProcessor
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.NpcRevealProcessor
import org.rsmod.api.utils.logging.GameExceptionHandler
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList

public class NpcMainProcess
@Inject
constructor(
    private val npcList: NpcList,
    private val registry: NpcRegistry,
    private val reveal: NpcRevealProcessor,
    private val hunt: NpcHuntProcessor,
    private val regen: NpcRegenProcessor,
    private val aiTimers: AiTimerProcessor,
    private val timers: NpcTimerProcessor,
    private val aiQueues: AiQueueProcessor,
    private val queues: NpcQueueProcessor,
    private val modes: NpcModeProcessor,
    private val interactions: NpcInteractionProcessor,
    private val mapClock: MapClock,
    private val exceptionHandler: GameExceptionHandler,
) {
    public fun process() {
        for (npc in npcList) {
            // Repository removal invalidates the slot immediately, but the current NpcList
            // iterator may still contain the old reference until this cycle finishes. Never let
            // that stale actor enter another processing stage.
            if (!npc.isSlotAssigned) continue
            npc.beginMainCycle(mapClock.cycle)
            npc.tryOrDespawn {
                resumePausedProcess()
                revealProcess()
                processHunt()
                processRegen()
                processAiTimer()
                processAiQueues()
                processQueues()
                processTimers()
                processModes()
                processInteractions()
            }
        }
    }

    private fun Npc.resumePausedProcess() {
        if (isProcessable) {
            advanceActiveCoroutine()
        }
    }

    private fun Npc.revealProcess() {
        if (isSlotAssigned) {
            reveal.process(this)
        }
    }

    private fun Npc.processHunt() {
        if (isProcessable) {
            hunt.process(this)
        }
    }

    private fun Npc.processRegen() {
        if (isProcessable) {
            regen.process(this)
        }
    }

    private fun Npc.processAiTimer() {
        if (isProcessable) {
            aiTimers.process(this)
        }
    }

    private fun Npc.processAiQueues() {
        if (isProcessable) {
            aiQueues.process(this)
        }
    }

    private fun Npc.processQueues() {
        if (isProcessable) {
            queues.process(this)
        }
    }

    private fun Npc.processTimers() {
        if (isProcessable) {
            timers.process(this)
        }
    }

    private fun Npc.processModes() {
        if (isProcessable) {
            modes.process(this)
        }
    }

    private fun Npc.processInteractions() {
        if (isProcessable) {
            interactions.process(this)
        }
    }

    private val Npc.isProcessable: Boolean
        get() = isSlotAssigned && canProcess

    private inline fun Npc.tryOrDespawn(block: Npc.() -> Unit) =
        try {
            block(this)
        } catch (e: Exception) {
            if (isSlotAssigned) registry.del(this)
            exceptionHandler.handle(e) { "Error processing main cycle for npc: $this." }
        } catch (e: NotImplementedError) {
            if (isSlotAssigned) registry.del(this)
            exceptionHandler.handle(e) { "Error processing main cycle for npc: $this." }
        }
}

internal fun Npc.beginMainCycle(cycle: Int) {
    processedMapClock = cycle
    // Scripted encounters can move an npc from an earlier actor's cycle. Preserve the origin of
    // that already-pending step instead of rewriting it to the destination and encoding a 0,0
    // walk during post-tick.
    if (!hasMovedThisCycle) previousCoords = coords
}
