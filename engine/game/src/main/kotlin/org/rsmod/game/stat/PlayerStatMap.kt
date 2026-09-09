package org.rsmod.game.stat

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import it.unimi.dsi.fastutil.bytes.Byte2ByteOpenHashMap
import it.unimi.dsi.fastutil.bytes.Byte2IntOpenHashMap
import org.rsmod.annotations.InternalApi
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player

public class PlayerStatMap(
    private val xp: Byte2IntOpenHashMap = Byte2IntOpenHashMap(),
    private val baseLevels: Byte2ByteOpenHashMap = Byte2ByteOpenHashMap(),
    private val currLevels: Byte2ByteOpenHashMap = Byte2ByteOpenHashMap(),
) {
    private var observation: Observation? = null

    /** Registry-owned binding; offline account loading intentionally has no gameplay observers. */
    @InternalApi
    public fun bindObservations(player: Player, eventBus: EventBus, clock: MapClock) {
        require(player.statMap === this)
        check(observation == null) { "Stat observations already bound" }
        observation = Observation(player, eventBus, clock)
    }

    @InternalApi
    public fun unbindObservations() {
        observation = null
    }

    public fun getXP(stat: String): Int = getFineXP(stat) / XP_FINE_PRECISION

    public fun setXP(stat: String, xp: Int) {
        setFineXP(stat, xp * XP_FINE_PRECISION)
    }

    public fun getFineXP(stat: String): Int =
        xp.getOrDefault(stat.asRSCM(RSCMType.STAT).toByte(), 0)

    public fun setFineXP(stat: String, xp: Int) {
        require(xp in 0..MAX_FINE_XP) {
            "`xp` must be within range [0..$MAX_FINE_XP]. (stat=$stat, xp=$xp)"
        }
        this.xp[stat.asRSCM(RSCMType.STAT).toByte()] = xp
    }

    @InternalApi
    public fun getBaseLevel(stat: String): Byte =
        baseLevels.getOrDefault(stat.asRSCM(RSCMType.STAT).toByte(), 1)

    public fun setBaseLevel(stat: String, level: Byte) {
        val id = stat.asRSCM(RSCMType.STAT).toByte()
        val previous = baseLevels.getOrDefault(id, 1)
        baseLevels[id] = level
        publish(id, StatLevelKind.Base, previous, level)
    }

    @InternalApi
    public fun getCurrentLevel(stat: String): Byte =
        currLevels.getOrDefault(stat.asRSCM(RSCMType.STAT).toByte(), 1)

    public fun setCurrentLevel(stat: String, level: Byte) {
        val id = stat.asRSCM(RSCMType.STAT).toByte()
        val previous = currLevels.getOrDefault(id, 1)
        currLevels[id] = level
        publish(id, StatLevelKind.Current, previous, level)
    }

    private fun publish(id: Byte, kind: StatLevelKind, previous: Byte, current: Byte) {
        if (previous == current) return
        val (player, bus, clock) = observation ?: return
        bus.publish(
            PlayerStatChangedEvent(
                player,
                id.toInt() and 0xFF,
                kind,
                previous.toInt() and 0xFF,
                current.toInt() and 0xFF,
                clock.cycle,
                player.combatHitScope?.token(),
            )
        )
    }

    private data class Observation(val player: Player, val bus: EventBus, val clock: MapClock)

    public companion object {
        public const val MAX_XP: Int = 200_000_000
        public const val XP_FINE_PRECISION: Int = 10
        public const val MAX_FINE_XP: Int = MAX_XP * XP_FINE_PRECISION

        public fun toFineXP(xp: Double): Double = xp * XP_FINE_PRECISION

        public fun normalizeFineXP(fineXp: Int): Int = fineXp / XP_FINE_PRECISION
    }
}
