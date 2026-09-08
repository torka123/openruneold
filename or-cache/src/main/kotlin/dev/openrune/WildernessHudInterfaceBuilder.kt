package dev.openrune

import com.displee.cache.CacheLibrary
import dev.openrune.cache.INTERFACES
import dev.openrune.cache.filestore.definition.ComponentDecoder
import dev.openrune.definition.type.widget.ComponentType
import dev.openrune.filesystem.Cache
import io.netty.buffer.Unpooled
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Adds two server-owned text children to the stock PvP icon overlay.
 *
 * The stock wilderness-level child is rewritten by clientscript 388, while child 52 is the
 * intentionally flashing `quitterwarning`. Neither is safe for server-defined underground
 * Wilderness levels. These clones keep the native fonts and layout but remove every client hook.
 */
public fun main() {
    val root = Paths.get(System.getProperty("user.dir")).normalize()
    patchWildernessHudInterface(
        live = root.resolve(Paths.get(".data", "cache", "LIVE")),
        server = root.resolve(Paths.get(".data", "cache", "SERVER")),
    )
}

public fun patchWildernessHudInterface(live: Path, server: Path) {
    val caches = listOf(live, server)
    caches.forEach { path ->
        require(Files.isDirectory(path)) { "Required cache not found at $path" }
        patch(path)
        verify(path)
    }
    println(
        "Added dedicated Wilderness HUD children " +
            "${WildernessHudInterfaceIds.ServerLevel}/${WildernessHudInterfaceIds.ServerRange} " +
            "to interface ${WildernessHudInterfaceIds.Interface} in LIVE and SERVER."
    )
}

private fun patch(path: Path) {
    val encoded: Map<Int, ByteArray>
    val cache = Cache.load(path)
    try {
        val decoder = ComponentDecoder(cache, WildernessHudInterfaceIds.Revision)
        val stockLevel =
            decode(cache, decoder, WildernessHudInterfaceIds.StockLevel, "wildernesslevel")
        val stockRange =
            decode(cache, decoder, WildernessHudInterfaceIds.StockRange, "comlevelrange")
        val components =
            linkedMapOf(
                WildernessHudInterfaceIds.ServerLevel to
                    serverClone(
                        stockLevel,
                        WildernessHudInterfaceIds.ServerLevel,
                        "server_wildernesslevel",
                        WildernessHudInterfaceIds.LevelY,
                    ),
                WildernessHudInterfaceIds.ServerRange to
                    serverClone(
                        stockRange,
                        WildernessHudInterfaceIds.ServerRange,
                        "server_comlevelrange",
                        WildernessHudInterfaceIds.RangeY,
                    ),
            )
        encoded = components.mapValues { (_, component) -> encode(decoder, component) }
    } finally {
        cache.close()
    }

    val writable = CacheLibrary(path.toString())
    try {
        encoded.forEach { (child, bytes) ->
            writable.put(INTERFACES, WildernessHudInterfaceIds.Interface, child, bytes)
        }
        check(writable.index(INTERFACES).update()) { "Interface index update failed for $path" }
    } finally {
        writable.close()
    }
}

private fun serverClone(stock: ComponentType, child: Int, name: String, y: Int): ComponentType {
    val packed = WildernessHudInterfaceIds.packed(child)
    return stock.copy(
        y = y,
        yMode = WildernessHudInterfaceIds.AbsolutePositionMode,
        hide = true,
        text = "",
        onLoad = null,
        onVarTransmit = null,
        onTimer = null,
        onVarTransmitList = null,
        internalId = packed,
        internalName = name,
        id = stock.id,
    )
}

private fun encode(decoder: ComponentDecoder, component: ComponentType): ByteArray {
    val buffer = Unpooled.buffer()
    return try {
        decoder.encode(component, buffer)
        ByteArray(buffer.readableBytes()).also(buffer::readBytes)
    } finally {
        buffer.release()
    }
}

private fun decode(
    cache: Cache,
    decoder: ComponentDecoder,
    child: Int,
    name: String,
): ComponentType {
    val bytes =
        requireNotNull(cache.data(INTERFACES, WildernessHudInterfaceIds.Interface, child)) {
            "Missing interface ${WildernessHudInterfaceIds.Interface}:$child"
        }
    val buffer = Unpooled.wrappedBuffer(bytes)
    return try {
        decoder.read(WildernessHudInterfaceIds.packed(child), buffer, name)
    } finally {
        buffer.release()
    }
}

private fun verify(path: Path) {
    val cache = Cache.load(path)
    try {
        val decoder = ComponentDecoder(cache, WildernessHudInterfaceIds.Revision)
        val parent = decode(cache, decoder, WildernessHudInterfaceIds.Parent, "pvp_icons_container")
        val skull = decode(cache, decoder, WildernessHudInterfaceIds.Skull, "wilderness_skull")
        val stockLevel =
            decode(cache, decoder, WildernessHudInterfaceIds.StockLevel, "wildernesslevel")
        val stockRange =
            decode(cache, decoder, WildernessHudInterfaceIds.StockRange, "comlevelrange")
        val level =
            decode(cache, decoder, WildernessHudInterfaceIds.ServerLevel, "server_wildernesslevel")
        val range =
            decode(cache, decoder, WildernessHudInterfaceIds.ServerRange, "server_comlevelrange")
        check(level.textFont == stockLevel.textFont && level.textFont == 495)
        check(range.textFont == stockRange.textFont && range.textFont == 494)
        check(level.textShadow && range.textShadow)
        check(level.layer == stockLevel.layer && range.layer == stockRange.layer)
        check(level.x == 0 && range.x == 0)
        check(level.xMode == stockLevel.xMode && range.xMode == stockRange.xMode)
        check(level.widthMode == stockLevel.widthMode && range.widthMode == stockRange.widthMode)
        check(level.textAlignH == 1 && range.textAlignH == 1)
        check(level.yMode == WildernessHudInterfaceIds.AbsolutePositionMode)
        check(range.yMode == WildernessHudInterfaceIds.AbsolutePositionMode)
        check(level.y == WildernessHudInterfaceIds.LevelY)
        check(range.y == WildernessHudInterfaceIds.RangeY)
        check(level.y >= skull.y + skull.height + WildernessHudInterfaceIds.SkullToTextGap)
        check(range.y >= level.y + level.height + WildernessHudInterfaceIds.LineGap)
        check(range.y + range.height <= parent.height)
        check(level.hide && range.hide)
        check(level.onLoad == null && level.onTimer == null && level.onVarTransmit == null)
        check(range.onLoad == null && range.onTimer == null && range.onVarTransmit == null)
    } finally {
        cache.close()
    }
}

private object WildernessHudInterfaceIds {
    const val Revision = 240
    const val Interface = 90
    const val Parent = 44
    const val Skull = 45
    const val StockRange = 49
    const val StockLevel = 50
    const val ServerLevel = 55
    const val ServerRange = 56
    const val AbsolutePositionMode = 0
    const val SkullToTextGap = 2
    const val LineGap = 1
    const val LevelY = 27
    const val RangeY = 44

    fun packed(child: Int): Int = (Interface shl 16) or child
}
