package org.rsmod.content.bosses.dt2

/** Historical pre-DSL rotation retained unchanged as a differential test oracle. */
/** Counter transitions consume no RNG. Specials do not increment the standard-attack counter. */
internal class DukeRotation {
    var standards = 0
        private set

    private var sinceGaze = 0
    private var sinceGas = 0
    private var gasDue = 8
    private var lastWasGaze = false

    fun next(hp: Int, awakened: Boolean): DukeAction {
        if (sinceGaze == 5) {
            sinceGaze = 0
            lastWasGaze = true
            return DukeAction.GAZE
        }
        if (sinceGas >= gasDue && !lastWasGaze) {
            sinceGas = 0
            gasDue = DukeRules.gasInterval(hp, awakened)
            return DukeAction.GAS
        }
        standards++
        sinceGaze++
        sinceGas++
        lastWasGaze = false
        return DukeAction.STANDARD
    }
}
