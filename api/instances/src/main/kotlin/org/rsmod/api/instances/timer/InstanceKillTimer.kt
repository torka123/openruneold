package org.rsmod.api.instances.timer

import org.rsmod.api.instances.INSTANCE_KILL_TIMER_MAX_PLAYERS
import org.rsmod.api.instances.INSTANCE_KILL_TIMER_MAX_TICKS
import org.rsmod.api.instances.INSTANCE_TICKS_PER_MINUTE
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.instanceKillBest
import org.rsmod.api.instances.recordInstanceKillBest
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Player

internal object InstanceKillTimer {

    fun tracksKillTime(session: InstanceSession): Boolean =
        session.spec.maxPlayers <= INSTANCE_KILL_TIMER_MAX_PLAYERS

    fun formatDuration(ticks: Int): String {
        val totalSeconds = ticks * 60 / INSTANCE_TICKS_PER_MINUTE
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60

        return if (minutes > 0) {
            "$minutes minute${if (minutes == 1) "" else "s"} and $seconds second${if (seconds == 1) "" else "s"}"
        } else {
            "$seconds second${if (seconds == 1) "" else "s"}"
        }
    }

    /** Reports the kill time and returns true only when a new personal best was recorded. */
    fun reportKillTime(player: Player, instanceKey: String, elapsedTicks: Int): Boolean {
        val duration = formatDuration(elapsedTicks)

        if (elapsedTicks > INSTANCE_KILL_TIMER_MAX_TICKS) {
            player.mes(
                "<col=ff0000>Your kill time of $duration was too long to record a personal best.</col>",
                ChatType.Engine,
            )
            return false
        }

        val improved = player.recordInstanceKillBest(instanceKey, elapsedTicks)
        val best = player.instanceKillBest(instanceKey)?.let(::formatDuration)

        return when {
            improved -> {
                player.mes(
                    "<col=008000>Kill time: $duration — a new personal best of ${best ?: duration}!</col>",
                    ChatType.Engine,
                )
                true
            }

            best != null -> {
                player.mes(
                    "Kill time: $duration. Your personal best remains $best.",
                    ChatType.Engine,
                )
                false
            }

            else -> {
                player.mes("Kill time: $duration.", ChatType.Engine)
                false
            }
        }
    }
}
