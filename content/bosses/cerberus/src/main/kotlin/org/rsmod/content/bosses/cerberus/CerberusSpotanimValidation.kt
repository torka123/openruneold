package org.rsmod.content.bosses.cerberus

import dev.openrune.OsrsCacheProvider
import dev.openrune.definition.type.SpotAnimType
import dev.openrune.filesystem.Cache
import java.nio.file.Path

/** Validate definitions decoded from the same LIVE cache that supplies the client's JS5 data. */
internal object CerberusSpotanimValidation {
    private val definitions by lazy {
        val cache = Cache.load(Path.of(".data/cache/LIVE"))
        try {
            mutableMapOf<Int, SpotAnimType>().also {
                OsrsCacheProvider.SpotAnimDecoder(240).load(cache, it)
            }
        } finally {
            cache.close()
        }
    }

    fun requireLoaded(id: Int): Int {
        requireNotNull(definitions[id]) { "Missing Cerberus spot-animation $id in the LIVE cache." }
        return id
    }
}
