package org.rsmod.api.player.hit.modifier

import dev.openrune.ServerCacheManager
import dev.openrune.gamevals.GameValProvider
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.api.player.vars.VarPlayerIntMapSetter

class LaunchPrayerSnapshotTest {
    @Test
    fun `normal and triple components retain launch protection after later switches`() {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("game.yml")) }
        System.setProperty("user.dir", root.toString())
        GameValProvider.load("$root${File.separator}")
        val cache = ServerCacheManager.init(root.resolve(".data/cache/SERVER"), 240)
        try {
            val player = Player()
            for ((style, prayer) in listOf(
                HitType.Magic to "varbit.prayer_protectfrommagic",
                HitType.Ranged to "varbit.prayer_protectfrommissiles",
                HitType.Melee to "varbit.prayer_protectfrommelee",
            )) {
                for (protected in listOf(false, true)) {
                    VarPlayerIntMapSetter.set(player, prayer, if (protected) 1 else 0)
                    val builder = builder(style)
                    val modifier = NpcCalculatedProtectionPrayerPlayerHitModifier(
                        protectedAtCalculation = player.vars[prayer] == 1, reductionPercent = 100,
                    )
                    with(modifier) { builder.modify(player) }
                    val launched = builder.build()
                    VarPlayerIntMapSetter.set(player, prayer, if (protected) 0 else 1)
                    assertEquals(if (protected) 0 else 23, launched.damage, "$style protected=$protected")
                    VarPlayerIntMapSetter.set(player, prayer, 0)
                }
            }
        } finally {
            cache.close()
        }
    }

    private fun builder(style: HitType) = HitBuilder(
        type = style, damage = 23, sourceUid = null, sourceSlot = null,
        isFromNpc = false, isFromPlayer = false, clientDelay = 0,
        righthandType = null, secondaryType = null,
        targetHitmark = 16, sourceHitmark = 16, publicHitmark = null,
        zeroDamageHitmarkLit = null, zeroDamageHitmarkTint = null, maxDamageHitmarkLit = null,
        targetMaxDamageThreshold = Int.MAX_VALUE, sourceMaxDamageThreshold = Int.MAX_VALUE,
    )
}
