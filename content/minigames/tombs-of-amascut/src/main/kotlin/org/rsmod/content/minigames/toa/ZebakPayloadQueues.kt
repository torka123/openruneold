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
    private val jugCollisions = queues.createOwner()
    private val waterSplashes = queues.createOwner()
    private val rockCorpses = queues.createOwner()
    private val acidExposure = queues.createOwner()

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

    fun armJugCollision(runtime: ZebakJugRuntime) {
        runtime.collisionTask?.cancel()
        runtime.collisionPending = true
        runtime.collisionReady = false
        if (jugCollisions.isClosed) return
        val tick = clock.cycle + 1
        runtime.collisionTask =
            jugCollisions.add(ready = { clock.cycle >= tick }) {
                runtime.collisionTask = null
                if (valid() && state.jugs[runtime.npc.hashCode()] === runtime) {
                    runtime.collisionReady = true
                }
            }
    }

    fun processJugReadiness() = process.processOwned(jugCollisions)

    fun cancelJug(runtime: ZebakJugRuntime) {
        runtime.collisionTask?.cancel()
        runtime.collisionTask = null
        runtime.collisionPending = false
        runtime.collisionReady = false
    }

    fun scheduleWaterSplash(runtime: ZebakWaterSplashRuntime, cleanse: () -> Unit) {
        if (waterSplashes.isClosed) return
        state.waterSplashes += runtime
        val tick = clock.cycle + 1
        runtime.cleanseTask =
            waterSplashes.add(ready = { clock.cycle >= tick }) {
                runtime.cleanseTask = null
                val present = state.waterSplashes.removeAll { it === runtime }
                if (valid() && present) cleanse()
            }
    }

    fun processWaterSplashes() = process.processOwned(waterSplashes)

    fun armRockCorpse(runtime: ZebakRockRuntime) {
        runtime.collapseTask?.cancel()
        runtime.collapsing = true
        runtime.collapseReady = false
        if (rockCorpses.isClosed) return
        val tick = clock.cycle + ZebakTuning.ROAR_ROCK_CLEANUP_DELAY
        runtime.collapseTask =
            rockCorpses.add(ready = { clock.cycle >= tick }) {
                runtime.collapseTask = null
                if (valid() && state.rocks[runtime.npc.hashCode()] === runtime) {
                    runtime.collapseReady = true
                }
            }
    }

    fun processRockReadiness() = process.processOwned(rockCorpses)

    fun cancelRock(runtime: ZebakRockRuntime) {
        runtime.collapseTask?.cancel()
        runtime.collapseTask = null
        runtime.collapseReady = false
    }

    fun armAcidExposure(runtime: ZebakPlayerRuntime) {
        cancelAcidExposure(runtime)
        if (acidExposure.isClosed) return
        val tick = clock.cycle + ZebakTuning.ACID_TICK_INTERVAL
        runtime.acidTask =
            acidExposure.add(ready = { clock.cycle >= tick }) {
                runtime.acidTask = null
                // Player rebinding preserves the exposure object; do not capture an old UID.
                if (valid() && state.playerState.values.any { it === runtime }) {
                    runtime.acidReady = true
                }
            }
    }

    fun processAcidReadiness() = process.processOwned(acidExposure)

    fun resetAcidExposure(runtime: ZebakPlayerRuntime) {
        cancelAcidExposure(runtime)
        runtime.acidReady = true
    }

    fun cancelAcidExposure(runtime: ZebakPlayerRuntime) {
        runtime.acidTask?.cancel()
        runtime.acidTask = null
        runtime.acidReady = false
    }

    fun close() {
        blood.close()
        cloudExpiry.close()
        cloudContact.close()
        crocodileBites.close()
        jugCollisions.close()
        waterSplashes.close()
        rockCorpses.close()
        acidExposure.close()
        state.bloodClouds.values.forEach(::cancelCloud)
        state.waterCrocodiles.values.forEach(::cancelCrocodile)
        state.jugs.values.forEach(::cancelJug)
        state.rocks.values.forEach(::cancelRock)
        state.waterSplashes.forEach {
            it.cleanseTask?.cancel()
            it.cleanseTask = null
        }
        state.playerState.values.forEach(::cancelAcidExposure)
    }
}
