package org.rsmod.content.minigames.toa

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.bosses.runtime.BossEncounter
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.BossStats
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.PhaseSpec
import org.rsmod.api.bosses.spec.Selector
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.instances.InstanceSession
import org.rsmod.game.MapClock
import org.rsmod.game.queue.WorldQueueList

/** The session owns cadence even when its exhausted Obelisk cannot supply a payload. */
internal class WardenObeliskQueues(
    session: InstanceSession,
    queues: WorldQueueList,
    private val process: WorldQueueListProcess,
    private val clock: MapClock,
    private val stillAttached: () -> Boolean,
) {
    private val owner = queues.createOwner()
    private var unregisterSession: () -> Unit = {}
    private var dispatched = false

    var nextAt: Int = -1
        private set

    init {
        unregisterSession = session.onCleanup(::close)
    }

    fun arm(deadline: Int, interval: Int, launch: () -> Unit) {
        require(interval > 0)
        check(!owner.isClosed) { "Warden Obelisk queue is closed." }
        clear()
        nextAt = deadline
        owner.add(ready = { clock.cycle >= deadline }) {
            if (!stillAttached()) {
                nextAt = -1
            } else {
                val revision = owner.revision
                val currentCycle = clock.cycle
                dispatched = true
                try {
                    launch()
                } catch (failure: Throwable) {
                    // The old dispatch advanced its deadline only after a successful payload.
                    if (!owner.isClosed && owner.revision == revision) {
                        arm(deadline, interval, launch)
                    }
                    throw failure
                }
                // The payload observes the old deadline. Cleanup or explicit rearm wins over
                // automatic recurrence, and a missing-actor no-op still advances the old cadence.
                if (!owner.isClosed && owner.revision == revision) {
                    arm(
                        WardenPhaseTwoCadence.advancePast(currentCycle, deadline, interval),
                        interval,
                        launch,
                    )
                }
            }
        }
    }

    fun process(): Boolean {
        dispatched = false
        process.processOwned(owner)
        return dispatched
    }

    fun clear() {
        owner.clear()
        nextAt = -1
    }

    fun close() {
        owner.close()
        nextAt = -1
        unregisterSession()
    }
}

// This exact choice instance owns a branch cursor on each exhausted Obelisk encounter.
private val wardenObeliskChoice =
    Effect.Choose(
        Selector.Rotation(WardenObeliskSpecial.entries.map { it.name }),
        WardenObeliskSpecial.entries.associate { it.name to Effect.NoOp },
    )

/** Ordinary AI is empty; the session's independently due dispatch selects the declared branch. */
internal fun wardenObeliskBossSpec(): BossSpec =
    BossSpec(
        npcTypes =
            listOf(requireNotNull(RSCM.getReverseMapping(RSCMType.NPC, WardenObelisk.EXHAUSTED))),
        stats = BossStats(),
        abilities = mapOf("obelisk" to wardenObeliskChoice),
        phases =
            mapOf(
                "obelisk" to
                    PhaseSpec(
                        "obelisk",
                        lockMovement = true,
                        selector = Selector.Rotation(emptyList()),
                    )
            ),
        triggers = emptyList(),
    )

internal fun BossEncounter.selectWardenObelisk(tick: Int): WardenObeliskSpecial =
    WardenObeliskSpecial.valueOf(checkNotNull(selectBranch(wardenObeliskChoice, tick, null)))
