package org.rsmod.content.minigames.toa

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player

@Singleton
internal class ToaUi @Inject constructor(private val eventBus: EventBus) {
    fun verifyDefinitions() {
        val interfaceId = HUD_INTERFACE.asRSCM(RSCMType.INTERFACE)
        require(interfaceId == HUD_INTERFACE_ID) {
            "Revision-240 ToA HUD changed: expected $HUD_INTERFACE_ID, found $interfaceId"
        }
        requireNotNull(ServerCacheManager.getInterface(interfaceId)) {
            "Missing native revision-240 ToA HUD $HUD_INTERFACE"
        }
        HUD_TEXT_COMPONENTS.forEach { component ->
            val type = ServerCacheManager.fromComponent(component)
            require(type.interfaceId == interfaceId) {
                "ToA HUD component is mapped to the wrong interface: $component"
            }
        }
    }

    fun open(player: Player, raid: ToaRaidSession, cycle: Int) {
        player.ifOpenOverlay(HUD_INTERFACE, eventBus)
        sync(player, raid, cycle)
    }

    fun sync(player: Player, raid: ToaRaidSession, cycle: Int) {
        val timerStart = raid.timerStartedAt
        val elapsed = timerStart?.let { (cycle - it).coerceAtLeast(0) } ?: 0
        val elapsedSeconds = elapsed * 3 / 5
        player.ifSetText(LEVEL_TEXT, "Level: ${raid.rewardRaidLevel}")
        player.ifSetText(
            TIMER_TEXT,
            "${elapsedSeconds / 60}:${(elapsedSeconds % 60).toString().padStart(2, '0')}",
        )
        player.ifSetText(SCABARAS_LEVEL, (raid.pathLevels[ToaPath.SCABARAS] ?: 0).toString())
        player.ifSetText(HET_LEVEL, (raid.pathLevels[ToaPath.HET] ?: 0).toString())
        player.ifSetText(APMEKEN_LEVEL, (raid.pathLevels[ToaPath.APMEKEN] ?: 0).toString())
        player.ifSetText(CRONDIS_LEVEL, (raid.pathLevels[ToaPath.CRONDIS] ?: 0).toString())
    }

    fun close(player: Player) = player.ifCloseOverlay(HUD_INTERFACE, eventBus)

    companion object {
        const val HUD_INTERFACE_ID = 481
        const val HUD_INTERFACE = "interface.toa_hud"
        const val LEVEL_TEXT = "component.toa_hud:raid_level_txt"
        const val TIMER_TEXT = "component.toa_hud:raid_timer"
        const val SCABARAS_LEVEL = "component.toa_hud:nexus_scabaras_level"
        const val HET_LEVEL = "component.toa_hud:nexus_akkha_level"
        const val APMEKEN_LEVEL = "component.toa_hud:nexus_apmekan_level"
        const val CRONDIS_LEVEL = "component.toa_hud:nexus_crondis_level"
        val HUD_TEXT_COMPONENTS =
            setOf(LEVEL_TEXT, TIMER_TEXT, SCABARAS_LEVEL, HET_LEVEL, APMEKEN_LEVEL, CRONDIS_LEVEL)
    }
}
