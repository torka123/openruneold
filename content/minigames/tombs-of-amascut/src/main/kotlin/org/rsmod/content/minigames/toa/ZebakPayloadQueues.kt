package org.rsmod.content.minigames.toa

import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.game.MapClock
import org.rsmod.game.queue.WorldQueueList

/** Native queues mark readiness; the retained actor loops own spatial checks and payload order. */
internal class ZebakPayloadQueues(
    queues: WorldQueueList,
    private val process: WorldQueueListProcess,
    private val clock: MapClock,
    private val state: ZebakEncounter,
    private val valid: () -> Boolean,
) {
    private val blood = queues.createOwner()
    private val cloudExpiry = queues.createOwner()
    private val cloudContact = queues.createOwner()
    private val crocodileBites = queues.createOwner()

    val isClosed: Boolean
        get() = blood.isClosed

    fun armBlood(tick: Int, launch: () -> Unit) {
        blood.clear()
        if (blood.isClosed) return
        blood.add(ready = { clock.cycle >= tick }) {
            if (valid()) {
                // Re-arm from this eligible dispatch before the branch/target RNG, as before.
                armBlood(clock.cycle + ZebakTuning.BLOOD_INTERVAL, launch)
                launch()
            }
        }
    }

    fun processBlood() = process.processOwned(blood)

    fun addCloud(runtime: ZebakBloodCloudRuntime) {
        val expiry = clock.cycle + 40
        runtime.expiryTask =
            cloudExpiry.add(ready = { clock.cycle >= expiry }) {
                runtime.expiryTask = null
                if (validCloud(runtime)) runtime.expired = true
            }
        armCloudContact(runtime, delay = 0)
    }

    fun armCloudContact(
        runtime: ZebakBloodCloudRuntime,
        delay: Int = ZebakTuning.BLOOD_CLOUD_CONTACT_INTERVAL,
    ) {
        runtime.contactTask?.cancel()
        runtime.contactReady = false
        if (cloudContact.isClosed) return
        val tick = clock.cycle + delay
        runtime.contactTask =
            cloudContact.add(ready = { clock.cycle >= tick }) {
                runtime.contactTask = null
                if (validCloud(runtime)) runtime.contactReady = true
            }
    }

    fun cancelCloud(runtime: ZebakBloodCloudRuntime) {
        runtime.expiryTask?.cancel()
        runtime.expiryTask = null
        runtime.contactTask?.cancel()
        runtime.contactTask = null
        runtime.contactReady = false
    }

    fun processCloudReadiness() {
        process.processOwned(cloudExpiry)
        process.processOwned(cloudContact)
    }

    fun armCrocodileBite(runtime: ZebakWaterCrocodileRuntime) {
        runtime.biteTask?.cancel()
        runtime.biteReady = false
        if (crocodileBites.isClosed) return
        val tick = clock.cycle + ZebakTuning.WATER_CROCODILE_ATTACK_INTERVAL
        runtime.biteTask =
            crocodileBites.add(ready = { clock.cycle >= tick }) {
                runtime.biteTask = null
                if (valid() && state.waterCrocodiles[runtime.target] === runtime) {
                    runtime.biteReady = true
                }
            }
    }

    fun cancelCrocodile(runtime: ZebakWaterCrocodileRuntime) {
        runtime.biteTask?.cancel()
        runtime.biteTask = null
        runtime.biteReady = false
    }

    fun processCrocodileReadiness() = process.processOwned(crocodileBites)

    private fun validCloud(runtime: ZebakBloodCloudRuntime): Boolean =
        valid() && state.bloodClouds[runtime.npc.hashCode()] === runtime

    fun close() {
        blood.close()
        cloudExpiry.close()
        cloudContact.close()
        crocodileBites.close()
        state.bloodClouds.values.forEach(::cancelCloud)
        state.waterCrocodiles.values.forEach(::cancelCrocodile)
    }
}
