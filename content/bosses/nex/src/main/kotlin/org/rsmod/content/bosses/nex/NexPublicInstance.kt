package org.rsmod.content.bosses.nex

import jakarta.inject.Inject
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceScript
import org.rsmod.api.instances.InstanceSettings
import org.rsmod.map.CoordGrid

internal const val NEX_PUBLIC_INSTANCE_KEY = "nex_public"

// Preserve the current public-room limits and reward owner. OSRS cap/private admission and
// durable Nex reward records remain separately mapped mechanics in the Nex evidence ledger.
internal val NEX_PUBLIC_INSTANCE_SPEC =
    InstanceSettings(maxPlayers = Int.MAX_VALUE, recordsEligible = false)
        .withArea(
            InstanceArea.fixed(
                CoordGrid(2910, 5189),
                CoordGrid(2941, 5218),
                NEX_ARENA_ENTRY,
                NEX_BANK_RETURN,
            ),
            settingsRowId = 0,
        )

internal class NexPublicInstance @Inject constructor(registry: BossInstanceRegistry) :
    InstanceScript(registry, NEX_PUBLIC_INSTANCE_KEY, NEX_PUBLIC_INSTANCE_SPEC)
