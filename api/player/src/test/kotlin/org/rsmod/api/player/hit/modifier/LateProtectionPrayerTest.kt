package org.rsmod.api.player.hit.modifier

import dev.openrune.ServerCacheManager
import dev.openrune.gamevals.GameValProvider
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType

class LateProtectionPrayerTest {
    @Test
    fun `launch takes precedence while late matching prayer has its own reduction`() {
        for ((style, prayer) in prayers) for (launch in listOf(false, true)) {
            for (impact in listOf(false, true)) {
                val player = Player()
                VarPlayerIntMapSetter.set(player, prayer, if (impact) 1 else 0)
                val hit = builder(style)
                with(NpcCalculatedProtectionPrayerPlayerHitModifier(launch, 50, 25)) {
                    hit.modify(player)
                }
                assertEquals(if (launch) 16 else if (impact) 24 else 33, hit.damage)
            }
        }
    }

    @Test
    fun `default remains launch-only and wrong prayer cannot reduce damage`() {
        val player = Player()
        VarPlayerIntMapSetter.set(player, "varbit.prayer_protectfrommagic", 1)
        val default = builder(HitType.Magic)
        with(NpcCalculatedProtectionPrayerPlayerHitModifier(false, 50)) { default.modify(player) }
        assertEquals(33, default.damage)
        for (style in listOf(HitType.Ranged, HitType.Melee, HitType.Typeless)) {
            val hit = builder(style)
            with(NpcCalculatedProtectionPrayerPlayerHitModifier(false, 50, 25)) {
                hit.modify(player)
            }
            assertEquals(33, hit.damage)
        }
    }

    @Test
    fun `invalid late percentages are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            NpcCalculatedProtectionPrayerPlayerHitModifier(false, 50, -1)
        }
        assertFailsWith<IllegalArgumentException> {
            NpcCalculatedProtectionPrayerPlayerHitModifier(false, 50, 101)
        }
    }

    private fun builder(style: HitType) =
        HitBuilder(
            type = style,
            damage = 33,
            sourceUid = null,
            sourceSlot = null,
            isFromNpc = false,
            isFromPlayer = false,
            clientDelay = 0,
            righthandType = null,
            secondaryType = null,
            targetHitmark = 16,
            sourceHitmark = 16,
            publicHitmark = null,
            zeroDamageHitmarkLit = null,
            zeroDamageHitmarkTint = null,
            maxDamageHitmarkLit = null,
            targetMaxDamageThreshold = Int.MAX_VALUE,
            sourceMaxDamageThreshold = Int.MAX_VALUE,
        )

    companion object {
        private val prayers =
            listOf(
                HitType.Magic to "varbit.prayer_protectfrommagic",
                HitType.Ranged to "varbit.prayer_protectfrommissiles",
                HitType.Melee to "varbit.prayer_protectfrommelee",
            )

        @BeforeAll
        @JvmStatic
        fun cache() {
            val root =
                generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                    .first { Files.exists(it.resolve("game.yml")) }
            System.setProperty("user.dir", root.toString())
            GameValProvider.load("$root${File.separator}")
            ServerCacheManager.init(root.resolve(".data/cache/SERVER"), 240).close()
        }
    }
}
