package dev.openrune

import dev.openrune.definition.type.SpotAnimType
import dev.openrune.filesystem.Cache
import java.nio.file.Path

internal fun ServerCacheManager.getSpotAnimation(id: Int): SpotAnimType? =
    cerberusSpotanims.value[id]

private val cerberusSpotanims = lazy {
    val cache = Cache.load(Path.of(".data/cache/LIVE"))
    try {
        mutableMapOf<Int, SpotAnimType>().also { definitions ->
            OsrsCacheProvider.SpotAnimDecoder(240).load(cache, definitions)
        }
    } finally {
        cache.close()
    }
}
