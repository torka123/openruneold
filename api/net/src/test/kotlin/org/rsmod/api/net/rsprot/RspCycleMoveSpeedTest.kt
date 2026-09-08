package org.rsmod.api.net.rsprot

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.game.movement.MoveSpeed

class RspCycleMoveSpeedTest {
    @Test
    fun `stationary telejump after login still transmits snap speed`() {
        assertTrue(
            shouldTransmitTempMoveSpeed(
                pendingMoveSpeed = MoveSpeed.Stationary,
                cachedMoveSpeed = MoveSpeed.Stationary,
                pendingTelejump = true,
                coordinatesChanged = true,
            )
        )
    }

    @Test
    fun `repeated stationary telejump still transmits snap speed`() {
        assertTrue(
            shouldTransmitTempMoveSpeed(
                pendingMoveSpeed = MoveSpeed.Stationary,
                cachedMoveSpeed = MoveSpeed.Stationary,
                pendingTelejump = true,
                coordinatesChanged = true,
            )
        )
    }

    @Test
    fun `normal movement still transmits when speed changes`() {
        assertTrue(
            shouldTransmitTempMoveSpeed(
                pendingMoveSpeed = MoveSpeed.Run,
                cachedMoveSpeed = MoveSpeed.Walk,
                pendingTelejump = false,
                coordinatesChanged = true,
            )
        )
    }

    @Test
    fun `stationary non-telejump does not send redundant speed`() {
        assertFalse(
            shouldTransmitTempMoveSpeed(
                pendingMoveSpeed = MoveSpeed.Stationary,
                cachedMoveSpeed = MoveSpeed.Stationary,
                pendingTelejump = false,
                coordinatesChanged = true,
            )
        )
    }

    @Test
    fun `unchanged coordinates never send temporary speed`() {
        assertFalse(
            shouldTransmitTempMoveSpeed(
                pendingMoveSpeed = MoveSpeed.Stationary,
                cachedMoveSpeed = MoveSpeed.Stationary,
                pendingTelejump = true,
                coordinatesChanged = false,
            )
        )
    }
}
