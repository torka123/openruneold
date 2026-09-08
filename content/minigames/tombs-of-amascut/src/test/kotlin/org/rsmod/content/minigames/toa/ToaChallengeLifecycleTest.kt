package org.rsmod.content.minigames.toa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ToaChallengeLifecycleTest {
    @Test
    fun `donor entry spawns are translated to revision 240 room locals`() {
        val expected =
            mapOf(
                ToaRoom.SCABARAS to (2 to 31),
                ToaRoom.KEPHRI to (15 to 32),
                ToaRoom.HET to (50 to 31),
                ToaRoom.AKKHA to (48 to 32),
                ToaRoom.APMEKEN to (16 to 31),
                ToaRoom.BABA to (14 to 31),
                ToaRoom.CRONDIS to (50 to 31),
                ToaRoom.ZEBAK to (54 to 31),
                ToaRoom.WARDENS to (31 to 56),
                ToaRoom.TUMEKEN_WARDEN to (31 to 41),
            )
        expected.forEach { (room, local) ->
            assertEquals(local.first, room.entryLocalX, room.displayName)
            assertEquals(local.second, room.entryLocalZ, room.displayName)
        }
    }

    @Test
    fun `challenge entries start outside and cross the exact red barrier`() {
        assertEquals(
            TOA_ACTIVE_PATHS.mapTo(linkedSetOf()) { it.challenge },
            ToaChallengeRoomRules.rooms,
        )
        ToaChallengeRoomRules.rooms.forEach { room ->
            val bounds = assertNotNull(ToaChallengeRoomRules.bounds(room))
            assertFalse(bounds.containsLocal(room.entryLocalX, room.entryLocalZ), room.displayName)
        }

        val southBarrier =
            ToaBarrierCrossing.candidates(
                player = org.rsmod.map.CoordGrid(11, 26, 0),
                minX = 12,
                minZ = 26,
                maxX = 12,
                maxZ = 26,
            )
        val northBarrier =
            ToaBarrierCrossing.candidates(
                player = org.rsmod.map.CoordGrid(11, 38, 0),
                minX = 12,
                minZ = 38,
                maxX = 12,
                maxZ = 38,
            )
        assertEquals(13 to 26, southBarrier.first().let { it.x to it.z })
        assertEquals(13 to 38, northBarrier.first().let { it.x to it.z })
        val bounds = assertNotNull(ToaChallengeRoomRules.bounds(ToaRoom.SCABARAS))
        assertTrue(bounds.containsLocal(southBarrier.first().x, southBarrier.first().z))
        assertTrue(bounds.containsLocal(northBarrier.first().x, northBarrier.first().z))
    }

    @Test
    fun `red barrier is the authoritative challenge state transition`() {
        assertEquals(45135, ToaLocs.CHALLENGE_BARRIER)
        assertEquals(
            ToaBarrierDecision.CONFIRM_START,
            ToaChallengeRoomRules.decide(
                ToaChallengeStage.INACTIVE,
                spectating = false,
                inside = false,
                confirmed = false,
            ),
        )
        assertEquals(
            ToaBarrierDecision.START_CUTSCENE,
            ToaChallengeRoomRules.decide(
                ToaChallengeStage.INACTIVE,
                spectating = false,
                inside = false,
                confirmed = true,
            ),
        )
        assertEquals(
            ToaBarrierDecision.ENTRY_BUSY,
            ToaChallengeRoomRules.decide(
                ToaChallengeStage.STARTING,
                spectating = false,
                inside = false,
                confirmed = true,
            ),
        )
        assertEquals(
            ToaBarrierDecision.JOIN_CUTSCENE,
            ToaChallengeRoomRules.decide(
                ToaChallengeStage.ACTIVE,
                spectating = false,
                inside = false,
                confirmed = false,
            ),
        )
        assertEquals(
            ToaBarrierDecision.BLOCK_ACTIVE_EXIT,
            ToaChallengeRoomRules.decide(
                ToaChallengeStage.ACTIVE,
                spectating = false,
                inside = true,
                confirmed = false,
            ),
        )
        assertEquals(
            ToaBarrierDecision.EXIT_COMPLETED_CHALLENGE,
            ToaChallengeRoomRules.decide(
                ToaChallengeStage.ACTIVE,
                spectating = false,
                inside = true,
                confirmed = false,
                completionExitOpen = true,
            ),
            "A completed Crondis objective must reopen the route to its continuation passage.",
        )
        assertEquals(
            ToaBarrierDecision.DENY,
            ToaChallengeRoomRules.decide(
                ToaChallengeStage.INACTIVE,
                spectating = true,
                inside = false,
                confirmed = true,
            ),
        )
    }

    @Test
    fun `camera entries are bounded while Akkha uses its captured direct crystal transition`() {
        val expected =
            TOA_ACTIVE_PATHS.flatMapTo(linkedSetOf()) { listOf(it.challenge, it.boss) } +
                ToaRoom.WARDENS
        assertEquals(null, ToaChallengeRoomRules.cutscene(ToaRoom.AKKHA))
        expected.minus(ToaRoom.AKKHA).forEach { room ->
            val scene = assertNotNull(ToaChallengeRoomRules.cutscene(room), room.displayName)
            assertTrue(scene.cameraLocalX in 0 until 64)
            assertTrue(scene.cameraLocalZ in 0 until 64)
            assertTrue(scene.focusLocalX in 0 until 64)
            assertTrue(scene.focusLocalZ in 0 until 64)
            assertTrue(scene.durationTicks in 1..10)
        }
    }

    @Test
    fun `Akkha crystal transition uses packet audited locals and effects`() {
        assertEquals(
            25 to 30,
            AkkhaEntryLifecycle.WAITING_LOCAL_X to AkkhaEntryLifecycle.WAITING_LOCAL_Z,
        )
        assertEquals(
            41 to 32,
            AkkhaEntryLifecycle.FIGHT_LOCAL_X to AkkhaEntryLifecycle.FIGHT_LOCAL_Z,
        )
        assertEquals(AkkhaEntryLifecycle.FIGHT_LOCAL_X, ToaRoom.AKKHA.fightLocalX)
        assertEquals(AkkhaEntryLifecycle.FIGHT_LOCAL_Z, ToaRoom.AKKHA.fightLocalZ)
        assertEquals(409, AkkhaEntryLifecycle.PLAYER_SPOTANIM)
        assertEquals(198, AkkhaEntryLifecycle.START_SOUND)
        assertEquals(739, AkkhaEntryLifecycle.MIDI)
        assertEquals("Challenge started: Akkha.", AkkhaEntryLifecycle.START_MESSAGE)
    }
}
