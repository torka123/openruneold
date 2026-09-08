package org.rsmod.content.minigames.toa

import dev.openrune.ServerCacheManager

internal object ToaCacheTestSupport {
    private val revision240 =
        lazy(LazyThreadSafetyMode.SYNCHRONIZED) { ServerCacheManager.init(240) }

    fun initRevision240() = revision240.value
}
