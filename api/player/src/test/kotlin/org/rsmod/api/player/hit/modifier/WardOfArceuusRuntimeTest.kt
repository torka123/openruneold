package org.rsmod.api.player.hit.modifier

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.rsmod.annotations.InternalApi
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType

class WardOfArceuusRuntimeTest {
    @OptIn(InternalApi::class)
    @Test fun `real NPC damage obeys active Ward independently of display preference`() {
        val player = Player().apply {
            uuid = 1L; observerUUID = 1L; slotId = 1; assignUid()
            currentMapClock = 100
            statMap.setBaseLevel("stat.hitpoints", 99)
            statMap.setCurrentLevel("stat.hitpoints", 99)
            inv = invMap.getOrPut("inv.inv")
            worn = invMap.getOrPut("inv.worn")
        }
        val demon = ServerCacheManager.getNpcs().entries.first { it.value.param(params.demon) != 0 }.key
        val nonDemon = ServerCacheManager.getNpcs().entries.first { it.value.param(params.demon) == 0 }.key
        for (disabled in 0..1) {
            VarPlayerIntMapSetter.set(player, "varbit.buff_ward_of_arceuus_disabled", disabled)
            WardOfArceuusState.clear(player)
            assertEquals(11, hit(player, demon))
            WardOfArceuusState.activate(player, 102)
            assertEquals(10, hit(player, demon))
            assertEquals(11, hit(player, nonDemon))
            assertEquals(11, hit(player, null)) // Unknown source is never classified as a demon.
            player.currentMapClock = 102
            assertEquals(11, hit(player, demon))
            assertEquals(disabled, player.vars["varbit.buff_ward_of_arceuus_disabled"])
            player.currentMapClock = 100
        }
    }

    private fun hit(player: Player, npcType: Int?): Int {
        val hit = HitBuilder(
            type = HitType.Melee, damage = 11,
            sourceUid = npcType?.let { NpcUid(1, it).packed }, sourceSlot = 1,
            isFromNpc = true, isFromPlayer = false, clientDelay = 0,
            righthandType = null, secondaryType = null,
            targetHitmark = 16, sourceHitmark = 16, publicHitmark = null,
            zeroDamageHitmarkLit = null, zeroDamageHitmarkTint = null, maxDamageHitmarkLit = null,
            targetMaxDamageThreshold = Int.MAX_VALUE, sourceMaxDamageThreshold = Int.MAX_VALUE,
        )
        with(StandardPlayerHitModifier) { hit.modify(player) }
        return hit.damage
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}
