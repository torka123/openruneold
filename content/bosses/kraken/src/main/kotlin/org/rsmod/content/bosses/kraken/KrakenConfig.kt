package org.rsmod.content.bosses.kraken

import dev.openrune.types.NpcServerType
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceNpc
import org.rsmod.api.instances.InstanceSettings
import org.rsmod.api.instances.RegionLocal
import org.rsmod.map.CoordGrid

internal const val KRAKEN_INSTANCE_KEY = "kraken_cove"
internal const val KRAKEN_SOURCE_MAP_X = 35
internal const val KRAKEN_SOURCE_MAP_Z = 156
internal const val KRAKEN_SOURCE_REGION = (KRAKEN_SOURCE_MAP_X shl 8) or KRAKEN_SOURCE_MAP_Z

internal const val KRAKEN_ENTRY_LOCAL_X = 40
internal const val KRAKEN_ENTRY_LOCAL_Z = 38
internal const val KRAKEN_BOSS_LOCAL_X = 38
internal const val KRAKEN_BOSS_LOCAL_Z = 50

/** Public-side approach tile immediately south of cache loc 537 (Enter/Private/Peek). */
internal val KRAKEN_PUBLIC_EXIT = CoordGrid(2280, 10016, 0)

internal val KRAKEN_TENTACLE_SPAWNS = listOf(35 to 50, 35 to 54, 44 to 50, 44 to 54)

internal val KRAKEN_AREA =
    InstanceArea.copyRegions(
        regionIds = listOf(KRAKEN_SOURCE_REGION),
        enterCoord =
            RegionLocal(
                0,
                KRAKEN_SOURCE_MAP_X,
                KRAKEN_SOURCE_MAP_Z,
                KRAKEN_ENTRY_LOCAL_X,
                KRAKEN_ENTRY_LOCAL_Z,
            ),
        exitCoord = KRAKEN_PUBLIC_EXIT,
        npcSpawns =
            listOf(
                InstanceNpc(
                    KrakenAssets.BOSS_WHIRLPOOL,
                    CoordGrid(
                        KRAKEN_SOURCE_MAP_X * 64 + KRAKEN_BOSS_LOCAL_X,
                        KRAKEN_SOURCE_MAP_Z * 64 + KRAKEN_BOSS_LOCAL_Z,
                    ),
                )
            ) +
                KRAKEN_TENTACLE_SPAWNS.map { (x, z) ->
                    InstanceNpc(
                        KrakenAssets.TENTACLE_WHIRLPOOL,
                        CoordGrid(KRAKEN_SOURCE_MAP_X * 64 + x, KRAKEN_SOURCE_MAP_Z * 64 + z),
                    )
                },
    )

internal fun krakenSpec(activeType: NpcServerType) =
    InstanceSettings(
            maxPlayers = 1,
            destroyWhenEmpty = true,
            bossNpc = listOf(activeType),
            bossName = "Kraken",
            recommendedCombat = 100..126,
            teamSize = 1,
            description = "Private Kraken encounter",
        )
        .withArea(KRAKEN_AREA, settingsRowId = 0)

internal enum class KrakenPhase {
    DORMANT,
    ACTIVE,
    COMPLETE,
}

internal object KrakenAssets {
    const val BOSS = "npc.slayer_kraken_boss"
    const val BOSS_WHIRLPOOL = "npc.slayer_kraken_boss_whirlpool"
    const val TENTACLE = "npc.slayer_kraken_boss_tentacle"
    const val TENTACLE_WHIRLPOOL = "npc.slayer_kraken_boss_tentacle_whirlpool"

    const val PUBLIC_CREVICE = 537
    const val INSIDE_CREVICE = 538

    const val MAGIC_PROJECTILE_CONTROLLER = "projanim.magic_spell"
    const val BOSS_PROJECTILE = "spotanim.firewave_travel"
    const val BOSS_IMPACT = "spotanim.firewave_impact"
    const val TENTACLE_PROJECTILE = "spotanim.waterstrike_travel"
    const val TENTACLE_IMPACT = "spotanim.waterstrike_impact"
}
