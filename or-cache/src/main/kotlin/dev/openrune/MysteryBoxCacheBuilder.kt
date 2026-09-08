package dev.openrune

import com.displee.cache.CacheLibrary
import dev.openrune.cache.CONFIGS
import dev.openrune.cache.MODELS
import dev.openrune.definition.codec.SpotAnimCodec
import dev.openrune.definition.type.SpotAnimType
import dev.openrune.filesystem.Cache
import io.netty.buffer.Unpooled
import java.nio.file.Files
import java.nio.file.Path

/** Installs the isolated Mystery Box graphics after the revision-240 LIVE cache is rebuilt. */
public fun patchMysteryBoxAssets(live: Path) {
    require(revision.first == MysteryBoxCacheIds.Revision) {
        "Mystery Box graphics are defined for revision ${MysteryBoxCacheIds.Revision}, " +
            "not revision ${revision.first}."
    }
    require(Files.isDirectory(live)) { "Required LIVE cache not found at $live" }

    verifyModelPresent(live)

    val codec = SpotAnimCodec(MysteryBoxCacheIds.Revision)
    val encoded =
        MysteryBoxCacheIds.Graphics.associate { graphic ->
            graphic.id to encode(codec, graphic.toDefinition())
        }
    write(live, encoded)
    verify(live, codec)

    println(
        "Installed Mystery Box model ${MysteryBoxCacheIds.Model} and graphics " +
            "${MysteryBoxCacheIds.Graphics.first().id}..${MysteryBoxCacheIds.Graphics.last().id} in LIVE."
    )
}

private fun verifyModelPresent(live: Path) {
    val cache = Cache.load(live)
    try {
        check(cache.data(MODELS, MysteryBoxCacheIds.Model, 0) != null) {
            "Mystery Box model ${MysteryBoxCacheIds.Model} is missing from $live"
        }
    } finally {
        cache.close()
    }
}

private fun encode(codec: SpotAnimCodec, definition: SpotAnimType): ByteArray {
    val buffer = Unpooled.buffer()
    return try {
        with(codec) { buffer.encode(definition) }
        ByteArray(buffer.readableBytes()).also(buffer::readBytes)
    } finally {
        buffer.release()
    }
}

private fun write(live: Path, graphics: Map<Int, ByteArray>) {
    val cache = CacheLibrary(live.toString())
    try {
        graphics.forEach { (id, bytes) ->
            cache.put(CONFIGS, MysteryBoxCacheIds.SpotAnimArchive, id, bytes)
        }
        check(cache.index(CONFIGS).update()) {
            "Cache index update failed for Mystery Box graphics in $live"
        }
    } finally {
        cache.close()
    }
}

private fun verify(live: Path, codec: SpotAnimCodec) {
    val cache = Cache.load(live)
    try {
        MysteryBoxCacheIds.Graphics.forEach { expected ->
            val bytes =
                requireNotNull(
                    cache.data(CONFIGS, MysteryBoxCacheIds.SpotAnimArchive, expected.id)
                ) {
                    "Mystery Box graphic ${expected.id} is missing from $live"
                }
            val actual = codec.loadData(expected.id, bytes)
            check(actual.modelId == expected.modelId) {
                "Mystery Box graphic ${expected.id} model mismatch: ${actual.modelId}"
            }
            check(actual.animationId == expected.animationId) {
                "Mystery Box graphic ${expected.id} animation mismatch: ${actual.animationId}"
            }
            check(actual.originalColours == expected.originalColours) {
                "Mystery Box graphic ${expected.id} recolour source mismatch"
            }
            check(actual.modifiedColours == expected.modifiedColours) {
                "Mystery Box graphic ${expected.id} recolour target mismatch"
            }
        }
    } finally {
        cache.close()
    }
}

private data class MysteryBoxGraphic(
    val id: Int,
    val modelId: Int,
    val animationId: Int = -1,
    val originalColours: List<Int>? = null,
    val modifiedColours: List<Int>? = null,
) {
    fun toDefinition(): SpotAnimType =
        SpotAnimType().also { definition ->
            definition.id = id
            definition.modelId = modelId
            definition.animationId = animationId
            definition.resizeX = 128
            definition.resizeY = 128
            definition.ambient = 30
            definition.contrast = 30
            definition.originalColours = originalColours?.toMutableList()
            definition.modifiedColours = modifiedColours?.toMutableList()
        }
}

private object MysteryBoxCacheIds {
    const val Revision = 240
    const val Model = 61869
    const val SpotAnimArchive = 13
    const val BoxAnimation = 6353

    val Graphics: List<MysteryBoxGraphic> =
        listOf(
            MysteryBoxGraphic(id = 4018, modelId = Model, animationId = BoxAnimation),
            MysteryBoxGraphic(id = 4019, modelId = 2635),
            MysteryBoxGraphic(id = 4020, modelId = 35742),
            MysteryBoxGraphic(id = 4021, modelId = 28075),
            MysteryBoxGraphic(id = 4022, modelId = 32799),
            MysteryBoxGraphic(
                id = 4023,
                modelId = 2635,
                originalColours = listOf(926),
                modifiedColours = listOf(-14400),
            ),
        )
}
