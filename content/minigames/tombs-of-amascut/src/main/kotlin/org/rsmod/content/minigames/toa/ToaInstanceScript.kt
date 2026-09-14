package org.rsmod.content.minigames.toa

import jakarta.inject.Inject
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceScript
import org.rsmod.api.instances.InstanceSettings
import org.rsmod.api.instances.InstanceSpec
import org.rsmod.api.instances.events.InstancePlayerExternalTeleportEvent
import org.rsmod.api.script.onEvent
import org.rsmod.plugin.scripts.ScriptContext

/** The raid's transaction still admits the complete party before finalizing native membership. */
internal class ToaInstanceScript
@Inject
constructor(registry: BossInstanceRegistry, private val raids: ToaRaidManager) :
    InstanceScript(registry, TOA_INSTANCE_KEY, toaInstanceSpec()) {
    override fun ScriptContext.configure() {
        onInstancePlayerJoin { manager.sessionForId(instanceId)?.let { raids.onJoin(player, it) } }
        onInstancePlayerLeave { raids.onLeave(player, instanceId) }
        onEvent<InstancePlayerExternalTeleportEvent> {
            if (key == TOA_INSTANCE_KEY) raids.externalTeleport(player, instanceId)
        }
    }
}

internal fun toaInstanceSpec(description: String = "Tombs of Amascut"): InstanceSpec =
    InstanceSettings(
            maxPlayers = TOA_MAX_PARTY_SIZE,
            reclaimTicks = 2_000,
            graceTicks = 10,
            destroyWhenEmpty = false,
            description = description,
            timeLimitTicks = null,
        )
        .withArea(ToaInstanceLayout.area(), settingsRowId = 0)
