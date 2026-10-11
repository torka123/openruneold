package org.rsmod.content.bosses.bryophyta

import jakarta.inject.Singleton
import org.rsmod.api.instances.InstanceId

public enum class BryophytaRunPhase {
    Active,
    Defeated,
    Respawning,
}

private data class BryophytaInstanceState(
    var phase: BryophytaRunPhase = BryophytaRunPhase.Active
)

@Singleton
public class BryophytaRunState {
    private val states = HashMap<InstanceId, BryophytaInstanceState>()

    public fun initialize(id: InstanceId) {
        states[id] = BryophytaInstanceState()
    }

    public fun markDefeated(id: InstanceId) {
        states[id]?.phase = BryophytaRunPhase.Defeated
    }

    public fun beginClaim(id: InstanceId): Boolean {
        val state = states[id] ?: return false
        if (state.phase != BryophytaRunPhase.Defeated) return false
        state.phase = BryophytaRunPhase.Respawning
        return true
    }

    public fun markActive(id: InstanceId) {
        states[id]?.phase = BryophytaRunPhase.Active
    }

    public fun phase(id: InstanceId): BryophytaRunPhase? = states[id]?.phase

    public fun remove(id: InstanceId) {
        states.remove(id)
    }
}
