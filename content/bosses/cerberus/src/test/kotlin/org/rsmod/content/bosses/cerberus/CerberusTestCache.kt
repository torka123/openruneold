package org.rsmod.content.bosses.cerberus

import dev.openrune.ServerCacheManager

internal object CerberusTestCache {
    private val initialized by lazy { ServerCacheManager.init(240).close() }

    fun ensureLoaded() { initialized }
}
