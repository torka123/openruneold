package org.rsmod.content.skills.sailing

/**
 * Keeps incomplete Sailing content unreachable to regular players while its engine and content are
 * developed on the live server classpath. Administrator-only diagnostics remain available for
 * controlled testing.
 *
 * This gate may become `true` only after the collision, lifecycle, persistence, multiplayer, and
 * two-restart acceptance gates in `docs/sailing/SAILING_TEST_PLAN.md` pass.
 */
internal object SailingReleaseGate {
    const val playerGameplayEnabled: Boolean = false
}
