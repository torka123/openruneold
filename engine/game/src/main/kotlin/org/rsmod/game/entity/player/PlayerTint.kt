package org.rsmod.game.entity.player

/** Opaque identity for one owned tint application on one Player. */
public class PlayerTintToken internal constructor()

/**
 * A one-shot player-info tint update.
 *
 * The client consumes this update once and retains the visual effect for the supplied client-cycle
 * interval. A producer must therefore queue [CLEAR] when an encounter ends early.
 */
public data class PlayerTint(
    val startTime: Int,
    val endTime: Int,
    val hue: Int,
    val saturation: Int,
    val lightness: Int,
    val weight: Int,
) {
    init {
        require(startTime in UNSIGNED_SHORT_RANGE) { "Invalid tint start time: $startTime" }
        require(endTime in UNSIGNED_SHORT_RANGE) { "Invalid tint end time: $endTime" }
        require(hue in UNSIGNED_BYTE_RANGE) { "Invalid tint hue: $hue" }
        require(saturation in UNSIGNED_BYTE_RANGE) { "Invalid tint saturation: $saturation" }
        require(lightness in UNSIGNED_BYTE_RANGE) { "Invalid tint lightness: $lightness" }
        require(weight in UNSIGNED_BYTE_RANGE) { "Invalid tint weight: $weight" }
    }

    public companion object {
        private val UNSIGNED_SHORT_RANGE: IntRange = 0..65_535
        private val UNSIGNED_BYTE_RANGE: IntRange = 0..255

        /** The client-side no-tint sentinel from the captured player-info update. */
        public val CLEAR: PlayerTint = PlayerTint(0, 0, 255, 255, 255, 0)
    }
}
