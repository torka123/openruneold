package org.rsmod.content.bosses.obor

import jakarta.inject.Singleton
import org.rsmod.api.instances.InstanceId

public enum class OborRunPhase {
    Active,
    Defeated,
    Respawning,
}

/** Instance-owned claim state. [beginClaim] makes repeated chest clicks idempotent. */
@Singleton
public class OborRunState {
    private val phases = HashMap<InstanceId, OborRunPhase>()

    fun initialize(id: InstanceId) {
        phases[id] = OborRunPhase.Active
    }

    fun markDefeated(id: InstanceId) {
        phases[id] = OborRunPhase.Defeated
    }

    fun markActive(id: InstanceId) {
        phases[id] = OborRunPhase.Active
    }

    fun beginClaim(id: InstanceId): Boolean {
        if (phases[id] != OborRunPhase.Defeated) {
            return false
        }
        phases[id] = OborRunPhase.Respawning
        return true
    }

    fun phase(id: InstanceId): OborRunPhase? = phases[id]

    fun remove(id: InstanceId) {
        phases.remove(id)
    }
}
